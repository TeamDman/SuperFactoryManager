package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.inspection.SFMInspectionPresetReopen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** One palette action to return to the exact prepared comparison after closing its screen. */
public final class SFMExplorerDiffReopenAction implements SFMClientAction<SFMClientActionContext> {
    @Override public Component title() { return Component.literal("Reopen last diff preset"); }
    @Override public Component description() {
        return Component.literal("Restore the last comparison workspace and selected Explorer row");
    }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> !context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.unavailable(Component.literal("Originating screen closed"))
                : !SFMInspectionPresetReopen.canReopen()
                ? SFMClientActionAvailability.unavailable(Component.literal("No closed comparison workspace to reopen"))
                : SFMClientActionAvailability.available(context);
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
    }
    @Override public int execute(SFMClientActionContext context, CommandContext<SFMClientActionSource> command) {
        SFMInspectionPresetReopen.reopen(context);
        command.getSource().sendFeedback(Component.literal("Reopened the last comparison workspace"));
        return 1;
    }
}
