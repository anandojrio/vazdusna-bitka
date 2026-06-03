package org.example.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.dto.CommandMessage;
import org.example.common.enums.AircraftType;
import org.example.common.enums.Side;
import org.example.common.model.Position;
import org.example.radar.service.RadarService;
import org.example.squadroon.aircraft.AircraftWorker;
import org.example.squadroon.missile.BaseLauncherPool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.rmi.Naming;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedSquadroonApplication {

    private static final String COMMAND_CENTER_HOST = "localhost";
    private static final int COMMAND_CENTER_PORT = 5001;
    private static final boolean LOG_SQUADRON_DEBUG = false;

    public static void main(String[] args) {
        try {
            RadarService radarService =
                    (RadarService) Naming.lookup("rmi://localhost:1099/RadarService");

            Socket ccSocket = new Socket(COMMAND_CENTER_HOST, COMMAND_CENTER_PORT);
            PrintWriter ccWriter = new PrintWriter(
                    new OutputStreamWriter(ccSocket.getOutputStream()), true);
            BufferedReader ccReader = new BufferedReader(
                    new InputStreamReader(ccSocket.getInputStream()));

            Map<String, AircraftWorker> aircraftMap = new HashMap<>();
            List<Thread> aircraftThreads = new ArrayList<>();

            BaseLauncherPool redBaseLauncherPool = new BaseLauncherPool(Side.RED);

            AircraftWorker c1 = new AircraftWorker(
                    "C1",
                    AircraftType.F15,
                    Side.RED,
                    new Position(7.0, 7.0),   // H8
                    radarService,
                    ccWriter,
                    redBaseLauncherPool
            );

            AircraftWorker c2 = new AircraftWorker(
                    "C2",
                    AircraftType.F22,
                    Side.RED,
                    new Position(7.0, 7.0),   // H8
                    radarService,
                    ccWriter,
                    redBaseLauncherPool
            );

            AircraftWorker c3 = new AircraftWorker(
                    "C3",
                    AircraftType.MIG31,
                    Side.RED,
                    new Position(7.0, 7.0),   // H8
                    radarService,
                    ccWriter,
                    redBaseLauncherPool
            );

            AircraftWorker c4 = new AircraftWorker(
                    "C4",
                    AircraftType.SU30,
                    Side.RED,
                    new Position(7.0, 7.0),   // H8
                    radarService,
                    ccWriter,
                    redBaseLauncherPool
            );

            AircraftWorker c5 = new AircraftWorker(
                    "C5",
                    AircraftType.MIG31,
                    Side.RED,
                    new Position(7.0, 7.0),   // H8
                    radarService,
                    ccWriter,
                    redBaseLauncherPool
            );

            aircraftMap.put("C1", c1);
            aircraftMap.put("C2", c2);
            aircraftMap.put("C3", c3);
            aircraftMap.put("C4", c4);
            aircraftMap.put("C5", c5);

            aircraftThreads.add(new Thread(c1, "Aircraft-C1"));
            aircraftThreads.add(new Thread(c2, "Aircraft-C2"));
            aircraftThreads.add(new Thread(c3, "Aircraft-C3"));
            aircraftThreads.add(new Thread(c4, "Aircraft-C4"));
            aircraftThreads.add(new Thread(c5, "Aircraft-C5"));

            for (Thread t : aircraftThreads) {
                t.start();
            }

            System.out.println("RedSquadronApplication started successfully.");
            System.out.println("5 red aircraft threads are running and waiting for commands from Red Command Center.");
            System.out.println("[RED SQ] Base launcher pool initialized: " + redBaseLauncherPool.getStatusSummary());

            Thread commandReceiver = new Thread(() -> {
                ObjectMapper mapper =
                        new ObjectMapper();
                try {
                    String line;
                    while ((line = ccReader.readLine()) != null) {
                        CommandMessage cmd = mapper.readValue(line, CommandMessage.class);

                        AircraftWorker target = aircraftMap.get(cmd.getAircraftId());
                        if (target == null) {
                            System.out.println("[RED SQ] Unknown aircraft id in command: " + cmd.getAircraftId());
                            continue;
                        }

                        switch (cmd.getType()) {
                            case RETURN_TO_BASE -> {
                                if(LOG_SQUADRON_DEBUG)
                                    System.out.println("[RED SQ] RETURN_TO_BASE for " + cmd.getAircraftId());
                                target.setReturnToBase(true);
                            }
                            case PATROL -> {
                                if(LOG_SQUADRON_DEBUG)
                                    System.out.printf("[RED SQ] PATROL for %s: x[%.1f, %.1f], y[%.1f, %.1f]%n",
                                        cmd.getAircraftId(),
                                        cmd.getMinX(), cmd.getMaxX(),
                                        cmd.getMinY(), cmd.getMaxY());
                                target.setPatrolArea(
                                        cmd.getMinX(), cmd.getMaxX(),
                                        cmd.getMinY(), cmd.getMaxY()
                                );
                            }
                            case ATTACK -> {
                                Position targetPosition = new Position(cmd.getTargetX(), cmd.getTargetY());

                                if(LOG_SQUADRON_DEBUG)
                                    System.out.printf("[RED SQ] ATTACK for %s -> %s at (%.1f, %.1f)%n",
                                        cmd.getAircraftId(),
                                        cmd.getTargetId(),
                                        cmd.getTargetX(),
                                        cmd.getTargetY());

                                target.handleAttackCommand(cmd.getTargetId(), targetPosition);
                            }
                            case DESTROY -> {
                                System.out.println("[RED SQ] DESTROY for " + cmd.getAircraftId());
                                target.destroyFromCommandCenter();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[RED SQ] Command receiver ended: " + e.getMessage());
                    for (AircraftWorker worker : aircraftMap.values()) {
                        worker.stop();
                    }
                }
            }, "Red-Squadron-Command-Receiver");

            commandReceiver.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}