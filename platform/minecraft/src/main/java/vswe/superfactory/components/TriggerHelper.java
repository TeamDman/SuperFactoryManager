package vswe.superfactory.components;

import net.minecraft.util.EnumFacing;
import vswe.superfactory.blocks.ConnectionBlockType;
import vswe.superfactory.blocks.IRedstoneNode;
import vswe.superfactory.blocks.ITriggerNode;
import vswe.superfactory.components.internal.ConnectionOption;
import vswe.superfactory.components.internal.SlotInventoryHolder;

import java.util.EnumSet;
import java.util.List;

public abstract class TriggerHelper {
	public static final int TRIGGER_INTERVAL_ID = 2;
	protected ConnectionBlockType blockType;
	protected boolean             canUseMergedDetection;
	protected int                 containerId;
	protected int                 sidesId;

	protected TriggerHelper(boolean canUseMergedDetection, int containerId, int sidesId, ConnectionBlockType blockType) {
		this.canUseMergedDetection = canUseMergedDetection;
		this.containerId = containerId;
		this.sidesId = sidesId;
		this.blockType = blockType;
	}

	public abstract void onTrigger(FlowComponent item, EnumSet<ConnectionOption> valid);

	protected boolean hasRedStoneFlipped(FlowComponent component, int[] newPower, int[] oldPower, boolean high) {
		ComponentMenuRedstoneSides menuRedstone = (ComponentMenuRedstoneSides) component.getMenus().get(sidesId);

		for (int i = 0; i < oldPower.length; i++) {
			if (menuRedstone.isSideRequired(i)) {
				if ((high && !isBlockPowered(component, oldPower[i]) && isBlockPowered(component, newPower[i])) || (!high && isBlockPowered(component, oldPower[i]) && !isBlockPowered(component, newPower[i]))) {
					return true;
				}
			}
		}
		return false;
	}

	protected boolean isPulseReceived(FlowComponent component, int[] newPower, int[] oldPower, boolean high) {
		return hasRedStoneFlipped(component, newPower, oldPower, high) && isTriggerPowered(component, newPower, high);
	}

	protected boolean isPulseReceived(FlowComponent component, List<SlotInventoryHolder> containers, ITriggerNode trigger, boolean high) {
		boolean requiresAll = trigger != null;
		for (SlotInventoryHolder container : containers) {
			ITriggerNode input = container.getTrigger();

			boolean flag;
			if (input.equals(trigger) || !requiresAll) {
				flag = isPulseReceived(component, input.getData(), input.getOldData(), high);
			} else {
				flag = isTriggerPowered(component, input.getData(), high);
			}

			if (flag) {
				if (!requiresAll) {
					return true;
				}
			} else if (requiresAll) {
				return false;
			}
		}

		return requiresAll;
	}

	protected boolean isSpecialPulseReceived(FlowComponent component, boolean high) {
		List<SlotInventoryHolder> containers = CommandExecutor.getContainers(component.getManager(), component.getMenus().get(containerId), blockType);

		if (containers != null) {
			ComponentMenuContainer componentMenuContainer = (ComponentMenuContainer) component.getMenus().get(containerId);

			boolean requiresAll = componentMenuContainer.getOption() == 0;
			boolean foundPulse  = false;

			for (SlotInventoryHolder container : containers) {
				ITriggerNode input = container.getTrigger();

				boolean flag;

				flag = isPulseReceived(component, input.getData(), input.getOldData(), high);
				if (flag) {
					foundPulse = true;
				} else {
					flag = isTriggerPowered(component, input.getData(), high);
				}

				if (foundPulse) {
					if (!requiresAll) {
						return true;
					}
				} else if (requiresAll && !flag) {
					return false;
				}
			}

			return requiresAll && foundPulse;
		} else {
			return false;
		}
	}

	protected boolean isTriggerPowered(FlowComponent item, boolean high) {
		List<SlotInventoryHolder> receivers = CommandExecutor.getContainers(item.getManager(), item.getMenus().get(containerId), blockType);

		return receivers != null && isTriggerPowered(receivers, item, high);
	}

	public boolean isTriggerPowered(List<SlotInventoryHolder> receivers, FlowComponent component, boolean high) {
		ComponentMenuContainer menuContainer = (ComponentMenuContainer) component.getMenus().get(containerId);
		if (canUseMergedDetection && menuContainer.getOption() == 0) {
			int[] currentPower = new int[EnumFacing.values().length];
			for (SlotInventoryHolder receiver : receivers) {
				IRedstoneNode node = receiver.getNode();
				for (int i = 0; i < currentPower.length; i++) {
					currentPower[i] = Math.min(15, currentPower[i] + node.getPower()[i]);
				}
			}

			return isTriggerPowered(component, currentPower, high);
		} else {
			boolean requiresAll = menuContainer.getOption() == 0 || (menuContainer.getOption() == 1 && canUseMergedDetection);
			for (SlotInventoryHolder receiver : receivers) {
				int[] data;
				if (receiver.getTile() instanceof ITriggerNode) {
					data = receiver.getTrigger().getData();
				} else {
					data = receiver.getNode().getPower();
				}

				if (isTriggerPowered(component, data, high)) {
					if (!requiresAll) {
						return true;
					}
				} else {
					if (requiresAll) {
						return false;
					}
				}
			}
			return requiresAll;
		}
	}

	protected boolean isTriggerPowered(FlowComponent component, int[] currentPower, boolean high) {
		ComponentMenuRedstoneSidesTrigger menuSides = (ComponentMenuRedstoneSidesTrigger) component.getMenus().get(sidesId);
		for (int i = 0; i < currentPower.length; i++) {
			if (menuSides.isSideRequired(i)) {
				if (isBlockPowered(component, currentPower[i]) == high) {
					if (!menuSides.requireAll()) {
						return true;
					}
				} else if (menuSides.requireAll()) {
					return false;
				}
			}
		}
		return menuSides.requireAll();
	}

	protected abstract boolean isBlockPowered(FlowComponent component, int power);

	protected void activateTrigger(FlowComponent item, EnumSet<ConnectionOption> types) {
		item.getManager().activateTrigger(item, types);
	}
}
