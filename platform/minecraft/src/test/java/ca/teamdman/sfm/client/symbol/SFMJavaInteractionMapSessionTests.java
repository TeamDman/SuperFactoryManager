package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaInteractionMapSessionTests {
    @Test
    void pathlessScratchRevisionsNeverSubmitInteractionMapWork() {
        FakeLookupService service = new FakeLookupService();
        SFMJavaInteractionMapSession session = new SFMJavaInteractionMapSession(service);

        session.refresh(pathlessContribution("", 1), 1, SFMDefinitionRequest.sha256(""));
        session.refresh(pathlessContribution("hello", 2), 2, SFMDefinitionRequest.sha256("hello"));

        assertEquals(0, service.results.size());
        assertEquals(0, service.cancellations.get());
        assertFalse(session.pending());
        assertTrue(session.current(2, SFMDefinitionRequest.sha256("hello")).isEmpty());
    }

    @Test
    void onlyTheLatestExactDocumentGenerationCanPublish() {
        FakeLookupService service = new FakeLookupService();
        SFMJavaInteractionMapSession session = new SFMJavaInteractionMapSession(service);
        SFMJavaInteractionMap.Request firstRequest = SFMJavaInteractionMapProtocolTests.request();
        SFMJavaInteractionMap.Request secondRequest = new SFMJavaInteractionMap.Request(
                18,
                4,
                firstRequest.workspace(),
                firstRequest.document()
        );
        String hash = firstRequest.document().contentHash();
        SFMContextContribution contribution = contribution(firstRequest.document().text(), 4);

        session.refresh(contribution, 3, hash);
        session.refresh(contribution, 4, hash);
        assertEquals(1, service.cancellations.get());

        service.complete(0, lookup(firstRequest));
        assertTrue(session.current(3, hash).isEmpty());
        assertTrue(session.current(4, hash).isEmpty());

        service.complete(1, lookup(secondRequest));
        assertEquals(4, session.current(4, hash).orElseThrow().documentGeneration());
        assertTrue(session.current(3, hash).isEmpty());

        session.close();
        assertEquals(2, service.cancellations.get());
        assertTrue(session.current(4, hash).isEmpty());
    }

    @Test
    void aMismatchedPublicationFailsClosed() {
        FakeLookupService service = new FakeLookupService();
        SFMJavaInteractionMapSession session = new SFMJavaInteractionMapSession(service);
        SFMJavaInteractionMap.Request request = SFMJavaInteractionMapProtocolTests.request();
        SFMContextContribution contribution = contribution(request.document().text(), 3);

        session.refresh(contribution, 99, request.document().contentHash());
        service.complete(0, lookup(request));

        assertTrue(session.current(99, request.document().contentHash()).isEmpty());
    }

    @Test
    void invalidRequestDiagnosticsBecomeActionableWithoutRetainingPrivatePaths() {
        SFMJavaInteractionMap.Request request = SFMJavaInteractionMapProtocolTests.request();
        JsonObject json = SFMJavaInteractionMapProtocolTests.resultJson(request);
        json.addProperty("outcome", "invalid-request");
        JsonObject diagnostic = new JsonObject();
        diagnostic.addProperty("code", "java.interaction-map-invalid-request");
        diagnostic.addProperty("severity", "error");
        diagnostic.addProperty(
                "message",
                "definition document `secret-root`:`SecretProject/String.java` has no editable, managed-JDK, or acquired-dependency source authority"
        );
        diagnostic.add("span", null);
        JsonArray diagnostics = new JsonArray();
        diagnostics.add(diagnostic);
        json.add("diagnostics", diagnostics);

        SFMJavaInteractionMapSession.FailureSummary summary =
                SFMJavaInteractionMapSession.failureSummary(
                        SFMJavaInteractionMapJsonCodec.decodeResult(json.toString()));

        assertEquals("java.interaction-map-invalid-request", summary.diagnosticCodes());
        assertEquals("document-not-indexed", summary.category());
        assertEquals("verify-negotiated-root-or-refresh-index", summary.nextAction());
        assertFalse(summary.toString().contains("SecretProject"));
        assertFalse(summary.toString().contains("String.java"));
    }

    private static SFMContextContribution contribution(String text, long generation) {
        return contribution(addressedSnapshot(text), text, generation);
    }

    private static SFMContextContribution pathlessContribution(String text, long generation) {
        return contribution(SFMTextDocumentSnapshot.literal(text), text, generation);
    }

    private static SFMContextContribution contribution(
            SFMTextDocumentSnapshot baseline,
            String text,
            long generation
    ) {
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "fixture-editor",
                baseline,
                text,
                false,
                true,
                List.of(),
                List.of()
        );
        return new SFMContextContribution(
                new SFMContextOriginId("sfm:test", "fixture", "document"),
                new SFMContextGenerationEvidence(generation, generation, generation, 0),
                projection
        );
    }

    private static SFMTextDocumentSnapshot addressedSnapshot(String text) {
        SFMTextDocumentSnapshot literal = SFMTextDocumentSnapshot.literal(text);
        return new SFMTextDocumentSnapshot(
                literal.state(),
                literal.text(),
                literal.mutationCapability(),
                Optional.of(SFMPath.parse("file:///D:/workspace/source/A.java")),
                Optional.of(SFMPath.parse("file:///D:/workspace/source/")),
                literal.sha256(),
                literal.byteLength(),
                literal.lastModified(),
                literal.lineEndingKind(),
                literal.targetRange(),
                literal.diagnostics(),
                literal.sourceRootIdentity()
        );
    }

    private static SFMJavaInteractionMapLookupService.Lookup lookup(SFMJavaInteractionMap.Request request) {
        SFMJavaInteractionMap.Result result = SFMJavaInteractionMapJsonCodec.decodeResult(
                SFMJavaInteractionMapProtocolTests.resultJson(request).toString());
        SFMDefinitionRequest.SourceRoot root = request.workspace().sourceRoots().get(0);
        SFMSymbolServerProtocol.ServerHello hello = new SFMSymbolServerProtocol.ServerHello(
                SFMSymbolServerProtocol.PROTOCOL_SCHEMA,
                "fixture-symbol-server",
                "1",
                Set.copyOf(SFMSymbolServerProtocol.CLIENT_CAPABILITIES),
                1_048_576,
                8,
                new SFMSymbolServerProtocol.WorkspaceMetadata(
                        request.workspace(),
                        List.of(new SFMSymbolServerProtocol.SourceRootMapping(
                                "D:\\workspace\\source",
                                root.id(),
                                root.sourceSet(),
                                root.path()
                        ))
                ),
                "{}"
        );
        return new SFMJavaInteractionMapLookupService.Lookup(hello, result);
    }

    private static final class FakeLookupService implements SFMJavaInteractionMapLookupService {
        private final List<CompletableFuture<Lookup>> results = new ArrayList<>();
        private final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public Submission queryInteractionMap(SFMContextContribution contribution) {
            CompletableFuture<Lookup> result = new CompletableFuture<>();
            results.add(result);
            return new Submission(result, cancellations::incrementAndGet);
        }

        private void complete(int index, Lookup lookup) {
            results.get(index).complete(lookup);
        }
    }
}
