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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemPickerEntryTests {
    @Test
    public void accessibleNameCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
{% case minecraft_version %}
{% when "26.1.2" %}
                () -> new SFMItemPickerEntry(SFMResourceLocation.parse("minecraft:paper"), "  "));
{% when "1.21", "1.21.1" %}
                () -> new SFMItemPickerEntry(SFMResourceLocation.parse("minecraft:paper"), "  "));
{% else %}
                () -> new SFMItemPickerEntry(new ResourceLocation("minecraft:paper"), "  "));
{% endcase %}
    }

    @Test
    public void accessibleNameParticipatesInSearchWithoutReplacingStableId() {
        SFMItemPickerEntry entry = new SFMItemPickerEntry(
{% case minecraft_version %}
{% when "26.1.2" %}
                SFMResourceLocation.parse("sfm:disk"), "SFM Program Disk"
{% when "1.21", "1.21.1" %}
                SFMResourceLocation.parse("sfm:disk"), "SFM Program Disk"
{% else %}
                new ResourceLocation("sfm:disk"), "SFM Program Disk"
{% endcase %}
        );
        assertTrue(entry.matches("program"));
        assertTrue(entry.matches("sfm:disk"));
        assertEquals(java.util.List.of("SFM Program Disk", "sfm:disk"), entry.accessibleDetails());
    }
}
