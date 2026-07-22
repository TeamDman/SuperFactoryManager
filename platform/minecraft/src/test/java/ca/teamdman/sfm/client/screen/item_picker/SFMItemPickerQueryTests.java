package ca.teamdman.sfm.client.screen.item_picker;

import net.minecraft.resources.ResourceLocation;
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
    public void malformedMatcherProducesADiagnostic() {
        SFMItemPickerQuery.ParseResult result = SFMItemPickerQuery.parse("minecraft:* WITH TAG #");
        assertFalse(result.valid());
        assertFalse(result.diagnostic().isBlank());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
