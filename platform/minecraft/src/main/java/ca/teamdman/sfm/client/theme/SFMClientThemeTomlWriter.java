package ca.teamdman.sfm.client.theme;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRuleCodec;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression;

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
        theme.fileIcons().entrySet().stream().filter(entry -> theme.explicitFileIcons().contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey()).forEach(entry ->
                appendIcon(out, quoted(entry.getKey()), entry.getValue()));
        out.append("\n[icons.actions]\n");
        theme.actionIcons().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> appendIcon(out, quoted(entry.getKey().toString()), entry.getValue()));
        if (!theme.previewRules().isEmpty()) out.append('\n').append(SFMItemstackPreviewRuleCodec.write(theme.previewRules()));
        return out.toString();
    }

    private static void appendIcon(StringBuilder out, String key, SFMItemIcon icon) {
        out.append(key).append(" = { item = ")
                .append(quoted(icon.requestedItem().toString()))
                .append(", fallback = ")
                .append(quoted(icon.fallbackItem().toString()))
                .append(", label = ")
                .append(quoted(icon.accessibleLabel()))
                .append(" }\n");
    }

    private static String hex(int argb) { return String.format("#%08X", argb); }

    private static String quoted(String value) {
        return SFMItemstackPreviewExpression.quote(value);
    }
}
