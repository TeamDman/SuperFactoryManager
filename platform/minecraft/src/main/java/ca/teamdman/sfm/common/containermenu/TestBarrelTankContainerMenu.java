package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
import ca.teamdman.sfm.common.registry.SFMMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class TestBarrelTankContainerMenu extends AbstractContainerMenu {
    public final Container container;
    public final FluidTank tank;

    public TestBarrelTankContainerMenu(
            int windowId,
            Inventory inv,
            Container container,
            FluidStack tankContents
    ) {
        super(SFMMenus.TEST_BARREL_TANK_MENU.get(), windowId);
        checkContainerSize(container, 1);
        this.container = container;
        this.tank = new FluidTank(1000);
        this.tank.setFluid(tankContents);

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
            FriendlyByteBuf buf
    ) {
        this(
                windowId,
                inventory,
                new SimpleContainer(27),
                buf.readFluidStack()
        );
    }

    public TestBarrelTankContainerMenu(
            int containerId,
            Inventory inventory,
            TestBarrelTankBlockEntity blockEntity
    ) {
        this(
                containerId,
                inventory,
                blockEntity,
                blockEntity.getTank().getFluid()
        );
    }

    public static void encode(
            TestBarrelTankBlockEntity blockEntity,
            FriendlyByteBuf buf
    ) {
        buf.writeLong(blockEntity.getTank().getFluidAmount());
        buf.writeFluidStack(blockEntity.getTank().getFluid());
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
