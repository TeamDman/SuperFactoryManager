package ca.teamdman.sfm.client.screen;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}

/** Shared pixel-aligned crosshair used by Draw cursors and read-only visualizations. */
public final class SFMGuiCrosshair {
    private SFMGuiCrosshair() {
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static void draw(PoseStack poseStack, int x, int y, int radius, int color) {
        GuiComponent.fill(poseStack, x - radius, y, x - 2, y + 1, color);
        GuiComponent.fill(poseStack, x + 3, y, x + radius + 1, y + 1, color);
        GuiComponent.fill(poseStack, x, y - radius, x + 1, y - 2, color);
        GuiComponent.fill(poseStack, x, y + 3, x + 1, y + radius + 1, color);
        GuiComponent.fill(poseStack, x, y, x + 1, y + 1, color);
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int radius, int color) {
{% else %}
    public static void draw(GuiGraphics graphics, int x, int y, int radius, int color) {
{% endcase %}
        graphics.fill(x - radius, y, x - 2, y + 1, color);
        graphics.fill(x + 3, y, x + radius + 1, y + 1, color);
        graphics.fill(x, y - radius, x + 1, y - 2, color);
        graphics.fill(x, y + 3, x + 1, y + radius + 1, color);
        graphics.fill(x, y, x + 1, y + 1, color);
{% endcase %}
    }
}
