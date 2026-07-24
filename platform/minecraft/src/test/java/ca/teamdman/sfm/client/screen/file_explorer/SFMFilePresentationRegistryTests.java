package ca.teamdman.sfm.client.screen.file_explorer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMFilePresentationRegistryTests {
    private final SFMFilePresentationRegistry registry = SFMFilePresentationRegistry.createDefault();

    @Test
    public void knownExtensionsAreMatchedCaseInsensitively() {
        assertEquals("SFM program", registry.presentationFor(file("Factory.SFML")).kindLabel());
        assertEquals("Java source", registry.presentationFor(file("Explorer.JAVA")).kindLabel());
    }

    @Test
    public void compoundExtensionsWinOverShorterSuffixes() {
        assertEquals("compressed archive", registry.presentationFor(file("sources.tar.gz")).kindLabel());
        assertEquals("gzip archive", registry.presentationFor(file("sources.gz")).kindLabel());
        assertEquals("minecraft:ender_chest", registry.presentationFor(file("sources.tar.gz")).itemIcon().requestedItem().toString());
        assertEquals("minecraft:barrel", registry.presentationFor(file("sources.gz")).itemIcon().requestedItem().toString());
    }

    @Test
    public void unknownAndAbsentExtensionsHaveDistinctTextualFallbacks() {
        assertEquals("unknown file", registry.presentationFor(file("notes.xyz")).kindLabel());
        assertEquals("file without extension", registry.presentationFor(file("README")).kindLabel());
    }

    @Test
    public void directoriesIgnoreDotsInTheirNames() {
        SFMFileExplorerEntry directory = SFMFileExplorerEntry.directory("config.d", "config.d", java.util.List.of());
        assertEquals("directory", registry.presentationFor(directory).kindLabel());
        assertEquals("minecraft:chest", registry.presentationFor(directory).itemIcon().requestedItem().toString());
    }

    @Test
    public void everyPresentationHasAccessibleTextAndVanillaFallback() {
        for (SFMFileExplorerEntry entry : java.util.List.of(
                file("Factory.sfml"), file("archive.tar.gz"), file("notes.xyz"), file("README"),
                SFMFileExplorerEntry.directory("src", "src", java.util.List.of())
        )) {
            SFMFilePresentation presentation = registry.presentationFor(entry);
            assertFalse(presentation.itemIcon().accessibleLabel().isBlank());
            assertEquals("minecraft:paper", presentation.itemIcon().fallbackItem().toString());
            assertEquals(presentation.kindLabel(), presentation.itemIcon().accessibleLabel());
        }
    }

    @Test
    public void fileRowsCanFitTheFixedSixteenPixelItemIcon() {
        assertEquals(16, ca.teamdman.sfm.client.presentation.SFMItemIconRenderer.SIZE);
        assertTrue(SFMFileExplorerPanel.ROW_HEIGHT >= ca.teamdman.sfm.client.presentation.SFMItemIconRenderer.SIZE);
    }

    private static SFMFileExplorerEntry file(String name) {
        return SFMFileExplorerEntry.file(name, name);
    }
}
