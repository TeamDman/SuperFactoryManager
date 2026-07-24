package ca.teamdman.sfm.client.screen.workspace.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMTimelineModelTests {
    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SFMTimelineBounds(2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTimelineModel(new SFMTimelineBounds(0, 2), 3, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTimelineModel(new SFMTimelineBounds(0, 2), 0, 0));
    }

    @Test
    void seekAndStepClampToTheInclusiveBounds() {
        SFMTimelineModel model = new SFMTimelineModel(new SFMTimelineBounds(2, 5), 3, 4);

        assertTrue(model.seek(99));
        assertEquals(5, model.current());
        assertFalse(model.seek(99));
        assertTrue(model.step(-99));
        assertEquals(2, model.current());
        assertFalse(model.playing());
    }

    @Test
    void playbackAdvancesFractionallyAndLandsOnConfiguredKeyframeTicks() {
        SFMTimelineModel model = new SFMTimelineModel(new SFMTimelineBounds(0, 2), 0, 3);
        model.togglePlaying();

        assertTrue(model.tick());
        assertEquals(1D / 3D, model.keyframePosition(), 1.0E-9D);
        assertTrue(model.tick());
        assertEquals(2D / 3D, model.keyframePosition(), 1.0E-9D);
        assertTrue(model.tick());
        assertEquals(1, model.current());
        assertTrue(model.playing());
        assertTrue(model.tick());
        assertTrue(model.tick());
        assertTrue(model.tick());
        assertEquals(2, model.current());
        assertFalse(model.playing());
    }

    @Test
    void playbackAtTheFinalTimestepDoesNotLoop() {
        SFMTimelineModel model = new SFMTimelineModel(new SFMTimelineBounds(0, 1), 1, 1);
        model.togglePlaying();
        assertFalse(model.playing());
        assertFalse(model.tick());
        assertEquals(1, model.current());
    }

    @Test
    void fractionalSeekClampsAndReverseSeekIsStable() {
        SFMTimelineModel model = new SFMTimelineModel(new SFMTimelineBounds(0, 2), 0D,
                new SFMKeyframeTimeline(2, 8));
        assertTrue(model.seekKeyframePosition(1.25D));
        assertEquals(4D, model.elapsedTicks(), 1.0E-9D);
        assertTrue(model.seekKeyframePosition(2D));
        assertTrue(model.seekKeyframePosition(1.25D));
        assertEquals(1.25D, model.keyframePosition(), 1.0E-9D);
        assertTrue(model.seekKeyframePosition(-100D));
        assertEquals(0D, model.keyframePosition(), 1.0E-9D);
    }
}
