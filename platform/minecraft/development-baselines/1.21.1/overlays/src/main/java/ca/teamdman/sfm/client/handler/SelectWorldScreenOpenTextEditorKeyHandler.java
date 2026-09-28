package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenDiskOpenContext;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.neoforged.neoforge.client.event.InputEvent;

import java.util.Date;
import java.util.stream.Collectors;

public class SelectWorldScreenOpenTextEditorKeyHandler {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
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
            ISFMTextEditScreenOpenContext openContext = new SFMTextEditScreenDiskOpenContext(
                    initialContent,
                    LabelPositionHolder.empty(),
                    (x) -> {
                    }
            );
            SFMScreenChangeHelpers.showProgramEditScreen(openContext);
        }
    }
}
