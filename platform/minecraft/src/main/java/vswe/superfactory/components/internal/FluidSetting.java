package vswe.superfactory.components.internal;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import vswe.superfactory.Localization;
import vswe.superfactory.components.ComponentMenuFluid;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;

import java.util.ArrayList;
import java.util.List;

public class FluidSetting extends Setting {
	private static final String NBT_FLUID_AMOUNT = "Amount";
	private static final String NBT_FLUID_NAME   = "FluidName";
	private int   amount;
	private Fluid fluid;

	public FluidSetting(int id) {
		super(id);
	}

	@Override
	public void clear() {
		super.clear();

		fluid = null;
		setDefaultAmount();
	}

	@Override
	public List<String> getMouseOver() {
		List<String> ret = new ArrayList<String>();

		if (fluid == null) {
			ret.add(Localization.NO_FLUID_SELECTED.toString());
		} else {
			ret.add(ComponentMenuFluid.getDisplayName(fluid));
		}

		ret.add("");
		ret.add(Localization.CHANGE_FLUID.toString());
		if (fluid != null) {
			ret.add(Localization.EDIT_SETTING.toString());
		}

		return ret;
	}

	@Override
	public int getDefaultAmount() {
		return 1000;
	}

	@Override
	public int getAmount() {
		return amount;
	}

	@Override
	public void setAmount(int val) {
		amount = val;
	}

	@Override
	public boolean isValid() {
		return fluid != null;
	}

	@Override
	public void writeData(DataWriter dw) {
		dw.writeString(fluid.getName(), DataBitHelper.MENU_FLUID_ID_LENGTH);
	}

	@Override
	public void readData(DataReader dr) {
		fluid = FluidRegistry.getFluid(dr.readString(DataBitHelper.MENU_FLUID_ID_LENGTH));
	}

	@Override
	public void copyFrom(Setting setting) {
		fluid = ((FluidSetting) setting).fluid;
	}

	@Override
	public void load(NBTTagCompound settingTag) {
		//TODO load properly
		fluid = FluidRegistry.getFluid(settingTag.getString(NBT_FLUID_NAME));
		amount = settingTag.getInteger(NBT_FLUID_AMOUNT);
	}

	@Override
	public void save(NBTTagCompound settingTag) {
		//TODO save properly
		settingTag.setString(NBT_FLUID_NAME, fluid.getName());
		settingTag.setInteger(NBT_FLUID_AMOUNT, amount);
	}

	@Override
	public boolean isContentEqual(Setting otherSetting) {
		return fluid.getName().equals(((FluidSetting) otherSetting).fluid.getName());
	}

	@Override
	public void setContent(Object obj) {
		fluid = (Fluid) obj;
		setDefaultAmount();
	}

	public String getFluidName() {
		return fluid.getName();
	}

	public void setFluidFromName(String name) {
		fluid = FluidRegistry.getFluid(name);
	}

	public Fluid getFluid() {
		return fluid;
	}
}
