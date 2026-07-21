package ca.teamdman.sfm.client.screen.file_explorer;

/**
 * Supplies a bounded, read-only logical tree to the explorer.
 *
 * <p>Implementations may represent SFM workspaces, mounted disks, repository
 * sources, or explicitly accepted dropped paths. They must not grant write
 * access merely because an entry is visible.</p>
 */
public interface SFMFileExplorerSource {
    String displayName();

    SFMFileExplorerSnapshot snapshot();

    default SFMFileReadResult readText(String logicalPath) {
        return SFMFileReadResult.unsupported("This source does not expose file contents");
    }
}
