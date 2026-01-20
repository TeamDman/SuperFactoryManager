package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.util.NbtJmesPathEvaluator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

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
        return "NBT \"" + jmesPathExpression.replace("\"", "\\\"") + "\"";
    }
}
