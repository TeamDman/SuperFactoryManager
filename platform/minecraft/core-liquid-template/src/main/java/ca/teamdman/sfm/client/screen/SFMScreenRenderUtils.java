package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when "1.19.2" %}
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
{% when "1.19.4" %}
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
{% else %}
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
{% endcase %}

public class SFMScreenRenderUtils {

    @MCVersionDependentBehaviour
    public static void enableKeyRepeating() {
{% case minecraft_version %}
{% when "1.19.2" %}
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true);
{% else %}
        // 1.19.2
//        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true);
{% endcase %}
    }

    /**
     * Applies a colour inversion for a region to impart a highlight effect.
     * <p/>
     * See also: {@link net.minecraft.client.gui.components.MultiLineEditBox#renderHighlight(PoseStack, int, int, int, int)}
     */
    @SuppressWarnings("JavadocReference")
    @MCVersionDependentBehaviour
    public static void renderHighlight(
{% case minecraft_version %}
{% when "1.19.2" %}
            PoseStack poseStack,
{% if features.screen_fractional_highlights %}
            double startX,
            double startY,
            double endX,
            double endY
{% else %}
            int startX,
            int startY,
            int endX,
            int endY
{% endif %}
{% when "1.19.4" %}
            PoseStack poseStack,
            int startX,
            int startY,
            int endX,
            int endY
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            GuiGraphics graphics,
            int startX,
            int startY,
            int endX,
            int endY
{% else %}
            GuiGraphicsExtractor graphics,
            int startX,
            int startY,
            int endX,
            int endY
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2" %}
        Matrix4f matrix4f = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(0.0F, 0.0F, 1.0F, 1.0F);
        RenderSystem.disableTexture();
        RenderSystem.enableColorLogicOp();
        RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferbuilder.vertex(matrix4f, (float) startX, (float) endY, 0.0F).endVertex();
        bufferbuilder.vertex(matrix4f, (float) endX, (float) endY, 0.0F).endVertex();
        bufferbuilder.vertex(matrix4f, (float) endX, (float) startY, 0.0F).endVertex();
        bufferbuilder.vertex(matrix4f, (float) startX, (float) startY, 0.0F).endVertex();
        tesselator.end();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableColorLogicOp();
        RenderSystem.enableTexture();
{% when "1.19.4" %}
        RenderSystem.enableColorLogicOp();
        RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
        GuiComponent.fill(poseStack, startX, startY, endX, endY, -16776961);
        RenderSystem.disableColorLogicOp();
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        graphics.fill(RenderType.guiTextHighlight(), startX, startY, endX, endY, -16776961);
{% else %}
        graphics.fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, startX, startY, endX, endY, -16776961);
{% endcase %}
    }

}
