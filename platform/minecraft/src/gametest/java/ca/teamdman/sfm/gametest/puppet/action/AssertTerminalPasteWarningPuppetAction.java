package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.terminal.SFMTerminalPasteWarning;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

/** Verifies exact warning copy/default focus while retaining no clipboard body in the artifact. */
public record AssertTerminalPasteWarningPuppetAction(
        String artifactName,
        String canonicalPreview
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "assert guarded terminal paste warning";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!runtime.isTerminalPasteWarningOpen()) return false;
        String expected = SFMTerminalPasteWarning.text(canonicalPreview);
        boolean exactWarning = expected.equals(runtime.terminalPasteWarningText());
        boolean cancelFocused = runtime.terminalPasteWarningCancelFocused();
        if (!exactWarning || !cancelFocused) {
            throw new IllegalStateException("Terminal paste warning copy or default Cancel focus differed");
        }
        String evidence = "schema=sfm-terminal-paste-warning-puppet-v1\n"
                + "exact_warning_copy=true\n"
                + "canonical_preview_matches=true\n"
                + "cancel_initially_focused=true\n"
                + "clipboard_body_retained_in_artifact=false\n"
                + "preview_retained_in_artifact=false\n"
                + "warning_chars=" + expected.length() + "\n"
                + "preview_chars=" + canonicalPreview.length() + "\n";
        runtime.writeTerminalInteractionEvidence(artifactName, evidence);
        return true;
    }
}
