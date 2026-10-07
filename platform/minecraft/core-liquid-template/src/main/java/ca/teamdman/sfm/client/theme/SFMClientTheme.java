package ca.teamdman.sfm.client.theme;

{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
{% if features.theme_preview_rules %}
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRules;
{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}

{% if features.theme_file_icon_matching %}
import java.util.ArrayList;
{% endif %}
{% if features.theme_file_icon_matching %}
import java.util.Comparator;
{% endif %}
import java.util.EnumMap;
import java.util.LinkedHashMap;
{% if features.theme_file_icon_matching %}
import java.util.Locale;
{% endif %}
import java.util.Map;
import java.util.Objects;
{% if features.theme_file_icon_matching %}
import java.util.Optional;
{% endif %}
{% if features.theme_file_icon_defaults or features.theme_preview_rules %}
import java.util.List;
{% endif %}
{% if features.theme_preview_rules %}
import java.util.Set;
{% endif %}

/** Fully resolved immutable theme snapshot; rendering never reads configuration files. */
public record SFMClientTheme(
        Map<SFMColourRole, Integer> colours,
        Map<String, SFMSyntaxStyle> sfmlSyntax,
        Map<String, SFMItemIcon> fileIcons,
{% if features.theme_preview_rules %}
        Map<ResourceLocation, SFMItemIcon> actionIcons,
        List<SFMItemstackPreviewRules.Rule> previewRules,
        Set<String> explicitFileIcons
{% else %}
{% case minecraft_version %}
{% when "26.1.2" %}
        Map<Identifier, SFMItemIcon> actionIcons
{% else %}
        Map<ResourceLocation, SFMItemIcon> actionIcons
{% endcase %}
{% endif %}
) {
    public static final String DEFAULT_SYNTAX = "default";

    public SFMClientTheme {
        colours = Map.copyOf(colours);
        sfmlSyntax = Map.copyOf(sfmlSyntax);
        fileIcons = Map.copyOf(fileIcons);
        actionIcons = Map.copyOf(actionIcons);
{% if features.theme_preview_rules %}
        previewRules = List.copyOf(previewRules);
        explicitFileIcons = Set.copyOf(explicitFileIcons);
        if (!fileIcons.keySet().containsAll(explicitFileIcons)) throw new IllegalArgumentException("Unknown explicit file icon key");
{% endif %}
        for (SFMColourRole role : SFMColourRole.values()) {
            if (!colours.containsKey(role)) throw new IllegalArgumentException("Missing colour role " + role.id());
        }
        if (!sfmlSyntax.containsKey(DEFAULT_SYNTAX)) throw new IllegalArgumentException("Missing default syntax style");
    }

{% if features.theme_preview_rules %}
    /** Callers supplying a map explicitly own those values; defaults use the provenance-aware constructor. */
    public SFMClientTheme(Map<SFMColourRole, Integer> colours, Map<String, SFMSyntaxStyle> syntax,
                          Map<String, SFMItemIcon> files, Map<ResourceLocation, SFMItemIcon> actions) {
        this(colours, syntax, files, actions, List.of(), files.keySet());
    }

    public SFMClientTheme withPreviewRules(List<SFMItemstackPreviewRules.Rule> rules) {
        return new SFMClientTheme(colours, sfmlSyntax, fileIcons, actionIcons, rules, explicitFileIcons);
    }

{% endif %}
    public int colour(SFMColourRole role) { return colours.get(role); }
    public SFMSyntaxStyle syntax(String tokenId) { return sfmlSyntax.getOrDefault(tokenId, sfmlSyntax.get(DEFAULT_SYNTAX)); }

    public SFMItemIcon fileIcon(String key) {
        return fileIcons.getOrDefault(key, fileIcons.get("unknown"));
    }

{% if features.theme_file_icon_matching %}
    /**
     * Resolves a file name through the theme's longest, case-insensitive suffix.
     * Compound suffixes therefore win over their shorter tails regardless of
     * map insertion order.
     */
    public Optional<SFMItemIcon> matchingFileIcon(String fileName) {
        String lowerName = Objects.requireNonNull(fileName, "fileName").toLowerCase(Locale.ROOT);
        ArrayList<Map.Entry<String, SFMItemIcon>> matches = new ArrayList<>();
        for (Map.Entry<String, SFMItemIcon> entry : fileIcons.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith(".") || key.length() == 1) continue;
            if (lowerName.endsWith(key) && lowerName.length() > key.length()) matches.add(entry);
        }
        matches.sort(Comparator
                .<Map.Entry<String, SFMItemIcon>>comparingInt(entry -> entry.getKey().length())
                .reversed()
                .thenComparing(Map.Entry::getKey));
        if (!matches.isEmpty()) return Optional.of(matches.get(0).getValue());
        return isExtensionless(lowerName)
                ? Optional.ofNullable(fileIcons.get("extensionless"))
                : Optional.empty();
    }

    public SFMItemIcon fileIconForName(String fileName) {
        return matchingFileIcon(fileName).orElseGet(() -> fileIcon("unknown"));
    }

{% endif %}
{% case minecraft_version %}
{% when "26.1.2" %}
    public SFMItemIcon actionIcon(Identifier actionId, SFMItemIcon fallback) {
{% else %}
    public SFMItemIcon actionIcon(ResourceLocation actionId, SFMItemIcon fallback) {
{% endcase %}
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
{% if features.theme_file_icon_defaults %}
        files.put("directory", icon("minecraft:chest", "minecraft:barrel", "directory"));
{% else %}
        files.put("directory", icon("minecraft:chest", "directory"));
{% endif %}
        files.put("unknown", icon("minecraft:paper", "unknown file"));
{% if features.theme_file_icon_defaults %}
        files.put("extensionless", icon("minecraft:paper", "file without extension"));
{% else %}
        files.put("extensionless", icon("minecraft:name_tag", "file without extension"));
{% endif %}
        files.put(".sfml", icon("sfm:disk", "SFM program"));
{% if features.theme_file_icon_defaults %}
        files.put(".java", icon("minecraft:cocoa_beans", "Java source"));
{% else %}
        files.put(".java", icon("minecraft:book", "Java source"));
{% endif %}
{% if features.theme_file_icon_defaults %}
        files.put(".sfm-review.json", icon("minecraft:bell", "SFM release review"));
{% endif %}
{% if features.theme_file_icon_defaults %}
        files.put(".json", icon("minecraft:written_book", "JSON document"));
{% else %}
        files.put(".json", icon("minecraft:map", "JSON document"));
{% endif %}
        files.put(".toml", icon("minecraft:comparator", "TOML configuration"));
{% if features.theme_file_icon_defaults %}
        files.put(".gradle", icon("minecraft:anvil", "Gradle build script"));
        files.put(".md", icon("minecraft:book", "Markdown document"));
        files.put(".markdown", icon("minecraft:book", "Markdown document"));
        files.put(".rs", icon("minecraft:iron_ingot", "Rust source"));
        files.put(".ps1", icon("minecraft:nautilus_shell", "PowerShell script"));
        files.put(".lua", icon("minecraft:ender_pearl", "Lua script"));
        files.put(".txt", icon("minecraft:paper", "Plain text"));
        files.put(".log", icon("minecraft:writable_book", "Log messages"));
        files.put(".json5", icon("minecraft:written_book", "JSON5 document"));
        for (String suffix : List.of(".ini", ".cfg", ".properties"))
            files.put(suffix, icon("minecraft:repeater", "Configuration"));
        for (String suffix : List.of(".gz", ".zip", ".jar"))
            files.put(suffix, icon("minecraft:bundle", "Archive"));
        files.put(".png", icon("minecraft:painting", "Image"));
        files.put(".dat", icon("minecraft:slime_ball", "Binary data"));
        files.put(".mca", icon("minecraft:grass_block", "Minecraft region"));
        for (String suffix : List.of(".dat_old", ".bak"))
            files.put(suffix, icon("minecraft:clock", "Backup data"));
        files.put(".lock", icon("minecraft:tripwire_hook", "Lock file"));
        files.put(".marker", icon("minecraft:redstone_torch", "State marker"));
        files.put(".v1", icon("minecraft:chiseled_stone_bricks", "Versioned data"));
        files.put(".class", icon("minecraft:nether_brick", "Compiled Java class"));
        for (String suffix : List.of(".o", ".obj"))
            files.put(suffix, icon("minecraft:brick", "Compiled object"));
        for (String suffix : List.of(".rlib", ".lib", ".a", ".dll", ".so", ".dylib"))
            files.put(suffix, icon("minecraft:netherite_ingot", "Compiled library"));
        files.put(".rmeta", icon("minecraft:knowledge_book", "Rust compilation metadata"));
        files.put(".timestamp", icon("minecraft:clock", "Build timestamp"));
        files.put(".d", icon("minecraft:string", "Build dependency information"));
        files.put(".pdb", icon("minecraft:spyglass", "Debug symbols"));
        files.put(".exe", icon("minecraft:command_block", "Executable program"));
        files.put(".bin", icon("minecraft:redstone", "Binary payload"));
        files.put(".ndjson", icon("minecraft:writable_book", "Structured event stream"));
        files.put(".patch", icon("minecraft:shears", "Source patch"));
        files.put(".diff", icon("minecraft:shears", "Source diff"));
        files.put(".map", icon("minecraft:compass", "Source map"));
        for (String suffix : List.of(".js", ".mjs", ".cjs"))
            files.put(suffix, icon("minecraft:glowstone_dust", "JavaScript source"));
        for (String suffix : List.of(".ts", ".tsx"))
            files.put(suffix, icon("minecraft:lapis_lazuli", "TypeScript source"));
        for (String suffix : List.of(".c", ".cpp", ".cc"))
            files.put(suffix, icon("minecraft:piston", "Native source"));
        for (String suffix : List.of(".h", ".hpp"))
            files.put(suffix, icon("minecraft:tripwire_hook", "Native interface header"));
        files.put(".html", icon("minecraft:oak_sign", "Web document"));
        files.put(".css", icon("minecraft:magenta_dye", "Web styling"));
        files.put(".svg", icon("minecraft:painting", "Vector image"));
        files.put(".g4", icon("minecraft:enchanted_book", "ANTLR grammar"));
        for (String suffix : List.of(".yaml", ".yml", ".xml"))
            files.put(suffix, icon("minecraft:repeater", "Structured configuration"));
        for (String suffix : List.of(".sh", ".bash"))
            files.put(suffix, icon("minecraft:nautilus_shell", "Shell script"));
        for (String suffix : List.of(".bat", ".cmd"))
            files.put(suffix, icon("minecraft:lever", "Windows command script"));
{% endif %}
{% if features.theme_preview_rules %}
        return new SFMClientTheme(colours, syntax, files, Map.of(), List.of(), Set.of());
{% else %}
        return new SFMClientTheme(colours, syntax, files, Map.of());
{% endif %}
    }

{% if features.theme_file_icon_matching %}
    private static boolean isExtensionless(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1;
    }

{% endif %}
    private static SFMSyntaxStyle style(int colour) {
        return new SFMSyntaxStyle(colour, false, false, false);
    }

    private static SFMItemIcon icon(String id, String label) {
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
        return new SFMItemIcon(SFMResourceLocation.parse(id), SFMItemIcon.PAPER, label);
{% else %}
        return new SFMItemIcon(new ResourceLocation(id), SFMItemIcon.PAPER, label);
{% endcase %}
    }
{% if features.theme_file_icon_defaults %}

    private static SFMItemIcon icon(String id, String fallbackId, String label) {
        return new SFMItemIcon(new ResourceLocation(id), new ResourceLocation(fallbackId), label);
    }
{% endif %}
}
