package vswe.superfactory.components;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.FluidSetting;
import vswe.superfactory.components.internal.Setting;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ComponentMenuFluid extends ComponentMenuStuff {
	private TextBoxNumber amountTextBoxBuckets;
	private TextBoxNumber amountTextBoxMilli;

	public ComponentMenuFluid(FlowComponent parent) {
		super(parent, FluidSetting.class);

		numberTextBoxes.addTextBox(amountTextBoxBuckets = new TextBoxNumber(10, 50, 3, true) {
			@Override
			public boolean isVisible() {
				return selectedSetting.isLimitedByAmount();
			}

			@Override
			public void onNumberChanged() {
				sendAmountData();
			}
		});

		numberTextBoxes.addTextBox(amountTextBoxMilli = new TextBoxNumber(60, 50, 3, true) {
			@Override
			public boolean isVisible() {
				return selectedSetting.isLimitedByAmount();
			}

			@Override
			public void onNumberChanged() {
				sendAmountData();
			}
		});
	}

	private void sendAmountData() {
		selectedSetting.setAmount(amountTextBoxBuckets.getNumber() * 1000 + amountTextBoxMilli.getNumber());
		writeServerData(DataTypeHeader.AMOUNT);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawInfoMenuContent(GuiManager gui, int mX, int mY) {
		if (selectedSetting.isLimitedByAmount()) {
			gui.drawCenteredString(Localization.BUCKETS.toString(), amountTextBoxBuckets.getX(), amountTextBoxBuckets.getY() - 7, 0.7F, amountTextBoxBuckets.getWidth(), 0x404040);
			gui.drawCenteredString(Localization.MILLI_BUCKETS.toString(), amountTextBoxMilli.getX(), amountTextBoxMilli.getY() - 7, 0.55F, amountTextBoxMilli.getWidth(), 0x404040);
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawResultObject(GuiManager gui, Object obj, int x, int y) {
		gui.drawFluid((Fluid) obj, x, y);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawSettingObject(GuiManager gui, Setting setting, int x, int y) {
		drawResultObject(gui, ((FluidSetting) setting).getFluid(), x, y);
	}

	@Override
	protected List<String> getResultObjectMouseOver(Object obj) {
		List<String> ret = new ArrayList<String>();
		ret.add(getDisplayName((Fluid) obj));
		return ret;
	}

	@Override
	protected List<String> getSettingObjectMouseOver(Setting setting) {
		return getResultObjectMouseOver(((FluidSetting) setting).getFluid());
	}

	@Override
	protected void updateTextBoxes() {
		int amount = selectedSetting.getAmount();
		amountTextBoxBuckets.setNumber(amount / 1000);
		amountTextBoxMilli.setNumber(amount % 1000);
	}

	@Override
	protected DataBitHelper getAmountBitLength() {
		return DataBitHelper.MENU_FLUID_AMOUNT;
	}

	@Override
	protected void readSpecificHeaderData(DataReader dr, DataTypeHeader header, Setting setting) {
		FluidSetting fluidSetting = (FluidSetting) setting;

		switch (header) {
			case SET_ITEM:
				fluidSetting.setFluidFromName(dr.readString(DataBitHelper.MENU_FLUID_ID_LENGTH));

				if (isEditing()) {
					updateTextBoxes();
				}


		}
	}

	@Override
	protected void writeSpecificHeaderData(DataWriter dw, DataTypeHeader header, Setting setting) {
		FluidSetting fluidSetting = (FluidSetting) setting;
		switch (header) {
			case SET_ITEM:
				dw.writeString(fluidSetting.getFluidName(), DataBitHelper.MENU_FLUID_ID_LENGTH);
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected List updateSearch(String search, boolean showAll) {
		List ret = new ArrayList(FluidRegistry.getRegisteredFluids().values());

		Iterator<Fluid> itemIterator = ret.iterator();

		if (!showAll) {
			while (itemIterator.hasNext()) {

				Fluid fluid = itemIterator.next();

				if (!getDisplayName(fluid).toLowerCase().contains(search)) {
					itemIterator.remove();
				}
			}
		}

		return ret;
	}

	public static String getDisplayName(Fluid fluid) {
		//different mods store the name in different ways apparently
		String name = fluid.getLocalizedName(new FluidStack(fluid, 1));
		if (name.indexOf(".") != -1) {
			name = FluidRegistry.getFluidName(fluid);
		}

		return name;
	}

	@Override
	public String getName() {
		return Localization.FLUIDS_MENU.toString();
	}

	protected FluidSetting getSelectedSetting() {
		return (FluidSetting) selectedSetting;
	}
}
