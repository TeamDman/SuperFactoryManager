package vswe.superfactory.components.internal;

import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;

public class SideSlotTarget {
	private EnumFacing    side;
	private List<Integer> slots = new ArrayList<>();

	public SideSlotTarget(EnumFacing side) {
		this.side = side;
	}

	public void addSlot(int slot) {
		slots.add(slot);
	}

	public EnumFacing getSide() {
		return side;
	}

	public List<Integer> getSlots() {
		return slots;
	}
}
