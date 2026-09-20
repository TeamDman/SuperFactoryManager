package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewSemanticProviderRegistryTests {
    @Test
    void freezesProviderIdsOrdersSnapshotsAndBoundsCapacity() {
        SFMReleaseReviewSemanticProviderRegistry registry = new SFMReleaseReviewSemanticProviderRegistry();
        AtomicReference<String> mutableId = new AtomicReference<>("test:z-provider");
        var mutable = provider(mutableId::get, (selection, documents) -> List.of());
        var z = registry.register(mutable);
        var a = registry.register(provider("test:a-provider", (selection, documents) -> List.of()));
        try {
            mutableId.set("test:changed-after-registration");
            assertEquals(List.of("test:a-provider", "test:z-provider"), ids(registry.snapshot()));
            assertThrows(UnsupportedOperationException.class, () -> registry.snapshot().add(mutable));
            assertThrows(IllegalArgumentException.class, () -> registry.register(
                    provider("test:a-provider", (selection, documents) -> List.of())));
            assertTrue(z.active());
            z.close();
            assertFalse(z.active());
            z.close();
            assertEquals(List.of("test:a-provider"), ids(registry.snapshot()));
        } finally {
            z.close();
            a.close();
        }
        assertEquals(0, registry.size());

        ArrayList<SFMReleaseReviewSemanticProviderRegistry.Registration> bounded = new ArrayList<>();
        try {
            for (int index = 0; index < SFMReleaseReviewSemanticProviderRegistry.MAX_PROVIDERS; index++) {
                String id = "test:bounded-" + index;
                bounded.add(registry.register(provider(id, (selection, documents) -> List.of())));
            }
            assertThrows(IllegalStateException.class, () -> registry.register(
                    provider("test:bounded-overflow", (selection, documents) -> List.of())));
        } finally {
            bounded.forEach(SFMReleaseReviewSemanticProviderRegistry.Registration::close);
        }
        assertEquals(0, registry.size());
    }

    @Test
    void supportsConcurrentRegistrationsReadsAndUnregistration() throws Exception {
        SFMReleaseReviewSemanticProviderRegistry registry = new SFMReleaseReviewSemanticProviderRegistry();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        ArrayList<Future<SFMReleaseReviewSemanticProviderRegistry.Registration>> registrations = new ArrayList<>();
        ArrayList<Future<?>> readers = new ArrayList<>();
        ArrayList<SFMReleaseReviewSemanticProviderRegistry.Registration> handles = new ArrayList<>();
        try {
            for (int index = 0; index < 24; index++) {
                String id = "test:concurrent-" + String.format("%02d", index);
                registrations.add(executor.submit(() -> {
                    start.await();
                    return registry.register(provider(id, (selection, documents) -> List.of()));
                }));
            }
            for (int reader = 0; reader < 4; reader++) {
                readers.add(executor.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 100; iteration++) {
                        List<String> observed = ids(registry.snapshot());
                        ArrayList<String> sorted = new ArrayList<>(observed);
                        sorted.sort(String::compareTo);
                        assertEquals(sorted, observed);
                        assertEquals(observed.size(), new HashSet<>(observed).size());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<SFMReleaseReviewSemanticProviderRegistry.Registration> registration : registrations) {
                handles.add(registration.get());
            }
            for (Future<?> reader : readers) reader.get();
            assertEquals(24, registry.size());
            assertEquals(ids(registry.snapshot()).stream().sorted().toList(), ids(registry.snapshot()));
        } finally {
            handles.forEach(SFMReleaseReviewSemanticProviderRegistry.Registration::close);
            executor.shutdownNow();
        }
        assertEquals(0, registry.size());
    }

    @Test
    void captureCombinesRegisteredEvidenceDiagnosesFailureAndKeepsLiteralAuthority() throws Exception {
        String documentRevision = "1.19.2:after:src/Cafe.java";
        var body = provider("test:release-review/body", (selection, documents) -> List.of(
                new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence(
                        SFMReleaseReviewV1.SelectorKind.BODY,
                        "fixture.Café#value():body",
                        List.of(new SFMReleaseReviewV1.AddressedRange(documentRevision, 35, 60)),
                        List.of(new SFMReleaseReviewV1.Evidence("authority", "focused registry test")),
                        SFMReleaseReviewV1.ProposalConfidence.EXACT,
                        "a".repeat(64),
                        "snapshot:focused-registry-test",
                        List.of()
                )
        ));
        var failed = provider("test:release-review/failed", (selection, documents) -> {
            throw new IllegalStateException("fixture provider offline");
        });

        try (var ignoredBody = SFMReleaseReviewSemanticProviderRegistry.global().register(body);
             var ignoredFailure = SFMReleaseReviewSemanticProviderRegistry.global().register(failed)) {
            SFMReleaseReviewEditorCapture.Capture capture = editorCapture(
                    fixture(), documentRevision, 35, 60);

            assertEquals(SFMReleaseReviewV1.SelectorKind.LITERAL, capture.proposals().proposals().get(0).kind());
            SFMReleaseReviewV1.SelectorProposal semantic = capture.proposals().proposals().stream()
                    .filter(value -> value.semanticProvider().orElse("").equals("test:release-review/body"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(SFMReleaseReviewV1.SelectorKind.BODY, semantic.kind());
            assertEquals(capture.adapted().pinnedSelection(), semantic.literalWitness());
            assertTrue(capture.diagnostics().stream().anyMatch(value ->
                    value.contains("review.semantic-provider-failed [test:release-review/failed]")
                            && value.contains("fixture provider offline")));
        }

        var registeredCollision = provider(
                SFMReleaseReviewSelectorProposalAdapter.JAVA_INTERACTION_MAP_PROVIDER,
                (selection, documents) -> List.of());
        var exactJava = new SFMReleaseReviewSelectorProposalAdapter.JavaInteractionMapProvider(Map.of());
        List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> combined =
                SFMReleaseReviewEditorCapture.combineSemanticProviders(
                        List.of(body, registeredCollision), Optional.of(exactJava));
        assertEquals(List.of(
                SFMReleaseReviewSelectorProposalAdapter.JAVA_INTERACTION_MAP_PROVIDER,
                "test:release-review/body"
        ), ids(combined));
        assertSame(exactJava, combined.get(0));
    }

    private static SFMReleaseReviewEditorCapture.Capture editorCapture(
            SFMReleaseReviewV1 review,
            String documentRevisionId,
            int startByte,
            int endByte
    ) {
        SFMReleaseReviewCorpus.DocumentView corpus = SFMReleaseReviewCorpus.from(review)
                .documentRevision(documentRevisionId).orElseThrow();
        String text = corpus.materializedDocument().orElseThrow().text();
        SFMPath root = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                List.of(documentRevisionId),
                Optional.empty(),
                true
        );
        SFMPath addressed = new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                "review",
                "document",
                List.of(documentRevisionId, "src", "Cafe.java"),
                Optional.empty(),
                false
        );
        SFMTextDocumentSnapshot baseline = SFMTextDocumentSnapshot.pinned(
                addressed,
                root,
                text,
                corpus.binding().sha256(),
                Optional.empty(),
                Optional.empty()
        );
        SFMTextDocumentSelection selection = new SFMTextDocumentSelection(
                "primary",
                SFMTextDocumentRange.positionAtByteOffset(text, startByte),
                SFMTextDocumentRange.positionAtByteOffset(text, endByte),
                true
        );
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "sfm:text_editor_v3",
                baseline,
                text,
                false,
                true,
                List.of(),
                List.of(new SFMContextSelectionProjection(
                        "primary",
                        List.of(selection.orderedRange()),
                        true,
                        List.of(selection)
                ))
        );
        return SFMReleaseReviewEditorCapture.capture(
                new SFMClientActionContext(null, () -> true, null),
                new SFMContextSnapshot(1, 1, 1, Optional.empty(), List.of()),
                projection,
                review
        );
    }

    private static SFMReleaseReviewV1 fixture() throws Exception {
        return SFMReleaseReviewV1Codec.parse(Files.readString(fixturePath()).replace("\r\n", "\n"));
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical release-review fixture");
    }

    private static List<String> ids(
            List<? extends SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> providers
    ) {
        return providers.stream().map(
                SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider::id).toList();
    }

    private static SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider provider(
            String id,
            ProviderBody body
    ) {
        return provider(() -> id, body);
    }

    private static SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider provider(
            ProviderId id,
            ProviderBody body
    ) {
        return new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider() {
            @Override
            public String id() {
                return id.get();
            }

            @Override
            public List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence> propose(
                    SFMReleaseReviewV1.PinnedSelection selection,
                    SFMReleaseReviewSelectionAdapter.DocumentResolver documents
            ) throws Exception {
                return body.propose(selection, documents);
            }
        };
    }

    @FunctionalInterface
    private interface ProviderId {
        String get();
    }

    @FunctionalInterface
    private interface ProviderBody {
        List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence> propose(
                SFMReleaseReviewV1.PinnedSelection selection,
                SFMReleaseReviewSelectionAdapter.DocumentResolver documents
        ) throws Exception;
    }
}
