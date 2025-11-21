package ca.teamdman.sfm.common.label;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.util.ConfirmationParams;

public interface LabelGunPlan {

    /// Must only be called on the server side
    void run();

    /// Determines if the user will be prompted to confirm the action
    default @Nullable ConfirmationParams getConfirmation() {
        return null;
    }
}
