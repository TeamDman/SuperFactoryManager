package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.common.config.SFMConfig;
import com.google.common.base.Strings;
import net.minecraft.client.gui.FontRenderer;

public class SFMTextEditorUtils {

    public static boolean shouldShowLineNumbers() {

        return SFMConfig.client.showLineNumbers;
    }

    public static int getLineNumberWidth(FontRenderer font, int lineCount) {

        if (SFMTextEditorUtils.shouldShowLineNumbers()) {
            int numDigits = String.valueOf(lineCount).length();
            return font.getStringWidth(Strings.repeat("0", numDigits));
        } else {
            return 0;
        }
    }
}
