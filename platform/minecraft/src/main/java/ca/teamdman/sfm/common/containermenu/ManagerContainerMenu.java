package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.logging.TranslatableLogEvent;
import ca.teamdman.sfm.common.net.ServerboundManagerSetLogLevelPacket;
import ca.teamdman.sfm.common.timing.SFMDurationNetworkUtils;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

import java.time.Duration;
import java.util.ArrayDeque;

import static ca.teamdman.sfm.common.timing.SFMDurationNetworkUtils.readDurationArray;

public class ManagerContainerMenu extends Container {
    public final IInventory CONTAINER;
    public final InventoryPlayer PLAYER_INVENTORY;
    public final BlockPos MANAGER_POSITION;
    public final ArrayDeque<TranslatableLogEvent> logs;
    public String logLevel;
    public boolean isLogScreenOpen = false;
    public String program;
    public ManagerBlockEntity.State state;
    public Duration[] tickTimes;
    public Duration[] externalTickTimes;


    public ManagerContainerMenu(
            int windowId,
            InventoryPlayer inv,
            IInventory container,
            BlockPos blockEntityPos,
            String program,
            String logLevel,
            ManagerBlockEntity.State state,
            Duration[] tickTimes,
            Duration[] externalTickTimes,
            ArrayDeque<TranslatableLogEvent> logs
    ) {
        this.windowId = windowId;
        assert container.getSizeInventory() == 1;
        this.CONTAINER = container;
        this.PLAYER_INVENTORY = inv;
        this.MANAGER_POSITION = blockEntityPos;
        this.logLevel = logLevel;
        this.logs = logs;
        this.program = program;
        this.state = state;
        this.tickTimes = tickTimes;
        this.externalTickTimes = externalTickTimes;

        this.addSlotToContainer(new Slot(container, 0, 15, 47) {
            @Override
            public int getSlotStackLimit() {
                return 1;
            }

            @Override
            public boolean isItemValid(ItemStack stack) {
                return stack.getItem() instanceof DiskItem;
            }
        });

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlotToContainer(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int k = 0; k < 9; ++k) {
            this.addSlotToContainer(new Slot(inv, k, 8 + k * 18, 142));
        }
    }

    public ManagerContainerMenu(
            int windowId,
            InventoryPlayer inventory,
            PacketBuffer buf
    ) {
        this(
                windowId,
                inventory,
                new net.minecraft.inventory.InventoryBasic("manager", true, 1),
                buf.readBlockPos(),
                buf.readString(Program.MAX_PROGRAM_LENGTH),
                buf.readString(ServerboundManagerSetLogLevelPacket.MAX_LOG_LEVEL_NAME_LENGTH),
                buf.readEnumValue(ManagerBlockEntity.State.class),
                readDurationArray(buf.readLongArray(null)),
                readDurationArray(buf.readLongArray(null)),
                new ArrayDeque<>()
        );
    }

    public ManagerContainerMenu(
            int windowId,
            InventoryPlayer inventory,
            ManagerBlockEntity manager
    ) {
        this(
                windowId,
                inventory,
                manager,
                manager.getPos(),
                manager.getProgramStringOrEmptyIfNull(),
                manager.logger.getLogLevel().name(),
                manager.getState(),
                manager.getTickTimes(),
                manager.getExternalTickTimes(),
                new ArrayDeque<>()
        );
    }

//    public static void encode(
//            ManagerBlockEntity manager,
//            PacketBuffer buf
//    ) {
//        buf.writeBlockPos(manager.getPos());
//        buf.writeString(manager.getProgramStringOrEmptyIfNull());
//        buf.writeString(
//                manager.logger.getLogLevel().name()
//        );
//        buf.writeEnumValue(manager.getState());
//        SFMDurationNetworkUtils.writeDurationArray(manager.getTickTimes(), buf);
//    }

    public ItemStack getDisk() {
        return this.CONTAINER.getStackInSlot(0);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return CONTAINER.isUsableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(
            EntityPlayer player,
            int slotIndex
    ) {
        var slot = this.inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        var containerEnd = CONTAINER.getSizeInventory();
        var inventoryEnd = this.inventorySlots.size();

        var contents = slot.getStack();
        var result = contents.copy();

        if (slotIndex < containerEnd) {
            // clicked slot in container
            if (!this.mergeItemStack(contents, containerEnd, inventoryEnd, true)) return ItemStack.EMPTY;
        } else {
            // clicked slot in inventory
            if (!this.mergeItemStack(contents, 0, containerEnd, false)) return ItemStack.EMPTY;
        }

        if (contents.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        return result;
    }
}