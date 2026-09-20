package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.Objects;

/** Pure client-local frame-counter expression; it reads no world or inbox state. */
public record BoolFrameModulo(long divisor, ComparisonOperator comparison, long value) implements BoolExpr {
    public BoolFrameModulo {
        if (divisor <= 0) throw new IllegalArgumentException("FRAME MOD divisor must be positive");
        Objects.requireNonNull(comparison, "comparison");
    }

    public boolean testFrame(long frameIndex) {
        if (frameIndex < 0) throw new IllegalArgumentException("Frame index must be non-negative");
        return comparison.test(frameIndex % divisor, value);
    }

    @Override
    public boolean test(ProgramContext context) {
        throw new IllegalStateException("FRAME MOD cannot read server ProgramContext");
    }

    @Override
    public String toString() {
        return "FRAME MOD " + divisor + " " + comparison + " " + value;
    }
}
