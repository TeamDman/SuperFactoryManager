package ca.teamdman.sfm.client.keybinding;

public record SFMActionInvocationIntent(
        String actionId,
        String commandDraft,
        String bindingId,
        long bindingRevision,
        long firstSourceEvent,
        long lastSourceEvent
) {
}
