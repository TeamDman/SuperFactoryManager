package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.screen.ManagerIdeScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV2;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.ArrayList;

public class OverlayHider {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTryOverlay(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof SFMTextEditScreenV2
            || Minecraft.getInstance().screen instanceof ManagerIdeScreen) {
            event.setCanceled(true);
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTryOverlayElement(RenderGuiOverlayEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof ManagerIdeScreen) {
            event.setCanceled(true);
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onScreenBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        if (Minecraft.getInstance().screen instanceof ManagerIdeScreen) {
            event.setCanceled(true);
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        if (Minecraft.getInstance().screen instanceof ManagerIdeScreen) {
            event.setCanceled(true);
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (Minecraft.getInstance().screen instanceof ManagerIdeScreen) {
            event.setCanceled(true);
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT, priority = EventPriority.LOWEST)
    public static void onScreenInitPost(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ManagerIdeScreen)) {
            return;
        }

        for (GuiEventListener listener : new ArrayList<>(event.getListenersList())) {
            String className = listener.getClass().getName();
            if (className.equals("dev.ftb.mods.ftblibrary.sidebar.SidebarGroupGuiButton")
                || className.contains(".ftblibrary.sidebar.")
                || className.startsWith("mezz.jei.")
                || className.contains(".jei.")) {
                event.removeListener(listener);
            }
        }
    }
}
