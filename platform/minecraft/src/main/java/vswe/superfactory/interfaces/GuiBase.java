package vswe.superfactory.interfaces;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.settings.Settings;
import vswe.superfactory.tiles.TileEntityClusterElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SideOnly(Side.CLIENT)
public abstract class GuiBase extends GuiAntiNEI {
	private static final ResourceLocation TERRAIN = new ResourceLocation("textures/atlas/blocks.png");

	public GuiBase(Container container) {
		super(container);
	}

	public static ResourceLocation registerTexture(String name) {
		return new ResourceLocation(SuperFactoryManager.RESOURCE_LOCATION, "textures/gui/" + name + ".png");
	}

	public void drawTexture(int x, int y, int srcX, int srcY, int w, int h) {
		float scale = getScale();

		drawScaleFriendlyTexture(
				fixScaledCoordinate(guiLeft + x, scale, this.mc.displayWidth),
				fixScaledCoordinate(guiTop + y, scale, this.mc.displayHeight),
				fixScaledCoordinate(srcX, scale, 256),
				fixScaledCoordinate(srcY, scale, 256),
				fixScaledCoordinate(w, scale, this.mc.displayWidth),
				fixScaledCoordinate(h, scale, this.mc.displayHeight)
		);
	}

	private double fixScaledCoordinate(int val, float scale, int size) {
		double d = val / scale;
		d *= size;
		d = Math.floor(d);
		d /= size;
		d *= scale;

		return d;
	}

	public void drawScaleFriendlyTexture(double x, double y, double srcX, double srcY, double w, double h) {
		float         f             = 0.00390625F;
		float         f1            = 0.00390625F;
		Tessellator   tessellator   = Tessellator.getInstance();
		BufferBuilder worldRenderer = tessellator.getBuffer();
		worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
		worldRenderer.pos(x + 0, y + h, (double) this.zLevel).tex((srcX + 0) * f, (srcY + h) * f1).endVertex();
		worldRenderer.pos(x + w, y + h, (double) this.zLevel).tex((srcX + w) * f, (srcY + h) * f1).endVertex();
		worldRenderer.pos(x + w, y + 0, (double) this.zLevel).tex((srcX + w) * f, (srcY + 0) * f1).endVertex();
		worldRenderer.pos(x + 0, y + 0, (double) this.zLevel).tex((srcX + 0) * f, (srcY + 0) * f1).endVertex();
		tessellator.draw();
	}

	protected float getScale() {

		net.minecraft.client.gui.ScaledResolution scaledresolution = new net.minecraft.client.gui.ScaledResolution(this.mc);
		float                                     w                = scaledresolution.getScaledWidth() * 0.9F;
		float                                     h                = scaledresolution.getScaledHeight() * 0.9F;
		float                                     multX            = w / xSize;
		float                                     multY            = h / ySize;
		float                                     mult             = Math.min(multX, multY);
		if (mult > 1F && !Settings.isEnlargeInterfaces()) {
			mult = 1F;
		}

		mult = (float) (Math.floor(mult * 1000)) / 1000F;
//		System.out.printf("xsize %d\tysize %d\twidth %d\theight %d\txfac %f\tyfac %f\tmult %f\n",xSize, ySize, width, height, multX, multY, mult);

		return mult;
	}

	public void drawSplitString(String str, int x, int y, int w, float mult, int color) {
		GlStateManager.pushMatrix();
		GlStateManager.scale(mult, mult, 1F);
		fontRenderer.drawSplitString(str, (int) ((x + guiLeft) / mult), (int) ((y + guiTop) / mult), (int) (w / mult), color);
		bindTexture(getComponentResource());
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

		GlStateManager.popMatrix();
	}

	public abstract ResourceLocation getComponentResource();

	public static void bindTexture(ResourceLocation resource) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(resource);
	}

	public void drawString(String str, int x, int y, int color) {
		drawString(str, x, y, 1F, color);
	}

	public void drawString(String str, int x, int y, float mult, int color) {
		GlStateManager.pushMatrix();
		GlStateManager.scale(mult, mult, 1F);
		fontRenderer.drawString(str, (int) ((x + guiLeft) / mult), (int) ((y + guiTop) / mult), color);
		bindTexture(getComponentResource());
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

		GlStateManager.popMatrix();
	}

	public void drawMouseOver(String str, int x, int y, int width) {
		drawMouseOver(getLinesFromText(str, width), x, y);
	}

	public List<String> getLinesFromText(String str, int width) {
		List<String> lst   = new ArrayList<String>();
		String[]     words = str.split(" ");
		String       line  = "";
		for (String word : words) {
			String newLine;
			if (line.equals("")) {
				newLine = word;
			} else {
				newLine = line + " " + word;
			}
			if (getStringWidth(newLine) < width) {
				line = newLine;
			} else {
				lst.add(line);
				line = word;
			}
		}
		lst.add(line);

		return lst;
	}

	public int getStringWidth(String str) {
		return fontRenderer.getStringWidth(str);
	}

	public void drawMouseOver(List lst, int x, int y) {
		if (lst != null) {
			drawHoveringText(lst, x + guiLeft, y + guiTop, fontRenderer);
		}
	}

	public void drawMouseOver(IAdvancedTooltip tooltip, int mX, int mY) {
		drawMouseOver(tooltip, mX, mY, mX, mY);
	}

	public void drawMouseOver(IAdvancedTooltip tooltip, int x, int y, int mX, int mY) {
		if (tooltip != null) {
			GlStateManager.disableRescaleNormal();
			RenderHelper.disableStandardItemLighting();
			GlStateManager.disableLighting();
			GlStateManager.disableDepth();


			List<String> prefix       = tooltip.getPrefix(this);
			List<String> suffix       = tooltip.getSuffix(this);
			int          prefixLength = prefix != null ? prefix.size() : 0;
			int          suffixLength = suffix != null ? suffix.size() : 0;
			int          extraHeight  = tooltip.getExtraHeight(this);

			int width  = Math.max(tooltip.getMinWidth(this), Math.max(getStringMaxWidth(prefix), getStringMaxWidth(suffix)));
			int height = extraHeight + (prefixLength + suffixLength) * 10 - 2;


			x += guiLeft + 12;
			y += guiTop - 12;
			if (x + width > this.width) {
				x -= 28 + width;
			}
			if (y + height + 6 > this.height) {
				y = this.height - height - 6;
			}


			this.zLevel = 300.0F;
			itemRender.zLevel = 300.0F;
			int j1 = -267386864;
			this.drawGradientRect(x - 3, y - 4, x + width + 3, y - 3, j1, j1);
			this.drawGradientRect(x - 3, y + height + 3, x + width + 3, y + height + 4, j1, j1);
			this.drawGradientRect(x - 3, y - 3, x + width + 3, y + height + 3, j1, j1);
			this.drawGradientRect(x - 4, y - 3, x - 3, y + height + 3, j1, j1);
			this.drawGradientRect(x + width + 3, y - 3, x + width + 4, y + height + 3, j1, j1);
			int k1 = 1347420415;
			int l1 = (k1 & 16711422) >> 1 | k1 & -16777216;
			this.drawGradientRect(x - 3, y - 3 + 1, x - 3 + 1, y + height + 3 - 1, k1, l1);
			this.drawGradientRect(x + width + 2, y - 3 + 1, x + width + 3, y + height + 3 - 1, k1, l1);
			this.drawGradientRect(x - 3, y - 3, x + width + 3, y - 3 + 1, k1, k1);
			this.drawGradientRect(x - 3, y + height + 2, x + width + 3, y + height + 3, l1, l1);

			y = renderLines(prefix, x, y, true);
			tooltip.drawContent(this, x - guiLeft, y - guiTop, mX, mY);
			y += extraHeight;
			renderLines(suffix, x, y, false);

			this.zLevel = 0.0F;
			itemRender.zLevel = 0.0F;
			GlStateManager.enableLighting();
			GlStateManager.enableDepth();
			RenderHelper.enableStandardItemLighting();
			GlStateManager.enableRescaleNormal();
		}
	}

	private int getStringMaxWidth(List<String> lines) {
		int width = 0;

		if (lines != null) {
			for (String line : lines) {
				int w = fontRenderer.getStringWidth(line);

				if (w > width) {
					width = w;
				}
			}
		}

		return width;
	}

	private int renderLines(List<String> lines, int mX, int mY, boolean first) {
		return renderLines(lines, mX, mY, first, false);
	}

	private int renderLines(List<String> lines, int mX, int mY, boolean first, boolean fake) {
		if (lines != null) {
			for (String line : lines) {
				if (!fake) {
					fontRenderer.drawStringWithShadow(line, mX, mY, -1);
				}

				if (first) {
					mY += 2;
					first = false;
				}

				mY += 10;
			}
		}

		return mY;
	}

	public int getAdvancedToolTipContentStartX(IAdvancedTooltip tooltip) {
		return 12;
	}

	public int getAdvancedToolTipContentStartY(IAdvancedTooltip tooltip) {
		if (tooltip != null) {
			return renderLines(tooltip.getPrefix(this), 0, 0, true, true) - 12;
		}

		return 0;
	}

	public void drawMouseOver(String str, int x, int y) {
		drawMouseOver(Arrays.asList(str.split("\n")), x, y);
	}

	public void drawBlock(TileEntity te, int x, int y) {
		ItemStack item = getItemStackFromBlock(te);
		if (!item.isEmpty()) {
			drawItemStack(item, x, y);
		}
	}

	public String getBlockName(TileEntity te) {
		ItemStack item = getItemStackFromBlock(te);

		if (!item.isEmpty()) {
			return getItemName(item);
		}

		return "Unknown";
	}

	public String getItemName(ItemStack item) {
		try {
			List str = item.getTooltip(Minecraft.getMinecraft().player, ITooltipFlag.TooltipFlags.NORMAL);
			if (str != null && str.size() > 0) {
				return (String) str.get(0);
			}
		} catch (Throwable ignored) {
		}

		return "Unknown";
	}

	private ItemStack getItemStackFromBlock(TileEntity te) {
		if (te != null) {
			if (te instanceof TileEntityClusterElement) {
				return ((TileEntityClusterElement) te).getItemStackFromBlock();
			}

			World world = te.getWorld();
			Block block = te.getBlockType();
			if (world != null && block != null) {
				BlockPos pos = te.getPos();

				return getItemStackFromBlock(world, pos, block, world.getBlockState(pos));
			}
		}

		return ItemStack.EMPTY;
	}

	private ItemStack getItemStackFromBlock(World world, BlockPos pos, Block block, IBlockState state) {

		try {
			//try to get it by picking the block
			ItemStack item = block.getPickBlock(state, new RayTraceResult(RayTraceResult.Type.BLOCK, new Vec3d(pos.getX(), pos.getY(), pos.getZ()), EnumFacing.UP, pos), world, pos, FMLClientHandler.instance().getClientPlayerEntity());
			if (!item.isEmpty()) {
				return item;
			}
		} catch (Throwable ignored) {
		}


		try {
			//try to get it from dropped items
			List<ItemStack> items = block.getDrops(world, pos, state, 0);
			if (!items.isEmpty() && !items.get(0).isEmpty()) {
				return items.get(0);
			}
		} catch (Throwable ignored) {

		}


		//get it from its id and meta
		return new ItemStack(block, 1, block.getMetaFromState(state));
	}

	public ItemStack getItemStackFromBlock(World world, int x, int y, int z) {
		if (world != null) {
			BlockPos pos   = new BlockPos(x, y, z);
			Block    block = world.getBlockState(pos).getBlock();
			if (block != null) {
				return getItemStackFromBlock(world, pos, block, world.getBlockState(pos));
			}
		}

		return ItemStack.EMPTY;
	}

	public void drawItemAmount(ItemStack itemstack, int x, int y) {
		itemRender.renderItemOverlayIntoGUI(fontRenderer, itemstack, x + guiLeft, y + guiTop, null);
		bindTexture(getComponentResource());
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.disableLighting();
	}

	public void drawItemStack(ItemStack itemstack, int x, int y) {
		GlStateManager.pushMatrix();

		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.disableLighting();
		GlStateManager.enableRescaleNormal();
		GlStateManager.enableColorMaterial();
		GlStateManager.enableLighting();

		itemRender.zLevel = 1F;


		try {
			itemRender.renderItemIntoGUI(itemstack, x + guiLeft, y + guiTop);
		} catch (Exception ex) {
			if (itemstack.getItemDamage() != 0) {
				ItemStack newStack = itemstack.copy();
				newStack.setItemDamage(0);
				drawItemStack(newStack, x, y);
			}
		} finally {
			itemRender.zLevel = 0F;

			bindTexture(getComponentResource());
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			GlStateManager.disableLighting();
			GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GlStateManager.enableAlpha();
			GlStateManager.popMatrix();
		}

	}

	public void drawCenteredString(String str, int x, int y, float mult, int width, int color) {
		drawString(str, x + (width - (int) (getStringWidth(str) * mult)) / 2, y, mult, color);
	}

	public void drawCursor(int x, int y, int z, int color) {
		drawCursor(x, y, z, 1F, color);
	}

	public void drawCursor(int x, int y, int z, float size, int color) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, 0, z);
		x += guiLeft;
		y += guiTop;
		GlStateManager.translate(x, y, 0);
		GlStateManager.scale(size, size, 0);
		GlStateManager.translate(-x, -y, 0);
		Gui.drawRect(x, y + 1, x + 1, y + 10, color);
		GlStateManager.popMatrix();
	}

	public void drawLine(int x1, int y1, int x2, int y2) {
		GlStateManager.pushMatrix();

		GlStateManager.disableTexture2D();
		GlStateManager.color(0.4F, 0.4F, 0.4F, 1F);

		//GlStateManager.enableBlend();
		//GL11.glBlendFunc(GL11.GL_DST_COLOR, GL11.GL_DST_COLOR);
		//GL11.glShadeModel(GL11.GL_SMOOTH);
		//GL11.glEnable(GL11.GL_LINE_SMOOTH);
		//GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
		//GL11.glLineWidth(5);
		GL11.glLineWidth(1 + 5 * this.width / 500F);

		GL11.glBegin(GL11.GL_LINES);
		GL11.glVertex3f(guiLeft + x1, guiTop + y1, 0);
		GL11.glVertex3f(guiLeft + x2, guiTop + y2, 0);
		GL11.glEnd();

		GlStateManager.disableBlend();
		GlStateManager.color(1F, 1F, 1F, 1F);
		GlStateManager.enableTexture2D();
		GlStateManager.popMatrix();
	}

	@Override
	public final void drawDefaultBackground() {
		super.drawDefaultBackground();

		startScaling();
	}

	private void startScaling() {
		//start scale
		GlStateManager.pushMatrix();

		float scale = getScale();

		GlStateManager.scale(scale, scale, 1);
		GlStateManager.translate(-guiLeft, -guiTop, 0.0F);
		GlStateManager.translate((this.width - this.xSize * scale) / (2 * scale), (this.height - this.ySize * scale) / (2 * scale), 0.0F);
	}

	@Override
	public void drawScreen(int x, int y, float f) {

		super.drawScreen(scaleX(x), scaleY(y), f);

		stopScaling();
	}

	private void stopScaling() {
		//stop scale
		GlStateManager.popMatrix();
	}

	protected int scaleX(float x) {
		float scale = getScale();
		x /= scale;
		x += guiLeft;
		x -= (this.width - this.xSize * scale) / (2 * scale);
		return (int) x;
	}

	protected int scaleY(float y) {
		float scale = getScale();
		y /= scale;
		y += guiTop;
		y -= (this.height - this.ySize * scale) / (2 * scale);
		return (int) y;
	}

	public void drawFluid(Fluid fluid, int x, int y) {

		if (fluid == null) {
			return;
		}

		TextureMap         textureMapBlocks = mc.getTextureMapBlocks();
		ResourceLocation   fluidStill       = fluid.getStill();
		TextureAtlasSprite icon             = null;

		if (fluidStill != null)
			icon = textureMapBlocks.getAtlasSprite(fluidStill.toString());

		if (icon == null) {
			icon = textureMapBlocks.getMissingSprite();
		}

		if (icon != null) {
			bindTexture(TERRAIN);
			setColor(fluid.getColor());

			drawIcon(icon, x, y);

			GlStateManager.color(1F, 1F, 1F, 1F);
			bindTexture(getComponentResource());
		}
	}

	public void drawIcon(TextureAtlasSprite texture, int x, int y) {
		drawTexturedModalRect(guiLeft + x, guiTop + y, texture, 16, 16);
	}

	private void setColor(int color) {
		float[] colorComponents = new float[3];
		for (int i = 0; i < colorComponents.length; i++) {
			colorComponents[i] = ((color & (255 << (i * 8))) >> (i * 8)) / 255F;
		}
		GlStateManager.color(colorComponents[2], colorComponents[1], colorComponents[0], 1F);
	}


	public int getFontHeight() {
		return fontRenderer.FONT_HEIGHT;
	}

	public int getLeft() {
		return guiLeft;
	}

	public int getTop() {
		return guiTop;
	}
}
