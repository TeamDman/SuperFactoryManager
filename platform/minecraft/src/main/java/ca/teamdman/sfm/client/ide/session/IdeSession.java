package ca.teamdman.sfm.client.ide.session;

import java.util.ArrayList;
import java.util.List;

public final class IdeSession {
    private IdeShellContextSnapshot shellContext = IdeShellContextSnapshot.empty();
    private IdeSessionTarget focusedTarget = IdeSessionTarget.none();
    private final List<IdeSessionTarget> selectedTargets = new ArrayList<>();

    public IdeShellContextSnapshot shellContext() {
        return shellContext;
    }

    public IdeSessionTarget focusedTarget() {
        return focusedTarget;
    }

    public List<IdeSessionTarget> selectedTargets() {
        return List.copyOf(selectedTargets);
    }

    public void update(IdeShellContextSnapshot newShellContext, IdeSessionTarget newFocusedTarget, List<IdeSessionTarget> newSelectedTargets) {
        this.shellContext = newShellContext;
        this.focusedTarget = newFocusedTarget;
        this.selectedTargets.clear();
        this.selectedTargets.addAll(newSelectedTargets);
    }
}