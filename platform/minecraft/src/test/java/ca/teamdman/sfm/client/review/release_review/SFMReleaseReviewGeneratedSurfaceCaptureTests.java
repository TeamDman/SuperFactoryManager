package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewGeneratedSurfaceCaptureTests {
    @Test
    void selectedGeneratedBytesProjectBackToPinnedCorpusBeforeCommentProposal() throws Exception {
        SFMReleaseReviewV1 review = fixture();
        SFMReleaseReviewCorpus.DocumentView corpus = review.corpusDocuments().stream()
                .filter(value -> value.materialization() == SFMReleaseReviewV1.Materialization.COMPLETE)
                .map(value -> SFMReleaseReviewCorpus.from(review).documentRevision(value.documentRevisionId()))
                .flatMap(Optional::stream)
                .filter(value -> value.materializedDocument().map(document -> !document.text().isEmpty())
                        .orElse(false))
                .findFirst().orElseThrow();
        String text = corpus.materializedDocument().orElseThrow().text();
        var source = SFMReleaseReviewSurfaceV1.Source.fromCorpus(
                corpus.binding().documentRevisionId(),
                corpus.binding().path(),
                extensionLanguage(corpus.binding().path()),
                text
        );
        boolean after = corpus.binding().snapshotSide() == SFMReleaseReviewV1.SnapshotSide.AFTER;
        var pair = new SFMReleaseReviewSurfaceV1.FilePair(
                "surface-capture-fixture",
                corpus.binding().laneId(),
                after ? SFMReleaseReviewV1.ChangeOperation.ADDED : SFMReleaseReviewV1.ChangeOperation.DELETED,
                List.of("surface-capture-unit"),
                after ? Optional.empty() : Optional.of(source),
                after ? Optional.of(source) : Optional.empty()
        );
        var recipe = new SFMReleaseReviewSurfaceV1.Recipe(
                pair, SFMReleaseReviewSurfaceV1.SurfaceKind.TEXT_DIFF);
        SFMReleaseReviewSurfaceRuntime surfaceRuntime = runtime();
        try {
            var generated = surfaceRuntime.generate(
                    recipe, 1, new SFMExplorerCancellationToken()).join();
            var snapshot = generated.snapshot();
            int selectedBytes = Math.min(12, snapshot.text().getBytes(StandardCharsets.UTF_8).length);
            var start = SFMTextDocumentRange.positionAtByteOffset(snapshot.text(), 0);
            var end = SFMTextDocumentRange.positionAtByteOffset(snapshot.text(), selectedBytes);
            var selection = new SFMTextDocumentSelection("primary", start, end, true);
            var projection = SFMContextDocumentProjection.capture(
                    "sfm:text_editor_v3",
                    snapshot,
                    snapshot.text(),
                    false,
                    true,
                    List.of(),
                    List.of(new SFMContextSelectionProjection(
                            "primary", List.of(selection.orderedRange()), true, List.of(selection)))
            );

            var capture = SFMReleaseReviewEditorCapture.captureGeneratedSurface(
                    new SFMClientActionContext(null, () -> true, null),
                    projection,
                    review,
                    snapshot.path().orElseThrow(),
                    surfaceRuntime
            );

            assertFalse(capture.adapted().pinnedSelection().ranges().isEmpty());
            assertTrue(capture.adapted().pinnedSelection().ranges().stream()
                    .allMatch(range -> range.documentRevisionId()
                            .equals(corpus.binding().documentRevisionId())));
            assertEquals(corpus.binding().sha256(),
                    capture.adapted().pinnedSelection().ranges().get(0).documentSha256());
            assertTrue(capture.proposals().proposals().stream()
                    .anyMatch(proposal -> proposal.kind() == SFMReleaseReviewV1.SelectorKind.LITERAL));
            assertTrue(capture.diagnostics().stream()
                    .anyMatch(value -> value.contains("pinned source range")));
        } finally {
            surfaceRuntime.close();
        }
    }

    private static SFMReleaseReviewSurfaceRuntime runtime() {
        return new SFMReleaseReviewSurfaceRuntime(
                new SFMReleaseReviewSurfaceRuntime.Configuration(
                        "fixture-worker", Duration.ofSeconds(2), Duration.ofMillis(100),
                        16 * 1024 * 1024, 64 * 1024),
                Executors.newSingleThreadExecutor(action -> {
                    Thread thread = new Thread(action, "sfm-review-surface-capture-test");
                    thread.setDaemon(true);
                    return thread;
                }),
                (configuration, json, cancellation) -> {
                    var request = SFMReleaseReviewSurfaceJsonCodec.decodeRequest(json);
                    return new SFMReleaseReviewSurfaceRuntime.ProcessResult(
                            0,
                            SFMReleaseReviewSurfaceJsonCodec.encodeSurface(
                                    SFMReleaseReviewSurfaceJsonCodecTests.afterSurface(request)),
                            ""
                    );
                },
                true
        );
    }

    private static String extensionLanguage(String path) {
        return path.endsWith(".java") ? "java" : "text";
    }

    private static SFMReleaseReviewV1 fixture() throws Exception {
        return SFMReleaseReviewV1Codec.parse(
                Files.readString(fixturePath(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
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
