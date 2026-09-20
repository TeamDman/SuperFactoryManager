package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

import java.util.Objects;

/** Typed, render-ready presentation selected independently from resolver data. */
public record SFMExplorerPresentation(String label, Icon icon) {
    public SFMExplorerPresentation {
        label = Objects.requireNonNull(label, "label");
        if (label.isEmpty()) throw new IllegalArgumentException("Explorer presentation labels must not be empty");
        Objects.requireNonNull(icon, "icon");
    }

    /** A presentation icon knows how to draw itself without path-specific logic in the panel. */
    public sealed interface Icon permits MarkerIcon, ItemIcon, DegradedIcon {
        void render(PoseStack poseStack, Minecraft minecraft, int x, int y, int height, int colour);
    }

    /** The exclamation is an inspectable warning, not merely a colour distinction. */
    public record DegradedIcon(Icon baseline) implements Icon {
        public DegradedIcon { Objects.requireNonNull(baseline); }
        @Override public void render(PoseStack poseStack, Minecraft minecraft, int x, int y, int height, int colour) {
            baseline.render(poseStack,minecraft,x,y,height,colour);
            SFMFontUtils.draw(poseStack,minecraft.font,"!",x+11,y,0xFFFFFF55,true);
        }
    }

    /** Deterministic generic marker used when no richer contributor accepts an entry. */
    public record MarkerIcon(String marker) implements Icon {
        public MarkerIcon {
            marker = Objects.requireNonNull(marker, "marker");
            if (marker.isEmpty()) throw new IllegalArgumentException("Explorer marker icons must not be empty");
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                int x,
                int y,
                int height,
                int colour
        ) {
            int textY = y + Math.max(0, (height - minecraft.font.lineHeight) / 2);
            SFMFontUtils.draw(poseStack, minecraft.font, marker, x, textY, colour, true);
        }
    }

    /** Minecraft ItemStack-backed icon contributed by the Minecraft presentation defaults. */
    public record ItemIcon(SFMItemIcon item) implements Icon {
        public ItemIcon {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public void render(
                PoseStack poseStack,
                Minecraft minecraft,
                int x,
                int y,
                int height,
                int colour
        ) {
            int iconY = y + Math.max(0, (height - SFMItemIconRenderer.SIZE) / 2);
            SFMItemIconRenderer.render(poseStack, minecraft, item, x, iconY);
        }
    }
}
