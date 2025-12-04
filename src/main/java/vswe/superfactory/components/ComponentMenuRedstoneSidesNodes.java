package vswe.superfactory.components;


import vswe.superfactory.Localization;

public class ComponentMenuRedstoneSidesNodes extends ComponentMenuRedstoneSidesTrigger {
	public ComponentMenuRedstoneSidesNodes(FlowComponent parent) {
		super(parent);
	}

	@Override
	public boolean isVisible() {
		return true;
	}

	@Override
	public String getName() {
		return Localization.REDSTONE_SIDES_MENU.toString();
	}
}
