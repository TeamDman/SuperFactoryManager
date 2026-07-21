package ca.teamdman.sfm.client.screen.workspace;

import java.nio.file.Path;
import java.util.List;

/** Explicit capability for a focused panel that accepts final Java file-drop delivery. */
public interface SFMFileDropTarget {
    void onFilesDrop(List<Path> paths);
}
