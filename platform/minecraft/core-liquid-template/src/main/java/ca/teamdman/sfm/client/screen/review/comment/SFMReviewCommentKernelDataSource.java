package ca.teamdman.sfm.client.screen.review.comment;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
{% if features.legacy_comment_review or features.legacy_repository_review %}
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Store;
{% else %}
{% if features.review_sessions %}
import ca.teamdman.sfm.client.review.session.SFMReviewSessionStore;
{% endif %}
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;
{% endif %}

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

{% if features.legacy_comment_review or features.legacy_repository_review %}
/** Mutable UI adapter over the authoritative immutable v1 session model. */
{% else %}
/** Mutable UI adapter over the authoritative immutable v2 session model. */
{% endif %}
public final class SFMReviewCommentKernelDataSource implements SFMReviewCommentDataSource {
{% if features.legacy_comment_review or features.legacy_repository_review %}
    private final SFMReviewSessionV1Store store;
{% else %}
{% if features.review_sessions %}
    private final SFMReviewSessionStore store;
{% endif %}
{% endif %}
    private final List<LegacyRow> legacyRows;
{% if features.legacy_comment_review or features.legacy_repository_review %}
    private SFMReviewSessionV1 session;
{% else %}
    private SFMReviewSessionV2 session;
{% endif %}

{% if features.review_sessions or features.release_review %}
    /** Convenience import for callers that still hold a frozen v1 session. */
{% endif %}
    public SFMReviewCommentKernelDataSource(SFMReviewSessionV1 session) {
{% if features.review_sessions or features.release_review %}
        this(SFMReviewSessionV2Codec.migrate(session));
    }

    public SFMReviewCommentKernelDataSource(SFMReviewSessionV2 session) {
{% endif %}
{% if features.review_sessions or features.legacy_comment_review or features.legacy_repository_review %}
        this(session, null, List.of());
{% else %}
        this(session, List.of());
{% endif %}
    }

{% if features.review_sessions or features.release_review %}
{% if features.review_sessions %}
    public SFMReviewCommentKernelDataSource(SFMReviewSessionV2 session, SFMReviewSessionStore store) {
        this(session, store, List.of());
    }
{% endif %}

{% endif %}
    public SFMReviewCommentKernelDataSource(
{% if features.legacy_comment_review or features.legacy_repository_review %}
            SFMReviewSessionV1 session,
            SFMReviewSessionV1Store store,
{% else %}
            SFMReviewSessionV2 session,
{% if features.review_sessions %}
            SFMReviewSessionStore store,
{% endif %}
{% endif %}
            List<LegacyRow> legacyRows
    ) {
        this.session = Objects.requireNonNull(session, "session");
{% if features.review_sessions or features.legacy_comment_review or features.legacy_repository_review %}
        this.store = store;
{% endif %}
        this.legacyRows = List.copyOf(legacyRows);
{% if features.legacy_comment_review or features.legacy_repository_review %}
        SFMReviewSessionV1Kernel.evaluateAll(session);
{% else %}
        SFMReviewSessionV2Kernel.evaluateAll(session);
{% endif %}
    }

    @Override
    public SessionView refresh() {
        List<DocumentView> documents = new ArrayList<>();
        Set<String> documentIds = new java.util.HashSet<>();
        for (SFMReviewSessionV1.RevisionLane lane : session.revisionLanes()) {
            appendDocuments(documents, documentIds, lane.before().documents(), Side.BEFORE);
            appendDocuments(documents, documentIds, lane.after().documents(), Side.AFTER);
        }

{% if features.legacy_comment_review or features.legacy_repository_review %}
        List<SFMReviewSessionV1Kernel.Evaluation> evaluations = SFMReviewSessionV1Kernel.evaluateAll(session);
        Map<String, SFMReviewSessionV1Kernel.Evaluation> evaluationByComment = new LinkedHashMap<>();
{% else %}
        List<SFMReviewSessionV2Kernel.Evaluation> evaluations = SFMReviewSessionV2Kernel.evaluateAll(session);
        Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluationByComment = new LinkedHashMap<>();
{% endif %}
        evaluations.forEach(evaluation -> evaluationByComment.put(evaluation.commentId(), evaluation));
{% if features.legacy_comment_review or features.legacy_repository_review %}
        List<CommentView> comments = session.comments().stream().map(comment -> {
            SFMReviewSessionV1Kernel.Evaluation evaluation = evaluationByComment.get(comment.id());
{% else %}
        List<CommentView> comments = session.comments().stream()
{% if features.release_review %}
                .filter(comment -> !ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewGeneratedMarkers.isChangeMarker(comment))
{% endif %}
                .map(comment -> {
            SFMReviewSessionV2Kernel.Evaluation evaluation = evaluationByComment.get(comment.id());
{% endif %}
            Set<String> hashtags = Set.copyOf(SFMReviewSessionV1Kernel.derivedHashtags(comment.text()));
{% if features.review_sessions or features.release_review %}
            boolean candidate = comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget;
{% endif %}
            return new CommentView(comment.id(), comment.text(),
                    comment.provenance().kind() + " · " + comment.provenance().producer(),
                    hashtags.contains("#archived"),
{% if features.review_sessions or features.release_review %}
                    candidate,
                    targetLabel(comment.target()),
{% endif %}
                    evaluation.ranges().stream().map(SFMReviewCommentKernelDataSource::rangeView).toList(),
                    status(evaluation.status()));
        }).toList();
        List<StyleRuleView> styles = session.styleRules().stream().map(style -> new StyleRuleView(
                style.id(), style.requiredHashtags(), style.priority(), colour(style.foreground()),
{% if features.legacy_comment_review or features.legacy_repository_review %}
                colour(style.background()), colour(style.underline()), colour(style.gutterMarker()), style.enabled()
{% else %}
                colour(style.background()), colour(style.underline()), style.gutterMarker(), style.enabled()
{% endif %}
        )).toList();
{% if features.legacy_comment_review or features.legacy_repository_review %}
        List<MigrationView> migrations = evaluations.stream().map(evaluation -> new MigrationView(
{% else %}
        Set<String> visibleIds = comments.stream().map(CommentView::id).collect(java.util.stream.Collectors.toSet());
        List<MigrationView> migrations = evaluations.stream().filter(evaluation -> visibleIds.contains(evaluation.commentId()))
                .map(evaluation -> new MigrationView(
{% endif %}
                evaluation.commentId(), status(evaluation.status()), String.join("; ", evaluation.diagnostics())
        )).toList();
        return new SessionView(session.title(), documents, comments, styles, migrations, legacyRows);
    }

    @Override
    public String createLiteralComment(String text, List<RangeView> ranges) {
        if (text.isBlank()) throw new IllegalArgumentException("Comment text must not be blank");
        if (ranges.isEmpty()) throw new IllegalArgumentException("A comment requires at least one range");
        Map<String, SFMReviewSessionV1.DocumentRevision> documents = documentsById();
        List<SFMReviewSessionV1.SelectionRule> rules = ranges.stream()
                .map(range -> literalRule(range, documents))
                .map(SFMReviewSessionV1.SelectionRule.class::cast)
                .toList();
        SFMReviewSessionV1.SelectionRule rule = rules.size() == 1
                ? rules.get(0) : new SFMReviewSessionV1.Union(rules);
        String id = nextHumanId();
{% if features.legacy_comment_review or features.legacy_repository_review %}
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>(session.comments());
        comments.add(new SFMReviewSessionV1.Comment(id, text,
                new SFMReviewSessionV1.Provenance("human", "in-game-reviewer", "1", List.of()), rule));
{% else %}
        List<SFMReviewSessionV2.Comment> comments = new ArrayList<>(session.comments());
        comments.add(new SFMReviewSessionV2.Comment(id, text,
                new SFMReviewSessionV1.Provenance("human", "in-game-reviewer", "1", List.of()),
                new SFMReviewSessionV2.CommittedReviewTarget(rule)));
{% endif %}
        replaceComments(comments);
        return id;
    }

    @Override
    public void editComment(String id, String text) {
        if (text.isBlank()) throw new IllegalArgumentException("Comment text must not be blank");
{% if features.legacy_comment_review or features.legacy_repository_review %}
        replaceComment(id, comment -> new SFMReviewSessionV1.Comment(
                comment.id(), text, comment.provenance(), comment.selectionRule()));
{% else %}
        replaceComment(id, comment -> new SFMReviewSessionV2.Comment(
                comment.id(), text, comment.provenance(), comment.target()));
{% endif %}
    }

    @Override
    public void archiveComment(String id) {
        replaceComment(id, comment -> {
            if (SFMReviewSessionV1Kernel.derivedHashtags(comment.text()).contains("#archived")) return comment;
{% if features.legacy_comment_review or features.legacy_repository_review %}
            return new SFMReviewSessionV1.Comment(comment.id(), "#archived " + comment.text(),
                    comment.provenance(), comment.selectionRule());
{% else %}
            return new SFMReviewSessionV2.Comment(comment.id(), "#archived " + comment.text(),
                    comment.provenance(), comment.target());
{% endif %}
        });
    }

    @Override
    public void updateStyleColour(String id, StyleChannel channel, int argb) {
        String value = String.format(Locale.ROOT, "#%08X", argb);
        List<SFMReviewSessionV1.StyleRule> styles = new ArrayList<>(session.styleRules());
        for (int index = 0; index < styles.size(); index++) {
            SFMReviewSessionV1.StyleRule style = styles.get(index);
            if (!style.id().equals(id)) continue;
            styles.set(index, new SFMReviewSessionV1.StyleRule(style.id(), style.requiredHashtags(), style.priority(),
                    channel == StyleChannel.FOREGROUND ? value : style.foreground(),
                    channel == StyleChannel.BACKGROUND ? value : style.background(),
                    channel == StyleChannel.UNDERLINE ? value : style.underline(),
{% if features.legacy_comment_review or features.legacy_repository_review %}
                    channel == StyleChannel.GUTTER ? value : style.gutterMarker(), style.enabled()));
{% else %}
                    style.gutterMarker(), style.enabled()));
{% endif %}
            replaceStyles(styles);
            return;
        }
        throw new IllegalArgumentException("Unknown style rule " + id);
    }

{% if features.legacy_comment_review or features.legacy_repository_review %}
    public SFMReviewSessionV1 session() {
{% else %}
    public SFMReviewSessionV2 session() {
{% endif %}
        return session;
    }

{% if features.legacy_comment_review or features.legacy_repository_review %}
    private void replaceComment(String id, java.util.function.UnaryOperator<SFMReviewSessionV1.Comment> update) {
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>(session.comments());
{% else %}
    private void replaceComment(String id, java.util.function.UnaryOperator<SFMReviewSessionV2.Comment> update) {
        List<SFMReviewSessionV2.Comment> comments = new ArrayList<>(session.comments());
{% endif %}
        for (int index = 0; index < comments.size(); index++) {
            if (!comments.get(index).id().equals(id)) continue;
            comments.set(index, update.apply(comments.get(index)));
            replaceComments(comments);
            return;
        }
        throw new IllegalArgumentException("Unknown comment " + id);
    }

{% if features.legacy_comment_review or features.legacy_repository_review %}
    private void replaceComments(List<SFMReviewSessionV1.Comment> comments) {
        session = new SFMReviewSessionV1(session.schema(), session.id(), session.title(), session.coordinateSystem(),
{% else %}
    private void replaceComments(List<SFMReviewSessionV2.Comment> comments) {
        session = new SFMReviewSessionV2(session.schema(), session.id(), session.title(), session.coordinateSystem(),
{% endif %}
                session.revisionLanes(), comments, session.styleRules(), session.completionPolicy());
        persist();
    }

    private void replaceStyles(List<SFMReviewSessionV1.StyleRule> styles) {
{% if features.legacy_comment_review or features.legacy_repository_review %}
        session = new SFMReviewSessionV1(session.schema(), session.id(), session.title(), session.coordinateSystem(),
{% else %}
        session = new SFMReviewSessionV2(session.schema(), session.id(), session.title(), session.coordinateSystem(),
{% endif %}
                session.revisionLanes(), session.comments(), styles, session.completionPolicy());
        persist();
    }

    private void persist() {
{% if features.legacy_comment_review or features.legacy_repository_review %}
        SFMReviewSessionV1Kernel.evaluateAll(session);
{% else %}
        SFMReviewSessionV2Kernel.evaluateAll(session);
{% endif %}
{% if features.review_sessions or features.legacy_comment_review or features.legacy_repository_review %}
        if (store == null) return;
        try {
            store.save(session);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist review session", exception);
        }
{% endif %}
    }

    private SFMReviewSessionV1.LiteralUtf8Range literalRule(
            RangeView range,
            Map<String, SFMReviewSessionV1.DocumentRevision> documents
    ) {
        SFMReviewSessionV1.DocumentRevision document = documents.get(range.documentRevisionId());
        if (document == null) throw new IllegalArgumentException("Unknown document " + range.documentRevisionId());
        byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
        if (range.startByte() < 0 || range.endByte() < range.startByte() || range.endByte() > bytes.length) {
            throw new IllegalArgumentException("Range is outside document " + document.id());
        }
        SFMReviewSessionV1Kernel.utf8ByteToUtf16Index(document.text(), range.startByte());
        SFMReviewSessionV1Kernel.utf8ByteToUtf16Index(document.text(), range.endByte());
        byte[] selected = java.util.Arrays.copyOfRange(bytes, range.startByte(), range.endByte());
        return new SFMReviewSessionV1.LiteralUtf8Range(document.id(), range.startByte(), range.endByte(),
                SFMReviewSessionV1Kernel.sha256(bytes), SFMReviewSessionV1Kernel.sha256(selected));
    }

    private String nextHumanId() {
{% if features.legacy_comment_review or features.legacy_repository_review %}
        Set<String> ids = session.comments().stream().map(SFMReviewSessionV1.Comment::id)
{% else %}
        Set<String> ids = session.comments().stream().map(SFMReviewSessionV2.Comment::id)
{% endif %}
                .collect(java.util.stream.Collectors.toSet());
        for (int candidate = 1; ; candidate++) {
            String id = "human-" + candidate;
            if (!ids.contains(id)) return id;
        }
    }

    private Map<String, SFMReviewSessionV1.DocumentRevision> documentsById() {
        Map<String, SFMReviewSessionV1.DocumentRevision> result = new LinkedHashMap<>();
        for (SFMReviewSessionV1.RevisionLane lane : session.revisionLanes()) {
            lane.before().documents().forEach(document -> result.put(document.id(), document));
            lane.after().documents().forEach(document -> result.put(document.id(), document));
        }
        return result;
    }

    private static void appendDocuments(
            List<DocumentView> output,
            Set<String> documentIds,
            List<SFMReviewSessionV1.DocumentRevision> documents,
            Side side
    ) {
        for (SFMReviewSessionV1.DocumentRevision document : documents) {
            if (!documentIds.add(document.id())) throw new IllegalArgumentException("Duplicate document id " + document.id());
            output.add(new DocumentView(document.id(), side, document.path(), document.text()));
        }
    }

    private static RangeView rangeView(SFMReviewSessionV1Kernel.Range range) {
        return new RangeView(range.documentRevisionId(), range.startByte(), range.endByte());
    }

{% if features.legacy_comment_review or features.legacy_repository_review %}
    private static EvaluationStatus status(SFMReviewSessionV1Kernel.Status status) {
{% else %}
    private static EvaluationStatus status(SFMReviewSessionV2Kernel.Status status) {
{% endif %}
        return EvaluationStatus.valueOf(status.name());
    }

{% if features.review_sessions or features.release_review %}
    private static String targetLabel(SFMReviewSessionV2.CommentTarget target) {
        if (target instanceof SFMReviewSessionV2.CommittedReviewTarget committed) {
            String label = "committed " + selectionRuleLabel(committed.selectionRule());
            if (committed.candidatePromotion().isPresent()) {
                label += " · promoted-from="
                        + committed.candidatePromotion().orElseThrow().sourceCandidateCommentId()
                        + " · correspondence="
                        + committed.candidatePromotion().orElseThrow().correspondence();
            }
            return label;
        }
        SFMReviewSessionV2.CandidateTrajectoryTarget candidate =
                (SFMReviewSessionV2.CandidateTrajectoryTarget) target;
        StringBuilder label = new StringBuilder("candidate ")
                .append(candidate.targetKind().name().toLowerCase(Locale.ROOT))
                .append(" · machine=").append(candidate.machineId()).append('@').append(candidate.machineRevision())
                .append(" · plan=").append(candidate.trajectoryPlanRevisionId())
                .append(" · route=").append(candidate.routeId())
                .append(" · position=").append(candidate.routeStepPosition())
                .append(" · step=").append(candidate.trajectoryStepId().orElse("route-start"));
        candidate.actionIntentId().ifPresent(action -> label.append(" · action=").append(action));
        label.append(" · state=").append(candidate.predictedStateId());
        candidate.predictedStateHash().ifPresent(hash -> label.append(" · state-hash=").append(hash));
        label.append(" · status=").append(candidate.projectionStatus().name().toLowerCase(Locale.ROOT));
        candidate.projectedDocumentSelection().ifPresent(selection -> label
                .append(" · document=").append(selection.documentId())
                .append('[').append(selection.startByte()).append(',').append(selection.endByte()).append(')')
                .append("@").append(selection.documentTextSha256()));
        return label.toString();
    }

    private static String selectionRuleLabel(SFMReviewSessionV1.SelectionRule rule) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            return literal.documentRevisionId() + '[' + literal.startByte() + ',' + literal.endByte() + ')';
        }
        if (rule instanceof SFMReviewSessionV1.Union union) {
            return "union(" + union.rules().stream()
                    .map(SFMReviewCommentKernelDataSource::selectionRuleLabel)
                    .collect(java.util.stream.Collectors.joining(", ")) + ')';
        }
        if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            return "intersection(" + intersection.rules().stream()
                    .map(SFMReviewCommentKernelDataSource::selectionRuleLabel)
                    .collect(java.util.stream.Collectors.joining(", ")) + ')';
        }
        SFMReviewSessionV1.Difference difference = (SFMReviewSessionV1.Difference) rule;
        return "difference(" + selectionRuleLabel(difference.include()) + "; exclude="
                + difference.exclude().stream()
                .map(SFMReviewCommentKernelDataSource::selectionRuleLabel)
                .collect(java.util.stream.Collectors.joining(", ")) + ')';
    }

{% endif %}
    private static Integer colour(String value) {
        if (value == null) return null;
        if (!value.matches("#[0-9A-Fa-f]{8}")) throw new IllegalArgumentException("Invalid ARGB colour " + value);
        return (int) Long.parseLong(value.substring(1), 16);
    }
}
