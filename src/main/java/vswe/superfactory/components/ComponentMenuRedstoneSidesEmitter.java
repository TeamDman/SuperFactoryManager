package vswe.superfactory.components;

import vswe.superfactory.Localization;

public class ComponentMenuRedstoneSidesEmitter extends ComponentMenuRedstoneSides {
	public ComponentMenuRedstoneSidesEmitter(FlowComponent parent) {
		super(parent);
	}

	@Override
	protected void initRadioButtons() {
		radioButtonList.add(new RadioButton(RADIO_BUTTON_X_LEFT, RADIO_BUTTON_Y, Localization.STRONG_POWER));
		radioButtonList.add(new RadioButton(RADIO_BUTTON_X_RIGHT, RADIO_BUTTON_Y, Localization.WEAK_POWER));
	}

	@Override
	protected String getMessage() {
		return Localization.REDSTONE_EMITTER_SIDES_INFO.toString();
	}

	public boolean useStrongSignal() {
		return useFirstOption();
	}

	@Override
	public String getName() {
		return Localization.REDSTONE_EMITTER_SIDES_MENU.toString();
	}
}
