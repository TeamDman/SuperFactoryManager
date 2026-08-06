package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Creates a new independent panel from the captured panel's typed scene recipe. */
public final class DuplicatePanelAction implements SFMClientAction<PanelActionSupport.CapturedPanel> {
    private final SFMWorkspaceSide side;

    public DuplicatePanelAction(SFMWorkspaceSide side) {
        this.side = Objects.requireNonNull(side);
    }

    @Override
    public Component title() {
        return Component.literal("Duplicate panel " + side.name().toLowerCase());
    }

    @Override
    public Component description() {
        return Component.literal(
                "Open an independent copy of the captured panel from its typed scene recipe");
    }

    @Override
    public SFMClientActionRequirement<PanelActionSupport.CapturedPanel> requirement() {
        return context -> {
            SFMClientActionAvailability<PanelActionSupport.CapturedPanel> captured =
                    PanelActionSupport.resolveCapturedPanel(context);
            if (!captured.isAvailable()) return captured;
            PanelActionSupport.CapturedPanel target = captured.target();
            var unavailable = target.workspace().duplicateUnavailableReason(target.panelId());
            if (unavailable.isPresent()) {
                return SFMClientActionAvailability.unavailable(unavailable.orElseThrow());
            }
            return captured;
        };
    }

    @Override
    public int execute(
            PanelActionSupport.CapturedPanel target,
            CommandContext<SFMClientActionSource> context
    ) {
        SFMWorkspacePanelIntentResult result = target.workspace().duplicatePanel(target.panelId(), side);
        return PanelActionSupport.closePaletteAfter(
                result == SFMWorkspacePanelIntentResult.APPLIED ? 1 : 0);
    }
}
