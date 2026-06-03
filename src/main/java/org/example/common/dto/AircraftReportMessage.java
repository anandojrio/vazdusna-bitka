package org.example.common.dto;

import org.example.common.model.Position;

import java.io.Serializable;
import java.util.List;

public class AircraftReportMessage implements Serializable {

    private String aircraftId;
    private Position position;
    private List<RadarContact> radarContacts;

    public AircraftReportMessage() {
    }

    public AircraftReportMessage(String aircraftId, Position position, List<RadarContact> radarContacts) {
        this.aircraftId = aircraftId;
        this.position = position;
        this.radarContacts = radarContacts;
    }

    public String getAircraftId() {
        return aircraftId;
    }

    public Position getPosition() {
        return position;
    }

    public List<RadarContact> getRadarContacts() {
        return radarContacts;
    }

    public void setAircraftId(String aircraftId) {
        this.aircraftId = aircraftId;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void setRadarContacts(List<RadarContact> radarContacts) {
        this.radarContacts = radarContacts;
    }
}