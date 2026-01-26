package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenTitleScreenOpenContext;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;

public class TitleScreenOpenTextEditorOnLaunchHandler {
    public static boolean firstTime = false; // disabled for now lol
//    public static boolean firstTime = true;
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        if (!firstTime) return;
        if (event.getNewScreen() instanceof TitleScreen titleScreen) {
            firstTime = false;

            ISFMTextEditScreenOpenContext ctx = new SFMTextEditScreenTitleScreenOpenContext(
                    "",
                    LabelPositionHolder.empty(),
                    s -> {},
                    titleScreen
            );
            ISFMTextEditScreen screen = SFMScreenChangeHelpers.createProgramEditScreen(ctx);
            event.setNewScreen(screen.asScreen());
        }
    }
}
