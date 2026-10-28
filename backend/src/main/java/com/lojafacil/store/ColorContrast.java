package com.lojafacil.store;

import java.util.regex.Pattern;

public final class ColorContrast {
    public static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    public static final double MIN_RATIO = 4.5;
    public static final String LIGHT_TEXT = "#FFFFFF";
    public static final String DARK_TEXT = "#111827";

    private ColorContrast() {
    }

    public static double ratio(String hexA, String hexB) {
        double la = luminance(hexA);
        double lb = luminance(hexB);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    public static String bestTextOn(String background) {
        return ratio(background, LIGHT_TEXT) >= ratio(background, DARK_TEXT) ? LIGHT_TEXT : DARK_TEXT;
    }

    public static boolean isReadable(String background) {
        return ratio(background, bestTextOn(background)) >= MIN_RATIO;
    }

    static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = channel((rgb >> 16) & 0xFF);
        double g = channel((rgb >> 8) & 0xFF);
        double b = channel(rgb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
