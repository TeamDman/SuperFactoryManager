package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentDecoration;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.nio.charset.StandardCharsets;

/** Source-mapped foreground, change-background and comment channels stay independent. */
public final class SFMReleaseReviewSurfacePresentation {
    private SFMReleaseReviewSurfacePresentation() {}

    public record Slice(String revisionId, int sourceStart, int surfaceStart, int length) {}

    public static Map<String, SFMReviewSessionV1.DocumentRevision> sources(
            SFMReleaseReviewV1 review, SFMReleaseReviewSurfaceV1.Surface surface) {
        var required = surface.mappings().stream().flatMap(mapping -> mapping.sourceRanges().stream())
                .map(SFMReleaseReviewSurfaceV1.SourceRange::documentRevisionId).collect(java.util.stream.Collectors.toSet());
        Map<String, SFMReviewSessionV1.DocumentRevision> answer = new LinkedHashMap<>();
        review.reviewSession().revisionLanes().stream().flatMap(lane -> java.util.stream.Stream.concat(
                lane.before().documents().stream(), lane.after().documents().stream()))
                .filter(document -> required.contains(document.id())).forEach(document -> answer.put(document.id(), document));
        return answer;
    }

    /** One authoritative copy per surface byte; context may reference both identical source sides. */
    public static List<Slice> slices(SFMReleaseReviewSurfaceV1.Surface surface,
                                     Map<String, SFMReviewSessionV1.DocumentRevision> sources) {
        byte[] rendered = surface.text().getBytes(StandardCharsets.UTF_8);
        Map<String, byte[]> bytes = new java.util.HashMap<>();
        sources.forEach((id, source) -> bytes.put(id, source.text().getBytes(StandardCharsets.UTF_8)));
        ArrayList<Slice> answer = new ArrayList<>();
        for (var mapping : surface.mappings()) {
            for (var source : mapping.sourceRanges()) {
                var document = sources.get(source.documentRevisionId());
                if (document == null || !source.documentSha256().equals("sha256:" + document.sha256())) continue;
                byte[] original = bytes.get(document.id());
                int length = mapping.surfaceRange().length();
                if (source.range().length() != length || source.range().endByte() > original.length
                        || mapping.surfaceRange().endByte() > rendered.length) continue;
                if (!Arrays.equals(original, source.range().startByte(), source.range().endByte(),
                        rendered, mapping.surfaceRange().startByte(), mapping.surfaceRange().endByte())) continue;
                answer.add(new Slice(document.id(), source.range().startByte(), mapping.surfaceRange().startByte(), length));
                break;
            }
        }
        return List.copyOf(answer);
    }

    public static List<FormattingSpan> projectSyntax(List<Slice> slices, String revisionId, List<FormattingSpan> spans) {
        ArrayList<FormattingSpan> answer = new ArrayList<>();
        for (Slice slice : slices) {
            if (!slice.revisionId().equals(revisionId)) continue;
            for (FormattingSpan span : spans) {
                if (span.endByte() <= slice.sourceStart()) continue;
                if (span.startByte() >= slice.sourceStart() + slice.length()) break;
                int start = Math.max(slice.sourceStart(), span.startByte());
                int end = Math.min(slice.sourceStart() + slice.length(), span.endByte());
                if (start < end) answer.add(new FormattingSpan(slice.surfaceStart() + start - slice.sourceStart(),
                        slice.surfaceStart() + end - slice.sourceStart(), span.formatting()));
            }
        }
        return List.copyOf(answer);
    }

    public static List<SFMTextDocumentDecoration> decorations(SFMReleaseReviewV1 review,
                                                              SFMReleaseReviewSurfaceV1.Surface surface) {
        ArrayList<SFMTextDocumentDecoration> answer = new ArrayList<>();
        for (var mapping : surface.mappings()) {
            Integer colour = switch (mapping.kind()) {
                case ADDITION, STRUCTURAL_AFTER -> 0x704FAF69;
                case DELETION, STRUCTURAL_BEFORE -> 0x70D66B6B;
                default -> null;
            };
            if (colour == null) continue;
            boolean addition = mapping.kind() == SFMReleaseReviewSurfaceV1.MappingKind.ADDITION
                    || mapping.kind() == SFMReleaseReviewSurfaceV1.MappingKind.STRUCTURAL_AFTER;
            answer.add(new SFMTextDocumentDecoration("diff:" + mapping.surfaceRange().startByte(),
                    range(surface.text(), mapping.surfaceRange().startByte(), mapping.surfaceRange().endByte()),
                    OptionalInt.of(colour), OptionalInt.empty(), Optional.empty(),
                    addition ? "Added source" : "Removed source", Optional.empty()));
        }
        // Comments must project through EVERY matching source side, not just the foreground's
        // preferred copy: a shared context row can have independent before/after comments.
        for (var source : sources(review, surface).values()) {
            SFMPath root = new SFMPath(SFMPath.Kind.CONTRIBUTED, "review", "document", List.of(), Optional.empty(), true);
            SFMPath path = new SFMPath(SFMPath.Kind.CONTRIBUTED, "review", "document", List.of(source.id()), Optional.empty(), false);
            SFMTextDocumentSnapshot snapshot = SFMTextDocumentSnapshot.pinned(path, root, source.text(),
                    source.sha256(), Optional.empty(), Optional.empty(), Optional.empty(),
                    SFMTextDocumentLanguage.fromFileName(source.path()));
            for (var decoration : SFMReleaseReviewCommentDecorations.forDocument(review, snapshot)) {
                for (var mapping : surface.mappings()) {
                    for (var mapped : mapping.sourceRanges()) {
                        if (!mapped.documentRevisionId().equals(source.id())) continue;
                        int start = Math.max(mapped.range().startByte(), decoration.range().start().byteOffset());
                        int end = Math.min(mapped.range().endByte(), decoration.range().end().byteOffset());
                        if (start >= end) continue;
                        int offset = mapping.surfaceRange().startByte() - mapped.range().startByte();
                        answer.add(new SFMTextDocumentDecoration(decoration.id() + ":diff:" + (start + offset),
                                range(surface.text(), start + offset, end + offset), OptionalInt.empty(),
                                decoration.underlineArgb(), decoration.gutterMarker(), decoration.narration(),
                                decoration.interactiveObject()));
                    }
                }
            }
        }
        return List.copyOf(answer);
    }

    private static SFMTextDocumentRange range(String text, int start, int end) {
        return new SFMTextDocumentRange(SFMTextDocumentRange.positionAtByteOffset(text, start),
                SFMTextDocumentRange.positionAtByteOffset(text, end));
    }
}
