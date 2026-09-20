package ca.teamdman.sfm.client.action;

import java.util.List;

/** A live UI surface exposing stable, action-addressable keyboard focus targets. */
public interface SFMFocusTargetHost {
    List<String> focusTargetIds();

    boolean focusTarget(String targetId);
}
