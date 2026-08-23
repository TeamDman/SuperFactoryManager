package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionId;
import ca.teamdman.sfm.client.explorer.SFMSelectionPathResolution;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Codec;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewSelectionAdapterTests {
    @Test
    void capturesUnicodeBackwardSelectionAndReverseProjectsAReadOnlyView() {
        String text = "A🦀B\r\nC";
        String revision = "selection-revision-7";
        var document = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "document-after-a",
                revision,
                "file:///workspace/A.java",
                text
        );
        var backward = new SFMTextDocumentSelection(
                "cursor-primary",
                position(text, 5),
                position(text, 1),
                true
        );
        var capture = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                revision,
                "selection://review-target@revision-7",
                List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                        document,
                        List.of(backward)
                ))
        );

        var adapted = SFMReleaseReviewSelectionAdapter.adapt(capture);
        var pinned = adapted.pinnedSelection();
        assertEquals(SFMReleaseReviewV1.SelectionDirection.BACKWARD, pinned.ranges().get(0).direction());
        assertEquals(1, pinned.ranges().get(0).startByte());
        assertEquals(5, pinned.ranges().get(0).endByte());
        var literal = assertInstanceOf(SFMReviewSessionV1.LiteralUtf8Range.class, adapted.literalRule());
        assertEquals(SFMReviewSessionV1Kernel.sha256("🦀".getBytes(StandardCharsets.UTF_8)),
                literal.selectedTextSha256());

        var view = SFMReleaseReviewSelectionAdapter.reverseProject(
                pinned,
                SFMReleaseReviewSelectionAdapter.resolver(List.of(document))
        );
        assertTrue(view.readOnly());
        assertEquals(5, view.ranges().get(0).selection().anchor().byteOffset());
        assertEquals(1, view.ranges().get(0).selection().active().byteOffset());
        assertTrue(view.ranges().get(0).selection().primary());
    }

    @Test
    void preservesDisjointMultiDocumentOrderAndPrimaryRangeInAUnion() {
        String revision = "selection-revision-11";
        var first = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "doc-a", revision, "file:///workspace/A.java", "alpha"
        );
        var second = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "doc-b", revision, "file:///workspace/B.java", "βeta"
        );
        var capture = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                revision,
                "selection://multi@revision-11",
                List.of(
                        new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                                first,
                                List.of(selection("a", first.text(), 0, 1, false))
                        ),
                        new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                                second,
                                List.of(
                                        selection("b-primary", second.text(), 0, 2, true),
                                        selection("b-tail", second.text(), 2, 3, false)
                                )
                        )
                )
        );

        var adapted = SFMReleaseReviewSelectionAdapter.adapt(capture);
        assertEquals(List.of("doc-a", "doc-b", "doc-b"), adapted.pinnedSelection().ranges().stream()
                .map(SFMReleaseReviewV1.PinnedSelectionRange::documentRevisionId).toList());
        assertEquals(1, adapted.pinnedSelection().primaryRangeIndex());
        var union = assertInstanceOf(SFMReviewSessionV1.Union.class, adapted.literalRule());
        assertEquals(3, union.rules().size());
        assertEquals("doc-a", assertInstanceOf(
                SFMReviewSessionV1.LiteralUtf8Range.class, union.rules().get(0)).documentRevisionId());
        assertEquals("doc-b", assertInstanceOf(
                SFMReviewSessionV1.LiteralUtf8Range.class, union.rules().get(1)).documentRevisionId());
    }

    @Test
    void rejectsStaleHashesRevisionsAndNonUtf8Boundaries() {
        String text = "A🦀B";
        String validHash = SFMReviewSessionV1Kernel.sha256(text.getBytes(StandardCharsets.UTF_8));
        String staleHash = SFMReviewSessionV1Kernel.sha256("different".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () ->
                new SFMReleaseReviewSelectionAdapter.DocumentWitness(
                        "doc", "rev", "file:///A.java", text, staleHash));

        var oldRevision = new SFMReleaseReviewSelectionAdapter.DocumentWitness(
                "doc", "old-revision", "file:///A.java", text, validHash);
        assertThrows(IllegalArgumentException.class, () ->
                new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                        "new-revision",
                        "selection://x@revision-2",
                        List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                                oldRevision,
                                List.of(selection("cursor", text, 1, 5, true))
                        ))
                ));

        assertThrows(IllegalArgumentException.class, () ->
                new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                        oldRevision,
                        List.of(new SFMTextDocumentSelection(
                                "split-scalar",
                                new SFMTextDocumentPosition(0, 1, 2),
                                position(text, 5),
                                true
                        ))
                ));

        var original = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                "old-revision",
                "file:///A.java",
                List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                        oldRevision,
                        List.of(selection("cursor", text, 1, 5, true))
                ))
        );
        var pinned = SFMReleaseReviewSelectionAdapter.adapt(original).pinnedSelection();
        var changed = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "doc", "old-revision", "file:///A.java", "A🦀changed"
        );
        assertThrows(IllegalArgumentException.class, () ->
                SFMReleaseReviewSelectionAdapter.reverseProject(
                        pinned,
                        SFMReleaseReviewSelectionAdapter.resolver(List.of(changed))
                ));
    }

    @Test
    void pinsALiveNamedSelectionBeforeCallingTheTypedRangeProjector() {
        SFMPath livePath = SFMPath.parse("selection://selection-3");
        SFMPath member = SFMPath.parse("file:///D:/workspace/A.java");
        var resolution = new SFMSelectionPathResolution(
                livePath,
                41,
                SFMSelectionPathResolution.Completeness.COMPLETE,
                Optional.of(new SFMSelectionId("selection-3")),
                Optional.of(9L),
                false,
                Set.of(member),
                List.of()
        );

        var capture = SFMReleaseReviewSelectionAdapter.capture(resolution, context -> {
            assertEquals("selection-3@revision-9", context.selectionRevision());
            assertEquals("selection://selection-3@revision-9", context.sourceExpression());
            assertEquals(List.of(member), context.memberPaths());
            var document = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                    "doc-a", context.selectionRevision(), member.canonical(), "hello"
            );
            return List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                    document,
                    List.of(selection("primary", document.text(), 0, 5, true))
            ));
        });

        assertEquals("selection://selection-3@revision-9", capture.sourceExpression());
        assertFalse(capture.sourceExpression().equals(livePath.canonical()));
        assertEquals("selection-3@revision-9",
                SFMReleaseReviewSelectionAdapter.adapt(capture).pinnedSelection().selectionRevision());
    }

    @Test
    void capturesWhitespaceOnlySelectionWithoutDroppingOrNormalizingBytes() {
        String text = "class A {\n\t  \n}\n";
        String revision = "selection-whitespace-1";
        var document = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "doc-whitespace", revision, "file:///workspace/Whitespace.java", text);
        var capture = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                revision,
                "selection://whitespace@revision-1",
                List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                        document,
                        List.of(selection("whitespace-primary", text, 10, 13, true))
                ))
        );

        var adapted = SFMReleaseReviewSelectionAdapter.adapt(capture);
        assertEquals(10, adapted.pinnedSelection().ranges().get(0).startByte());
        assertEquals(13, adapted.pinnedSelection().ranges().get(0).endByte());
        var literal = assertInstanceOf(SFMReviewSessionV1.LiteralUtf8Range.class, adapted.literalRule());
        assertEquals(SFMReviewSessionV1Kernel.sha256("\t  ".getBytes(StandardCharsets.UTF_8)),
                literal.selectedTextSha256());

        var reopened = SFMReleaseReviewSelectionAdapter.reverseProject(
                adapted.pinnedSelection(), SFMReleaseReviewSelectionAdapter.resolver(List.of(document)));
        assertEquals(10, reopened.ranges().get(0).selection().orderedRange().start().byteOffset());
        assertEquals(13, reopened.ranges().get(0).selection().orderedRange().end().byteOffset());
    }

    @Test
    void adaptedSelectionSurvivesFrozenV1ToV2MigrationWithoutDerivingApproval() {
        String text = "Café\n";
        String revision = "selection-migration-1";
        var document = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "doc-migration", revision, "file:///workspace/Cafe.java", text);
        var adapted = SFMReleaseReviewSelectionAdapter.adapt(
                new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                        revision,
                        "selection://migration@revision-1",
                        List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                                document,
                                List.of(selection("migration-primary", text, 0, 5, true))
                        ))
                ));
        var sourceDocument = new SFMReviewSessionV1.DocumentRevision(
                document.documentRevisionId(),
                "src/Cafe.java",
                "utf-8",
                document.sha256(),
                text
        );
        var v1 = new SFMReviewSessionV1(
                SFMReviewSessionV1.SCHEMA,
                "sfm:fixture/selection-migration",
                "Selection migration fixture",
                SFMReviewSessionV1.COORDINATE_SYSTEM,
                List.of(new SFMReviewSessionV1.RevisionLane(
                        "1.19.2",
                        new SFMReviewSessionV1.Repository("sfm", "."),
                        "1.19.2",
                        new SFMReviewSessionV1.Snapshot("before", List.of()),
                        new SFMReviewSessionV1.Snapshot("after", List.of(sourceDocument))
                )),
                List.of(new SFMReviewSessionV1.Comment(
                        "human:selection-note",
                        "Selection note without an approval hashtag.",
                        new SFMReviewSessionV1.Provenance("human", "fixture-reviewer", "1", List.of()),
                        adapted.literalRule()
                )),
                List.of(),
                new SFMReviewSessionV1.CompletionPolicy(
                        "changed_surface", "#approved", List.of("#problem", "#needs-change"))
        );

        String v1Bytes = SFMReviewSessionV1Codec.write(v1);
        SFMReviewSessionV2 migrated = SFMReviewSessionV2Codec.parseOrMigrate(v1Bytes);
        SFMReviewSessionV2.Comment migratedComment = migrated.comments().get(0);
        var migratedTarget = assertInstanceOf(
                SFMReviewSessionV2.CommittedReviewTarget.class, migratedComment.target());
        assertEquals(adapted.literalRule(), migratedTarget.selectionRule());
        assertTrue(migratedTarget.candidatePromotion().isEmpty());
        assertTrue(SFMReviewSessionV1Kernel.derivedHashtags(migratedComment.text()).isEmpty(),
                "The adapter and migration must not manufacture approval from selection evidence");
        assertFalse(SFMReviewSessionV1Kernel.isApprovalEffective(
                v1, v1.comments().get(0), SFMReviewSessionV1Kernel.evaluateComment(v1, v1.comments().get(0))));

        String v2Bytes = SFMReviewSessionV2Codec.write(migrated);
        assertEquals(migrated, SFMReviewSessionV2Codec.parse(v2Bytes));
    }

    @Test
    void canonicalFixtureAdapterPinsOrderedUnicodeRangesAndRejectsLiveStaleOrChangedWitnesses()
            throws Exception {
        SFMReleaseReviewV1 review = SFMReleaseReviewV1Codec.parse(
                Files.readString(fixturePath()).replace("\r\n", "\n"));
        SFMReleaseReviewV1.CommentSelectorBinding binding = review.selectorBindings().stream()
                .filter(value -> value.commentId().equals("human:unicode-multidoc-note"))
                .findFirst().orElseThrow();
        SFMReleaseReviewV1.PinnedSelection pinned = binding.capturedSelection();
        SFMReleaseReviewCorpus corpus = SFMReleaseReviewCorpus.from(review);
        String pinnedRevision = pinned.selectionRevision();
        var cafe = witness(corpus, "1.19.2:after:src/Cafe.java", pinnedRevision);
        var unicode = witness(corpus, "1.19.2:after:src/Unicode.java", pinnedRevision);
        var capture = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                pinnedRevision,
                pinned.sourceExpression(),
                List.of(
                        new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                                cafe,
                                List.of(selection("cafe-backward", cafe.text(), 28, 23, false))
                        ),
                        new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                                unicode,
                                List.of(
                                        selection("unicode-primary", unicode.text(), 61, 67, true),
                                        selection("unicode-name", unicode.text(), 23, 30, false)
                                )
                        )
                )
        );

        var adapted = SFMReleaseReviewSelectionAdapter.adapt(capture);
        assertEquals(pinned, adapted.pinnedSelection());
        assertEquals(binding.selectedProposal().selectionRule(), adapted.literalRule());
        assertEquals(pinned, binding.selectedProposal().literalWitness());
        assertEquals("2222222222222222222222222222222222222222",
                binding.selectedProposal().sourceSnapshotId());

        String liveRevision = "selection-fixture@5555555555555555555555555555555555555555";
        var staleCafe = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                cafe.documentRevisionId(), liveRevision, cafe.sourceAddress(), cafe.text());
        var staleUnicode = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                unicode.documentRevisionId(), liveRevision, unicode.sourceAddress(), unicode.text());
        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class, () ->
                SFMReleaseReviewSelectionAdapter.reverseProject(
                        pinned, SFMReleaseReviewSelectionAdapter.resolver(List.of(staleCafe, staleUnicode))));
        assertTrue(stale.getMessage().contains("stale selection revision"));

        var currentLive = witness(corpus, "1.19.2-live:after:src/Cafe.java", pinnedRevision);
        IllegalArgumentException liveOnly = assertThrows(IllegalArgumentException.class, () ->
                SFMReleaseReviewSelectionAdapter.reverseProject(
                        pinned, SFMReleaseReviewSelectionAdapter.resolver(List.of(currentLive, unicode))));
        assertTrue(liveOnly.getMessage().contains("Document revision is unavailable"),
                "A current-live document identity must not substitute for the pinned revision");

        var changedCafe = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                cafe.documentRevisionId(), pinnedRevision, cafe.sourceAddress(), currentLive.text());
        IllegalArgumentException hashMismatch = assertThrows(IllegalArgumentException.class, () ->
                SFMReleaseReviewSelectionAdapter.reverseProject(
                        pinned, SFMReleaseReviewSelectionAdapter.resolver(List.of(changedCafe, unicode))));
        assertTrue(hashMismatch.getMessage().contains("content hash changed"));

        assertEquals(List.of("unit:src/Cafe.java:value"),
                SFMReleaseReviewKernel.query(review, "#approved intersect 1.19.2 HEAD").reviewUnitIds(),
                "Adapting a neutral selection must not derive another approval");
    }

    private static SFMTextDocumentSelection selection(
            String id,
            String text,
            int anchor,
            int active,
            boolean primary
    ) {
        return new SFMTextDocumentSelection(id, position(text, anchor), position(text, active), primary);
    }

    private static SFMTextDocumentPosition position(String text, int byteOffset) {
        return SFMTextDocumentRange.positionAtByteOffset(text, byteOffset);
    }

    private static SFMReleaseReviewSelectionAdapter.DocumentWitness witness(
            SFMReleaseReviewCorpus corpus,
            String documentRevisionId,
            String selectionRevision
    ) {
        SFMReleaseReviewCorpus.DocumentView view = corpus.documentRevision(documentRevisionId).orElseThrow();
        return SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                documentRevisionId,
                selectionRevision,
                view.binding().sourceOwner() + ":" + view.binding().sourceLocator(),
                view.materializedDocument().orElseThrow().text()
        );
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
