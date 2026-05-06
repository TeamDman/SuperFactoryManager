package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SFMWidgetUtils {
    /// The field is private in 1.19.4 when it is not in 1.19.2
    @MCVersionDependentBehaviour
    public static int getX(AbstractWidget widget) {
        return widget.getX();
    }

    /// The field is private in 1.19.4 when it is not in 1.19.2
    @MCVersionDependentBehaviour
    public static int getY(AbstractWidget widget) {
        return widget.getY();
    }

    @MCVersionDependentBehaviour
    public static void hideTooltipsWhenNotFocused(Screen screen, List<Renderable> renderables) {
        if (Minecraft.getInstance().screen != screen) {
            // this should fix the annoying Ctrl+E popup when editing
            renderables
                    .stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .forEach(w -> w.setFocused(false));
        }
    }

    @SuppressWarnings("unused")
    @MCVersionDependentBehaviour
    public static void renderChildTooltips(
            Matrix3x2fStack pose,
            int mx,
            int my,
            List<Renderable> renderables
    ) {
//        // 1.19.2: manually render button tooltips
//        renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
    }

    /// hasShiftDown was moved to {@link KeyEvent#hasShiftDown()}
    /// but that would require rewriting {@link SFMButtonBuilder}
    @MCVersionDependentBehaviour
    public static boolean hasShiftDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @MCVersionDependentBehaviour
    public static boolean hasCtrlDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    @MCVersionDependentBehaviour
    public static boolean hasAltDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }
}
