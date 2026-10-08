package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.action.SFMClientAction;
{% else %}
{% endcase %}
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.action.SFMClientActionInvocationTrace;
{% else %}
{% endcase %}
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMClientCommandInsertion;
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
{% else %}
{% endcase %}
import ca.teamdman.sfm.client.screen.SFMCommandDraftScreen;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import org.jetbrains.annotations.Nullable;
{% else %}
{% endcase %}
import java.util.List;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
{% else %}
{% endcase %}

public final class SFMKeyBindingService {
    public static final SFMKeyBindingService INSTANCE = new SFMKeyBindingService();
    private static final long SEQUENCE_TIMEOUT_TICKS = 20;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private final SFMKeyBindingProfile profile;
{% else %}
    private final SFMKeyBindingProfile profile = new SFMKeyBindingProfile();
{% endcase %}
    private final SFMKeyBindingEngine engine = new SFMKeyBindingEngine(SEQUENCE_TIMEOUT_TICKS);
    private boolean dispatchSuspended;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private boolean persistenceWritable;
    private List<String> persistenceDiagnostics;
    private long eventSequence;
    private List<SFMKeyBindingConflict> lastConflicts = List.of();
    private final LinkedHashMap<Long, SFMKeyInputEvent> recentEvents = new LinkedHashMap<>();
{% else %}
{% endcase %}

    private SFMKeyBindingService() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMKeyBindingStorage.LoadResult loaded = SFMKeyBindingStorage.load();
        profile = new SFMKeyBindingProfile(
                SFMKeyBindingDefaults.definitions(),
                loaded.state(),
                SFMKeyboardUsageSituations.catalog());
        persistenceWritable = loaded.writable();
        persistenceDiagnostics = loaded.diagnostics();
{% else %}
        SFMKeyBindingStorage.load().forEach(profile::put);
{% endcase %}
        refreshEngine();
    }

    public SFMKeyBindingProfile profile() {
        return profile;
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    public List<SFMKeyBinding> bindingsForAction(Identifier actionId) {
{% else %}
    public List<SFMKeyBinding> bindingsForAction(ResourceLocation actionId) {
{% endcase %}
        return profile.bindingsForAction(actionId.toString());
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public List<SFMKeyBinding> bindingsForCommand(
            ResourceLocation actionId,
            String commandDraft
    ) {
        return profile.bindingsForCommand(actionId.toString(), commandDraft);
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

{% else %}
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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

{% else %}
    public void put(SFMKeyBinding binding) {
        profile.put(binding);
        refreshEngine();
        SFMKeyBindingStorage.save(profile.snapshot());
    }

{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void removeEphemeral(String bindingId) {
        if (profile.removeEphemeral(bindingId)) refreshEngine();
    }

{% else %}
{% endcase %}
    public void remove(String bindingId) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (profile.origin(bindingId) == SFMKeyBindingProfile.Origin.EPHEMERAL) {
            removeEphemeral(bindingId);
            return;
        }
        if (profile.remove(bindingId)) changed();
{% else %}
        profile.remove(bindingId);
        refreshEngine();
        SFMKeyBindingStorage.save(profile.snapshot());
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void restoreBuiltIn(String bindingId) {
        if (profile.restoreBuiltIn(bindingId)) changed();
    }

{% else %}
{% endcase %}
    public void setEnabled(String bindingId, boolean enabled) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (profile.origin(bindingId) == SFMKeyBindingProfile.Origin.EPHEMERAL) {
            if (profile.setEnabled(bindingId, enabled)) refreshEngine();
            return;
        }
        if (profile.setEnabled(bindingId, enabled)) changed();
{% else %}
        profile.setEnabled(bindingId, enabled);
        refreshEngine();
        SFMKeyBindingStorage.save(profile.snapshot());
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /** Compatibility entry point for explicit-global raw Forge events. */
{% else %}
{% endcase %}
    public List<SFMActionInvocationIntent> accept(SFMKeyInputEvent event) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = minecraft == null ? null : minecraft.screen;
        return accept(
                event,
                SFMKeyboardUsageContextSnapshot.global(
                        origin,
                        () -> Minecraft.getInstance().screen == origin)).intents();
{% else %}
        if (dispatchSuspended) return List.of();
        List<SFMActionInvocationIntent> intents = engine.accept(
                event);
        intents.forEach(this::dispatch);
        return intents;
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public SFMKeyBindingMatchResult acceptKey(
            int keyCode,
            SFMKeyInputEvent.Type type,
            Set<SFMKeyModifier> modifiers,
            SFMKeyboardUsageContextSnapshot context
    ) {
        return acceptKey(keyCode, type, modifiers, context, ignored -> { });
    }

    /**
     * Observes the final match before any action intent is dispatched. This is
     * the pre-dispatch authority used by document journals to retain consumed
     * shortcuts without racing an immediately executed client action.
     */
    public SFMKeyBindingMatchResult acceptKey(
            int keyCode,
            SFMKeyInputEvent.Type type,
            Set<SFMKeyModifier> modifiers,
            SFMKeyboardUsageContextSnapshot context,
            Consumer<SFMKeyBindingMatchResult> beforeDispatch
    ) {
        Objects.requireNonNull(beforeDispatch, "beforeDispatch");
        return accept(new SFMKeyInputEvent(
                ++eventSequence,
                engine.currentTick(),
                keyCode,
                type,
                modifiers), context, beforeDispatch);
    }

    public SFMKeyBindingMatchResult accept(
            SFMKeyInputEvent event,
            SFMKeyboardUsageContextSnapshot context
    ) {
        return accept(event, context, ignored -> { });
    }

    private SFMKeyBindingMatchResult accept(
            SFMKeyInputEvent event,
            SFMKeyboardUsageContextSnapshot context,
            Consumer<SFMKeyBindingMatchResult> beforeDispatch
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
        beforeDispatch.accept(result);
        result.intents().forEach(intent -> dispatch(
                intent,
                context,
                provenance(intent, effectiveSnapshot, result, context)
        ));
        return result;
    }

{% else %}
{% endcase %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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

{% else %}
{% endcase %}
    private void refreshEngine() {
        engine.replaceBindings(profile.snapshot());
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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
{% else %}
    private void dispatch(
            SFMActionInvocationIntent intent) {
{% endcase %}
        Minecraft minecraft = Minecraft.getInstance();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMClientActionContext context = usageContext.actionContext();
        @Nullable Screen origin = usageContext.originatingHost() instanceof Screen screen ? screen : minecraft.screen;
{% else %}
        Screen origin = minecraft.screen;
        SFMClientActionContext context = SFMClientActionContext.create(
                origin,
                () -> Minecraft.getInstance().screen == origin
        );
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            try (SFMClientActionInvocationTrace.Scope traceScope =
                         SFMClientActionInvocationTrace.activate(provenance)) {
{% else %}
            try {
{% endcase %}
                SFMClientActionExecutor.execute(command, context, ignored -> {
                });
            } catch (CommandSyntaxException exception) {
                SFM.LOGGER.warn("Dynamic binding command failed: {}", command, exception);
            }
        });
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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

{% else %}
{% endcase %}
    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
