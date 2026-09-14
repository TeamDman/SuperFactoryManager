package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.GeneratedItemProgramInputSource;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramRelation;
import ca.teamdman.sfm.common.program.ProgramValueReference;

import java.util.Locale;
import java.util.Objects;

/** Registers one lazy packet input source for each relation occurrence. */
public record CreateInputStatement(
        String carrierId,
        String valueVariable
) implements Statement {
    public CreateInputStatement {
        carrierId = Objects.requireNonNull(carrierId).toLowerCase(Locale.ROOT);
        valueVariable = Objects.requireNonNull(valueVariable);
        if (!carrierId.equals("sfm:packet")) {
            throw new IllegalArgumentException("CREATE INPUT only supports sfm:packet in this MVP");
        }
    }

    @Override
    public void tick(ProgramContext context) {
        ProgramRelation relation = context.getVariableEnvironment().getRelation(valueVariable)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + valueVariable));
        if (!context.getBehaviour().allowsRuntimeMaterialization()) {
            return;
        }
        relation.rows().forEach(row -> {
            if (!(row.value() instanceof ProgramValueReference reference)) {
                throw new IllegalArgumentException("CREATE INPUT requires an SFM value variable: " + valueVariable);
            }
            context.addInput(new GeneratedItemProgramInputSource(
                    "CREATE INPUT " + carrierId + " WITH " + valueVariable,
                    reference::get
            ));
        });
    }

    @Override
    public String toString() {
        return "CREATE INPUT " + carrierId + " WITH " + valueVariable;
    }
}
