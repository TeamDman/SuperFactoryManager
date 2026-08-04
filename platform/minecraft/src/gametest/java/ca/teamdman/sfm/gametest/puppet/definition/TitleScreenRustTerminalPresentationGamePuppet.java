package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/**
 * V-4.2c live matrix for panel-local renderer and pixel-transport selection.
 * Run this same puppet at {@code 1280x720@auto} and {@code 3840x2130@7}; the
 * latter is rejected if Minecraft does not actually reach effective scale 7.
 */
@SFMGamePuppet
public final class TitleScreenRustTerminalPresentationGamePuppet {
    private static final String CPU = "rust-cpu-fontdue";
    private static final String GPU = "rust-gpu-slug";
    private static final String FULL_PNG = "full-png";
    private static final String FULL_RGBA = "full-raw-rgba";
    private static final String DIRTY_RGBA = "dirty-raw-rgba";
    // Keep the witness short enough to remain a single logical line after the
    // terminal is split into two narrow panels.
    private static final String LEFT_TOKEN = "SFM-LEFT-PTY-V1";
    // Keep the exact machine-readable output line within the 35-column split
    // panel so terminal soft wrapping cannot turn one witness into two rows.
    private static final String RIGHT_TOKEN = "SFM-RIGHT-PTY-V1";

    private TitleScreenRustTerminalPresentationGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/server/start");
        puppet.waitTicks(20);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(80);
        puppet.executeTerminal("$global:SfmPresentationWitness='" + LEFT_TOKEN
                + "'; $global:SfmPresentationStep=0");

        exercise(puppet, CPU, FULL_PNG, "cpu-full-png", 1, false);
        exercise(puppet, CPU, FULL_RGBA, "cpu-full-raw-rgba", 2, true);
        exercise(puppet, CPU, DIRTY_RGBA, "cpu-dirty-raw-rgba", 3, true);
        exercise(puppet, GPU, FULL_PNG, "gpu-full-png", 4, true);
        exercise(puppet, GPU, FULL_RGBA, "gpu-full-raw-rgba", 5, true);
        exercise(puppet, GPU, DIRTY_RGBA, "gpu-dirty-raw-rgba", 6, true);

        // Return to CPU in the same PTY. The global token and monotonic step
        // prove that renderer replacement did not recreate PowerShell.
        exercise(puppet, CPU, FULL_PNG, "cpu-full-png-return", 7, true);

        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open/right sfm:terminal");
        puppet.waitTicks(80);
        select(puppet, GPU, DIRTY_RGBA);
        puppet.executeTerminal("$global:SfmPresentationWitness='" + RIGHT_TOKEN
                + "'; $global:SfmPresentationStep=1; " + witnessCommand());
        puppet.waitTicks(40);
        puppet.capture("independent-right-gpu-dirty", caption(GPU, DIRTY_RGBA,
                "The right panel owns an independent GPU/dirty presentation and PTY."));
        puppet.assertTerminalPresentationEvidence(
                "independent-right-gpu-dirty", GPU, DIRTY_RGBA,
                "SFM-WITNESS:" + RIGHT_TOKEN + ":1", false);

        // Focus the first visible slot, switch only that captured panel, and
        // prove the right panel's tuple/generation remains unchanged afterward.
        puppet.clickWorkspacePanel(0);
        select(puppet, CPU, FULL_RGBA);
        puppet.executeTerminal("$global:SfmPresentationStep++; " + witnessCommand());
        puppet.waitTicks(40);
        puppet.capture("independent-left-cpu-full-raw", caption(CPU, FULL_RGBA,
                "The left panel switches while the right panel remains GPU/dirty."));
        puppet.assertTerminalPresentationEvidence(
                "independent-left-cpu-full-raw", CPU, FULL_RGBA,
                "SFM-WITNESS:" + LEFT_TOKEN + ":8", true);

        puppet.clickWorkspacePanel(1);
        puppet.executeTerminal("$global:SfmPresentationStep++; " + witnessCommand());
        puppet.waitTicks(40);
        puppet.capture("independent-right-stable", caption(GPU, DIRTY_RGBA,
                "The right panel retained its tuple, generation, and distinct PowerShell global."));
        puppet.assertTerminalPresentationEvidence(
                "independent-right-stable", GPU, DIRTY_RGBA,
                "SFM-WITNESS:" + RIGHT_TOKEN + ":2", false);
    }

    private static void exercise(
            SFMGamePuppetHelper puppet,
            String renderer,
            String transport,
            String artifact,
            int step,
            boolean freshPresentationExpected
    ) {
        select(puppet, renderer, transport);
        String witness = "SFM-WITNESS:" + LEFT_TOKEN + ":" + step;
        puppet.executeTerminal("$global:SfmPresentationStep++; "
                + "1..100 | Out-Host; "
                + "Write-Host -ForegroundColor Cyan 'hello, world!'; "
                + "Write-Output 'SFM-SLUG: g / b r 6 ❯ │ ┌─┐ \uE0B0 表 e\u0301 😀 �'; "
                + witnessCommand());
        puppet.waitTicks(40);
        puppet.capture(artifact, caption(renderer, transport,
                "The same range, ANSI colour, and difficult-glyph fixture is pushed without polling."));
        puppet.assertTerminalPresentationEvidence(
                artifact, renderer, transport, witness, freshPresentationExpected);
    }

    private static void select(SFMGamePuppetHelper puppet, String renderer, String transport) {
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/renderer/set " + renderer);
        puppet.waitTicks(30);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/transport/set " + transport);
        puppet.waitTicks(60);
    }

    private static String witnessCommand() {
        return "Write-Output ('SFM-WITNESS:{0}:{1}' -f "
                + "$global:SfmPresentationWitness,$global:SfmPresentationStep)";
    }

    private static Component caption(String renderer, String transport, String detail) {
        return Component.literal("SFM Terminal ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(renderer + " / " + transport + " — " + detail));
    }
}
