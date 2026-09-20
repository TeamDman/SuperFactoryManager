package ca.teamdman.sfm.client.syntax;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTextEditorSyntaxSessionTests {
    @Test
    void publishesOnlyTheCurrentOpenSourceGeneration() {
        FakeService service = new FakeService();
        ArrayList<SFMTextEditorSyntaxSession.Publication> publications = new ArrayList<>();
        ArrayList<Throwable> failures = new ArrayList<>();
        SFMTextEditorSyntaxSession session = new SFMTextEditorSyntaxSession(
                "editor:1", service, Runnable::run, publications::add, failures::add
        );

        session.request(1, "java", "class Old {}");
        FakeService.Pending old = service.pending.get(0);
        session.request(2, "java", "class New {}");
        FakeService.Pending current = service.pending.get(1);
        assertTrue(old.cancelled);
        old.future.complete(highlighted(old.request));
        assertTrue(publications.isEmpty());

        current.future.complete(highlighted(current.request));
        assertEquals(1, publications.size());
        assertEquals("class New {}", publications.get(0).request().source());
        assertTrue(failures.isEmpty());
        assertEquals(1, session.telemetry().published());
    }

    @Test
    void closeCancelsAndDiscardsLateResult() {
        FakeService service = new FakeService();
        ArrayList<SFMTextEditorSyntaxSession.Publication> publications = new ArrayList<>();
        SFMTextEditorSyntaxSession session = new SFMTextEditorSyntaxSession(
                "editor:2", service, Runnable::run, publications::add, ignored -> { }
        );
        session.request(1, "java", "class A {}");
        FakeService.Pending pending = service.pending.get(0);
        session.close();
        assertTrue(pending.cancelled);
        pending.future.complete(highlighted(pending.request));
        assertTrue(publications.isEmpty());
        assertFalse(session.telemetry().active());
    }

    private static SFMSyntaxHighlightResult highlighted(SFMSyntaxHighlightRequest request) {
        return new SFMSyntaxHighlightResult(
                SFMSyntaxHighlightResult.SCHEMA,
                request.requestId(), request.requestGeneration(), request.originId(), request.originGeneration(),
                request.language(), request.sourceSha256(), request.sourceBytes(),
                SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED, true,
                SFMSyntaxHighlightResult.PARSER_FINGERPRINT,
                SFMSyntaxHighlightResult.FORMATTING_SCHEMA,
                1,
                SFMSyntaxHighlightResult.CacheEvidence.bypassed(),
                List.of(),
                List.of(new SFMSyntaxHighlightResult.Span(0, 5, "keyword", List.of("light_purple")))
        );
    }

    private static final class FakeService implements SFMSyntaxHighlightService {
        private final List<Pending> pending = new ArrayList<>();
        private long requestId;

        @Override
        public Submission query(String originId, long originGeneration, String language, String exactSource) {
            SFMSyntaxHighlightRequest request = SFMSyntaxHighlightRequest.create(
                    ++requestId, requestId, originId, originGeneration, language, exactSource, 1024
            );
            Pending value = new Pending(request);
            pending.add(value);
            return new Submission(request, value.future, () -> value.cancelled = true);
        }

        private static final class Pending {
            private final SFMSyntaxHighlightRequest request;
            private final CompletableFuture<SFMSyntaxHighlightResult> future = new CompletableFuture<>();
            private boolean cancelled;

            private Pending(SFMSyntaxHighlightRequest request) {
                this.request = request;
            }
        }
    }
}
