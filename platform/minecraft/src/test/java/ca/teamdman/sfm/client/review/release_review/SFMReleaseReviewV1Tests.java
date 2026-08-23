package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.review.comment.SFMReleaseReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewV1Tests {
    @Test
    void canonicalFixtureRoundTripsAndRetainsStructuralSelectionProvenance() throws Exception {
        String fixtureBytes = Files.readString(fixturePath()).replace("\r\n", "\n");
        SFMReleaseReviewV1 parsed = SFMReleaseReviewV1Codec.parse(fixtureBytes);
        String canonical = SFMReleaseReviewV1Codec.write(parsed);
        assertEquals(fixtureBytes, canonical);
        assertEquals(canonical, SFMReleaseReviewV1Codec.write(SFMReleaseReviewV1Codec.parse(canonical)));
        SFMReleaseReviewKernel.validate(parsed);
        assertEquals(2, parsed.selectorBindings().size());
        SFMReleaseReviewV1.CommentSelectorBinding binding = parsed.selectorBindings().stream()
                .filter(value -> value.commentId().equals("human:approved-value"))
                .findFirst().orElseThrow();
        assertEquals(SFMReleaseReviewV1.SelectionDirection.BACKWARD,
                binding.capturedSelection().ranges().get(0).direction());
        assertEquals(SFMReleaseReviewV1.SelectorKind.BODY, binding.selectedProposal().kind());
        assertEquals("fixture.Café#value():body", binding.selectedProposal().semanticKey().orElseThrow());
        assertEquals(binding.capturedSelection(), binding.selectedProposal().literalWitness());

        SFMReleaseReviewV1.CommentSelectorBinding multiDocument = parsed.selectorBindings().stream()
                .filter(value -> value.commentId().equals("human:unicode-multidoc-note"))
                .findFirst().orElseThrow();
        assertEquals(SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION,
                multiDocument.selectedProposal().kind());
        assertEquals(multiDocument.capturedSelection(), multiDocument.selectedProposal().literalWitness());
        assertEquals(1, multiDocument.capturedSelection().primaryRangeIndex());
        assertEquals(List.of(
                        "1.19.2:after:src/Cafe.java:23-28:BACKWARD",
                        "1.19.2:after:src/Unicode.java:61-67:FORWARD",
                        "1.19.2:after:src/Unicode.java:23-30:FORWARD"
                ), multiDocument.capturedSelection().ranges().stream()
                        .map(range -> range.documentRevisionId() + ":" + range.startByte() + "-" + range.endByte()
                                + ":" + range.direction())
                        .toList());
        assertEquals("2222222222222222222222222222222222222222",
                multiDocument.selectedProposal().sourceSnapshotId());
    }

    @Test
    void canonicalFixtureFreezesThreeSnapshotsPinnedVersusLiveAndOneApprovalAuthority() throws Exception {
        String fixtureBytes = Files.readString(fixturePath()).replace("\r\n", "\n");
        SFMReleaseReviewV1 document = SFMReleaseReviewV1Codec.parse(fixtureBytes);
        SFMReviewSessionV1.RevisionLane pinned = document.reviewSession().revisionLanes().stream()
                .filter(lane -> lane.id().equals("1.19.2"))
                .findFirst().orElseThrow();
        SFMReviewSessionV1.RevisionLane live = document.reviewSession().revisionLanes().stream()
                .filter(lane -> lane.id().equals("1.19.2-live"))
                .findFirst().orElseThrow();

        assertEquals("1111111111111111111111111111111111111111", pinned.before().id());
        assertEquals("2222222222222222222222222222222222222222", pinned.after().id());
        assertEquals(pinned.after().id(), live.before().id(), "B must join the A→B and B→C fixture lanes");
        assertEquals("5555555555555555555555555555555555555555", live.after().id());
        assertEquals(List.of("src/Cafe.java", "src/Other.java", "src/Unicode.java"),
                pinned.after().documents().stream().map(SFMReviewSessionV1.DocumentRevision::path).toList());

        SFMReleaseReviewV1.RepositoryBinding pinnedBinding = document.repositoryBindings().stream()
                .filter(value -> value.laneId().equals("1.19.2"))
                .findFirst().orElseThrow();
        SFMReleaseReviewV1.RepositoryBinding liveBinding = document.repositoryBindings().stream()
                .filter(value -> value.laneId().equals("1.19.2-live"))
                .findFirst().orElseThrow();
        assertEquals(pinned.after().id(), pinnedBinding.candidateCommit());
        assertEquals(pinnedBinding.candidateCommit(), liveBinding.beforeCommit());
        assertEquals(live.after().id(), liveBinding.candidateCommit());
        assertNotEquals(pinnedBinding.candidateCommit(), liveBinding.candidateCommit(),
                "The pinned review target must not float to current live HEAD");

        assertEquals(1, countOccurrences(fixtureBytes, "\"comments\":"),
                "The embedded v2 comments list is the sole comment authority");
        assertEquals(0, countOccurrences(fixtureBytes, "\"approved\":"),
                "Approval must not be serialized as a second derived field");
        List<SFMReviewSessionV2.Comment> approvals = document.reviewSession().comments().stream()
                .filter(comment -> SFMReviewSessionV1Kernel.derivedHashtags(comment.text()).contains("#approved"))
                .toList();
        assertEquals(List.of("human:approved-value"), approvals.stream()
                .map(SFMReviewSessionV2.Comment::id).toList());
        assertEquals(List.of("unit:src/Cafe.java:value"),
                SFMReleaseReviewKernel.query(document, "#approved intersect 1.19.2 HEAD").reviewUnitIds());
    }

    @Test
    void corpusBorrowsTheEmbeddedSnapshotWitnesses() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        SFMReleaseReviewCorpus corpus = SFMReleaseReviewCorpus.from(document);
        SFMReleaseReviewCorpus.DocumentView value = corpus
                .documentRevision("1.19.2:after:src/Cafe.java").orElseThrow();
        assertEquals(value.binding().sha256(),
                SFMReleaseReviewKernel.sha256(value.utf8Bytes().orElseThrow()));
        assertEquals("git", value.binding().sourceOwner());
        assertEquals("2222222222222222222222222222222222222222:src/Cafe.java",
                value.binding().sourceLocator());
        assertEquals(List.of(
                        "corpus:before:cafe|git|1111111111111111111111111111111111111111:src/Cafe.java|COMPLETE",
                        "corpus:after:cafe|git|2222222222222222222222222222222222222222:src/Cafe.java|COMPLETE",
                        "corpus:after:other|git|2222222222222222222222222222222222222222:src/Other.java|COMPLETE",
                        "corpus:after:unicode|git|2222222222222222222222222222222222222222:src/Unicode.java|COMPLETE",
                        "corpus:live-before:cafe|git|2222222222222222222222222222222222222222:src/Cafe.java|COMPLETE",
                        "corpus:live-after:cafe|git|5555555555555555555555555555555555555555:src/Cafe.java|COMPLETE",
                        "corpus:live-after:missing|candidate-history-frame|fixture-plan#live:src/Missing.java|MISSING",
                        "corpus:live-after:partial|candidate-history-frame|fixture-plan#live:src/Partial.java|PARTIAL"
                ), document.corpusDocuments().stream()
                        .map(binding -> binding.id() + "|" + binding.sourceOwner() + "|"
                                + binding.sourceLocator() + "|" + binding.materialization())
                        .toList(), "Corpus order and source-owner provenance are both contractual");

        SFMReleaseReviewCorpus.DocumentView partial = corpus
                .documentRevision("1.19.2-live:after:src/Partial.java").orElseThrow();
        assertEquals(SFMReleaseReviewV1.Materialization.PARTIAL, partial.binding().materialization());
        assertEquals("candidate-history-frame", partial.binding().sourceOwner());
        assertEquals("fixture-plan#live:src/Partial.java", partial.binding().sourceLocator());
        assertTrue(partial.utf8Bytes().isEmpty(), "Partial frame bytes must not be invented by the adapter");

        SFMReleaseReviewCorpus.DocumentView missing = corpus
                .documentRevision("1.19.2-live:after:src/Missing.java").orElseThrow();
        assertEquals(SFMReleaseReviewV1.Materialization.MISSING, missing.binding().materialization());
        assertEquals("candidate-history-frame", missing.binding().sourceOwner());
        assertEquals("fixture-plan#live:src/Missing.java", missing.binding().sourceLocator());
        assertTrue(missing.utf8Bytes().isEmpty(), "Missing frame bytes must remain unavailable");
        assertEquals(document.corpusDocuments().size(), corpus.documents().size());
    }

    @Test
    void canonicalFixtureRejectsACorpusHashThatDisagreesWithItsSourceSnapshot() throws Exception {
        String fixtureBytes = Files.readString(fixturePath()).replace("\r\n", "\n");
        int corpus = fixtureBytes.indexOf("\"id\": \"corpus:after:cafe\"");
        String expected = "72cf61653a927ca969e3b18f51d507072564b94d74616b51f821acc3a170b5c3";
        int hash = fixtureBytes.indexOf(expected, corpus);
        assertTrue(corpus >= 0 && hash > corpus, "Fixture edit must target the corpus witness, not its source snapshot");
        String mismatched = fixtureBytes.substring(0, hash) + "0".repeat(64)
                + fixtureBytes.substring(hash + expected.length());
        SFMReleaseReviewV1 parsedMismatch = SFMReleaseReviewV1Codec.parse(mismatched);
        assertThrows(IllegalArgumentException.class, () -> SFMReleaseReviewKernel.validate(parsedMismatch));
    }

    @Test
    void canonicalCrossLanguageFixtureHasExactQueryAndCompletionParity() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        SFMReleaseReviewKernel.QueryResult raw = SFMReleaseReviewKernel.query(
                document, "#approved intersect 1.19.2 HEAD");
        assertEquals("((#approved intersect 1.19.2) intersect HEAD)", raw.normalizedExpression());
        assertEquals(List.of("unit:src/Cafe.java:value"), raw.reviewUnitIds());
        assertEquals(List.of("unit:src/Cafe.java:value"), SFMReleaseReviewKernel.query(
                document, "effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds());
        assertEquals(List.of("unit:src/Other.java:file"), SFMReleaseReviewKernel.query(
                document, "remaining intersect 1.19.2 HEAD").reviewUnitIds());
        assertEquals(List.of(), SFMReleaseReviewKernel.query(
                document, "blocking intersect 1.19.2 HEAD").reviewUnitIds());
        assertEquals(List.of(), SFMReleaseReviewKernel.query(
                document, "suspended intersect 1.19.2 HEAD").reviewUnitIds());
        assertEquals(raw.reviewUnitIds(),
                SFMReleaseReviewKernel.query(document, "approved-for-candidate").reviewUnitIds());
        assertEquals(List.of("unit:src/Other.java:file"),
                SFMReleaseReviewKernel.query(document, "remaining-work").reviewUnitIds());

        SFMReleaseReviewKernel.CompletionReport status = SFMReleaseReviewKernel.completion(document);
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS, status.status());
        assertEquals(2, status.changedDomain());
        assertEquals(1, status.approvedRaw());
        assertEquals(1, status.approvedEffective());
        assertEquals(1, status.remaining());
        assertEquals(0, status.blocking());
        assertEquals(0, status.suspended());
        assertEquals(0, status.missing());
        assertEquals(0, status.deferred());
        assertEquals(1, status.unsupported());
        assertEquals(0, status.staleProducer());
        assertEquals(List.of("unit:src/Cafe.java:value", "unit:src/Other.java:file"),
                status.witnesses().changedDomain());
        assertEquals(List.of("unit:src/Cafe.java:value"), status.witnesses().approvedRaw());
        assertEquals(List.of("unit:src/Cafe.java:value"), status.witnesses().approvedEffective());
        assertEquals(List.of("unit:src/Other.java:file"), status.witnesses().remaining());
        assertEquals(List.of(), status.witnesses().blocking());
        assertEquals(List.of(), status.witnesses().suspended());
        assertEquals(List.of(), status.witnesses().missing());
        assertEquals(List.of(), status.witnesses().deferred());
        assertEquals(List.of("unit:src/Other.java:file"), status.witnesses().unsupported());
        assertEquals(List.of(), status.witnesses().staleProducer());
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewKernel.query(document, "not-a-real-query"));
    }

    @Test
    void effectiveApprovalExcludesASeparatelyCommentedOverlappingBlocker() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        SFMReviewSessionV2.Comment approval = document.reviewSession().comments().stream()
                .filter(comment -> comment.id().equals("human:approved-value"))
                .findFirst().orElseThrow();
        List<SFMReviewSessionV2.Comment> comments = new java.util.ArrayList<>(
                document.reviewSession().comments());
        comments.add(new SFMReviewSessionV2.Comment(
                "human:block-value",
                "#problem The independently approved surface still has a release blocker.",
                new SFMReviewSessionV1.Provenance(
                        "human", "fixture-reviewer", "1", List.of("human:approved-value")),
                approval.target()
        ));
        SFMReleaseReviewV1 blocked = withComments(document, comments);

        assertEquals(List.of("unit:src/Cafe.java:value"),
                SFMReleaseReviewKernel.query(blocked, "#approved").reviewUnitIds());
        assertEquals(List.of("unit:src/Cafe.java:value"),
                SFMReleaseReviewKernel.query(blocked, "#problem").reviewUnitIds());
        assertEquals(List.of("unit:src/Cafe.java:value"),
                SFMReleaseReviewKernel.query(blocked, "blocking").reviewUnitIds());
        assertTrue(SFMReleaseReviewKernel.query(blocked, "effective(#approved)").reviewUnitIds().isEmpty());
        assertTrue(SFMReleaseReviewKernel.query(blocked, "approved-effective").reviewUnitIds().isEmpty());
        assertEquals(List.of("unit:src/Cafe.java:value"),
                SFMReleaseReviewKernel.query(blocked, "suspended").reviewUnitIds());
    }

    @Test
    void staleAttestationCannotMakeSemanticallyChangedReadyReviewReadyAgain() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        SFMReviewSessionV2.Comment other = document.reviewSession().comments().stream()
                .filter(comment -> comment.id().equals("generated:unit-other"))
                .findFirst().orElseThrow();
        List<SFMReviewSessionV2.Comment> readyComments = new java.util.ArrayList<>(
                document.reviewSession().comments());
        readyComments.add(new SFMReviewSessionV2.Comment(
                "human:approved-other",
                "#approved The added fallback surface has been reviewed.",
                new SFMReviewSessionV1.Provenance(
                        "human", "fixture-reviewer", "1", List.of("generated:unit-other")),
                other.target()
        ));
        SFMReleaseReviewV1 ready = withComments(document, readyComments);
        SFMReleaseReviewKernel.CompletionReport readyReport = SFMReleaseReviewKernel.completion(ready);
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.READY_FOR_MAINTAINER_ATTESTATION,
                readyReport.status());

        SFMReleaseReviewV1 attested = copy(ready, ready.resumeState(), ready.reviewSession(), List.of(
                new SFMReleaseReviewV1.CompletionAttestation(
                        "fixture-ready-attestation",
                        readyReport.reviewSemanticStateHash(),
                        "fixture-maintainer",
                        "2026-08-23T00:00:00Z",
                        "Fixture evidence only"
                )
        ));
        assertEquals(readyReport.reviewSemanticStateHash(), SFMReleaseReviewKernel.semanticStateHash(attested),
                "Attestations must remain outside their own semantic-state hash");
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.COMPLETE,
                SFMReleaseReviewKernel.completion(attested).status());

        List<SFMReviewSessionV2.Comment> changedComments = new java.util.ArrayList<>(
                attested.reviewSession().comments());
        SFMReviewSessionV2.Comment approvedOther = changedComments.stream()
                .filter(comment -> comment.id().equals("human:approved-other"))
                .findFirst().orElseThrow();
        changedComments.set(changedComments.indexOf(approvedOther), new SFMReviewSessionV2.Comment(
                approvedOther.id(), approvedOther.text() + " Semantic review notes changed.",
                approvedOther.provenance(), approvedOther.target()));
        SFMReleaseReviewV1 semanticallyChanged = copy(
                attested,
                attested.resumeState(),
                sessionWithComments(attested.reviewSession(), changedComments),
                attested.completionAttestations()
        );
        SFMReleaseReviewKernel.CompletionReport stale = SFMReleaseReviewKernel.completion(semanticallyChanged);
        assertNotEquals(readyReport.reviewSemanticStateHash(), stale.reviewSemanticStateHash());
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.STALE, stale.status());
        assertTrue(stale.diagnostics().contains(
                "Completion attestations do not match the current semantic state"));
    }

    @Test
    void changedNamedQueryInvalidatesTheStatusSemanticStateAndPriorAttestation() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        String originalHash = SFMReleaseReviewKernel.semanticStateHash(document);
        SFMReleaseReviewV1 attested = copy(document, document.resumeState(), document.reviewSession(), List.of(
                new SFMReleaseReviewV1.CompletionAttestation(
                        "fixture-query-attestation",
                        originalHash,
                        "fixture-maintainer",
                        "2026-08-23T00:00:00Z",
                        "Fixture query-revision evidence"
                )
        ));
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS,
                SFMReleaseReviewKernel.completion(attested).status(),
                "A matching attestation must not complete an otherwise incomplete fixture");

        List<SFMReleaseReviewV1.NamedQuery> changedQueries = attested.namedQueries().stream()
                .map(query -> query.id().equals("remaining-work")
                        ? new SFMReleaseReviewV1.NamedQuery(query.id(), "HEAD")
                        : query)
                .toList();
        SFMReleaseReviewV1 changed = withNamedQueries(attested, changedQueries);

        assertEquals(List.of("unit:src/Cafe.java:value", "unit:src/Other.java:file"),
                SFMReleaseReviewKernel.query(changed, "remaining-work").reviewUnitIds());
        assertTrue(SFMReleaseReviewKernel.activeQueryRevisionStale(changed));
        assertNotEquals(originalHash, SFMReleaseReviewKernel.semanticStateHash(changed));
        SFMReleaseReviewKernel.CompletionReport changedStatus = SFMReleaseReviewKernel.completion(changed);
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.STALE, changedStatus.status());
        assertTrue(changedStatus.diagnostics().contains(
                "Active named-query revision differs from its persisted work-queue expression"));
        assertTrue(changedStatus.diagnostics().contains(
                "Completion attestations do not match the current semantic state"));
    }

    @Test
    void normalizedEquivalentNamedQuerySpellingDoesNotCreateAStaleActiveRevision() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        List<SFMReleaseReviewV1.NamedQuery> equivalentQueries = document.namedQueries().stream()
                .map(query -> query.id().equals("remaining-work")
                        ? new SFMReleaseReviewV1.NamedQuery(query.id(), "remaining 1.19.2 HEAD")
                        : query)
                .toList();
        SFMReleaseReviewV1 equivalent = withNamedQueries(document, equivalentQueries);

        assertEquals(
                SFMReleaseReviewQuery.normalize("remaining intersect 1.19.2 HEAD"),
                SFMReleaseReviewQuery.normalize("remaining 1.19.2 HEAD")
        );
        assertFalse(SFMReleaseReviewKernel.activeQueryRevisionStale(equivalent));
        SFMReleaseReviewKernel.CompletionReport status = SFMReleaseReviewKernel.completion(equivalent);
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS, status.status());
        assertFalse(status.diagnostics().contains(
                "Active named-query revision differs from its persisted work-queue expression"));
    }

    @Test
    void presentationStyleChangesPreserveSemanticStateAndAttestationFreshness() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        String originalHash = SFMReleaseReviewKernel.semanticStateHash(document);
        SFMReleaseReviewV1 attested = copy(document, document.resumeState(), document.reviewSession(), List.of(
                new SFMReleaseReviewV1.CompletionAttestation(
                        "fixture-style-attestation",
                        originalHash,
                        "fixture-maintainer",
                        "2026-08-23T00:00:00Z",
                        "Fixture presentation-only style evidence"
                )
        ));
        SFMReviewSessionV2 styledSession = new SFMReviewSessionV2(
                attested.reviewSession().schema(),
                attested.reviewSession().id(),
                attested.reviewSession().title(),
                attested.reviewSession().coordinateSystem(),
                attested.reviewSession().revisionLanes(),
                attested.reviewSession().comments(),
                List.of(new SFMReviewSessionV1.StyleRule(
                        "presentation-only",
                        List.of("#approved"),
                        100,
                        "green",
                        "black",
                        "single",
                        "A",
                        true
                )),
                attested.reviewSession().completionPolicy()
        );
        SFMReleaseReviewV1 styled = copy(
                attested,
                attested.resumeState(),
                styledSession,
                attested.completionAttestations()
        );

        assertEquals(originalHash, SFMReleaseReviewKernel.semanticStateHash(styled));
        SFMReleaseReviewKernel.CompletionReport status = SFMReleaseReviewKernel.completion(styled);
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS, status.status());
        assertFalse(status.diagnostics().contains(
                "Completion attestations do not match the current semantic state"));
    }

    @Test
    void changedAndUncoveredAliasesAndConfiguredEmptyLanesAreRecognized() throws Exception {
        SFMReleaseReviewV1 document = withEmptyLane(fixture(), "1.21.1");

        assertEquals(SFMReleaseReviewKernel.query(document, "changed-domain").reviewUnitIds(),
                SFMReleaseReviewKernel.query(document, "changed").reviewUnitIds());
        assertEquals(SFMReleaseReviewKernel.query(document, "remaining").reviewUnitIds(),
                SFMReleaseReviewKernel.query(document, "uncovered").reviewUnitIds());
        assertTrue(SFMReleaseReviewKernel.query(document, "1.21.1").reviewUnitIds().isEmpty(),
                "A configured lane remains a valid atom even when it currently contains no review units");
    }

    @Test
    void releaseExplorerUsesPinnedCorpusAndKeepsBothTombstoneSides() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        SFMReviewExplorerModel changes = SFMReviewExplorerModel.releaseChanges(document);
        assertEquals(List.of(
                        "src/Cafe.java", "src/Missing.java", "src/Other.java", "src/Partial.java", "src/Unicode.java"),
                changes.root().children().stream().map(SFMReviewExplorerModel.Node::label).toList());
        SFMReviewExplorerModel.Node other = changes.root().children().stream()
                .filter(node -> node.label().equals("src/Other.java"))
                .findFirst().orElseThrow();
        assertEquals(1, other.children().size());
        assertEquals(4, other.children().get(0).children().size(),
                "each immutable file pair exposes before, after, text-diff, and structured-diff leaves");
        assertTrue(other.children().get(0).children().get(0).leaf().missing());
        assertFalse(other.children().get(0).children().get(1).leaf().missing());

        SFMReviewExplorerModel queue = SFMReviewExplorerModel.releaseQuery(document, "remaining-work");
        assertEquals(1, queue.root().children().size());
        assertEquals("release/unit/unit:src/Other.java:file", queue.selected().id());
        assertTrue(queue.root().children().get(0).children().get(0).leaf().missing());
        assertFalse(queue.root().children().get(0).children().get(1).leaf().missing());
        SFMReleaseReviewV1.Utf8Range expectedOtherRange = document.reviewUnits().stream()
                .filter(unit -> unit.id().equals("unit:src/Other.java:file"))
                .findFirst().orElseThrow().afterRanges().get(0);
        assertEquals(
                Optional.of(expectedOtherRange),
                queue.root().children().get(0).children().get(1).leaf().targetRange(),
                "opening a review work item must retain the exact pinned UTF-8 surface"
        );

        SFMReviewExplorerModel approved = SFMReviewExplorerModel.releaseQuery(
                document, "#approved intersect 1.19.2 HEAD");
        assertEquals(
                Optional.of(new SFMReleaseReviewV1.Utf8Range(35, 60)),
                approved.root().children().get(0).children().get(1).leaf().targetRange()
        );
    }

    @Test
    void completionIsFailClosedAndSemanticHashExcludesResumeAndAttestations() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        SFMReleaseReviewKernel.CompletionReport report = SFMReleaseReviewKernel.completion(document);
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS, report.status());
        assertEquals(2, report.changedDomain());
        assertEquals(1, report.approvedRaw());
        assertEquals(1, report.approvedEffective());
        assertEquals(1, report.remaining());
        assertEquals(1, report.unsupported());
        assertEquals(List.of("unit:src/Cafe.java:value", "unit:src/Other.java:file"),
                report.witnesses().changedDomain());
        assertEquals(List.of("unit:src/Cafe.java:value"), report.witnesses().approvedRaw());
        assertEquals(report.witnesses().approvedRaw(), report.witnesses().approvedEffective());
        assertEquals(List.of("unit:src/Other.java:file"), report.witnesses().remaining());
        assertEquals(report.remaining(), report.witnesses().remaining().size());
        assertEquals(report.unsupported(), report.witnesses().unsupported().size());

        SFMReleaseReviewV1 movedCursor = copy(document,
                new SFMReleaseReviewV1.ResumeState(
                        Optional.of("approved-for-candidate"), Optional.of("#approved 1.19.2 HEAD"),
                        Optional.of("unit:src/Cafe.java:value"), List.of("unit:src/Other.java:file"), 99),
                document.reviewSession(), document.completionAttestations());
        assertEquals(report.reviewSemanticStateHash(), SFMReleaseReviewKernel.semanticStateHash(movedCursor));

        SFMReleaseReviewV1 withAttestation = copy(document, document.resumeState(), document.reviewSession(), List.of(
                new SFMReleaseReviewV1.CompletionAttestation(
                        "fixture-attestation", report.reviewSemanticStateHash(), "fixture-maintainer",
                        "2026-08-23T00:00:00Z", "Fixture evidence only")
        ));
        assertEquals(report.reviewSemanticStateHash(), SFMReleaseReviewKernel.semanticStateHash(withAttestation));
        assertEquals(SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS,
                SFMReleaseReviewKernel.completion(withAttestation).status(),
                "An attestation cannot complete a document whose domain is still incomplete");

        List<SFMReviewSessionV2.Comment> changedComments = new java.util.ArrayList<>(document.reviewSession().comments());
        SFMReviewSessionV2.Comment original = changedComments.get(1);
        changedComments.set(1, new SFMReviewSessionV2.Comment(
                original.id(), original.text() + " edited", original.provenance(), original.target()));
        SFMReviewSessionV2 changedSession = new SFMReviewSessionV2(
                document.reviewSession().schema(), document.reviewSession().id(), document.reviewSession().title(),
                document.reviewSession().coordinateSystem(), document.reviewSession().revisionLanes(), changedComments,
                document.reviewSession().styleRules(), document.reviewSession().completionPolicy());
        assertNotEquals(report.reviewSemanticStateHash(), SFMReleaseReviewKernel.semanticStateHash(
                copy(document, document.resumeState(), changedSession, List.of())));
    }

    @Test
    void explicitPathStoreDetectsExternalEditsAndSingleWriter(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("review.sfm-review.json");
        SFMReleaseReviewV1 document = fixture();
        try (SFMReleaseReviewStore store = SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE)) {
            SFMReleaseReviewStore.SaveResult first = store.save(document, Optional.empty());
            assertTrue(Files.isRegularFile(path));
            SFMReleaseReviewStore.LoadResult reopened = store.load();
            assertEquals(first.contentHash(), reopened.openedContentHash().orElseThrow());
            assertFalse(reopened.recoveredMachineLocalCopy());
            assertThrows(java.io.IOException.class,
                    () -> SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE));

            Files.writeString(path, Files.readString(path) + " ");
            assertThrows(SFMReleaseReviewStore.ExternalEditConflict.class,
                    () -> store.save(document, Optional.of(first.contentHash())));
        }
        try (SFMReleaseReviewStore store = SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE)) {
            assertEquals(path.toAbsolutePath().normalize(), store.path());
        }
    }

    @Test
    void writableLeaseIsExclusiveAcrossProcesses(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("cross-process.sfm-review.json");
        String javaExecutable = ProcessHandle.current().info().command()
                .orElseThrow(() -> new IllegalStateException("Current Java executable is unavailable"));
        String separator = System.getProperty("path.separator");
        String classPath = String.join(separator,
                Path.of(SFMReleaseReviewStoreLeaseProbe.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString(),
                Path.of(SFMReleaseReviewStore.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString());
        Process child = new ProcessBuilder(
                javaExecutable,
                "-cp", classPath,
                SFMReleaseReviewStoreLeaseProbe.class.getName(),
                path.toString()
        ).redirectErrorStream(true).start();
        try {
            var ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return child.inputReader().readLine();
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            });
            assertEquals("LEASE_READY", ready.get(Duration.ofSeconds(5).toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS));
            java.io.IOException conflict = assertThrows(java.io.IOException.class,
                    () -> SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE));
            assertTrue(conflict.getMessage().contains("another process"));
        } finally {
            child.getOutputStream().write('\n');
            child.getOutputStream().flush();
            if (!child.waitFor(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                child.destroy();
            }
            if (!child.waitFor(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                child.destroyForcibly();
                assertTrue(child.waitFor(Duration.ofSeconds(5).toMillis(),
                        java.util.concurrent.TimeUnit.MILLISECONDS));
            }
        }
        try (SFMReleaseReviewStore ignored = SFMReleaseReviewStore.open(
                path, SFMReleaseReviewStore.Access.WRITABLE)) {
            assertTrue(Files.isRegularFile(ignored.writerLockPath()));
        }
    }

    @Test
    void runtimeAutosavesAndResumesWithoutItsMachineLocalRecovery(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("docs/reviews/fixture.sfm-review.json");
        SFMReleaseReviewV1 document = fixture();
        Path recovery;
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, document);
            SFMReleaseReviewRuntime.MutationResult mutation = runtime.mutate(current -> copy(
                    current,
                    new SFMReleaseReviewV1.ResumeState(
                            Optional.of("remaining-work"), Optional.of("remaining 1.19.2 HEAD"),
                            Optional.of("unit:src/Other.java:file"), List.of("unit:src/Other.java:file"), 2),
                    current.reviewSession(), current.completionAttestations()
            ));
            assertTrue(mutation.saved());
            assertFalse(runtime.dirty());
            try (SFMReleaseReviewStore probe = SFMReleaseReviewStore.open(
                    path, SFMReleaseReviewStore.Access.READ_ONLY)) {
                recovery = probe.recoveryPath();
            }
        }
        Files.deleteIfExists(recovery);
        try (SFMReleaseReviewRuntime reopened = new SFMReleaseReviewRuntime()) {
            SFMReleaseReviewRuntime.OpenResult result = reopened.open(path, true);
            assertTrue(result.document().isPresent());
            assertFalse(result.recoveredMachineLocalCopy());
            assertEquals(2, result.document().orElseThrow().resumeState().generation());
            assertEquals(List.of("unit:src/Other.java:file"),
                    reopened.query("remaining-work").reviewUnitIds());
        }
    }

    @Test
    void runtimeCachesCompletionUntilTheDocumentChanges(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("status-cache.sfm-review.json");
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, fixture());
            SFMReleaseReviewKernel.CompletionReport first = runtime.status();
            assertSame(first, runtime.status(), "palette availability refreshes must reuse completion analysis");

            assertTrue(runtime.activateQuery(Optional.empty(), "remaining").saved());
            SFMReleaseReviewKernel.CompletionReport afterMutation = runtime.status();
            assertNotSame(first, afterMutation, "every committed mutation must invalidate cached completion");
            assertSame(afterMutation, runtime.status());
        }
    }

    @Test
    void readOnlyMutationDoesNotPublishCandidateOrAdvanceGeneration(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("read-only-mutation.sfm-review.json");
        try (SFMReleaseReviewRuntime writer = new SFMReleaseReviewRuntime()) {
            writer.create(path, fixture());
        }

        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        try {
            runtime.open(path, false);
            SFMReleaseReviewRuntime.Snapshot before = runtime.snapshot();
            SFMReleaseReviewRuntime.MutationResult failed = runtime.mutate(current -> copy(
                    current,
                    new SFMReleaseReviewV1.ResumeState(
                            Optional.of("remaining-work"), Optional.of("remaining-work"),
                            Optional.of("unit:src/Other.java:file"), List.of(), 99),
                    current.reviewSession(), current.completionAttestations()));
            assertFalse(failed.saved());
            assertFalse(failed.dirty());
            assertTrue(failed.failure().orElseThrow().contains("code=review.read-only"));
            assertTrue(failed.failure().orElseThrow().contains("access=READ_ONLY"));
            SFMReleaseReviewRuntime.Snapshot after = runtime.snapshot();
            assertEquals(before.document(), after.document());
            assertEquals(before.generation(), after.generation());
            assertFalse(after.dirty());
            assertEquals(1, runtime.document().orElseThrow().resumeState().generation());

            runtime.close();
            assertEquals(1, runtime.open(path, false).document().orElseThrow().resumeState().generation(),
                    "reopening must reveal the unchanged authoritative repository bytes");
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void failedAtomicCommentSaveLeavesPublishedSnapshotAndCompletionCacheIntact(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("external-edit-comment.sfm-review.json");
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, fixture());
            SFMReleaseReviewRuntime.Snapshot before = runtime.snapshot();
            SFMReleaseReviewKernel.CompletionReport cachedStatus = runtime.status();
            SFMReleaseReviewV1.CommentSelectorBinding existingBinding = before.document().orElseThrow()
                    .selectorBindings().get(0);
            SFMReleaseReviewV1.SelectorProposal existingProposal = existingBinding.selectedProposal();
            SFMReleaseReviewV1.SelectorProposal proposal = new SFMReleaseReviewV1.SelectorProposal(
                    "test:atomic-save-failure",
                    existingProposal.kind(),
                    existingProposal.selectionRule(),
                    existingProposal.literalWitness(),
                    existingProposal.semanticProvider(),
                    existingProposal.semanticKey(),
                    existingProposal.semanticProvenance(),
                    existingProposal.confidence(),
                    existingProposal.projectionFingerprint(),
                    existingProposal.sourceSnapshotId(),
                    existingProposal.diagnostics()
            );

            Files.writeString(path, Files.readString(path) + " ");
            SFMReleaseReviewRuntime.CommentMutationResult failed = runtime.createComment(
                    "#needs-change must not become a ghost comment",
                    existingBinding.capturedSelection(),
                    proposal
            );

            assertFalse(failed.mutation().saved());
            assertFalse(failed.mutation().dirty());
            String diagnostic = failed.mutation().failure().orElseThrow();
            assertTrue(diagnostic.contains("code=review.external-edit-conflict"));
            assertTrue(diagnostic.contains("operation=mutate"));
            assertTrue(diagnostic.contains("failure_type=ExternalEditConflict"));
            assertTrue(diagnostic.contains(path.toAbsolutePath().normalize().toString()));
            SFMReleaseReviewRuntime.Snapshot after = runtime.snapshot();
            assertEquals(before.document(), after.document());
            assertEquals(before.generation(), after.generation());
            assertEquals(before.dirty(), after.dirty());
            assertSame(cachedStatus, runtime.status(),
                    "a rejected candidate must not invalidate the published completion cache");
            assertFalse(after.document().orElseThrow().reviewSession().comments().stream()
                    .anyMatch(comment -> comment.text().contains("ghost comment")));
        }
    }

    @Test
    void runtimeSnapshotPublishesAccessAndWritabilityWithDocumentIdentity(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("snapshot-access.sfm-review.json");
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        try {
            SFMReleaseReviewRuntime.Snapshot closed = runtime.snapshot();
            assertTrue(closed.access().isEmpty());
            assertFalse(closed.writable());

            runtime.create(path, fixture());
            SFMReleaseReviewRuntime.Snapshot writable = runtime.snapshot();
            assertEquals(Optional.of(SFMReleaseReviewStore.Access.WRITABLE), writable.access());
            assertTrue(writable.writable());

            runtime.discardAndClose();
            runtime.open(path, false);
            SFMReleaseReviewRuntime.Snapshot readOnly = runtime.snapshot();
            assertEquals(Optional.of(SFMReleaseReviewStore.Access.READ_ONLY), readOnly.access());
            assertFalse(readOnly.writable());
        } finally {
            runtime.discardAndClose();
        }
    }

    @Test
    void namedQueryMutationAutosavesAndBecomesTheResumableQueue(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("named-query.sfm-review.json");
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, fixture());
            SFMReleaseReviewRuntime.MutationResult mutation = runtime.saveNamedQuery(
                    "needs-human-review",
                    "remaining intersect 1.19.2 HEAD",
                    true
            );
            assertTrue(mutation.saved());
            assertEquals(Optional.of("needs-human-review"),
                    runtime.document().orElseThrow().resumeState().activeQueryId());
            assertEquals(List.of("unit:src/Other.java:file"),
                    runtime.query("needs-human-review").reviewUnitIds());
        }
        try (SFMReleaseReviewRuntime reopened = new SFMReleaseReviewRuntime()) {
            SFMReleaseReviewV1 document = reopened.open(path, true).document().orElseThrow();
            assertTrue(document.namedQueries().stream().anyMatch(query ->
                    query.id().equals("needs-human-review")
                            && query.expression().equals("remaining intersect 1.19.2 HEAD")));
            assertEquals(Optional.of("needs-human-review"), document.resumeState().activeQueryId());
            assertEquals(List.of("unit:src/Other.java:file"),
                    reopened.query("needs-human-review").reviewUnitIds());
        }
    }

    @Test
    void ordinaryCommentMutationsAutosaveIntoThePortableEnvelope(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("review.sfm-review.json");
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, fixture());
            SFMReleaseReviewCommentDataSource comments = new SFMReleaseReviewCommentDataSource(runtime);
            String id = comments.createLiteralComment(
                    "#note Portable comment",
                    List.of(new SFMReviewCommentDataSource.RangeView(
                            "1.19.2:after:src/Cafe.java", 35, 60))
            );
            assertEquals("human-1", id);
            assertFalse(runtime.dirty());
        }
        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        assertTrue(reopened.reviewSession().comments().stream()
                .anyMatch(comment -> comment.id().equals("human-1")
                        && comment.text().equals("#note Portable comment")));
    }

    @Test
    void editorCaptureRetainsBackwardSelectionAndLiteralFallback() throws Exception {
        SFMReleaseReviewV1 document = fixture();
        SFMReleaseReviewEditorCapture.Capture capture = editorCapture(
                document, "1.19.2:after:src/Cafe.java", 35, 60, true);
        assertEquals(SFMReleaseReviewV1.SelectionDirection.BACKWARD,
                capture.adapted().pinnedSelection().ranges().get(0).direction());
        assertEquals(SFMReleaseReviewV1.SelectorKind.LITERAL,
                capture.proposals().proposals().get(0).kind());
        assertTrue(capture.diagnostics().stream().anyMatch(value -> value.contains("literal selection")));
    }

    @Test
    void structurallyPinnedEditorCommentAutosavesWithItsSelectorBinding(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("review.sfm-review.json");
        SFMReleaseReviewV1 document = fixture();
        SFMReleaseReviewEditorCapture.Capture capture = editorCapture(
                document, "1.19.2:after:src/Other.java", 0, 32, false);
        SFMReleaseReviewV1.SelectorProposal literal = capture.proposals().proposals().get(0);
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, document);
            SFMReleaseReviewRuntime.CommentMutationResult created = runtime.createComment(
                    "#approved Reviewed through the in-game release-review surface.",
                    capture.adapted().pinnedSelection(),
                    literal
            );
            assertTrue(created.mutation().saved());
            assertFalse(runtime.dirty());
            assertEquals(2, runtime.query("#approved intersect 1.19.2 HEAD").reviewUnitIds().size());
        }
        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        assertTrue(reopened.selectorBindings().stream().anyMatch(binding ->
                binding.commentId().equals("human:release-review:1")
                        && binding.selectedProposal().kind() == SFMReleaseReviewV1.SelectorKind.LITERAL));
    }

    @Test
    void witnessedRelocationRequiresExplicitConfirmationAndPersistsItsOrdinaryDecisionComment(
            @TempDir Path directory
    ) throws Exception {
        Path path = directory.resolve("review.sfm-review.json");
        SFMReleaseReviewV1.AddressedRange relocated = new SFMReleaseReviewV1.AddressedRange(
                "1.19.2:after:src/Other.java", 0, 32);
        SFMReleaseReviewV1 document = withMigration(
                fixture(), SFMReleaseReviewV1.EvaluationStatus.RELOCATED, List.of(relocated));

        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, document);
            assertTrue(runtime.query("effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds().isEmpty(),
                    "an unconfirmed relocation must suspend human approval");
            assertEquals(1, runtime.status().suspended());
            SFMReleaseReviewRuntime.MigrationMutationResult result = runtime.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.RELOCATION_CONFIRMED,
                    java.util.OptionalInt.empty(),
                    "Confirmed the witnessed relocation in the candidate snapshot."
            );
            assertTrue(result.mutation().saved());
            assertFalse(runtime.dirty());
            assertEquals(List.of("unit:src/Other.java:file"),
                    runtime.query("#approved intersect 1.19.2 HEAD").reviewUnitIds());
            assertEquals(List.of("unit:src/Other.java:file"),
                    runtime.query("effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds());
            assertThrows(IllegalArgumentException.class, () -> runtime.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.RELOCATION_CONFIRMED,
                    java.util.OptionalInt.empty(),
                    "A stale second decision must fail."
            ));
        }

        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        SFMReleaseReviewV1.MigrationReport report = reopened.migrationReports().get(0);
        assertEquals(SFMReleaseReviewV1.MigrationDecision.RELOCATION_CONFIRMED, report.decision());
        assertTrue(report.decisionCommentId().isPresent());
        assertTrue(reopened.reviewSession().comments().stream().anyMatch(comment ->
                comment.id().equals(report.decisionCommentId().orElseThrow())
                        && comment.text().contains("#relocation-confirmed")));
        SFMReleaseReviewV1.CommentSelectorBinding binding = reopened.selectorBindings().get(0);
        assertEquals("1.19.2:after:src/Other.java",
                ((ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.LiteralUtf8Range)
                        binding.selectedProposal().selectionRule()).documentRevisionId());
        assertEquals("1.19.2:after:src/Cafe.java",
                binding.selectedProposal().literalWitness().ranges().get(0).documentRevisionId(),
                "Migration must retain the original literal witness as provenance");
    }

    @Test
    void ambiguousMigrationCanBeDeferredAcrossReopenAndThenExplicitlyRetargeted(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("review.sfm-review.json");
        SFMReleaseReviewV1.AddressedRange candidate = new SFMReleaseReviewV1.AddressedRange(
                "1.19.2:after:src/Other.java", 0, 32);
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, withMigration(
                    fixture(), SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS, List.of(candidate)));
            assertTrue(runtime.query("effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds().isEmpty());
            assertEquals(1, runtime.status().suspended());
            assertEquals(1, runtime.status().missing());
            assertThrows(IllegalArgumentException.class, () -> runtime.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.RELOCATION_CONFIRMED,
                    java.util.OptionalInt.empty(),
                    "Ambiguous evidence is not safe relocation evidence."
            ));
            assertTrue(runtime.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.DEFERRED,
                    java.util.OptionalInt.empty(),
                    "Defer until the candidate is manually inspected."
            ).mutation().saved());
        }
        try (SFMReleaseReviewRuntime reopened = new SFMReleaseReviewRuntime()) {
            reopened.open(path, true);
            assertEquals(SFMReleaseReviewV1.MigrationDecision.DEFERRED,
                    reopened.document().orElseThrow().migrationReports().get(0).decision());
            assertEquals(1, reopened.status().suspended(),
                    "deferring ambiguous evidence must not restore effective approval");
            assertTrue(reopened.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.RETARGETED,
                    java.util.OptionalInt.of(0),
                    "Retargeted after inspecting candidate one."
            ).mutation().saved());
        }
        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        assertEquals(SFMReleaseReviewV1.MigrationDecision.RETARGETED,
                reopened.migrationReports().get(0).decision());
        assertEquals(2, reopened.reviewSession().comments().stream()
                .filter(comment -> comment.text().contains("#migration-decision"))
                .count());
    }

    @Test
    void migrationDecisionRejectsAStaleChoiceCapture(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("stale-migration-choice.sfm-review.json");
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, withMigration(
                    fixture(), SFMReleaseReviewV1.EvaluationStatus.RELOCATED,
                    List.of(new SFMReleaseReviewV1.AddressedRange(
                            "1.19.2:after:src/Other.java", 0, 32))));
            String stale = SFMReleaseReviewKernel.semanticStateHash(runtime.document().orElseThrow());
            assertTrue(runtime.saveNamedQuery("new-state", "remaining", true).saved());
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> runtime.decideMigration(
                            "migration-1",
                            stale,
                            SFMReleaseReviewV1.MigrationDecision.RELOCATION_CONFIRMED,
                            java.util.OptionalInt.empty(),
                            "This stale choice must not be applied."));
            assertTrue(failure.getMessage().contains("Migration choice is stale"));
            assertEquals(SFMReleaseReviewV1.MigrationDecision.UNRESOLVED,
                    runtime.document().orElseThrow().migrationReports().get(0).decision());
        }
    }

    @Test
    void archivingAnUnresolvedApprovalRemovesItFromRawAndEffectiveCoverage(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("review.sfm-review.json");
        SFMReleaseReviewV1 document = withMigration(
                fixture(), SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED, List.of());
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, document);
            assertEquals(1, runtime.query("#approved intersect 1.19.2 HEAD").reviewUnitIds().size());
            assertTrue(runtime.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.ARCHIVED,
                    java.util.OptionalInt.empty(),
                    "The old approval cannot safely apply to the changed content."
            ).mutation().saved());
            assertTrue(runtime.query("#approved intersect 1.19.2 HEAD").reviewUnitIds().isEmpty());
            assertTrue(runtime.query("effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds().isEmpty());
            assertEquals(2, runtime.query("remaining-work").reviewUnitIds().size());
        }
        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        assertTrue(reopened.reviewSession().comments().stream().anyMatch(comment ->
                comment.id().equals("human:approved-value")
                        && comment.text().startsWith("#archived ")));
    }

    @Test
    void selectorEditedDecisionPersistsReplacementWitnessAndHumanProvenance(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("selector-edited.sfm-review.json");
        SFMReleaseReviewV1 original = fixture();
        SFMReleaseReviewV1.CommentSelectorBinding originalBinding = original.selectorBindings().get(0);
        SFMReleaseReviewV1.AddressedRange candidate = new SFMReleaseReviewV1.AddressedRange(
                "1.19.2:after:src/Other.java", 0, 32);
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, withMigration(
                    original, SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS, List.of(candidate)));
            SFMReleaseReviewRuntime.MigrationMutationResult result = runtime.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.SELECTOR_EDITED,
                    java.util.OptionalInt.of(0),
                    "Edited the selector after inspecting the candidate witness."
            );
            assertTrue(result.mutation().saved());
            assertFalse(runtime.dirty());
        }

        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        SFMReleaseReviewKernel.validate(reopened);
        SFMReleaseReviewV1.MigrationReport report = reopened.migrationReports().get(0);
        assertEquals(SFMReleaseReviewV1.MigrationDecision.SELECTOR_EDITED, report.decision());
        assertTrue(report.decisionCommentId().isPresent());
        SFMReleaseReviewV1.CommentSelectorBinding migrated = reopened.selectorBindings().get(0);
        assertEquals(originalBinding.capturedSelection(), migrated.capturedSelection());
        assertEquals(originalBinding.selectedProposal().literalWitness(), migrated.selectedProposal().literalWitness(),
                "editing a selector must not rewrite the captured literal provenance");
        assertEquals(Optional.of("sfm:human-migration"), migrated.selectedProposal().semanticProvider());
        assertTrue(migrated.selectedProposal().semanticProvenance().containsAll(
                originalBinding.selectedProposal().semanticProvenance()));
        assertTrue(migrated.selectedProposal().semanticProvenance().stream().anyMatch(evidence ->
                evidence.key().equals("migration-report")
                        && evidence.value().equals("migration:fixture-value")));
        SFMReviewSessionV1.LiteralUtf8Range replacement = (SFMReviewSessionV1.LiteralUtf8Range)
                migrated.selectedProposal().selectionRule();
        assertEquals(candidate.documentRevisionId(), replacement.documentRevisionId());
        assertEquals(candidate.startByte(), replacement.startByte());
        assertEquals(candidate.endByte(), replacement.endByte());
        assertTrue(reopened.reviewSession().comments().stream().anyMatch(comment ->
                comment.id().equals(report.decisionCommentId().orElseThrow())
                        && comment.text().contains("#selector-edited")
                        && comment.provenance().parentCommentIds().contains(originalBinding.commentId())));

        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.open(path, true);
            assertEquals(List.of("unit:src/Other.java:file"),
                    runtime.query("effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds());
        }
    }

    @Test
    void discardedDecisionPersistsArchivedSourceWithoutInventingAReplacement(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("discarded.sfm-review.json");
        SFMReleaseReviewV1 original = fixture();
        SFMReleaseReviewV1.CommentSelectorBinding originalBinding = original.selectorBindings().get(0);
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, withMigration(
                    original, SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED, List.of()));
            SFMReleaseReviewRuntime.MigrationMutationResult result = runtime.decideMigration(
                    "migration:fixture-value",
                    SFMReleaseReviewV1.MigrationDecision.DISCARDED,
                    java.util.OptionalInt.empty(),
                    "Discarded because this approval no longer describes candidate code."
            );
            assertTrue(result.mutation().saved());
            assertTrue(runtime.query("#approved intersect 1.19.2 HEAD").reviewUnitIds().isEmpty());
            assertTrue(runtime.query("effective(#approved) intersect 1.19.2 HEAD").reviewUnitIds().isEmpty());
        }

        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        SFMReleaseReviewKernel.validate(reopened);
        SFMReleaseReviewV1.MigrationReport report = reopened.migrationReports().get(0);
        assertEquals(SFMReleaseReviewV1.MigrationDecision.DISCARDED, report.decision());
        assertEquals(originalBinding, reopened.selectorBindings().get(0),
                "discard must preserve the old selector as inspectable provenance instead of retargeting it");
        SFMReviewSessionV2.Comment source = reopened.reviewSession().comments().stream()
                .filter(comment -> comment.id().equals(originalBinding.commentId()))
                .findFirst().orElseThrow();
        assertTrue(source.text().startsWith("#archived "));
        assertTrue(reopened.reviewSession().comments().stream().anyMatch(comment ->
                comment.id().equals(report.decisionCommentId().orElseThrow())
                        && comment.text().contains("#discarded")
                        && comment.provenance().parentCommentIds().contains(originalBinding.commentId())));
    }

    @Test
    void deferringTheLastQueueItemWrapsToTheFirstStableUnit(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("review.sfm-review.json");
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, fixture());
            assertTrue(runtime.activateQuery(Optional.empty(), "HEAD").saved());
            assertTrue(runtime.move(1).saved());
            assertEquals("unit:src/Other.java:file",
                    runtime.document().orElseThrow().resumeState().currentUnitId().orElseThrow());
            assertTrue(runtime.deferCurrent().saved());
            assertEquals("unit:src/Cafe.java:value",
                    runtime.document().orElseThrow().resumeState().currentUnitId().orElseThrow());
            assertEquals(List.of("unit:src/Other.java:file"),
                    runtime.document().orElseThrow().resumeState().deferredUnitIds());
        }
    }

    @Test
    void stableUnitSelectionPersistsOnlyForMembersOfTheActiveQueue(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("select-review-unit.sfm-review.json");
        try (SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime()) {
            runtime.create(path, fixture());
            assertTrue(runtime.activateQuery(Optional.empty(), "HEAD").saved());
            assertTrue(runtime.selectUnit("unit:src/Other.java:file").saved());
            assertEquals("unit:src/Other.java:file",
                    runtime.document().orElseThrow().resumeState().currentUnitId().orElseThrow());
            SFMReleaseReviewRuntime.MutationResult missing = runtime.selectUnit("unit:missing");
            assertFalse(missing.saved());
            assertTrue(missing.failure().orElseThrow().contains("active work queue"));
            assertEquals("unit:src/Other.java:file",
                    runtime.document().orElseThrow().resumeState().currentUnitId().orElseThrow());
        }
        SFMReleaseReviewV1 reopened = SFMReleaseReviewV1Codec.parse(Files.readString(path));
        assertEquals("unit:src/Other.java:file", reopened.resumeState().currentUnitId().orElseThrow());
    }

    @Test
    void unknownFieldsAndDanglingResumeIdsAreRejected() throws Exception {
        String json = Files.readString(fixturePath()).replace("\r\n", "\n");
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewV1Codec.parse(json.replaceFirst("\\{", "{\n  \"future_field\": true,")));
        int proposal = json.indexOf("\"selected_proposal\"");
        int proposalRule = json.indexOf("\"kind\": \"literal_utf8_range\"", proposal);
        String invalidNestedRule = json.substring(0, proposalRule)
                + "\"kind\": \"literal_utf8_range\", \"future_rule_field\": true"
                + json.substring(proposalRule + "\"kind\": \"literal_utf8_range\"".length());
        assertThrows(IllegalArgumentException.class,
                () -> SFMReleaseReviewV1Codec.parse(invalidNestedRule));
        SFMReleaseReviewV1 document = fixture();
        SFMReleaseReviewV1 dangling = copy(document,
                new SFMReleaseReviewV1.ResumeState(Optional.empty(), Optional.empty(),
                        Optional.of("unit:missing"), List.of(), 0),
                document.reviewSession(), List.of());
        assertThrows(IllegalArgumentException.class, () -> SFMReleaseReviewKernel.validate(dangling));
        assertThrows(IllegalArgumentException.class, () -> new SFMReleaseReviewV1.CompletionAttestation(
                "bad-time", "0".repeat(64), "maintainer", "tomorrow", "invalid timestamp"));
    }

    private static SFMReleaseReviewV1 copy(
            SFMReleaseReviewV1 document,
            SFMReleaseReviewV1.ResumeState resume,
            SFMReviewSessionV2 session,
            List<SFMReleaseReviewV1.CompletionAttestation> attestations
    ) {
        return new SFMReleaseReviewV1(
                document.schema(), session, document.repositoryBindings(), document.corpusDocuments(),
                document.reviewUnits(), document.selectorBindings(), document.migrationReports(), document.namedQueries(),
                resume, document.producerGenerations(), attestations
        );
    }

    private static SFMReleaseReviewV1 withNamedQueries(
            SFMReleaseReviewV1 document,
            List<SFMReleaseReviewV1.NamedQuery> namedQueries
    ) {
        return new SFMReleaseReviewV1(
                document.schema(), document.reviewSession(), document.repositoryBindings(), document.corpusDocuments(),
                document.reviewUnits(), document.selectorBindings(), document.migrationReports(), namedQueries,
                document.resumeState(), document.producerGenerations(), document.completionAttestations()
        );
    }

    private static SFMReleaseReviewV1 withComments(
            SFMReleaseReviewV1 document,
            List<SFMReviewSessionV2.Comment> comments
    ) {
        return copy(
                document,
                document.resumeState(),
                sessionWithComments(document.reviewSession(), comments),
                document.completionAttestations()
        );
    }

    private static SFMReviewSessionV2 sessionWithComments(
            SFMReviewSessionV2 session,
            List<SFMReviewSessionV2.Comment> comments
    ) {
        return new SFMReviewSessionV2(
                session.schema(), session.id(), session.title(), session.coordinateSystem(),
                session.revisionLanes(), comments, session.styleRules(), session.completionPolicy()
        );
    }

    private static SFMReleaseReviewV1 withEmptyLane(SFMReleaseReviewV1 document, String laneId) {
        List<SFMReviewSessionV1.RevisionLane> lanes = new java.util.ArrayList<>(
                document.reviewSession().revisionLanes());
        lanes.add(new SFMReviewSessionV1.RevisionLane(
                laneId,
                new SFMReviewSessionV1.Repository("sfm", "."),
                laneId,
                new SFMReviewSessionV1.Snapshot("empty-before", List.of()),
                new SFMReviewSessionV1.Snapshot("empty-after", List.of())
        ));
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                document.reviewSession().schema(),
                document.reviewSession().id(),
                document.reviewSession().title(),
                document.reviewSession().coordinateSystem(),
                lanes,
                document.reviewSession().comments(),
                document.reviewSession().styleRules(),
                document.reviewSession().completionPolicy()
        );
        List<SFMReleaseReviewV1.RepositoryBinding> bindings = new java.util.ArrayList<>(
                document.repositoryBindings());
        bindings.add(new SFMReleaseReviewV1.RepositoryBinding(
                laneId,
                "sfm",
                ".",
                "empty-before",
                "8".repeat(40),
                "9".repeat(40),
                "HEAD",
                "a".repeat(40),
                "b".repeat(40),
                List.of("docs/reviews/empty.sfm-review.json")
        ));
        return new SFMReleaseReviewV1(
                document.schema(), session, bindings, document.corpusDocuments(), document.reviewUnits(),
                document.selectorBindings(), document.migrationReports(), document.namedQueries(),
                document.resumeState(), document.producerGenerations(), document.completionAttestations()
        );
    }

    private static SFMReleaseReviewV1 fixture() throws Exception {
        return SFMReleaseReviewV1Codec.parse(Files.readString(fixturePath()).replace("\r\n", "\n"));
    }

    private static SFMReleaseReviewV1 withMigration(
            SFMReleaseReviewV1 document,
            SFMReleaseReviewV1.EvaluationStatus candidateStatus,
            List<SFMReleaseReviewV1.AddressedRange> candidates
    ) {
        SFMReleaseReviewV1.CommentSelectorBinding binding = document.selectorBindings().get(0);
        SFMReleaseReviewV1.EvaluationResult source = new SFMReleaseReviewV1.EvaluationResult(
                binding.selectedProposal().id(),
                SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED,
                List.of(),
                List.of(),
                List.of(),
                List.of("The source selector no longer has an exact effective match.")
        );
        SFMReleaseReviewV1.EvaluationResult candidate = new SFMReleaseReviewV1.EvaluationResult(
                binding.selectedProposal().id(),
                candidateStatus,
                candidateStatus == SFMReleaseReviewV1.EvaluationStatus.RELOCATED ? candidates : List.of(),
                candidates,
                List.of(),
                List.of("Synthetic migration evidence for the focused persistence contract test.")
        );
        SFMReleaseReviewV1.MigrationReport migration = new SFMReleaseReviewV1.MigrationReport(
                "migration:fixture-value",
                binding.selectedProposal().id(),
                source,
                candidate,
                binding.capturedSelection().ranges(),
                candidates,
                SFMReleaseReviewV1.MigrationDecision.UNRESOLVED,
                Optional.empty()
        );
        return new SFMReleaseReviewV1(
                document.schema(), document.reviewSession(), document.repositoryBindings(), document.corpusDocuments(),
                document.reviewUnits(), document.selectorBindings(), List.of(migration), document.namedQueries(),
                document.resumeState(), document.producerGenerations(), document.completionAttestations()
        );
    }

    private static SFMReleaseReviewEditorCapture.Capture editorCapture(
            SFMReleaseReviewV1 document,
            String revisionId,
            int startByte,
            int endByte,
            boolean backward
    ) {
        SFMReleaseReviewCorpus.DocumentView corpus = SFMReleaseReviewCorpus.from(document)
                .documentRevision(revisionId).orElseThrow();
        String text = corpus.materializedDocument().orElseThrow().text();
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED, "review", "document", List.of(revisionId), Optional.empty(), true);
        java.util.ArrayList<String> segments = new java.util.ArrayList<>();
        segments.add(revisionId);
        for (String segment : corpus.binding().path().split("/")) {
            if (!segment.isBlank()) segments.add(segment);
        }
        SFMPath addressed = new SFMPath(
                SFMPath.Kind.CONTRIBUTED, "review", "document", segments, Optional.empty(), false);
        SFMTextDocumentSnapshot baseline = SFMTextDocumentSnapshot.pinned(
                addressed, root, text, corpus.binding().sha256(), Optional.empty(), Optional.empty());
        var start = SFMTextDocumentRange.positionAtByteOffset(text, startByte);
        var end = SFMTextDocumentRange.positionAtByteOffset(text, endByte);
        SFMTextDocumentSelection exact = new SFMTextDocumentSelection(
                "primary", backward ? end : start, backward ? start : end, true);
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3", baseline, text, false, true, List.of(),
                List.of(new SFMContextSelectionProjection(
                        "primary", List.of(exact.orderedRange()), true, List.of(exact))));
        return SFMReleaseReviewEditorCapture.capture(
                new SFMClientActionContext(null, () -> true, null),
                new SFMContextSnapshot(1, 1, 1, Optional.empty(), List.of()),
                projection,
                document
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

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf(needle, offset)) >= 0; offset += needle.length()) {
            count++;
        }
        return count;
    }
}
