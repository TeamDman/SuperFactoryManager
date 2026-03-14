package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.SFMPerformanceTweaks;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// Having this logic inside ResourceIdentifier.java causes classloading issues lol
public class RegexCache {
    private static final Map<String, Predicate<String>> patternCache = new Object2ObjectOpenHashMap<>();

    static {
        // we want to make common match-all patterns fast
        // resource names are lowercase alphanumeric with underscores
        String[] matchAny = new String[]{
                ".",
                "[a-z0-9/._-]",
                };
        String[] suffixes = new String[]{"+", "*"};
        for (String s : matchAny) {
            for (String suffix : suffixes) {
                patternCache.put(s + suffix, s1 -> true);
                patternCache.put("^" + s + suffix, s1 -> true);
                patternCache.put("^" + s + suffix + "$", s1 -> true);
                patternCache.put(s + suffix + "$", s1 -> true);
            }
        }
    }

    public static Predicate<String> buildPredicate(String possiblePattern) {
        if (SFMPerformanceTweaks.REGEX_CACHE_ENABLED) {
            return isRegexPattern(possiblePattern)
                   ? patternCache.computeIfAbsent(possiblePattern, RegexCache::getPredicateFromRegex)
                   : possiblePattern::equalsIgnoreCase;
        } else {
            return isRegexPattern(possiblePattern)
                   ? Pattern.compile(possiblePattern).asMatchPredicate()
                   : possiblePattern::equalsIgnoreCase;
        }
    }

    /// Optimized version of Pattern.compile(x).asMatchPredicate()
    ///
    /// Special cases for common patterns
    private static Predicate<String> getPredicateFromRegex(String x) {
        if (!SFMPerformanceTweaks.REGEX_PREDICATE_OPTIMIZATION) {
            return Pattern.compile(x).asMatchPredicate();
        }
        if (x.startsWith(".*") && x.endsWith(".*")) {
            String substring = x.substring(2, x.length() - 2);
            if (!isRegexPattern(substring)) {
                String finalSubstring = substring.toLowerCase(Locale.ROOT);
                return s -> s.contains(finalSubstring);
            }
        } else if (x.startsWith(".*")) {
            String suffix = x.substring(2);
            if (!isRegexPattern(suffix)) {
                String finalSuffix = suffix.toLowerCase(Locale.ROOT);
                return s -> s.endsWith(finalSuffix);
            }
        } else if (x.endsWith(".*")) {
            String prefix = x.substring(0, x.length() - 2);
            if (!isRegexPattern(prefix)) {
                String finalPrefix = prefix.toLowerCase(Locale.ROOT);
                return s -> s.startsWith(finalPrefix);
            }
        } else if (x.contains(".*")) {
            String[] parts = x.split("\\.\\*");
            if (parts.length == 2 && !isRegexPattern(parts[0]) && !isRegexPattern(parts[1])) {
                String begin = parts[0].toLowerCase(Locale.ROOT);
                String end = parts[1].toLowerCase(Locale.ROOT);
                return s -> s.startsWith(begin) && s.endsWith(end);
            }
        }
        // Default case for other regex patterns
        return Pattern.compile(x).asMatchPredicate();
    }


    public static boolean isRegexPattern(String pattern) {
        String specialChars = ".?*+^$[](){}|\\";
        for (int i = 0; i < pattern.length(); i++) {
            if (specialChars.indexOf(pattern.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
