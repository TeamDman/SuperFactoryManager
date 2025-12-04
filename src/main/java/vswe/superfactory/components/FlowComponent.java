package vswe.superfactory.components;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.components.internal.ComponentType;
import vswe.superfactory.components.internal.ConnectionOption;
import vswe.superfactory.components.internal.ConnectionSet;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.*;
import vswe.superfactory.settings.Settings;
import vswe.superfactory.tiles.TileEntityManager;

import java.lang.reflect.Constructor;
import java.util.*;

public class FlowComponent implements IComponentNetworkReader, Comparable<FlowComponent> {
	private static final int ARROW_SIZE_H = 10;
	private static final int ARROW_SIZE_W = 9;
	private static final int ARROW_SRC_X  = 0;
	private static final int ARROW_SRC_Y  = 20;
	private static final int ARROW_X      = -10;
	private static final int ARROW_Y      = 5;
	private static final int COMPONENT_SIZE_H       = 20;
	private static final int COMPONENT_SIZE_LARGE_H = 152;
	private static final int COMPONENT_SIZE_LARGE_W = 124;
	private static final int COMPONENT_SIZE_W       = 64;
	private static final int COMPONENT_SRC_LARGE_X  = 64;
	private static final int COMPONENT_SRC_X        = 0;
	private static final int COMPONENT_SRC_Y        = 0;
	private static final int CONNECTION_SIZE_H     = 6;
	private static final int CONNECTION_SIZE_W     = 7;
	private static final int CONNECTION_SRC_X      = 0;
	private static final int CONNECTION_SRC_Y      = 58;
	private static final int CONNECTION_SRC_Y_SIDE = 245;
	private static final int CURSOR_X        = -1;
	private static final int CURSOR_Y        = -4;
	private static final int CURSOR_Z        = 5;
	private static final int DRAGGABLE_SIZE         = 6;
	private static final int EDIT_SIZE       = 9;
	private static final int EDIT_SIZE_SMALL = 7;
	private static final int EDIT_SRC_X      = 32;
	private static final int EDIT_SRC_Y      = 189;
	private static final int EDIT_X          = 103;
	private static final int EDIT_X_SMALL    = 105;
	private static final int EDIT_Y          = 6;
	private static final int EDIT_Y_BOT      = 11;
	private static final int EDIT_Y_TOP      = 2;
	private static final int ERROR_SIZE_H = 10;
	private static final int ERROR_SIZE_W = 2;
	private static final int ERROR_SRC_X  = 42;
	private static final int ERROR_SRC_Y  = 212;
	private static final int ERROR_X      = 2;
	private static final int ERROR_Y      = 8;
	private static final int MAX_NODES  = 15;
	private static final int MENU_ARROW_SIZE_H = 9;
	private static final int MENU_ARROW_SIZE_W = 9;
	private static final int MENU_ARROW_SRC_X  = 0;
	private static final int MENU_ARROW_SRC_Y  = 40;
	private static final int MENU_ARROW_X      = 109;
	private static final int MENU_ARROW_Y      = 2;
	private static final int MENU_ITEM_CAPACITY = 5;
	private static final int MENU_ITEM_SIZE_H   = 13;
	private static final int MENU_ITEM_SIZE_W   = 120;
	private static final int MENU_ITEM_SRC_X    = 0;
	private static final int MENU_ITEM_SRC_Y    = 152;
	private static final int MENU_ITEM_TEXT_X = 5;
	private static final int MENU_ITEM_TEXT_Y = 3;
	private static final int MENU_SIZE_H        = 130;
	private static final int MENU_X             = 2;
	private static final int MENU_Y             = 20;
	private static final String NBT_CONNECTION                   = "Connection";
	private static final String NBT_CONNECTION_POS               = "ConnectionPos";
	private static final String NBT_CONNECTION_TARGET_COMPONENT  = "ConnectionComponent";
	private static final String NBT_CONNECTION_TARGET_CONNECTION = "ConnectionConnection";
	private static final String NBT_INTERVAL                     = "Interval";
	private static final String NBT_MENUS                        = "Menus";
	private static final String NBT_NAME                         = "Name";
	private static final String NBT_NODES                        = "Nodes";
	private static final String NBT_PARENT                       = "Parent";
	private static final String NBT_POS_X                        = "PosX";
	private static final String NBT_POS_Y                        = "PosY";
	private static final String NBT_TYPE                         = "Type";
	private static final int NODE_SIZE  = 4;
	private static final int NODE_SRC_X = 120;
	private static final int NODE_SRC_Y = 152;
	private static final int TEXT_HEIGHT     = 10;
	private static final int TEXT_MAX_LENGTH  = Integer.MAX_VALUE;//31;
	private static final int TEXT_SPACE       = 135;
	private static final int TEXT_SPACE_SHORT = 65;
	private static final int TEXT_X          = 7;
	private static final int TEXT_Y          = 10;
	List<String> errors = new ArrayList<String>();
	private String                   cachedName;
	private String                   cachedShortName;
	private List<FlowComponent>      childrenInputNodes;
	private List<FlowComponent>      childrenOutputNodes;
	private ConnectionSet            connectionSet;
	private Map<Integer, Connection> connections;
	private int                      currentInterval;
	private int                      id;
	private boolean                  isDragging;
	private boolean                  isEditing;
	private boolean                  isInventoryListDirty = true;
	private boolean                  isLarge;
	private boolean                  isLoading;
	private TileEntityManager        manager;
	private List<ComponentMenu>      menus;
	private int                      mouseDragX;
	private int                      mouseDragY;
	private int                      mouseStartX;
	private int                      mouseStartY;
	private String                   name;
	private int                      openMenuId;
	private int                      overrideX = -1;
	private int                      overrideY = -1;
	private FlowComponent            parent;
	private int                      parentLoadId = -1;
	private int                      resetTimer;
	private TextBoxLogic             textBox;
	private ComponentType            type;
	private int                      x;
	private int                      y;

	public FlowComponent(TileEntityManager manager, int x, int y, ComponentType type) {
		this.x = x;
		this.y = y;
		this.connectionSet = type.getSets()[0];
		this.type = type;
		this.manager = manager;
		this.id = manager.getFlowItems().size();

		menus = new ArrayList<ComponentMenu>();
		for (Class<? extends ComponentMenu> componentMenuClass : type.getClasses()) {
			try {
				Constructor<? extends ComponentMenu> constructor = componentMenuClass.getConstructor(FlowComponent.class);
				Object                               obj         = constructor.newInstance(this);


				menus.add((ComponentMenu) obj);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		openMenuId = -1;
		connections = new HashMap<Integer, Connection>();
		textBox = new TextBoxLogic(TEXT_MAX_LENGTH, TEXT_SPACE);

		childrenInputNodes = new ArrayList<FlowComponent>();
		childrenOutputNodes = new ArrayList<FlowComponent>();
	}

	public static FlowComponent readFromNBT(TileEntityManager jam, NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		FlowComponent component = null;
		try {
			int x      = nbtTagCompound.getShort(NBT_POS_X);
			int y      = nbtTagCompound.getShort(NBT_POS_Y);
			int typeId = nbtTagCompound.getByte(NBT_TYPE);

			component = new FlowComponent(jam, x, y, ComponentType.getTypeFromId(typeId));
			component.isLoading = true;

			if (nbtTagCompound.hasKey(NBT_NAME)) {
				component.name = nbtTagCompound.getString(NBT_NAME);
			} else {
				component.name = null;
			}

			if (nbtTagCompound.hasKey(NBT_PARENT)) {
				component.parentLoadId = nbtTagCompound.getShort(NBT_PARENT);
			}

			NBTTagList connections = nbtTagCompound.getTagList(NBT_CONNECTION, 10);
			for (int i = 0; i < connections.tagCount(); i++) {
				NBTTagCompound connectionTag = connections.getCompoundTagAt(i);

				int componentId;
				if (version < 9) {
					componentId = connectionTag.getByte(NBT_CONNECTION_TARGET_COMPONENT);
				} else {
					componentId = connectionTag.getShort(NBT_CONNECTION_TARGET_COMPONENT);
				}
				Connection connection = new Connection(componentId, connectionTag.getByte(NBT_CONNECTION_TARGET_CONNECTION));

				if (connectionTag.hasKey(NBT_NODES)) {
					connection.getNodes().clear();
					NBTTagList nodes = connectionTag.getTagList(NBT_NODES, 10);
					for (int j = 0; j < nodes.tagCount(); j++) {
						NBTTagCompound nodeTag = nodes.getCompoundTagAt(j);

						connection.getNodes().add(new Point(nodeTag.getShort(NBT_POS_X), nodeTag.getShort(NBT_POS_Y)));
					}
				}

				component.connections.put((int) connectionTag.getByte(NBT_CONNECTION_POS), connection);
			}

			if (component.type == ComponentType.TRIGGER) {
				component.currentInterval = nbtTagCompound.getShort(NBT_INTERVAL);
			}

			NBTTagList menuTagList = nbtTagCompound.getTagList(NBT_MENUS, 10);
			int        menuId      = 0;
			for (int i = 0; i < menuTagList.tagCount(); i++) {
				NBTTagCompound menuTag = menuTagList.getCompoundTagAt(i);


				//added an extra menu to the triggers
				if (component.type == ComponentType.TRIGGER && i == 1 && version < 1) {
					menuId++;
				}

				//added a second extra menu to the triggers
				if (component.type == ComponentType.TRIGGER && i == 2 && version < 5) {
					menuId++;
				}

				//added a third extra menu to the triggers
				if (component.type == ComponentType.TRIGGER && i == 0 && version < 6) {
					menuId++;
				}


				//added the bud menus to the triggers
				if (component.type == ComponentType.TRIGGER && i == 1 && version < 8) {
					menuId++;
				}
				if (component.type == ComponentType.TRIGGER && i == 4 && version < 8) {
					menuId++;
				}

				//added an extra menu to the flow controls
				if (component.type == ComponentType.FLOW_CONTROL && i == 0 && version < 4) {
					menuId++;
				}

				//added two extra menus to the camouflage updater
				if (component.type == ComponentType.CAMOUFLAGE && i == 1 && version < 10) {
					menuId += 2;
				}

				//added crafting priority to the crafter
				if (component.type == ComponentType.AUTO_CRAFTING && i == 1 && version < 11) {
					menuId++;
				}

				component.menus.get(menuId).readFromNBT(menuTag, version, pickup);
				menuId++;
			}

			return component;
		} finally {
			if (component != null) {
				component.isLoading = false;
			}
		}
	}

	public int getCurrentInterval() {
		return currentInterval;
	}

	public void setCurrentInterval(int currentInterval) {
		this.currentInterval = currentInterval;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public void update(float partial) {
		//no need for this to be precise, can ignore the partial time
		if (resetTimer > 0) {
			if (resetTimer == 1) {
				x = mouseStartX;
				y = mouseStartY;
			}

			resetTimer--;
		}

		for (ComponentMenu menu : menus) {
			menu.update(partial);
		}
	}

	@SideOnly(Side.CLIENT)
	public void draw(GuiManager gui, int mX, int mY, int zLevel) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, 0, zLevel);

		gui.drawTexture(x, y, isLarge ? COMPONENT_SRC_LARGE_X : COMPONENT_SRC_X, COMPONENT_SRC_Y, getComponentWidth(), getComponentHeight());

		int internalX = mX - x;
		int internalY = mY - y;

		int srcArrowX = isLarge ? 1 : 0;
		int srcArrowY = inArrowBounds(internalX, internalY) ? 1 : 0;
		gui.drawTexture(x + getComponentWidth() + ARROW_X, y + ARROW_Y, ARROW_SRC_X + ARROW_SIZE_W * srcArrowX, ARROW_SRC_Y + ARROW_SIZE_H * srcArrowY, ARROW_SIZE_W, ARROW_SIZE_H);


		if (isLarge) {
			for (int i = 0; i < menus.size(); i++) {
				ComponentMenu menu = menus.get(i);

				if (!menu.isVisible()) {
					if (openMenuId == i) {
						openMenuId = -1;
					}

					continue;
				}

				int itemX = getMenuAreaX();
				int itemY = y + getMenuItemY(i);
				gui.drawTexture(itemX, itemY, MENU_ITEM_SRC_X, MENU_ITEM_SRC_Y, MENU_ITEM_SIZE_W, MENU_ITEM_SIZE_H);

				int srcItemArrowX = inMenuArrowBounds(i, internalX, internalY) ? 1 : 0;
				int srcItemArrowY = i == openMenuId ? 1 : 0;
				gui.drawTexture(itemX + MENU_ARROW_X, itemY + MENU_ARROW_Y, MENU_ARROW_SRC_X + MENU_ARROW_SIZE_W * srcItemArrowX, MENU_ARROW_SRC_Y + MENU_ARROW_SIZE_H * srcItemArrowY, MENU_ARROW_SIZE_W, MENU_ARROW_SIZE_H);


				gui.drawString(menu.getName(), x + MENU_X + MENU_ITEM_TEXT_X, y + getMenuItemY(i) + MENU_ITEM_TEXT_Y, 0x404040);

				if (i == openMenuId) {
					GlStateManager.pushMatrix();
					GlStateManager.translate(itemX, getMenuAreaY(i), 0);
					menu.draw(gui, mX - itemX, mY - getMenuAreaY(i));
					GlStateManager.popMatrix();
				}
			}
		}


		boolean hasConnection = false;
		int     outputCount   = 0;
		int     inputCount    = 0;
		int     sideCount     = 0;
		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			ConnectionOption connection = connectionSet.getConnections()[i];

			int[] location = getConnectionLocation(connection, inputCount, outputCount, sideCount);
			if (location == null) {
				continue;
			}

			if (connection.isInput()) {
				inputCount++;
			} else if (connection.getType() == ConnectionOption.ConnectionType.OUTPUT) {
				outputCount++;
			} else {
				sideCount++;
			}

			int connectionWidth  = location[3];
			int connectionHeight = location[4];

			int srcConnectionX = (CollisionHelper.inBounds(location[0], location[1], connectionWidth, connectionHeight, mX, mY)) ? 1 : 0;

			Connection current = manager.getCurrentlyConnecting();
			if (current != null && current.getComponentId() == id && current.getConnectionId() == i) {
				gui.drawLine(location[0] + connectionWidth / 2, location[1] + connectionHeight / 2, overrideX != -1 ? overrideX : mX, overrideY != -1 ? overrideY : mY);
			}

			Connection connectedConnection = connections.get(i);
			if (connectedConnection != null) {
				hasConnection = true;
				if (id < connectedConnection.getComponentId() && connectedConnection.getComponentId() < manager.getFlowItems().size()) {
					int[] otherLocation = manager.getFlowItems().get(connectedConnection.getComponentId()).getConnectionLocationFromId(connectedConnection.getConnectionId());
					if (otherLocation == null) {
						continue;
					}
					int startX = location[0] + connectionWidth / 2;
					int startY = location[1] + connectionHeight / 2;
					int endX   = otherLocation[0] + connectionWidth / 2;
					int endY   = otherLocation[1] + connectionHeight / 2;

					GlStateManager.pushMatrix();
					GlStateManager.translate(0, 0, -zLevel);
					List<Point> nodes = connectedConnection.getNodes();
					for (int j = 0; j <= nodes.size(); j++) {
						int x1, y1, x2, y2;
						if (j == 0) {
							x1 = startX;
							y1 = startY;
						} else {
							x1 = nodes.get(j - 1).getX();
							y1 = nodes.get(j - 1).getY();
						}

						if (j == nodes.size()) {
							x2 = endX;
							y2 = endY;
						} else {
							x2 = nodes.get(j).getX();
							y2 = nodes.get(j).getY();
						}

						gui.drawLine(x1, y1, x2, y2);
					}

					for (Point node : nodes) {
						int x        = node.getX() - NODE_SIZE / 2;
						int y        = node.getY() - NODE_SIZE / 2;
						int srcXNode = connectedConnection.getSelectedNode() == null && CollisionHelper.inBounds(x, y, NODE_SIZE, NODE_SIZE, mX, mY) ? 1 : 0;
						gui.drawTexture(x, y, NODE_SRC_X + srcXNode * NODE_SIZE, NODE_SRC_Y, NODE_SIZE, NODE_SIZE);
					}

					GlStateManager.popMatrix();
				}
			}

			gui.drawTexture(location[0], location[1], CONNECTION_SRC_X + srcConnectionX * connectionWidth, location[2], connectionWidth, connectionHeight);

		}

		errors.clear();
		if (hasConnection || getConnectionSet().getConnections().length == 0) {
			for (ComponentMenu menu : menus) {
				if (menu.isVisible()) {
					menu.addErrors(errors);
				}
			}
		}

		if (!errors.isEmpty()) {
			int srcErrorY = CollisionHelper.inBounds(x + ERROR_X, y + ERROR_Y, ERROR_SIZE_W, ERROR_SIZE_H, mX, mY) ? 1 : 0;
			gui.drawTexture(x + ERROR_X, y + ERROR_Y, ERROR_SRC_X, ERROR_SRC_Y + srcErrorY * ERROR_SIZE_H, ERROR_SIZE_W, ERROR_SIZE_H);
		}

		if (!isEditing || isLarge) {
			String name = getName();
			if (!isLarge) {
				name = getShortName(gui, name);
			}
			gui.drawString(name, x + TEXT_X, y + TEXT_Y, 0.7F, isEditing ? 0x707020 : 0x404040);
		}

		if (isEditing) {
			gui.drawString(getShortName(gui, getName()), x + TEXT_X, y + TEXT_Y, 0.7F, 0x207020);
		}

		if (name != null && Settings.isCommandTypes() && !GuiScreen.isCtrlKeyDown()) {
			gui.drawCenteredString(getType().getName(), x + DRAGGABLE_SIZE, y + 3, 0.7F, getComponentWidth() - DRAGGABLE_SIZE - ARROW_SIZE_W, 0x707070);
		}

		if (isLarge) {
			if (isEditing) {
				gui.drawCursor(x + TEXT_X + (int) ((textBox.getCursorPosition(gui) + CURSOR_X) * 0.7F), y + TEXT_Y + (int) (CURSOR_Y * 0.7F), CURSOR_Z, 0.7F, 0xFFFFFFFF);
				for (int i = 0; i < 2; i++) {
					int buttonX = x + EDIT_X_SMALL;
					int buttonY = y + (i == 0 ? EDIT_Y_TOP : EDIT_Y_BOT);

					int srcXButton = CollisionHelper.inBounds(buttonX, buttonY, EDIT_SIZE_SMALL, EDIT_SIZE_SMALL, mX, mY) ? 1 : 0;
					int srcYButton = i;

					gui.drawTexture(buttonX, buttonY, EDIT_SRC_X + srcXButton * EDIT_SIZE_SMALL, EDIT_SRC_Y + EDIT_SIZE + EDIT_SIZE_SMALL * srcYButton, EDIT_SIZE_SMALL, EDIT_SIZE_SMALL);
				}
			} else {
				int buttonX    = x + EDIT_X;
				int buttonY    = y + EDIT_Y;
				int srcXButton = CollisionHelper.inBounds(buttonX, buttonY, EDIT_SIZE, EDIT_SIZE, mX, mY) ? 1 : 0;

				gui.drawTexture(buttonX, buttonY, EDIT_SRC_X + srcXButton * EDIT_SIZE, EDIT_SRC_Y, EDIT_SIZE, EDIT_SIZE);
			}
		}

		GlStateManager.popMatrix();
	}

	private String getShortName(GuiManager gui, String name) {
		if (!name.equals(cachedName)) {
			cachedShortName = "";
			for (char c : name.toCharArray()) {
				if (gui.getStringWidth(cachedShortName + c) > TEXT_SPACE_SHORT) {
					break;
				}
				cachedShortName += c;
			}
		}
		cachedName = name;
		return cachedShortName;
	}

	@SideOnly(Side.CLIENT)
	public String getName() {
		return textBox.getText() == null ? name == null || GuiScreen.isCtrlKeyDown() ? getType().getName() : name : textBox.getText();
	}

	@SideOnly(Side.CLIENT)
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		if (isLarge) {
			for (int i = 0; i < menus.size(); i++) {
				ComponentMenu menu = menus.get(i);

				if (menu.isVisible() && i == openMenuId) {
					GlStateManager.pushMatrix();
					GlStateManager.translate(getMenuAreaX(), getMenuAreaY(i), 0);
					menu.drawMouseOver(gui, mX - getMenuAreaX(), mY - getMenuAreaY(i));
					GlStateManager.popMatrix();
				}
			}
		}

		int outputCount = 0;
		int inputCount  = 0;
		int sideCount   = 0;
		for (ConnectionOption connection : connectionSet.getConnections()) {
			int[] location = getConnectionLocation(connection, inputCount, outputCount, sideCount);
			if (location == null) {
				continue;
			}

			if (connection.isInput()) {
				inputCount++;
			} else if (connection.getType() == ConnectionOption.ConnectionType.OUTPUT) {
				outputCount++;
			} else {
				sideCount++;
			}

			if (CollisionHelper.inBounds(location[0], location[1], CONNECTION_SIZE_W, CONNECTION_SIZE_H, mX, mY)) {
				gui.drawMouseOver(connection.getName(this, (connection.isInput() ? inputCount : outputCount) - 1), mX, mY);
			}
		}

		if (!errors.isEmpty()) {
			if (CollisionHelper.inBounds(x + ERROR_X, y + ERROR_Y, ERROR_SIZE_W, ERROR_SIZE_H, mX, mY)) {
				String str = "";
				for (int i = 0; i < errors.size(); i++) {
					if (i != 0) {
						str += "\n\n";
					}
					str += errors.get(i);
				}
				gui.drawMouseOver(str, mX, mY);
			}
		}
	}

	private int[] getConnectionLocation(ConnectionOption connection, int inputCount, int outputCount, int sideCount) {
		int id = inputCount + outputCount + sideCount;
		if (!connection.isInput()) {
			id -= Math.min(connectionSet.getInputCount(), childrenInputNodes.size());
		}

		if (!connection.isValid(this, id)) {
			return null;
		}

		int targetX;
		int targetY;

		if (connection.getType() == ConnectionOption.ConnectionType.SIDE) {
			targetY = y + (int) (getComponentHeight() * ((sideCount + 0.5) / connectionSet.getSideCount()));
			targetY -= CONNECTION_SIZE_H / 2;
			targetX = x + getComponentWidth();
			return new int[]{targetX, targetY, CONNECTION_SRC_Y_SIDE, CONNECTION_SIZE_H, CONNECTION_SIZE_W};
		} else {
			int srcConnectionY;
			int currentCount;
			int totalCount;

			if (connection.isInput()) {
				currentCount = inputCount;

				totalCount = connectionSet.getInputCount();
				if (getConnectionSet() == ConnectionSet.DYNAMIC) {
					totalCount = Math.min(totalCount, childrenInputNodes.size());
				}

				srcConnectionY = 1;
				targetY = y - CONNECTION_SIZE_H;
			} else {
				currentCount = outputCount;

				totalCount = connectionSet.getOutputCount();
				if (getConnectionSet() == ConnectionSet.DYNAMIC) {
					totalCount = Math.min(totalCount, childrenOutputNodes.size());
				}
				srcConnectionY = 0;
				targetY = y + getComponentHeight();
			}

			targetX = x + (int) (getComponentWidth() * ((currentCount + 0.5) / totalCount));
			targetX -= CONNECTION_SIZE_W / 2;

			return new int[]{targetX, targetY, CONNECTION_SRC_Y + srcConnectionY * CONNECTION_SIZE_H, CONNECTION_SIZE_W, CONNECTION_SIZE_H};
		}
	}

	public ConnectionSet getConnectionSet() {
		return connectionSet;
	}

	public void setConnectionSet(ConnectionSet connectionSet) {
		if (this.connections != null && this.connectionSet != null && !isLoading) {
			int oldLength = this.connectionSet.getConnections().length;
			int newLength = connectionSet.getConnections().length;

			for (int i = 0; i < Math.min(oldLength, newLength); i++) {
				Connection connection = connections.get(i);
				if (connection != null && this.connectionSet.getConnections()[i].isInput() != connectionSet.getConnections()[i].isInput()) {
					removeConnection(i);
				}
			}

			for (int i = newLength; i < oldLength; i++) {
				Connection connection = connections.get(i);
				if (connection != null) {
					removeConnection(i);
				}
			}
		}
		this.connectionSet = connectionSet;
	}

	public void removeConnection(int id) {
		Connection connection = connections.get(id);

		addConnection(id, null);
		if (connection.getComponentId() >= 0 && connection.getComponentId() < getManager().getFlowItems().size()) {
			manager.getFlowItems().get(connection.getComponentId()).addConnection(connection.getConnectionId(), null);
		}
	}

	private void addConnection(int id, Connection connection) {
		if (getManager().getWorld() != null && getManager().getWorld().isRemote) {
			DataWriter dw = PacketHandler.getWriterForServerComponentPacket(this, null);
			if (connection != null) {
				writeConnectionData(dw, id, true, connection.getComponentId(), connection.getConnectionId());
			} else {
				writeConnectionData(dw, id, false, 0, 0);
			}
			PacketHandler.sendDataToServer(dw);
		}
		connections.put(id, connection);
	}

	public TileEntityManager getManager() {
		return manager;
	}

	private void writeConnectionData(DataWriter dw, int i, boolean target, int targetComponent, int targetConnection) {
		dw.writeBoolean(false);
		dw.writeData(i, DataBitHelper.CONNECTION_ID);
		dw.writeBoolean(true); //connection
		dw.writeBoolean(target);
		if (target) {
			dw.writeComponentId(getManager(), targetComponent);
			dw.writeData(targetConnection, DataBitHelper.CONNECTION_ID);
		}
	}

	public int getComponentWidth() {
		return isLarge ? COMPONENT_SIZE_LARGE_W : COMPONENT_SIZE_W;
	}

	public int getComponentHeight() {
		return isLarge ? COMPONENT_SIZE_LARGE_H : COMPONENT_SIZE_H;
	}

	private int getMenuAreaX() {
		return x + MENU_X;
	}

	private int getMenuAreaY(int i) {
		return y + getMenuItemY(i) + MENU_ITEM_SIZE_H;
	}

	private int getMenuItemY(int id) {


		int ret = MENU_Y;
		for (int i = 0; i < id; i++) {
			if (menus.get(i).isVisible()) {
				ret += MENU_ITEM_SIZE_H - 1;
				if (openMenuId == i) {
					ret += getMenuOpenSize() - 1;
				}
			}
		}


		return ret;
	}

	public static int getMenuOpenSize() {
		return MENU_SIZE_H - MENU_ITEM_CAPACITY * (MENU_ITEM_SIZE_H - 1);
	}

	public boolean onClick(int mX, int mY, int button) {
		if (CollisionHelper.inBounds(x, y, getComponentWidth(), getComponentHeight(), mX, mY)) {
			int internalX = mX - x;
			int internalY = mY - y;

			if (internalX <= DRAGGABLE_SIZE && internalY <= DRAGGABLE_SIZE) {
				mouseStartX = mouseDragX = mX;
				mouseStartY = mouseDragY = mY;
				isDragging = true;
			} else if (isLarge && !isEditing && CollisionHelper.inBounds(EDIT_X, EDIT_Y, EDIT_SIZE, EDIT_SIZE, internalX, internalY)) {
				isEditing = true;
				textBox.setTextAndCursor(getName());
			} else if (isLarge && isEditing && CollisionHelper.inBounds(EDIT_X_SMALL, EDIT_Y_TOP, EDIT_SIZE_SMALL, EDIT_SIZE_SMALL, internalX, internalY)) {
				isEditing = false;
				name = textBox.getText();
				if (name.equals("")) {
					name = null;
				}
				sendNameToServer();
				textBox.setText(null);
			} else if (isLarge && isEditing && CollisionHelper.inBounds(EDIT_X_SMALL, EDIT_Y_BOT, EDIT_SIZE_SMALL, EDIT_SIZE_SMALL, internalX, internalY)) {
				isEditing = false;
				textBox.setText(null);
			} else if (isLarge && isEditing && CollisionHelper.inBounds(TEXT_X, TEXT_Y, TEXT_SPACE, TEXT_HEIGHT, internalX, internalY)) {
				if (button == 1) {
					textBox.setTextAndCursor("");
				}
			} else if (inArrowBounds(internalX, internalY) || (Settings.isLargeOpenHitBox() && internalY < COMPONENT_SIZE_H)) {
				if (!isLarge && type == ComponentType.GROUP && Settings.isQuickGroupOpen() && !GuiScreen.isShiftKeyDown()) {
					manager.setSelectedComponent(this);
				} else {
					isLarge = !isLarge;
				}
			} else if (isLarge) {

				for (int i = 0; i < menus.size(); i++) {
					ComponentMenu menu = menus.get(i);

					if (menu.isVisible()) {
						if (inMenuArrowBounds(i, internalX, internalY) || (Settings.isLargeOpenHitBoxMenu() && internalY >= getMenuItemY(i) && internalY <= getMenuItemY(i) + MENU_ITEM_SIZE_H)) {
							if (openMenuId == i) {
								openMenuId = -1;
							} else {
								openMenuId = i;
							}

							return true;
						}

						if (i == openMenuId) {
							menu.onClick(mX - getMenuAreaX(), mY - getMenuAreaY(i), button);
						}
					}
				}

			}
			return true;
		} else {
			int outputCount = 0;
			int inputCount  = 0;
			int sideCount   = 0;
			for (int i = 0; i < connectionSet.getConnections().length; i++) {
				ConnectionOption connection = connectionSet.getConnections()[i];

				int[] location = getConnectionLocation(connection, inputCount, outputCount, sideCount);
				if (location == null) {
					continue;
				}
				if (connection.isInput()) {
					inputCount++;
				} else if (connection.getType() == ConnectionOption.ConnectionType.OUTPUT) {
					outputCount++;
				} else {
					sideCount++;
				}

				if (CollisionHelper.inBounds(location[0], location[1], CONNECTION_SIZE_W, CONNECTION_SIZE_H, mX, mY)) {


					Connection current = manager.getCurrentlyConnecting();
					if (button == 1 && current == null) {
						Connection selected = connections.get(i);

						if (selected != null) {
							int           connectionId = i;
							boolean       reversed     = false;
							FlowComponent component    = this;
							if (selected.getComponentId() < id) {
								connectionId = selected.getConnectionId();
								component = manager.getFlowItems().get(selected.getComponentId());
								selected = component.getConnection(selected.getConnectionId());
								reversed = true;
							}
							if (selected.getNodes().size() < MAX_NODES && selected.getSelectedNode() == null) {
								int id = reversed ? selected.getNodes().size() : 0;
								selected.addAndSelectNode(mX, mY, id);
								component.sendConnectionNode(connectionId, id, false, true, mX, mY);
							}
						}
					} else {
						if (current == null) {
							if (connections.get(i) != null) {
								removeConnection(i);
							}
							manager.setCurrentlyConnecting(new Connection(id, i));
						} else if (current.getComponentId() == this.id && current.getConnectionId() == i) {
							manager.setCurrentlyConnecting(null);
						} else if (current.getComponentId() != id) {
							if (manager.getFlowItems().size() <= current.getComponentId() || current.getComponentId() < 0)
								return true;

							FlowComponent    connectTo       = manager.getFlowItems().get(current.getComponentId());

							if (connectTo.connectionSet.getConnections().length <= current.getConnectionId() || current.getConnectionId() < 0)
								return true;

							ConnectionOption connectToOption = connectTo.connectionSet.getConnections()[current.getConnectionId()];

							if (connectToOption.isInput() != connection.isInput()) {

								if (checkForLoops(i, current)) {
									return true;
								}

								if (connections.get(i) != null) {
									removeConnection(i);
								}

								Connection thisConnection = new Connection(id, i);
								connectTo.addConnection(current.getConnectionId(), thisConnection);
								addConnection(i, manager.getCurrentlyConnecting());
								manager.setCurrentlyConnecting(null);
							}
						}
					}

					return true;
				} else {
					Connection selected = connections.get(i);
					if (selected != null) {
						List<Point> nodes = selected.getNodes();
						for (int j = 0; j < nodes.size(); j++) {
							Point node = nodes.get(j);
							int   x    = node.getX() - NODE_SIZE / 2;
							int   y    = node.getY() - NODE_SIZE / 2;
							if (CollisionHelper.inBounds(x, y, NODE_SIZE, NODE_SIZE, mX, mY)) {
								if (button == 0) {
									selected.setSelectedNode(node);
								} else if (button == 1) {
									if (GuiScreen.isShiftKeyDown()) {
										sendConnectionNode(i, j, true, false, 0, 0);
									} else if (selected.getNodes().size() < MAX_NODES && selected.getSelectedNode() == null) {
										selected.addAndSelectNode(mX, mY, j + 1);
										sendConnectionNode(i, j + 1, false, true, mX, mY);
									}
								}
								return true;
							}
						}
					}
				}
			}

			return false;
		}
	}

	private boolean checkForLoops(int connectionId, Connection connection) {
		return checkForLoops(new ArrayList<Integer>(), this, connectionId, connection);
	}

	private boolean checkForLoops(List<Integer> usedComponents, FlowComponent currentComponent, int connectionId, Connection connection) {
		if (usedComponents.contains(currentComponent.getId())) {
			return true;
		}
		usedComponents.add(currentComponent.getId());

		for (int i = 0; i < currentComponent.connectionSet.getConnections().length; i++) {
			if (!currentComponent.connectionSet.getConnections()[i].isInput()) {
				Connection c = null;

				if (connectionId == i && currentComponent.getId() == this.id) {
					//the new connection
					c = connection;
				} else if (connection.getComponentId() == currentComponent.getId() && connection.getConnectionId() == i) {
					//the new connection in the other direction
					c = new Connection(this.getId(), connectionId);
				} else {
					c = currentComponent.connections.get(i);
					//old connection that will be replaced
					if (c != null && c.getComponentId() == this.id && c.getConnectionId() == connectionId) {
						c = null;
					}
				}

				if (c != null) {
					if (c.getComponentId() >= 0 && c.getComponentId() < manager.getFlowItems().size()) {
						List<Integer> usedComponentsCopy = new ArrayList<Integer>(usedComponents);

						if (checkForLoops(usedComponentsCopy, manager.getFlowItems().get(c.getComponentId()), connectionId, connection)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	public void removeAllConnections() {
		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			Connection connection = connections.get(i);
			if (connection != null) {
				removeConnection(i);
			}
		}
	}

	public void onDrag(int mX, int mY) {
		followMouse(mX, mY);

		for (int i = 0; i < menus.size(); i++) {
			ComponentMenu menu = menus.get(i);

			menu.onDrag(mX - getMenuAreaX(), mY - getMenuAreaY(i), i == openMenuId);
		}


		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			Connection connection = connections.get(i);
			if (connection != null) {
				connection.update(mX, mY);
			}
		}
	}

	@SideOnly(Side.CLIENT)
	private void followMouse(int mX, int mY) {
		if (isDragging) {
			x += mX - mouseDragX;
			y += mY - mouseDragY;


			if (GuiScreen.isShiftKeyDown()) {
				adjustToGrid();
				mouseDragX = x;
				mouseDragY = y;
			} else {
				mouseDragX = mX;
				mouseDragY = mY;
			}
		}
	}

	private void adjustToGrid() {
		x = (x / 10) * 10;
		y = (y / 10) * 10;
	}

	public void onRelease(int mX, int mY, int button) {
		followMouse(mX, mY);
		if (isDragging) {
			writeLocationData();
		}


		for (int i = 0; i < menus.size(); i++) {
			ComponentMenu menu = menus.get(i);
			menu.onRelease(mX - getMenuAreaX(), mY - getMenuAreaY(i), isLarge && i == openMenuId);
		}


		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			Connection connection = connections.get(i);
			if (connection != null) {
				for (int j = 0; j < connection.getNodes().size(); j++) {
					Point node = connection.getNodes().get(j);
					if (node.equals(connection.getSelectedNode())) {
						connection.update(mX, mY);
						sendConnectionNode(i, j, false, false, node.getX(), node.getY());
						connection.setSelectedNode(null);
						return;
					}
				}
			}
		}

	}

	private void writeLocationData() {
		DataWriter dw = PacketHandler.getWriterForServerComponentPacket(this, null);
		writeLocationData(dw);
		PacketHandler.sendDataToServer(dw);
	}

	private void writeLocationData(DataWriter dw) {
		dw.writeBoolean(true); //component specific
		dw.writeBoolean(true); //location
		dw.writeBoolean(true); //position
		dw.writeData(x, DataBitHelper.FLOW_CONTROL_X);
		dw.writeData(y, DataBitHelper.FLOW_CONTROL_Y);
	}

	private void sendConnectionNode(int connectionId, int nodeId, boolean deleted, boolean created, int x, int y) {
		DataWriter dw = PacketHandler.getWriterForServerComponentPacket(this, null);
		writeConnectionNode(dw, -1, connectionId, nodeId, deleted, created, x, y);
		PacketHandler.sendDataToServer(dw);
	}

	private void writeConnectionNode(DataWriter dw, int length, int connectionId, int nodeId, boolean deleted, boolean created, int x, int y) {
		dw.writeBoolean(false);
		dw.writeData(connectionId, DataBitHelper.CONNECTION_ID);
		dw.writeBoolean(false); //nodes
		dw.writeData(nodeId, DataBitHelper.NODE_ID);
		if (length != -1) {
			dw.writeData(length, DataBitHelper.NODE_ID);
		}
		dw.writeBoolean(deleted);
		if (!deleted) {
			dw.writeBoolean(created);
			dw.writeData(x, DataBitHelper.FLOW_CONTROL_X);
			dw.writeData(y, DataBitHelper.FLOW_CONTROL_Y);
		}

	}

	public void postRelease() {
		isDragging = false;
	}

	public void adjustEverythingToGridRaw() {
		if (true)
			return;  //TODO work in progress
		adjustToGrid();
		for (Connection connection : connections.values()) {
			if (connection != null) {
				connection.adjustAllToGrid();
			}
		}
	}

	public void adjustEverythingToGridFine() {
		if (true)
			return; //TODO work in progress
		int outputCount = 0;
		int inputCount  = 0;
		int sideCount   = 0;
		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			ConnectionOption connectionOption = connectionSet.getConnections()[i];

			int[] location = getConnectionLocation(connectionOption, inputCount, outputCount, sideCount);
			if (location == null) {
				continue;
			}

			if (connectionOption.isInput()) {
				inputCount++;
			} else if (connectionOption.getType() == ConnectionOption.ConnectionType.OUTPUT) {
				outputCount++;
			} else {
				sideCount++;
			}

			Connection connection = connections.get(i);
			if (connection != null && id < connection.getComponentId()) {


				int   startX        = location[0] + location[3] / 2;
				int   startY        = location[1] + location[4] / 2;
				int[] otherLocation = getManager().getFlowItems().get(connection.getComponentId()).getConnectionLocationFromId(connection.getConnectionId());
				if (otherLocation == null) {
					return;
				}
				int endX = otherLocation[0] + otherLocation[3] / 2;
				int endY = otherLocation[1] + otherLocation[4] / 2;

				if (startX != endX && startY != endY && connection.getNodes().size() < 1) {
					connection.getNodes().add(new Point(startX, startY));
				}

				int x = startX;
				int y = startY;

				List<Point> nodes = connection.getNodes();
				for (int j = 0; j <= nodes.size(); j++) {
					Point   point;
					boolean isFinal;
					if (j == nodes.size()) {
						if (j == 0) {
							break;
						}
						point = nodes.get(j - 1);
						x = endX;
						y = endY;
					} else {
						point = nodes.get(j);
					}
                    /*boolean closeX = Math.abs(point.getX() - x) < 20;
                    boolean closeY = Math.abs(point.getY() - y) < 20;

                    if (closeX && closeY) {
                        System.out.println("Double"); */
					if (Math.abs(point.getX() - x) < Math.abs(point.getY() - y)) {
						point.setX(x);
					} else {
						point.setY(y);
					}
					//TODO how do we decide?
                    /*} else if (closeX) {
                        point.setX(x);
                    } else if (closeY) {
                        point.setY(y);
                    } else {
                        System.out.println("Nothing");
                        //Do nothing
                    } */

					x = point.getX();
					y = point.getY();
				}
			}
		}


	}

	public int[] getConnectionLocationFromId(int id) {
		int outputCount = 0;
		int inputCount  = 0;
		int sideCount   = 0;
		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			ConnectionOption connection = connectionSet.getConnections()[i];

			int[] location = getConnectionLocation(connection, inputCount, outputCount, sideCount);
			if (location == null) {
				continue;
			}
			if (id == i) {
				return location;
			}
			if (connection.isInput()) {
				inputCount++;
			} else if (connection.getType() == ConnectionOption.ConnectionType.OUTPUT) {
				outputCount++;
			} else {
				sideCount++;
			}
		}
		return null;
	}

	private boolean inArrowBounds(int internalX, int internalY) {
		return CollisionHelper.inBounds(getComponentWidth() + ARROW_X, ARROW_Y, ARROW_SIZE_W, ARROW_SIZE_H, internalX, internalY);
	}

	private boolean inMenuArrowBounds(int i, int internalX, int internalY) {
		return CollisionHelper.inBounds(MENU_X + MENU_ARROW_X, getMenuItemY(i) + MENU_ARROW_Y, MENU_ARROW_SIZE_W, MENU_ARROW_SIZE_H, internalX, internalY);
	}

	@SideOnly(Side.CLIENT)
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		if (isLarge && isEditing) {
			textBox.onKeyStroke(gui, c, k);
			return true;
		} else if (isLarge && openMenuId != -1) {
			return menus.get(openMenuId).onKeyStroke(gui, c, k);
		} else {
			return false;
		}
	}

	public ComponentType getType() {
		return type;
	}

	public List<ComponentMenu> getMenus() { // todo: make Enum and hashmap for this magic number abomination
		return menus;
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		//might need some clean up
		if (dr.readBoolean()) {
			if (dr.readBoolean()) {
				if (dr.readBoolean()) {
					x = dr.readData(DataBitHelper.FLOW_CONTROL_X);
					y = dr.readData(DataBitHelper.FLOW_CONTROL_Y);
				} else {
					if (dr.readBoolean()) {
						setParent(getManager().getFlowItems().get(dr.readComponentId()));
					} else {
						setParent(null);
					}
				}

			} else {
				name = dr.readString(DataBitHelper.NAME_LENGTH);
			}
		} else {
			int connectionId = dr.readData(DataBitHelper.CONNECTION_ID);
			if (dr.readBoolean()) {
				Connection connection;
				if (dr.readBoolean()) {
					int targetComponentId  = dr.readComponentId();
					int targetConnectionId = dr.readData(DataBitHelper.CONNECTION_ID);

					connection = new Connection(targetComponentId, targetConnectionId);
				} else {
					connection = null;
				}

				connections.put(connectionId, connection);
			} else if (connections.get(connectionId) != null) {
				Connection connection = connections.get(connectionId);

				int id     = dr.readData(DataBitHelper.NODE_ID);
				int length = -1;
				if (manager.getWorld().isRemote) {
					length = dr.readData(DataBitHelper.NODE_ID);
				}
				boolean deleted = dr.readBoolean();
				boolean created = false;
				if (!deleted) {
					created = dr.readBoolean();
				}
				if (id >= 0 && ((created && id == connection.getNodes().size()) || id < connection.getNodes().size())) {
					if (deleted) {
						connection.getNodes().remove(id);
					} else {
						Point node;
						if (created) {
							node = new Point();
							if (connection.getNodes().size() < MAX_NODES && (!manager.getWorld().isRemote || length > connection.getNodes().size())) {
								connection.getNodes().add(id, node);
							}
						} else {
							node = connection.getNodes().get(id);
						}

						node.setX(dr.readData(DataBitHelper.FLOW_CONTROL_X));
						node.setY(dr.readData(DataBitHelper.FLOW_CONTROL_Y));
					}

				}
			}
		}
	}

	public FlowComponent copy() {
		FlowComponent copy = new FlowComponent(manager, x, y, type);
		copy.id = id;
		copy.name = name;

		for (int i = 0; i < menus.size(); i++) {
			ComponentMenu menu = menus.get(i);

			copy.menus.get(i).copyFrom(menu);
		}


		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			Connection connection = connections.get(i);
			if (connection != null) {
				copy.connections.put(i, connection.copy());
			}
		}

		return copy;
	}

	public void refreshData(ContainerManager container, FlowComponent newData) {
		if (x != newData.x || y != newData.y) {
			x = newData.x;
			y = newData.y;

			DataWriter dw = PacketHandler.getWriterForClientComponentPacket(container, this, null);
			writeLocationData(dw);
			PacketHandler.sendDataToListeningClients(container, dw);
		}

		if (((parent == null) != (newData.parent == null)) || (parent != null && parent.getId() != newData.parent.getId())) {
			if (newData.parent == null) {
				setParent(null);
			} else {
				setParent(getManager().getFlowItems().get(newData.parent.getId()));
			}

			DataWriter dw = PacketHandler.getWriterForClientComponentPacket(container, this, null);
			writeParentData(dw);
			PacketHandler.sendDataToListeningClients(container, dw);
		}

		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			if (newData.connections.get(i) == null && connections.get(i) != null) {
				connections.put(i, null);
				DataWriter dw = PacketHandler.getWriterForClientComponentPacket(container, this, null);
				writeConnectionData(dw, i, false, 0, 0);
				PacketHandler.sendDataToListeningClients(container, dw);
			}

			if (newData.connections.get(i) != null && (connections.get(i) == null || newData.connections.get(i).getComponentId() != connections.get(i).getComponentId() || newData.connections.get(i).getConnectionId() != connections.get(i).getConnectionId())) {
				connections.put(i, newData.connections.get(i).copy());
				DataWriter dw = PacketHandler.getWriterForClientComponentPacket(container, this, null);
				writeConnectionData(dw, i, true, connections.get(i).getComponentId(), connections.get(i).getConnectionId());
				PacketHandler.sendDataToListeningClients(container, dw);
			}
			Connection connection    = connections.get(i);
			Connection newConnection = newData.connections.get(i);
			if (connection != null && newConnection != null) {
				boolean deleted = connection.getNodes().size() > newConnection.getNodes().size();
				boolean created = connection.getNodes().size() < newConnection.getNodes().size();

				if (deleted) {
					boolean hasDeleted = false;
					for (int j = 0; j < newConnection.getNodes().size(); j++) {
						Point node    = connection.getNodes().get(j);
						Point newNode = newConnection.getNodes().get(j);

						if (node.getX() != newNode.getX() || node.getY() != newNode.getY()) {
							sendClientConnectionNode(container, newConnection.getNodes().size(), i, j, true, false, 0, 0);
							hasDeleted = true;
							break;
						}
					}

					if (!hasDeleted) {
						sendClientConnectionNode(container, newConnection.getNodes().size(), i, newConnection.getNodes().size(), true, false, 0, 0);
					}
				} else {
					boolean updated = false;
					for (int j = 0; j < connection.getNodes().size(); j++) {
						Point node    = connection.getNodes().get(j);
						Point newNode = newConnection.getNodes().get(j);

						if (node.getX() != newNode.getX() || node.getY() != newNode.getY()) {
							updated = true;
							if (created) {
								Point nextNode = newConnection.getNodes().get(j + 1);
								if (node.getX() == nextNode.getX() && node.getY() == nextNode.getY()) {
									sendClientConnectionNode(container, newConnection.getNodes().size(), i, j, false, true, newNode.getX(), newNode.getY());
									break;
								}
							}
							sendClientConnectionNode(container, newConnection.getNodes().size(), i, j, false, false, newNode.getX(), newNode.getY());
							break;
						}
					}

					if (!updated && created) {
						int nodeId = connection.getNodes().size();
						sendClientConnectionNode(container, newConnection.getNodes().size(), i, nodeId, false, true, newConnection.getNodes().get(nodeId).getX(), newConnection.getNodes().get(nodeId).getY());
					}
				}

				connections.put(i, newConnection.copy());
			}
		}

		if ((newData.name == null && name != null) || (newData.name != null && name == null) || (newData != null && name != null && !newData.name.equals(name))) {
			name = newData.name;

			sendNameToClient(container);
		}

		for (int i = 0; i < menus.size(); i++) {
			menus.get(i).refreshData(container, newData.menus.get(i));
		}
	}

	private void writeParentData(DataWriter dw) {
		dw.writeBoolean(true); //component specific
		dw.writeBoolean(true); //location
		dw.writeBoolean(false); //parent
		if (parent != null) {
			dw.writeBoolean(true);
			dw.writeComponentId(getManager(), parent.getId());
		} else {
			dw.writeBoolean(false);
		}
	}

	private void sendClientConnectionNode(ContainerManager container, int length, int connectionId, int nodeId, boolean deleted, boolean created, int x, int y) {
		DataWriter dw = PacketHandler.getWriterForClientComponentPacket(container, this, null);
		writeConnectionNode(dw, length, connectionId, nodeId, deleted, created, x, y);
		PacketHandler.sendDataToListeningClients(container, dw);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	private void sendNameToClient(ContainerManager container) {
		DataWriter dw = PacketHandler.getWriterForClientComponentPacket(container, this, null);
		writeName(dw);
		PacketHandler.sendDataToListeningClients(container, dw);
	}

	private void writeName(DataWriter dw) {
		dw.writeBoolean(true); //component specific
		dw.writeBoolean(false); //name
		dw.writeString(name, DataBitHelper.NAME_LENGTH);
	}

	public Connection getConnection(int i) {
		return connections.get(i);
	}

	public boolean isBeingMoved() {
		return isDragging;
	}

	public void decreaseId() {
		id--;
	}

	public void updateConnectionIdsAtRemoval(int idToRemove) {
		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			Connection connection = connections.get(i);
			if (connection != null) {
				if (connection.getComponentId() == idToRemove) {
					connections.remove(i);
				} else if (connection.getComponentId() > idToRemove) {
					connection.setComponentId(connection.getComponentId() - 1);
				}
			}
		}
		if (parent != null && parent.getId() == idToRemove) {
			setParent(null);
		}
	}

	private void sendNameToServer() {
		DataWriter dw = PacketHandler.getWriterForServerComponentPacket(this, null);
		writeName(dw);
		PacketHandler.sendDataToServer(dw);
	}

	public void linkParentAfterLoad() {
		if (parentLoadId != -1) {
			setParent(getManager().getFlowItems().get(parentLoadId));
		} else {
			setParent(null);
		}
	}

	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setShort(NBT_POS_X, (short) x);
		nbtTagCompound.setShort(NBT_POS_Y, (short) y);
		nbtTagCompound.setByte(NBT_TYPE, (byte) type.getId());
		if (name != null) {
			nbtTagCompound.setString(NBT_NAME, name);
		}
		if (parent != null) {
			nbtTagCompound.setShort(NBT_PARENT, (short) parent.getId());
		}
		NBTTagList connections = new NBTTagList();
		for (int i = 0; i < connectionSet.getConnections().length; i++) {
			Connection connection = this.connections.get(i);

			if (connection != null) {
				NBTTagCompound connectionTag = new NBTTagCompound();
				connectionTag.setByte(NBT_CONNECTION_POS, (byte) i);
				connectionTag.setShort(NBT_CONNECTION_TARGET_COMPONENT, (short) connection.getComponentId());
				connectionTag.setByte(NBT_CONNECTION_TARGET_CONNECTION, (byte) connection.getConnectionId());


				NBTTagList nodes = new NBTTagList();
				for (Point point : connection.getNodes()) {
					NBTTagCompound nodeTag = new NBTTagCompound();

					nodeTag.setShort(NBT_POS_X, (short) point.getX());
					nodeTag.setShort(NBT_POS_Y, (short) point.getY());

					nodes.appendTag(nodeTag);
				}

				connectionTag.setTag(NBT_NODES, nodes);

				connections.appendTag(connectionTag);
			}
		}
		nbtTagCompound.setTag(NBT_CONNECTION, connections);

		if (type == ComponentType.TRIGGER) {
			nbtTagCompound.setShort(NBT_INTERVAL, (short) currentInterval);
		}

		NBTTagList menuTagList = new NBTTagList();
		for (int i = 0; i < menus.size(); i++) {
			ComponentMenu menu = menus.get(i);

			NBTTagCompound menuTag = new NBTTagCompound();

			menu.writeToNBT(menuTag, pickup);

			menuTagList.appendTag(menuTag);

		}
		nbtTagCompound.setTag(NBT_MENUS, menuTagList);
	}

	public boolean isOpen() {
		return isLarge;
	}

	public void setOpen(boolean b) {
		isLarge = b;
		openMenuId = -1;
	}

	public void close() {
		isLarge = false;
	}

	public void setConnection(int i, Connection connection) {
		connections.put(i, connection);
	}

	public void clearConnections() {
		connections.clear();
	}

	public String getComponentName() {
		return name;
	}

	public void setComponentName(String name) {
		this.name = name;
	}

	public FlowComponent getParent() {
		return parent;
	}

	public void setParent(FlowComponent parent) {
		if (this.parent != null) {
			if (getConnectionSet() == ConnectionSet.INPUT_NODE || getConnectionSet() == ConnectionSet.OUTPUT_NODE) {
				this.parent.childrenInputNodes.remove(this);
				this.parent.childrenOutputNodes.remove(this);
				Collections.sort(this.parent.childrenInputNodes);
				Collections.sort(this.parent.childrenOutputNodes);
			}
		}
		this.parent = parent;
		if (this.parent != null) {
			if (getConnectionSet() == ConnectionSet.INPUT_NODE && !this.parent.childrenInputNodes.contains(this)) {
				this.parent.childrenInputNodes.add(this);
				Collections.sort(this.parent.childrenInputNodes);
			} else if (getConnectionSet() == ConnectionSet.OUTPUT_NODE && !this.parent.childrenOutputNodes.contains(this)) {
				this.parent.childrenOutputNodes.add(this);
				Collections.sort(this.parent.childrenOutputNodes);
			}
		}
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		FlowComponent component = (FlowComponent) o;

		return id == component.id;
	}

	public boolean isVisible() {
		return isVisible(getManager().getSelectedComponent());
	}

	public boolean isVisible(FlowComponent selectedComponent) {
		return (selectedComponent == null && parent == null) || (parent != null && parent.equals(selectedComponent));
	}

	public List<FlowComponent> getChildrenOutputNodes() {
		return childrenOutputNodes;
	}

	public List<FlowComponent> getChildrenInputNodes() {
		return childrenInputNodes;
	}

	@Override
	public int compareTo(FlowComponent o) {
		return ((Integer) id).compareTo(o.id);
	}

	public void resetPosition() {
		resetTimer = 20;
	}

	public void setParentLoadId(int i) {
		parentLoadId = i;
	}

	public boolean isInventoryListDirty() {
		return isInventoryListDirty;
	}

	public void setInventoryListDirty(boolean inventoryListDirty) {
		isInventoryListDirty = inventoryListDirty;
	}

	public void doScroll(int scroll) {
		if (isLarge) {
			if (openMenuId != -1) {
				menus.get(openMenuId).doScroll(scroll);
			}
		}
	}

	public void onGuiClosed() {
		for (ComponentMenu menu : menus) {
			menu.onGuiClosed();
		}
	}

	public void setNameEdited(boolean b) {
		isEditing = b;
		if (b) {
			textBox.setTextAndCursor("");
		} else {
			textBox.setText(null);
		}
	}

	public void refreshEditing(String name) {
		textBox.setTextAndCursor(name);
	}

	public boolean isNameBeingEdited() {
		return isEditing;
	}

	public int getOverrideY() {
		return overrideY;
	}

	public void setOverrideY(int overrideY) {
		this.overrideY = overrideY;
	}

	public int getOverrideX() {
		return overrideX;
	}

	public void setOverrideX(int overrideX) {
		this.overrideX = overrideX;
	}

	public Map<Integer, Connection> getConnections() {
		return connections;
	}

	public int getOpenMenuId() {
		return openMenuId;
	}

	public void setOpenMenuId(int openMenuId) {
		this.openMenuId = openMenuId;
	}
}
