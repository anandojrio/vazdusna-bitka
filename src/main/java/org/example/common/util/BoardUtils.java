package org.example.common.util;

import org.example.common.model.PatrolBox;
import org.example.common.model.Position;

public final class BoardUtils {

    public static final double MIN_COORD = 0.0;
    public static final double MAX_COORD = 7.0;

    private BoardUtils() {
    }

    public static int colFromFile(char file) {
        char upper = Character.toUpperCase(file);
        if (upper < 'A' || upper > 'H') {
            throw new IllegalArgumentException("Column must be between A and H.");
        }
        return upper - 'A';
    }

    public static int rowFromRank(int rank) {
        if (rank < 1 || rank > 8) {
            throw new IllegalArgumentException("Row must be between 1 and 8.");
        }
        return rank - 1;
    }

    public static double clampToBoard(double value) {
        return Math.max(MIN_COORD, Math.min(MAX_COORD, value));
    }

    public static PatrolBox toPatrolBox(String fromCell, String toCell) {
        String from = normalizeCell(fromCell);
        String to = normalizeCell(toCell);

        int col1 = colFromFile(from.charAt(0));
        int row1 = rowFromRank(Integer.parseInt(from.substring(1)));

        int col2 = colFromFile(to.charAt(0));
        int row2 = rowFromRank(Integer.parseInt(to.substring(1)));

        int minCol = Math.min(col1, col2);
        int maxCol = Math.max(col1, col2);
        int minRow = Math.min(row1, row2);
        int maxRow = Math.max(row1, row2);

        double minX = clampToBoard(minCol);
        double maxX = clampToBoard(maxCol + 1.0);
        double minY = clampToBoard(minRow);
        double maxY = clampToBoard(maxRow + 1.0);

        return new PatrolBox(minX, maxX, minY, maxY);
    }

    public static String positionToCell(Position position) {
        int col = (int) Math.floor(clampToBoard(position.getX()));
        int row = (int) Math.floor(clampToBoard(position.getY()));

        char file = (char) ('A' + col);
        int rank = row + 1;

        return "" + file + rank;
    }

    private static String normalizeCell(String cell) {
        if (cell == null || cell.isBlank()) {
            throw new IllegalArgumentException("Cell must not be blank.");
        }

        String normalized = cell.trim().toUpperCase();

        if (!normalized.matches("^[A-H][1-8]$")) {
            throw new IllegalArgumentException("Invalid cell format: " + cell);
        }

        return normalized;
    }

    public static String[][] emptyBoardLabels() {
        String[][] board = new String[8][8];
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                char file = (char) ('A' + col);
                int rank = row + 1;
                board[row][col] = "" + file + rank;
            }
        }
        return board;
    }
}