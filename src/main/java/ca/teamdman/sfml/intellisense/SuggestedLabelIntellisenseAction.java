package ca.teamdman.sfml.intellisense;

import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

@Desugar public record SuggestedLabelIntellisenseAction(
        String label,
        int numBlocks
) implements IntellisenseAction {
    @Override
    public ITextComponent getComponent() {
        return new TextComponentString("%s (%d)".formatted(label, numBlocks));
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
