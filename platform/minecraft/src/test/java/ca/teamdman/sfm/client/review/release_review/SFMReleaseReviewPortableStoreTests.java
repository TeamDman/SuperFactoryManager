package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused portable-file and recovery-boundary evidence for RCS-6. */
class SFMReleaseReviewPortableStoreTests {
    private static final List<String> PORTABLE_QUERY_EXPRESSIONS = List.of(
            "#approved intersect 1.19.2 HEAD",
            "effective(#approved) intersect 1.19.2 HEAD",
            "remaining intersect 1.19.2 HEAD",
            "blocking intersect 1.19.2 HEAD",
            "suspended intersect 1.19.2 HEAD"
    );

    @Test
    void saveAsPreservesCanonicalContentAndMovesSubsequentAutosaves(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("docs/reviews/source.sfm-review.json");
        Path destination = directory.resolve("elsewhere/copied.sfm-review.json");
        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        try {
            runtime.create(source, fixture());
            String originalCanonical = Files.readString(source, StandardCharsets.UTF_8);
            assertEquals(SFMReleaseReviewV1Codec.write(runtime.document().orElseThrow()), originalCanonical);

            runtime.saveAs(destination);

            assertEquals(destination.toAbsolutePath().normalize(), runtime.path().orElseThrow());
            assertEquals(originalCanonical, Files.readString(destination, StandardCharsets.UTF_8));
            assertFalse(runtime.dirty());

            SFMReleaseReviewRuntime.MutationResult autosaved = runtime.saveNamedQuery(
                    "after-save-as",
                    "remaining intersect 1.19.2 HEAD",
                    true
            );
            assertTrue(autosaved.saved());
            assertFalse(runtime.dirty());
            assertEquals(originalCanonical, Files.readString(source, StandardCharsets.UTF_8),
                    "the original repository file must stop receiving autosaves after save-as");
            assertEquals(
                    SFMReleaseReviewV1Codec.write(runtime.document().orElseThrow()),
                    Files.readString(destination, StandardCharsets.UTF_8),
                    "the new explicit path must contain the current canonical envelope"
            );
            assertTrue(SFMReleaseReviewV1Codec.parse(Files.readString(destination, StandardCharsets.UTF_8))
                    .namedQueries().stream().anyMatch(query -> query.id().equals("after-save-as")));
        } finally {
            runtime.discardAndClose();
            deleteMachineLocalState(source);
            deleteMachineLocalState(destination);
        }
    }

    @Test
    void readOnlyMutationCannotChangePublishedStateAndSaveAsRemainsAvailable(@TempDir Path directory)
            throws Exception {
        Path source = directory.resolve("review.sfm-review.json");
        Path recovered = directory.resolve("recovered/review.sfm-review.json");
        createRepositoryFile(source);
        String authoritative = Files.readString(source, StandardCharsets.UTF_8);

        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        try {
            runtime.open(source, false);
            SFMReleaseReviewRuntime.Snapshot before = runtime.snapshot();
            SFMReleaseReviewRuntime.MutationResult failed = runtime.activateQuery(Optional.empty(), "HEAD");

            assertFalse(failed.saved());
            assertFalse(failed.dirty());
            assertFalse(runtime.dirty());
            assertTrue(failed.failure().orElseThrow().contains("code=review.read-only"));
            assertEquals(before.document(), runtime.snapshot().document());
            assertEquals(before.generation(), runtime.snapshot().generation());
            assertEquals(authoritative, Files.readString(source, StandardCharsets.UTF_8),
                    "read-only mutation must not change authoritative repository bytes");

            runtime.saveAs(recovered);

            assertFalse(runtime.dirty());
            assertEquals(recovered.toAbsolutePath().normalize(), runtime.path().orElseThrow());
            assertEquals(authoritative, Files.readString(source, StandardCharsets.UTF_8));
            SFMReleaseReviewV1 recoveredDocument = SFMReleaseReviewV1Codec.parse(
                    Files.readString(recovered, StandardCharsets.UTF_8));
            assertEquals(1, recoveredDocument.resumeState().generation(),
                    "save-as must preserve the previously published state at a new authority path");
            assertEquals(SFMReleaseReviewV1Codec.write(runtime.document().orElseThrow()),
                    Files.readString(recovered, StandardCharsets.UTF_8));
        } finally {
            runtime.discardAndClose();
            deleteMachineLocalState(source);
            deleteMachineLocalState(recovered);
        }
    }

    @Test
    void recoveredDirtyStateSurvivesRejectedMutationAndCanBeSavedExplicitly(@TempDir Path directory)
            throws Exception {
        Path source = directory.resolve("review.sfm-review.json");
        createRepositoryFile(source);
        String authoritative = Files.readString(source, StandardCharsets.UTF_8);
        Files.delete(source);

        SFMReleaseReviewRuntime runtime = new SFMReleaseReviewRuntime();
        try {
            SFMReleaseReviewRuntime.OpenResult opened = runtime.open(source, false);
            assertTrue(opened.recoveredMachineLocalCopy());
            assertTrue(runtime.dirty());
            SFMReleaseReviewRuntime.Snapshot before = runtime.snapshot();
            SFMReleaseReviewRuntime.MutationResult rejected = runtime.activateQuery(Optional.empty(), "HEAD");
            assertFalse(rejected.saved());
            assertTrue(rejected.dirty(), "the pre-existing recovery dirtiness must remain truthful");
            assertEquals(before.document(), runtime.snapshot().document());
            assertEquals(before.generation(), runtime.snapshot().generation());
            assertThrows(IllegalStateException.class, runtime::close);

            runtime.saveAs(source);
            assertFalse(runtime.dirty());
            assertEquals(authoritative, Files.readString(source, StandardCharsets.UTF_8));
        } finally {
            runtime.discardAndClose();
            deleteMachineLocalState(source);
        }
    }

    @Test
    void repositoryFileReopensAfterItsMachineLocalRecoveryAndLeaseFilesAreDeleted(@TempDir Path directory)
            throws Exception {
        Path repositoryFile = directory.resolve("docs/reviews/portable.sfm-review.json");
        PortableTruth expected = createPortableReview(repositoryFile);
        SFMReleaseReviewStore.MachineLocalState machineLocalState =
                SFMReleaseReviewStore.machineLocalState(repositoryFile);
        assertTrue(Files.isRegularFile(machineLocalState.recovery()));
        assertTrue(Files.isRegularFile(machineLocalState.writerLease()));

        Files.deleteIfExists(machineLocalState.recovery());
        Files.deleteIfExists(machineLocalState.writerLease());
        assertFalse(Files.exists(machineLocalState.recovery()));
        assertFalse(Files.exists(machineLocalState.writerLease()));

        SFMReleaseReviewRuntime reopened = new SFMReleaseReviewRuntime();
        try {
            SFMReleaseReviewRuntime.OpenResult result = reopened.open(repositoryFile, false);
            assertTrue(result.document().isPresent());
            assertFalse(result.recoveredMachineLocalCopy());
            assertFalse(reopened.dirty());
            assertPortableTruth(expected, repositoryFile, reopened);
            assertFalse(Files.exists(machineLocalState.recovery()),
                    "read-only reopen must not recreate dispensable recovery state");
            assertFalse(Files.exists(machineLocalState.writerLease()),
                    "read-only reopen must not recreate a writer lease");
        } finally {
            reopened.discardAndClose();
            deleteMachineLocalState(repositoryFile);
        }
    }

    @Test
    void truncatedCanonicalWriteRecoversTheLastValidMachineLocalCopyFailClosed(@TempDir Path directory)
            throws Exception {
        Path repositoryFile = directory.resolve("docs/reviews/interrupted.sfm-review.json");
        Path restoredFile = directory.resolve("recovered-checkout/docs/reviews/interrupted.sfm-review.json");
        PortableTruth expected = createPortableReview(repositoryFile);
        SFMReleaseReviewStore.MachineLocalState machineLocalState =
                SFMReleaseReviewStore.machineLocalState(repositoryFile);
        assertEquals(expected.canonical(), Files.readString(machineLocalState.recovery(), StandardCharsets.UTF_8));

        String interruptedBytes = "{\n  \"schema\": \"sfm.release-review/1\",\n  \"review_session\":";
        Files.writeString(repositoryFile, interruptedBytes, StandardCharsets.UTF_8);

        SFMReleaseReviewRuntime recovered = new SFMReleaseReviewRuntime();
        try {
            SFMReleaseReviewRuntime.OpenResult result = recovered.open(repositoryFile, false);

            assertTrue(result.recoveredMachineLocalCopy());
            assertTrue(recovered.dirty(), "recovery bytes are not authoritative until explicitly saved");
            assertTrue(result.diagnostics().stream().anyMatch(message ->
                    message.contains("Invalid repository file")));
            assertTrue(result.diagnostics().stream().anyMatch(message ->
                    message.contains("Recovered machine-local bytes")));
            assertRuntimeTruth(expected, recovered);
            assertThrows(IllegalStateException.class, recovered::close,
                    "dirty recovered state must not be silently discarded");

            recovered.saveAs(restoredFile);

            assertFalse(recovered.dirty());
            assertEquals(interruptedBytes, Files.readString(repositoryFile, StandardCharsets.UTF_8),
                    "recovery must not silently overwrite the damaged authority");
            assertPortableTruth(expected, restoredFile, recovered);
        } finally {
            recovered.discardAndClose();
            deleteMachineLocalState(repositoryFile);
            deleteMachineLocalState(restoredFile);
        }
    }

    @Test
    void canonicalBytesReopenEquivalentlyFromADifferentCheckoutPath(@TempDir Path directory) throws Exception {
        Path firstCheckout = directory.resolve("checkout-one");
        Path secondCheckout = directory.resolve("checkout-two");
        Path firstReview = firstCheckout.resolve("docs/reviews/release.sfm-review.json");
        Path secondReview = secondCheckout.resolve("docs/reviews/release.sfm-review.json");
        PortableTruth expected = createPortableReview(firstReview);
        byte[] canonicalBytes = Files.readAllBytes(firstReview);

        Files.createDirectories(secondReview.getParent());
        Files.copy(firstReview, secondReview, StandardCopyOption.REPLACE_EXISTING);
        deleteMachineLocalState(firstReview);
        deleteMachineLocalState(secondReview);
        assertArrayEquals(canonicalBytes, Files.readAllBytes(secondReview));
        assertFalse(SFMReleaseReviewStore.machineLocalState(firstReview).recovery().equals(
                SFMReleaseReviewStore.machineLocalState(secondReview).recovery()),
                "checkout-specific recovery keys must not be required for portable truth");

        SFMReleaseReviewRuntime reopened = new SFMReleaseReviewRuntime();
        try {
            SFMReleaseReviewRuntime.OpenResult result = reopened.open(secondReview, false);

            assertTrue(result.document().isPresent());
            assertFalse(result.recoveredMachineLocalCopy());
            assertFalse(reopened.dirty());
            assertEquals(secondReview.toAbsolutePath().normalize(), reopened.path().orElseThrow());
            assertPortableTruth(expected, secondReview, reopened);
            assertArrayEquals(canonicalBytes, Files.readAllBytes(secondReview));
        } finally {
            reopened.discardAndClose();
            deleteMachineLocalState(firstReview);
            deleteMachineLocalState(secondReview);
        }
    }

    private static void createRepositoryFile(Path path) throws Exception {
        SFMReleaseReviewRuntime writer = new SFMReleaseReviewRuntime();
        try {
            writer.create(path, fixture());
        } finally {
            writer.discardAndClose();
        }
    }

    private static PortableTruth createPortableReview(Path path) throws Exception {
        try (SFMReleaseReviewRuntime writer = new SFMReleaseReviewRuntime()) {
            writer.create(path, fixture());
            assertTrue(writer.saveNamedQuery(
                    "portable-proof",
                    "remaining intersect 1.19.2 HEAD",
                    true
            ).saved());
            assertTrue(writer.deferCurrent().saved(), "the fixture must retain non-trivial resumable progress");
        }
        return portableTruth(path);
    }

    private static void deleteMachineLocalState(Path path) throws Exception {
        SFMReleaseReviewStore.MachineLocalState state = SFMReleaseReviewStore.machineLocalState(path);
        Files.deleteIfExists(state.recovery());
        Files.deleteIfExists(state.writerLease());
    }

    private static PortableTruth portableTruth(Path path) throws Exception {
        String canonical = Files.readString(path, StandardCharsets.UTF_8);
        SFMReleaseReviewV1 document = SFMReleaseReviewV1Codec.parse(canonical);
        LinkedHashMap<String, SFMReleaseReviewKernel.QueryResult> queries = new LinkedHashMap<>();
        for (String expression : PORTABLE_QUERY_EXPRESSIONS) {
            queries.put(expression, SFMReleaseReviewKernel.query(document, expression));
        }
        return new PortableTruth(
                canonical,
                document,
                queries,
                document.resumeState(),
                SFMReleaseReviewKernel.completion(document)
        );
    }

    private static void assertPortableTruth(
            PortableTruth expected,
            Path path,
            SFMReleaseReviewRuntime runtime
    ) throws Exception {
        assertEquals(expected.canonical(), Files.readString(path, StandardCharsets.UTF_8),
                "canonical bytes must be unchanged");
        assertRuntimeTruth(expected, runtime);
    }

    private static void assertRuntimeTruth(PortableTruth expected, SFMReleaseReviewRuntime runtime) {
        SFMReleaseReviewV1 actual = runtime.document().orElseThrow();
        assertEquals(expected.document(), actual, "the complete portable model must be equivalent");
        assertEquals(expected.progress(), actual.resumeState(), "resume progress must be equivalent");
        for (Map.Entry<String, SFMReleaseReviewKernel.QueryResult> query : expected.queries().entrySet()) {
            assertEquals(query.getValue(), runtime.query(query.getKey()),
                    "query result must be equivalent for " + query.getKey());
        }
        assertEquals(expected.completion(), runtime.status(), "completion evidence must be equivalent");
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

    private record PortableTruth(
            String canonical,
            SFMReleaseReviewV1 document,
            Map<String, SFMReleaseReviewKernel.QueryResult> queries,
            SFMReleaseReviewV1.ResumeState progress,
            SFMReleaseReviewKernel.CompletionReport completion
    ) {
        private PortableTruth {
            queries = Map.copyOf(queries);
        }
    }
}
