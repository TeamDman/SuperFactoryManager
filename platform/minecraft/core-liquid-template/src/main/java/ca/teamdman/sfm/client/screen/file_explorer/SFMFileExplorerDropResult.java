package ca.teamdman.sfm.client.screen.file_explorer;

import org.jetbrains.annotations.Nullable;

public record SFMFileExplorerDropResult(
        boolean accepted,
        String message,
        @Nullable SFMPathFileExplorerSource replacement
) {
    public static SFMFileExplorerDropResult accepted(SFMPathFileExplorerSource source) {
        return new SFMFileExplorerDropResult(true, "Root replaced: " + source.root().getFileName(), source);
    }

    public static SFMFileExplorerDropResult rejected(String message) {
        return new SFMFileExplorerDropResult(false, message, null);
    }
}
