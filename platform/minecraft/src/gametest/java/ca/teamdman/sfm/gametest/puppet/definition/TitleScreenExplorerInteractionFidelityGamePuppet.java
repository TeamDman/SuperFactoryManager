package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMItemRegistryExplorerResolver;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/** GUI-scale-matrix journey for XEXP-20 through XEXP-25. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.GUI_SCALE_MATRIX)
public final class TitleScreenExplorerInteractionFidelityGamePuppet {
    private TitleScreenExplorerInteractionFidelityGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        Path sourceRoot = Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolveSibling("src");
        Path sfmJava = sourceRoot.resolve("main/java/ca/teamdman/sfm/SFM.java");
        SFMPath javaAddress = SFMPath.fromNative(sfmJava);
        SFMPath itemRoot = SFMItemRegistryExplorerResolver.ROOT;
        SFMPath stone = SFMPath.parse("registry://minecraft/item/minecraft/stone");

        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.executeCommandPalette(
                "sfm action invoke sfm:panel/open sfm:explorer " + javaAddress.canonical()
        );
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/root/add focused "
                        + itemRoot.canonical() + " --if-no-match fail"
        );
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/node/expand focused " + itemRoot.canonical()
        );
        puppet.waitForExplorerPath(stone, false);

        puppet.openCommandPalette();
        puppet.setCommandPaletteInput("sfm action invoke sfm:explorer/view/set focused ");
        puppet.capture("x8b-deep-view-completion", caption(
                "The exact deep view prefix discovers registered list and small-icon values with descriptions."
        ));
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/view/set focused sfm:small_icons"
        );

        puppet.openCommandPalette();
        puppet.setCommandPaletteInput("sfm action invoke sfm:explorer/path-display/set focused ");
        puppet.capture("x8b-deep-path-display-completion", caption(
                "Path-label completion remains an independent axis with name, relative, and absolute choices."
        ));
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/path-display/set focused sfm:absolute_path"
        );

        puppet.exerciseExplorerInteractionFidelity(javaAddress);
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM Explorer X-8b — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
