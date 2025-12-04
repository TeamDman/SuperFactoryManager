package vswe.superfactory.components.internal;

import net.minecraft.item.ItemStack;
import vswe.superfactory.components.FlowComponent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ItemBufferElement implements IItemBufferElement {
	private FlowComponent                      component;
	private int                                currentStackSize;
	private boolean                            fairShare;
	private List<SlotStackInventoryHolder>     holders;
	private SlotInventoryHolder                inventoryHolder;
	private Iterator<SlotStackInventoryHolder> iterator;
	private ItemSetting                        setting;
	private int                                shareId;
	private int                                sharedBy;
	private int                                totalStackSize;
	private boolean                            useWhiteList;


	public ItemBufferElement(FlowComponent owner, Setting setting, SlotInventoryHolder inventoryHolder, boolean useWhiteList, SlotStackInventoryHolder target) {
		this(owner, setting, inventoryHolder, useWhiteList);
		addTarget(target);
		sharedBy = 1;
	}

	private ItemBufferElement(FlowComponent owner, Setting setting, SlotInventoryHolder inventoryHolder, boolean useWhiteList) {
		this.component = owner;
		this.setting = (ItemSetting) setting;
		this.inventoryHolder = inventoryHolder;
		this.useWhiteList = useWhiteList;
		holders = new ArrayList<SlotStackInventoryHolder>();

	}

	private void addTarget(SlotStackInventoryHolder target) {
		holders.add(target);

		totalStackSize += target.getSizeRemaining();
		currentStackSize = totalStackSize;
	}

	public boolean addTarget(FlowComponent owner, Setting setting, SlotInventoryHolder inventoryHolder, SlotStackInventoryHolder target) {
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

	public List<SlotStackInventoryHolder> getSubElements() {
		return holders;
	}

	@Override
	public void prepareSubElements() {
		iterator = holders.iterator();
	}

	@Override
	public IItemBufferSubElement getSubElement() {
		if (iterator.hasNext()) {
			return iterator.next();
		} else {
			return null;
		}
	}

	@Override
	public void removeSubElement() {
		iterator.remove();
	}

	public int retrieveItemCount(int desiredItemCount) {
		if (setting == null || !setting.isLimitedByAmount()) {
			return desiredItemCount;
		} else {
			int itemsAllowedToBeMoved;
			if (useWhiteList) {
				int movedItems = totalStackSize - currentStackSize;
//				itemsAllowedToBeMoved = setting.getItem().getCount() - movedItems;
				itemsAllowedToBeMoved = setting.getAmount() - movedItems;

				int amountLeft = itemsAllowedToBeMoved % sharedBy;
				itemsAllowedToBeMoved /= sharedBy;

				if (!fairShare) {
					if (shareId < amountLeft) {
						itemsAllowedToBeMoved++;
					}
				}
			} else {
				itemsAllowedToBeMoved = currentStackSize - setting.getItem().getCount();
//				itemsAllowedToBeMoved = setting.getAmount();
			}


			return Math.min(itemsAllowedToBeMoved, desiredItemCount);
		}
	}

	public void decreaseStackSize(int itemsToMove) {
		currentStackSize -= itemsToMove * (useWhiteList ? sharedBy : 1);
	}

	@Override
	public void releaseSubElements() {
		iterator = null;
	}

	public ItemStack getItemStack() {
		if (setting != null && !setting.getItem().isEmpty()) {
			return setting.getItem();
		} else {
			return holders.get(0).getItemStack();
		}
	}

	public int getBufferSize(Setting outputSetting) {
		int bufferSize = 0;
		if (setting != null) {
			for (SlotStackInventoryHolder holder : holders) {
				ItemStack item = holder.getItemStack();
				if (setting.isEqualForCommandExecutor(item)) {
					bufferSize += item.getCount();
				}
			}

			if (setting.isLimitedByAmount()) {
				int maxSize;
				if (useWhiteList) {
					maxSize = setting.getItem().getCount();
				} else {
					maxSize = totalStackSize - setting.getItem().getCount();
				}
				bufferSize = Math.min(bufferSize, maxSize);
			}
		}
		return bufferSize;
	}

	public ItemBufferElement getSplitElement(int elementAmount, int id, boolean fair) {
		ItemBufferElement element = new ItemBufferElement(this.component, this.setting, this.inventoryHolder, this.useWhiteList);
		element.holders = new ArrayList<SlotStackInventoryHolder>();
		for (SlotStackInventoryHolder holder : holders) {
			element.addTarget((holder).getSplitElement(elementAmount, id, fair));
		}
		if (useWhiteList) {
			element.sharedBy = sharedBy * elementAmount;
			element.fairShare = fair;
			element.shareId = elementAmount * shareId + id;
			element.currentStackSize -= totalStackSize - currentStackSize;
			if (element.currentStackSize < 0) {
				element.currentStackSize = 0;
			}
		} else {
			element.currentStackSize = Math.min(currentStackSize, element.totalStackSize);
		}
		return element;
	}
}
