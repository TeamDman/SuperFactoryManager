package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerPanelInteractionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 320, 180);

    @Test
    public void unavailableRevealControlIsAbsentFromTheFocusCycle() {
        Fixture fixture = fixture(20);
        SFMExplorerPanel panel = fixture.panel();

        assertEquals(new SFMExplorerPanel.FocusChrome(false, false, false, false, true), panel.focusChrome(true));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(new SFMExplorerPanel.FocusChrome(true, false, false, false, false), panel.focusChrome(true));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(new SFMExplorerPanel.FocusChrome(false, false, false, true, false), panel.focusChrome(true));
        assertFalse(panel.revealControlHasKeyboardFocus());
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertTrue(panel.findControlHasKeyboardFocus());
        assertEquals(ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations.EXPLORER_FIND, panel.keyboardUsageSituationId());
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(new SFMExplorerPanel.FocusChrome(false, false, false, false, true), panel.focusChrome(true));
        assertEquals(new SFMExplorerPanel.FocusChrome(false, false, false, false, false), panel.focusChrome(false));

        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT));
        assertTrue(panel.findControlHasKeyboardFocus());
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT));
        assertTrue(panel.filterControlHasKeyboardFocus());
        assertFalse(panel.locationControlHasKeyboardFocus());
        assertFalse(panel.bodyControlHasKeyboardFocus());
        assertTrue(panel.narration().getString().contains("Filter control focused"));
    }

    @Test
    public void eachWheelCallbackMutatesOneOrderedStepImmediatelyAndTheNextFrameObservesAll() {
        Fixture fixture = fixture(24);
        SFMExplorerPanel panel = fixture.panel();
        SFMExplorerPanelViewport.Rect body = panel.model().state(BOUNDS).viewport().layout().body();
        double x = body.x() + Math.max(0, body.width() / 2D);
        double y = body.y() + Math.max(0, body.height() / 2D);

        for (int expected = 1; expected <= 3; expected++) {
            assertTrue(panel.mouseScrolled(x, y, -1));
            assertEquals(expected, fixture.session().snapshot().scrollOffset(),
                    "wheel callbacks must not wait for a later callback or timer");
            assertEquals(expected, panel.model().state(BOUNDS).viewport().scrollRow());
        }

        List<SFMExplorerPanelModel.ScrollTrace> beforeFrame = panel.model().scrollTraceSnapshot();
        assertEquals(List.of(1L, 2L, 3L), beforeFrame.stream()
                .map(SFMExplorerPanelModel.ScrollTrace::sequence).toList());
        assertEquals(List.of(0, 1, 2), beforeFrame.stream()
                .map(SFMExplorerPanelModel.ScrollTrace::beforeRow).toList());
        assertEquals(List.of(1, 2, 3), beforeFrame.stream()
                .map(SFMExplorerPanelModel.ScrollTrace::afterRow).toList());
        assertTrue(beforeFrame.stream().allMatch(trace -> trace.eventToModelNanos() >= 0));
        assertTrue(beforeFrame.stream().allMatch(trace -> trace.firstObservedFrame().isEmpty()));
        assertTrue(beforeFrame.stream().allMatch(trace -> trace.firstObservedNanoTime().isEmpty()));
        assertTrue(beforeFrame.stream().allMatch(trace -> trace.modelToFrameNanos().isEmpty()));
        assertTrue(beforeFrame.stream().allMatch(trace -> trace.eventToFrameNanos().isEmpty()));

        panel.model().observeVisibleFrame(panel.model().state(BOUNDS).viewport().scrollRow());
        List<SFMExplorerPanelModel.ScrollTrace> afterFrame = panel.model().scrollTraceSnapshot();
        assertTrue(afterFrame.stream().allMatch(trace -> trace.firstObservedFrame().equals(Optional.of(1L))));
        assertTrue(afterFrame.stream().allMatch(trace -> trace.firstObservedNanoTime().isPresent()));
        assertTrue(afterFrame.stream().allMatch(trace -> trace.modelToFrameNanos().orElseThrow() >= 0));
        assertTrue(afterFrame.stream().allMatch(trace -> trace.eventToFrameNanos().orElseThrow() >= 0));
        assertTrue(afterFrame.stream().allMatch(trace -> trace.observedScrollRow().equals(Optional.of(3))));
    }

    @Test
    public void oneLargerWheelDeltaRetainsItsMagnitudeAsOneOrderedCallback() {
        Fixture fixture = fixture(30);
        SFMExplorerPanel panel = fixture.panel();
        SFMExplorerPanelViewport.Rect body = panel.model().state(BOUNDS).viewport().layout().body();

        assertTrue(panel.mouseScrolled(body.x() + 1, body.y() + 1, -3));
        assertEquals(3, fixture.session().snapshot().scrollOffset());
        SFMExplorerPanelModel.ScrollTrace trace = panel.model().scrollTraceSnapshot().get(0);
        assertEquals(3, trace.requestedDelta());
        assertEquals(0, trace.beforeRow());
        assertEquals(3, trace.afterRow());
    }

    @Test
    public void scrollingReusesTheMaterializedProjectionAndMovesAVisibleScrollbar() {
        Fixture fixture = fixture(30);
        SFMExplorerPanel panel = fixture.panel();
        SFMExplorerPanelModel.State before = panel.model().state(BOUNDS);
        SFMExplorerPanel.ScrollbarGeometry beforeScrollbar =
                SFMExplorerPanel.scrollbarGeometry(before).orElseThrow();

        panel.model().scrollRows(3, BOUNDS);
        SFMExplorerPanelModel.State after = panel.model().state(BOUNDS);
        SFMExplorerPanel.ScrollbarGeometry afterScrollbar =
                SFMExplorerPanel.scrollbarGeometry(after).orElseThrow();

        assertSame(before.projection(), after.projection(),
                "cursor and scroll changes must not rebuild and resort the materialized tree");
        assertEquals(beforeScrollbar.track(), afterScrollbar.track());
        assertTrue(afterScrollbar.thumb().y() > beforeScrollbar.thumb().y());
        assertEquals(beforeScrollbar.thumb().height(), afterScrollbar.thumb().height());
    }

    @Test
    public void productionCommentsScrollingPublishesEveryWheelDeltaWithoutReprojectingWhileWorkIsPending() {
        ProductionCommentsFixture fixture = productionCommentsFixture(129);
        SFMExplorerPanel panel = fixture.panel();
        SFMExplorerPanelModel.State warmed = panel.model().state(BOUNDS);
        SFMExplorerPanelModel.ProjectionWorkTelemetry warmedTelemetry =
                panel.model().projectionWorkTelemetry();
        SFMExplorerPanelViewport.Rect body = warmed.viewport().layout().body();
        int capacity = warmed.viewport().capacity();

        assertEquals(130, warmed.projection().rows().size(),
                "129 comments plus one inline loading row model the observed Comments lens");
        assertTrue(fixture.session().activeRequestCount() > 0,
                "the wheel proof must run while review child work remains pending");
        assertEquals(1, warmedTelemetry.inlineLoadingBuilds());
        assertTrue(capacity > 0);
        assertTrue(warmed.viewport().cells().size() <= capacity,
                "the viewport must remain bounded instead of rendering the full comment corpus");

        for (int expected = 1; expected <= 12; expected++) {
            assertTrue(panel.mouseScrolled(body.x() + 2, body.y() + 2, -1));
            assertEquals(expected, fixture.session().snapshot().scrollOffset(),
                    "each unlocked-wheel callback must publish immediately and independently");
            SFMExplorerPanelModel.State observed = panel.model().state(BOUNDS);
            assertEquals(expected, observed.viewport().scrollRow());
            assertSame(warmed.projection(), observed.projection(),
                    "pending review work must not recreate the full decorated comment projection");
            assertTrue(observed.viewport().cells().size() <= capacity);
        }

        SFMExplorerPanelModel.ProjectionWorkTelemetry afterWheel =
                panel.model().projectionWorkTelemetry();
        assertEquals(warmedTelemetry.projectionBuilds(), afterWheel.projectionBuilds(),
                "wheel callbacks must not rebuild or rescore the semantic corpus");
        assertEquals(warmedTelemetry.inlineLoadingBuilds(), afterWheel.inlineLoadingBuilds(),
                "a stable pending request must reuse its inline-loading projection");
        assertTrue(afterWheel.projectionCacheHits() >= warmedTelemetry.projectionCacheHits() + 24,
                "each callback and observation should take the projection-cache path");
        assertTrue(afterWheel.inlineLoadingCacheHits() >= warmedTelemetry.inlineLoadingCacheHits() + 24);
        assertEquals(130, afterWheel.latestProjectionRows());
        assertTrue(afterWheel.latestViewportCells() <= capacity);

        List<SFMExplorerPanelModel.ScrollTrace> beforeFrame = panel.model().scrollTraceSnapshot();
        assertEquals(12, beforeFrame.size(), "wheel callbacks must not be coalesced");
        assertEquals(
                java.util.stream.LongStream.rangeClosed(1, 12).boxed().toList(),
                beforeFrame.stream().map(SFMExplorerPanelModel.ScrollTrace::sequence).toList()
        );
        assertEquals(
                java.util.stream.IntStream.rangeClosed(1, 12).boxed().toList(),
                beforeFrame.stream().map(SFMExplorerPanelModel.ScrollTrace::afterRow).toList()
        );
        assertTrue(beforeFrame.stream().allMatch(trace -> trace.firstObservedFrame().isEmpty()));

        panel.model().observeVisibleFrame(12);
        List<SFMExplorerPanelModel.ScrollTrace> afterFrame = panel.model().scrollTraceSnapshot();
        assertTrue(afterFrame.stream().allMatch(trace -> trace.firstObservedFrame().equals(Optional.of(1L))));
        assertTrue(afterFrame.stream().allMatch(trace -> trace.observedScrollRow().equals(Optional.of(12))));
        assertTrue(afterFrame.stream().allMatch(trace -> trace.eventToFrameNanos().orElseThrow() >= 0));

        fixture.executor().runPending();
        fixture.pendingRequest().completion().join();
        assertEquals(0, fixture.session().activeRequestCount());
    }

    @Test
    public void explorerChromeStillRoutesTheWheelToItsPrimaryList() {
        Fixture fixture = fixture(30);
        SFMExplorerPanel panel = fixture.panel();

        assertTrue(panel.mouseScrolled(BOUNDS.width() / 2D, 2, -1));
        assertEquals(1, fixture.session().snapshot().scrollOffset());
        assertFalse(panel.mouseScrolled(BOUNDS.width() + 1, 2, -1));
        assertEquals(1, fixture.session().snapshot().scrollOffset());
    }

    @Test
    public void filterControlEmitsSelfContainedSetAndClearActions() {
        Fixture fixture = fixture(4);
        SFMExplorerPanel panel = fixture.panel();
        assertFalse(panel.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL),
                "Find/Filter focus chords are dispatched by remappable actions, not hard-coded in the panel");
        panel.focusSearch("filter");
        assertTrue(panel.filterControlHasKeyboardFocus());
        assertFalse(panel.charTyped('f', GLFW.GLFW_MOD_CONTROL),
                "command chords must not leak characters into the filter query");
        assertTrue(panel.charTyped('s', 0));
        assertTrue(panel.charTyped('t', 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0));

        assertEquals(List.of(
                "sfm action invoke sfm:explorer/filter/match id(interaction) literal false false false s",
                "sfm action invoke sfm:explorer/filter/match id(interaction) literal false false false st",
                "sfm action invoke sfm:explorer/filter/match id(interaction) literal false false false s",
                "sfm action invoke sfm:explorer/filter/clear id(interaction)"
        ), fixture.actions());
    }

    @Test
    public void repeatedDownKeyCallbacksRemainAnImmediateResponsiveControl() {
        Fixture fixture = fixture(24);
        SFMExplorerPanel panel = fixture.panel();
        List<SFMPath> orderedPaths = panel.model().state(BOUNDS).projection().rows().stream()
                .map(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row::path)
                .toList();

        for (int expected = 1; expected <= 3; expected++) {
            assertTrue(panel.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
            assertEquals(
                    orderedPaths.get(expected),
                    fixture.session().snapshot().navigationCursor().orElseThrow()
            );
        }
        assertTrue(panel.model().scrollTraceSnapshot().isEmpty(),
                "the Down-key comparison control must not masquerade as wheel telemetry");
    }

    @Test public void filterWordEditingSelectionAndUndoEmitOneSemanticUpdatePerEdit() {
        Fixture fixture = fixture(4);
        SFMExplorerPanel panel = fixture.panel();
        panel.focusSearch("filter");
        for (char c : "hello world tail".toCharArray()) panel.charTyped(c, 0);
        fixture.actions().clear();
        panel.keyPressed(GLFW.GLFW_KEY_LEFT, 0, GLFW.GLFW_MOD_CONTROL);
        panel.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        assertTrue(fixture.actions().isEmpty(), "caret movement must not requery the domain");
        panel.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, GLFW.GLFW_MOD_CONTROL);
        assertEquals(List.of("sfm action invoke sfm:explorer/filter/match id(interaction) literal false false false hello  tail"), fixture.actions());
        panel.keyPressed(GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL);
        assertEquals("sfm action invoke sfm:explorer/filter/match id(interaction) literal false false false hello world tail",
                fixture.actions().get(1));
        panel.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        panel.charTyped('x', 0);
        assertEquals("sfm action invoke sfm:explorer/filter/match id(interaction) literal false false false x", fixture.actions().get(2));
        panel.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        panel.keyPressed(GLFW.GLFW_KEY_DELETE, 0, GLFW.GLFW_MOD_CONTROL);
        assertEquals("sfm action invoke sfm:explorer/filter/clear id(interaction)", fixture.actions().get(3));
    }

    @Test
    public void filterMatchesAndContextAncestorsHaveDistinctVisualAndNarratedPresentations() {
        SFMPath matchPath = SFMPath.parse("registry://minecraft/item/minecraft/sfm_java");
        SFMExplorerEntry entry = SFMExplorerEntry.simple(
                matchPath,
                "SFM.java",
                false,
                Optional.of("minecraft:paper")
        );
        SFMExplorerProjection.Row match = new SFMExplorerProjection.Row(
                matchPath,
                entry,
                1,
                false,
                false,
                entry.sortKey(SFMExplorerEntry.SORT_NAME),
                SFMExplorerProjection.FilterRole.MATCH
        );
        SFMExplorerProjection.Row context = new SFMExplorerProjection.Row(
                matchPath,
                entry,
                1,
                false,
                true,
                entry.sortKey(SFMExplorerEntry.SORT_NAME),
                SFMExplorerProjection.FilterRole.CONTEXT_ANCESTOR
        );

        SFMExplorerPanel.FilterRowPresentation matchPresentation =
                SFMExplorerPanel.filterRowPresentation(match, entry.label());
        SFMExplorerPanel.FilterRowPresentation contextPresentation =
                SFMExplorerPanel.filterRowPresentation(context, entry.label());

        assertEquals("SFM.java", matchPresentation.label());
        assertEquals("filter match", matchPresentation.narration());
        assertEquals("[context] SFM.java", contextPresentation.label());
        assertEquals(
                "context ancestor included to locate a filter match",
                contextPresentation.narration()
        );
        assertNotEquals(matchPresentation.textColour(), contextPresentation.textColour());

        SFMExplorerProjection.FilterEvidence evidence = new SFMExplorerProjection.FilterEvidence(
                "SFM.java",
                8,
                2,
                5,
                3,
                true
        );
        assertEquals(
                "2 matches + 3 context ancestors + 0 context children = 5 visible / 8 materialized entries; "
                        + "unmaterialized subtrees excluded",
                SFMExplorerPanel.filterSummary(evidence)
        );
        assertEquals(
                "Filter showing 2 matches and 3 context ancestors and 0 context children, "
                        + "5 visible rows from 8 materialized "
                        + "entries. Unmaterialized subtrees are excluded",
                SFMExplorerPanel.filterNarration(evidence)
        );
    }

    @Test
    public void activeFilterNarrationReportsCountsAndTheSelectedRowsRole() {
        Fixture fixture = fixture(4);
        fixture.session().setHoist(SFMExplorerProjection.Hoist.SHOW_ROOTS);
        fixture.session().setFilterQuery("3");

        fixture.session().navigateTo(ROOT);
        String contextNarration = fixture.panel().narration().getString();
        assertTrue(contextNarration.contains(
                "Filter showing 1 match and 1 context ancestor and 0 context children, 2 visible rows"
        ));
        assertTrue(contextNarration.contains("context ancestor included to locate a filter match"));

        fixture.session().navigateTo(SFMPath.parse("registry://minecraft/item/minecraft/test_item_3"));
        String matchNarration = fixture.panel().narration().getString();
        assertTrue(matchNarration.contains("filter match"));
        assertFalse(matchNarration.contains("context ancestor included to locate a filter match"));
    }

    @Test
    public void ordinaryResolverFilterChangesDoNotRetainAnOldProjectionForever() {
        Fixture fixture = fixture(4);
        fixture.session().setFilterQuery("1");
        var before = fixture.panel().model().state(BOUNDS);
        assertTrue(before.projection().rows().stream().anyMatch(row -> row.path().canonical().endsWith("test_item_1")));
        fixture.session().setFilterQuery("2");
        var after = fixture.panel().model().state(BOUNDS);
        assertTrue(after.projection().rows().stream().anyMatch(row -> row.path().canonical().endsWith("test_item_2")),
                "a resolver without an async complete-filter domain is not perpetually pending");
        assertFalse(after.projection().rows().stream().anyMatch(row -> row.path().canonical().endsWith("test_item_1")));
    }

    @Test
    public void stableFilteredFramesAndWheelCallbacksDoNotComposeTheFilterDomain() {
        Fixture fixture = fixture(30);
        fixture.session().setFilterQuery("item");
        var warmed = fixture.panel().model().state(BOUNDS);
        long requests = fixture.loader().filterProjectionRequestCount();
        assertTrue(requests > 0);
        for (int index = 0; index < 12; index++) {
            fixture.panel().model().scrollRows(1, BOUNDS);
            assertSame(warmed.projection(), fixture.panel().model().state(BOUNDS).projection());
        }
        assertEquals(requests, fixture.loader().filterProjectionRequestCount(),
                "a cached final projection must not rebuild its input filter domain");
    }

    @Test
    public void virtualPointerModifiersDriveRangeToggleAndRightClickPreservesMembers() {
        Fixture fixture = fixture(4);
        var panel = fixture.panel();
        var cells = panel.model().state(BOUNDS).viewport().cells();
        var a = cells.get(0).row().path();
        var b = cells.get(1).row().path();
        var c = cells.get(2).row().path();
        click(panel, cells.get(0), 0, 0);
        click(panel, cells.get(2), 0, GLFW.GLFW_MOD_CONTROL);
        assertEquals(java.util.Set.of(a,c), fixture.session().selectedPaths());
        click(panel, cells.get(2), 0, GLFW.GLFW_MOD_SHIFT);
        assertEquals(java.util.Set.of(a,b,c), fixture.session().selectedPaths());
        click(panel, cells.get(2), 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        assertTrue(fixture.session().selectedPaths().isEmpty());
        assertTrue(panel.model().state(BOUNDS).selectedPaths().isEmpty(), "render must not repopulate an empty selection");
        click(panel, cells.get(0), 0, 0);
        click(panel, cells.get(2), 0, GLFW.GLFW_MOD_CONTROL);
        click(panel, cells.get(0), 1, 0);
        assertEquals(java.util.Set.of(a,c), fixture.session().selectedPaths());
        assertEquals(Optional.of(a), fixture.session().snapshot().navigationCursor());
        assertTrue(ca.teamdman.sfm.client.input.SFMPointerInputModifiers.current().isEmpty());
        assertFalse(fixture.actions().stream().anyMatch(action -> action.contains("path/open")), "modified clicks do not double-open");
    }

    @Test
    public void keyboardRangeAndCursorNavigationKeepHiddenMembershipAndPrimaryDistinct() {
        Fixture fixture = fixture(6);
        var panel = fixture.panel();
        var rows = panel.model().state(BOUNDS).projection().rows();
        panel.selectRow("replace", rows.get(1).path());
        panel.keyPressed(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_SHIFT);
        assertEquals(java.util.Set.of(rows.get(1).path(), rows.get(2).path()), fixture.session().selectedPaths());
        panel.keyPressed(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_CONTROL);
        assertEquals(Optional.of(rows.get(3).path()), fixture.session().snapshot().navigationCursor());
        assertEquals(2, fixture.session().selectedPaths().size());
        fixture.session().setFilterOptions(ca.teamdman.sfm.client.search.SFMTextMatchOptions.defaults());
        fixture.session().setFilterQuery("no such item");
        assertEquals(2, panel.model().state(BOUNDS).selectedPaths().size());
        fixture.session().setFilterQuery("");
        assertEquals(2, panel.model().state(BOUNDS).selectedPaths().size());
        var inspected = SFMExplorerRowInspection.capture(fixture.session().snapshot(), rows.get(1),
                fixture.loader().relationSnapshot(), List.of(), true, "body");
        assertTrue(inspected.selected());
        assertTrue(inspected.detailsPayload().contains("selection-count: 2"));
        assertTrue(inspected.detailsPayload().contains("row-is-primary: false"));
        assertTrue(inspected.detailsPayload().contains("not an implicit bulk operation"));
    }

    @Test
    public void additiveAndAllFindUseCompleteScopedMembershipAndEnterReplaces() throws Exception {
        Fixture fixture = fixture(6);
        var panel = fixture.panel();
        panel.setFindMaterializedOnly(true);
        panel.focusSearch("find");
        for (char ch : "Test Item".toCharArray()) panel.charTyped(ch, 0);
        awaitSearch(panel);
        int before = fixture.session().selectedPaths().size();
        panel.selectSearchMatches(false);
        awaitMembers(panel, fixture.session(), before + 1);
        panel.selectSearchMatches(true);
        assertEquals(6, fixture.session().selectedPaths().size());
        panel.moveSearch(-1, true);
        awaitMembers(panel, fixture.session(), 1);
        assertFalse(panel.narration().getString().contains("Selected all"), "old action feedback is not current selection state");
        var selected = fixture.session().selectedPaths();
        panel.clearSearch("find");
        panel.toggleSearchOption("find", "regex");
        panel.charTyped('[', 0);
        panel.selectSearchMatches(true);
        long until = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < until && !panel.narration().getString().contains("unavailable")) { panel.tick(); Thread.sleep(5); }
        assertEquals(selected, fixture.session().selectedPaths(), "invalid/partial search cannot claim select-all");
    }

    private static void click(SFMExplorerPanel panel, SFMExplorerPanelViewport.Cell cell, int button, int modifiers) {
        assertTrue(ca.teamdman.sfm.client.input.SFMPointerInputModifiers.during(modifiers,
                () -> panel.mouseClicked(cell.bounds().x() + cell.bounds().width() - 20, cell.bounds().y() + 5, button)));
    }

    private static void awaitSearch(SFMExplorerPanel panel) throws Exception {
        long until = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < until) {
            panel.tick();
            if (panel.narration().getString().contains("6 matches")) return;
            Thread.sleep(5);
        }
        throw new AssertionError(panel.narration().getString());
    }

    private static void awaitMembers(SFMExplorerPanel panel, SFMExplorerSession session, int count) throws Exception {
        long until = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < until && session.selectedPaths().size() != count) { panel.tick(); Thread.sleep(5); }
        assertEquals(count, session.selectedPaths().size(), panel.narration().getString());
    }

    private static Fixture fixture(int childCount) {
        ArrayList<SFMPath> children = new ArrayList<>();
        ArrayList<SFMInMemoryRegistryExplorerResolver.Node> nodes = new ArrayList<>();
        nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(ROOT, "Items", true, Optional.of("minecraft:chest")),
                children
        ));
        for (int index = 0; index < childCount; index++) {
            SFMPath child = SFMPath.parse("registry://minecraft/item/minecraft/test_item_" + index);
            children.add(child);
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                    SFMExplorerEntry.simple(
                            child,
                            "Test Item " + index,
                            false,
                            Optional.of("minecraft:paper")
                    ),
                    List.of()
            ));
        }
        // The root node captures a mutable list above; rebuild it with the completed immutable child set.
        nodes.set(0, new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(ROOT, "Items", true, Optional.of("minecraft:chest")),
                List.copyOf(children)
        ));
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                nodes,
                Runnable::run,
                Math.max(32, childCount)
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        loader.openRoot(ROOT).join();
        loader.refresh(ROOT, Math.max(32, childCount)).completion().join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("interaction"), ROOT, new SFMSelectionRepository()
        );
        ArrayList<String> actions = new ArrayList<>();
        SFMExplorerPanel panel = new SFMExplorerPanel(
                session,
                loader,
                actions::add,
                () -> {},
                () -> {},
                SFMExplorerPresentationRegistry.minecraftDefaults(),
                ignored -> {}
        );
        panel.resized(null, BOUNDS);
        return new Fixture(session, panel, actions, loader);
    }

    private static ProductionCommentsFixture productionCommentsFixture(int commentCount) {
        SFMPath root = SFMPath.parse("registry://sfm/review-comments/");
        ArrayList<SFMPath> comments = new ArrayList<>();
        ArrayList<SFMInMemoryRegistryExplorerResolver.Node> nodes = new ArrayList<>();
        for (int index = 0; index < commentCount; index++) {
            String suffix = String.format("%03d", index);
            SFMPath comment = SFMPath.parse("registry://sfm/review-comments/comment-" + suffix);
            SFMPath value = SFMPath.parse(comment.canonical() + "/value");
            SFMPath selector = SFMPath.parse(comment.canonical() + "/selector");
            SFMPath matches = SFMPath.parse(comment.canonical() + "/matches");
            SFMPath provenance = SFMPath.parse(comment.canonical() + "/provenance");
            comments.add(comment);
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                    SFMExplorerEntry.simple(
                            comment,
                            "#approved Review comment " + suffix,
                            true,
                            Optional.of("minecraft:writable_book")
                    ),
                    List.of(value, selector, matches, provenance)
            ));
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                    SFMExplorerEntry.simple(value, "value", false, Optional.of("minecraft:paper")),
                    List.of()
            ));
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                    SFMExplorerEntry.simple(selector, "selector", false, Optional.of("minecraft:compass")),
                    List.of()
            ));
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                    SFMExplorerEntry.simple(matches, "matches (1)", false, Optional.of("minecraft:target")),
                    List.of()
            ));
            nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                    SFMExplorerEntry.simple(provenance, "provenance", false, Optional.of("minecraft:name_tag")),
                    List.of()
            ));
        }
        nodes.add(new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(root, "Release Review Comments", true, Optional.of("minecraft:chest")),
                List.copyOf(comments)
        ));
        PausableExecutor executor = new PausableExecutor();
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                nodes,
                executor,
                Math.max(256, commentCount)
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        loader.openRoot(root).join();
        loader.refresh(root, commentCount).completion().join();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("production-comments"), root, new SFMSelectionRepository()
        );
        SFMPath pendingParent = comments.get(0);
        session.expand(pendingParent);
        executor.pause();
        SFMLazyExplorerLoader.LoadHandle pendingRequest = session.requestChildren(
                pendingParent,
                loader,
                4
        );
        SFMExplorerPanel panel = new SFMExplorerPanel(
                session,
                loader,
                ignored -> {},
                () -> {},
                () -> {},
                SFMExplorerPresentationRegistry.minecraftDefaults(),
                ignored -> {}
        );
        panel.resized(null, BOUNDS);
        return new ProductionCommentsFixture(session, panel, executor, pendingRequest);
    }

    private record Fixture(
            SFMExplorerSession session,
            SFMExplorerPanel panel,
            List<String> actions,
            SFMLazyExplorerLoader loader
    ) {
    }

    private record ProductionCommentsFixture(
            SFMExplorerSession session,
            SFMExplorerPanel panel,
            PausableExecutor executor,
            SFMLazyExplorerLoader.LoadHandle pendingRequest
    ) {
    }

    private static final class PausableExecutor implements Executor {
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        private boolean paused;

        @Override
        public void execute(Runnable command) {
            if (paused) {
                pending.addLast(command);
            } else {
                command.run();
            }
        }

        private void pause() {
            paused = true;
        }

        private void runPending() {
            paused = false;
            while (!pending.isEmpty()) pending.removeFirst().run();
        }
    }
}
