package ca.teamdman.sfml.ast;

import java.util.regex.Pattern;

/**
 * Converts an unquoted SFML literal glob into a regex without treating literal punctuation as regex syntax.
 *
 * <p>The current SFML grammar only routes grammar-legal identifier components through this helper. Review and
 * filter consumers may use the wider resource-location punctuation contract directly. Quoted SFML resources are
 * explicit regexes and intentionally bypass this conversion.</p>
 */
public final class SFMLLiteralGlob {
    private static final String REGEX_META = ".?+^$[](){}|\\";

    private SFMLLiteralGlob() {
    }

    public static String toRegex(String glob) {
        boolean conversionRequired = glob.indexOf('*') >= 0;
        for (int i = 0; i < glob.length() && !conversionRequired; i++) {
            conversionRequired = REGEX_META.indexOf(glob.charAt(i)) >= 0;
        }
        if (!conversionRequired) return glob;

        StringBuilder regex = new StringBuilder();
        int literalStart = 0;
        for (int i = 0; i < glob.length(); i++) {
            if (glob.charAt(i) != '*') continue;
            appendLiteral(regex, glob, literalStart, i);
            regex.append(".*");
            literalStart = i + 1;
        }
        appendLiteral(regex, glob, literalStart, glob.length());
        return regex.toString();
    }

    private static void appendLiteral(StringBuilder regex, String glob, int start, int end) {
        if (start < end) regex.append(Pattern.quote(glob.substring(start, end)));
    }
}
