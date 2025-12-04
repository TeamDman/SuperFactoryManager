package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.CollisionHelper;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;


public class ComponentMenuSignText extends ComponentMenu {
	private static final int   CHECK_BOX_X        = 75;
	private static final int   CHECK_BOX_Y        = 2;
	private static final int   CURSOR_X           = 2;
	private static final int   CURSOR_Y           = 0;
	private static final int   CURSOR_Z           = 5;
	private static final float IDLE_TIME          = 1F;
	private static final String NBT_LINES  = "Lines";
	private static final String NBT_TEXT   = "Text";
	private static final String NBT_UPDATE = "Update";
	private static final int   TEXT_BOX_SIZE_H    = 12;
	private static final int   TEXT_BOX_SIZE_W    = 64;
	private static final int   TEXT_BOX_SRC_X     = 0;
	private static final int   TEXT_BOX_SRC_Y     = 165;
	private static final int   TEXT_BOX_TEXT_X    = 3;
	private static final int   TEXT_BOX_TEXT_Y    = 5;
	private static final int   TEXT_BOX_X         = 5;
	private static final int   TEXT_BOX_Y         = 5;
	private static final int   TEXT_BOX_Y_SPACING = 15;
	private CheckBoxList   checkBoxes;
	private float[]        hasChanged;
	private int            selected = -1;
	private TextBoxLogic[] textBoxes;
	private boolean[]      update;

	public ComponentMenuSignText(FlowComponent parent) {
		super(parent);

		textBoxes = new TextBoxLogic[4];
		update = new boolean[textBoxes.length];
		hasChanged = new float[textBoxes.length];
		checkBoxes = new CheckBoxList();
		for (int i = 0; i < textBoxes.length; i++) {
			final int id = i;
			textBoxes[i] = new TextBoxLogic(15, TEXT_BOX_SIZE_W - TEXT_BOX_TEXT_X * 2) {
				@Override
				protected void textChanged() {
					hasChanged[id] = IDLE_TIME;
				}
			};
			textBoxes[i].setWidthMultiplier(0.6F);
			textBoxes[i].setTextAndCursor("");

			checkBoxes.addCheckBox(new CheckBox(Localization.UPDATE_LINE, CHECK_BOX_X, CHECK_BOX_Y + TEXT_BOX_Y + i * TEXT_BOX_Y_SPACING) {
				@Override
				public void setValue(boolean val) {
					update[id] = val;
				}

				@Override
				public boolean getValue() {
					return update[id];
				}

				@Override
				public void onUpdate() {
					DataWriter dw = getWriterForServerComponentPacket();
					dw.writeData(id, DataBitHelper.LINE_ID);
					dw.writeBoolean(false);
					dw.writeBoolean(getValue());
					PacketHandler.sendDataToServer(dw);
				}
			});
			update[i] = true;
		}

	}

	@Override
	public String getName() {
		return Localization.SIGN_TEXT.toString();
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		for (int i = 0; i < textBoxes.length; i++) {
			TextBoxLogic textBox = textBoxes[i];

			int srcBoxY = selected == i ? 1 : 0;
			int y       = TEXT_BOX_Y + i * TEXT_BOX_Y_SPACING;
			gui.drawTexture(TEXT_BOX_X, y, TEXT_BOX_SRC_X, TEXT_BOX_SRC_Y + srcBoxY * TEXT_BOX_SIZE_H, TEXT_BOX_SIZE_W, TEXT_BOX_SIZE_H);
			gui.drawString(textBox.getText(), TEXT_BOX_X + TEXT_BOX_TEXT_X, y + TEXT_BOX_TEXT_Y, 0.6F, 0xFFFFFF);

			if (selected == i) {
				gui.drawCursor(TEXT_BOX_X + textBox.getCursorPosition(gui) + CURSOR_X, y + CURSOR_Y, CURSOR_Z, 0xFFFFFFFF);
			}
		}
		checkBoxes.draw(gui, mX, mY);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		for (int i = 0; i < textBoxes.length; i++) {
			if (CollisionHelper.inBounds(TEXT_BOX_X, TEXT_BOX_Y + TEXT_BOX_Y_SPACING * i, TEXT_BOX_SIZE_W, TEXT_BOX_SIZE_H, mX, mY)) {
				if (i == selected) {
					selected = -1;
				} else {
					selected = i;
				}
				onSelectedChange();
				break;
			}
		}
		checkBoxes.onClick(mX, mY);
	}

	@Override
	public void onDrag(int mX, int mY, boolean isMenuOpen) {
	}

	@Override
	public void onRelease(int mX, int mY, boolean isMenuOpen) {
	}

	@SideOnly(Side.CLIENT)
	@Override
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		if (k == 15) {
			selected = (selected + 1) % 4;
			onSelectedChange();
			return true;
		} else if (selected == -1) {
			return super.onKeyStroke(gui, c, k);
		} else {
			textBoxes[selected].onKeyStroke(gui, c, k);
			return true;
		}
	}

	private void onSelectedChange() {
		update(IDLE_TIME);
	}

	@Override
	public void writeData(DataWriter dw) {
		for (int i = 0; i < textBoxes.length; i++) {
			dw.writeBoolean(update[i]);
			dw.writeString(textBoxes[i].getText(), DataBitHelper.LINE_LENGTH);
		}
	}

	@Override
	public void readData(DataReader dr) {
		for (int i = 0; i < textBoxes.length; i++) {
			update[i] = dr.readBoolean();
			setTextPostSync(i, dr.readString(DataBitHelper.LINE_LENGTH));
			textBoxes[i].resetCursor();
		}
	}

	private void setTextPostSync(int id, String str) {
		if (str == null) {
			str = "";
		}

		textBoxes[id].setText(str);
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		ComponentMenuSignText textMenu = (ComponentMenuSignText) menu;
		for (int i = 0; i < textBoxes.length; i++) {
			textBoxes[i].setText(textMenu.textBoxes[i].getText());
			update[i] = textMenu.update[i];
		}
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		ComponentMenuSignText newDataText = (ComponentMenuSignText) newData;
		for (int i = 0; i < textBoxes.length; i++) {
			if (!newDataText.textBoxes[i].getText().equals(textBoxes[i].getText())) {
				textBoxes[i].setText(newDataText.textBoxes[i].getText());
				DataWriter dw = getWriterForClientComponentPacket(container);
				dw.writeData(i, DataBitHelper.LINE_ID);
				dw.writeBoolean(true);
				dw.writeString(textBoxes[i].getText(), DataBitHelper.LINE_LENGTH);
				PacketHandler.sendDataToListeningClients(container, dw);
			}

			if (newDataText.update[i] != update[i]) {
				update[i] = newDataText.update[i];
				DataWriter dw = getWriterForClientComponentPacket(container);
				dw.writeData(i, DataBitHelper.LINE_ID);
				dw.writeBoolean(false);
				dw.writeBoolean(update[i]);
				PacketHandler.sendDataToListeningClients(container, dw);
			}
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		NBTTagList list = nbtTagCompound.getTagList(NBT_LINES, 10);
		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound element = list.getCompoundTagAt(i);
			update[i] = element.getBoolean(NBT_UPDATE);
			setTextPostSync(i, element.getString(NBT_TEXT));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		NBTTagList list = new NBTTagList();

		for (int i = 0; i < textBoxes.length; i++) {
			NBTTagCompound element = new NBTTagCompound();
			element.setBoolean(NBT_UPDATE, update[i]);
			element.setString(NBT_TEXT, textBoxes[i].getText());
			list.appendTag(element);
		}

		nbtTagCompound.setTag(NBT_LINES, list);
	}

	@Override
	public void update(float partial) {
		for (int i = 0; i < hasChanged.length; i++) {
			if (hasChanged[i] > 0) {
				hasChanged[i] -= partial;
				if (hasChanged[i] <= 0) {
					DataWriter dw = getWriterForServerComponentPacket();
					dw.writeData(i, DataBitHelper.LINE_ID);
					dw.writeBoolean(true);
					dw.writeString(textBoxes[i].getText(), DataBitHelper.LINE_LENGTH);
					PacketHandler.sendDataToServer(dw);
				}
			}
		}
	}

	@Override
	public void onGuiClosed() {
		update(IDLE_TIME);
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		int id = dr.readData(DataBitHelper.LINE_ID);
		if (dr.readBoolean()) {
			setTextPostSync(id, dr.readString(DataBitHelper.LINE_LENGTH));
			textBoxes[id].updateCursor();
		} else {
			update[id] = dr.readBoolean();
		}
	}

	public boolean shouldUpdate(int id) {
		return update[id];
	}

	public String getText(int id) {
		return textBoxes[id].getText();
	}
}
