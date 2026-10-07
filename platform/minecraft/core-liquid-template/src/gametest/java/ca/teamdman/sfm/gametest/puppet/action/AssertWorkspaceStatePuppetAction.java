package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

public record AssertWorkspaceStatePuppetAction(
        int totalEntries,
        int visibleEntries,
        int focusedSlotEntries,
        String expectedFocusedNarration,
        int expectedFocusedScale
) implements SFMPuppetAction {
    public AssertWorkspaceStatePuppetAction {
        if (totalEntries < 0 || visibleEntries < 0 || focusedSlotEntries < 0) {
            throw new IllegalArgumentException("Workspace counts must not be negative");
        }
        if (expectedFocusedScale < -1) {
            throw new IllegalArgumentException("Expected workspace scale must be -1 or non-negative");
        }
    }

    @Override
    public String description() {
        return "assert workspace state entries=" + totalEntries + " visible=" + visibleEntries;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        runtime.assertWorkspaceState(
                totalEntries,
                visibleEntries,
                focusedSlotEntries,
                expectedFocusedNarration,
                expectedFocusedScale
        );
        return true;
    }
}
