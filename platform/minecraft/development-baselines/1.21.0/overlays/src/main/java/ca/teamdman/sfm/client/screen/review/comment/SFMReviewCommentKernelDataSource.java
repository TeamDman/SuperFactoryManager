package ca.teamdman.sfm.client.screen.review.comment;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable UI adapter over the authoritative immutable v1 session model. */
public final class SFMReviewCommentKernelDataSource implements SFMReviewCommentDataSource {
    private final SFMReviewSessionV1Store store;
    private final List<LegacyRow> legacyRows;
    private SFMReviewSessionV1 session;

    public SFMReviewCommentKernelDataSource(SFMReviewSessionV1 session) {
        this(session, null, List.of());
    }

    public SFMReviewCommentKernelDataSource(
            SFMReviewSessionV1 session,
            SFMReviewSessionV1Store store,
            List<LegacyRow> legacyRows
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.store = store;
        this.legacyRows = List.copyOf(legacyRows);
        SFMReviewSessionV1Kernel.evaluateAll(session);
    }

    @Override
    public SessionView refresh() {
        List<DocumentView> documents = new ArrayList<>();
        Set<String> documentIds = new java.util.HashSet<>();
        for (SFMReviewSessionV1.RevisionLane lane : session.revisionLanes()) {
            appendDocuments(documents, documentIds, lane.before().documents(), Side.BEFORE);
            appendDocuments(documents, documentIds, lane.after().documents(), Side.AFTER);
        }

        List<SFMReviewSessionV1Kernel.Evaluation> evaluations = SFMReviewSessionV1Kernel.evaluateAll(session);
        Map<String, SFMReviewSessionV1Kernel.Evaluation> evaluationByComment = new LinkedHashMap<>();
        evaluations.forEach(evaluation -> evaluationByComment.put(evaluation.commentId(), evaluation));
        List<CommentView> comments = session.comments().stream().map(comment -> {
            SFMReviewSessionV1Kernel.Evaluation evaluation = evaluationByComment.get(comment.id());
            Set<String> hashtags = Set.copyOf(SFMReviewSessionV1Kernel.derivedHashtags(comment.text()));
            return new CommentView(comment.id(), comment.text(),
                    comment.provenance().kind() + " · " + comment.provenance().producer(),
                    hashtags.contains("#archived"),
                    evaluation.ranges().stream().map(SFMReviewCommentKernelDataSource::rangeView).toList(),
                    status(evaluation.status()));
        }).toList();
        List<StyleRuleView> styles = session.styleRules().stream().map(style -> new StyleRuleView(
                style.id(), style.requiredHashtags(), style.priority(), colour(style.foreground()),
                colour(style.background()), colour(style.underline()), colour(style.gutterMarker()), style.enabled()
        )).toList();
        List<MigrationView> migrations = evaluations.stream().map(evaluation -> new MigrationView(
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
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>(session.comments());
        comments.add(new SFMReviewSessionV1.Comment(id, text,
                new SFMReviewSessionV1.Provenance("human", "in-game-reviewer", "1", List.of()), rule));
        replaceComments(comments);
        return id;
    }

    @Override
    public void editComment(String id, String text) {
        if (text.isBlank()) throw new IllegalArgumentException("Comment text must not be blank");
        replaceComment(id, comment -> new SFMReviewSessionV1.Comment(
                comment.id(), text, comment.provenance(), comment.selectionRule()));
    }

    @Override
    public void archiveComment(String id) {
        replaceComment(id, comment -> {
            if (SFMReviewSessionV1Kernel.derivedHashtags(comment.text()).contains("#archived")) return comment;
            return new SFMReviewSessionV1.Comment(comment.id(), "#archived " + comment.text(),
                    comment.provenance(), comment.selectionRule());
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
                    channel == StyleChannel.GUTTER ? value : style.gutterMarker(), style.enabled()));
            replaceStyles(styles);
            return;
        }
        throw new IllegalArgumentException("Unknown style rule " + id);
    }

    public SFMReviewSessionV1 session() {
        return session;
    }

    private void replaceComment(String id, java.util.function.UnaryOperator<SFMReviewSessionV1.Comment> update) {
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>(session.comments());
        for (int index = 0; index < comments.size(); index++) {
            if (!comments.get(index).id().equals(id)) continue;
            comments.set(index, update.apply(comments.get(index)));
            replaceComments(comments);
            return;
        }
        throw new IllegalArgumentException("Unknown comment " + id);
    }

    private void replaceComments(List<SFMReviewSessionV1.Comment> comments) {
        session = new SFMReviewSessionV1(session.schema(), session.id(), session.title(), session.coordinateSystem(),
                session.revisionLanes(), comments, session.styleRules(), session.completionPolicy());
        persist();
    }

    private void replaceStyles(List<SFMReviewSessionV1.StyleRule> styles) {
        session = new SFMReviewSessionV1(session.schema(), session.id(), session.title(), session.coordinateSystem(),
                session.revisionLanes(), session.comments(), styles, session.completionPolicy());
        persist();
    }

    private void persist() {
        SFMReviewSessionV1Kernel.evaluateAll(session);
        if (store == null) return;
        try {
            store.save(session);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist review session", exception);
        }
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
        Set<String> ids = session.comments().stream().map(SFMReviewSessionV1.Comment::id)
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

    private static EvaluationStatus status(SFMReviewSessionV1Kernel.Status status) {
        return EvaluationStatus.valueOf(status.name());
    }

    private static Integer colour(String value) {
        if (value == null) return null;
        if (!value.matches("#[0-9A-Fa-f]{8}")) throw new IllegalArgumentException("Invalid ARGB colour " + value);
        return (int) Long.parseLong(value.substring(1), 16);
    }
}
