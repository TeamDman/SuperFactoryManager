package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramRelation;
import ca.teamdman.sfm.common.program.ProgramResourceValue;
import ca.teamdman.sfm.common.program.ProgramValueReference;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Objects;

/** Maps copied {@code sfm:text} item occurrences to copied strings. */
public record TextReadValueExpression(
        String operationId,
        String sourceVariable
) implements ProgramValueExpression {
    public TextReadValueExpression {
        operationId = Objects.requireNonNull(operationId).toLowerCase(Locale.ROOT);
        sourceVariable = Objects.requireNonNull(sourceVariable);
        if (!operationId.equals("sfm:text/read")) {
            throw new IllegalArgumentException("Unknown value operation: " + operationId);
        }
    }

    @Override
    public ProgramRelation evaluate(ProgramContext context) {
        ProgramRelation source = context.getVariableEnvironment().getRelation(sourceVariable)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + sourceVariable));
        return source.map(row -> {
            if (!(row.value() instanceof ProgramResourceValue resource)
                || !(resource.stack() instanceof ItemStack itemStack)) {
                throw new IllegalArgumentException("sfm:text/read requires an item input binding");
            }
            ItemStack copiedStack = itemStack.copy();
            return ProgramValueReference.lazy(() -> SFMValue.of(SFMTextResourceAdapters.read(copiedStack)));
        });
    }

    @Override
    public String toString() {
        return "STRING OF INVOKE \"" + operationId + "\" WITH " + sourceVariable;
    }
}
