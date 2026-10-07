package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryPanel;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Transposes the captured ordinary document-history canvas without changing graph identity. */
public final class SFMDocumentHistoryViewTransposeAction
        implements SFMClientAction<PanelActionSupport.CapturedPanel> {
    @Override
    public Component title() {
        return Component.literal("Transpose document history view");
    }

    @Override
    public Component description() {
        return Component.literal("Switch the captured history canvas between top-down and left-right");
    }

    @Override
    public SFMClientActionRequirement<PanelActionSupport.CapturedPanel> requirement() {
        return context -> {
            SFMClientActionAvailability<PanelActionSupport.CapturedPanel> captured =
                    PanelActionSupport.resolveCapturedPanel(context);
            if (!captured.isAvailable()) return captured;
            if (!(captured.target().workspace().panelInstance(captured.target().panelId())
                    instanceof SFMDocumentHistoryPanel)) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "The captured panel is not a document-history canvas"));
            }
            return captured;
        };
    }

    @Override
    public int execute(
            PanelActionSupport.CapturedPanel target,
            CommandContext<SFMClientActionSource> context
    ) {
        if (!(target.workspace().panelInstance(target.panelId()) instanceof SFMDocumentHistoryPanel panel)) {
            return 0;
        }
        panel.transpose();
        context.getSource().sendFeedback(Component.literal(
                "Document history orientation: " + panel.orientation().name().toLowerCase(java.util.Locale.ROOT)));
        return PanelActionSupport.closePaletteAfter(1);
    }
}
