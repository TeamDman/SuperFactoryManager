package ca.teamdman.sfm.client.syntax;

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

class SFMSyntaxHighlightContractTests {
    @Test
    void requestPreservesExactUnicodeTextHashLanguageAndIdentity() {
        String source = "class Café { String value = \"🙂\"; }\r\n";
        SFMSyntaxHighlightRequest request = SFMSyntaxHighlightRequest.create(
                7, 11, "editor:java:é", 13, "java", source, 100
        );

        assertEquals(source, request.source());
        assertEquals(SFMSyntaxHighlightRequest.sha256(source), request.sourceSha256());
        assertEquals(source.getBytes(StandardCharsets.UTF_8).length, request.sourceBytes());
        assertEquals(request, SFMSyntaxHighlightJsonCodec.decodeRequest(
                SFMSyntaxHighlightJsonCodec.encodeRequest(request)
        ));

        assertThrows(IllegalArgumentException.class, () -> new SFMSyntaxHighlightRequest(
                request.schema(), request.requestId(), request.requestGeneration(), request.originId(),
                request.originGeneration(), request.language(), source + "changed", request.sourceSha256(),
                request.maximumSpans()
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightRequest.create(
                7, 11, "editor", 13, "Java", source, 100
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightRequest.create(
                0, 11, "editor", 13, "java", source, 100
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightRequest.sha256("bad\uD800"));
    }

    @Test
    void resultAcceptsUtf8ScalarBoundariesAndRejectsSplitNonAsciiRanges() {
        String source = "class Café { String face = \"🙂\"; }\r\n";
        SFMSyntaxHighlightRequest request = request(source);
        int accentStart = utf8Offset(source, source.indexOf('é'));
        int accentEnd = accentStart + "é".getBytes(StandardCharsets.UTF_8).length;
        int emojiUtf16 = source.indexOf("🙂");
        int emojiStart = utf8Offset(source, emojiUtf16);
        int emojiEnd = emojiStart + "🙂".getBytes(StandardCharsets.UTF_8).length;
        SFMSyntaxHighlightResult result = result(request, List.of(
                new SFMSyntaxHighlightResult.Span(accentStart, accentEnd, "type", List.of("aqua")),
                new SFMSyntaxHighlightResult.Span(emojiStart, emojiEnd, "string", List.of("green"))
        ));

        result.validateAgainst(request, SFMSyntaxHighlightLimits.defaults());
        assertEquals(result, SFMSyntaxHighlightJsonCodec.decodeResult(
                SFMSyntaxHighlightJsonCodec.encodeResult(result)
        ));

        SFMSyntaxHighlightResult splitAccent = result(request, List.of(
                new SFMSyntaxHighlightResult.Span(accentStart + 1L, accentEnd, "type", List.of("aqua"))
        ));
        assertThrows(IllegalArgumentException.class, () -> splitAccent.validateAgainst(
                request,
                SFMSyntaxHighlightLimits.defaults()
        ));
        SFMSyntaxHighlightResult splitEmoji = result(request, List.of(
                new SFMSyntaxHighlightResult.Span(emojiStart, emojiStart + 2L, "string", List.of("green"))
        ));
        assertThrows(IllegalArgumentException.class, () -> splitEmoji.validateAgainst(
                request,
                SFMSyntaxHighlightLimits.defaults()
        ));
    }

    @Test
    void resultRejectsUnknownDuplicateOverlappingAndMalformedStyles() {
        SFMSyntaxHighlightRequest request = request("class A {}\n");
        assertThrows(IllegalArgumentException.class, () -> new SFMSyntaxHighlightResult.Span(
                0, 5, "keyword", List.of("chartreuse")
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMSyntaxHighlightResult.Span(
                0, 5, "keyword", List.of("yellow", "yellow")
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMSyntaxHighlightResult.Span(
                5, 5, "keyword", List.of("yellow")
        ));

        SFMSyntaxHighlightResult overlapping = result(request, List.of(
                new SFMSyntaxHighlightResult.Span(0, 5, "keyword", List.of("light_purple")),
                new SFMSyntaxHighlightResult.Span(4, 7, "type", List.of("aqua"))
        ));
        assertThrows(IllegalArgumentException.class, () -> overlapping.validateAgainst(
                request,
                SFMSyntaxHighlightLimits.defaults()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SFMSyntaxHighlightResult.Diagnostic(
                "java.parse-gap",
                SFMSyntaxHighlightResult.DiagnosticSeverity.WARNING,
                "gap",
                Optional.of(1L),
                Optional.empty()
        ));
    }

    @Test
    void strictJsonRejectsMissingUnknownFractionalAndUnknownWireValues() {
        SFMSyntaxHighlightRequest request = request("class A {}\n");
        String encodedRequest = SFMSyntaxHighlightJsonCodec.encodeRequest(request);
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightJsonCodec.decodeRequest(
                encodedRequest.substring(0, encodedRequest.length() - 1) + ",\"future\":true}"
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightJsonCodec.decodeRequest(
                encodedRequest.replace("\"request_id\":1", "\"request_id\":1.5")
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightJsonCodec.decodeRequest(
                encodedRequest.replace("\"language\":\"java\",", "")
        ));

        String encodedResult = SFMSyntaxHighlightJsonCodec.encodeResult(result(request, List.of(
                new SFMSyntaxHighlightResult.Span(0, 5, "keyword", List.of("light_purple"))
        )));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightJsonCodec.decodeResult(
                encodedResult.replace("\"outcome\":\"highlighted\"", "\"outcome\":\"future\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightJsonCodec.decodeResult(
                encodedResult.replace("\"light_purple\"", "\"chartreuse\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMSyntaxHighlightJsonCodec.decodeResult(
                encodedResult.replace("\"status\":\"miss\"", "\"status\":\"mystery\"")
        ));
    }

    @Test
    void resultCollectionsAndRegistrySnapshotsAreImmutableAndDeterministic() {
        SFMSyntaxHighlightRequest request = request("class A {}\n");
        ArrayList<SFMSyntaxHighlightResult.Span> spans = new ArrayList<>();
        spans.add(new SFMSyntaxHighlightResult.Span(0, 5, "keyword", List.of("light_purple")));
        SFMSyntaxHighlightResult result = result(request, spans);
        spans.clear();
        assertEquals(1, result.spans().size());
        assertThrows(UnsupportedOperationException.class, () -> result.spans().clear());

        AtomicBoolean cancelled = new AtomicBoolean();
        TestProvider low = new TestProvider("low", true, cancelled);
        TestProvider unavailable = new TestProvider("unavailable", false, new AtomicBoolean());
        TestProvider alpha = new TestProvider("alpha", true, cancelled);
        SFMSyntaxHighlightProviderRegistry registry = new SFMSyntaxHighlightProviderRegistry();
        registry.register(1, low);
        registry.register(10, unavailable);
        registry.register(10, alpha);

        assertEquals(alpha.id(), registry.preferredAvailable().orElseThrow().id());
        SFMSyntaxHighlightProvider.Query query = alpha.query(request);
        assertTrue(query.cancel());
        assertFalse(query.cancel());
        assertTrue(cancelled.get());
        assertTrue(query.result().isCancelled());
        registry.close();
        assertTrue(low.closed);
        assertTrue(alpha.closed);
        assertTrue(unavailable.closed);
    }

    static SFMSyntaxHighlightRequest request(String source) {
        return SFMSyntaxHighlightRequest.create(1, 1, "editor:test", 1, "java", source, 100);
    }

    static SFMSyntaxHighlightResult result(
            SFMSyntaxHighlightRequest request,
            List<SFMSyntaxHighlightResult.Span> spans
    ) {
        return new SFMSyntaxHighlightResult(
                SFMSyntaxHighlightResult.SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.originId(),
                request.originGeneration(),
                request.language(),
                request.sourceSha256(),
                request.sourceBytes(),
                SFMSyntaxHighlightResult.Outcome.HIGHLIGHTED,
                true,
                SFMSyntaxHighlightResult.PARSER_FINGERPRINT,
                SFMSyntaxHighlightResult.FORMATTING_SCHEMA,
                25,
                new SFMSyntaxHighlightResult.CacheEvidence(
                        SFMSyntaxHighlightResult.CacheStatus.MISS, 1, 128, 0, 1, 0
                ),
                List.of(SFMSyntaxHighlightResult.Diagnostic.withoutRange(
                        "java.test",
                        SFMSyntaxHighlightResult.DiagnosticSeverity.INFO,
                        "deterministic test result"
                )),
                spans
        );
    }

    private static int utf8Offset(String source, int utf16Offset) {
        return source.substring(0, utf16Offset).getBytes(StandardCharsets.UTF_8).length;
    }

    private static final class TestProvider implements SFMSyntaxHighlightProvider {
        private final ResourceLocation id;
        private final boolean available;
        private final AtomicBoolean cancelled;
        private boolean closed;

        private TestProvider(String path, boolean available, AtomicBoolean cancelled) {
            this.id = new ResourceLocation("sfm", path);
            this.available = available;
            this.cancelled = cancelled;
        }

        @Override public ResourceLocation id() { return id; }
        @Override public boolean available() { return available; }
        @Override public Query query(SFMSyntaxHighlightRequest request) {
            return new Query(request, new CompletableFuture<>(), () -> cancelled.set(true));
        }
        @Override public void close() { closed = true; }
    }
}
