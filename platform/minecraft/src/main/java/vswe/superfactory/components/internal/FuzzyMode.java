package vswe.superfactory.components.internal;

import vswe.superfactory.Localization;

public enum FuzzyMode {
	PRECISE(Localization.DETECTION_PRECISE, true),
	NBT_FUZZY(Localization.DETECTION_NBT_FUZZY, true),
	FUZZY(Localization.DETECTION_FUZZY, false),
	ORE_DICTIONARY(Localization.DETECTION_ORE_DICTIONARY, true),
	MOD_GROUPING(Localization.MOD_GROUPING, false),
	ALL(Localization.ALL_ITEMS, false);

	private Localization text;
	private boolean      useMeta;

	FuzzyMode(Localization text, boolean useMeta) {
		this.text = text;
		this.useMeta = useMeta;
	}

	@Override
	public String toString() {
		return text.toString();
	}


	public boolean requiresMetaData() {
		return useMeta;
	}
}
