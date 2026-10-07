package ca.teamdman.sfm.client.keybinding;

/**
 * Screen capability for publishing the exact contextual keybinding snapshot
 * that owns its current keyboard focus.
 *
 * <p>This keeps dynamic input dispatch independent of concrete screen types.
 * Workspace panels and transient editable screens can therefore participate
 * in the same contextual bindings without masquerading as one another.</p>
 */
public interface SFMKeyboardUsageContextProvider {
    SFMKeyboardUsageContextSnapshot keyboardUsageContextSnapshot();
}
