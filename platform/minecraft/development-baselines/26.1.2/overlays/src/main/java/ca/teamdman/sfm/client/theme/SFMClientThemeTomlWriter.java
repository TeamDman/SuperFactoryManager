package ca.teamdman.sfm.client.theme;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;

import java.util.Comparator;
import java.util.Map;

/** Deterministic full-snapshot TOML writer paired with {@link SFMClientThemeLoader}. */
public final class SFMClientThemeTomlWriter {
    private SFMClientThemeTomlWriter() {
    }

    public static String write(SFMClientTheme theme) {
        StringBuilder out = new StringBuilder("schema_version = 1\n\n[colours]\n");
        for (SFMColourRole role : SFMColourRole.values()) {
            out.append(quoted(role.id())).append(" = ").append(quoted(hex(theme.colour(role)))).append('\n');
        }
        out.append("\n[syntax.sfml]\n");
        theme.sfmlSyntax().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            SFMSyntaxStyle style = entry.getValue();
            out.append(quoted(entry.getKey())).append(" = { colour = ").append(quoted(hex(style.colour())))
                    .append(", bold = ").append(style.bold())
                    .append(", italic = ").append(style.italic())
                    .append(", underlined = ").append(style.underlined()).append(" }\n");
        });
        out.append("\n[icons.files]\n");
        theme.fileIcons().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                out.append(quoted(entry.getKey())).append(" = ")
                        .append(quoted(entry.getValue().requestedItem().toString())).append('\n'));
        out.append("\n[icons.actions]\n");
        theme.actionIcons().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> out.append(quoted(entry.getKey().toString())).append(" = ")
                        .append(quoted(entry.getValue().requestedItem().toString())).append('\n'));
        return out.toString();
    }

    private static String hex(int argb) { return String.format("#%08X", argb); }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
