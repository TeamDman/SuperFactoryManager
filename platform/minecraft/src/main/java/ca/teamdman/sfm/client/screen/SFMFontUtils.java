package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class SFMFontUtils {
    @MCVersionDependentBehaviour
    public static void drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix3x2fc matrix,
            MultiBufferSource bufferSource
    ) {
        Matrix4f m4 = new Matrix4f(
                matrix.m00(), matrix.m01(), 0, 0,
                matrix.m10(), matrix.m11(), 0, 0,
                0, 0, 1, 0,
                matrix.m20(), matrix.m21(), 0, 1
        );
        font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                m4,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightCoordsUtil.FULL_BRIGHT
        );
    }

    @MCVersionDependentBehaviour
    public static void drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix4fc matrix,
            MultiBufferSource bufferSource
    ) {
        font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                matrix,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightCoordsUtil.FULL_BRIGHT
        );
    }

    @SuppressWarnings("UnusedReturnValue")
    @MCVersionDependentBehaviour
    public static void drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix3x2fc matrix,
            MultiBufferSource bufferSource
    ) {
        Matrix4f m4 = new Matrix4f(
                matrix.m00(), matrix.m01(), 0, 0,
                matrix.m10(), matrix.m11(), 0, 0,
                0, 0, 1, 0,
                matrix.m20(), matrix.m21(), 0, 1
        );
        font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                m4,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightCoordsUtil.FULL_BRIGHT
        );
    }

    @SuppressWarnings("UnusedReturnValue")
    @MCVersionDependentBehaviour
    public static void drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix4fc matrix,
            MultiBufferSource bufferSource
    ) {
        font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                matrix,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightCoordsUtil.FULL_BRIGHT
        );
    }

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
