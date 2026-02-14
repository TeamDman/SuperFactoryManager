package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.ConnectionSet;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;


public class ComponentMenuSplit extends ComponentMenu {
	private static final int CHECK_BOX_X = 15;
	private static final String NBT_EMPTY = "Empty";
	private static final String NBT_FAIR  = "Fair";
	private static final String NBT_SPLIT = "Split";
	private static final int RADIO_X     = 5;
	private static final int RADIO_Y     = 5;
	private static final int SPACING_Y   = 15;
	private CheckBoxList    checkBoxes;
	private RadioButtonList radioButtons;
	private boolean         useEmpty;
	private boolean         useFair;

	public ComponentMenuSplit(FlowComponent parent) {
		super(parent);


		radioButtons = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				setSelectedOption(selectedOption);
				sendServerData(0);
			}
		};

		radioButtons.add(new RadioButton(RADIO_X, RADIO_Y, Localization.SEQUENTIAL));
		radioButtons.add(new RadioButton(RADIO_X, RADIO_Y + SPACING_Y, Localization.SPLIT));

		checkBoxes = new CheckBoxList();

		checkBoxes.addCheckBox(new CheckBox(Localization.FAIR_SPLIT, CHECK_BOX_X, RADIO_Y + 2 * SPACING_Y) {
			@Override
			public boolean getValue() {
				return useFair();
			}			@Override
			public void setValue(boolean val) {
				setFair(val);
			}



			@Override
			public void onUpdate() {
				sendServerData(1);
			}
		});

		checkBoxes.addCheckBox(new CheckBox(Localization.EMPTY_PINS, CHECK_BOX_X, RADIO_Y + 3 * SPACING_Y) {
			@Override
			public void setValue(boolean val) {
				setEmpty(val);
			}

			@Override
			public boolean getValue() {
				return useEmpty();
			}

			@Override
			public void onUpdate() {
				sendServerData(2);
			}
		});
	}

	private void sendServerData(int id) {
		DataWriter dw = getWriterForServerComponentPacket();
		writeData(dw, id);
		PacketHandler.sendDataToServer(dw);
	}

	private void writeData(DataWriter dw, int id) {
		dw.writeData(id, DataBitHelper.MENU_SPLIT_DATA_ID);
		switch (id) {
			case 0:
				dw.writeBoolean(useSplit());
				break;
			case 1:
				dw.writeBoolean(useFair());
				break;
			case 2:
				dw.writeBoolean(useEmpty());
				break;
		}
	}

	public boolean useSplit() {
		return radioButtons.getSelectedOption() == 1;
	}

	public boolean useFair() {
		return useFair;
	}

	public boolean useEmpty() {
		return useEmpty;
	}

	private void setFair(boolean val) {
		useFair = val;
	}

	private void setEmpty(boolean val) {
		useEmpty = val;
	}

	@Override
	public String getName() {
		return Localization.SPLIT_MENU.toString();
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		if (useSplit()) {
			checkBoxes.draw(gui, mX, mY);
		}
		radioButtons.draw(gui, mX, mY);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		if (useSplit()) {
			checkBoxes.onClick(mX, mY);
		}
		radioButtons.onClick(mX, mY, button);
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
		dw.writeBoolean(useSplit());
		if (useSplit()) {
			dw.writeBoolean(useFair());
			dw.writeBoolean(useEmpty());
		}
	}

	@Override
	public void readData(DataReader dr) {
		setSplit(dr.readBoolean());
		if (useSplit()) {
			setFair(dr.readBoolean());
			setEmpty(dr.readBoolean());
		} else {
			setFair(false);
			setEmpty(false);
		}
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		ComponentMenuSplit menuSplit = (ComponentMenuSplit) menu;
		setSplit(menuSplit.useSplit());
		setFair(menuSplit.useFair());
		setEmpty(menuSplit.useEmpty());
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		ComponentMenuSplit newDataSplit = (ComponentMenuSplit) newData;

		if (useSplit() != newDataSplit.useSplit()) {
			setSplit(newDataSplit.useSplit());

			sendClientData(container, 0);
		}

		if (useFair() != newDataSplit.useFair()) {
			setFair(newDataSplit.useFair());

			sendClientData(container, 1);
		}

		if (useEmpty() != newDataSplit.useEmpty()) {
			setEmpty(newDataSplit.useEmpty());

			sendClientData(container, 2);
		}
	}

	private void sendClientData(ContainerManager container, int id) {
		DataWriter dw = getWriterForClientComponentPacket(container);
		writeData(dw, id);
		PacketHandler.sendDataToListeningClients(container, dw);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		setSplit(nbtTagCompound.getBoolean(NBT_SPLIT));
		if (useSplit()) {
			setFair(nbtTagCompound.getBoolean(NBT_FAIR));
			setEmpty(nbtTagCompound.getBoolean(NBT_EMPTY));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setBoolean(NBT_SPLIT, useSplit());
		if (useSplit()) {
			nbtTagCompound.setBoolean(NBT_FAIR, useFair());
			nbtTagCompound.setBoolean(NBT_EMPTY, useEmpty());
		}
	}

	@Override
	public boolean isVisible() {
		return isSplitConnection(getParent());
	}

	public static boolean isSplitConnection(FlowComponent component) {
		return component.getConnectionSet() == ConnectionSet.MULTIPLE_OUTPUT_2 || component.getConnectionSet() == ConnectionSet.MULTIPLE_OUTPUT_5;
	}

	private void setSplit(boolean val) {
		radioButtons.setSelectedOption(val ? 1 : 0);
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		int id = dr.readData(DataBitHelper.MENU_SPLIT_DATA_ID);
		switch (id) {
			case 0:
				setSplit(dr.readBoolean());
				break;
			case 1:
				setFair(dr.readBoolean());
				break;
			case 2:
				setEmpty(dr.readBoolean());
				break;
		}
	}

}
