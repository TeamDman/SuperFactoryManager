package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayInstanceId;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneController;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMOverlayActionGrammarTests {
    private static final String EXACT_HISTORY = "id(sfm%3Ahistory)";
    private static final String PLACEMENT = SFMOverlaySceneContract.SceneState.defaults()
            .overlays()
            .get(0)
            .placement()
            .canonical();

    @Test
    void everyOverlayGrammarRequiresAnExplicitSelectorAndItsTypedArguments() {
        assertGrammar(SFMOverlayAction.Kind.VISIBILITY_SET,
                List.of("action all visible", "action " + EXACT_HISTORY + " hidden"),
                List.of("action", "action all", "action all true"));
        assertGrammar(SFMOverlayAction.Kind.VISIBILITY_TOGGLE,
                List.of("action all", "action " + EXACT_HISTORY),
                List.of("action", "action all visible"));
        assertGrammar(SFMOverlayAction.Kind.PLACEMENT_SET,
                List.of("action all " + PLACEMENT),
                List.of("action", "action all"));
        assertGrammar(SFMOverlayAction.Kind.INPUT_MODE_SET,
                List.of("action all passive", "action " + EXACT_HISTORY + " interactive"),
                List.of("action", "action all", "action all active"));
        assertGrammar(SFMOverlayAction.Kind.FOCUS_ACQUIRE,
                List.of("action " + EXACT_HISTORY),
                List.of("action", "action " + EXACT_HISTORY + " now"));
        assertGrammar(SFMOverlayAction.Kind.FOCUS_RELEASE,
                List.of("action focused"),
                List.of("action", "action focused now"));
        assertGrammar(SFMOverlayAction.Kind.Z_ORDER_SET,
                List.of("action all -1000000", "action " + EXACT_HISTORY + " 1000000"),
                List.of("action", "action all", "action all 1000001"));
    }

    @Test
    void executionRejectsNoncanonicalSelectorAndPlacementSpellings() {
        FakeRuntime runtime = new FakeRuntime(success());
        CommandDispatcher<SFMClientActionSource> placement = dispatcher(
                SFMOverlayAction.Kind.PLACEMENT_SET,
                runtime
        );
        CommandDispatcher<SFMClientActionSource> toggle = dispatcher(
                SFMOverlayAction.Kind.VISIBILITY_TOGGLE,
                runtime
        );

        assertThrows(CommandSyntaxException.class, () -> toggle.execute(
                "action id(sfm:history)",
                source(new ArrayList<>())
        ));
        assertThrows(CommandSyntaxException.class, () -> placement.execute(
                "action all " + PLACEMENT.replace("gui-safe(1,", "gui-safe(1.0,"),
                source(new ArrayList<>())
        ));
    }

    @Test
    void executionForwardsTypedOperationAndReportsEveryTypedTargetResult() throws Exception {
        FakeRuntime runtime = new FakeRuntime(new SFMOverlaySceneController.BatchResult(
                "all",
                2,
                3,
                List.of(
                        new SFMOverlaySceneController.TargetResult(
                                new OverlayInstanceId("sfm:history"),
                                SFMOverlaySceneController.Status.APPLIED,
                                "Visibility set to true"
                        ),
                        new SFMOverlaySceneController.TargetResult(
                                new OverlayInstanceId("sfm:second"),
                                SFMOverlaySceneController.Status.NO_CHANGE,
                                "Visibility already true"
                        )
                ),
                List.of()
        ));
        ArrayList<String> feedback = new ArrayList<>();

        int affected = dispatcher(SFMOverlayAction.Kind.VISIBILITY_SET, runtime).execute(
                "action all visible",
                source(feedback)
        );

        assertEquals(2, affected);
        assertEquals("all", runtime.selector.get().canonical());
        SFMOverlaySceneController.SetVisibility operation = assertInstanceOf(
                SFMOverlaySceneController.SetVisibility.class,
                runtime.operation.get()
        );
        assertTrue(operation.visible());
        assertEquals(List.of(
                "sfm:history [applied] Visibility set to true",
                "sfm:second [no-change] Visibility already true"
        ), feedback);
    }

    @Test
    void rejectedTypedTargetMakesTheInvocationFailAfterPublishingEvidence() {
        FakeRuntime runtime = new FakeRuntime(new SFMOverlaySceneController.BatchResult(
                EXACT_HISTORY,
                0,
                0,
                List.of(new SFMOverlaySceneController.TargetResult(
                        new OverlayInstanceId("sfm:history"),
                        SFMOverlaySceneController.Status.REJECTED,
                        "Cannot focus an overlay in passive mode"
                )),
                List.of()
        ));
        ArrayList<String> feedback = new ArrayList<>();

        CommandSyntaxException failure = assertThrows(CommandSyntaxException.class, () -> dispatcher(
                SFMOverlayAction.Kind.FOCUS_ACQUIRE,
                runtime
        ).execute("action " + EXACT_HISTORY, source(feedback)));

        assertTrue(failure.getMessage().contains("Cannot focus an overlay in passive mode"));
        assertEquals(List.of(
                "sfm:history [rejected] Cannot focus an overlay in passive mode"
        ), feedback);
    }

    private static void assertGrammar(
            SFMOverlayAction.Kind kind,
            List<String> executable,
            List<String> incomplete
    ) {
        CommandDispatcher<SFMClientActionSource> dispatcher = dispatcher(kind, new FakeRuntime(success()));
        SFMClientActionSource source = source(new ArrayList<>());
        executable.forEach(command -> assertTrue(isExecutable(dispatcher.parse(command, source)), command));
        incomplete.forEach(command -> assertFalse(isExecutable(dispatcher.parse(command, source)), command));
    }

    private static CommandDispatcher<SFMClientActionSource> dispatcher(
            SFMOverlayAction.Kind kind,
            SFMOverlayAction.RuntimeAccess runtime
    ) {
        SFMOverlayAction action = new SFMOverlayAction(kind, runtime);
        LiteralArgumentBuilder<SFMClientActionSource> root = LiteralArgumentBuilder.literal("action");
        action.configureCommandNode(root);
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(root);
        return dispatcher;
    }

    private static SFMClientActionSource source(List<String> feedback) {
        return new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true),
                message -> feedback.add(message.getString())
        );
    }

    private static boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        assertNotNull(parsed);
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }

    private static SFMOverlaySceneController.BatchResult success() {
        return new SFMOverlaySceneController.BatchResult(
                "all",
                0,
                0,
                List.of(new SFMOverlaySceneController.TargetResult(
                        new OverlayInstanceId("sfm:history"),
                        SFMOverlaySceneController.Status.NO_CHANGE,
                        "No change"
                )),
                List.of()
        );
    }

    private static final class FakeRuntime implements SFMOverlayAction.RuntimeAccess {
        private final SFMOverlaySceneController.BatchResult result;
        private final AtomicReference<SFMEntitySelector> selector = new AtomicReference<>();
        private final AtomicReference<SFMOverlaySceneController.Operation> operation = new AtomicReference<>();

        private FakeRuntime(SFMOverlaySceneController.BatchResult result) {
            this.result = result;
        }

        @Override
        public Iterable<String> overlayIds() {
            return List.of("sfm:history");
        }

        @Override
        public SFMOverlaySceneContract.SceneState scene() {
            return SFMOverlaySceneContract.SceneState.defaults();
        }

        @Override
        public SFMOverlaySceneController.BatchResult execute(
                SFMEntitySelector selector,
                SFMOverlaySceneController.Operation operation
        ) {
            this.selector.set(selector);
            this.operation.set(operation);
            return result;
        }
    }
}
