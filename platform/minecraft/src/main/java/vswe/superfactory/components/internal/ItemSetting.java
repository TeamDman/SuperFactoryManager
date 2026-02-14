package vswe.superfactory.components.internal;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import vswe.superfactory.ItemUtils;
import vswe.superfactory.Localization;
import vswe.superfactory.components.ComponentMenuItem;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;

import java.util.ArrayList;
import java.util.List;

public class ItemSetting extends Setting {
	private static final String    NBT_SETTING_FUZZY      = "FuzzyMode";
	private static final String    NBT_SETTING_FUZZY_OLD  = "Fuzzy";
	private static final String    NBT_SETTING_ITEM_COUNT = "ItemCount";
	private static final String    NBT_SETTING_ITEM_DMG   = "ItemDamage";
	private static final String    NBT_SETTING_ITEM_ID    = "ItemId";
	private static final String    NBT_TAG                = "tag"; //must be "tag" to match the vanilla value, see ItemStack.readFromNBT
	private              int       amount;
	private              FuzzyMode fuzzyMode;
	private              ItemStack item;

	public ItemSetting(int id) {
		super(id);
	}

	@Override
	public void clear() {
		super.clear();

		fuzzyMode = FuzzyMode.PRECISE;
		item = ItemStack.EMPTY;
		amount = 1;
	}

	@Override
	public List<String> getMouseOver() {
		if (!item.isEmpty() && GuiScreen.isShiftKeyDown()) {
			return ComponentMenuItem.getToolTip(item);
		}

		List<String> ret = new ArrayList<String>();

		if (item.isEmpty()) {
			ret.add(Localization.NO_ITEM_SELECTED.toString());
		} else {
			ret.add(ComponentMenuItem.getDisplayName(item));
		}

		ret.add("");
		ret.add(Localization.CHANGE_ITEM.toString());
		if (!item.isEmpty()) {
			ret.add(Localization.EDIT_SETTING.toString());
			ret.add(Localization.FULL_DESCRIPTION.toString());
		}

		return ret;
	}

	@Override
	public int getDefaultAmount() {
		return 1;
	}

	@Override
	public int getAmount() {
		return item.isEmpty() ? 0 : amount;
	}

	@Override
	public void setAmount(int val) {
		if (!item.isEmpty()) {
			amount = val;
		}
	}

	@Override
	public boolean isValid() {
		return !item.isEmpty();
	}

	@Override
	public void writeData(DataWriter dw) {
		dw.writeData(Item.getIdFromItem(item.getItem()), DataBitHelper.MENU_ITEM_ID);
		dw.writeData(fuzzyMode.ordinal(), DataBitHelper.FUZZY_MODE);
		dw.writeData(item.getItemDamage(), DataBitHelper.MENU_ITEM_META);
		dw.writeNBT(item.getTagCompound());
	}

	@Override
	public void readData(DataReader dr) {
		int id = dr.readData(DataBitHelper.MENU_ITEM_ID);
		fuzzyMode = FuzzyMode.values()[dr.readData(DataBitHelper.FUZZY_MODE)];
		int meta = dr.readData(DataBitHelper.MENU_ITEM_META);
		item = new ItemStack(Item.getItemById(id), 1, meta);
		item.setTagCompound(dr.readNBT());
	}

	@Override
	public void copyFrom(Setting setting) {
		ItemSetting other = (ItemSetting) setting;
		item = other.item.copy();
		fuzzyMode = other.fuzzyMode;
		amount = other.amount;
	}

	@Override
	public void load(NBTTagCompound settingTag) {
		item = new ItemStack(Item.getItemById(settingTag.getShort(NBT_SETTING_ITEM_ID)), 1, settingTag.getShort(NBT_SETTING_ITEM_DMG));
		amount = settingTag.getShort(NBT_SETTING_ITEM_COUNT);

		//used to be a boolean
		if (settingTag.hasKey(NBT_SETTING_FUZZY_OLD)) {
			fuzzyMode = settingTag.getBoolean(NBT_SETTING_FUZZY_OLD) ? FuzzyMode.FUZZY : FuzzyMode.PRECISE;
		} else {
			fuzzyMode = FuzzyMode.values()[settingTag.getByte(NBT_SETTING_FUZZY)];
		}

		if (settingTag.hasKey(NBT_TAG)) {
			item.setTagCompound(settingTag.getCompoundTag(NBT_TAG));
		} else {
			item.setTagCompound(null);
		}
	}

	@Override
	public void save(NBTTagCompound settingTag) {
		settingTag.setShort(NBT_SETTING_ITEM_ID, (short) Item.getIdFromItem(item.getItem()));
		settingTag.setShort(NBT_SETTING_ITEM_COUNT, (short) amount);
		settingTag.setShort(NBT_SETTING_ITEM_DMG, (short) item.getItemDamage());
		settingTag.setByte(NBT_SETTING_FUZZY, (byte) fuzzyMode.ordinal());
		if (item.getTagCompound() != null) {
			settingTag.setTag(NBT_TAG, item.getTagCompound());
		}
	}

	@Override
	public boolean isContentEqual(Setting otherSetting) {
		return Item.getIdFromItem(item.getItem()) == Item.getIdFromItem(((ItemSetting) otherSetting).item.getItem()) && ItemStack.areItemStackTagsEqual(item, ((ItemSetting) otherSetting).item);
	}

	@Override
	public void setContent(Object obj) {
		item = ((ItemStack) obj).copy();
	}

	public FuzzyMode getFuzzyMode() {
		return fuzzyMode;
	}

	public void setFuzzyMode(FuzzyMode fuzzy) {
		this.fuzzyMode = fuzzy;
	}

	public ItemStack getItem() {
		return item.copy();
	}

	public void setItem(ItemStack item) {
		this.item = item;
	}

	public boolean isEqualForCommandExecutor(ItemStack other) {
		if (!isValid() || other.isEmpty()) {
			return false;
		} else {
			ItemStack thisItem = getActualItem();
			switch (fuzzyMode) {
				case ORE_DICTIONARY:
					return ItemUtils.isItemEqual(other, thisItem, true, true, true);
				case PRECISE:
					return Item.getIdFromItem(thisItem.getItem()) == Item.getIdFromItem(other.getItem()) && thisItem.getItemDamage() == other.getItemDamage() && ItemStack.areItemStackTagsEqual(thisItem, other);
				case NBT_FUZZY:
					return Item.getIdFromItem(thisItem.getItem()) == Item.getIdFromItem(other.getItem()) && thisItem.getItemDamage() == other.getItemDamage();
				case FUZZY:
					return Item.getIdFromItem(thisItem.getItem()) == Item.getIdFromItem(other.getItem());
				case MOD_GROUPING:
					return ModItemHelper.areItemsFromSameMod(thisItem.getItem(), other.getItem());
				case ALL:
					return true;
				default:
					return false;
			}
		}
	}

	public ItemStack getActualItem() {
		ItemStack copy = item.copy();
		copy.setCount(amount);
		return copy;
	}

	public boolean canChangeMetaData() {
		return true;
	}
}
