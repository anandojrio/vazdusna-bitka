package org.example.common.dto;

import org.example.common.enums.FlyingObjectType;
import org.example.common.enums.Side;
import org.example.common.model.Position;

import java.io.Serializable;

public class RadarContact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private FlyingObjectType objectType;
    private Side side;
    private Position position;

    public RadarContact() {
    }

    public RadarContact(String id, FlyingObjectType objectType, Side side, Position position) {
        this.id = id;
        this.objectType = objectType;
        this.side = side;
        this.position = position;
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
