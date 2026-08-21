package ca.teamdman.sfm.client.overlay.scene;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMSelectorResolution;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ClipPolicy;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ContentRecipe;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.InputMode;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayInstanceId;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Placement;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ReferenceFrame;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SceneState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SizeConstraints;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMOverlaySceneControllerTests {
    @Test
    public void selectorsResolveAllFocusedUnionAndDifferenceAsStableSets() {
        OverlayInstanceId a = id("a");
        OverlayInstanceId b = id("b");
        OverlayInstanceId c = id("c");
        SFMOverlaySceneController controller = new SFMOverlaySceneController(scene(
                7,
                List.of(
                        overlay(c, true, InputMode.INTERACTIVE, 3, placement(30, 30)),
                        overlay(a, false, InputMode.PASSIVE, 1, placement(10, 10)),
                        overlay(b, true, InputMode.INTERACTIVE, 2, placement(20, 20))
                ),
                Optional.of(b)
        ));

        assertEquals(List.of(a, b, c), controller.resolve(selector("all")).identities());
        assertEquals(List.of(b), controller.resolve(selector("focused")).identities());
        assertEquals(
                List.of(b, c),
                controller.resolve(selector("union(focused,id(test%3Ac))")).identities()
        );
        assertEquals(
                List.of(c),
                controller.resolve(selector("difference(all,union(focused,id(test%3Aa)))")).identities()
        );
    }

    @Test
    public void staleExactIdentityIsACompleteEmptySetAndCannotMutateRevision() {
        SFMOverlaySceneController controller = new SFMOverlaySceneController(scene(
                12,
                List.of(overlay(id("present"), false, InputMode.PASSIVE, 0, placement(10, 10))),
                Optional.empty()
        ));
        SFMEntitySelector stale = exact(id("removed"));

        SFMSelectorResolution<OverlayInstanceId> resolution = controller.resolve(stale);
        SFMOverlaySceneController.BatchResult result = controller.execute(
                stale,
                new SFMOverlaySceneController.SetVisibility(true)
        );

        assertTrue(resolution.complete());
        assertTrue(resolution.identities().isEmpty());
        assertTrue(resolution.hasDiagnostic("selector.exact-miss"));
        assertTrue(result.targets().isEmpty());
        assertEquals(12, result.beforeRevision());
        assertEquals(12, result.afterRevision());
        assertTrue(result.diagnostics().stream().anyMatch(value -> value.startsWith("selector.exact-miss:")));
    }

    @Test
    public void visibilityAndPlacementMutateIndependently() {
        OverlayInstanceId target = id("target");
        Placement original = placement(10, 10);
        Placement moved = placement(75, 45);
        SFMOverlaySceneController controller = new SFMOverlaySceneController(scene(
                2,
                List.of(overlay(target, false, InputMode.PASSIVE, 9, original)),
                Optional.empty()
        ));

        controller.execute(exact(target), new SFMOverlaySceneController.SetPlacement(moved));
        OverlayState afterMove = controller.snapshot().overlay(target).orElseThrow();
        assertFalse(afterMove.visible());
        assertEquals(moved, afterMove.placement());
        assertEquals(InputMode.PASSIVE, afterMove.inputMode());
        assertEquals(9, afterMove.zOrder());

        controller.execute(exact(target), new SFMOverlaySceneController.SetVisibility(true));
        OverlayState afterShow = controller.snapshot().overlay(target).orElseThrow();
        assertTrue(afterShow.visible());
        assertEquals(moved, afterShow.placement());

        controller.execute(exact(target), new SFMOverlaySceneController.SetVisibility(false));
        OverlayState afterHide = controller.snapshot().overlay(target).orElseThrow();
        assertFalse(afterHide.visible());
        assertEquals(moved, afterHide.placement());
    }

    @Test
    public void focusRequiresOneVisibleInteractiveTarget() {
        OverlayInstanceId hidden = id("hidden");
        OverlayInstanceId passive = id("passive");
        OverlayInstanceId ready = id("ready");
        SFMOverlaySceneController controller = new SFMOverlaySceneController(scene(
                20,
                List.of(
                        overlay(hidden, false, InputMode.INTERACTIVE, 1, placement(10, 10)),
                        overlay(passive, true, InputMode.PASSIVE, 2, placement(20, 20)),
                        overlay(ready, true, InputMode.INTERACTIVE, 3, placement(30, 30))
                ),
                Optional.empty()
        ));

        assertSingleStatus(
                SFMOverlaySceneController.Status.REJECTED,
                controller.execute(exact(hidden), new SFMOverlaySceneController.Focus())
        );
        assertSingleStatus(
                SFMOverlaySceneController.Status.REJECTED,
                controller.execute(exact(passive), new SFMOverlaySceneController.Focus())
        );
        SFMOverlaySceneController.BatchResult ambiguous = controller.execute(
                selector("all"),
                new SFMOverlaySceneController.Focus()
        );
        assertTrue(ambiguous.targets().isEmpty());
        assertTrue(ambiguous.diagnostics().stream().anyMatch(value -> value.startsWith("overlay.focus-cardinality:")));
        assertEquals(20, controller.snapshot().revision());

        assertSingleStatus(
                SFMOverlaySceneController.Status.APPLIED,
                controller.execute(exact(ready), new SFMOverlaySceneController.Focus())
        );
        assertEquals(Optional.of(ready), controller.snapshot().focusedOverlay());
    }

    @Test
    public void hidingOrMakingFocusedOverlayPassiveReleasesFocusWithoutLosingOtherState() {
        OverlayInstanceId target = id("target");
        Placement placement = placement(65, 35);
        OverlayState focused = overlay(target, true, InputMode.INTERACTIVE, 77, placement);
        SFMOverlaySceneController controller = new SFMOverlaySceneController(scene(
                4,
                List.of(focused),
                Optional.of(target)
        ));

        controller.execute(exact(target), new SFMOverlaySceneController.SetVisibility(false));
        OverlayState hidden = controller.snapshot().overlay(target).orElseThrow();
        assertTrue(controller.snapshot().focusedOverlay().isEmpty());
        assertEquals(placement, hidden.placement());
        assertEquals(InputMode.INTERACTIVE, hidden.inputMode());
        assertEquals(77, hidden.zOrder());

        controller.restore(scene(9, List.of(focused), Optional.of(target)));
        controller.execute(exact(target), new SFMOverlaySceneController.SetInputMode(InputMode.PASSIVE));
        OverlayState passive = controller.snapshot().overlay(target).orElseThrow();
        assertTrue(controller.snapshot().focusedOverlay().isEmpty());
        assertTrue(passive.visible());
        assertEquals(placement, passive.placement());
        assertEquals(77, passive.zOrder());
    }

    @Test
    public void hitTestingUsesHighestZOrderThenStableIdentityAndIgnoresIneligibleOverlays() {
        Placement overlap = placement(10, 10);
        OverlayInstanceId b = id("b");
        OverlayInstanceId c = id("c");
        SFMOverlaySceneController controller = new SFMOverlaySceneController(scene(
                1,
                List.of(
                        overlay(id("a"), true, InputMode.INTERACTIVE, 5, overlap),
                        overlay(b, true, InputMode.INTERACTIVE, 9, overlap),
                        overlay(c, true, InputMode.INTERACTIVE, 9, overlap),
                        overlay(id("hidden"), false, InputMode.INTERACTIVE, 100, overlap),
                        overlay(id("passive"), true, InputMode.PASSIVE, 200, overlap)
                ),
                Optional.empty()
        ));

        assertEquals(c, controller.topInteractiveAt(20, 20, 500, 500).orElseThrow().id());
        controller.execute(exact(c), new SFMOverlaySceneController.SetZOrder(8));
        assertEquals(b, controller.topInteractiveAt(20, 20, 500, 500).orElseThrow().id());
        assertTrue(controller.topInteractiveAt(300, 300, 500, 500).isEmpty());
    }

    @Test
    public void noOpOperationsAndRepeatedFocusKeepRevisionStable() {
        OverlayInstanceId target = id("target");
        Placement placement = placement(10, 10);
        SceneState initial = scene(
                30,
                List.of(overlay(target, true, InputMode.INTERACTIVE, 4, placement)),
                Optional.empty()
        );
        SFMOverlaySceneController controller = new SFMOverlaySceneController(initial);

        assertNoChange(controller.execute(exact(target), new SFMOverlaySceneController.SetVisibility(true)), 30);
        assertNoChange(controller.execute(exact(target), new SFMOverlaySceneController.SetPlacement(placement)), 30);
        assertNoChange(controller.execute(exact(target), new SFMOverlaySceneController.SetInputMode(InputMode.INTERACTIVE)), 30);
        assertNoChange(controller.execute(exact(target), new SFMOverlaySceneController.SetZOrder(4)), 30);
        assertNoChange(controller.execute(exact(target), new SFMOverlaySceneController.Release()), 30);
        assertEquals(initial, controller.snapshot());

        assertSingleStatus(
                SFMOverlaySceneController.Status.APPLIED,
                controller.execute(exact(target), new SFMOverlaySceneController.Focus())
        );
        assertEquals(31, controller.snapshot().revision());
        assertNoChange(controller.execute(exact(target), new SFMOverlaySceneController.Focus()), 31);
    }

    private static OverlayInstanceId id(String suffix) {
        return new OverlayInstanceId("test:" + suffix);
    }

    private static SFMEntitySelector exact(OverlayInstanceId id) {
        return SFMEntitySelector.exact(SFMEntitySelector.Domain.OVERLAY, id.value());
    }

    private static SFMEntitySelector selector(String canonical) {
        return SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.OVERLAY, canonical);
    }

    private static SceneState scene(
            long revision,
            List<OverlayState> overlays,
            Optional<OverlayInstanceId> focused
    ) {
        return new SceneState(SFMOverlaySceneContract.SCHEMA, revision, overlays, focused);
    }

    private static OverlayState overlay(
            OverlayInstanceId id,
            boolean visible,
            InputMode mode,
            int zOrder,
            Placement placement
    ) {
        return new OverlayState(
                id,
                new ContentRecipe("test:content", id.value()),
                visible,
                placement,
                mode,
                zOrder,
                Map.of("owner", id.value())
        );
    }

    private static Placement placement(int x, int y) {
        return new Placement(
                ReferenceFrame.GUI_SAFE_VIEWPORT,
                0D,
                0D,
                0D,
                0D,
                x,
                y,
                Optional.of(new SizeConstraints(100, 100, 100, 100, 100, 100)),
                ClipPolicy.CLIP_TO_VIEWPORT
        );
    }

    private static void assertSingleStatus(
            SFMOverlaySceneController.Status expected,
            SFMOverlaySceneController.BatchResult result
    ) {
        assertEquals(1, result.targets().size());
        assertEquals(expected, result.targets().get(0).status());
    }

    private static void assertNoChange(SFMOverlaySceneController.BatchResult result, long revision) {
        assertEquals(revision, result.beforeRevision());
        assertEquals(revision, result.afterRevision());
        assertSingleStatus(SFMOverlaySceneController.Status.NO_CHANGE, result);
    }
}
