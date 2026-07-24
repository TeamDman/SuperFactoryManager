package ca.teamdman.sfm.client.screen.file_explorer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMFileExplorerDropPolicyTests {
    @TempDir Path temp;

    @Test
    void acceptsExactlyOneExistingDirectoryAndPanelReplacesRoot() throws IOException {
        Path replacement = Files.createDirectory(temp.resolve("replacement"));
        Files.writeString(replacement.resolve("new.txt"), "new");
        SFMFileExplorerSource original = new SFMFileExplorerFixtureSource();
        SFMFileExplorerPanel panel = new SFMFileExplorerPanel(original, intent -> {});

        panel.onFilesDrop(List.of(replacement));

        assertTrue(panel.model().source() instanceof SFMPathFileExplorerSource);
        assertEquals(replacement.toAbsolutePath().normalize(), ((SFMPathFileExplorerSource) panel.model().source()).root());
        assertTrue(panel.statusMessage().startsWith("Root replaced:"));
    }

    @Test
    void rejectsEmptyMultipleFileAndMissingWithoutReplacingRoot() throws IOException {
        SFMFileExplorerSource original = new SFMFileExplorerFixtureSource();
        SFMFileExplorerPanel panel = new SFMFileExplorerPanel(original, intent -> {});
        Path directory = Files.createDirectory(temp.resolve("directory"));
        Path file = Files.writeString(temp.resolve("file.txt"), "file");

        panel.onFilesDrop(List.of());
        assertSame(original, panel.model().source());
        panel.onFilesDrop(List.of(directory, directory));
        assertSame(original, panel.model().source());
        panel.onFilesDrop(List.of(file));
        assertSame(original, panel.model().source());
        panel.onFilesDrop(List.of(temp.resolve("missing")));
        assertSame(original, panel.model().source());
        assertFalse(SFMFileExplorerDropPolicy.evaluate(null).accepted());
        assertFalse(SFMFileExplorerDropPolicy.evaluate(Arrays.asList((Path) null)).accepted());
        assertTrue(panel.statusMessage().startsWith("Drop rejected:"));
    }

    @Test
    void structuredPolicyRejectsSymlinkDirectoryWhenPortable() throws IOException {
        Path target = Files.createDirectory(temp.resolve("target"));
        Path link = temp.resolve("link");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable: " + exception);
        }
        SFMFileExplorerDropResult result = SFMFileExplorerDropPolicy.evaluate(List.of(link));
        assertFalse(result.accepted());
        assertEquals(null, result.replacement());
    }
}
