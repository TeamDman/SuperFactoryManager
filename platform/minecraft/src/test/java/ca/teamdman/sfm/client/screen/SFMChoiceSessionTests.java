package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionAvailability;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionDispatcherCompiler;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionInvocationTrace;
import ca.teamdman.sfm.client.action.SFMClientActionRequirement;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class SFMChoiceSessionTests {
    private static final ResourceLocation FIRST = new ResourceLocation("sfm", "first");
    private static final ResourceLocation SECOND = new ResourceLocation("sfm", "second");
    private static final ResourceLocation REQUIRED = new ResourceLocation("sfm", "required");

    @AfterEach
    void clearSessions() {
        SFMChoiceSessionService.clearForTests();
    }

    @Test
    void sessionTreeContainsOnlyExactCanonicalChoices() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        Map<ResourceLocation, SFMClientAction<?>> actions = new LinkedHashMap<>();
        actions.put(FIRST, new CountingAction(executions));
        actions.put(SECOND, new CountingAction(executions));
        actions.put(REQUIRED, new RequiredAction(executions));
        SFMClientActionCommandTree global = SFMClientActionDispatcherCompiler.compileCommandTree(actions.entrySet());
        SFMChoiceSession session = SFMChoiceSessionService.create(
                List.of(
                        SFMActionChoice.invoke(FIRST, ""),
                        SFMActionChoice.invoke(REQUIRED, "fixed"),
                        SFMActionChoice.invoke(REQUIRED, "other")),
                SFMClientActionContext.create("captured", () -> true),
                actions::get,
                global);
        SFMClientActionCommandTree surface = session.activate();
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create("ambient", () -> true));

        assertEquals(List.of(
                "sfm action invoke sfm:first",
                "sfm action invoke sfm:required fixed",
                "sfm action invoke sfm:required other"), session.canonicalCommands());
        assertTrue(SFMClientActionExecutor.isExecutable(surface.parse(
                session.prefix() + "sfm:first", source)));
        assertTrue(SFMClientActionExecutor.isExecutable(surface.parse(
                session.prefix() + "sfm:required fixed", source)));
        assertTrue(SFMClientActionExecutor.isExecutable(surface.parse(
                session.prefix() + "sfm:required other", source)));
        assertFalse(SFMClientActionExecutor.isExecutable(surface.parse(
                session.prefix() + "sfm:required", source)));
        assertFalse(SFMClientActionExecutor.isExecutable(surface.parse(
                session.prefix() + "sfm:second", source)));
        String unknownSession = "sfm choose " + (session.id() + 1) + " sfm:first";
        assertFalse(SFMClientActionExecutor.isExecutable(surface.parse(unknownSession, source)));
        assertThrows(CommandSyntaxException.class, () -> surface.execute(unknownSession, source));
        assertFalse(SFMClientActionExecutor.isExecutable(surface.parse(
                session.prefix() + "sfm:required different", source)));

        List<String> suggestions = surface.getPaletteSuggestions(
                        session.prefix(), surface.parse(session.prefix(), source))
                .join().getList().stream().map(suggestion -> suggestion.getText()).toList();
        assertEquals(List.of("sfm:first", "sfm:required fixed", "sfm:required other"), suggestions);

        String fuzzy = session.prefix() + "req";
        assertEquals(List.of("sfm:required fixed", "sfm:required other"), surface.getPaletteSuggestions(
                        fuzzy, surface.parse(fuzzy, source))
                .join().getList().stream().map(suggestion -> suggestion.getText()).toList());
        assertEquals(1, surface.execute(session.prefix() + "sfm:required fixed", source));
        assertEquals(1, executions.get());
        assertTrue(session.invalidated());
        assertThrows(CommandSyntaxException.class,
                () -> surface.execute(session.prefix() + "sfm:required fixed", source));
    }

    @Test
    void constrainedExecutionRecordsTheCanonicalActionRatherThanTheEphemeralChoiceWrapper()
            throws Exception {
        ResourceLocation actionId = new ResourceLocation("sfm", "trace-choice");
        AtomicReference<SFMClientActionInvocationTrace.Provenance> observed = new AtomicReference<>();
        Map<ResourceLocation, SFMClientAction<?>> actions = Map.of(
                actionId,
                new TraceCapturingAction(observed)
        );
        SFMClientActionCommandTree global = SFMClientActionDispatcherCompiler.compileCommandTree(
                actions.entrySet()
        );
        SFMClientActionContext captured = SFMClientActionContext.create("captured", () -> true);
        SFMChoiceSession session = SFMChoiceSessionService.create(
                List.of(SFMActionChoice.invoke(actionId, "")),
                captured,
                actions::get,
                global
        );

        assertEquals(1, session.activate().execute(
                session.prefix() + actionId,
                new SFMClientActionSource(SFMClientActionContext.create("ambient", () -> true))
        ));
        assertTrue(observed.get() instanceof SFMClientActionInvocationTrace.RegisteredActionProvenance);
        assertEquals(
                "sfm action invoke sfm:trace-choice",
                observed.get().commandDraft()
        );
    }

    @Test
    void executionUsesCapturedContextAndFailureLeavesCurrentSessionAvailable() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        Object captured = new Object();
        ResourceLocation failingId = new ResourceLocation("sfm", "failing");
        Map<ResourceLocation, SFMClientAction<?>> actions = Map.of(
                failingId, new CapturedFailingAction(captured, attempts));
        SFMClientActionCommandTree global = SFMClientActionDispatcherCompiler.compileCommandTree(actions.entrySet());
        SFMChoiceSession session = SFMChoiceSessionService.create(
                List.of(SFMActionChoice.invoke(failingId, "")),
                SFMClientActionContext.create(captured, () -> true),
                actions::get,
                global);
        SFMClientActionCommandTree surface = session.activate();
        SFMClientActionSource ambient = new SFMClientActionSource(
                SFMClientActionContext.create(new Object(), () -> true));
        String command = session.prefix() + failingId;

        assertThrows(CommandSyntaxException.class, () -> surface.execute(command, ambient));
        assertFalse(session.invalidated());
        assertTrue(SFMChoiceSessionService.isCurrent(session));
        assertThrows(CommandSyntaxException.class, () -> surface.execute(command, ambient));
        assertEquals(2, attempts.get());
    }

    @Test
    void executionRetainsThePanelIdentityCapturedWhenTheSessionOpened() throws Exception {
        SFMScreenMultiplexer workspace = headlessWorkspace(SFMWorkspaceLayout.sideBySide(
                new FakePanel("left"), new FakePanel("right")));
        SFMWorkspacePanelId left = workspace.panelIds().get(0);
        SFMWorkspacePanelId right = workspace.panelIds().get(1);
        workspace.focusPanel(left);
        SFMClientActionContext captured = SFMClientActionContext.create(workspace, () -> true);
        workspace.focusPanel(right);

        ResourceLocation actionId = new ResourceLocation("sfm", "captured_panel");
        AtomicInteger executions = new AtomicInteger();
        Map<ResourceLocation, SFMClientAction<?>> actions = Map.of(
                actionId, new CapturedPanelAction(workspace, left, executions));
        SFMClientActionCommandTree global = SFMClientActionDispatcherCompiler.compileCommandTree(actions.entrySet());
        SFMChoiceSession session = SFMChoiceSessionService.create(
                List.of(SFMActionChoice.invoke(actionId, "")),
                captured,
                actions::get,
                global);

        assertEquals(left, captured.originatingPanelId());
        assertEquals(right, workspace.focusedPanelId());
        assertEquals(1, session.activate().execute(
                session.prefix() + actionId,
                new SFMClientActionSource(SFMClientActionContext.create(workspace, () -> true))));
        assertEquals(1, executions.get());
    }

    @Test
    void invalidationMakesAnAlreadyCompiledSurfaceStale() {
        AtomicInteger executions = new AtomicInteger();
        Map<ResourceLocation, SFMClientAction<?>> actions = Map.of(FIRST, new CountingAction(executions));
        SFMClientActionCommandTree global = SFMClientActionDispatcherCompiler.compileCommandTree(actions.entrySet());
        SFMChoiceSession session = SFMChoiceSessionService.create(
                List.of(SFMActionChoice.invoke(FIRST, "")),
                SFMClientActionContext.create(new Object(), () -> true),
                actions::get,
                global);
        SFMClientActionCommandTree surface = session.activate();
        SFMChoiceSessionService.invalidate(session);

        assertThrows(CommandSyntaxException.class, () -> surface.execute(
                session.prefix() + FIRST,
                new SFMClientActionSource(SFMClientActionContext.create(new Object(), () -> true))));
        assertEquals(0, executions.get());
    }

    @Test
    void serviceIsBoundedAndNeverReusesLiveProcessIds() {
        AtomicInteger executions = new AtomicInteger();
        Map<ResourceLocation, SFMClientAction<?>> actions = Map.of(FIRST, new CountingAction(executions));
        SFMClientActionCommandTree global = SFMClientActionDispatcherCompiler.compileCommandTree(actions.entrySet());
        SFMClientActionContext context = SFMClientActionContext.create(new Object(), () -> true);
        SFMChoiceSession first = SFMChoiceSessionService.create(
                List.of(SFMActionChoice.invoke(FIRST, "")), context, actions::get, global);
        long previousId = first.id();
        SFMChoiceSession newest = first;
        for (int index = 0; index < 16; index++) {
            newest = SFMChoiceSessionService.create(
                    List.of(SFMActionChoice.invoke(FIRST, "")), context, actions::get, global);
            assertTrue(newest.id() > previousId);
            previousId = newest.id();
        }

        assertTrue(first.invalidated());
        assertFalse(SFMChoiceSessionService.isCurrent(first));
        assertTrue(SFMChoiceSessionService.isCurrent(newest));
    }

    private record CountingAction(AtomicInteger executions) implements SFMClientAction<Object> {
        @Override
        public Component title() {
            return Component.literal("counting");
        }

        @Override
        public Component description() {
            return Component.literal("counting action");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return context -> SFMClientActionAvailability.available(context.originatingHost());
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            executions.incrementAndGet();
            return 1;
        }
    }

    private record TraceCapturingAction(
            AtomicReference<SFMClientActionInvocationTrace.Provenance> observed
    ) implements SFMClientAction<Object> {
        @Override
        public Component title() {
            return Component.literal("trace choice");
        }

        @Override
        public Component description() {
            return Component.literal("capture registered action provenance");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return context -> SFMClientActionAvailability.available(context.originatingHost());
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            observed.set(SFMClientActionInvocationTrace.current().orElseThrow());
            return 1;
        }
    }

    private static final class RequiredAction implements SFMClientAction<Object> {
        private final AtomicInteger executions;

        private RequiredAction(AtomicInteger executions) {
            this.executions = executions;
        }

        @Override
        public Component title() {
            return Component.literal("required");
        }

        @Override
        public Component description() {
            return Component.literal("required action");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return context -> SFMClientActionAvailability.available(context.originatingHost());
        }

        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                    "value", StringArgumentType.word()).executes(this::invoke));
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            executions.incrementAndGet();
            return 1;
        }
    }

    private record CapturedFailingAction(Object expected, AtomicInteger attempts)
            implements SFMClientAction<Object> {
        @Override
        public Component title() {
            return Component.literal("failing");
        }

        @Override
        public Component description() {
            return Component.literal("failing action");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return context -> SFMClientActionAvailability.available(context.originatingHost());
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context)
                throws CommandSyntaxException {
            assertEquals(expected, target);
            attempts.incrementAndGet();
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("deliberate failure")).create();
        }
    }

    private record CapturedPanelAction(
            SFMScreenMultiplexer expectedWorkspace,
            SFMWorkspacePanelId expectedPanel,
            AtomicInteger executions
    ) implements SFMClientAction<SFMClientActionContext> {
        @Override
        public Component title() {
            return Component.literal("captured panel");
        }

        @Override
        public Component description() {
            return Component.literal("captured panel action");
        }

        @Override
        public SFMClientActionRequirement<SFMClientActionContext> requirement() {
            return context -> SFMClientActionAvailability.available(context);
        }

        @Override
        public int execute(
                SFMClientActionContext target,
                CommandContext<SFMClientActionSource> context
        ) {
            assertSame(expectedWorkspace, target.originatingHost());
            assertEquals(expectedPanel, target.originatingPanelId());
            executions.incrementAndGet();
            return 1;
        }
    }

    private record FakePanel(String name) implements SFMScreenPanel {
        @Override
        public Component title() {
            return Component.literal(name);
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                SFMScreenPanelBounds bounds,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
        }
    }

    private static SFMScreenMultiplexer headlessWorkspace(SFMWorkspaceLayout layout)
            throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        SFMScreenMultiplexer workspace =
                (SFMScreenMultiplexer) unsafe.allocateInstance(SFMScreenMultiplexer.class);
        Field layoutField = SFMScreenMultiplexer.class.getDeclaredField("layout");
        layoutField.setAccessible(true);
        layoutField.set(workspace, layout);
        return workspace;
    }
}
