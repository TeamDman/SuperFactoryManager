package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.draw.SFMDrawWorkspace;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

public class SfmDrawOpenKeyHandler {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (!SFMKeyMappings.isKeyDown(SFMKeyMappings.IDE_OPEN_DRAW_KEY)) {
            return;
        }

        if (minecraft.screen instanceof SfmDrawScreen) {
            minecraft.setScreen(null);
        } else if (minecraft.screen == null) {
            SFMDrawWorkspace.openDefaultCanvasScreen();
        } else {
            return;
        }

        KeyMapping.set(InputConstants.getKey(event.getKey(), event.getScanCode()), false);
    }
}