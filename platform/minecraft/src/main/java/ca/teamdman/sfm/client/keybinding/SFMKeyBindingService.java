package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionInvocationTrace;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMClientCommandInsertion;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.SFMCommandDraftScreen;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

public final class SFMKeyBindingService {
    public static final SFMKeyBindingService INSTANCE = new SFMKeyBindingService();
    private static final long SEQUENCE_TIMEOUT_TICKS = 20;

    private final SFMKeyBindingProfile profile;
    private final SFMKeyBindingEngine engine = new SFMKeyBindingEngine(SEQUENCE_TIMEOUT_TICKS);
    private boolean dispatchSuspended;
    private boolean persistenceWritable;
    private List<String> persistenceDiagnostics;
    private long eventSequence;
    private List<SFMKeyBindingConflict> lastConflicts = List.of();
    private final LinkedHashMap<Long, SFMKeyInputEvent> recentEvents = new LinkedHashMap<>();

    private SFMKeyBindingService() {
        SFMKeyBindingStorage.LoadResult loaded = SFMKeyBindingStorage.load();
        profile = new SFMKeyBindingProfile(
                SFMKeyBindingDefaults.definitions(),
                loaded.state(),
                SFMKeyboardUsageSituations.catalog());
        persistenceWritable = loaded.writable();
        persistenceDiagnostics = loaded.diagnostics();
        refreshEngine();
    }

    public SFMKeyBindingProfile profile() {
        return profile;
    }

    public List<SFMKeyBinding> bindingsForAction(ResourceLocation actionId) {
        return profile.bindingsForAction(actionId.toString());
    }

    public List<SFMKeyBinding> tombstonedBuiltInsForAction(ResourceLocation actionId) {
        return profile.tombstonedBuiltInsForAction(actionId.toString());
    }

    public List<ResourceLocation> situationIds() {
        return SFMKeyboardUsageSituations.catalog().ids();
    }

    public Optional<SFMKeyboardUsageSituation> situation(ResourceLocation id) {
        return SFMKeyboardUsageSituations.catalog().get(id);
    }

    public List<SFMKeyBindingConflict> lastConflicts() {
        return lastConflicts;
    }

    public boolean persistenceWritable() {
        return persistenceWritable;
    }

    public List<String> persistenceDiagnostics() {
        return persistenceDiagnostics;
    }

    public void put(SFMKeyBinding binding) {
        if (profile.origin(binding.bindingId()) == SFMKeyBindingProfile.Origin.EPHEMERAL) {
            profile.putEphemeral(binding);
            refreshEngine();
            return;
        }
        profile.put(binding);
        changed();
    }

    public void putEphemeral(SFMKeyBinding binding) {
        profile.putEphemeral(binding);
        refreshEngine();
    }

    public void removeEphemeral(String bindingId) {
        if (profile.removeEphemeral(bindingId)) refreshEngine();
    }

    public void remove(String bindingId) {
        if (profile.origin(bindingId) == SFMKeyBindingProfile.Origin.EPHEMERAL) {
            removeEphemeral(bindingId);
            return;
        }
        if (profile.remove(bindingId)) changed();
    }

    public void restoreBuiltIn(String bindingId) {
        if (profile.restoreBuiltIn(bindingId)) changed();
    }

    public void setEnabled(String bindingId, boolean enabled) {
        if (profile.origin(bindingId) == SFMKeyBindingProfile.Origin.EPHEMERAL) {
            if (profile.setEnabled(bindingId, enabled)) refreshEngine();
            return;
        }
        if (profile.setEnabled(bindingId, enabled)) changed();
    }

    /** Compatibility entry point for explicit-global raw Forge events. */
    public List<SFMActionInvocationIntent> accept(SFMKeyInputEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = minecraft == null ? null : minecraft.screen;
        return accept(
                event,
                SFMKeyboardUsageContextSnapshot.global(
                        origin,
                        () -> Minecraft.getInstance().screen == origin)).intents();
    }

    public SFMKeyBindingMatchResult acceptKey(
            int keyCode,
            SFMKeyInputEvent.Type type,
            Set<SFMKeyModifier> modifiers,
            SFMKeyboardUsageContextSnapshot context
    ) {
        return accept(new SFMKeyInputEvent(
                ++eventSequence,
                engine.currentTick(),
                keyCode,
                type,
                modifiers), context);
    }

    public SFMKeyBindingMatchResult accept(
            SFMKeyInputEvent event,
            SFMKeyboardUsageContextSnapshot context
    ) {
        if (dispatchSuspended) return SFMKeyBindingMatchResult.UNMATCHED;
        remember(event);
        SFMKeyBindingSnapshot effectiveSnapshot = profile.snapshot();
        SFMClientActionContext actionContext = context.actionContext();
        SFMKeyBindingMatchResult result = engine.accept(
                event,
                context,
                binding -> isAvailable(binding, actionContext));
        lastConflicts = result.conflicts();
        if (!lastConflicts.isEmpty()) {
            SFM.LOGGER.warn("Dynamic keybinding conflict at {}: {}",
                    context.activeSituations(), lastConflicts);
        }
        result.intents().forEach(intent -> dispatch(
                intent,
                context,
                provenance(intent, effectiveSnapshot, result, context)
        ));
        return result;
    }

    public void advanceTime(long tick) {
        engine.advanceTime(tick);
    }

    public long currentTick() {
        return engine.currentTick();
    }

    public void reset(SFMKeyBindingEngine.ResetReason reason) {
        engine.reset(reason);
    }

    public void setDispatchSuspended(boolean suspended) {
        dispatchSuspended = suspended;
        engine.reset(SFMKeyBindingEngine.ResetReason.MANUAL);
    }

    private void changed() {
        refreshEngine();
        if (persistenceWritable) {
            SFMKeyBindingStorage.SaveResult saved = SFMKeyBindingStorage.save(profile.userState());
            if (!saved.succeeded()) {
                persistenceWritable = false;
                persistenceDiagnostics = saved.diagnostics();
                SFM.LOGGER.warn(
                        "Dynamic keybinding storage became read-only after a save failure: {}",
                        persistenceDiagnostics);
            }
        } else {
            SFM.LOGGER.warn("Dynamic keybinding changes are session-only because storage is read-only: {}",
                    persistenceDiagnostics);
        }
    }

    private void refreshEngine() {
        engine.replaceBindings(profile.snapshot());
    }

    private boolean isAvailable(SFMKeyBinding binding, SFMClientActionContext context) {
        try {
            ResourceLocation id = new ResourceLocation(binding.actionId());
            SFMClientAction<?> action = SFMClientActions.registry().get(id);
            return action != null && action.requirement().resolve(context).isAvailable();
        } catch (RuntimeException exception) {
            SFM.LOGGER.warn("Dynamic binding {} targets unavailable action {}",
                    binding.bindingId(), binding.actionId());
            return false;
        }
    }

    private void dispatch(
            SFMActionInvocationIntent intent,
            SFMKeyboardUsageContextSnapshot usageContext,
            SFMClientActionInvocationTrace.DynamicBindingProvenance provenance
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        SFMClientActionContext context = usageContext.actionContext();
        @Nullable Screen origin = usageContext.originatingHost() instanceof Screen screen ? screen : minecraft.screen;
        String commandDraft = stripSlash(intent.commandDraft()).stripLeading();
        String command = SFMClientCommandInsertion.prepare(
                commandDraft,
                SFMClientActions.commandTree(),
                new SFMClientActionSource(context)
        );
        ParseResults<SFMClientActionSource> parsed = SFMClientActions.commandTree().parse(
                command,
                new SFMClientActionSource(context)
        );
        boolean complete = !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
        if (!complete) {
            SFMCommandDraftScreen.open(origin, command, intent);
            return;
        }
        minecraft.execute(() -> {
            try (SFMClientActionInvocationTrace.Scope traceScope =
                         SFMClientActionInvocationTrace.activate(provenance)) {
                SFMClientActionExecutor.execute(command, context, ignored -> {
                });
            } catch (CommandSyntaxException exception) {
                SFM.LOGGER.warn("Dynamic binding command failed: {}", command, exception);
            }
        });
    }

    private void remember(SFMKeyInputEvent event) {
        recentEvents.put(event.sequenceNumber(), event);
        while (recentEvents.size() > 256) {
            recentEvents.remove(recentEvents.keySet().iterator().next());
        }
    }

    private SFMClientActionInvocationTrace.DynamicBindingProvenance provenance(
            SFMActionInvocationIntent intent,
            SFMKeyBindingSnapshot snapshot,
            SFMKeyBindingMatchResult result,
            SFMKeyboardUsageContextSnapshot context
    ) {
        List<SFMKeyInputEvent> sourceEvents = recentEvents.values().stream()
                .filter(event -> event.sequenceNumber() >= intent.firstSourceEvent()
                        && event.sequenceNumber() <= intent.lastSourceEvent())
                .toList();
        if (sourceEvents.isEmpty()) {
            throw new IllegalStateException("Dynamic binding invocation lost its source event range");
        }
        return new SFMClientActionInvocationTrace.DynamicBindingProvenance(
                sourceEvents,
                snapshot,
                intent.bindingId(),
                intent.actionId(),
                intent.commandDraft(),
                context.activeSituations().stream().map(Object::toString).toList(),
                result.consumed(),
                result.conflicts()
        );
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
