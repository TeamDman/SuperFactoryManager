package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.BufferBlockTier;
import ca.teamdman.sfm.common.capability.IRedstoneSignalStorage;
import ca.teamdman.sfm.common.capability.RedstoneSignalStorage;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import net.minecraft.nbt.IntTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// Can hold one stack worth of any resource type.
/// Each resource type has its own independent handler.
/// Handlers are created on demand and cached.
/// Only one resource type can have a non-empty handler at a time.
@SuppressWarnings({"unchecked", "rawtypes"})
public class BufferBlockEntityContents {
    /// Mapping from resource type to the handler.
    private final Map<ResourceType<?, ?, ?>, Object> contents = new HashMap<>();
    public final BufferBlockTier tier;
    private final Runnable onChanged;

    public BufferBlockEntityContents(BufferBlockTier tier) {
        this(tier, () -> {});
    }

    public BufferBlockEntityContents(BufferBlockTier tier, Runnable onChanged) {
        this.tier = tier;
        this.onChanged = onChanged;
    }

    public BufferBlock.ContainedResource lastUsedResource = BufferBlock.ContainedResource.Unknown;

    public int getStoredRedstone() {
        Object handler = contents.get(SFMResourceTypes.REDSTONE.get());
        return handler instanceof IRedstoneSignalStorage storage ? storage.getStoredAmount() : 0;
    }

    public void onRedstoneChanged() {
        lastUsedResource = getBlockIconType();
        onChanged.run();
    }

    /// Restore in place so existing capability handles observe the loaded count.
    /// Other experimental buffer resource types do not yet have persistence.
    public void loadRedstone(long amount) {
        var type = SFMResourceTypes.REDSTONE.get();
        Object existing = contents.get(type);
        if (existing instanceof RedstoneSignalStorage storage) {
            storage.deserializeNBT(IntTag.valueOf(0));
        }
        if (amount > 0 && isEmpty()) {
            var storage = (RedstoneSignalStorage) getCapability(type).unwrap();
            storage.deserializeNBT(IntTag.valueOf((int) Math.min(amount, Integer.MAX_VALUE)));
        }
        lastUsedResource = getBlockIconType();
    }


    /// Should return None if querying for a resource type when other resource types are not empty.
    public <CAP> SFMBlockCapabilityResult<CAP> getCapability(
            ResourceType<?, ?, CAP> type
    ) {
        // Discover existing handler
        @Nullable CAP handler = (CAP) contents.get(type);

        if (handler != null) {
            // The handler is present.
            if (!type.isHandlerEmpty(handler)) {
                // When the handler is not empty, assume that it's the one in use.
                return SFMBlockCapabilityResult.of(handler);
            } else {
                // When the handler is empty, make sure all other handlers are empty before allowing access.
                if (isEmpty()) {
                    return SFMBlockCapabilityResult.of(handler);
                } else {
                    return SFMBlockCapabilityResult.empty();
                }
            }
        } else {
            // The handler is absent.
            // We can only create a new one if all other handlers are empty.
            if (!isEmpty()) {
                return SFMBlockCapabilityResult.empty();
            }

            handler = type.createHandlerForBufferBlock(this);
            contents.put(type, handler);
            return SFMBlockCapabilityResult.of(handler);
        }
    }

    /// This is empty when all present handlers are empty.
    public boolean isEmpty() {
        for (Map.Entry<ResourceType<?, ?, ?>, Object> entry : contents.entrySet()) {
            if (!isHandlerEmpty(
                    (ResourceType) entry.getKey(),
                    entry.getValue()
            )) {
                return false;
            }
        }
        return true;
    }

    public BufferBlock.ContainedResource getBlockIconType() {
        for (Map.Entry<ResourceType<?, ?, ?>, Object> entry : contents.entrySet()) {
            ResourceType resourceType = entry.getKey();
            Object cap = entry.getValue();
            boolean handlerEmpty = isHandlerEmpty(resourceType, cap);
            if (!handlerEmpty) {
                return BufferBlock.ContainedResource.from(resourceType);
            }
        }
        return BufferBlock.ContainedResource.Unknown;
    }

    /// Allow insertion of resources that are already present, and when empty.
    public boolean allowInsertion(ResourceType<?, ?, ?> queryType) {
        for (Map.Entry<ResourceType<?, ?, ?>, Object> entry : contents.entrySet()) {
            ResourceType entryType = entry.getKey();
            Object entryHandler = entry.getValue();
            boolean handlerEmpty = isHandlerEmpty(entryType, entryHandler);
            if (!handlerEmpty) {
                // Something is present, only allow insertion of the same type.
                return entryType.equals(queryType);
            }
        }
        return true;
    }

    /// Check if a specific handler is empty.
    /// Helps with generic safety.
    private <STACK, ITEM, CAP> boolean isHandlerEmpty(
            ResourceType<STACK, ITEM, CAP> type,
            CAP handler
    ) {
        return type.isHandlerEmpty(handler);
    }
}
