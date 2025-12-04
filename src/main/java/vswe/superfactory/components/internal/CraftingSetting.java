package vswe.superfactory.components.internal;


import vswe.superfactory.components.internal.ItemSetting;

public class CraftingSetting extends ItemSetting {
	public CraftingSetting(int id) {
		super(id);
	}

	@Override
	public boolean isAmountSpecific() {
		return false;
	}
}
