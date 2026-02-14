package vswe.superfactory.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.FuzzyMode;
import vswe.superfactory.components.internal.ItemSetting;
import vswe.superfactory.components.internal.Setting;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.util.SearchUtil;

import java.util.ArrayList;
import java.util.List;

public class ComponentMenuItem extends ComponentMenuStuff {


	private static final int           ARROW_HEIGHT   = 10;
	private static final int           ARROW_SRC_X    = 18;
	private static final int           ARROW_SRC_Y    = 20;
	private static final int           ARROW_TEXT_Y   = 40;
	private static final int           ARROW_WIDTH    = 6;
	private static final int           ARROW_X_LEFT   = 5;
	private static final int           ARROW_X_RIGHT  = 109;
	private static final int           ARROW_Y        = 37;
	private static final int           DMG_VAL_TEXT_X = 15;
	private static final int           DMG_VAL_TEXT_Y = 55;
	private              TextBoxNumber amountTextBox;
	private              TextBoxNumber damageValueTextBox;


	public ComponentMenuItem(FlowComponent parent) {
		this(parent, ItemSetting.class);
	}

	protected ComponentMenuItem(FlowComponent parent, Class<? extends Setting> settingClass) {
		super(parent, settingClass);

		if (settings.get(0).isAmountSpecific()) {
			numberTextBoxes.addTextBox(amountTextBox = new TextBoxNumber(80, 24, 3, true) {
				@Override
				public boolean isVisible() {
					return selectedSetting.isLimitedByAmount();
				}

				@Override
				public void onNumberChanged() {
					selectedSetting.setAmount(getNumber());
					writeServerData(DataTypeHeader.AMOUNT);
				}
			});
		}

		numberTextBoxes.addTextBox(damageValueTextBox = new TextBoxNumber(70, 52, 5, true) {
			@Override
			public boolean isVisible() {
				return getSelectedSetting().canChangeMetaData() && getSelectedSetting().getFuzzyMode().requiresMetaData();
			}

			@Override
			public void onNumberChanged() {
				getSelectedSetting().getItem().setItemDamage(getNumber());
				writeServerData(DataTypeHeader.META);
			}
		});
	}

	protected ItemSetting getSelectedSetting() {
		return (ItemSetting) selectedSetting;
	}

	@SideOnly(Side.CLIENT)
	public static List<String> getToolTip(ItemStack itemStack) {
		try {
			return itemStack.getTooltip(Minecraft.getMinecraft().player, ITooltipFlag.TooltipFlags.NORMAL);
		} catch (Exception ex) {
			if (itemStack.getItemDamage() == 0) {
				return new ArrayList<String>();
			} else {
				ItemStack newItem = itemStack.copy();
				newItem.setItemDamage(0);
				return getToolTip(newItem);
			}
		}
	}

	@SideOnly(Side.CLIENT)
	public static String getDisplayName(ItemStack itemStack) {
		try {
			return itemStack.getDisplayName();
		} catch (Exception ex) {
			if (itemStack.getItemDamage() == 0) {
				return "";
			} else {
				ItemStack newItem = itemStack.copy();
				newItem.setItemDamage(0);
				return getDisplayName(newItem);
			}
		}
	}

	@Override
	public String getName() {
		return Localization.ITEM_MENU.toString();
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawInfoMenuContent(GuiManager gui, int mX, int mY) {
		if (damageValueTextBox.isVisible()) {
			gui.drawString(Localization.DAMAGE_VALUE.toString(), DMG_VAL_TEXT_X, DMG_VAL_TEXT_Y, 0.7F, 0x404040);
		}

		for (int i = 0; i < 2; i++) {
			int x = i == 0 ? ARROW_X_LEFT : ARROW_X_RIGHT;
			int y = ARROW_Y;

			int srcXArrow = i;
			int srcYArrow = CollisionHelper.inBounds(x, y, ARROW_WIDTH, ARROW_HEIGHT, mX, mY) ? 1 : 0;

			gui.drawTexture(x, y, ARROW_SRC_X + srcXArrow * ARROW_WIDTH, ARROW_SRC_Y + srcYArrow * ARROW_HEIGHT, ARROW_WIDTH, ARROW_HEIGHT);
		}
		gui.drawCenteredString(getSelectedSetting().getFuzzyMode().toString(), ARROW_X_LEFT, ARROW_TEXT_Y, 0.7F, ARROW_X_RIGHT - ARROW_X_LEFT + ARROW_WIDTH, 0x404040);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawResultObject(GuiManager gui, Object obj, int x, int y) {
		gui.drawItemStack((ItemStack) obj, x, y);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void drawSettingObject(GuiManager gui, Setting setting, int x, int y) {
		drawResultObject(gui, ((ItemSetting) setting).getItem(), x, y);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected List<String> getResultObjectMouseOver(Object o) {
		return getToolTip((ItemStack) o);
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected List<String> getSettingObjectMouseOver(Setting setting) {
		return getResultObjectMouseOver(((ItemSetting) setting).getItem());
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		super.onClick(mX, mY, button);

		if (isEditing()) {
			for (int i = -1; i <= 1; i += 2) {
				int x = i == 1 ? ARROW_X_RIGHT : ARROW_X_LEFT;
				int y = ARROW_Y;


				if (CollisionHelper.inBounds(x, y, ARROW_WIDTH, ARROW_HEIGHT, mX, mY)) {
					int id = getSelectedSetting().getFuzzyMode().ordinal();
					id += i;
					if (id < 0) {
						id = FuzzyMode.values().length - 1;
					} else if (id == FuzzyMode.values().length) {
						id = 0;
					}
					getSelectedSetting().setFuzzyMode(FuzzyMode.values()[id]);
					writeServerData(DataTypeHeader.USE_FUZZY);
					break;
				}
			}

            /*if (CollisionHelper.inBounds(EDIT_ITEM_X, EDIT_ITEM_Y, ITEM_SIZE, ITEM_SIZE, mX, mY) && getSelectedSetting().getItem().hasTagCompound()) {
                getParent().getManager().specialRenderer = new NBTRenderer(getSelectedSetting().getItem().getTagCompound());
            }*/
		}
	}

	@Override
	protected void updateTextBoxes() {
		if (amountTextBox != null) {
			amountTextBox.setNumber(selectedSetting.getAmount());
		}
		damageValueTextBox.setNumber(getSelectedSetting().getItem().getItemDamage());
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		super.refreshData(container, newData);
		for (int i = 0; i < settings.size(); i++) {
			ItemSetting setting    = (ItemSetting) settings.get(i);
			ItemSetting newSetting = (ItemSetting) ((ComponentMenuStuff) newData).settings.get(i);
			if (newSetting.getFuzzyMode() != setting.getFuzzyMode()) {
				setting.setFuzzyMode(newSetting.getFuzzyMode());
				writeClientData(container, DataTypeHeader.USE_FUZZY, setting);
			}

			if (newSetting.isValid() && setting.isValid()) {
				if (newSetting.getItem().getItemDamage() != setting.getItem().getItemDamage()) {
					setting.getItem().setItemDamage(newSetting.getItem().getItemDamage());
					writeClientData(container, DataTypeHeader.META, setting);
				}

			}
		}
	}

	@Override
	protected DataBitHelper getAmountBitLength() {
		return DataBitHelper.MENU_ITEM_AMOUNT;
	}

	@Override
	protected void readSpecificHeaderData(DataReader dr, DataTypeHeader header, Setting setting) {
		ItemSetting itemSetting = (ItemSetting) setting;

		switch (header) {
			case SET_ITEM:
				int id = dr.readData(DataBitHelper.MENU_ITEM_ID);
				int dmg = dr.readData(DataBitHelper.MENU_ITEM_META);

				itemSetting.setItem(new ItemStack(Item.getItemById(id), 1, dmg));
				itemSetting.getItem().setTagCompound(dr.readNBT());

				if (isEditing()) {
					updateTextBoxes();
				}

				break;
			case USE_FUZZY:
				itemSetting.setFuzzyMode(FuzzyMode.values()[dr.readData(DataBitHelper.FUZZY_MODE)]);
				break;
			case META:
				if (setting.isValid()) {
					itemSetting.getItem().setItemDamage(dr.readData(DataBitHelper.MENU_ITEM_META));
					if (isEditing()) {
						damageValueTextBox.setNumber(itemSetting.getItem().getItemDamage());
					}
				}
				break;

		}
	}

	@Override
	protected void writeSpecificHeaderData(DataWriter dw, DataTypeHeader header, Setting setting) {
		ItemSetting itemSetting = (ItemSetting) setting;
		switch (header) {
			case SET_ITEM:
				dw.writeData(Item.getIdFromItem(itemSetting.getItem().getItem()), DataBitHelper.MENU_ITEM_ID);
				dw.writeData(itemSetting.getItem().getItemDamage(), DataBitHelper.MENU_ITEM_META);
				dw.writeNBT(itemSetting.getItem().getTagCompound());
				break;
			case USE_FUZZY:
				dw.writeData(itemSetting.getFuzzyMode().ordinal(), DataBitHelper.FUZZY_MODE);
				break;
			case META:
				dw.writeData(itemSetting.getItem().getItemDamage(), DataBitHelper.MENU_ITEM_META);
		}
	}

	@Override
	protected List updateSearch(String search, boolean showAll) {
		return SearchUtil.getSearchResults(search,showAll);
	}
}
