package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;

final class SFMRepositoryReviewPanelSupport {
    private SFMRepositoryReviewPanelSupport() {}

    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    static void renderText(GuiGraphicsExtractor graphics, Minecraft minecraft, String value,
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
