package ca.teamdman.sfm.client.terminal;

import java.util.Objects;

/** Exact user-facing multiline-paste warning contract, independent from Minecraft bootstrap. */
public final class SFMTerminalPasteWarning {
    public static final String TITLE = "Warning";
    public static final String MESSAGE = "You are about to paste text that contains multiple lines. "
            + "If you paste this text into your shell, it may result in the unexpected execution of commands. "
            + "Do you wish to continue?";
    public static final String PREVIEW_LABEL = "Clipboard contents (preview):";
    public static final String APPROVE_LABEL = "Paste anyway";
    public static final String CANCEL_LABEL = "Cancel";

    private SFMTerminalPasteWarning() {
    }

    public static String text(String preview) {
        return TITLE + "\n" + MESSAGE + "\n" + PREVIEW_LABEL + "\n"
                + Objects.requireNonNull(preview, "preview");
    }
}
