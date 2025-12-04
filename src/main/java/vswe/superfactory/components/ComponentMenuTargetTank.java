package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;

public class ComponentMenuTargetTank extends ComponentMenuTarget {
	private static final String NBT_FULL = "ONLY_FULL";
	private static final int RADIO_BUTTON_SPACING = 12;
	private static final int RADIO_BUTTON_X       = 36;
	private static final int RADIO_BUTTON_Y       = 45;
	private boolean[]       onlyFull = new boolean[directions.length];
	private RadioButtonList radioButtons;

	public ComponentMenuTargetTank(FlowComponent parent) {
		super(parent);

		radioButtons = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				DataWriter dw = getWriterForServerComponentPacket();
				dw.writeData(selectedDirectionId, DataBitHelper.MENU_TARGET_DIRECTION_ID);
				dw.writeData(DataTypeHeader.START_OR_TANK_DATA.getId(), DataBitHelper.MENU_TARGET_TYPE_HEADER);
				dw.writeBoolean(selectedOption == 1);
				PacketHandler.sendDataToServer(dw);
			}
		};

		radioButtons.add(new RadioButton(RADIO_BUTTON_X, RADIO_BUTTON_Y, Localization.EMPTY_TANK));
		radioButtons.add(new RadioButton(RADIO_BUTTON_X, RADIO_BUTTON_Y + RADIO_BUTTON_SPACING, Localization.FILLED_TANK));
	}

	@Override
	protected Button getSecondButton() {
		return new Button(27) {
			@Override
			protected String getLabel() {
				return useAdvancedSetting(selectedDirectionId) ? Localization.ADVANCED_MODE.toString() : Localization.SIMPLE_MODE.toString();
			}

			@Override
			protected String getMouseOverText() {
				return useAdvancedSetting(selectedDirectionId) ? Localization.SIMPLE_MODE_LONG.toString() : Localization.ADVANCED_MODE_LONG.toString();
			}

			@Override
			protected void onClicked() {
				writeData(DataTypeHeader.USE_ADVANCED_SETTING, useAdvancedSetting(selectedDirectionId) ? 0 : 1);
			}
		};
	}

	@Override
	protected void writeAdvancedSetting(DataWriter dw, int i) {
		dw.writeBoolean(onlyFull[i]);
	}

	@Override
	protected void readAdvancedSetting(DataReader dr, int i) {
		onlyFull[i] = dr.readBoolean();
	}

	@Override
	protected void resetAdvancedSetting(int i) {
		onlyFull[i] = false;
	}

	@Override
	protected void copyAdvancedSetting(ComponentMenu menu, int i) {
		ComponentMenuTargetTank menuTarget = (ComponentMenuTargetTank) menu;
		onlyFull[i] = menuTarget.onlyFull[i];
	}

	@Override
	protected void refreshAdvancedComponentData(ContainerManager container, ComponentMenu newData, int i) {
		ComponentMenuTargetTank newDataTarget = (ComponentMenuTargetTank) newData;

		if (onlyFull[i] != newDataTarget.onlyFull[i]) {
			onlyFull[i] = newDataTarget.onlyFull[i];

			DataWriter dw = getWriterForClientComponentPacket(container);
			dw.writeData(i, DataBitHelper.MENU_TARGET_DIRECTION_ID);
			dw.writeData(DataTypeHeader.START_OR_TANK_DATA.getId(), DataBitHelper.MENU_TARGET_TYPE_HEADER);
			dw.writeBoolean(onlyFull[i]);
			PacketHandler.sendDataToListeningClients(container, dw);
		}
	}

	@Override
	protected void loadAdvancedComponent(NBTTagCompound directionTag, int i) {
		onlyFull[i] = directionTag.getBoolean(NBT_FULL);
	}

	@Override
	protected void saveAdvancedComponent(NBTTagCompound directionTag, int i) {
		directionTag.setBoolean(NBT_FULL, onlyFull[i]);
	}

	@Override
	protected void refreshAdvancedComponent() {
		if (selectedDirectionId != -1) {
			radioButtons.setSelectedOption(onlyFull[selectedDirectionId] ? 1 : 0);
		}
	}

	@Override
	protected void onAdvancedClick(int mX, int mY, int button) {
		radioButtons.onClick(mX, mY, button);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawAdvancedComponent(GuiManager gui, int mX, int mY) {
		radioButtons.draw(gui, mX, mY);
	}

	@Override
	protected void readAdvancedNetworkComponent(DataReader dr, DataTypeHeader header, int i) {
		onlyFull[i] = dr.readBoolean();
		refreshAdvancedComponent();
	}

	public boolean requireEmpty(int side) {
		return !onlyFull[side];
	}


}
