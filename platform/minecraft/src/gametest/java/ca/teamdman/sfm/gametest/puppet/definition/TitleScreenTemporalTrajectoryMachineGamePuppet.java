package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.AssertTemporalTrajectoryMachinePuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural title-screen proof of planning, non-destructive undo, insertion, and replan. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 10 * 60
)
public final class TitleScreenTemporalTrajectoryMachineGamePuppet {
    private TitleScreenTemporalTrajectoryMachineGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:chamber/temporal-decimal-numbering",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(1, 1, 1, "Temporal Numbering Chamber", -1);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open/right sfm:episode/history",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(2, 2, 1, "History Graph", -1);
        puppet.waitTicks(20);
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.INITIAL,
                "stage-initial", "initial", "Initial immutable two-item chamber and live History Graph.");

        palette(puppet, "sfm action invoke sfm:episode/trajectory/plan focused");
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.PLANNED,
                "stage-planned", "planned-route",
                "A* selects the two-step semantic route; projected work is not committed history.");

        palette(puppet, "sfm action invoke sfm:episode/trajectory/step focused");
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.FIRST_STEP,
                "stage-first-step", "first-step",
                "Step commits only the two-hyphen selection and advances the instruction pointer once.");

        palette(puppet, "sfm action invoke sfm:episode/trajectory/run focused 32");
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.NUMBERED_TWO,
                "stage-numbered-two", "numbered-two",
                "Bounded Run reaches the exact two-item document and SUPERVISION_READY, never approval.");

        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.POST_UNDO,
                "stage-post-undo", "post-undo-retained-child",
                "Ctrl+Z moves the document head back while retaining the old numbered child.");

        puppet.pressScreenKey(GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_END, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.typeScreenText("- apricots");
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.THREE_ITEM_FORK,
                "stage-three-item-fork", "three-item-fork",
                "Natural Text Editor V3 input creates a sibling history without clobbering the old branch.");

        palette(puppet, "sfm action invoke sfm:episode/trajectory/step focused");
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.STALE_STEP,
                "stage-stale-step", "stale-step",
                "The retained old plan pauses at a stale parent and does not mutate the edited document.");

        palette(puppet, "sfm action invoke sfm:episode/trajectory/replan focused");
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.REPLANNED,
                "stage-replanned", "replanned-route",
                "Replan retains the original route and re-evaluates the semantic intent over three markers.");

        palette(puppet, "sfm action invoke sfm:episode/trajectory/run focused 32");
        checkpoint(puppet, AssertTemporalTrajectoryMachinePuppetAction.Stage.NUMBERED_THREE,
                "stage-numbered-three", "numbered-three",
                "The new route reaches exact 1/2/3 numbering with checkout bytes unchanged.");
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static void checkpoint(
            SFMGamePuppetHelper puppet,
            AssertTemporalTrajectoryMachinePuppetAction.Stage stage,
            String artifact,
            String capture,
            String caption
    ) {
        puppet.assertTemporalTrajectoryMachine(stage, artifact);
        puppet.capture(capture, Component.literal("Temporal trajectory: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caption).withStyle(ChatFormatting.BLACK)));
    }
}
