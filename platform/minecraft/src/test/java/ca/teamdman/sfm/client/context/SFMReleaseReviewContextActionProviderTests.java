package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSelectionAdapter;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSelectorProposalAdapter;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewContextActionProviderTests {
    @Test
    void existingCommentLookupUsesExactRevisionAndHalfOpenSelectionOrCaret() {
        var ranges = List.of(new SFMReviewSessionV1Kernel.Range("after", 3, 8));
        assertTrue(SFMReleaseReviewContextActionProvider.overlaps(selection("after", 4, 6), ranges));
        assertTrue(SFMReleaseReviewContextActionProvider.overlaps(selection("after", 3, 3), ranges));
        assertEquals(false, SFMReleaseReviewContextActionProvider.overlaps(selection("after", 8, 8), ranges));
        assertEquals(false, SFMReleaseReviewContextActionProvider.overlaps(selection("before", 4, 6), ranges));
        assertEquals(false, SFMReleaseReviewContextActionProvider.overlaps(selection("after", 0, 3), ranges));
        assertEquals(false, SFMReleaseReviewContextActionProvider.overlaps(selection("after", 0, 10),
                List.of(new SFMReviewSessionV1Kernel.Range("after", 5, 5))));
    }

    private static SFMReleaseReviewV1.PinnedSelection selection(String revision, int start, int end) {
        return new SFMReleaseReviewV1.PinnedSelection("test", "test", 0, List.of(
                new SFMReleaseReviewV1.PinnedSelectionRange(SFMReleaseReviewV1.SelectionDirection.FORWARD,
                        revision, "a".repeat(64), start, end)));
    }

    @Test
    void semanticDestinationIsEvidenceAfterTheActualLocalCommentTarget() {
        String text = "@Override\nvoid run() {}\n";
        String revision = "selection-local-override";
        var document = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "after-local",
                revision,
                "review://document/after-local/src/main/java/example/Local.java",
                text
        );
        var selection = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                revision,
                document.sourceAddress(),
                List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                        document,
                        List.of(new SFMTextDocumentSelection(
                                "primary",
                                SFMTextDocumentRange.positionAtByteOffset(text, 0),
                                SFMTextDocumentRange.positionAtByteOffset(text, 9),
                                true
                        ))
                ))
        );
        var adapted = SFMReleaseReviewSelectionAdapter.adapt(selection);
        var documents = SFMReleaseReviewSelectionAdapter.resolver(selection);
        String fingerprint = SFMReviewSessionV1Kernel.sha256(
                "local-override-projection".getBytes(StandardCharsets.UTF_8));
        var provider = new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider() {
            @Override
            public String id() {
                return "test:java-definition";
            }

            @Override
            public List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence> propose(
                    SFMReleaseReviewV1.PinnedSelection ignored,
                    SFMReleaseReviewSelectionAdapter.DocumentResolver ignoredDocuments
            ) {
                return List.of(new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence(
                        SFMReleaseReviewV1.SelectorKind.SYMBOL,
                        "jdk-source://java.base/java/lang/Override.java",
                        List.of(new SFMReleaseReviewV1.AddressedRange("after-local", 0, 9)),
                        List.of(new SFMReleaseReviewV1.Evidence(
                                "definition-destination",
                                "jdk-source://java.base/java/lang/Override.java"
                        )),
                        SFMReleaseReviewV1.ProposalConfidence.EXACT,
                        fingerprint,
                        "snapshot-local-override",
                        List.of()
                ));
            }
        };
        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                adapted,
                documents,
                List.of(provider)
        );

        assertEquals(
                "target Local.java [0..9) · exact selected bytes",
                SFMReleaseReviewContextActionProvider.label(documents, batch.proposals().get(0))
        );
        String semantic = SFMReleaseReviewContextActionProvider.label(
                documents,
                batch.proposals().stream()
                        .filter(value -> value.kind() == SFMReleaseReviewV1.SelectorKind.SYMBOL)
                        .findFirst()
                        .orElseThrow()
        );
        assertTrue(semantic.startsWith("target Local.java [0..9) · symbol evidence → "));
        assertTrue(semantic.endsWith("jdk-source://java.base/java/lang/Override.java"));
    }
}
