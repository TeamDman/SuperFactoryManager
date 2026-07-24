package ca.teamdman.sfm.client.screen.workspace.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMInventoryReplayFixtureTests {
    @Test
    void everySemanticKeyframeHasExactlyOneCobblestoneOwner() {
        for (int keyframe = 0; keyframe <= 3; keyframe++) {
            SFMInventoryReplayFixture.Frame frame = SFMInventoryReplayFixture.frameAt(keyframe);
            int owners = (frame.chestOwnsCobblestone() ? 1 : 0)
                    + (frame.cursorOwnsCobblestone() ? 1 : 0)
                    + (frame.playerOwnsCobblestone() ? 1 : 0);
            assertEquals(1, owners, "keyframe=" + keyframe);
        }
    }

    @Test
    void semanticOwnershipBoundariesAndFractionalTransitAreDistinct() {
        assertTrue(SFMInventoryReplayFixture.frameAt(0).chestOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(1).cursorOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.sample(1.5D).cursorOwnsCobblestone());
        assertEquals(0.5D, SFMInventoryReplayFixture.sample(1.5D).cursorPathPosition());
        assertTrue(SFMInventoryReplayFixture.frameAt(2).cursorOwnsCobblestone());
        assertTrue(SFMInventoryReplayFixture.frameAt(3).playerOwnsCobblestone());
    }

    @Test
    void reverseAndRandomAccessReturnEqualIndependentValues() {
        SFMInventoryReplayFixture.Frame first = SFMInventoryReplayFixture.sample(1.375D);
        SFMInventoryReplayFixture.sample(3D);
        SFMInventoryReplayFixture.sample(0D);
        SFMInventoryReplayFixture.Frame second = SFMInventoryReplayFixture.sample(1.375D);
        assertEquals(first, second);
        assertNotSame(first, second);
    }

    @Test
    void rejectsPositionsOutsideAdvertisedBounds() {
        assertThrows(IllegalArgumentException.class, () -> SFMInventoryReplayFixture.frameAt(-1));
        assertThrows(IllegalArgumentException.class, () -> SFMInventoryReplayFixture.frameAt(4));
        assertThrows(IllegalArgumentException.class, () -> SFMFalsifiedInventoryReplayPanel.stateAt(-0.1D));
        assertThrows(IllegalArgumentException.class, () -> SFMFalsifiedInventoryReplayPanel.stateAt(3.1D));
    }
}
