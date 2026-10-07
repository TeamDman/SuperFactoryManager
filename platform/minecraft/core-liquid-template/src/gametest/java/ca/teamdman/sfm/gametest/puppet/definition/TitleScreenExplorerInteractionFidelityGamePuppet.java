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
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/** GUI-scale-matrix journey for XEXP-20 through XEXP-25 and X-8c filter hierarchy fidelity. */
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
        List<Path> sourceDirectories = List.of(
                sourceRoot.resolve("main"),
                sourceRoot.resolve("main/java"),
                sourceRoot.resolve("main/java/ca"),
                sourceRoot.resolve("main/java/ca/teamdman"),
                sourceRoot.resolve("main/java/ca/teamdman/sfm")
        );
        SFMPath sourceAddress = SFMPath.fromNative(sourceRoot);
        SFMPath javaAddress = SFMPath.fromNative(sfmJava);
        SFMPath itemRoot = SFMItemRegistryExplorerResolver.ROOT;

        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.executeCommandPalette(
                "sfm action invoke sfm:panel/open sfm:explorer "
                        + sourceAddress.canonical()
        );
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        for (Path directory : sourceDirectories) {
            SFMPath address = SFMPath.fromNative(directory);
            puppet.waitForExplorerPath(address, true);
            puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        }
        puppet.waitForExplorerPath(javaAddress, true);
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/root/add all "
                        + itemRoot.canonical() + " --if-no-match fail"
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.waitForExplorerPath(itemRoot, false);
        // A warm single-root relation auto-hoists sourceRoot, so the initial
        // Right key may expand its first child rather than the hidden root.
        // Once a second root makes sourceRoot visible, expand it explicitly.
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/node/expand all " + sourceAddress.canonical()
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.waitForExplorerPath(javaAddress, false);
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/node/expand all " + itemRoot.canonical()
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);

        puppet.openCommandPalette();
        String viewPrefix = "sfm action invoke sfm:explorer/view/set focused ";
        puppet.setCommandPaletteInput(viewPrefix);
        puppet.waitForCommandPaletteSuggestions(viewPrefix, List.of("sfm:list", "sfm:small_icons"));
        puppet.capture("x8b-deep-view-completion", caption(
                "The exact deep view prefix discovers registered list and small-icon values with descriptions."
        ));
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/view/set all sfm:small_icons"
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);

        puppet.openCommandPalette();
        String pathDisplayPrefix = "sfm action invoke sfm:explorer/path-display/set focused ";
        puppet.setCommandPaletteInput(pathDisplayPrefix);
        puppet.waitForCommandPaletteSuggestions(
                pathDisplayPrefix,
                List.of("sfm:absolute_path", "sfm:name", "sfm:relative_path")
        );
        puppet.capture("x8b-deep-path-display-completion", caption(
                "Path-label completion remains an independent axis with name, relative, and absolute choices."
        ));
        puppet.executeCommandPalette(
                "sfm action invoke sfm:explorer/path-display/set all sfm:absolute_path"
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_ESCAPE, 0);
        puppet.waitForScreen(SFMScreenMultiplexer.class);

        puppet.exerciseExplorerInteractionFidelity(javaAddress);
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM Explorer X-8b/X-8c — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
