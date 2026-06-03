package org.example.common.dto;

import org.example.common.enums.AircraftType;
import org.example.common.enums.FlyingObjectType;
import org.example.common.enums.Side;
import org.example.common.model.Position;

import java.io.Serializable;

public class RadarUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    private Side side;
    private FlyingObjectType objectType;
    private AircraftType aircraftType;
    private Position position;
    private double radarRange;

    public RadarUpdateRequest(AircraftType aircraftType, String id, FlyingObjectType objectType, Position position, double radarRange, Side side) {
        this.aircraftType = aircraftType;
        this.id = id;
        this.objectType = objectType;
        this.position = position;
        this.radarRange = radarRange;
        this.side = side;
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

    public double getRadarRange() {
        return radarRange;
    }

    public void setRadarRange(double radarRange) {
        this.radarRange = radarRange;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }
}
