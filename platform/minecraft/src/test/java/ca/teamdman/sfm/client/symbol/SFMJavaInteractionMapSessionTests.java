package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaInteractionMapSessionTests {
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

    private static SFMContextContribution contribution(String text, long generation) {
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "fixture-editor",
                SFMTextDocumentSnapshot.literal(text),
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
