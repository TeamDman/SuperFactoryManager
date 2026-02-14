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

public class ComponentMenuPulse extends ComponentMenu {

	private static final int CHECK_BOX_X            = 5;
	private static final int CHECK_BOX_Y            = 5;
	private static final String NBT_SECOND    = "Seconds";
	private static final String NBT_TICK      = "Ticks";
	private static final String NBT_TYPE      = "Type";
	private static final String NBT_USE_PULSE = "UsePulse";
	private static final int RADIO_BUTTON_SPACING_X = 67;
	private static final int RADIO_BUTTON_SPACING_Y = 12;
	private static final int RADIO_BUTTON_X         = 5;
	private static final int RADIO_BUTTON_Y         = 44;
	private static final int TEXT_BOX_X_LEFT  = 10;
	private static final int TEXT_BOX_X_RIGHT = 70;
	private static final int TEXT_BOX_Y       = 25;
	private CheckBoxList      checkBoxes;
	private RadioButtonList   radioButtons;
	private TextBoxNumber     secondsTextBox;
	private TextBoxNumberList textBoxes;
	private TextBoxNumber     ticksTextBox;
	private boolean           usePulse;

	public ComponentMenuPulse(FlowComponent parent) {
		super(parent);

		checkBoxes = new CheckBoxList();
		checkBoxes.addCheckBox(new CheckBox(Localization.DO_EMIT_PULSE, CHECK_BOX_X, CHECK_BOX_Y) {
			@Override
			public boolean getValue() {
				return usePulse;
			}

			@Override
			public void setValue(boolean val) {
				usePulse = val;
			}

			@Override
			public void onUpdate() {
				sendServerPacket(ComponentSyncType.CHECK_BOX);
			}
		});

		radioButtons = new RadioButtonList() {
			@Override
			public void updateSelectedOption(int selectedOption) {
				radioButtons.setSelectedOption(selectedOption);

				sendServerPacket(ComponentSyncType.RADIO_BUTTON);
			}
		};

		for (int i = 0; i < PULSE_OPTIONS.values().length; i++) {
			int x = i % 2;
			int y = i / 2;

			radioButtons.add(new RadioButton(RADIO_BUTTON_X + x * RADIO_BUTTON_SPACING_X, RADIO_BUTTON_Y + y * RADIO_BUTTON_SPACING_Y, PULSE_OPTIONS.values()[i].getName()));
		}


		textBoxes = new TextBoxNumberList();
		textBoxes.addTextBox(secondsTextBox = new TextBoxNumber(TEXT_BOX_X_LEFT, TEXT_BOX_Y, 2, true) {
			@Override
			public void onNumberChanged() {
				sendServerPacket(ComponentSyncType.TEXT_BOX_1);
			}
		});
		textBoxes.addTextBox(ticksTextBox = new TextBoxNumber(TEXT_BOX_X_RIGHT, TEXT_BOX_Y, 2, true) {
			@Override
			public int getMaxNumber() {
				return 19;
			}

			@Override
			public void onNumberChanged() {
				sendServerPacket(ComponentSyncType.TEXT_BOX_2);
			}
		});

		setDefault();
	}

	private void sendServerPacket(ComponentSyncType type) {
		DataWriter dw = getWriterForServerComponentPacket();
		writeData(dw, type);
		PacketHandler.sendDataToServer(dw);
	}

	private void writeData(DataWriter dw, ComponentSyncType type) {
		dw.writeData(type.ordinal(), DataBitHelper.PULSE_COMPONENT_TYPES);
		switch (type) {
			case CHECK_BOX:
				dw.writeBoolean(usePulse);
				break;
			case RADIO_BUTTON:
				dw.writeData(radioButtons.getSelectedOption(), DataBitHelper.PULSE_TYPES);
				break;
			case TEXT_BOX_1:
				dw.writeData(secondsTextBox.getNumber(), DataBitHelper.PULSE_SECONDS);
				break;
			case TEXT_BOX_2:
				dw.writeData(ticksTextBox.getNumber(), DataBitHelper.PULSE_TICKS);

		}
	}

	private void setDefault() {
		radioButtons.setSelectedOption(0);
		secondsTextBox.setNumber(0);
		ticksTextBox.setNumber(10);
	}

	@Override
	public String getName() {
		return Localization.PULSE_MENU.toString();
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		checkBoxes.draw(gui, mX, mY);
		if (usePulse) {
			radioButtons.draw(gui, mX, mY);
			textBoxes.draw(gui, mX, mY);

			gui.drawCenteredString(Localization.SECONDS.toString(), secondsTextBox.getX(), secondsTextBox.getY() - 7, 0.7F, secondsTextBox.getWidth(), 0x404040);
			gui.drawCenteredString(Localization.TICKS.toString(), ticksTextBox.getX(), ticksTextBox.getY() - 7, 0.7F, ticksTextBox.getWidth(), 0x404040);
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {

	}

	@Override
	public void onClick(int mX, int mY, int button) {
		checkBoxes.onClick(mX, mY);
		if (usePulse) {
			radioButtons.onClick(mX, mY, button);
			textBoxes.onClick(mX, mY, button);
		}
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
		return usePulse && textBoxes.onKeyStroke(gui, c, k);

	}

	@Override
	public void writeData(DataWriter dw) {
		dw.writeBoolean(usePulse);
		if (usePulse) {
			dw.writeData(radioButtons.getSelectedOption(), DataBitHelper.PULSE_TYPES);
			dw.writeData(secondsTextBox.getNumber(), DataBitHelper.PULSE_SECONDS);
			dw.writeData(ticksTextBox.getNumber(), DataBitHelper.PULSE_TICKS);
		}
	}

	@Override
	public void readData(DataReader dr) {
		usePulse = dr.readBoolean();
		if (usePulse) {
			radioButtons.setSelectedOption(dr.readData(DataBitHelper.PULSE_TYPES));
			secondsTextBox.setNumber(dr.readData(DataBitHelper.PULSE_SECONDS));
			ticksTextBox.setNumber(dr.readData(DataBitHelper.PULSE_TICKS));
		}
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		ComponentMenuPulse menuPulse = (ComponentMenuPulse) menu;
		usePulse = menuPulse.usePulse;
		if (usePulse) {
			radioButtons.setSelectedOption(menuPulse.radioButtons.getSelectedOption());
			secondsTextBox.setNumber(menuPulse.secondsTextBox.getNumber());
			ticksTextBox.setNumber(menuPulse.ticksTextBox.getNumber());
		} else {
			setDefault();
		}
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		ComponentMenuPulse newDataPulse = (ComponentMenuPulse) newData;

		if (usePulse != newDataPulse.usePulse) {
			usePulse = newDataPulse.usePulse;

			sendClientPacket(container, ComponentSyncType.CHECK_BOX);

			if (!usePulse) {
				setDefault();
				return;
			}
		}

		if (radioButtons.getSelectedOption() != newDataPulse.radioButtons.getSelectedOption()) {
			radioButtons.setSelectedOption(newDataPulse.radioButtons.getSelectedOption());

			sendClientPacket(container, ComponentSyncType.RADIO_BUTTON);
		}

		if (secondsTextBox.getNumber() != newDataPulse.secondsTextBox.getNumber()) {
			secondsTextBox.setNumber(newDataPulse.secondsTextBox.getNumber());

			sendClientPacket(container, ComponentSyncType.TEXT_BOX_1);
		}

		if (ticksTextBox.getNumber() != newDataPulse.ticksTextBox.getNumber()) {
			ticksTextBox.setNumber(newDataPulse.ticksTextBox.getNumber());

			sendClientPacket(container, ComponentSyncType.TEXT_BOX_2);
		}
	}

	private void sendClientPacket(ContainerManager container, ComponentSyncType type) {
		DataWriter dw = getWriterForClientComponentPacket(container);
		writeData(dw, type);
		PacketHandler.sendDataToListeningClients(container, dw);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		usePulse = nbtTagCompound.getBoolean(NBT_USE_PULSE);
		if (usePulse) {
			radioButtons.setSelectedOption(nbtTagCompound.getByte(NBT_TYPE));
			secondsTextBox.setNumber(nbtTagCompound.getByte(NBT_SECOND));
			ticksTextBox.setNumber(nbtTagCompound.getByte(NBT_TICK));
		} else {
			setDefault();
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setBoolean(NBT_USE_PULSE, usePulse);
		if (usePulse) {
			nbtTagCompound.setByte(NBT_TYPE, (byte) radioButtons.getSelectedOption());
			nbtTagCompound.setByte(NBT_SECOND, (byte) secondsTextBox.getNumber());
			nbtTagCompound.setByte(NBT_TICK, (byte) ticksTextBox.getNumber());
		}
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		ComponentSyncType type = ComponentSyncType.values()[dr.readData(DataBitHelper.PULSE_COMPONENT_TYPES)];

		switch (type) {
			case CHECK_BOX:
				usePulse = dr.readBoolean();
				if (!usePulse) {
					setDefault();
				}
				break;
			case RADIO_BUTTON:
				radioButtons.setSelectedOption(dr.readData(DataBitHelper.PULSE_TYPES));
				break;
			case TEXT_BOX_1:
				secondsTextBox.setNumber(dr.readData(DataBitHelper.PULSE_SECONDS));
				break;
			case TEXT_BOX_2:
				ticksTextBox.setNumber(dr.readData(DataBitHelper.PULSE_TICKS));

		}
	}

	public boolean shouldEmitPulse() {
		return usePulse;
	}

	public PULSE_OPTIONS getSelectedPulseOverride() {
		return PULSE_OPTIONS.values()[radioButtons.getSelectedOption()];
	}

	public int getPulseTime() {
		return secondsTextBox.getNumber() * 20 + ticksTextBox.getNumber();
	}

	private enum ComponentSyncType {
		CHECK_BOX,
		RADIO_BUTTON,
		TEXT_BOX_1,
		TEXT_BOX_2
	}

	public enum PULSE_OPTIONS {
		EXTEND_OLD(Localization.EXTEND_OLD),
		KEEP_ALL(Localization.KEEP_ALL),
		KEEP_OLD(Localization.KEEP_OLD),
		KEEP_NEW(Localization.KEEP_NEW);

		private Localization name;

		PULSE_OPTIONS(Localization name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return name.toString();
		}

		public Localization getName() {
			return name;
		}
	}
}
