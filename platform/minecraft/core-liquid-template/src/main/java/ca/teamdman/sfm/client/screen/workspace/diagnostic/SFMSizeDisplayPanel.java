package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.GuiComponent;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * A small composable debug leaf that makes its allocated area immediately
 * visible without adding viewport, pointer, or window diagnostics.
 */
public final class SFMSizeDisplayPanel implements SFMScreenPanel {
    private final String label;
    private final int backgroundColour;
    private final SFMSizeDisplayDimensionsSource dimensionsSource;

    public SFMSizeDisplayPanel(String label, int backgroundColour) {
        this(label, backgroundColour, SFMSizeDisplayDimensionsSource.allocatedPanel());
    }

    public SFMSizeDisplayPanel(
            String label,
            int backgroundColour,
            SFMSizeDisplayDimensionsSource dimensionsSource
    ) {
        this.label = Objects.requireNonNull(label);
        this.backgroundColour = backgroundColour | 0xFF000000;
        this.dimensionsSource = Objects.requireNonNull(dimensionsSource);
    }

    public SFMSizeDisplayPanel(String label, SFMSizeDisplayDimensions dimensions, int backgroundColour) {
        this(label, backgroundColour, minecraft -> Objects.requireNonNull(dimensions));
    }

    public String label() {
        return label;
    }

    public int backgroundColour() {
        return backgroundColour;
    }

    @Override
    public Component title() {
        return Component.literal("Size display · " + label);
    }

    @Override
    public Component narration() {
        return Component.literal(label + ": " + dimensionsDescription());
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(
                poseStack,
                bounds.x(),
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(bounds.x(),
{% endcase %}
                bounds.y(),
                bounds.x() + bounds.width(),
                bounds.y() + bounds.height(),
                backgroundColour
        );

        SFMSizeDisplayDimensions dimensions = dimensionsSource.snapshot(bounds);
        String text = dimensions.width() + " × " + dimensions.height();
        SFMSizeDisplayGeometry geometry = SFMSizeDisplayGeometry.create(
                bounds,
                text,
                minecraft.font.width(text),
                minecraft.font.lineHeight,
                backgroundColour
        );
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                graphics,
{% endcase %}
                minecraft.font,
                text,
                geometry.textX(),
                geometry.textY(),
                geometry.textColour(),
                false
        );
    }

    private String dimensionsDescription() {
        return "logical size";
    }
}
