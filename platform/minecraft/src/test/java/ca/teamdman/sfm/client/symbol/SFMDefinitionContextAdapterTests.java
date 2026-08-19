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
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSourceRootIdentity;
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
    void resolverAuthorizedSubtreeComposesWithContainingWorkerSourceRoot() {
        Path repoRoot = Path.of("D:/workspace/sfm");
        Path sourceRoot = repoRoot.resolve("platform/minecraft/src/main/java");
        Path authorizedPackage = sourceRoot.resolve("ca/teamdman/sfm");
        Path file = authorizedPackage.resolve("OutputStatement.java");
        String text = "package ca.teamdman.sfm; class OutputStatement {}\n";

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        authorizedPackage,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 38))
                )),
                Optional.of(hello(8, List.of(root(
                        "main-java", "main", sourceRoot,
                        "platform/minecraft/src/main/java"
                )))),
                12,
                5
        );

        assertTrue(adapted.success(),
                "a document contained by both roots must not produce the historical "
                        + "'No worker source root contains the document within its resolver authorization' false negative");
        assertEquals("main-java", adapted.request().orElseThrow().document().rootId());
        assertEquals(
                "ca/teamdman/sfm/OutputStatement.java",
                adapted.request().orElseThrow().document().rootRelativePath()
        );
    }

    @Test
    void acquiredDependencySourceUsesItsNegotiatedRootPrefixAndContributedAddress() {
        Path dependencyRoot = Path.of("D:/cache/dependency-sources/forge");
        Path file = dependencyRoot.resolve("net/minecraftforge/ForgeType.java");
        String text = "package net.minecraftforge; class ForgeType {}\n";
        SFMSymbolServerProtocol.DependencySourceRootMapping dependency =
                new SFMSymbolServerProtocol.DependencySourceRootMapping(
                        dependencyRoot.toString(),
                        "dependency-source-0",
                        "dependency:forge:userdev",
                        "dependency/forge/userdev/loader-pipeline"
                );

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        dependencyRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 38))
                )),
                Optional.of(externalHello(12, List.of(), List.of(), List.of(dependency), List.of())),
                17,
                10
        );

        assertTrue(adapted.success());
        SFMDefinitionRequest.Document document = adapted.request().orElseThrow().document();
        assertEquals(
                "dependency-source://dependency-source-0/net/minecraftforge/ForgeType.java",
                document.address()
        );
        assertEquals("dependency-source-0", document.rootId());
        assertEquals("net/minecraftforge/ForgeType.java", document.rootRelativePath());
        assertEquals(
                "dependency/forge/userdev/loader-pipeline/net/minecraftforge/ForgeType.java",
                document.reportPath()
        );
        assertEquals("dependency:forge:userdev", document.sourceSet());
        assertEquals(Optional.of(SFMDefinitionRequest.sha256(text)), document.diskContentHash());
        assertEquals(document, new SFMJavaInteractionMap.Request(
                18,
                10,
                adapted.request().orElseThrow().workspace(),
                document
        ).document());
    }

    @Test
    void retainedDependencyRootIdentityDisambiguatesOnePhysicalTreeWithMultipleSemanticRoots() {
        Path sharedRoot = Path.of("D:/cache/forge/combined-deobfuscated.filetree");
        Path file = sharedRoot.resolve("net/minecraft/network/chat/contents/TranslatableContents.java");
        String text = "package net.minecraft.network.chat.contents; class TranslatableContents {}\n";
        SFMSymbolServerProtocol.DependencySourceRootMapping forge =
                new SFMSymbolServerProtocol.DependencySourceRootMapping(
                        sharedRoot.toString(),
                        "dependency-source-3",
                        "dependency:forge:userdev",
                        "dependency/forge/userdev/loader-pipeline"
                );
        SFMSymbolServerProtocol.DependencySourceRootMapping minecraft =
                new SFMSymbolServerProtocol.DependencySourceRootMapping(
                        sharedRoot.toString(),
                        "dependency-source-6",
                        "dependency:minecraft:main",
                        "dependency/minecraft/main/minecraft-pipeline"
                );
        SFMTextDocumentSourceRootIdentity identity = new SFMTextDocumentSourceRootIdentity(
                "dependency-source",
                "dependency-source",
                forge.rootId(),
                forge.sourceSet(),
                forge.reportPrefix()
        );
        SFMSymbolServerProtocol.ServerHello hello = externalHello(
                13,
                List.of(),
                List.of(),
                List.of(forge, minecraft),
                List.of()
        );

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        sharedRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(
                                text, 0, text.indexOf("TranslatableContents"))),
                        Optional.of(identity)
                )),
                Optional.of(hello),
                18,
                10
        );

        assertTrue(adapted.success());
        SFMDefinitionRequest.Document document = adapted.request().orElseThrow().document();
        assertEquals("dependency-source-3", document.rootId());
        assertEquals("dependency:forge:userdev", document.sourceSet());
        assertEquals(
                "dependency/forge/userdev/loader-pipeline/"
                        + "net/minecraft/network/chat/contents/TranslatableContents.java",
                document.reportPath()
        );

        var withoutIdentity = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        sharedRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(
                                text, 0, text.indexOf("TranslatableContents")))
                )),
                Optional.of(hello),
                19,
                10
        );
        assertEquals(
                SFMDefinitionContextAdapter.DiagnosticCode.AUTHORIZED_ROOT_AMBIGUOUS,
                withoutIdentity.diagnostics().get(0).code(),
                "an unproven semantic identity must still fail closed"
        );
    }

    @Test
    void staleRetainedDependencyRootIdentityFailsClosed() {
        Path sharedRoot = Path.of("D:/cache/forge/combined-deobfuscated.filetree");
        Path file = sharedRoot.resolve("net/minecraft/network/chat/contents/TranslatableContents.java");
        String text = "package net.minecraft.network.chat.contents; class TranslatableContents {}\n";
        SFMSymbolServerProtocol.DependencySourceRootMapping forge =
                new SFMSymbolServerProtocol.DependencySourceRootMapping(
                        sharedRoot.toString(),
                        "dependency-source-3",
                        "dependency:forge:userdev",
                        "dependency/forge/userdev/loader-pipeline"
                );
        SFMTextDocumentSourceRootIdentity stale = new SFMTextDocumentSourceRootIdentity(
                "dependency-source",
                "dependency-source",
                forge.rootId(),
                forge.sourceSet(),
                "dependency/forge/userdev/stale"
        );

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        sharedRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(
                                text, 0, text.indexOf("TranslatableContents"))),
                        Optional.of(stale)
                )),
                Optional.of(externalHello(
                        13, List.of(), List.of(), List.of(forge), List.of())),
                20,
                10
        );

        assertEquals(
                SFMDefinitionContextAdapter.DiagnosticCode.ROOT_METADATA_MISMATCH,
                adapted.diagnostics().get(0).code()
        );
    }

    @Test
    void managedJdkSourceUsesNegotiatedResolverIdentityWithoutWorkspaceFallback() {
        Path jdkRoot = Path.of("D:/cache/jdk/java-17/abc123/tree");
        Path file = jdkRoot.resolve("java.base/java/lang/String.java");
        String text = "package java.lang; public final class String {}\n";
        SFMDefinitionRequest.SourceRoot requestRoot = new SFMDefinitionRequest.SourceRoot(
                "jdk-java-17-abc123",
                "jdk:java-17",
                "jdk/java-17/abc123",
                "jdk",
                true
        );
        SFMSymbolServerProtocol.SourceRootMapping orderedRoot =
                new SFMSymbolServerProtocol.SourceRootMapping(
                        jdkRoot.toString(),
                        requestRoot.id(),
                        requestRoot.sourceSet(),
                        requestRoot.path()
                );
        SFMSymbolServerProtocol.ManagedSourceRootMapping managedRoot =
                new SFMSymbolServerProtocol.ManagedSourceRootMapping(
                        "jdk-source",
                        "jdk-source",
                        requestRoot.path(),
                        jdkRoot.toString(),
                        requestRoot.id(),
                        requestRoot.sourceSet(),
                        Optional.of(requestRoot.path()),
                        Optional.of("jdk/java-17/abc123")
                );

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        jdkRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 38))
                )),
                Optional.of(externalHello(
                        13,
                        List.of(requestRoot),
                        List.of(orderedRoot),
                        List.of(),
                        List.of(managedRoot)
                )),
                19,
                11
        );

        assertTrue(adapted.success());
        SFMDefinitionRequest.Document document = adapted.request().orElseThrow().document();
        assertEquals(
                "jdk-source://jdk-java-17-abc123/java.base/java/lang/String.java",
                document.address()
        );
        assertEquals("java.base/java/lang/String.java", document.rootRelativePath());
        assertEquals(
                "jdk/java-17/abc123/java.base/java/lang/String.java",
                document.reportPath()
        );
        assertEquals(document, new SFMJavaInteractionMap.Request(
                20,
                11,
                adapted.request().orElseThrow().workspace(),
                document
        ).document());
    }

    @Test
    void inconsistentManagedJdkIdentityFailsClosedInsteadOfUsingOrderedRoot() {
        Path jdkRoot = Path.of("D:/cache/jdk/java-17/abc123/tree");
        Path file = jdkRoot.resolve("java.base/java/lang/String.java");
        String text = "package java.lang; public final class String {}\n";
        SFMDefinitionRequest.SourceRoot requestRoot = new SFMDefinitionRequest.SourceRoot(
                "jdk-java-17-abc123", "jdk:java-17", "jdk/java-17/abc123", "jdk", true);
        SFMSymbolServerProtocol.SourceRootMapping orderedRoot =
                new SFMSymbolServerProtocol.SourceRootMapping(
                        jdkRoot.toString(), requestRoot.id(), requestRoot.sourceSet(), requestRoot.path());
        SFMSymbolServerProtocol.ManagedSourceRootMapping inconsistent =
                new SFMSymbolServerProtocol.ManagedSourceRootMapping(
                        "jdk-source",
                        "jdk-source",
                        "jdk/java-17/different",
                        jdkRoot.toString(),
                        requestRoot.id(),
                        requestRoot.sourceSet(),
                        Optional.of(requestRoot.path()),
                        Optional.of("jdk/java-17/abc123")
                );

        var adapted = new SFMDefinitionContextAdapter().adapt(
                contribution(projection(
                        jdkRoot,
                        file,
                        text,
                        text,
                        new SFMContextPosition.Text(SFMContextTextCoordinates.atLineColumn(text, 0, 38))
                )),
                Optional.of(externalHello(
                        14,
                        List.of(requestRoot),
                        List.of(orderedRoot),
                        List.of(),
                        List.of(inconsistent)
                )),
                21,
                12
        );

        assertFalse(adapted.success());
        assertEquals(
                SFMDefinitionContextAdapter.DiagnosticCode.ROOT_METADATA_MISMATCH,
                adapted.diagnostics().get(0).code()
        );
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
        return projection(
                authorizedRoot,
                path,
                baselineText,
                currentText,
                position,
                Optional.empty()
        );
    }

    private static SFMContextDocumentProjection projection(
            Path authorizedRoot,
            Path path,
            String baselineText,
            String currentText,
            SFMContextPosition position,
            Optional<SFMTextDocumentSourceRootIdentity> sourceRootIdentity
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
                List.of(),
                sourceRootIdentity
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

    private static SFMSymbolServerProtocol.ServerHello externalHello(
            long generation,
            List<SFMDefinitionRequest.SourceRoot> requestRoots,
            List<SFMSymbolServerProtocol.SourceRootMapping> mappings,
            List<SFMSymbolServerProtocol.DependencySourceRootMapping> dependencyMappings,
            List<SFMSymbolServerProtocol.ManagedSourceRootMapping> managedMappings
    ) {
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
                mappings,
                dependencyMappings,
                managedMappings
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
