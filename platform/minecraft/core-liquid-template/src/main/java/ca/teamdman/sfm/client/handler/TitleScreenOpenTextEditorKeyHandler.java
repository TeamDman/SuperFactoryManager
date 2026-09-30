package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenTitleScreenOpenContext;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.client.event.InputEvent;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.client.event.InputEvent;
{% endcase %}

public class TitleScreenOpenTextEditorKeyHandler {
    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onKey(InputEvent.Key event) {
        if (
                SFMKeyMappings.isKeyDown(SFMKeyMappings.TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY)
                && Minecraft.getInstance().screen instanceof TitleScreen titleScreen
        ) {
            String initialContent = """
                    Hi there!
                    """.stripTrailing().stripIndent();
            ISFMTextEditScreenOpenContext openContext = new SFMTextEditScreenTitleScreenOpenContext(
                    initialContent,
                    LabelPositionHolder.empty(),
                    (x) -> {
                    },
                    titleScreen
            );
            SFMScreenChangeHelpers.showProgramEditScreen(openContext);
        }
    }
}
