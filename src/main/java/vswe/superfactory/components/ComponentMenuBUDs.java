package vswe.superfactory.components;


import vswe.superfactory.Localization;
import vswe.superfactory.blocks.ConnectionBlockType;
import vswe.superfactory.components.internal.ConnectionSet;

import java.util.List;

public class ComponentMenuBUDs extends ComponentMenuContainer {
	public ComponentMenuBUDs(FlowComponent parent) {
		super(parent, ConnectionBlockType.BUD);
	}

	@Override
	public String getName() {
		return Localization.DETECTOR_MENU.toString();
	}

	@Override
	public void addErrors(List<String> errors) {
		if (selectedInventories.isEmpty()) {
			errors.add(Localization.NO_DETECTOR_ERROR.toString());
		}
	}

	@Override
	public boolean isVisible() {
		return getParent().getConnectionSet() == ConnectionSet.BUD;
	}

	@Override
	protected void initRadioButtons() {
		radioButtonsMulti.add(new ComponentMenuContainer.RadioButtonInventory(0, Localization.REQUIRE_ALL_TARGETS));
		radioButtonsMulti.add(new ComponentMenuContainer.RadioButtonInventory(1, Localization.REQUIRE_ONE_TARGET));
	}
}
