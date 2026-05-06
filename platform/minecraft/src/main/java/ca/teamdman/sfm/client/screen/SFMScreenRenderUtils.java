package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

public class SFMScreenRenderUtils {

    @MCVersionDependentBehaviour
    public static void enableKeyRepeating() {
        // 1.19.2
//        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true);
    }

    /**
     * Applies a colour inversion for a region to impart a highlight effect.
     * <p/>
     * See also: {@link net.minecraft.client.gui.components.MultiLineEditBox#renderHighlight(PoseStack, int, int, int, int)}
     */
    @SuppressWarnings("JavadocReference")
    @MCVersionDependentBehaviour
    public static void renderHighlight(
            GuiGraphicsExtractor graphics,
            int startX,
            int startY,
            int endX,
            int endY
    ) {

        graphics.fill(RenderPipelines.GUI_TEXT_HIGHLIGHT, startX, startY, endX, endY, -16776961);
    }

}
