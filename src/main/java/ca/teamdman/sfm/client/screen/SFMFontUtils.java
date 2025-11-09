package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

public class SFMFontUtils {
    /**
     * Draws text to the screen
     *
     * @return the width of the drawn text
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            ITextComponent text,
            FontRenderer font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent
    ) {
        return font.drawString(text.getFormattedText(), x, y, -1, dropShadow);
    }

    /**
     * Draws text to the screen
     *
     * @return the width of the drawn text
     */
    @SuppressWarnings("UnusedReturnValue")
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            String text,
            FontRenderer font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent
    ) {
        return font.drawString(text, x, y, -1, dropShadow);
    }

    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            FontRenderer font,
            ITextComponent text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        font.drawString(text.getFormattedText(), x, y, colour, shadow);
    }

    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            FontRenderer font,
            String text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        font.drawString(text, x, y, colour, shadow);
    }
}
