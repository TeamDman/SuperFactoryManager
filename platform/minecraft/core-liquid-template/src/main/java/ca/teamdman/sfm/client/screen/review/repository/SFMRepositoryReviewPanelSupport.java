package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
import net.minecraft.client.Minecraft;

final class SFMRepositoryReviewPanelSupport {
    private SFMRepositoryReviewPanelSupport() {}

    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% case minecraft_version %}
{% when "26.1.2" %}
    static void renderText(GuiGraphicsExtractor graphics, Minecraft minecraft, String value,
{% else %}
    static void renderText(GuiGraphics graphics, Minecraft minecraft, String value,
{% endcase %}
                           int x, int y, int width, int colour, boolean shadow) {
        if (width <= 0) return;
        SFMFontUtils.draw(graphics, minecraft.font, minecraft.font.plainSubstrByWidth(value, width),
                x, y, colour, shadow);
    }

    static String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
