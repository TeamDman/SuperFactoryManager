package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

public class TestBarrelTankBlockEntity extends BaseContainerBlockEntity {
    private final LazyOptional<IItemHandler> item_capability = LazyOptional.of(() -> new InvWrapper(this));
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final FluidTank tank = new FluidTank(1000);
    public final LazyOptional<IFluidHandler> fluid_capability = LazyOptional.of(() -> tank);

    public TestBarrelTankBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {
        super(SFMBlockEntities.TEST_BARREL_TANK_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    //    @Override
    @SuppressWarnings("unused") // 1.21.1 only
    public boolean isValidBlockState(BlockState blockState) {
        return SFMBlockEntities.TEST_BARREL_BLOCK_ENTITY.get().isValid(blockState);
    }

    @Override
    public <T> LazyOptional<T> getCapability(
            Capability<T> cap,
            @Nullable Direction side
    ) {
        if (cap == SFMWellKnownCapabilities.ITEM_HANDLER.capabilityKind()) {
            return item_capability.cast();
        }
        if (cap == SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind()) {
            return fluid_capability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public ItemStack getItem(int pSlot) {
        return items.get(pSlot);
    }

    @Override
    public ItemStack removeItem(
            int pSlot,
            int pAmount
    ) {
        ItemStack itemstack = ContainerHelper.removeItem(items, pSlot, pAmount);
        if (!itemstack.isEmpty()) {
            this.setChanged();
        }

        return itemstack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int pSlot) {
        return ContainerHelper.takeItem(items, pSlot);
    }

    @Override
    public void setItem(
            int pSlot,
            ItemStack pStack
    ) {
        if (pSlot < 0 || pSlot >= items.size()) return;
        items.set(pSlot, pStack);
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return SFMContainerUtil.stillValid(this, pPlayer);
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public FluidTank getTank() {
        return tank;
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
    }

    @Override
    protected Component getDefaultName() {
        return LocalizationKeys.TEST_BARREL_TANK_CONTAINER.getComponent();
    }

    @Override
    protected AbstractContainerMenu createMenu(
            int pContainerId,
            Inventory pInventory
    ) {
        return new TestBarrelTankContainerMenu(pContainerId, pInventory, this);
    }
}
