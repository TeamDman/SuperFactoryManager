package ca.teamdman.sfm.client.screen.workspace.timeline;

/** Pure deterministic playback state with related semantic-keyframe and elapsed-tick coordinates. */
public final class SFMTimelineModel {
    private final SFMTimelineBounds bounds;
    private final SFMKeyframeTimeline timeline;
    private double keyframePosition;
    private double elapsedTicks;
    private boolean playing;

    /** Compatibility constructor for integer timelines with uniform transition durations. */
    public SFMTimelineModel(SFMTimelineBounds bounds, int initial, int ticksPerStep) {
        this(bounds, initial, SFMKeyframeTimeline.uniform(
                bounds.last() - bounds.first() + 1,
                ticksPerStep
        ));
    }

    public SFMTimelineModel(SFMTimelineBounds bounds, double initialPosition, SFMKeyframeTimeline timeline) {
        if (bounds.last() - bounds.first() != timeline.lastKeyframe()) {
            throw new IllegalArgumentException("Timeline bounds must span the semantic keyframes");
        }
        if (initialPosition < bounds.first() || initialPosition > bounds.last()) {
            throw new IllegalArgumentException("Initial keyframe position is outside timeline bounds");
        }
        this.bounds = bounds;
        this.timeline = timeline;
        this.keyframePosition = initialPosition;
        this.elapsedTicks = timeline.elapsedTicksAtPosition(initialPosition - bounds.first());
    }

    public SFMTimelineBounds bounds() { return bounds; }
    public SFMKeyframeTimeline timeline() { return timeline; }
    public int current() { return (int) Math.round(keyframePosition); }
    public double keyframePosition() { return keyframePosition; }
    public double elapsedTicks() { return elapsedTicks; }
    public int ticksPerStep() {
        return timeline.lastKeyframe() == 0 ? 0 : timeline.transitionTicks(0);
    }
    public boolean playing() { return playing; }

    public boolean seek(int keyframe) { return seekKeyframePosition(keyframe); }

    public boolean seekKeyframePosition(double position) {
        double next = bounds.clamp(position);
        double nextTicks = timeline.elapsedTicksAtPosition(next - bounds.first());
        boolean changed = different(next, keyframePosition) || different(nextTicks, elapsedTicks);
        keyframePosition = next;
        elapsedTicks = nextTicks;
        return changed;
    }

    public boolean seekElapsedTicks(double ticks) {
        double nextTicks = timeline.clampElapsedTicks(ticks);
        double nextPosition = bounds.first() + timeline.positionAtElapsedTicks(nextTicks);
        boolean changed = different(nextPosition, keyframePosition) || different(nextTicks, elapsedTicks);
        elapsedTicks = nextTicks;
        keyframePosition = nextPosition;
        return changed;
    }

    /** Previous/next always lands on a semantic keyframe, never an animation sample. */
    public boolean jumpKeyframe(int direction) {
        playing = false;
        if (direction == 0) return false;
        int target = direction < 0
                ? (int) Math.ceil(keyframePosition) - 1
                : (int) Math.floor(keyframePosition) + 1;
        return seekKeyframePosition(target);
    }

    public boolean step(int delta) {
        if (delta == 0) return false;
        boolean changed = false;
        int direction = Integer.signum(delta);
        for (int i = 0; i < Math.abs(delta); i++) changed |= jumpKeyframe(direction);
        return changed;
    }

    public void togglePlaying() {
        if (playing) {
            playing = false;
        } else if (elapsedTicks < timeline.totalTicks()) {
            playing = true;
        }
    }

    public void pause() { playing = false; }

    /** @return true when deterministic animation time changed. */
    public boolean tick() {
        if (!playing) return false;
        if (elapsedTicks >= timeline.totalTicks()) {
            playing = false;
            return false;
        }
        seekElapsedTicks(elapsedTicks + 1D);
        if (elapsedTicks >= timeline.totalTicks()) playing = false;
        return true;
    }

    private static boolean different(double left, double right) {
        return Math.abs(left - right) > 1.0E-9D;
    }
}
