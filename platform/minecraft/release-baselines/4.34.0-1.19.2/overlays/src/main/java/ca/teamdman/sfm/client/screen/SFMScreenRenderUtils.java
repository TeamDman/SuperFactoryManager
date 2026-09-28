package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

public class SFMScreenRenderUtils {

    @MCVersionDependentBehaviour
    public static void enableKeyRepeating() {
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true);
    }

    /**
     * Applies a colour inversion for a region to impart a highlight effect.
     * <p/>
     * See also: {@link net.minecraft.client.gui.components.MultiLineEditBox#renderHighlight(PoseStack, int, int, int, int)}
     */
    @SuppressWarnings("JavadocReference")
    @MCVersionDependentBehaviour
    public static void renderHighlight(
            PoseStack poseStack,
            int startX,
            int startY,
            int endX,
            int endY
    ) {
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
    }

}
