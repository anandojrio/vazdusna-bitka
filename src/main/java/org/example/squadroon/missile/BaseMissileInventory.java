package org.example.squadroon.missile;

import org.example.common.enums.Side;

public class BaseMissileInventory {

    public static final int LAUNCHERS_PER_SIDE = 3;
    public static final int MISSILES_PER_LAUNCHER = 5;
    public static final int TOTAL_BASE_MISSILES = LAUNCHERS_PER_SIDE * MISSILES_PER_LAUNCHER;

    private final Side side;
    private int remainingBaseMissiles = TOTAL_BASE_MISSILES;

    public BaseMissileInventory(Side side) {
        this.side = side;
    }

    public synchronized boolean tryConsumeBaseMissile() {
        if (remainingBaseMissiles <= 0) {
            return false;
        }
        remainingBaseMissiles--;
        return true;
    }

    public synchronized int getRemainingBaseMissiles() {
        return remainingBaseMissiles;
    }

    public int getTotalBaseMissiles() {
        return TOTAL_BASE_MISSILES;
    }

    public Side getSide() {
        return side;
    }
}