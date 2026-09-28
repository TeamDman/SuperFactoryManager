package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;

public class SFMFontUtils {
    /**
     * Draws text to the screen
     * @return the width of the drawn text
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
    }

    /**
     * Draws text to the screen
     * @return the width of the drawn text
     */
    @SuppressWarnings("UnusedReturnValue")
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                -1,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
    }

    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            PoseStack context,
            Font font,
            Component text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        if (shadow) {
            font.drawShadow(context, text, x, y, colour);
        } else {
            font.draw(context, text, x, y, colour);
        }
    }

    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            PoseStack context,
            Font font,
            String text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        if (shadow) {
            font.drawShadow(context, text, x, y, colour);
        } else {
            font.draw(context, text, x, y, colour);
        }
    }
}
