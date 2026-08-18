package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMContextActionsOpenAction;
import ca.teamdman.sfm.client.action.SFMJumpToDefinitionAction;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.context.SFMContextSnapshot;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDefaults;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageSituationCatalog;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJumpToDefinitionActionTests {
    private static final SFMContextOriginId DOCUMENT_ORIGIN =
            new SFMContextOriginId("sfm:text-editor", "panel-under-test", "document");

    @Test
    void canonicalActionExposesDirectLookupAndExactSelectionRoutes() {
        SFMJumpToDefinitionAction action = new SFMJumpToDefinitionAction();
        LiteralArgumentBuilder<SFMClientActionSource> builder =
                LiteralArgumentBuilder.literal(SFMJumpToDefinitionAction.ID.toString());
        action.configureCommandNode(builder);
        var node = builder.build();

        assertEquals("sfm:symbol/definition/open", SFMJumpToDefinitionAction.ID.toString());
        assertNotNull(node.getCommand(), "the canonical action must invoke lookup directly");
        assertEquals(Set.of("select"), node.getChildren().stream()
                .map(child -> child.getName()).collect(java.util.stream.Collectors.toSet()));
        var session = node.getChild("select").getChildren().iterator().next();
        assertEquals("definition_session", session.getName());
        var index = session.getChildren().iterator().next();
        var label = (ArgumentCommandNode<SFMClientActionSource, String>)
                index.getChildren().iterator().next();
        assertEquals("definition_label", label.getName());
        assertEquals(
                StringArgumentType.StringType.GREEDY_PHRASE,
                ((StringArgumentType) label.getType()).getType(),
                "definition labels contain file URI punctuation and must consume the exact remaining command"
        );
    }

    @Test
    void f12AndAltEnterAreTextEditorScopedDirectAndContextualRoutes() {
        List<SFMKeyBinding> definitionBindings = SFMKeyBindingDefaults.definitions().stream()
                .filter(binding -> binding.actionId().equals(SFMJumpToDefinitionAction.ID.toString()))
                .toList();
        assertEquals(1, definitionBindings.size());

        SFMKeyBinding f12 = definitionBindings.get(0);
        assertEquals("sfm action invoke sfm:symbol/definition/open", f12.commandDraft());
        assertEquals(SFMKeyboardUsageSituations.TEXT_EDITOR, f12.situationId());
        assertEquals(Set.of(), f12.sequence().strokes().get(0).modifiers());

        SFMKeyBinding altEnter = SFMKeyBindingDefaults.definitions().stream()
                .filter(binding -> binding.actionId().equals(SFMContextActionsOpenAction.ID.toString()))
                .findFirst().orElseThrow();
        assertEquals("sfm action invoke sfm:context/actions/open", altEnter.commandDraft());
        assertEquals(SFMKeyboardUsageSituations.TEXT_EDITOR, altEnter.situationId());
        assertEquals(Set.of(SFMKeyModifier.ALT), altEnter.sequence().strokes().get(0).modifiers());
        assertEquals(
                List.of(
                        SFMKeyboardUsageSituations.TEXT_EDITOR,
                        SFMKeyboardUsageSituations.DEFAULT,
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyboardUsageSituations.GLOBAL
                ),
                new SFMKeyboardUsageSituationCatalog(SFMKeyboardUsageSituations.builtIns())
                        .ancestry(SFMKeyboardUsageSituations.TEXT_EDITOR)
        );
    }

    @Test
    void oneManyAndIncompleteNoneAreRoutedBeforeAnyUiMutation() {
        SFMDefinitionResult.Definition first = definition("a.A", "A.java", 6, "A");
        SFMDefinitionResult.Definition second = definition("b.A", "B.java", 6, "A");

        SFMDefinitionOutcomeRouter.Open one = assertInstanceOf(
                SFMDefinitionOutcomeRouter.Open.class,
                SFMDefinitionOutcomeRouter.route(result(
                        SFMDefinitionResult.Outcome.SUCCESS,
                        SFMDefinitionResult.Completeness.COMPLETE,
                        List.of(first)))
        );
        assertEquals(first, one.definition());

        SFMDefinitionOutcomeRouter.Choose many = assertInstanceOf(
                SFMDefinitionOutcomeRouter.Choose.class,
                SFMDefinitionOutcomeRouter.route(result(
                        SFMDefinitionResult.Outcome.AMBIGUOUS,
                        SFMDefinitionResult.Completeness.COMPLETE,
                        List.of(second, first)))
        );
        assertEquals(List.of(first, second), many.definitions());

        SFMDefinitionOutcomeRouter.Report none = assertInstanceOf(
                SFMDefinitionOutcomeRouter.Report.class,
                SFMDefinitionOutcomeRouter.route(result(
                        SFMDefinitionResult.Outcome.NO_DEFINITION,
                        SFMDefinitionResult.Completeness.INCOMPLETE,
                        List.of()))
        );
        assertTrue(none.incomplete());
        assertTrue(none.message().contains("index is incomplete"));
    }

    @Test
    void incompleteIndexNoSymbolFalseNegativeMessageRemainsExact() {
        SFMDefinitionOutcomeRouter.Report report = assertInstanceOf(
                SFMDefinitionOutcomeRouter.Report.class,
                SFMDefinitionOutcomeRouter.route(result(
                        SFMDefinitionResult.Outcome.NO_SYMBOL,
                        SFMDefinitionResult.Completeness.INCOMPLETE,
                        List.of()))
        );

        assertTrue(report.incomplete());
        assertEquals(
                "No symbol is present at the captured editor position (dependency/source index is incomplete)",
                report.message()
        );
    }

    @Test
    void staleAndUnavailableFailuresBecomeStableUserFacingOutcomes() {
        assertEquals(
                "A newer jump-to-definition request superseded this one",
                SFMJumpToDefinitionController.failureMessage(new CompletionException(
                        new SFMDefinitionQueryCoordinator.StaleResponseException()))
        );
        assertTrue(SFMJumpToDefinitionController.failureMessage(
                new IllegalStateException("worker executable missing")).contains("worker executable missing"));
    }

    @Test
    void immediatePaletteCompletionUsesTheExactCapturedPaletteToWorkspaceRoute() {
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution("class Use { Target value; }\n", 12, 1));
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(1);
        AtomicBoolean paletteRouteCurrent = new AtomicBoolean(true);
        AtomicInteger navigations = new AtomicInteger();
        List<Component> feedback = new ArrayList<>();
        CompletableFuture<SFMDefinitionLookupService.Lookup> completed = CompletableFuture.completedFuture(
                new SFMDefinitionLookupService.Lookup(
                        hello(),
                        result(
                                SFMDefinitionResult.Outcome.SUCCESS,
                                SFMDefinitionResult.Completeness.COMPLETE,
                                List.of(definition("example.Target", "Target.java", 6, "Target"))
                        )
                )
        );
        SFMJumpToDefinitionController controller = controller(
                completed,
                ignored -> false,
                navigations,
                currentContribution
        );

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, paletteRouteCurrent::get, panelId),
                feedback::add
        ));

        assertEquals(1, navigations.get(),
                "an already-completed lookup must survive its exact constrained-palette handoff");
        assertTrue(feedback.stream().anyMatch(message -> message.getString().contains("Definition opened")));
    }

    @Test
    void immediatePaletteFailureRemainsVisibleBeforeThePaletteCloses() {
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution("class Use { Target value; }\n", 12, 1));
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(1);
        List<Component> feedback = new ArrayList<>();
        SFMJumpToDefinitionController controller = controller(
                CompletableFuture.failedFuture(new IllegalStateException("worker executable missing")),
                ignored -> false,
                new AtomicInteger(),
                currentContribution
        );

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, panelId),
                feedback::add
        ));

        assertTrue(feedback.stream().anyMatch(message ->
                        message.getString().contains("worker executable missing")),
                "an immediate failure must be reported while the invoking palette is still current");
    }

    @Test
    void paletteCompletionMayFinishAfterTheExactPaletteReturnsToItsWorkspace() {
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution("class Use { Target value; }\n", 12, 1));
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(1);
        AtomicBoolean paletteRouteCurrent = new AtomicBoolean(true);
        AtomicBoolean workspaceCurrent = new AtomicBoolean(false);
        AtomicInteger navigations = new AtomicInteger();
        CompletableFuture<SFMDefinitionLookupService.Lookup> pending = new CompletableFuture<>();
        SFMJumpToDefinitionController controller = controller(
                pending, ignored -> workspaceCurrent.get(), navigations, currentContribution);

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, paletteRouteCurrent::get, panelId),
                ignored -> { }
        ));
        paletteRouteCurrent.set(false);
        workspaceCurrent.set(true);
        pending.complete(successfulLookup());

        assertEquals(1, navigations.get(),
                "the same invocation remains valid across its exact palette-to-workspace transition");
    }

    @Test
    void completionDoesNotCrossFromTheCapturedRouteIntoAnUnrelatedScreen() {
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution("class Use { Target value; }\n", 12, 1));
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(1);
        AtomicBoolean paletteRouteCurrent = new AtomicBoolean(true);
        CompletableFuture<SFMDefinitionLookupService.Lookup> pending = new CompletableFuture<>();
        AtomicInteger navigations = new AtomicInteger();
        SFMJumpToDefinitionController controller = controller(
                pending, ignored -> false, navigations, currentContribution);

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, paletteRouteCurrent::get, panelId),
                ignored -> { }
        ));
        paletteRouteCurrent.set(false);
        pending.complete(successfulLookup());

        assertEquals(0, navigations.get(),
                "an unrelated current screen must not inherit the captured palette's completion");
    }

    @Test
    void changedDocumentMakesAnAsyncResultStaleBeforeNavigation() {
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution("class Use { Target value; }\n", 12, 1));
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(1);
        CompletableFuture<SFMDefinitionLookupService.Lookup> pending = new CompletableFuture<>();
        AtomicInteger navigations = new AtomicInteger();
        List<Component> feedback = new ArrayList<>();
        SFMJumpToDefinitionController controller = controller(
                pending, ignored -> true, navigations, currentContribution);

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, panelId),
                feedback::add
        ));
        currentContribution.set(contribution("class Use { Other value; }\n", 12, 1));
        pending.complete(successfulLookup());

        assertEquals(0, navigations.get());
        assertTrue(feedback.stream().anyMatch(message ->
                message.getString().contains("source document bytes changed")));
    }

    @Test
    void cursorSelectionFocusAndCaptureChangesDoNotInvalidateAnExplicitRequest() {
        String text = "class Use { Target value; }\n";
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution(text, 12, 1));
        TestWorkspaceState state = new TestWorkspaceState(currentContribution);
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(1);
        CompletableFuture<SFMDefinitionLookupService.Lookup> pending = new CompletableFuture<>();
        AtomicInteger navigations = new AtomicInteger();
        List<Component> feedback = new ArrayList<>();
        SFMJumpToDefinitionController controller = controller(
                ignored -> new SFMDefinitionLookupService.Submission(pending, () -> { }),
                ignored -> true,
                navigations,
                state
        );

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, panelId),
                feedback::add
        ));
        currentContribution.set(contributionWithSelection(text, 13, 99));
        state.focusedOrigin.set(Optional.empty());
        pending.complete(successfulLookup());

        assertEquals(1, navigations.get(),
                "cursor, selection, contributor, and capture changes must not invalidate stable bytes/address");
        assertTrue(feedback.stream().noneMatch(message -> message.getString().contains("ignored")));
    }

    @Test
    void changedResolverAddressRejectsWithoutBlamingTheCursor() {
        String text = "class Use { Target value; }\n";
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                addressedContribution(text, "file:///D:/workspace/src/Use.java", 12, 1));
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        CompletableFuture<SFMDefinitionLookupService.Lookup> pending = new CompletableFuture<>();
        AtomicInteger navigations = new AtomicInteger();
        List<Component> feedback = new ArrayList<>();
        SFMJumpToDefinitionController controller = controller(
                pending, ignored -> true, navigations, currentContribution);

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, new SFMWorkspacePanelId(1)),
                feedback::add
        ));
        currentContribution.set(addressedContribution(
                text, "file:///D:/workspace/src/Replaced.java", 12, 2));
        pending.complete(successfulLookup());

        assertEquals(0, navigations.get());
        assertTrue(feedback.stream().anyMatch(message ->
                message.getString().contains("source document resolver/address changed")));
        assertTrue(feedback.stream().noneMatch(message ->
                message.getString().contains("cursor changed")));
    }

    @Test
    void changedProviderGenerationRejectsWithTheProviderDimension() {
        String text = "class Use { Target value; }\n";
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution(text, 12, 1, 7));
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMWorkspacePanelId panelId = new SFMWorkspacePanelId(1);
        CompletableFuture<SFMDefinitionLookupService.Lookup> pending = new CompletableFuture<>();
        AtomicInteger navigations = new AtomicInteger();
        List<Component> feedback = new ArrayList<>();
        SFMJumpToDefinitionController controller = controller(
                pending, ignored -> true, navigations, currentContribution);

        assertTrue(controller.begin(
                new SFMClientActionContext(workspace, () -> true, panelId),
                feedback::add
        ));
        currentContribution.set(contribution(text, 12, 2, 8));
        pending.complete(successfulLookup());

        assertEquals(0, navigations.get());
        assertTrue(feedback.stream().anyMatch(message ->
                message.getString().contains("semantic provider generation changed")));
    }

    @Test
    void removedAndReplacedPanelEntriesHaveDistinctTypedReasons() {
        String text = "class Use { Target value; }\n";
        for (boolean removed : List.of(true, false)) {
            AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                    contribution(text, 12, 1));
            TestWorkspaceState state = new TestWorkspaceState(currentContribution);
            SFMScreenMultiplexer workspace = uninitializedWorkspace();
            CompletableFuture<SFMDefinitionLookupService.Lookup> pending = new CompletableFuture<>();
            AtomicInteger navigations = new AtomicInteger();
            List<Component> feedback = new ArrayList<>();
            SFMJumpToDefinitionController controller = controller(
                    ignored -> new SFMDefinitionLookupService.Submission(pending, () -> { }),
                    ignored -> true,
                    navigations,
                    state
            );

            assertTrue(controller.begin(
                    new SFMClientActionContext(workspace, () -> true, new SFMWorkspacePanelId(1)),
                    feedback::add
            ));
            state.panelEntry.set(removed ? null : new Object());
            pending.complete(successfulLookup());

            assertEquals(0, navigations.get());
            String expected = removed ? "source panel was removed" : "source panel entry was replaced";
            assertTrue(feedback.stream().anyMatch(message -> message.getString().contains(expected)));
        }
    }

    @Test
    void newerExplicitRequestRejectsAnOutOfOrderOlderCompletion() {
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution("class Use { Target value; }\n", 12, 1));
        TestWorkspaceState state = new TestWorkspaceState(currentContribution);
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        CompletableFuture<SFMDefinitionLookupService.Lookup> first = new CompletableFuture<>();
        CompletableFuture<SFMDefinitionLookupService.Lookup> second = new CompletableFuture<>();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger firstCancellation = new AtomicInteger();
        AtomicInteger navigations = new AtomicInteger();
        List<Component> feedback = new ArrayList<>();
        SFMJumpToDefinitionController controller = controller(
                ignored -> calls.getAndIncrement() == 0
                        ? new SFMDefinitionLookupService.Submission(first, firstCancellation::incrementAndGet)
                        : new SFMDefinitionLookupService.Submission(second, () -> { }),
                ignored -> true,
                navigations,
                state
        );
        SFMClientActionContext context = new SFMClientActionContext(
                workspace, () -> true, new SFMWorkspacePanelId(1));

        assertTrue(controller.begin(context, feedback::add));
        assertTrue(controller.begin(context, feedback::add));
        assertEquals(1, firstCancellation.get());

        first.complete(successfulLookup());
        second.complete(successfulLookup());

        assertEquals(1, navigations.get(), "only the newest request may navigate");
        assertTrue(feedback.stream().anyMatch(message ->
                message.getString().contains("newer explicit request superseded")));
    }

    @Test
    void puppetGateAcceptsThenHoldsCompletionUntilChangedBytesAreRejected() {
        String original = "class Use { Target value; }\n";
        AtomicReference<SFMContextContribution> currentContribution = new AtomicReference<>(
                contribution(original, 12, 1));
        TestWorkspaceState state = new TestWorkspaceState(currentContribution);
        CompletableFuture<SFMDefinitionLookupService.Lookup> result = new CompletableFuture<>();
        AtomicReference<Runnable> timeoutAction = new AtomicReference<>();
        SFMNavigationPuppetCompletionGate gate = SFMNavigationPuppetCompletionGate.forTests(
                () -> true,
                System::nanoTime,
                (timeout, action) -> timeoutAction.set(action)
        );
        AtomicInteger navigations = new AtomicInteger();
        List<Component> feedback = new ArrayList<>();
        SFMScreenMultiplexer workspace = uninitializedWorkspace();
        SFMJumpToDefinitionController controller = controller(
                ignored -> new SFMDefinitionLookupService.Submission(result, () -> { }),
                ignored -> true,
                navigations,
                state,
                gate
        );

        try (SFMNavigationPuppetCompletionGate.Lease lease = gate.arm(Duration.ofSeconds(5))) {
            assertTrue(controller.begin(
                    new SFMClientActionContext(workspace, () -> true, new SFMWorkspacePanelId(1)),
                    feedback::add
            ));
            assertTrue(lease.observation().accepted(), "the controller must acknowledge the captured request");

            result.complete(successfulLookup());
            assertTrue(lease.observation().completionHeld(), "the completed lookup must be held before validation");
            assertEquals(0, navigations.get());

            currentContribution.set(contribution(
                    "class Use { Other value; }\n",
                    12,
                    2
            ));
            lease.release();

            assertEquals(0, navigations.get(), "changed bytes must never reach navigation");
            assertEquals(
                    Optional.of("DOCUMENT_CONTENT_CHANGED"),
                    lease.observation().rejectionCode()
            );
            assertTrue(feedback.stream().anyMatch(message ->
                    message.getString().equals(
                            "Jump to definition ignored: the source document bytes changed"
                    )));
            assertTrue(timeoutAction.get() != null, "the gate must retain its bounded timeout");
        }
    }

    private static SFMJumpToDefinitionController controller(
            CompletableFuture<SFMDefinitionLookupService.Lookup> result,
            java.util.function.Predicate<SFMScreenMultiplexer> currentWorkspace,
            AtomicInteger navigations,
            AtomicReference<SFMContextContribution> currentContribution
    ) {
        SFMDefinitionLookupService lookup = ignored ->
                new SFMDefinitionLookupService.Submission(result, () -> { });
        return controller(lookup, currentWorkspace, navigations, new TestWorkspaceState(currentContribution));
    }

    private static SFMJumpToDefinitionController controller(
            SFMDefinitionLookupService lookup,
            java.util.function.Predicate<SFMScreenMultiplexer> currentWorkspace,
            AtomicInteger navigations,
            SFMNavigationWorkspaceState workspaceState
    ) {
        return controller(
                lookup,
                currentWorkspace,
                navigations,
                workspaceState,
                SFMNavigationCompletionGate.DIRECT
        );
    }

    private static SFMJumpToDefinitionController controller(
            SFMDefinitionLookupService lookup,
            java.util.function.Predicate<SFMScreenMultiplexer> currentWorkspace,
            AtomicInteger navigations,
            SFMNavigationWorkspaceState workspaceState,
            SFMNavigationCompletionGate completionGate
    ) {
        return new SFMJumpToDefinitionController(
                SFMJumpToDefinitionAction.ID,
                () -> lookup,
                Runnable::run,
                currentWorkspace,
                (context, title, choices) -> { },
                (workspace, sourcePanelId, hello, definition) -> {
                    navigations.incrementAndGet();
                    return new SFMDefinitionNavigation.Result(
                            SFMDefinitionNavigation.Status.FOCUSED_EXISTING,
                            sourcePanelId,
                            "Definition opened"
                    );
                },
                new SFMDefinitionChoiceSessionService(),
                workspaceState,
                completionGate
        );
    }

    private static SFMDefinitionLookupService.Lookup successfulLookup() {
        return new SFMDefinitionLookupService.Lookup(
                hello(),
                result(
                        SFMDefinitionResult.Outcome.SUCCESS,
                        SFMDefinitionResult.Completeness.COMPLETE,
                        List.of(definition("example.Target", "Target.java", 6, "Target"))
                )
        );
    }

    private static SFMContextContribution contribution(String text, int byteOffset, long generation) {
        return contribution(text, byteOffset, generation, 0);
    }

    private static SFMContextContribution contribution(
            String text,
            int byteOffset,
            long generation,
            long providerGeneration
    ) {
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
                new SFMContextGenerationEvidence(
                        generation, generation, generation, providerGeneration),
                projection
        );
    }

    private static SFMContextContribution contributionWithSelection(
            String text,
            int byteOffset,
            long generation
    ) {
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
                List.of(new SFMContextSelectionProjection(
                        "primary-selection",
                        List.of(new SFMTextDocumentRange(
                                SFMTextDocumentRange.positionAtByteOffset(text, 0),
                                SFMTextDocumentRange.positionAtByteOffset(text, 5)
                        )),
                        true
                ))
        );
        return new SFMContextContribution(
                DOCUMENT_ORIGIN,
                new SFMContextGenerationEvidence(generation, 1, generation, 0),
                projection
        );
    }

    private static SFMContextContribution addressedContribution(
            String text,
            String address,
            int byteOffset,
            long generation
    ) {
        SFMPath path = SFMPath.parse(address);
        SFMPath root = SFMPath.parse("file:///D:/workspace/src/");
        SFMTextDocumentSnapshot baseline = new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.READ_ONLY,
                Optional.of(path),
                Optional.of(root),
                Optional.of(sha256Witness(text).substring("sha256:".length())),
                OptionalLong.of(text.getBytes(StandardCharsets.UTF_8).length),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.LF),
                Optional.empty(),
                List.of()
        );
        SFMContextDocumentProjection projection = SFMContextDocumentProjection.capture(
                "editor-v3",
                baseline,
                text,
                false,
                true,
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

    private static final class TestWorkspaceState implements SFMNavigationWorkspaceState {
        private final AtomicReference<SFMContextContribution> currentContribution;
        private final AtomicReference<Object> panelEntry = new AtomicReference<>(new Object());
        private final AtomicReference<Optional<SFMContextOriginId>> focusedOrigin =
                new AtomicReference<>();

        private TestWorkspaceState(AtomicReference<SFMContextContribution> currentContribution) {
            this.currentContribution = currentContribution;
            this.focusedOrigin.set(Optional.of(currentContribution.get().originId()));
        }

        @Override
        public SFMContextSnapshot snapshot(SFMScreenMultiplexer workspace) {
            SFMContextContribution contribution = currentContribution.get();
            return new SFMContextSnapshot(
                    99,
                    77,
                    55,
                    focusedOrigin.get(),
                    List.of(contribution)
            );
        }

        @Override
        public Optional<Object> panelEntryIdentity(
                SFMScreenMultiplexer workspace,
                SFMWorkspacePanelId panelId
        ) {
            return Optional.ofNullable(panelEntry.get());
        }
    }

    private static SFMSymbolServerProtocol.ServerHello hello() {
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "main", "main", "src", "declared", true);
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                "1.19.2", SFMDefinitionRequest.ClasspathMode.BRANCH, List.of(root),
                "blake3:classpath", Optional.empty(),
                "blake3:0000000000000000000000000000000000000000000000000000000000000000", 1);
        return new SFMSymbolServerProtocol.ServerHello(
                SFMSymbolServerProtocol.PROTOCOL_SCHEMA,
                "sfm-symbol-server",
                "1",
                Set.copyOf(SFMSymbolServerProtocol.CLIENT_CAPABILITIES),
                1024 * 1024,
                8,
                new SFMSymbolServerProtocol.WorkspaceMetadata(
                        workspace,
                        List.of(new SFMSymbolServerProtocol.SourceRootMapping(
                                "D:\\workspace\\src", "main", "main", "src"))
                ),
                "{}"
        );
    }

    private static SFMScreenMultiplexer uninitializedWorkspace() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (SFMScreenMultiplexer) ((Unsafe) field.get(null))
                    .allocateInstance(SFMScreenMultiplexer.class);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not allocate a headless workspace test double", failure);
        }
    }

    static SFMDefinitionResult.Definition definition(
            String qualifiedName,
            String file,
            long startByte,
            String name
    ) {
        return definitionAtAddress(
                qualifiedName,
                "workspace://main/" + file,
                file,
                startByte,
                name
        );
    }

    static SFMDefinitionResult.Definition definitionAtAddress(
            String qualifiedName,
            String address,
            String file,
            long startByte,
            String name
    ) {
        String resolverId = address.startsWith("workspace://") ? "workspace" : "sfm:file";
        SFMDefinitionResult.SymbolIdentity symbol = new SFMDefinitionResult.SymbolIdentity(
                "class", qualifiedName, name, Optional.empty(), qualifiedName);
        SFMDefinitionResult.DefinitionSourceSpan span = new SFMDefinitionResult.DefinitionSourceSpan(
                address, resolverId, "main", file, file, "main",
                "blake3:0000000000000000000000000000000000000000000000000000000000000000",
                Optional.of(sha256Witness("class " + name + " {}\n")),
                startByte, startByte + name.length(), 1, startByte + 1,
                1, startByte + name.length() + 1
        );
        return new SFMDefinitionResult.Definition(symbol, span, span, "resolved");
    }

    static String sha256Witness(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static SFMDefinitionResult result(
            SFMDefinitionResult.Outcome outcome,
            SFMDefinitionResult.Completeness completeness,
            List<SFMDefinitionResult.Definition> definitions
    ) {
        SFMDefinitionRequest.SourceRoot root = new SFMDefinitionRequest.SourceRoot(
                "main", "main", "src", "declared", true);
        return new SFMDefinitionResult(
                SFMDefinitionResult.SCHEMA,
                1, 1, 1, outcome,
                new SFMDefinitionResult.AnalysisContext(
                        "1.19.2", "1.19.2", "17", "jdk", List.of(root),
                        List.of(new SFMDefinitionResult.SourceSet("main", List.of("main"))),
                        List.of(), SFMDefinitionRequest.ClasspathMode.BRANCH,
                        "blake3:classpath", "arborium", "blake3:index"
                ),
                new SFMDefinitionResult.DocumentIdentity(
                        "file:///D:/workspace/src/Use.java", "main", "Use.java", "Use.java", "main",
                        "sha256:0000000000000000000000000000000000000000000000000000000000000000",
                        Optional.empty()
                ),
                new SFMDefinitionRequest.Position(1, 1, 0),
                List.of(), definitions, completeness, List.of(), List.of(), Optional.empty()
        );
    }
}
