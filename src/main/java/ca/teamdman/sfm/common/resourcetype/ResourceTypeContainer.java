package ca.teamdman.sfm.common.resourcetype;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.registries.IForgeRegistryEntry;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.blockentity.BufferBlockEntityContents;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.program.CapabilityConsumer;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.util.Pair;
import ca.teamdman.sfml.ast.*;

public abstract class ResourceTypeContainer extends IForgeRegistryEntry.Impl<ResourceTypeContainer> {

    public abstract ResourceType<?, ?, ?> get();

    public abstract static class ResourceType<STACK, ITEM, CAP> {
        public final ResourceTypeContainer container;
        public final SFMBlockCapabilityKind<CAP> CAPABILITY_KIND;

        public ResourceType(ResourceTypeContainer container, SFMBlockCapabilityKind<CAP> CAPABILITY_KIND) {
            this.container = container;
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
                boolean simulate
        );
        public boolean canExtract(CAP capability, int slot) {
            return true;
        }

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
                boolean simulate
        );

        public boolean canInsert(CAP capability, int slot) {
            return true;
        }

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
                    .trace(x -> x.accept(LocalizationKeys.LOG_RESOURCE_TYPE_GET_CAPABILITIES_BEGIN.get(
                            displayAsCode(),
                            displayAsCapabilityClass(),
                            labelAccess
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
                BiConsumer<EnumFacing, CAP> consumer
        ) {

            for (EnumFacing dir : sides.resolve(programContext.getLevel().getBlockState(pos))) {
                SFMBlockCapabilityResult<CAP> maybeCap = programContext.getNetwork()
                        .getCapability(CAPABILITY_KIND, pos, dir, programContext.getLogger());
                if (maybeCap.isPresent()) {
                    programContext
                            .getLogger()
                            .debug(x -> x.accept(LocalizationKeys.LOG_RESOURCE_TYPE_GET_CAPABILITIES_CAP_PRESENT.get(
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

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public abstract boolean registryKeyExists(ResourceLocation location);

        public abstract ResourceLocation getRegistryKeyForStack(STACK stack);

        public abstract ResourceLocation getRegistryKeyForItem(ITEM item);

        public abstract @Nullable ITEM getItemFromRegistryKey(ResourceLocation location);

        public abstract Set<ResourceLocation> getRegistryKeys();

        public abstract Iterable<ITEM> getItems();

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
            ResourceLocation thisKey = SFMResourceTypes.registry().getId(this.container);
            return thisKey != null ? thisKey.toString() : "null";
        }

        public String displayAsCapabilityClass() {

            return CAPABILITY_KIND.getName();
        }

        protected abstract STACK setCount(
                STACK stack,
                long amount
        );

        public abstract Optional<Long> getMetaForStack(STACK stack);
    }
}
