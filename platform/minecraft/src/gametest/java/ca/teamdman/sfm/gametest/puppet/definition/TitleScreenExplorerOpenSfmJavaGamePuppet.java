package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.explorer.SFMPath;
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

/** Declared Auto-plus-numeric-scale proof of the generic explorer opening real SFM source. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.GUI_SCALE_MATRIX)
public final class TitleScreenExplorerOpenSfmJavaGamePuppet {
    private TitleScreenExplorerOpenSfmJavaGamePuppet() {
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
                "sfm action invoke sfm:panel/open sfm:explorer " + SFMPath.fromNative(sourceRoot).canonical()
        );
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        // A fresh relation shows and selects the single root; a warm relation
        // may auto-hoist it and show its children directly. Right-expanding
        // the initial selection is therefore necessary only in the fresh case
        // and harmless in the warm case.
        puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        for (Path directory : directories) {
            SFMPath address = SFMPath.fromNative(directory);
            puppet.waitForExplorerPath(address, true);
            puppet.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
        }
        puppet.waitForExplorerPath(SFMPath.fromNative(sfmJava), true);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL);
        puppet.assertAddressedSfmJava(sourceRoot, sfmJava);
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("explorer-open-sfm-java", caption(
                "The generic lazy explorer selects real SFM.java beside a resolver-authorized, read-only Text Editor v3 panel."
        ));
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM Addressed Source — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
