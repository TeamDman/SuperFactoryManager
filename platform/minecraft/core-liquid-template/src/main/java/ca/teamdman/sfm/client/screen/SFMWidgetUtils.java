package ca.teamdman.sfm.client.screen;

{% case minecraft_version %}
{% when '1.19.2' %}
import ca.teamdman.sfm.client.screen.widget.SFMExtendedButtonWithTooltip;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
{% endcase %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when '26.1.2' %}
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
{% endcase %}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.client.gui.components.Widget;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.client.gui.components.Renderable;
{% endcase %}
import net.minecraft.client.gui.screens.Screen;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.client.input.KeyEvent;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
{% endcase %}

import java.util.List;

public class SFMWidgetUtils {
    /// The field is private in 1.19.4 when it is not in 1.19.2
    @MCVersionDependentBehaviour
    public static int getX(AbstractWidget widget) {
{% case minecraft_version %}
{% when '1.19.2' %}
        return widget.x;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return widget.getX();
{% endcase %}
    }

    /// The field is private in 1.19.4 when it is not in 1.19.2
    @MCVersionDependentBehaviour
    public static int getY(AbstractWidget widget) {
{% case minecraft_version %}
{% when '1.19.2' %}
        return widget.y;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        return widget.getY();
{% endcase %}
    }

    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2' %}
    public static void hideTooltipsWhenNotFocused(Screen screen, List<Widget> renderables) {
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    public static void hideTooltipsWhenNotFocused(Screen screen, List<Renderable> renderables) {
{% endcase %}
        if (Minecraft.getInstance().screen != screen) {
            // this should fix the annoying Ctrl+E popup when editing
            renderables
                    .stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .forEach(w -> w.setFocused(false));
        }
    }

{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
    @SuppressWarnings("unused")
{% endcase %}
    @MCVersionDependentBehaviour
    public static void renderChildTooltips(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            PoseStack pose,
{% when '26.1.2' %}
            Matrix3x2fStack pose,
{% endcase %}
            int mx,
            int my,
{% case minecraft_version %}
{% when '1.19.2' %}
            List<Widget> renderables
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
            List<Renderable> renderables
{% endcase %}
    ) {
{% case minecraft_version %}
{% when '1.19.2' %}
        // 1.19.2: manually render button tooltips
        renderables
                .stream()
                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
                .map(SFMExtendedButtonWithTooltip.class::cast)
                .forEach(x -> x.renderToolTip(pose, mx, my));
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
//        // 1.19.2: manually render button tooltips
//        renderables
//                .stream()
//                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
//                .map(SFMExtendedButtonWithTooltip.class::cast)
//                .forEach(x -> x.renderToolTip(pose, mx, my));
{% endcase %}
    }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}

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
{% endcase %}
}
