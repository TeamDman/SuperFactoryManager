package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.inspection.SFMInspectionPresetReopen;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Closes the prepared comparison using the same workspace lifecycle as Escape. */
public final class SFMExplorerDiffCloseAction implements SFMClientAction<SFMClientActionContext> {
    @Override public Component title() { return Component.literal("Close prepared diff"); }
    @Override public Component description() {
        return Component.literal("Close the prepared comparison and retain its Explorer selection for reopening");
    }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> SFMInspectionPresetReopen.hasOpenPreset()
                && (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer
                    || Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen)
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(Component.literal("No prepared comparison is open"));
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
    }
    @Override public int execute(SFMClientActionContext context, CommandContext<SFMClientActionSource> command) {
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)
            palette.onClose();
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace))
            throw new IllegalStateException("Prepared comparison workspace is not visible");
        workspace.onClose();
        return 1;
    }
}
