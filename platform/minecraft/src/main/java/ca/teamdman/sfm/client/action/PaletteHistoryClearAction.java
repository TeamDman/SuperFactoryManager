package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Clears both the in-memory and persisted palette history. */
public final class PaletteHistoryClearAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return Component.literal("Clear command history");
    }

    @Override
    public Component description() {
        return Component.literal("Clear all successful command-palette history");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context) {
        SFMCommandHistoryService.clear();
        return PanelActionSupport.closePaletteAfter(1);
    }
}
