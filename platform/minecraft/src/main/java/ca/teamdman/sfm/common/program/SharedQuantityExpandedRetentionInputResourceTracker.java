package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("DuplicatedCode")
public class SharedQuantityExpandedRetentionInputResourceTracker implements IInputResourceTracker {
    private final ResourceLimit resource_limit;
    private final ResourceIdSet exclusions;
    private final Long2ObjectOpenHashMap<Int2ObjectArrayMap<Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<ResourceLocation>>>>
            retention_obligations_by_pos_by_slot_by_item = new Long2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<ResourceLocation>>
            retention_obligations_by_item = new Object2ObjectOpenHashMap<>();
    private long transferred = 0;

    public SharedQuantityExpandedRetentionInputResourceTracker(
            ResourceLimit resourceLimit,
            ResourceIdSet exclusions
    ) {
        this.resource_limit = resourceLimit;
        this.exclusions = exclusions;
    }

    @Override
    public <STACK, CAP, ITEM> boolean isDone(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    ) {
        long can_transfer = resource_limit.limit().quantity().number().value();
        return transferred >= can_transfer;
    }

    @Override
    public ResourceLimit getResourceLimit() {
        return resource_limit;
    }

    @Override
    public ResourceIdSet getExclusions() {
        return exclusions;
    }

    @Override
    public <STACK, ITEM, CAP> long getRetentionObligationForSlot(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            BlockPos pos,
            int slot
    ) {
        var posEntry = retention_obligations_by_pos_by_slot_by_item.get(pos.asLong());
        if (posEntry != null) {
            var resourceTypeEntry = posEntry.get(slot);
            if (resourceTypeEntry != null) {
                ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
                var itemEntry = resourceTypeEntry.get(resourceType);
                if (itemEntry != null) {
                    return itemEntry.getLong(item_id);
                }
            }
        }
        return 0;
    }

    @Override
    public <STACK, ITEM, CAP> long getRemainingRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key
    ) {
        long retention = resource_limit.limit().retention().number().value();
        long progress = 0;
        // don't use getOrDefault to avoid allocations
        var entry = retention_obligations_by_item.get(resourceType);
        if (entry != null) {
            ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
            if (entry.containsKey(item_id)) {
                progress = entry.getLong(item_id);
            }
        }
        return retention - progress;
    }

    @Override
    public <STACK, ITEM, CAP> void trackRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            int slot,
            BlockPos pos,
            long dedicatingToObligation
    ) {
        ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
        retention_obligations_by_item.computeIfAbsent(resourceType, k -> new Object2LongOpenHashMap<>())
                .addTo(item_id, dedicatingToObligation);
        retention_obligations_by_pos_by_slot_by_item
                .computeIfAbsent(pos.asLong(), k -> new Int2ObjectArrayMap<>())
                .computeIfAbsent(slot, k -> new Object2ObjectOpenHashMap<>())
                .computeIfAbsent(resourceType, k -> new Object2LongOpenHashMap<>())
                .addTo(item_id, dedicatingToObligation);
    }

    @Override
    public <STACK, ITEM, CAP> long getMaxTransferable(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack
    ) {
        long max = resource_limit.limit().quantity().number().value();
        return max - transferred;
    }

    @Override
    public <STACK, ITEM, CAP> void trackTransfer(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            long amount
    ) {
        transferred += amount;
    }

    @Override
    public String toString() {
        return "SharedQuantityExpandedRetentionInputResourceTracker@"
               + Integer.toHexString(System.identityHashCode(this))
               + "{"
               +
               "TRANSFERRED="
               + transferred
               +
               ", RETENTION_OBLIGATION_PROGRESS="
               + retention_obligations_by_item
                       .values()
                       .stream()
                       .flatMapToLong(x -> x.values().longStream())
                       .sum()
               +
               ", RESOURCE_LIMIT="
               + resource_limit
               +
               ", EXCLUSIONS="
               + exclusions
               +
               "}";
    }
}
