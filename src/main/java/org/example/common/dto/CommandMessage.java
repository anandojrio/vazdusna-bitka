package org.example.common.dto;

import java.io.Serializable;

public class CommandMessage implements Serializable {

    public enum CommandType {
        PATROL,
        RETURN_TO_BASE,
        ATTACK,
        DESTROY
    }

    private CommandType type;
    private String aircraftId;

    // ciljni avion za ATTACK komandu
    private String targetId;
    private double targetX;
    private double targetY;

    // PRAVOUGAONI BOX ZA PATROL
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;

    public CommandMessage() {
    }

    public CommandMessage(CommandType type, String aircraftId) {
        this.type = type;
        this.aircraftId = aircraftId;
    }

    public CommandMessage(CommandMessage.CommandType type,
                          String aircraftId,
                          double minX,
                          double maxX,
                          double minY,
                          double maxY) {
        this.type = type;
        this.aircraftId = aircraftId;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    public CommandType getType() {
        return type;
    }

    public String getAircraftId() {
        return aircraftId;
    }

    public String getTargetId() {
        return targetId;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public void setType(CommandType type) {
        this.type = type;
    }

    public void setAircraftId(String aircraftId) {
        this.aircraftId = aircraftId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void setMinX(double minX) {
        this.minX = minX;
    }

    public void setMaxX(double maxX) {
        this.maxX = maxX;
    }

    public void setMinY(double minY) {
        this.minY = minY;
    }

    public void setMaxY(double maxY) {
        this.maxY = maxY;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public void setTargetX(double targetX) {
        this.targetX = targetX;
    }

    public void setTargetY(double targetY) {
        this.targetY = targetY;
    }
}