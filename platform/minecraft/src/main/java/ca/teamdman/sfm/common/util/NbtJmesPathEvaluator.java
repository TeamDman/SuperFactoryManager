package ca.teamdman.sfm.common.util;

import com.google.gson.*;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.gson.GsonRuntime;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for evaluating JMESPath expressions against Minecraft NBT data.
 * Converts NBT to JSON, then applies JMESPath queries.
 */
public class NbtJmesPathEvaluator {
    private static final JmesPath<JsonElement> JMESPATH_RUNTIME = new GsonRuntime();
    private final Expression<JsonElement> expression;

    public NbtJmesPathEvaluator(String jmesPathExpression) {
        this.expression = JMESPATH_RUNTIME.compile(jmesPathExpression);
    }

    /**
     * Compiles a JMESPath expression, throwing an exception if it's invalid.
     * Use this method to validate expressions at parse time.
     *
     * @param jmesPathExpression The JMESPath expression to compile
     * @return A compiled NbtJmesPathEvaluator
     * @throws io.burt.jmespath.parser.ParseException if the expression is invalid
     */
    public static NbtJmesPathEvaluator compile(String jmesPathExpression) {
        return new NbtJmesPathEvaluator(jmesPathExpression);
    }

    /**
     * Evaluates the JMESPath expression against an ItemStack's NBT.
     *
     * @param stack The ItemStack to query
     * @return true if the result is truthy (non-null, non-empty, non-false, non-zero)
     */
    public boolean matchesItemStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return matchesNbt(tag);
    }

    /**
     * Evaluates the JMESPath expression against a FluidStack's NBT.
     *
     * @param stack The FluidStack to query
     * @return true if the result is truthy (non-null, non-empty, non-false, non-zero)
     */
    public boolean matchesFluidStack(FluidStack stack) {
        CompoundTag tag = stack.getTag();
        return matchesNbt(tag);
    }

    /**
     * Evaluates the JMESPath expression against a CompoundTag.
     *
     * @param tag The NBT tag to query (may be null)
     * @return true if the result is truthy
     */
    public boolean matchesNbt(@Nullable CompoundTag tag) {
        JsonElement json = nbtToJson(tag);
        JsonElement result = expression.search(json);
        return isTruthy(result);
    }

    /**
     * Extracts the NBT CompoundTag from a stack.
     * Supports ItemStack and FluidStack.
     *
     * @param stack The stack to extract NBT from
     * @return The CompoundTag, or null if the stack type is not supported or has no NBT
     */
    public static @Nullable CompoundTag getNbtFromStack(Object stack) {
        if (stack instanceof ItemStack itemStack) {
            return itemStack.getTag();
        } else if (stack instanceof FluidStack fluidStack) {
            return fluidStack.getTag();
        }
        return null;
    }

    /**
     * Converts a Minecraft NBT Tag to a Gson JsonElement.
     *
     * @param tag The NBT tag (may be null)
     * @return The corresponding JsonElement
     */
    public static JsonElement nbtToJson(@Nullable Tag tag) {
        if (tag == null) {
            return JsonNull.INSTANCE;
        }

        return switch (tag.getId()) {
            case Tag.TAG_BYTE -> new JsonPrimitive(((ByteTag) tag).getAsByte());
            case Tag.TAG_SHORT -> new JsonPrimitive(((ShortTag) tag).getAsShort());
            case Tag.TAG_INT -> new JsonPrimitive(((IntTag) tag).getAsInt());
            case Tag.TAG_LONG -> new JsonPrimitive(((LongTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> new JsonPrimitive(((FloatTag) tag).getAsFloat());
            case Tag.TAG_DOUBLE -> new JsonPrimitive(((DoubleTag) tag).getAsDouble());
            case Tag.TAG_STRING -> new JsonPrimitive(tag.getAsString());
            case Tag.TAG_BYTE_ARRAY -> byteArrayToJson((ByteArrayTag) tag);
            case Tag.TAG_INT_ARRAY -> intArrayToJson((IntArrayTag) tag);
            case Tag.TAG_LONG_ARRAY -> longArrayToJson((LongArrayTag) tag);
            case Tag.TAG_LIST -> listToJson((ListTag) tag);
            case Tag.TAG_COMPOUND -> compoundToJson((CompoundTag) tag);
            default -> JsonNull.INSTANCE;
        };
    }

    private static JsonArray byteArrayToJson(ByteArrayTag tag) {
        JsonArray array = new JsonArray();
        for (byte b : tag.getAsByteArray()) {
            array.add(b);
        }
        return array;
    }

    private static JsonArray intArrayToJson(IntArrayTag tag) {
        JsonArray array = new JsonArray();
        for (int i : tag.getAsIntArray()) {
            array.add(i);
        }
        return array;
    }

    private static JsonArray longArrayToJson(LongArrayTag tag) {
        JsonArray array = new JsonArray();
        for (long l : tag.getAsLongArray()) {
            array.add(l);
        }
        return array;
    }

    private static JsonArray listToJson(ListTag tag) {
        JsonArray array = new JsonArray();
        for (Tag element : tag) {
            array.add(nbtToJson(element));
        }
        return array;
    }

    private static JsonObject compoundToJson(CompoundTag tag) {
        JsonObject object = new JsonObject();
        for (String key : tag.getAllKeys()) {
            object.add(key, nbtToJson(tag.get(key)));
        }
        return object;
    }

    /**
     * Determines if a JMESPath result is truthy.
     * A result is truthy unless it is:
     * - null
     * - empty array
     * - empty object
     * - empty string
     * - boolean false
     * - number 0
     *
     * @param result The result from a JMESPath expression
     * @return true if the result is truthy
     */
    public static boolean isTruthy(@Nullable JsonElement result) {
        if (result == null || result.isJsonNull()) {
            return false;
        }

        if (result.isJsonPrimitive()) {
            JsonPrimitive primitive = result.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsDouble() != 0;
            }
            if (primitive.isString()) {
                return !primitive.getAsString().isEmpty();
            }
        }

        if (result.isJsonArray()) {
            return result.getAsJsonArray().size() > 0;
        }

        if (result.isJsonObject()) {
            return result.getAsJsonObject().size() > 0;
        }

        return true;
    }

    /**
     * Gets the expression string for display/debugging purposes.
     */
    public String getExpressionString() {
        return expression.toString();
    }
}
