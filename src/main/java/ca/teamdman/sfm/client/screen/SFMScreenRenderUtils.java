package ca.teamdman.sfm.client.screen;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

import com.bbscn.AbstractWidget;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;

public class SFMScreenRenderUtils {

    public static int getX(AbstractWidget widget) {
        return widget.getX();
    }

    public static int getY(AbstractWidget widget) {
        return widget.getY();
    }

    @MCVersionDependentBehaviour
    public static void enableKeyRepeating() {
        // 1.19.2
        // Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true);
    }

    /**
     * Applies a colour inversion for a region to impart a highlight effect.
     * <p/>
     * See also:
     * {@link net.minecraft.client.gui.components.MultiLineEditBox#renderHighlight(PoseStack, int, int, int, int)}
     */
    @SuppressWarnings("JavadocReference")
    @MCVersionDependentBehaviour
    public static void renderHighlight(
                                       int startX,
                                       int startY,
                                       int endX,
                                       int endY) {
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(GlStateManager.LogicOp.OR_REVERSE);
        Gui.drawRect(startX, startY, endX, endY, -16776961);
        // GuiComponent.fill(poseStack, startX, startY, endX, endY, -16776961);
        GlStateManager.disableColorLogic();
    }
}
