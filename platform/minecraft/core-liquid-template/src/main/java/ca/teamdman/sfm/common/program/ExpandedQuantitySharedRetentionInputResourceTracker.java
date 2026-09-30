package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}

@SuppressWarnings("DuplicatedCode")
public class ExpandedQuantitySharedRetentionInputResourceTracker implements IInputResourceTracker {
    private final ResourceLimit resource_limit;
    private final ResourceIdSet exclusions;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<ResourceLocation>>
{% when '26.1.2' %}
    private final Object2ObjectOpenHashMap<ResourceType<?, ?, ?>, Object2LongOpenHashMap<Identifier>>
{% endcase %}
            transferred_by_item = new Object2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Int2LongArrayMap> retention_obligations_by_pos_by_slot = new Long2ObjectOpenHashMap<>();
    private long retention_obligation_progress = 0;

    public ExpandedQuantitySharedRetentionInputResourceTracker(
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
        long transferred_for_item = 0;
        var transferred_for_resource_type = transferred_by_item.get(type);
        if (transferred_for_resource_type != null) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation item_id = type.getRegistryKeyForStack(stack);
{% when '26.1.2' %}
            Identifier item_id = type.getRegistryKeyForStack(stack);
{% endcase %}
            transferred_for_item = transferred_for_resource_type.getLong(item_id);
        }
        return transferred_for_item >= can_transfer;
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
        var posEntry = retention_obligations_by_pos_by_slot.get(pos.asLong());
        if (posEntry == null) {
            return 0;
        }
        return posEntry.getOrDefault(slot, 0);
    }

    @Override
    public <STACK, ITEM, CAP> long getRemainingRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key
    ) {
        return resource_limit.limit().retention().number().value() - retention_obligation_progress;
    }

    @Override
    public <STACK, ITEM, CAP> void trackRetentionObligation(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK key,
            int slot,
            BlockPos pos,
            long dedicatingToObligation
    ) {
        this.retention_obligation_progress += dedicatingToObligation;
        this.retention_obligations_by_pos_by_slot
                .computeIfAbsent(pos.asLong(), k -> new Int2LongArrayMap())
                .merge(slot, dedicatingToObligation, Long::sum);
    }

    @Override
    public <STACK, ITEM, CAP> long getMaxTransferable(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack
    ) {
        long max_transfer = resource_limit.limit().quantity().number().value();
        long transferred_for_item = 0;
        var transferred_for_resource_type = transferred_by_item.get(resourceType);
        if (transferred_for_resource_type != null) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            ResourceLocation item_id = resourceType.getRegistryKeyForStack(stack);
{% when '26.1.2' %}
            Identifier item_id = resourceType.getRegistryKeyForStack(stack);
{% endcase %}
            transferred_for_item = transferred_for_resource_type.getLong(item_id);
        }
        return max_transfer - transferred_for_item;
    }

    @Override
    public <STACK, ITEM, CAP> void trackTransfer(
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            long amount
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        ResourceLocation item_id = resourceType.getRegistryKeyForStack(stack);
{% when '26.1.2' %}
        Identifier item_id = resourceType.getRegistryKeyForStack(stack);
{% endcase %}
        transferred_by_item.computeIfAbsent(resourceType, k -> new Object2LongOpenHashMap<>())
                .addTo(item_id, amount);
    }

}
