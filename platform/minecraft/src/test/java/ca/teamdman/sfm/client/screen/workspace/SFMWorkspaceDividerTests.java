package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMWorkspaceDividerTests {
    private static final SFMScreenPanelBounds VIEWPORT = new SFMScreenPanelBounds(0, 0, 202, 102);
    private static final int DIVIDER = 2;
    private static final int HIT_SLOP = 3;
    private static final int MINIMUM = 20;

    @Test
    void twoPanelCaptureUsesStableIdentityAbsoluteDeltasAndMinimumClamping() {
        SFMScreenPanel left = new SFMTestScreenPanel("left");
        SFMScreenPanel right = new SFMTestScreenPanel("right");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = layout.panels().get(0).id();
        SFMWorkspacePanelId rightId = layout.panels().get(1).id();
        SFMWorkspacePanelId focus = layout.focusedPanel();
        SFMWorkspaceDivider divider = onlyDivider(layout);

        assertEquals(new SFMScreenPanelBounds(100, 0, 2, 102), divider.lineBounds());
        assertEquals(new SFMScreenPanelBounds(97, 0, 8, 102), divider.hitBounds());
        assertEquals(20, divider.minimumPosition());
        assertEquals(180, divider.maximumPosition());
        assertEquals(divider.id(), SFMWorkspaceDividerId.parse(divider.id().toString()));

        SFMWorkspaceLayout.DividerResizeSession session = capture(layout, divider.id());
        SFMWorkspaceDividerResizeResult clamped = layout.updateDividerResize(session, 500, 0);
        assertEquals(SFMWorkspaceDividerResizeResult.Status.CLAMPED, clamped.status());
        assertEquals(80, clamped.appliedDeltas().get(divider.id()));
        assertEquals(180, clamped.afterBounds().get(leftId).width());
        assertEquals(20, clamped.afterBounds().get(rightId).width());

        SFMWorkspaceDividerResizeResult reversedFromOriginal = layout.updateDividerResize(session, -30, 0);
        assertEquals(70, reversedFromOriginal.afterBounds().get(leftId).width());
        assertEquals(130, reversedFromOriginal.afterBounds().get(rightId).width());
        assertTrue(layout.cancelDividerResize(session));
        assertEquals(100, layout.bounds(VIEWPORT, DIVIDER).get(leftId).width());
        assertEquals(100, layout.bounds(VIEWPORT, DIVIDER).get(rightId).width());
        assertEquals(focus, layout.focusedPanel());
        assertSame(left, layout.panel(leftId));
        assertSame(right, layout.panel(rightId));
    }

    @Test
    void unequalSharesAndNestedSameAxisMinimumsRemainConstrained() {
        SFMScreenPanel left = new SFMTestScreenPanel("left");
        SFMScreenPanel right = new SFMTestScreenPanel("right");
        SFMWorkspaceLayout unequal = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspacePanelId leftId = id(unequal, left);
        SFMWorkspacePanelId rightId = id(unequal, right);
        assertTrue(unequal.configurePanel(leftId, 3.0, MINIMUM));
        assertTrue(unequal.configurePanel(rightId, 1.0, MINIMUM));
        SFMWorkspaceDivider unequalDivider = onlyDivider(unequal);
        assertEquals(140, unequalDivider.beforePixels());
        assertEquals(60, unequalDivider.afterPixels());

        SFMWorkspaceLayout.DividerResizeSession unequalSession = capture(unequal, unequalDivider.id());
        SFMWorkspaceDividerResizeResult unequalClamp = unequal.updateDividerResize(unequalSession, -500, 0);
        assertEquals(SFMWorkspaceDividerResizeResult.Status.CLAMPED, unequalClamp.status());
        assertEquals(MINIMUM, unequalClamp.afterBounds().get(leftId).width());
        assertEquals(180, unequalClamp.afterBounds().get(rightId).width());

        SFMScreenPanel outerLeft = new SFMTestScreenPanel("outer-left");
        SFMScreenPanel middle = new SFMTestScreenPanel("middle");
        SFMScreenPanel outerRight = new SFMTestScreenPanel("outer-right");
        SFMScreenPanel hidden = new SFMTestScreenPanel("hidden");
        SFMWorkspaceLayout nested = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(outerLeft),
                SFMWorkspaceLayout.stack(0,
                        SFMWorkspaceLayout.horizontal(
                                SFMWorkspaceLayout.panel(middle),
                                SFMWorkspaceLayout.panel(outerRight)),
                        SFMWorkspaceLayout.panel(hidden))));
        SFMScreenPanelBounds nestedViewport = new SFMScreenPanelBounds(0, 0, 404, 100);
        SFMWorkspaceDivider outerDivider = nested.dividers(
                        nestedViewport, DIVIDER, HIT_SLOP, MINIMUM).stream()
                .filter(divider -> divider.id().nodePath().equals("r"))
                .findFirst().orElseThrow();
        SFMWorkspaceLayout.DividerResizeSession nestedSession = nested.captureDividerResize(
                List.of(outerDivider.id()), nestedViewport, DIVIDER, HIT_SLOP, MINIMUM).orElseThrow();
        SFMWorkspaceDividerResizeResult nestedClamp = nested.updateDividerResize(nestedSession, 500, 0);

        assertEquals(SFMWorkspaceDividerResizeResult.Status.CLAMPED, nestedClamp.status());
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> nestedBounds = nestedClamp.afterBounds();
        assertEquals(MINIMUM, nestedBounds.get(id(nested, middle)).width());
        assertEquals(MINIMUM, nestedBounds.get(id(nested, outerRight)).width());
        assertSame(hidden, nested.panel(id(nested, hidden)), "hidden stack content must remain attached");
    }

    @Test
    void tJunctionCapturesOneDividerPerOrthogonalAxisAndResizesBoth() {
        SFMScreenPanel left = new SFMTestScreenPanel("left");
        SFMScreenPanel top = new SFMTestScreenPanel("top");
        SFMScreenPanel bottom = new SFMTestScreenPanel("bottom");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(left),
                SFMWorkspaceLayout.vertical(
                        SFMWorkspaceLayout.panel(top),
                        SFMWorkspaceLayout.panel(bottom))));
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 302, 202);
        SFMWorkspaceDivider horizontalMovement = layout.dividers(viewport, DIVIDER, HIT_SLOP, MINIMUM)
                .stream().filter(candidate -> candidate.axis() == SFMWorkspaceAxis.HORIZONTAL)
                .findFirst().orElseThrow();
        SFMWorkspaceDivider verticalMovement = layout.dividers(viewport, DIVIDER, HIT_SLOP, MINIMUM)
                .stream().filter(candidate -> candidate.axis() == SFMWorkspaceAxis.VERTICAL)
                .findFirst().orElseThrow();
        SFMWorkspaceDividerHit hit = layout.hitTestDividers(
                horizontalMovement.position(), verticalMovement.position(),
                viewport, DIVIDER, HIT_SLOP, MINIMUM);

        assertEquals(SFMWorkspaceDividerCursor.RESIZE_BOTH, hit.cursor());
        assertEquals(2, hit.dividers().size());
        SFMWorkspaceLayout.DividerResizeSession session = layout.captureDividerResize(
                hit.dividers().stream().map(SFMWorkspaceDivider::id).toList(),
                viewport, DIVIDER, HIT_SLOP, MINIMUM).orElseThrow();
        layout.updateDividerResize(session, 20, 10);
        layout.finishDividerResize(session);

        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = layout.bounds(viewport, DIVIDER);
        SFMWorkspacePanelId leftId = id(layout, left);
        SFMWorkspacePanelId topId = id(layout, top);
        SFMWorkspacePanelId bottomId = id(layout, bottom);
        assertEquals(170, bounds.get(leftId).width());
        assertEquals(130, bounds.get(topId).width());
        assertEquals(110, bounds.get(topId).height());
        assertEquals(90, bounds.get(bottomId).height());
    }

    @Test
    void fourPaneIntersectionUsesExplicitLinksButCoincidenceAloneDoesNot() {
        FourPane fixture = fourPane();
        List<SFMWorkspaceDivider> dividers = fixture.layout.dividers(
                fixture.viewport, DIVIDER, HIT_SLOP, MINIMUM);
        List<SFMWorkspaceDividerId> columnDividers = dividers.stream()
                .filter(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL)
                .map(SFMWorkspaceDivider::id)
                .toList();
        SFMWorkspaceDivider rowDivider = dividers.stream()
                .filter(divider -> divider.axis() == SFMWorkspaceAxis.VERTICAL)
                .findFirst().orElseThrow();

        SFMWorkspaceDividerHit coincident = fixture.layout.hitTestDividers(
                dividers.stream().filter(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL)
                        .findFirst().orElseThrow().position(),
                rowDivider.position(),
                fixture.viewport, DIVIDER, HIT_SLOP, MINIMUM);
        assertEquals(2, coincident.dividers().size(),
                "coincident same-axis dividers must not become implicitly linked");

        assertTrue(fixture.layout.linkDividers(
                new SFMWorkspaceDividerLinkId("four-pane-columns"), columnDividers));
        SFMWorkspaceDividerHit linked = fixture.layout.hitTestDividers(
                dividers.stream().filter(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL)
                        .findFirst().orElseThrow().position(),
                rowDivider.position(),
                fixture.viewport, DIVIDER, HIT_SLOP, MINIMUM);
        assertEquals(SFMWorkspaceDividerCursor.RESIZE_BOTH, linked.cursor());
        assertEquals(3, linked.dividers().size());

        SFMWorkspaceLayout.DividerResizeSession session = fixture.layout.captureDividerResize(
                linked.dividers().stream().map(SFMWorkspaceDivider::id).toList(),
                fixture.viewport, DIVIDER, HIT_SLOP, MINIMUM).orElseThrow();
        fixture.layout.updateDividerResize(session, 20, 10);
        fixture.layout.finishDividerResize(session);
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds = fixture.layout.bounds(fixture.viewport, DIVIDER);
        assertEquals(170, bounds.get(id(fixture.layout, fixture.topLeft)).width());
        assertEquals(130, bounds.get(id(fixture.layout, fixture.topRight)).width());
        assertEquals(170, bounds.get(id(fixture.layout, fixture.bottomLeft)).width());
        assertEquals(130, bounds.get(id(fixture.layout, fixture.bottomRight)).width());
        assertEquals(110, bounds.get(id(fixture.layout, fixture.topLeft)).height());
        assertEquals(90, bounds.get(id(fixture.layout, fixture.bottomLeft)).height());
    }

    @Test
    void linkedDividersUseOneSharedClampWhenMemberMinimumsDiffer() {
        FourPane fixture = fourPane();
        SFMWorkspacePanelId topLeftId = id(fixture.layout, fixture.topLeft);
        SFMWorkspacePanelId topRightId = id(fixture.layout, fixture.topRight);
        assertTrue(fixture.layout.configurePanel(topLeftId, 15.0, 1));
        assertTrue(fixture.layout.configurePanel(topRightId, 1.0, 140));
        List<SFMWorkspaceDivider> columns = fixture.layout.dividers(
                        fixture.viewport, DIVIDER, HIT_SLOP, MINIMUM).stream()
                .filter(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL)
                .toList();
        List<SFMWorkspaceDividerId> columnDividers = columns.stream().map(SFMWorkspaceDivider::id).toList();
        int sharedMaximum = columns.stream().mapToInt(SFMWorkspaceDivider::maximumDelta).min().orElseThrow();
        assertTrue(sharedMaximum >= 0 && sharedMaximum < 50, "fixture must exercise asymmetric clamping");
        assertTrue(fixture.layout.linkDividers(
                new SFMWorkspaceDividerLinkId("asymmetric-columns"), columnDividers));

        SFMWorkspaceLayout.DividerResizeSession session = fixture.layout.captureDividerResize(
                List.of(columnDividers.get(0)), fixture.viewport, DIVIDER, HIT_SLOP, MINIMUM).orElseThrow();
        SFMWorkspaceDividerResizeResult result = fixture.layout.updateDividerResize(session, 50, 0);
        fixture.layout.finishDividerResize(session);

        assertEquals(SFMWorkspaceDividerResizeResult.Status.CLAMPED, result.status());
        assertEquals(List.of(sharedMaximum, sharedMaximum), columnDividers.stream()
                .map(result.appliedDeltas()::get)
                .toList(), "an explicit link must remain geometrically aligned at the tightest minimum");
        assertEquals(
                result.beforeBounds().get(topLeftId).width() + sharedMaximum,
                result.afterBounds().get(topLeftId).width());
        SFMWorkspacePanelId bottomLeftId = id(fixture.layout, fixture.bottomLeft);
        assertEquals(
                result.beforeBounds().get(bottomLeftId).width() + sharedMaximum,
                result.afterBounds().get(bottomLeftId).width());
    }

    @Test
    void keyboardAndExplicitDividerIntentShareTheSameConstraintModel() {
        SFMWorkspaceLayout keyboard = SFMWorkspaceLayout.sideBySide(
                new SFMTestScreenPanel("keyboard-left"), new SFMTestScreenPanel("keyboard-right"));
        SFMWorkspaceLayout explicit = SFMWorkspaceLayout.sideBySide(
                new SFMTestScreenPanel("explicit-left"), new SFMTestScreenPanel("explicit-right"));
        SFMWorkspacePanelId keyboardLeft = keyboard.visiblePanels().get(0).id();
        SFMWorkspaceDivider explicitDivider = onlyDivider(explicit);

        assertTrue(keyboard.resize(
                keyboardLeft, SFMWorkspaceSide.RIGHT, VIEWPORT, DIVIDER, MINIMUM));
        SFMWorkspaceDividerResizeResult explicitResult = explicit.resizeDividers(
                new SFMWorkspaceResizeDividersIntent(List.of(explicitDivider.id()), 10, 0),
                VIEWPORT,
                DIVIDER,
                HIT_SLOP,
                MINIMUM);

        assertEquals(SFMWorkspaceDividerResizeResult.Status.APPLIED, explicitResult.status());
        assertEquals(
                keyboard.bounds(VIEWPORT, DIVIDER).values().stream().map(SFMScreenPanelBounds::width).toList(),
                explicit.bounds(VIEWPORT, DIVIDER).values().stream().map(SFMScreenPanelBounds::width).toList());
    }

    @Test
    void relinkingDividersDoesNotLeaveAFalseSingletonLink() {
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("top-left")),
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("top-right"))),
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("middle-left")),
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("middle-right"))),
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("bottom-left")),
                        SFMWorkspaceLayout.panel(new SFMTestScreenPanel("bottom-right")))));
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 302, 302);
        List<SFMWorkspaceDividerId> columns = layout.dividers(
                        viewport, DIVIDER, HIT_SLOP, MINIMUM).stream()
                .filter(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL)
                .map(SFMWorkspaceDivider::id)
                .toList();
        SFMWorkspaceDividerLinkId first = new SFMWorkspaceDividerLinkId("first");
        SFMWorkspaceDividerLinkId second = new SFMWorkspaceDividerLinkId("second");

        assertTrue(layout.linkDividers(first, columns.subList(0, 2)));
        assertTrue(layout.linkDividers(second, columns.subList(1, 3)));

        assertFalse(layout.dividerLinks().containsKey(first));
        assertEquals(columns.subList(1, 3), layout.dividerLinks().get(second));
        assertTrue(layout.dividers(viewport, DIVIDER, HIT_SLOP, MINIMUM).stream()
                .filter(divider -> divider.id().equals(columns.get(0)))
                .allMatch(divider -> divider.linkId() == null));
    }

    @Test
    void resizingPreservesStackFocusAndContentIdentity() {
        SFMScreenPanel left = new SFMTestScreenPanel("left");
        SFMScreenPanel hidden = new SFMTestScreenPanel("hidden");
        SFMScreenPanel right = new SFMTestScreenPanel("right");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.stack(0,
                        SFMWorkspaceLayout.panel(left),
                        SFMWorkspaceLayout.panel(hidden)),
                SFMWorkspaceLayout.panel(right)));
        SFMWorkspacePanelId leftId = id(layout, left);
        SFMWorkspacePanelId hiddenId = id(layout, hidden);
        SFMWorkspacePanelId rightId = id(layout, right);
        SFMWorkspaceStackId stackBefore = layout.stackId(leftId).orElseThrow();
        SFMWorkspacePanelId focusBefore = layout.focusedPanel();
        List<SFMWorkspaceLayout.PanelEntry> slotBefore = layout.slotEntries(leftId);
        SFMWorkspaceDivider divider = onlyDivider(layout);

        SFMWorkspaceLayout.DividerResizeSession session = capture(layout, divider.id());
        layout.updateDividerResize(session, 25, 0);
        layout.finishDividerResize(session);

        assertEquals(stackBefore, layout.stackId(leftId).orElseThrow());
        assertEquals(stackBefore, layout.stackId(hiddenId).orElseThrow());
        assertEquals(slotBefore, layout.slotEntries(leftId));
        assertEquals(focusBefore, layout.focusedPanel());
        assertSame(left, layout.panel(leftId));
        assertSame(hidden, layout.panel(hiddenId));
        assertSame(right, layout.panel(rightId));
    }

    @Test
    void newerLayoutMutationMakesCaptureStaleWithoutRollingItBack() {
        SFMScreenPanel left = new SFMTestScreenPanel("left");
        SFMScreenPanel right = new SFMTestScreenPanel("right");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.sideBySide(left, right);
        SFMWorkspaceDivider divider = onlyDivider(layout);
        SFMWorkspaceLayout.DividerResizeSession session = capture(layout, divider.id());
        SFMScreenPanel inserted = new SFMTestScreenPanel("inserted");
        SFMWorkspacePanelId insertedId = layout.insert(
                layout.panels().get(1).id(), SFMWorkspaceSide.RIGHT, inserted);

        assertEquals(SFMWorkspaceDividerResizeResult.Status.STALE,
                layout.updateDividerResize(session, 20, 0).status());
        assertFalse(layout.cancelDividerResize(session));
        assertSame(inserted, layout.panel(insertedId));
        assertEquals(3, layout.visiblePanels().size());
    }

    @Test
    void physicalDividerGeometryUsesFloorForStartsAndCeilForEnds() {
        SFMWorkspaceDivider logical = onlyDivider(SFMWorkspaceLayout.sideBySide(
                new SFMTestScreenPanel("left"), new SFMTestScreenPanel("right")));
        SFMWorkspaceDividerView view = SFMWorkspaceDividerView.scale(logical, VIEWPORT, 404, 204);

        assertEquals(new SFMScreenPanelBounds(200, 0, 4, 204), view.physicalLineBounds());
        assertEquals(new SFMScreenPanelBounds(194, 0, 16, 204), view.physicalHitBounds());
    }

    @Test
    void physicalDividerGeometryAccountsForViewportOffsetAndNonUniformScale() {
        SFMWorkspaceDivider logical = onlyDivider(SFMWorkspaceLayout.sideBySide(
                new SFMTestScreenPanel("left"), new SFMTestScreenPanel("right")));
        SFMWorkspaceDivider shifted = new SFMWorkspaceDivider(
                logical.id(),
                logical.axis(),
                new SFMScreenPanelBounds(110, 20, 2, 102),
                new SFMScreenPanelBounds(107, 20, 8, 102),
                logical.position(),
                logical.minimumPosition(),
                logical.maximumPosition(),
                logical.beforePixels(),
                logical.afterPixels(),
                logical.beforeMinimumPixels(),
                logical.afterMinimumPixels(),
                logical.beforeShare(),
                logical.afterShare(),
                logical.beforeTrackPanels(),
                logical.afterTrackPanels(),
                logical.linkId());

        SFMWorkspaceDividerView view = SFMWorkspaceDividerView.scale(
                shifted, new SFMScreenPanelBounds(10, 20, 202, 102), 606, 204);

        assertEquals(new SFMScreenPanelBounds(300, 0, 6, 204), view.physicalLineBounds());
        assertEquals(new SFMScreenPanelBounds(291, 0, 24, 204), view.physicalHitBounds());
    }

    private static SFMWorkspaceDivider onlyDivider(SFMWorkspaceLayout layout) {
        return layout.dividers(VIEWPORT, DIVIDER, HIT_SLOP, MINIMUM).get(0);
    }

    private static SFMWorkspaceLayout.DividerResizeSession capture(
            SFMWorkspaceLayout layout,
            SFMWorkspaceDividerId dividerId
    ) {
        return layout.captureDividerResize(
                List.of(dividerId), VIEWPORT, DIVIDER, HIT_SLOP, MINIMUM).orElseThrow();
    }

    private static SFMWorkspacePanelId id(SFMWorkspaceLayout layout, SFMScreenPanel panel) {
        return layout.panels().stream().filter(entry -> entry.panel() == panel).findFirst().orElseThrow().id();
    }

    private static FourPane fourPane() {
        SFMScreenPanel topLeft = new SFMTestScreenPanel("top-left");
        SFMScreenPanel topRight = new SFMTestScreenPanel("top-right");
        SFMScreenPanel bottomLeft = new SFMTestScreenPanel("bottom-left");
        SFMScreenPanel bottomRight = new SFMTestScreenPanel("bottom-right");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(topLeft),
                        SFMWorkspaceLayout.panel(topRight)),
                SFMWorkspaceLayout.horizontal(
                        SFMWorkspaceLayout.panel(bottomLeft),
                        SFMWorkspaceLayout.panel(bottomRight))));
        return new FourPane(
                layout,
                new SFMScreenPanelBounds(0, 0, 302, 202),
                topLeft,
                topRight,
                bottomLeft,
                bottomRight);
    }

    private record FourPane(
            SFMWorkspaceLayout layout,
            SFMScreenPanelBounds viewport,
            SFMScreenPanel topLeft,
            SFMScreenPanel topRight,
            SFMScreenPanel bottomLeft,
            SFMScreenPanel bottomRight
    ) {
    }
}
