package ca.teamdman.sfm.common.util;

import java.util.Arrays;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public class StringUtil {


    /**
     * Ponyfill for Java 12's String.indent(int n).
     * Adds or removes indentation from each line.
     */
    public static String indentPonyfill(String s, int n) {
        String[] lines = s.split("\\r?\\n|\\r", -1);

        if (n == 0) {
            return s + "\n";
        }

        final String indentString = (n > 0)
                ? Stream.generate(() -> " ").limit(n).collect(Collectors.joining())
                : ""; // If outdenting, we don't need a string prefix.

        return Stream.of(lines)
                .map(line -> {
                    if (line.isEmpty()) {
                        return "";
                    }

                    if (n > 0) {
                        return indentString + line;
                    } else {
                        int spacesToRemove = -n;
                        int actualSpaces = 0;

                        while (actualSpaces < line.length() && actualSpaces < spacesToRemove && line.charAt(actualSpaces) == ' ') {
                            actualSpaces++;
                        }

                        return line.substring(actualSpaces);
                    }
                })
                .collect(Collectors.joining("\n"))
                + "\n";
    }
}
