package org.example.common.model;

import org.example.common.enums.AircraftType;
import org.example.common.enums.FlyingObjectType;
import org.example.common.enums.Side;

public class AirObjectState {
    private String id;
    private FlyingObjectType objectType;
    private AircraftType aircraftType;
    public Side side;
    public Position position;
    private boolean active;

    public AirObjectState(boolean active, AircraftType aircraftType, String id, FlyingObjectType objectType, Position position, Side side) {
        this.active = active;
        this.aircraftType = aircraftType;
        this.id = id;
        this.objectType = objectType;
        this.position = position;
        this.side = side;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public AircraftType getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(AircraftType aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public FlyingObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(FlyingObjectType objectType) {
        this.objectType = objectType;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }
}
