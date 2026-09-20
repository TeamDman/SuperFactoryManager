package ca.teamdman.sfm.client.theme;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMClientThemeTests {
    @TempDir Path temporaryDirectory;
    @AfterEach
    public void resetTheme() {
        SFMClientThemeService.resetForTests();
    }

    @Test
    public void sparseOverridesInheritUnspecifiedDefaults() {
        SFMThemeLoadResult result = SFMClientThemeLoader.load("""
                schema_version = 1
                [colours]
                "panel.background" = "#FF123456"
                [syntax.sfml]
                keyword = { colour = "gold" }
                """, SFMClientTheme.defaults());

        assertTrue(result.valid(), result.diagnostics().toString());
        SFMClientTheme theme = result.theme().orElseThrow();
        assertEquals(0xFF123456, theme.colour(SFMColourRole.PANEL_BACKGROUND));
        assertEquals(SFMColourRole.TEXT_PRIMARY.defaultArgb(), theme.colour(SFMColourRole.TEXT_PRIMARY));
        assertEquals(0xFFFFAA00, theme.syntax("keyword").colour());
        assertTrue(theme.syntax("keyword").bold());
    }

    @Test
    public void invalidValuesProduceDiagnosticsWithoutSnapshot() {
        SFMThemeLoadResult result = SFMClientThemeLoader.load("""
                schema_version = 2
                [colours]
                "not.a.role" = "#FFFFFFFF"
                "panel.background" = "orange-ish"
                [icons.files]
                ".java" = "not an id"
                """, SFMClientTheme.defaults());

        assertFalse(result.valid());
        assertTrue(result.theme().isEmpty());
        assertTrue(result.diagnostics().size() >= 4, result.diagnostics().toString());
    }

    @Test
    public void malformedReloadRetainsLastValidSnapshot() {
        SFMThemeLoadResult valid = SFMClientThemeService.reloadText("""
                schema_version = 1
                [colours]
                "panel.background" = "#FF112233"
                """);
        SFMClientTheme accepted = SFMClientThemeService.active();
        SFMThemeLoadResult invalid = SFMClientThemeService.reloadText("schema_version = [");

        assertTrue(valid.valid());
        assertFalse(invalid.valid());
        assertEquals(accepted, SFMClientThemeService.active());
        assertEquals(0xFF112233, SFMClientThemeService.active().colour(SFMColourRole.PANEL_BACKGROUND));
        assertFalse(SFMClientThemeService.diagnostics().isEmpty());
    }

    @Test
    public void unknownTokenUsesResolvedDefaultStyle() {
        SFMClientTheme theme = SFMClientThemeLoader.load("""
                schema_version = 1
                [syntax.sfml]
                default = { colour = "#FF010203", italic = true }
                """, SFMClientTheme.defaults()).theme().orElseThrow();

        assertEquals(0xFF010203, theme.syntax("future_token").colour());
        assertTrue(theme.syntax("future_token").italic());
    }

    @Test
    public void unavailableRegistryItemUsesConfiguredFallbackThenPaper() {
        ResourceLocation missing = new ResourceLocation("example", "missing");
        ResourceLocation fallback = new ResourceLocation("minecraft", "book");
        SFMItemIcon icon = new SFMItemIcon(missing, fallback, "missing icon");

        assertEquals(fallback, SFMItemIconResolver.selectAvailableId(icon, fallback::equals));
        assertEquals(SFMItemIcon.PAPER, SFMItemIconResolver.selectAvailableId(icon, ignored -> false));
    }

    @Test
    public void scalarDirectoryOverrideRetainsTheTitleSafeContainerFallback() {
        SFMClientTheme theme = SFMClientThemeLoader.load("""
                schema_version = 1
                [icons.files]
                directory = "minecraft:chest"
                """, SFMClientTheme.defaults()).theme().orElseThrow();

        assertEquals(new ResourceLocation("minecraft", "chest"),
                theme.fileIcon("directory").requestedItem());
        assertEquals(SFMItemIcon.BARREL, theme.fileIcon("directory").fallbackItem());
    }

    @Test
    public void fullSnapshotWriterRoundTripsTypedThemeExactly() {
        SFMClientTheme original = SFMClientTheme.defaults();
        EnumMap<SFMColourRole,Integer> colours = new EnumMap<>(SFMColourRole.class);
        colours.putAll(original.colours());
        colours.put(SFMColourRole.PANEL_BACKGROUND, 0xFF345678);
        Map<String,SFMItemIcon> icons = new LinkedHashMap<>(original.fileIcons());
        icons.put(".sfml", SFMItemIcon.vanilla("chest", "SFM program"));
        SFMClientTheme edited = new SFMClientTheme(colours, original.sfmlSyntax(), icons,
                Map.of(new ResourceLocation("sfm:palette/open"), SFMItemIcon.vanilla("compass", "palette")));

        SFMThemeLoadResult result = SFMClientThemeLoader.load(SFMClientThemeTomlWriter.write(edited), original);
        assertTrue(result.valid(), result.diagnostics().toString());
        SFMClientTheme parsed = result.theme().orElseThrow();
        assertEquals(edited.colours(), parsed.colours());
        assertEquals(edited.sfmlSyntax(), parsed.sfmlSyntax());
        assertEquals(edited.fileIcons(), parsed.fileIcons());
        assertEquals(edited.actionIcons(), parsed.actionIcons());
        assertEquals("minecraft:chest", parsed.fileIcon(".sfml").requestedItem().toString());
        assertEquals("minecraft:compass", parsed.actionIcons().get(new ResourceLocation("sfm:palette/open")).requestedItem().toString());
    }

    @Test
    public void invalidSaveRetainsFileAndActiveSnapshot() throws Exception {
        Path path = temporaryDirectory.resolve("theme.toml");
        SFMClientTheme requested = SFMClientTheme.defaults();
        SFMThemeLoadResult saved = SFMClientThemeService.save(path, requested);
        assertTrue(saved.valid());
        SFMClientTheme accepted = saved.theme().orElseThrow();
        String before = Files.readString(path);

        SFMThemeLoadResult rejected = SFMClientThemeService.saveText(path, "schema_version = [");

        assertFalse(rejected.valid());
        assertEquals(before, Files.readString(path));
        assertEquals(accepted, SFMClientThemeService.active());
    }
}
