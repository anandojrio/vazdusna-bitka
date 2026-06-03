package org.example.common.util;

import org.example.common.model.Position;

import java.awt.*;

public class DistanceUtils {
    public DistanceUtils() {
    }

    public static double euclideanDistance(Position p1, Position p2) {
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
