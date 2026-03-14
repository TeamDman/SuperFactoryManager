package ca.teamdman.sfml.ast;

import java.util.List;

import static ca.teamdman.sfml.ast.ResourceQuantity.IdExpansionBehaviour.NO_EXPAND;

public record Limit(
        ResourceQuantity quantity,
        ResourceQuantity retention
) implements SfmlAstNode {
    public static final Limit MAX_QUANTITY_NO_RETENTION = new Limit(
            new ResourceQuantity(NO_EXPAND, NumberExpression.fromLiteral(Long.MAX_VALUE)),
            new ResourceQuantity(NO_EXPAND, NumberExpression.fromLiteral(0))
    );
    public static final Limit MAX_QUANTITY_MAX_RETENTION = new Limit(
            new ResourceQuantity(NO_EXPAND, NumberExpression.fromLiteral(Long.MAX_VALUE)),
            new ResourceQuantity(NO_EXPAND, NumberExpression.fromLiteral(Long.MAX_VALUE))
    );

    public static final Limit UNSET = new Limit(
            ResourceQuantity.UNSET,
            ResourceQuantity.UNSET
    );

    public Limit withDefaults(Limit limit) {
        if (quantity() == ResourceQuantity.UNSET && retention() == ResourceQuantity.UNSET) {
            return limit;
        } else if (quantity() == ResourceQuantity.UNSET) {
            return new Limit(
                    limit.quantity(),
                    retention()
            );
        } else if (retention() == ResourceQuantity.UNSET) {
            return new Limit(
                    quantity(),
                    limit.retention()
            );
        }
        return this;
    }

    @Override
    public String toString() {
        return quantity + " RETAIN " + retention;
    }

    public String toStringCondensed(Limit defaults) {
        StringBuilder sb = new StringBuilder();
        if (!quantity.number().equals(defaults.quantity().number())) {
            sb.append(quantity);
        }
        if (!retention.number().equals(defaults.retention().number())) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("RETAIN ").append(retention);
        }
        return sb.toString();
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(quantity, retention);
    }

}
