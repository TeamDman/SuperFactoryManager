package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.gui.screens.Screen;

import java.util.Optional;

/** Resolves the one temporal document that owns a screen input event. */
public final class SFMDocumentHistoryInputRouting {
    private SFMDocumentHistoryInputRouting() {
    }

    public static Optional<SFMDocumentHistoryInputTarget> resolve(Screen screen) {
        if (screen instanceof SFMDocumentHistoryInputTarget direct) return Optional.of(direct);
        if (screen instanceof SFMScreenMultiplexer workspace
                && workspace.focusedPanelInstance() instanceof SFMDocumentHistoryInputTarget focused) {
            return Optional.of(focused);
        }
        return Optional.empty();
    }
}
