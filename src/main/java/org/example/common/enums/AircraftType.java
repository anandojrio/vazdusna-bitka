package org.example.common.enums;

public enum AircraftType {
    MIG31(0.1, 0.2, 1600, 3.0),
    F22  (0.1, 0.2, 1900, 2.5),
    F15  (0.1, 0.2, 2300, 2.0),
    SU30 (0.1, 0.2, 2700, 2.5);

    private final double patrolStep;
    private final double returnStep;
    private final int maxPauseMs;
    private final double radarRange;

    AircraftType(double patrolStep, double returnStep, int maxPauseMs, double radarRange) {
        this.patrolStep = patrolStep;
        this.returnStep = returnStep;
        this.maxPauseMs = maxPauseMs;
        this.radarRange = radarRange;
    }

    public double getPatrolStep() {
        return patrolStep;
    }

    public double getReturnStep() {
        return returnStep;
    }

    public int getMaxPauseMs() {
        return maxPauseMs;
    }

    public double getRadarRange() {
        return radarRange;
    }
}