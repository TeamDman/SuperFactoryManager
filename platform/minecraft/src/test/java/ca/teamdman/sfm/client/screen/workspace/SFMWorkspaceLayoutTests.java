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
    void removingTheFinalPanelLeavesAnEmptySafeLayout() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.single(left);
        SFMWorkspacePanelId leftId = layout.focusedPanel();

        assertTrue(layout.remove(leftId));

        assertTrue(layout.panels().isEmpty());
        assertTrue(layout.visiblePanels().isEmpty());
        assertTrue(layout.bounds(new SFMScreenPanelBounds(0, 0, 100, 40), 2).isEmpty());
        assertNull(layout.focusedPanel());
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
    void focusingHiddenLeafActivatesEveryContainingStackBeforeDispatch() {
        SFMScreenPanel nested = new SFMTestScreenPanel("nested");
        SFMWorkspaceLayout.LayoutSpec innerStack = SFMWorkspaceLayout.stack(0,
                SFMWorkspaceLayout.panel(new SFMTestScreenPanel("peer")),
                SFMWorkspaceLayout.panel(nested));
        SFMWorkspaceLayout.LayoutSpec nestedRow = SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(right), innerStack);
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.stack(0,
                SFMWorkspaceLayout.panel(left), nestedRow));
        SFMWorkspacePanelId nestedId = layout.panels().stream()
                .filter(entry -> entry.panel() == nested)
                .findFirst().orElseThrow().id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 202, 100);
        assertNull(layout.bounds(viewport, 2).get(nestedId));

        assertTrue(layout.focus(nestedId));

        assertEquals(nestedId, layout.focusedPanel());
        assertEquals(new SFMScreenPanelBounds(102, 0, 100, 100), layout.bounds(viewport, 2).get(nestedId));
        assertSame(nested, layout.panel(layout.focusedPanel()));
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

    @Test
    void focusedOpenPushesIntoTheFocusedSlotAndTraversalVisitsEveryEntry() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.single(left);
        SFMWorkspacePanelId leftId = layout.focusedPanel();
        SFMWorkspacePanelId rightId = layout.pushToFocusedStack(
                right,
                SFMWorkspacePanelMetadata.explorerPreview(leftId.toString())
        );

        assertEquals(rightId, layout.focusedPanel());
        assertEquals(List.of(leftId, rightId), layout.focusedSlotEntries().stream()
                .map(SFMWorkspaceLayout.PanelEntry::id).toList());
        assertEquals(List.of(rightId), layout.visiblePanels().stream()
                .map(SFMWorkspaceLayout.PanelEntry::id).toList());

        assertTrue(layout.traverse(-1));
        assertEquals(leftId, layout.focusedPanel());
        assertTrue(layout.traverse(1));
        assertEquals(rightId, layout.focusedPanel());
    }

    @Test
    void movingPreservesEntryIdentityAndPushesIntoTheNeighborSlot() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        assertTrue(layout.focus(leftId));

        assertTrue(layout.move(leftId, SFMWorkspaceSide.RIGHT));
        assertEquals(leftId, layout.focusedPanel());
        assertEquals(List.of(rightId, leftId), layout.slotEntries(rightId).stream()
                .map(SFMWorkspaceLayout.PanelEntry::id).toList());
        assertSame(left, layout.panel(leftId));
        assertSame(right, layout.panel(rightId));
    }

    @Test
    void movingUsesPhysicalDirectionAndCanCreateAnEdgeSlotFromAStack() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        assertTrue(layout.focus(leftId));

        assertTrue(layout.move(leftId, SFMWorkspaceSide.RIGHT));
        assertEquals(List.of(rightId, leftId), layout.slotEntries(rightId).stream()
                .map(SFMWorkspaceLayout.PanelEntry::id).toList());
        assertEquals(leftId, layout.focusedPanel());

        SFMWorkspaceLayout edge = SFMWorkspaceLayout.group(SFMWorkspaceLayout.stack(0,
                SFMWorkspaceLayout.panel(left), SFMWorkspaceLayout.panel(right)));
        SFMWorkspacePanelId edgeLeft = edge.panels().stream()
                .filter(entry -> entry.panel() == left).findFirst().orElseThrow().id();
        SFMWorkspacePanelId edgeRight = edge.panels().stream()
                .filter(entry -> entry.panel() == right).findFirst().orElseThrow().id();
        assertTrue(edge.focus(edgeLeft));
        assertTrue(edge.move(edgeLeft, SFMWorkspaceSide.RIGHT));
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = edge.bounds(
                new SFMScreenPanelBounds(0, 0, 100, 100), 2);
        assertTrue(bounds.get(edgeLeft).x() > bounds.get(edgeRight).x());
        assertEquals(edgeLeft, edge.focusedPanel());
    }

    @Test
    void directionalInsertionAroundAStackKeepsTheStackAsOneSlot() {
        SFMScreenPanel stackedPeer = new SFMTestScreenPanel("stacked-peer");
        SFMScreenPanel below = new SFMTestScreenPanel("below");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left),
                SFMWorkspaceLayout.stack(0, SFMWorkspaceLayout.panel(right), SFMWorkspaceLayout.panel(stackedPeer))
        ));
        SFMWorkspacePanelId rightId = layout.panels().stream()
                .filter(entry -> entry.panel() == right).findFirst().orElseThrow().id();
        SFMWorkspacePanelId belowId = layout.insert(rightId, SFMWorkspaceSide.BELOW, below);

        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = layout.bounds(
                new SFMScreenPanelBounds(0, 0, 102, 102), 2);
        assertEquals(bounds.get(rightId).x(), bounds.get(belowId).x());
        assertTrue(bounds.get(belowId).y() > bounds.get(rightId).y());
        assertEquals(2, layout.slotEntries(rightId).size());
        assertTrue(layout.slotEntries(rightId).stream().anyMatch(entry -> entry.panel() == right));
        assertTrue(layout.slotEntries(rightId).stream().anyMatch(entry -> entry.panel() == stackedPeer));
    }

    @Test
    void contentAndScaleRotationsAreIndependentVisibleEntryAssignments() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        layout.updateMetadata(leftId, SFMWorkspacePanelMetadata.ordinary().withGuiScaleOverride(2));
        layout.updateMetadata(rightId, SFMWorkspacePanelMetadata.ordinary().withGuiScaleOverride(4));

        assertTrue(layout.rotateVisibleScale(1));
        assertEquals(4, layout.metadata(leftId).guiScaleOverride());
        assertEquals(2, layout.metadata(rightId).guiScaleOverride());

        assertTrue(layout.rotateVisibleContent(1));
        assertSame(right, layout.panel(leftId));
        assertSame(left, layout.panel(rightId));
        assertEquals(4, layout.metadata(leftId).guiScaleOverride());
        assertEquals(2, layout.metadata(rightId).guiScaleOverride());
    }

    @Test
    void capturedPanelScaleDoesNotRetargetAfterFocusChanges() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId capturedLeft = layout.panels().get(0).id();
        SFMWorkspacePanelId focusedRight = layout.panels().get(1).id();
        assertTrue(layout.focus(focusedRight));

        assertTrue(layout.setGuiScale(capturedLeft, 4));

        assertEquals(4, layout.metadata(capturedLeft).guiScaleOverride());
        assertNull(layout.metadata(focusedRight).guiScaleOverride());
        assertEquals(focusedRight, layout.focusedPanel());
    }

    @Test
    void directionalResizeUsesTheDeepestMatchingAxis() {
        SFMScreenPanel topLeft = new SFMTestScreenPanel("top-left");
        SFMScreenPanel topRight = new SFMTestScreenPanel("top-right");
        SFMScreenPanel bottom = new SFMTestScreenPanel("bottom");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left),
                SFMWorkspaceLayout.vertical(
                        SFMWorkspaceLayout.horizontal(
                                SFMWorkspaceLayout.panel(topLeft),
                                SFMWorkspaceLayout.panel(topRight)
                        ),
                        SFMWorkspaceLayout.panel(bottom)
                )
        ));
        SFMWorkspacePanelId topLeftId = layout.panels().stream()
                .filter(entry -> entry.panel() == topLeft).findFirst().orElseThrow().id();
        SFMWorkspacePanelId topRightId = layout.panels().stream()
                .filter(entry -> entry.panel() == topRight).findFirst().orElseThrow().id();
        SFMWorkspacePanelId bottomId = layout.panels().stream()
                .filter(entry -> entry.panel() == bottom).findFirst().orElseThrow().id();
        SFMWorkspacePanelId outerLeftId = layout.panels().stream()
                .filter(entry -> entry.panel() == left).findFirst().orElseThrow().id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 202, 102);

        assertTrue(layout.resize(topLeftId, SFMWorkspaceSide.RIGHT, viewport, 2, 20));
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> horizontal = layout.bounds(viewport, 2);
        assertEquals(100, horizontal.get(outerLeftId).width());
        assertEquals(54, horizontal.get(topLeftId).width());
        assertEquals(44, horizontal.get(topRightId).width());

        assertTrue(layout.resize(topLeftId, SFMWorkspaceSide.BELOW, viewport, 2, 20));
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> vertical = layout.bounds(viewport, 2);
        assertEquals(55, vertical.get(topLeftId).height());
        assertEquals(45, vertical.get(bottomId).height());
        assertEquals(54, vertical.get(topLeftId).width());

        assertTrue(layout.resize(bottomId, SFMWorkspaceSide.ABOVE, viewport, 2, 20));
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> verticalRestored = layout.bounds(viewport, 2);
        assertEquals(50, verticalRestored.get(topLeftId).height());
        assertEquals(50, verticalRestored.get(bottomId).height());
    }

    @Test
    void directionalResizeSearchesOutwardButIsUnavailableAtTheOuterEdge() {
        SFMScreenPanel top = new SFMTestScreenPanel("top");
        SFMScreenPanel bottom = new SFMTestScreenPanel("bottom");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left),
                SFMWorkspaceLayout.vertical(
                        SFMWorkspaceLayout.panel(top),
                        SFMWorkspaceLayout.panel(bottom)
                )
        ));
        SFMWorkspacePanelId leftId = layout.panels().stream()
                .filter(entry -> entry.panel() == left).findFirst().orElseThrow().id();
        SFMWorkspacePanelId topId = layout.panels().stream()
                .filter(entry -> entry.panel() == top).findFirst().orElseThrow().id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 202, 100);

        assertTrue(layout.resize(topId, SFMWorkspaceSide.LEFT, viewport, 2, 20));
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> resized = layout.bounds(viewport, 2);
        assertEquals(90, resized.get(leftId).width());
        assertEquals(110, resized.get(topId).width());

        assertFalse(layout.resize(leftId, SFMWorkspaceSide.LEFT, viewport, 2, 20));
        assertEquals(resized, layout.bounds(viewport, 2));
        assertFalse(SFMWorkspaceLayout.single(right).resize(
                new SFMWorkspacePanelId(0),
                SFMWorkspaceSide.RIGHT,
                viewport,
                2,
                20
        ));
    }

    @Test
    void repeatedResizeStopsAtMinimumAndNeighborInverseRestoresTheAllocation() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 102, 40);

        for (int step = 0; step < 6; step++) {
            assertTrue(layout.resize(leftId, SFMWorkspaceSide.RIGHT, viewport, 2, 20));
        }
        assertFalse(layout.resize(leftId, SFMWorkspaceSide.RIGHT, viewport, 2, 20));
        assertEquals(80, layout.bounds(viewport, 2).get(leftId).width());
        assertEquals(20, layout.bounds(viewport, 2).get(rightId).width());

        for (int step = 0; step < 6; step++) {
            assertTrue(layout.resize(rightId, SFMWorkspaceSide.LEFT, viewport, 2, 20));
        }
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> restored = layout.bounds(viewport, 2);
        assertEquals(50, restored.get(leftId).width());
        assertEquals(50, restored.get(rightId).width());
    }

    @Test
    void threeTrackResizeChangesOnlyTheAdjacentPairAndDoesNotDrift() {
        SFMScreenPanel middle = new SFMTestScreenPanel("middle");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left),
                SFMWorkspaceLayout.panel(middle),
                SFMWorkspaceLayout.panel(right)
        ));
        SFMWorkspacePanelId leftId = layout.panels().stream()
                .filter(entry -> entry.panel() == left).findFirst().orElseThrow().id();
        SFMWorkspacePanelId middleId = layout.panels().stream()
                .filter(entry -> entry.panel() == middle).findFirst().orElseThrow().id();
        SFMWorkspacePanelId rightId = layout.panels().stream()
                .filter(entry -> entry.panel() == right).findFirst().orElseThrow().id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 300, 40);

        assertTrue(layout.resize(middleId, SFMWorkspaceSide.RIGHT, viewport, 0, 20));
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> grown = layout.bounds(viewport, 0);
        assertEquals(100, grown.get(leftId).width());
        assertEquals(115, grown.get(middleId).width());
        assertEquals(85, grown.get(rightId).width());

        assertTrue(layout.resize(rightId, SFMWorkspaceSide.LEFT, viewport, 0, 20));
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> restored = layout.bounds(viewport, 0);
        assertEquals(100, restored.get(leftId).width());
        assertEquals(100, restored.get(middleId).width());
        assertEquals(100, restored.get(rightId).width());
    }

    @Test
    void resizingAStackPreservesItsEntriesActiveLeafAndFocus() {
        SFMScreenPanel hidden = new SFMTestScreenPanel("hidden");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.stack(
                        0,
                        SFMWorkspaceLayout.panel(left),
                        SFMWorkspaceLayout.panel(hidden)
                ),
                SFMWorkspaceLayout.panel(right)
        ));
        SFMWorkspacePanelId leftId = layout.panels().stream()
                .filter(entry -> entry.panel() == left).findFirst().orElseThrow().id();
        SFMWorkspacePanelId hiddenId = layout.panels().stream()
                .filter(entry -> entry.panel() == hidden).findFirst().orElseThrow().id();
        SFMWorkspacePanelId rightId = layout.panels().stream()
                .filter(entry -> entry.panel() == right).findFirst().orElseThrow().id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 100, 40);

        assertTrue(layout.resize(leftId, SFMWorkspaceSide.RIGHT, viewport, 0, 20));

        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> resized = layout.bounds(viewport, 0);
        assertEquals(55, resized.get(leftId).width());
        assertEquals(45, resized.get(rightId).width());
        assertNull(resized.get(hiddenId));
        assertEquals(List.of(leftId, hiddenId), layout.slotEntries(leftId).stream()
                .map(SFMWorkspaceLayout.PanelEntry::id).toList());
        assertEquals(leftId, layout.focusedPanel());
    }

    @Test
    void nestedDonorCannotShrinkAnyPanelBelowTheRequestedMinimum() {
        SFMScreenPanel nestedLeft = new SFMTestScreenPanel("nested-left");
        SFMScreenPanel nestedRight = new SFMTestScreenPanel("nested-right");
        SFMScreenPanel nestedBottom = new SFMTestScreenPanel("nested-bottom");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left),
                SFMWorkspaceLayout.vertical(
                        SFMWorkspaceLayout.horizontal(
                                SFMWorkspaceLayout.panel(nestedLeft),
                                SFMWorkspaceLayout.panel(nestedRight)
                        ),
                        SFMWorkspaceLayout.panel(nestedBottom)
                )
        ));
        SFMWorkspacePanelId leftId = layout.panels().stream()
                .filter(entry -> entry.panel() == left).findFirst().orElseThrow().id();
        SFMWorkspacePanelId nestedLeftId = layout.panels().stream()
                .filter(entry -> entry.panel() == nestedLeft).findFirst().orElseThrow().id();
        SFMWorkspacePanelId nestedRightId = layout.panels().stream()
                .filter(entry -> entry.panel() == nestedRight).findFirst().orElseThrow().id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 102, 102);

        assertTrue(layout.resize(leftId, SFMWorkspaceSide.RIGHT, viewport, 2, 20));
        assertTrue(layout.resize(leftId, SFMWorkspaceSide.RIGHT, viewport, 2, 20));
        assertFalse(layout.resize(leftId, SFMWorkspaceSide.RIGHT, viewport, 2, 20));

        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounded = layout.bounds(viewport, 2);
        assertEquals(20, bounded.get(nestedLeftId).width());
        assertEquals(20, bounded.get(nestedRightId).width());
    }

    @Test
    void canResizeUsesTheSameComputationWithoutMutatingLayoutState() {
        SFMScreenPanel hidden = new SFMTestScreenPanel("hidden");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.stack(
                        0,
                        SFMWorkspaceLayout.panel(left),
                        SFMWorkspaceLayout.panel(hidden)
                ),
                SFMWorkspaceLayout.panel(right)
        ));
        SFMWorkspacePanelId leftId = layout.panels().stream()
                .filter(entry -> entry.panel() == left).findFirst().orElseThrow().id();
        SFMWorkspacePanelId rightId = layout.panels().stream()
                .filter(entry -> entry.panel() == right).findFirst().orElseThrow().id();
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 100, 40);
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> originalBounds = layout.bounds(viewport, 0);
        List<SFMWorkspaceLayout.PanelEntry> originalPanels = layout.panels();
        List<SFMWorkspaceLayout.PanelEntry> originalSlot = layout.slotEntries(leftId);
        SFMWorkspacePanelId originalFocus = layout.focusedPanel();

        assertTrue(layout.canResize(leftId, SFMWorkspaceSide.RIGHT, viewport, 0, 45));
        assertEquals(originalBounds, layout.bounds(viewport, 0));
        assertEquals(originalPanels, layout.panels());
        assertEquals(originalSlot, layout.slotEntries(leftId));
        assertEquals(originalFocus, layout.focusedPanel());

        assertTrue(layout.resize(leftId, SFMWorkspaceSide.RIGHT, viewport, 0, 45));
        assertEquals(55, layout.bounds(viewport, 0).get(leftId).width());
        assertEquals(45, layout.bounds(viewport, 0).get(rightId).width());

        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> minimumBound = layout.bounds(viewport, 0);
        assertFalse(layout.canResize(leftId, SFMWorkspaceSide.RIGHT, viewport, 0, 45));
        assertFalse(layout.canResize(leftId, SFMWorkspaceSide.LEFT, viewport, 0, 45));
        assertFalse(layout.canResize(new SFMWorkspacePanelId(999), SFMWorkspaceSide.RIGHT, viewport, 0, 45));
        assertEquals(minimumBound, layout.bounds(viewport, 0));
        assertEquals(originalPanels, layout.panels());
        assertEquals(originalSlot, layout.slotEntries(leftId));
        assertEquals(originalFocus, layout.focusedPanel());
    }
}
