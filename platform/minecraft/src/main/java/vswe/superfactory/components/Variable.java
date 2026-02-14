package vswe.superfactory.components;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import vswe.superfactory.components.internal.IContainerSelection;
import vswe.superfactory.interfaces.GuiManager;

import java.util.ArrayList;
import java.util.List;

public class Variable implements IContainerSelection {
	private static final String NBT_EXECUTED     = "Executed";
	private static final String NBT_SELECTION    = "Selection";
	private static final String NBT_SELECTION_ID = "Id";
	private static final int VARIABLE_SIZE  = 14;
	private static final int VARIABLE_SRC_X = 32;
	private static final int VARIABLE_SRC_Y = 130;
	private List<Integer> containers;
	private FlowComponent declaration;
	private boolean       executed;
	private int           id;

	public Variable(int id) {
		this.id = id;
		containers = new ArrayList<Integer>();
	}

	public boolean isValid() {
		return declaration != null;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void draw(GuiManager gui, int x, int y) {
		VariableColor.values()[id].applyColor();
		gui.drawTexture(x + 1, y + 1, VARIABLE_SRC_X, VARIABLE_SRC_Y, VARIABLE_SIZE, VARIABLE_SIZE);
		GlStateManager.color(1F, 1F, 1F, 1F);
	}

	@Override
	public String getDescription(GuiManager gui) {
		VariableColor color = VariableColor.values()[id];

		return color.getTextColor().toString() + getNameFromColor(color);
	}

	@Override
	public String getName(GuiManager gui) {
		return getNameFromColor(VariableColor.values()[id]);
	}

	@Override
	public boolean isVariable() {
		return true;
	}

	private String getNameFromColor(VariableColor color) {
		if (getDeclaration() == null || getDeclaration().getComponentName() == null) {
			return color.toString();
		} else {
			return getDeclaration().getComponentName();
		}

	}

	public FlowComponent getDeclaration() {
		return declaration;
	}

	public void setDeclaration(FlowComponent flowComponent) {
		if (flowComponent == null || declaration == null) {
			declaration = flowComponent;
		}
	}

	public List<Integer> getContainers() {
		return containers;
	}

	public void setContainers(List<Integer> containers) {
		this.containers = containers;
	}

	public void add(int id) {
		if (!containers.contains(id)) {
			containers.add(id);
		}
	}

	public boolean hasBeenExecuted() {
		return executed;
	}

	public void setExecuted(boolean executed) {
		this.executed = executed;
	}

	public void clearContainers() {
		containers.clear();
	}

	public void remove(int id) {
		containers.remove((Integer) id);
	}

	public void readFromNBT(NBTTagCompound nbtTagCompound) {
		executed = nbtTagCompound.getBoolean(NBT_EXECUTED);
		containers.clear();
		NBTTagList tagList = nbtTagCompound.getTagList(NBT_SELECTION, 10);

		for (int i = 0; i < tagList.tagCount(); i++) {
			NBTTagCompound selectionTag = tagList.getCompoundTagAt(i);

			containers.add((int) selectionTag.getShort(NBT_SELECTION_ID));
		}
	}

	public void writeToNBT(NBTTagCompound nbtTagCompound) {
		nbtTagCompound.setBoolean(NBT_EXECUTED, executed);

		NBTTagList tagList = new NBTTagList();

		for (int i = 0; i < containers.size(); i++) {
			NBTTagCompound selectionTag = new NBTTagCompound();

			selectionTag.setShort(NBT_SELECTION_ID, (short) (int) containers.get(i));
			tagList.appendTag(selectionTag);
		}

		nbtTagCompound.setTag(NBT_SELECTION, tagList);
	}
}
