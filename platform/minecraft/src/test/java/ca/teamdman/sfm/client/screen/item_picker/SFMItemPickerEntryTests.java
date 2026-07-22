package ca.teamdman.sfm.client.screen.item_picker;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemPickerEntryTests {
    @Test
    public void accessibleNameCannotBeBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> new SFMItemPickerEntry(new ResourceLocation("minecraft:paper"), "  "));
    }

    @Test
    public void accessibleNameParticipatesInSearchWithoutReplacingStableId() {
        SFMItemPickerEntry entry = new SFMItemPickerEntry(
                new ResourceLocation("sfm:disk"), "SFM Program Disk"
        );
        assertTrue(entry.matches("program"));
        assertTrue(entry.matches("sfm:disk"));
        assertEquals(java.util.List.of("SFM Program Disk", "sfm:disk"), entry.accessibleDetails());
    }
}
