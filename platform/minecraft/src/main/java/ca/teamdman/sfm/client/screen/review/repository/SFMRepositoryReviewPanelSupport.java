package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

final class SFMRepositoryReviewPanelSupport {
    private SFMRepositoryReviewPanelSupport() {}

    static void renderText(PoseStack poseStack, Minecraft minecraft, String value,
                           int x, int y, int width, int colour, boolean shadow) {
        if (width <= 0) return;
        SFMFontUtils.draw(poseStack, minecraft.font, minecraft.font.plainSubstrByWidth(value, width),
                x, y, colour, shadow);
    }

    static String fileName(String path) {
        int separator = path.lastIndexOf('/');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
