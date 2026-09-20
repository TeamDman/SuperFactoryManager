package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;

import java.util.Objects;

/** Exact typed equality: integers and floating values remain distinct. */
public record BoolClientValueEquals(String variable, SFMValue expected) implements BoolExpr {
    public BoolClientValueEquals { Objects.requireNonNull(variable); Objects.requireNonNull(expected); }
    @Override public boolean test(ProgramContext context) {
        throw new IllegalStateException("Client value comparison requires a Client Manager");
    }
    @Override public String toString() {
        return variable + " EQ JSON \"" + SFMValueSchema.canonicalActionJson(expected).replace("\"", "\\\"") + "\"";
    }
}
