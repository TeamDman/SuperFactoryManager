package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMExplorerPathLabelerTests {
    private static final SFMPath ROOT = SFMPath.parse("file:///D:/Repos/SFM");
    private static final SFMPath FILE = SFMPath.parse("file:///D:/Repos/SFM/src/main/java/SFM.java");

    @Test
    public void nameRelativeAndAbsoluteLabelsAreIndependentFromListVersusIcons() {
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("labels"), ROOT, new SFMSelectionRepository()
        );
        SFMExplorerProjection.Row row = row(FILE, "SFM.java");

        assertEquals("SFM.java", SFMExplorerPathLabeler.label(row, session.snapshot()));

        session.setView(SFMExplorerProjection.View.SMALL_ICONS);
        session.setPathDisplay(SFMExplorerProjection.PathDisplay.RELATIVE_PATH);
        assertEquals("src/main/java/SFM.java", SFMExplorerPathLabeler.label(row, session.snapshot()));
        assertEquals(SFMExplorerProjection.View.SMALL_ICONS, session.snapshot().settings().view());

        session.setPathDisplay(SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH);
        assertEquals(FILE.canonical(), SFMExplorerPathLabeler.label(row, session.snapshot()));
        assertEquals(SFMExplorerProjection.View.SMALL_ICONS, session.snapshot().settings().view());

        session.setView(SFMExplorerProjection.View.LIST);
        assertEquals(FILE.canonical(), SFMExplorerPathLabeler.label(row, session.snapshot()));
        assertEquals(SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH,
                session.snapshot().settings().pathDisplay());
    }

    @Test
    public void aRelativeRootUsesDotAndUnrelatedPathsRemainTruthfullyCanonical() {
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("relative"), ROOT, new SFMSelectionRepository()
        );
        session.setPathDisplay(SFMExplorerProjection.PathDisplay.RELATIVE_PATH);

        assertEquals(".", SFMExplorerPathLabeler.label(row(ROOT, "SFM"), session.snapshot()));
        SFMPath unrelated = SFMPath.parse("registry://minecraft/item/minecraft/stone");
        assertEquals(unrelated.canonical(), SFMExplorerPathLabeler.label(
                row(unrelated, "Stone"), session.snapshot()
        ));
    }

    private static SFMExplorerProjection.Row row(SFMPath path, String label) {
        SFMExplorerEntry entry = SFMExplorerEntry.simple(path, label, false, Optional.empty());
        return new SFMExplorerProjection.Row(
                path,
                entry,
                0,
                false,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME)
        );
    }
}
