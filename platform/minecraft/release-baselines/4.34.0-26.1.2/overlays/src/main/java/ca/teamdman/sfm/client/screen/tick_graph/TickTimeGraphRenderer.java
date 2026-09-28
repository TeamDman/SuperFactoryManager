package ca.teamdman.sfm.client.screen.tick_graph;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;

public class TickTimeGraphRenderer extends PictureInPictureRenderer<TickTimeGraphRenderState> {

    public TickTimeGraphRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public @NonNull Class<TickTimeGraphRenderState> getRenderStateClass() {
        return TickTimeGraphRenderState.class;
    }

    @Override
    protected @NonNull String getTextureLabel() {
        return "sfm: tick_time_graph";
    }

    @Override
    protected void renderToTexture(TickTimeGraphRenderState state, @NonNull PoseStack pose) {
        int plotHeight = state.y1() - state.y0();

        VertexConsumer consumer = this.bufferSource.getBuffer(RenderTypes.lines());

        for (int i = 0; i < state.tickTimes().length; i++) {
            long yNanos = state.tickTimes()[i].toNanos();

            float normalizedTickTime = yNanos == 0 ? 0 : (float) (Math.log10(yNanos) / Math.log10(state.yMax()));

            float plotPosX = state.spaceBetweenPoints() * i;
            float plotPosY = plotHeight - normalizedTickTime * plotHeight;

            var c = ManagerScreen.getMillisecondColour(yNanos / 1_000_000f);
            int color = c.getColor() != null ? ARGB.opaque(c.getColor()) : -1;

            if (i != 0) {
                consumer.addVertex(plotPosX, plotPosY, 0).setColor(color).setNormal(1, 0, 0).setLineWidth(20.0f);
            }
            consumer.addVertex(plotPosX, plotPosY, 0).setColor(color).setNormal(1, 0, 0).setLineWidth(20.0f);
            if (i == state.tickTimes().length - 1) {
                consumer.addVertex(plotPosX, plotPosY, 0).setColor(color).setNormal(1, 0, 0).setLineWidth(20.0f);
            }
        }

        // Flush the buffer so the lines are written to the texture
        this.bufferSource.endBatch(RenderTypes.lines());
    }
}