package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerEntry;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;

import java.util.List;

/** Deterministic visual fixture covering every first-slice icon fallback. */
public final class SFMFileIconGalleryFixtureSource implements SFMFileExplorerSource {
    private final SFMFileExplorerSnapshot snapshot = SFMFileExplorerSnapshot.ready(List.of(
            SFMFileExplorerEntry.directory("workspace", "workspace", List.of()),
            file("Factory.sfml"),
            file("ReviewWorkspace.java"),
            file("settings.toml"),
            file("metadata.json"),
            file("sources.tar.gz"),
            file("sources.gz"),
            file("README"),
            file("notes.unknown"),
            file("theme.missing-item")
    ));

    @Override
    public String displayName() {
        return "ItemStack icon gallery / deterministic fallbacks";
    }

    @Override
    public SFMFileExplorerSnapshot snapshot() {
        return snapshot;
    }

    private static SFMFileExplorerEntry file(String name) {
        return SFMFileExplorerEntry.file(name, name);
    }
}
