package org.example.squadroon.missile;

import org.example.common.enums.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BaseLauncherPool {

    public static final int LAUNCHER_COUNT = 3;
    public static final int MISSILES_PER_LAUNCHER = 5;

    private final Side side;
    private final List<LauncherState> launchers = new ArrayList<>();

    public BaseLauncherPool(Side side) {
        this.side = side;
        for (int i = 1; i <= LAUNCHER_COUNT; i++) {
            launchers.add(new LauncherState(i, MISSILES_PER_LAUNCHER));
        }
    }

    public synchronized LaunchAllocation tryAcquireLauncher() {
        List<LauncherState> candidates = new ArrayList<>();
        for (LauncherState launcher : launchers) {
            if (!launcher.active && launcher.remainingMissiles > 0) {
                candidates.add(launcher);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Collections.shuffle(candidates, ThreadLocalRandom.current());
        LauncherState selected = candidates.get(0);

        selected.active = true;
        selected.remainingMissiles--;

        return new LaunchAllocation(
                selected.launcherId,
                selected.remainingMissiles,
                getTotalRemainingMissilesInternal(),
                LAUNCHER_COUNT * MISSILES_PER_LAUNCHER
        );
    }

    public synchronized void releaseLauncher(int launcherId) {
        LauncherState launcher = findLauncher(launcherId);
        if (launcher != null) {
            launcher.active = false;
        }
    }

    public synchronized int getTotalRemainingMissiles() {
        return getTotalRemainingMissilesInternal();
    }

    public synchronized int getActiveLauncherCount() {
        int count = 0;
        for (LauncherState launcher : launchers) {
            if (launcher.active) {
                count++;
            }
        }
        return count;
    }

    public synchronized String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("side=").append(side)
                .append(", activeLaunchers=").append(getActiveLauncherCount())
                .append("/").append(LAUNCHER_COUNT)
                .append(", totalRemaining=").append(getTotalRemainingMissilesInternal())
                .append("/").append(LAUNCHER_COUNT * MISSILES_PER_LAUNCHER)
                .append(", launchers=[");

        for (int i = 0; i < launchers.size(); i++) {
            LauncherState l = launchers.get(i);
            if (i > 0) sb.append(", ");
            sb.append("L").append(l.launcherId)
                    .append(":remaining=").append(l.remainingMissiles)
                    .append(",active=").append(l.active);
        }

        sb.append("]");
        return sb.toString();
    }

    private LauncherState findLauncher(int launcherId) {
        for (LauncherState launcher : launchers) {
            if (launcher.launcherId == launcherId) {
                return launcher;
            }
        }
        return null;
    }

    private int getTotalRemainingMissilesInternal() {
        int total = 0;
        for (LauncherState launcher : launchers) {
            total += launcher.remainingMissiles;
        }
        return total;
    }

    private static class LauncherState {
        private final int launcherId;
        private int remainingMissiles;
        private boolean active;

        private LauncherState(int launcherId, int remainingMissiles) {
            this.launcherId = launcherId;
            this.remainingMissiles = remainingMissiles;
            this.active = false;
        }
    }

    public static class LaunchAllocation {
        private final int launcherId;
        private final int launcherRemainingMissiles;
        private final int totalRemainingMissiles;
        private final int totalCapacity;

        public LaunchAllocation(int launcherId,
                                int launcherRemainingMissiles,
                                int totalRemainingMissiles,
                                int totalCapacity) {
            this.launcherId = launcherId;
            this.launcherRemainingMissiles = launcherRemainingMissiles;
            this.totalRemainingMissiles = totalRemainingMissiles;
            this.totalCapacity = totalCapacity;
        }

        public int getLauncherId() {
            return launcherId;
        }

        public int getLauncherRemainingMissiles() {
            return launcherRemainingMissiles;
        }

        public int getTotalRemainingMissiles() {
            return totalRemainingMissiles;
        }

        public int getTotalCapacity() {
            return totalCapacity;
        }
    }
}