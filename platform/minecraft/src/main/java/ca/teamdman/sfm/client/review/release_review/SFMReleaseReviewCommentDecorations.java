package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentDecoration;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Projects exact committed review comments onto one immutable source preview. */
public final class SFMReleaseReviewCommentDecorations {
    private SFMReleaseReviewCommentDecorations() {
    }

    public static List<SFMTextDocumentDecoration> forDocument(
            SFMReleaseReviewV1 review,
            SFMTextDocumentSnapshot document
    ) {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(document, "document");
        if (!document.ready() || document.path().isEmpty()) return List.of();
        var path = document.path().orElseThrow();
        if (!path.scheme().equals("review") || !path.authority().equals("document")
                || path.segments().isEmpty()) return List.of();
        String revisionId = path.segments().get(0);
        Optional<SFMReviewSessionV1.DocumentRevision> revision = review.reviewSession().revisionLanes().stream()
                .flatMap(lane -> java.util.stream.Stream.concat(
                        lane.before().documents().stream(), lane.after().documents().stream()))
                .filter(candidate -> candidate.id().equals(revisionId))
                .findFirst();
        if (revision.isEmpty()) return List.of();
        String expectedHash = SFMReviewSessionV1Kernel.sha256(
                revision.orElseThrow().text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (document.sha256().filter(expectedHash::equals).isEmpty()) return List.of();

        ArrayList<SFMTextDocumentDecoration> answer = new ArrayList<>();
        for (SFMReviewSessionV2.Comment comment : review.reviewSession().comments()) {
            if (SFMReleaseReviewGeneratedMarkers.isChangeMarker(comment)) continue;
            if (!(comment.target() instanceof SFMReviewSessionV2.CommittedReviewTarget committed)
                    || !referencesDocument(committed.selectionRule(), revisionId)) continue;
            SFMReviewSessionV2Kernel.Evaluation evaluation =
                    SFMReviewSessionV2Kernel.evaluateComment(review.reviewSession(), comment);
            if (evaluation.status() != SFMReviewSessionV2Kernel.Status.RESOLVED_EXACTLY
                    && evaluation.status() != SFMReviewSessionV2Kernel.Status.RESOLVED_WITH_RELOCATION) continue;
            VisualStyle style = visualStyle(review.reviewSession().styleRules(), comment.text());
            int rangeIndex = 0;
            for (SFMReviewSessionV1Kernel.Range range : evaluation.ranges()) {
                if (!range.documentRevisionId().equals(revisionId) || range.startByte() == range.endByte()) continue;
                SFMTextDocumentRange projected = new SFMTextDocumentRange(
                        SFMTextDocumentRange.positionAtByteOffset(document.text(), range.startByte()),
                        SFMTextDocumentRange.positionAtByteOffset(document.text(), range.endByte())
                );
                answer.add(new SFMTextDocumentDecoration(
                        comment.id() + ":" + rangeIndex++,
                        projected,
                        optional(style.background()),
                        optional(style.underline()),
                        Optional.ofNullable(style.gutterMarker()),
                        comment.id() + " · " + comment.text(),
                        Optional.of(new SFMTextDocumentDecoration.InteractiveObject(
                                "sfm:review/comment",
                                comment.id(),
                                preview(comment.text(), 112),
                                List.of(
                                        "Comment " + comment.id(),
                                        "The gutter marker is a style; the comment value is shown above",
                                        "Click the gutter marker to inspect value, selector, matches, and provenance"
                                )
                        ))
                ));
            }
        }
        answer.sort(Comparator.comparing(decoration -> decoration.range().start().byteOffset()));
        return List.copyOf(answer);
    }

    private static boolean referencesDocument(SFMReviewSessionV1.SelectionRule rule, String revisionId) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            return literal.documentRevisionId().equals(revisionId);
        }
        if (rule instanceof SFMReviewSessionV1.Union union) {
            return union.rules().stream().anyMatch(child -> referencesDocument(child, revisionId));
        }
        if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            return intersection.rules().stream().anyMatch(child -> referencesDocument(child, revisionId));
        }
        SFMReviewSessionV1.Difference difference = (SFMReviewSessionV1.Difference) rule;
        return referencesDocument(difference.include(), revisionId)
                || difference.exclude().stream().anyMatch(child -> referencesDocument(child, revisionId));
    }

    private static VisualStyle visualStyle(List<SFMReviewSessionV1.StyleRule> rules, String commentText) {
        Set<String> hashtags = Set.copyOf(SFMReviewSessionV1Kernel.derivedHashtags(commentText));
        Integer background;
        Integer underline;
        String gutter;
        if (hashtags.contains("#needs-change") || hashtags.contains("#problem")) {
            background = 0x33441111;
            underline = 0xFFFF5555;
            gutter = "!";
        } else if (hashtags.contains("#approved")) {
            background = 0x3322AA44;
            underline = 0xFF55FF88;
            gutter = "✓";
        } else {
            background = 0x333B82F6;
            underline = 0xFF60A5FA;
            gutter = "•";
        }
        for (SFMReviewSessionV1.StyleRule rule : rules.stream()
                .filter(SFMReviewSessionV1.StyleRule::enabled)
                .filter(rule -> hashtags.containsAll(rule.requiredHashtags()))
                .sorted(Comparator.comparingInt(SFMReviewSessionV1.StyleRule::priority))
                .toList()) {
            Integer parsedBackground = colour(rule.background());
            Integer parsedUnderline = colour(rule.underline());
            if (parsedBackground != null) background = parsedBackground;
            if (parsedUnderline != null) underline = parsedUnderline;
            if (rule.gutterMarker() != null) gutter = rule.gutterMarker();
        }
        return new VisualStyle(background, underline, gutter);
    }

    private static Integer colour(String value) {
        if (value == null || !value.matches("#[0-9A-Fa-f]{8}")) return null;
        return (int) Long.parseLong(value.substring(1), 16);
    }

    private static OptionalInt optional(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static String preview(String value, int maximumCodePoints) {
        String normalized = value.replaceAll("\\s+", " ").strip();
        if (normalized.isEmpty()) return "(empty comment)";
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= maximumCodePoints) return normalized;
        return normalized.substring(0, normalized.offsetByCodePoints(0, maximumCodePoints)).stripTrailing() + "…";
    }

    private record VisualStyle(Integer background, Integer underline, String gutterMarker) {
    }
}
