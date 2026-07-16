package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;

public class TitleScreenOpenTextEditorOnLaunchHandler {
    public static boolean firstTime = true;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        var launchScreen = SFMProperties.clientRunTitleScreen();
        if (launchScreen.isEmpty()) return;
        if (!firstTime) return;
        if (event.getNewScreen() instanceof TitleScreen titleScreen) {
            firstTime = false;
            event.setNewScreen(launchScreen.get().create(titleScreen));
        }
    }
}
