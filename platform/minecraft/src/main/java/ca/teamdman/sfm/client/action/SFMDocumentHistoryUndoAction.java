package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMDocumentHistoryTarget;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Moves the captured document head to its retained parent revision. */
public final class SFMDocumentHistoryUndoAction implements SFMClientAction<PanelActionSupport.CapturedPanel> {
    @Override
    public Component title() {
        return Component.literal("Undo document history");
    }

    @Override
    public Component description() {
        return Component.literal("Move the focused document head backward without deleting alternate histories");
    }

    @Override
    public SFMClientActionRequirement<PanelActionSupport.CapturedPanel> requirement() {
        return context -> {
            SFMClientActionAvailability<PanelActionSupport.CapturedPanel> captured =
                    PanelActionSupport.resolveCapturedPanel(context);
            if (!captured.isAvailable()) return captured;
            if (!(captured.target().workspace().panelInstance(captured.target().panelId())
                    instanceof SFMDocumentHistoryTarget)) {
                return SFMClientActionAvailability.unavailable(
                        Component.literal("The focused panel has no document history head")
                );
            }
            return captured;
        };
    }

    @Override
    public int execute(
            PanelActionSupport.CapturedPanel captured,
            CommandContext<SFMClientActionSource> context
    ) {
        SFMDocumentHistoryTarget target = (SFMDocumentHistoryTarget) captured.workspace()
                .panelInstance(captured.panelId());
        SFMHistoryGraphRuntime.OperationResult result = target.undoDocumentHistory();
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.status() == SFMHistoryGraphRuntime.OperationStatus.APPLIED
                ? PanelActionSupport.closePaletteAfter(1)
                : 0;
    }
}
