package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPresentation;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPresentationRegistry;
import ca.teamdman.sfm.client.screen.explorer.SFMFilePathExplorerPresenter;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRuntime;
import ca.teamdman.sfm.client.syntax.process.SFMSyntaxServerSupervisor;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Objects;

/** Waits for real Arborium styles and writes the source-free C-6 presentation witness. */
public final class AssertSfmJavaSyntaxPresentationPuppetAction implements SFMPuppetAction {
    private static final ResourceLocation CHEST = new ResourceLocation("minecraft", "chest");
    private static final ResourceLocation PAPER = new ResourceLocation("minecraft", "paper");

    private final Path root;
    private final Path file;
    private final SFMPath rootAddress;
    private final SFMPath fileAddress;
    private final String artifactName;
    private int ticks;

    public AssertSfmJavaSyntaxPresentationPuppetAction(Path root, Path file, String artifactName) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.artifactName = requireArtifactName(artifactName);
        rootAddress = SFMPath.fromNative(this.root);
        fileAddress = SFMPath.fromNative(this.file);
    }

    @Override
    public String description() {
        return "wait for live Arborium presentation evidence for SFM.java";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            return waitOrFail("the SFM source workspace");
        }
        var explorer = SFMSourcePuppetProbe.explorer(workspace);
        var editor = SFMSourcePuppetProbe.editor(workspace, file);
        if (explorer.isEmpty() || editor.isEmpty()) return waitOrFail("the explorer and addressed SFM.java editor");

        SFMTextDocumentSnapshot document = editor.orElseThrow().state().documentSnapshot().orElse(null);
        var resolved = editor.orElseThrow().resolvedPanel();
        if (document == null || !document.ready() || resolved.isEmpty()) {
            return waitOrFail("the resolved, ready SFM.java editor");
        }
        SFMDrawCanvasScreen.SyntaxPresentationEvidence syntax = resolved.orElseThrow()
                .syntaxPresentationEvidence().orElse(null);
        if (syntax == null) return waitOrFail("published Java syntax styles");

        require(document.readOnly() && editor.orElseThrow().state().isReadOnly(),
                "SFM.java must remain read-only while syntax styles are presented");
        require(document.path().equals(java.util.Optional.of(fileAddress)), "SFM.java path identity changed");
        require(document.authorizedRoot().equals(java.util.Optional.of(rootAddress)),
                "SFM.java root authority changed");
        String baselineDocumentHash = SFMSourcePuppetProbe.canonicalHash(document.sha256().orElseThrow());
        String currentCanvasProjectionHash = SFMSourcePuppetProbe.currentCanvasProjectionSha256(
                editor.orElseThrow()
        );
        // A deferred editor can expose the previous presentation for a tick while the
        // exact-document request is in flight. That is a bounded readiness state, not
        // evidence of a broken publication. Only accept the witness once all three
        // identities have converged on the currently displayed immutable document.
        if (!syntax.sourceSha256().equals(currentCanvasProjectionHash)) {
            return waitOrFail("syntax request/result identity for the displayed SFM.java document");
        }
        require(syntax.requestId() > 0 && syntax.requestGeneration() > 0 && syntax.originGeneration() > 0,
                "Syntax request identities must be positive");
        require(syntax.spanCount() > 0, "Arborium did not publish any Java spans");
        require(syntax.distinctTagCount() > 1, "Java presentation did not contain distinct Arborium tags");
        require(syntax.distinctFormattingCount() > 1,
                "Java presentation did not contain distinct Minecraft formatting styles");
        require(!syntax.parserFingerprint().isBlank(), "Syntax parser fingerprint is missing");
        require(!syntax.formattingSchema().isBlank(), "Syntax formatting schema is missing");
        require(java.util.Set.of("hit", "miss", "bypassed").contains(syntax.cacheStatus()),
                "Unexpected syntax cache status: " + syntax.cacheStatus());
        require(syntax.rustElapsedMicros() >= 0 && syntax.queryToVisibleMicros() >= 0,
                "Syntax timings must not be negative");
        SFMSyntaxServerSupervisor.Telemetry worker = SFMSyntaxHighlightRuntime.get().workerTelemetry();
        require(worker.launchAttempts() == 1, "Cold syntax proof must use exactly one worker launch");
        require(worker.sessionsReady() == 1, "Cold syntax proof must establish exactly one ready worker session");
        require(worker.launchFailures() == 0 && worker.protocolFailures() == 0
                        && worker.transportFailures() == 0 && worker.restarts() == 0,
                "Cold syntax worker must not fail or restart");
        require(worker.completed() >= 1 && worker.processAlive(),
                "Cold syntax worker must complete a request and remain reusable");

        SFMScreenPanelBounds explorerBounds = workspace.panelContentBounds(explorer.orElseThrow().panelId());
        if (explorerBounds == null || explorerBounds.width() <= 0 || explorerBounds.height() <= 0) {
            return waitOrFail("non-empty explorer bounds");
        }
        SFMExplorerProjection.Result projection = explorer.orElseThrow().panel().model()
                .state(explorerBounds).projection();
        SFMExplorerProjection.Row directoryRow = row(projection, SFMPath.fromNative(file.getParent()));
        SFMExplorerProjection.Row fileRow = row(projection, fileAddress);
        SFMExplorerPresentationRegistry registry = SFMExplorerPresentationRegistry.minecraftDefaults();
        SFMExplorerPresentationRegistry.Resolution directory = registry.resolve(directoryRow);
        SFMExplorerPresentationRegistry.Resolution regularFile = registry.resolve(fileRow);
        require(directory.contributorId().equals(SFMFilePathExplorerPresenter.ID),
                "Directory did not use the file-path presenter");
        require(regularFile.contributorId().equals(SFMFilePathExplorerPresenter.ID),
                "File did not use the file-path presenter");
        SFMItemIcon directoryIcon = itemIcon(directory.presentation());
        SFMItemIcon fileIcon = itemIcon(regularFile.presentation());
        require(directoryIcon.requestedItem().equals(CHEST), "Directory icon is not minecraft:chest");
        require(fileIcon.requestedItem().equals(PAPER), "File icon is not minecraft:paper");

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.sfm-java-live-presentation-puppet/1");
        JsonObject explorerEvidence = new JsonObject();
        explorerEvidence.addProperty("id", explorer.orElseThrow().panel().explorerId().toString());
        explorerEvidence.addProperty("location",
                explorer.orElseThrow().panel().sessionSnapshot().location().canonical());
        explorerEvidence.addProperty("selected_path", explorer.orElseThrow().panel().sessionSnapshot()
                .navigationCursor().orElseThrow().canonical());
        explorerEvidence.addProperty("directory_presenter", directory.contributorId());
        explorerEvidence.addProperty("directory_icon", directoryIcon.requestedItem().toString());
        explorerEvidence.addProperty("file_presenter", regularFile.contributorId());
        explorerEvidence.addProperty("file_icon", fileIcon.requestedItem().toString());
        evidence.add("explorer", explorerEvidence);

        JsonObject documentEvidence = new JsonObject();
        documentEvidence.addProperty("path", fileAddress.canonical());
        documentEvidence.addProperty("authorized_root", rootAddress.canonical());
        documentEvidence.addProperty("baseline_sha256", baselineDocumentHash);
        documentEvidence.addProperty("current_canvas_projection_sha256", currentCanvasProjectionHash);
        documentEvidence.addProperty("byte_length", document.byteLength().orElseThrow());
        documentEvidence.addProperty("read_only", true);
        documentEvidence.addProperty("read_only_chrome_model_expectation", true);
        documentEvidence.addProperty("read_only_chrome_pixel_measurement_performed", false);
        evidence.add("document", documentEvidence);

        JsonObject syntaxEvidence = new JsonObject();
        syntaxEvidence.addProperty("request_id", syntax.requestId());
        syntaxEvidence.addProperty("request_generation", syntax.requestGeneration());
        syntaxEvidence.addProperty("origin_generation", syntax.originGeneration());
        syntaxEvidence.addProperty("request_source_sha256", syntax.sourceSha256());
        syntaxEvidence.addProperty("result_source_sha256", syntax.sourceSha256());
        syntaxEvidence.addProperty("current_canvas_projection_request_result_hashes_match", true);
        syntaxEvidence.addProperty("parser_fingerprint", syntax.parserFingerprint());
        syntaxEvidence.addProperty("formatting_schema", syntax.formattingSchema());
        syntaxEvidence.addProperty("span_count", syntax.spanCount());
        syntaxEvidence.addProperty("distinct_tag_count", syntax.distinctTagCount());
        syntaxEvidence.addProperty("distinct_formatting_count", syntax.distinctFormattingCount());
        syntaxEvidence.addProperty("cache_status", syntax.cacheStatus());
        syntaxEvidence.addProperty("rust_elapsed_micros", syntax.rustElapsedMicros());
        syntaxEvidence.addProperty("query_to_visible_micros", syntax.queryToVisibleMicros());
        syntaxEvidence.addProperty("distinct_java_styling_model_span_evidence", true);
        syntaxEvidence.addProperty("distinct_java_styling_pixel_measurement_performed", false);
        evidence.add("syntax", syntaxEvidence);

        JsonObject timing = new JsonObject();
        timing.addProperty("classification", "cold-first-document-worker-startup-to-visible");
        timing.addProperty("measurement_start", "editor syntax request before lazy worker startup");
        timing.addProperty("measurement_end", "exact-document styles published to the visible editor model");
        timing.add("cold_worker_startup_to_visible", SFMSourcePuppetProbe.timing(syntax.queryToVisibleMicros()));
        timing.add("rust_analysis", SFMSourcePuppetProbe.timing(syntax.rustElapsedMicros()));
        evidence.add("timing", timing);

        JsonObject workerEvidence = new JsonObject();
        workerEvidence.addProperty("lifecycle", worker.lifecycle().name().toLowerCase(java.util.Locale.ROOT));
        workerEvidence.addProperty("launch_attempts", worker.launchAttempts());
        workerEvidence.addProperty("sessions_ready", worker.sessionsReady());
        workerEvidence.addProperty("launch_failures", worker.launchFailures());
        workerEvidence.addProperty("protocol_failures", worker.protocolFailures());
        workerEvidence.addProperty("transport_failures", worker.transportFailures());
        workerEvidence.addProperty("submitted", worker.submitted());
        workerEvidence.addProperty("completed", worker.completed());
        workerEvidence.addProperty("restarts", worker.restarts());
        workerEvidence.addProperty("pending_requests", worker.pendingRequests());
        workerEvidence.addProperty("process_alive", worker.processAlive());
        evidence.add("worker", workerEvidence);

        JsonObject visualEvidence = new JsonObject();
        visualEvidence.addProperty("mandatory_screenshot_capture_id", artifactName);
        visualEvidence.addProperty("capture_contract", "puppet fails unless a non-empty PNG is written");
        visualEvidence.addProperty("pixel_assertion_performed", false);
        visualEvidence.addProperty("verification", "human-visual-review-required");
        evidence.add("visual_evidence", visualEvidence);
        evidence.addProperty("raw_source_retained", false);
        evidence.addProperty("source_files_mutated", false);

        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        return true;
    }

    private boolean waitOrFail(String expected) {
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 2) {
            throw new IllegalStateException("Timed out waiting for " + expected);
        }
        return false;
    }

    private static SFMExplorerProjection.Row row(SFMExplorerProjection.Result projection, SFMPath path) {
        return projection.rows().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Explorer projection is missing " + path.canonical()));
    }

    private static SFMItemIcon itemIcon(SFMExplorerPresentation presentation) {
        if (!(presentation.icon() instanceof SFMExplorerPresentation.ItemIcon item)) {
            throw new IllegalStateException("Explorer presentation is not ItemStack-backed");
        }
        return item.item();
    }

    private static String requireArtifactName(String value) {
        String answer = Objects.requireNonNull(value, "artifactName").strip();
        if (answer.isEmpty()) throw new IllegalArgumentException("Artifact name must not be blank");
        return answer;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
