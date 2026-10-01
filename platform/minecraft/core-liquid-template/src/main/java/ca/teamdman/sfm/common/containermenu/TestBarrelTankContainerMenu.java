package ca.teamdman.sfm.common.containermenu;

import ca.teamdman.sfm.common.blockentity.TestBarrelTankBlockEntity;
import ca.teamdman.sfm.common.registry.registration.SFMMenus;
{% if targets.mc_26_1_2 %}
import net.minecraft.core.NonNullList;
{% endif %}
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% else %}
import net.minecraft.network.FriendlyByteBuf;
{% endcase %}
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
{% when "26.1.2" %}
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
{% else %}
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
{% endcase %}

public class TestBarrelTankContainerMenu extends AbstractContainerMenu {
    public final Container container;
{% if targets.mc_26_1_2 %}
    public final FluidStacksResourceHandler tank;
{% else %}
    public final FluidTank tank;
{% endif %}

    public TestBarrelTankContainerMenu(
            int windowId,
            Inventory inv,
            Container container,
            FluidStack tankContents
    ) {
        super(SFMMenus.TEST_BARREL_TANK.get(), windowId);
        checkContainerSize(container, 1);
        this.container = container;
{% if targets.mc_26_1_2 %}
        this.tank = new FluidStacksResourceHandler(NonNullList.of(tankContents), 1000);
{% else %}
        this.tank = new FluidTank(1000);
        this.tank.setFluid(tankContents);
{% endif %}

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
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
            RegistryFriendlyByteBuf buf
{% else %}
            FriendlyByteBuf buf
{% endcase %}
    ) {
        this(
                windowId,
                inventory,
                new SimpleContainer(27),
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
                FluidStack.STREAM_CODEC.decode(buf)
{% else %}
                buf.readFluidStack()
{% endcase %}
        );
    }

    public TestBarrelTankContainerMenu(
            int containerId,
            Inventory inventory,
            TestBarrelTankBlockEntity blockEntity
    ) {
{% if targets.mc_26_1_2 %}
{% if features.test_barrel_constructor_delegation %}
{% else %}
        FluidStacksResourceHandler tank = blockEntity.getTank();
{% endif %}
{% endif %}
        this(
                containerId,
                inventory,
                blockEntity,
{% if targets.mc_26_1_2 %}
{% if features.test_barrel_constructor_delegation %}
                displayedFluid(blockEntity)
{% else %}
                tank.getResource(0).toStack(tank.getAmountAsInt(0))
{% endif %}
{% else %}
                blockEntity.getTank().getFluid()
{% endif %}
        );
    }
{% if targets.mc_26_1_2 %}
{% if features.test_barrel_constructor_delegation %}

    private static FluidStack displayedFluid(TestBarrelTankBlockEntity blockEntity) {
        FluidStacksResourceHandler tank = blockEntity.getTank();
        return tank.getResource(0).toStack(tank.getAmountAsInt(0));
    }
{% endif %}
{% endif %}

    public static void encode(
            TestBarrelTankBlockEntity blockEntity,
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
            RegistryFriendlyByteBuf buf
{% else %}
            FriendlyByteBuf buf
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        FluidStacksResourceHandler tank = blockEntity.getTank();
        buf.writeLong(tank.getAmountAsLong(0));
        FluidStack.STREAM_CODEC.encode(buf, tank.getResource(0).toStack(tank.getAmountAsInt(0)));
{% when "1.21", "1.21.1" %}
        buf.writeLong(blockEntity.getTank().getFluidAmount());
        FluidStack.STREAM_CODEC.encode(buf, blockEntity.getTank().getFluid());
{% else %}
        buf.writeLong(blockEntity.getTank().getFluidAmount());
        buf.writeFluidStack(blockEntity.getTank().getFluid());
{% endcase %}
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
