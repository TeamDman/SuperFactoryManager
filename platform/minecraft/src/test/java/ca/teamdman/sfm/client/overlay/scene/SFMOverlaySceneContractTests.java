package ca.teamdman.sfm.client.overlay.scene;

import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Bounds;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ClipPolicy;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Placement;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ReferenceFrame;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SizeConstraints;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Viewport;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMOverlaySceneContractTests {
    @Test
    public void defaultScenePublishesHiddenAddressableHistoryAndFpsOverlays() {
        SFMOverlaySceneContract.SceneState defaults = SFMOverlaySceneContract.SceneState.defaults();

        assertEquals(2, defaults.overlays().size());
        var fps = defaults.overlay(new SFMOverlaySceneContract.OverlayInstanceId(
                SFMOverlaySceneContract.FPS_OVERLAY_ID)).orElseThrow();
        assertEquals(SFMOverlaySceneContract.FPS_RECIPE_ID, fps.recipe().contentId());
        assertFalse(fps.visible());
        assertEquals(SFMOverlaySceneContract.InputMode.PASSIVE, fps.inputMode());
    }

    @Test
    public void genericOverlayHostRegistersBothMachineAndOrdinaryDocumentHistoryContent() {
        SFMClientOverlayRuntime runtime = new SFMClientOverlayRuntime();

        assertTrue(runtime.registeredContentIds().contains(SFMOverlaySceneContract.HISTORY_RECIPE_ID));
        assertTrue(runtime.registeredContentIds().contains(
                SFMOverlaySceneContract.DOCUMENT_HISTORY_RECIPE_ID));
        assertTrue(runtime.registeredContentIds().contains(SFMOverlaySceneContract.FPS_RECIPE_ID));
    }

    @Test
    public void placementCanonicalRoundTripPreservesEveryField() {
        Placement constrained = new Placement(
                ReferenceFrame.GUI_SAFE_VIEWPORT,
                0.125D,
                0.75D,
                1D,
                0.5D,
                -17,
                29,
                Optional.of(new SizeConstraints(96, 72, 333, 222, 1200, 900)),
                ClipPolicy.CLIP_TO_VIEWPORT
        );
        String constrainedCanonical =
                "gui-safe(0.125,0.75,1,0.5,-17,29,96,72,333,222,1200,900,clip)";

        assertEquals(constrainedCanonical, constrained.canonical());
        assertEquals(constrained, Placement.parseCanonical(constrainedCanonical));
        assertEquals(constrainedCanonical, Placement.parseCanonical(constrainedCanonical).canonical());

        Placement automatic = new Placement(
                ReferenceFrame.GUI_SAFE_VIEWPORT,
                0D,
                1D,
                0D,
                1D,
                4,
                -4,
                Optional.empty(),
                ClipPolicy.CLAMP_TO_SAFE_VIEWPORT
        );
        String automaticCanonical =
                "gui-safe(0,1,0,1,4,-4,auto,auto,auto,auto,auto,auto,clamp)";

        assertEquals(automaticCanonical, automatic.canonical());
        assertEquals(automatic, Placement.parseCanonical(automaticCanonical));
    }

    @Test
    public void placementUsesNonZeroViewportOriginAndReflowsAfterResize() {
        Placement centeredOnBottom = new Placement(
                ReferenceFrame.GUI_SAFE_VIEWPORT,
                0.5D,
                1D,
                0.5D,
                1D,
                5,
                -7,
                Optional.empty(),
                ClipPolicy.CLIP_TO_VIEWPORT
        );

        assertEquals(
                new Bounds(405, 543, 200, 100),
                centeredOnBottom.resolve(new Viewport(100, 50, 800, 600), 200, 100)
        );
        assertEquals(
                new Bounds(145, 123, 160, 80),
                centeredOnBottom.resolve(new Viewport(20, 10, 400, 200), 160, 80)
        );
    }

    @Test
    public void topRightPlacementRetainsInsetsAcrossViewportResize() {
        Placement placement = Placement.topRight(200, 100);

        assertEquals(
                new Bounds(642, 38, 200, 100),
                placement.resolve(new Viewport(50, 30, 800, 600), 420, 300)
        );
        assertEquals(
                new Bounds(242, 38, 200, 100),
                placement.resolve(new Viewport(50, 30, 400, 200), 420, 300)
        );
    }
}
