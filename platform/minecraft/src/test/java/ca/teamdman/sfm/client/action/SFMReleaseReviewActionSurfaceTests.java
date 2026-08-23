package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewStore;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused action-discovery and fail-closed attestation evidence for RCS-6/RCS-8. */
class SFMReleaseReviewActionSurfaceTests {
    private static final SFMClientActionContext CURRENT_CONTEXT =
            new SFMClientActionContext(null, () -> true, null);

    @AfterEach
    void closeSharedRuntime() {
        SFMReleaseReviewRuntime.get().discardAndClose();
    }

    @Test
    void attestIsAvailableOnlyForAnOpenExactlyReadyReview(@TempDir Path directory) throws Exception {
        SFMReleaseReviewAction action = new SFMReleaseReviewAction(SFMReleaseReviewAction.Kind.ATTEST);
        assertFalse(action.requirement().resolve(CURRENT_CONTEXT).isAvailable(),
                "attestation must be unavailable when no release review is open");

        SFMReleaseReviewV1 inProgress = fixture();
        assertAvailability(directory, "in-progress", inProgress,
                SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS, false, action);

        SFMReleaseReviewV1 ready = readyFixture(inProgress);
        assertAvailability(directory, "ready", ready,
                SFMReleaseReviewKernel.CompletionStatus.READY_FOR_MAINTAINER_ATTESTATION, true, action);

        String readyHash = SFMReleaseReviewKernel.semanticStateHash(ready);
        SFMReleaseReviewV1 complete = withAttestations(ready, List.of(new SFMReleaseReviewV1.CompletionAttestation(
                "complete-attestation",
                readyHash,
                "fixture-maintainer",
                "2026-08-23T00:00:00Z",
                "Fixture completion evidence"
        )));
        assertAvailability(directory, "complete", complete,
                SFMReleaseReviewKernel.CompletionStatus.COMPLETE, false, action);

        SFMReleaseReviewV1 stale = withAttestations(ready, List.of(new SFMReleaseReviewV1.CompletionAttestation(
                "stale-attestation",
                "0".repeat(64),
                "fixture-maintainer",
                "2026-08-23T00:00:00Z",
                "Intentionally stale fixture evidence"
        )));
        assertAvailability(directory, "stale", stale,
                SFMReleaseReviewKernel.CompletionStatus.STALE, false, action);
    }

    @Test
    void portableReviewActionsRetainCanonicalPathsRegistrationFieldsAndGrammar() throws Exception {
        assertEquals("review/session/create", SFMReleaseReviewAction.Kind.CREATE.path());
        assertEquals("review/session/open", SFMReleaseReviewAction.Kind.OPEN.path());
        assertEquals("review/session/open/read_only", SFMReleaseReviewAction.Kind.OPEN_READ_ONLY.path());
        assertEquals("review/session/save", SFMReleaseReviewAction.Kind.SAVE.path());
        assertEquals("review/session/save/as", SFMReleaseReviewAction.Kind.SAVE_AS.path());
        assertEquals("review/session/query/save", SFMReleaseReviewAction.Kind.QUERY_SAVE.path());

        assertEquals(SFMReleaseReviewAction.Kind.values().length,
                new HashSet<>(java.util.Arrays.stream(SFMReleaseReviewAction.Kind.values())
                        .map(SFMReleaseReviewAction.Kind::path).toList()).size(),
                "release-review action paths must remain unique and discoverable");

        for (String field : List.of(
                "RELEASE_CREATE", "RELEASE_OPEN", "RELEASE_OPEN_READ_ONLY", "RELEASE_SAVE", "RELEASE_SAVE_AS",
                "RELEASE_QUERY_SAVE", "RELEASE_SELECT", "RELEASE_ATTEST")) {
            assertNotNull(SFMReviewActions.class.getDeclaredField(field), field + " must remain registered");
        }

        assertTrue(executable(SFMReleaseReviewAction.Kind.CREATE,
                "action \"docs/reviews/release.sfm-review.json\" 1.19.2 4.34.0-1.19.2 HEAD"));
        assertTrue(executable(SFMReleaseReviewAction.Kind.CREATE,
                "action \"docs/reviews/release.sfm-review.json\" 1.19.2 4.34.0-1.19.2 HEAD \"D:/repo\""));
        assertTrue(executable(SFMReleaseReviewAction.Kind.OPEN,
                "action D:/repo/docs/reviews/release.sfm-review.json"));
        assertTrue(executable(SFMReleaseReviewAction.Kind.OPEN_READ_ONLY,
                "action D:/repo/docs/reviews/release.sfm-review.json"));
        assertTrue(executable(SFMReleaseReviewAction.Kind.SAVE, "action"));
        assertTrue(executable(SFMReleaseReviewAction.Kind.SAVE_AS,
                "action D:/repo/docs/reviews/copy.sfm-review.json"));
        assertTrue(executable(SFMReleaseReviewAction.Kind.QUERY_SAVE,
                "action remaining-review remaining intersect 1.19.2 HEAD"));
        assertTrue(executable(SFMReleaseReviewAction.Kind.SELECT,
                "action \"unit:1.19.2:src/Example.java:hunk:1\""));

        assertFalse(executable(SFMReleaseReviewAction.Kind.CREATE,
                "action \"docs/reviews/release.sfm-review.json\" 1.19.2 4.34.0-1.19.2"));
        assertFalse(executable(SFMReleaseReviewAction.Kind.OPEN, "action"));
        assertFalse(executable(SFMReleaseReviewAction.Kind.SAVE_AS, "action"));
        assertFalse(executable(SFMReleaseReviewAction.Kind.QUERY_SAVE, "action remaining-review"));
        assertFalse(executable(SFMReleaseReviewAction.Kind.SELECT, "action"));
    }

    private static void assertAvailability(
            Path directory,
            String name,
            SFMReleaseReviewV1 document,
            SFMReleaseReviewKernel.CompletionStatus expectedStatus,
            boolean expectedAvailable,
            SFMReleaseReviewAction action
    ) throws Exception {
        Path path = directory.resolve(name + ".sfm-review.json");
        Files.writeString(path, SFMReleaseReviewV1Codec.write(document), StandardCharsets.UTF_8);
        SFMReleaseReviewRuntime runtime = SFMReleaseReviewRuntime.get();
        try {
            runtime.open(path, true);
            assertEquals(expectedStatus, runtime.status().status());
            SFMClientActionAvailability<SFMClientActionContext> availability =
                    action.requirement().resolve(CURRENT_CONTEXT);
            assertEquals(expectedAvailable, availability.isAvailable(),
                    "unexpected attestation availability for " + expectedStatus);
            if (!expectedAvailable) {
                assertFalse(availability.unavailableReason().getString().isBlank());
            }
        } finally {
            runtime.discardAndClose();
            deleteMachineLocalState(path);
        }
    }

    private static SFMReleaseReviewV1 readyFixture(SFMReleaseReviewV1 source) {
        ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>(source.reviewSession().comments());
        SFMReviewSessionV2.Comment uncovered = comments.get(2);
        comments.set(2, new SFMReviewSessionV2.Comment(
                uncovered.id(),
                "#approved Reviewed the added fallback file surface.",
                uncovered.provenance(),
                uncovered.target()
        ));
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                source.reviewSession().schema(),
                source.reviewSession().id(),
                source.reviewSession().title(),
                source.reviewSession().coordinateSystem(),
                source.reviewSession().revisionLanes(),
                comments,
                source.reviewSession().styleRules(),
                source.reviewSession().completionPolicy()
        );
        return new SFMReleaseReviewV1(
                source.schema(), session, source.repositoryBindings(), source.corpusDocuments(),
                source.reviewUnits(), source.selectorBindings(), source.migrationReports(), source.namedQueries(),
                source.resumeState(), source.producerGenerations(), source.completionAttestations()
        );
    }

    private static SFMReleaseReviewV1 withAttestations(
            SFMReleaseReviewV1 source,
            List<SFMReleaseReviewV1.CompletionAttestation> attestations
    ) {
        return new SFMReleaseReviewV1(
                source.schema(), source.reviewSession(), source.repositoryBindings(), source.corpusDocuments(),
                source.reviewUnits(), source.selectorBindings(), source.migrationReports(), source.namedQueries(),
                source.resumeState(), source.producerGenerations(), attestations
        );
    }

    private static boolean executable(SFMReleaseReviewAction.Kind kind, String command) {
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal("action");
        new SFMReleaseReviewAction(kind).configureCommandNode(node);
        dispatcher.register(node);
        ParseResults<SFMClientActionSource> parsed = dispatcher.parse(
                command,
                new SFMClientActionSource(CURRENT_CONTEXT)
        );
        return SFMClientActionExecutor.isExecutable(parsed);
    }

    private static void deleteMachineLocalState(Path path) throws Exception {
        Path recovery;
        Path writerLease;
        try (SFMReleaseReviewStore store = SFMReleaseReviewStore.open(
                path, SFMReleaseReviewStore.Access.READ_ONLY)) {
            recovery = store.recoveryPath();
            writerLease = store.writerLockPath();
        }
        Files.deleteIfExists(recovery);
        Files.deleteIfExists(writerLease);
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
