package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.Setting;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public abstract class ComponentMenuStuff extends ComponentMenu {
	protected static final int                       EDIT_ITEM_X           = 5;
	protected static final int                       EDIT_ITEM_Y           = 5;
	protected static final int                       ITEM_SIZE             = 16;
	protected static final int                       ITEM_SIZE_WITH_MARGIN = 20;
	protected static final int                       ITEM_X                = 5;
	protected static final int                       RADIO_BUTTON_X_LEFT   = 5;
	protected static final int                       RADIO_BUTTON_X_RIGHT  = 65;
	protected static final int                       RADIO_BUTTON_Y        = 5;
	private static final   int                       BACK_SIZE_H           = 9;
	private static final   int                       BACK_SIZE_W           = 9;
	private static final   int                       BACK_SRC_X            = 46;
	private static final   int                       BACK_SRC_Y            = 52;
	private static final   int                       BACK_X                = 108;
	private static final   int                       BACK_Y                = 57;
	private static final   int                       DELETE_SIZE_H         = 11;
	private static final   int                       DELETE_SIZE_W         = 32;
	private static final   int                       DELETE_SRC_X          = 0;
	private static final   int                       DELETE_SRC_Y          = 130;
	private static final   int                       DELETE_TEXT_Y         = 3;
	private static final   int                       DELETE_X              = 85;
	private static final   int                       DELETE_Y              = 3;
	private static final   String                    NBT_RADIO_SELECTION   = "FirstSelected";
	private static final   String                    NBT_SETTINGS          = "Settings";
	private static final   String                    NBT_SETTING_ID        = "Id";
	private static final   String                    NBT_SETTING_USE_SIZE  = "SizeLimit";
	private static final   int                       SETTING_SRC_X         = 0;
	private static final   int                       SETTING_SRC_Y         = 189;
	protected              CheckBoxList              checkBoxes;
	protected              TextBoxNumberList         numberTextBoxes;
	protected              RadioButtonList           radioButtons;
	protected              ScrollController          scrollControllerSearch;
	protected              ScrollController<Setting> scrollControllerSelected;
	protected              Setting                   selectedSetting;
	protected              List<Setting>             settings;
	private                boolean                   editSetting;
	private                List<Setting>             externalSettings;

	public ComponentMenuStuff(FlowComponent parent, Class<? extends Setting> settingClass) {
		super(parent);
		settings = new ArrayList<Setting>();
		externalSettings = new ArrayList<Setting>();
		for (int i = 0; i < getSettingCount(); i++) {
			try {
				Constructor<? extends Setting> constructor = settingClass.getConstructor(int.class);
				Setting                        setting     = constructor.newInstance(i);
				settings.add(setting);
				externalSettings.add(setting);
			} catch (Exception ex) {
				System.err.println("Failed to create setting");
			}

		}
		numberTextBoxes = new TextBoxNumberList();


		radioButtons = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				DataWriter dw = getWriterForServerComponentPacket();
				dw.writeBoolean(false); //no specific item
				writeRadioButtonRefreshState(dw, selectedOption == 0);
				PacketHandler.sendDataToServer(dw);
			}
		};

		initRadioButtons();

		checkBoxes = new CheckBoxList();
		if (settings.get(0).isAmountSpecific()) {
			checkBoxes.addCheckBox(new CheckBox(Localization.SPECIFY_AMOUNT, 5, 25) {
				@Override
				public boolean getValue() {
					return selectedSetting.isLimitedByAmount();
				}

				@Override
				public void setValue(boolean val) {
					selectedSetting.setLimitedByAmount(val);
				}

				@Override
				public void onUpdate() {
					writeServerData(DataTypeHeader.USE_AMOUNT);
				}
			});
		}

		final ComponentMenuStuff self = this;
		scrollControllerSearch = new ScrollController(true) {
			@Override
			protected List getSearchResults(String search, boolean all) {
				if (search.equals("")) { // Short circuit required for server to not call client only methods
					return new ArrayList();
				}

				return self.updateSearch(search, all);
			}

			@Override
			protected void onClick(Object o, int mX, int mY, int button) {
				selectedSetting.setContent(o);
				writeServerData(DataTypeHeader.SET_ITEM);
				selectedSetting = null;
				updateScrolling();
			}

			@Override
			protected void draw(GuiManager gui, Object o, int x, int y, boolean hover) {
				drawResultObject(gui, o, x, y);
			}

			@Override
			protected void drawMouseOver(GuiManager gui, Object o, int mX, int mY) {
				if (o != null) {
					gui.drawMouseOver(getResultObjectMouseOver(o), mX, mY);
				}
			}
		};

		scrollControllerSelected = new ScrollController<Setting>(false) {
			@Override
			protected List<Setting> getSearchResults(String search, boolean all) {
				return settings;
			}

			@Override
			protected void onClick(Setting setting, int mX, int mY, int button) {
				selectedSetting = setting;
				editSetting = button == 1 && doAllowEdit();


				if (editSetting && !selectedSetting.isValid()) {
					selectedSetting = null;
					editSetting = false;
				} else {
					if (editSetting) {
						updateTextBoxes();
					}
					updateScrolling();
				}
			}

			@Override
			protected void draw(GuiManager gui, Setting setting, int x, int y, boolean hover) {
				int srcSettingX = setting.isValid() ? 0 : 1;
				int srcSettingY = hover ? 1 : 0;

				gui.drawTexture(x, y, SETTING_SRC_X + srcSettingX * ITEM_SIZE, SETTING_SRC_Y + srcSettingY * ITEM_SIZE, ITEM_SIZE, ITEM_SIZE);
				if (setting.isValid()) {
					drawSettingObject(gui, setting, x, y);
				}
			}

			@Override
			protected void drawMouseOver(GuiManager gui, Setting setting, int mX, int mY) {
				if (setting.isValid()) {
					gui.drawMouseOver(getSettingObjectMouseOver(setting), mX, mY);
				}
			}
		};
	}

	protected void initRadioButtons() {
		radioButtons.add(new RadioButton(RADIO_BUTTON_X_LEFT, RADIO_BUTTON_Y, Localization.WHITE_LIST));
		radioButtons.add(new RadioButton(RADIO_BUTTON_X_RIGHT, RADIO_BUTTON_Y, Localization.BLACK_LIST));
	}

	protected int getSettingCount() {
		return 30;
	}

	protected boolean doAllowEdit() {
		return true;
	}

	@SideOnly(Side.CLIENT)
	protected abstract void drawResultObject(GuiManager gui, Object obj, int x, int y);

	@SideOnly(Side.CLIENT)
	protected abstract void drawSettingObject(GuiManager gui, Setting setting, int x, int y);

	@SideOnly(Side.CLIENT)
	protected abstract List<String> getResultObjectMouseOver(Object obj);

	@SideOnly(Side.CLIENT)
	protected abstract List<String> getSettingObjectMouseOver(Setting setting);

	protected abstract void updateTextBoxes();

	protected void writeRadioButtonRefreshState(DataWriter dw, boolean value) {
		dw.writeBoolean(value);
	}

	protected void writeServerData(DataTypeHeader header) {
		writeServerData(header, selectedSetting);
	}

	protected void writeServerData(DataTypeHeader header, Setting setting) {
		DataWriter dw = getWriterForServerComponentPacket();
		writeData(dw, header, setting);
		PacketHandler.sendDataToServer(dw);
	}

	private void writeData(DataWriter dw, DataTypeHeader header, Setting setting) {
		dw.writeBoolean(true); //specific setting is being used
		dw.writeData(setting.getId(), DataBitHelper.MENU_ITEM_SETTING_ID);
		dw.writeData(header.id, DataBitHelper.MENU_ITEM_TYPE_HEADER);

		switch (header) {
			case CLEAR:
				break;
			case USE_AMOUNT:
				if (setting.isAmountSpecific()) {
					dw.writeBoolean(setting.isLimitedByAmount());
				}
				break;
			case AMOUNT:
				if (setting.isAmountSpecific()) {
					dw.writeData(setting.getAmount(), getAmountBitLength());
				}
				break;
			default:
				writeSpecificHeaderData(dw, header, setting);

		}

		//if the client send data to the server, do the update right away on that client
		if (getParent().getManager().getWorld().isRemote) {
			onSettingContentChange();
		}
	}

	protected abstract DataBitHelper getAmountBitLength();

	protected abstract void writeSpecificHeaderData(DataWriter dw, DataTypeHeader header, Setting setting);

	protected void onSettingContentChange() {

	}

	@SideOnly(Side.CLIENT)
	protected abstract List updateSearch(String search, boolean showAll);

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		if (isEditing()) {
			checkBoxes.draw(gui, mX, mY);

			drawSettingObject(gui, selectedSetting, EDIT_ITEM_X, EDIT_ITEM_Y);

			numberTextBoxes.draw(gui, mX, mY);

			drawInfoMenuContent(gui, mX, mY);

			int srcDeleteY = inDeleteBounds(mX, mY) ? 1 : 0;
			gui.drawTexture(DELETE_X, DELETE_Y, DELETE_SRC_X, DELETE_SRC_Y + srcDeleteY * DELETE_SIZE_H, DELETE_SIZE_W, DELETE_SIZE_H);
			gui.drawCenteredString(Localization.DELETE.toString(), DELETE_X, DELETE_Y + DELETE_TEXT_Y, 0.7F, DELETE_SIZE_W, 0xBB4040);
		} else {
			if (!isSearching()) {
				radioButtons.draw(gui, mX, mY);
			}
			if (isListVisible()) {
				getScrollingList().draw(gui, mX, mY);
			}
		}

		if (selectedSetting != null) {
			int srcBackX = inBackBounds(mX, mY) ? 1 : 0;

			gui.drawTexture(BACK_X, BACK_Y, BACK_SRC_X + srcBackX * BACK_SIZE_W, BACK_SRC_Y, BACK_SIZE_W, BACK_SIZE_H);
		}
	}

	protected boolean isListVisible() {
		return true;
	}

	@SideOnly(Side.CLIENT)
	protected abstract void drawInfoMenuContent(GuiManager gui, int mX, int mY);

	private boolean inBackBounds(int mX, int mY) {
		return CollisionHelper.inBounds(BACK_X, BACK_Y, BACK_SIZE_W, BACK_SIZE_H, mX, mY);
	}

	private boolean inDeleteBounds(int mX, int mY) {
		return CollisionHelper.inBounds(DELETE_X, DELETE_Y, DELETE_SIZE_W, DELETE_SIZE_H, mX, mY);
	}

	private ScrollController getScrollingList() {
		return isSearching() ? scrollControllerSearch : scrollControllerSelected;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		if (isEditing()) {
			if (CollisionHelper.inBounds(EDIT_ITEM_X, EDIT_ITEM_Y, ITEM_SIZE, ITEM_SIZE, mX, mY)) {
				scrollControllerSelected.drawMouseOver(gui, selectedSetting, mX, mY);
			} else if (inDeleteBounds(mX, mY)) {
				gui.drawMouseOver(Localization.DELETE_ITEM_SELECTION.toString(), mX, mY);
			}
		} else if (isListVisible()) {
			getScrollingList().drawMouseOver(gui, mX, mY);
		}


		if (selectedSetting != null && inBackBounds(mX, mY)) {
			gui.drawMouseOver(isEditing() ? Localization.GO_BACK.toString() : Localization.CANCEL.toString(), mX, mY);
		}
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		if (isEditing()) {
			checkBoxes.onClick(mX, mY);

			numberTextBoxes.onClick(mX, mY, button);

			if (inDeleteBounds(mX, mY)) {
				selectedSetting.delete();
				writeServerData(DataTypeHeader.CLEAR);
				selectedSetting = null;
				getScrollingList().updateScrolling();
			}
		} else {
			if (!isSearching()) {
				radioButtons.onClick(mX, mY, button);
			}
			if (isListVisible()) {
				getScrollingList().onClick(mX, mY, button);
			}
		}

		if (selectedSetting != null && inBackBounds(mX, mY)) {
			selectedSetting = null;
			getScrollingList().updateScrolling();
		}
	}

	@Override
	public void onDrag(int mX, int mY, boolean isMenuOpen) {

	}

	@Override
	public void onRelease(int mX, int mY, boolean isMenuOpen) {
		getScrollingList().onRelease(mX, mY);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		return isSearching() && getScrollingList().onKeyStroke(gui, c, k) || isEditing() && numberTextBoxes.onKeyStroke(gui, c, k);
	}

	@Override
	public void writeData(DataWriter dw) {
		dw.writeBoolean(isFirstRadioButtonSelected());
		for (Setting setting : settings) {
			dw.writeBoolean(setting.isValid());
			if (setting.isValid()) {
				setting.writeData(dw);
				if (setting.isAmountSpecific()) {
					dw.writeBoolean(setting.isLimitedByAmount());
					if (setting.isLimitedByAmount()) {
						dw.writeData(setting.getAmount(), getAmountBitLength());
					}
				}
			}
		}
	}

	@Override
	public void readData(DataReader dr) {
		setFirstRadioButtonSelected(dr.readBoolean());
		for (Setting setting : settings) {
			if (!dr.readBoolean()) {
				setting.clear();
			} else {
				setting.readData(dr);
				if (setting.isAmountSpecific()) {
					setting.setLimitedByAmount(dr.readBoolean());

					if (setting.isLimitedByAmount()) {
						setting.setAmount(dr.readData(getAmountBitLength()));
					} else {
						setting.setDefaultAmount();
					}
				}
			}
		}

		onSettingContentChange();
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		ComponentMenuStuff menuItem = (ComponentMenuStuff) menu;

		setFirstRadioButtonSelected(menuItem.isFirstRadioButtonSelected());

		for (int i = 0; i < settings.size(); i++) {
			if (!menuItem.settings.get(i).isValid()) {
				settings.get(i).clear();
			} else {
				settings.get(i).copyFrom(menuItem.settings.get(i));
				if (settings.get(i).isAmountSpecific()) {
					settings.get(i).setLimitedByAmount(menuItem.settings.get(i).isLimitedByAmount());
					settings.get(i).setAmount(menuItem.settings.get(i).getAmount());
				}
			}
		}
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		if (((ComponentMenuStuff) newData).isFirstRadioButtonSelected() != isFirstRadioButtonSelected()) {
			setFirstRadioButtonSelected(((ComponentMenuStuff) newData).isFirstRadioButtonSelected());

			DataWriter dw = getWriterForClientComponentPacket(container);
			dw.writeBoolean(false); //no specific setting
			writeRadioButtonRefreshState(dw, isFirstRadioButtonSelected());
			PacketHandler.sendDataToListeningClients(container, dw);
		}

		for (int i = 0; i < settings.size(); i++) {
			Setting setting    = settings.get(i);
			Setting newSetting = ((ComponentMenuStuff) newData).settings.get(i);

			if (!newSetting.isValid() && setting.isValid()) {
				setting.clear();
				writeClientData(container, DataTypeHeader.CLEAR, setting);
			}

			if (newSetting.isValid() && (!setting.isValid() || !setting.isContentEqual(newSetting))) {
				setting.copyFrom(newSetting);
				writeClientData(container, DataTypeHeader.SET_ITEM, setting);
			}

			if (setting.isAmountSpecific()) {
				if (newSetting.isLimitedByAmount() != setting.isLimitedByAmount()) {
					setting.setLimitedByAmount(newSetting.isLimitedByAmount());
					writeClientData(container, DataTypeHeader.USE_AMOUNT, setting);
				}

				if (newSetting.isValid() && setting.isValid()) {
					if (newSetting.getAmount() != setting.getAmount()) {
						setting.setAmount(newSetting.getAmount());
						writeClientData(container, DataTypeHeader.AMOUNT, setting);
					}
				}
			}
		}
	}

	protected void writeClientData(ContainerManager container, DataTypeHeader header, Setting setting) {
		DataWriter dw = getWriterForClientComponentPacket(container);
		writeData(dw, header, setting);
		PacketHandler.sendDataToListeningClients(container, dw);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		setFirstRadioButtonSelected(nbtTagCompound.getBoolean(NBT_RADIO_SELECTION));

		NBTTagList settingTagList = nbtTagCompound.getTagList(NBT_SETTINGS, 10);
		for (int i = 0; i < settingTagList.tagCount(); i++) {
			NBTTagCompound settingTag = settingTagList.getCompoundTagAt(i);
			Setting        setting    = settings.get(settingTag.getByte(NBT_SETTING_ID));
			setting.load(settingTag);
			if (setting.isAmountSpecific()) {
				setting.setLimitedByAmount(settingTag.getBoolean(NBT_SETTING_USE_SIZE));
			}
		}

		onSettingContentChange();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setBoolean(NBT_RADIO_SELECTION, isFirstRadioButtonSelected());

		NBTTagList settingTagList = new NBTTagList();
		for (int i = 0; i < settings.size(); i++) {
			Setting setting = settings.get(i);
			if (setting.isValid()) {
				NBTTagCompound settingTag = new NBTTagCompound();
				settingTag.setByte(NBT_SETTING_ID, (byte) setting.getId());
				setting.save(settingTag);
				if (setting.isAmountSpecific()) {
					settingTag.setBoolean(NBT_SETTING_USE_SIZE, setting.isLimitedByAmount());
				}
				settingTagList.appendTag(settingTag);
			}
		}
		nbtTagCompound.setTag(NBT_SETTINGS, settingTagList);
	}

	@Override
	public void addErrors(List<String> errors) {
		if (useWhiteList()) {
			for (Setting setting : settings) {
				if (setting.isValid()) {
					return;
				}
			}
			errors.add(Localization.EMPTY_WHITE_LIST_ERROR.toString());
		}
	}

	@Override
	public void update(float partial) {
		if (isSearching()) {
			scrollControllerSearch.update(partial);
		} else if (!isSearching() && !isEditing()) {
			scrollControllerSelected.update(partial);
		}
	}

	@Override
	public void doScroll(int scroll) {
		if (isSearching()) {
			scrollControllerSearch.doScroll(scroll);
		} else if (!isSearching() && !isEditing()) {
			scrollControllerSelected.doScroll(scroll);
		}
	}

	protected boolean isEditing() {
		return selectedSetting != null && editSetting;
	}

	protected boolean isSearching() {
		return selectedSetting != null && !editSetting;
	}

	public boolean useWhiteList() {
		return isFirstRadioButtonSelected();
	}

	protected boolean isFirstRadioButtonSelected() {
		return radioButtons.getSelectedOption() == 0;
	}

	protected void setFirstRadioButtonSelected(boolean value) {
		radioButtons.setSelectedOption(value ? 0 : 1);
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		boolean useSetting = dr.readBoolean();

		if (useSetting) {
			int            settingId = dr.readData(DataBitHelper.MENU_ITEM_SETTING_ID);
			Setting        setting   = settings.get(settingId);
			int            headerId  = dr.readData(DataBitHelper.MENU_ITEM_TYPE_HEADER);
			DataTypeHeader header    = getHeaderFromId(headerId);

			switch (header) {
				case CLEAR:
					setting.clear();
					selectedSetting = null;
					break;
				case USE_AMOUNT:
					if (setting.isAmountSpecific()) {
						setting.setLimitedByAmount(dr.readBoolean());
						if (!setting.isLimitedByAmount() && setting.isValid()) {
							setting.setDefaultAmount();
						}
					}
					break;
				case AMOUNT:
					if (setting.isAmountSpecific() && setting.isValid()) {
						setting.setAmount(dr.readData(getAmountBitLength()));
						if (isEditing()) {
							updateTextBoxes();
						}
					}
					break;
				default:
					readSpecificHeaderData(dr, header, setting);

			}

			onSettingContentChange();
		} else {
			readNonSettingData(dr);
		}
	}

	protected void readNonSettingData(DataReader dr) {
		setFirstRadioButtonSelected(dr.readBoolean());
	}

	protected abstract void readSpecificHeaderData(DataReader dr, DataTypeHeader header, Setting setting);

	private DataTypeHeader getHeaderFromId(int id) {
		for (DataTypeHeader header : DataTypeHeader.values()) {
			if (id == header.id) {
				return header;
			}
		}
		return null;
	}

	public List<Setting> getSettings() {
		return externalSettings;
	}

	public void setBlackList() {
		setFirstRadioButtonSelected(false);
	}

	protected enum DataTypeHeader {
		CLEAR(0),
		SET_ITEM(1),
		USE_AMOUNT(2),
		USE_FUZZY(3),
		AMOUNT(4),
		META(5);

		private int id;

		DataTypeHeader(int header) {
			this.id = header;
		}
	}
}


