package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewLedgerStoreTests {
    @TempDir Path temporary;

    @Test void installedCompanionRetainsDisplayedBytesAfterDiskChanges() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("true".equals(System.getenv("SFM_TEST_LEDGER_COMPANION")));
        run("git", "init", "--quiet");
        run("git", "config", "user.name", "Disposable review test");
        run("git", "config", "user.email", "review-test@example.invalid");
        Path source = temporary.resolve("A.java");
        Files.writeString(source, "class A { int value = 1; }\n");
        run("git", "add", "A.java");
        run("git", "commit", "--quiet", "-m", "Disposable baseline");
        String baseline = run("git", "rev-parse", "HEAD").trim();
        String displayed = "class A { int value = 2; }\n";
        Files.writeString(source, displayed);
        Path file = temporary.resolve("test.sfm-review.json");
        var ledger = SFMReleaseReviewLedgerV3.create("integration-review", "Integration", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane("1.19.2", "sfm", ".", baseline,
                        Optional.empty(), List.of("."), List.of("test.sfm-review.json"), true)));
        String initial = SFMReleaseReviewLedgerV3Codec.write(ledger);
        Files.writeString(file, initial);
        String capturedId;
        try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.WRITABLE)) {
            var loaded = store.load();
            assertTrue(loaded.document().isPresent(), loaded.diagnostics().toString());
            assertEquals(initial, Files.readString(file));
            var base = loaded.document().orElseThrow();
            var body = base.reviewSession().revisionLanes().stream().flatMap(lane -> lane.after().documents().stream())
                    .filter(document -> document.path().equals("A.java")).findFirst().orElseThrow();
            capturedId = body.id();
            assertEquals(displayed, body.text());
            int length = displayed.getBytes(StandardCharsets.UTF_8).length;
            var selected = new SFMReleaseReviewV1.PinnedSelection("integration-selection", "explicit", 0, List.of(
                    new SFMReleaseReviewV1.PinnedSelectionRange(SFMReleaseReviewV1.SelectionDirection.FORWARD,
                            body.id(), body.sha256(), 0, length)));
            var rule = new SFMReviewSessionV1.LiteralUtf8Range(body.id(), 0, length, body.sha256(), body.sha256());
            var proposal = new SFMReleaseReviewV1.SelectorProposal("integration-proposal", SFMReleaseReviewV1.SelectorKind.LITERAL,
                    rule, selected, Optional.empty(), Optional.empty(), List.of(), SFMReleaseReviewV1.ProposalConfidence.EXACT,
                    "0".repeat(64), "observation", List.of());
            var human = new SFMReviewSessionV2.Comment("human:integration", "#approved",
                    new SFMReviewSessionV1.Provenance("human", "test", "1", List.of()),
                    new SFMReviewSessionV2.CommittedReviewTarget(rule));
            var comments = new java.util.ArrayList<>(base.reviewSession().comments());
            comments.add(human);
            Files.writeString(source, "class A { int value = 3; }\n");
            store.save(withComments(base, comments, List.of(
                    new SFMReleaseReviewV1.CommentSelectorBinding(human.id(), selected, proposal))), loaded.openedContentHash());
        }
        var saved = SFMReleaseReviewLedgerV3Codec.parse(Files.readString(file));
        assertEquals(1, saved.evidence().contents().size());
        assertEquals(displayed, saved.evidence().contents().get(0).text());
        assertEquals(1, saved.state().reviewSession().comments().size());
        String durableBytes = Files.readString(file);
        try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.READ_ONLY)) {
            var reopened = store.load();
            assertTrue(reopened.document().isPresent(), reopened.diagnostics().toString());
            var observation = reopened.document().orElseThrow();
            assertTrue(observation.reviewSession().revisionLanes().stream()
                    .flatMap(lane -> lane.after().documents().stream())
                    .anyMatch(body -> body.id().equals(capturedId) && body.text().equals(displayed)));
            assertTrue(observation.reviewSession().revisionLanes().stream()
                    .flatMap(lane -> lane.after().documents().stream())
                    .anyMatch(body -> body.text().contains("value = 3")));
            assertHistoricalApproval(observation, true);
        }
        // An insertion before the approved bytes, a removed field, or an unrelated
        // edit in the same document creates a new identity; none relocates approval.
        for (String changed : List.of("// inserted before\n" + displayed,
                "class A {}\n", displayed + "// unrelated same-file edit\n")) {
            Files.writeString(source, changed);
            try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.READ_ONLY)) {
                var reopened = store.load();
                assertTrue(reopened.document().isPresent(), reopened.diagnostics().toString());
                assertHistoricalApproval(reopened.document().orElseThrow(), true);
            }
            assertEquals(durableBytes, Files.readString(file));
        }
        // Restoring the exact original snapshot restores its exact document identity.
        Files.writeString(source, displayed);
        try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.READ_ONLY)) {
            var reopened = store.load();
            assertTrue(reopened.document().isPresent(), reopened.diagnostics().toString());
            assertHistoricalApproval(reopened.document().orElseThrow(), false);
        }
        // The capture contract includes the whole snapshot ID. Identical file bytes
        // in a different snapshot are evidence for explicit migration, not permission
        // to silently transfer an approval. Explain this instead of hiding the distinction.
        Files.writeString(temporary.resolve("B.java"), "class B {}\n");
        try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.READ_ONLY)) {
            var reopened = store.load();
            assertTrue(reopened.document().isPresent(), reopened.diagnostics().toString());
            var observation = reopened.document().orElseThrow();
            assertHistoricalApproval(observation, true);
            assertTrue(SFMReleaseReviewKernel.approvalEvidence(observation).stream()
                    .anyMatch(value -> value.commentId().equals("human:integration")
                            && value.explanation().contains("identical bytes exist")));
        }
        Files.delete(source);
        try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.READ_ONLY)) {
            var reopened = store.load();
            assertTrue(reopened.document().isPresent(), reopened.diagnostics().toString());
            assertHistoricalApproval(reopened.document().orElseThrow(), true);
        }
        assertEquals(durableBytes, Files.readString(file));
        // Explicitly accept a uniquely relocated literal witness. Preserve the original
        // approval and capture the new destination only at this human mutation boundary.
        Files.writeString(source, "// inserted before\n" + displayed);
        String successorId;
        var runtime = new SFMReleaseReviewRuntime(Runnable::run);
        try {
            assertTrue(runtime.open(file, true).document().isPresent());
            var context = runtime.captureMigrationContext();
            var preview = SFMReviewMigrationPlan.preview(context.observation(), "human:integration", "1.19.2",
                    SFMReviewMigrationPreview.Limits.DEFAULT);
            assertTrue(preview.canAccept(), preview.toString());
            assertEquals(SFMReviewMigrationPreview.Status.RELOCATED, preview.ranges().get(0).status());
            assertEquals(durableBytes, Files.readString(file), "preview must not retain candidate content");
            String externalBytes = durableBytes + "\n";
            Files.writeString(file, externalBytes);
            var conflict = runtime.acceptMigrationSuccessorAsync(context, preview, "Must not overwrite an external edit").join();
            assertFalse(conflict.mutation().saved());
            assertEquals(externalBytes, Files.readString(file));
            assertEquals(context.lease(), runtime.snapshot(), "failed acceptance must not publish successor state");
            // This is our disposable conflict injection; restoring exact authority permits retry.
            Files.writeString(file, durableBytes);
            var accepted = runtime.acceptMigrationSuccessorAsync(context, preview, "Reviewed the relocated bytes").join();
            assertTrue(accepted.mutation().saved(), accepted.mutation().toString());
            successorId = accepted.commentId();
            String acceptedBytes = Files.readString(file);
            var stale = runtime.acceptMigrationSuccessorAsync(context, preview, "Stale second acceptance").join();
            assertFalse(stale.mutation().saved());
            assertEquals(acceptedBytes, Files.readString(file));
        } finally { runtime.close(); }
        var acceptedLedger = SFMReleaseReviewLedgerV3Codec.parse(Files.readString(file));
        assertEquals(2, acceptedLedger.evidence().contents().size());
        assertTrue(acceptedLedger.state().reviewSession().comments().containsAll(saved.state().reviewSession().comments()));
        assertTrue(acceptedLedger.state().selectorBindings().containsAll(saved.state().selectorBindings()));
        try (var store = SFMReleaseReviewStore.open(file, SFMReleaseReviewStore.Access.READ_ONLY)) {
            var reopened = store.load().document().orElseThrow();
            var successor = reopened.reviewSession().comments().stream().filter(c -> c.id().equals(successorId)).findFirst().orElseThrow();
            assertEquals(List.of("human:integration"), successor.provenance().parentCommentIds());
            assertFalse(SFMReleaseReviewKernel.approvalEvidence(reopened).stream()
                    .filter(e -> e.commentId().equals(successorId)).findFirst().orElseThrow().effectiveCurrentRanges().isEmpty());
            assertHistoricalApproval(reopened, true);
        }
    }

    private static void assertHistoricalApproval(SFMReleaseReviewV1 observation, boolean historical) {
        var evidence = SFMReleaseReviewKernel.approvalEvidence(observation).stream()
                .filter(value -> value.commentId().equals("human:integration")).findFirst().orElseThrow();
        assertEquals(historical, !evidence.historicalRanges().isEmpty());
        assertEquals(historical, evidence.effectiveCurrentRanges().isEmpty());
        var completion = SFMReleaseReviewKernel.completion(observation);
        assertEquals(completion.witnesses().remaining(), SFMReleaseReviewKernel.query(observation, "remaining").reviewUnitIds());
        var attributed = ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel.normalize(
                SFMReleaseReviewKernel.approvalEvidence(observation).stream()
                        .flatMap(value -> value.effectiveCurrentRanges().stream()).toList());
        var canonical = ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel.normalize(
                completion.surfaceCoverage().stream().flatMap(value -> value.approved().stream()).toList());
        assertEquals(canonical, attributed);
    }

    private String run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(temporary.toFile()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS), "Disposable Git command timed out");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            return output;
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    @Test void commentAndExactEvidenceCommitTogetherAndFailedSaveDoesNotAdvanceAuthority() throws Exception {
        var fixture = SFMReleaseReviewV1Codec.parse(fixture());
        var base = withComments(fixture, List.of(), List.of());
        var target = base.repositoryBindings().get(0);
        var ledger = SFMReleaseReviewLedgerV3.create(base.reviewSession().id(), base.reviewSession().title(), List.of(
                new SFMReleaseReviewLedgerV3.TargetLane(target.laneId(), target.repositoryId(), ".", target.beforeCommit(),
                        Optional.empty(), List.of("."), List.of("test.sfm-review.json"), true)));
        Path path = temporary.resolve("test.sfm-review.json");
        String initial = SFMReleaseReviewLedgerV3Codec.write(ledger);
        Files.writeString(path, initial);
        var body = base.reviewSession().revisionLanes().get(0).after().documents().get(0);
        int length = body.text().getBytes(StandardCharsets.UTF_8).length;
        var observed = new SFMReviewEvidenceTable.Observed(new SFMReviewEvidenceTable.Document(
                body.id(), body.path(), body.sha256(), Optional.empty()), body.text());
        var sources = Map.of(body.id(), observed);
        SFMReleaseReviewLedgerResolver resolver = (file, authority) -> {
            var saved = SFMReleaseReviewLedgerV3Codec.parse(authority);
            return new SFMReleaseReviewLedgerResolver.Resolved(withComments(base,
                    saved.state().reviewSession().comments(), saved.state().selectorBindings()), sources);
        };
        var selected = new SFMReleaseReviewV1.PinnedSelection("selected", "explicit", 0, List.of(
                new SFMReleaseReviewV1.PinnedSelectionRange(SFMReleaseReviewV1.SelectionDirection.FORWARD,
                        body.id(), body.sha256(), 0, length)));
        var rule = new SFMReviewSessionV1.LiteralUtf8Range(body.id(), 0, length, body.sha256(), body.sha256());
        var proposal = new SFMReleaseReviewV1.SelectorProposal("proposal", SFMReleaseReviewV1.SelectorKind.LITERAL,
                rule, selected, Optional.empty(), Optional.empty(), List.of(), SFMReleaseReviewV1.ProposalConfidence.EXACT,
                "0".repeat(64), "observation", List.of());
        var human = new SFMReviewSessionV2.Comment("human:save", "#approved",
                new SFMReviewSessionV1.Provenance("human", "test", "1", List.of()),
                new SFMReviewSessionV2.CommittedReviewTarget(rule));
        var edited = withComments(base, List.of(human), List.of(
                new SFMReleaseReviewV1.CommentSelectorBinding(human.id(), selected, proposal)));
        try (var store = SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.WRITABLE, resolver)) {
            var loaded = store.load();
            assertTrue(loaded.document().isPresent(), loaded.diagnostics().toString());
            assertEquals(initial, Files.readString(path), "opening must not materialize durable bytes");
            var otherTarget = new SFMReleaseReviewLedgerV3.TargetLane(target.laneId(), target.repositoryId(), ".",
                    "b".repeat(40), Optional.empty(), List.of("."), List.of("test.sfm-review.json"), true);
            Files.writeString(path, SFMReleaseReviewLedgerV3Codec.write(new SFMReleaseReviewLedgerV3(
                    List.of(otherTarget), ledger.state(), ledger.evidence())));
            assertTrue(store.stageLoad().document().isPresent());
            // Cancelled staging is deliberately not accepted; restore the original external bytes.
            Files.writeString(path, initial);
            assertThrows(IllegalStateException.class, () -> store.save(edited, loaded.openedContentHash(),
                    () -> { throw new IllegalStateException("injected before replacement"); }));
            assertEquals(initial, Files.readString(path));
            var saved = store.save(edited, loaded.openedContentHash());
            var durable = SFMReleaseReviewLedgerV3Codec.parse(Files.readString(path));
            assertEquals(ledger.targets(), durable.targets(), "unpublished staged load cannot change save authority");
            assertEquals(List.of(human), durable.state().reviewSession().comments());
            assertEquals(body.text(), durable.evidence().contents().get(0).text());
            assertEquals(1, durable.evidence().contents().size());
            assertThrows(IllegalArgumentException.class, () -> new SFMReleaseReviewLedgerV3(
                    durable.targets(), durable.state(), SFMReviewEvidenceTable.EMPTY),
                    "a saved comment cannot silently rely on the current disk for missing evidence");
            assertTrue(durable.state().reviewSession().revisionLanes().isEmpty());
            var same = store.save(edited, Optional.of(saved.contentHash()));
            assertEquals(saved.contentHash(), same.contentHash());
            Files.writeString(path, Files.readString(path) + "\n");
            assertThrows(SFMReleaseReviewStore.ExternalEditConflict.class,
                    () -> store.save(edited, Optional.of(same.contentHash())));
        }
        try (var reopened = SFMReleaseReviewStore.open(path, SFMReleaseReviewStore.Access.READ_ONLY, resolver)) {
            var loaded = reopened.load();
            assertTrue(loaded.document().isPresent(), loaded.diagnostics().toString());
            assertEquals(List.of(human), loaded.document().orElseThrow().reviewSession().comments());
        }
    }

    private static SFMReleaseReviewV1 withComments(SFMReleaseReviewV1 base, List<SFMReviewSessionV2.Comment> comments,
                                                  List<SFMReleaseReviewV1.CommentSelectorBinding> bindings) {
        var s = base.reviewSession();
        var session = new SFMReviewSessionV2(s.schema(), s.id(), s.title(), s.coordinateSystem(), s.revisionLanes(),
                comments, s.styleRules(), s.completionPolicy());
        return new SFMReleaseReviewV1(base.schema(), session, base.repositoryBindings(), base.corpusDocuments(),
                base.reviewUnits(), bindings, List.of(), List.of(), SFMReleaseReviewV1.ResumeState.empty(),
                base.producerGenerations(), List.of());
    }

    private static String fixture() throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path file = root.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(file)) return Files.readString(file);
        }
        throw new IllegalStateException("Review fixture not found");
    }
}
