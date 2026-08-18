package ca.teamdman.sfm.client.symbol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSymbolServerProtocolTests {
    @Test
    void rustExtendedLengthWindowsPathsBecomeOrdinaryDriveAndUncPaths() {
        assertEquals(
                "D:\\workspace\\source",
                SFMSymbolServerProtocol.ordinaryWindowsPath("\\\\?\\D:\\workspace\\source")
        );
        assertEquals(
                "\\\\server\\share\\source",
                SFMSymbolServerProtocol.ordinaryWindowsPath("\\\\?\\UNC\\server\\share\\source")
        );
        assertEquals(
                "D:\\workspace\\source",
                SFMSymbolServerProtocol.ordinaryWindowsPath("D:\\workspace\\source")
        );
    }

    @Test
    void clientHelloExactlyMatchesTheConvergedRustEnvelope() {
        assertEquals(
                "{\"kind\":\"hello\",\"schema\":\"sfm.symbol-server.hello/1\",\"hello\":"
                        + "{\"protocol_schema\":\"sfm.symbol-server/1\",\"client_name\":\"minecraft\","
                        + "\"client_version\":\"1\",\"capabilities\":[\"definition-at-position\","
                        + "\"cancellation\",\"workspace-generation\",\"ping\",\"shutdown\","
                        + "\"usage-at-position\",\"java-interaction-map\"],"
                        + "\"max_frame_bytes\":16777216}}",
                SFMSymbolServerProtocol.hello("minecraft", "1", 16_777_216)
        );
    }

    @Test
    void helloRetainsTypedWorkspaceAndRawAdditiveMetadata() throws Exception {
        String json = helloEnvelope(7, "D:/workspace/source", "extra_field", "retained");

        var frame = assertInstanceOf(
                SFMSymbolServerProtocol.HelloFrame.class,
                SFMSymbolServerProtocol.decodeServerFrame(json)
        );

        assertEquals(7, frame.hello().workspaceGeneration());
        assertEquals("custom-0", frame.hello().workspace().rootMappings().get(0).rootId());
        assertEquals("source", frame.hello().workspace().rootMappings().get(0).reportRootPath());
        assertTrue(frame.hello().rawHelloJson().contains("extra_field"));
        assertEquals(
                "file:///D:/workspace/source",
                frame.hello().workspace().rootMappings().get(0).absoluteRootAddress()
        );
    }

    @Test
    void helloRetainsManagedDependencySourceRootMetadata() throws Exception {
        JsonObject envelope = JsonParser.parseString(
                helloEnvelope(7, "D:/workspace/source", null, null)
        ).getAsJsonObject();
        JsonObject workspace = envelope.getAsJsonObject("hello").getAsJsonObject("workspace");
        JsonObject dependencyRoot = new JsonObject();
        dependencyRoot.addProperty("canonical_absolute_path", "D:/workspace/dependencies/forge");
        dependencyRoot.addProperty("root_id", "dependency-source-0");
        dependencyRoot.addProperty("source_set", "dependency:forge");
        dependencyRoot.addProperty("report_prefix", "dependency/forge/userdev/loader-pipeline");
        com.google.gson.JsonArray dependencyRoots = new com.google.gson.JsonArray();
        dependencyRoots.add(dependencyRoot);
        workspace.add("dependency_source_roots", dependencyRoots);

        var frame = assertInstanceOf(
                SFMSymbolServerProtocol.HelloFrame.class,
                SFMSymbolServerProtocol.decodeServerFrame(envelope.toString())
        );
        var decoded = frame.hello().workspace().dependencySourceRootMappings().get(0);
        assertEquals("dependency-source-0", decoded.rootId());
        assertEquals("dependency:forge", decoded.sourceSet());
        assertEquals("dependency/forge/userdev/loader-pipeline", decoded.reportPrefix());
        assertEquals(
                "file:///D:/workspace/dependencies/forge",
                decoded.absoluteRootAddress()
        );
    }

    @Test
    void helloRetainsCanonicalJdkManagedSourceAuthority() throws Exception {
        JsonObject envelope = JsonParser.parseString(
                helloEnvelope(7, "D:/workspace/source", null, null)
        ).getAsJsonObject();
        JsonObject workspace = envelope.getAsJsonObject("hello").getAsJsonObject("workspace");
        JsonObject requestWorkspace = workspace.getAsJsonObject("request_workspace");

        JsonObject jdkRoot = new JsonObject();
        jdkRoot.addProperty("id", "jdk-java-17-abc123");
        jdkRoot.addProperty("source_set", "jdk:java-17");
        jdkRoot.addProperty("path", "jdk/java-17/abc123");
        jdkRoot.addProperty("kind", "jdk");
        jdkRoot.addProperty("exists", true);
        requestWorkspace.getAsJsonArray("source_roots").add(jdkRoot);

        JsonObject orderedRoot = new JsonObject();
        orderedRoot.addProperty("canonical_absolute_path", "D:/cache/jdk/java-17/abc123/tree");
        orderedRoot.addProperty("root_id", "jdk-java-17-abc123");
        orderedRoot.addProperty("source_set", "jdk:java-17");
        orderedRoot.addProperty("report_root_path", "jdk/java-17/abc123");
        workspace.getAsJsonArray("roots").add(orderedRoot);

        JsonObject managed = new JsonObject();
        managed.addProperty("resolver_id", "jdk-source");
        managed.addProperty("address_scheme", "jdk-source");
        managed.addProperty("resolver_identity", "jdk/java-17/abc123");
        managed.addProperty("canonical_absolute_path", "D:/cache/jdk/java-17/abc123/tree");
        managed.addProperty("root_id", "jdk-java-17-abc123");
        managed.addProperty("source_set", "jdk:java-17");
        managed.addProperty("portable_root_path", "jdk/java-17/abc123");
        managed.add("report_prefix", com.google.gson.JsonNull.INSTANCE);
        com.google.gson.JsonArray managedRoots = new com.google.gson.JsonArray();
        managedRoots.add(managed);
        workspace.add("managed_source_roots", managedRoots);

        var frame = assertInstanceOf(
                SFMSymbolServerProtocol.HelloFrame.class,
                SFMSymbolServerProtocol.decodeServerFrame(envelope.toString())
        );
        var decoded = frame.hello().workspace().managedSourceRootMappings().get(0);
        assertEquals("jdk-source", decoded.resolverId());
        assertEquals("jdk-java-17-abc123", decoded.rootId());
        assertEquals(Optional.of("jdk/java-17/abc123"), decoded.portableRootPath());
        assertEquals("file:///D:/cache/jdk/java-17/abc123/tree", decoded.absoluteRootAddress());
    }

    @Test
    void workspaceAckCarriesACompleteReplacementWorkspace() throws Exception {
        JsonObject hello = JsonParser.parseString(helloEnvelope(12, "D:/workspace/new-source", null, null))
                .getAsJsonObject()
                .getAsJsonObject("hello");
        JsonObject update = new JsonObject();
        update.add("workspace", hello.getAsJsonObject("workspace"));
        update.addProperty("cancelled_requests", 2);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("kind", "workspace-generation");
        envelope.addProperty("schema", SFMSymbolServerProtocol.WORKSPACE_GENERATION_SCHEMA);
        envelope.add("update", update);

        var frame = assertInstanceOf(
                SFMSymbolServerProtocol.WorkspaceGenerationFrame.class,
                SFMSymbolServerProtocol.decodeServerFrame(envelope.toString())
        );
        assertEquals(12, frame.workspaceGeneration());
        assertEquals(2, frame.cancelledRequests());
        assertEquals("D:\\workspace\\new-source",
                frame.workspace().rootMappings().get(0).canonicalAbsolutePath());
    }

    @Test
    void exactDefinitionResultEnvelopeDelegatesToTheDefinitionCodec() throws Exception {
        SFMDefinitionRequest request = request(9, 3, 7);
        SFMDefinitionResult result = result(request);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("kind", "definition-result");
        envelope.addProperty("schema", SFMSymbolServerProtocol.DEFINITION_SCHEMA);
        envelope.add("result", JsonParser.parseString(SFMDefinitionJsonCodec.encodeResult(result)));

        var decoded = assertInstanceOf(
                SFMSymbolServerProtocol.DefinitionResultFrame.class,
                SFMSymbolServerProtocol.decodeServerFrame(envelope.toString())
        );
        assertEquals(result, decoded.result());
    }

    @Test
    void usageRequestAndResultUseTheAdditiveWorkerCapability() throws Exception {
        SFMUsageAtPositionRequest request = SFMUsageAtPositionRequest.fromDefinition(request(19, 4, 7));
        SFMDefinitionResult definition = result(request.asDefinitionRequest());
        SFMUsageAtPositionResult result = new SFMUsageAtPositionResult(
                SFMUsageAtPositionResult.SCHEMA,
                definition.requestId(), definition.requestGeneration(), definition.workspaceGeneration(),
                definition.outcome(), definition.context(), definition.document(), definition.position(),
                definition.symbols(), definition.definitions(), List.of(), List.of(),
                definition.completeness(), definition.diagnostics(), definition.recoveryActions(),
                definition.dependencyIndex()
        );

        JsonObject requestEnvelope = JsonParser.parseString(
                SFMSymbolServerProtocol.usageAtPosition(request)).getAsJsonObject();
        assertEquals("usage-at-position", requestEnvelope.get("kind").getAsString());
        assertEquals(SFMSymbolServerProtocol.USAGE_AT_POSITION_SCHEMA,
                requestEnvelope.get("schema").getAsString());
        assertEquals(request, SFMDefinitionJsonCodec.decodeUsageRequest(
                requestEnvelope.getAsJsonObject("request").toString()));

        JsonObject resultEnvelope = new JsonObject();
        resultEnvelope.addProperty("kind", "usage-at-position-result");
        resultEnvelope.addProperty("schema", SFMSymbolServerProtocol.USAGE_AT_POSITION_SCHEMA);
        resultEnvelope.add("result", JsonParser.parseString(
                SFMDefinitionJsonCodec.encodeUsageResult(result)));
        var decoded = assertInstanceOf(
                SFMSymbolServerProtocol.UsageAtPositionResultFrame.class,
                SFMSymbolServerProtocol.decodeServerFrame(resultEnvelope.toString())
        );
        assertEquals(result, decoded.result());
        assertEquals(
                SFMSymbolServerProtocol.cancel(request.asDefinitionRequest(), "test"),
                SFMSymbolServerProtocol.cancel(request, "test"),
                "both request kinds share the same typed cancellation identity"
        );
    }

    @Test
    void requestAndAddressedResultExactlyMatchTheRustVersionThreeJsonContract() {
        SFMDefinitionRequest request = request(7, 3, 11);
        String hash = "sha256:f119fc42a923d52cbd5420b0c5841969bef8dea5e8b78ba392ffb58312380247";
        String expectedRequest = "{\"schema\":\"sfm.definition-at-position-request/2\","
                + "\"request_id\":7,\"request_generation\":3,\"workspace\":{"
                + "\"branch\":\"1.19.2\",\"classpath_mode\":\"isolated\",\"source_roots\":[{"
                + "\"id\":\"custom-0\",\"source_set\":\"custom\",\"path\":\"source\","
                + "\"kind\":\"custom\",\"exists\":true}],\"classpath_fingerprint\":\"blake3:workspace\","
                + "\"workspace_fingerprint\":\"blake3:0000000000000000000000000000000000000000000000000000000000000000\","
                + "\"workspace_generation\":11},\"document\":{"
                + "\"address\":\"file:///D:/workspace/source/A.java\",\"root_id\":\"custom-0\","
                + "\"root_relative_path\":\"A.java\",\"report_path\":\"source/A.java\","
                + "\"source_set\":\"custom\",\"text\":\"class A {}\\n\",\"content_hash\":\"" + hash + "\","
                + "\"disk_content_hash\":\"" + hash + "\"},"
                + "\"position\":{\"line\":1,\"column\":7,\"byte_offset\":6}}";
        assertEquals(expectedRequest, SFMDefinitionJsonCodec.encodeRequest(request));
        assertEquals(request, SFMDefinitionJsonCodec.decodeRequest(expectedRequest));

        SFMDefinitionResult addressed = addressedResult(request);
        String identifierSpan = "{\"address\":\"file:///D:/workspace/source/A.java\","
                + "\"resolver_id\":\"sfm:file\",\"root_id\":\"custom-0\","
                + "\"root_relative_path\":\"A.java\",\"report_path\":\"source/A.java\","
                + "\"source_set\":\"custom\",\"source_hash\":\"" + hash + "\","
                + "\"source_sha256\":\"" + hash + "\","
                + "\"start_byte\":6,\"end_byte\":7,\"start_line\":1,\"start_column\":7,"
                + "\"end_line\":1,\"end_column\":8}";
        String declarationSpan = "{\"address\":\"file:///D:/workspace/source/A.java\","
                + "\"resolver_id\":\"sfm:file\",\"root_id\":\"custom-0\","
                + "\"root_relative_path\":\"A.java\",\"report_path\":\"source/A.java\","
                + "\"source_set\":\"custom\",\"source_hash\":\"" + hash + "\","
                + "\"source_sha256\":\"" + hash + "\","
                + "\"start_byte\":0,\"end_byte\":10,\"start_line\":1,\"start_column\":1,"
                + "\"end_line\":1,\"end_column\":11}";
        String symbol = "{\"kind\":\"class\",\"owner\":\"example\",\"name\":\"A\","
                + "\"qualified_name\":\"example.A\"}";
        String expectedResult = "{\"schema\":\"sfm.definition-at-position-result/3\","
                + "\"request_id\":7,\"request_generation\":3,\"workspace_generation\":11,"
                + "\"outcome\":\"success\",\"context\":{\"branch\":\"1.19.2\","
                + "\"minecraft_version\":\"1.19.2\",\"java_release\":\"17\",\"jdk\":\"jdk\","
                + "\"source_roots\":[{\"id\":\"custom-0\",\"source_set\":\"custom\","
                + "\"path\":\"source\",\"kind\":\"custom\",\"exists\":true}],"
                + "\"source_sets\":[{\"id\":\"custom\",\"visible_source_sets\":[\"custom\"]}],"
                + "\"source_exclusions\":[],\"classpath_mode\":\"isolated\","
                + "\"classpath_fingerprint\":\"blake3:workspace\",\"parser_fingerprint\":\"arborium\","
                + "\"index_fingerprint\":\"blake3:index\"},\"document\":{"
                + "\"address\":\"file:///D:/workspace/source/A.java\",\"root_id\":\"custom-0\","
                + "\"root_relative_path\":\"A.java\",\"report_path\":\"source/A.java\","
                + "\"source_set\":\"custom\",\"content_hash\":\"" + hash + "\","
                + "\"disk_content_hash\":\"" + hash + "\"},"
                + "\"position\":{\"line\":1,\"column\":7,\"byte_offset\":6},"
                + "\"symbols\":[" + symbol + "],\"definitions\":[{\"symbol\":" + symbol
                + ",\"identifier_span\":" + identifierSpan + ",\"declaration_span\":"
                + declarationSpan + ",\"confidence\":\"resolved\"}],\"completeness\":\"incomplete\","
                + "\"diagnostics\":[],\"recovery_actions\":[{"
                + "\"kind\":\"acquire-dependency-sources\",\"label\":\"Acquire dependency sources\","
                + "\"command\":\"sfm-propagate-changes dependency source acquire --branch 1.19.2\"}]}";
        assertEquals(expectedResult, SFMDefinitionJsonCodec.encodeResult(addressed));
        assertEquals(addressed, SFMDefinitionJsonCodec.decodeResult(expectedResult));
    }

    @Test
    void versionOneRequestAndResultAreRejectedWithoutFallback() {
        SFMDefinitionRequest request = request(7, 3, 11);
        String oldRequest = SFMDefinitionJsonCodec.encodeRequest(request)
                .replace(SFMDefinitionRequest.SCHEMA, "sfm.definition-at-position-request/1");
        String oldResult = SFMDefinitionJsonCodec.encodeResult(result(request))
                .replace(SFMDefinitionResult.SCHEMA, "sfm.definition-at-position-result/2");

        assertThrows(IllegalArgumentException.class, () -> SFMDefinitionJsonCodec.decodeRequest(oldRequest));
        assertThrows(IllegalArgumentException.class, () -> SFMDefinitionJsonCodec.decodeResult(oldResult));
        assertEquals(
                SFMDefinitionResult.Outcome.STALE_DOCUMENT,
                SFMDefinitionResult.Outcome.fromWireName("stale-document")
        );
    }

    @Test
    void mismatchedSchemaAndRelativeWorkerRootFailClosed() {
        assertThrows(SFMSymbolServerProtocol.ProtocolException.class, () ->
                SFMSymbolServerProtocol.decodeServerFrame(
                        "{\"kind\":\"pong\",\"schema\":\"wrong\",\"nonce\":1}"
                ));
        assertThrows(SFMSymbolServerProtocol.ProtocolException.class, () ->
                SFMSymbolServerProtocol.decodeServerFrame(helloEnvelope(7, "relative/source", null, null)));
        assertThrows(SFMSymbolServerProtocol.ProtocolException.class, () ->
                SFMSymbolServerProtocol.decodeServerFrame(
                        "{\"kind\":\"pong\",\"schema\":\"sfm.symbol-server.ping/1\",\"nonce\":1.5}"
                ));
        assertThrows(SFMSymbolServerProtocol.ProtocolException.class, () ->
                SFMSymbolServerProtocol.decodeServerFrame(
                        "{\"kind\":\"pong\",\"schema\":\"sfm.symbol-server.ping/1\","
                                + "\"nonce\":18446744073709551615}"
                ));
        assertThrows(SFMSymbolServerProtocol.ProtocolException.class, () ->
                SFMSymbolServerProtocol.decodeServerFrame(
                        "{\"kind\":\"cancelled\",\"schema\":\"sfm.symbol-server.cancel/1\","
                                + "\"cancellation\":{\"request_id\":1,\"request_generation\":1,"
                                + "\"workspace_generation\":1,\"status\":\"future-status\"}}"
                ));
        assertThrows(SFMSymbolServerProtocol.ProtocolException.class, () ->
                SFMSymbolServerProtocol.decodeServerFrame(
                        "{\"kind\":\"error\",\"schema\":\"sfm.symbol-server.error/1\",\"error\":{"
                                + "\"code\":\"x\",\"message\":\"x\",\"disposition\":\"future\","
                                + "\"request_id\":null,\"request_generation\":null}}"
                ));
    }

    static String helloEnvelope(
            long generation,
            String absoluteRoot,
            String extraName,
            String extraValue
    ) {
        JsonObject requestWorkspace = new JsonObject();
        requestWorkspace.addProperty("branch", "1.19.2");
        requestWorkspace.addProperty("classpath_mode", "isolated");
        com.google.gson.JsonArray roots = new com.google.gson.JsonArray();
        JsonObject root = new JsonObject();
        root.addProperty("id", "custom-0");
        root.addProperty("source_set", "custom");
        root.addProperty("path", "source");
        root.addProperty("kind", "custom");
        root.addProperty("exists", true);
        roots.add(root);
        requestWorkspace.add("source_roots", roots);
        requestWorkspace.addProperty("classpath_fingerprint", "blake3:workspace");
        requestWorkspace.addProperty(
                "workspace_fingerprint",
                "blake3:0000000000000000000000000000000000000000000000000000000000000000"
        );
        requestWorkspace.addProperty("workspace_generation", generation);

        JsonObject mapping = new JsonObject();
        mapping.addProperty("canonical_absolute_path", absoluteRoot);
        mapping.addProperty("root_id", "custom-0");
        mapping.addProperty("source_set", "custom");
        mapping.addProperty("report_root_path", "source");
        com.google.gson.JsonArray mappings = new com.google.gson.JsonArray();
        mappings.add(mapping);
        JsonObject workspace = new JsonObject();
        workspace.add("request_workspace", requestWorkspace);
        workspace.add("roots", mappings);

        JsonObject hello = new JsonObject();
        hello.addProperty("protocol_schema", SFMSymbolServerProtocol.PROTOCOL_SCHEMA);
        hello.addProperty("server_name", "sfm-propagate-changes");
        hello.addProperty("server_version", "test");
        com.google.gson.JsonArray capabilities = new com.google.gson.JsonArray();
        SFMSymbolServerProtocol.CLIENT_CAPABILITIES.forEach(capabilities::add);
        hello.add("capabilities", capabilities);
        hello.addProperty("max_frame_bytes", 1024 * 1024);
        hello.addProperty("max_pending_definitions", 8);
        hello.add("workspace", workspace);
        if (extraName != null) hello.addProperty(extraName, extraValue);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("kind", "hello");
        envelope.addProperty("schema", SFMSymbolServerProtocol.HELLO_SCHEMA);
        envelope.add("hello", hello);
        return envelope.toString();
    }

    static SFMDefinitionRequest request(long requestId, long requestGeneration, long workspaceGeneration) {
        String text = "class A {}\n";
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "custom-0", "custom", "source", "custom", true
        );
        return new SFMDefinitionRequest(
                requestId,
                requestGeneration,
                new SFMDefinitionRequest.Workspace(
                        "1.19.2",
                        SFMDefinitionRequest.ClasspathMode.ISOLATED,
                        List.of(root),
                        "blake3:workspace",
                        Optional.empty(),
                        "blake3:0000000000000000000000000000000000000000000000000000000000000000",
                        workspaceGeneration
                ),
                SFMDefinitionRequest.Document.sha256(
                        "file:///D:/workspace/source/A.java",
                        "custom-0",
                        "A.java",
                        "source/A.java",
                        "custom",
                        text,
                        Optional.of(SFMDefinitionRequest.sha256(text))
                ),
                SFMDefinitionRequest.Position.fromText(text, 1, 7)
        );
    }

    static SFMDefinitionResult result(SFMDefinitionRequest request) {
        return new SFMDefinitionResult(
                SFMDefinitionResult.SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                SFMDefinitionResult.Outcome.NO_SYMBOL,
                new SFMDefinitionResult.AnalysisContext(
                        "1.19.2", "1.19.2", "17", "jdk",
                        request.workspace().sourceRoots(),
                        List.of(new SFMDefinitionResult.SourceSet("custom", List.of("custom"))),
                        List.of(),
                        request.workspace().classpathMode(),
                        request.workspace().classpathFingerprint(),
                        "arborium",
                        "blake3:index"
                ),
                new SFMDefinitionResult.DocumentIdentity(
                        request.document().address(), request.document().rootId(),
                        request.document().rootRelativePath(), request.document().reportPath(),
                        request.document().sourceSet(), request.document().contentHash(),
                        request.document().diskContentHash()
                ),
                request.position(),
                List.of(), List.of(), SFMDefinitionResult.Completeness.COMPLETE,
                List.of(), List.of(), Optional.empty()
        );
    }

    private static SFMDefinitionResult addressedResult(SFMDefinitionRequest request) {
        SFMDefinitionResult.SymbolIdentity symbol = new SFMDefinitionResult.SymbolIdentity(
                "class", "example", "A", Optional.empty(), "example.A"
        );
        SFMDefinitionResult.DefinitionSourceSpan identifier = new SFMDefinitionResult.DefinitionSourceSpan(
                request.document().address(), "sfm:file", request.document().rootId(),
                request.document().rootRelativePath(), request.document().reportPath(),
                request.document().sourceSet(), request.document().contentHash(),
                Optional.of(request.document().contentHash()),
                6, 7, 1, 7, 1, 8
        );
        SFMDefinitionResult.DefinitionSourceSpan declaration = new SFMDefinitionResult.DefinitionSourceSpan(
                request.document().address(), "sfm:file", request.document().rootId(),
                request.document().rootRelativePath(), request.document().reportPath(),
                request.document().sourceSet(), request.document().contentHash(),
                Optional.of(request.document().contentHash()),
                0, 10, 1, 1, 1, 11
        );
        return new SFMDefinitionResult(
                SFMDefinitionResult.SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                SFMDefinitionResult.Outcome.SUCCESS,
                new SFMDefinitionResult.AnalysisContext(
                        "1.19.2", "1.19.2", "17", "jdk",
                        request.workspace().sourceRoots(),
                        List.of(new SFMDefinitionResult.SourceSet("custom", List.of("custom"))),
                        List.of(), request.workspace().classpathMode(),
                        request.workspace().classpathFingerprint(), "arborium", "blake3:index"
                ),
                new SFMDefinitionResult.DocumentIdentity(
                        request.document().address(), request.document().rootId(),
                        request.document().rootRelativePath(), request.document().reportPath(),
                        request.document().sourceSet(), request.document().contentHash(),
                        request.document().diskContentHash()
                ),
                request.position(),
                List.of(symbol),
                List.of(new SFMDefinitionResult.Definition(symbol, identifier, declaration, "resolved")),
                SFMDefinitionResult.Completeness.INCOMPLETE,
                List.of(),
                List.of(new SFMDefinitionResult.RecoveryAction(
                        SFMDefinitionResult.RecoveryActionKind.ACQUIRE_DEPENDENCY_SOURCES,
                        "Acquire dependency sources",
                        Optional.of("sfm-propagate-changes dependency source acquire --branch 1.19.2")
                )),
                Optional.empty()
        );
    }
}
