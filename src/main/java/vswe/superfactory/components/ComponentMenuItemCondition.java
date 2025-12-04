package vswe.superfactory.components;


import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.IConditionStuffMenu;
import vswe.superfactory.components.internal.Setting;

import java.util.List;

public class ComponentMenuItemCondition extends ComponentMenuItem implements IConditionStuffMenu {
	public ComponentMenuItemCondition(FlowComponent parent) {
		super(parent);
	}


	@Override
	protected void initRadioButtons() {
		radioButtons.add(new RadioButton(RADIO_BUTTON_X_LEFT, RADIO_BUTTON_Y, Localization.REQUIRES_ALL));
		radioButtons.add(new RadioButton(RADIO_BUTTON_X_RIGHT, RADIO_BUTTON_Y, Localization.IF_ANY));
	}

	@Override
	public void addErrors(List<String> errors) {
		for (Setting setting : getSettings()) {
			if (setting.isValid()) {
				return;
			}
		}

		errors.add(Localization.NO_CONDITION_ERROR.toString());
	}

	public boolean requiresAll() {
		return isFirstRadioButtonSelected();
	}
}
