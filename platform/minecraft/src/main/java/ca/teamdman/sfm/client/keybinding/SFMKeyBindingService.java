package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class SFMKeyBindingService {
    public static final SFMKeyBindingService INSTANCE = new SFMKeyBindingService();
    private static final long SEQUENCE_TIMEOUT_TICKS = 20;

    private final SFMKeyBindingProfile profile = new SFMKeyBindingProfile();
    private final SFMKeyBindingEngine engine = new SFMKeyBindingEngine(SEQUENCE_TIMEOUT_TICKS);
    private boolean dispatchSuspended;

    private SFMKeyBindingService() {
        SFMKeyBindingStorage.load().forEach(profile::put);
        refreshEngine();
    }

    public SFMKeyBindingProfile profile() {
        return profile;
    }

    public List<SFMKeyBinding> bindingsForAction(ResourceLocation actionId) {
        return profile.bindingsForAction(actionId.toString());
    }

    public void put(SFMKeyBinding binding) {
        profile.put(binding);
        refreshEngine();
        SFMKeyBindingStorage.save(profile.snapshot());
    }

    public void remove(String bindingId) {
        profile.remove(bindingId);
        refreshEngine();
        SFMKeyBindingStorage.save(profile.snapshot());
    }

    public void setEnabled(String bindingId, boolean enabled) {
        profile.setEnabled(bindingId, enabled);
        refreshEngine();
        SFMKeyBindingStorage.save(profile.snapshot());
    }

    public List<SFMActionInvocationIntent> accept(SFMKeyInputEvent event) {
        if (dispatchSuspended) return List.of();
        List<SFMActionInvocationIntent> intents = engine.accept(event);
        intents.forEach(this::dispatch);
        return intents;
    }

    public void advanceTime(long tick) {
        engine.advanceTime(tick);
    }

    public void reset(SFMKeyBindingEngine.ResetReason reason) {
        engine.reset(reason);
    }

    public void setDispatchSuspended(boolean suspended) {
        dispatchSuspended = suspended;
        engine.reset(SFMKeyBindingEngine.ResetReason.MANUAL);
    }

    private void refreshEngine() {
        engine.replaceBindings(profile.snapshot());
    }

    private void dispatch(SFMActionInvocationIntent intent) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = minecraft.screen;
        SFMClientActionContext context = SFMClientActionContext.create(
                origin,
                () -> Minecraft.getInstance().screen == origin
        );
        String command = stripSlash(intent.commandDraft()).trim();
        ParseResults<SFMClientActionSource> parsed = SFMClientActions.commandTree().parse(
                command,
                new SFMClientActionSource(context)
        );
        boolean complete = !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
        if (!complete) {
            SFMCommandPaletteScreen.open(SFMCommandPaletteScreen.createOriginContext(), command);
            return;
        }
        minecraft.execute(() -> {
            try {
                SFMClientActions.commandTree().execute(command, new SFMClientActionSource(context));
            } catch (CommandSyntaxException exception) {
                SFM.LOGGER.warn("Dynamic binding command failed: {}", command, exception);
            }
        });
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }
}
