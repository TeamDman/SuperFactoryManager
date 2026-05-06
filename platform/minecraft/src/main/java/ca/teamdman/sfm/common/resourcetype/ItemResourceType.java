package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.stream.Stream;

public class ItemResourceType extends RegistryBackedResourceType<ResourceStack<ItemResource>, Item, ResourceHandler<ItemResource>> {
    public ItemResourceType() {
        super(SFMWellKnownCapabilities.ITEM_HANDLER);
    }

    @Override
    public SFMRegistryWrapper<Item> getRegistry() {
        return SFMWellKnownRegistries.ITEMS;
    }


    @Override
    public Item getItem(ResourceStack<ItemResource> stack) {
        return stack.resource().getItem();
    }

    @Override
    public ResourceHandler<ItemResource> createHandlerForBufferBlock(BufferBlockEntityContents contents) {
        return new ItemStacksResourceHandler(contents.tier.numSlots) {
            @Override
            public boolean isValid(int index, ItemResource resource) {
                boolean isValid = !this.getResource(0).isEmpty() || contents.isEmpty();
                if (isValid) {
                    contents.lastUsedResource = BufferBlock.ContainedResource.Item;
                }
                return isValid;
            }
        };
    }

    @Override
    public long getAmount(ResourceStack<ItemResource> stack) {
        return stack.amount();
    }

    @Override
    public ResourceStack<ItemResource> getStackInSlot(
            ResourceHandler<ItemResource> cap,
            int slot
    ) {
        return new ResourceStack<>(cap.getResource(slot), cap.getAmountAsInt(slot));
    }

    @Override
    public ResourceStack<ItemResource> extract(
            ResourceHandler<ItemResource> handler,
            int slot,
            long amount,
            TransactionContext tx
    ) {
        try (var ctx = Transaction.open(tx)) {
            int finalAmount = amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
            // Mekanism bin (not creative?) intentionally only returns stacks with count 64, avoiding going past the max stack size
            // https://github.com/mekanism/Mekanism/blob/f92b48a49e0766cd3aa78e95c9c4a47ba90402f5/src/main/java/mekanism/common/inventory/slot/BasicInventorySlot.java#L174-L175
            ItemResource resource = handler.getResource(slot);
            int extracted = handler.extract(slot, resource, finalAmount, ctx);
            ctx.commit();

            return new ResourceStack<>(resource, extracted);
        }
    }

    @Override
    public boolean matchesStackType(Object o) {
        return o instanceof ItemStack;
    }

    @Override
    public boolean matchesCapabilityHandler(Object o) {
        return o instanceof ResourceHandler;
    }

    /**
     * We want to also return block tags here.
     * <p>
     * <a href="https://github.com/CoFH/CoFHCore/blob/58b83bd0ef1676783323dce54788c3161faab49d/src/main/java/cofh/core/event/CoreClientEvents.java#L127">CoFH Core adds the "Press Ctrl for Tags" tooltip</a>
     * See: {@link cofh.core.event.CoreClientEvents#handleItemTooltipEvent(ItemTooltipEvent)}
     */
    @SuppressWarnings("JavadocReference")
    @Override
    public Stream<Identifier> getTagsForStack(Item stack) {
        // Get block tags
        Stream<TagKey<Block>> blockTagKeys;
            Block block = Block.byItem(stack);
            if (block != Blocks.AIR) {
                //noinspection deprecation
                blockTagKeys = block.builtInRegistryHolder().tags();
            } else {
                blockTagKeys = Stream.empty();
            }
        // Get item tags
        //noinspection deprecation
        Stream<TagKey<Item>> itemTagKeys = stack.builtInRegistryHolder().tags();

        // Return union
        return Stream.concat(itemTagKeys, blockTagKeys).map(TagKey::location);
    }

    @Override
    public Item stackToItem(ResourceStack<ItemResource> stack) {
        return stack.resource().getItem();
    }

    @Override
    public int getSlots(ResourceHandler<ItemResource> handler) {
        return handler.size();
    }

    @Override
    public long getMaxStackSize(ResourceStack<ItemResource> stack) {
        return stack.resource().getMaxStackSize();
    }

    @Override
    public long getMaxStackSizeForSlot(
            ResourceHandler<ItemResource> handler,
            int slot
    ) {
        return handler.getCapacityAsLong(slot, handler.getResource(slot));
    }

    /**
     * @return remaining stack that was not inserted
     */
    @Override
    public ResourceStack<ItemResource> insert(
            ResourceHandler<ItemResource> handler,
            int slot,
            ResourceStack<ItemResource> stack,
            TransactionContext tx
    ) {
        try (var ctx = Transaction.open(tx)) {
            ItemResource resource = stack.resource();
            int amount = stack.amount();

            int inserted = handler.insert(slot, resource, amount, ctx);
            ctx.commit();

            return new ResourceStack<>(resource, amount - inserted);
        }
    }

    @Override
    public boolean isEmpty(ResourceStack<ItemResource> stack) {
        return stack.isEmpty();
    }
}
