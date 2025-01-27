package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.CapabilityConsumer;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfml.ast.*;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class ResourceType<STACK, ITEM, CAP> {
    private final Map<ITEM, ResourceLocation> registryKeyCache = new Object2ObjectOpenHashMap<>();


    public final BlockCapability<CAP, @Nullable Direction> CAPABILITY_KIND;

    public ResourceType(BlockCapability<CAP, @Nullable Direction> CAPABILITY_KIND) {
        this.CAPABILITY_KIND = CAPABILITY_KIND;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceType<?, ?, ?> that)) return false;
        return Objects.equals(CAPABILITY_KIND, that.CAPABILITY_KIND);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(CAPABILITY_KIND);
    }

    public abstract long getAmount(STACK stack);

    /**
     * Some resource types may exceed MAX_LONG, this method should be used to get the difference between two stacks
     */
    public long getAmountDifference(
            STACK stack1,
            STACK stack2
    ) {
        return getAmount(stack1) - getAmount(stack2);
    }

    public abstract STACK getStackInSlot(
            CAP cap,
            int slot
    );

    public abstract STACK extract(
            CAP cap,
            int slot,
            long amount,
            boolean simulate
    );

    public abstract int getSlots(CAP handler);

    public abstract long getMaxStackSize(STACK stack);

    public abstract long getMaxStackSizeForSlot(
            CAP cap,
            int slot
    );

    public abstract STACK insert(
            CAP cap,
            int slot,
            STACK stack,
            boolean simulate
    );

    public abstract boolean isEmpty(STACK stack);

    @SuppressWarnings("unused")
    public abstract STACK getEmptyStack();

    public abstract boolean matchesStackType(Object o);

    public boolean matchesStack(
            ResourceIdentifier<STACK, ITEM, CAP> resourceId,
            Object stack
    ) {
        if (!matchesStackType(stack)) return false;
        @SuppressWarnings("unchecked") STACK stack_ = (STACK) stack;
        if (isEmpty(stack_)) return false;
        var stackId = getRegistryKey(stack_);
        return resourceId.matchesResourceLocation(stackId);
    }

    public abstract boolean matchesCapabilityType(Object o);

    public void forEachCapability(
            ProgramContext programContext,
            LabelAccess labelAccess,
            CapabilityConsumer<CAP> consumer
    ) {
        // Log
        programContext
                .getLogger()
                .trace(x -> x.accept(LocalizationKeys.LOG_RESOURCE_TYPE_GET_CAPABILITIES_BEGIN.get(
                        displayAsCode(),
                        displayAsCapabilityClass(),
                        labelAccess
                )));

        CableNetwork network = programContext.getNetwork();
        RoundRobin roundRobin = labelAccess.roundRobin();
        LabelPositionHolder labelPositionHolder = programContext.getLabelPositionHolder();
        HashMap<Pair<Label, BlockPos>, Integer> positions = roundRobin.getPositionsForLabels(
                labelAccess,
                labelPositionHolder
        );

        for (var pair : positions.keySet()) {
            Label label = pair.getFirst();
            BlockPos pos = pair.getSecond();
            // Expand pos to (pos, direction) pairs
            for (Direction dir : labelAccess.directions()) {
                // Get capability from the network
                var maybeCap = network
                        .getCapability(CAPABILITY_KIND, pos, dir, programContext.getLogger());
                if (maybeCap != null) {
                    CAP cap = maybeCap.getCapability();
                    if (cap != null) {
                        programContext
                                .getLogger()
                                .debug(x -> x.accept(LocalizationKeys.LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_PRESENT.get(
                                        displayAsCapabilityClass(),
                                        pos,
                                        dir
                                )));
                        consumer.accept(label, pos, dir, cap, positions.get(pair));
                        continue;
                    }
                }
                // Log error
                programContext
                        .getLogger()
                        .error(x -> x.accept(LocalizationKeys.LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_NOT_PRESENT.get(
                                displayAsCapabilityClass(),
                                pos,
                                dir
                        )));
            }
        }
    }

    public abstract Stream<ResourceLocation> getTagsForStack(STACK stack);

    public Stream<STACK> getStacksInSlots(
            CAP cap,
            NumberRangeSet slots
    ) {
        var rtn = Stream.<STACK>builder();
        for (int slot = 0; slot < getSlots(cap); slot++) {
            if (!slots.contains(slot)) continue;
            var stack = getStackInSlot(cap, slot);
            if (!isEmpty(stack)) {
                rtn.add(stack);
            }
        }
        return rtn.build();
    }

    public boolean registryKeyExists(ResourceLocation location) {
        return getRegistry().containsKey(location);
    }

    public ResourceLocation getRegistryKey(STACK stack) {
        ITEM item = getItem(stack);
        var found = registryKeyCache.get(item);
        if (found != null) return found;
        found = getRegistry().getKey(item);
        if (found == null) {
            throw new NullPointerException("Registry key not found for item: " + item);
        }
        registryKeyCache.put(item, found);
        return found;
    }

    public abstract Registry<ITEM> getRegistry();

    public abstract ITEM getItem(STACK stack);

    public abstract STACK copy(STACK stack);

    @SuppressWarnings("unused")
    public STACK withCount(
            STACK stack,
            long count
    ) {
        return setCount(copy(stack), count);
    }

    public String displayAsCode() {
        ResourceLocation thisKey = SFMResourceTypes.DEFERRED_TYPES.getKey(this);
        return thisKey != null ? thisKey.toString() : "null";
    }

    public String displayAsCapabilityClass() {
        return CAPABILITY_KIND.name().toString();
    }

    protected abstract STACK setCount(
            STACK stack,
            long amount
    );

}
