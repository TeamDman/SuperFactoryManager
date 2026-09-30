package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}

@SuppressWarnings("DuplicatedCode")
public class SharedQuantityExpandedRetentionOutputResourceTracker implements IOutputResourceTracker {
    private final ResourceLimit resource_limit;
    private final ResourceIdSet exclusions;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<ResourceLocation>>
{% when '26.1.2' %}
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<Identifier>>
{% endcase %}
            retention_obligations_by_item = new Object2ObjectOpenHashMap<>();
    private long transferred = 0;

    public SharedQuantityExpandedRetentionOutputResourceTracker(
            ResourceLimit resourceLimit,
            ResourceIdSet exclusions
    ) {
        this.resource_limit = resourceLimit;
        this.exclusions = exclusions;
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
    public <STACK, CAP, ITEM> boolean isDone(
            ResourceType<STACK, ITEM, CAP> type,
            STACK stack
    ) {
        long max_transfer = resource_limit.limit().quantity().number().value();
        if (transferred >= max_transfer) {
            return true;
        }

        long max_put = resource_limit.limit().retention().number().value();
        long retained_for_item = 0;
        var retained_for_resource_type = retention_obligations_by_item.get(type);
        if (retained_for_resource_type != null) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation item_id = type.getRegistryKeyForStack(stack);
{% when '26.1.2' %}
            Identifier item_id = type.getRegistryKeyForStack(stack);
{% endcase %}
            retained_for_item = retained_for_resource_type.getLong(item_id);
        }
        return retained_for_item >= max_put;
    }

    @Override
    public <STACK, ITEM, CAP> void updateRetentionObservation(
            ResourceType<STACK, ITEM, CAP> type,
            STACK observed
    ) {
        if (matchesStack(observed)) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation item_id = type.getRegistryKeyForStack(observed);
{% when '26.1.2' %}
            Identifier item_id = type.getRegistryKeyForStack(observed);
{% endcase %}
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
        long unusedQuantity = max_transfer - transferred;

        long max_retain = resource_limit.limit().retention().number().value();
        long retained_for_item = 0;
        var retained_for_resource_type = retention_obligations_by_item.get(resourceType);
        if (retained_for_resource_type != null) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
{% when '26.1.2' %}
            Identifier item_id = resourceType.getRegistryKeyForStack(key);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        ResourceLocation item_id = resourceType.getRegistryKeyForStack(key);
{% when '26.1.2' %}
        Identifier item_id = resourceType.getRegistryKeyForStack(key);
{% endcase %}
        transferred += amount;
        retention_obligations_by_item.computeIfAbsent(resourceType, k -> new Object2LongOpenHashMap<>())
                .addTo(item_id, amount);
    }

    @Override
    public String toString() {
        return "SharedQuantityExpandedRetentionOutputResourceTracker{" +
               "resource_limit=" + resource_limit +
               ", exclusions=" + exclusions +
               ", transferred=" + transferred +
               ", retention_obligations_by_item=" + retention_obligations_by_item +
               '}';
    }
}
