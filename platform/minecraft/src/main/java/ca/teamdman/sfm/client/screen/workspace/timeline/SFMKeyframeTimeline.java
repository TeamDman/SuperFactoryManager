package ca.teamdman.sfm.client.screen.workspace.timeline;

import java.util.Arrays;

/** Immutable conversion between semantic keyframe space and deterministic client-tick time. */
public final class SFMKeyframeTimeline {
    private final int[] transitionTicks;
    private final int[] keyframeTicks;

    public SFMKeyframeTimeline(int... transitionTicks) {
        this.transitionTicks = Arrays.copyOf(transitionTicks, transitionTicks.length);
        this.keyframeTicks = new int[transitionTicks.length + 1];
        for (int i = 0; i < transitionTicks.length; i++) {
            if (transitionTicks[i] < 0) {
                throw new IllegalArgumentException("Transition duration must not be negative");
            }
            keyframeTicks[i + 1] = Math.addExact(keyframeTicks[i], transitionTicks[i]);
        }
    }

    public static SFMKeyframeTimeline uniform(int keyframeCount, int ticksPerTransition) {
        if (keyframeCount < 1) throw new IllegalArgumentException("At least one keyframe is required");
        if (ticksPerTransition < 1) throw new IllegalArgumentException("Ticks per transition must be positive");
        int[] durations = new int[keyframeCount - 1];
        Arrays.fill(durations, ticksPerTransition);
        return new SFMKeyframeTimeline(durations);
    }

    public int keyframeCount() { return keyframeTicks.length; }
    public int lastKeyframe() { return keyframeTicks.length - 1; }
    public int totalTicks() { return keyframeTicks[keyframeTicks.length - 1]; }
    public int transitionTicks(int transition) { return transitionTicks[transition]; }
    public int elapsedTicksAtKeyframe(int keyframe) { return keyframeTicks[clampKeyframe(keyframe)]; }

    public double positionAtElapsedTicks(double elapsedTicks) {
        double clamped = clampElapsedTicks(elapsedTicks);
        if (clamped >= totalTicks()) return lastKeyframe();
        for (int transition = 0; transition < transitionTicks.length; transition++) {
            int start = keyframeTicks[transition];
            int duration = transitionTicks[transition];
            int end = keyframeTicks[transition + 1];
            if (duration == 0) continue;
            if (clamped < end) return transition + (clamped - start) / duration;
        }
        return lastKeyframe();
    }

    public double elapsedTicksAtPosition(double keyframePosition) {
        double clamped = clampPosition(keyframePosition);
        if (clamped >= lastKeyframe()) return totalTicks();
        int transition = (int) Math.floor(clamped);
        double fraction = clamped - transition;
        return keyframeTicks[transition] + fraction * transitionTicks[transition];
    }

    public double clampPosition(double position) {
        if (!Double.isFinite(position)) throw new IllegalArgumentException("Keyframe position must be finite");
        return Math.max(0D, Math.min(lastKeyframe(), position));
    }

    public double clampElapsedTicks(double ticks) {
        if (!Double.isFinite(ticks)) throw new IllegalArgumentException("Elapsed ticks must be finite");
        return Math.max(0D, Math.min(totalTicks(), ticks));
    }

    private int clampKeyframe(int keyframe) {
        return Math.max(0, Math.min(lastKeyframe(), keyframe));
    }
}
