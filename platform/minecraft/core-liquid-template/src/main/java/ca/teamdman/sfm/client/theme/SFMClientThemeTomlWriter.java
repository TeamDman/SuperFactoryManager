package ca.teamdman.sfm.client.theme;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRuleCodec;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression;
{% endif %}
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
        theme.fileIcons().entrySet().stream().filter(entry -> theme.explicitFileIcons().contains(entry.getKey()))
{% else %}
        theme.fileIcons().entrySet().stream()
{% endif %}
                .sorted(Map.Entry.comparingByKey()).forEach(entry ->
                appendIcon(out, quoted(entry.getKey()), entry.getValue()));
{% else %}
        theme.fileIcons().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                out.append(quoted(entry.getKey())).append(" = ")
                        .append(quoted(entry.getValue().requestedItem().toString())).append('\n'));
{% endcase %}
        out.append("\n[icons.actions]\n");
        theme.actionIcons().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                .forEach(entry -> appendIcon(out, quoted(entry.getKey().toString()), entry.getValue()));
{% if features.theme_preview_rules %}
        if (!theme.previewRules().isEmpty()) out.append('\n').append(SFMItemstackPreviewRuleCodec.write(theme.previewRules()));
{% endif %}
{% else %}
                .forEach(entry -> out.append(quoted(entry.getKey().toString())).append(" = ")
                        .append(quoted(entry.getValue().requestedItem().toString())).append('\n'));
{% endcase %}
        return out.toString();
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void appendIcon(StringBuilder out, String key, SFMItemIcon icon) {
        out.append(key).append(" = { item = ")
                .append(quoted(icon.requestedItem().toString()))
                .append(", fallback = ")
                .append(quoted(icon.fallbackItem().toString()))
                .append(", label = ")
                .append(quoted(icon.accessibleLabel()))
                .append(" }\n");
    }

{% endcase %}
    private static String hex(int argb) { return String.format("#%08X", argb); }

    private static String quoted(String value) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
        return SFMItemstackPreviewExpression.quote(value);
{% else %}
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
{% endif %}
{% else %}
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
{% endcase %}
    }
}
