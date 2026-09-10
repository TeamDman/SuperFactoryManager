package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel.Range;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Revision-qualified interval evidence; work-unit membership is not surface coverage. */
public final class SFMReleaseReviewCoverage {
    public record UnitCoverage(String reviewUnitId, boolean bounded,
                               List<Range> required, List<Range> approved, List<Range> remaining) {
        public UnitCoverage {
            required = List.copyOf(required);
            approved = List.copyOf(approved);
            remaining = List.copyOf(remaining);
        }

        public boolean fullyApproved() { return bounded && !required.isEmpty() && remaining.isEmpty(); }
    }

    private SFMReleaseReviewCoverage() {}

    static List<UnitCoverage> evaluate(SFMReleaseReviewV1 document, List<Range> approvals, List<Range> blockers) {
        Map<String, SFMReviewSessionV1.DocumentRevision> sources = new HashMap<>();
        document.reviewSession().revisionLanes().forEach(lane -> {
            lane.before().documents().forEach(source -> sources.put(source.id(), source));
            lane.after().documents().forEach(source -> sources.put(source.id(), source));
        });
        Map<String, SFMReleaseReviewV1.CorpusDocument> corpus = new HashMap<>();
        document.corpusDocuments().forEach(source -> corpus.put(source.documentRevisionId(), source));
        Map<String, List<Range>> approvedByRevision = new HashMap<>();
        SFMReviewSessionV1Kernel.difference(approvals, blockers).forEach(range ->
                approvedByRevision.computeIfAbsent(range.documentRevisionId(), ignored -> new ArrayList<>()).add(range));
        List<UnitCoverage> result = new ArrayList<>();
        for (var unit : document.reviewUnits()) {
            List<Range> required = new ArrayList<>();
            boolean before = addSide(unit.beforeDocumentRevisionId(), unit.beforeRanges(), sources, corpus, required);
            boolean after = addSide(unit.afterDocumentRevisionId(), unit.afterRanges(), sources, corpus, required);
            required = SFMReviewSessionV1Kernel.normalize(required);
            List<Range> applicable = new ArrayList<>();
            required.stream().map(Range::documentRevisionId).distinct().forEach(id ->
                    applicable.addAll(approvedByRevision.getOrDefault(id, List.of())));
            result.add(new UnitCoverage(unit.id(), before && after && !required.isEmpty(), required,
                    SFMReviewSessionV1Kernel.intersection(required, applicable),
                    SFMReviewSessionV1Kernel.difference(required, applicable)));
        }
        result.sort(Comparator.comparing(UnitCoverage::reviewUnitId));
        return List.copyOf(result);
    }

    private static boolean addSide(Optional<String> revision, List<SFMReleaseReviewV1.Utf8Range> ranges,
                                   Map<String, SFMReviewSessionV1.DocumentRevision> sources,
                                   Map<String, SFMReleaseReviewV1.CorpusDocument> corpus, List<Range> result) {
        if (revision.isEmpty()) return ranges.isEmpty();
        String id = revision.orElseThrow();
        var source = sources.get(id);
        var binding = corpus.get(id);
        if (source == null || binding == null || binding.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE)
            return false;
        byte[] bytes = source.text().getBytes(StandardCharsets.UTF_8);
        // No declared range is a whole-file fallback, never "any comment in this file".
        if (ranges.isEmpty()) {
            if (bytes.length == 0) return false; // An empty/binary operation needs explicit operation evidence.
            result.add(new Range(id, 0, bytes.length));
            return true;
        }
        boolean valid = true;
        for (var range : ranges) {
            if (range.startByte() < 0 || range.endByte() < range.startByte() || range.endByte() > bytes.length
                    || !boundary(bytes, range.startByte()) || !boundary(bytes, range.endByte())) {
                valid = false;
                continue;
            }
            if (range.startByte() < range.endByte()) result.add(new Range(id, range.startByte(), range.endByte()));
        }
        return valid;
    }

    private static boolean boundary(byte[] bytes, int offset) {
        return offset == bytes.length || (bytes[offset] & 0xc0) != 0x80;
    }
}
