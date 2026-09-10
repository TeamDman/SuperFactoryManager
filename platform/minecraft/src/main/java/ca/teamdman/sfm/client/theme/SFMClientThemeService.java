package ca.teamdman.sfm.client.theme;

import net.minecraft.client.Minecraft;
import ca.teamdman.sfm.client.theme.preview.*;
import com.electronwill.nightconfig.toml.TomlFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Owns atomic replacement of the immutable active theme snapshot. */
public final class SFMClientThemeService {
    public static final String DEFAULT_TOML = """
            schema_version = 1

            [icons.files]
            directory = { item = "minecraft:chest", fallback = "minecraft:barrel", label = "directory" }
            unknown = "minecraft:paper"
            extensionless = "minecraft:paper"
            ".sfml" = "sfm:disk"
            ".java" = "minecraft:cocoa_beans"
            ".sfm-review.json" = "minecraft:bell"
            ".json" = "minecraft:written_book"
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
    private record Authority(SFMItemstackPreviewThemeTarget target,String text) {}
    private static volatile Authority authority;
    public static final int MAX_THEME_BYTES=1024*1024;

    private SFMClientThemeService() {
    }

    public static SFMClientTheme active() { return ACTIVE.get(); }
    public static List<String> diagnostics() { return DIAGNOSTICS.get(); }
    public static Optional<SFMItemstackPreviewThemeTarget> activeAuthority() {
        Authority captured=authority;
        return captured==null ? Optional.empty() : Optional.of(captured.target());
    }

    public static Path activeThemePath() {
        Authority captured=authority;
        if (captured!=null) return captured.target().path();
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

    public static synchronized SFMThemeLoadResult reload(Path path) {
        try {
            Path actual=path.toRealPath();
            String text=readBounded(actual);
            SFMThemeLoadResult result=reloadText(text);
            if (result.valid()) authority=new Authority(new SFMItemstackPreviewThemeTarget(actual,SFMItemstackPreviewRules.sha256(text)),text);
            return result;
        } catch (IOException e) {
            return reject(List.of("Could not read theme file " + path + ": " + e.getMessage()));
        }
    }

    public static synchronized SFMThemeLoadResult reloadText(String toml) {
        if (toml.getBytes(StandardCharsets.UTF_8).length>MAX_THEME_BYTES) return reject(List.of("Theme exceeds 1 MiB limit"));
        SFMThemeLoadResult result = SFMClientThemeLoader.load(toml, DEFAULT_THEME);
        if (result.valid()) {
            ACTIVE.set(result.theme().orElseThrow());
            authority=null;
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
            return reload(path);
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
    public static synchronized SFMThemeLoadResult saveText(Path path, String toml) {
        return saveText(path,toml,SFMClientThemeService::writeAtomically);
    }
    @FunctionalInterface interface ThemeWriter { void write(Path path,String text) throws IOException; }
    private static SFMThemeLoadResult saveText(Path path,String toml,ThemeWriter writer) {
        if (toml.getBytes(StandardCharsets.UTF_8).length>MAX_THEME_BYTES) return reject(List.of("Theme exceeds 1 MiB limit"));
        SFMThemeLoadResult candidate = SFMClientThemeLoader.load(toml, DEFAULT_THEME);
        if (!candidate.valid()) return reject(candidate.diagnostics());
        try {
            Path resolved=Files.exists(path) ? path.toRealPath() : path.toAbsolutePath().normalize();
            var nextAuthority=new Authority(new SFMItemstackPreviewThemeTarget(resolved,SFMItemstackPreviewRules.sha256(toml)),toml);
            writer.write(path, toml);
            ACTIVE.set(candidate.theme().orElseThrow());
            authority=nextAuthority;
            DIAGNOSTICS.set(List.of());
            return candidate;
        } catch (IOException e) {
            return reject(List.of("Could not save theme file " + path + ": " + e.getMessage()));
        }
    }

    public static void resetForTests() {
        ACTIVE.set(DEFAULT_THEME);
        authority=null;
        DIAGNOSTICS.set(List.of());
    }

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

    private static String readBounded(Path path) throws IOException {
        try (var input=Files.newInputStream(path)) {
            byte[] bytes=input.readNBytes(MAX_THEME_BYTES+1);
            if (bytes.length>MAX_THEME_BYTES) throw new IOException("Theme exceeds 1 MiB limit");
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
    }

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
