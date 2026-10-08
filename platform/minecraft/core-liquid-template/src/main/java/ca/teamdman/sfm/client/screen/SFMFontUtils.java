package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.19.2" %}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
{% when "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% else %}
{% endcase %}
import net.minecraft.client.gui.Font;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.renderer.LightTexture;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
{% else %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.util.FastColor;
{% if features.font_formatted_text %}
import net.minecraft.util.FormattedCharSequence;
{% endif %}
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.util.FastColor;
{% if features.font_formatted_text %}
import net.minecraft.util.FormattedCharSequence;
{% endif %}
import org.joml.Matrix4f;
{% else %}
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
{% endcase %}

public class SFMFontUtils {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /**
     * Draws text to the screen
     * @return the width of the drawn text
     */
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    /**
     * Draws text to the screen
     *
     * @return the width of the drawn text
     */
{% else %}
{% endcase %}
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static int drawInBatch(
{% else %}
    public static void drawInBatch(
{% endcase %}
            Component text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            Matrix4f matrix4f,
{% else %}
            Matrix3x2fc matrix,
{% endcase %}
            MultiBufferSource bufferSource
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return font.drawInBatch(
{% else %}
        Matrix4f m4 = new Matrix4f(
                matrix.m00(), matrix.m01(), 0, 0,
                matrix.m10(), matrix.m11(), 0, 0,
                0, 0, 1, 0,
                matrix.m20(), matrix.m21(), 0, 1
        );
        font.drawInBatch(
{% endcase %}
                text,
                x,
                y,
                -1,
                dropShadow,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                matrix4f,
{% else %}
                m4,
{% endcase %}
                bufferSource,
{% case minecraft_version %}
{% when "1.19.2" %}
                transparent,
{% else %}
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
{% endcase %}
                0,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                LightTexture.FULL_BRIGHT
{% else %}
                LightCoordsUtil.FULL_BRIGHT
{% endcase %}
        );
    }
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.font_caller_owned_batches %}

    /**
     * Draws coloured component text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
{% endif %}
{% when "1.19.4" %}
{% if features.font_caller_owned_batches %}

    /**
     * Draws coloured component text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            Component text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
{% endif %}
{% else %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    /**
     * Draws text to the screen
     * @return the width of the drawn text
     */
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    /**
     * Draws text to the screen
     *
     * @return the width of the drawn text
     */
{% else %}
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

{% endcase %}
    @SuppressWarnings("UnusedReturnValue")
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public static int drawInBatch(
{% else %}
    public static void drawInBatch(
{% endcase %}
            String text,
            Font font,
            float x,
            float y,
            boolean dropShadow,
            boolean transparent,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            Matrix4f matrix4f,
{% else %}
            Matrix3x2fc matrix,
{% endcase %}
            MultiBufferSource bufferSource
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return font.drawInBatch(
{% else %}
        Matrix4f m4 = new Matrix4f(
                matrix.m00(), matrix.m01(), 0, 0,
                matrix.m10(), matrix.m11(), 0, 0,
                0, 0, 1, 0,
                matrix.m20(), matrix.m21(), 0, 1
        );
        font.drawInBatch(
{% endcase %}
                text,
                x,
                y,
                -1,
                dropShadow,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                matrix4f,
{% else %}
                m4,
{% endcase %}
                bufferSource,
{% case minecraft_version %}
{% when "1.19.2" %}
                transparent,
{% else %}
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
{% endcase %}
                0,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                LightTexture.FULL_BRIGHT
{% else %}
                LightCoordsUtil.FULL_BRIGHT
{% endcase %}
        );
    }
{% case minecraft_version %}
{% when "1.19.2" %}
{% if features.font_caller_owned_batches %}

    /**
     * Draws coloured plain text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
{% endif %}
{% when "1.19.4" %}
{% if features.font_caller_owned_batches %}

    /**
     * Draws coloured plain text into a caller-owned batch. The caller is
     * responsible for flushing the buffer after all related text is queued.
     */
    @MCVersionDependentBehaviour
    public static int drawInBatch(
            String text,
            Font font,
            float x,
            float y,
            int colour,
            boolean dropShadow,
            boolean transparent,
            Matrix4f matrix4f,
            MultiBufferSource bufferSource
    ) {
        return font.drawInBatch(
                text,
                x,
                y,
                colour,
                dropShadow,
                matrix4f,
                bufferSource,
                transparent ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
        );
    }
{% endif %}
{% else %}
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
{% else %}
    @SuppressWarnings("UnusedReturnValue")
{% endcase %}
    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% else %}
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
{% endcase %}
    public static void draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            PoseStack context,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            GuiGraphics graphics,
{% else %}
            GuiGraphicsExtractor graphics,
{% endcase %}
            Font font,
            Component text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (shadow) {
            font.drawShadow(context, text, x, y, colour);
        } else {
            font.draw(context, text, x, y, colour);
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        graphics.drawString(font, text, x, y, colour, shadow);
{% else %}
        graphics.text(font, text, x, y, normalizeLegacyRgb(colour), shadow);
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
{% else %}
{% endcase %}
    @MCVersionDependentBehaviour
    public static void draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            PoseStack context,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            GuiGraphics graphics,
{% else %}
            GuiGraphicsExtractor graphics,
{% endcase %}
            Font font,
            String text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (shadow) {
            font.drawShadow(context, text, x, y, colour);
        } else {
            font.draw(context, text, x, y, colour);
        }
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        graphics.drawString(font, text, x, y, colour, shadow);
{% else %}
        graphics.text(font, text, x, y, normalizeLegacyRgb(colour), shadow);
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.font_formatted_text %}

    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            PoseStack context,
            Font font,
            FormattedCharSequence text,
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
{% endif %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% if features.font_formatted_text %}

    /**
     * @param colour See also: {@link FastColor.ARGB32#color(int, int, int, int)}
     */
    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.drawString(font, text, x, y, colour, shadow);
    }
{% endif %}
{% else %}

    @MCVersionDependentBehaviour
    private static int normalizeLegacyRgb(int colour) {
        if ((colour & 0xFF000000) == 0) {
            return colour | 0xFF000000;
        }
        return colour;
    }
{% if features.font_formatted_text %}

    @MCVersionDependentBehaviour
    public static void draw(
            GuiGraphicsExtractor graphics,
            Font font,
            net.minecraft.util.FormattedCharSequence text,
            int x,
            int y,
            int colour,
            boolean shadow
    ) {
        graphics.text(font, text, x, y, normalizeLegacyRgb(colour), shadow);
    }
{% endif %}
{% endcase %}
}
