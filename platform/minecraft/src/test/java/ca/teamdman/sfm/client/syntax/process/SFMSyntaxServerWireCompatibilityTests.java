package ca.teamdman.sfm.client.syntax.process;

import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightJsonCodec;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightRequest;
import ca.teamdman.sfm.client.syntax.SFMSyntaxHighlightResult;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMSyntaxServerWireCompatibilityTests {
    @Test
    void decodesRustHelloAndHighlightResultEnvelopes() throws Exception {
        String hello = """
                {"kind":"hello","schema":"sfm.syntax-server.hello/1","hello":{
                  "protocol_schema":"sfm.syntax-server/1",
                  "server_name":"sfm-propagate-changes",
                  "server_version":"0.1.1",
                  "capabilities":["highlight","cancellation","ping","shutdown"],
                  "max_frame_bytes":16777216,
                  "max_pending_requests":8,
                  "supported_languages":["java"],
                  "request_schema":"sfm.syntax-highlight.request/1",
                  "result_schema":"sfm.syntax-highlight.result/1"
                }}
                """;
        var decodedHello = assertInstanceOf(
                SFMSyntaxServerProtocol.HelloFrame.class,
                SFMSyntaxServerProtocol.decodeServerFrame(hello)
        );
        assertEquals(8, decodedHello.hello().maximumPendingRequests());
        assertEquals(List.of("java"), decodedHello.hello().supportedLanguages());

        SFMSyntaxHighlightRequest request = SFMSyntaxHighlightRequest.create(
                7, 8, "editor:unicode", 9, "java", "class Café {}", 100
        );
        SFMSyntaxHighlightResult result = highlighted(request);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("kind", "highlight-result");
        envelope.addProperty("schema", SFMSyntaxServerProtocol.HIGHLIGHT_SCHEMA);
        envelope.add("result", JsonParser.parseString(SFMSyntaxHighlightJsonCodec.encodeResult(result)));
        var decodedResult = assertInstanceOf(
                SFMSyntaxServerProtocol.HighlightResultFrame.class,
                SFMSyntaxServerProtocol.decodeServerFrame(envelope.toString())
        );
        assertEquals(result, decodedResult.result());
    }

    @Test
    void outboundFramesCarryExactSchemasAndIdentity() {
        SFMSyntaxHighlightRequest request = SFMSyntaxHighlightRequest.create(
                1, 2, "editor:1", 3, "java", "class A {}", 100
        );
        JsonObject hello = JsonParser.parseString(SFMSyntaxServerProtocol.hello(
                "sfm-minecraft", "1", 4096
        )).getAsJsonObject();
        assertEquals("hello", hello.get("kind").getAsString());
        assertEquals(SFMSyntaxServerProtocol.PROTOCOL_SCHEMA,
                hello.getAsJsonObject("hello").get("protocol_schema").getAsString());

        JsonObject highlight = JsonParser.parseString(SFMSyntaxServerProtocol.highlight(request)).getAsJsonObject();
        assertEquals(request.source(), highlight.getAsJsonObject("request").get("source").getAsString());
        JsonObject cancel = JsonParser.parseString(SFMSyntaxServerProtocol.cancel(request, "changed"))
                .getAsJsonObject();
        assertEquals(request.originGeneration(), cancel.get("origin_generation").getAsLong());
    }

    @Test
    void frameCodecHandlesFragmentationUnicodeAndStrictBounds() throws Exception {
        String json = "{\"kind\":\"ping\",\"value\":\"🦀\"}";
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        SFMSyntaxServerFrameCodec.write(encoded, json, 1024);
        assertEquals(
                Optional.of(json),
                SFMSyntaxServerFrameCodec.read(new OneByteInputStream(encoded.toByteArray()), 1024)
        );
        assertEquals(Optional.empty(), SFMSyntaxServerFrameCodec.read(
                new ByteArrayInputStream(new byte[0]), 1024
        ));
        assertThrows(IOException.class, () -> SFMSyntaxServerFrameCodec.read(
                new ByteArrayInputStream(new byte[] { 1, 0 }), 1024
        ));
        assertThrows(IOException.class, () -> SFMSyntaxServerFrameCodec.write(
                new ByteArrayOutputStream(), json, 4
        ));
    }

    @Test
    void rejectsUnknownFieldsSchemasAndFormatting() {
        String unknown = """
                {"kind":"pong","schema":"sfm.syntax-server.ping/1","nonce":1,"surprise":true}
                """;
        assertThrows(
                SFMSyntaxServerProtocol.ProtocolException.class,
                () -> SFMSyntaxServerProtocol.decodeServerFrame(unknown)
        );
        String wrongSchema = """
                {"kind":"pong","schema":"sfm.syntax-server.cancel/1","nonce":1}
                """;
        assertThrows(
                SFMSyntaxServerProtocol.ProtocolException.class,
                () -> SFMSyntaxServerProtocol.decodeServerFrame(wrongSchema)
        );
    }

    private static SFMSyntaxHighlightResult highlighted(SFMSyntaxHighlightRequest request) {
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
                        SFMSyntaxHighlightResult.CacheStatus.MISS, 1, request.sourceBytes(), 0, 1, 0
                ),
                List.of(),
                List.of(new SFMSyntaxHighlightResult.Span(
                        0,
                        "class".getBytes(StandardCharsets.UTF_8).length,
                        "keyword",
                        List.of("light_purple")
                ))
        );
    }

    private static final class OneByteInputStream extends InputStream {
        private final ByteArrayInputStream delegate;

        private OneByteInputStream(byte[] bytes) {
            delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, Math.min(1, length));
        }
    }
}
