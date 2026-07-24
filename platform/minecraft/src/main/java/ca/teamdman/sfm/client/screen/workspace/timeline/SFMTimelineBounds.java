package ca.teamdman.sfm.client.screen.workspace.timeline;

public record SFMTimelineBounds(int first, int last) {
    public SFMTimelineBounds {
        if (first > last) throw new IllegalArgumentException("Timeline first timestep must not exceed last timestep");
    }

    public int clamp(int timestep) {
        return Math.max(first, Math.min(last, timestep));
    }

    public double clamp(double position) {
        if (!Double.isFinite(position)) throw new IllegalArgumentException("Timeline position must be finite");
        return Math.max(first, Math.min(last, position));
    }
}
