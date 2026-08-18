package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.semantic.SFMSpatialCoverageArtifacts;
import ca.teamdman.sfm.client.semantic.SFMSpatialCoverageService;
import ca.teamdman.sfm.client.semantic.SFMSpatialSamplingPolicies;
import ca.teamdman.sfm.client.semantic.SFMSpatialSemanticContract;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Client-thread capture plus bounded background execution for the canonical coverage action. */
final class SFMSpatialCoverageRuntime {
    private static final AtomicLong NEXT_RUN = new AtomicLong();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(
            daemonThreads("sfm-spatial-coverage"));
    private static final SimpleCommandExceptionType TEXT_EDITOR_REQUIRED =
            new SimpleCommandExceptionType(Component.literal(
                    "Spatial coverage requires a focused Text Editor v3 panel"));
    private static final SimpleCommandExceptionType MAP_PENDING =
            new SimpleCommandExceptionType(Component.literal(
                    "The focused editor's Java interaction map is not ready; retry after semantic indexing completes"));

    private SFMSpatialCoverageRuntime() {
    }

    static int run(
            PanelActionSupport.CapturedPanel target,
            SFMSpatialCoverageRunAction.Request request,
            Consumer<Component> feedback
    ) throws CommandSyntaxException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(feedback, "feedback");
        if (!(target.workspace().panelInstance(target.panelId()) instanceof SFMTextEditorPanel focused)) {
            throw TEXT_EDITOR_REQUIRED.create();
        }
        SFMTextEditorPanel.SpatialCoverageCapture focusedCapture =
                focused.captureSpatialCoverage().orElseThrow(MAP_PENDING::create);
        long runNumber = NEXT_RUN.updateAndGet(value -> {
            if (value == Long.MAX_VALUE) throw new IllegalStateException("Spatial coverage run ids exhausted");
            return value + 1;
        });
        String runId = "spatial-coverage-" + runNumber;
        Path destination = artifactDestination(request.artifactDestination(), runId);
        List<SFMTextEditorPanel.SpatialCoverageCapture> captures = captures(target, request, focusedCapture);
        SFMSpatialCoverageService.WorkspaceSnapshot workspace = workspaceSnapshot(
                request, focusedCapture, captures);
        SFMSpatialSemanticContract.CoverageRequest coverageRequest = new SFMSpatialSemanticContract.CoverageRequest(
                SFMSpatialSemanticContract.COVERAGE_REQUEST_SCHEMA,
                runId,
                SFMSpatialSemanticContract.Scope.fromWireName(request.scope()),
                request.selector(),
                request.profile(),
                request.layoutMatrix(),
                request.seed(),
                request.budget(),
                destination.toString(),
                focusedCapture.snapshot()
        );
        feedback.accept(Component.literal("Spatial coverage queued: " + runId)
                .withStyle(ChatFormatting.GRAY));
        WORKER.execute(() -> execute(
                target,
                coverageRequest,
                workspace,
                destination,
                feedback
        ));
        return 1;
    }

    private static List<SFMTextEditorPanel.SpatialCoverageCapture> captures(
            PanelActionSupport.CapturedPanel target,
            SFMSpatialCoverageRunAction.Request request,
            SFMTextEditorPanel.SpatialCoverageCapture focused
    ) {
        if (request.scope().equals("document")) return List.of(focused);
        LinkedHashMap<String, SFMTextEditorPanel.SpatialCoverageCapture> byAddress = new LinkedHashMap<>();
        target.workspace().panels().stream()
                .filter(SFMTextEditorPanel.class::isInstance)
                .map(SFMTextEditorPanel.class::cast)
                .map(SFMTextEditorPanel::captureSpatialCoverage)
                .flatMap(java.util.Optional::stream)
                .filter(capture -> capture.snapshot().workspaceFingerprint()
                        .equals(focused.snapshot().workspaceFingerprint()))
                .forEach(capture -> byAddress.putIfAbsent(
                        capture.document().address(), capture));
        byAddress.putIfAbsent(focused.document().address(), focused);
        return List.copyOf(byAddress.values());
    }

    private static SFMSpatialCoverageService.WorkspaceSnapshot workspaceSnapshot(
            SFMSpatialCoverageRunAction.Request request,
            SFMTextEditorPanel.SpatialCoverageCapture focused,
            List<SFMTextEditorPanel.SpatialCoverageCapture> captures
    ) {
        LinkedHashMap<String, SFMSpatialCoverageService.Document> documents = new LinkedHashMap<>();
        captures.forEach(capture -> documents.putIfAbsent(
                capture.document().address(), capture.document()));
        if (request.scope().equals("workspace")) {
            for (SFMJavaInteractionMap.FileRow file : focused.workspaceInventory()) {
                documents.computeIfAbsent(file.address(), ignored -> unavailableCanvas(file));
            }
        }
        return new SFMSpatialCoverageService.WorkspaceSnapshot(
                focused.snapshot().workspaceFingerprint(),
                List.copyOf(documents.values())
        );
    }

    private static SFMSpatialCoverageService.Document unavailableCanvas(SFMJavaInteractionMap.FileRow file) {
        SFMSpatialSemanticContract.FileState state = switch (file.state()) {
            case MISSING -> SFMSpatialSemanticContract.FileState.MISSING;
            case STALE -> SFMSpatialSemanticContract.FileState.STALE;
            case PARSE_FAILED -> SFMSpatialSemanticContract.FileState.PARSE_FAILED;
            case LAYOUT_FAILED -> SFMSpatialSemanticContract.FileState.LAYOUT_FAILED;
            case INDEX_FAILED -> SFMSpatialSemanticContract.FileState.INDEX_FAILED;
            case TIMEOUT -> SFMSpatialSemanticContract.FileState.TIMEOUT;
            case FAILED -> SFMSpatialSemanticContract.FileState.FAILED;
            case UNSUPPORTED_EXTENSION -> SFMSpatialSemanticContract.FileState.UNSUPPORTED_EXTENSION;
            case SKIPPED, COVERED, PARTIAL -> SFMSpatialSemanticContract.FileState.SKIPPED;
        };
        String diagnostic = file.diagnostic().orElseGet(() ->
                "No live Java canvas layout is available for this workspace file");
        return SFMSpatialCoverageService.Document.failed(
                file.address(),
                file.sourceSet(),
                file.contentHash().orElse("unavailable"),
                state,
                diagnostic
        );
    }

    private static void execute(
            PanelActionSupport.CapturedPanel target,
            SFMSpatialSemanticContract.CoverageRequest request,
            SFMSpatialCoverageService.WorkspaceSnapshot workspace,
            Path destination,
            Consumer<Component> feedback
    ) {
        try {
            SFMSpatialCoverageService.Run run = new SFMSpatialCoverageService().run(
                    request,
                    workspace,
                    policy(request.profile())
            );
            SFMSpatialCoverageArtifacts.Written written = SFMSpatialCoverageArtifacts.write(run, destination);
            long uncovered = run.report().files().stream()
                    .filter(file -> file.state() != SFMSpatialSemanticContract.FileState.COVERED)
                    .count();
            publish(target, feedback, Component.literal(
                    "Spatial coverage complete: " + written.report()
                            + " (non-covered files: " + uncovered + ")")
                    .withStyle(uncovered == 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
        } catch (Throwable failure) {
            String detail = failure.getMessage();
            publish(target, feedback, Component.literal(
                    "Spatial coverage failed: "
                            + (detail == null || detail.isBlank()
                            ? failure.getClass().getSimpleName() : detail))
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static SFMSpatialSamplingPolicies.Policy policy(String profile) {
        return switch (profile) {
            case "sfm:classification" -> SFMSpatialSamplingPolicies.stratified();
            case "sfm:strict_java_navigation", "sfm:branch_boundary" ->
                    SFMSpatialSamplingPolicies.adaptiveFailureSeeking();
            case "sfm:real_gesture", "sfm:reciprocity" -> SFMSpatialSamplingPolicies.maximin();
            default -> throw new IllegalArgumentException("Unsupported spatial coverage profile: " + profile);
        };
    }

    private static Path artifactDestination(String configured, String runId) {
        Minecraft minecraft = Minecraft.getInstance();
        Path gameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (configured.equals("auto")) {
            return gameDirectory.resolve("sfm-artifacts")
                    .resolve("spatial-coverage")
                    .resolve(runId);
        }
        Path requested = Path.of(configured);
        return (requested.isAbsolute() ? requested : gameDirectory.resolve(requested))
                .toAbsolutePath()
                .normalize();
    }

    private static void publish(
            PanelActionSupport.CapturedPanel target,
            Consumer<Component> feedback,
            Component message
    ) {
        Minecraft.getInstance().execute(() -> {
            feedback.accept(message);
            if (Minecraft.getInstance().screen == target.workspace()) {
                target.workspace().showWorkspaceToast("sfm:spatial-coverage", message, false);
            }
        });
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
