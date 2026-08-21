package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMTimelineDynamicBoundsTests {
    @Test
    void lateCandidateRouteGrowthRefreshesTransportWithoutDiscardingValidPosition() {
        DynamicChild child = new DynamicChild();
        SFMTimelinePanel panel = new SFMTimelinePanel(child, 12);
        assertEquals(new SFMTimelineBounds(0, 0), panel.model().bounds());

        child.last = 3;
        panel.tick();
        assertEquals(new SFMTimelineBounds(0, 3), panel.model().bounds());
        panel.seek(2);
        assertEquals(2, child.position);

        child.last = 5;
        panel.tick();
        assertEquals(new SFMTimelineBounds(0, 5), panel.model().bounds());
        assertEquals(2D, panel.model().keyframePosition());

        child.last = 1;
        panel.tick();
        assertEquals(1D, panel.model().keyframePosition());
        assertEquals(1, child.position);
    }

    private static final class DynamicChild implements SFMSeekableTimelinePanel {
        private int last;
        private int position;

        @Override
        public Component title() {
            return Component.literal("dynamic");
        }

        @Override
        public SFMTimelineBounds timelineBounds() {
            return new SFMTimelineBounds(0, last);
        }

        @Override
        public void setTimelinePosition(int timestep) {
            position = timestep;
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
        }
    }
}
