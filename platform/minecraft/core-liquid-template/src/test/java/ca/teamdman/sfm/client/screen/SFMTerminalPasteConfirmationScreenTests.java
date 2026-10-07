package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.terminal.SFMTerminalPasteWarning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMTerminalPasteConfirmationScreenTests {
    @Test
    void warningUsesTheExactCopyAndServerPreview() {
        assertEquals("""
                Warning
                You are about to paste text that contains multiple lines. If you paste this text into your shell, it may result in the unexpected execution of commands. Do you wish to continue?
                Clipboard contents (preview):
                99
                100""", SFMTerminalPasteWarning.text("99\n100"));
        assertEquals("Paste anyway", SFMTerminalPasteWarning.APPROVE_LABEL);
        assertEquals("Cancel", SFMTerminalPasteWarning.CANCEL_LABEL);
    }
}
