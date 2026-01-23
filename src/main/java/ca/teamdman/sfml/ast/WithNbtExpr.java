package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.NbtJmesPathEvaluator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

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
        CompoundTag tag = getNbtFromStack(stack);
        return evaluator.matchesNbt(tag);
    }

    /**
     * Extracts the NBT CompoundTag from a stack.
     * Supports ItemStack and FluidStack.
     *
     * @param stack The stack to extract NBT from
     * @return The CompoundTag, or null if the stack type is not supported or has no NBT
     */
    private static CompoundTag getNbtFromStack(Object stack) {
        if (stack instanceof ItemStack itemStack) {
            return itemStack.getTag();
        } else if (stack instanceof FluidStack fluidStack) {
            return fluidStack.getTag();
        }
        return null;
    }

    @Override
    public String toString() {
        return "NBT " + expression;
    }
}
