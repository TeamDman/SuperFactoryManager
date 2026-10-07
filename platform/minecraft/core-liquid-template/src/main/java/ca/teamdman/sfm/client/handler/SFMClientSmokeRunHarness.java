package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.event.ScreenEvent;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.client.event.ScreenEvent;
{% endcase %}

public class SFMClientSmokeRunHarness {
    private static boolean titleScreenHandled = false;

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
        if (titleScreenHandled || !isSmokeMode() || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }

        titleScreenHandled = true;
        SFM.LOGGER.info("SFM_CLIENT_SMOKE_READY title_screen");
        Minecraft.getInstance().stop();
    }

    private static boolean isSmokeMode() {
        return SFMProperties.clientRunMode() == SFMProperties.ClientRunMode.SMOKE;
    }
}
