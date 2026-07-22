package ca.teamdman.sfm.client.screen.color;

import java.util.Locale;

/** Immutable typed ARGB colour independent of any preference or theme role. */
public record SFMArgbColor(int argb) {
    public enum HexOrder { ARGB, RGBA }

    public int alpha() { return argb >>> 24 & 0xFF; }
    public int red() { return argb >>> 16 & 0xFF; }
    public int green() { return argb >>> 8 & 0xFF; }
    public int blue() { return argb & 0xFF; }

    public SFMArgbColor withAlpha(int value) { return of(value, red(), green(), blue()); }
    public SFMArgbColor withRed(int value) { return of(alpha(), value, green(), blue()); }
    public SFMArgbColor withGreen(int value) { return of(alpha(), red(), value, blue()); }
    public SFMArgbColor withBlue(int value) { return of(alpha(), red(), green(), value); }

    public String toHex(HexOrder order) {
        return order == HexOrder.ARGB
                ? String.format(Locale.ROOT, "#%02X%02X%02X%02X", alpha(), red(), green(), blue())
                : String.format(Locale.ROOT, "#%02X%02X%02X%02X", red(), green(), blue(), alpha());
    }

    public static SFMArgbColor parseHex(String text, HexOrder order) {
        String digits = text.strip();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() != 6 && digits.length() != 8) {
            throw new IllegalArgumentException("Expected 6 or 8 hexadecimal digits");
        }
        final long parsed;
        try {
            parsed = Long.parseUnsignedLong(digits, 16);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Colour contains a non-hexadecimal character", exception);
        }
        if (digits.length() == 6) return new SFMArgbColor(0xFF000000 | (int) parsed);
        if (order == HexOrder.ARGB) return new SFMArgbColor((int) parsed);
        return new SFMArgbColor((int) ((parsed & 0xFF) << 24 | parsed >>> 8));
    }

    public Hsv toHsv() {
        double r = red() / 255D;
        double g = green() / 255D;
        double b = blue() / 255D;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double delta = max - min;
        double hue;
        if (delta == 0D) hue = 0D;
        else if (max == r) hue = ((g - b) / delta) % 6D;
        else if (max == g) hue = (b - r) / delta + 2D;
        else hue = (r - g) / delta + 4D;
        hue = ((hue / 6D) + 1D) % 1D;
        return new Hsv(hue, max == 0D ? 0D : delta / max, max);
    }

    public static SFMArgbColor fromHsv(int alpha, double hue, double saturation, double value) {
        hue = normalized(hue);
        saturation = unit(saturation);
        value = unit(value);
        double scaled = hue * 6D;
        int sector = (int) Math.floor(scaled);
        double fraction = scaled - sector;
        double p = value * (1D - saturation);
        double q = value * (1D - fraction * saturation);
        double t = value * (1D - (1D - fraction) * saturation);
        double r;
        double g;
        double b;
        switch (sector % 6) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return of(alpha, channel(r), channel(g), channel(b));
    }

    public static SFMArgbColor of(int alpha, int red, int green, int blue) {
        return new SFMArgbColor(clamp(alpha) << 24 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue));
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private static int channel(double value) { return clamp((int) Math.round(value * 255D)); }
    private static double unit(double value) { return Math.max(0D, Math.min(1D, value)); }
    private static double normalized(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("HSV component must be finite");
        return ((value % 1D) + 1D) % 1D;
    }

    public record Hsv(double hue, double saturation, double value) {
    }
}
