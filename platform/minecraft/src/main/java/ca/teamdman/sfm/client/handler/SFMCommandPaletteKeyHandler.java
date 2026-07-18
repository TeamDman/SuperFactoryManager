package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;

public final class SFMCommandPaletteKeyHandler {
    private static boolean commandPaletteKeyDown;

    private SFMCommandPaletteKeyHandler() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // KeyMapping#consumeClick is not reliable while a Minecraft GUI is
        // active: the title screen can consume the key event before the
        // mapping's click counter is updated.  Use the same physical-key
        // path as the title-screen Ctrl+E handler, with an explicit edge
        // detector so holding Ctrl+K does not repeatedly toggle the palette.
        boolean keyDown = SFMKeyMappings.isKeyDown(SFMKeyMappings.COMMAND_PALETTE_KEY);
        if (!keyDown) {
            commandPaletteKeyDown = false;
            return;
        }
        if (commandPaletteKeyDown) return;
        commandPaletteKeyDown = true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SFMCommandPaletteScreen palette) {
            palette.onClose();
            return;
        }
        openFromCurrentScreen();
    }

    /**
     * Opens the palette through the same registered action used by Ctrl+K.
     * Game puppets use this to exercise the client action surface without
     * depending on an OS-level keyboard injection API.
     */
    public static boolean openFromCurrentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SFMCommandPaletteScreen) {
            return true;
        }
        SFMClientActionContext context = SFMCommandPaletteScreen.createOriginContext();
        try {
            SFMClientActions.commandTree().execute(
                    "sfm action invoke " + new ResourceLocation(SFM.MOD_ID, "palette/open"),
                    new SFMClientActionSource(context)
            );
            return minecraft.screen instanceof SFMCommandPaletteScreen;
        } catch (CommandSyntaxException exception) {
            SFM.LOGGER.error("Unable to open the SFM command palette", exception);
            return false;
        } catch (RuntimeException exception) {
            SFM.LOGGER.error("Unable to open the SFM command palette", exception);
            return false;
        }
    }
}
