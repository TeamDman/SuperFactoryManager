package ca.teamdman.sfm.common.util;

import com.google.gson.*;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.gson.GsonRuntime;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
     * Evaluates the JMESPath expression against an ItemStack's components.
     * In 1.21+, this builds a JSON object from DataComponents (Damage, Enchantments, CustomData, etc.).
     *
     * @param stack The ItemStack to query
     * @return true if the result is truthy (non-null, non-empty, non-false, non-zero)
     */
    public boolean matchesItemStack(ItemStack stack) {
        JsonElement json = itemStackToJson(stack);
        JsonElement result = expression.search(json);
        return isTruthy(result);
    }

    /**
     * Converts an ItemStack to a JSON object by serializing all its DataComponents.
     * The components are flattened into a user-friendly structure for querying.
     *
     * @param stack The ItemStack to convert
     * @return A JsonElement representing the item in a query-friendly format
     */
    public static JsonElement itemStackToJson(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return JsonNull.INSTANCE;
        }
        return itemStackToJson(stack, server.registryAccess());
    }

    /**
     * Converts an ItemStack to a JSON object using the provided registry access.
     * The result is transformed into a user-friendly structure:
     * - "id": the item id
     * - "count": the stack count
     * - Each component is flattened with its namespace prefix stripped (e.g., "damage" not "minecraft:damage")
     * - Enchantments are transformed into an array format for easy querying
     *
     * @param stack The ItemStack to convert
     * @param registries The registry access for serialization
     * @return A JsonElement representing the item in a query-friendly format
     */
    public static JsonElement itemStackToJson(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return new JsonObject();
        }

        // ItemStack.save() throws IllegalStateException for counts > 99
        // Workaround: save a copy with count=1, then use the actual count
        int actualCount = stack.getCount();
        ItemStack copyForSave = stack.copyWithCount(1);

        Tag nbt = copyForSave.save(registries);
        JsonElement raw = nbtToJson(nbt);

        if (!raw.isJsonObject()) {
            return raw;
        }

        JsonObject rawObj = raw.getAsJsonObject();
        JsonObject result = new JsonObject();

        // Copy id from NBT
        if (rawObj.has("id")) {
            result.add("id", rawObj.get("id"));
        }
        // Use the actual count from the original stack
        result.addProperty("count", actualCount);

        // Flatten components
        if (rawObj.has("components") && rawObj.get("components").isJsonObject()) {
            JsonObject components = rawObj.getAsJsonObject("components");
            for (var entry : components.entrySet()) {
                String key = entry.getKey();
                // Strip "minecraft:" prefix for convenience
                if (key.startsWith("minecraft:")) {
                    key = key.substring("minecraft:".length());
                }
                // Special handling for enchantments - transform to array format
                if (key.equals("enchantments") && entry.getValue().isJsonObject()) {
                    result.add("enchantments", transformEnchantments(entry.getValue().getAsJsonObject()));
                } else {
                    result.add(key, entry.getValue());
                }
            }
        }

        return result;
    }

    /**
     * Transforms enchantments from the Minecraft format to an array format.
     * Input: {"levels": {"minecraft:sharpness": 5}}
     * Output: [{"id": "minecraft:sharpness", "lvl": 5}]
     */
    private static JsonArray transformEnchantments(JsonObject enchObj) {
        JsonArray arr = new JsonArray();
        if (enchObj.has("levels") && enchObj.get("levels").isJsonObject()) {
            JsonObject levels = enchObj.getAsJsonObject("levels");
            for (var entry : levels.entrySet()) {
                JsonObject ench = new JsonObject();
                ench.addProperty("id", entry.getKey());
                ench.add("lvl", entry.getValue());
                arr.add(ench);
            }
        }
        return arr;
    }

    /**
     * Evaluates the JMESPath expression against a FluidStack's components.
     *
     * @param stack The FluidStack to query
     * @return true if the result is truthy (non-null, non-empty, non-false, non-zero)
     */
    public boolean matchesFluidStack(FluidStack stack) {
        JsonElement json = fluidStackToJson(stack);
        JsonElement result = expression.search(json);
        return isTruthy(result);
    }

    /**
     * Converts a FluidStack to a JSON object by serializing all its DataComponents.
     *
     * @param stack The FluidStack to convert
     * @return A JsonElement representing the fluid in a query-friendly format
     */
    public static JsonElement fluidStackToJson(FluidStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return JsonNull.INSTANCE;
        }
        return fluidStackToJson(stack, server.registryAccess());
    }

    /**
     * Converts a FluidStack to a JSON object using the provided registry access.
     * Components are flattened with namespace prefixes stripped for convenience.
     *
     * @param stack The FluidStack to convert
     * @param registries The registry access for serialization
     * @return A JsonElement representing the fluid in a query-friendly format
     */
    public static JsonElement fluidStackToJson(FluidStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return new JsonObject();
        }
        Tag nbt = stack.save(registries);
        JsonElement raw = nbtToJson(nbt);

        if (!raw.isJsonObject()) {
            return raw;
        }

        JsonObject rawObj = raw.getAsJsonObject();
        JsonObject result = new JsonObject();

        // Copy id and amount
        if (rawObj.has("id")) {
            result.add("id", rawObj.get("id"));
        }
        if (rawObj.has("amount")) {
            result.add("amount", rawObj.get("amount"));
        }

        // Flatten components
        if (rawObj.has("components") && rawObj.get("components").isJsonObject()) {
            JsonObject components = rawObj.getAsJsonObject("components");
            for (var entry : components.entrySet()) {
                String key = entry.getKey();
                // Strip "minecraft:" prefix for convenience
                if (key.startsWith("minecraft:")) {
                    key = key.substring("minecraft:".length());
                }
                result.add(key, entry.getValue());
            }
        }

        return result;
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
     * Searches the given JSON element using the compiled JMESPath expression.
     *
     * @param json The JSON element to search
     * @return The result of the JMESPath query
     */
    public JsonElement search(JsonElement json) {
        return expression.search(json);
    }

    /**
     * Converts a stack to a JSON representation for JMESPath querying.
     *
     * @param stack The stack to convert
     * @return A JsonElement representing the stack's full serialized form
     */
    public static JsonElement getJsonFromStack(Object stack) {
        if (stack instanceof ItemStack itemStack) {
            return itemStackToJson(itemStack);
        } else if (stack instanceof FluidStack fluidStack) {
            return fluidStackToJson(fluidStack);
        }
        return nbtToJson(null);
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
