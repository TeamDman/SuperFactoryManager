package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Matches the serialized value of an item data component.
 *
 * <p>Scalar values are compared as text, while structured values can be supplied as JSON.
 * Components that cannot be serialized via their persistent codec do not match.
 */
public record WithComponent(String componentId, String expectedValue)
        implements ASTNode, WithClause, ToStringPretty {

    @Override
    public <STACK> boolean matchesStack(ResourceType<STACK, ?, ?> resourceType, STACK stack) {

        if (!(stack instanceof ItemStack itemStack)) {
            return false;
        }

        ResourceLocation id = ResourceLocation.tryParse(componentId);
        if (id == null || !BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id)) {
            return false;
        }

        DataComponentType<?> componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        return componentType != null && matchesComponent(itemStack, componentType, expectedValue);
    }

    private static <T> boolean matchesComponent(
            ItemStack stack,
            DataComponentType<T> componentType,
            String expectedValue
    ) {

        T value = stack.get(componentType);
        Codec<T> codec = componentType.codec();
        if (value == null || codec == null) {
            return false;
        }

        try {
            return codec.encodeStart(JsonOps.INSTANCE, value)
                    .result()
                    .filter(actual -> matchesEncodedValue(actual, expectedValue))
                    .isPresent();
        } catch (RuntimeException ignored) {
            // A modded component's codec must not crash the factory manager's tick.
            return false;
        }
    }

    private static boolean matchesEncodedValue(JsonElement actual, String expectedValue) {

        if (actual.isJsonPrimitive()) {
            return ((JsonPrimitive) actual).getAsString().equals(expectedValue);
        }
        try {
            return actual.equals(JsonParser.parseString(expectedValue));
        } catch (JsonParseException ignored) {
            return false;
        }
    }

    @Override
    public String toString() {

        return "COMPONENT \"" + componentId.replace("\"", "\\\"") + "\" = \""
               + expectedValue.replace("\"", "\\\"") + "\"";
    }
}
