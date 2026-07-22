package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class SFMWorkspaceLayoutTests {
    private final SFMScreenPanel left = new SFMTestScreenPanel("left");
    private final SFMScreenPanel right = new SFMTestScreenPanel("right");

    @Test
    void singlePanelFillsViewportThenSplitsExactlyInHalf() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.single(left);
        SFMWorkspacePanelId leftId = layout.focusedPanel();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(7, 9, 102, 40);
        assertEquals(viewport, layout.bounds(viewport, 2).get(leftId));

        SFMWorkspacePanelId rightId = layout.insert(leftId, SFMWorkspaceSide.RIGHT, right);
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = layout.bounds(viewport, 2);
        assertEquals(new SFMScreenPanelBounds(7, 9, 50, 40), bounds.get(leftId));
        assertEquals(new SFMScreenPanelBounds(59, 9, 50, 40), bounds.get(rightId));
    }

    @Test
    void sameAxisInsertionNormalizesToDeterministicPeerBounds() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        SFMScreenPanel third = new SFMTestScreenPanel("third");

        SFMWorkspacePanelId thirdId = layout.insert(rightId, SFMWorkspaceSide.RIGHT, third);
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = layout.bounds(
                new SFMScreenPanelBounds(0, 0, 100, 40),
                2
        );

        assertEquals(List.of(left, right, third), layout.panels().stream().map(SFMWorkspaceLayout.PanelEntry::panel).toList());
        // Splitting the right half preserves the left panel's prior half-share.
        assertEquals(new SFMScreenPanelBounds(0, 0, 47, 40), bounds.get(layout.panels().get(0).id()));
        assertEquals(new SFMScreenPanelBounds(49, 0, 24, 40), bounds.get(rightId));
        assertEquals(new SFMScreenPanelBounds(75, 0, 25, 40), bounds.get(thirdId));
    }

    @Test
    void perpendicularInsertionCreatesNestedLinearAllocation() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        SFMWorkspacePanelId belowId = layout.insert(
                rightId,
                SFMWorkspaceSide.BELOW,
                new SFMTestScreenPanel("below")
        );

        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = layout.bounds(
                new SFMScreenPanelBounds(0, 0, 102, 82),
                2
        );

        assertEquals(new SFMScreenPanelBounds(52, 0, 50, 40), bounds.get(rightId));
        assertEquals(new SFMScreenPanelBounds(52, 42, 50, 40), bounds.get(belowId));
    }

    @Test
    void allocationHonoursMinimumsAndShares() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        assertTrue(layout.configurePanel(leftId, 1.0, 70));
        assertTrue(layout.configurePanel(rightId, 3.0, 10));

        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = layout.bounds(
                new SFMScreenPanelBounds(0, 0, 100, 20),
                2
        );

        assertEquals(74, bounds.get(leftId).width());
        assertEquals(24, bounds.get(rightId).width());
    }

    @Test
    void removalPreservesStableIdsAndSelectsAdjacentFocus() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        SFMWorkspacePanelId thirdId = layout.insert(rightId, SFMWorkspaceSide.RIGHT, new SFMTestScreenPanel("third"));

        assertEquals(thirdId, layout.focusedPanel());
        assertTrue(layout.remove(thirdId));
        assertEquals(rightId, layout.focusedPanel());
        assertSame(right, layout.panel(rightId));
        assertSame(left, layout.panel(leftId));
        assertFalse(layout.remove(thirdId));
    }

    @Test
    void stackAllocatesOnlyActivePanelAndRetainsBothIdentitiesWhenFlipped() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.stack(0,
                SFMWorkspaceLayout.panel(left), SFMWorkspaceLayout.panel(right)));
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(3, 4, 90, 50);

        assertEquals(viewport, layout.bounds(viewport, 2).get(leftId));
        assertNull(layout.bounds(viewport, 2).get(rightId));

        layout.recompose(SFMWorkspaceLayout.stack(1,
                SFMWorkspaceLayout.panel(left), SFMWorkspaceLayout.panel(right)));
        assertEquals(leftId, layout.panels().get(0).id());
        assertEquals(rightId, layout.panels().get(1).id());
        assertNull(layout.bounds(viewport, 2).get(leftId));
        assertEquals(viewport, layout.bounds(viewport, 2).get(rightId));
        assertEquals(rightId, layout.focusedPanel());
    }

    @Test
    void invalidDuplicatePanelGroupDoesNotProduceLayout() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(left), SFMWorkspaceLayout.panel(left))));
    }

    @Test
    void temporaryMaximizeShapeRetainsIdsWhenFullGroupReturns() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left), SFMWorkspaceLayout.panel(right)));
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();

        layout.recompose(SFMWorkspaceLayout.panel(right));
        assertEquals(rightId, layout.panels().get(0).id());
        assertEquals(List.of(leftId, rightId), layout.allPanels().stream().map(SFMWorkspaceLayout.PanelEntry::id).toList());
        layout.recompose(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left), SFMWorkspaceLayout.panel(right)));

        assertEquals(leftId, layout.panels().get(0).id());
        assertEquals(rightId, layout.panels().get(1).id());
    }
}
