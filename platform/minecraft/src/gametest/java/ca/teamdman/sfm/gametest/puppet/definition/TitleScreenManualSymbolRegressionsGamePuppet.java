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

/** Natural-use proof for the exact Java navigation failures reported on 2026-08-18. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.GUI_SCALE_MATRIX,
        timeoutTicks = 20 * 7 * 60
)
public final class TitleScreenManualSymbolRegressionsGamePuppet {
    private TitleScreenManualSymbolRegressionsGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        Path sourceRoot = Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolveSibling("src");
        Path mainJava = sourceRoot.resolve("main/java");
        Path sfmJava = mainJava.resolve("ca/teamdman/sfm/SFM.java");
        Path compatJava = mainJava.resolve("ca/teamdman/sfm/common/compat/SFMModCompat.java");
        Path translationUtilsJava = mainJava.resolve(
                "ca/teamdman/sfm/common/util/SFMTranslationUtils.java");
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

        puppet.assertSymbolInspectionCopy(
                sfmJava,
                "isComputerCraftLoaded",
                0,
                "manual-regression-qualified-static-member-before-resolution",
                "before-resolution"
        );
        puppet.assertJumpToDefinition(
                sfmJava,
                "SFMModCompat",
                0,
                compatJava,
                "manual-regression-qualified-owner"
        );
        puppet.assertJumpToDefinition(
                sfmJava,
                "isComputerCraftLoaded",
                0,
                compatJava,
                "manual-regression-qualified-member"
        );
        puppet.assertSymbolInspectionCopy(
                sfmJava,
                "isComputerCraftLoaded",
                0,
                "manual-regression-qualified-static-member-after-resolution",
                "after-resolution"
        );
        puppet.assertSymbolInspectionCopy(
                sfmJava,
                "FMLJavaModLoadingContext",
                -1,
                "manual-regression-forge-loader-source-before-resolution",
                "before-resolution"
        );
        puppet.assertDependencyJumpToDefinitionIfIndexed(
                sfmJava,
                "FMLJavaModLoadingContext",
                -1,
                "net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext",
                "manual-regression-forge-loader-source",
                "manual-regression-forge-loader-source",
                caption("The locked javafmllanguage artifact supplies a navigable Forge loader definition.")
        );
        puppet.assertSymbolInspectionCopy(
                sfmJava,
                "FMLJavaModLoadingContext",
                -1,
                "manual-regression-forge-loader-source-after-resolution",
                "after-resolution"
        );

        puppet.executeCommandPalette(
                "sfm action invoke sfm:path/open "
                        + SFMPath.fromNative(translationUtilsJava).canonical()
                        + " adjacent"
        );
        puppet.waitForScreen(SFMScreenMultiplexer.class);
        puppet.assertSymbolInspectionCopy(
                translationUtilsJava,
                "TranslatableContents",
                6,
                "manual-regression-varargs-constructor-before-resolution",
                "before-resolution"
        );
        puppet.assertDependencyJumpToDefinitionIfIndexed(
                translationUtilsJava,
                "TranslatableContents",
                6,
                "net.minecraft.network.chat.contents.TranslatableContents",
                "manual-regression-varargs-constructor",
                "manual-regression-varargs-constructor",
                caption("The exact two-argument call opens the unique String/Object-array constructor.")
        );
        puppet.assertSymbolInspectionCopy(
                translationUtilsJava,
                "TranslatableContents",
                6,
                "manual-regression-varargs-constructor-after-resolution",
                "after-resolution"
        );
        puppet.closeScreenNaturally();
    }

    private static Component caption(String text) {
        return Component.literal("SFM Manual Navigation Regressions — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }
}
