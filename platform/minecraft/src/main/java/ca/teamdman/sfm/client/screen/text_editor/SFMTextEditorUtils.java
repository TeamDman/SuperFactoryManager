package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.common.config.SFMConfig;
import net.minecraft.client.gui.Font;

public class SFMTextEditorUtils {

    public static boolean shouldShowLineNumbers() {

        return SFMConfig.getOrDefault(SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.showLineNumbers);
    }

    public static int getLineNumberWidth(Font font, int lineCount) {

        if (SFMTextEditorUtils.shouldShowLineNumbers()) {
            int numDigits = String.valueOf(lineCount).length();
            return font.width("0".repeat(numDigits));
        } else {
            return 0;
        }
    }
}
