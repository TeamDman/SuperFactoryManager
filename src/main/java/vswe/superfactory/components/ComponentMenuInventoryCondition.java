package vswe.superfactory.components;


import vswe.superfactory.Localization;

public class ComponentMenuInventoryCondition extends ComponentMenuInventory {
	public ComponentMenuInventoryCondition(FlowComponent parent) {
		super(parent);
	}

	@Override
	protected void initRadioButtons() {
		radioButtonsMulti.add(new RadioButtonInventory(0, Localization.RUN_SHARED_ONCE));
		radioButtonsMulti.add(new RadioButtonInventory(1, Localization.REQUIRE_ALL_TARGETS));
		radioButtonsMulti.add(new RadioButtonInventory(2, Localization.REQUIRE_ONE_TARGET));
	}
}
