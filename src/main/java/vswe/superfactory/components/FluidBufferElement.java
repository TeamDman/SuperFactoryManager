package vswe.superfactory.components;

import net.minecraftforge.fluids.FluidStack;
import vswe.superfactory.components.internal.FluidSetting;
import vswe.superfactory.components.internal.Setting;
import vswe.superfactory.components.internal.SlotInventoryHolder;
import vswe.superfactory.components.internal.StackTankHolder;

import java.util.ArrayList;
import java.util.List;

public class FluidBufferElement {
	private FlowComponent         component;
	private int                   currentTransferSize;
	private boolean               fairShare;
	private List<StackTankHolder> holders;
	private SlotInventoryHolder   inventoryHolder;
	private Setting               setting;
	private int                   shareId;
	private int                   sharedBy;
	private int                   totalTransferSize;
	private boolean               useWhiteList;

	public FluidBufferElement(FlowComponent owner, Setting setting, SlotInventoryHolder inventoryHolder, boolean useWhiteList, StackTankHolder target) {
		this(owner, setting, inventoryHolder, useWhiteList);
		addTarget(target);
		sharedBy = 1;
	}

	public FluidBufferElement(FlowComponent owner, Setting setting, SlotInventoryHolder inventoryHolder, boolean useWhiteList) {
		this.component = owner;
		this.setting = setting;
		this.inventoryHolder = inventoryHolder;
		this.useWhiteList = useWhiteList;
		holders = new ArrayList<StackTankHolder>();
	}

	private void addTarget(StackTankHolder target) {
		holders.add(target);

		FluidStack temp = target.getFluidStack();
		if (temp != null) {
			totalTransferSize += target.getSizeLeft();
			currentTransferSize = totalTransferSize;
		}
	}

	public boolean addTarget(FlowComponent owner, Setting setting, SlotInventoryHolder inventoryHolder, StackTankHolder target) {

		if (component.getId() == owner.getId() && (this.setting == null || (setting != null && this.setting.getId() == setting.getId())) && (this.inventoryHolder.isShared() || this.inventoryHolder.equals(inventoryHolder))) {
			addTarget(target);
			return true;
		} else {
			return false;
		}
	}

	public Setting getSetting() {
		return setting;
	}

	public int retrieveItemCount(int desiredItemCount) {
		if (setting == null || !setting.isLimitedByAmount()) {
			return desiredItemCount;
		} else {
			int itemsAllowedToBeMoved;
			if (useWhiteList) {
				int movedItems = totalTransferSize - currentTransferSize;
				itemsAllowedToBeMoved = setting.getAmount() - movedItems;

				int amountLeft = itemsAllowedToBeMoved % sharedBy;
				itemsAllowedToBeMoved /= sharedBy;

				if (!fairShare) {
					if (shareId < amountLeft) {
						itemsAllowedToBeMoved++;
					}
				}
			} else {
				itemsAllowedToBeMoved = currentTransferSize - setting.getAmount();
			}


			return Math.min(itemsAllowedToBeMoved, desiredItemCount);
		}
	}

	public void decreaseStackSize(int itemsToMove) {
		currentTransferSize -= itemsToMove * (useWhiteList ? sharedBy : 1);
	}

	public int getBufferSize(Setting outputSetting) {
		int bufferSize = 0;

		for (StackTankHolder holder : getHolders()) {
			FluidStack fluidStack = holder.getFluidStack();
			if (fluidStack != null && fluidStack.getFluid().getName().equals(((FluidSetting) outputSetting).getFluidName())) {
				bufferSize += fluidStack.amount;
			}
		}
		if (setting != null && setting.isLimitedByAmount()) {
			int maxSize;
			if (useWhiteList) {
				maxSize = setting.getAmount();
			} else {
				maxSize = totalTransferSize - setting.getAmount();
			}
			bufferSize = Math.min(bufferSize, maxSize);
		}
		return bufferSize;
	}

	public List<StackTankHolder> getHolders() {
		return holders;
	}

	public FluidBufferElement getSplitElement(int elementAmount, int id, boolean fair) {

		FluidBufferElement element = new FluidBufferElement(this.component, this.setting, this.inventoryHolder, this.useWhiteList);
		element.holders = new ArrayList<StackTankHolder>();
		for (StackTankHolder holder : holders) {
			element.addTarget(holder.getSplitElement(elementAmount, id, fair));
		}
		if (useWhiteList) {
			element.sharedBy = sharedBy * elementAmount;
			element.fairShare = fair;
			element.shareId = elementAmount * shareId + id;
			element.currentTransferSize -= totalTransferSize - currentTransferSize;
			if (element.currentTransferSize < 0) {
				element.currentTransferSize = 0;
			}
		} else {
			element.currentTransferSize = Math.min(currentTransferSize, element.totalTransferSize);
		}

		return element;
	}
}
