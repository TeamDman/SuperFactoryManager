package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable borrowed view from release-corpus addresses to embedded snapshot bytes. */
public final class SFMReleaseReviewCorpus {
    public record DocumentView(
            SFMReleaseReviewV1.CorpusDocument binding,
            Optional<SFMReviewSessionV1.DocumentRevision> materializedDocument
    ) {
        public DocumentView {
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(materializedDocument, "materializedDocument");
        }

        public Optional<byte[]> utf8Bytes() {
            return materializedDocument.map(value -> value.text().getBytes(StandardCharsets.UTF_8));
        }
    }

    private final Map<String, DocumentView> byCorpusId;
    private final Map<String, DocumentView> byRevisionId;

    private SFMReleaseReviewCorpus(Map<String, DocumentView> byCorpusId,
                                   Map<String, DocumentView> byRevisionId) {
        this.byCorpusId = Map.copyOf(byCorpusId);
        this.byRevisionId = Map.copyOf(byRevisionId);
    }

    public static SFMReleaseReviewCorpus from(SFMReleaseReviewV1 review) {
        SFMReleaseReviewKernel.validate(review);
        Map<String, SFMReviewSessionV1.DocumentRevision> embedded = new LinkedHashMap<>();
        review.reviewSession().revisionLanes().forEach(lane -> {
            lane.before().documents().forEach(value -> embedded.put(value.id(), value));
            lane.after().documents().forEach(value -> embedded.put(value.id(), value));
        });
        Map<String, DocumentView> byCorpus = new LinkedHashMap<>();
        Map<String, DocumentView> byRevision = new LinkedHashMap<>();
        for (SFMReleaseReviewV1.CorpusDocument binding : review.corpusDocuments()) {
            DocumentView view = new DocumentView(binding, Optional.ofNullable(embedded.get(binding.documentRevisionId())));
            byCorpus.put(binding.id(), view);
            byRevision.put(binding.documentRevisionId(), view);
        }
        return new SFMReleaseReviewCorpus(byCorpus, byRevision);
    }

    public Optional<DocumentView> corpusDocument(String id) {
        return Optional.ofNullable(byCorpusId.get(id));
    }

    public Optional<DocumentView> documentRevision(String id) {
        return Optional.ofNullable(byRevisionId.get(id));
    }

    public List<DocumentView> documents() {
        return byCorpusId.values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.binding().id()))
                .toList();
    }
}
