package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.symbol.SFMSymbolServerSupervisor;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Integrated C-11 proof over the real large OutputStatement.java document. */
@SFMGamePuppet(
        viewportProfile = SFMGamePuppetViewportProfile.FIXED_1280X720_AUTO,
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
        requireCurrentSymbolWorker(symbolCliRoot, symbolWorker);
        System.setProperty(SFMSymbolServerSupervisor.EXECUTABLE_PROPERTY, symbolWorker.toString());
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
        puppet.closeScreenNaturally();
    }

    private static void requireCurrentSymbolWorker(Path cliRoot, Path worker) {
        if (!Files.isRegularFile(worker)) {
            throw new IllegalStateException(
                    "C-11 requires the current worktree symbol worker; build "
                            + worker + " before launching the puppet"
            );
        }
        try {
            long workerModified = Files.getLastModifiedTime(worker).toMillis();
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
                        "C-11 refuses the stale worktree symbol worker " + worker
                                + "; rebuild it after the newest Rust source change"
                );
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not validate the C-11 worktree symbol worker", failure);
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
