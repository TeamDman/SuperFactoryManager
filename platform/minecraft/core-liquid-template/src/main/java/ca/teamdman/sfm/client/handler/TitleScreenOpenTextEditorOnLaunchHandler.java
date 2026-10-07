package ca.teamdman.sfm.client.handler;

{% if features.client_launch_screen %}
{% else %}
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.text_editor.ISFMTextEditScreen;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenTitleScreenOpenContext;
{% endif %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% if features.client_launch_screen %}
{% else %}
import ca.teamdman.sfm.common.label.LabelPositionHolder;
{% endif %}
import ca.teamdman.sfm.common.util.SFMDist;
{% if features.client_launch_screen %}
import ca.teamdman.sfm.properties.SFMProperties;
{% endif %}
import net.minecraft.client.gui.screens.TitleScreen;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.event.ScreenEvent;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.client.event.ScreenEvent;
{% endcase %}

public class TitleScreenOpenTextEditorOnLaunchHandler {
{% if features.client_launch_screen %}
    public static boolean firstTime = true;

{% else %}
    public static boolean firstTime = false; // disabled for now lol
//    public static boolean firstTime = true;
{% endif %}
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onTitleScreenOpen(ScreenEvent.Opening event) {
{% if features.client_launch_screen %}
        var launchScreen = SFMProperties.clientRunTitleScreen();
        if (launchScreen.isEmpty()) return;
{% endif %}
        if (!firstTime) return;
        if (event.getNewScreen() instanceof TitleScreen titleScreen) {
            firstTime = false;
{% if features.client_launch_screen %}
            event.setNewScreen(launchScreen.get().create(titleScreen));
{% else %}

            ISFMTextEditScreenOpenContext ctx = new SFMTextEditScreenTitleScreenOpenContext(
                    "",
                    LabelPositionHolder.empty(),
                    s -> {},
                    titleScreen
            );
            ISFMTextEditScreen screen = SFMScreenChangeHelpers.createProgramEditScreen(ctx);
            event.setNewScreen(screen.asScreen());
{% endif %}
        }
    }
}
