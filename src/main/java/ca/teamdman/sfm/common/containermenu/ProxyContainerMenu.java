package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.ProxyBlockEntity;
import ca.teamdman.sfm.common.registry.SFMMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Tuple;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ProxyContainerMenu extends AbstractContainerMenu {
    public static final List<Tuple<Integer, Integer>> CONTAINER_SLOT_POSITIONS = List.of(
            new Tuple<>(80, 17),
            new Tuple<>(100, 37),
            new Tuple<>(80, 57),
            new Tuple<>(60, 37)
    );

    public static Tuple<Integer, Integer> PLAYER_INV_START_POS = new Tuple<>(8, 90);

    private final ProxyBlockEntity PROXY_BLOCK_ENTITY;

    public ProxyContainerMenu(int windowId, Inventory inventory, FriendlyByteBuf buf) {
        this(windowId, inventory, (ProxyBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public ProxyContainerMenu(int windowId, Inventory inventory, ProxyBlockEntity proxyBlockEntity) {
        super(SFMMenus.PROXY_MENU.get(), windowId);

        PROXY_BLOCK_ENTITY = proxyBlockEntity;

        createContainerSlot(PROXY_BLOCK_ENTITY);
        createPlayerInventory(inventory);
    }

    public static void encode(ProxyBlockEntity pbe, FriendlyByteBuf buf) {
        buf.writeBlockPos(pbe.getBlockPos());
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        var slot = this.slots.get(pIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        var containerEnd = PROXY_BLOCK_ENTITY.getContainerSize();
        var inventoryEnd = this.slots.size();

        var contents = slot.getItem();
        var result = contents.copy();

        if (pIndex < containerEnd) {
            // clicked slot in container
            if (!this.moveItemStackTo(contents, containerEnd, inventoryEnd, true)) return ItemStack.EMPTY;
        } else {
            // clicked slot in inventory
            if (!this.moveItemStackTo(contents, 0, containerEnd, false)) return ItemStack.EMPTY;
        }

        if (contents.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return PROXY_BLOCK_ENTITY.stillValid(pPlayer);
    }

    private void createContainerSlot(Container container) {
        for (int slot = 0; slot < CONTAINER_SLOT_POSITIONS.size(); slot++) {
            Tuple<Integer, Integer> pos = CONTAINER_SLOT_POSITIONS.get(slot);
            addSlot(
                    new Slot(container, slot, pos.getA(), pos.getB())
            );
        }
    }

    private void createPlayerInventory(Inventory playerInv) {
        int xPos = PLAYER_INV_START_POS.getA();
        int yPos = PLAYER_INV_START_POS.getB();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInv,
                        9 + column + (row * 9),
                        xPos + (column * 18),
                        yPos + (row * 18)));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInv, column, xPos + column * 18, yPos + (18 * 3) + 4));
        }
    }
}
