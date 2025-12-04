package vswe.superfactory.components;

import vswe.superfactory.Localization;

import java.util.List;


public class ComponentMenuCamouflageSides extends ComponentMenuRedstoneSides {
	public ComponentMenuCamouflageSides(FlowComponent parent) {
		super(parent);
	}

	@Override
	protected void initRadioButtons() {
		//no options
	}

	@Override
	protected String getMessage() {
		return Localization.CAMOUFLAGE_SIDES_INFO.toString();
	}

	@Override
	public void addErrors(List<String> errors) {
		if (isVisible() && selection == 0) {
			errors.add(Localization.NO_SIDES_ERROR.toString());
		}
	}

	@Override
	public String getName() {
		return Localization.CAMOUFLAGE_SIDES_NAME.toString();
	}

	@Override
	public boolean isVisible() {
		return true;
	}
}
