package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerEntry;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSnapshot;
import ca.teamdman.sfm.client.screen.file_explorer.SFMFileExplorerSource;

import java.util.ArrayList;
import java.util.List;

/** Deterministic 1,002-file hierarchy used only by the visual puppet harness. */
public final class SFMFileExplorerLargeFixtureSource implements SFMFileExplorerSource {
    private final SFMFileExplorerSnapshot snapshot = SFMFileExplorerSnapshot.ready(List.of(
            directory("0000-0999", 0, 999),
            directory("1000-1999", 1000, 1001)
    ));

    @Override
    public String displayName() {
        return "Synthetic 0000.txt-1001.txt / 1,002 files";
    }

    @Override
    public SFMFileExplorerSnapshot snapshot() {
        return snapshot;
    }

    private static SFMFileExplorerEntry directory(
            String name,
            int first,
            int last
    ) {
        ArrayList<SFMFileExplorerEntry> files = new ArrayList<>(last - first + 1);
        for (int value = first; value <= last; value++) {
            String fileName = "%04d.txt".formatted(value);
            files.add(SFMFileExplorerEntry.file(name + "/" + fileName, fileName));
        }
        return SFMFileExplorerEntry.directory(name, name, files);
    }
}
