package ca.teamdman.sfm.client.review.repository;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic comparison-to-comment lowering for the frozen bundle and session contracts. */
final class SFMRepositoryReviewSessionProjector {
    private SFMRepositoryReviewSessionProjector() {}

    static Projection project(SFMRepositoryReviewBundleV1 bundle) {
        String sessionId = "sfm.repository-review-session/1:" + bundle.id();
        List<SFMReviewSessionV1.DocumentRevision> beforeDocuments = documents(sessionId, "before", bundle.before());
        List<SFMReviewSessionV1.DocumentRevision> afterDocuments = documents(sessionId, "after", bundle.after());
        Map<String, SFMReviewSessionV1.DocumentRevision> beforeByPath = byPath(beforeDocuments);
        Map<String, SFMReviewSessionV1.DocumentRevision> afterByPath = byPath(afterDocuments);
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (SFMRepositoryReviewBundleV1.FileChange change : bundle.comparison().fileChanges()) {
            diagnostics.addAll(change.diagnostics().stream().map(value -> changeKey(change) + ": " + value).toList());
            for (SFMRepositoryReviewBundleV1.Operation operation : change.operations()) {
                SFMReviewSessionV1.SelectionRule rule = selectionRule(operation, beforeByPath, afterByPath);
                if (rule == null) {
                    diagnostics.add("Operation " + operation.id() + " has an empty selection and produced no comment");
                    continue;
                }
                String hashtag = switch (operation.kind()) {
                    case INSERT -> "#added";
                    case DELETE -> "#removed";
                    case REPLACE -> "#modified";
                };
                String text = hashtag + " Comparison operation " + operation.id();
                if (!change.diagnostics().isEmpty()) text += ". " + String.join("; ", change.diagnostics());
                List<String> provenanceLinks = new ArrayList<>();
                provenanceLinks.add("comparison-operation:" + operation.id());
                provenanceLinks.add("before-snapshot:" + bundle.before().id());
                provenanceLinks.add("after-snapshot:" + bundle.after().id());
                if (operation.before() != null)
                    provenanceLinks.add("before-selection-sha256:" + operation.before().sha256());
                if (operation.after() != null)
                    provenanceLinks.add("after-selection-sha256:" + operation.after().sha256());
                comments.add(new SFMReviewSessionV1.Comment(
                        "diff:" + operation.id(), text,
                        new SFMReviewSessionV1.Provenance("diff_engine", bundle.producer().id(),
                                bundle.producer().contractVersion(), provenanceLinks), rule));
            }
        }
        SFMReviewSessionV1.RevisionLane lane = new SFMReviewSessionV1.RevisionLane(
                "repository", new SFMReviewSessionV1.Repository(
                        bundle.repository().name(), bundle.repository().displayPath()),
                bundle.before().source().revision() + " → " + bundle.after().source().revision(),
                new SFMReviewSessionV1.Snapshot(bundle.before().id(), beforeDocuments),
                new SFMReviewSessionV1.Snapshot(bundle.after().id(), afterDocuments));
        SFMReviewSessionV1 session = new SFMReviewSessionV1(
                SFMReviewSessionV1.SCHEMA, sessionId, bundle.name(), SFMReviewSessionV1.COORDINATE_SYSTEM,
                List.of(lane), comments, defaultStyles(),
                new SFMReviewSessionV1.CompletionPolicy("changed_surface", "#approved",
                        List.of("#problem", "#needs-change")));
        return new Projection(session, diagnostics);
    }

    private static SFMReviewSessionV1.SelectionRule selectionRule(
            SFMRepositoryReviewBundleV1.Operation operation,
            Map<String, SFMReviewSessionV1.DocumentRevision> before,
            Map<String, SFMReviewSessionV1.DocumentRevision> after
    ) {
        SFMReviewSessionV1.SelectionRule beforeRule = literal(operation.before(), before);
        SFMReviewSessionV1.SelectionRule afterRule = literal(operation.after(), after);
        if (beforeRule == null) return afterRule;
        if (afterRule == null) return beforeRule;
        return new SFMReviewSessionV1.Union(List.of(beforeRule, afterRule));
    }

    private static SFMReviewSessionV1.SelectionRule literal(
            SFMRepositoryReviewBundleV1.Selection selection,
            Map<String, SFMReviewSessionV1.DocumentRevision> documents
    ) {
        if (selection == null || selection.startByte() == selection.endByte()) return null;
        SFMReviewSessionV1.DocumentRevision document = documents.get(selection.path());
        if (document == null) throw new IllegalArgumentException("Selection document was not projected: " + selection.path());
        return new SFMReviewSessionV1.LiteralUtf8Range(document.id(), selection.startByte(), selection.endByte(),
                document.sha256(), selection.sha256());
    }

    private static List<SFMReviewSessionV1.DocumentRevision> documents(
            String sessionId, String side, SFMRepositoryReviewBundleV1.Snapshot snapshot) {
        List<SFMReviewSessionV1.DocumentRevision> result = new ArrayList<>();
        for (SFMRepositoryReviewBundleV1.FileEntry file : snapshot.files()) {
            if (file.encoding() != SFMRepositoryReviewBundleV1.Encoding.UTF8) continue;
            result.add(new SFMReviewSessionV1.DocumentRevision(
                    sessionId + ":" + side + ":" + file.path(), file.path(), "utf-8", file.sha256(), file.text()));
        }
        return result;
    }

    private static Map<String, SFMReviewSessionV1.DocumentRevision> byPath(
            List<SFMReviewSessionV1.DocumentRevision> documents) {
        Map<String, SFMReviewSessionV1.DocumentRevision> result = new LinkedHashMap<>();
        documents.forEach(document -> result.put(document.path(), document));
        return result;
    }

    private static List<SFMReviewSessionV1.StyleRule> defaultStyles() {
        return List.of(
                new SFMReviewSessionV1.StyleRule("diff-added", List.of("#added"), 20,
                        "#FF77DD88", "#33228844", null, "#FF55CC77", true),
                new SFMReviewSessionV1.StyleRule("diff-removed", List.of("#removed"), 20,
                        "#FFFF8888", "#33441111", null, "#FFFF6666", true),
                new SFMReviewSessionV1.StyleRule("diff-modified", List.of("#modified"), 20,
                        "#FF88BBFF", "#33224477", null, "#FF6699EE", true)
        );
    }

    private static String changeKey(SFMRepositoryReviewBundleV1.FileChange change) {
        return change.afterPath() != null ? change.afterPath() : change.beforePath();
    }

    record Projection(SFMReviewSessionV1 session, List<String> diagnostics) {
        Projection { diagnostics = List.copyOf(diagnostics); }
    }
}
