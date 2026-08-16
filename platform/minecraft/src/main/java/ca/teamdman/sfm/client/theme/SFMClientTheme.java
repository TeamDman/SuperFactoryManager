package ca.teamdman.sfm.client.theme;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fully resolved immutable theme snapshot; rendering never reads configuration files. */
public record SFMClientTheme(
        Map<SFMColourRole, Integer> colours,
        Map<String, SFMSyntaxStyle> sfmlSyntax,
        Map<String, SFMItemIcon> fileIcons,
        Map<ResourceLocation, SFMItemIcon> actionIcons
) {
    public static final String DEFAULT_SYNTAX = "default";

    public SFMClientTheme {
        colours = Map.copyOf(colours);
        sfmlSyntax = Map.copyOf(sfmlSyntax);
        fileIcons = Map.copyOf(fileIcons);
        actionIcons = Map.copyOf(actionIcons);
        for (SFMColourRole role : SFMColourRole.values()) {
            if (!colours.containsKey(role)) throw new IllegalArgumentException("Missing colour role " + role.id());
        }
        if (!sfmlSyntax.containsKey(DEFAULT_SYNTAX)) throw new IllegalArgumentException("Missing default syntax style");
    }

    public int colour(SFMColourRole role) { return colours.get(role); }
    public SFMSyntaxStyle syntax(String tokenId) { return sfmlSyntax.getOrDefault(tokenId, sfmlSyntax.get(DEFAULT_SYNTAX)); }

    public SFMItemIcon fileIcon(String key) {
        return fileIcons.getOrDefault(key, fileIcons.get("unknown"));
    }

    public SFMItemIcon actionIcon(ResourceLocation actionId, SFMItemIcon fallback) {
        return actionIcons.getOrDefault(actionId, Objects.requireNonNull(fallback));
    }

    public static SFMClientTheme defaults() {
        EnumMap<SFMColourRole, Integer> colours = new EnumMap<>(SFMColourRole.class);
        for (SFMColourRole role : SFMColourRole.values()) colours.put(role, role.defaultArgb());

        Map<String, SFMSyntaxStyle> syntax = new LinkedHashMap<>();
        syntax.put(DEFAULT_SYNTAX, style(0xFFFFFFFF));
        syntax.put("direction", style(0xFFAA00AA));
        syntax.put("comment", new SFMSyntaxStyle(0xFFAAAAAA, false, true, false));
        syntax.put("io", style(0xFFFF55FF));
        syntax.put("keyword", new SFMSyntaxStyle(0xFF5555FF, true, false, false));
        syntax.put("string", style(0xFF55FF55));
        syntax.put("modifier", style(0xFFFFAA00));
        syntax.put("number", style(0xFF55FFFF));
        syntax.put("redstone", style(0xFFFF5555));
        syntax.put("round_robin", style(0xFFFFFF55));

        Map<String, SFMItemIcon> files = new LinkedHashMap<>();
        files.put("directory", icon("minecraft:chest", "directory"));
        files.put("unknown", icon("minecraft:paper", "unknown file"));
        files.put("extensionless", icon("minecraft:name_tag", "file without extension"));
        files.put(".sfml", icon("sfm:disk", "SFM program"));
        files.put(".java", icon("minecraft:cocoa_beans", "Java source"));
        files.put(".json", icon("minecraft:map", "JSON document"));
        files.put(".toml", icon("minecraft:comparator", "TOML configuration"));
        return new SFMClientTheme(colours, syntax, files, Map.of());
    }

    private static SFMSyntaxStyle style(int colour) {
        return new SFMSyntaxStyle(colour, false, false, false);
    }

    private static SFMItemIcon icon(String id, String label) {
        return new SFMItemIcon(new ResourceLocation(id), SFMItemIcon.PAPER, label);
    }
}
