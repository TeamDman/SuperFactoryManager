package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
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
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        GuiComponent.fill(
                poseStack,
                bounds.x(),
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
                poseStack,
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
