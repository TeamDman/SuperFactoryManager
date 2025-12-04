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
import vswe.superfactory.network.packets.PacketHandler;

import java.util.List;

public abstract class ComponentMenuRedstoneSides extends ComponentMenu {
	protected static final int RADIO_BUTTON_X_LEFT  = 5;
	protected static final int RADIO_BUTTON_X_RIGHT = 65;
	protected static final int RADIO_BUTTON_Y       = 23;
	private static final int CHECKBOX_SPACING_X = 70;
	private static final int CHECKBOX_SPACING_Y = 12;
	private static final int CHECKBOX_X         = 5;
	private static final int CHECKBOX_Y         = 35;
	private static final int MENU_WIDTH    = 120;
	private static final String NBT_ACTIVE = "Selection";
	private static final String NBT_ALL    = "RequrieAll";
	private static final int TEXT_MARGIN_X = 5;
	private static final int TEXT_Y        = 5;
	protected RadioButtonList radioButtonList;
	protected int             selection;
	private   CheckBoxList    checkBoxList;
	public ComponentMenuRedstoneSides(FlowComponent parent) {
		super(parent);

		selection = 0x3F; //All selected

		checkBoxList = new CheckBoxList();

		for (int i = 0; i < EnumFacing.values().length; i++) {
			checkBoxList.addCheckBox(new CheckBoxSide(i));
		}

		radioButtonList = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				setFirstOption(selectedOption == 0);
				sendServerData(true);
			}
		};

		radioButtonList.setSelectedOption(1);

		initRadioButtons();
	}

	protected abstract void initRadioButtons();

	private void sendServerData(boolean syncRequire) {
		DataWriter dw = getWriterForServerComponentPacket();
		writeData(dw, syncRequire);
		PacketHandler.sendDataToServer(dw);
	}

	private void writeData(DataWriter dw, boolean syncRequire) {
		dw.writeBoolean(syncRequire);
		if (syncRequire) {
			dw.writeBoolean(useFirstOption());
		} else {
			dw.writeData(selection, DataBitHelper.MENU_REDSTONE_SETTING);
		}
	}

	protected boolean useFirstOption() {
		return radioButtonList.getSelectedOption() == 0;
	}

	protected void setFirstOption(boolean val) {
		radioButtonList.setSelectedOption(val ? 0 : 1);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		gui.drawSplitString(getMessage(), TEXT_MARGIN_X, TEXT_Y, MENU_WIDTH - TEXT_MARGIN_X, 0.7F, 0x404040);

		checkBoxList.draw(gui, mX, mY);
		radioButtonList.draw(gui, mX, mY);
	}

	protected abstract String getMessage();

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		checkBoxList.onClick(mX, mY);
		radioButtonList.onClick(mX, mY, button);
	}

	@Override
	public void onDrag(int mX, int mY, boolean isMenuOpen) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onRelease(int mX, int mY, boolean isMenuOpen) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void writeData(DataWriter dw) {
		dw.writeBoolean(useFirstOption());
		dw.writeData(selection, DataBitHelper.MENU_REDSTONE_SETTING);
	}

	@Override
	public void readData(DataReader dr) {
		setFirstOption(dr.readBoolean());
		selection = dr.readData(DataBitHelper.MENU_REDSTONE_SETTING);
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		ComponentMenuRedstoneSides menuRedstone = (ComponentMenuRedstoneSides) menu;

		selection = menuRedstone.selection;
		setFirstOption(menuRedstone.useFirstOption());
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		ComponentMenuRedstoneSides newDataRedstone = (ComponentMenuRedstoneSides) newData;

		if (useFirstOption() != newDataRedstone.useFirstOption()) {
			setFirstOption(newDataRedstone.useFirstOption());

			sendClientData(container, true);
		}

		if (selection != newDataRedstone.selection) {
			selection = newDataRedstone.selection;

			sendClientData(container, false);
		}
	}

	private void sendClientData(ContainerManager container, boolean syncRequire) {
		DataWriter dw = getWriterForClientComponentPacket(container);
		writeData(dw, syncRequire);
		PacketHandler.sendDataToListeningClients(container, dw);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		//Forgot to save it in earlier versions
		if (version >= 3) {
			selection = nbtTagCompound.getByte(NBT_ACTIVE);
			setFirstOption(nbtTagCompound.getBoolean(NBT_ALL));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setByte(NBT_ACTIVE, (byte) selection);
		nbtTagCompound.setBoolean(NBT_ALL, useFirstOption());
	}

	@Override
	public void addErrors(List<String> errors) {
		if (isVisible() && selection == 0) {
			errors.add(Localization.NO_REDSTONE_SIDES_ERROR.toString());
		}
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		if (dr.readBoolean()) {
			setFirstOption(dr.readBoolean());
		} else {
			selection = dr.readData(DataBitHelper.MENU_REDSTONE_SETTING);
		}
	}

	public boolean isSideRequired(int i) {
		return (selection & (1 << i)) != 0;
	}

	private class CheckBoxSide extends CheckBox {
		private int id;

		public CheckBoxSide(int id) {
			super(Localization.getDirectionLocalization(EnumFacing.byIndex(id)), CHECKBOX_X + CHECKBOX_SPACING_X * (id % 2), CHECKBOX_Y + CHECKBOX_SPACING_Y * (id / 2));

			this.id = id;
		}

		@Override
		public boolean getValue() {
			return (selection & (1 << id)) != 0;
		}

		@Override
		public void setValue(boolean val) {
			if (val) {
				selection |= 1 << id;
			} else {
				selection &= ~(1 << id);
			}
		}

		@Override
		public void onUpdate() {
			sendServerData(false);
		}
	}
}
