package ca.teamdman.sfm.client.screen.history;

import ca.teamdman.sfm.client.history.SFMHistoryGraphTestFixture;
import ca.teamdman.sfm.client.history.presentation.SFMHistoryGraphPresentationModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMHistoryGraphPanelModelTests {
    @Test
    void initialSelectionUsesInstructionPointerWhileActualHeadRemainsIndependent() {
        SFMHistoryGraphPanelModel model = new SFMHistoryGraphPanelModel();
        model.update(SFMHistoryGraphTestFixture.snapshot("episode-a", 0).presentation());

        assertTrue(model.selected().orElseThrow().instructionPointer());
        assertFalse(model.selected().orElseThrow().actualHead());
        assertTrue(model.rows().stream().anyMatch(SFMHistoryGraphPanelModel.Row::actualHead));
        assertTrue(model.selected().orElseThrow().details().stream()
                .anyMatch(detail -> detail.key().equals("trajectory.f") && detail.value().equals("2")));
        assertTrue(model.selected().orElseThrow().accessibleNarration().contains("trajectory.f 2"));
        assertEquals(
                List.of("trajectory.g", "trajectory.h", "trajectory.f"),
                SFMHistoryGraphPanel.visibleDetails(model.selected().orElseThrow(), 3).stream()
                        .map(SFMHistoryGraphPresentationModel.Detail::key)
                        .toList()
        );
    }

    @Test
    void selectionSurvivesLiveProjectionReplacementByStableSubjectKey() {
        SFMHistoryGraphPanelModel model = new SFMHistoryGraphPanelModel();
        var first = SFMHistoryGraphTestFixture.snapshot("episode-a", 0).presentation();
        model.update(first);
        model.selectLast();
        String selected = model.selected().orElseThrow().key();

        model.update(SFMHistoryGraphTestFixture.snapshot("episode-a", 1).presentation());

        assertEquals(selected, model.selected().orElseThrow().key());
    }

    @Test
    void viewportIsBoundedAndTracksKeyboardSelectionWithoutLosingRows() {
        SFMHistoryGraphPanelModel model = new SFMHistoryGraphPanelModel();
        model.update(SFMHistoryGraphTestFixture.snapshot("episode-a", 0).presentation());
        int total = model.rows().size();
        assertTrue(total > 3);

        model.selectLast();
        List<SFMHistoryGraphPanelModel.Row> visible = model.visibleRows(3);

        assertEquals(3, visible.size());
        assertEquals(model.selected().orElseThrow(), visible.get(visible.size() - 1));
        assertEquals(total - 3, model.firstVisibleIndex(3));
        assertTrue(model.selectFirst());
        assertEquals(0, model.selectedIndex());
    }

    @Test
    void rowProjectionIsDeterministicAndEdgesFollowTheirSourceLane() {
        var presentation = SFMHistoryGraphTestFixture.snapshot("episode-a", 0).presentation();
        List<SFMHistoryGraphPanelModel.Row> first = SFMHistoryGraphPanelModel.projectRows(presentation);
        List<SFMHistoryGraphPanelModel.Row> second = SFMHistoryGraphPanelModel.projectRows(presentation);

        assertEquals(first, second);
        for (int index = 0; index < first.size(); index++) {
            SFMHistoryGraphPanelModel.Row row = first.get(index);
            if (row.kind() != SFMHistoryGraphPanelModel.RowKind.EDGE) continue;
            assertTrue(index > 0);
            assertTrue(row.depth() >= 1);
        }
    }
}
