package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Grows the captured panel toward one adjacent split track by the named layout step. */
public final class ResizePanelAction implements SFMClientAction<PanelActionSupport.CapturedPanel> {
    private final SFMWorkspaceSide side;

    public ResizePanelAction(SFMWorkspaceSide side) {
        this.side = Objects.requireNonNull(side);
    }

    @Override
    public Component title() {
        return Component.literal("Resize panel " + side.name().toLowerCase());
    }

    @Override
    public Component description() {
        return Component.literal("Grow the captured panel toward the nearest matching split track");
    }

    @Override
    public SFMClientActionRequirement<PanelActionSupport.CapturedPanel> requirement() {
        return context -> {
            SFMClientActionAvailability<PanelActionSupport.CapturedPanel> captured =
                    PanelActionSupport.resolveCapturedPanel(context);
            if (!captured.isAvailable()) return captured;
            PanelActionSupport.CapturedPanel target = captured.target();
            if (!target.workspace().canResizePanel(target.panelId(), side)) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "The captured panel cannot grow farther in that direction"));
            }
            return captured;
        };
    }

    @Override
    public int execute(
            PanelActionSupport.CapturedPanel target,
            CommandContext<SFMClientActionSource> context
    ) {
        SFMWorkspacePanelIntentResult result = target.workspace().resizePanel(target.panelId(), side);
        return PanelActionSupport.closePaletteAfter(
                result == SFMWorkspacePanelIntentResult.APPLIED ? 1 : 0);
    }
}
