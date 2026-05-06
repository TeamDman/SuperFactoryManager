package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;

public class SFMFontUtils {
    /**
     * Draws text to the screen
     *
     */
    @MCVersionDependentBehaviour
    public static void drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightCoordsUtil.FULL_BRIGHT
        );
    }

    /**
     * Draws text to the screen
     *
     */
    @SuppressWarnings("UnusedReturnValue")
    @MCVersionDependentBehaviour
    public static void drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightCoordsUtil.FULL_BRIGHT
        );
    }

    /**
     * @param colour See also: {@link ARGB#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            Component text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.text(font, text, x, y, colour, shadow);
    }

    /**
     * @param colour See also: {@link ARGB#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.text(font, text, x, y, colour, shadow);
    }
}
