package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.action.AssertWorkspaceCounterfactualPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Natural X5 title-to-palette-to-explorer-to-editor counterfactual proof. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 15 * 60
)
public final class TitleScreenWorkspaceCounterfactualGamePuppet {
    private static final String EDIT_SUFFIX_BODY = "// reviewed through a compatible restorable suffix";

    private TitleScreenWorkspaceCounterfactualGamePuppet() { }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:chamber/workspace-counterfactual",
                SFMScreenMultiplexer.class
        );
        puppet.waitTicks(20);
        checkpoint(puppet, AssertWorkspaceCounterfactualPuppetAction.Stage.INITIAL,
                "workspace-counterfactual-initial",
                "The ordinary Explorer and History Graph begin before any document selection.");

        // The history panel opened to the right; focus the ordinary explorer,
        // expand its one root, select A.java, and use the established
        // Ctrl+Enter adjacent-open gesture through sfm:path/open.
        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL);
        checkpoint(puppet, AssertWorkspaceCounterfactualPuppetAction.Stage.A_OPEN,
                "workspace-counterfactual-a-open",
                "A.java opens and receives focus in an ordinary writable Text Editor V3 panel.");

        puppet.pressScreenKey(GLFW.GLFW_KEY_END, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.typeScreenText(EDIT_SUFFIX_BODY);
        puppet.waitTicks(10);
        checkpoint(puppet, AssertWorkspaceCounterfactualPuppetAction.Stage.A_EDITED,
                "workspace-counterfactual-a-edited",
                "Natural Text Editor V3 input commits one complete in-memory A.java value.");

        palette(puppet,
                "sfm action invoke sfm:episode/workspace-counterfactual/fork/before-selection focused");
        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        checkpoint(puppet, AssertWorkspaceCounterfactualPuppetAction.Stage.B_SELECTED,
                "workspace-counterfactual-b-selected",
                "Undo-like head movement retains A while the real explorer changes selection to B.java.");

        palette(puppet,
                "sfm action invoke sfm:episode/workspace-counterfactual/checkout/recorded-a focused");
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        checkpoint(puppet, AssertWorkspaceCounterfactualPuppetAction.Stage.RECORDED_CHECKOUT,
                "workspace-counterfactual-recorded-checkout",
                "Recorded checkout moves the head to A without evaluating or executing the suffix.");

        palette(puppet,
                "sfm action invoke sfm:episode/workspace-counterfactual/replay/frozen-a focused");
        checkpoint(puppet, AssertWorkspaceCounterfactualPuppetAction.Stage.FROZEN_A,
                "workspace-counterfactual-frozen-a",
                "Frozen-witness replay still targets A.java despite the retained B selection.");

        palette(puppet,
                "sfm action invoke sfm:episode/workspace-counterfactual/replay/reevaluate-selected focused");
        checkpoint(puppet, AssertWorkspaceCounterfactualPuppetAction.Stage.REEVALUATED_BARRIER,
                "workspace-counterfactual-reevaluated-b",
                "Intent re-evaluation targets B, applies only the compatible suffix, and stops at the external barrier.");
    }

    private static void palette(SFMGamePuppetHelper puppet, String command) {
        puppet.executeCommandPalette(command);
        puppet.executeCommandPalette("sfm action invoke sfm:palette/close");
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
    }

    private static void checkpoint(
            SFMGamePuppetHelper puppet,
            AssertWorkspaceCounterfactualPuppetAction.Stage stage,
            String artifactAndCapture,
            String caption
    ) {
        puppet.assertWorkspaceCounterfactual(stage, artifactAndCapture, artifactAndCapture);
        puppet.capture(artifactAndCapture, Component.literal("Workspace counterfactual: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(caption).withStyle(ChatFormatting.BLACK)));
    }
}
