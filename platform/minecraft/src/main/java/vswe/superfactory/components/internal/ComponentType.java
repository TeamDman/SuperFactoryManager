package vswe.superfactory.components.internal;

import vswe.superfactory.Localization;
import vswe.superfactory.components.*;

import java.util.EnumSet;

public enum ComponentType {
	TRIGGER(0, Localization.TRIGGER_SHORT, Localization.TRIGGER_LONG,
			new ConnectionSet[]{ConnectionSet.CONTINUOUSLY, ConnectionSet.REDSTONE, ConnectionSet.BUD},
			ComponentMenuReceivers.class, ComponentMenuBUDs.class, ComponentMenuInterval.class, ComponentMenuRedstoneSidesTrigger.class, ComponentMenuRedstoneStrength.class, ComponentMenuUpdateBlock.class, ComponentMenuResult.class),
	INPUT(1, Localization.INPUT_SHORT, Localization.INPUT_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuInventory.class, ComponentMenuTargetInventory.class, ComponentMenuItem.class, ComponentMenuResult.class),
	OUTPUT(2, Localization.OUTPUT_SHORT, Localization.OUTPUT_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuInventory.class, ComponentMenuTargetInventory.class, ComponentMenuItemOutput.class, ComponentMenuResult.class),
	CONDITION(3, Localization.CONDITION_SHORT, Localization.CONDITION_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD_CONDITION},
			ComponentMenuInventoryCondition.class, ComponentMenuTargetInventory.class, ComponentMenuItemCondition.class, ComponentMenuResult.class),
	FLOW_CONTROL(4, Localization.FLOW_CONTROL_SHORT, Localization.FLOW_CONTROL_LONG,
			new ConnectionSet[]{ConnectionSet.MULTIPLE_INPUT_2, ConnectionSet.MULTIPLE_INPUT_5, ConnectionSet.MULTIPLE_OUTPUT_2, ConnectionSet.MULTIPLE_OUTPUT_5},
			ComponentMenuSplit.class, ComponentMenuResult.class),
	FLUID_INPUT(5, Localization.FLUID_INPUT_SHORT, Localization.FLUID_INPUT_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuTank.class, ComponentMenuTargetTank.class, ComponentMenuFluid.class, ComponentMenuResult.class),
	FLUID_OUTPUT(6, Localization.FLUID_OUTPUT_SHORT, Localization.FLUID_OUTPUT_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuTank.class, ComponentMenuTargetTank.class, ComponentMenuFluidOutput.class, ComponentMenuResult.class),
	FLUID_CONDITION(7, Localization.FLUID_CONDITION_SHORT, Localization.FLUID_CONDITION_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD_CONDITION},
			ComponentMenuTankCondition.class, ComponentMenuTargetTank.class, ComponentMenuFluidCondition.class, ComponentMenuResult.class),
	REDSTONE_EMITTER(8, Localization.REDSTONE_EMITTER_SHORT, Localization.REDSTONE_EMITTER_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuEmitters.class, ComponentMenuRedstoneSidesEmitter.class, ComponentMenuRedstoneOutput.class, ComponentMenuPulse.class, ComponentMenuResult.class),
	REDSTONE_CONDITION(9, Localization.REDSTONE_CONDITION_SHORT, Localization.REDSTONE_CONDITION_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD_CONDITION},
			ComponentMenuNodes.class, ComponentMenuRedstoneSidesNodes.class, ComponentMenuRedstoneStrengthNodes.class, ComponentMenuResult.class),
	VARIABLE(10, Localization.CONTAINER_VARIABLE_SHORT, Localization.CONTAINER_VARIABLE_LONG,
			new ConnectionSet[]{ConnectionSet.EMPTY, ConnectionSet.STANDARD},
			ComponentMenuVariable.class, ComponentMenuContainerTypesVariable.class, ComponentMenuVariableContainers.class, ComponentMenuListOrderVariable.class, ComponentMenuResult.class),
	FOR_EACH(11, Localization.FOR_EACH_LOOP_SHORT, Localization.FOR_EACH_LOOP_LONG,
			new ConnectionSet[]{ConnectionSet.FOR_EACH},
			ComponentMenuVariableLoop.class, ComponentMenuContainerTypes.class, ComponentMenuListOrder.class, ComponentMenuResult.class),
	AUTO_CRAFTING(12, Localization.AUTO_CRAFTER_SHORT, Localization.AUTO_CRAFTER_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuCrafting.class, ComponentMenuCraftingPriority.class, ComponentMenuContainerScrap.class, ComponentMenuResult.class),
	GROUP(13, Localization.GROUP_SHORT, Localization.GROUP_LONG,
			new ConnectionSet[]{ConnectionSet.DYNAMIC},
			ComponentMenuGroup.class, ComponentMenuResult.class),
	NODE(14, Localization.NODE_SHORT, Localization.NODE_LONG,
			new ConnectionSet[]{ConnectionSet.INPUT_NODE, ConnectionSet.OUTPUT_NODE},
			ComponentMenuResult.class),
	CAMOUFLAGE(15, Localization.CAMOUFLAGE_SHORT, Localization.CAMOUFLAGE_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuCamouflage.class, ComponentMenuCamouflageShape.class, ComponentMenuCamouflageInside.class, ComponentMenuCamouflageSides.class, ComponentMenuCamouflageItems.class, ComponentMenuResult.class),
	SIGN(16, Localization.SIGN_SHORT, Localization.SIGN_LONG,
			new ConnectionSet[]{ConnectionSet.STANDARD},
			ComponentMenuSigns.class, ComponentMenuSignText.class, ComponentMenuResult.class);


	private Class<? extends ComponentMenu>[] classes;
	private int                              id;
	private Localization                     longName;
	private Localization                     name;
	private ConnectionSet[]                  sets;

	//todo: investigate sets param => EnumSet<ConnectionSet> sets
	ComponentType(int id, Localization name, Localization longName, ConnectionSet[] sets, Class<? extends ComponentMenu>... classes) {
		this.classes = classes;
		this.id = id;
		this.sets = sets;
		this.name = name;
		this.longName = longName;
	}

	public static ComponentType getTypeFromId(int id) {
		for (ComponentType componentType : values()) {
			if (id == componentType.id) {
				return componentType;
			}
		}
		return null;
	}

	public Class<? extends ComponentMenu>[] getClasses() {
		return classes;
	}

	public int getId() {
		return id;
	}

	public ConnectionSet[] getSets() {
		return sets;
	}

	@Override
	public String toString() {
		return getName() + "[" + getLongName() + "]";
	}

	public String getName() {
		return name.toString();
	}

	public String getLongName() {
		return longName.toString();
	}

	public Localization getLongUnLocalizedName() {
		return longName;
	}
}
