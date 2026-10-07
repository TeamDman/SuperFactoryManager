package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigTracker;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Enables or disables command history and persists that preference. */
public final class PaletteHistoryPersistenceAction implements SFMClientAction<SFMClientActionContext> {
    public enum Operation {
        ENABLE,
        DISABLE
    }

    private final Operation operation;

    public PaletteHistoryPersistenceAction(Operation operation) {
        this.operation = operation;
    }

    @Override
    public Component title() {
        return Component.literal(operation == Operation.ENABLE
                ? "Enable command history"
                : "Disable command history");
    }

    @Override
    public Component description() {
        return Component.literal(operation == Operation.ENABLE
                ? "Load and remember successful palette commands"
                : "Stop loading, showing, recording, and saving palette history");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        boolean enabled = operation == Operation.ENABLE;
        try {
            SFMConfig.CLIENT_CONFIG.commandPaletteHistoryEnabled.set(enabled);
            if (SFMCommandHistoryService.hasPersistentStorage()
                    && !SFMConfigTracker.saveClientConfig()) {
                throw new IllegalStateException("SFM client config is unavailable");
            }
            SFMCommandHistoryService.setPersistenceEnabled(enabled);
        } catch (RuntimeException exception) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Unable to persist command history setting: " + exception.getMessage())).create();
        }
        context.getSource().sendFeedback(Component.literal(enabled
                ? "Command history enabled"
                : "Command history disabled"));
        return PanelActionSupport.closePaletteAfter(1);
    }
}
