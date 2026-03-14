package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("DuplicatedCode")
public class ExpandedQuantitySharedRetentionOutputResourceTracker implements IOutputResourceTracker {
    private final ResourceLimit resource_limit;
    private final ResourceIdSet exclusions;
    private long retention_obligation_progress = 0;
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<ResourceLocation>>
            transferred_by_item = new Object2ObjectOpenHashMap<>();

    public ExpandedQuantitySharedRetentionOutputResourceTracker(
            ResourceLimit resourceLimit,
            ResourceIdSet exclusions
    ) {
        this.resource_limit = resourceLimit;
        this.exclusions = exclusions;
    }

    @SuppressWarnings("RedundantIfStatement")
    @Override
    public <STACK, CAP, ITEM> boolean isDone(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    ) {
        long max_put = resource_limit.limit().retention().number().value();
        if (retention_obligation_progress >= max_put) {
            return true;
        }

        long can_transfer = resource_limit.limit().quantity().number().value();
        long transferred_for_item = 0;
        var transferred_for_resource_type = transferred_by_item.get(type);
        if (transferred_for_resource_type != null) {
            ResourceLocation item_id = type.getRegistryKeyForStack(stack);
            transferred_for_item = transferred_for_resource_type.getLong(item_id);
        }
        if (transferred_for_item >= can_transfer) {
            return true;
        }
        return false;
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
    public <STACK, ITEM, CAP> void updateRetentionObservation(
            ResourceType<STACK, ITEM, CAP> type,
            STACK observed
    ) {
        if (matchesStack(observed)) {
            retention_obligation_progress += type.getAmount(observed);
        }
    }

    @Override
    public <STACK, ITEM, CAP> long getMaxTransferable(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key
    ) {
        long max_transfer = resource_limit.limit().quantity().number().value();
        long transferred_for_item = 0;
        var transferred_for_resource_type = transferred_by_item.get(resourceType);
        if (transferred_for_resource_type != null) {
            ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
            transferred_for_item = transferred_for_resource_type.getLong(item_id);
        }
        long unusedQuantity = max_transfer - transferred_for_item;

        long max_retain = resource_limit.limit().retention().number().value();
        long remainingRetentionRoom = max_retain - retention_obligation_progress;

        return Math.min(unusedQuantity, remainingRetentionRoom);
    }

    @Override
    public <STACK, ITEM, CAP> void trackTransfer(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            long amount
    ) {
        ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
        transferred_by_item.computeIfAbsent(resourceType, k -> new Object2LongOpenHashMap<>())
                .addTo(item_id, amount);
        retention_obligation_progress += amount;
    }

    @Override
    public String toString() {
        return "ExpandedQuantityExpandedRetentionOutputResourceTracker{" +
               "resource_limit=" + resource_limit +
               ", exclusions=" + exclusions +
               ", retention_obligation_progress=" + retention_obligation_progress +
               ", transferred_by_item=" + transferred_by_item +
               '}';
    }
}
