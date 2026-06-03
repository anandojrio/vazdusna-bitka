package org.example.common.dto;

import java.io.Serializable;

public class SquadroonOutboundMessage implements Serializable {

    public enum MessageType {
        AIRCRAFT_REPORT,
        MISSILE_REPORT
    }

    private MessageType messageType;
    private AircraftReportMessage aircraftReport;
    private MissileReportMessage missileReport;

    public SquadroonOutboundMessage() {
    }

    public SquadroonOutboundMessage(MessageType messageType,
                                   AircraftReportMessage aircraftReport,
                                   MissileReportMessage missileReport) {
        this.messageType = messageType;
        this.aircraftReport = aircraftReport;
        this.missileReport = missileReport;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public AircraftReportMessage getAircraftReport() {
        return aircraftReport;
    }

    public MissileReportMessage getMissileReport() {
        return missileReport;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public void setAircraftReport(AircraftReportMessage aircraftReport) {
        this.aircraftReport = aircraftReport;
    }

    public void setMissileReport(MissileReportMessage missileReport) {
        this.missileReport = missileReport;
    }
}