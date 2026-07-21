package ca.teamdman.sfm.client.screen.file_explorer;

import java.util.List;

/** A safe in-memory source used by the first UI experiment. */
public final class SFMFileExplorerFixtureSource implements SFMFileExplorerSource {
    private final SFMFileExplorerSnapshot snapshot = SFMFileExplorerSnapshot.ready(List.of(
            SFMFileExplorerEntry.directory("programs", "programs", List.of(
                    SFMFileExplorerEntry.file("programs/factory.sfml", "factory.sfml"),
                    SFMFileExplorerEntry.file("programs/archive.sfmp", "archive.sfmp")
            )),
            SFMFileExplorerEntry.directory("src", "src", List.of(
                    SFMFileExplorerEntry.file("src/ReviewWorkspace.java", "ReviewWorkspace.java"),
                    SFMFileExplorerEntry.file("src/SFML.g4", "SFML.g4")
            )),
            SFMFileExplorerEntry.file("settings.json", "settings.json"),
            SFMFileExplorerEntry.file("README", "README"),
            SFMFileExplorerEntry.file("notes.unknown", "notes.unknown")
    ));

    @Override
    public String displayName() {
        return "SFM read-only fixture";
    }

    @Override
    public SFMFileExplorerSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public SFMFileReadResult readText(String logicalPath) {
        return SFMFileReadResult.ready("Fixture content for " + logicalPath + "\nread-only\n");
    }
}
