package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class TestBarrelTankBlockEntity extends BaseContainerBlockEntity {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEST_BARREL_TANK_CONTAINER = new LocalizationEntry(
            "container.sfm.test_barrel_tank",
            "Test Barrel Tank"
    );

    private final FluidTank tank = new FluidTank(1000);

    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    public TestBarrelTankBlockEntity(
            BlockPos pPos,
            BlockState pBlockState
    ) {

        super(SFMBlockEntities.TEST_BARREL_TANK.get(), pPos, pBlockState);
    }

    //    @Override
    @SuppressWarnings("unused") // 1.21.1 only
    public boolean isValidBlockState(BlockState blockState) {

        return SFMBlockEntities.TEST_BARREL.get().isValid(blockState);
    }

    @Override
    protected void loadAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.loadAdditional(pTag, pRegistries);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(pTag, this.items, pRegistries);
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

    @Override
    public NonNullList<ItemStack> getItems() {

        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> pItems) {
        this.items = pItems;
    }

    public FluidTank getTank() {

        return tank;
    }

    @Override
    protected void saveAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.saveAdditional(pTag, pRegistries);
        ContainerHelper.saveAllItems(pTag, this.items, pRegistries);
    }


    @Override
    protected Component getDefaultName() {

        return TEST_BARREL_TANK_CONTAINER.getComponent();
    }

    @Override
    protected AbstractContainerMenu createMenu(
            int pContainerId,
            Inventory pInventory
    ) {

        return new TestBarrelTankContainerMenu(pContainerId, pInventory, this);
    }

}
