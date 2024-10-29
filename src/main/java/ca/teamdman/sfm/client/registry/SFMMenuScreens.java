package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.gui.screen.ManagerScreen;
import ca.teamdman.sfm.client.gui.screen.ProxyScreen;
import ca.teamdman.sfm.common.registry.SFMMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = SFM.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class SFMMenuScreens {
    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(SFMMenus.MANAGER_MENU.get(), ManagerScreen::new);
        event.register(SFMMenus.PROXY_MENU.get(), ProxyScreen::new);
    }
}
