package vswe.superfactory.components;

import vswe.superfactory.Localization;

public class ComponentMenuRedstoneStrengthNodes extends ComponentMenuRedstoneStrength {
	public ComponentMenuRedstoneStrengthNodes(FlowComponent parent) {
		super(parent);
	}

	@Override
	public String getName() {
		return Localization.REDSTONE_STRENGTH_MENU_CONDITION.toString();
	}

	@Override
	public boolean isVisible() {
		return true;
	}
}
