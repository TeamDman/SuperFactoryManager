package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

/** Live proof of the grammar scene and explicit Text Editor v3 panel entry. */
@SFMGamePuppet
public final class TitleScreenTextEditorPanelGamePuppet {
    private TitleScreenTextEditorPanelGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);

        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:grammar",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(1, 1, 1, "Grammar", -1);
        puppet.capture("text-editor-grammar", caption(
                "The bundled SFML grammar opens as a read-only Text Editor v3 panel."
        ));

        puppet.closeScreenNaturally();
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:text_editor",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(1, 1, 1, "Text Editor v3", -1);
        puppet.capture("text-editor-default", caption(
                "The editor scene without an id resolves the configured default registration."
        ));

        puppet.closeScreenNaturally();
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:text_editor sfm:text_editor_v3",
                SFMScreenMultiplexer.class
        );
        puppet.assertWorkspaceState(1, 1, 1, "Text Editor v3", -1);
        puppet.capture("text-editor-v3", caption(
                "The explicit editor id opens Text Editor v3 through the shared panel contract."
        ));
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM Text Editor: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
