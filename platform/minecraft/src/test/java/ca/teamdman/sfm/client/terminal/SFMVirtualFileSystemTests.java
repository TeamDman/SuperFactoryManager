package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMVirtualFileSystemTests {
    @Test
    void normalizesPathsAndListsImmediateChildrenInStableOrder() {
        SFMVirtualFileSystem filesystem = new SFMVirtualFileSystem();
        filesystem.put("/workspace/z.txt", "z");
        filesystem.put("/workspace/nested/a.txt", "a");

        assertEquals("z", filesystem.read("/workspace/./z.txt"));
        assertEquals(java.util.List.of("nested/", "z.txt"), filesystem.list("/workspace"));
        assertEquals(java.util.List.of("a.txt"), filesystem.list("/workspace/nested/"));
    }

    @Test
    void enforcesFileAndAggregateBounds() {
        SFMVirtualFileSystem filesystem = new SFMVirtualFileSystem(1, 4, 4);
        filesystem.put("/one", "1234");
        assertThrows(IllegalArgumentException.class, () -> filesystem.put("/too-large", "12345"));
        assertThrows(IllegalStateException.class, () -> filesystem.put("/two", "1"));
    }
}
