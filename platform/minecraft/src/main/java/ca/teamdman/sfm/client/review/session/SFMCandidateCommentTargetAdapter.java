package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Checked adapter from candidate-history authority into durable comment-target witnesses. */
public final class SFMCandidateCommentTargetAdapter {
    public record Utf8Range(int startByte, int endByte) {
        public Utf8Range {
            if (startByte < 0 || endByte < startByte) {
                throw new IllegalArgumentException("Candidate comment range must be forward and half-open");
            }
        }
    }

    private SFMCandidateCommentTargetAdapter() {
    }

    public static SFMReviewSessionV2.CandidateTrajectoryTarget capture(
            SFMCandidateHistoryContract.CandidateRouteProjection route,
            SFMCandidateHistoryContract.CandidateFrame frame,
            SFMReviewSessionV2.CandidateTargetKind targetKind,
            Optional<Utf8Range> requestedRange
    ) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(requestedRange, "requestedRange");

        Optional<SFMReviewSessionV2.ProjectedDocumentSelection> selection;
        if (targetKind == SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION) {
            SFMCandidateHistoryContract.CandidateDocument document = frame.document()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Candidate glyph comments require exact materialized document bytes"));
            Utf8Range range = requestedRange.orElseThrow(() -> new IllegalArgumentException(
                    "Candidate glyph comments require an explicit UTF-8 range"));
            byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
            if (range.endByte() > bytes.length
                    || !isBoundary(bytes, range.startByte())
                    || !isBoundary(bytes, range.endByte())) {
                throw new IllegalArgumentException("Candidate comment range is outside the document or splits UTF-8");
            }
            String documentHash = SFMReviewSessionV1Kernel.sha256(bytes);
            String selectedHash = SFMReviewSessionV1Kernel.sha256(
                    Arrays.copyOfRange(bytes, range.startByte(), range.endByte()));
            selection = Optional.of(new SFMReviewSessionV2.ProjectedDocumentSelection(
                    document.documentId(),
                    document.stateHash(),
                    documentHash,
                    range.startByte(),
                    range.endByte(),
                    selectedHash
            ));
        } else {
            if (requestedRange.isPresent()) {
                throw new IllegalArgumentException("Only candidate glyph targets accept a UTF-8 range");
            }
            selection = Optional.empty();
        }
        return SFMReviewSessionV2.CandidateTrajectoryTarget.fromFrame(route, frame, targetKind, selection);
    }

    private static boolean isBoundary(byte[] bytes, int offset) {
        return offset >= 0 && offset <= bytes.length
                && (offset == bytes.length || (bytes[offset] & 0xC0) != 0x80);
    }
}
