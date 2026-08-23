package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

/** Selects one boundary of the command palette's suggestion viewport. */
public final class CommandPaletteSuggestionSelectionAction implements SFMClientAction<SFMCommandPaletteScreen> {
    public enum Boundary {
        FIRST,
        LAST
    }

    private final Boundary boundary;

    public CommandPaletteSuggestionSelectionAction(Boundary boundary) {
        this.boundary = boundary;
    }

    @Override
    public Component title() {
        return Component.literal(boundary == Boundary.FIRST
                ? "Select first palette suggestion"
                : "Select last palette suggestion");
    }

    @Override
    public Component description() {
        return Component.literal(boundary == Boundary.FIRST
                ? "Move command palette suggestion focus to the first row"
                : "Move command palette suggestion focus to the last row");
    }

    @Override
    public SFMClientActionRequirement<SFMCommandPaletteScreen> requirement() {
        return context -> {
            SFMClientActionAvailability<SFMCommandPaletteScreen> palette = context.requireOriginatingHost(
                    SFMCommandPaletteScreen.class,
                    Component.literal("No command palette is open")
            );
            if (!palette.isAvailable()) return palette;
            if (!palette.target().hasSuggestionsForAction()) {
                return SFMClientActionAvailability.unavailable(
                        Component.literal("The command palette has no suggestions"));
            }
            return palette;
        };
    }

    @Override
    public int execute(
            SFMCommandPaletteScreen target,
            CommandContext<SFMClientActionSource> context
    ) {
        boolean selected = boundary == Boundary.FIRST
                ? target.selectFirstSuggestionForAction()
                : target.selectLastSuggestionForAction();
        return selected ? 1 : 0;
    }
}
