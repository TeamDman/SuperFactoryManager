package ca.teamdman.sfm.client.screen.file_explorer;

import java.util.List;
import java.util.Objects;

public record SFMFileExplorerSnapshot(
        State state,
        List<SFMFileExplorerEntry> roots,
        String message
) {
    public enum State {
        LOADING,
        READY,
        ERROR
    }

    public SFMFileExplorerSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(message, "message");
        roots = List.copyOf(roots);
        if (state != State.READY && !roots.isEmpty()) {
            throw new IllegalArgumentException("Only ready snapshots may contain entries");
        }
    }

    public static SFMFileExplorerSnapshot loading(String message) {
        return new SFMFileExplorerSnapshot(State.LOADING, List.of(), message);
    }

    public static SFMFileExplorerSnapshot ready(List<SFMFileExplorerEntry> roots) {
        return new SFMFileExplorerSnapshot(State.READY, roots, "");
    }

    public static SFMFileExplorerSnapshot error(String message) {
        return new SFMFileExplorerSnapshot(State.ERROR, List.of(), message);
    }
}
