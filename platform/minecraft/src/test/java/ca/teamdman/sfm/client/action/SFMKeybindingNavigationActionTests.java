package ca.teamdman.sfm.client.action;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMKeybindingNavigationActionTests {
    @Test
    void paletteNamesDistinguishSfmKeyBindsFromMinecraftControls() {
        String sfmKeyBinds = new OpenKeyBindingScreenAction().title().getString();
        String minecraftControls = new OpenMinecraftScreenAction().title().getString();

        assertEquals("Open SFM Key Binds", sfmKeyBinds);
        assertEquals("Open Minecraft screen", minecraftControls);
        assertNotEquals(sfmKeyBinds, minecraftControls);
    }

    @Test
    void typedMinecraftScreenActionSuggestsEverySupportedDestination() throws Exception {
        ResourceLocation actionId = new ResourceLocation("sfm", "minecraft/screen/open");
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(actionId, new OpenMinecraftScreenAction())
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));
        String prefix = "sfm action invoke " + actionId + " ";

        List<String> suggestions = tree.getCompletionSuggestions(tree.parse(prefix, source)).get()
                .getList().stream().map(suggestion -> suggestion.getText()).toList();

        assertTrue(suggestions.containsAll(List.of("TitleScreen", "ControlsScreen", "KeyBindsScreen")));
    }
}
