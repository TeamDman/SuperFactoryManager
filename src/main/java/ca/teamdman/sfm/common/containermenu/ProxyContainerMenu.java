package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.ProxyBlockEntity;
import ca.teamdman.sfm.common.registry.SFMMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ProxyContainerMenu extends AbstractContainerMenu {
    private final ProxyBlockEntity PROXY_BLOCK_ENTITY;
    private final Inventory PLAYER_INVENTORY;

    public ProxyContainerMenu(int windowId, Inventory inventory, FriendlyByteBuf buf) {
        this(windowId, inventory, (ProxyBlockEntity) inventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public ProxyContainerMenu(int windowId, Inventory inventory, ProxyBlockEntity proxyBlockEntity) {
        super(SFMMenus.PROXY_MENU.get(), windowId);

        PROXY_BLOCK_ENTITY = proxyBlockEntity;
        PLAYER_INVENTORY = inventory;

        addContainerSlot(proxyBlockEntity);
    }

    @Override
    public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
        return null;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return PROXY_BLOCK_ENTITY.stillValid(pPlayer);
    }

    private void addContainerSlot(Container container) {
        addSlot(new Slot(
                container,
                0,
                81,
                5
        ));
    }

    private void addPlayerInventory(Inventory inventory) {
    }

    private void addPlayerHotbar(Inventory inventory) {
    }

    private void addInternalItemSlots() {
    }

    private void addInternalTanks() {
    }
}
