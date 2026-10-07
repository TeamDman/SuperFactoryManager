package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% else %}
import net.minecraft.client.gui.GuiGraphics;
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% else %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% endcase %}
    public void render(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            PoseStack poseStack,
{% when "26.1.2" %}
            GuiGraphicsExtractor graphics,
{% else %}
            GuiGraphics graphics,
{% endcase %}
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                poseStack,
{% else %}
                graphics,
{% endcase %}
                minecraft.font,
                title().copy().withStyle(ChatFormatting.BOLD),
                bounds.x() + 10,
                bounds.y() + 10,
                0xFFFFFFFF,
                false
        );
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                poseStack,
{% else %}
                graphics,
{% endcase %}
                minecraft.font,
                minecraft.font.plainSubstrByWidth(displayText, Math.max(0, bounds.width() - 20)),
                bounds.x() + 10,
                bounds.y() + 30,
                0xFFFFFFFF,
                false
        );
    }
}
