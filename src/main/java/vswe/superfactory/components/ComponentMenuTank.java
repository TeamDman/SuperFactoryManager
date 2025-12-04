package vswe.superfactory.components;

import vswe.superfactory.Localization;
import vswe.superfactory.blocks.ConnectionBlockType;

import java.util.List;

public class ComponentMenuTank extends ComponentMenuContainer {
	public ComponentMenuTank(FlowComponent parent) {
		super(parent, ConnectionBlockType.TANK);
	}

	@Override
	public String getName() {
		return Localization.TANK_MENU.toString();
	}

	@Override
	public void addErrors(List<String> errors) {
		if (selectedInventories.isEmpty()) {
			errors.add(Localization.NO_TANK_ERROR.toString());
		}
	}
}
