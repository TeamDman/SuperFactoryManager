package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Container menu for the library block GUI.
 * Provides access to disk slots for storing library disks.
 */
public class LibraryContainerMenu extends AbstractContainerMenu {

    // Disk slot layout constants
    private static final int DISK_SLOT_START_X = 44;
    private static final int DISK_SLOT_START_Y = 20;
    private static final int SLOT_SPACING = 18;
    private static final int DISK_SLOTS_PER_ROW = 5;

    // Player inventory layout constants
    private static final int PLAYER_INV_START_X = 8;
    private static final int PLAYER_INV_START_Y = 84;
    private static final int PLAYER_HOTBAR_Y = 142;

    /**
     * Record to track library name, slot index, and error/warning status.
     */
    public record LibraryEntry(String name, int slotIndex, boolean hasErrors, boolean hasWarnings) {}

    /**
     * Extracts library entries from a container's disk slots.
     * Used by both server-side (LibraryBlockEntity) and client-side (refreshing from synced container).
     *
     * @param container The container to extract entries from
     * @param slotCount The number of disk slots to check
     * @return List of library entries with their names and slot indices
     */
    public static List<LibraryEntry> extractLibraryEntries(Container container, int slotCount) {
        List<LibraryEntry> entries = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            ItemStack disk = container.getItem(i);
            if (!DiskItem.isValidDisk(disk)) continue;

            String source = DiskItem.getProgramString(disk);
            String name = DiskItem.extractName(source);
            if (name == null || name.isEmpty()) {
                name = "(unnamed)";
            }
            boolean hasErrors = !DiskItem.getErrors(disk).isEmpty();
            boolean hasWarnings = !DiskItem.getWarnings(disk).isEmpty();
            entries.add(new LibraryEntry(name, i, hasErrors, hasWarnings));
        }
        return entries;
    }

    public final Inventory PLAYER_INVENTORY;
    public final BlockPos LIBRARY_POSITION;
    public final Container CONTAINER;
    public List<LibraryEntry> libraryEntries;

    public LibraryContainerMenu(
            int windowId,
            Inventory inv,
            BlockPos blockEntityPos,
            Container container,
            List<LibraryEntry> libraryEntries
    ) {
        super(SFMMenus.LIBRARY_MENU.get(), windowId);
        this.PLAYER_INVENTORY = inv;
        this.LIBRARY_POSITION = blockEntityPos;
        this.CONTAINER = container;
        this.libraryEntries = new ArrayList<>(libraryEntries);

        // Add disk slots (2 rows of 5)
        for (int i = 0; i < LibraryBlockEntity.DISK_SLOT_COUNT; i++) {
            int x = DISK_SLOT_START_X + (i % DISK_SLOTS_PER_ROW) * SLOT_SPACING;
            int y = DISK_SLOT_START_Y + (i / DISK_SLOTS_PER_ROW) * SLOT_SPACING;
            this.addSlot(new Slot(container, i, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof DiskItem;
                }
            });
        }

        // Add player inventory slots
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(inv, j + i * 9 + 9,
                        PLAYER_INV_START_X + j * SLOT_SPACING,
                        PLAYER_INV_START_Y + i * SLOT_SPACING));
            }
        }

        // Add player hotbar slots
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(inv, k, PLAYER_INV_START_X + k * SLOT_SPACING, PLAYER_HOTBAR_Y));
        }
    }

    public LibraryContainerMenu(
            int windowId,
            Inventory inventory,
            FriendlyByteBuf buf
    ) {
        this(
                windowId,
                inventory,
                buf.readBlockPos(),
                new SimpleClientContainer(LibraryBlockEntity.DISK_SLOT_COUNT),
                readLibraryEntries(buf)
        );
    }

    public LibraryContainerMenu(
            int windowId,
            Inventory inventory,
            LibraryBlockEntity library
    ) {
        this(
                windowId,
                inventory,
                library.getBlockPos(),
                library,
                library.getLibraryEntries()
        );
    }

    public static void encode(
            LibraryBlockEntity library,
            FriendlyByteBuf buf
    ) {
        buf.writeBlockPos(library.getBlockPos());
        writeLibraryEntries(library.getLibraryEntries(), buf);
    }

    private static List<LibraryEntry> readLibraryEntries(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<LibraryEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf(256);
            int slotIndex = buf.readVarInt();
            boolean hasErrors = buf.readBoolean();
            boolean hasWarnings = buf.readBoolean();
            entries.add(new LibraryEntry(name, slotIndex, hasErrors, hasWarnings));
        }
        return entries;
    }

    private static void writeLibraryEntries(List<LibraryEntry> entries, FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (LibraryEntry entry : entries) {
            buf.writeUtf(entry.name(), 256);
            buf.writeVarInt(entry.slotIndex());
            buf.writeBoolean(entry.hasErrors());
            buf.writeBoolean(entry.hasWarnings());
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level.getBlockEntity(LIBRARY_POSITION) instanceof LibraryBlockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            // Disk slots are 0-9, player inventory is 10-45
            if (slotIndex < LibraryBlockEntity.DISK_SLOT_COUNT) {
                // Moving from disk slot to player inventory
                if (!this.moveItemStackTo(slotStack, LibraryBlockEntity.DISK_SLOT_COUNT, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory to disk slots (only if it's a disk)
                if (slotStack.getItem() instanceof DiskItem) {
                    if (!this.moveItemStackTo(slotStack, 0, LibraryBlockEntity.DISK_SLOT_COUNT, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    /**
     * Simple container for client-side use when the actual block entity isn't available.
     */
    private static class SimpleClientContainer implements Container {
        private final ItemStack[] items;

        public SimpleClientContainer(int size) {
            this.items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = ItemStack.EMPTY;
            }
        }

        @Override
        public int getContainerSize() {
            return items.length;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack item : items) {
                if (!item.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < items.length ? items[slot] : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot >= 0 && slot < items.length && !items[slot].isEmpty() && amount > 0) {
                return items[slot].split(amount);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot >= 0 && slot < items.length) {
                ItemStack result = items[slot];
                items[slot] = ItemStack.EMPTY;
                return result;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot >= 0 && slot < items.length) {
                items[slot] = stack;
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < items.length; i++) {
                items[i] = ItemStack.EMPTY;
            }
        }
    }
}
