package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.NbtJmesPathEvaluator;

/**
 * AST node for NBT filtering using grammar-based expressions.
 * Example usage: INPUT WITH NBT damage > 10 FROM a
 *
 * This compiles the grammar-based expression to JMESPath for evaluation.
 */
public record WithNbtExpr(
        NbtExpr expression,
        String jmesPathExpression,
        NbtJmesPathEvaluator evaluator
) implements ASTNode, WithClause, ToStringPretty {

    /**
     * Creates a WithNbtExpr node from an NbtExpr, compiling to JMESPath.
     *
     * @param expression The NBT expression
     * @return A new WithNbtExpr node
     * @throws io.burt.jmespath.parser.ParseException if the compiled expression is invalid
     */
    public static WithNbtExpr create(NbtExpr expression) {
        String jmesPath = expression.toJmesPath();
        SFM.LOGGER.debug("NBT expression \"{}\" compiled to JMESPath \"{}\"", expression, jmesPath);
        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile(jmesPath);
        return new WithNbtExpr(expression, jmesPath, evaluator);
    }

    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        return NbtJmesPathEvaluator.isTruthy(evaluator.search(NbtJmesPathEvaluator.getJsonFromStack(stack)));
    }

    @Override
    public String toString() {
        return "NBT " + expression + " (JMESPath: " + jmesPathExpression + ")";
    }
}
