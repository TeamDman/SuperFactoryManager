package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.diagnostic.SFMSizeDisplayPanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.List;

/**
 * Launches the short-lived external {@code sfm.exe} itself, then observes its
 * registered client action on later Minecraft ticks.
 *
 * <p>The process must not be waited on synchronously: its Vox request is
 * dispatched back onto the Minecraft client thread that is ticking this
 * action.</p>
 */
public final class InvokeExternalCliSizeDisplayPuppetAction implements SFMPuppetAction {
    public static final String EXECUTABLE_PROPERTY = "sfm.controlCliExecutable";
    private static final int TIMEOUT_TICKS = 1200;

    private Process process;
    private int ticks;

    @Override
    public String description() {
        return "invoke `sfm invoke sfm:panel/open sfm:size_display` as an external process";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (process == null) {
            process = startCli();
            return false;
        }

        boolean sizeDisplayOpen = Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace
                && workspace.panels().stream().anyMatch(SFMSizeDisplayPanel.class::isInstance);
        if (!process.isAlive()) {
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException("External sfm.exe invocation exited with code " + exitCode);
            }
            if (sizeDisplayOpen) {
                return true;
            }
        }

        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            stopProcess();
            throw new IllegalStateException(
                    "Timed out running `sfm invoke sfm:panel/open sfm:size_display`"
            );
        }
        return false;
    }

    static List<String> command(String executable) {
        return List.of(
                executable,
                "invoke",
                "sfm:panel/open",
                "sfm:size_display",
                "--output-format",
                "json"
        );
    }

    private Process startCli() {
        String executable = System.getProperty(EXECUTABLE_PROPERTY, "sfm.exe").trim();
        if (executable.isEmpty()) {
            throw new IllegalStateException("System property " + EXECUTABLE_PROPERTY + " is empty");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command(executable));
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process started = builder.start();
            started.getOutputStream().close();
            SFM.LOGGER.info(
                    "SFM_GAME_PUPPET_EXTERNAL_CLI_STARTED pid={} command={}",
                    started.pid(),
                    String.join(" ", command(executable))
            );
            return started;
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not launch " + executable
                            + "; install platform/cli/sfm or set -D"
                            + EXECUTABLE_PROPERTY + "=<path>",
                    error
            );
        }
    }

    private void stopProcess() {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
