package vswe.superfactory.components;


import vswe.superfactory.Localization;
import vswe.superfactory.components.internal.ConnectionSet;

public class ComponentMenuListOrderVariable extends ComponentMenuListOrder {
	public ComponentMenuListOrderVariable(FlowComponent parent) {
		super(parent);
	}

	@Override
	public boolean isVisible() {
		return getParent().getConnectionSet() == ConnectionSet.STANDARD;
	}

	@Override
	public String getName() {
		return Localization.VALUE_ORDER_MENU.toString();
	}
}
