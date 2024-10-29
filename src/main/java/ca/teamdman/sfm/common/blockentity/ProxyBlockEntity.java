package ca.teamdman.sfm.common.blockentity;

//import ca.teamdman.sfm.common.containermenu.ProxyContainerMenu;

import ca.teamdman.sfm.common.containermenu.ProxyContainerMenu;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.util.SFMContainerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/*
 * https://github.com/CyclopsMC/CapabilityProxy/blob/master-1.21/loader-neoforge/src/main/java/org/cyclops/capabilityproxy/blockentity/BlockEntityItemCapabilityProxyNeoForge.java
 */
public class ProxyBlockEntity extends BlockEntity implements MenuProvider, Container {
    private static final Component TITLE = Component.literal("Item Capability Proxy");

    public static Map<BlockCapability<?, ?>, ItemCapability<?, ?>> BLOCK_TO_ITEM_CAPABILITIES = Map.of();

    private final NonNullList<ItemStack> inventory = NonNullList.withSize(1, ItemStack.EMPTY);

    public ProxyBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(SFMBlockEntities.PROXY_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    @Nullable
    public static <T, C1, C2> ItemCapability<T, C2> blockCapabilityToItemCapability(BlockCapability<T, C1> capability) {
        return (ItemCapability<T, C2>) BLOCK_TO_ITEM_CAPABILITIES.get(capability);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        invalidateCapabilities();
    }


    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
        return new ProxyContainerMenu(windowId, inventory, this);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public int getContainerSize() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slotId) {
        if (slotId < 0 || slotId >= getContainerSize()) return ItemStack.EMPTY;
        return inventory.get(slotId);
    }

    @Override
    public ItemStack removeItem(int slotId, int amount) {
        ItemStack result = ContainerHelper.removeItem(inventory, slotId, amount);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slotId) {
        ItemStack result = ContainerHelper.takeItem(inventory, slotId);
        setChanged();
        return result;
    }

    @Override
    public void setItem(int slotId, ItemStack itemStack) {
        if (slotId < 0 || slotId >= getContainerSize()) return;
        inventory.set(slotId, itemStack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return SFMContainerUtil.stillValid(this, player);
    }

    @Override
    public void clearContent() {
        inventory.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.saveAdditional(pTag, pRegistries);
        ContainerHelper.saveAllItems(pTag, inventory, pRegistries);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        ContainerHelper.loadAllItems(pTag, inventory, pRegistries);
    }

    @Nullable
    public <T, C1, C2> T getCapability(BlockCapability<T, C1> blockCapability, @Nullable C1 context) {
        if (context == Direction.UP | context == Direction.DOWN) {
            if (blockCapability == Capabilities.ItemHandler.BLOCK) {
                return (T) new InvWrapper(this);
            }
            return null;
        }

        ItemStack itemStack = getItem(0);
        ItemCapability<T, C2> itemCapability = blockCapabilityToItemCapability(blockCapability);
        if (itemCapability == null) {
            return null;
        }
        T cap = itemStack.getCapability(itemCapability, null);

        return cap;
    }
}