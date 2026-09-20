package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewCommentDecorationsTests {
    @Test
    void exactCommittedCommentBecomesAnIndependentSourceDecoration() throws Exception {
        SFMReleaseReviewV1 review = SFMReleaseReviewV1Codec.parse(Files.readString(fixturePath()));
        SFMReleaseReviewV1.CommentSelectorBinding binding = review.selectorBindings().get(0);
        SFMReleaseReviewV1.PinnedSelectionRange selected = binding.capturedSelection().ranges().get(0);
        SFMReleaseReviewCorpus.DocumentView corpus = SFMReleaseReviewCorpus.from(review)
                .documentRevision(selected.documentRevisionId()).orElseThrow();
        String text = corpus.materializedDocument().orElseThrow().text();
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                List.of(selected.documentRevisionId()),
                Optional.empty(),
                true
        );
        ArrayList<String> segments = new ArrayList<>(root.segments());
        for (String segment : corpus.binding().path().split("/")) {
            if (!segment.isEmpty()) segments.add(segment);
        }
        SFMPath path = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                segments,
                Optional.empty(),
                false
        );
        SFMTextDocumentSnapshot snapshot = SFMTextDocumentSnapshot.pinned(
                path,
                root,
                text,
                corpus.binding().sha256(),
                Optional.empty(),
                Optional.empty()
        );

        var decorations = SFMReleaseReviewCommentDecorations.forDocument(review, snapshot);

        assertFalse(decorations.isEmpty());
        assertTrue(decorations.stream().anyMatch(decoration ->
                decoration.id().startsWith(binding.commentId() + ":")
                        && decoration.backgroundArgb().isPresent()
                        && decoration.underlineArgb().isPresent()
                        && decoration.interactiveObject().isPresent()
                        && decoration.interactiveObject().orElseThrow().kind().equals("sfm:review/comment")
                        && decoration.interactiveObject().orElseThrow().id().equals(binding.commentId())));
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical release-review fixture");
    }
}
