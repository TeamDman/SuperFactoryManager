package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class ItemResourceType extends RegistryBackedResourceType<ItemStack, Item, IItemHandler> {
    public ItemResourceType() {
        super(SFMWellKnownCapabilities.ITEM_HANDLER);
    }

    @Override
    public SFMRegistryWrapper<Item> getRegistry() {
        return SFMWellKnownRegistries.ITEMS;
    }


    @Override
    public Item getItem(ItemStack itemStack) {
        return itemStack.getItem();
    }

    @Override
    public ItemStack copy(ItemStack stack) {
        return stack.copy();
    }

    @Override
    public IItemHandler createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new ItemStackHandler(contents.tier.numSlots) {
            @Override
            public boolean isItemValid(
                    int slot,
                    @NotNull ItemStack stack
            ) {
                boolean isValid = !this.getStackInSlot(0).isEmpty() || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Item;
                }
                return isValid;
            }
        };
    }

    @Override
    public long getAmount(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    public ItemStack getStackInSlot(
            IItemHandler cap,
            int slot
    ) {
        return cap.getStackInSlot(slot);
    }

    @Override
    public ItemStack extract(
            IItemHandler handler,
            int slot,
            long amount,
            boolean simulate
    ) {
        int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
        // Mekanism bin (not creative?) intentionally only returns stacks with count 64, avoiding going past the max stack size
        // https://github.com/mekanism/Mekanism/blob/f92b48a49e0766cd3aa78e95c9c4a47ba90402f5/src/main/java/mekanism/common/inventory/slot/BasicInventorySlot.java#L174-L175
        return handler.extractItem(slot, finalAmount, simulate);
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof ItemStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof IItemHandler;
    }

    /**
     * We want to also return block tags here.
     * <p>
     * <a href="https://github.com/CoFH/CoFHCore/blob/58b83bd0ef1676783323dce54788c3161faab49d/src/main/java/cofh/core/event/CoreClientEvents.java#L127">CoFH Core adds the "Press Ctrl for Tags" tooltip</a>
     * See: {@link cofh.core.event.CoreClientEvents#handleItemTooltipEvent(ItemTooltipEvent)}
     */
    @SuppressWarnings("JavadocReference")
    @Override
    public Stream<ResourceLocation> getTagsForStack(ItemStack itemStack) {
        // Get block tags
        Stream<TagKey<Block>> blockTagKeys;
        if (!itemStack.isEmpty()) {
            Block block = Block.byItem(itemStack.getItem());
            if (block != Blocks.AIR) {
                //noinspection deprecation
                blockTagKeys = block.builtInRegistryHolder().tags();
            } else {
                blockTagKeys = Stream.empty();
            }
        } else {
            blockTagKeys = Stream.empty();
        }

        // Get item tags
        //noinspection deprecation
        Stream<TagKey<Item>> itemTagKeys = itemStack.getItem().builtInRegistryHolder().tags();

        // Return union
        return Stream.concat(itemTagKeys, blockTagKeys).map(TagKey::location);
    }

    @Override
    public int getSlots(IItemHandler handler) {
        return handler.getSlots();
    }

    @Override
    public long getMaxStackSize(ItemStack itemStack) {
        return itemStack.getMaxStackSize();
    }

    @Override
    public long getMaxStackSizeForSlot(
            IItemHandler handler,
            int slot
    ) {
        return handler.getSlotLimit(slot);
    }

    /**
     * @return remaining stack that was not inserted
     */
    @Override
    public ItemStack insert(
            IItemHandler handler,
            int slot,
            ItemStack stack,
            boolean simulate
    ) {
        // undo the fix for testing by uncommenting this lol
//        if (true) {
//            return handler.insertItem(slot, stack, simulate);
//        }

        if (!simulate) {
            return handler.insertItem(slot, stack, false);
        }
        // When simulating, we want to avoid integer overflows in other mods by going a bit lower than MAX_INT
        // Example we are avoiding by doing this:
        /*
        [18:35:49] [Server thread/ERROR] [sfm/]: !!!RESOURCE LOSS HAS OCCURRED!!!    TRANSFORMER/sfm@4.19.1/ca.teamdman.sfml.ast.OutputStatement.moveTo(OutputStatement.java:197)
        === Summary ===
        Simulated extraction            : 2147483647 phytogro
        Simulated insertion remainder   : 1 air <-- the output block lied here
        Actual extraction               : 64 phytogro
        Actual insertion                : 1 phytogro
        Actual insertion remainder      : 63 phytogro (sfm:item:phytogro) <-- this is what was lost
        === Manager ===
        Level: minecraft:overworld (ServerLevel[test])
        Position: BlockPos{x=260, y=-60, z=628}
        === Input Slot ===
        Slot: 0
        Position: BlockPos{x=262, y=-60, z=628}
        Direction: up
        Capability: mekanism.common.capabilities.proxy.ProxyItemHandler@49cc116e (mekanism.common.capabilities.proxy.ProxyItemHandler)
        Block Entity: mekanism.common.tile.TileEntityBin (mekanism:creative_bin)
        Block: mekanism.common.block.basic.BlockBin (mekanism:creative_bin)
        Block State: Block{mekanism:creative_bin}[active=false,facing=north]
        === Output Slot ===
        Slot: 1
        Position: BlockPos{x=231, y=-2, z=628}
        Direction: null
        Capability: cofh.lib.inventory.ManagedItemHandler@3141c283 (cofh.lib.inventory.ManagedItemHandler)
        Block Entity: cofh.thermal.expansion.block.entity.machine.MachineInsolatorTile (thermal:machine_insolator)
        Block: cofh.core.block.TileBlockActive4Way (thermal:machine_insolator)
        Block State: Block{thermal:machine_insolator}[active=true,facing=north]
         */

        // https://discord.com/channels/399037120930512897/434101214548852746/1306494699758157824
        // Dr Lemming — 2024-11-14 2:13 AM
        //    cool so that's stupid and SFM shouldn't do that
        //    there's literally no reason to throw a max_int there
        //    I am unlikely to fix that

        int grace = 64 * 640;
        int count = stack.getCount();
        if (count > Integer.MAX_VALUE - grace) {
            // reduce the stack size in place
            stack.setCount(Integer.MAX_VALUE - grace);
            // perform the simulated insertion
            // hope that the callee isn't storing a reference where this deception will be noticed
            // it's a simulated insertion anyway, so there would have to be some really wack logic for this to be a problem
            ItemStack rtn = handler.insertItem(slot, stack, true);
            // restore the stack size
            stack.setCount(count);
            // the simulated amount is returning the remainder
            // because we lied about the stack size, we must bump the remainder by the difference
            if (rtn.isEmpty()) {
                // the air stack loses information about the item so we will clone our input instead
                rtn = copy(stack);
                rtn.setCount(grace);
            } else {
                rtn.grow(grace);
            }
            // return the result
            return rtn;
        } else {
            return handler.insertItem(slot, stack, true);
        }
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return stack.isEmpty();
    }

    @Override
    public ItemStack getEmptyStack() {
        return ItemStack.EMPTY;
    }

    @Override
    protected ItemStack setCount(
            ItemStack stack,
            long amount
    ) {
        stack.setCount((int) Math.min(amount, Integer.MAX_VALUE));
        return stack;
    }

}
