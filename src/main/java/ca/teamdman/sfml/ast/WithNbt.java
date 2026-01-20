package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.NbtJmesPathEvaluator;
import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

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
        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile(jmesPathExpression);
        return new WithNbt(jmesPathExpression, evaluator);
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
        return "NBT \"" + jmesPathExpression.replace("\"", "\\\"") + "\"";
    }
}
