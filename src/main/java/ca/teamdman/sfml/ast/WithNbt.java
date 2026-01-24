package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.NbtJmesPathEvaluator;

/**
 * AST node for NBT filtering using JMESPath expressions.
 * Example usage: INPUT WITH NBT "Damage > `10`" FROM a
 */
public record WithNbt(
        String jmesPathExpression,
        NbtJmesPathEvaluator evaluator
) implements ASTNode, WithClause, ToStringPretty {

    /**
     * Creates a WithNbt node, compiling the JMESPath expression for validation.
     *
     * @param jmesPathExpression The JMESPath expression string
     * @return A new WithNbt node
     * @throws io.burt.jmespath.parser.ParseException if the expression is invalid
     */
    public static WithNbt create(String jmesPathExpression) {
        SFM.LOGGER.debug("NBT quoted JMESPath expression \"{}\"", jmesPathExpression);
        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile(jmesPathExpression);
        return new WithNbt(jmesPathExpression, evaluator);
    }

    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        return evaluator.matchesNbt(NbtJmesPathEvaluator.getNbtFromStack(stack));
    }

    @Override
    public String toString() {
        return "NBT \"" + jmesPathExpression.replace("\"", "\\\"") + "\"";
    }
}
