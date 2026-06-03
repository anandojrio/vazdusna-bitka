package org.example.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.example.common.enums.MissileLaunchOrigin;
import org.example.common.enums.MissileStatus;
import org.example.common.enums.Side;
import org.example.common.model.Position;

import java.io.Serializable;

public class MissileReportMessage implements Serializable {

    private String missileId;
    private String targetId;
    private String sourceAircraftId;
    private MissileLaunchOrigin launchOrigin;
    private Position position;
    private Position lastKnownTargetPosition;
    private Side side;
    private MissileStatus status;

    public MissileReportMessage() {
    }

    public MissileReportMessage(String missileId,
                                String targetId,
                                String sourceAircraftId,
                                MissileLaunchOrigin launchOrigin,
                                Position position,
                                Position lastKnownTargetPosition,
                                Side side,
                                MissileStatus status) {
        this.missileId = missileId;
        this.targetId = targetId;
        this.sourceAircraftId = sourceAircraftId;
        this.launchOrigin = launchOrigin;
        this.position = position;
        this.lastKnownTargetPosition = lastKnownTargetPosition;
        this.side = side;
        this.status = status;
    }

    public String getMissileId() {
        return missileId;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getSourceAircraftId() {
        return sourceAircraftId;
    }

    public MissileLaunchOrigin getLaunchOrigin() {
        return launchOrigin;
    }

    public Position getPosition() {
        return position;
    }

    public Position getLastKnownTargetPosition() {
        return lastKnownTargetPosition;
    }

    public Side getSide() {
        return side;
    }

    public MissileStatus getStatus() {
        return status;
    }

    @JsonIgnore
    public boolean isHit() {
        return status == MissileStatus.HIT;
    }

    @JsonIgnore
    public boolean isSelfDestructed() {
        return status == MissileStatus.SELF_DESTRUCTED;
    }

    @JsonIgnore
    public boolean isTracking() {
        return status == MissileStatus.TRACKING;
    }
}