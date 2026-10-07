package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.client.screen.ManagerScreen;
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.client_manager_gui %}
import ca.teamdman.sfm.client.screen.ClientManagerScreen;
{% endif %}
{% endcase %}
import ca.teamdman.sfm.client.screen.TestBarrelTankScreen;
{% case minecraft_version %}
{% when "1.21", "1.21.1" %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% when "26.1.2" %}
import ca.teamdman.sfm.client.screen.tick_graph.TickTimeGraphRenderState;
import ca.teamdman.sfm.client.screen.tick_graph.TickTimeGraphRenderer;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
import net.minecraft.client.gui.screens.MenuScreens;
{% when "1.21", "1.21.1" %}
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
{% when "26.1.2" %}
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
{% endcase %}

public class SFMMenuScreens {
{% case minecraft_version %}
{% when "1.19.2" %}
    public static void register() {
        MenuScreens.register(SFMMenus.MANAGER.get(), ManagerScreen::new);
{% if features.client_manager_gui %}
        MenuScreens.register(SFMMenus.CLIENT_MANAGER.get(), ClientManagerScreen::new);
{% endif %}
        MenuScreens.register(SFMMenus.TEST_BARREL_TANK.get(), TestBarrelTankScreen::new);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" %}
    public static void register() {
        MenuScreens.register(SFMMenus.MANAGER.get(), ManagerScreen::new);
        MenuScreens.register(SFMMenus.TEST_BARREL_TANK.get(), TestBarrelTankScreen::new);
{% when "1.21", "1.21.1", "26.1.2" %}
    @SFMSubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(SFMMenus.MANAGER.get(), ManagerScreen::new);
        event.register(SFMMenus.TEST_BARREL_TANK.get(), TestBarrelTankScreen::new);
{% endcase %}
    }
{% case minecraft_version %}
{% when "26.1.2" %}

    @SFMSubscribeEvent
    public static void registerPip(RegisterPictureInPictureRenderersEvent event) {
        event.register(
                TickTimeGraphRenderState.class,
                TickTimeGraphRenderer::new
        );
    }
{% endcase %}
}
