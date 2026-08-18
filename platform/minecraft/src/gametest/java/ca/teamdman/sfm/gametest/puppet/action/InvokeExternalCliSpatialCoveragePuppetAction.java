package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMExternalCliPuppetProcess;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Live COV-12 proof through the external Rust CLI and Java Vox action server. */
public final class InvokeExternalCliSpatialCoveragePuppetAction implements SFMPuppetAction {
    private static final int TIMEOUT_TICKS = 20 * 180;
    private static final String REPORT = "coverage-report.json";
    private static final String MAP = "coverage-map.json";
    private static final String HEATMAP = "coverage-heatmap.png";

    private final Path sourceFile;
    private final Path artifactDirectory;
    private final String artifactName;
    private SFMExternalCliPuppetProcess process;
    private SFMExternalCliPuppetProcess.Completed completed;
    private int ticks;

    public InvokeExternalCliSpatialCoveragePuppetAction(
            Path sourceFile,
            Path artifactDirectory,
            String artifactName
    ) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile")
                .toAbsolutePath()
                .normalize();
        this.artifactDirectory = Objects.requireNonNull(artifactDirectory, "artifactDirectory")
                .toAbsolutePath()
                .normalize();
        this.artifactName = Objects.requireNonNull(artifactName, "artifactName");
    }

    @Override
    public String description() {
        return "invoke spatial coverage through the external sfm.exe remoting client";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++ticks > TIMEOUT_TICKS) {
            closeProcess();
            throw new IllegalStateException("Timed out waiting for external spatial-coverage artifacts in "
                    + artifactDirectory);
        }
        if (process == null) {
            if (!focusReadySourceEditor()) return false;
            clearPriorArtifacts();
            process = SFMExternalCliPuppetProcess.start(command(executable(), artifactDirectory));
            return false;
        }
        if (completed == null) {
            completed = process.poll().orElse(null);
            if (completed == null) return false;
            if (completed.exitCode() != 0) {
                throw new IllegalStateException("External spatial coverage exited with code "
                        + completed.exitCode() + ": " + completed.stderr());
            }
        }
        Path reportPath = artifactDirectory.resolve(REPORT);
        Path mapPath = artifactDirectory.resolve(MAP);
        Path heatmapPath = artifactDirectory.resolve(HEATMAP);
        if (!Files.isRegularFile(reportPath)
                || !Files.isRegularFile(mapPath)
                || !Files.isRegularFile(heatmapPath)) {
            return false;
        }

        JsonObject report;
        try {
            report = JsonParser.parseString(Files.readString(reportPath, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read external spatial coverage report", failure);
        }
        if (!"sfm.spatial-coverage-report/1".equals(report.get("schema").getAsString())) {
            throw new IllegalStateException("External spatial coverage wrote an unexpected report schema: "
                    + report.get("schema"));
        }
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.external-spatial-coverage-puppet/1");
        evidence.add("argv", new GsonBuilder().create().toJsonTree(completed.argv()));
        evidence.addProperty("process_id", completed.processId());
        evidence.addProperty("exit_code", completed.exitCode());
        evidence.addProperty("duration_millis", completed.durationMillis());
        evidence.addProperty("stdout", completed.stdout());
        evidence.addProperty("stderr", completed.stderr());
        evidence.addProperty("artifact_directory", artifactDirectory.toString());
        evidence.addProperty("report", reportPath.toString());
        evidence.addProperty("map", mapPath.toString());
        evidence.addProperty("heatmap", heatmapPath.toString());
        evidence.add("coverage_report", report);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        closeProcess();
        return true;
    }

    static List<String> command(String executable, Path destination) {
        return List.of(
                executable,
                "spatial",
                "coverage",
                "run",
                "document",
                "focused",
                "sfm:strict_java_navigation",
                "sfm:auto_1_through_8",
                "0",
                "4096",
                destination.toString(),
                "--output-format",
                "json"
        );
    }

    private static String executable() {
        String executable = System.getProperty(SFMExternalCliPuppetProcess.EXECUTABLE_PROPERTY, "sfm.exe").trim();
        if (executable.isEmpty()) {
            throw new IllegalStateException("System property "
                    + SFMExternalCliPuppetProcess.EXECUTABLE_PROPERTY + " is empty");
        }
        return executable;
    }

    private void clearPriorArtifacts() {
        try {
            Files.deleteIfExists(artifactDirectory.resolve(REPORT));
            Files.deleteIfExists(artifactDirectory.resolve(MAP));
            Files.deleteIfExists(artifactDirectory.resolve(HEATMAP));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not clear prior bounded coverage artifacts in "
                    + artifactDirectory, failure);
        }
    }

    private boolean focusReadySourceEditor() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, sourceFile)
                .orElseThrow(() -> new IllegalStateException(
                        "The remoting coverage source editor is no longer present: " + sourceFile));
        if (!workspace.focusPanel(source.panelId())) {
            throw new IllegalStateException("Could not focus the remoting coverage source editor");
        }
        return source.resolvedPanel()
                .flatMap(editor -> editor.captureSpatialCoverage())
                .isPresent();
    }

    private void closeProcess() {
        if (process == null) return;
        process.close();
    }
}
