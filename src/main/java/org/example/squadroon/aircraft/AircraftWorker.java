package org.example.squadroon.aircraft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.dto.AircraftReportMessage;
import org.example.common.dto.RadarScanResponse;
import org.example.common.dto.RadarUpdateRequest;
import org.example.common.dto.SquadroonOutboundMessage;
import org.example.common.enums.AircraftState;
import org.example.common.enums.AircraftType;
import org.example.common.enums.FlyingObjectType;
import org.example.common.enums.Side;
import org.example.common.model.Position;
import org.example.common.util.BoardUtils;
import org.example.radar.service.RadarService;
import org.example.squadroon.missile.BaseLauncherPool;
import org.example.squadroon.missile.MissileWorker;
import org.example.common.enums.MissileLaunchOrigin;

import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class AircraftWorker implements Runnable {

    private final PrintWriter commandCenterWriter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<MissileWorker> activeMissiles = new CopyOnWriteArrayList<>();

    private final String id;
    private final AircraftType aircraftType;
    private final Side side;
    private final RadarService radarService;
    private final Position basePosition;

    private volatile Position position;
    private final double radarRange;
    private volatile boolean running = true;
    private volatile AircraftState state = AircraftState.IN_BASE;

    // poslednja celija aviona
    private volatile String lastKnownCell = null;

    private volatile boolean aircraftMissileAvailable = true;
    private final BaseLauncherPool baseLauncherPool;
    private static final double MISSILE_RADAR_RANGE = 0.6;

    private volatile double patrolMinX = -1.0;
    private volatile double patrolMaxX = -1.0;
    private volatile double patrolMinY = -1.0;
    private volatile double patrolMaxY = -1.0;

    private volatile double stepX;
    private volatile double stepY;

    private static final double MIN_COORD = 0.0;
    private static final double MAX_COORD = 7.0;

    private final Random random = new Random();

    private static final boolean DEBUG_MOVEMENT = false; // brzine itd.
    private static final boolean LOG_AIRCRAFT_DEBUG = false; // stari verbose
    private static final boolean LOG_AIRCRAFT_STATE_CHANGES = true;
    private static final boolean LOG_AIRCRAFT_CELL_CHANGES = true;
    private static final boolean LOG_AIRCRAFT_ATTACKS = true;

    public AircraftWorker(String id,
                          AircraftType aircraftType,
                          Side side,
                          Position initialPosition,
                          RadarService radarService,
                          PrintWriter commandCenterWriter,
                          BaseLauncherPool baseLauncherPool) {
        this.id = id;
        this.aircraftType = aircraftType;
        this.side = side;
        this.position = initialPosition;
        this.radarRange = aircraftType.getRadarRange();
        this.radarService = radarService;
        this.commandCenterWriter = commandCenterWriter;
        this.basePosition = new Position(initialPosition.getX(), initialPosition.getY());
        this.baseLauncherPool = baseLauncherPool;

        resetPatrolDirection();
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                switch (state) {
                    case IN_BASE -> handleInBase();
                    case PATROLLING -> handlePatrolling();
                    case RETURNING -> handleReturning();
                    case DESTROYED -> handleDestroyed();
                }

                sleepBySpeed();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop();
            } catch (RemoteException e) {
                logEvent("radar communication failure: " + e.getMessage());
                stop();
            } catch (Exception e) {
                logEvent("unexpected error: " + e.getMessage());
                stop();
            }
        }
    }

    private void sleepBySpeed() throws InterruptedException {
        int maxPause = aircraftType.getMaxPauseMs();

        // Minimalna pauza da se izbegne 0, onda uniformno u [min, max]
        int minPause = 1000;
        if (maxPause < minPause) {
            maxPause = minPause;
        }

        int pause = minPause + random.nextInt(maxPause - minPause + 1);

        // test log za brzine razlicitih aviona (RANDOM SVAKI PUT)
        if (DEBUG_MOVEMENT && state == AircraftState.PATROLLING) {
            System.out.printf("[%s | %s] sleeping %d ms%n", id, aircraftType, pause);
        }

        Thread.sleep(pause);
    }

    private void handleInBase() throws Exception {
        sendReport(false);
    }

    private void handlePatrolling() throws Exception {
        if (!hasPatrolArea()) {
            logEvent("invalid patrol area, switching to IN_BASE");
            state = AircraftState.IN_BASE;
            return;
        }

        patrolStep();
        sendReport(true);
    }

    private void handleReturning() throws Exception {
        moveTowardsBase();
        sendReport(true);

        if (isAtBase()) {
            position = new Position(basePosition.getX(), basePosition.getY());
            clearPatrolArea();
            changeState(AircraftState.IN_BASE, "arrived at base");
        }
    }

    public void handleAttackCommand(String targetId, Position targetPosition) {
        if (state == AircraftState.DESTROYED) {
            logEvent("ignoring ATTACK, aircraft destroyed");
            return;
        }
        if (targetId == null || targetId.isBlank()) {
            logEvent("ATTACK command without targetId, ignoring");
            return;
        }
        if (targetPosition == null) {
            logEvent("ATTACK " + targetId + " but target position unknown, ignoring");
            return;
        }

        MissileLaunchOrigin launchOrigin;
        Position launchPosition;
        Integer launcherId = null;

        if (aircraftMissileAvailable) {
            launchOrigin = MissileLaunchOrigin.AIRCRAFT;
            launchPosition = new Position(position.getX(), position.getY());
            aircraftMissileAvailable = false;
        } else {
            BaseLauncherPool.LaunchAllocation allocation = baseLauncherPool.tryAcquireLauncher();

            if (allocation == null) {
                logEvent("cannot launch base missile towards " + targetId
                        + " - no free base launcher or no base missiles remaining; "
                        + baseLauncherPool.getStatusSummary());
                return;
            }

            launchOrigin = MissileLaunchOrigin.COMMAND_CENTER_BASE;
            launchPosition = new Position(basePosition.getX(), basePosition.getY());
            launcherId = allocation.getLauncherId();

            logEvent("assigned base launcher L" + launcherId
                    + " for missile towards " + targetId
                    + "; launcherRemaining=" + allocation.getLauncherRemainingMissiles()
                    + ", totalRemaining=" + allocation.getTotalRemainingMissiles()
                    + "/" + allocation.getTotalCapacity());
        }

        String missileId = id + "-M" + System.currentTimeMillis();
        Integer finalLauncherId = launcherId;

        MissileWorker missile = new MissileWorker(
                missileId,
                side,
                id,
                targetId,
                launchOrigin,
                launchPosition,
                new Position(targetPosition.getX(), targetPosition.getY()),
                radarService,
                MISSILE_RADAR_RANGE,
                commandCenterWriter,
                () -> {
                    if (finalLauncherId != null) {
                        baseLauncherPool.releaseLauncher(finalLauncherId);
                        logEvent("released base launcher L" + finalLauncherId
                                + "; " + baseLauncherPool.getStatusSummary());
                    }
                }
        );

        activeMissiles.add(missile);
        new Thread(missile, "Missile-" + missileId).start();

        logEvent("launched missile " + missileId
                + " towards " + targetId
                + " from " + formatPosition(launchPosition)
                + ", origin=" + launchOrigin
                + (launcherId != null ? ", launcher=L" + launcherId : "")
                + ", lastKnownTarget=" + formatPosition(targetPosition));
    }

    private MissileLaunchOrigin resolveLaunchOrigin() {
        if (aircraftMissileAvailable) {
            return MissileLaunchOrigin.AIRCRAFT;
        }
        return MissileLaunchOrigin.COMMAND_CENTER_BASE;
    }

    private void changeState(AircraftState newState, String reason) {
        if (newState == state) return;

        AircraftState old = this.state;
        this.state = newState;

        if (LOG_AIRCRAFT_STATE_CHANGES) {
            System.out.printf("[%s | %s] state %s -> %s (%s)%n",
                    id, aircraftType, old, newState, reason);
        }
    }

    private void handleDestroyed() {
        running = false;
    }

    public synchronized void setReturnToBase(boolean returnToBase) {
        if (!returnToBase || state == AircraftState.DESTROYED) {
            return;
        }
        changeState(AircraftState.RETURNING, "RETURN_TO_BASE command");
    }

    public synchronized void setPatrolArea(double minX, double maxX, double minY, double maxY) {
        if (state == AircraftState.DESTROYED) {
            return;
        }

        this.patrolMinX = Math.min(clampToBoard(minX), clampToBoard(maxX));
        this.patrolMaxX = Math.max(clampToBoard(minX), clampToBoard(maxX));
        this.patrolMinY = Math.min(clampToBoard(minY), clampToBoard(maxY));
        this.patrolMaxY = Math.max(clampToBoard(minY), clampToBoard(maxY));

        double patrolStep = aircraftType.getPatrolStep();

        if (this.patrolMinX == this.patrolMaxX) {
            this.patrolMaxX = clampToBoard(this.patrolMaxX + patrolStep);
        }
        if (this.patrolMinY == this.patrolMaxY) {
            this.patrolMaxY = clampToBoard(this.patrolMaxY + patrolStep);
        }

        resetPatrolDirection();
        changeState(AircraftState.PATROLLING, String.format(
                "PATROL x[%.1f, %.1f], y[%.1f, %.1f]",
                patrolMinX, patrolMaxX, patrolMinY, patrolMaxY
        ));
        logEvent("patrolStep=" + aircraftType.getPatrolStep()
                + ", returnStep=" + aircraftType.getReturnStep());
    }

    public synchronized void markDestroyed() {
        changeState(AircraftState.DESTROYED, "marked destroyed by squadron");
    }

    private void patrolStep() {
        double patrolStep = aircraftType.getPatrolStep();

        double currentX = position.getX();
        double currentY = position.getY();

        boolean outsideX = currentX < patrolMinX || currentX > patrolMaxX;
        boolean outsideY = currentY < patrolMinY || currentY > patrolMaxY;

        if (outsideX || outsideY) {
            double targetX = clamp(currentX, patrolMinX, patrolMaxX);
            double targetY = clamp(currentY, patrolMinY, patrolMaxY);

            double dx = targetX - currentX;
            double dy = targetY - currentY;

            double moveX = clamp(dx, -patrolStep, patrolStep);
            double moveY = clamp(dy, -patrolStep, patrolStep);

            double newX = clampToBoard(currentX + moveX);
            double newY = clampToBoard(currentY + moveY);

            position = new Position(roundToOneDecimal(newX), roundToOneDecimal(newY));
            return;
        }

        double nextX = currentX + stepX;
        double nextY = currentY + stepY;

        if (nextX < patrolMinX || nextX > patrolMaxX) {
            stepX = (stepX > 0) ? -patrolStep : patrolStep;
            nextX = currentX + stepX;
        }

        if (nextY < patrolMinY || nextY > patrolMaxY) {
            stepY = (stepY > 0) ? -patrolStep : patrolStep;
            nextY = currentY + stepY;
        }

        nextX = clamp(nextX, patrolMinX, patrolMaxX);
        nextY = clamp(nextY, patrolMinY, patrolMaxY);

        position = new Position(roundToOneDecimal(nextX), roundToOneDecimal(nextY));
    }

    private void moveTowardsBase() {
        double dx = basePosition.getX() - position.getX();
        double dy = basePosition.getY() - position.getY();

        if (Math.abs(dx) < 0.05 && Math.abs(dy) < 0.05) {
            position = new Position(basePosition.getX(), basePosition.getY());
            return;
        }

        double returnStep = aircraftType.getReturnStep();

        double stepToBaseX = clamp(dx, -returnStep, returnStep);
        double stepToBaseY = clamp(dy, -returnStep, returnStep);

        double newX = clampToBoard(position.getX() + stepToBaseX);
        double newY = clampToBoard(position.getY() + stepToBaseY);

        position = new Position(roundToOneDecimal(newX), roundToOneDecimal(newY));
    }

    public synchronized void destroyFromCommandCenter() {
        if (state == AircraftState.DESTROYED) {
            return;
        }
        changeState(AircraftState.DESTROYED, "DESTROY command from enemy Command Center");
    }

    private boolean isAtBase() {
        return Math.abs(position.getX() - basePosition.getX()) < 0.05
                && Math.abs(position.getY() - basePosition.getY()) < 0.05;
    }

    private boolean hasPatrolArea() {
        return patrolMinX >= 0.0 && patrolMaxX >= 0.0
                && patrolMinY >= 0.0 && patrolMaxY >= 0.0
                && patrolMinX < patrolMaxX
                && patrolMinY < patrolMaxY;
    }

    private void clearPatrolArea() {
        patrolMinX = -1.0;
        patrolMaxX = -1.0;
        patrolMinY = -1.0;
        patrolMaxY = -1.0;
    }

    private void resetPatrolDirection() {
        double patrolStep = aircraftType.getPatrolStep();

        double currentX = position.getX();
        double currentY = position.getY();

        if (currentX < patrolMinX) {
            this.stepX = patrolStep;
        } else if (currentX > patrolMaxX) {
            this.stepX = -patrolStep;
        } else {
            this.stepX = (stepX >= 0) ? patrolStep : -patrolStep;
        }

        if (currentY < patrolMinY) {
            this.stepY = patrolStep;
        } else if (currentY > patrolMaxY) {
            this.stepY = -patrolStep;
        } else {
            this.stepY = (stepY >= 0) ? patrolStep : -patrolStep;
        }
    }

    private void sendReport(boolean logMovement) throws Exception {
        RadarUpdateRequest request = new RadarUpdateRequest(
                aircraftType,
                id,
                FlyingObjectType.AIRCRAFT,
                position,
                radarRange,
                side
        );

        RadarScanResponse response = radarService.updateAndScan(request);

        AircraftReportMessage report = new AircraftReportMessage(
                id,
                position,
                response.getVisibleObjects()
        );

        SquadroonOutboundMessage outbound = new SquadroonOutboundMessage(
                SquadroonOutboundMessage.MessageType.AIRCRAFT_REPORT,
                report,
                null
        );

        String json = objectMapper.writeValueAsString(outbound);
        commandCenterWriter.println(json);
        commandCenterWriter.flush();

        // Finalni Squadron logovi:
        logCellChangeIfNeeded();

//        if (LOG_AIRCRAFT_ATTACKS && !response.getVisibleObjectIds().isEmpty()) {
//            System.out.printf("[%s | %s] radar contacts=%s at [%s]%n",
//                    id,
//                    aircraftType,
//                    response.getVisibleObjectIds(),
//                    BoardUtils.positionToCell(position));
//        }
    }

    private void logCellChangeIfNeeded() {
        if (!LOG_AIRCRAFT_CELL_CHANGES) return;

        String newCell = BoardUtils.positionToCell(position);
        String oldCell = this.lastKnownCell;

        if (oldCell == null) {
            System.out.printf("[%s | %s] spawned at [%s] state=%s%n",
                    id, aircraftType, newCell, state);
        } else if (!newCell.equals(oldCell)) {
            System.out.printf("[%s | %s] moved %s -> %s state=%s%n",
                    id, aircraftType, oldCell, newCell, state);
        }

        this.lastKnownCell = newCell;
    }

    private void logEvent(String msg) {
        if (!LOG_AIRCRAFT_DEBUG) return;
        System.out.printf("[%s | %s | %s] %s%n", id, aircraftType, state, msg);
    }

    private double clampToBoard(double value) {
        return Math.max(MIN_COORD, Math.min(MAX_COORD, value));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public void stop() {
        running = false;
    }

    private String formatPosition(Position p) {
        return String.format("(%.1f, %.1f)", p.getX(), p.getY());
    }
}