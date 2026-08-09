package ca.teamdman.sfml.test;

import ca.teamdman.sfm.common.util.NbtJmesPathEvaluator;
import ca.teamdman.sfml.ast.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import io.burt.jmespath.parser.ParseException;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ca.teamdman.sfml.test.SFMLTestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the NBT JMESPath filtering feature.
 */
public class SFMLNbtFilteringTests {

    // ==================== Grammar Parsing Tests ====================

    @Test
    public void nbtFilterBasicSyntax() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "Damage" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterWithDamageQuery() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "Damage > `10`" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterWithEnchantmentsQuery() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "Enchantments[0]" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterComplexEnchantmentQuery() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "Enchantments[?id == 'minecraft:sharpness' && lvl > `3`]" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterWithoutNbt() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITHOUT NBT "display.Name" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterCombinedWithTag() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "Damage" AND TAG minecraft:swords FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterWithOr() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "Damage" OR TAG minecraft:tools FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterNegated() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NOT NBT "Damage" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterParenthesized() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH (NBT "Damage" AND TAG minecraft:swords) FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterInResourceLimit() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT 64 diamond_sword WITH NBT "Damage < `100`" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtFilterInBooleanHas() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT FROM a
                IF a HAS > 0 diamond_sword WITH NBT "Enchantments[0]" THEN
                    OUTPUT TO b
                END
            END
        """);
    }

    @Test
    public void nbtFilterInvalidExpressionSyntax() {
        // Invalid JMESPath syntax should cause a compile error
        assertCompileErrorsPresent("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "[?invalid syntax here" FROM a
                OUTPUT TO b
            END
        """);
    }

    // ==================== NBT to JSON Conversion Tests ====================

    @Test
    public void nbtToJsonNull() {
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(null);
        assertTrue(result.isJsonNull());
    }

    @Test
    public void nbtToJsonByte() {
        ByteTag tag = ByteTag.valueOf((byte) 42);
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonPrimitive());
        assertEquals(42, result.getAsInt());
    }

    @Test
    public void nbtToJsonShort() {
        ShortTag tag = ShortTag.valueOf((short) 1000);
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonPrimitive());
        assertEquals(1000, result.getAsInt());
    }

    @Test
    public void nbtToJsonInt() {
        IntTag tag = IntTag.valueOf(100000);
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonPrimitive());
        assertEquals(100000, result.getAsInt());
    }

    @Test
    public void nbtToJsonLong() {
        LongTag tag = LongTag.valueOf(10000000000L);
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonPrimitive());
        assertEquals(10000000000L, result.getAsLong());
    }

    @Test
    public void nbtToJsonFloat() {
        FloatTag tag = FloatTag.valueOf(3.14f);
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonPrimitive());
        assertEquals(3.14f, result.getAsFloat(), 0.001f);
    }

    @Test
    public void nbtToJsonDouble() {
        DoubleTag tag = DoubleTag.valueOf(3.14159265359);
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonPrimitive());
        assertEquals(3.14159265359, result.getAsDouble(), 0.0000001);
    }

    @Test
    public void nbtToJsonString() {
        StringTag tag = StringTag.valueOf("hello world");
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonPrimitive());
        assertEquals("hello world", result.getAsString());
    }

    @Test
    public void nbtToJsonByteArray() {
        ByteArrayTag tag = new ByteArrayTag(new byte[]{1, 2, 3});
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonArray());
        assertEquals(3, result.getAsJsonArray().size());
        assertEquals(1, result.getAsJsonArray().get(0).getAsInt());
        assertEquals(2, result.getAsJsonArray().get(1).getAsInt());
        assertEquals(3, result.getAsJsonArray().get(2).getAsInt());
    }

    @Test
    public void nbtToJsonIntArray() {
        IntArrayTag tag = new IntArrayTag(new int[]{100, 200, 300});
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonArray());
        assertEquals(3, result.getAsJsonArray().size());
        assertEquals(100, result.getAsJsonArray().get(0).getAsInt());
        assertEquals(200, result.getAsJsonArray().get(1).getAsInt());
        assertEquals(300, result.getAsJsonArray().get(2).getAsInt());
    }

    @Test
    public void nbtToJsonLongArray() {
        LongArrayTag tag = new LongArrayTag(new long[]{1000000000L, 2000000000L});
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonArray());
        assertEquals(2, result.getAsJsonArray().size());
        assertEquals(1000000000L, result.getAsJsonArray().get(0).getAsLong());
        assertEquals(2000000000L, result.getAsJsonArray().get(1).getAsLong());
    }

    @Test
    public void nbtToJsonList() {
        ListTag tag = new ListTag();
        tag.add(StringTag.valueOf("a"));
        tag.add(StringTag.valueOf("b"));
        tag.add(StringTag.valueOf("c"));
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonArray());
        assertEquals(3, result.getAsJsonArray().size());
        assertEquals("a", result.getAsJsonArray().get(0).getAsString());
        assertEquals("b", result.getAsJsonArray().get(1).getAsString());
        assertEquals("c", result.getAsJsonArray().get(2).getAsString());
    }

    @Test
    public void nbtToJsonCompound() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", "test");
        tag.putInt("value", 42);
        JsonElement result = NbtJmesPathEvaluator.nbtToJson(tag);
        assertTrue(result.isJsonObject());
        assertEquals("test", result.getAsJsonObject().get("name").getAsString());
        assertEquals(42, result.getAsJsonObject().get("value").getAsInt());
    }

    @Test
    public void nbtToJsonNestedCompound() {
        CompoundTag inner = new CompoundTag();
        inner.putString("innerKey", "innerValue");

        CompoundTag outer = new CompoundTag();
        outer.put("nested", inner);

        JsonElement result = NbtJmesPathEvaluator.nbtToJson(outer);
        assertTrue(result.isJsonObject());
        assertTrue(result.getAsJsonObject().has("nested"));
        assertTrue(result.getAsJsonObject().get("nested").isJsonObject());
        assertEquals("innerValue", result.getAsJsonObject().get("nested").getAsJsonObject().get("innerKey").getAsString());
    }

    // ==================== Truthiness Tests ====================

    @Test
    public void truthinessNull() {
        assertFalse(NbtJmesPathEvaluator.isTruthy(null));
        assertFalse(NbtJmesPathEvaluator.isTruthy(JsonNull.INSTANCE));
    }

    @Test
    public void truthinessBooleanFalse() {
        assertFalse(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(false)));
    }

    @Test
    public void truthinessBooleanTrue() {
        assertTrue(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(true)));
    }

    @Test
    public void truthinessZero() {
        assertFalse(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(0)));
        assertFalse(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(0.0)));
    }

    @Test
    public void truthinessNonZeroNumber() {
        assertTrue(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(1)));
        assertTrue(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(-1)));
        assertTrue(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(3.14)));
    }

    @Test
    public void truthinessEmptyString() {
        assertFalse(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive("")));
    }

    @Test
    public void truthinessNonEmptyString() {
        assertTrue(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive("hello")));
        assertTrue(NbtJmesPathEvaluator.isTruthy(new JsonPrimitive(" ")));
    }

    @Test
    public void truthinessEmptyArray() {
        assertFalse(NbtJmesPathEvaluator.isTruthy(new com.google.gson.JsonArray()));
    }

    @Test
    public void truthinessNonEmptyArray() {
        var array = new com.google.gson.JsonArray();
        array.add(1);
        assertTrue(NbtJmesPathEvaluator.isTruthy(array));
    }

    @Test
    public void truthinessEmptyObject() {
        assertFalse(NbtJmesPathEvaluator.isTruthy(new com.google.gson.JsonObject()));
    }

    @Test
    public void truthinessNonEmptyObject() {
        var obj = new com.google.gson.JsonObject();
        obj.addProperty("key", "value");
        assertTrue(NbtJmesPathEvaluator.isTruthy(obj));
    }

    // ==================== JMESPath Evaluation Tests ====================

    @Test
    public void jmesPathSimpleFieldAccess() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Damage", 50);

        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile("Damage");
        assertTrue(evaluator.matchesNbt(tag));
    }

    @Test
    public void jmesPathMissingField() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Other", 50);

        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile("Damage");
        assertFalse(evaluator.matchesNbt(tag));
    }

    @Test
    public void jmesPathComparison() {
        // JMESPath comparison operators return boolean true/false
        CompoundTag tag = new CompoundTag();
        tag.putInt("Damage", 50);

        // Use comparison expression that returns boolean
        NbtJmesPathEvaluator greaterThan10 = NbtJmesPathEvaluator.compile("Damage > `10`");
        NbtJmesPathEvaluator greaterThan100 = NbtJmesPathEvaluator.compile("Damage > `100`");

        assertTrue(greaterThan10.matchesNbt(tag));
        assertFalse(greaterThan100.matchesNbt(tag));
    }

    @Test
    public void jmesPathArrayAccess() {
        CompoundTag tag = new CompoundTag();
        ListTag enchantments = new ListTag();
        CompoundTag enchant1 = new CompoundTag();
        enchant1.putString("id", "minecraft:sharpness");
        enchant1.putInt("lvl", 5);
        enchantments.add(enchant1);
        tag.put("Enchantments", enchantments);

        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile("Enchantments[0]");
        assertTrue(evaluator.matchesNbt(tag));
    }

    @Test
    public void jmesPathEmptyArrayAccess() {
        CompoundTag tag = new CompoundTag();
        tag.put("Enchantments", new ListTag());

        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile("Enchantments[0]");
        assertFalse(evaluator.matchesNbt(tag));
    }

    @Test
    public void jmesPathNestedFieldAccess() {
        CompoundTag tag = new CompoundTag();
        CompoundTag display = new CompoundTag();
        display.putString("Name", "Test Item");
        tag.put("display", display);

        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile("display.Name");
        assertTrue(evaluator.matchesNbt(tag));
    }

    @Test
    public void jmesPathNullTag() {
        NbtJmesPathEvaluator evaluator = NbtJmesPathEvaluator.compile("Damage");
        assertFalse(evaluator.matchesNbt(null));
    }

    @Test
    public void jmesPathFilterExpression() {
        CompoundTag tag = new CompoundTag();
        ListTag enchantments = new ListTag();

        CompoundTag enchant1 = new CompoundTag();
        enchant1.putString("id", "minecraft:sharpness");
        enchant1.putInt("lvl", 5);
        enchantments.add(enchant1);

        CompoundTag enchant2 = new CompoundTag();
        enchant2.putString("id", "minecraft:unbreaking");
        enchant2.putInt("lvl", 3);
        enchantments.add(enchant2);

        tag.put("Enchantments", enchantments);

        // Filter for sharpness enchantment
        NbtJmesPathEvaluator sharpnessFilter = NbtJmesPathEvaluator.compile(
                "Enchantments[?id == 'minecraft:sharpness']"
        );
        assertTrue(sharpnessFilter.matchesNbt(tag));

        // Filter for non-existent enchantment
        NbtJmesPathEvaluator fireAspectFilter = NbtJmesPathEvaluator.compile(
                "Enchantments[?id == 'minecraft:fire_aspect']"
        );
        assertFalse(fireAspectFilter.matchesNbt(tag));
    }

    @Test
    public void jmesPathInvalidExpressionThrows() {
        assertThrows(ParseException.class, () -> {
            NbtJmesPathEvaluator.compile("[?unclosed bracket");
        });
    }

    // ==================== Grammar-based NBT Expression Tests ====================

    @Test
    public void nbtExprSimpleField() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Damage FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprFieldComparison() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Damage > 10 FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprFieldEquality() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Damage = 0 FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprFieldLessOrEqual() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Damage <= 100 FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprNamespacedComponent() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT minecraft:custom_data FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprNamespacedComponentWithComparison() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT productivebees:gene_group.purity = 100 FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprNestedField() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT display.Name FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprArrayAccess() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Enchantments[0] FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprArrayAccessWithField() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Enchantments[0].id FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprArrayWildcard() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Enchantments[*].lvl FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprArrayFilter() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Enchantments[?id = "minecraft:sharpness"] FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprArrayFilterWithComparison() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Enchantments[?lvl > 3] FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprArrayFilterWithAt() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Enchantments[?@.id = "minecraft:sharpness"] FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprNegativeNumber() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT temperature > -10 FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprBooleanValue() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT active = true FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprCombinedWithTag() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT Damage > 0 AND TAG minecraft:swords FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprOldSyntaxStillWorks() {
        // Verify the old string-based syntax still works
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITH NBT "Enchantments[*].lvl | max(@) > `3`" FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprWithWithout() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT WITHOUT NBT Damage FROM a
                OUTPUT TO b
            END
        """);
    }

    @Test
    public void nbtExprInBooleanHas() {
        assertNoCompileErrors("""
            EVERY 20 TICKS DO
                INPUT FROM a
                IF a HAS > 0 diamond_sword WITH NBT Damage > 10 THEN
                    OUTPUT TO b
                END
            END
        """);
    }

    // ==================== JMESPath Compilation Tests ====================

    @Test
    public void nbtExprCompilesToSimplePath() {
        NbtComponent component = NbtComponent.simple("Damage");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtExpr expr = NbtExpr.pathOnly(path);

        assertEquals("Damage", expr.toJmesPath());
    }

    @Test
    public void nbtExprCompilesToNamespacedPath() {
        NbtComponent component = NbtComponent.namespaced("minecraft", "custom_data");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtExpr expr = NbtExpr.pathOnly(path);

        assertEquals("\"minecraft:custom_data\"", expr.toJmesPath());
    }

    @Test
    public void nbtExprCompilesToNestedPath() {
        NbtComponent component = NbtComponent.simple("display");
        NbtPathElement element = NbtPathElement.field("Name");
        NbtPath path = new NbtPath(component, null, List.of(element));
        NbtExpr expr = NbtExpr.pathOnly(path);

        assertEquals("display.Name", expr.toJmesPath());
    }

    @Test
    public void nbtExprCompilesToArrayPath() {
        NbtComponent component = NbtComponent.simple("Enchantments");
        ArrayIndex.NumberIndex arrayIndex = new ArrayIndex.NumberIndex(0);
        NbtPath path = new NbtPath(component, arrayIndex, List.of());
        NbtExpr expr = NbtExpr.pathOnly(path);

        assertEquals("Enchantments[0]", expr.toJmesPath());
    }

    @Test
    public void nbtExprCompilesToComparison() {
        NbtComponent component = NbtComponent.simple("Damage");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtExpr expr = NbtExpr.comparison(path, ComparisonOperator.GREATER, new NbtValue.NbtNumber(10));

        assertEquals("Damage > `10`", expr.toJmesPath());
    }

    @Test
    public void nbtExprCompilesToStringComparison() {
        NbtFilterPath filterPath = new NbtFilterPath(false, List.of("id"));
        NbtFilterExpr filterExpr = new NbtFilterExpr(
                filterPath,
                ComparisonOperator.EQUALS,
                new NbtValue.NbtString("minecraft:sharpness")
        );
        ArrayIndex.FilterIndex filterIndex = new ArrayIndex.FilterIndex(filterExpr);
        NbtComponent component = NbtComponent.simple("Enchantments");
        NbtPath path = new NbtPath(component, filterIndex, List.of());
        NbtExpr expr = NbtExpr.pathOnly(path);

        assertEquals("Enchantments[?id == 'minecraft:sharpness']", expr.toJmesPath());
    }

    @Test
    public void nbtExprCompilesNamespacedWithNestedField() {
        NbtComponent component = NbtComponent.namespaced("productivebees", "gene_group");
        NbtPathElement element = NbtPathElement.field("purity");
        NbtPath path = new NbtPath(component, null, List.of(element));
        NbtExpr expr = NbtExpr.comparison(path, ComparisonOperator.EQUALS, new NbtValue.NbtNumber(100));

        assertEquals("\"productivebees:gene_group\".purity == `100`", expr.toJmesPath());
    }

    @Test
    public void nbtValueNegativeNumber() {
        NbtValue value = new NbtValue.NbtNumber(-42);
        assertEquals("`-42`", value.toJmesPath());
    }

    @Test
    public void nbtValueBoolean() {
        NbtValue trueVal = new NbtValue.NbtBoolean(true);
        NbtValue falseVal = new NbtValue.NbtBoolean(false);
        assertEquals("`true`", trueVal.toJmesPath());
        assertEquals("`false`", falseVal.toJmesPath());
    }

    @Test
    public void nbtFilterPathWithAt() {
        NbtFilterPath path = new NbtFilterPath(true, List.of("id"));
        assertEquals("@.id", path.toJmesPath());
    }

    @Test
    public void nbtFilterPathWithoutAt() {
        NbtFilterPath path = new NbtFilterPath(false, List.of("nested", "field"));
        assertEquals("nested.field", path.toJmesPath());
    }

    @Test
    public void nbtInExpressionCompilesToContains() {
        NbtComponent component = NbtComponent.namespaced("potion_contents", "potion");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtArrayLiteral array = new NbtArrayLiteral(List.of(
                new NbtValue.NbtString("minecraft:water"),
                new NbtValue.NbtString("minecraft:mundane")
        ));
        NbtExpr expr = NbtExpr.inArray(path, array);

        assertEquals("contains(['minecraft:water', 'minecraft:mundane'], \"potion_contents:potion\")", expr.toJmesPath());
    }

    @Test
    public void nbtInExpressionParsesCorrectly() {
        String input = """
            EVERY 20 TICKS DO
                INPUT potion WITH NBT potion_contents.potion IN ["minecraft:water", "minecraft:mundane"] FROM chest
            END
        """;
        assertNoCompileErrors(input);
    }

    // ==================== Wildcard Pattern Tests ====================

    @Test
    public void nbtWildcardStartsWith() {
        NbtComponent component = NbtComponent.simple("id");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtExpr expr = NbtExpr.comparison(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("mekanism:*"));

        assertEquals("starts_with(id, 'mekanism:')", expr.toJmesPath());
    }

    @Test
    public void nbtWildcardEndsWith() {
        NbtComponent component = NbtComponent.simple("id");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtExpr expr = NbtExpr.comparison(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("*_ore"));

        assertEquals("ends_with(id, '_ore')", expr.toJmesPath());
    }

    @Test
    public void nbtWildcardContains() {
        NbtComponent component = NbtComponent.simple("id");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtExpr expr = NbtExpr.comparison(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("*diamond*"));

        assertEquals("contains(id, 'diamond')", expr.toJmesPath());
    }

    @Test
    public void nbtWildcardAny() {
        NbtComponent component = NbtComponent.simple("id");
        NbtPath path = new NbtPath(component, null, List.of());
        NbtExpr expr = NbtExpr.comparison(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("*"));

        // Just "*" means existence check
        assertEquals("id", expr.toJmesPath());
    }

    @Test
    public void nbtWildcardParsesCorrectly() {
        String input = """
            EVERY 20 TICKS DO
                INPUT WITH NBT id = "mekanism:*" FROM chest
            END
        """;
        assertNoCompileErrors(input);
    }

    @Test
    public void nbtWildcardEndsWithParsesCorrectly() {
        String input = """
            EVERY 20 TICKS DO
                INPUT WITH NBT id = "*_ore" FROM chest
            END
        """;
        assertNoCompileErrors(input);
    }

    @Test
    public void nbtWildcardContainsParsesCorrectly() {
        String input = """
            EVERY 20 TICKS DO
                INPUT WITH NBT id = "*diamond*" FROM chest
            END
        """;
        assertNoCompileErrors(input);
    }

    @Test
    public void nbtWildcardInArrayFilter() {
        String input = """
            EVERY 20 TICKS DO
                INPUT WITH NBT Enchantments[?id = "minecraft:*"] FROM chest
            END
        """;
        assertNoCompileErrors(input);
    }

    @Test
    public void nbtWildcardInArrayFilterConvertsCorrectly() {
        NbtFilterPath path = new NbtFilterPath(false, List.of("id"));
        NbtFilterExpr expr = new NbtFilterExpr(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("minecraft:*"));

        assertEquals("starts_with(id, 'minecraft:')", expr.toJmesPath());
    }

    @Test
    public void nbtWildcardEndsWithInArrayFilter() {
        NbtFilterPath path = new NbtFilterPath(false, List.of("id"));
        NbtFilterExpr expr = new NbtFilterExpr(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("*_protection"));

        assertEquals("ends_with(id, '_protection')", expr.toJmesPath());
    }

    @Test
    public void nbtWildcardContainsInArrayFilter() {
        NbtFilterPath path = new NbtFilterPath(false, List.of("id"));
        NbtFilterExpr expr = new NbtFilterExpr(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("*fire*"));

        assertEquals("contains(id, 'fire')", expr.toJmesPath());
    }

    @Test
    public void nbtWildcardAnyInArrayFilter() {
        NbtFilterPath path = new NbtFilterPath(false, List.of("id"));
        NbtFilterExpr expr = new NbtFilterExpr(path, ComparisonOperator.EQUALS, new NbtValue.NbtString("*"));

        // Just "*" means existence check
        assertEquals("id", expr.toJmesPath());
    }
}
