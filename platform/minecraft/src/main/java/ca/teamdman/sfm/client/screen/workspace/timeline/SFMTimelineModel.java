package ca.teamdman.sfm.client.screen.workspace.timeline;

/** Pure bounded-integer playback state. One call to {@link #tick()} is one deterministic client tick. */
public final class SFMTimelineModel {
    private final SFMTimelineBounds bounds;
    private final int ticksPerStep;
    private int current;
    private int elapsedTicks;
    private boolean playing;

    public SFMTimelineModel(SFMTimelineBounds bounds, int initial, int ticksPerStep) {
        if (ticksPerStep < 1) throw new IllegalArgumentException("Timeline ticks per step must be positive");
        if (initial < bounds.first() || initial > bounds.last()) {
            throw new IllegalArgumentException("Initial timestep is outside timeline bounds");
        }
        this.bounds = bounds;
        this.current = initial;
        this.ticksPerStep = ticksPerStep;
    }

    public SFMTimelineBounds bounds() { return bounds; }
    public int current() { return current; }
    public int ticksPerStep() { return ticksPerStep; }
    public boolean playing() { return playing; }

    public boolean seek(int timestep) {
        int next = bounds.clamp(timestep);
        elapsedTicks = 0;
        if (next == current) return false;
        current = next;
        return true;
    }

    public boolean step(int delta) {
        playing = false;
        return seek(current + delta);
    }

    public void togglePlaying() {
        if (playing) {
            playing = false;
        } else if (current < bounds.last()) {
            playing = true;
            elapsedTicks = 0;
        }
    }

    public void pause() {
        playing = false;
        elapsedTicks = 0;
    }

    /** @return true when the selected timestep changed. */
    public boolean tick() {
        if (!playing) return false;
        elapsedTicks++;
        if (elapsedTicks < ticksPerStep) return false;
        elapsedTicks = 0;
        if (current >= bounds.last()) {
            playing = false;
            return false;
        }
        current++;
        if (current >= bounds.last()) playing = false;
        return true;
    }
}
