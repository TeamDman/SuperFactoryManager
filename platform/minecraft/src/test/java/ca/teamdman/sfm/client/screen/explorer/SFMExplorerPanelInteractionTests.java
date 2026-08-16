package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerPanelInteractionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMScreenPanelBounds BOUNDS = new SFMScreenPanelBounds(0, 0, 320, 180);

    @Test
    public void locationFilterAndBodyOwnExactlyOneFocusedChromeAtATime() {
        Fixture fixture = fixture(20);
        SFMExplorerPanel panel = fixture.panel();

        assertEquals(new SFMExplorerPanel.FocusChrome(false, false, true), panel.focusChrome(true));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(new SFMExplorerPanel.FocusChrome(true, false, false), panel.focusChrome(true));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(new SFMExplorerPanel.FocusChrome(false, true, false), panel.focusChrome(true));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals(new SFMExplorerPanel.FocusChrome(false, false, true), panel.focusChrome(true));
        assertEquals(new SFMExplorerPanel.FocusChrome(false, false, false), panel.focusChrome(false));

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
    public void filterControlEmitsSelfContainedSetAndClearActions() {
        Fixture fixture = fixture(4);
        SFMExplorerPanel panel = fixture.panel();
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL));
        assertTrue(panel.filterControlHasKeyboardFocus());
        assertFalse(panel.charTyped('f', GLFW.GLFW_MOD_CONTROL),
                "command chords must not leak characters into the filter query");
        assertTrue(panel.charTyped('s', 0));
        assertTrue(panel.charTyped('t', 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        assertTrue(panel.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0));

        assertEquals(List.of(
                "sfm action invoke sfm:explorer/filter/set id(interaction) s",
                "sfm action invoke sfm:explorer/filter/set id(interaction) st",
                "sfm action invoke sfm:explorer/filter/set id(interaction) s",
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
        return new Fixture(session, panel, actions);
    }

    private record Fixture(
            SFMExplorerSession session,
            SFMExplorerPanel panel,
            List<String> actions
    ) {
    }
}
