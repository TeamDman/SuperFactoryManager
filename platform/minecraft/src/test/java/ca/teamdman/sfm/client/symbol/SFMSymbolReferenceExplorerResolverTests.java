package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMSymbolReferenceExplorerResolverTests {
    @Test
    void canonicalRootParseAndPrintRoundTrips() {
        SFMSymbolReferenceResultRepository.ResultId id =
                new SFMSymbolReferenceResultRepository.ResultId("result-42");
        SFMPath root = SFMSymbolReferenceResultRepository.rootPath(id);

        assertEquals("symbol-references://result-42/", root.canonical());
        assertEquals(root, SFMPath.parse(root.canonical()));
        assertEquals(Optional.of(id), SFMSymbolReferenceResultRepository.parseRoot(root));
        assertEquals(
                Optional.empty(),
                SFMSymbolReferenceResultRepository.parseRoot(SFMPath.parse(
                        "symbol-references://result-42/analysis/"
                ))
        );
    }

    @Test
    void zeroReferenceResultStillMakesCompletenessAndAnalysisVisible() {
        Fixture fixture = new Fixture();
        SFMSymbolReferenceResultRepository.StoredResult stored = fixture.repository.append(
                fixture.result(List.of(), SFMDefinitionResult.Completeness.COMPLETE, false),
                fixture.hello
        );

        List<SFMExplorerEntry> root = fixture.children(stored.rootPath(), fixture.repository.generation(), 20);
        assertEquals(List.of("Analysis"), labels(root));
        List<String> analysis = labels(fixture.children(root.get(0).path(), fixture.repository.generation(), 20));
        assertTrue(analysis.contains("Completeness: complete"));
        assertTrue(analysis.contains("References: 0"));
        assertTrue(analysis.contains("Diagnostics (0)"));
        assertTrue(analysis.contains("Recovery actions (0)"));
        assertTrue(analysis.contains("Skipped categories (0)"));
    }

    @Test
    void oneReferenceHasCategoryFileAndExactSpanLeaf() {
        Fixture fixture = new Fixture();
        SFMUsageAtPositionResult.Usage usage = fixture.usage(
                SFMUsageAtPositionResult.UsageKind.INVOCATION,
                "workspace://main/q/Use.java",
                "q/Use.java",
                80,
                86,
                4,
                9
        );
        SFMSymbolReferenceResultRepository.StoredResult stored = fixture.repository.append(
                fixture.result(List.of(usage), SFMDefinitionResult.Completeness.COMPLETE, false),
                fixture.hello
        );
        long generation = fixture.repository.generation();

        SFMExplorerEntry category = fixture.children(stored.rootPath(), generation, 20).get(1);
        assertEquals("Invocation (1)", category.label());
        SFMExplorerEntry file = fixture.children(category.path(), generation, 20).get(0);
        assertEquals("q/Use.java (1)", file.label());
        SFMExplorerEntry leaf = fixture.children(file.path(), generation, 20).get(0);
        assertFalse(leaf.expandable());

        SFMSymbolReferenceResultRepository.LeafLookup lookup = fixture.resolver.lookupLeaf(leaf.path())
                .orElseThrow();
        assertSame(usage, lookup.usage());
        assertSame(usage.span(), lookup.sourceSpan());
        assertSame(fixture.hello, lookup.serverHello());
        assertEquals(stored.id(), lookup.resultId());
    }

    @Test
    void manyReferencesHaveStableCategoryFileAndSpanOrdering() {
        Fixture fixture = new Fixture();
        List<SFMUsageAtPositionResult.Usage> shuffled = List.of(
                fixture.usage(SFMUsageAtPositionResult.UsageKind.INVOCATION,
                        "workspace://main/z/Z.java", "z/Z.java", 30, 34, 3, 5),
                fixture.usage(SFMUsageAtPositionResult.UsageKind.IMPORT,
                        "workspace://main/q/Use.java", "q/Use.java", 5, 20, 1, 6),
                fixture.usage(SFMUsageAtPositionResult.UsageKind.INVOCATION,
                        "workspace://main/q/Use.java", "q/Use.java", 90, 94, 7, 4),
                fixture.usage(SFMUsageAtPositionResult.UsageKind.INVOCATION,
                        "workspace://main/q/Use.java", "q/Use.java", 40, 44, 4, 4)
        );
        SFMSymbolReferenceResultRepository.StoredResult stored = fixture.repository.append(
                fixture.result(shuffled, SFMDefinitionResult.Completeness.COMPLETE, false),
                fixture.hello
        );
        long generation = fixture.repository.generation();

        List<SFMExplorerEntry> root = fixture.children(stored.rootPath(), generation, 20);
        assertEquals(List.of("Analysis", "Import (1)", "Invocation (3)"), labels(root));
        SFMExplorerEntry invocation = root.get(2);
        List<SFMExplorerEntry> files = fixture.children(invocation.path(), generation, 20);
        assertEquals(List.of("q/Use.java (2)", "z/Z.java (1)"), labels(files));
        List<SFMExplorerEntry> spans = fixture.children(files.get(0).path(), generation, 20);
        assertTrue(spans.get(0).label().startsWith("line 4:4"));
        assertTrue(spans.get(1).label().startsWith("line 7:4"));
    }

    @Test
    void partialResultExposesDiagnosticsRecoveryAndSkippedCategories() {
        Fixture fixture = new Fixture();
        SFMSymbolReferenceResultRepository.StoredResult stored = fixture.repository.append(
                fixture.result(List.of(), SFMDefinitionResult.Completeness.INCOMPLETE, true),
                fixture.hello
        );
        long generation = fixture.repository.generation();
        SFMExplorerEntry analysis = fixture.children(stored.rootPath(), generation, 20).get(0);
        List<SFMExplorerEntry> analysisChildren = fixture.children(analysis.path(), generation, 20);

        assertTrue(labels(analysisChildren).contains("Completeness: incomplete"));
        SFMExplorerEntry diagnostics = named(analysisChildren, "Diagnostics (1)");
        assertEquals(
                List.of("warning java.partial: Dependency sources are incomplete"),
                labels(fixture.children(diagnostics.path(), generation, 20))
        );
        SFMExplorerEntry recovery = named(analysisChildren, "Recovery actions (1)");
        assertEquals(
                List.of("Refresh dependency index — sfm-propagate-changes.exe symbol index refresh"),
                labels(fixture.children(recovery.path(), generation, 20))
        );
        SFMExplorerEntry skipped = named(analysisChildren, "Skipped categories (1)");
        assertEquals(
                List.of("Dynamic Dispatch: Dynamic targets are not guessed"),
                labels(fixture.children(skipped.path(), generation, 20))
        );
    }

    @Test
    void appendPreservesPriorPathsAndPayloadsWhileAdvancingGeneration() {
        Fixture fixture = new Fixture();
        SFMUsageAtPositionResult.Usage firstUsage = fixture.usage(
                SFMUsageAtPositionResult.UsageKind.TYPE_REFERENCE,
                "workspace://main/a/A.java", "a/A.java", 10, 11, 1, 11
        );
        SFMSymbolReferenceResultRepository.StoredResult first = fixture.repository.append(
                fixture.result(List.of(firstUsage), SFMDefinitionResult.Completeness.COMPLETE, false),
                fixture.hello
        );
        long firstGeneration = fixture.repository.generation();
        SFMPath firstLeaf = fixture.firstLeaf(first.rootPath(), firstGeneration);
        SFMSymbolReferenceResultRepository.LeafLookup before = fixture.resolver.lookupLeaf(firstLeaf)
                .orElseThrow();

        SFMSymbolReferenceResultRepository.StoredResult second = fixture.repository.append(
                fixture.result(List.of(fixture.usage(
                        SFMUsageAtPositionResult.UsageKind.FIELD_REFERENCE,
                        "workspace://main/b/B.java", "b/B.java", 50, 51, 5, 2
                )), SFMDefinitionResult.Completeness.COMPLETE, false),
                fixture.hello
        );

        assertEquals("symbol-references://result-1/", first.rootPath().canonical());
        assertEquals("symbol-references://result-2/", second.rootPath().canonical());
        assertEquals(firstLeaf, SFMPath.parse(firstLeaf.canonical()));
        assertSame(before, fixture.resolver.lookupLeaf(firstLeaf).orElseThrow());
        assertSame(firstUsage, fixture.resolver.lookupLeaf(firstLeaf).orElseThrow().usage());
        assertThrows(
                SFMExplorerResolver.StaleGenerationException.class,
                () -> fixture.repository.snapshot(firstGeneration)
        );
        assertTrue(fixture.repository.node(first.rootPath()).isPresent());
    }

    @Test
    void pagingIsBoundedAndContinuationIsDeterministic() {
        Fixture fixture = new Fixture(2);
        SFMSymbolReferenceResultRepository.StoredResult stored = fixture.repository.append(
                fixture.result(List.of(), SFMDefinitionResult.Completeness.INCOMPLETE, true),
                fixture.hello
        );
        long generation = fixture.repository.generation();
        SFMPath analysis = fixture.children(stored.rootPath(), generation, 20).get(0).path();

        SFMExplorerResolver.ChildPage first = fixture.page(analysis, generation, Optional.empty(), 100);
        assertEquals(2, first.entries().size());
        assertEquals(Optional.of("offset-2"), first.continuation());
        assertEquals(9, first.observedEntries());
        SFMExplorerResolver.ChildPage second = fixture.page(
                analysis,
                generation,
                first.continuation(),
                100
        );
        assertEquals(2, second.entries().size());
        assertEquals(Optional.of("offset-4"), second.continuation());
    }

    @Test
    void cancellationAndRepeatedLeafLookupAreSafe() {
        Fixture fixture = new Fixture();
        SFMUsageAtPositionResult.Usage usage = fixture.usage(
                SFMUsageAtPositionResult.UsageKind.LOCAL_REFERENCE,
                "workspace://main/q/Use.java", "q/Use.java", 12, 17, 2, 3
        );
        SFMSymbolReferenceResultRepository.StoredResult stored = fixture.repository.append(
                fixture.result(List.of(usage), SFMDefinitionResult.Completeness.COMPLETE, false),
                fixture.hello
        );
        SFMPath leaf = fixture.firstLeaf(stored.rootPath(), fixture.repository.generation());
        SFMSymbolReferenceResultRepository.LeafLookup first = fixture.resolver.lookupLeaf(leaf).orElseThrow();
        SFMSymbolReferenceResultRepository.LeafLookup second = fixture.resolver.lookupLeaf(leaf).orElseThrow();
        assertSame(first, second);
        assertEquals(usage.span(), second.sourceSpan());

        SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
        cancellation.cancel();
        CompletionException failure = assertThrows(
                CompletionException.class,
                () -> fixture.resolver.describe(stored.rootPath(), cancellation).join()
        );
        assertTrue(failure.getCause() instanceof java.util.concurrent.CancellationException);
    }

    private static List<String> labels(List<SFMExplorerEntry> entries) {
        return entries.stream().map(SFMExplorerEntry::label).toList();
    }

    private static SFMExplorerEntry named(List<SFMExplorerEntry> entries, String label) {
        return entries.stream().filter(entry -> entry.label().equals(label)).findFirst().orElseThrow();
    }

    private static final class Fixture {
        private final SFMSymbolReferenceResultRepository repository =
                new SFMSymbolReferenceResultRepository();
        private final SFMSymbolReferenceExplorerResolver resolver;
        private final SFMDefinitionRequest.Workspace workspace;
        private final SFMSymbolServerProtocol.ServerHello hello;
        private final SFMDefinitionResult.SymbolIdentity symbol = new SFMDefinitionResult.SymbolIdentity(
                "class", "q.Use", "Use", Optional.empty(), "q.Use"
        );

        private Fixture() {
            this(64);
        }

        private Fixture(int maximumPageSize) {
            resolver = new SFMSymbolReferenceExplorerResolver(repository, Runnable::run, maximumPageSize);
            SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                    "main", "main", "source", "custom", true
            );
            workspace = new SFMDefinitionRequest.Workspace(
                    "1.19.2",
                    SFMDefinitionRequest.ClasspathMode.ISOLATED,
                    List.of(root),
                    "blake3:classpath",
                    Optional.empty(),
                    hash('a'),
                    7
            );
            SFMSymbolServerProtocol.WorkspaceMetadata metadata =
                    new SFMSymbolServerProtocol.WorkspaceMetadata(
                            workspace,
                            List.of(new SFMSymbolServerProtocol.SourceRootMapping(
                                    Path.of("D:/fixture/source").toAbsolutePath().normalize().toString(),
                                    "main",
                                    "main",
                                    "source"
                            ))
                    );
            hello = new SFMSymbolServerProtocol.ServerHello(
                    SFMSymbolServerProtocol.PROTOCOL_SCHEMA,
                    "fixture-symbol-server",
                    "1.0.0",
                    Set.copyOf(SFMSymbolServerProtocol.CLIENT_CAPABILITIES),
                    1_048_576,
                    8,
                    metadata,
                    "{\"fixture\":true}"
            );
        }

        private SFMUsageAtPositionResult result(
                List<SFMUsageAtPositionResult.Usage> usages,
                SFMDefinitionResult.Completeness completeness,
                boolean partial
        ) {
            SFMDefinitionResult.AnalysisContext context = new SFMDefinitionResult.AnalysisContext(
                    "1.19.2",
                    "1.19.2",
                    "17",
                    "fixture",
                    workspace.sourceRoots(),
                    List.of(new SFMDefinitionResult.SourceSet("main", List.of("main"))),
                    List.of(),
                    workspace.classpathMode(),
                    workspace.classpathFingerprint(),
                    "arborium-java/fixture",
                    hash('b')
            );
            SFMDefinitionResult.DocumentIdentity document = new SFMDefinitionResult.DocumentIdentity(
                    "workspace://main/q/Use.java",
                    "main",
                    "q/Use.java",
                    "source/q/Use.java",
                    "main",
                    hash('c'),
                    Optional.empty()
            );
            return new SFMUsageAtPositionResult(
                    SFMUsageAtPositionResult.SCHEMA,
                    11,
                    3,
                    workspace.workspaceGeneration(),
                    SFMDefinitionResult.Outcome.SUCCESS,
                    context,
                    document,
                    new SFMDefinitionRequest.Position(1, 1, 0),
                    usages.isEmpty() ? List.of() : List.of(symbol),
                    List.of(),
                    usages,
                    partial
                            ? List.of(new SFMUsageAtPositionResult.SkippedCategory(
                                    SFMUsageAtPositionResult.SkippedCategoryKind.DYNAMIC_DISPATCH,
                                    "Dynamic targets are not guessed"
                            ))
                            : List.of(),
                    completeness,
                    partial
                            ? List.of(new SFMDefinitionResult.Diagnostic(
                                    "java.partial",
                                    "warning",
                                    "Dependency sources are incomplete",
                                    Optional.empty()
                            ))
                            : List.of(),
                    partial
                            ? List.of(new SFMDefinitionResult.RecoveryAction(
                                    SFMDefinitionResult.RecoveryActionKind.REFRESH_DEPENDENCY_INDEX,
                                    "Refresh dependency index",
                                    Optional.of("sfm-propagate-changes.exe symbol index refresh")
                            ))
                            : List.of(),
                    Optional.empty()
            );
        }

        private SFMUsageAtPositionResult.Usage usage(
                SFMUsageAtPositionResult.UsageKind kind,
                String address,
                String relativePath,
                long start,
                long end,
                long line,
                long column
        ) {
            SFMDefinitionResult.DefinitionSourceSpan span = new SFMDefinitionResult.DefinitionSourceSpan(
                    address,
                    "workspace",
                    "main",
                    relativePath,
                    "source/" + relativePath,
                    "main",
                    hash('d'),
                    Optional.empty(),
                    start,
                    end,
                    line,
                    column,
                    line,
                    column + end - start
            );
            return new SFMUsageAtPositionResult.Usage(symbol, kind, span, "resolved");
        }

        private List<SFMExplorerEntry> children(SFMPath parent, long generation, int pageSize) {
            ArrayList<SFMExplorerEntry> answer = new ArrayList<>();
            Optional<String> continuation = Optional.empty();
            do {
                SFMExplorerResolver.ChildPage page = page(parent, generation, continuation, pageSize);
                answer.addAll(page.entries());
                continuation = page.continuation();
            } while (continuation.isPresent());
            return List.copyOf(answer);
        }

        private SFMExplorerResolver.ChildPage page(
                SFMPath parent,
                long generation,
                Optional<String> continuation,
                int pageSize
        ) {
            return resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(
                    parent,
                    continuation,
                    pageSize,
                    generation,
                    new SFMExplorerCancellationToken()
            )).join();
        }

        private SFMPath firstLeaf(SFMPath root, long generation) {
            List<SFMExplorerEntry> rootChildren = children(root, generation, 20);
            SFMExplorerEntry category = rootChildren.stream()
                    .filter(entry -> !entry.path().segments().get(0).equals("analysis"))
                    .findFirst()
                    .orElseThrow();
            SFMExplorerEntry file = children(category.path(), generation, 20).get(0);
            return children(file.path(), generation, 20).get(0).path();
        }
    }

    private static String hash(char value) {
        return "blake3:" + String.valueOf(value).repeat(64);
    }
}
