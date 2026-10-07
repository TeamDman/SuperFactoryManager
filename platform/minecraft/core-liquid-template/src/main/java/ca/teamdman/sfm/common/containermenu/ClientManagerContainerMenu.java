package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.ClientManagerProgramProjection;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Client Manager's server-authoritative disk slot and the player's inventory.
 *
 * <p>The server-side slot deliberately exposes only the bounded program projection
 * to routine menu synchronization. Taking the disk through an explicit user action
 * still returns the full server-owned stack, while a client that merely has the
 * block loaded continues to receive the same projection through block-entity data.</p>
 */
public final class ClientManagerContainerMenu extends AbstractContainerMenu {
    public final Container CONTAINER;
    public final Inventory PLAYER_INVENTORY;
    public final BlockPos MANAGER_POSITION;
    private final ClientManagerBlockEntity manager;

    public ClientManagerContainerMenu(
            int windowId,
            Inventory inventory,
            Container container,
            BlockPos managerPosition,
            ClientManagerBlockEntity manager
    ) {
        super(SFMMenus.CLIENT_MANAGER.get(), windowId);
        checkContainerSize(container, 1);
        this.CONTAINER = container;
        this.PLAYER_INVENTORY = inventory;
        this.MANAGER_POSITION = managerPosition;
        this.manager = manager;

        this.addSlot(new Slot(container, 0, 15, 47) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DiskItem
                        && (manager == null || manager.acceptsDisk(stack));
            }
        });

        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; ++column) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    public ClientManagerContainerMenu(int windowId, Inventory inventory, ClientManagerBlockEntity manager) {
        this(windowId, inventory, new ServerContainer(manager), manager.getBlockPos(), manager);
    }

    public ClientManagerContainerMenu(int windowId, Inventory inventory, FriendlyByteBuf buf) {
        this(windowId, inventory, new SimpleContainer(1), buf.readBlockPos(), null);
    }

    public static void encode(ClientManagerBlockEntity manager, FriendlyByteBuf buf) {
        buf.writeBlockPos(manager.getBlockPos());
    }

    public ClientManagerBlockEntity manager() {
        return manager;
    }

    @Override
    public boolean stillValid(Player player) {
        return CONTAINER.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) return ItemStack.EMPTY;

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        // Do not shift-click the projected menu copy back into the player's
        // inventory. An explicit extraction returns the full server stack.
        if (slotIndex == 0 && manager != null) {
            ItemStack fullDisk = manager.disk();
            if (fullDisk.isEmpty()) return ItemStack.EMPTY;
            ItemStack result = fullDisk.copy();
            if (!this.moveItemStackTo(fullDisk, 1, this.slots.size(), true)) return ItemStack.EMPTY;
            manager.removeDisk();
            return result;
        }

        ItemStack contents = slot.getItem();
        ItemStack result = contents.copy();
        int containerEnd = 1;
        int inventoryEnd = this.slots.size();
        if (slotIndex < containerEnd) {
            if (!this.moveItemStackTo(contents, containerEnd, inventoryEnd, true)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(contents, 0, containerEnd, false)) return ItemStack.EMPTY;
        }

        if (contents.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    /** The menu's normal slot view is a bounded program-only projection. */
    private static ItemStack projected(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        return ClientManagerProgramProjection.project(source.getTag() == null
                        ? new CompoundTag() : source.getTag())
                .map(tag -> {
                    ItemStack result = new ItemStack(SFMItems.DISK.get());
                    result.setTag(tag);
                    return result;
                })
                .orElse(ItemStack.EMPTY);
    }

    private static final class ServerContainer implements Container {
        private final ClientManagerBlockEntity manager;

        private ServerContainer(ClientManagerBlockEntity manager) {
            this.manager = manager;
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return manager.disk().isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? projected(manager.disk()) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return slot == 0 && amount > 0 ? manager.removeDisk() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return slot == 0 ? manager.removeDisk() : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot != 0) return;
            if (stack.isEmpty()) manager.removeDisk();
            else if (manager.acceptsDisk(stack)) manager.setDisk(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            manager.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return SFMContainerUtil.stillValid(manager, player);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == 0 && stack.getItem() instanceof DiskItem;
        }

        @Override
        public void clearContent() {
            if (!manager.disk().isEmpty()) manager.removeDisk();
        }
    }
}
