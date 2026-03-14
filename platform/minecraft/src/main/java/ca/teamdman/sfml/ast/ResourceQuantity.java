package ca.teamdman.sfml.ast;

import java.util.List;

public record ResourceQuantity(
        IdExpansionBehaviour idExpansionBehaviour,

        NumberExpression number
) implements SfmlAstNode {
    @SuppressWarnings("DataFlowIssue")
    public static final ResourceQuantity UNSET = new ResourceQuantity(IdExpansionBehaviour.NO_EXPAND, null);
    public static final ResourceQuantity MAX_QUANTITY = new ResourceQuantity(
            IdExpansionBehaviour.NO_EXPAND, NumberExpression.fromLiteral(Long.MAX_VALUE)
    );

    public ResourceQuantity add(ResourceQuantity quantity) {
        return new ResourceQuantity(
                idExpansionBehaviour, number.add(quantity.number)
        );
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(idExpansionBehaviour, number);
    }

    public enum IdExpansionBehaviour implements SfmlAstNode {
        EXPAND,
        NO_EXPAND;

        @Override
        public List<? extends SfmlAstNode> getChildNodes() {

            return List.of();
        }
    }

    @Override
    public String toString() {
        return (this == UNSET ? "UNSET" : number) + (idExpansionBehaviour == IdExpansionBehaviour.EXPAND ? " EACH" : "");
    }
}
