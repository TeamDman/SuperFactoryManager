package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.client.screen.TestBarrelTankScreen;
import ca.teamdman.sfm.common.registry.SFMMenus;
import net.minecraft.client.gui.screens.MenuScreens;

public class SFMMenuScreens {
    public static void register() {
        MenuScreens.register(SFMMenus.MANAGER_MENU.get(), ManagerScreen::new);
        MenuScreens.register(SFMMenus.TEST_BARREL_TANK_MENU.get(), TestBarrelTankScreen::new);
    }
}
