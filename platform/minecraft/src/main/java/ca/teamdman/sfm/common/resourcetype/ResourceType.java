package ca.teamdman.sfm.common.resourcetype;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.program.CapabilityConsumer;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfml.ast.*;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public abstract class ResourceType<STACK, ITEM, CAP> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_RESOURCE_TYPE_GET_CAPABILITIES_BEGIN = new LocalizationEntry(
            "log.sfm.resource_type.get_capabilities.begin",
            "Gathering capabilities of type %s (%s) against labels %s"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_NOT_PRESENT = new LocalizationEntry(
            "log.sfm.resource_type.get_capabilities.not_present",
            "Capability %s %s direction=%s not present"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_PRESENT = new LocalizationEntry(
            "log.sfm.resource_type.get_capabilities.present",
            "Capability %s %s direction=%s present"
    );

    public final SFMBlockCapabilityKind<CAP> CAPABILITY_KIND;

    public ResourceType(SFMBlockCapabilityKind<CAP> CAPABILITY_KIND) {

        this.CAPABILITY_KIND = CAPABILITY_KIND;
    }

    public SFMBlockCapabilityKind<CAP> capabilityKind() {

        return CAPABILITY_KIND;
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

    /// Creates a new empty handler for use in a {@link BufferBlockEntityContents}.
    /// This handler should only accept items when the buffer is empty or when the handler is already not empty.
    ///
    /// Note that in the implementations, we always check if this.hasAnything() before contents.isEmpty() since the former
    /// should be a less expensive check.
    public abstract CAP createHandlerForBufferBlock(BufferBlockEntityContents contents);

    public boolean isHandlerEmpty(CAP cap) {

        for (int slot = 0; slot < getSlots(cap); slot++) {
            if (!isEmpty(getStackInSlot(cap, slot))) {
                return false;
            }
        }
        return true;
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
            TransactionContext tx
    );

    public abstract int getSlots(CAP handler);

    public abstract long getMaxStackSize(STACK stack);

    public abstract long getMaxStackSizeForSlot(
            CAP cap,
            int slot
    );

    /**
     * @return the remainder, what was not inserted
     */
    public abstract STACK insert(
            CAP cap,
            int slot,
            STACK stack,
            TransactionContext tx
    );

    public abstract boolean isEmpty(STACK stack);

    public abstract boolean matchesStackType(Object o);

    public boolean matchesStack(
            ResourceIdentifier<STACK, ITEM, CAP> resourceId,
            Object stack
    ) {

        if (!matchesStackType(stack)) return false;
        @SuppressWarnings("unchecked") STACK stack_ = (STACK) stack;
        if (isEmpty(stack_)) return false;
        var stackId = getRegistryKeyForStack(stack_);
        return resourceId.matchesResourceLocation(stackId);
    }

    /// Checks if the provided handler is an instance of the capability associated with this resource type
    public abstract boolean matchesCapabilityHandler(Object o);

    public void forEachCapability(
            ProgramContext programContext,
            LabelAccess labelAccess,
            CapabilityConsumer<CAP> consumer
    ) {
        // Log
        programContext
                .getLogger()
                .trace(x -> x.accept(LOG_RESOURCE_TYPE_GET_CAPABILITIES_BEGIN.get(
                        displayAsCode(),
                        displayAsCapabilityClass(),
                        labelAccess.toString() // @MCVersionDependentBehaviour We must cast to string here // do I want to update the base method to perform the component/boolean/string/other check and call tostring on my own?
                )));

        for (Pair<Label, BlockPos> pair : labelAccess.getLabelledPositions(programContext.getLabelPositionHolder())) {
            Label label = pair.getFirst();
            BlockPos pos = pair.getSecond();
            forEachDirectionalCapability(
                    programContext,
                    labelAccess.sides(),
                    pos,
                    (dir, cap) -> consumer.accept(label, pos, dir, cap)
            );
        }
    }

    public void forEachDirectionalCapability(
            ProgramContext programContext,
            SideQualifier sides,
            BlockPos pos,
            BiConsumer<Direction, CAP> consumer
    ) {

        for (Direction dir : sides.resolve(programContext.getLevel().getBlockState(pos))) {
            SFMBlockCapabilityResult<CAP> maybeCap = programContext.getNetwork()
                    .getCapability(CAPABILITY_KIND, pos, dir, programContext.getLogger());
            if (maybeCap.isPresent()) {
                programContext
                        .getLogger()
                        .debug(x -> x.accept(LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_PRESENT.get(
                                displayAsCapabilityClass(),
                                pos,
                                dir
                        )));
                CAP cap = maybeCap.unwrap();
                consumer.accept(dir, cap);
            } else {
                // Log error
                programContext
                        .getLogger()
                        .error(x -> x.accept(LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_NOT_PRESENT.get(
                                displayAsCapabilityClass(),
                                pos,
                                dir
                        )));
            }
        }
    }

    public abstract Stream<Identifier> getTagsForStack(ITEM stack);

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

    public abstract ITEM stackToItem(STACK stack);

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public abstract boolean registryKeyExists(Identifier location);

    public abstract Identifier getRegistryKeyForStack(STACK stack);

    public abstract Identifier getRegistryKeyForItem(ITEM item);

    public abstract @Nullable ITEM getItemFromRegistryKey(Identifier location);

    public abstract Set<Identifier> getRegistryKeys();

    public abstract Iterable<ITEM> getItems();

    public abstract ITEM getItem(STACK stack);

    @SuppressWarnings("unused")
    public abstract STACK withCount(
            STACK stack,
            long count
    );

    public String displayAsCode() {

        Identifier thisKey = SFMResourceTypes.registry().getId(this);
        return thisKey != null ? thisKey.toString() : "null";
    }

    public String displayAsCapabilityClass() {

        return CAPABILITY_KIND.getName();
    }
}
