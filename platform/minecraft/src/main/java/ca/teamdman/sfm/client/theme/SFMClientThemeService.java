package ca.teamdman.sfm.client.theme;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Owns atomic replacement of the immutable active theme snapshot. */
public final class SFMClientThemeService {
    public static final String DEFAULT_TOML = """
            schema_version = 1

            [icons.files]
            directory = "minecraft:chest"
            unknown = "minecraft:paper"
            extensionless = "minecraft:name_tag"
            ".sfml" = "sfm:disk"
            ".java" = "minecraft:book"
            ".json" = "minecraft:map"
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

    private SFMClientThemeService() {
    }

    public static SFMClientTheme active() { return ACTIVE.get(); }
    public static List<String> diagnostics() { return DIAGNOSTICS.get(); }

    public static Path activeThemePath() {
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

    public static SFMThemeLoadResult reload(Path path) {
        try {
            return reloadText(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return reject(List.of("Could not read theme file " + path + ": " + e.getMessage()));
        }
    }

    public static SFMThemeLoadResult reloadText(String toml) {
        SFMThemeLoadResult result = SFMClientThemeLoader.load(toml, DEFAULT_THEME);
        if (result.valid()) {
            ACTIVE.set(result.theme().orElseThrow());
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
            ACTIVE.set(DEFAULT_THEME);
            DIAGNOSTICS.set(List.of());
            return new SFMThemeLoadResult(java.util.Optional.of(DEFAULT_THEME), List.of());
        } catch (IOException e) {
            return reject(List.of("Could not restore default theme at " + path + ": " + e.getMessage()));
        }
    }

    public static void resetForTests() {
        ACTIVE.set(DEFAULT_THEME);
        DIAGNOSTICS.set(List.of());
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
