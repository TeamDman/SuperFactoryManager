package vswe.superfactory.components;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.ConnectionSet;
import vswe.superfactory.interfaces.ContainerManager;
import vswe.superfactory.interfaces.GuiManager;
import vswe.superfactory.network.packets.DataBitHelper;
import vswe.superfactory.network.packets.DataReader;
import vswe.superfactory.network.packets.DataWriter;
import vswe.superfactory.network.packets.PacketHandler;

import java.util.List;

public class ComponentMenuVariable extends ComponentMenu {
	private static final int CHECK_BOX_X = 5;
	private static final int CHECK_BOX_Y = 52;
	private static final String NBT_EXECUTED = "Executed";
	private static final String NBT_MODE     = "Mode";
	private static final String NBT_VARIABLE = "Variable";
	private static final int RADIO_BUTTON_SPACING = 12;
	private static final int RADIO_BUTTON_X       = 5;
	private static final int RADIO_BUTTON_Y       = 28;
	private CheckBoxList    checkBoxes;
	private boolean         executed;
	private RadioButtonList radioButtons;
	private int             selectedVariable = 0;
	private VariableDisplay varDisplay;
	public ComponentMenuVariable(FlowComponent parent) {
		super(parent);

		int declarationCount  = 0;
		int modificationCount = 0;

		radioButtons = new RadioButtonList() {
			@Override
			public int getSelectedOption() {
				int          id   = super.getSelectedOption();
				VariableMode mode = VariableMode.values()[id];
				if (mode.declaration != isDeclaration()) {
					setSelectedOption(id = getDefaultId());
				}

				return id;
			}

			@Override
			public void setSelectedOption(int selectedOption) {
				super.setSelectedOption(selectedOption);

				if (isDeclaration()) {
					getParent().getManager().updateVariables();
				}
			}

			@Override
			public void updateSelectedOption(int selectedOption) {
				setSelectedOption(selectedOption);
				DataWriter dw = getWriterForServerComponentPacket();
				dw.writeBoolean(true); //var || mode
				dw.writeBoolean(false); //mode
				dw.writeData(selectedOption, DataBitHelper.CONTAINER_MODE);
				PacketHandler.sendDataToServer(dw);
			}
		};

		for (int i = 0; i < VariableMode.values().length; i++) {
			final VariableMode mode = VariableMode.values()[i];
			int                id   = mode.declaration ? declarationCount++ : modificationCount++;

			radioButtons.add(new RadioButton(RADIO_BUTTON_X, RADIO_BUTTON_Y + id * RADIO_BUTTON_SPACING, mode.getName()) {
				@Override
				public boolean isVisible() {
					return mode.declaration == isDeclaration();
				}
			});
		}

		radioButtons.setSelectedOption(getDefaultId());

		varDisplay = new VariableDisplay(null, 5, 5) {
			@Override
			public int getValue() {
				return selectedVariable;
			}

			@Override
			public void setValue(int val) {
				setSelectedVariable(val);
			}

			@Override
			public void onUpdate() {
				DataWriter dw = getWriterForServerComponentPacket();
				dw.writeBoolean(true); //var || mode
				dw.writeBoolean(true); //var
				dw.writeData(selectedVariable, DataBitHelper.VARIABLE_TYPE);
				PacketHandler.sendDataToServer(dw);
			}
		};

		checkBoxes = new CheckBoxList();
		checkBoxes.addCheckBox(new CheckBox(Localization.GLOBAL_VALUE_SET, CHECK_BOX_X, CHECK_BOX_Y) {
			@Override
			public boolean getValue() {
				return executed;
			}

			@Override
			public void setValue(boolean val) {
				executed = val;
			}

			@Override
			public void onUpdate() {
				DataWriter dw = getWriterForServerComponentPacket();
				dw.writeBoolean(false); //executed
				dw.writeBoolean(executed);
				PacketHandler.sendDataToServer(dw);
			}

			@Override
			public boolean isVisible() {
				return getVariableMode() == VariableMode.GLOBAL;
			}
		});
	}

	public boolean isDeclaration() {
		return getParent().getConnectionSet() == ConnectionSet.EMPTY;
	}

	private int getDefaultId() {
		return isDeclaration() ? 1 : 2;
	}

	public VariableMode getVariableMode() {
		return VariableMode.values()[radioButtons.getSelectedOption()];
	}

	@Override
	public String getName() {
		return Localization.VARIABLE_MENU.toString();
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		radioButtons.draw(gui, mX, mY);
		varDisplay.draw(gui, mX, mY);
		checkBoxes.draw(gui, mX, mY);
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void drawMouseOver(GuiManager gui, int mX, int mY) {
		varDisplay.drawMouseOver(gui, mX, mY);
	}

	@Override
	public void onClick(int mX, int mY, int button) {
		radioButtons.onClick(mX, mY, button);
		varDisplay.onClick(mX, mY);
		checkBoxes.onClick(mX, mY);
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
	public void writeData(DataWriter dw) {
		dw.writeData(selectedVariable, DataBitHelper.VARIABLE_TYPE);
		dw.writeData(radioButtons.getSelectedOption(), DataBitHelper.CONTAINER_MODE);
		dw.writeBoolean(executed);
	}

	@Override
	public void readData(DataReader dr) {
		setSelectedVariable(dr.readData(DataBitHelper.VARIABLE_TYPE));
		radioButtons.setSelectedOption(dr.readData(DataBitHelper.CONTAINER_MODE));
		executed = dr.readBoolean();
	}

	@Override
	public void copyFrom(ComponentMenu menu) {
		setSelectedVariable(((ComponentMenuVariable) menu).selectedVariable);
		radioButtons.setSelectedOption(((ComponentMenuVariable) menu).radioButtons.getSelectedOption());
		executed = ((ComponentMenuVariable) menu).executed;
	}

	@Override
	public void refreshData(ContainerManager container, ComponentMenu newData) {
		ComponentMenuVariable newDataMode = (ComponentMenuVariable) newData;

		if (selectedVariable != newDataMode.selectedVariable) {
			setSelectedVariable(newDataMode.selectedVariable);

			DataWriter dw = getWriterForClientComponentPacket(container);
			dw.writeBoolean(true); //var  || mode
			dw.writeBoolean(true); //var
			dw.writeData(selectedVariable, DataBitHelper.VARIABLE_TYPE);
			PacketHandler.sendDataToListeningClients(container, dw);
		}

		if (radioButtons.getRawSelectedOption() != newDataMode.radioButtons.getRawSelectedOption()) {
			radioButtons.setSelectedOption(newDataMode.radioButtons.getRawSelectedOption());

			DataWriter dw = getWriterForClientComponentPacket(container);
			dw.writeBoolean(true); //var  || mode
			dw.writeBoolean(false); //mode
			dw.writeData(radioButtons.getRawSelectedOption(), DataBitHelper.CONTAINER_MODE);
			PacketHandler.sendDataToListeningClients(container, dw);
		}


		if (executed != newDataMode.getVariable().hasBeenExecuted()) {
			executed = newDataMode.getVariable().hasBeenExecuted();

			DataWriter dw = getWriterForClientComponentPacket(container);
			dw.writeBoolean(false); //executed
			dw.writeBoolean(executed);
			PacketHandler.sendDataToListeningClients(container, dw);
		}
	}

	private Variable getVariable() {
		return getParent().getManager().getVariables()[getSelectedVariable()];
	}

	public int getSelectedVariable() {
		return selectedVariable;
	}

	private void setSelectedVariable(int val) {
		selectedVariable = val;

		if (isDeclaration()) {
			getParent().getManager().updateVariables();
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbtTagCompound, int version, boolean pickup) {
		setSelectedVariable(nbtTagCompound.getByte(NBT_VARIABLE));
		radioButtons.setSelectedOption(nbtTagCompound.getByte(NBT_MODE));
		executed = nbtTagCompound.getBoolean(NBT_EXECUTED);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbtTagCompound, boolean pickup) {
		nbtTagCompound.setByte(NBT_VARIABLE, (byte) selectedVariable);
		nbtTagCompound.setByte(NBT_MODE, (byte) radioButtons.getSelectedOption());
		nbtTagCompound.setBoolean(NBT_EXECUTED, executed);
	}

	@Override
	public void addErrors(List<String> errors) {
		Variable variable = getParent().getManager().getVariables()[selectedVariable];
		if (!variable.isValid()) {
			errors.add(Localization.NOT_DECLARED_ERROR.toString());
		} else if (isDeclaration() && variable.getDeclaration().getId() != getParent().getId()) {
			errors.add(Localization.ALREADY_DECLARED_ERROR.toString());
		}
	}

	@Override
	public void readNetworkComponent(DataReader dr) {
		if (dr.readBoolean()) {
			if (dr.readBoolean()) {
				setSelectedVariable(dr.readData(DataBitHelper.VARIABLE_TYPE));
			} else {
				radioButtons.setSelectedOption(dr.readData(DataBitHelper.CONTAINER_MODE));
			}
		} else {
			executed = dr.readBoolean();
			if (!getParent().getManager().getWorld().isRemote) {
				getVariable().setExecuted(executed);
			}
		}
	}


	public enum VariableMode {
		GLOBAL(Localization.GLOBAL, true),
		LOCAL(Localization.LOCAL, true),
		ADD(Localization.ADD, false),
		REMOVE(Localization.REMOVE, false),
		SET(Localization.SET, false);

		private boolean      declaration;
		private Localization name;

		VariableMode(Localization name, boolean declaration) {
			this.name = name;
			this.declaration = declaration;
		}

		public Localization getName() {
			return name;
		}

		@Override
		public String toString() {
			return super.toString().charAt(0) + super.toString().substring(1).toLowerCase();
		}
	}
}
