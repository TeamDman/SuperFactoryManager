package ca.teamdman.sfm.client.screen.tick_graph;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

public record TickTimeGraphRenderState(
        Duration[] tickTimes,
        int x0, int x1, int y0, int y1,
        float scale, long yMax, int spaceBetweenPoints,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public TickTimeGraphRenderState(
            Duration[] tickTimes,
            int x,
            int y,
            int width,
            int height,
            long yMax,
            int spaceBetweenPoints,
            @Nullable ScreenRectangle scissorArea
    ) {
        this(
                tickTimes,
                x,           // x0
                x + width,   // x1
                y,           // y0
                y + height,  // y1
                1f,          // scale
                yMax,
                spaceBetweenPoints,
                scissorArea,
                PictureInPictureRenderState.getBounds(x, y, x + width, y + height, scissorArea)
        );
    }
}
