package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SFMKeybindingNavigationActionTests {
    @Test
    void paletteNamesDistinguishSfmKeyBindsFromMinecraftControls() {
        String sfmKeyBinds = new OpenKeyBindingScreenAction().title().getString();
        String minecraftControls = new OpenMinecraftControlsAction().title().getString();

        assertEquals("Open SFM Key Binds", sfmKeyBinds);
        assertEquals("Open Minecraft Controls", minecraftControls);
        assertNotEquals(sfmKeyBinds, minecraftControls);
    }
}
