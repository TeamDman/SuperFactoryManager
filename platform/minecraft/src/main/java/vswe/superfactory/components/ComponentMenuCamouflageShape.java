package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;


public class ComponentMenuCamouflageShape extends ComponentMenuCamouflageAdvanced {
	private static final int CHECK_BOX_SPACING_X = 50;
	private static final int CHECK_BOX_SPACING_Y = 10;
	private static final int CHECK_BOX_X         = 5;
	private static final int CHECK_BOX_Y         = 3;
	private static final String NBT_COLLISION = "UseCollision";
	private static final String NBT_FULL      = "FullCollision";
	private static final String NBT_MAX_X     = "MaxX";
	private static final String NBT_MAX_Y     = "MaxY";
	private static final String NBT_MAX_Z     = "MaxZ";
	private static final String NBT_MIN_X     = "MinX";
	private static final String NBT_MIN_Y     = "MinY";
	private static final String NBT_MIN_Z     = "MinZ";
	private static final String NBT_USE       = "Use";
	private static final int TEXT_BOX_SPACING_X = 40;
	private static final int TEXT_BOX_SPACING_Y = 15;
	private static final int TEXT_BOX_X         = 30;
	private static final int TEXT_BOX_Y         = 25;
	private static final int TEXT_X             = 6;
	private static final int TEXT_X_TO          = 55;
	private static final int TEXT_Y             = 28;
	private CheckBoxList      checkBoxes;
	private Localization[] coordinates = {Localization.X, Localization.Y, Localization.Z};
	private boolean           fullCollision;
	private boolean           inUse;
	private TextBoxNumberList textBoxes;
	private boolean           useCollision;

	public ComponentMenuCamouflageShape(FlowComponent parent) {
		super(parent);

		checkBoxes = new CheckBoxList();

		checkBoxes.addCheckBox(new CheckBox(Localization.CAMOUFLAGE_BOUNDS_USE, CHECK_BOX_X, CHECK_BOX_Y) {
			@Override
			public boolean getValue() {
				return inUse;
			}			@Override
			public void setValue(boolean val) {
				inUse = val;
			}



			@Override
			public void onUpdate() {
				sendCheckBoxPacket();
			}
		});

		checkBoxes.addCheckBox(new CheckBox(Localization.CAMOUFLAGE_COLLISION_USE, CHECK_BOX_X, CHECK_BOX_Y + CHECK_BOX_SPACING_Y) {
			@Override
			public void setValue(boolean val) {
				useCollision = val;
			}

			@Override
			public boolean getValue() {
				return useCollision;
			}

			@Override
			public void onUpdate() {
				sendCheckBoxPacket();
			}

			@Override
			public boolean isVisible() {
				return inUse;
			}
		});

		checkBoxes.addCheckBox(new CheckBox(Localization.CAMOUFLAGE_COLLISION_FULL, CHECK_BOX_X + CHECK_BOX_SPACING_X, CHECK_BOX_Y + CHECK_BOX_SPACING_Y) {
			@Override
			public void setValue(boolean val) {
				fullCollision = val;
			}

			@Override
			public boolean getValue() {
				return fullCollision;
			}

			@Override
			public void onUpdate() {
				sendCheckBoxPacket();
			}

			@Override
			public boolean isVisible() {
				return inUse && useCollision;
			}
		});


		textBoxes = new TextBoxNumberList();
		for (int i = 0; i < 6; i++) {
			int x = i % 2;
			int y = i / 2;

			textBoxes.addTextBox(new TextBoxRange(i, TEXT_BOX_X + TEXT_BOX_SPACING_X * x, TEXT_BOX_Y + TEXT_BOX_SPACING_Y * y, x == 0 ? 0 : 32));
		}

		loadDefault();

	}

	private void sendCheckBoxPacket() {
		DataWriter dw = getWriterForServerComponentPacket();
		dw.writeData(0, DataBitHelper.CAMOUFLAGE_BOUND_TYPE);
		dw.writeBoolean(inUse);
		if (inUse) {
			dw.writeBoolean(useCollision);
			dw.writeBoolean(fullCollision);
		}
		PacketHandler.sendDataToServer(dw);
	}

	private void loadDefault() {
		inUse = false;
		useCollision = true;
		fullCollision = false;
		for (int i = 0; i < 6; i++) {
			((TextBoxRange) textBoxes.getTextBox(i)).reset();
		}
	}

	public boolean shouldUpdate() {
		return inUse;
	}

	public boolean isUseCollision() {
		return useCollision;
	}

	public boolean isFullCollision() {
		return fullCollision;
	}

	public int getBounds(int id) {
		return textBoxes.getTextBox(id).getNumber();
	}

	@Override
	public String getName() {
		return Localization.BOUNDS_MENU.toString();
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		checkBoxes.onClick(mX, mY);
		if (inUse) {
			textBoxes.onClick(mX, mY, button);
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

	@Override
	public boolean onKeyStroke(GuiManager gui, char c, int k) {
		if (inUse) {
			return textBoxes.onKeyStroke(gui, c, k);
		} else {
			return super.onKeyStroke(gui, c, k);
		}
	}

	@Override
	public void writeData(DataWriter dw) {
		dw.writeBoolean(inUse);
		if (inUse) {
			dw.writeBoolean(useCollision);
			dw.writeBoolean(fullCollision);
			for (int i = 0; i < 6; i++) {
				dw.writeData(textBoxes.getTextBox(i).getNumber(), DataBitHelper.CAMOUFLAGE_BOUNDS);
			}
		}
	}

	@Override
	public void readData(DataReader dr) {
		inUse = dr.readBoolean();
		if (inUse) {
			useCollision = dr.readBoolean();
			fullCollision = dr.readBoolean();
			for (int i = 0; i < 6; i++) {
				textBoxes.getTextBox(i).setNumber(dr.readData(DataBitHelper.CAMOUFLAGE_BOUNDS));
			}
		} else {
			loadDefault();
		}
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		ComponentMenuCamouflageShape menuShape = (ComponentMenuCamouflageShape) menu;
		if (menuShape.inUse) {
			inUse = true;
			useCollision = menuShape.useCollision;
			fullCollision = menuShape.fullCollision;
			for (int i = 0; i < 6; i++) {
				textBoxes.getTextBox(i).setNumber(menuShape.textBoxes.getTextBox(i).getNumber());
			}
		} else {
			loadDefault();
		}
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		ComponentMenuCamouflageShape newDataShape = (ComponentMenuCamouflageShape) newData;
		if (inUse != newDataShape.inUse || useCollision != newDataShape.useCollision || fullCollision != newDataShape.fullCollision) {
			inUse = newDataShape.inUse;

			DataWriter dw = getWriterForClientComponentPacket(container);
			dw.writeData(0, DataBitHelper.CAMOUFLAGE_BOUND_TYPE);
			dw.writeBoolean(inUse);
			if (!inUse) {
				loadDefault();
			} else {
				useCollision = newDataShape.useCollision;
				fullCollision = newDataShape.fullCollision;
				dw.writeBoolean(useCollision);
				dw.writeBoolean(fullCollision);
			}
			PacketHandler.sendDataToListeningClients(container, dw);
		}

		for (int i = 0; i < 6; i++) {
			if (textBoxes.getTextBox(i).getNumber() != newDataShape.textBoxes.getTextBox(i).getNumber()) {
				textBoxes.getTextBox(i).setNumber(newDataShape.textBoxes.getTextBox(i).getNumber());

				DataWriter dw = getWriterForClientComponentPacket(container);
				dw.writeData(i + 1, DataBitHelper.CAMOUFLAGE_BOUND_TYPE);
				dw.writeData(textBoxes.getTextBox(i).getNumber(), DataBitHelper.CAMOUFLAGE_BOUNDS);
				PacketHandler.sendDataToListeningClients(container, dw);
			}
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		inUse = nbtTagCompound.getBoolean(NBT_USE);
		if (inUse) {
			useCollision = nbtTagCompound.getBoolean(NBT_COLLISION);
			fullCollision = nbtTagCompound.getBoolean(NBT_FULL);
			textBoxes.getTextBox(0).setNumber(nbtTagCompound.getByte(NBT_MIN_X));
			textBoxes.getTextBox(1).setNumber(nbtTagCompound.getByte(NBT_MAX_X));
			textBoxes.getTextBox(2).setNumber(nbtTagCompound.getByte(NBT_MIN_Y));
			textBoxes.getTextBox(3).setNumber(nbtTagCompound.getByte(NBT_MAX_Y));
			textBoxes.getTextBox(4).setNumber(nbtTagCompound.getByte(NBT_MIN_Z));
			textBoxes.getTextBox(5).setNumber(nbtTagCompound.getByte(NBT_MAX_Z));
		} else {
			loadDefault();
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setBoolean(NBT_USE, inUse);
		if (inUse) {
			nbtTagCompound.setBoolean(NBT_COLLISION, useCollision);
			nbtTagCompound.setBoolean(NBT_FULL, fullCollision);
			nbtTagCompound.setByte(NBT_MIN_X, (byte) textBoxes.getTextBox(0).getNumber());
			nbtTagCompound.setByte(NBT_MAX_X, (byte) textBoxes.getTextBox(1).getNumber());
			nbtTagCompound.setByte(NBT_MIN_Y, (byte) textBoxes.getTextBox(2).getNumber());
			nbtTagCompound.setByte(NBT_MAX_Y, (byte) textBoxes.getTextBox(3).getNumber());
			nbtTagCompound.setByte(NBT_MIN_Z, (byte) textBoxes.getTextBox(4).getNumber());
			nbtTagCompound.setByte(NBT_MAX_Z, (byte) textBoxes.getTextBox(5).getNumber());
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		super.draw(gui, mX, mY);

		checkBoxes.draw(gui, mX, mY);
		if (inUse) {
			textBoxes.draw(gui, mX, mY);
			for (int i = 0; i < coordinates.length; i++) {
				gui.drawString(coordinates[i].toString(), TEXT_X, TEXT_Y + TEXT_BOX_SPACING_Y * i, 0x404040);
				gui.drawString(Localization.TO.toString(), TEXT_X_TO, TEXT_Y + TEXT_BOX_SPACING_Y * i, 0x404040);
			}
		}
	}

	@Override
	protected String getWarningText() {
		return Localization.BOUNDS_WARNING.toString();
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		int id = dr.readData(DataBitHelper.CAMOUFLAGE_BOUND_TYPE);
		if (id == 0) {
			inUse = dr.readBoolean();
			if (inUse) {
				useCollision = dr.readBoolean();
				fullCollision = dr.readBoolean();
			} else {
				loadDefault();
			}
		} else {
			textBoxes.getTextBox(id - 1).setNumber(dr.readData(DataBitHelper.CAMOUFLAGE_BOUNDS));
		}
	}

	private class TextBoxRange extends TextBoxNumber {
		private int defaultNumber;
		private int id;

		public TextBoxRange(int id, int x, int y, int defaultNumber) {
			super(x, y, 2, false);
			this.defaultNumber = defaultNumber;
			this.id = id;
		}

		@Override
		public int getMaxNumber() {
			return 32;
		}

		@Override
		public void onNumberChanged() {
			DataWriter dw = getWriterForServerComponentPacket();
			dw.writeData(id + 1, DataBitHelper.CAMOUFLAGE_BOUND_TYPE);
			dw.writeData(getNumber(), DataBitHelper.CAMOUFLAGE_BOUNDS);
			PacketHandler.sendDataToServer(dw);
		}

		public void reset() {
			setNumber(defaultNumber);
		}
	}


}
