package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.widget.SFMExtendedButtonWithTooltip;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public class SFMWidgetUtils {
    /// The field is private in 1.19.4 when it is not in 1.19.2
    @MCVersionDependentBehaviour
    public static int getX(AbstractWidget widget) {
        return widget.x;
    }

    /// The field is private in 1.19.4 when it is not in 1.19.2
    @MCVersionDependentBehaviour
    public static int getY(AbstractWidget widget) {
        return widget.y;
    }

    @MCVersionDependentBehaviour
    public static void hideTooltipsWhenNotFocused(Screen screen, List<Widget> renderables) {
        if (Minecraft.getInstance().screen != screen) {
            // this should fix the annoying Ctrl+E popup when editing
            renderables
                    .stream()
                    .filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast)
                    .forEach(w -> w.setFocused(false));
        }
    }

    @MCVersionDependentBehaviour
    public static void renderChildTooltips(
            PoseStack pose,
            int mx,
            int my,
            List<Widget> renderables
    ) {
        // 1.19.2: manually render button tooltips
        renderables
                .stream()
                .filter(SFMExtendedButtonWithTooltip.class::isInstance)
                .map(SFMExtendedButtonWithTooltip.class::cast)
                .forEach(x -> x.renderToolTip(pose, mx, my));
    }
}
