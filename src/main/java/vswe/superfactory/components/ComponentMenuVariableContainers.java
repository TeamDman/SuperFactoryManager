package vswe.superfactory.components;

import vswe.superfactory.Localization;
import vswe.superfactory.blocks.ConnectionBlockType;

import java.util.EnumSet;


public class ComponentMenuVariableContainers extends ComponentMenuContainer {
	public ComponentMenuVariableContainers(FlowComponent parent) {
		super(parent, null);
	}

	@Override
	protected void initRadioButtons() {
		//nothing
	}

	@Override
	protected EnumSet<ConnectionBlockType> getValidTypes() {
		ComponentMenuContainerTypes componentMenuContainerTypes = ((ComponentMenuContainerTypes) getParent().getMenus().get(1));

		if (componentMenuContainerTypes.isVisible()) {
			return componentMenuContainerTypes.getValidTypes();
		} else {
			int      variableId = ((ComponentMenuVariable) getParent().getMenus().get(0)).getSelectedVariable();
			Variable variable   = getParent().getManager().getVariables()[variableId];
			if (variable.isValid()) {
				return ((ComponentMenuContainerTypes) variable.getDeclaration().getMenus().get(1)).getValidTypes();
			} else {
				return EnumSet.noneOf(ConnectionBlockType.class);
			}
		}
	}

	@Override
	public boolean isVariableAllowed(EnumSet<ConnectionBlockType> validTypes, int i) {
		return super.isVariableAllowed(validTypes, i) && i != ((ComponentMenuVariable) getParent().getMenus().get(0)).getSelectedVariable();
	}

	@Override
	public String getName() {
		return Localization.VARIABLE_CONTAINERS_MENU.toString();
	}
}
