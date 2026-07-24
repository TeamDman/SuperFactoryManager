package ca.teamdman.sfm.client.theme;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The authoritative typed theme loader.
 *
 * <p>NightConfig owns TOML interpretation and typed snapshot construction. The repository's
 * ANTLR TOML grammar remains an editor-only syntax/diagnostic surface and must not independently
 * decide runtime theme meaning.</p>
 */
public final class SFMClientThemeLoader {
    private SFMClientThemeLoader() {
    }

    public static SFMThemeLoadResult load(String toml, SFMClientTheme defaults) {
        List<String> diagnostics = new ArrayList<>();
        Config root;
        try {
            root = TomlFormat.instance().createParser().parse(toml);
        } catch (RuntimeException e) {
            return new SFMThemeLoadResult(Optional.empty(), List.of("Malformed TOML: " + e.getMessage()));
        }

        Object schema = root.get("schema_version");
        if (!(schema instanceof Number number) || number.intValue() != 1) {
            diagnostics.add("schema_version must be 1");
        }

        EnumMap<SFMColourRole, Integer> colours = new EnumMap<>(SFMColourRole.class);
        colours.putAll(defaults.colours());
        Config colourConfig = configAt(root, "colours", diagnostics);
        if (colourConfig != null) {
            for (Map.Entry<String, Object> entry : colourConfig.valueMap().entrySet()) {
                Optional<SFMColourRole> role = SFMColourRole.byId(entry.getKey());
                if (role.isEmpty()) {
                    diagnostics.add("Unknown colour role: " + entry.getKey());
                    continue;
                }
                parseColour(entry.getValue(), "colours.\"" + entry.getKey() + "\"", diagnostics)
                        .ifPresent(value -> colours.put(role.get(), value));
            }
        }

        Map<String, SFMSyntaxStyle> syntax = new LinkedHashMap<>(defaults.sfmlSyntax());
        Config syntaxConfig = configAt(root, "syntax.sfml", diagnostics);
        if (syntaxConfig != null) {
            for (Map.Entry<String, Object> entry : syntaxConfig.valueMap().entrySet()) {
                String tokenId = entry.getKey().toLowerCase(Locale.ROOT);
                if (!defaults.sfmlSyntax().containsKey(tokenId)) {
                    diagnostics.add("Unknown SFML syntax token id: " + entry.getKey());
                    continue;
                }
                if (!(entry.getValue() instanceof Config styleConfig)) {
                    diagnostics.add("syntax.sfml." + entry.getKey() + " must be an inline table");
                    continue;
                }
                SFMSyntaxStyle inherited = syntax.get(tokenId);
                int colour = parseColour(styleConfig.get("colour"), "syntax.sfml." + tokenId + ".colour", diagnostics)
                        .orElse(inherited.colour());
                boolean bold = booleanValue(styleConfig, "bold", inherited.bold(), tokenId, diagnostics);
                boolean italic = booleanValue(styleConfig, "italic", inherited.italic(), tokenId, diagnostics);
                boolean underlined = booleanValue(styleConfig, "underlined", inherited.underlined(), tokenId, diagnostics);
                syntax.put(tokenId, new SFMSyntaxStyle(colour, bold, italic, underlined));
            }
        }

        Map<String, SFMItemIcon> fileIcons = new LinkedHashMap<>(defaults.fileIcons());
        Config fileIconConfig = configAt(root, "icons.files", diagnostics);
        if (fileIconConfig != null) {
            for (Map.Entry<String, Object> entry : fileIconConfig.valueMap().entrySet()) {
                parseIcon(entry.getValue(), "icons.files." + entry.getKey(), entry.getKey(), diagnostics)
                        .ifPresent(icon -> fileIcons.put(entry.getKey().toLowerCase(Locale.ROOT), icon));
            }
        }

        Map<ResourceLocation, SFMItemIcon> actionIcons = new LinkedHashMap<>(defaults.actionIcons());
        Config actionIconConfig = configAt(root, "icons.actions", diagnostics);
        if (actionIconConfig != null) {
            for (Map.Entry<String, Object> entry : actionIconConfig.valueMap().entrySet()) {
                try {
                    ResourceLocation actionId = new ResourceLocation(entry.getKey());
                    parseIcon(entry.getValue(), "icons.actions.\"" + entry.getKey() + "\"", entry.getKey(), diagnostics)
                            .ifPresent(icon -> actionIcons.put(actionId, icon));
                } catch (RuntimeException e) {
                    diagnostics.add("Invalid action id: " + entry.getKey());
                }
            }
        }

        if (!diagnostics.isEmpty()) return new SFMThemeLoadResult(Optional.empty(), diagnostics);
        return new SFMThemeLoadResult(Optional.of(new SFMClientTheme(colours, syntax, fileIcons, actionIcons)), List.of());
    }

    private static Config configAt(Config root, String path, List<String> diagnostics) {
        Object value = root.get(path);
        if (value == null) return null;
        if (value instanceof Config config) return config;
        diagnostics.add(path + " must be a table");
        return null;
    }

    private static Optional<Integer> parseColour(Object value, String path, List<String> diagnostics) {
        if (value == null) return Optional.empty();
        if (!(value instanceof String text)) {
            diagnostics.add(path + " must be a named colour or #RRGGBB/#AARRGGBB string");
            return Optional.empty();
        }
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        if (normalized.matches("#[0-9a-f]{6}")) {
            return Optional.of((int) (0xFF000000L | Long.parseLong(normalized.substring(1), 16)));
        }
        if (normalized.matches("#[0-9a-f]{8}")) {
            return Optional.of((int) Long.parseLong(normalized.substring(1), 16));
        }
        ChatFormatting formatting = ChatFormatting.getByName(normalized);
        if (formatting != null && formatting.getColor() != null) {
            return Optional.of(0xFF000000 | formatting.getColor());
        }
        diagnostics.add(path + " has invalid colour: " + text);
        return Optional.empty();
    }

    private static boolean booleanValue(
            Config config,
            String key,
            boolean fallback,
            String tokenId,
            List<String> diagnostics
    ) {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        diagnostics.add("syntax.sfml." + tokenId + "." + key + " must be true or false");
        return fallback;
    }

    private static Optional<SFMItemIcon> parseIcon(
            Object value,
            String path,
            String label,
            List<String> diagnostics
    ) {
        if (!(value instanceof String text)) {
            diagnostics.add(path + " must be an item registry id string");
            return Optional.empty();
        }
        try {
            return Optional.of(new SFMItemIcon(new ResourceLocation(text), SFMItemIcon.PAPER, label));
        } catch (RuntimeException e) {
            diagnostics.add(path + " has invalid item id: " + text);
            return Optional.empty();
        }
    }
}
