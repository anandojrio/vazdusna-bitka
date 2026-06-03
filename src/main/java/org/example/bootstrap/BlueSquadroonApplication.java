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

public class BlueSquadroonApplication {

    private static final String COMMAND_CENTER_HOST = "localhost";
    private static final int COMMAND_CENTER_PORT = 5000;
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

            BaseLauncherPool blueBaseLauncherPool = new BaseLauncherPool(Side.BLUE);

            AircraftWorker p1 = new AircraftWorker(
                    "P1",
                    AircraftType.MIG31,
                    Side.BLUE,
                    new Position(0.0, 0.0),   // A1
                    radarService,
                    ccWriter,
                    blueBaseLauncherPool
            );

            AircraftWorker p2 = new AircraftWorker(
                    "P2",
                    AircraftType.F15,
                    Side.BLUE,
                    new Position(0.0, 0.0),   // A1
                    radarService,
                    ccWriter,
                    blueBaseLauncherPool
            );

            AircraftWorker p3 = new AircraftWorker(
                    "P3",
                    AircraftType.F22,
                    Side.BLUE,
                    new Position(0.0, 0.0),   // A1
                    radarService,
                    ccWriter,
                    blueBaseLauncherPool
            );

            AircraftWorker p4 = new AircraftWorker(
                    "P4",
                    AircraftType.SU30,
                    Side.BLUE,
                    new Position(0.0, 0.0),   // A1
                    radarService,
                    ccWriter,
                    blueBaseLauncherPool
            );

            AircraftWorker p5 = new AircraftWorker(
                    "P5",
                    AircraftType.MIG31,
                    Side.BLUE,
                    new Position(0.0, 0.0),   // A1
                    radarService,
                    ccWriter,
                    blueBaseLauncherPool
            );

            aircraftMap.put("P1", p1);
            aircraftMap.put("P2", p2);
            aircraftMap.put("P3", p3);
            aircraftMap.put("P4", p4);
            aircraftMap.put("P5", p5);

            aircraftThreads.add(new Thread(p1, "Aircraft-P1"));
            aircraftThreads.add(new Thread(p2, "Aircraft-P2"));
            aircraftThreads.add(new Thread(p3, "Aircraft-P3"));
            aircraftThreads.add(new Thread(p4, "Aircraft-P4"));
            aircraftThreads.add(new Thread(p5, "Aircraft-P5"));

            for (Thread t : aircraftThreads) {
                t.start();
            }

            System.out.println("BlueSquadronApplication started successfully.");
            System.out.println("5 blue aircraft threads are running and waiting for commands from Blue Command Center.");
            System.out.println("[BLUE SQ] Base launcher pool initialized: " + blueBaseLauncherPool.getStatusSummary());

            Thread commandReceiver = new Thread(() -> {
                ObjectMapper mapper =
                        new ObjectMapper();
                try {
                    String line;
                    while ((line = ccReader.readLine()) != null) {
                        CommandMessage cmd = mapper.readValue(line, CommandMessage.class);

                        AircraftWorker target = aircraftMap.get(cmd.getAircraftId());
                        if (target == null) {
                            System.out.println("[BLUE SQ] Unknown aircraft id in command: " + cmd.getAircraftId());
                            continue;
                        }

                        switch (cmd.getType()) {
                            case RETURN_TO_BASE -> {
                                if(LOG_SQUADRON_DEBUG)
                                    System.out.println("[BLUE SQ] RETURN_TO_BASE for " + cmd.getAircraftId());
                                target.setReturnToBase(true);
                            }
                            case PATROL -> {
                                if(LOG_SQUADRON_DEBUG)
                                    System.out.printf("[BLUE SQ] PATROL for %s: x[%.1f, %.1f], y[%.1f, %.1f]%n",
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
                                    System.out.printf("[BLUE SQ] ATTACK for %s -> %s at (%.1f, %.1f)%n",
                                        cmd.getAircraftId(),
                                        cmd.getTargetId(),
                                        cmd.getTargetX(),
                                        cmd.getTargetY());

                                target.handleAttackCommand(cmd.getTargetId(), targetPosition);
                            }
                            case DESTROY -> {
                                System.out.println("[BLUE SQ] DESTROY for " + cmd.getAircraftId());
                                target.destroyFromCommandCenter();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[BLUE SQ] Command receiver ended: " + e.getMessage());
                    for (AircraftWorker worker : aircraftMap.values()) {
                        worker.stop();
                    }
                }
            }, "Blue-Squadron-Command-Receiver");

            commandReceiver.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}