package vswe.superfactory.components;


import vswe.superfactory.Localization;
import vswe.superfactory.blocks.ConnectionBlockType;

import java.util.List;


public class ComponentMenuCamouflage extends ComponentMenuContainer {
	public ComponentMenuCamouflage(FlowComponent parent) {
		super(parent, ConnectionBlockType.CAMOUFLAGE);
	}

	@Override
	public String getName() {
		return Localization.CAMOUFLAGE_BLOCK_MENU.toString();
	}

	@Override
	public void addErrors(List<String> errors) {
		if (selectedInventories.isEmpty()) {
			errors.add(Localization.NO_CAMOUFLAGE_BLOCKS_ERROR.toString());
		}
	}

	@Override
	protected void initRadioButtons() {
		//nothing here
	}
}
