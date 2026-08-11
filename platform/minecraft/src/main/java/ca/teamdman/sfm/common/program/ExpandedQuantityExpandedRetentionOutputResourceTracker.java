package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("DuplicatedCode")
public class ExpandedQuantityExpandedRetentionOutputResourceTracker implements IOutputResourceTracker {
    private final ResourceLimit resource_limit;
    private final ResourceIdSet exclusions;
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<ResourceLocation>>
            retention_obligations_by_item = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<ResourceLocation>>
            transferred_by_item = new Object2ObjectOpenHashMap<>();

    public ExpandedQuantityExpandedRetentionOutputResourceTracker(
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
        long max_put = resource_limit.limit().retention().number().value();

        long transferred_for_item = 0;
        var transferred_for_resource_type = transferred_by_item.get(type);
        if (transferred_for_resource_type != null) {
            ResourceLocation item_id = type.getRegistryKeyForStack(stack);
            transferred_for_item = transferred_for_resource_type.getLong(item_id);
        }
        if (transferred_for_item >= can_transfer) {
            return true;
        }

        long retained_for_item = 0;
        var retained_for_resource_type = retention_obligations_by_item.get(type);
        if (retained_for_resource_type != null) {
            ResourceLocation item_id = type.getRegistryKeyForStack(stack);
            retained_for_item = retained_for_resource_type.getLong(item_id);
        }
        return retained_for_item >= max_put;
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
            ResourceLocation item_id = type.getRegistryKeyForStack(observed);
            retention_obligations_by_item.computeIfAbsent(type, k -> new Object2LongOpenHashMap<>())
                    .addTo(item_id, type.getAmount(observed));
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
        long retained_for_item = 0;
        var retained_for_resource_type = retention_obligations_by_item.get(resourceType);
        if (retained_for_resource_type != null) {
            ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
            retained_for_item = retained_for_resource_type.getLong(item_id);
        }
        long remainingRetentionRoom = max_retain - retained_for_item;

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
        retention_obligations_by_item.computeIfAbsent(resourceType, k -> new Object2LongOpenHashMap<>())
                .addTo(item_id, amount);
    }

    @Override
    public String toString() {
        return "ExpandedQuantityExpandedRetentionOutputResourceTracker{" +
                "resource_limit=" + resource_limit +
                ", exclusions=" + exclusions +
                ", retention_obligations_by_item=" + retention_obligations_by_item +
                ", transferred_by_item=" + transferred_by_item +
                '}';
    }
}
