package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Explicit placeholder for a screen parked behind the workspace.
 *
 * <p>It does not pretend that arbitrary vanilla screens are safe panels. The
 * original screen is restored intact when the multiplexer closes.</p>
 */
public record SFMPreviousScreenPanel(@Nullable Screen previousScreen) implements SFMScreenPanel {
    @Override
    public Component title() {
        return previousScreen == null ? Component.literal("Game") : previousScreen.getTitle();
    }

    @Override
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        SFMFontUtils.draw(
                poseStack,
                minecraft.font,
                title().copy().withStyle(ChatFormatting.BOLD),
                bounds.x() + 10,
                bounds.y() + 10,
                0xFFFFFFFF,
                false
        );
        SFMFontUtils.draw(
                poseStack,
                minecraft.font,
                minecraft.font.plainSubstrByWidth(
                        "Previous screen parked; Escape restores it",
                        Math.max(0, bounds.width() - 20)
                ),
                bounds.x() + 10,
                bounds.y() + 30,
                0xFFB0B0B0,
                false
        );
    }
}
