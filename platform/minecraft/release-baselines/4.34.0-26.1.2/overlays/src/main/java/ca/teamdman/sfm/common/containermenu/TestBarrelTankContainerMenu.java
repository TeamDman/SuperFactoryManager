package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

public class TestBarrelTankContainerMenu extends AbstractContainerMenu {
    public final Container container;
    public final FluidStacksResourceHandler tank;

    public TestBarrelTankContainerMenu(
            int windowId,
            Inventory inv,
            Container container,
            FluidStack tankContents
    ) {
        super(SFMMenus.TEST_BARREL_TANK.get(), windowId);
        checkContainerSize(container, 1);
        this.container = container;
        this.tank = new FluidStacksResourceHandler(NonNullList.of(tankContents), 1000);

        container.startOpen(inv.player);
        int i = -18;
        for (int j = 0; j < 3; ++j) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(container, k + j * 9, 8 + k * 18, 18 + j * 18));
            }
        }

        for (int l = 0; l < 3; ++l) {
            for (int j1 = 0; j1 < 9; ++j1) {
                this.addSlot(new Slot(inv, j1 + l * 9 + 9, 8 + j1 * 18, 103 + l * 18 + i));
            }
        }

        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(inv, i1, 8 + i1 * 18, 161 + i));
        }
    }

    public TestBarrelTankContainerMenu(
            int windowId,
            Inventory inventory,
            RegistryFriendlyByteBuf buf
    ) {
        this(
                windowId,
                inventory,
                new SimpleContainer(27),
                FluidStack.STREAM_CODEC.decode(buf)
        );
    }

    public TestBarrelTankContainerMenu(
            int containerId,
            Inventory inventory,
            TestBarrelTankBlockEntity blockEntity
    ) {
        FluidStacksResourceHandler tank = blockEntity.getTank();
        this(
                containerId,
                inventory,
                blockEntity,
                tank.getResource(0).toStack(tank.getAmountAsInt(0))
        );
    }

    public static void encode(
            TestBarrelTankBlockEntity blockEntity,
            RegistryFriendlyByteBuf buf
    ) {
        FluidStacksResourceHandler tank = blockEntity.getTank();
        buf.writeLong(tank.getAmountAsLong(0));
        FluidStack.STREAM_CODEC.encode(buf, tank.getResource(0).toStack(tank.getAmountAsInt(0)));
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int slotIndex
    ) {
        var slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        var containerEnd = container.getContainerSize();
        var inventoryEnd = this.slots.size();

        var contents = slot.getItem();
        var result = contents.copy();

        if (slotIndex < containerEnd) {
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
}
