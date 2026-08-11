package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.TestBarrelTankScreen;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class SFMMenuScreens {
    @SFMSubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(SFMMenus.MANAGER.get(), ManagerScreen::new);
        event.register(SFMMenus.TEST_BARREL_TANK.get(), TestBarrelTankScreen::new);
    }
}
