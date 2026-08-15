package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRuntime;
import ca.teamdman.sfm.client.syntax.process.SFMSyntaxServerSupervisor;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Repeats the exact immutable document request and proves cache/session reuse. */
public final class AssertWarmSfmJavaSyntaxPresentationPuppetAction implements SFMPuppetAction {
    private final Path file;
    private final String artifactName;
    private final String mandatoryScreenshotCaptureId;
    private int ticks;
    private SFMWorkspacePanelId sourcePanelId;
    private SFMWorkspacePanelId warmPanelId;
    private Set<SFMWorkspacePanelId> panelsBefore = Set.of();
    private SFMDrawCanvasScreen.SyntaxPresentationEvidence coldSyntax;
    private SFMSyntaxServerSupervisor.Telemetry workerBefore;
    private String currentCanvasProjectionHash;
    private long openedNanos;

    public AssertWarmSfmJavaSyntaxPresentationPuppetAction(
            Path file,
            String artifactName,
            String mandatoryScreenshotCaptureId
    ) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.artifactName = requireNonBlank(artifactName, "artifactName");
        this.mandatoryScreenshotCaptureId = requireNonBlank(
                mandatoryScreenshotCaptureId,
                "mandatoryScreenshotCaptureId"
        );
    }

    @Override
    public String description() {
        return "repeat SFM.java syntax highlighting and prove warm worker/cache reuse";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            return waitOrFail("the SFM source workspace");
        }
        if (warmPanelId == null) return openWarmPanel(workspace);
        return observeAndCleanup(runtime, workspace);
    }

    private boolean openWarmPanel(SFMScreenMultiplexer workspace) {
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, file).orElse(null);
        if (source == null || source.resolvedPanel().isEmpty()
                || source.state().documentSnapshot().isEmpty()) {
            return waitOrFail("the resolved source editor for the warm syntax request");
        }
        SFMTextDocumentSnapshot snapshot = source.state().documentSnapshot().orElseThrow();
        if (!snapshot.ready()) return waitOrFail("the ready immutable source snapshot");
        coldSyntax = source.resolvedPanel().orElseThrow().syntaxPresentationEvidence().orElse(null);
        if (coldSyntax == null) return waitOrFail("the cold syntax publication");
        currentCanvasProjectionHash = SFMSourcePuppetProbe.currentCanvasProjectionSha256(source);
        if (!coldSyntax.sourceSha256().equals(currentCanvasProjectionHash)) {
            return waitOrFail("the cold syntax publication for the exact source hash");
        }

        workerBefore = SFMSyntaxHighlightRuntime.get().workerTelemetry();
        require(workerBefore.processAlive() && workerBefore.sessionsReady() == 1,
                "Warm syntax request requires one reusable ready worker session");
        require(workerBefore.launchFailures() == 0 && workerBefore.protocolFailures() == 0
                        && workerBefore.transportFailures() == 0 && workerBefore.restarts() == 0,
                "Warm syntax request cannot start from a failed worker session");

        sourcePanelId = source.panelId();
        panelsBefore = Set.copyOf(workspace.panelIds());
        SFMTextEditorPanelRecipe recipe = new SFMTextEditorPanelRecipe(
                new ResourceLocation(SFM.MOD_ID, "text_editor"),
                SFMTextEditors.V3.getId().orElseThrow().location(),
                new SFMTextDocumentSource.Literal(snapshot.text()),
                true,
                "SFM.java warm syntax probe"
        );
        openedNanos = System.nanoTime();
        SFMWorkspacePanelIntentResult result = workspace.openIntoSlot(
                sourcePanelId,
                recipe.createResolvedPanel(snapshot),
                SFMWorkspacePanelMetadata.ordinary(),
                null
        );
        require(result == SFMWorkspacePanelIntentResult.APPLIED,
                "Could not open the isolated warm syntax probe as a temporary tab");
        LinkedHashSet<SFMWorkspacePanelId> added = new LinkedHashSet<>(workspace.panelIds());
        added.removeAll(panelsBefore);
        require(added.size() == 1, "Warm syntax probe must add exactly one temporary panel");
        warmPanelId = added.iterator().next();
        require(workspace.focusedPanelId().equals(warmPanelId),
                "Warm syntax probe must be focused while its styles publish");
        return false;
    }

    private boolean observeAndCleanup(ISFMGamePuppetRuntime runtime, SFMScreenMultiplexer workspace) {
        SFMSourcePuppetProbe.EditorHandle warm = SFMSourcePuppetProbe.editor(workspace, warmPanelId).orElse(null);
        if (warm == null || warm.resolvedPanel().isEmpty() || warm.state().documentSnapshot().isEmpty()) {
            return waitOrFail("the temporary warm syntax editor");
        }
        SFMTextDocumentSnapshot snapshot = warm.state().documentSnapshot().orElseThrow();
        SFMDrawCanvasScreen.SyntaxPresentationEvidence syntax = warm.resolvedPanel().orElseThrow()
                .syntaxPresentationEvidence().orElse(null);
        if (syntax == null || !syntax.sourceSha256().equals(currentCanvasProjectionHash)) {
            return waitOrFail("the repeated exact-document syntax publication");
        }

        require(snapshot.ready() && snapshot.readOnly(), "Warm syntax probe must remain immutable and read-only");
        String warmCanvasProjectionHash = SFMSourcePuppetProbe.currentCanvasProjectionSha256(warm);
        require(warmCanvasProjectionHash.equals(currentCanvasProjectionHash),
                "Warm syntax probe changed the exact current document projection");
        require(syntax.requestId() != coldSyntax.requestId(),
                "Warm syntax proof must be a distinct worker request");
        require(syntax.cacheStatus().equals("hit"),
                "Repeated exact-document syntax request must hit the worker cache");
        require(syntax.spanCount() > 0 && syntax.distinctTagCount() > 1
                        && syntax.distinctFormattingCount() > 1,
                "Warm syntax response must preserve the distinct Java style model");

        SFMSyntaxServerSupervisor.Telemetry workerAfter = SFMSyntaxHighlightRuntime.get().workerTelemetry();
        require(workerAfter.launchAttempts() == workerBefore.launchAttempts()
                        && workerAfter.sessionsReady() == workerBefore.sessionsReady()
                        && workerAfter.restarts() == workerBefore.restarts(),
                "Warm syntax request must reuse the existing worker session");
        require(workerAfter.launchFailures() == 0 && workerAfter.protocolFailures() == 0
                        && workerAfter.transportFailures() == 0 && workerAfter.processAlive(),
                "Warm syntax worker failed or stopped during the repeated request");
        long puppetOpenToVisibleMicros = Math.max(0L, (System.nanoTime() - openedNanos) / 1_000L);

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.sfm-java-warm-syntax-puppet/1");
        evidence.addProperty("outcome", "success");
        evidence.addProperty("current_canvas_projection_sha256", currentCanvasProjectionHash);
        evidence.addProperty("cold_request_id", coldSyntax.requestId());
        evidence.addProperty("warm_request_id", syntax.requestId());
        evidence.addProperty("cache_status", syntax.cacheStatus());
        evidence.addProperty("worker_session_reused", true);
        evidence.addProperty("worker_launch_attempts_before", workerBefore.launchAttempts());
        evidence.addProperty("worker_launch_attempts_after", workerAfter.launchAttempts());
        evidence.addProperty("worker_sessions_ready_before", workerBefore.sessionsReady());
        evidence.addProperty("worker_sessions_ready_after", workerAfter.sessionsReady());
        evidence.addProperty("parser_fingerprint", syntax.parserFingerprint());
        evidence.addProperty("formatting_schema", syntax.formattingSchema());
        evidence.addProperty("span_count", syntax.spanCount());
        evidence.addProperty("distinct_tag_count", syntax.distinctTagCount());
        evidence.addProperty("distinct_formatting_count", syntax.distinctFormattingCount());

        JsonObject timing = new JsonObject();
        timing.addProperty("classification", "warm-repeated-document-query-to-visible");
        timing.add("query_to_visible", SFMSourcePuppetProbe.timing(syntax.queryToVisibleMicros()));
        timing.add("puppet_open_to_visible", SFMSourcePuppetProbe.timing(puppetOpenToVisibleMicros));
        timing.add("rust_analysis", SFMSourcePuppetProbe.timing(syntax.rustElapsedMicros()));
        evidence.add("timing", timing);

        JsonObject visual = new JsonObject();
        visual.addProperty("mandatory_screenshot_capture_id", mandatoryScreenshotCaptureId);
        visual.addProperty("pixel_assertion_performed", false);
        visual.addProperty("verification", "human-visual-review-required");
        evidence.add("visual_evidence", visual);
        evidence.addProperty("raw_source_retained", false);
        evidence.addProperty("source_files_mutated", false);

        require(workspace.closePanel(warmPanelId) == SFMWorkspacePanelIntentResult.APPLIED,
                "Could not close the temporary warm syntax panel");
        require(workspace.focusPanel(sourcePanelId), "Could not restore focus to the original source panel");
        require(Set.copyOf(workspace.panelIds()).equals(panelsBefore),
                "Warm syntax probe did not restore the original panel set");
        SFMSourcePuppetProbe.EditorHandle retained = SFMSourcePuppetProbe.editor(workspace, file)
                .orElseThrow(() -> new IllegalStateException("Warm syntax probe lost the source editor"));
        require(SFMSourcePuppetProbe.currentCanvasProjectionSha256(retained)
                        .equals(currentCanvasProjectionHash),
                "Warm syntax probe changed the original source editor projection");

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

    private static String requireNonBlank(String value, String name) {
        String answer = Objects.requireNonNull(value, name).strip();
        if (answer.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return answer;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
