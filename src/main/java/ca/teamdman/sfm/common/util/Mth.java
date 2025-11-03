package ca.teamdman.sfm.common.util;

public class Mth {
    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
    public static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }
}
