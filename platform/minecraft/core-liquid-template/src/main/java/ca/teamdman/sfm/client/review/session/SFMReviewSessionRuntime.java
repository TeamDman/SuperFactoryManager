package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Application-scoped canonical review sessions for trajectory machines. */
public final class SFMReviewSessionRuntime {
    private static final SFMReviewSessionRuntime INSTANCE = new SFMReviewSessionRuntime(
            SFMHistoryGraphRuntime.get(),
            machineId -> SFMReviewSessionStore.forSessionId("candidate-comments/" + machineId)
    );

    private final SFMHistoryGraphRuntime historyRuntime;
    private final Function<String, SFMReviewSessionStore> storeFactory;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public SFMReviewSessionRuntime(
            SFMHistoryGraphRuntime historyRuntime,
            Function<String, SFMReviewSessionStore> storeFactory
    ) {
        this.historyRuntime = Objects.requireNonNull(historyRuntime, "historyRuntime");
        this.storeFactory = Objects.requireNonNull(storeFactory, "storeFactory");
    }

    public static SFMReviewSessionRuntime get() {
        return INSTANCE;
    }

    public synchronized SFMReviewSessionV2 session(String machineId) {
        return entry(machineId).session;
    }

    public synchronized List<SFMReviewSessionV2.Comment> commentsForFrame(
            String machineId,
            String planRevisionId,
            String routeId,
            int routeStepPosition
    ) {
        return entry(machineId).session.comments().stream()
                .filter(comment -> comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget)
                .filter(comment -> {
                    SFMReviewSessionV2.CandidateTrajectoryTarget target =
                            (SFMReviewSessionV2.CandidateTrajectoryTarget) comment.target();
                    return target.trajectoryPlanRevisionId().equals(planRevisionId)
                            && target.routeId().equals(routeId)
                            && (target.targetKind() == SFMReviewSessionV2.CandidateTargetKind.ROUTE
                            || target.routeStepPosition() == routeStepPosition);
                })
                .toList();
    }

    public synchronized Optional<SFMReviewSessionV2.Comment> comment(String machineId, String commentId) {
        return entry(machineId).session.comments().stream()
                .filter(comment -> comment.id().equals(commentId))
                .findFirst();
    }

    public synchronized String createCandidateComment(
            SFMCandidateHistoryContract.CandidateRouteProjection route,
            SFMCandidateHistoryContract.CandidateFrame frame,
            SFMReviewSessionV2.CandidateTargetKind targetKind,
            Optional<SFMCandidateCommentTargetAdapter.Utf8Range> range,
            String text
    ) {
        text = requireText(text, "comment text");
        SFMReviewSessionV2.CandidateTrajectoryTarget target = SFMCandidateCommentTargetAdapter.capture(
                route, frame, targetKind, range);
        Entry entry = entry(route.machineId());
        String id = nextCommentId(entry.session);
        SFMReviewSessionV2.Comment comment = new SFMReviewSessionV2.Comment(
                id,
                text,
                new SFMReviewSessionV1.Provenance("human", "in-game-reviewer", "2", List.of()),
                target
        );
        entry.session = replaceComments(entry.session, append(entry.session.comments(), comment));
        persist(entry);
        return id;
    }

    public synchronized void editComment(String machineId, String commentId, String text) {
        String replacementText = requireText(text, "comment text");
        Entry entry = entry(machineId);
        entry.session = replaceOne(entry.session, commentId, comment -> new SFMReviewSessionV2.Comment(
                comment.id(), replacementText, comment.provenance(), comment.target()));
        persist(entry);
    }

    public synchronized void archiveComment(String machineId, String commentId) {
        Entry entry = entry(machineId);
        entry.session = replaceOne(entry.session, commentId, comment -> {
            if (SFMReviewSessionV1Kernel.derivedHashtags(comment.text()).contains("#archived")) return comment;
            return new SFMReviewSessionV2.Comment(
                    comment.id(), "#archived " + comment.text(), comment.provenance(), comment.target());
        });
        persist(entry);
    }

    public synchronized SFMReviewSessionV2Kernel.PromotionResult promoteExact(
            String machineId,
            String commentId,
            String decisionId
    ) {
        Entry entry = entry(machineId);
        SFMReviewSessionV2.Comment comment = entry.session.comments().stream()
                .filter(value -> value.id().equals(commentId))
                .findFirst()
                .orElse(null);
        if (comment == null) {
            return new SFMReviewSessionV2Kernel.PromotionResult(
                    SFMReviewSessionV2Kernel.PromotionStatus.MISSING,
                    entry.session,
                    Optional.empty(),
                    "Candidate comment was not found"
            );
        }
        if (!(comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target)
                || target.projectedDocumentSelection().isEmpty()) {
            return new SFMReviewSessionV2Kernel.PromotionResult(
                    SFMReviewSessionV2Kernel.PromotionStatus.UNSUPPORTED,
                    entry.session,
                    Optional.empty(),
                    "Only candidate document-region comments can be promoted in this slice"
            );
        }
        Optional<SFMHistoryGraphRuntime.CommittedDocument> committed = historyRuntime.committedDocument(machineId);
        if (committed.isEmpty()) {
            return new SFMReviewSessionV2Kernel.PromotionResult(
                    SFMReviewSessionV2Kernel.PromotionStatus.MISSING,
                    entry.session,
                    Optional.empty(),
                    "Trajectory machine does not expose committed document evidence"
            );
        }
        SFMHistoryGraphRuntime.CommittedDocument document = committed.orElseThrow();
        SFMReviewSessionV2.ProjectedDocumentSelection selection = target.projectedDocumentSelection().orElseThrow();
        SFMReviewSessionV1.DocumentRevision revision = executionRevision(document);
        SFMReviewSessionV2Kernel.PromotionResult result = SFMReviewSessionV2Kernel.promoteExact(
                entry.session,
                commentId,
                new SFMReviewSessionV2Kernel.ExactExecutionWitness(
                        requireText(decisionId, "decisionId"),
                        document.actualHistoryHeadId(),
                        document.stateId(),
                        document.stateHash(),
                        document.documentId(),
                        revision,
                        selection.startByte(),
                        selection.endByte()
                )
        );
        if (result.status() == SFMReviewSessionV2Kernel.PromotionStatus.PROMOTED_EXACTLY) {
            entry.session = result.session();
            persist(entry);
        }
        return result;
    }

    public synchronized SFMReviewSessionV2Kernel.MigrationResult migrateWitnessed(
            String machineId,
            String commentId,
            int startByte,
            int endByte,
            String decisionId,
            List<String> correspondenceEvidence
    ) {
        Entry entry = entry(machineId);
        SFMReviewSessionV2.Comment comment = entry.session.comments().stream()
                .filter(value -> value.id().equals(commentId))
                .findFirst()
                .orElse(null);
        if (comment == null) {
            return new SFMReviewSessionV2Kernel.MigrationResult(
                    SFMReviewSessionV2Kernel.MigrationStatus.MISSING,
                    entry.session,
                    Optional.empty(),
                    "Candidate comment was not found"
            );
        }
        Optional<SFMHistoryGraphRuntime.CommittedDocument> committed = historyRuntime.committedDocument(machineId);
        if (committed.isEmpty()) {
            return new SFMReviewSessionV2Kernel.MigrationResult(
                    SFMReviewSessionV2Kernel.MigrationStatus.MISSING,
                    entry.session,
                    Optional.empty(),
                    "Trajectory machine does not expose committed document evidence"
            );
        }
        SFMHistoryGraphRuntime.CommittedDocument document = committed.orElseThrow();
        SFMReviewSessionV2Kernel.MigrationResult result = SFMReviewSessionV2Kernel.migrateWitnessed(
                entry.session,
                commentId,
                new SFMReviewSessionV2Kernel.WitnessedMigrationDecision(
                        requireText(decisionId, "decisionId"),
                        document.actualHistoryHeadId(),
                        document.stateId(),
                        document.stateHash(),
                        document.documentId(),
                        executionRevision(document),
                        startByte,
                        endByte,
                        correspondenceEvidence
                )
        );
        if (result.status() == SFMReviewSessionV2Kernel.MigrationStatus.MIGRATED_WITH_WITNESS) {
            entry.session = result.session();
            persist(entry);
        }
        return result;
    }

    /** Reloads the canonical persisted session and returns its migration/recovery evidence. */
    public synchronized SFMReviewSessionStore.LoadResult reload(String machineId) {
        String resolvedMachineId = requireText(machineId, "machineId");
        SFMReviewSessionStore store = storeFactory.apply(resolvedMachineId);
        SFMReviewSessionStore.LoadResult loaded = store.load();
        SFMReviewSessionV2 session = loaded.session().orElseGet(() -> emptySession(resolvedMachineId));
        entries.put(resolvedMachineId, new Entry(resolvedMachineId, store, session, loaded.diagnostics()));
        return loaded;
    }

    /** Bounded fixture/test reset; production UI does not expose destructive session clearing. */
    public synchronized void resetForFixture(String machineId) {
        Entry entry = entry(machineId);
        entry.session = emptySession(machineId);
        entry.diagnostics = List.of("Fixture reset");
        persist(entry);
    }

    public synchronized List<String> diagnostics(String machineId) {
        return entry(machineId).diagnostics;
    }

    private Entry entry(String machineId) {
        String resolvedMachineId = requireText(machineId, "machineId");
        Entry existing = entries.get(resolvedMachineId);
        if (existing != null) return existing;
        SFMReviewSessionStore store = storeFactory.apply(resolvedMachineId);
        SFMReviewSessionStore.LoadResult loaded = store.load();
        Entry created = new Entry(
                resolvedMachineId,
                store,
                loaded.session().orElseGet(() -> emptySession(resolvedMachineId)),
                loaded.diagnostics()
        );
        entries.put(resolvedMachineId, created);
        return created;
    }

    private static SFMReviewSessionV2 replaceOne(
            SFMReviewSessionV2 session,
            String commentId,
            java.util.function.UnaryOperator<SFMReviewSessionV2.Comment> replacement
    ) {
        ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>(session.comments());
        for (int index = 0; index < comments.size(); index++) {
            if (!comments.get(index).id().equals(commentId)) continue;
            comments.set(index, replacement.apply(comments.get(index)));
            return replaceComments(session, comments);
        }
        throw new IllegalArgumentException("Unknown comment " + commentId);
    }

    private static SFMReviewSessionV2 replaceComments(
            SFMReviewSessionV2 session,
            List<SFMReviewSessionV2.Comment> comments
    ) {
        return new SFMReviewSessionV2(
                session.schema(), session.id(), session.title(), session.coordinateSystem(),
                session.revisionLanes(), comments, session.styleRules(), session.completionPolicy());
    }

    private static List<SFMReviewSessionV2.Comment> append(
            List<SFMReviewSessionV2.Comment> comments,
            SFMReviewSessionV2.Comment comment
    ) {
        ArrayList<SFMReviewSessionV2.Comment> answer = new ArrayList<>(comments);
        answer.add(comment);
        return List.copyOf(answer);
    }

    private static String nextCommentId(SFMReviewSessionV2 session) {
        java.util.Set<String> ids = session.comments().stream().map(SFMReviewSessionV2.Comment::id)
                .collect(java.util.stream.Collectors.toSet());
        for (int candidate = 1; ; candidate++) {
            String id = "candidate-human-" + candidate;
            if (!ids.contains(id)) return id;
        }
    }

    private static SFMReviewSessionV2 emptySession(String machineId) {
        return SFMReviewSessionV2.empty(
                "sfm:candidate-comments/" + safeSegment(machineId),
                "Candidate comments · " + machineId
        );
    }

    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static SFMReviewSessionV1.DocumentRevision executionRevision(
            SFMHistoryGraphRuntime.CommittedDocument document
    ) {
        byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
        String documentSha256 = SFMReviewSessionV1Kernel.sha256(bytes);
        String revisionId = "candidate-execution/" + safeSegment(document.machineId()) + "/"
                + safeSegment(document.documentId()) + "/" + documentSha256;
        return new SFMReviewSessionV1.DocumentRevision(
                revisionId,
                revisionId + ".txt",
                "utf-8",
                documentSha256,
                document.text()
        );
    }

    private static void persist(Entry entry) {
        SFMReviewSessionV2Kernel.evaluateAll(entry.session);
        try {
            entry.store.save(entry.session);
            entry.diagnostics = List.of("Saved " + entry.store.path());
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to persist candidate comments", failure);
        }
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static final class Entry {
        private final String machineId;
        private final SFMReviewSessionStore store;
        private SFMReviewSessionV2 session;
        private List<String> diagnostics;

        private Entry(
                String machineId,
                SFMReviewSessionStore store,
                SFMReviewSessionV2 session,
                List<String> diagnostics
        ) {
            this.machineId = machineId;
            this.store = store;
            this.session = session;
            this.diagnostics = List.copyOf(diagnostics);
        }
    }
}
