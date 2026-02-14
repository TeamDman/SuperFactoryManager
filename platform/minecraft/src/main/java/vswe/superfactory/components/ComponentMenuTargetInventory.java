package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;

import java.util.List;

public class ComponentMenuTargetInventory extends ComponentMenuTarget {
	private static final String NBT_END   = "EndRange";
	private static final String NBT_START = "StartRange";
	private int[] endRange   = new int[directions.length];
	private TextBoxNumber     endTextBox;

	private int[] startRange = new int[directions.length];
	private TextBoxNumber     startTextBox;
	private TextBoxNumberList textBoxes;

	public ComponentMenuTargetInventory(FlowComponent parent) {
		super(parent);

		textBoxes = new TextBoxNumberList();
		textBoxes.addTextBox(startTextBox = new TextBoxNumber(39, 49, 2, false) {
			@Override
			public void onNumberChanged() {
				if (selectedDirectionId != -1 && getParent().getManager().getWorld().isRemote) {
					writeData(DataTypeHeader.START_OR_TANK_DATA, getNumber());
				}
			}
		});
		textBoxes.addTextBox(endTextBox = new TextBoxNumber(60, 49, 2, false) {
			@Override
			public void onNumberChanged() {
				if (selectedDirectionId != -1 && getParent().getManager().getWorld().isRemote) {
					writeData(DataTypeHeader.END, getNumber());
				}
			}
		});
	}

	@Override
	protected Button getSecondButton() {
		return new Button(27) {
			@Override
			protected String getLabel() {
				return useAdvancedSetting(selectedDirectionId) ? Localization.ALL_SLOTS.toString() : Localization.ID_RANGE.toString();
			}

			@Override
			protected String getMouseOverText() {
				return useAdvancedSetting(selectedDirectionId) ? Localization.ALL_SLOTS_LONG.toString() : Localization.ID_RANGE_LONG.toString();
			}

			@Override
			protected void onClicked() {
				writeData(DataTypeHeader.USE_ADVANCED_SETTING, useAdvancedSetting(selectedDirectionId) ? 0 : 1);
			}
		};
	}

	@Override
	protected void writeAdvancedSetting(DataWriter dw, int i) {
		dw.writeData(startRange[i], DataBitHelper.MENU_TARGET_RANGE);
		dw.writeData(endRange[i], DataBitHelper.MENU_TARGET_RANGE);
	}

	@Override
	protected void readAdvancedSetting(DataReader dr, int i) {
		startRange[i] = dr.readData(DataBitHelper.MENU_TARGET_RANGE);
		endRange[i] = dr.readData(DataBitHelper.MENU_TARGET_RANGE);
	}

	@Override
	protected void resetAdvancedSetting(int i) {
		startRange[i] = endRange[i] = 0;
	}

	@Override
	protected void copyAdvancedSetting(ComponentMenu menu, int i) {
		ComponentMenuTargetInventory menuTarget = (ComponentMenuTargetInventory) menu;
		startRange[i] = menuTarget.startRange[i];
		endRange[i] = menuTarget.endRange[i];
	}

	@Override
	protected void refreshAdvancedComponentData(ContainerManager container, ComponentMenu newData, int i) {
		ComponentMenuTargetInventory newDataTarget = (ComponentMenuTargetInventory) newData;

		if (startRange[i] != newDataTarget.startRange[i]) {
			startRange[i] = newDataTarget.startRange[i];

			writeUpdatedData(container, i, DataTypeHeader.START_OR_TANK_DATA, startRange[i]);
		}

		if (endRange[i] != newDataTarget.endRange[i]) {
			endRange[i] = newDataTarget.endRange[i];

			writeUpdatedData(container, i, DataTypeHeader.END, endRange[i]);
		}
	}

	@Override
	protected void loadAdvancedComponent(NBTTagCompound directionTag, int i) {
		startRange[i] = directionTag.getByte(NBT_START);
		endRange[i] = directionTag.getByte(NBT_END);
	}

	@Override
	protected void saveAdvancedComponent(NBTTagCompound directionTag, int i) {
		directionTag.setByte(NBT_START, (byte) getStart(i));
		directionTag.setByte(NBT_END, (byte) getEnd(i));
	}

	public int getStart(int i) {
		return startRange[i];
	}

	public int getEnd(int i) {
		return endRange[i];
	}

	@Override
	public void addErrors(List<String> errors) {
		for (int i = 0; i < directions.length; i++) {
			if (isActive(i) && getStart(i) > getEnd(i)) {
				errors.add(Localization.getDirectionLocalization(EnumFacing.byIndex(i)).toString() + " " + Localization.INVALID_RANGE.toString());
			}
		}

		super.addErrors(errors);
	}

	@Override
	protected void refreshAdvancedComponent() {
		if (selectedDirectionId != -1) {
			startTextBox.setNumber(startRange[selectedDirectionId]);
			endTextBox.setNumber(endRange[selectedDirectionId]);
		}
	}

	@Override
	protected void onAdvancedClick(int mX, int mY, int button) {
		textBoxes.onClick(mX, mY, button);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawAdvancedComponent(GuiManager gui, int mX, int mY) {
		textBoxes.draw(gui, mX, mY);
	}

	@Override
	protected void readAdvancedNetworkComponent(DataReader dr, DataTypeHeader header, int i) {
		int data = dr.readData(header.getBits());
		switch (header) {
			case START_OR_TANK_DATA:
				startRange[i] = data;
				refreshAdvancedComponent();
				break;
			case END:
				endRange[i] = data;
				refreshAdvancedComponent();
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		if (selectedDirectionId != -1 && useAdvancedSetting(selectedDirectionId)) {
			return textBoxes.onKeyStroke(gui, c, k);
		}


		return false;
	}
}
