package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.NbtJmesPathEvaluator;
import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile(jmesPath);
        return new WithNbtExpr(expression, jmesPath, evaluator);
    }

    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        JsonElement json = getJsonFromStack(stack);
        return NbtJmesPathEvaluator.isTruthy(evaluator.search(json));
    }

    /**
     * Converts a stack to a JSON representation for JMESPath querying.
     *
     * @param stack The stack to convert
     * @return A JsonElement representing the stack's full serialized form
     */
    private static JsonElement getJsonFromStack(Object stack) {
        if (stack instanceof ItemStack itemStack) {
            return NbtJmesPathEvaluator.itemStackToJson(itemStack);
        } else if (stack instanceof FluidStack fluidStack) {
            return NbtJmesPathEvaluator.fluidStackToJson(fluidStack);
        }
        return NbtJmesPathEvaluator.nbtToJson(null);
    }

    @Override
    public String toString() {
        return "NBT " + expression;
    }
}
