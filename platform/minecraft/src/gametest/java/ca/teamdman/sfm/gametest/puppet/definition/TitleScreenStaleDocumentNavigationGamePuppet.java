package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/** Dedicated natural-input acceptance proof for SS-5 stale-document rejection. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 6 * 60
)
public final class TitleScreenStaleDocumentNavigationGamePuppet {
    private TitleScreenStaleDocumentNavigationGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        Path sourceRoot = Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolveSibling("src");
        Path sfmJava = sourceRoot.resolve("main/java/ca/teamdman/sfm/SFM.java");
        List<Path> directories = List.of(
                sourceRoot.resolve("main"),
                sourceRoot.resolve("main/java"),
                sourceRoot.resolve("main/java/ca"),
                sourceRoot.resolve("main/java/ca/teamdman"),
                sourceRoot.resolve("main/java/ca/teamdman/sfm")
        );

        puppet.waitForOverlayToNotBePresent(LoadingOverlay.class);
        puppet.waitTicks(20);
        puppet.executeCommandPalette(
                "sfm action invoke sfm:panel/open sfm:explorer "
                        + SFMPath.fromNative(sourceRoot).canonical()
        );
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        for (Path directory : directories) {
            puppet.waitForExplorerPath(SFMPath.fromNative(directory), true);
            puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        }
        puppet.waitForExplorerPath(SFMPath.fromNative(sfmJava), true);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL);
        puppet.assertAddressedSfmJava(sourceRoot, sfmJava);
        puppet.assertStaleDocumentJumpRejection(
                sfmJava,
                "SFMBlocks",
                0,
                "stale-document-navigation"
        );
        puppet.closeScreenNaturally();
    }
}
