package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Stable semantic metadata for a visible SFM control.
 *
 * <p>This deliberately describes the control independently of its rendering
 * implementation.  Vanilla widgets, panel widgets, and future addressable
 * explorer entries can all expose the same contract to audits and action
 * discovery.</p>
 */
public interface SFMActionElement {
    ResourceLocation elementId();

    ResourceLocation keyboardUsageSituationId();

    Component narration();

    Optional<String> actionDraft();

    /** Whether the element participates in keyboard focus traversal. */
    boolean isKeyboardReachable();
}
