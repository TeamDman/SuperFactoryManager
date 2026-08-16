package ca.teamdman.sfm.client.symbol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMUsageAtPositionContractTests {
    @Test
    void requestAndResultRoundTripTheFrozenRustWireContract() {
        SFMUsageAtPositionRequest request = request();
        SFMDefinitionResult.SymbolIdentity symbol = new SFMDefinitionResult.SymbolIdentity(
                "local-variable", "q.Use#method()", "value", Optional.empty(),
                "q.Use#method()#local:value@37"
        );
        SFMDefinitionResult.DefinitionSourceSpan span = span("workspace://main/q/Use.java", 37, 42);
        SFMUsageAtPositionResult result = new SFMUsageAtPositionResult(
                SFMUsageAtPositionResult.SCHEMA,
                request.requestId(),
                request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                SFMDefinitionResult.Outcome.SUCCESS,
                context(request),
                new SFMDefinitionResult.DocumentIdentity(
                        request.document().address(), request.document().rootId(),
                        request.document().rootRelativePath(), request.document().reportPath(),
                        request.document().sourceSet(), request.document().contentHash(),
                        request.document().diskContentHash()
                ),
                request.position(),
                List.of(symbol),
                List.of(new SFMDefinitionResult.Definition(symbol, span, span, "resolved")),
                List.of(new SFMUsageAtPositionResult.Usage(
                        symbol,
                        SFMUsageAtPositionResult.UsageKind.LOCAL_REFERENCE,
                        span,
                        "resolved"
                )),
                List.of(new SFMUsageAtPositionResult.SkippedCategory(
                        SFMUsageAtPositionResult.SkippedCategoryKind.DYNAMIC_DISPATCH,
                        "Dynamic targets are not guessed."
                )),
                SFMDefinitionResult.Completeness.COMPLETE,
                List.of(),
                List.of(),
                Optional.empty()
        );

        assertEquals(request, SFMDefinitionJsonCodec.decodeUsageRequest(
                SFMDefinitionJsonCodec.encodeUsageRequest(request)));
        assertEquals(result, SFMDefinitionJsonCodec.decodeUsageResult(
                SFMDefinitionJsonCodec.encodeUsageResult(result)));
        assertEquals(
                SFMDefinitionJsonCodec.encodeUsageResult(result),
                SFMDefinitionJsonCodec.encodeUsageResult(
                        SFMDefinitionJsonCodec.decodeUsageResult(
                                SFMDefinitionJsonCodec.encodeUsageResult(result)
                        )
                ),
                "the versioned result must have a stable canonical JSON spelling"
        );
        assertEquals(true, result.matches(request));
    }

    @Test
    void jdkIsAFirstClassSourceRootKind() {
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "jdk-java-17-fixture",
                "jdk:java-17",
                "jdk/java-17/fixture",
                "jdk",
                true
        );
        assertEquals("jdk", root.kind());
    }

    private static SFMUsageAtPositionRequest request() {
        String text = "package q; class Use { void method() { int value = 1; } }\n";
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "main", "main", "source", "custom", true
        );
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                "1.19.2",
                SFMDefinitionRequest.ClasspathMode.ISOLATED,
                List.of(root),
                "blake3:classpath",
                Optional.empty(),
                hash('a'),
                7
        );
        SFMDefinitionRequest.Document document = SFMDefinitionRequest.Document.sha256(
                "workspace://main/q/Use.java",
                "main",
                "q/Use.java",
                "source/q/Use.java",
                "main",
                text,
                Optional.empty()
        );
        return new SFMUsageAtPositionRequest(
                SFMUsageAtPositionRequest.SCHEMA,
                11,
                3,
                workspace,
                document,
                SFMDefinitionRequest.Position.fromText(text, 1, text.indexOf("value") + 1L)
        );
    }

    private static SFMDefinitionResult.AnalysisContext context(SFMUsageAtPositionRequest request) {
        return new SFMDefinitionResult.AnalysisContext(
                "1.19.2",
                "1.19.2",
                "17",
                "fixture",
                request.workspace().sourceRoots(),
                List.of(new SFMDefinitionResult.SourceSet("main", List.of("main"))),
                List.of(),
                request.workspace().classpathMode(),
                request.workspace().classpathFingerprint(),
                "arborium-java/fixture",
                hash('b')
        );
    }

    private static SFMDefinitionResult.DefinitionSourceSpan span(
            String address,
            long start,
            long end
    ) {
        return new SFMDefinitionResult.DefinitionSourceSpan(
                address,
                "workspace",
                "main",
                "q/Use.java",
                "source/q/Use.java",
                "main",
                hash('c'),
                Optional.of(hash('d').replace("blake3:", "sha256:")),
                start,
                end,
                1,
                start + 1,
                1,
                end + 1
        );
    }

    private static String hash(char value) {
        return "blake3:" + String.valueOf(value).repeat(64);
    }
}
