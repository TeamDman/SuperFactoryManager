package vswe.superfactory.components;

import net.minecraft.util.EnumFacing;
import vswe.superfactory.blocks.ConnectionBlockType;
import vswe.superfactory.components.internal.ConnectionOption;
import vswe.superfactory.components.internal.SlotInventoryHolder;
import vswe.superfactory.tiles.TileEntityInput;

import java.util.EnumSet;
import java.util.List;

public class TriggerHelperRedstone extends TriggerHelper {
	private int strengthId;

	public TriggerHelperRedstone(int sidesId, int strengthId) {
		super(true, 0, sidesId, ConnectionBlockType.RECEIVER);

		this.strengthId = strengthId;
	}

	@Override
	public void onTrigger(FlowComponent item, EnumSet<ConnectionOption> valid) {
		if (isTriggerPowered(item, true)) {
			valid.add(ConnectionOption.REDSTONE_HIGH);
		}
		if (isTriggerPowered(item, false)) {
			valid.add(ConnectionOption.REDSTONE_LOW);
		}
	}

	@Override
	protected boolean isBlockPowered(FlowComponent component, int power) {
		ComponentMenuRedstoneStrength menuStrength = (ComponentMenuRedstoneStrength) component.getMenus().get(strengthId);
		boolean                       inRange      = menuStrength.getLow() <= power && power <= menuStrength.getHigh();

		return inRange != menuStrength.isInverted();
	}

	public void onRedstoneTrigger(FlowComponent item, TileEntityInput inputTrigger) {
		List<SlotInventoryHolder> receivers = CommandExecutor.getContainers(item.getManager(), item.getMenus().get(containerId), blockType);

		if (receivers != null) {
			ComponentMenuContainer componentMenuContainer = (ComponentMenuContainer) item.getMenus().get(containerId);
			int[]                  newPower               = new int[EnumFacing.values().length];
			int[]                  oldPower               = new int[EnumFacing.values().length];
			if (canUseMergedDetection && componentMenuContainer.getOption() == 0) {
				for (SlotInventoryHolder receiver : receivers) {
					TileEntityInput input = receiver.getReceiver();

					for (int i = 0; i < newPower.length; i++) {
						newPower[i] = Math.min(15, newPower[i] + input.getData()[i]);
						oldPower[i] = Math.min(15, oldPower[i] + input.getOldData()[i]);
					}
				}
				if (isPulseReceived(item, newPower, oldPower, true)) {
					activateTrigger(item, EnumSet.of(ConnectionOption.REDSTONE_PULSE_HIGH));
				}
				if (isPulseReceived(item, newPower, oldPower, false)) {
					activateTrigger(item, EnumSet.of(ConnectionOption.REDSTONE_PULSE_LOW));
				}
			} else {
				TileEntityInput trigger = (componentMenuContainer.getOption() == 0 || (componentMenuContainer.getOption() == 1 && canUseMergedDetection)) ? inputTrigger : null;
				if (isPulseReceived(item, receivers, trigger, true)) {
					activateTrigger(item, EnumSet.of(ConnectionOption.REDSTONE_PULSE_HIGH));
				}

				if (isPulseReceived(item, receivers, trigger, false)) {
					activateTrigger(item, EnumSet.of(ConnectionOption.REDSTONE_PULSE_LOW));
				}
			}
		}
	}
}
