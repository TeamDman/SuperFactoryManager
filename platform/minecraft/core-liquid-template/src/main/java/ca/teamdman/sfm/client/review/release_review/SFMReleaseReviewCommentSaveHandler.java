package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveHandler;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** A freeform editor retains its exact draft, then edits the same durable comment on subsequent saves. */
public final class SFMReleaseReviewCommentSaveHandler implements SFMTextDocumentSaveHandler {
    public record Submission(
            long operationId,
            String draftId,
            CompletableFuture<SFMTextDocumentSaveResult> completion
    ) { }

    private final SFMReleaseReviewRuntime runtime;
    private final SFMReleaseReviewCommentDraftService service;
    private final SFMReleaseReviewCommentDraftService.Draft draft;
    private final Runnable afterSave;
    private final Consumer<Submission> afterSubmit;
    private String commentId;
    private String savedText;
    private CompletableFuture<SFMTextDocumentSaveResult> pending;
    private long operationId;

    public SFMReleaseReviewCommentSaveHandler(
            SFMReleaseReviewRuntime runtime, SFMReleaseReviewCommentDraftService service,
            String draftId, Runnable afterSave
    ) {
        this(runtime, service, draftId, afterSave, ignored -> { });
    }

    public SFMReleaseReviewCommentSaveHandler(
            SFMReleaseReviewRuntime runtime, SFMReleaseReviewCommentDraftService service,
            String draftId, Runnable afterSave, Consumer<Submission> afterSubmit
    ) {
        this.runtime = runtime;
        this.service = service;
        this.draft = service.requireCurrent(draftId);
        this.afterSave = afterSave;
        this.afterSubmit = afterSubmit;
    }

    @Override public boolean asynchronous() { return true; }

    @Override public boolean detachSaveAndCloseAfterSubmission() { return true; }

    @Override public SFMTextDocumentSaveResult save(String content) {
        return SFMTextDocumentSaveResult.rejected(Component.literal(
                "This review comment requires an editor with asynchronous Save/Done support"));
    }

    @Override
    public synchronized CompletableFuture<SFMTextDocumentSaveResult> saveAsync(String content) {
        if (pending != null && !pending.isDone()) return pending;
        var snapshot = runtime.snapshot();
        if (snapshot.openEpoch() != draft.reviewOpenEpoch()
                || !snapshot.path().filter(draft.reviewPath()::equals).isPresent()) {
            return CompletableFuture.completedFuture(SFMTextDocumentSaveResult.rejected(
                    Component.literal("The review was replaced after this comment editor was opened")));
        }
        if (commentId != null && content.equals(savedText)) {
            return CompletableFuture.completedFuture(SFMTextDocumentSaveResult.success());
        }
        CompletableFuture<SFMReleaseReviewRuntime.CommentMutationResult> persistence;
        if (commentId == null) {
            persistence = service.applyAsync(draft.id(), content);
        } else {
            String savedId = commentId;
            persistence = runtime.updateCommentAsync(draft.reviewPath(), draft.reviewOpenEpoch(),
                    savedId, savedText, content).thenApply(result ->
                    new SFMReleaseReviewRuntime.CommentMutationResult(savedId, result));
        }
        operationId = runtime.pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L);
        pending = persistence.handle((result, failure) -> {
            if (failure != null) {
                Throwable root = failure;
                while (root.getCause() != null) root = root.getCause();
                return SFMTextDocumentSaveResult.rejected(Component.literal(
                        Optional.ofNullable(root.getMessage()).orElse(root.getClass().getSimpleName())));
            }
            if (!result.mutation().saved()) return SFMTextDocumentSaveResult.rejected(Component.literal(
                    result.mutation().failure().orElse("Review comment was not saved")));
            synchronized (SFMReleaseReviewCommentSaveHandler.this) {
                commentId = result.commentId();
                savedText = content;
            }
            try {
                afterSave.run();
            } catch (RuntimeException presentationFailure) {
                // Persistence already committed. A detached/failed UI refresh must never
                // turn that fact into a false save failure or duplicate retry.
                ca.teamdman.sfm.SFM.LOGGER.warn(
                        "SFM_REVIEW_COMMENT_SAVED_REFRESH_FAILED comment={} review={}",
                        result.commentId(), draft.reviewPath(), presentationFailure);
            }
            return SFMTextDocumentSaveResult.success();
        });
        try {
            afterSubmit.accept(new Submission(operationId, draft.id(), pending));
        } catch (RuntimeException presentationFailure) {
            ca.teamdman.sfm.SFM.LOGGER.warn(
                    "SFM_REVIEW_COMMENT_SAVE_FEEDBACK_FAILED draft={} review={}",
                    draft.id(), draft.reviewPath(), presentationFailure);
        }
        return pending;
    }

    @Override public synchronized boolean cancelPendingSave() {
        return pending != null && !pending.isDone() && runtime.cancelOperation(operationId);
    }
}
