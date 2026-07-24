package ca.teamdman.sfm.client.screen.file_explorer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMPathFileExplorerSourceTests {
    @TempDir
    Path root;

    @Test
    public void readsOnlyMetadataAndExcludesCaptureAndHistoryLocations() throws IOException {
        Files.writeString(root.resolve("visible.txt"), "unchanged");
        Files.createDirectory(root.resolve("nested"));
        Files.writeString(root.resolve("nested/inside.json"), "{}");
        Files.createDirectory(root.resolve("screenshots"));
        Files.writeString(root.resolve("screenshots/capture.png"), "capture");
        Files.createDirectory(root.resolve("logs"));
        Files.writeString(root.resolve("logs/latest.log"), "log");
        Files.writeString(root.resolve("servers.dat"), "history");

        SFMFileExplorerSnapshot snapshot = new SFMPathFileExplorerSource(root).snapshot();
        assertEquals(SFMFileExplorerSnapshot.State.READY, snapshot.state());
        List<String> names = snapshot.roots().get(0).children().stream().map(SFMFileExplorerEntry::name).toList();
        assertTrue(names.contains("visible.txt"));
        assertTrue(names.contains("nested"));
        assertFalse(names.contains("screenshots"));
        assertFalse(names.contains("logs"));
        assertFalse(names.contains("servers.dat"));
        assertEquals("unchanged", Files.readString(root.resolve("visible.txt")));
    }

    @Test
    public void capsChildrenDeterministically() throws IOException {
        for (int index = 0; index < SFMPathFileExplorerSource.MAX_CHILDREN_PER_DIRECTORY + 10; index++) {
            Files.writeString(root.resolve("%03d.txt".formatted(index)), "");
        }
        SFMFileExplorerEntry rootEntry = new SFMPathFileExplorerSource(root).snapshot().roots().get(0);
        assertEquals(SFMPathFileExplorerSource.MAX_CHILDREN_PER_DIRECTORY, rootEntry.children().size());
        assertEquals("000.txt", rootEntry.children().get(0).name());
        assertEquals("063.txt", rootEntry.children().get(63).name());
    }

    @Test
    public void readsBoundedUtf8TextInsideRoot() throws IOException {
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/hello.txt"), "hello π", StandardCharsets.UTF_8);
        SFMFileReadResult result = new SFMPathFileExplorerSource(root).readText("nested/hello.txt");
        assertEquals(SFMFileReadResult.State.READY, result.state());
        assertEquals("hello π", result.text());
    }

    @Test
    public void rejectsTraversalOversizeAndInvalidUtf8() throws IOException {
        Path outside = root.getParent().resolve("outside.txt");
        Files.writeString(outside, "outside");
        Files.write(root.resolve("large.txt"), new byte[SFMPathFileExplorerSource.MAX_TEXT_BYTES + 1]);
        Files.write(root.resolve("invalid.txt"), new byte[]{(byte) 0xC3, (byte) 0x28});
        SFMPathFileExplorerSource source = new SFMPathFileExplorerSource(root);

        assertEquals(SFMFileReadResult.State.ERROR, source.readText("../outside.txt").state());
        assertEquals(SFMFileReadResult.State.ERROR, source.readText("large.txt").state());
        assertEquals(SFMFileReadResult.State.ERROR, source.readText("invalid.txt").state());
    }

    @Test
    public void rejectsSymlinkWhenPortable() throws IOException {
        Path target = root.resolve("target.txt");
        Path link = root.resolve("link.txt");
        Files.writeString(target, "target");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | IOException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links unavailable: " + exception);
        }
        assertEquals(SFMFileReadResult.State.ERROR, new SFMPathFileExplorerSource(root).readText("link.txt").state());
    }
}
