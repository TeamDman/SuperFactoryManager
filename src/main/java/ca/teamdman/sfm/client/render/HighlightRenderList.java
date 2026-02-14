package ca.teamdman.sfm.client.render;

import ca.teamdman.sfm.common.util.BlockPosSet;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.HashSet;

public class HighlightRenderList {
    private final double size;
    public final BlockPosSet positions;
    private int renderList;

    private final int r, g, b, a;


    public HighlightRenderList(BlockPosSet blockPos, int r, int g, int b, int a, float highlightFraction) {
        this.size = highlightFraction;
        this.positions = blockPos;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;

        compileRenderList();
    }

    private void compileRenderList() {
        renderList = GL11.glGenLists(1);
        GL11.glNewList(renderList, GL11.GL_COMPILE);

        BufferBuilder wr = Tessellator.getInstance().getBuffer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        double start = (1 - size) / 2.0;

        for (BlockPos pos : positions.blockPosIterator()) {
            wr.setTranslation(pos.getX() + start, pos.getY() + start, pos.getZ() + start);

            boolean north = positions.contains(pos.north());
            boolean south = positions.contains(pos.south());
            boolean west = positions.contains(pos.west());
            boolean east = positions.contains(pos.east());
            boolean up = positions.contains(pos.up());
            boolean down = positions.contains(pos.down());

            // NORTH
            if (!(size == 1 && north)) {
                wr.pos(0, 0, 0).color(r, g, b, a).endVertex();
                wr.pos(0, size, 0).color(r, g, b, a).endVertex();
                wr.pos(size, size, 0).color(r, g, b, a).endVertex();
                wr.pos(size, 0, 0).color(r, g, b, a).endVertex();
            }

            if (!(size == 1 && south)) {
                wr.pos(size, 0, size).color(r, g, b, a).endVertex();
                wr.pos(size, size, size).color(r, g, b, a).endVertex();
                wr.pos(0, size, size).color(r, g, b, a).endVertex();
                wr.pos(0, 0, size).color(r, g, b, a).endVertex();
            }

            if (!(size == 1 && west)) {
                wr.pos(0, 0, 0).color(r, g, b, a).endVertex();
                wr.pos(0, 0, size).color(r, g, b, a).endVertex();
                wr.pos(0, size, size).color(r, g, b, a).endVertex();
                wr.pos(0, size, 0).color(r, g, b, a).endVertex();
            }

            if (!(size == 1 && east)) {
                wr.pos(size, size, 0).color(r, g, b, a).endVertex();
                wr.pos(size, size, size).color(r, g, b, a).endVertex();
                wr.pos(size, 0, size).color(r, g, b, a).endVertex();
                wr.pos(size, 0, 0).color(r, g, b, a).endVertex();
            }

            if (!(size == 1 && down)) {
                wr.pos(0, 0, 0).color(r, g, b, a).endVertex();
                wr.pos(size, 0, 0).color(r, g, b, a).endVertex();
                wr.pos(size, 0, size).color(r, g, b, a).endVertex();
                wr.pos(0, 0, size).color(r, g, b, a).endVertex();
            }

            if (!(size == 1 && up)) {
                wr.pos(0, size, size).color(r, g, b, a).endVertex();
                wr.pos(size, size, size).color(r, g, b, a).endVertex();
                wr.pos(size, size, 0).color(r, g, b, a).endVertex();
                wr.pos(0, size, 0).color(r, g, b, a).endVertex();
            }
        }

        Tessellator.getInstance().draw();

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);
        var color = new Color(0X404040, false);
        GlStateManager.color(
                r,
                g,
                b,
                255
        );

        for (BlockPos pos : positions.blockPosIterator()) {
            wr.setTranslation(pos.getX() + start, pos.getY() + start, pos.getZ() + start);

            boolean north = positions.contains(pos.north());
            boolean south = positions.contains(pos.south());
            boolean west = positions.contains(pos.west());
            boolean east = positions.contains(pos.east());
            boolean up = positions.contains(pos.up());
            boolean down = positions.contains(pos.down());

            // NORTH WEST
            if (!(size == 1 && (north || west))) {
                wr.pos(0, 0, 0).endVertex();
                wr.pos(0, size, 0).endVertex();
            }
            // NORTH EAST
            if (!(size == 1 && (north || east))) {
                wr.pos(size, size, 0).endVertex();
                wr.pos(size, 0, 0).endVertex();
            }
            // SOUTH EAST
            if (!(size == 1 && (south || east))) {
                wr.pos(size, 0, size).endVertex();
                wr.pos(size, size, size).endVertex();
            }
            // SOUTH WEST
            if (!(size == 1 && (south || west))) {
                wr.pos(0, size, size).endVertex();
                wr.pos(0, 0, size).endVertex();
            }
            // WEST DOWN
            if (!(size == 1 && (west || down))) {
                wr.pos(0, 0, 0).endVertex();
                wr.pos(0, 0, size).endVertex();
            }
            // WEST UP
            if (!(size == 1 && (west || up))) {
                wr.pos(0, size, size).endVertex();
                wr.pos(0, size, 0).endVertex();
            }
            // EAST UP
            if (!(size == 1 && (east || up))) {
                wr.pos(size, size, 0).endVertex();
                wr.pos(size, size, size).endVertex();
            }
            // EAST DOWN
            if (!(size == 1 && (east || down))) {
                wr.pos(size, 0, size).endVertex();
                wr.pos(size, 0, 0).endVertex();
            }
            // DOWN NORTH
            if (!(size == 1 && (down || north))) {
                wr.pos(0, 0, 0).endVertex();
                wr.pos(size, 0, 0).endVertex();
            }
            // DOWN SOUTH
            if (!(size == 1 && (down || south))) {
                wr.pos(size, 0, size).endVertex();
                wr.pos(0, 0, size).endVertex();
            }

            // UP SOUTH
            if (!(size == 1 && (up || south))) {
                wr.pos(0, size, size).endVertex();
                wr.pos(size, size, size).endVertex();
            }
            // UP NORTH
            if (!(size == 1 && (up || north))) {
                wr.pos(size, size, 0).endVertex();
                wr.pos(0, size, 0).endVertex();
            }
        }

        wr.setTranslation(0, 0, 0);
        Tessellator.getInstance().draw();
        GL11.glEndList();
    }

//    private int getFaceAlpha(HighlightRenderList.CompiledPosition cp, BlockPos pos, EnumFacing face) {
//        return cp.checkFace(pos, face) ? 224 : 64;
//    }

   public void render() {
        GL11.glCallList(renderList);
    }

    public void destroy() {
        GL11.glDeleteLists(renderList, 1);
    }
}