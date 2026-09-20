package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Bounded one-shot registry for comment choices captured from an exact editor
 * selection. Choice palettes retain this immutable evidence rather than
 * recapturing whichever panel happens to be focused when a later choice runs.
 */
public final class SFMReleaseReviewCommentDraftService {
    public static final int MAXIMUM_DRAFTS = 128;
    public static final int MAXIMUM_RECENT_TEMPLATES = 5;
    private static final SFMReleaseReviewCommentDraftService INSTANCE =
            new SFMReleaseReviewCommentDraftService(SFMReleaseReviewRuntime.get());

    public record Draft(
            String id,
            Path reviewPath,
            long reviewOpenEpoch,
            String corpusFingerprint,
            SFMReleaseReviewEditorCapture.Capture capture,
            SFMReleaseReviewV1.SelectorProposal proposal
    ) {
        public Draft {
            id = requireText(id, "id");
            reviewPath = Objects.requireNonNull(reviewPath, "reviewPath").toAbsolutePath().normalize();
            if (reviewOpenEpoch <= 0) throw new IllegalArgumentException("reviewOpenEpoch must be positive");
            corpusFingerprint = requireText(corpusFingerprint, "corpusFingerprint");
            Objects.requireNonNull(capture, "capture");
            Objects.requireNonNull(proposal, "proposal");
            if (!capture.proposals().proposals().contains(proposal)) {
                throw new IllegalArgumentException("The draft proposal is absent from its frozen capture");
            }
        }

        Draft rebound(long nextOpenEpoch) {
            return new Draft(id, reviewPath, nextOpenEpoch, corpusFingerprint, capture, proposal);
        }
    }

    private final SFMReleaseReviewRuntime runtime;
    private final LinkedHashMap<String, Draft> drafts = new LinkedHashMap<>();
    private record PendingDraft(String text, long operationId,
                                CompletableFuture<SFMReleaseReviewRuntime.CommentMutationResult> result) { }
    private final LinkedHashMap<String, PendingDraft> pendingDrafts = new LinkedHashMap<>();
    public enum Cancellation { DISCARDED, REQUESTED_BEFORE_COMMIT, COMMITTING, ABSENT }
    private long nextId = 1;

    SFMReleaseReviewCommentDraftService(SFMReleaseReviewRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public static SFMReleaseReviewCommentDraftService get() {
        return INSTANCE;
    }

    public synchronized Draft create(
            SFMReleaseReviewEditorCapture.Capture capture,
            SFMReleaseReviewV1.SelectorProposal proposal
    ) {
        Objects.requireNonNull(capture, "capture");
        Objects.requireNonNull(proposal, "proposal");
        SFMReleaseReviewRuntime.Snapshot snapshot = requireOpenSnapshot();
        String id = "review-comment-draft-" + nextId++;
        Draft draft = new Draft(
                id,
                snapshot.path().orElseThrow(),
                snapshot.openEpoch(),
                corpusFingerprint(snapshot.document().orElseThrow()),
                capture,
                proposal
        );
        drafts.put(id, draft);
        while (drafts.size() > MAXIMUM_DRAFTS) {
            String evicted = drafts.keySet().stream().filter(key -> !pendingDrafts.containsKey(key)).findFirst().orElseThrow();
            drafts.remove(evicted);
        }
        return draft;
    }

    public synchronized Draft requireCurrent(String id) {
        Draft draft = require(id);
        SFMReleaseReviewRuntime.Snapshot snapshot = requireOpenSnapshot();
        if (!draft.reviewPath().equals(snapshot.path().orElseThrow().toAbsolutePath().normalize())) {
            throw new IllegalStateException("A different release-review file is now open");
        }
        if (draft.reviewOpenEpoch() != snapshot.openEpoch()) {
            throw new IllegalStateException("The release-review session was replaced after this selection was captured");
        }
        if (!draft.corpusFingerprint().equals(corpusFingerprint(snapshot.document().orElseThrow()))) {
            throw new IllegalStateException("The pinned release-review corpus changed after this selection was captured");
        }
        return draft;
    }

    public synchronized Draft require(String id) {
        Draft draft = drafts.get(requireText(id, "id"));
        if (draft == null) throw new IllegalArgumentException("Unknown or expired review comment draft: " + id);
        return draft;
    }

    public synchronized Draft rebindAfterWritableOpen(String id) {
        Draft draft = require(id);
        SFMReleaseReviewRuntime.Snapshot snapshot = requireOpenSnapshot();
        if (!snapshot.writable()) throw new IllegalStateException("The release review is still read-only");
        if (!draft.reviewPath().equals(snapshot.path().orElseThrow().toAbsolutePath().normalize())) {
            throw new IllegalStateException("Writable reopen selected a different review file");
        }
        if (!draft.corpusFingerprint().equals(corpusFingerprint(snapshot.document().orElseThrow()))) {
            throw new IllegalStateException("Writable reopen produced a different pinned review corpus");
        }
        Draft rebound = draft.rebound(snapshot.openEpoch());
        drafts.put(id, rebound);
        return rebound;
    }

    public synchronized SFMReleaseReviewRuntime.CommentMutationResult apply(String id, String text) {
        Draft draft = requireCurrent(id);
        SFMReleaseReviewRuntime.CommentMutationResult result = runtime.createComment(
                requireCommentText(text),
                draft.capture().adapted().pinnedSelection(),
                draft.proposal()
        );
        if (result.mutation().saved()) drafts.remove(id);
        return result;
    }

    public synchronized CompletableFuture<SFMReleaseReviewRuntime.CommentMutationResult> applyAsync(
            String id,
            String text
    ) {
        PendingDraft previous = pendingDrafts.get(id);
        if (previous != null) {
            return previous.text().equals(text) ? previous.result() : CompletableFuture.failedFuture(
                    new IllegalStateException("This comment draft already has a different save pending"));
        }
        Draft draft = requireCurrent(id);
        var persistence = runtime.createCommentAsync(
                requireCommentText(text),
                draft.capture().adapted().pinnedSelection(),
                draft.proposal()
        );
        var completion = new CompletableFuture<SFMReleaseReviewRuntime.CommentMutationResult>();
        var work = new PendingDraft(text,
                runtime.pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L), completion);
        pendingDrafts.put(id, work);
        persistence.whenComplete((result, failure) -> {
            synchronized (SFMReleaseReviewCommentDraftService.this) {
                pendingDrafts.remove(id, work);
                if (failure == null && result.mutation().saved()) drafts.remove(id, draft);
            }
            if (failure != null) completion.completeExceptionally(failure);
            else completion.complete(result);
        });
        return completion;
    }

    public synchronized boolean cancel(String id) {
        Cancellation result = cancelChoice(id);
        return result == Cancellation.DISCARDED || result == Cancellation.REQUESTED_BEFORE_COMMIT;
    }

    public synchronized Cancellation cancelChoice(String id) {
        id = requireText(id, "id");
        PendingDraft pending = pendingDrafts.get(id);
        if (pending != null) return runtime.cancelOperation(pending.operationId())
                ? Cancellation.REQUESTED_BEFORE_COMMIT : Cancellation.COMMITTING;
        return drafts.remove(id) != null ? Cancellation.DISCARDED : Cancellation.ABSENT;
    }

    public synchronized List<String> recentTemplates() {
        return recentHumanTemplates(requireOpenSnapshot().document().orElseThrow().reviewSession().comments());
    }

    static List<String> recentHumanTemplates(
            List<ca.teamdman.sfm.client.review.session.SFMReviewSessionV2.Comment> comments
    ) {
        LinkedHashSet<String> newestFirst = new LinkedHashSet<>();
        for (int index = comments.size() - 1; index >= 0 && newestFirst.size() < MAXIMUM_RECENT_TEMPLATES; index--) {
            var comment = comments.get(index);
            // Generated units are useful review objects, but are not human writing templates.
            // Eligibility is provenance, not a blacklist of words a human might legitimately use.
            if (!"human".equals(comment.provenance().kind())) continue;
            if (!comment.text().isBlank()) newestFirst.add(comment.text());
        }
        return List.copyOf(newestFirst);
    }

    synchronized List<Draft> draftsForTests() {
        return List.copyOf(drafts.values());
    }

    synchronized void clearForTests() {
        pendingDrafts.values().forEach(work -> runtime.cancelOperation(work.operationId()));
        pendingDrafts.clear();
        drafts.clear();
        nextId = 1;
    }

    private SFMReleaseReviewRuntime.Snapshot requireOpenSnapshot() {
        SFMReleaseReviewRuntime.Snapshot snapshot = runtime.snapshot();
        if (snapshot.document().isEmpty()) throw new IllegalStateException("No release-review document is open");
        return snapshot;
    }

    static String corpusFingerprint(SFMReleaseReviewV1 review) {
        ArrayList<String> rows = new ArrayList<>();
        for (SFMReleaseReviewV1.CorpusDocument document : review.corpusDocuments()) {
            rows.add(document.documentRevisionId() + "\u0000" + document.sha256() + "\u0000"
                    + document.snapshotSide() + "\u0000" + document.path());
        }
        rows.sort(String::compareTo);
        return "sha256:" + SFMReviewSessionV1Kernel.sha256(
                String.join("\n", rows).getBytes(StandardCharsets.UTF_8));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String answer = value.strip();
        if (answer.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return answer;
    }

    private static String requireCommentText(String value) {
        Objects.requireNonNull(value, "text");
        if (value.isBlank()) throw new IllegalArgumentException("Comment text must not be blank");
        return value;
    }
}
