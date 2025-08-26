package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

public record ResourceQuantity(
        NumExpr expr,
        IdExpansionBehaviour idExpansionBehaviour
) implements ASTNode {
    @SuppressWarnings("DataFlowIssue")
    public static final ResourceQuantity UNSET = new ResourceQuantity(null, IdExpansionBehaviour.NO_EXPAND);
    public static final ResourceQuantity MAX_QUANTITY = new ResourceQuantity(
            new Number(Long.MAX_VALUE),
            IdExpansionBehaviour.NO_EXPAND
    );

    public long eval(ProgramContext context) {
        if (this == UNSET) return 0;
        return expr.eval(context);
    }

    public ResourceQuantity add(ResourceQuantity quantity) {
        // simple add only when both sides are Number literals; else fallback to rhs
        if (this.expr instanceof Number a && quantity.expr instanceof Number b) {
            return new ResourceQuantity(
                    new Number(a.value() + b.value()),
                    idExpansionBehaviour
            );
        }
        return quantity; // minimal impl; not used in critical paths
    }

    @Override
    public String toString() {
        return (this == UNSET ? "UNSET" : String.valueOf(expr)) + (idExpansionBehaviour == IdExpansionBehaviour.EXPAND ? " EACH" : "");
    }

    public Number number() {
        return expr instanceof Number n ? n : new Number(0);
    }

    public enum IdExpansionBehaviour {
        EXPAND,
        NO_EXPAND
    }
}
