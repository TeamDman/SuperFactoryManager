package vswe.superfactory.components;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.ConnectionSet;
import vswe.superfactory.components.internal.ItemSetting;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;

import java.util.List;

public class ComponentMenuUpdateBlock extends ComponentMenuItem {
	private static final int CHECKBOX_OFFSET = 2;
	private static final int ID_START_X  = 1;
	private static final int ID_START_Y  = 1;
	private static final int ID_TEXT_BOX = 48;
	private static final int META_BITS              = 4;
	private static final int META_INVERTED_OFFSET   = 83;
	private static final int META_SETTINGS          = 3;
	private static final int META_SPACING           = 17;
	private static final int META_START_X           = 1;
	private static final int META_START_Y           = 21;
	private static final int META_TEXT_BOX_OFFSET_1 = 37;
	private static final int META_TEXT_BOX_OFFSET_2 = 58;
	private static final int META_TEXT_X            = 3;
	private static final int META_TEXT_Y            = 17;
	private static final String NBT_BITS     = "Bits";
	private static final String NBT_HIGH     = "High";
	private static final String NBT_ID       = "BlockId";
	private static final String NBT_INVERTED = "Inverted";
	private static final String NBT_LOW      = "Low";
	private static final String NBT_SETTINGS = "Meta";
	private static final String NBT_USE_ID   = "UseId";
	private CheckBoxList      checkBoxes;
	private boolean       idInverted;
	private MetaSetting[] settings;
	private TextBoxNumberList textBoxes;
	private boolean       useId;


	public ComponentMenuUpdateBlock(FlowComponent parent) {
		super(parent);

		settings = new MetaSetting[META_SETTINGS];

		textBoxes = new TextBoxNumberList();

		checkBoxes = new CheckBoxList();

		scrollControllerSelected.setItemsPerRow(1);
		scrollControllerSelected.setVisibleRows(1);
		scrollControllerSelected.setItemUpperLimit(-2);
		scrollControllerSelected.setX(ID_START_X + ID_TEXT_BOX + 10);


		checkBoxes.addCheckBox(new CheckBox(Localization.USE_ID, ID_START_X, ID_START_Y + CHECKBOX_OFFSET) {
			@Override
			public boolean getValue() {
				return useId;
			}			@Override
			public void setValue(boolean val) {
				useId = val;
			}



			@Override
			public void onUpdate() {
				sendServerData(0, 0);
			}
		});

        /*textBoxes.addTextBox(textBoxId = new TextBoxNumber(ID_START_X + ID_TEXT_BOX, ID_START_Y, 4, true) {
            @Override
            public int getMaxNumber() {
                return 4095;
            }

            @Override
            public void onNumberChanged() {
                sendServerData(0, 1);
            }

            @Override
            public boolean isVisible() {
                return useId;
            }
        });*/

		checkBoxes.addCheckBox(new CheckBox(Localization.INVERT, ID_START_X + META_INVERTED_OFFSET, ID_START_Y + CHECKBOX_OFFSET) {
			@Override
			public void setValue(boolean val) {
				idInverted = val;
			}

			@Override
			public boolean getValue() {
				return idInverted;
			}

			@Override
			public void onUpdate() {
				sendServerData(0, 2);
			}

			@Override
			public boolean isVisible() {
				return useId;
			}
		});

		for (int i = 0; i < META_SETTINGS; i++) {
			final int setting = i;
			settings[setting] = new MetaSetting();
			for (int j = 0; j < settings[setting].bits.length; j++) {
				final int bit = j;
				checkBoxes.addCheckBox(new CheckBox(null, META_START_X + (settings[setting].bits.length - (bit + 1)) * CheckBoxList.CHECK_BOX_SIZE, META_START_Y + CHECKBOX_OFFSET + setting * META_SPACING) {
					@Override
					public void setValue(boolean val) {
						settings[setting].bits[bit] = val;
						if (!val) {
							settings[setting].lowerTextBox.setNumber(settings[setting].lowerTextBox.getNumber());
							settings[setting].higherTextBox.setNumber(settings[setting].higherTextBox.getNumber());
						}
					}

					@Override
					public boolean getValue() {
						return settings[setting].bits[bit];
					}

					@Override
					public void onUpdate() {
						sendServerData(setting + 1, bit);
					}
				});

				settings[setting].bits[bit] = setting == 0;
			}

			textBoxes.addTextBox(settings[setting].lowerTextBox = new TextBoxNumber(META_START_X + META_TEXT_BOX_OFFSET_1, META_START_Y + setting * META_SPACING, 2, false) {
				@Override
				public int getMaxNumber() {
					return settings[setting].getMaxNumber();
				}

				@Override
				public void onNumberChanged() {
					sendServerData(setting + 1, 4);
				}

				@Override
				public boolean isVisible() {
					return settings[setting].inUse();
				}
			});

			textBoxes.addTextBox(settings[setting].higherTextBox = new TextBoxNumber(META_START_X + META_TEXT_BOX_OFFSET_2, META_START_Y + setting * META_SPACING, 2, false) {
				@Override
				public int getMaxNumber() {
					return settings[setting].getMaxNumber();
				}

				@Override
				public void onNumberChanged() {
					sendServerData(setting + 1, 5);
				}

				@Override
				public boolean isVisible() {
					return settings[setting].inUse();
				}
			});

			checkBoxes.addCheckBox(new CheckBox(Localization.INVERT, META_START_X + META_INVERTED_OFFSET, META_START_Y + CHECKBOX_OFFSET + setting * META_SPACING) {
				@Override
				public void setValue(boolean val) {
					settings[setting].inverted = val;
				}

				@Override
				public boolean getValue() {
					return settings[setting].inverted;
				}

				@Override
				public void onUpdate() {
					sendServerData(setting + 1, 6);
				}

				@Override
				public boolean isVisible() {
					return settings[setting].inUse();
				}
			});

			settings[setting].higherTextBox.setNumber(15);
		}


	}

	private void sendServerData(int id, int subId) {
		DataWriter dw = getWriterForServerComponentPacket();
		writeData(dw, id, subId);
		PacketHandler.sendDataToServer(dw);
	}

	private void writeData(DataWriter dw, int id, int subId) {
		dw.writeBoolean(false); //no setting specific
		dw.writeBoolean(true); //other data
		dw.writeData(id, DataBitHelper.BUD_SYNC_TYPE);
		dw.writeData(subId, id == 0 ? DataBitHelper.BUD_SYNC_SUB_TYPE_SHORT : DataBitHelper.BUD_SYNC_SUB_TYPE_LONG);

		if (id == 0) {
			if (subId == 0) {
				dw.writeBoolean(useId);
			} else if (subId == 2) {
				dw.writeBoolean(idInverted);
			}
		} else {
			id--;
			MetaSetting setting = settings[id];
			if (subId < 4) {
				dw.writeBoolean(setting.bits[subId]);
			} else if (subId == 4) {
				dw.writeData(setting.lowerTextBox.getNumber(), DataBitHelper.BLOCK_META);
			} else if (subId == 5) {
				dw.writeData(setting.higherTextBox.getNumber(), DataBitHelper.BLOCK_META);
			} else if (subId == 6) {
				dw.writeBoolean(setting.inverted);
			}
		}
	}

	public boolean useId() {
		return useId;
	}

	public int getBlockId() {
		ItemSetting itemSetting = (ItemSetting) getSettings().get(0);
		return itemSetting.getItem().isEmpty() ? 0 : Item.getIdFromItem(itemSetting.getItem().getItem());
	}

	public boolean isIdInverted() {
		return idInverted;
	}

	public MetaSetting[] getMetaSettings() {
		return settings;
	}

	@Override
	public String getName() {
		return Localization.UPDATE_BLOCK_MENU.toString();
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		if (!isEditing() && !isSearching()) {
			textBoxes.onClick(mX, mY, button);
			checkBoxes.onClick(mX, mY);
			if (useId) {
				super.onClick(mX, mY, button);
			}
		} else {
			super.onClick(mX, mY, button);
		}
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		super.refreshData(container, newData);

		ComponentMenuUpdateBlock newDataUpdate = (ComponentMenuUpdateBlock) newData;

		if (useId != newDataUpdate.useId) {
			useId = newDataUpdate.useId;
			sendClientData(container, 0, 0);
		}

		if (idInverted != newDataUpdate.idInverted) {
			idInverted = newDataUpdate.idInverted;
			sendClientData(container, 0, 2);
		}

		for (int i = 0; i < settings.length; i++) {
			int id = i + 1;

			MetaSetting setting    = settings[i];
			MetaSetting newSetting = newDataUpdate.settings[i];

			for (int j = 0; j < setting.bits.length; j++) {
				if (setting.bits[j] != newSetting.bits[j]) {
					setting.bits[j] = newSetting.bits[j];
					sendClientData(container, id, j);
				}
			}

			if (setting.lowerTextBox.getNumber() != newSetting.lowerTextBox.getNumber()) {
				setting.lowerTextBox.setNumber(newSetting.lowerTextBox.getNumber());
				sendClientData(container, id, 4);
			}

			if (setting.higherTextBox.getNumber() != newSetting.higherTextBox.getNumber()) {
				setting.higherTextBox.setNumber(newSetting.higherTextBox.getNumber());
				sendClientData(container, id, 5);
			}

			if (setting.inverted != newSetting.inverted) {
				setting.inverted = newSetting.inverted;
				sendClientData(container, id, 6);
			}
		}
	}

	private void sendClientData(ContainerManager container, int id, int subId) {
		DataWriter dw = getWriterForClientComponentPacket(container);
		writeData(dw, id, subId);
		PacketHandler.sendDataToListeningClients(container, dw);
	}

	@Override
	public boolean isVisible() {
		return getParent().getConnectionSet() == ConnectionSet.BUD;
	}

	@Override
	protected void initRadioButtons() {
		//no radio buttons
	}

	@Override
	protected int getSettingCount() {
		return 1;
	}

	@Override
	protected void writeRadioButtonRefreshState(DataWriter dw, boolean value) {
		dw.writeBoolean(false);
		super.writeRadioButtonRefreshState(dw, value);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		if (!isEditing() && !isSearching()) {
			textBoxes.draw(gui, mX, mY);
			checkBoxes.draw(gui, mX, mY);
			gui.drawString(Localization.META.toString(), META_TEXT_X, META_TEXT_Y, 0.7F, 0x404040);
			if (useId) {
				super.draw(gui, mX, mY);
			}
		} else {
			super.draw(gui, mX, mY);
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		if (isEditing() || isSearching() || useId) {
			super.drawMouseOver(gui, mX, mY);
		}
	}

	@Override
	public void onDrag(int mX, int mY, boolean isMenuOpen) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@Override
	public void onRelease(int mX, int mY, boolean isMenuOpen) {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	@SideOnly(Side.CLIENT)
	@Override
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		if (!isEditing() && !isSearching()) {
			return textBoxes.onKeyStroke(gui, c, k);
		} else {
			return super.onKeyStroke(gui, c, k);
		}
	}

	@Override
	public void writeData(DataWriter dw) {
		super.writeData(dw);

		dw.writeBoolean(useId);
		dw.writeBoolean(idInverted);

		for (MetaSetting setting : settings) {
			for (boolean bit : setting.bits) {
				dw.writeBoolean(bit);
			}
			dw.writeData(setting.lowerTextBox.getNumber(), DataBitHelper.BLOCK_META);
			dw.writeData(setting.higherTextBox.getNumber(), DataBitHelper.BLOCK_META);
			dw.writeBoolean(setting.inverted);
		}
	}

	@Override
	public void readData(DataReader dr) {
		super.readData(dr);

		useId = dr.readBoolean();
		idInverted = dr.readBoolean();


		for (MetaSetting setting : settings) {
			for (int i = 0; i < setting.bits.length; i++) {
				setting.bits[i] = dr.readBoolean();
			}
			setting.lowerTextBox.setNumber(dr.readData(DataBitHelper.BLOCK_META));
			setting.higherTextBox.setNumber(dr.readData(DataBitHelper.BLOCK_META));
			setting.inverted = dr.readBoolean();
		}
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		super.copyFrom(menu);

		ComponentMenuUpdateBlock menuUpdate = (ComponentMenuUpdateBlock) menu;
		useId = menuUpdate.useId;
		idInverted = menuUpdate.idInverted;

		for (int i = 0; i < settings.length; i++) {
			for (int j = 0; j < settings[i].bits.length; j++) {
				settings[i].bits[j] = menuUpdate.settings[i].bits[j];
			}
			settings[i].lowerTextBox.setNumber(menuUpdate.settings[i].lowerTextBox.getNumber());
			settings[i].higherTextBox.setNumber(menuUpdate.settings[i].higherTextBox.getNumber());
			settings[i].inverted = menuUpdate.settings[i].inverted;
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		if (version >= 11) {
			super.readFromNBT(nbtTagCompound, version, pickup);
		} else {
			ItemSetting setting = (ItemSetting) getSettings().get(0);
			setting.setItem(new ItemStack(Item.getItemById(nbtTagCompound.getShort(NBT_ID))));
		}
		useId = nbtTagCompound.getBoolean(NBT_USE_ID);

		idInverted = nbtTagCompound.getBoolean(NBT_INVERTED);

		NBTTagList list = nbtTagCompound.getTagList(NBT_SETTINGS, 10);
		for (int i = 0; i < list.tagCount(); i++) {
			MetaSetting    setting    = settings[i];
			NBTTagCompound settingTag = list.getCompoundTagAt(i);

			byte bits = settingTag.getByte(NBT_BITS);
			for (int j = 0; j < setting.bits.length; j++) {
				setting.bits[j] = ((bits >> j) & 1) != 0;
			}
			setting.lowerTextBox.setNumber(settingTag.getByte(NBT_LOW));
			setting.higherTextBox.setNumber(settingTag.getByte(NBT_HIGH));
			setting.inverted = settingTag.getBoolean(NBT_INVERTED);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		super.writeToNBT(nbtTagCompound, pickup);

		nbtTagCompound.setBoolean(NBT_USE_ID, useId);
		nbtTagCompound.setBoolean(NBT_INVERTED, idInverted);

		NBTTagList list = new NBTTagList();
		for (MetaSetting setting : settings) {
			NBTTagCompound settingTag = new NBTTagCompound();
			byte           bits       = 0;
			for (int i = 0; i < setting.bits.length; i++) {
				if (setting.bits[i]) {
					bits |= 1 << i;
				}
			}
			settingTag.setByte(NBT_BITS, bits);
			settingTag.setByte(NBT_LOW, (byte) setting.lowerTextBox.getNumber());
			settingTag.setByte(NBT_HIGH, (byte) setting.higherTextBox.getNumber());
			settingTag.setBoolean(NBT_INVERTED, setting.inverted);

			list.appendTag(settingTag);
		}
		nbtTagCompound.setTag(NBT_SETTINGS, list);
	}

	@Override
	public void addErrors(List<String> errors) {
		if (useId) {
			super.addErrors(errors);
		}
	}

	@Override
	protected void readNonSettingData(DataReader dr) {
		if (dr.readBoolean()) {
			int id = dr.readData(DataBitHelper.BUD_SYNC_TYPE);
			if (id == 0) {
				int subId = dr.readData(DataBitHelper.BUD_SYNC_SUB_TYPE_SHORT);
				if (subId == 0) {
					useId = dr.readBoolean();
				} else if (subId == 2) {
					idInverted = dr.readBoolean();
				}
			} else {
				id--;
				MetaSetting setting = settings[id];
				int         subId   = dr.readData(DataBitHelper.BUD_SYNC_SUB_TYPE_LONG);
				if (subId < 4) {
					setting.bits[subId] = dr.readBoolean();
					if (!setting.bits[subId]) {
						setting.lowerTextBox.setNumber(setting.lowerTextBox.getNumber());
						setting.higherTextBox.setNumber(setting.higherTextBox.getNumber());
					}
				} else if (subId == 4) {
					setting.lowerTextBox.setNumber(dr.readData(DataBitHelper.BLOCK_META));
				} else if (subId == 5) {
					setting.higherTextBox.setNumber(dr.readData(DataBitHelper.BLOCK_META));
				} else if (subId == 6) {
					setting.inverted = dr.readBoolean();
				}
			}
		} else {
			super.readNonSettingData(dr);
		}
	}

	public class MetaSetting {
		public boolean[]     bits = new boolean[META_BITS];
		public TextBoxNumber higherTextBox;
		public boolean       inverted;
		public TextBoxNumber lowerTextBox;

		public boolean inUse() {
			return selectedBits() > 0;
		}

		private int selectedBits() {
			int count = 0;
			for (boolean bit : bits) {
				if (bit) {
					count++;
				}
			}

			return count;
		}

		public int getMaxNumber() {
			return (int) Math.pow(2, selectedBits()) - 1;
		}
	}
}
