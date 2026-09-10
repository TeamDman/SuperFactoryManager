package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewSurfacePresentationTests {
    @Test
    void sourceHighlightingPendingAndFailuresAreVisibleAndClosedSessionsIgnoreLateResults() throws Exception {
        var review = fixture();
        var source = review.reviewSession().revisionLanes().get(0).after().documents().get(0);
        var pair = new SFMReleaseReviewSurfaceV1.FilePair("status-pair", "1.19.2",
                SFMReleaseReviewV1.ChangeOperation.ADDED, List.of("status-unit"), java.util.Optional.empty(),
                java.util.Optional.of(SFMReleaseReviewSurfaceV1.Source.fromCorpus(source.id(), source.path(), "java", source.text())));
        var surface = SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(
                new SFMReleaseReviewSurfaceV1.Recipe(pair, SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF).request(1, 1));
        for (boolean closeFirst : List.of(false, true)) {
            var future = new java.util.concurrent.CompletableFuture<ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult>();
            var statuses = new java.util.ArrayList<String>();
            var failures = new java.util.ArrayList<Throwable>();
            var session = new SFMReleaseReviewSurfaceSyntaxSession("test:diff", review, surface,
                    (origin, generation, language, text) -> new ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightService.Submission(
                            ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest.create(1, 1, origin, generation, language, text, 1024),
                            future, () -> {}),
                    Runnable::run, ignored -> {}, failures::add, statuses::add);
            assertTrue(statuses.get(statuses.size() - 1).contains("loading"));
            int before = statuses.size();
            if (closeFirst) session.close();
            future.completeExceptionally(new IllegalStateException("worker unavailable"));
            if (closeFirst) {
                assertEquals(before, statuses.size());
                assertTrue(failures.isEmpty());
            } else {
                assertTrue(statuses.get(statuses.size() - 1).contains("unavailable"));
                assertEquals(1, failures.size());
            }
            session.close();
        }
    }

    @Test
    void sourceSyntaxProjectsOnlyToExactMappedBytes() {
        var source = SFMReleaseReviewSurfaceJsonCodecTests.request(1, 1).filePair().after().orElseThrow();
        var surface = SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(SFMReleaseReviewSurfaceJsonCodecTests.request(1, 1));
        var document = new SFMReviewSessionV1.DocumentRevision(source.documentRevisionId(), source.path(), "utf-8", source.sha256().substring(7), source.text());
        var slices = SFMReleaseReviewSurfacePresentation.slices(surface, Map.of(document.id(), document));
        assertEquals(1, slices.size());
        var spans = List.of(new FormattingSpan(0, 5, List.of(ChatFormatting.LIGHT_PURPLE)));
        assertEquals(spans, SFMReleaseReviewSurfacePresentation.projectSyntax(slices, document.id(), spans));
        assertTrue(SFMReleaseReviewSurfacePresentation.projectSyntax(slices, "other-revision", spans).isEmpty());
        var stale = new SFMReviewSessionV1.DocumentRevision(document.id(), source.path(), "utf-8", source.sha256(), "wrong bytes");
        assertTrue(SFMReleaseReviewSurfacePresentation.slices(surface, Map.of(document.id(), stale)).isEmpty());
    }

    @Test
    void syntaxIsClippedAcrossDiffPrefixesAndUnmappedHeaders() {
        var slices = List.of(new SFMReleaseReviewSurfacePresentation.Slice("before", 5, 20, 6),
                new SFMReleaseReviewSurfacePresentation.Slice("after", 5, 40, 6));
        var spans = List.of(new FormattingSpan(3, 8, List.of(ChatFormatting.AQUA)),
                new FormattingSpan(8, 14, List.of(ChatFormatting.GOLD)));
        assertEquals(List.of(new FormattingSpan(20, 23, List.of(ChatFormatting.AQUA)),
                new FormattingSpan(23, 26, List.of(ChatFormatting.GOLD))),
                SFMReleaseReviewSurfacePresentation.projectSyntax(slices, "before", spans));
    }

    @Test
    void changeBackgroundAndCommentUnderlinesAreSeparateAndKeepCommentIdentity() throws Exception {
        var review = fixture();
        var source = review.reviewSession().revisionLanes().get(0).after().documents().get(0);
        var pair = new SFMReleaseReviewSurfaceV1.FilePair("pair-test", "1.19.2",
                SFMReleaseReviewV1.ChangeOperation.ADDED, List.of("unit-test"), java.util.Optional.empty(),
                java.util.Optional.of(SFMReleaseReviewSurfaceV1.Source.fromCorpus(source.id(), source.path(), "java", source.text())));
        var request = new SFMReleaseReviewSurfaceV1.Recipe(pair, SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF).request(1, 1);
        var decorations = SFMReleaseReviewSurfacePresentation.decorations(review,
                SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(request));
        assertTrue(decorations.stream().anyMatch(value -> value.narration().equals("Added source")
                && value.backgroundArgb().isPresent() && value.underlineArgb().isEmpty()));
        var comment = decorations.stream().filter(value -> value.interactiveObject().isPresent()
                && value.interactiveObject().orElseThrow().id().equals("human:approved-value")).findFirst().orElseThrow();
        assertEquals(35, comment.range().start().byteOffset());
        assertEquals(60, comment.range().end().byteOffset());
        assertTrue(comment.backgroundArgb().isEmpty(), "Approval must not turn a removed line's background green");
        assertTrue(comment.underlineArgb().isPresent());
        assertTrue(comment.gutterMarker().isPresent());
    }

    private static SFMReleaseReviewV1 fixture() throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null && !Files.exists(cursor.resolve("docs/architecture/fixtures/release-review-v1.json"))) cursor = cursor.getParent();
        return SFMReleaseReviewV1Codec.parse(Files.readString(cursor.resolve("docs/architecture/fixtures/release-review-v1.json")));
    }
}
