package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.ScreenEvent;

public class TitleScreenOpenTextEditorOnLaunchHandler {
    public static final String TITLE_SCREEN_PROPERTY = "sfm.clientRun.titleScreen";
    public static final String OPEN_TEXT_EDITOR_ON_TITLE_SCREEN_PROPERTY = "sfm.clientRun.openTextEditorOnTitleScreen";
    public static boolean firstTime = true;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        String launchScreen = getTitleScreenLaunchScreen();
        if (launchScreen.isEmpty()) return;
        if (!firstTime) return;
        if (event.getNewScreen() instanceof TitleScreen titleScreen) {
            firstTime = false;
            SFMTitleScreenDevScreen devScreen = SFMTitleScreenDevScreen
                    .byId(launchScreen)
                    .orElseThrow(() -> new IllegalStateException("Unsupported SFM title screen launch screen: " + launchScreen));
            event.setNewScreen(devScreen.create(titleScreen));
        }
    }

    private static String getTitleScreenLaunchScreen() {
        String launchScreen = System.getProperty(TITLE_SCREEN_PROPERTY, "").trim();
        if (!launchScreen.isEmpty()) {
            return launchScreen;
        }
        if (Boolean.getBoolean(OPEN_TEXT_EDITOR_ON_TITLE_SCREEN_PROPERTY)) {
            return SFMTitleScreenDevScreen.TEXT_EDITOR.id();
        }
        return "";
    }
}
