package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;

/** A visualization that can reconstruct any advertised timestep without relying on visit order. */
public interface SFMSeekableTimelinePanel extends SFMScreenPanel {
    SFMTimelineBounds timelineBounds();

    void setTimelinePosition(int timestep);
}
