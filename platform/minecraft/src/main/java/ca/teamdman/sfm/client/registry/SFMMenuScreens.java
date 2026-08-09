package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.TestBarrelTankScreen;
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
import net.minecraft.client.gui.screens.MenuScreens;

public class SFMMenuScreens {
    public static void register() {
        MenuScreens.register(SFMMenus.MANAGER.get(), ManagerScreen::new);
        MenuScreens.register(SFMMenus.TEST_BARREL_TANK.get(), TestBarrelTankScreen::new);
    }
}
