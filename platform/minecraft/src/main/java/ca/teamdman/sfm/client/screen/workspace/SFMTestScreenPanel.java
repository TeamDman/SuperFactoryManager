package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public record SFMTestScreenPanel(String displayText) implements SFMScreenPanel {
    @Override
    public Component title() {
        return Component.literal("Test screen");
    }

    @Override
    public Component narration() {
        return Component.literal(displayText);
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
                displayText,
                bounds.x() + 10,
                bounds.y() + 30,
                0xFFFFFFFF,
                false
        );
    }
}
