package ca.teamdman.sfm.client.presentation;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** Presentation-only summaries. Never use these strings as stored values or command arguments. */
public final class SFMTextSummary {
    private SFMTextSummary() { }

    public static String singleLine(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean space = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                space = result.length() > 0;
            } else {
                if (space) result.append(' ');
                result.appendCodePoint(codePoint);
                space = false;
            }
        }
        return result.toString();
    }

    public static String codePoints(String value, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("Summary budget must be positive");
        return value.codePointCount(0, value.length()) <= maximum ? value
                : value.substring(0, value.offsetByCodePoints(0, maximum - 1)) + "…";
    }

    public static String fitLine(String value, int width, ToIntFunction<String> measure) {
        Objects.requireNonNull(measure, "measure");
        String line = singleLine(value);
        if (width <= 0) return "";
        if (measure.applyAsInt(line) <= width) return line;
        if (measure.applyAsInt("…") > width) return "";
        int low = 0;
        int high = line.codePointCount(0, line.length());
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String candidate = line.substring(0, line.offsetByCodePoints(0, middle)) + "…";
            if (measure.applyAsInt(candidate) <= width) low = middle;
            else high = middle - 1;
        }
        return line.substring(0, line.offsetByCodePoints(0, low)) + "…";
    }
}
