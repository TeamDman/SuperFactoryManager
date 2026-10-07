package ca.teamdman.sfm.client.screen.explorer;

/**
 * Boundary between explorer presentation and the registered semantic-action
 * executor. The panel never reaches through this seam to mutate an explorer
 * session directly.
 */
@FunctionalInterface
public interface SFMExplorerSemanticActionSink {
    void submit(String canonicalCommand);
}
