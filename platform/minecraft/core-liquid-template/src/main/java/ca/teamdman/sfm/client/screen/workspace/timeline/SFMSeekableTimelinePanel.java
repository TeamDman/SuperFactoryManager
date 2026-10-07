package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;

/** A visualization that can reconstruct any advertised timestep without relying on visit order. */
public interface SFMSeekableTimelinePanel extends SFMScreenPanel {
    SFMTimelineBounds timelineBounds();

    void setTimelinePosition(int timestep);

    /** Fractional semantic-keyframe position used for deterministic interpolation. */
    default void setTimelinePosition(double keyframePosition) {
        setTimelinePosition((int) Math.floor(keyframePosition));
    }

    default SFMKeyframeTimeline animationTimeline(int defaultTicksPerTransition) {
        SFMTimelineBounds bounds = timelineBounds();
        return SFMKeyframeTimeline.uniform(bounds.last() - bounds.first() + 1, defaultTicksPerTransition);
    }
}
