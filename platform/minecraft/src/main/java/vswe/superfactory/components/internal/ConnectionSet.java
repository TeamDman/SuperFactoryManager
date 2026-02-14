package vswe.superfactory.components.internal;


import vswe.superfactory.Localization;

public enum ConnectionSet {
	STANDARD(Localization.CONNECTION_SET_STANDARD, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_OUTPUT),
	CONTINUOUSLY(Localization.CONNECTION_SET_INTERVAL, ConnectionOption.INTERVAL),
	REDSTONE(Localization.CONNECTION_SET_REDSTONE, ConnectionOption.REDSTONE_PULSE_HIGH, ConnectionOption.REDSTONE_HIGH, ConnectionOption.REDSTONE_LOW, ConnectionOption.REDSTONE_PULSE_LOW),
	STANDARD_CONDITION(Localization.CONNECTION_SET_CONDITION, ConnectionOption.STANDARD_INPUT, ConnectionOption.CONDITION_TRUE, ConnectionOption.CONDITION_FALSE),
	MULTIPLE_INPUT_2(Localization.CONNECTION_SET_COLLECTOR_2, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_OUTPUT),
	MULTIPLE_INPUT_5(Localization.CONNECTION_SET_COLLECTOR_5, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_OUTPUT),
	MULTIPLE_OUTPUT_2(Localization.CONNECTION_SET_SPLIT_2, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_OUTPUT, ConnectionOption.STANDARD_OUTPUT),
	MULTIPLE_OUTPUT_5(Localization.CONNECTION_SET_SPLIT_5, ConnectionOption.STANDARD_INPUT, ConnectionOption.STANDARD_OUTPUT, ConnectionOption.STANDARD_OUTPUT, ConnectionOption.STANDARD_OUTPUT, ConnectionOption.STANDARD_OUTPUT, ConnectionOption.STANDARD_OUTPUT),
	EMPTY(Localization.CONNECTION_SET_DECLARATION),
	FOR_EACH(Localization.CONNECTION_SET_FOR_EACH, ConnectionOption.STANDARD_INPUT, ConnectionOption.FOR_EACH, ConnectionOption.STANDARD_OUTPUT),
	BUD(Localization.CONNECTION_SET_BUD, ConnectionOption.BUD_PULSE_HIGH, ConnectionOption.BUD_HIGH, ConnectionOption.BUD, ConnectionOption.BUD_LOW, ConnectionOption.BUD_PULSE_LOW),
	OUTPUT_NODE(Localization.CONNECTION_SET_OUTPUT_NODE, ConnectionOption.STANDARD_INPUT),
	INPUT_NODE(Localization.CONNECTION_SET_INPUT_NODE, ConnectionOption.STANDARD_OUTPUT),
	DYNAMIC(Localization.CONNECTION_SET_DYNAMIC, ConnectionOption.DYNAMIC_INPUT, ConnectionOption.DYNAMIC_INPUT, ConnectionOption.DYNAMIC_INPUT, ConnectionOption.DYNAMIC_INPUT, ConnectionOption.DYNAMIC_INPUT, ConnectionOption.DYNAMIC_OUTPUT, ConnectionOption.DYNAMIC_OUTPUT, ConnectionOption.DYNAMIC_OUTPUT, ConnectionOption.DYNAMIC_OUTPUT, ConnectionOption.DYNAMIC_OUTPUT),
	CHAT(Localization.CONNECTION_SET_CHAT, ConnectionOption.STANDARD_OUTPUT);


	private ConnectionOption[] connections;
	private int                inputCount;
	private Localization       name;
	private int                outputCount;
	private int                sideCount;


	ConnectionSet(Localization name, ConnectionOption... connections) {
		this.connections = connections;

		for (ConnectionOption connection : connections) {
			if (connection.isInput()) {
				inputCount++;
			} else if (connection.getType() == ConnectionOption.ConnectionType.OUTPUT) {
				outputCount++;
			} else {
				sideCount++;
			}
		}

		this.name = name;
	}


	public ConnectionOption[] getConnections() {
		return connections;
	}

	public int getOutputCount() {
		return outputCount;
	}

	public int getInputCount() {
		return inputCount;
	}

	public int getSideCount() {
		return sideCount;
	}

	@Override
	public String toString() {
		return name.toString();
	}

	public Localization getName() {
		return name;
	}
}
