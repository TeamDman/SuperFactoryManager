package ca.teamdman.sfm.client.inspection;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
}
