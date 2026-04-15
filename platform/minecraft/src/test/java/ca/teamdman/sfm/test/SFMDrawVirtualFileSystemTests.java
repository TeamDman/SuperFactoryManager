package ca.teamdman.sfm.test;

import ca.teamdman.sfm.client.draw.SFMDrawCanvasDocument;
import ca.teamdman.sfm.client.draw.SFMDrawCanvasStorage;
import ca.teamdman.sfm.client.draw.SFMDrawVirtualDirectoryListing;
import ca.teamdman.sfm.client.draw.SFMDrawVirtualFileSystem;
import ca.teamdman.sfm.client.draw.SFMDrawVirtualPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMDrawVirtualFileSystemTests {
    @Test
    public void resolveCanvasPathUsesMountedUserHome(@TempDir Path gameDirectory) {
        SFMDrawVirtualPath canvasPath = SFMDrawVirtualFileSystem.resolveCanvasPath("canvas", gameDirectory);

        assertEquals("/user/home/canvas.sfm-draw.json", canvasPath.virtualPath());
        assertEquals(
                gameDirectory.resolve("sfm-home").resolve("canvas.sfm-draw.json").normalize(),
                canvasPath.mountedPath()
        );
    }

    @Test
    public void resolveCanvasPathRejectsEscapingMountedRoot(@TempDir Path gameDirectory) {
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMDrawVirtualFileSystem.resolveCanvasPath("../canvas", gameDirectory)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMDrawVirtualFileSystem.resolveCanvasPath("/server/canvas", gameDirectory)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMDrawVirtualFileSystem.resolveCanvasPath("/user/home/../canvas", gameDirectory)
        );
    }

    @Test
    public void canvasStorageAndMoveRoundTrip(@TempDir Path gameDirectory) throws IOException {
        SFMDrawVirtualPath sourcePath = SFMDrawVirtualFileSystem.resolveCanvasPath("canvas", gameDirectory);
        SFMDrawCanvasDocument created = SFMDrawCanvasStorage.loadOrCreate(sourcePath);
        assertTrue(Files.exists(sourcePath.mountedPath()));

        SFMDrawCanvasDocument updated = new SFMDrawCanvasDocument(
                created.version(),
                24.0D,
                -8.0D,
                1.75D,
                created.activeLayer(),
                created.elementsLayerMuted(),
                created.chromeLayerMuted(),
                created.historyLayerMuted(),
                created.layerOrigins(),
                created.nextElementId(),
                created.nextGroupId(),
                List.of(SFMDrawCanvasDocument.Element.text(
                        1,
                        "ELEMENTS",
                        false,
                        false,
                        -1,
                        List.of(),
                        12.0D,
                        16.0D,
                        "hello",
                        0xFFFFFFFF,
                        1.0D
                )),
                null
        );
        SFMDrawCanvasStorage.write(sourcePath, updated);
        assertEquals(updated, SFMDrawCanvasStorage.read(sourcePath));

        SFMDrawVirtualPath destinationPath = SFMDrawVirtualFileSystem.resolveCanvasPath("canvas2", gameDirectory);
        SFMDrawVirtualFileSystem.move(sourcePath, destinationPath);

        assertFalse(Files.exists(sourcePath.mountedPath()));
        assertTrue(Files.exists(destinationPath.mountedPath()));
        assertEquals(updated, SFMDrawCanvasStorage.read(destinationPath));
    }

    @Test
    public void canvasStorageRoundTripsOriginsHistoryAndBindings() throws IOException {
        SFMDrawCanvasDocument document = new SFMDrawCanvasDocument(
                SFMDrawCanvasDocument.CURRENT_VERSION,
                6.0D,
                -3.0D,
                1.5D,
                "HISTORY",
                false,
                true,
                false,
                List.of(
                        new SFMDrawCanvasDocument.LayerOrigin("ELEMENTS", 12.0D, 18.0D),
                        new SFMDrawCanvasDocument.LayerOrigin("CHROME", 0.0D, 0.0D),
                        new SFMDrawCanvasDocument.LayerOrigin("HISTORY", -8.0D, 4.0D)
                ),
                7,
                3,
                List.of(
                        SFMDrawCanvasDocument.Element.rectangle(
                                1,
                                "ELEMENTS",
                                false,
                                false,
                                -1,
                                List.of(),
                                10.0D,
                                10.0D,
                                30.0D,
                                24.0D,
                                0x11223344,
                                0x55667788
                        ),
                        SFMDrawCanvasDocument.Element.arrow(
                                2,
                                "ELEMENTS",
                                false,
                                false,
                                -1,
                                List.of(),
                                List.of(
                                        new SFMDrawCanvasDocument.Point(5.0D, 5.0D),
                                        new SFMDrawCanvasDocument.Point(25.0D, 16.0D)
                                ),
                                List.of(),
                                0xFFE8A652,
                                new SFMDrawCanvasDocument.EndpointBinding(1, 0.25D, 0.5D),
                                new SFMDrawCanvasDocument.EndpointBinding(1, 0.8D, 0.6D)
                        )
                ),
                null
        );

        assertEquals(document, SFMDrawCanvasStorage.parse(SFMDrawCanvasStorage.toJson(document)));
    }

    @Test
    public void listDirectoryShowsDirectoriesFirstAndStripsCanvasExtension(@TempDir Path gameDirectory) throws IOException {
        Path userHome = SFMDrawVirtualFileSystem.userHomeRoot(gameDirectory);
        Files.createDirectories(userHome.resolve("examples"));
        Files.writeString(userHome.resolve("canvas.sfm-draw.json"), "{}{}");
        Files.writeString(userHome.resolve("notes.sfml"), "hello");

        SFMDrawVirtualDirectoryListing listing = SFMDrawVirtualFileSystem.listDirectory("", gameDirectory);
        assertEquals("/user/home", listing.target().virtualPath());
        assertEquals("examples", listing.entries().get(0).displayName());
        assertTrue(listing.entries().get(0).directory());
        assertEquals("canvas", listing.entries().get(1).displayName());
        assertEquals("/user/home/canvas.sfm-draw.json", listing.entries().get(1).virtualPath());
        assertEquals("notes.sfml", listing.entries().get(2).displayName());
    }
}