package ca.teamdman.sfm.client.screen.item_picker;

{% case minecraft_version %}
{% when "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

import net.minecraft.resources.Identifier;
{% when "1.21", "1.21.1" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

import net.minecraft.resources.ResourceLocation;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemPickerQueryTests {
    private static final SFMItemPickerEntry IRON_INGOT = new SFMItemPickerEntry(
            id("minecraft:iron_ingot"),
            "Iron Ingot",
            List.of(id("forge:ingots"), id("forge:ingots/iron"))
    );
    private static final SFMItemPickerEntry OAK_LOG = new SFMItemPickerEntry(
            id("minecraft:oak_log"),
            "Oak Log",
            List.of(id("minecraft:logs"))
    );

    @Test
    public void wildcardResourceMatcherUsesTheSfmlResourceRule() {
        SFMItemPickerQuery.ParseResult result = SFMItemPickerQuery.parse("minecraft:*_ingot");
        assertTrue(result.valid(), result.diagnostic());
        assertTrue(result.query().matches(IRON_INGOT));
        assertFalse(result.query().matches(OAK_LOG));
    }

    @Test
    public void disjunctionUsesTheSfmlAst() {
        SFMItemPickerQuery.ParseResult result = SFMItemPickerQuery.parse("sfm:* OR minecraft:*_log");
        assertTrue(result.valid(), result.diagnostic());
        assertFalse(result.query().matches(IRON_INGOT));
        assertTrue(result.query().matches(OAK_LOG));
    }

    @Test
    public void nonItemResourceTypesDoNotLeakIntoTheItemPicker() {
        SFMItemPickerQuery.ParseResult result = SFMItemPickerQuery.parse("fluid:minecraft:iron_ingot");
        assertTrue(result.valid(), result.diagnostic());
        assertFalse(result.query().matches(IRON_INGOT));
    }

    @Test
    public void withTagUsesExistingTagMatcherSemantics() {
        SFMItemPickerQuery.ParseResult result = SFMItemPickerQuery.parse("* WITH TAG #forge:ingots/**");
        assertTrue(result.valid(), result.diagnostic());
        assertTrue(result.query().usesTags());
        assertTrue(result.query().matches(IRON_INGOT));
        assertFalse(result.query().matches(OAK_LOG));
    }

    @Test
    public void quotedResourceKeepsExplicitRegexSemantics() {
        SFMItemPickerQuery.ParseResult result = SFMItemPickerQuery.parse("\"minecraft:iron.*\"");
        assertTrue(result.valid(), result.diagnostic());
        assertTrue(result.query().matches(IRON_INGOT));
        assertFalse(result.query().matches(OAK_LOG));
    }

    @Test
    public void malformedMatcherProducesADiagnostic() {
        SFMItemPickerQuery.ParseResult result = SFMItemPickerQuery.parse("minecraft:* WITH TAG #");
        assertFalse(result.valid());
        assertFalse(result.diagnostic().isBlank());
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private static Identifier id(String value) {
{% else %}
    private static ResourceLocation id(String value) {
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
        return SFMResourceLocation.parse(value);
{% when "1.21", "1.21.1" %}
        return SFMResourceLocation.parse(value);
{% else %}
        return new ResourceLocation(value);
{% endcase %}
    }
}
