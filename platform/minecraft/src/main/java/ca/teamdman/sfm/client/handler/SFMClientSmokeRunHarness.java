package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

public class SFMClientSmokeRunHarness {
    private static final String MODE_PROPERTY = "sfm.clientRun.mode";

    private static boolean titleScreenHandled = false;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    @MCVersionDependentBehaviour
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        if (titleScreenHandled || !isSmokeMode() || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }

        titleScreenHandled = true;
        SFM.LOGGER.info("SFM_CLIENT_SMOKE_READY title_screen");
        Minecraft.getInstance().stop();
    }

    private static boolean isSmokeMode() {
        return "smoke".equals(System.getProperty(MODE_PROPERTY, ""));
    }
}
