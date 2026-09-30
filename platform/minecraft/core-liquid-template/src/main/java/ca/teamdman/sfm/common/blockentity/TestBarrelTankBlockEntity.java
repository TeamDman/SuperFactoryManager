package ca.teamdman.sfm.common.blockentity;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.containermenu.TestBarrelTankContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
import net.minecraft.core.Direction;
{% when '1.20.3', '1.20.4', '26.1.2' %}
{% when '1.21', '1.21.1' %}
import net.minecraft.core.HolderLookup;
{% endcase %}
import net.minecraft.core.NonNullList;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.nbt.CompoundTag;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;
{% when '1.20.2' %}
import net.neoforged.neoforge.common.capabilities.Capability;
import net.neoforged.neoforge.common.util.LazyOptional;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;
{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
{% when '26.1.2' %}
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
{% endcase %}

public class TestBarrelTankBlockEntity extends BaseContainerBlockEntity {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEST_BARREL_TANK_CONTAINER = new LocalizationEntry(
            "container.sfm.test_barrel_tank",
            "Test Barrel Tank"
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
    private final LazyOptional<IItemHandler> item_capability = LazyOptional.of(() -> new InvWrapper(this));

    private final FluidTank tank = new FluidTank(1000);

    public final LazyOptional<IFluidHandler> fluid_capability = LazyOptional.of(() -> tank);

{% when '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final FluidTank tank = new FluidTank(1000);

{% when '26.1.2' %}
    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, 1000);

{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
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
{% when '1.20.3', '1.20.4' %}
    @Override
    public void load(CompoundTag pTag) {

        super.load(pTag);
{% when '1.21', '1.21.1' %}
    @Override
    protected void loadAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.loadAdditional(pTag, pRegistries);
{% when '26.1.2' %}
    @Override
    protected void loadAdditional(
            ValueInput input
    ) {
        super.loadAdditional(input);
{% endcase %}
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1' %}
        ContainerHelper.loadAllItems(pTag, this.items, pRegistries);
{% when '26.1.2' %}
        ContainerHelper.loadAllItems(input, this.items);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}

{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
    @Override
{% endcase %}
    public NonNullList<ItemStack> getItems() {

        return items;
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public FluidTank getTank() {
{% when '1.21', '1.21.1' %}
    @Override
    protected void setItems(NonNullList<ItemStack> pItems) {
        this.items = pItems;
    }

    public FluidTank getTank() {
{% when '26.1.2' %}
    @Override
    protected void setItems(NonNullList<ItemStack> pItems) {
        this.items = pItems;
    }

    public FluidStacksResourceHandler getTank() {
{% endcase %}

        return tank;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    protected void saveAdditional(CompoundTag pTag) {

        super.saveAdditional(pTag);
{% when '1.21', '1.21.1' %}
    protected void saveAdditional(
            CompoundTag pTag,
            HolderLookup.Provider pRegistries
    ) {
        super.saveAdditional(pTag, pRegistries);
        ContainerHelper.saveAllItems(pTag, this.items, pRegistries);
{% when '26.1.2' %}
    protected void saveAdditional(
            ValueOutput output
    ) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items, true);
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}

{% endcase %}
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
