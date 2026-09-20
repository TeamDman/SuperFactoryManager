package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewWorkQueue;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPreviewPlacement;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Shared canonical actions for palette commands and the visible review queue toolbar. */
public final class SFMReviewWorkQueueControls {
    public record Control(SFMReleaseReviewAction.Kind kind, String label, String description) {
        public SFMActionChoice choice() {
            return SFMActionChoice.invoke(new ResourceLocation("sfm", kind.path()), "", description);
        }
    }

    public static final List<Control> CONTROLS = List.of(
            new Control(SFMReleaseReviewAction.Kind.PREVIOUS, "Previous", "Previous non-deferred work unit; no wrap"),
            new Control(SFMReleaseReviewAction.Kind.NEXT, "Next", "Next non-deferred work unit; no wrap"),
            new Control(SFMReleaseReviewAction.Kind.DEFER, "Defer", "Defer saved work and advance; this is not approval"),
            new Control(SFMReleaseReviewAction.Kind.RESUME, "Resume", "Resume the first deferred unit in the saved queue"),
            new Control(SFMReleaseReviewAction.Kind.SHOW_CURRENT, "Show current", "Show current saved work without advancing or saving")
    );

    private SFMReviewWorkQueueControls() { }

    public static boolean isQueueKind(SFMReleaseReviewAction.Kind kind) {
        return kind == SFMReleaseReviewAction.Kind.SELECT
                || CONTROLS.stream().anyMatch(control -> control.kind() == kind);
    }

    public static boolean invoke(SFMClientActionContext context, SFMReleaseReviewAction.Kind kind,
                                 Optional<String> selected, Consumer<Component> feedback) {
        var available = SFMReviewLensSetAction.capture(context);
        if (!available.isAvailable()) {
            String reason = available.unavailableReason().getString();
            new SFMReleaseReviewOperationFeedback(context, 0, feedback).failed(reason, reason, List.of());
            return false;
        }
        var target = available.target();
        var runtime = SFMReleaseReviewRuntime.get();
        var captured = runtime.snapshot();
        try {
            if (!target.stillCurrent()) throw new IllegalStateException("The captured review Explorer changed");
            if (kind == SFMReleaseReviewAction.Kind.SHOW_CURRENT) {
                var status = new SFMReleaseReviewOperationFeedback(context, 0, feedback);
                status.pending(Component.literal("Locating saved review work…"));
                show(target, captured, status, "Saved review position retained; no file write", false);
                return true;
            }
            var operation = SFMReleaseReviewWorkQueue.Operation.valueOf(kind.name());
            var pending = runtime.workQueueAsync(captured, operation, selected);
            var status = new SFMReleaseReviewOperationFeedback(context,
                    runtime.pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L), feedback);
            status.pending(Component.literal("Saving review work cursor…"));
            pending.whenComplete((result, failure) -> onClient(status, () -> {
                if (failure != null || !result.saved()) {
                    String detail = failure == null ? result.failure().orElse("Queue change was not saved") : message(failure);
                    status.failed("Work cursor unchanged. " + concise(detail), detail, List.of());
                    return;
                }
                var now = runtime.snapshot();
                if (!target.stillCurrent() || now.generation() != captured.generation() + 1) {
                    status.complete(Component.literal("Work cursor saved; originating view changed, so it was not replaced."));
                    return;
                }
                if (now.document().orElseThrow().resumeState().currentUnitId().isEmpty()) {
                    status.complete(Component.literal("Work deferred and saved. End of queue; use Resume for deferred work."));
                    return;
                }
                show(target, now, status, "Work cursor saved", true);
            }));
            return true;
        } catch (RuntimeException failure) {
            String reason = message(failure);
            new SFMReleaseReviewOperationFeedback(context, 0, feedback)
                    .failed("Review work unavailable: " + concise(reason), reason, List.of());
            return false;
        }
    }

    private static void show(SFMReviewLensSetAction.Target target, SFMReleaseReviewRuntime.Snapshot captured,
                             SFMReleaseReviewOperationFeedback status, String outcome, boolean refreshAfterMutation) {
        var runtime = SFMReleaseReviewExplorerRuntime.get();
        var origin = SFMClientActionContinuation.capture(target.actionContext());
        var lensReady = refreshAfterMutation
                ? runtime.switchLens(target.explorer(), SFMReleaseReviewExplorerScreenType.Projection.QUERY, Optional.empty())
                : runtime.ensureActiveWorkQueueLens(target.explorer());
        lensReady
                .whenComplete((lens, switchFailure) -> onClient(status, () -> {
                    if (switchFailure != null) {
                        status.failed(outcome + "; queue display unavailable.", message(switchFailure), List.of());
                        return;
                    }
                    var guard = new SFMReleaseReviewNavigationGuard(origin, origin, captured.path().orElseThrow(),
                            captured.openEpoch(), captured.generation(), Set.of(lens.root()));
                    if (!guard.isCurrent(SFMReleaseReviewRuntime.get().snapshot())) {
                        stale(status, outcome);
                        return;
                    }
                    runtime.currentWorkTargetAsync(lens.root()).whenComplete((work, resolutionFailure) ->
                            onClient(status, () -> {
                                if (!guard.isCurrent(SFMReleaseReviewRuntime.get().snapshot())) {
                                    stale(status, outcome);
                                    return;
                                }
                                if (resolutionFailure != null) {
                                    status.failed("Saved work cannot be shown. " + concise(message(resolutionFailure)),
                                            message(resolutionFailure), List.of());
                                    return;
                                }
                                var revealPath = work.documentPath().orElse(work.unitPath());
                                target.explorer().revealPathRetainingFilter(lens.root(), revealPath).whenComplete((result, failure) ->
                                        onClient(status, () -> {
                                            if (!guard.isCurrent(SFMReleaseReviewRuntime.get().snapshot())) {
                                                stale(status, outcome);
                                                return;
                                            }
                                            if (failure != null) {
                                                status.failed(outcome + "; reveal unavailable.", message(failure), List.of());
                                                return;
                                            }
                                            work.documentPath().ifPresent(path -> runtime.openDocument(
                                                    new SFMClientActionContext(target.workspace(),
                                                            () -> guard.isCurrent(SFMReleaseReviewRuntime.get().snapshot()),
                                                            target.panelId()), path, SFMExplorerPreviewPlacement.Mode.PREVIEW));
                                            String filter = target.explorer().sessionSnapshot().settings().filterQuery();
                                            String ending = work.documentPath().isEmpty() ? "; source unavailable for this unit" : "";
                                            status.complete(Component.literal(outcome + ending + (filter.isEmpty() ? ""
                                                    : "; filter retained and may hide the cursor—clear it to reveal the row")));
                                            SFM.LOGGER.info("SFM_REVIEW_WORK_CURSOR_SHOWN unit={} generation={} filter={}",
                                                    work.unitId(), captured.generation(), filter);
                                        }));
                            }));
                }));
    }

    private static void stale(SFMReleaseReviewOperationFeedback status, String outcome) {
        status.complete(Component.literal(outcome + "; view changed, so navigation was not applied."));
    }

    private static void onClient(SFMReleaseReviewOperationFeedback status, Runnable action) {
        Minecraft.getInstance().execute(() -> {
            try {
                action.run();
            } catch (RuntimeException failure) {
                status.failed("Review navigation unavailable; saved progress is retained.", message(failure), List.of());
                SFM.LOGGER.warn("SFM_REVIEW_WORK_NAVIGATION_FAILED", failure);
            }
        });
    }

    private static String concise(String detail) {
        return detail.length() <= 150 ? detail : "See notification details for the reason.";
    }

    private static String message(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getSimpleName());
    }
}
