package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% endcase %}
    public void render(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            PoseStack poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            GuiGraphics graphics,
{% when "26.1.2" %}
            GuiGraphicsExtractor graphics,
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
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
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
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                graphics,
{% endcase %}
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
