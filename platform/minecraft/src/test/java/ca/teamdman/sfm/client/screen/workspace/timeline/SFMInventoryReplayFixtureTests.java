package ca.teamdman.sfm.client.screen.workspace.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMInventoryReplayFixtureTests {
    @Test
    void everyFrameHasExactlyOneCobblestoneOwner() {
        for (int timestep = 0; timestep <= 5; timestep++) {
            SFMInventoryReplayFixture.Frame frame = SFMInventoryReplayFixture.frameAt(timestep);
            int owners = (frame.chestOwnsCobblestone() ? 1 : 0)
                    + (frame.cursorOwnsCobblestone() ? 1 : 0)
                    + (frame.playerOwnsCobblestone() ? 1 : 0);
            assertEquals(1, owners, "t=" + timestep);
        }
    }

    @Test
    void ownershipAndCursorPathDescribePickupTransitAndPlacement() {
        assertTrue(SFMInventoryReplayFixture.frameAt(0).chestOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(1).cursorOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(2).cursorOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(3).cursorOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(4).cursorOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(5).playerOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(4).cursorPathPosition()
                > SFMInventoryReplayFixture.frameAt(1).cursorPathPosition());
    }

    @Test
    void randomAccessReturnsEqualIndependentFrameValues() {
        SFMInventoryReplayFixture.Frame first = SFMInventoryReplayFixture.frameAt(3);
        SFMInventoryReplayFixture.frameAt(5);
        SFMInventoryReplayFixture.frameAt(0);
        SFMInventoryReplayFixture.Frame second = SFMInventoryReplayFixture.frameAt(3);
        assertEquals(first, second);
        assertNotSame(first, second);
    }

    @Test
    void rejectsPositionsOutsideAdvertisedBounds() {
        assertThrows(IllegalArgumentException.class, () -> SFMInventoryReplayFixture.frameAt(-1));
        assertThrows(IllegalArgumentException.class, () -> SFMInventoryReplayFixture.frameAt(6));
    }
}
