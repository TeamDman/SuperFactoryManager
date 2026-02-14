package vswe.superfactory.blocks;

/*
import net.minecraftforge.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class RenderCamouflage implements ISimpleBlockRenderingHandler {

    private int id;
    public RenderCamouflage() {
        id = RenderingRegistry.getNextAvailableRenderId();
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelID, RenderBlocks renderer) {
        block.setBlockBoundsForItemRender();
        renderer.setRenderBoundsFromBlock(block);

        GL11.glPushMatrix();
        GL11.glRotatef(90, 0, 1, 0);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);

        Tessellator tessellator = Tessellator.instance;

        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(0, metadata));
        tessellator.draw();

        tessellator.startDrawingQuads();
        tessellator.setNormal(0F, 1F, 0F);
        renderer.renderFaceYPos(block, 0, 0, 0, block.getIcon(1, metadata));
        tessellator.draw();

        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(2, metadata));
        tessellator.draw();

        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(3, metadata));
        tessellator.draw();

        tessellator.startDrawingQuads();
        tessellator.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(4, metadata));
        tessellator.draw();

        tessellator.startDrawingQuads();
        tessellator.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(5, metadata));
        tessellator.draw();

        GL11.glPopMatrix();
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
        Tessellator.instance.setColorOpaque_F(1F, 1F, 1F);

        block.setBlockBoundsBasedOnState(world, x, y, z);
        renderer.setRenderBoundsFromBlock(block);
        renderer.renderStandardBlock(block, x, y, z);

        TileEntityCamouflage camouflage = TileEntityCluster.getTileEntity(TileEntityCamouflage.class, world, x, y, z);

        if (camouflage != null && camouflage.getCamouflageType().useDoubleRendering()) {
            BlockCamouflageBase camoBlock = (BlockCamouflageBase)block;

            float maxX = (float)block.getBlockBoundsMaxX();
            float maxY = (float)block.getBlockBoundsMaxY();
            float maxZ = (float)block.getBlockBoundsMaxZ();
            float minX = (float)block.getBlockBoundsMinX();
            float minY = (float)block.getBlockBoundsMinY();
            float minZ = (float)block.getBlockBoundsMinZ();

            float f = 0.0015F;
            float f2 = 0.002F;
            block.setBlockBounds(maxX + f2, maxY - f, maxZ + f2, minX - f2, minY + f, minZ - f2);
            renderer.setRenderBoundsFromBlock(block);
            renderer.renderFaceYNeg(block, x, y, z, camouflage.getIconWithDefault(world, x, y, z, camoBlock, 1, true));

            block.setBlockBounds(maxX + f2, maxY - f, maxZ + f2, minX - f2, minY + f, minZ - f2);
            renderer.setRenderBoundsFromBlock(block);
            renderer.renderFaceYPos(block, x, y, z, camouflage.getIconWithDefault(world, x, y, z, camoBlock, 0, true));

            block.setBlockBounds(maxX + f2, maxY + f2, maxZ - f, minX - f2, minY - f2, minZ + f);
            renderer.setRenderBoundsFromBlock(block);
            renderer.renderFaceZNeg(block, x, y, z, camouflage.getIconWithDefault(world, x, y, z, camoBlock, 3, true));

            block.setBlockBounds(maxX + f2, maxY + f2, maxZ - f, minX - f2, minY - f2, minZ + f);
            renderer.setRenderBoundsFromBlock(block);
            renderer.renderFaceZPos(block, x, y, z, camouflage.getIconWithDefault(world, x, y, z, camoBlock, 2, true));

            block.setBlockBounds(maxX - f, maxY + f2, maxZ + f2, minX + f, minY - f2, minZ - f2);
            renderer.setRenderBoundsFromBlock(block);
            renderer.renderFaceXNeg(block, x, y, z, camouflage.getIconWithDefault(world, x, y, z, camoBlock, 5, true));

            block.setBlockBounds(maxX - f, maxY + f2, maxZ + f2, minX + f, minY - f2, minZ - f2);
            renderer.setRenderBoundsFromBlock(block);
            renderer.renderFaceXPos(block, x, y, z, camouflage.getIconWithDefault(world, x, y, z, camoBlock, 4, true));

        }

        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return id;
    }



}
*/