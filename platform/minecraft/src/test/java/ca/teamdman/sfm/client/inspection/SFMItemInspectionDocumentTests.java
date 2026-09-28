package ca.teamdman.sfm.client.inspection;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMItemInspectionDocumentTests {
    @Test
    void genericDocumentIncludesTooltipIdAndPrettyItemData() {
        String document = SFMItemInspectionDocument.render(
                "Example Item",
                "test:example",
                3,
                List.of("Example Item", "A useful line"),
                "{\n  \"tag\": {}\n}",
                null
        );

        assertTrue(document.contains("item-id: test:example"));
        assertTrue(document.contains("count: 3"));
        assertTrue(document.contains("  - A useful line"));
        assertTrue(document.contains("item-stack-data-snbt:\n{\n  \"tag\": {}\n}"));
    }

    @Test
    void packetDocumentAddsPrettyValueWithoutReplacingGenericData() {
        String document = SFMItemInspectionDocument.render(
                "Data Packet",
                "sfm:packet",
                1,
                List.of("Data Packet"),
                "{\"id\": \"sfm:packet\"}",
                "{\n  \"job\": \"assemble\"\n}"
        );

        assertTrue(document.contains("packet-value-json:\n{\n  \"job\": \"assemble\"\n}"));
        assertTrue(document.contains("item-stack-data-snbt:"));
    }

    @Test
    void nestedCompoundAndListTagsAreIndentedDeterministically() {
        CompoundTag root = new CompoundTag();
        ListTag values = new ListTag();
        values.add(StringTag.valueOf("first"));
        values.add(StringTag.valueOf("second"));
        root.put("values", values);
        root.putString("alpha", "a");

        assertEquals(
                "{\n"
                + "  \"alpha\": \"a\",\n"
                + "  \"values\": [\n"
                + "    \"first\",\n"
                + "    \"second\"\n"
                + "  ]\n"
                + "}",
                SFMItemInspectionDocument.prettyTag(root)
        );
    }

    @Test
    void stringTagControlNewlinesRemainEscapedInsideOneSnbtToken() {
        CompoundTag root = new CompoundTag();
        root.putString("message", "first\nsecond\r\tthird");
        root.putString("other-controls", "a\b\f\u0000b");

        String pretty = SFMItemInspectionDocument.prettyTag(root);

        assertTrue(pretty.contains("\"message\": \"first\\nsecond\\r\\tthird\""));
        assertTrue(pretty.contains("\"other-controls\": \"a\\b\\f\\u0000b\""));
        assertTrue(!pretty.contains("first\nsecond"), "SNBT string leaked a literal line break");
    }

    @Test
    void tooltipComponentEvidenceKeepsLocalizedAndRawSections() {
        String document = SFMItemInspectionDocument.renderWithTooltipComponents(
                "Example Item",
                "test:example",
                1,
                List.of("Localized line"),
                List.of("{\"translate\":\"item.test.example\",\"color\":\"gold\"}"),
                "{}",
                null
        );

        assertTrue(document.contains("tooltip (unlocalized):\n  - {\"translate\":\"item.test.example\",\"color\":\"gold\"}"));
        assertTrue(document.contains("tooltip (localized):\n  - Localized line"));
    }

    @Test
    void optionalIngredientOverlayIsAbsentUntilRegistered() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assertTrue(SFMItemInspectionDocument.hoveredStack(null).isEmpty());
        assertTrue(SFMItemInspectionDocument.hoveredIngredient(null).isEmpty());

        ItemStack original = new ItemStack(Items.APPLE);
        ItemStack captured = SFMItemInspectionDocument.hoveredIngredient(() -> java.util.Optional.of(original))
                .orElseThrow();
        assertEquals(Items.APPLE, captured.getItem());
        assertTrue(captured != original, "Inspection should snapshot the optional overlay item");
    }
}
