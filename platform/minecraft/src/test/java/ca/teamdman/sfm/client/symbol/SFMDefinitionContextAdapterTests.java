package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDefinitionContextAdapterTests {
    private static final SFMContextOriginId ORIGIN =
            new SFMContextOriginId("sfm:text-editor", "panel-7", "document");

    @Test
    void dirtyUnicodeCrlfOverlayUsesDeepestAnalysisRootAndBothExactHashes() {
        Path repoRoot = Path.of("D:/workspace/sfm");
        Path sourceRoot = repoRoot.resolve("platform/minecraft/src/main/java");
        Path file = sourceRoot.resolve("ca/teamdman/sfm/A.java");
        String baselineText = "package ca.teamdman.sfm;\r\nclass A {}\r\n";
        String overlay = "package ca.teamdman.sfm;\r\nclass A { String café = \"🦀\"; }\r\n";
        var cursor = SFMContextTextCoordinates.atLineColumn(overlay, 1, 23);
        SFMContextDocumentProjection projection = projection(
                repoRoot,
                file,
                baselineText,
                overlay,
                new SFMContextPosition.Canvas(100.5, 42.25, Optional.of(cursor))
        );

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection),
                Optional.of(hello(7, List.of(root(
                        "main-java", "main", sourceRoot,
                        "platform/minecraft/src/main/java"
                )))),
                11,
                4
        );

        assertTrue(adapted.success());
        SFMDefinitionRequest request = adapted.request().orElseThrow();
        assertEquals("main-java", request.document().rootId());
        assertEquals("ca/teamdman/sfm/A.java", request.document().rootRelativePath());
        assertEquals(
                "platform/minecraft/src/main/java/ca/teamdman/sfm/A.java",
                request.document().reportPath()
        );
        assertEquals(SFMDefinitionRequest.sha256(overlay), request.document().contentHash());
        assertEquals(
                Optional.of(SFMDefinitionRequest.sha256(baselineText)),
                request.document().diskContentHash()
        );
        assertEquals(cursor.line() + 1L, request.position().line());
        assertEquals(cursor.column() + 1L, request.position().column());
        assertEquals(cursor.byteOffset(), request.position().byteOffset());
    }

    @Test
    void sameLookingSourceRootsAreSelectedByFullCanonicalContainment() {
        Path repoRoot = Path.of("D:/workspace/sfm");
        Path first = repoRoot.resolve("one/src/main/java");
        Path second = repoRoot.resolve("two/src/main/java");
        Path file = second.resolve("example/A.java");
        String text = "package example; class A {}\n";
        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        repoRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 23))
                )),
                Optional.of(hello(8, List.of(
                        root("first", "main", first, "one/src/main/java"),
                        root("second", "main", second, "two/src/main/java")
                ))),
                12,
                5
        );

        assertEquals("second", adapted.request().orElseThrow().document().rootId());
    }

    @Test
    void windowsRootContainmentUsesNativeCaseInsensitivePathSemantics() {
        Path authorizedRoot = Path.of("D:/WORKSPACE/SFM");
        Path sourceRoot = Path.of("D:/workspace/sfm/src/main/java");
        Path file = Path.of("D:/Workspace/Sfm/src/main/java/example/A.java");
        String text = "package example; class A {}\n";

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        authorizedRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 23))
                )),
                Optional.of(hello(8, List.of(root(
                        "main", "main", sourceRoot, "src/main/java"
                )))),
                12,
                5
        );

        assertTrue(adapted.success());
        assertEquals("example/A.java", adapted.request().orElseThrow().document().rootRelativePath());
    }

    @Test
    void equallyDeepMappingsAndOutsideAuthorizationFailClosed() {
        Path repoRoot = Path.of("D:/workspace/sfm");
        Path sourceRoot = repoRoot.resolve("src/main/java");
        Path file = sourceRoot.resolve("A.java");
        String text = "class A {}\n";
        SFMContextContribution contribution = contribution(projection(
                repoRoot,
                file,
                text,
                text,
                new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 6))
        ));

        var ambiguous = new SFMDefinitionContextAdapter().adapt(
                contribution,
                Optional.of(hello(9, List.of(
                        root("a", "main", sourceRoot, "a"),
                        root("b", "main", sourceRoot, "b")
                ))),
                13,
                6
        );
        assertEquals(
                SFMDefinitionContextAdapter.DiagnosticCode.AUTHORIZED_ROOT_AMBIGUOUS,
                ambiguous.diagnostics().get(0).code()
        );

        Path outsideAuthorization = Path.of("D:/different/repo");
        var outside = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        outsideAuthorization,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 6))
                )),
                Optional.of(hello(9, List.of(root("main", "main", sourceRoot, "src/main/java")))),
                14,
                7
        );
        assertEquals(
                SFMDefinitionContextAdapter.DiagnosticCode.DOCUMENT_OUTSIDE_AUTHORIZED_ROOT,
                outside.diagnostics().get(0).code()
        );
    }

    @Test
    void canvasWhitespaceAndAbsentHandshakeProduceTypedDiagnostics() {
        Path repoRoot = Path.of("D:/workspace/sfm");
        Path sourceRoot = repoRoot.resolve("src/main/java");
        Path file = sourceRoot.resolve("A.java");
        String text = "class A {}\n";
        SFMContextContribution whitespace = contribution(projection(
                repoRoot,
                file,
                text,
                text,
                new SFMContextPosition.Canvas(500, 500, Optional.empty())
        ));

        var noHit = new SFMDefinitionContextAdapter().adapt(
                whitespace,
                Optional.of(hello(10, List.of(root("main", "main", sourceRoot, "src/main/java")))),
                15,
                8
        );
        assertFalse(noHit.success());
        assertEquals(
                SFMDefinitionContextAdapter.DiagnosticCode.CANVAS_WHITESPACE,
                noHit.diagnostics().get(0).code()
        );

        var noHandshake = new SFMDefinitionContextAdapter().adapt(
                whitespace,
                Optional.empty(),
                16,
                9
        );
        assertEquals(
                SFMDefinitionContextAdapter.DiagnosticCode.HANDSHAKE_NOT_READY,
                noHandshake.diagnostics().get(0).code()
        );
    }

    private static SFMContextContribution contribution(SFMContextDocumentProjection projection) {
        return new SFMContextContribution(ORIGIN, SFMContextGenerationEvidence.INITIAL, projection);
    }

    private static SFMContextDocumentProjection projection(
            Path authorizedRoot,
            Path path,
            String baselineText,
            String currentText,
            SFMContextPosition position
    ) {
        SFMPath baselinePath = SFMPath.fromNative(path);
        SFMPath baselineRoot = SFMPath.fromNative(authorizedRoot);
        String baselineHash = rawSha256(baselineText);
        SFMTextDocumentSnapshot baseline = new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                baselineText,
                SFMTextDocumentSnapshot.MutationCapability.EDITABLE_IN_MEMORY,
                Optional.of(baselinePath),
                Optional.of(baselineRoot),
                Optional.of(baselineHash),
                OptionalLong.of(baselineText.getBytes(StandardCharsets.UTF_8).length),
                Optional.empty(),
                Optional.of(lineEndings(baselineText)),
                Optional.empty(),
                List.of()
        );
        return SFMContextDocumentProjection.capture(
                "editor-7",
                baseline,
                currentText,
                !baselineText.equals(currentText),
                false,
                List.of(new SFMContextCursorProjection("primary", position, true, true)),
                List.of()
        );
    }

    private static SFMSymbolServerProtocol.ServerHello hello(
            long generation,
            List<RootFixture> roots
    ) {
        List<SFMDefinitionRequest.SourceRoot> requestRoots = roots.stream()
                .map(root -> new SFMDefinitionRequest.SourceRoot(
                        root.id, root.sourceSet, root.reportPath, "custom", true
                ))
                .toList();
        List<SFMSymbolServerProtocol.SourceRootMapping> mappings = roots.stream()
                .map(root -> new SFMSymbolServerProtocol.SourceRootMapping(
                        root.path.toString(), root.id, root.sourceSet, root.reportPath
                ))
                .toList();
        SFMSymbolServerProtocol.WorkspaceMetadata workspace = new SFMSymbolServerProtocol.WorkspaceMetadata(
                new SFMDefinitionRequest.Workspace(
                        "1.19.2",
                        SFMDefinitionRequest.ClasspathMode.BRANCH,
                        requestRoots,
                        "blake3:workspace",
                        Optional.of("blake3:index"),
                        "blake3:0000000000000000000000000000000000000000000000000000000000000000",
                        generation
                ),
                mappings
        );
        return new SFMSymbolServerProtocol.ServerHello(
                SFMSymbolServerProtocol.PROTOCOL_SCHEMA,
                "sfm-propagate-changes",
                "test",
                java.util.Set.copyOf(SFMSymbolServerProtocol.CLIENT_CAPABILITIES),
                1024 * 1024,
                8,
                workspace,
                "{}"
        );
    }

    private static RootFixture root(
            String id,
            String sourceSet,
            Path path,
            String reportPath
    ) {
        return new RootFixture(id, sourceSet, path, reportPath);
    }

    private static String rawSha256(String text) {
        return SFMDefinitionRequest.sha256(text).substring("sha256:".length());
    }

    private static SFMResolverTextResult.LineEndingKind lineEndings(String text) {
        if (text.contains("\r\n")) return SFMResolverTextResult.LineEndingKind.CRLF;
        if (text.contains("\n")) return SFMResolverTextResult.LineEndingKind.LF;
        return SFMResolverTextResult.LineEndingKind.NONE;
    }

    private record RootFixture(String id, String sourceSet, Path path, String reportPath) {
    }
}
