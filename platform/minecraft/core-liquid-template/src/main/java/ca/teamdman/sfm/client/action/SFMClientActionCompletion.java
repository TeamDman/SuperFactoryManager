package ca.teamdman.sfm.client.action;

import java.util.List;
import java.util.Optional;

/** Explicit opt-in for cheap, pure contextual command construction. Never perform I/O here. */
public interface SFMClientActionCompletion {
    record Continuation(String arguments,String displayText) {}
    default List<Continuation> contextualContinuations(SFMClientActionContext context) { return List.of(); }
    /** Empty means use ordinary Brigadier; present may include non-executable named usage hints. */
    Optional<List<SFMPaletteCandidate>> argumentCandidates(String command,int argumentStart,int cursor,
                                                         SFMClientActionContext context);
    /** Constrained context menus must opt in before allowing an incomplete prefix to escape into construction. */
    default boolean acceptsContinuation(String canonicalCommand,SFMClientActionContext context) { return true; }
}
