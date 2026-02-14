package vswe.superfactory.components;

import ca.teamdman.sfm.common.util.BlockPosSet;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.BlockPane;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;
import vswe.superfactory.blocks.ConnectionBlock;
import vswe.superfactory.blocks.ConnectionBlockType;
import vswe.superfactory.components.internal.*;
import vswe.superfactory.tiles.TileEntityManager;

import java.util.*;
import java.util.stream.Collectors;

public class CommandExecutor {
	public static final int                         MAX_FLUID_TRANSFER = 10000000;
	private final       List<CraftingBufferElement> craftingBufferHigh;
	private final       List<CraftingBufferElement> craftingBufferLow;
	private final       List<FluidBufferElement>    fluidBuffer;
	final               List<ItemBufferElement>     itemBuffer;
	private final       TileEntityManager           manager;
	private final       List<Integer>               usedCommands;


	public CommandExecutor(TileEntityManager manager) {
		this.manager = manager;
		this.itemBuffer = new ArrayList<>();
		this.craftingBufferHigh = new ArrayList<>();
		this.craftingBufferLow = new ArrayList<>();
		this.fluidBuffer = new ArrayList<>();
		this.usedCommands = new ArrayList<>();
	}

	private CommandExecutor(TileEntityManager manager, List<ItemBufferElement> itemBufferSplit, List<CraftingBufferElement> craftingBufferHighSplit, List<CraftingBufferElement> craftingBufferLowSplit, List<FluidBufferElement> fluidBufferSplit, List<Integer> usedCommandCopy) {
		this.manager = manager;
		this.itemBuffer = itemBufferSplit;
		this.craftingBufferHigh = craftingBufferHighSplit;
		this.craftingBufferLow = craftingBufferLowSplit;
		this.usedCommands = usedCommandCopy;
		this.fluidBuffer = fluidBufferSplit;
	}

	public static List<SlotInventoryHolder> getContainers(TileEntityManager manager, ComponentMenu componentMenu, ConnectionBlockType type) {
		ComponentMenuContainer menuContainer = (ComponentMenuContainer) componentMenu;

		if (menuContainer.getSelectedInventories().size() == 0) {
			return new ArrayList<>();
		}

		List<SlotInventoryHolder> ret         = new ArrayList<>();
		List<ConnectionBlock>     inventories = manager.getConnectedInventories();
        Map<BlockPos,ConnectionBlock> inventoriesMap = manager.getConnectedInventoriesMap();
		Variable[]                variables   = manager.getVariables();
		for (int variableIndex = 0; variableIndex < variables.length; variableIndex++) {
			Variable variable = variables[variableIndex];
			if (variable.isValid()) {
				for (int inventoryIndex : menuContainer.getSelectedInventories()) {
					if (inventoryIndex == variableIndex) { // if we selected that variable
						List<Integer> selection = variable.getContainers();

						for (int selected : selection) {
							addContainer(inventories, ret, selected, menuContainer, type, ((ComponentMenuContainerTypes) variable.getDeclaration().getMenus().get(1)).getValidTypes());
						}
						break;
					}
				}
			}
		}

        var labels = manager.getLabels().getSortedLabelPositions();

		for (int i = 0; i < menuContainer.getSelectedInventories().size(); i++) {
			int baseIndex = menuContainer.getSelectedInventories().get(i);
            int selected = baseIndex - VariableColor.values().length;

			addContainer(inventories, ret, selected, menuContainer, type, EnumSet.allOf(ConnectionBlockType.class));


            if (baseIndex < 0 && labels.size() >= (-baseIndex) ) {
                BlockPosSet positions = labels.get(Math.abs(baseIndex) - 1);
                for (BlockPos.MutableBlockPos pos : positions.blockPosIterator()) {
                    ConnectionBlock connection;
                    if ((connection = inventoriesMap.getOrDefault(pos, null)) != null) {
                        if (connection.isOfType(type) && !connection.getTileEntity().isInvalid() && !containsTe(ret, connection.getTileEntity())) {
                            ret.add(new SlotInventoryHolder(baseIndex, connection.getTileEntity(), menuContainer.getOption()));
                        }
                    }
                }
            }
		}

		if (ret.isEmpty()) {
			return null;
		} else {
			return ret;
		}
	}

	private static void addContainer(List<ConnectionBlock> inventories, List<SlotInventoryHolder> ret, int selected, ComponentMenuContainer menuContainer, ConnectionBlockType requestType, EnumSet<ConnectionBlockType> variableType) {
		if (selected >= 0 && selected < inventories.size()) {
			ConnectionBlock connection = inventories.get(selected);

			if (connection.isOfType(requestType) && connection.isOfAnyType(variableType) && !connection.getTileEntity().isInvalid() && !containsTe(ret, connection.getTileEntity())) {
				ret.add(new SlotInventoryHolder(selected, connection.getTileEntity(), menuContainer.getOption()));
			}
		}
	}

	private static boolean containsTe(List<SlotInventoryHolder> lst, TileEntity te) {
		for (SlotInventoryHolder slotInventoryHolder : lst) {
			if (slotInventoryHolder.getTile().getPos().equals(te.getPos()) && slotInventoryHolder.getTile().getClass().equals(te.getClass())) {
				return true;
			}
		}
		return false;
	}

	public static void prepareValidSlots(ComponentMenu componentMenu, List<SlotInventoryHolder> inventories) {
		ComponentMenuTargetInventory menuTarget = (ComponentMenuTargetInventory) componentMenu;
		for (SlotInventoryHolder holder : inventories) {
			Map<EnumFacing, SideSlotTarget> validSlots = holder.getValidSlots();

			for (EnumFacing side : EnumFacing.VALUES) {
				int          sideI   = side.ordinal();
				IItemHandler handler = holder.getInventory(side);

				if (handler != null && menuTarget.isActive(sideI)) {
					int[] reportedSlots = new int[handler.getSlots()];
					for (int j = 0; j < reportedSlots.length; j++) {
						reportedSlots[j] = j;
					}
					int start = 0;
					int end   = reportedSlots.length;
					if (menuTarget.useAdvancedSetting(sideI)) {
						start = menuTarget.getStart(sideI);
						end = menuTarget.getEnd(sideI);
					}

					if (start > end) {
						continue;
					}

					for (int slot : reportedSlots) {
						if (slot >= start && slot <= end) {
							validSlots.computeIfAbsent(side, SideSlotTarget::new).addSlot(slot);
						}
					}
				}
			}
		}
	}

	public static boolean canExtractStack(IItemHandler handler, int slot) {
		return canExtractStack(handler, slot, handler.getStackInSlot(slot));
	}

	public static boolean canExtractStack(IItemHandler handler, int slot, ItemStack stack) {
		if (!stack.isEmpty()) {
			return !handler.extractItem(slot, stack.getMaxStackSize(), true).isEmpty();
		}
		return false;
	}

	public static boolean canInsertStack(IItemHandler handler, int slot) {
		return canInsertStack(handler, slot, handler.getStackInSlot(slot));
	}

	public static boolean canInsertStack(IItemHandler handler, int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		return handler.insertItem(slot, stack, true).getCount() != stack.getCount();
	}

	public void executeTriggerCommand(FlowComponent command, EnumSet<ConnectionOption> validTriggerOutputs) {
		for (Variable variable : manager.getVariables()) {
			if (variable.isValid()) {
				if (!variable.hasBeenExecuted() || ((ComponentMenuVariable) variable.getDeclaration().getMenus().get(0)).getVariableMode() == ComponentMenuVariable.VariableMode.LOCAL) {
					executeCommand(variable.getDeclaration(), 0);
					variable.setExecuted(true);
				}
			}
		}
		executeChildCommands(command, validTriggerOutputs);
	}

	private void executeChildCommands(FlowComponent command, EnumSet<ConnectionOption> validTriggerOutputs) {
		for (int i = 0; i < command.getConnectionSet().getConnections().length; i++) {
			Connection       connection = command.getConnection(i);
			ConnectionOption option     = command.getConnectionSet().getConnections()[i];
			if (connection != null && !option.isInput() && validTriggerOutputs.contains(option)) {
				executeCommand(manager.getFlowItems().get(connection.getComponentId()), connection.getConnectionId());
			}
		}
	}

	private void executeCommand(FlowComponent command, int connectionId) {
		//a loop has occurred
		if (usedCommands.contains(command.getId())) {
			return;
		}

		try {
			usedCommands.add(command.getId());
			switch (command.getType()) {
				case INPUT:
					List<SlotInventoryHolder> inputInventory = getInventories(command.getMenus().get(0));
					if (inputInventory != null) {
						prepareValidSlots(command.getMenus().get(1), inputInventory);
						getItems(command.getMenus().get(2), inputInventory);
					}
					break;
				case OUTPUT:
					List<SlotInventoryHolder> outputInventory = getInventories(command.getMenus().get(0));
					if (outputInventory != null) {
						prepareValidSlots(command.getMenus().get(1), outputInventory);
						insertItems(command.getMenus().get(2), outputInventory);
					}
					break;
				case CONDITION:
					List<SlotInventoryHolder> conditionInventory = getInventories(command.getMenus().get(0));
					if (conditionInventory != null) {
						prepareValidSlots(command.getMenus().get(1), conditionInventory);
						if (searchForStuff(command.getMenus().get(2), conditionInventory, false)) {
							executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_TRUE));
						} else {
							executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_FALSE));
						}
					}
					return;
				case FLUID_INPUT:
					List<SlotInventoryHolder> inputTank = getTanks(command.getMenus().get(0));
					if (inputTank != null) {
						getValidTanks(command.getMenus().get(1), inputTank);
						getFluids(command.getMenus().get(2), inputTank);
					}
					break;
				case FLUID_OUTPUT:
					List<SlotInventoryHolder> outputTank = getTanks(command.getMenus().get(0));
					if (outputTank != null) {
						getValidTanks(command.getMenus().get(1), outputTank);
						insertFluids(command.getMenus().get(2), outputTank);
					}
					break;
				case FLUID_CONDITION:
					List<SlotInventoryHolder> conditionTank = getTanks(command.getMenus().get(0));
					if (conditionTank != null) {
						getValidTanks(command.getMenus().get(1), conditionTank);
						if (searchForStuff(command.getMenus().get(2), conditionTank, true)) {
							executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_TRUE));
						} else {
							executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_FALSE));
						}
					}
					return;
				case FLOW_CONTROL:
					if (ComponentMenuSplit.isSplitConnection(command)) {
						if (splitFlow(command.getMenus().get(0))) {
							return;
						}
					}
					break;
				case REDSTONE_EMITTER:
					List<SlotInventoryHolder> emitters = getEmitters(command.getMenus().get(0));
					if (emitters != null) {
						for (SlotInventoryHolder emitter : emitters) {
							emitter.getEmitter().updateState((ComponentMenuRedstoneSidesEmitter) command.getMenus().get(1), (ComponentMenuRedstoneOutput) command.getMenus().get(2), (ComponentMenuPulse) command.getMenus().get(3));
						}
					}
					break;
				case REDSTONE_CONDITION:
					List<SlotInventoryHolder> nodes = getNodes(command.getMenus().get(0));
					if (nodes != null) {
						if (evaluateRedstoneCondition(nodes, command)) {
							executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_TRUE));
						} else {
							executeChildCommands(command, EnumSet.of(ConnectionOption.CONDITION_FALSE));
						}
					}

					return;
				case VARIABLE:
					List<SlotInventoryHolder> tiles = getTiles(command.getMenus().get(2));
					if (tiles != null) {
						updateVariable(tiles, (ComponentMenuVariable) command.getMenus().get(0), (ComponentMenuListOrder) command.getMenus().get(3));
					}
					break;
				case FOR_EACH:
					updateForLoop(command, (ComponentMenuVariableLoop) command.getMenus().get(0), (ComponentMenuContainerTypes) command.getMenus().get(1), (ComponentMenuListOrder) command.getMenus().get(2));
					executeChildCommands(command, EnumSet.of(ConnectionOption.STANDARD_OUTPUT));
					return;
				case AUTO_CRAFTING:
					CraftingBufferElement element = new CraftingBufferElement(this, (ComponentMenuCrafting) command.getMenus().get(0), (ComponentMenuContainerScrap) command.getMenus().get(2));
					if (((ComponentMenuCraftingPriority) command.getMenus().get(1)).shouldPrioritizeCrafting()) {
						craftingBufferHigh.add(element);
					} else {
						craftingBufferLow.add(element);
					}
					break;
				case GROUP:
					if (connectionId < command.getChildrenInputNodes().size()) {
						executeChildCommands(command.getChildrenInputNodes().get(connectionId), EnumSet.allOf(ConnectionOption.class));
					}
					return;
				case NODE:
					FlowComponent parent = command.getParent();
					if (parent != null) {
						for (int i = 0; i < parent.getChildrenOutputNodes().size(); i++) {
							if (command.equals(parent.getChildrenOutputNodes().get(i))) {
								Connection connection = parent.getConnection(parent.getConnectionSet().getInputCount() + i);
								if (connection != null) {
									executeCommand(manager.getFlowItems().get(connection.getComponentId()), connection.getConnectionId());
								}
								break;
							}
						}
					}
					return;
				case CAMOUFLAGE:
					List<SlotInventoryHolder> camouflage = getCamouflage(command.getMenus().get(0));
					if (camouflage != null) {
						ComponentMenuCamouflageShape  shape  = (ComponentMenuCamouflageShape) command.getMenus().get(1);
						ComponentMenuCamouflageInside inside = (ComponentMenuCamouflageInside) command.getMenus().get(2);
						ComponentMenuCamouflageSides  sides  = (ComponentMenuCamouflageSides) command.getMenus().get(3);
						ComponentMenuCamouflageItems  items  = (ComponentMenuCamouflageItems) command.getMenus().get(4);

						if (items.isFirstRadioButtonSelected() || items.getSettings().get(0).isValid()) {
							ItemStack itemStack = items.isFirstRadioButtonSelected() ? ItemStack.EMPTY : ((ItemSetting) items.getSettings().get(0)).getItem();
							for (SlotInventoryHolder slotInventoryHolder : camouflage) {
								slotInventoryHolder.getCamouflage().setBounds(shape);
								for (int i = 0; i < EnumFacing.values().length; i++) {
									if (sides.isSideRequired(i)) {
										slotInventoryHolder.getCamouflage().setItem(itemStack, i, inside.getCurrentType());
									}
								}
							}
						}
					}
					break;
				case SIGN:
					List<SlotInventoryHolder> sign = getSign(command.getMenus().get(0));
					if (sign != null) {
						for (SlotInventoryHolder slotInventoryHolder : sign) {
							slotInventoryHolder.getSign().updateSign((ComponentMenuSignText) command.getMenus().get(1));
						}
					}
			}
			executeChildCommands(command, EnumSet.allOf(ConnectionOption.class));
		} finally {
			usedCommands.remove((Integer) command.getId());
		}
	}

	private List<SlotInventoryHolder> getEmitters(ComponentMenu componentMenu) {
		return getContainers(manager, componentMenu, ConnectionBlockType.EMITTER);
	}

	private List<SlotInventoryHolder> getInventories(ComponentMenu componentMenu) {
		return getContainers(manager, componentMenu, ConnectionBlockType.INVENTORY);
	}

	private List<SlotInventoryHolder> getTanks(ComponentMenu componentMenu) {
		return getContainers(manager, componentMenu, ConnectionBlockType.TANK);
	}

	private List<SlotInventoryHolder> getNodes(ComponentMenu componentMenu) {
		return getContainers(manager, componentMenu, ConnectionBlockType.NODE);
	}

	private List<SlotInventoryHolder> getCamouflage(ComponentMenu componentMenu) {
		return getContainers(manager, componentMenu, ConnectionBlockType.CAMOUFLAGE);
	}

	private List<SlotInventoryHolder> getSign(ComponentMenu componentMenu) {
		return getContainers(manager, componentMenu, ConnectionBlockType.SIGN);
	}

	private List<SlotInventoryHolder> getTiles(ComponentMenu componentMenu) {
		return getContainers(manager, componentMenu, null);
	}

	private void getValidTanks(ComponentMenu componentMenu, List<SlotInventoryHolder> tanks) {
		ComponentMenuTargetTank menuTarget = (ComponentMenuTargetTank) componentMenu;

		for (int i = 0; i < tanks.size(); i++) {
			Map<EnumFacing, SideSlotTarget> validTanks = tanks.get(i).getValidSlots();

			for (EnumFacing side : EnumFacing.VALUES) {
				IFluidHandler tank = tanks.get(i).getTank(side);
				if (tank == null)
					continue;
				int sideI = side.ordinal();
				if (menuTarget.isActive(sideI)) {
					if (menuTarget.useAdvancedSetting(sideI)) {
						boolean empty = true;
						for (IFluidTankProperties fluidTankInfo : tank.getTankProperties()) {
							if (fluidTankInfo.getContents() != null && fluidTankInfo.getContents().amount > 0) {
								empty = false;
								break;
							}
						}

						if (empty != menuTarget.requireEmpty(sideI)) {
							continue;
						}
					}

					//Fluids don't have slots, only sides.
					validTanks.computeIfAbsent(side, SideSlotTarget::new).addSlot(0);
				}
			}
		}
	}

	private boolean isSlotValid(IItemHandler handler, ItemStack stack, int slot, boolean isSource) {
		if (stack.isEmpty()) {// || !handler.isItemValid(slot, stack)) { // unfix #9
			return false;
		} else {
			if (isSource) {
				return canExtractStack(handler, slot, stack);
			} else {
				return canInsertStack(handler, slot, stack);
			}
		}
	}

	private void getItems(ComponentMenu componentMenu, List<SlotInventoryHolder> inventories) {
		for (SlotInventoryHolder inventoryHolder : inventories) {
			ComponentMenuStuff                 menuItem   = (ComponentMenuStuff) componentMenu;
			IdentityHashMap<ItemStack, Object> seenStacks = new IdentityHashMap<>();
			for (SideSlotTarget sideSlotTarget : inventoryHolder.getValidSlots().values()) {
				IItemHandler inventory = inventoryHolder.getInventory(sideSlotTarget.getSide());
				for (int slot : sideSlotTarget.getSlots()) {
					ItemStack itemStack = inventory.getStackInSlot(slot);

					if (seenStacks.containsKey(itemStack) || !isSlotValid(inventory, itemStack, slot, true)) {
						continue;
					}
					seenStacks.put(itemStack, null);

					Setting setting = getStackSetting(componentMenu, itemStack);
					addItemToBuffer(menuItem, inventoryHolder, setting, itemStack, sideSlotTarget.getSide(), slot);
				}
			}
		}
	}

	private void addItemToBuffer(ComponentMenuStuff menuItem, SlotInventoryHolder inventory, Setting setting, ItemStack itemStack, EnumFacing side, int slot) {
		if ((menuItem.useWhiteList() == (setting != null)) || (setting != null && setting.isLimitedByAmount())) {
			FlowComponent            owner  = menuItem.getParent();
			SlotStackInventoryHolder target = new SlotStackInventoryHolder(itemStack, inventory.getInventory(side), slot);

			boolean added = false;
			for (ItemBufferElement itemBufferElement : itemBuffer) {
				if (itemBufferElement.addTarget(owner, setting, inventory, target)) {
					added = true;
					break;
				}
			}

			if (!added) {
				ItemBufferElement itemBufferElement = new ItemBufferElement(owner, setting, inventory, menuItem.useWhiteList(), target);
				itemBuffer.add(itemBufferElement);
			}
		}
	}

	private void getFluids(ComponentMenu componentMenu, List<SlotInventoryHolder> tanks) {
		for (SlotInventoryHolder inventoryHolder : tanks) {
			ComponentMenuStuff         menuItem  = (ComponentMenuStuff) componentMenu;
			List<IFluidTankProperties> tankInfos = new ArrayList<IFluidTankProperties>();
			//Fluids don't have slots, only sides.
			for (EnumFacing side : inventoryHolder.getValidSlots().keySet()) {
				try {
					IFluidHandler tank = inventoryHolder.getTank(side);
					//This is null when used with EnderIO Tanks??
					IFluidTankProperties[] currentTankInfos = tank.getTankProperties();
					if (currentTankInfos == null) {
						continue;
					}
					for (IFluidTankProperties fluidTankInfo : currentTankInfos) {
						if (fluidTankInfo == null) {
							continue;
						}
						boolean alreadyUsed = false;
						for (IFluidTankProperties tankInfo : tankInfos) {
							if (FluidStack.areFluidStackTagsEqual(tankInfo.getContents(), fluidTankInfo.getContents()) && tankInfo.getCapacity() == fluidTankInfo.getCapacity()) {
								alreadyUsed = true;
							}
						}
						if (alreadyUsed) {
							continue;
						}
						FluidStack fluidStack = fluidTankInfo.getContents();
						if (fluidStack == null) {
							continue;
						}
						fluidStack = fluidStack.copy();
						Setting setting = getFluidSetting(componentMenu, fluidStack);
						addFluidToBuffer(menuItem, inventoryHolder, tank, setting, fluidStack, side);
					}
					for (IFluidTankProperties fluidTankInfo : tank.getTankProperties()) {
						if (fluidTankInfo != null) {
							tankInfos.add(fluidTankInfo);
						}
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	private void addFluidToBuffer(ComponentMenuStuff menuItem, SlotInventoryHolder inventoryHolder, IFluidHandler tank, Setting setting, FluidStack fluidStack, EnumFacing side) {
		if ((menuItem.useWhiteList() == (setting != null)) || (setting != null && setting.isLimitedByAmount())) {
			FlowComponent   owner  = menuItem.getParent();
			StackTankHolder target = new StackTankHolder(fluidStack, tank, side);

			boolean added = false;
			for (FluidBufferElement fluidBufferElement : fluidBuffer) {
				if (fluidBufferElement.addTarget(owner, setting, inventoryHolder, target)) {
					added = true;
					break;
				}
			}

			if (!added) {
				FluidBufferElement itemBufferElement = new FluidBufferElement(owner, setting, inventoryHolder, menuItem.useWhiteList(), target);
				fluidBuffer.add(itemBufferElement);
			}
		}
	}

	/**
	 * Gets the setting that applies to the given stack.
	 * Used to be named isItemValid.
	 *
	 * @param componentMenu component containing settings to pick from
	 * @param itemStack     stack to find a matching setting for
	 * @return setting within the component that matches the stack
	 */
	private ItemSetting getStackSetting(ComponentMenu componentMenu, ItemStack itemStack) {
		ComponentMenuStuff menuItem = (ComponentMenuStuff) componentMenu;

		for (Setting setting : menuItem.getSettings()) {
			if (!(setting instanceof ItemSetting))
				continue;
			if (((ItemSetting) setting).isEqualForCommandExecutor(itemStack)) {
				return (ItemSetting) setting;
			}
		}

		return null;
	}

	/**
	 * Gets the setting that applies to the given fluid.
	 * Used to be called isFluidValid
	 *
	 * @param componentMenu component containing settings to pick from
	 * @param fluidStack    fluid to find a matching setting for
	 * @return setting within the component that matches the fluid
	 */
	private Setting getFluidSetting(ComponentMenu componentMenu, FluidStack fluidStack) {
		ComponentMenuStuff menuItem = (ComponentMenuStuff) componentMenu;

		if (fluidStack != null) {
			String fluidName = fluidStack.getFluid().getName();
			for (Setting setting : menuItem.getSettings()) {
				if (setting.isValid() && ((FluidSetting) setting).getFluidName().equals(fluidName)) {
					return setting;
				}
			}
		}
		return null;
	}

	private void insertItems(ComponentMenu componentMenu, List<SlotInventoryHolder> inventories) {
		ComponentMenuStuff menuItem = (ComponentMenuStuff) componentMenu;

		List<OutputItemCounter> outputCounters = new ArrayList<>();
		for (SlotInventoryHolder inventoryHolder : inventories) {
			if (!inventoryHolder.isShared()) {
				outputCounters.clear();
			}

			for (CraftingBufferElement craftingBufferElement : craftingBufferHigh) {
				insertItemsFromInputBufferElement(menuItem, inventories, outputCounters, inventoryHolder, craftingBufferElement);
			}
			for (ItemBufferElement itemBufferElement : itemBuffer) {
				insertItemsFromInputBufferElement(menuItem, inventories, outputCounters, inventoryHolder, itemBufferElement);
			}
			for (CraftingBufferElement craftingBufferElement : craftingBufferLow) {
				insertItemsFromInputBufferElement(menuItem, inventories, outputCounters, inventoryHolder, craftingBufferElement);
			}
		}

	}

	private void insertItemsFromInputBufferElement(ComponentMenuStuff menuItem, List<SlotInventoryHolder> inventories, List<OutputItemCounter> outputCounters, SlotInventoryHolder inventoryHolder, IItemBufferElement itemBufferElement) {
		IItemBufferSubElement subElement;
		int                   remaining;
		itemBufferElement.prepareSubElements();
		while ((subElement = itemBufferElement.getSubElement()) != null && (remaining = subElement.getSizeRemaining()) > 0) {
			ItemStack stackInBuffer = subElement.getItemStack();

			ItemSetting setting = getStackSetting(menuItem, stackInBuffer);

			if (setting == null && menuItem.useWhiteList())
				continue;
			if (setting != null && !menuItem.useWhiteList() && !setting.isLimitedByAmount())
				continue;

			OutputItemCounter outputItemCounter = outputCounters.stream()
					.filter(s -> s.areSettingsSame(setting))
					.findFirst()
					.orElseGet(() -> {
						OutputItemCounter c = new OutputItemCounter(itemBuffer, inventories, inventoryHolder, setting, menuItem.useWhiteList());
						outputCounters.add(c);
						return c;
					});

			//			System.out.println("Begin");
			for (SideSlotTarget sideSlotTarget : inventoryHolder.getValidSlots().values()) {
				IItemHandler inventory = inventoryHolder.getInventory(sideSlotTarget.getSide());
				if (inventory == null)
					continue;

				for (int slot : sideSlotTarget.getSlots()) {
					int moveCount = remaining;
					moveCount = outputItemCounter.retrieveItemCount(moveCount);
					moveCount = itemBufferElement.retrieveItemCount(moveCount);
					if (moveCount == 0)
						continue;
					//					System.out.println("Moving " + moveCount);

					ItemStack stackToInsert = stackInBuffer.copy();
					stackToInsert.setCount(moveCount);
					int leftoverCount = inventory.insertItem(slot, stackToInsert, false).getCount();
					if (leftoverCount == moveCount)
						continue; // We moved nothing

					moveCount -= leftoverCount; // Becomes the amount we actually moved
					remaining -= moveCount;
					itemBufferElement.decreaseStackSize(moveCount);
					outputItemCounter.modifyStackSize(moveCount);
					subElement.reduceAmount(moveCount);


					if (subElement.getSizeRemaining() <= 0) {
						subElement.remove();
						itemBufferElement.removeSubElement();
						subElement.onUpdate();
						break;
					}

					subElement.onUpdate();
				}
			}
		}
		itemBufferElement.releaseSubElements();
	}

	private void insertFluids(ComponentMenu componentMenu, List<SlotInventoryHolder> tanks) {
		ComponentMenuStuff menuItem = (ComponentMenuStuff) componentMenu;

		List<OutputFluidCounter> outputCounters = new ArrayList<OutputFluidCounter>();
		for (SlotInventoryHolder tankHolder : tanks) {
			if (!tankHolder.isShared()) {
				outputCounters.clear();
			}

			for (FluidBufferElement fluidBufferElement : fluidBuffer) {
				Iterator<StackTankHolder> fluidIterator = fluidBufferElement.getHolders().iterator();
				while (fluidIterator.hasNext()) {
					StackTankHolder holder     = fluidIterator.next();
					FluidStack      fluidStack = holder.getFluidStack();

					Setting setting = getFluidSetting(componentMenu, fluidStack);

					if ((menuItem.useWhiteList() == (setting == null)) && (setting == null || !setting.isLimitedByAmount())) {
						continue;
					}

					OutputFluidCounter outputFluidCounter = null;
					for (OutputFluidCounter e : outputCounters) {
						if (e.areSettingsSame(setting)) {
							outputFluidCounter = e;
							break;
						}
					}

					if (outputFluidCounter == null) {
						outputFluidCounter = new OutputFluidCounter(fluidBuffer, tanks, tankHolder, setting, menuItem.useWhiteList());
						outputCounters.add(outputFluidCounter);
					}

					for (SideSlotTarget sideSlotTarget : tankHolder.getValidSlots().values()) {
						IFluidHandler tank = tankHolder.getTank(sideSlotTarget.getSide());
						if (tank == null)
							continue;
						FluidStack temp = fluidStack.copy();
						temp.amount = holder.getSizeLeft();
						int amount = tank.fill(temp, false);
						amount = fluidBufferElement.retrieveItemCount(amount);
						amount = outputFluidCounter.retrieveItemCount(amount);

						if (amount > 0) {
							FluidStack resource = fluidStack.copy();
							resource.amount = amount;

							resource = holder.getTank().drain(resource, true);
							if (resource != null && resource.amount > 0) {
								tank.fill(resource, true);
								fluidBufferElement.decreaseStackSize(resource.amount);
								outputFluidCounter.modifyStackSize(resource.amount);
								holder.reduceAmount(resource.amount);
								if (holder.getSizeLeft() == 0) {
									fluidIterator.remove();
									break;
								}
							}
						}
					}
				}
			}
		}
	}

	private boolean searchForStuff(ComponentMenu componentMenu, List<SlotInventoryHolder> inventories, boolean useFluids) {
		if (inventories == null || inventories.size() == 0)
			return false;
		if (inventories.get(0).isShared()) {
			Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap = new HashMap<Integer, ConditionSettingChecker>();
			for (int i = 0; i < inventories.size(); i++) {
				calculateConditionData(componentMenu, inventories.get(i), conditionSettingCheckerMap, useFluids);
			}
			return checkConditionResult(componentMenu, conditionSettingCheckerMap);
		} else {
			boolean useAnd = inventories.get(0).getSharedOption() == 1;
			for (int i = 0; i < inventories.size(); i++) {
				Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap = new HashMap<Integer, ConditionSettingChecker>();
				calculateConditionData(componentMenu, inventories.get(i), conditionSettingCheckerMap, useFluids);

				if (checkConditionResult(componentMenu, conditionSettingCheckerMap)) {
					if (!useAnd) {
						return true;
					}
				} else if (useAnd) {
					return false;
				}
			}
			return useAnd;
		}
	}

	private void calculateConditionData(ComponentMenu componentMenu, SlotInventoryHolder inventoryHolder, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap, boolean useFluids) {
		if (useFluids) {
			calculateConditionDataFluid(componentMenu, inventoryHolder, conditionSettingCheckerMap);
		} else {
			calculateConditionDataItem(componentMenu, inventoryHolder, conditionSettingCheckerMap);
		}
	}

	private void calculateConditionDataItem(ComponentMenu componentMenu, SlotInventoryHolder inventoryHolder, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap) {
		IdentityHashMap<ItemStack, Object> seenStacks = new IdentityHashMap<>();
		for (SideSlotTarget sideSlotTarget : inventoryHolder.getValidSlots().values()) {
			for (int slot : sideSlotTarget.getSlots()) {
				IItemHandler inventory = inventoryHolder.getInventory(sideSlotTarget.getSide());
				if (inventory == null)
					continue;
				ItemStack itemStack = inventory.getStackInSlot(slot);

				if (seenStacks.containsKey(itemStack) || !isSlotValid(inventory, itemStack, slot, true)) {
					continue;
				}
				seenStacks.put(itemStack, null);

				Setting setting = getStackSetting(componentMenu, itemStack);
				if (setting != null) {
					ConditionSettingChecker conditionSettingChecker = conditionSettingCheckerMap.get(setting.getId());
					if (conditionSettingChecker == null) {
						conditionSettingCheckerMap.put(setting.getId(), conditionSettingChecker = new ConditionSettingChecker(setting));
					}
					conditionSettingChecker.addCount(itemStack.getCount());
				}
			}
		}
	}

	private void calculateConditionDataFluid(ComponentMenu componentMenu, SlotInventoryHolder inventoryHolder, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap) {
		for (SideSlotTarget sideSlotTarget : inventoryHolder.getValidSlots().values()) {
			IFluidHandler tank = inventoryHolder.getTank(sideSlotTarget.getSide());
			if (tank == null)
				continue;
			List<IFluidTankProperties> tankInfos        = new ArrayList<IFluidTankProperties>();
			IFluidTankProperties[]     currentTankInfos = tank.getTankProperties();
			if (currentTankInfos == null) {
				continue;
			}
			for (IFluidTankProperties fluidTankInfo : currentTankInfos) {
				if (fluidTankInfo == null) {
					continue;
				}
				boolean alreadyUsed = false;
				for (IFluidTankProperties tankInfo : tankInfos) {
					if (FluidStack.areFluidStackTagsEqual(tankInfo.getContents(), fluidTankInfo.getContents()) && tankInfo.getCapacity() == fluidTankInfo.getCapacity()) {
						alreadyUsed = true;
					}
				}

				if (alreadyUsed) {
					continue;
				}

				FluidStack fluidStack = fluidTankInfo.getContents();
				Setting    setting    = getFluidSetting(componentMenu, fluidStack);
				if (setting != null) {
					ConditionSettingChecker conditionSettingChecker = conditionSettingCheckerMap.get(setting.getId());
					if (conditionSettingChecker == null) {
						conditionSettingCheckerMap.put(setting.getId(), conditionSettingChecker = new ConditionSettingChecker(setting));
					}
					conditionSettingChecker.addCount(fluidStack.amount);
				}
			}
			for (IFluidTankProperties fluidTankInfo : tank.getTankProperties()) {
				if (fluidTankInfo != null) {
					tankInfos.add(fluidTankInfo);
				}
			}
		}
	}

	private boolean checkConditionResult(ComponentMenu componentMenu, Map<Integer, ConditionSettingChecker> conditionSettingCheckerMap) {
		ComponentMenuStuff  menuItem      = (ComponentMenuStuff) componentMenu;
		IConditionStuffMenu menuCondition = (IConditionStuffMenu) componentMenu;
		for (Setting setting : menuItem.getSettings()) {
			if (setting.isValid()) {
				ConditionSettingChecker conditionSettingChecker = conditionSettingCheckerMap.get(setting.getId());

				if (conditionSettingChecker != null && conditionSettingChecker.isTrue()) {
					if (!menuCondition.requiresAll()) {
						return true;
					}
				} else if (menuCondition.requiresAll()) {
					return false;
				}
			}
		}
		return menuCondition.requiresAll();
	}

	private boolean splitFlow(ComponentMenu componentMenu) {
		ComponentMenuSplit split = (ComponentMenuSplit) componentMenu;
		if (split.useSplit()) {
			int amount = componentMenu.getParent().getConnectionSet().getOutputCount();
			if (!split.useEmpty()) {
				ConnectionOption[] connections = componentMenu.getParent().getConnectionSet().getConnections();
				for (int i = 0; i < connections.length; i++) {
					ConnectionOption connectionOption = connections[i];
					if (!connectionOption.isInput() && componentMenu.getParent().getConnection(i) == null) {
						amount--;
					}
				}
			}

			int                usedId      = 0;
			ConnectionOption[] connections = componentMenu.getParent().getConnectionSet().getConnections();
			for (int i = 0; i < connections.length; i++) {
				ConnectionOption connectionOption = connections[i];
				Connection       connection       = componentMenu.getParent().getConnection(i);
				if (!connectionOption.isInput() && connection != null) {
					List<ItemBufferElement>  itemBufferSplit  = new ArrayList<ItemBufferElement>();
					List<FluidBufferElement> fluidBufferSplit = new ArrayList<FluidBufferElement>();

					for (ItemBufferElement element : itemBuffer) {
						itemBufferSplit.add(element.getSplitElement(amount, usedId, split.useFair()));
					}

					for (FluidBufferElement element : fluidBuffer) {
						fluidBufferSplit.add(element.getSplitElement(amount, usedId, split.useFair()));
					}

					List<Integer> usedCommandCopy = new ArrayList<Integer>();
					usedCommandCopy.addAll(usedCommands);

					CommandExecutor newExecutor = new CommandExecutor(manager, itemBufferSplit, new ArrayList<CraftingBufferElement>(craftingBufferHigh), new ArrayList<CraftingBufferElement>(craftingBufferLow), fluidBufferSplit, usedCommandCopy);
					newExecutor.executeCommand(manager.getFlowItems().get(connection.getComponentId()), connection.getConnectionId());
					usedId++;
				}
			}
			return true;
		}
		return false;
	}

	private boolean evaluateRedstoneCondition(List<SlotInventoryHolder> nodes, FlowComponent component) {
		return TileEntityManager.redstoneCondition.isTriggerPowered(nodes, component, true);
	}

	private void updateVariable(List<SlotInventoryHolder> tiles, ComponentMenuVariable menuVariable, ComponentMenuListOrder menuOrder) {
		ComponentMenuVariable.VariableMode mode     = menuVariable.getVariableMode();
		Variable                           variable = manager.getVariables()[menuVariable.getSelectedVariable()];

		if (variable.isValid()) {
			boolean remove = mode == ComponentMenuVariable.VariableMode.REMOVE;
			if (!remove && mode != ComponentMenuVariable.VariableMode.ADD) {
				variable.clearContainers();
			}

			List<Integer> idList = new ArrayList<Integer>();
			for (SlotInventoryHolder tile : tiles) {
				idList.add(tile.getId());
			}

			if (!menuVariable.isDeclaration()) {
				idList = applyOrder(idList, menuOrder);
			}

			List<ConnectionBlock>        inventories = manager.getConnectedInventories();
			EnumSet<ConnectionBlockType> validTypes  = ((ComponentMenuContainerTypes) variable.getDeclaration().getMenus().get(1)).getValidTypes();
			for (int id : idList) {
				if (remove) {
					variable.remove(id);
				} else if (id >= 0 && id < inventories.size() && inventories.get(id).isOfAnyType(validTypes)) {
					variable.add(id);
				}
			}
		}
	}

	private void updateForLoop(FlowComponent command, ComponentMenuVariableLoop variableMenu, ComponentMenuContainerTypes typesMenu, ComponentMenuListOrder orderMenu) {
		Variable list    = variableMenu.getListVariable();
		Variable element = variableMenu.getElementVariable();

		if (!list.isValid() || !element.isValid()) {
			return;
		}

		List<Integer> selection = applyOrder(list.getContainers(), orderMenu);

		EnumSet<ConnectionBlockType> validTypes = typesMenu.getValidTypes();
		validTypes.addAll(((ComponentMenuContainerTypes) element.getDeclaration().getMenus().get(1)).getValidTypes());
		List<ConnectionBlock> inventories = manager.getConnectedInventories();
		for (Integer selected : selection) {
			//Should always be true, simply making sure if the inventories have changed
			if (selected >= 0 && selected < inventories.size()) {
				ConnectionBlock inventory = inventories.get(selected);
				if (inventory.isOfAnyType(validTypes)) {
					// Store context
					final List<CraftingBufferElement> craftingBufferHigh = ImmutableList.copyOf(this.craftingBufferHigh);
					final List<CraftingBufferElement> craftingBufferLow  = ImmutableList.copyOf(this.craftingBufferLow);
					final List<FluidBufferElement>    fluidBuffer        = ImmutableList.copyOf(this.fluidBuffer);
					final List<ItemBufferElement>     itemBuffer         = ImmutableList.copyOf(this.itemBuffer);
					final List<Integer>               usedCommands       = ImmutableList.copyOf(this.usedCommands);

					// Execute loop item
					element.clearContainers();
					element.add(selected);
					executeChildCommands(command, EnumSet.of(ConnectionOption.FOR_EACH));

					// Ignore context changes
					this.craftingBufferHigh.clear();
					this.craftingBufferLow.clear();
					this.fluidBuffer.clear();
					this.itemBuffer.clear();
					this.usedCommands.clear();

					// Restore original context
					this.craftingBufferHigh.addAll(craftingBufferHigh);
					this.craftingBufferLow.addAll(craftingBufferLow);
					this.fluidBuffer.addAll(fluidBuffer);
					this.itemBuffer.addAll(itemBuffer);
					this.usedCommands.addAll(usedCommands);

				}
			}
		}
	}

	private List<Integer> applyOrder(List<Integer> original, ComponentMenuListOrder orderMenu) {
		List<Integer> ret = new ArrayList<Integer>(original);
		if (orderMenu.getOrder() == ComponentMenuListOrder.LoopOrder.RANDOM) {
			Collections.shuffle(ret);
		} else if (orderMenu.getOrder() == ComponentMenuListOrder.LoopOrder.NORMAL) {
			if (!orderMenu.isReversed()) {
				Collections.reverse(ret);
			}
		} else {
			Collections.sort(ret, orderMenu.getComparator());
		}

		if (!orderMenu.useAll()) {
			int len = orderMenu.getAmount();
			while (ret.size() > len) {
				ret.remove(ret.size() - 1);
			}
		}
		return ret;
	}
}
