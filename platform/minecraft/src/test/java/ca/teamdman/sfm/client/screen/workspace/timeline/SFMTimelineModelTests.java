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
    void playbackAdvancesOnlyAfterTheConfiguredNumberOfClientTicks() {
        SFMTimelineModel model = new SFMTimelineModel(new SFMTimelineBounds(0, 2), 0, 3);
        model.togglePlaying();

        assertFalse(model.tick());
        assertFalse(model.tick());
        assertTrue(model.tick());
        assertEquals(1, model.current());
        assertTrue(model.playing());
        assertFalse(model.tick());
        assertFalse(model.tick());
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
}
