package vswe.superfactory.components;

import vswe.superfactory.blocks.ConnectionBlockType;
import vswe.superfactory.components.internal.ConnectionOption;
import vswe.superfactory.components.internal.SlotInventoryHolder;
import vswe.superfactory.tiles.TileEntityBUD;

import java.util.EnumSet;
import java.util.List;


public class TriggerHelperBUD extends TriggerHelper {
	public static final int TRIGGER_BUD_BLOCK_ID = 5;

	public TriggerHelperBUD() {
		super(false, 1, 3, ConnectionBlockType.BUD);
	}

	@Override
	public void onTrigger(FlowComponent item, EnumSet<ConnectionOption> valid) {
		List<SlotInventoryHolder> buds = CommandExecutor.getContainers(item.getManager(), item.getMenus().get(containerId), blockType);

		if (buds != null) {
			for (SlotInventoryHolder bud : buds) {
				bud.getBUD().updateData();
			}

			if (isSpecialPulseReceived(item, true)) {
				valid.add(ConnectionOption.BUD_PULSE_HIGH);
				valid.add(ConnectionOption.BUD_HIGH);
			} else if (isTriggerPowered(item, true)) {
				valid.add(ConnectionOption.BUD_HIGH);
			}

			if (isSpecialPulseReceived(item, false)) {
				valid.add(ConnectionOption.BUD_PULSE_LOW);
				valid.add(ConnectionOption.BUD_LOW);
			} else if (isTriggerPowered(item, false)) {
				valid.add(ConnectionOption.BUD_LOW);
			}


			for (SlotInventoryHolder bud : buds) {
				bud.getBUD().makeOld();
			}
		}

	}

	@Override
	protected boolean isBlockPowered(FlowComponent component, int power) {
		int id   = power >>> 4;
		int meta = power & 15;


		ComponentMenuUpdateBlock updateMenu = (ComponentMenuUpdateBlock) component.getMenus().get(TRIGGER_BUD_BLOCK_ID);

		if (updateMenu.useId()) {
			boolean idMatch = id == updateMenu.getBlockId();

			if (updateMenu.isIdInverted() == idMatch) {
				return false;
			}
		}

		for (ComponentMenuUpdateBlock.MetaSetting setting : updateMenu.getMetaSettings()) {
			if (setting.inUse()) {
				int count       = 0;
				int settingMeta = 0;
				for (int i = 0; i < setting.bits.length; i++) {
					if (setting.bits[i]) {
						settingMeta |= ((meta >> i) & 1) << count;
						count++;
					}
				}
				boolean metaMatch = setting.lowerTextBox.getNumber() <= settingMeta && settingMeta <= setting.higherTextBox.getNumber();
				if (setting.inverted == metaMatch) {
					return false;
				}
			}
		}

		return true;
	}

	public void triggerBUD(FlowComponent item, TileEntityBUD tileEntityBUD) {
		List<SlotInventoryHolder> receivers = CommandExecutor.getContainers(item.getManager(), item.getMenus().get(containerId), blockType);

		if (receivers != null) {
			ComponentMenuContainer componentMenuContainer = (ComponentMenuContainer) item.getMenus().get(containerId);

			TileEntityBUD             trigger = componentMenuContainer.getOption() == 0 ? tileEntityBUD : null;
			EnumSet<ConnectionOption> valid   = EnumSet.noneOf(ConnectionOption.class);
			if (isTriggerPowered(item, true)) {
				valid.add(ConnectionOption.BUD);

				if (isPulseReceived(item, receivers, trigger, true)) {
					valid.add(ConnectionOption.BUD_PULSE_HIGH);
				}
			} else if (isPulseReceived(item, receivers, trigger, false)) {
				valid.add(ConnectionOption.BUD_PULSE_LOW);
			}

			if (!valid.isEmpty()) {
				activateTrigger(item, valid);
			}
		}
	}
}
