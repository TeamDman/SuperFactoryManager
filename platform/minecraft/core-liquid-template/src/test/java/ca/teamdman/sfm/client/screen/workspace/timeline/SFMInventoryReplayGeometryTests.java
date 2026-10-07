package ca.teamdman.sfm.client.screen.workspace.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMInventoryReplayGeometryTests {
    @Test
    void vanillaPitchPreservesAuditable1192GenericContainerOffsets() {
        SFMInventoryReplayGeometry geometry = SFMInventoryReplayGeometry.fit(0, 0, 194, 256);
        assertEquals(18, geometry.slotPitch());
        // ChestMenu 1.19.2: chest y=18, main y=103+(3-4)*18=85, hotbar y=161-18=143.
        assertEquals(67, geometry.playerMainTop() - geometry.chestTop());
        assertEquals(58, geometry.hotbarTop() - geometry.playerMainTop());
        assertEquals(4, geometry.hotbarGap());
        assertEquals(14, geometry.chestMainVisibleBlankRows());
    }

    @Test
    void resizeKeepsSectionsCenteredAndHotbarSeparated() {
        SFMInventoryReplayGeometry wide = SFMInventoryReplayGeometry.fit(10, 20, 600, 500);
        SFMInventoryReplayGeometry narrow = SFMInventoryReplayGeometry.fit(10, 20, 170, 250);
        assertTrue(wide.left() > 10);
        assertTrue(narrow.slotPitch() >= 14);
        assertTrue(wide.hotbarGap() > 0);
        assertTrue(narrow.hotbarGap() > 0);
        assertEquals(wide.left() + wide.gridWidth() / 2,
                (wide.chestSlotCenter(0).x() + wide.chestSlotCenter(8).x()) / 2);
    }

    @Test
    void playerMainAndHotbarIndicesUseDifferentOrigins() {
        SFMInventoryReplayGeometry geometry = SFMInventoryReplayGeometry.fit(0, 0, 194, 256);
        assertEquals(geometry.playerMainTop() + geometry.slotPitch() / 2,
                geometry.playerSlotCenter(0).y());
        assertEquals(geometry.hotbarTop() + geometry.slotPitch() / 2,
                geometry.playerSlotCenter(27).y());
        assertThrows(IllegalArgumentException.class, () -> geometry.playerSlotCenter(36));
    }
}
