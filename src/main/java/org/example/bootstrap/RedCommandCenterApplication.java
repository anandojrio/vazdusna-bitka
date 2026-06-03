package org.example.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.dto.*;
import org.example.common.enums.Side;
import org.example.common.model.PatrolBox;
import org.example.common.model.Position;
import org.example.common.util.AnsiColors;
import org.example.common.util.BoardUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RedCommandCenterApplication {

    private static final int PORT = 5001;
    private static final int KILL_LISTEN_PORT = 6001;
    private static final int REMOTE_CC_PORT = 6000;

    private static final boolean LOG_CC_FRIENDLY_MOVES = true;
    private static final boolean LOG_CC_ENEMY_MOVES = true;
    private static final boolean LOG_CC_ATTACKS = true;
    private static final boolean LOG_CC_MISSILES = true; // launch + hit + self-destruct
    private static final boolean LOG_CC_KILLS = true;

    private static final String SIDE_LABEL = "RED";
    private static final Side MY_SIDE = Side.RED;
    private static final int CELL_WIDTH = 14;

    private static volatile PrintWriter remoteCcWriter;

    private static final Map<String, Position> knownPositions = new ConcurrentHashMap<>();
    private static final Map<String, Position> enemyPositions = new ConcurrentHashMap<>();

    private static final Map<String, String> lastKnownCellByAircraft = new ConcurrentHashMap<>();
    private static final Map<String, String> lastKnownEnemyCellByAircraft = new ConcurrentHashMap<>();

    private static final List<PrintWriter> squadronWriters = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();

        try (ServerSocket squadronServerSocket = new ServerSocket(PORT);
             ServerSocket killServerSocket = new ServerSocket(KILL_LISTEN_PORT)) {

            System.out.println(AnsiColors.cyan("Red Command Center listening on port " + PORT));
            System.out.println(AnsiColors.cyan("Red Command Center listening for kill notifications on port " + KILL_LISTEN_PORT));

            Thread remoteConnectThread = new Thread(() -> connectToRemoteCc(), "RED-CC-Remote-Connect");
            Thread acceptThread = new Thread(() -> acceptSquadrons(squadronServerSocket, objectMapper), "RED-CC-Accept");
            Thread commandThread = new Thread(() -> commandLoop(objectMapper), "RED-CC-Command");
            Thread killThread = new Thread(() -> killListenLoop(killServerSocket, objectMapper), "RED-CC-Kill-Listen");

            remoteConnectThread.start();
            acceptThread.start();
            commandThread.start();
            killThread.start();

            remoteConnectThread.join();
            acceptThread.join();
            commandThread.join();
            killThread.join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void connectToRemoteCc() {
        while (remoteCcWriter == null) {
            try {
                Socket ccSocket = new Socket("localhost", REMOTE_CC_PORT);
                remoteCcWriter = new PrintWriter(new OutputStreamWriter(ccSocket.getOutputStream()), true);
                System.out.println("[RED CC] Connected to BLUE Command Center on port " + REMOTE_CC_PORT);
            } catch (Exception e) {
                System.out.println("[RED CC] Waiting for BLUE Command Center on port " + REMOTE_CC_PORT + "...");
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void acceptSquadrons(ServerSocket serverSocket, ObjectMapper objectMapper) {
        try {
            while (true) {
                Socket squadronSocket = serverSocket.accept();
                System.out.println("[RED CC] Squadron connected from " + squadronSocket.getRemoteSocketAddress());

                PrintWriter writer = new PrintWriter(new OutputStreamWriter(squadronSocket.getOutputStream()), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(squadronSocket.getInputStream()));

                squadronWriters.add(writer);

                Thread receiveThread = new Thread(
                        () -> receiveLoop(reader, writer, objectMapper),
                        "RED-CC-Receive-" + squadronSocket.getPort()
                );
                receiveThread.start();
            }
        } catch (Exception e) {
            System.out.println("[RED CC] Accept thread ended: " + e.getMessage());
        }
    }

    private static void receiveLoop(BufferedReader reader, PrintWriter writer, ObjectMapper objectMapper) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                SquadroonOutboundMessage outbound =
                        objectMapper.readValue(line, SquadroonOutboundMessage.class);

                switch (outbound.getMessageType()) {
                    case AIRCRAFT_REPORT -> handleAircraftReport(outbound.getAircraftReport());
                    case MISSILE_REPORT -> handleMissileReport(outbound.getMissileReport());
                }
            }
        } catch (Exception e) {
            System.out.println("[RED CC] Receive thread ended: " + e.getMessage());
        } finally {
            squadronWriters.remove(writer);
        }
    }

    private static void killListenLoop(ServerSocket killServerSocket, ObjectMapper objectMapper) {
        try {
            while (true) {
                Socket socket = killServerSocket.accept();
                System.out.println("[RED CC] Kill-link connected from " + socket.getRemoteSocketAddress());

                Thread t = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            KillNotification notification =
                                    objectMapper.readValue(line, KillNotification.class);
                            handleKillNotification(notification, objectMapper);
                        }
                    } catch (Exception e) {
                        System.out.println("[RED CC] Kill-link reader ended: " + e.getMessage());
                    }
                }, "RED-CC-Kill-Reader-" + socket.getPort());

                t.start();
            }
        } catch (Exception e) {
            System.out.println("[RED CC] Kill listen thread ended: " + e.getMessage());
        }
    }

    private static void handleKillNotification(KillNotification notification, ObjectMapper objectMapper) {
        if (notification == null || notification.getAircraftId() == null) {
            return;
        }

        String victimId = notification.getAircraftId().toUpperCase();

        if (LOG_CC_KILLS) {
            System.out.printf("[RED CC] KILL CONFIRMED by remote CC: %s destroyed%n",
                    colorizeAircraftId(victimId));
        }

        knownPositions.remove(victimId);
        lastKnownCellByAircraft.remove(victimId);

        try {
            CommandMessage destroyCmd = new CommandMessage(CommandMessage.CommandType.DESTROY, victimId);
            String json = objectMapper.writeValueAsString(destroyCmd);

            for (PrintWriter writer : squadronWriters) {
                writer.println(json);
                writer.flush();
            }

            if (LOG_CC_KILLS) {
                System.out.printf("[RED CC] Forwarded DESTROY command for %s to BLUE squadron%n",
                        colorizeAircraftId(victimId));
            }
        } catch (Exception e) {
            System.out.println("[RED CC] Failed to forward DESTROY command: " + e.getMessage());
        }
    }

    private static void handleMissileReport(MissileReportMessage message) {
        if (message == null) {
            return;
        }

        // Početak napada – prvi put kada CC dobije report za tu raketu
//        if (LOG_CC_MISSILES && message.getStatus() == MissileReportMessage.Status.LAUNCHED) {
//            System.out.printf("[RED CC] missile %s LAUNCHED by %s towards %s from [%s] lastKnownTarget=[%s]%n",
//                    message.getMissileId(),
//                    colorizeAircraftId(message.getSourceAircraftId()),
//                    colorizeAircraftId(message.getTargetId()),
//                    BoardUtils.positionToCell(message.getPosition()),
//                    BoardUtils.positionToCell(message.getLastKnownTargetPosition()));
//        }

        if (message.isHit()) {
            if (LOG_CC_MISSILES) {
                System.out.printf("[RED CC] missile %s HIT target %s at [%s]%n",
                        message.getMissileId(),
                        colorizeAircraftId(message.getTargetId()),
                        BoardUtils.positionToCell(message.getPosition()));
            }

            enemyPositions.remove(message.getTargetId());
            lastKnownEnemyCellByAircraft.remove(message.getTargetId());

            sendKillNotificationToRemote(message.getTargetId());
            return;
        }

        if (message.isSelfDestructed()) {
            if (LOG_CC_MISSILES) {
                System.out.printf("[RED CC] missile %s SELF_DESTRUCTED while tracking %s at [%s]%n",
                        message.getMissileId(),
                        colorizeAircraftId(message.getTargetId()),
                        BoardUtils.positionToCell(message.getPosition()));
            }
        }
    }

    private static void sendKillNotificationToRemote(String targetId) {
        if (remoteCcWriter == null) {
            System.out.println("[RED CC] No link to BLUE Command Center, cannot send kill notification for " + targetId);
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            KillNotification notification = new KillNotification(targetId);
            String json = mapper.writeValueAsString(notification);

            remoteCcWriter.println(json);
            remoteCcWriter.flush();

            System.out.println("[RED CC] Sent kill notification for " + targetId + " to BLUE Command Center.");
        } catch (Exception e) {
            System.out.println("[RED CC] Failed to send kill notification: " + e.getMessage());
        }
    }

    private static void handleAircraftReport(AircraftReportMessage message) {
        Position pos = message.getPosition();
        String aircraftId = message.getAircraftId();

        knownPositions.put(aircraftId, pos);

        String newCell = BoardUtils.positionToCell(pos);
        String oldCell = lastKnownCellByAircraft.put(aircraftId, newCell);

        if (LOG_CC_FRIENDLY_MOVES) {
            if (oldCell == null) {
                System.out.printf("[RED CC] %s registered at [%s]%n",
                        colorizeAircraftId(aircraftId), newCell);
            } else if (!newCell.equals(oldCell)) {
                System.out.printf("[RED CC] %s moved %s -> %s%n",
                        colorizeAircraftId(aircraftId), oldCell, newCell);
            }
        }

        updateEnemyContacts(message.getRadarContacts());
    }

    private static void updateEnemyContacts(List<RadarContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return;
        }

        for (RadarContact contact : contacts) {
            if (contact == null || contact.getId() == null || contact.getPosition() == null) {
                continue;
            }

            if (contact.getSide() == MY_SIDE) {
                continue;
            }

            enemyPositions.put(contact.getId(), contact.getPosition());

            String newCell = BoardUtils.positionToCell(contact.getPosition());
            String oldCell = lastKnownEnemyCellByAircraft.put(contact.getId(), newCell);

            if (LOG_CC_ENEMY_MOVES) {
                if (oldCell == null) {
                    System.out.printf("[RED CC] enemy %s detected at [%s]%n",
                            colorizeAircraftId(contact.getId()), newCell);
                } else if (!newCell.equals(oldCell)) {
                    System.out.printf("[RED CC] enemy %s moved %s -> %s%n",
                            colorizeAircraftId(contact.getId()), oldCell, newCell);
                }
            }
        }
    }

    private static void commandLoop(ObjectMapper objectMapper) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter commands for RED side:");
        System.out.println("  SHOW");
        System.out.println("  C1 RETURN");
        System.out.println("  C2 PATROL 5.0 7.0 5.0 7.0");
        System.out.println("  C3 PATROL G7 H8");
        System.out.println("  C1 ATTACK P3");

        try {
            while (true) {
                String line = scanner.nextLine();
                if (line == null || line.isBlank()) {
                    continue;
                }

                if ("SHOW".equalsIgnoreCase(line.trim())) {
                    printBoard(SIDE_LABEL);
                    continue;
                }

                if (squadronWriters.isEmpty()) {
                    System.out.println("[RED CC] No connected squadrons.");
                    continue;
                }

                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) {
                    System.out.println("[RED CC] Invalid command format.");
                    continue;
                }

                String aircraftId = parts[0];
                String cmd = parts[1].toUpperCase();

                CommandMessage command = buildCommand(aircraftId, cmd, parts);
                if (command == null) {
                    continue;
                }

                String json = objectMapper.writeValueAsString(command);

                for (PrintWriter writer : squadronWriters) {
                    writer.println(json);
                    writer.flush();
                }
            }
        } catch (Exception e) {
            System.out.println("[RED CC] Command thread ended: " + e.getMessage());
        }
    }

    private static CommandMessage buildCommand(String aircraftId, String cmd, String[] parts) {
        if ("RETURN".equals(cmd)) {
            System.out.println("[RED CC] Sending RETURN to " + aircraftId);
            return new CommandMessage(CommandMessage.CommandType.RETURN_TO_BASE, aircraftId);
        }

        if ("ATTACK".equals(cmd)) {
            if (parts.length != 3) {
                System.out.println("[RED CC] Invalid ATTACK command.");
                System.out.println("Use:");
                System.out.println("  C1 ATTACK P3");
                return null;
            }

            String targetId = parts[2].toUpperCase();
            Position targetPos = enemyPositions.get(targetId);

            if (targetPos == null) {
                System.out.println("[RED CC] Unknown or undetected enemy target: " + targetId);
                return null;
            }

            CommandMessage command = new CommandMessage(CommandMessage.CommandType.ATTACK, aircraftId);
            command.setTargetId(targetId);
            command.setTargetX(targetPos.getX());
            command.setTargetY(targetPos.getY());

            System.out.printf("[RED CC] Sending ATTACK to %s -> %s at (%.1f, %.1f)%n",
                    aircraftId, targetId, targetPos.getX(), targetPos.getY());

            return command;
        }

        if ("PATROL".equals(cmd)) {
            if (parts.length == 4) {
                String fromCell = parts[2].toUpperCase();
                String toCell = parts[3].toUpperCase();

                PatrolBox box = BoardUtils.toPatrolBox(fromCell, toCell);

                System.out.printf("[RED CC] Sending PATROL to %s for box %s -> %s%n",
                        aircraftId, fromCell, toCell);

                return new CommandMessage(
                        CommandMessage.CommandType.PATROL,
                        aircraftId,
                        box.minX(), box.maxX(),
                        box.minY(), box.maxY()
                );
            }

            if (parts.length == 6) {
                double minX = Double.parseDouble(parts[2]);
                double maxX = Double.parseDouble(parts[3]);
                double minY = Double.parseDouble(parts[4]);
                double maxY = Double.parseDouble(parts[5]);

                System.out.printf("[RED CC] Sending PATROL to %s for x[%.1f, %.1f], y[%.1f, %.1f]%n",
                        aircraftId, minX, maxX, minY, maxY);

                return new CommandMessage(
                        CommandMessage.CommandType.PATROL,
                        aircraftId,
                        minX, maxX, minY, maxY
                );
            }

            System.out.println("[RED CC] Invalid PATROL command.");
            System.out.println("Use:");
            System.out.println("  C1 PATROL G7 H8");
            System.out.println("  C1 PATROL 5.0 7.0 5.0 7.0");
            return null;
        }

        System.out.println("[RED CC] Unknown command: " + cmd);
        return null;
    }

    private static void printBoard(String sideLabel) {
        Map<String, List<String>> byCell = new HashMap<>();

        for (Map.Entry<String, Position> entry : knownPositions.entrySet()) {
            String aircraftId = entry.getKey();
            Position position = entry.getValue();
            String cell = BoardUtils.positionToCell(position);

            byCell.computeIfAbsent(cell, k -> new ArrayList<>())
                    .add(colorizeAircraftId(aircraftId));
        }

        for (Map.Entry<String, Position> entry : enemyPositions.entrySet()) {
            String aircraftId = entry.getKey();
            Position position = entry.getValue();
            String cell = BoardUtils.positionToCell(position);

            byCell.computeIfAbsent(cell, k -> new ArrayList<>())
                    .add(colorizeAircraftId(aircraftId));
        }

        String horizontal = "+" + "-".repeat(5) + "+" + ("-".repeat(CELL_WIDTH) + "+").repeat(8);

        System.out.println();
        System.out.println(AnsiColors.cyan("============== " + sideLabel + " COMMAND CENTER =============="));
        System.out.println(horizontal);

        for (int row = 7; row >= 0; row--) {
            StringBuilder line = new StringBuilder();
            line.append(String.format("| %-3d|", row + 1));

            for (int col = 0; col < 8; col++) {
                String cellName = "" + (char) ('A' + col) + (row + 1);
                List<String> ids = byCell.getOrDefault(cellName, List.of());
                String content = formatCellContent(ids);
                line.append(String.format("%-" + CELL_WIDTH + "s|", content));
            }

            System.out.println(line);
            System.out.println(horizontal);
        }

        StringBuilder footer = new StringBuilder("|    |");
        for (int col = 0; col < 8; col++) {
            footer.append(String.format("%-" + CELL_WIDTH + "s|", " " + (char) ('A' + col)));
        }

        System.out.println(footer);
        System.out.println(horizontal);
        System.out.println();
    }

    private static String formatCellContent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return ".";

        if (ids.size() <= 2) {
            return String.join(",", ids);
        }

        return ids.get(0) + "," + ids.get(1) + "...";
    }

    private static String colorizeAircraftId(String aircraftId) {
        if (aircraftId.startsWith("P")) {
            return AnsiColors.blue(aircraftId);
        }
        if (aircraftId.startsWith("C")) {
            return AnsiColors.red(aircraftId);
        }
        return aircraftId;
    }
}