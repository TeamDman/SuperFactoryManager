package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.SFMTerminalPasteConfirmationScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * V-4.2c live matrix for panel-local renderer and pixel-transport selection.
 * Run this same puppet at {@code 1280x720@auto} and {@code 3840x2130@7}; the
 * latter is rejected if Minecraft does not actually reach effective scale 7.
 */
@SFMGamePuppet(
        timeoutTicks = 20 * 5 * 60,
        viewportProfile = SFMGamePuppetViewportProfile.TERMINAL_PRESENTATION
)
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
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open sfm:terminal");
        puppet.waitTicks(20);
        puppet.startRustTerminalThroughUi();
        puppet.waitTicks(80);
        puppet.assertTerminalPresentationEvidence(
                "initial-default", CPU, FULL_PNG, null,
                true, false, false);
        puppet.executeTerminal("$global:SfmPresentationWitness='" + LEFT_TOKEN
                + "'; $global:SfmPresentationStep=0");

        exercise(puppet, CPU, FULL_PNG, "cpu-full-png", 1, false, false);
        exercise(puppet, CPU, FULL_RGBA, "cpu-full-raw-rgba", 2, true, false);
        exercise(puppet, CPU, DIRTY_RGBA, "cpu-dirty-raw-rgba", 3, true, false);
        // Exercise both axes through the actual keyboard-accessible
        // Presentation popover; the remaining tuples also retain typed action
        // coverage so the two user surfaces converge on the same transition.
        exercise(puppet, GPU, FULL_PNG, "gpu-full-png", 4, true, true);
        exercise(puppet, GPU, FULL_RGBA, "gpu-full-raw-rgba", 5, true, false);
        exercise(puppet, GPU, DIRTY_RGBA, "gpu-dirty-raw-rgba", 6, true, false);

        exerciseClipboardAndGuard(puppet);

        exerciseTerminalPropertiesAndChoices(puppet);

        puppet.executeTerminal("& 'G:\\Programming\\Repos\\ratatui-key-debug\\target\\debug\\ratatui_key_debug.exe'");
        puppet.waitTicks(30);
        puppet.capture("gpu-dirty-alternate-active", caption(GPU, DIRTY_RGBA,
                "The retained GPU presentation shows the child TUI alternate screen."));
        puppet.assertTerminalPresentationEvidence(
                "gpu-dirty-alternate-active", GPU, DIRTY_RGBA, null, false);
        puppet.writeTerminalContent("gpu-dirty-alternate-active-text", "Key Events", null);
        puppet.clickTerminal();
        puppet.dragTerminal();
        puppet.waitTicks(20);
        puppet.writeTerminalContent("gpu-dirty-child-mouse-text", "Drag(Left)", null);
        puppet.assertTerminalSelectionAbsent(
                "gpu-dirty-child-mouse-forwarding", GPU, DIRTY_RGBA, true);
        puppet.pressTerminalKeyDirect(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.pressTerminalKeyDirect(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.pressTerminalKeyDirect(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.waitTicks(30);
        puppet.executeTerminal(witnessCommand());
        puppet.waitTicks(30);
        puppet.capture("gpu-dirty-alternate-restored", caption(GPU, DIRTY_RGBA,
                "The same GPU presentation restores the original PowerShell screen and global."));
        puppet.assertTerminalPresentationEvidence(
                "gpu-dirty-alternate-restored", GPU, DIRTY_RGBA,
                "SFM-WITNESS:" + LEFT_TOKEN + ":6", false);
        puppet.writeTerminalContent(
                "gpu-dirty-alternate-restored-text",
                "line:SFM-WITNESS:" + LEFT_TOKEN + ":6",
                "Key Events");

        // Return to CPU in the same PTY. The global token and monotonic step
        // prove that renderer replacement did not recreate PowerShell.
        exercise(puppet, CPU, FULL_PNG, "cpu-full-png-return", 7, true, false);

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
                "SFM-WITNESS:" + LEFT_TOKEN + ":8",
                false, true, true);

        puppet.clickWorkspacePanel(1);
        puppet.executeTerminal("$global:SfmPresentationStep++; " + witnessCommand());
        puppet.waitTicks(40);
        puppet.capture("independent-right-stable", caption(GPU, DIRTY_RGBA,
                "The right panel retained its tuple, generation, and distinct PowerShell global."));
        puppet.assertTerminalPresentationEvidence(
                "independent-right-stable", GPU, DIRTY_RGBA,
                "SFM-WITNESS:" + RIGHT_TOKEN + ":2", false);

        // The exact-owner relationship is also retained when multiple Rust
        // terminals exist and focus moves to the properties panel.
        puppet.pressScreenKey(GLFW.GLFW_KEY_F3, 0);
        puppet.assertActionChoice(terminalDiagnosticChoices());
        puppet.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_UP, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.waitTicks(20);
        puppet.capture("terminal-properties-beside-gpu-dirty", caption(GPU, DIRTY_RGBA,
                "Requested/effective logical, physical, raster, font, cell, and scale values are visible beside their owner terminal."));
        puppet.assertTerminalPropertiesEvidence(
                "independent-right-properties", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", 0, 0, null, false);
    }

    private static void exercise(
            SFMGamePuppetHelper puppet,
            String renderer,
            String transport,
            String artifact,
            int step,
            boolean freshPresentationExpected,
            boolean selectThroughUi
    ) {
        if (selectThroughUi) {
            puppet.selectTerminalRendererThroughUi(renderer);
            puppet.waitTicks(60);
            puppet.selectTerminalTransportThroughUi(transport);
            puppet.waitTicks(60);
        } else {
            select(puppet, renderer, transport);
        }
        String witness = "SFM-WITNESS:" + LEFT_TOKEN + ":" + step;
        String copyToken = "SFM-COPY-" + step;
        puppet.executeTerminal("$global:SfmPresentationStep++; "
                + "1..100 | Out-Host; "
                + "Write-Host -ForegroundColor Cyan 'hello, world!'; "
                + "Write-Output 'SFM-SLUG: g / b r 6 ❯ │ ┌─┐ \uE0B0 表 e\u0301 😀 �'; "
                + "Write-Output '" + copyToken + "'; "
                + witnessCommand());
        puppet.waitTicks(40);
        puppet.capture(artifact, caption(renderer, transport,
                "The same range, ANSI colour, and difficult-glyph fixture is pushed without polling."));
        puppet.assertTerminalPresentationEvidence(
                artifact, renderer, transport, witness, freshPresentationExpected);
        boolean reverse = (step & 1) == 0;
        boolean rightClickCopy = (step & 1) == 0;
        puppet.selectTerminalText(
                artifact + "-selection", renderer, transport, copyToken, reverse);
        puppet.capture(artifact + "-selection", caption(renderer, transport,
                "Java overlays the Rust-authoritative selection without a new raster or texture upload."));
        puppet.copyTerminalSelection(
                artifact + "-copy", renderer, transport, copyToken, rightClickCopy);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/open/right sfm:terminal_properties");
        puppet.waitTicks(30);
        puppet.capture(artifact + "-properties", caption(renderer, transport,
                "The exact-owner properties panel reconciles logical, physical, Rust-raster, and Java draw telemetry for this tuple."));
        puppet.assertTerminalPropertiesEvidence(
                artifact, renderer, transport,
                "auto", "auto", "auto", null, 0, null, false);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/close");
        puppet.waitTicks(30);
    }

    private static void exerciseClipboardAndGuard(SFMGamePuppetHelper puppet) {
        puppet.executeTerminal("Clear-Host; Write-Output 'SFM-PASTE-BASELINE'");
        puppet.waitTicks(30);

        puppet.pasteTerminalText("Write-Output 'SFM-CTRL-V-SINGLE'");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(20);
        puppet.assertTerminalPrivateContent(
                "ctrl-v-single-line-paste",
                List.of("SFM-CTRL-V-SINGLE"),
                List.of());

        puppet.pasteTerminalTextByRightClick("Write-Output 'SFM-RIGHT-CLICK-SINGLE'");
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ENTER);
        puppet.waitTicks(20);
        puppet.assertTerminalPrivateContent(
                "right-click-single-line-paste",
                List.of("SFM-RIGHT-CLICK-SINGLE"),
                List.of());

        puppet.executeTerminal("Clear-Host; Write-Output 'SFM-GUARD-BASELINE'");
        puppet.waitTicks(20);
        puppet.pasteTerminalText("99\n100");
        puppet.waitForScreen(SFMTerminalPasteConfirmationScreen.class);
        puppet.assertTerminalPasteWarning("multiline-paste-cancel-warning", "99\n100");
        puppet.capture("multiline-paste-cancel-warning", caption(GPU, DIRTY_RGBA,
                "The exact multiline warning opens with Cancel focused and no PTY write."));
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertTerminalPrivateContent(
                "multiline-paste-cancelled",
                List.of("SFM-GUARD-BASELINE"),
                List.of("99", "100"));

        puppet.pasteTerminalText("99\n100");
        puppet.waitForScreen(SFMTerminalPasteConfirmationScreen.class);
        puppet.assertTerminalPasteWarning("multiline-paste-approve-warning", "99\n100");
        puppet.capture("multiline-paste-approve-warning", caption(GPU, DIRTY_RGBA,
                "Paste anyway releases only the exact privately retained multiline body."));
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        // The second line remains at the shell prompt until Enter. Routing
        // this through the workspace proves terminal focus was restored.
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.waitTicks(30);
        puppet.assertTerminalPrivateContent(
                "multiline-paste-approved-once",
                List.of("99", "100"),
                List.of());

        puppet.executeTerminal("1..10000 | ForEach-Object { Write-Output $_; Start-Sleep -Milliseconds 1 }");
        puppet.waitTicks(10);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL);
        puppet.waitTicks(30);
        puppet.writeTerminalContent(
                "no-selection-ctrl-c-interrupt", "❯", "line:10000");
    }

    private static void exerciseTerminalPropertiesAndChoices(SFMGamePuppetHelper puppet) {
        // Keyboard selection from F3 opens the preferred exact-owner panel.
        puppet.pressScreenKey(GLFW.GLFW_KEY_F3, 0);
        puppet.assertActionChoice(terminalDiagnosticChoices());
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
        puppet.waitTicks(30);
        puppet.assertTerminalPropertiesEvidence(
                "properties-open-keyboard", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", null, 0, null, false);

        // Traverse and activate every tuning widget through the shared child
        // host. Each +/- pair is followed by its Auto control so this keyboard
        // proof leaves the observable tuning state at stable defaults.
        for (int index = 0; index < 13; index++) {
            puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, 0);
            puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, 0);
            puppet.waitTicks(10);
        }
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        puppet.pressScreenKey(GLFW.GLFW_KEY_SPACE, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_TAB, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_SPACE, 0);
        puppet.waitTicks(30);
        puppet.assertTerminalPropertiesEvidence(
                "properties-buttons-keyboard-auto", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", null, 0, null, false);

        // The visible +/-/auto controls use the same typed path as palette
        // actions. Exercise each rendered operation through real mouse input.
        for (String operation : List.of(
                "SURFACE_WIDTH_INCREASE", "SURFACE_WIDTH_DECREASE",
                "SURFACE_HEIGHT_INCREASE", "SURFACE_HEIGHT_DECREASE", "SURFACE_AUTO",
                "FONT_INCREASE", "FONT_DECREASE", "FONT_AUTO",
                "COLUMNS_INCREASE", "COLUMNS_DECREASE",
                "ROWS_INCREASE", "ROWS_DECREASE", "CELLS_AUTO")) {
            puppet.clickTerminalPropertiesControl(operation);
        }
        puppet.waitTicks(30);
        puppet.assertTerminalPropertiesEvidence(
                "properties-buttons-auto", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", null, 0, null, false);

        // Global and panel-local scales are independent inputs to the physical
        // viewport contract. Change and restore both through registered actions.
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:ui/gui_scale/set 1");
        puppet.waitTicks(50);
        puppet.assertTerminalPropertiesEvidence(
                "properties-global-scale-one", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", 1, 0, null, false);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:ui/gui_scale/set 0");
        puppet.waitTicks(50);
        puppet.assertTerminalPropertiesEvidence(
                "properties-global-scale-auto", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", 0, 0, null, false);

        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/scale/set 2");
        puppet.waitTicks(50);
        puppet.assertTerminalPropertiesEvidence(
                "properties-panel-scale-two", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", 0, 2, null, false);
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/scale/clear");
        puppet.waitTicks(50);
        puppet.assertTerminalPropertiesEvidence(
                "properties-panel-scale-cleared", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", 0, 0, null, false);
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);

        // Every registered tuning operation uses this same owner-routed path.
        invokeTuning(puppet, "surface/width/increase", 20);
        invokeTuning(puppet, "surface/width/decrease", 20);
        invokeTuning(puppet, "surface/height/increase", 20);
        invokeTuning(puppet, "surface/height/decrease", 20);
        invokeTuning(puppet, "font/decrease", 20);
        invokeTuning(puppet, "font/increase", 20);
        invokeTuning(puppet, "font/auto", 20);
        invokeTuning(puppet, "cells/columns/increase", 20);
        invokeTuning(puppet, "cells/columns/decrease", 20);
        invokeTuning(puppet, "cells/rows/increase", 20);
        invokeTuning(puppet, "cells/rows/decrease", 20);

        // Establish a large valid manual surface/grid, then request an exact
        // font which cannot fit. Rust must return a typed INVALID_REQUEST while
        // Java retains the last accepted image and PTY, and atomically rolls
        // the rejected font override back to the accepted automatic mode.
        invokeTuning(puppet, "surface/set 2048 2048", 40);
        invokeTuning(puppet, "cells/set 240 120", 40);
        puppet.pressScreenKey(GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL);
        puppet.executeTerminal(witnessCommand());
        puppet.waitTicks(30);
        puppet.assertTerminalPresentationEvidence(
                "properties-pre-rejection", GPU, DIRTY_RGBA,
                "SFM-WITNESS:" + LEFT_TOKEN + ":6",
                false, false, true);
        puppet.pressScreenKey(GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL);
        invokeTuning(puppet, "font/set 64", 50);
        puppet.pressScreenKey(GLFW.GLFW_KEY_END, 0);
        puppet.capture("terminal-properties-invalid", caption(GPU, DIRTY_RGBA,
                "A typed INVALID_REQUEST is visible while the last valid frame remains retained."));
        puppet.assertTerminalPropertiesEvidence(
                "typed-invalid-request", GPU, DIRTY_RGBA,
                "manual", "auto", "manual", 0, 0, "INVALID_REQUEST", true);

        invokeTuning(puppet, "font/auto", 30);
        invokeTuning(puppet, "cells/auto", 30);
        invokeTuning(puppet, "surface/auto", 40);
        puppet.assertTerminalPropertiesEvidence(
                "properties-recovered-auto", GPU, DIRTY_RGBA,
                "auto", "auto", "auto", 0, 0, null, false);
        puppet.pressScreenKey(GLFW.GLFW_KEY_HOME, 0);

        // F3's size-display entry must be executable, not merely listed.
        puppet.pressScreenKey(GLFW.GLFW_KEY_F3, 0);
        puppet.assertActionChoice(propertiesDiagnosticChoices());
        puppet.clickActionChoice("sfm action invoke sfm:panel/open sfm:size_display");
        puppet.capture("terminal-size-display-from-f3", caption(GPU, DIRTY_RGBA,
                "The constrained diagnostics palette opened the live size-display scene."));
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:panel/close");
        puppet.waitTicks(20);

        // Escape cancels F3 without changing focus; a second F3 is selected by
        // mouse and closes only the properties panel.
        puppet.pressScreenKey(GLFW.GLFW_KEY_F3, 0);
        puppet.assertActionChoice(propertiesDiagnosticChoices());
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.pressScreenKey(GLFW.GLFW_KEY_F3, 0);
        puppet.assertActionChoice(propertiesDiagnosticChoices());
        puppet.clickActionChoice("sfm action invoke sfm:panel/close");
        puppet.waitTicks(30);
        puppet.clickTerminal();
        puppet.executeTerminal("Write-Output 'SFM-CHOOSER-MOUSE-RETURN'");
        puppet.waitTicks(20);
        puppet.writeTerminalContent("chooser-mouse-return", "SFM-CHOOSER-MOUSE-RETURN", null);

        // The terminal's first two Escapes reach the PTY; the third delegates
        // to the exact constrained close palette. Escape cancels it and keyboard
        // input immediately returns to the same terminal.
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.capture("terminal-escape-two-remaining", caption(GPU, DIRTY_RGBA,
                "The first Escape visibly reports that two presses remain."));
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.capture("terminal-escape-one-remaining", caption(GPU, DIRTY_RGBA,
                "The second Escape visibly reports that one press remains."));
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.assertActionChoice(escapeChoices());
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.clickTerminal();
        puppet.executeTerminal("Write-Output 'SFM-ESCAPE-CHOOSER-RETURN'");
        puppet.waitTicks(20);
        puppet.writeTerminalContent("escape-chooser-return", "SFM-ESCAPE-CHOOSER-RETURN", null);

        // Repeat the bounded close gesture and exercise its explicit mouse
        // cancel action as a second return-to-terminal path.
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressTerminalKey(GLFW.GLFW_KEY_ESCAPE);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.assertActionChoice(escapeChoices());
        puppet.clickActionChoice("sfm action invoke sfm:palette/close");
        puppet.clickTerminal();
        puppet.executeTerminal("Write-Output 'SFM-ESCAPE-MOUSE-CANCEL-RETURN'");
        puppet.waitTicks(20);
        puppet.writeTerminalContent(
                "escape-mouse-cancel-return", "SFM-ESCAPE-MOUSE-CANCEL-RETURN", null);
    }

    private static void invokeTuning(SFMGamePuppetHelper puppet, String suffix, int waitTicks) {
        puppet.openCommandPalette();
        puppet.executeCommandPalette("sfm action invoke sfm:terminal/properties/" + suffix);
        puppet.waitTicks(waitTicks);
    }

    private static List<String> terminalDiagnosticChoices() {
        return List.of(
                "sfm action invoke sfm:panel/open/right sfm:terminal_properties",
                "sfm action invoke sfm:panel/open sfm:size_display",
                "sfm action invoke sfm:panel/open/left sfm:size_display",
                "sfm action invoke sfm:panel/open/right sfm:size_display",
                "sfm action invoke sfm:panel/open/above sfm:size_display",
                "sfm action invoke sfm:panel/open/below sfm:size_display");
    }

    private static List<String> propertiesDiagnosticChoices() {
        return List.of(
                "sfm action invoke sfm:panel/close",
                "sfm action invoke sfm:panel/open sfm:size_display",
                "sfm action invoke sfm:panel/open/left sfm:size_display",
                "sfm action invoke sfm:panel/open/right sfm:size_display",
                "sfm action invoke sfm:panel/open/above sfm:size_display",
                "sfm action invoke sfm:panel/open/below sfm:size_display");
    }

    private static List<String> escapeChoices() {
        return List.of(
                "sfm action invoke sfm:panel/close",
                "sfm action invoke sfm:screen/close",
                "sfm action invoke sfm:palette/close");
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
