package org.example.squadroon.missile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.config.MissileConfig;
import org.example.common.dto.MissileReportMessage;
import org.example.common.dto.RadarContact;
import org.example.common.dto.RadarScanResponse;
import org.example.common.dto.RadarUpdateRequest;
import org.example.common.dto.SquadroonOutboundMessage;
import org.example.common.enums.FlyingObjectType;
import org.example.common.enums.MissileLaunchOrigin;
import org.example.common.enums.MissileStatus;
import org.example.common.enums.Side;
import org.example.common.model.Position;
import org.example.radar.service.RadarService;

import java.io.PrintWriter;
import java.util.List;

public class MissileWorker implements Runnable {

    private final String id;
    private final Side side;
    private final String sourceAircraftId;
    private final String targetId;
    private final MissileLaunchOrigin launchOrigin;

    private volatile Position position;
    private volatile Position targetPosition;

    private final RadarService radarService;
    private final double missileRadarRange;

    private final PrintWriter ccWriter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Runnable onFinished;

    private volatile boolean running = true;

    public MissileWorker(String id,
                         Side side,
                         String sourceAircraftId,
                         String targetId,
                         MissileLaunchOrigin launchOrigin,
                         Position initialPosition,
                         Position initialTargetPosition,
                         RadarService radarService,
                         double missileRadarRange,
                         PrintWriter ccWriter,
                         Runnable onFinished) {
        this.id = id;
        this.side = side;
        this.sourceAircraftId = sourceAircraftId;
        this.targetId = targetId;
        this.launchOrigin = launchOrigin;
        this.position = new Position(initialPosition.getX(), initialPosition.getY());
        this.targetPosition = new Position(initialTargetPosition.getX(), initialTargetPosition.getY());
        this.radarService = radarService;
        this.missileRadarRange = missileRadarRange;
        this.ccWriter = ccWriter;
        this.onFinished = onFinished;
    }

    @Override
    public void run() {
        int stepCount = 0;

        try {
            log("launched towards " + targetId
                    + " from " + fmt(position)
                    + ", origin=" + launchOrigin
                    + ", sourceAircraft=" + sourceAircraftId
                    + ", lastKnownTarget=" + fmt(targetPosition));

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    moveTowardsTarget();
                    stepCount++;

                    if (canConfirmHit()) {
                        sendReport(MissileStatus.HIT);
                        log("HIT " + targetId + " at " + fmt(position));
                        running = false;
                        break;
                    }

                    if (reachedLastKnownPosition()) {
                        sendReport(MissileStatus.SELF_DESTRUCTED);
                        log("SELF_DESTRUCTED near " + targetId
                                + " at " + fmt(position)
                                + ", lastKnownTarget=" + fmt(targetPosition)
                                + " (target not found in missile radar range)");
                        running = false;
                        break;
                    }

                    if (stepCount >= MissileConfig.MAX_STEPS) {
                        sendReport(MissileStatus.SELF_DESTRUCTED);
                        log("SELF_DESTRUCTED near " + targetId
                                + " at " + fmt(position)
                                + ", lastKnownTarget=" + fmt(targetPosition)
                                + ", steps=" + stepCount);
                        running = false;
                        break;
                    }

                    sendReport(MissileStatus.TRACKING);
                    Thread.sleep(1200);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                } catch (Exception e) {
                    log("error: " + e.getMessage());
                    running = false;
                }
            }

            log("finished");
        } finally {
            tryUnregisterFromRadar();

            if (onFinished != null) {
                try {
                    onFinished.run();
                } catch (Exception e) {
                    log("onFinished error: " + e.getMessage());
                }
            }
        }
    }

    public void updateTargetPosition(Position newPos) {
        this.targetPosition = new Position(newPos.getX(), newPos.getY());
    }

    private void moveTowardsTarget() {
        double dx = targetPosition.getX() - position.getX();
        double dy = targetPosition.getY() - position.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < 1e-6) {
            return;
        }

        double step = MissileConfig.STEP;

        if (distance <= step) {
            position = new Position(targetPosition.getX(), targetPosition.getY());
        } else {
            double ratio = step / distance;
            double newX = position.getX() + dx * ratio;
            double newY = position.getY() + dy * ratio;
            position = new Position(round1(newX), round1(newY));
        }
    }

    private boolean canConfirmHit() throws Exception {
        RadarScanResponse response = radarService.updateAndScan(
                new RadarUpdateRequest(
                        null,
                        id,
                        FlyingObjectType.MISSILE,
                        position,
                        missileRadarRange,
                        side
                )
        );

        List<RadarContact> visibleObjects = response.getVisibleObjects();

        return visibleObjects.stream()
                .filter(contact -> contact.getObjectType() == FlyingObjectType.AIRCRAFT)
                .anyMatch(contact -> targetId.equalsIgnoreCase(contact.getId()));
    }

    private boolean reachedLastKnownPosition() {
        double dx = targetPosition.getX() - position.getX();
        double dy = targetPosition.getY() - position.getY();
        double d2 = dx * dx + dy * dy;
        double r2 = MissileConfig.HIT_DISTANCE * MissileConfig.HIT_DISTANCE;
        return d2 <= r2;
    }

    private void tryUnregisterFromRadar() {
        try {
            radarService.unregister(id);
        } catch (Exception e) {
            log("radar unregister failed: " + e.getMessage());
        }
    }

    private void sendReport(MissileStatus status) throws Exception {
        MissileReportMessage missileReport = new MissileReportMessage(
                id,
                targetId,
                sourceAircraftId,
                launchOrigin,
                new Position(position.getX(), position.getY()),
                new Position(targetPosition.getX(), targetPosition.getY()),
                side,
                status
        );

        SquadroonOutboundMessage outbound = new SquadroonOutboundMessage(
                SquadroonOutboundMessage.MessageType.MISSILE_REPORT,
                null,
                missileReport
        );

        String json = objectMapper.writeValueAsString(outbound);
        ccWriter.println(json);
        ccWriter.flush();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private void log(String msg) {
        System.out.printf("[MISSILE %s | %s] %s%n", id, side, msg);
    }

    private String fmt(Position p) {
        return String.format("(%.1f, %.1f)", p.getX(), p.getY());
    }
}