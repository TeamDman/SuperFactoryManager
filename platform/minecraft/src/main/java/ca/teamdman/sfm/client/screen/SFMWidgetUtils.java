package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.bbscn.AbstractWidget;
import com.bbscn.Renderable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

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
    public static void hideTooltipsWhenNotFocused(GuiScreen screen, List<Renderable> renderables) {
        if (Minecraft.getMinecraft().currentScreen != screen) {
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
            int mx,
            int my,
            List<Renderable> renderables
    ) {

    }
}
