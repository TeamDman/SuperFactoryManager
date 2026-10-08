package ca.teamdman.sfm.client.theme;

import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
import ca.teamdman.sfm.client.theme.preview.*;
import com.electronwill.nightconfig.toml.TomlFormat;
{% endif %}
{% endcase %}
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
import java.util.Optional;
{% endif %}
{% endcase %}
import java.util.concurrent.atomic.AtomicReference;

/** Owns atomic replacement of the immutable active theme snapshot. */
public final class SFMClientThemeService {
    public static final String DEFAULT_TOML = """
            schema_version = 1

            [icons.files]
{% if features.theme_file_icon_defaults %}
            directory = { item = "minecraft:chest", fallback = "minecraft:barrel", label = "directory" }
{% else %}
            directory = "minecraft:chest"
{% endif %}
            unknown = "minecraft:paper"
{% if features.theme_file_icon_defaults %}
            extensionless = "minecraft:paper"
{% else %}
            extensionless = "minecraft:name_tag"
{% endif %}
            ".sfml" = "sfm:disk"
{% if features.theme_file_icon_defaults %}
            ".java" = "minecraft:cocoa_beans"
            ".sfm-review.json" = "minecraft:bell"
            ".json" = "minecraft:written_book"
{% else %}
            ".java" = "minecraft:book"
            ".json" = "minecraft:map"
{% endif %}
            ".toml" = "minecraft:comparator"

            [icons.actions]
            "sfm:palette/open" = "minecraft:compass"

            [syntax.sfml]
            keyword = { colour = "blue", bold = true }
            string = { colour = "green" }
            number = { colour = "aqua" }
            comment = { colour = "gray", italic = true }

            [colours]
            "panel.background" = "#F0202020"
            "panel.border" = "#FF707070"
            "panel.selection" = "#FF404040"
            "text.primary" = "#FFFFFFFF"
            "text.muted" = "#FFB0B0B0"
            "text.error" = "#FFFF5555"
            "text.accent" = "#FF55FFFF"
            "timeline.background" = "#EE11151A"
            "timeline.track" = "#FF4A5159"
            "timeline.keyframe" = "#FF55FFFF"
            "timeline.time" = "#FFFFAA33"
            "timeline.marker" = "#FFB8C0C8"
            """;

    private static final SFMClientTheme DEFAULT_THEME = SFMClientTheme.defaults();
    private static final AtomicReference<SFMClientTheme> ACTIVE = new AtomicReference<>(DEFAULT_THEME);
    private static final AtomicReference<List<String>> DIAGNOSTICS = new AtomicReference<>(List.of());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
    private record Authority(SFMItemstackPreviewThemeTarget target,String text) {}
    private static volatile Authority authority;
{% endif %}
    public static final int MAX_THEME_BYTES=1024*1024;
{% endcase %}

    private SFMClientThemeService() {
    }

    public static SFMClientTheme active() { return ACTIVE.get(); }
    public static List<String> diagnostics() { return DIAGNOSTICS.get(); }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}

    public static Optional<SFMItemstackPreviewThemeTarget> activeAuthority() {
        Authority captured=authority;
        return captured==null ? Optional.empty() : Optional.of(captured.target());
    }
{% endif %}
{% endcase %}

    public static Path activeThemePath() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
        Authority captured=authority;
        if (captured!=null) return captured.target().path();
{% endif %}
{% endcase %}
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("sfm-client-theme.toml");
    }

    public static SFMThemeLoadResult reload() {
        Path path = activeThemePath();
        try {
            if (Files.notExists(path)) writeAtomically(path, DEFAULT_TOML);
            return reload(path);
        } catch (IOException e) {
            return reject(List.of("Could not prepare theme file " + path + ": " + e.getMessage()));
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static synchronized SFMThemeLoadResult reload(Path path) {
{% else %}
    public static SFMThemeLoadResult reload(Path path) {
{% endcase %}
        try {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            Path actual=path.toRealPath();
            String text=readBounded(actual);
            SFMThemeLoadResult result=reloadText(text);
{% if features.theme_preview_rules %}
            if (result.valid()) authority=new Authority(new SFMItemstackPreviewThemeTarget(actual,SFMItemstackPreviewRules.sha256(text)),text);
{% endif %}
            return result;
{% else %}
            return reloadText(Files.readString(path, StandardCharsets.UTF_8));
{% endcase %}
        } catch (IOException e) {
            return reject(List.of("Could not read theme file " + path + ": " + e.getMessage()));
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static synchronized SFMThemeLoadResult reloadText(String toml) {
        if (toml.getBytes(StandardCharsets.UTF_8).length>MAX_THEME_BYTES) return reject(List.of("Theme exceeds 1 MiB limit"));
{% else %}
    public static SFMThemeLoadResult reloadText(String toml) {
{% endcase %}
        SFMThemeLoadResult result = SFMClientThemeLoader.load(toml, DEFAULT_THEME);
        if (result.valid()) {
            ACTIVE.set(result.theme().orElseThrow());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
            authority=null;
{% endif %}
{% endcase %}
            DIAGNOSTICS.set(List.of());
        } else {
            DIAGNOSTICS.set(result.diagnostics());
        }
        return result;
    }

    public static SFMThemeLoadResult restoreDefaults() {
        Path path = activeThemePath();
        try {
            writeAtomically(path, DEFAULT_TOML);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            return reload(path);
{% else %}
            ACTIVE.set(DEFAULT_THEME);
            DIAGNOSTICS.set(List.of());
            return new SFMThemeLoadResult(java.util.Optional.of(DEFAULT_THEME), List.of());
{% endcase %}
        } catch (IOException e) {
            return reject(List.of("Could not restore default theme at " + path + ": " + e.getMessage()));
        }
    }

    public static SFMThemeLoadResult save(SFMClientTheme theme) {
        return save(activeThemePath(), theme);
    }

    public static SFMThemeLoadResult save(Path path, SFMClientTheme theme) {
        return saveText(path, SFMClientThemeTomlWriter.write(theme));
    }

    /** Validates before writing, then atomically installs the exact parsed snapshot. */
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static synchronized SFMThemeLoadResult saveText(Path path, String toml) {
        return saveText(path,toml,SFMClientThemeService::writeAtomically);
    }
    @FunctionalInterface interface ThemeWriter { void write(Path path,String text) throws IOException; }
    private static SFMThemeLoadResult saveText(Path path,String toml,ThemeWriter writer) {
        if (toml.getBytes(StandardCharsets.UTF_8).length>MAX_THEME_BYTES) return reject(List.of("Theme exceeds 1 MiB limit"));
{% else %}
    public static SFMThemeLoadResult saveText(Path path, String toml) {
{% endcase %}
        SFMThemeLoadResult candidate = SFMClientThemeLoader.load(toml, DEFAULT_THEME);
        if (!candidate.valid()) return reject(candidate.diagnostics());
        try {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            Path resolved=Files.exists(path) ? path.toRealPath() : path.toAbsolutePath().normalize();
{% if features.theme_preview_rules %}
            var nextAuthority=new Authority(new SFMItemstackPreviewThemeTarget(resolved,SFMItemstackPreviewRules.sha256(toml)),toml);
{% endif %}
            writer.write(path, toml);
{% else %}
            writeAtomically(path, toml);
{% endcase %}
            ACTIVE.set(candidate.theme().orElseThrow());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
            authority=nextAuthority;
{% endif %}
{% endcase %}
            DIAGNOSTICS.set(List.of());
            return candidate;
        } catch (IOException e) {
            return reject(List.of("Could not save theme file " + path + ": " + e.getMessage()));
        }
    }

    public static void resetForTests() {
        ACTIVE.set(DEFAULT_THEME);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
        authority=null;
{% endif %}
{% endcase %}
        DIAGNOSTICS.set(List.of());
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.theme_preview_rules %}
    /** Changes only the typed rule section; unrelated TOML values survive. Caller owns explicit confirmation. */
    public static synchronized SFMThemeLoadResult mutatePreviewRules(SFMItemstackPreviewThemeTarget target,
            java.util.function.UnaryOperator<List<SFMItemstackPreviewRules.Rule>> mutation,
            java.util.function.BooleanSupplier stillCurrent) {
        return mutatePreviewRules(target,mutation,stillCurrent,SFMClientThemeService::writeAtomically);
    }
    static synchronized SFMThemeLoadResult mutatePreviewRules(SFMItemstackPreviewThemeTarget target,
            java.util.function.UnaryOperator<List<SFMItemstackPreviewRules.Rule>> mutation,
            java.util.function.BooleanSupplier stillCurrent,ThemeWriter writer) {
        Authority captured=authority;
        if (captured==null || !captured.target().equals(target) || !stillCurrent.getAsBoolean())
            return reject(List.of("Theme authority or captured context changed; reopen the rule command"));
        try {
            if (Files.isSymbolicLink(target.path())) return reject(List.of("Theme authority became a symbolic link"));
            String current=readBounded(target.path());
            if (!SFMItemstackPreviewRules.sha256(current).equals(target.sha256()))
                return reject(List.of("Theme revision changed on disk; reload before editing rules"));
            var registry=SFMItemstackPreviewRegistry.snapshot();
            var root=TomlFormat.instance().createParser().parse(current);
            var before=SFMItemstackPreviewRuleCodec.read(root,registry.operators());
            var after=List.copyOf(mutation.apply(before));
            var section=TomlFormat.instance().createParser().parse(SFMItemstackPreviewRuleCodec.write(after));
            root.set(SFMItemstackPreviewRuleCodec.SECTION,section.get(SFMItemstackPreviewRuleCodec.SECTION));
            java.io.StringWriter serialized=new java.io.StringWriter();
            TomlFormat.instance().createWriter().write(root,serialized);
            String next=serialized.toString();
            // Catch in-process cancellation and changes made while the candidate was prepared.
            if (!stillCurrent.getAsBoolean() || authority!=captured || registry!=SFMItemstackPreviewRegistry.snapshot()
                    || !SFMItemstackPreviewRules.sha256(readBounded(target.path())).equals(target.sha256()))
                return reject(List.of("Theme changed or operation cancelled before commit"));
            return saveText(target.path(),next,writer);
        } catch (IOException | RuntimeException failure) {
            return reject(List.of("Could not save preview rule: "+failure.getMessage()));
        }
    }

{% endif %}
    private static String readBounded(Path path) throws IOException {
        try (var input=Files.newInputStream(path)) {
            byte[] bytes=input.readNBytes(MAX_THEME_BYTES+1);
            if (bytes.length>MAX_THEME_BYTES) throw new IOException("Theme exceeds 1 MiB limit");
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }

{% endcase %}
    private static SFMThemeLoadResult reject(List<String> diagnostics) {
        List<String> immutable = List.copyOf(diagnostics);
        DIAGNOSTICS.set(immutable);
        return new SFMThemeLoadResult(java.util.Optional.empty(), immutable);
    }

    private static void writeAtomically(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
