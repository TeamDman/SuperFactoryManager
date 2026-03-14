package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.Objects;

/**
 * A number expression that holds both the eagerly evaluated numeric value
 * and the original expression AST for pretty printing purposes.
 *
 */
@SuppressWarnings("ClassCanBeRecord") // Records do not support transient fields
public final class NumberExpression implements SfmlAstNode, ToStringPretty, Displayable {

    @Override
    public String display() {

        return number.toString();
    }

    /// The const-computed result of this expression.
    /// Transient fields are omitted from {@link #getChildNodes()}.
    private transient final Number number;

    /// An expression that resolves to a number.
    private final INumberExpression expression;

    /**
     * @param number     The eagerly evaluated numeric value
     * @param expression The original expression AST node
     */
    public NumberExpression(
            Number number,
            INumberExpression expression
    ) {

        this.number = number;
        this.expression = expression;
    }

    /**
     * Get the evaluated long value
     */
    public long value() {

        return number.value();
    }

    /**
     * Add two number expressions together, creating a new NumberExpression with the combined value.
     * The resulting expression type is NumberAddition to preserve the operation structure.
     */
    public NumberExpression add(NumberExpression other) { // TODO v5: rename to concatAddition and add an add idk
        NumberAddition expr = new NumberAddition(this, other);
        Number resultValue = new Number(this.value() + other.value());
        return new NumberExpression(resultValue, expr);
    }

    /**
     * Subtract one number expression from another, creating a new NumberExpression with the result.
     * The resulting expression type is NumberSubtraction to preserve the operation structure.
     */
    public NumberExpression subtract(NumberExpression other) {

        NumberSubtraction expr = new NumberSubtraction(this, other);
        Number resultValue = new Number(this.value() - other.value());
        return new NumberExpression(resultValue, expr);
    }

    /**
     * Create a NumberExpression from a literal value
     */
    public static NumberExpression fromLiteral(long value) {

        NumberLiteral literal = new NumberLiteral(value);
        return new NumberExpression(new Number(value), literal);
    }

    @Override
    public String toString() {

        return expression.toString();
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(expression);
    }

    public Number number() {

        return number;
    }

    public INumberExpression expression() {

        return expression;
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (NumberExpression) obj;
        return Objects.equals(this.number, that.number) &&
               Objects.equals(this.expression, that.expression);
    }

    @Override
    public int hashCode() {

        return Objects.hash(number, expression);
    }

}
