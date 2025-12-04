package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;

public class ComponentMenuCraftingPriority extends ComponentMenu {
	private static final String NBT_SELECTED = "SelectedOption";
	private static final int RADIO_MARGIN = 13;
	private static final int RADIO_X      = 5;
	private static final int RADIO_Y      = 5;
	private RadioButtonList radioButtons;

	public ComponentMenuCraftingPriority(FlowComponent parent) {
		super(parent);

		radioButtons = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				DataWriter dw = getWriterForServerComponentPacket();
				dw.writeBoolean(selectedOption == 0);
				PacketHandler.sendDataToServer(dw);
			}
		};


		radioButtons.add(new RadioButton(RADIO_X, RADIO_Y, Localization.PRIORITY_MOVE_FIRST));
		radioButtons.add(new RadioButton(RADIO_X, RADIO_Y + RADIO_MARGIN, Localization.PRIORITY_CRAFT_FIRST));
	}

	@Override
	public String getName() {
		return "Priority";
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		radioButtons.draw(gui, mX, mY);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {

	}

	@Override
	public void onClick(int mX, int mY, int button) {
		radioButtons.onClick(mX, mY, button);
	}

	@Override
	public void onDrag(int mX, int mY, boolean isMenuOpen) {

	}

	@Override
	public void onRelease(int mX, int mY, boolean isMenuOpen) {

	}

	@Override
	public void writeData(DataWriter dw) {
		dw.writeBoolean(radioButtons.getSelectedOption() == 0);
	}

	@Override
	public void readData(DataReader dr) {
		radioButtons.setSelectedOption(dr.readBoolean() ? 0 : 1);
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		radioButtons.setSelectedOption(((ComponentMenuCraftingPriority) menu).radioButtons.getSelectedOption());
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		ComponentMenuCraftingPriority newDataPriority = ((ComponentMenuCraftingPriority) newData);

		if (radioButtons.getSelectedOption() != newDataPriority.radioButtons.getSelectedOption()) {
			radioButtons.setSelectedOption(newDataPriority.radioButtons.getSelectedOption());

			DataWriter dw = getWriterForClientComponentPacket(container);
			dw.writeBoolean(radioButtons.getSelectedOption() == 0);
			PacketHandler.sendDataToListeningClients(container, dw);
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		radioButtons.setSelectedOption(nbtTagCompound.getByte(NBT_SELECTED));
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setByte(NBT_SELECTED, (byte) radioButtons.getSelectedOption());
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		radioButtons.setSelectedOption(dr.readBoolean() ? 0 : 1);
	}

	public boolean shouldPrioritizeCrafting() {
		return radioButtons.getSelectedOption() == 1;
	}

	public void setPrioritizeCrafting(boolean val) {
		radioButtons.setSelectedOption(val ? 1 : 0);
	}
}
