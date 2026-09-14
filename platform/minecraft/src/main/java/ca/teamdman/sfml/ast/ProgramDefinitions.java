package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.value.SFMValuePattern;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable compile-time aliases used by one cached program. */
public record ProgramDefinitions(
        Map<String, SFMValuePattern> patterns,
        Map<String, String> players
) implements ASTNode {
    public static final ProgramDefinitions EMPTY = new ProgramDefinitions(Map.of(), Map.of());

    public ProgramDefinitions {
        patterns = normalizedCopy(patterns, "pattern");
        players = normalizedCopy(players, "player");
    }

    public Optional<SFMValuePattern> pattern(String name) {
        String normalized = normalize(name);
        return switch (normalized) {
            case "any" -> Optional.of(SFMValuePattern.ANY);
            case "string" -> Optional.of(SFMValuePattern.STRING);
            case "guid" -> Optional.of(SFMValuePattern.GUID);
            default -> Optional.ofNullable(patterns.get(normalized));
        };
    }

    public Optional<String> player(String name) {
        return Optional.ofNullable(players.get(normalize(name)));
    }

    public String toSource() {
        StringBuilder source = new StringBuilder();
        players.forEach((alias, player) -> source.append("LET ")
                .append(alias)
                .append(" BE PLAYER OF ")
                .append(player)
                .append('\n'));
        patterns.forEach((alias, pattern) -> source.append("LET ")
                .append(alias)
                .append(" BE LIKE ")
                .append(patternSource(pattern, true))
                .append('\n'));
        return source.toString();
    }

    private String patternSource(SFMValuePattern pattern, boolean allowObject) {
        if (pattern instanceof SFMValuePattern.AnyPattern) return "any";
        if (pattern instanceof SFMValuePattern.StringPattern) return "string";
        if (pattern instanceof SFMValuePattern.GuidPattern) return "guid";
        if (pattern instanceof SFMValuePattern.LiteralPattern literal
            && literal.expected() instanceof ca.teamdman.sfm.common.value.SFMValue.StringValue stringValue) {
            return quote(stringValue.value());
        }
        if (allowObject && pattern instanceof SFMValuePattern.ObjectPattern objectPattern) {
            return "object with " + objectPattern.fields().entrySet().stream()
                    .map(field -> "field " + field.getKey() + fieldPatternSource(field.getValue()))
                    .collect(java.util.stream.Collectors.joining(" and "));
        }
        return patterns.entrySet().stream()
                .filter(entry -> entry.getValue().equals(pattern))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Pattern cannot be represented as SFML: " + pattern));
    }

    private String fieldPatternSource(SFMValuePattern pattern) {
        if (pattern instanceof SFMValuePattern.LiteralPattern literal
            && literal.expected() instanceof ca.teamdman.sfm.common.value.SFMValue.StringValue stringValue) {
            return " of " + quote(stringValue.value());
        }
        return " like " + patternSource(pattern, false);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static <T> Map<String, T> normalizedCopy(
            Map<String, T> source,
            String kind
    ) {
        Objects.requireNonNull(source);
        LinkedHashMap<String, T> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            String normalized = normalize(name);
            if (copy.putIfAbsent(normalized, Objects.requireNonNull(value)) != null) {
                throw new IllegalArgumentException("Duplicate " + kind + " alias: " + name);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static String normalize(String name) {
        return Objects.requireNonNull(name).toLowerCase(Locale.ROOT);
    }
}
