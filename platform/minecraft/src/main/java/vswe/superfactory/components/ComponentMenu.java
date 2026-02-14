package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.IComponentNetworkReader;
import vswe.superfactory.network.packets.PacketHandler;

import java.util.List;

public abstract class ComponentMenu implements IComponentNetworkReader {
	private int           id;
	private FlowComponent parent;

	public ComponentMenu(FlowComponent parent) {
		this.parent = parent;
		id = parent.getMenus().size();
	}

	public abstract String getName();

	@SideOnly(Side.CLIENT)
	public abstract void draw(GuiManager gui, int mX, int mY);

	@SideOnly(Side.CLIENT)
	public abstract void drawMouseOver(GuiManager gui, int mX, int mY);

	public abstract void onClick(int mX, int mY, int button);

	public abstract void onDrag(int mX, int mY, boolean isMenuOpen);

	public abstract void onRelease(int mX, int mY, boolean isMenuOpen);

	@SideOnly(Side.CLIENT)
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		return false;
	}

	public abstract void writeData(DataWriter dw);

	public abstract void readData(DataReader dr);

	protected DataWriter getWriterForServerComponentPacket() {
		return PacketHandler.getWriterForServerComponentPacket(getParent(), this);
	}

	public FlowComponent getParent() {
		return parent;
	}

	protected DataWriter getWriterForClientComponentPacket(ContainerManager container) {
		return PacketHandler.getWriterForClientComponentPacket(container, getParent(), this);
	}

	public abstract void copyFrom(ComponentMenu menu);

	public abstract void refreshData(ContainerManager container, ComponentMenu newData);

	public int getId() {
		return id;
	}

	public abstract void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup);

	public abstract void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup);

	public void addErrors(List<String> errors) {
	}

	public boolean isVisible() {
		return true;
	}

	public void update(float partial) {
	}

	public void doScroll(int scroll) {
	}

	public void onGuiClosed() {
	}
}
