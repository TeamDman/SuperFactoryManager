package ca.teamdman.sfm.client.screen.workspace;

public enum SFMWorkspaceSide {
    LEFT(SFMWorkspaceAxis.HORIZONTAL, true),
    RIGHT(SFMWorkspaceAxis.HORIZONTAL, false),
    ABOVE(SFMWorkspaceAxis.VERTICAL, true),
    BELOW(SFMWorkspaceAxis.VERTICAL, false);

    private final SFMWorkspaceAxis axis;
    private final boolean before;

    SFMWorkspaceSide(SFMWorkspaceAxis axis, boolean before) {
        this.axis = axis;
        this.before = before;
    }

    public SFMWorkspaceAxis axis() {
        return axis;
    }

    public boolean before() {
        return before;
    }
}
