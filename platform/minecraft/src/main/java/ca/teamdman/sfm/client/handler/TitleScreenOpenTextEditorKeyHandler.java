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
import net.neoforged.neoforge.client.event.InputEvent;

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
