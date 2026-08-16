package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMFindReferencesControllerTests {
    private static final SFMContextOriginId DOCUMENT_ORIGIN =
            new SFMContextOriginId("sfm:text-editor", "panel-under-test", "document");
    private static final SFMWorkspacePanelId PANEL_ID = new SFMWorkspacePanelId(7);

    @Test
    void capturesTheFocusedDocumentOnceAndPresentsItsCompletedLookup() {
        SFMContextContribution captured = contribution("class Use { Target value; }\n", 12, 1);
        AtomicReference<SFMContextContribution> current = new AtomicReference<>(captured);
        CompletableFuture<SFMReferenceLookupService.Lookup> pending = new CompletableFuture<>();
        AtomicReference<SFMContextContribution> submitted = new AtomicReference<>();
        AtomicReference<SFMReferenceLookupService.Lookup> presented = new AtomicReference<>();
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMFindReferencesController controller = controller(
                current,
                contribution -> {
                    submitted.set(contribution);
                    return new SFMReferenceLookupService.Submission(pending, () -> { });
                },
                (context, host, sourcePanelId, lookup) -> {
                    assertSame(workspace, host);
                    assertEquals(PANEL_ID, sourcePanelId);
                    presented.set(lookup);
                    return true;
                }
        );

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, PANEL_ID),
                ignored -> { }
        ));
        assertSame(captured, submitted.get(), "lookup must receive the exact immutable focused contribution");

        SFMReferenceLookupService.Lookup lookup = lookup(result(
                List.of(usage("workspace://main/Use.java", 12)),
                SFMDefinitionResult.Completeness.COMPLETE,
                false
        ));
        pending.complete(lookup);
        assertSame(lookup, presented.get());
    }

    @Test
    void changedDocumentOrCursorRejectsTheAsyncResultAsStale() {
        SFMContextContribution captured = contribution("class Use { Target value; }\n", 12, 1);
        AtomicReference<SFMContextContribution> current = new AtomicReference<>(captured);
        CompletableFuture<SFMReferenceLookupService.Lookup> pending = new CompletableFuture<>();
        AtomicInteger presentations = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        ArrayList<Component> feedback = new ArrayList<>();
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMFindReferencesController controller = controller(
                current,
                ignored -> new SFMReferenceLookupService.Submission(pending, cancellations::incrementAndGet),
                (context, host, panel, lookup) -> {
                    presentations.incrementAndGet();
                    return true;
                }
        );

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, PANEL_ID),
                feedback::add
        ));
        current.set(contribution("class Use { Other value; }\n", 19, 2));
        controller.cancelStale(workspace);

        assertEquals(1, cancellations.get(), "stale work must be cancelled before it completes");
        pending.complete(lookup(result(List.of(), SFMDefinitionResult.Completeness.COMPLETE, false)));

        assertEquals(0, presentations.get());
        assertTrue(feedback.stream().anyMatch(message ->
                message.getString().contains("cancelled because the editor document, cursor, focus, or panel changed")));
    }

    @Test
    void aNewRequestForTheSameEditorCancelsThePreviousSubmission() {
        SFMContextContribution captured = contribution("class Use { Target value; }\n", 12, 1);
        AtomicReference<SFMContextContribution> current = new AtomicReference<>(captured);
        CompletableFuture<SFMReferenceLookupService.Lookup> first = new CompletableFuture<>();
        CompletableFuture<SFMReferenceLookupService.Lookup> second = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger firstCancellations = new AtomicInteger();
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMFindReferencesController controller = controller(
                current,
                ignored -> calls.getAndIncrement() == 0
                        ? new SFMReferenceLookupService.Submission(first, firstCancellations::incrementAndGet)
                        : new SFMReferenceLookupService.Submission(second, () -> { }),
                (context, host, panel, lookup) -> true
        );
        SFMClientActionContext context = new SFMClientActionContext(workspace, () -> true, PANEL_ID);

        assertTrue(controller.begin(context, ignored -> { }));
        assertTrue(controller.begin(context, ignored -> { }));

        assertEquals(1, firstCancellations.get());
        first.complete(lookup(result(List.of(), SFMDefinitionResult.Completeness.COMPLETE, false)));
        second.complete(lookup(result(List.of(), SFMDefinitionResult.Completeness.COMPLETE, false)));
    }

    @Test
    void removingTheWorkspaceCancelsEveryPendingSubmissionImmediately() {
        SFMContextContribution captured = contribution("class Use { Target value; }\n", 12, 1);
        AtomicReference<SFMContextContribution> current = new AtomicReference<>(captured);
        AtomicInteger cancellations = new AtomicInteger();
        CompletableFuture<SFMReferenceLookupService.Lookup> pending = new CompletableFuture<>();
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMFindReferencesController controller = controller(
                current,
                ignored -> new SFMReferenceLookupService.Submission(
                        pending,
                        cancellations::incrementAndGet
                ),
                (context, host, panel, lookup) -> true
        );

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, PANEL_ID),
                ignored -> { }
        ));
        controller.cancelWorkspace(workspace);
        pending.complete(lookup(result(List.of(), SFMDefinitionResult.Completeness.COMPLETE, false)));

        assertEquals(1, cancellations.get());
    }

    @Test
    void zeroManyAndPartialResultsAllOpenThePersistentExplorerPresenter() {
        List<SFMUsageAtPositionResult> cases = List.of(
                result(List.of(), SFMDefinitionResult.Completeness.COMPLETE, false),
                result(List.of(
                                usage("workspace://main/A.java", 1),
                                usage("workspace://main/B.java", 9),
                                usage("workspace://main/C.java", 17)),
                        SFMDefinitionResult.Completeness.COMPLETE, false),
                result(List.of(usage("workspace://main/Partial.java", 3)),
                        SFMDefinitionResult.Completeness.INCOMPLETE, true)
        );

        for (SFMUsageAtPositionResult expected : cases) {
            SFMContextContribution captured = contribution("class Use { Target value; }\n", 12, 1);
            AtomicReference<SFMContextContribution> current = new AtomicReference<>(captured);
            AtomicReference<SFMUsageAtPositionResult> presented = new AtomicReference<>();
            SFMScreenMultiplexer workspace = uninitializedWorkspace();
            SFMFindReferencesController controller = controller(
                    current,
                    ignored -> new SFMReferenceLookupService.Submission(
                            CompletableFuture.completedFuture(lookup(expected)), () -> { }),
                    (context, host, sourcePanelId, actual) -> {
                        presented.set(actual.result());
                        return true;
                    }
            );

            assertTrue(controller.begin(
                    new SFMClientActionContext(workspace, () -> true, PANEL_ID),
                    ignored -> { }
            ));
            assertSame(expected, presented.get(),
                    "zero, many, and incomplete results must all reach the persistent presenter");
        }
    }

    @Test
    void rejectsMissingFocusedDocumentBeforeSubmittingWork() {
        AtomicInteger submissions = new AtomicInteger();
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMFindReferencesController controller = new SFMFindReferencesController(
                () -> contribution -> {
                    submissions.incrementAndGet();
                    throw new AssertionError("lookup must not run without focused document context");
                },
                Runnable::run,
                ignored -> true,
                (context, host, sourcePanelId, lookup) -> true,
                new SFMFindReferencesController.WorkspaceState() {
                    @Override public SFMContextSnapshot snapshot(SFMScreenMultiplexer host) {
                        return new SFMContextSnapshot(1, 1, 1, Optional.empty(), List.of());
                    }

                    @Override public boolean containsPanel(
                            SFMScreenMultiplexer host,
                            SFMWorkspacePanelId panelId
                    ) {
                        return true;
                    }
                }
        );
        ArrayList<Component> feedback = new ArrayList<>();

        assertFalse(controller.begin(
                new SFMClientActionContext(workspace, () -> true, PANEL_ID),
                feedback::add
        ));
        assertEquals(0, submissions.get());
        assertTrue(feedback.stream().anyMatch(message ->
                message.getString().contains("focused editor has no active path-addressed text position")));
    }

    private static SFMFindReferencesController controller(
            AtomicReference<SFMContextContribution> current,
            SFMReferenceLookupService lookup,
            SFMFindReferencesController.Presenter presenter
    ) {
        return new SFMFindReferencesController(
                () -> lookup,
                Runnable::run,
                ignored -> true,
                presenter,
                new SFMFindReferencesController.WorkspaceState() {
                    @Override public SFMContextSnapshot snapshot(SFMScreenMultiplexer workspace) {
                        SFMContextContribution contribution = current.get();
                        return new SFMContextSnapshot(
                                1, 1, 1, Optional.of(contribution.originId()), List.of(contribution));
                    }

                    @Override public boolean containsPanel(
                            SFMScreenMultiplexer workspace,
                            SFMWorkspacePanelId panelId
                    ) {
                        return true;
                    }
                }
        );
    }

    private static SFMContextContribution contribution(String text, int byteOffset, long generation) {
        SFMTextDocumentSnapshot baseline = SFMTextDocumentSnapshot.literal(text);
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "editor-v3",
                baseline,
                text,
                false,
                false,
                List.of(new SFMContextCursorProjection(
                        "primary",
                        new SFMContextPosition.Text(
                                SFMTextDocumentRange.positionAtByteOffset(text, byteOffset)),
                        true,
                        true
                )),
                List.of()
        );
        return new SFMContextContribution(
                DOCUMENT_ORIGIN,
                new SFMContextGenerationEvidence(generation, generation, generation, 0),
                projection
        );
    }

    private static SFMReferenceLookupService.Lookup lookup(SFMUsageAtPositionResult result) {
        return new SFMReferenceLookupService.Lookup(hello(), result);
    }

    private static SFMUsageAtPositionResult result(
            List<SFMUsageAtPositionResult.Usage> usages,
            SFMDefinitionResult.Completeness completeness,
            boolean partial
    ) {
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "main", "main", "src", "declared", true);
        SFMDefinitionResult.AnalysisContext context = new SFMDefinitionResult.AnalysisContext(
                "1.19.2", "1.19.2", "17", "fixture", List.of(root),
                List.of(new SFMDefinitionResult.SourceSet("main", List.of("main"))),
                List.of(), SFMDefinitionRequest.ClasspathMode.BRANCH,
                hash('a'), "arborium-java/fixture", hash('b'));
        SFMDefinitionResult.DocumentIdentity document = new SFMDefinitionResult.DocumentIdentity(
                "workspace://main/Use.java", "main", "Use.java", "src/Use.java", "main",
                hash('c'), Optional.empty());
        return new SFMUsageAtPositionResult(
                SFMUsageAtPositionResult.SCHEMA,
                11, 3, 7,
                SFMDefinitionResult.Outcome.SUCCESS,
                context,
                document,
                new SFMDefinitionRequest.Position(1, 1, 0),
                usages.isEmpty() ? List.of() : List.of(symbol()),
                List.of(),
                usages,
                partial ? List.of(new SFMUsageAtPositionResult.SkippedCategory(
                        SFMUsageAtPositionResult.SkippedCategoryKind.DYNAMIC_DISPATCH,
                        "Dynamic targets are not guessed")) : List.of(),
                completeness,
                partial ? List.of(new SFMDefinitionResult.Diagnostic(
                        "java.partial", "warning", "Dependency sources are incomplete", Optional.empty())) : List.of(),
                partial ? List.of(new SFMDefinitionResult.RecoveryAction(
                        SFMDefinitionResult.RecoveryActionKind.REFRESH_DEPENDENCY_INDEX,
                        "Refresh dependency index", Optional.empty())) : List.of(),
                Optional.empty()
        );
    }

    private static SFMUsageAtPositionResult.Usage usage(String address, long start) {
        String relative = address.substring(address.lastIndexOf('/') + 1);
        SFMDefinitionResult.DefinitionSourceSpan span = new SFMDefinitionResult.DefinitionSourceSpan(
                address, "workspace", "main", relative, "src/" + relative, "main",
                hash('d'), Optional.empty(), start, start + 6, 1, start + 1, 1, start + 7);
        return new SFMUsageAtPositionResult.Usage(
                symbol(), SFMUsageAtPositionResult.UsageKind.TYPE_REFERENCE, span, "resolved");
    }

    private static SFMDefinitionResult.SymbolIdentity symbol() {
        return new SFMDefinitionResult.SymbolIdentity(
                "class", "example.Target", "Target", Optional.empty(), "example.Target");
    }

    private static SFMSymbolServerProtocol.ServerHello hello() {
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "main", "main", "src", "declared", true);
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                "1.19.2", SFMDefinitionRequest.ClasspathMode.BRANCH, List.of(root),
                hash('a'), Optional.empty(), hash('e'), 7);
        return new SFMSymbolServerProtocol.ServerHello(
                SFMSymbolServerProtocol.PROTOCOL_SCHEMA,
                "fixture-symbol-server", "1", Set.copyOf(SFMSymbolServerProtocol.CLIENT_CAPABILITIES),
                1_048_576, 8,
                new SFMSymbolServerProtocol.WorkspaceMetadata(
                        workspace,
                        List.of(new SFMSymbolServerProtocol.SourceRootMapping(
                                "D:\\workspace\\src", "main", "main", "src"))),
                "{}"
        );
    }

    private static String hash(char value) {
        return "blake3:" + String.valueOf(value).repeat(64);
    }

    private static SFMScreenMultiplexer uninitializedWorkspace() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (SFMScreenMultiplexer) ((sun.misc.Unsafe) field.get(null))
                    .allocateInstance(SFMScreenMultiplexer.class);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not allocate headless workspace test double", failure);
        }
    }
}
