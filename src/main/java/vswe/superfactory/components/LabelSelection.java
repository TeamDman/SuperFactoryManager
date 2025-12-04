package vswe.superfactory.components;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import vswe.superfactory.components.internal.IContainerSelection;
import vswe.superfactory.interfaces.GuiManager;

import java.util.ArrayList;
import java.util.List;

public class LabelSelection implements IContainerSelection {
	private static final String NBT_EXECUTED     = "Executed";
	private static final String NBT_SELECTION    = "Selection";
	private static final String NBT_SELECTION_ID = "Id";
	private static final int VARIABLE_SIZE  = 14;
	private static final int VARIABLE_SRC_X = 32;
	private static final int VARIABLE_SRC_Y = 130;
	private int           id;
    private String name;

	public LabelSelection(int id, String name) {
		this.id = id;
		this.name = name;
	}

	public boolean isValid() {
		return true;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void draw(GuiManager gui, int x, int y) {
		GlStateManager.color(0F, 0F, 0F, 1F);
		gui.drawTexture(x + 1, y + 1, VARIABLE_SRC_X, VARIABLE_SRC_Y, VARIABLE_SIZE, VARIABLE_SIZE);
		GlStateManager.color(1F, 1F, 1F, 1F);
	}

	@Override
	public String getDescription(GuiManager gui) {
		return "Label " + this.name;
	}

	@Override
	public String getName(GuiManager gui) {
		return this.name;
	}

	@Override
	public boolean isVariable() {
		return true;
	}

}
