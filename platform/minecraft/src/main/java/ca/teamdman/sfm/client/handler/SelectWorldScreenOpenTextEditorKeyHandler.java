package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Date;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SelectWorldScreenOpenTextEditorKeyHandler {
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (
                SFMKeyMappings.isKeyDown(SFMKeyMappings.TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY)
                && Minecraft.getInstance().screen instanceof SelectWorldScreen selectWorldScreen
        ) {
            WorldSelectionList list = selectWorldScreen.list;
            String initialContent = list
                    .children()
                    .stream()
                    .filter(WorldSelectionList.WorldListEntry.class::isInstance)
                    .map(WorldSelectionList.WorldListEntry.class::cast)
                    .map(entry -> entry.getLevelName() + "\n" + new Date(entry.summary.getLastPlayed()) + "\n" + entry.summary.getInfo().getString())
                    .collect(Collectors.joining("\n\n"));
            ISFMTextEditScreenOpenContext openContext = new SFMTextEditScreenOpenContext(
                    initialContent,
                    TextEditScreenContentLanguage.PLAINTEXT
            );
            SFMScreenChangeHelpers.showPreferredTextEditScreen(openContext);
        }
    }
}
