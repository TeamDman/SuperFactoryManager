package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

public class SfmDrawOpenKeyHandler {
    private static boolean wasKeyDown = false;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            wasKeyDown = false;
            return;
        }

        boolean keyDown = SFMKeyMappings.isKeyDown(SFMKeyMappings.IDE_OPEN_DRAW_KEY);
        if (keyDown && !wasKeyDown) {
            if (minecraft.screen instanceof SfmDrawScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.screen == null) {
                SFMScreenChangeHelpers.showSfmDrawScreen();
            }
        }
        wasKeyDown = keyDown;
    }
}