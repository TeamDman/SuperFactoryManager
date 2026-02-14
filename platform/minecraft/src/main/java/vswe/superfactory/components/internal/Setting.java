package vswe.superfactory.components.internal;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;

import java.util.List;

public abstract class Setting {
	private int id;

	private boolean isLimitedByAmount;

	public Setting(int id) {
		this.id = id;
		clear();
	}

	public void clear() {
		isLimitedByAmount = false;
	}

	@SideOnly(Side.CLIENT)
	public abstract List<String> getMouseOver();

	public int getId() {
		return id;
	}

	public boolean isLimitedByAmount() {
		return isLimitedByAmount;
	}

	public void setLimitedByAmount(boolean limitedByAmount) {
		isLimitedByAmount = limitedByAmount;
	}

	public void setDefaultAmount() {
		setAmount(getDefaultAmount());
	}

	public abstract int getDefaultAmount();

	public abstract int getAmount();

	public abstract void setAmount(int val);

	public abstract boolean isValid();

	public abstract void writeData(DataWriter dw);

	public abstract void readData(DataReader dr);

	public abstract void copyFrom(Setting setting);

	public abstract void load(NBTTagCompound settingTag);

	public abstract void save(NBTTagCompound settingTag);

	public abstract boolean isContentEqual(Setting otherSetting);

	public abstract void setContent(Object obj);

	public boolean isAmountSpecific() {
		return true;
	}

	public void delete() {
		clear();
	}
}