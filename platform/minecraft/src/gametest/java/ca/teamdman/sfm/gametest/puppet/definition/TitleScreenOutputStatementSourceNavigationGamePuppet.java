package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.symbol.SFMSymbolServerSupervisor;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.puppet.SFMExternalCliPuppetProcess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Integrated C-11 proof over the real large OutputStatement.java document. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.GUI_SCALE_MATRIX,
        timeoutTicks = 20 * 20 * 60
)
public final class TitleScreenOutputStatementSourceNavigationGamePuppet {
    private TitleScreenOutputStatementSourceNavigationGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        Path sourceRoot = Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolveSibling("src");
        Path outputStatement = sourceRoot.resolve(
                "main/java/ca/teamdman/sfml/ast/OutputStatement.java"
        );
        Path branchRoot = sourceRoot.getParent().getParent().getParent();
        Path symbolCliRoot = branchRoot.resolve("platform/cli/sfm-propagate-changes");
        Path symbolWorker = symbolCliRoot.resolve("target/release/sfm-propagate-changes.exe");
        requireCurrentRustExecutable(symbolCliRoot, symbolWorker, "symbol worker");
        System.setProperty(SFMSymbolServerSupervisor.EXECUTABLE_PROPERTY, symbolWorker.toString());
        boolean autoScale = Minecraft.getInstance().options.guiScale().get() == 0;
        if (autoScale) {
            Path controlCliRoot = branchRoot.resolve("platform/cli/sfm");
            Path controlCli = controlCliRoot.resolve("target/release/sfm.exe");
            requireCurrentRustExecutable(controlCliRoot, controlCli, "control CLI");
            System.setProperty(SFMExternalCliPuppetProcess.EXECUTABLE_PROPERTY, controlCli.toString());
        }
        List<Path> directories = List.of(
                sourceRoot.resolve("main"),
                sourceRoot.resolve("main/java"),
                sourceRoot.resolve("main/java/ca"),
                sourceRoot.resolve("main/java/ca/teamdman"),
                sourceRoot.resolve("main/java/ca/teamdman/sfml"),
                sourceRoot.resolve("main/java/ca/teamdman/sfml/ast")
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
        puppet.waitForExplorerPath(SFMPath.fromNative(outputStatement), true);
        puppet.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL);
        puppet.assertSourceNavigationJourney(
                sourceRoot,
                outputStatement,
                "output-statement-source-navigation"
        );
        if (autoScale) {
            puppet.invokeExternalCliSpatialCoverage(
                    outputStatement,
                    Minecraft.getInstance().gameDirectory.toPath()
                            .resolve("sfm-artifacts")
                            .resolve("spatial-coverage")
                            .resolve("remoting-output-statement-auto"),
                    "output-statement-source-navigation-remoting-coverage"
            );
        }
        puppet.openCommandPalette();
        puppet.executeCommandPaletteAndWaitForScreen(
                "sfm action invoke sfm:panel/open sfm:text_editor sfm:text_editor_v3",
                SFMScreenMultiplexer.class
        );
        puppet.pressScreenKey(GLFW.GLFW_KEY_F12, 0);
        puppet.exerciseActionableToast("output-statement-source-navigation-actionable-toast");
        puppet.capture(
                "output-statement-source-navigation-actionable-toast",
                Component.literal("SFM Source Navigation: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(
                                "A later failed lookup remains visible after copy, pin, resume, and exact dismiss."
                        ).withStyle(ChatFormatting.BLACK))
        );
        puppet.closeScreenNaturally();
    }

    private static void requireCurrentRustExecutable(Path cliRoot, Path executable, String label) {
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException(
                    "C-11 requires the current worktree " + label + "; build "
                            + executable + " before launching the puppet"
            );
        }
        try {
            long workerModified = Files.getLastModifiedTime(executable).toMillis();
            long sourceModified;
            try (Stream<Path> files = Files.walk(cliRoot.resolve("src"))) {
                sourceModified = files
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".rs"))
                        .mapToLong(TitleScreenOutputStatementSourceNavigationGamePuppet::lastModified)
                        .max()
                        .orElse(0L);
            }
            for (String manifest : List.of("Cargo.toml", "Cargo.lock", "build.rs")) {
                Path candidate = cliRoot.resolve(manifest);
                if (Files.isRegularFile(candidate)) {
                    sourceModified = Math.max(sourceModified, Files.getLastModifiedTime(candidate).toMillis());
                }
            }
            if (workerModified < sourceModified) {
                throw new IllegalStateException(
                        "C-11 refuses the stale worktree " + label + " " + executable
                                + "; rebuild it after the newest Rust source change"
                );
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not validate the C-11 worktree " + label, failure);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
