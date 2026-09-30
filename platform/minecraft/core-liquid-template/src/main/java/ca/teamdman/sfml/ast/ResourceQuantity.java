package ca.teamdman.sfml.ast;

public record ResourceQuantity(
        Number number,
        IdExpansionBehaviour idExpansionBehaviour
) implements ASTNode {
    @SuppressWarnings("DataFlowIssue")
    public static final ResourceQuantity UNSET = new ResourceQuantity(null, IdExpansionBehaviour.NO_EXPAND);
    public static final ResourceQuantity MAX_QUANTITY = new ResourceQuantity(
            new Number(Long.MAX_VALUE),
            IdExpansionBehaviour.NO_EXPAND
    );

    public ResourceQuantity add(ResourceQuantity quantity) {
        return new ResourceQuantity(
                number.add(quantity.number),
                idExpansionBehaviour
        );
    }

    public enum IdExpansionBehaviour {
        EXPAND,
        NO_EXPAND
    }

    @Override
    public String toString() {
        return (this == UNSET ? "UNSET" : number) + (idExpansionBehaviour == IdExpansionBehaviour.EXPAND ? " EACH" : "");
    }
}
