package org.example.common.dto;

import java.io.Serializable;

public class KillNotification implements Serializable {

    private String aircraftId; // koga treba ubiti (ID na strani primaoca)

    public KillNotification() {
    }

    public KillNotification(String aircraftId) {
        this.aircraftId = aircraftId;
    }

    public String getAircraftId() {
        return aircraftId;
    }

    public void setAircraftId(String aircraftId) {
        this.aircraftId = aircraftId;
    }
}