package ca.teamdman.sfm.client.screen.file_explorer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    }

    private static SFMFileExplorerEntry file(String name) {
        return SFMFileExplorerEntry.file(name, name);
    }
}
