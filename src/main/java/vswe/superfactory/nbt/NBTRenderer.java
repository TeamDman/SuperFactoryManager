package vswe.superfactory.nbt;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.interfaces.IInterfaceRenderer;

@SideOnly(Side.CLIENT)
public class NBTRenderer implements IInterfaceRenderer {
	private static final int INDENT_WIDTH = 5;
	private static final int LINE_HEIGHT  = 8;
	private static final int NODE_SIZE    = 4;
	private static final int NODE_SRC_X   = 120;
	private static final int NODE_SRC_Y   = 156;
	//TODO this uses an old texture file that was "removed" during the merge
	private static final int POS_X        = 5;
	private static final int POS_Y        = 5;
	private static final int TEXT_OFFSET  = 10;
	private static final int VALUE_OFFSET = 70;
	private NBTNode root;
	public NBTRenderer(NBTTagCompound compound) {
		root = NBTNode.generateNodes(compound);
	}

	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		drawNode(this.root, gui, mX, mY);
	}

	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onClick(GuiManager gui, int mX, int mY, int button) {
		if (button == 1) {
			gui.getManager().specialRenderer = null;
		} else {
			onNodeClick(root, mX, mY);
		}
	}

	@Override
	public void onDrag(GuiManager gui, int mX, int mY) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onRelease(GuiManager gui, int mX, int mY) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onKeyTyped(GuiManager gui, char c, int k) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onScroll(int scroll) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	private void drawNode(NBTNode node, GuiManager gui, int mX, int mY) {
		int x = POS_X + INDENT_WIDTH * node.getCachedDepth();
		int y = POS_Y + LINE_HEIGHT * node.getCachedLine();

		if (node.getCachedDepth() >= 0) {
			gui.drawString(node.getName(), x + TEXT_OFFSET, y, 0.7F, 0x404040);

			gui.drawString(node.getValue(), x + TEXT_OFFSET + VALUE_OFFSET, y, 0.7F, 0x404040);
		}

		if (node.getNodes() != null) {
			if (node.getCachedDepth() >= 0) {
				int nodeSrcX = CollisionHelper.inBounds(x, y, NODE_SIZE, NODE_SIZE, mX, mY) ? 1 : 0;
				int nodeSrcY = node.isOpen() ? 1 : 0;

				gui.drawTexture(x, y, NODE_SRC_X + nodeSrcX * NODE_SIZE, NODE_SRC_Y + nodeSrcY * NODE_SIZE, NODE_SIZE, NODE_SIZE);
			}

			if (node.isOpen()) {
				for (NBTNode child : node.getNodes()) {
					drawNode(child, gui, mX, mY);
				}
			}
		}
	}

	private boolean onNodeClick(NBTNode node, int mX, int mY) {
		int x = POS_X + INDENT_WIDTH * node.getCachedDepth();
		int y = POS_Y + LINE_HEIGHT * node.getCachedLine();


		if (node.getNodes() != null) {
			if (node.getCachedDepth() >= 0) {
				if (CollisionHelper.inBounds(x, y, NODE_SIZE, NODE_SIZE, mX, mY)) {
					node.setOpen(!node.isOpen());
					root.updatePosition();
					return true;
				}
			}


			if (node.isOpen()) {
				for (NBTNode child : node.getNodes()) {
					if (onNodeClick(child, mX, mY))
						return true;
				}
			}
		}

		return false;
	}
}
