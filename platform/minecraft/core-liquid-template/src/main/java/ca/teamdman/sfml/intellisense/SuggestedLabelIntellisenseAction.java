package ca.teamdman.sfml.intellisense;

import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import net.minecraft.network.chat.Component;

public record SuggestedLabelIntellisenseAction(
        String label,
        int numBlocks
) implements IntellisenseAction {
    @Override
    public Component getComponent() {
        return Component.literal("%s (%d)".formatted(label, numBlocks));
    }

    @Override
    public ManipulationResult perform(IntellisenseContext context) {
        if (Label.needsQuotes(label)) {
            return context.createMutableProgramString()
                    .replaceWordAndMoveCursorsToEnd("\"%s\" ".formatted(label))
                    .intoResult();
        } else {
            return context.createMutableProgramString()
                    .replaceWordAndMoveCursorsToEnd("%s ".formatted(label))
                    .intoResult();
        }
    }
}
