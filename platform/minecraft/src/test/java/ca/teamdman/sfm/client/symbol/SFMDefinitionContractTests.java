package ca.teamdman.sfm.client.symbol;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDefinitionContractTests {
    @Test
    void unicodeScalarAndCrLfPositionMatchesRustUtf8Contract() {
        String text = "class A {\r\n  String café = \"🦀\";\r\n}\r\n";
        SFMDefinitionRequest.Position position = SFMDefinitionRequest.Position.fromText(text, 2, 13);

        int expected = text.indexOf('é');
        assertEquals(text.substring(0, expected).getBytes(StandardCharsets.UTF_8).length, position.byteOffset());
        position.validateAgainst(text);
        assertThrows(IllegalArgumentException.class,
                () -> new SFMDefinitionRequest.Position(2, 13, position.byteOffset() + 1)
                        .validateAgainst(text));
    }

    @Test
    void requestCopiesRootsAndRejectsIdentityOrHashDisagreement() {
        ArrayList<SFMDefinitionRequest.SourceRoot> roots = new ArrayList<>();
        roots.add(root());
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                "1.19.2",
                SFMDefinitionRequest.ClasspathMode.ISOLATED,
                roots,
                "blake3:workspace",
                Optional.empty(),
                3
        );
        roots.clear();
        assertEquals(1, workspace.sourceRoots().size());

        String text = "class A {}\n";
        SFMDefinitionRequest.Document document = document(text);
        SFMDefinitionRequest request = new SFMDefinitionRequest(
                7,
                11,
                workspace,
                document,
                SFMDefinitionRequest.Position.fromText(text, 1, 7)
        );
        assertEquals(SFMDefinitionRequest.SCHEMA, request.schema());

        assertThrows(IllegalArgumentException.class, () -> new SFMDefinitionRequest.Document(
                document.address(),
                document.rootId(),
                document.rootRelativePath(),
                document.reportPath(),
                document.sourceSet(),
                text + "changed",
                document.contentHash(),
                Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMDefinitionRequest.Workspace(
                "1.19.2",
                SFMDefinitionRequest.ClasspathMode.ISOLATED,
                List.of(root(), root()),
                "blake3:workspace",
                Optional.empty(),
                3
        ));
    }

    @Test
    void resultCorrelationIncludesRequestWorkspaceAndDocumentIdentity() {
        SFMDefinitionRequest request = request();
        SFMDefinitionResult result = result(request);

        assertTrue(result.matches(request));
        assertFalse(result.matches(request.withIdentity(request.requestId() + 1, request.requestGeneration())));
        assertEquals(request, SFMDefinitionJsonCodec.decodeRequest(SFMDefinitionJsonCodec.encodeRequest(request)));
        assertEquals(result, SFMDefinitionJsonCodec.decodeResult(SFMDefinitionJsonCodec.encodeResult(result)));
        assertTrue(SFMDefinitionJsonCodec.encodeRequest(request).contains("\"request_id\":7"));
    }

    @Test
    void providerRegistryOrdersAvailableProvidersAndOwnsCancellation() {
        AtomicBoolean cancelled = new AtomicBoolean();
        TestProvider low = new TestProvider(new ResourceLocation("sfm", "low"), true, cancelled);
        TestProvider highUnavailable = new TestProvider(
                new ResourceLocation("sfm", "high_unavailable"),
                false,
                new AtomicBoolean()
        );
        TestProvider high = new TestProvider(new ResourceLocation("sfm", "high"), true, cancelled);
        SFMSymbolNavigationProviderRegistry registry = new SFMSymbolNavigationProviderRegistry();
        registry.register(1, low);
        registry.register(10, highUnavailable);
        registry.register(10, high);

        assertEquals(high.id(), registry.preferredAvailable().orElseThrow().id());
        SFMSymbolNavigationProvider.Query query = high.query(request());
        assertTrue(query.cancel());
        assertFalse(query.cancel());
        assertTrue(cancelled.get());
        assertTrue(query.result().isCancelled());

        registry.close();
        assertTrue(low.closed);
        assertTrue(high.closed);
        assertTrue(highUnavailable.closed);
    }

    private static SFMDefinitionRequest request() {
        String text = "class A {}\n";
        return new SFMDefinitionRequest(
                7,
                11,
                new SFMDefinitionRequest.Workspace(
                        "1.19.2",
                        SFMDefinitionRequest.ClasspathMode.ISOLATED,
                        List.of(root()),
                        "blake3:workspace",
                        Optional.empty(),
                        3
                ),
                document(text),
                SFMDefinitionRequest.Position.fromText(text, 1, 7)
        );
    }

    private static SFMDefinitionRequest.SourceRoot root() {
        return new SFMDefinitionRequest.SourceRoot("custom-0", "custom", "source", "custom", true);
    }

    private static SFMDefinitionRequest.Document document(String text) {
        return SFMDefinitionRequest.Document.sha256(
                "file:///source/example/A.java",
                "custom-0",
                "example/A.java",
                "source/example/A.java",
                "custom",
                text,
                Optional.empty()
        );
    }

    private static SFMDefinitionResult result(SFMDefinitionRequest request) {
        SFMDefinitionResult.AnalysisContext context = new SFMDefinitionResult.AnalysisContext(
                "1.19.2",
                "1.19.2",
                "17",
                "jdk",
                request.workspace().sourceRoots(),
                List.of(new SFMDefinitionResult.SourceSet("custom", List.of("custom"))),
                List.of(),
                request.workspace().classpathMode(),
                request.workspace().classpathFingerprint(),
                "arborium",
                "blake3:index"
        );
        SFMDefinitionResult.DocumentIdentity document = new SFMDefinitionResult.DocumentIdentity(
                request.document().address(),
                request.document().rootId(),
                request.document().rootRelativePath(),
                request.document().reportPath(),
                request.document().sourceSet(),
                request.document().contentHash(),
                request.document().diskContentHash()
        );
        return new SFMDefinitionResult(
                SFMDefinitionResult.SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                SFMDefinitionResult.Outcome.NO_SYMBOL,
                context,
                document,
                request.position(),
                List.of(),
                List.of(),
                SFMDefinitionResult.Completeness.COMPLETE,
                List.of(),
                List.of(),
                Optional.empty()
        );
    }

    private static final class TestProvider implements SFMSymbolNavigationProvider {
        private final ResourceLocation id;
        private final boolean available;
        private final AtomicBoolean cancelled;
        private boolean closed;

        private TestProvider(ResourceLocation id, boolean available, AtomicBoolean cancelled) {
            this.id = id;
            this.available = available;
            this.cancelled = cancelled;
        }

        @Override public ResourceLocation id() { return id; }
        @Override public boolean available() { return available; }
        @Override public Query query(SFMDefinitionRequest request) {
            return new Query(request, new CompletableFuture<>(), () -> cancelled.set(true));
        }
        @Override public void close() { closed = true; }
    }
}
