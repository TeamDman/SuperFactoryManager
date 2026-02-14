package vswe.superfactory.components.internal;

import vswe.superfactory.Localization;
import vswe.superfactory.components.FlowComponent;

public enum ConnectionOption {
	STANDARD_INPUT(Localization.CONNECTION_INPUT, ConnectionType.INPUT),
	STANDARD_OUTPUT(Localization.CONNECTION_OUTPUT, ConnectionType.OUTPUT),
	INTERVAL(Localization.CONNECTION_INTERVAL, ConnectionType.OUTPUT),
	REDSTONE_PULSE_HIGH(Localization.CONNECTION_ON_HIGH_REDSTONE_PULSE, ConnectionType.OUTPUT),
	REDSTONE_PULSE_LOW(Localization.CONNECTION_ON_LOW_REDSTONE_PULSE, ConnectionType.OUTPUT),
	REDSTONE_HIGH(Localization.CONNECTION_WHILE_HIGH_REDSTONE, ConnectionType.OUTPUT),
	REDSTONE_LOW(Localization.CONNECTION_WHILE_LOW_REDSTONE, ConnectionType.OUTPUT),
	CONDITION_TRUE(Localization.CONNECTION_TRUE, ConnectionType.OUTPUT),
	CONDITION_FALSE(Localization.CONNECTION_FALSE, ConnectionType.OUTPUT),
	FOR_EACH(Localization.CONNECTION_FOR_EACH, ConnectionType.SIDE),
	BUD(Localization.CONNECTION_ON_BLOCK_UPDATE, ConnectionType.OUTPUT),
	BUD_PULSE_HIGH(Localization.CONNECTION_ON_HIGH_BLOCK_PULSE, ConnectionType.OUTPUT),
	BUD_HIGH(Localization.CONNECTION_WHILE_HIGH_BLOCK, ConnectionType.OUTPUT),
	BUD_PULSE_LOW(Localization.CONNECTION_ON_LOW_BLOCK_PULSE, ConnectionType.OUTPUT),
	BUD_LOW(Localization.CONNECTION_WHILE_LOW_BLOCK, ConnectionType.OUTPUT),
	DYNAMIC_INPUT(null, ConnectionType.INPUT),
	DYNAMIC_OUTPUT(null, ConnectionType.OUTPUT);
	private Localization   name;
	private ConnectionType type;


	ConnectionOption(Localization name, ConnectionType type) {
		this.name = name;
		this.type = type;
	}

	public boolean isInput() {
		return type == ConnectionType.INPUT;
	}


	public ConnectionType getType() {
		return type;
	}


	public String getName(FlowComponent component, int id) {

		if (name != null) {
			return name.toString();
		} else if (this == DYNAMIC_INPUT) {
			return id < component.getChildrenInputNodes().size() ? component.getChildrenInputNodes().get(id).getName() : "";
		} else {
			return id < component.getChildrenOutputNodes().size() ? component.getChildrenOutputNodes().get(id).getName() : "";
		}
	}

	public boolean isValid(FlowComponent component, int id) {
		return name != null || (this == DYNAMIC_INPUT ? id < component.getChildrenInputNodes().size() : id < component.getChildrenOutputNodes().size());
	}

	public enum ConnectionType {
		INPUT,
		OUTPUT,
		SIDE
	}
}
