package org.example.common.util;

public final class AnsiColors {     // za farbanje ispisa iz command centra

    public static final String RESET = "\u001B[0m";

    public static final String BLUE = "\u001B[34m";
    public static final String GREEN = "\u001B[32m";
    public static final String RED = "\u001B[31m";
    public static final String YELLOW = "\u001B[33m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    private AnsiColors() {
    }

    public static String blue(String s) {
        return BLUE + s + RESET;
    }

    public static String green(String s) {
        return GREEN + s + RESET;
    }

    public static String red(String s) {
        return RED + s + RESET;
    }

    public static String yellow(String s) {
        return YELLOW + s + RESET;
    }

    public static String cyan(String s) {
        return CYAN + s + RESET;
    }

    public static String white(String s) {
        return WHITE + s + RESET;
    }
}