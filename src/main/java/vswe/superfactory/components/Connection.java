package vswe.superfactory.components;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class Connection {
	private int         componentId;
	private int         connectionId;
	private List<Point> nodes;
	private Point       selectedNode;

	public Connection(int componentId, int connectionId) {
		this.componentId = componentId;
		this.connectionId = connectionId;
		nodes = new ArrayList<Point>();
	}

	public int getComponentId() {
		return componentId;
	}

	public void setComponentId(int componentId) {
		this.componentId = componentId;
	}

	public int getConnectionId() {
		return connectionId;
	}

	public void setConnectionId(int connectionId) {
		this.connectionId = connectionId;
	}

	public Connection copy() {
		Connection copy = new Connection(this.componentId, this.connectionId);
		for (Point node : nodes) {
			copy.nodes.add(node.copy());
		}
		return copy;
	}

	public void addAndSelectNode(int mX, int mY, int id) {
		nodes.add(id, selectedNode = new Point(mX, mY));
	}

	@SideOnly(Side.CLIENT)
	public void update(int mX, int mY) {
		if (selectedNode != null) {
			selectedNode.setX(mX);
			selectedNode.setY(mY);

			if (GuiScreen.isShiftKeyDown()) {
				selectedNode.adjustToGrid();
			}
		}
	}

	public void adjustAllToGrid() {
		for (Point node : nodes) {
			node.adjustToGrid();
		}
	}

	public List<Point> getNodes() {
		return nodes;
	}

	public Point getSelectedNode() {
		return selectedNode;
	}

	public void setSelectedNode(Point selectedNode) {
		this.selectedNode = selectedNode;
	}

}
