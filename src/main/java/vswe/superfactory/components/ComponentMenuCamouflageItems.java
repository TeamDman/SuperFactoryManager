package vswe.superfactory.components;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.Localization;
import vswe.superfactory.interfaces.GuiManager;

import java.util.List;

public class ComponentMenuCamouflageItems extends ComponentMenuItem {
	private static final int MENU_WIDTH    = 120;
	private static final int TEXT_MARGIN_X = 5;
	private static final int TEXT_Y        = 40;

	public ComponentMenuCamouflageItems(FlowComponent parent) {
		super(parent);
	}

	@Override
	public String getName() {
		return Localization.CAMOUFLAGE_ITEM_MENU.toString();
	}

	@Override
	protected void initRadioButtons() {
		radioButtons.add(new RadioButton(RADIO_BUTTON_X_LEFT, RADIO_BUTTON_Y, Localization.CLEAR_CAMOUFLAGE));
		radioButtons.add(new RadioButton(RADIO_BUTTON_X_RIGHT, RADIO_BUTTON_Y, Localization.SET_CAMOUFLAGE));
	}

	@Override
	protected int getSettingCount() {
		return 1;
	}

	@Override
	protected boolean doAllowEdit() {
		return false;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void draw(GuiManager gui, int mX, int mY) {
		super.draw(gui, mX, mY);

		if (!isEditing() && !isSearching()) {
			gui.drawSplitString(Localization.CAMOUFLAGE_INFO.toString(), TEXT_MARGIN_X, TEXT_Y, MENU_WIDTH - TEXT_MARGIN_X * 2, 0.7F, 0x404040);
		}
	}

	@Override
	protected boolean isListVisible() {
		return isSearching() || !isFirstRadioButtonSelected();
	}

	@Override
	public void addErrors(List<String> errors) {
		if (!isFirstRadioButtonSelected() && !getSettings().get(0).isValid()) {
			errors.add(Localization.NO_CAMOUFLAGE_SETTING.toString());
		}
	}
}
