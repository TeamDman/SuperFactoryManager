package ca.teamdman.sfm.client.screen.workspace.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMKeyframeTimelineTests {
    @Test
    void unequalDurationsMapTimeToFractionalKeyframesAndBack() {
        SFMKeyframeTimeline timeline = new SFMKeyframeTimeline(6, 24, 10);

        assertEquals(1.5D, timeline.positionAtElapsedTicks(18D), 1.0E-9D);
        assertEquals(18D, timeline.elapsedTicksAtPosition(1.5D), 1.0E-9D);
        assertEquals(2.5D, timeline.positionAtElapsedTicks(35D), 1.0E-9D);
        assertEquals(40, timeline.totalTicks());
    }

    @Test
    void zeroDurationTransitionsResolveImmediatelyAndRemainBounded() {
        SFMKeyframeTimeline timeline = new SFMKeyframeTimeline(0, 10, 0);

        assertEquals(1D, timeline.positionAtElapsedTicks(0D), 1.0E-9D);
        assertEquals(1.5D, timeline.positionAtElapsedTicks(5D), 1.0E-9D);
        assertEquals(3D, timeline.positionAtElapsedTicks(10D), 1.0E-9D);
        assertEquals(0D, timeline.elapsedTicksAtPosition(0.5D), 1.0E-9D);
        assertEquals(10D, timeline.elapsedTicksAtPosition(2.5D), 1.0E-9D);
    }

    @Test
    void rejectsInvalidDurationsAndNonFiniteSeeks() {
        assertThrows(IllegalArgumentException.class, () -> new SFMKeyframeTimeline(1, -1));
        SFMKeyframeTimeline timeline = new SFMKeyframeTimeline(1);
        assertThrows(IllegalArgumentException.class, () -> timeline.clampPosition(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> timeline.clampElapsedTicks(Double.POSITIVE_INFINITY));
    }

    @Test
    void modelSeparatesKeyframeJumpsFromElapsedTimeSeeking() {
        SFMTimelineModel model = new SFMTimelineModel(new SFMTimelineBounds(0, 3), 0D,
                new SFMKeyframeTimeline(6, 24, 10));
        assertTrue(model.seekElapsedTicks(18D));
        assertEquals(1.5D, model.keyframePosition(), 1.0E-9D);
        assertTrue(model.jumpKeyframe(-1));
        assertEquals(1D, model.keyframePosition(), 1.0E-9D);
        assertEquals(6D, model.elapsedTicks(), 1.0E-9D);
        assertTrue(model.jumpKeyframe(1));
        assertEquals(2D, model.keyframePosition(), 1.0E-9D);
        assertEquals(30D, model.elapsedTicks(), 1.0E-9D);
    }

    @Test
    void deterministicPlaybackAndReverseRandomSeekDoNotDependOnHistory() {
        SFMTimelineModel model = new SFMTimelineModel(new SFMTimelineBounds(0, 3), 0D,
                new SFMKeyframeTimeline(6, 24, 10));
        model.togglePlaying();
        for (int i = 0; i < 18; i++) assertTrue(model.tick());
        assertEquals(1.5D, model.keyframePosition(), 1.0E-9D);
        model.pause();
        model.seekElapsedTicks(40D);
        model.seekElapsedTicks(0D);
        model.seekElapsedTicks(18D);
        assertEquals(1.5D, model.keyframePosition(), 1.0E-9D);
        assertFalse(model.playing());
    }
}
