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

/** Deterministic C-6 proof from real SFM.java presentation through F12 navigation. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
        timeoutTicks = 20 * 5 * 60
)
public final class TitleScreenSfmJavaJumpToDefinitionGamePuppet {
    private TitleScreenSfmJavaJumpToDefinitionGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        Path sourceRoot = Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolveSibling("src");
        Path mainJava = sourceRoot.resolve("main/java");
        Path sfmJava = mainJava.resolve("ca/teamdman/sfm/SFM.java");
        Path sfmBlocksJava = mainJava.resolve(
                "ca/teamdman/sfm/common/registry/registration/SFMBlocks.java"
        );
        Path ambiguityFixture = mainJava.resolve(
                "ca/teamdman/sfm/client/action/SFMClientActionCommandTree.java"
        );
        Path selectedAmbiguousTarget = mainJava.resolve("org/simmetrics/metrics/StringDistances.java");
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
        puppet.assertSfmJavaSyntaxPresentation(
                sourceRoot,
                sfmJava,
                "sfm-java-live-presentation"
        );
        puppet.assertWarmSfmJavaSyntaxPresentation(
                sfmJava,
                "sfm-java-warm-syntax",
                "sfm-java-live-presentation"
        );
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("sfm-java-live-presentation", caption(
                "Real SFM.java is read-only, Arborium-styled, and shown beside chest/paper explorer icons."
        ));

        puppet.assertJumpToDefinition(
                sfmJava,
                "SFMBlocks",
                0,
                sfmBlocksJava,
                "sfm-blocks-definition"
        );
        puppet.assertWarmJumpToDefinition(
                sfmJava,
                "SFMBlocks",
                0,
                sfmBlocksJava,
                "sfm-blocks-definition-warm",
                "sfm-blocks-definition"
        );
        puppet.waitTicks(SFMGamePuppetHelper.RENDER_SETTLE_TICKS);
        puppet.capture("sfm-blocks-definition", caption(
                "Cold then warm F12 navigation leaves the exact reused SFMBlocks declaration focused."
        ));

        puppet.assertAmbiguousJumpToDefinition(
                sfmJava,
                sourceRoot,
                ambiguityFixture,
                selectedAmbiguousTarget,
                "ambiguous-string-distances-definition",
                "ambiguous-definition-choice",
                caption("F12 presents only the two live StringDistances definition candidates."),
                "ambiguous-definition-selected",
                caption("The constrained choice opens the selected metrics.StringDistances declaration.")
        );

        puppet.assertDependencyJumpToDefinitionIfIndexed(
                sfmJava,
                "FMLClientSetupEvent",
                -1,
                "net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent",
                "fml-client-setup-event-definition",
                "fml-client-setup-event-definition",
                caption("With a ready pinned dependency index, F12 opens the exact Forge declaration.")
        );
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM Java Navigation — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
