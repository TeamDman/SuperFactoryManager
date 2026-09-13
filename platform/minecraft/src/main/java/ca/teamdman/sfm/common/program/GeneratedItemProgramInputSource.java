package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfml.ast.ResourceIdSet;
import ca.teamdman.sfml.ast.ResourceLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One occurrence of a lazily generated packet input.
 *
 * <p>The constructor and carrier are materialized together on first value or
 * resource demand. The resulting one-slot item handler is owned by the
 * execution scope independently of this active view, so forgetting the view
 * cannot prematurely dispose a materialized value.</p>
 */
public final class GeneratedItemProgramInputSource implements ProgramInputSource {
    private final String sourceDescription;
    private final Supplier<SFMValue> valueConstructor;
    private final Function<SFMValue, ItemStack> itemMaterializer;
    private final Supplier<ResourceType<ItemStack, Item, IItemHandler>> itemResourceType;
    private final GeneratedInputResourceTracker inputTracker = new GeneratedInputResourceTracker();

    private @Nullable SFMValue value;
    private @Nullable ProgramEphemeralItemResource resource;
    private @Nullable ResourceType<ItemStack, Item, IItemHandler> materializedResourceType;
    private @Nullable ProgramEphemeralResourceOwner materializedOwner;
    private @Nullable Throwable materializationFailure;
    private @Nullable LimitedInputSlot<ItemStack, Item, IItemHandler> slot;

    public GeneratedItemProgramInputSource(
            String sourceDescription,
            Supplier<SFMValue> valueConstructor
    ) {
        this(
                sourceDescription,
                valueConstructor,
                PacketItem::create,
                () -> SFMResourceTypes.ITEM.get()
        );
    }

    GeneratedItemProgramInputSource(
            String sourceDescription,
            Supplier<SFMValue> valueConstructor,
            Function<SFMValue, ItemStack> itemMaterializer,
            Supplier<ResourceType<ItemStack, Item, IItemHandler>> itemResourceType
    ) {
        this.sourceDescription = Objects.requireNonNull(sourceDescription);
        if (sourceDescription.isBlank()) {
            throw new IllegalArgumentException("Generated input source description cannot be blank");
        }
        this.valueConstructor = Objects.requireNonNull(valueConstructor);
        this.itemMaterializer = Objects.requireNonNull(itemMaterializer);
        this.itemResourceType = Objects.requireNonNull(itemResourceType);
    }

    /** Resolve this occurrence's immutable logical value exactly once. */
    public SFMValue value(ProgramEphemeralResourceOwner owner) {
        return materialize(owner).value();
    }

    @Override
    public void gatherSlots(
            ProgramContext context,
            Consumer<LimitedInputSlot<?, ?, ?>> slotConsumer
    ) {
        Materialization materialization = materialize(context.getEphemeralResourceOwner());
        ItemStack current = materialization.resource().handler().getStackInSlot(0);
        if (current.isEmpty()) {
            return;
        }
        if (slot == null) {
            slot = LimitedInputSlotObjectPool.acquireGenerated(
                    sourceDescription,
                    0,
                    materialization.resource().handler(),
                    inputTracker,
                    current,
                    materialization.itemResourceType()
            );
        }
        slotConsumer.accept(slot);
    }

    @Override
    public @Nullable ProgramInputSource forget(
            ProgramContext context,
            ProgramInputForgetRequest request
    ) {
        if (!request.allInputs()) {
            return this;
        }
        free();
        return null;
    }

    /** Releases view-owned pooled state; materialized storage remains context-owned. */
    @Override
    public void free() {
        if (slot != null) {
            LimitedInputSlotObjectPool.release(slot);
            slot = null;
        }
    }

    public boolean isMaterialized() {
        return value != null;
    }

    @Override
    public String toString() {
        return "GeneratedItemProgramInputSource{" + sourceDescription + '}';
    }

    private Materialization materialize(ProgramEphemeralResourceOwner owner) {
        Objects.requireNonNull(owner);
        if (materializedOwner != null && materializedOwner != owner) {
            throw new IllegalStateException("A generated input occurrence cannot be shared across execution scopes");
        }
        if (materializationFailure != null) {
            rethrowMaterializationFailure();
        }
        if (value != null && resource != null && materializedResourceType != null) {
            return new Materialization(value, resource, materializedResourceType);
        }

        materializedOwner = owner;
        try {
            SFMValue createdValue = Objects.requireNonNull(
                    valueConstructor.get(),
                    "Generated value constructor returned null"
            );
            ItemStack createdStack = Objects.requireNonNull(
                    itemMaterializer.apply(createdValue),
                    "Generated item materializer returned null"
            );
            if (createdStack.isEmpty()) {
                throw new IllegalStateException("Generated item materializer returned an empty stack");
            }
            ResourceType<ItemStack, Item, IItemHandler> type = Objects.requireNonNull(itemResourceType.get());
            ProgramEphemeralItemResource createdResource = new ProgramEphemeralItemResource(createdStack);
            owner.own(createdResource);
            createdResource.onDrained(() -> owner.release(createdResource));
            value = createdValue;
            resource = createdResource;
            materializedResourceType = type;
            return new Materialization(createdValue, createdResource, type);
        } catch (RuntimeException | Error failure) {
            materializationFailure = failure;
            throw failure;
        }
    }

    private void rethrowMaterializationFailure() {
        if (materializationFailure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw (Error) materializationFailure;
    }

    private record Materialization(
            SFMValue value,
            ProgramEphemeralItemResource resource,
            ResourceType<ItemStack, Item, IItemHandler> itemResourceType
    ) {
    }

    /** A generated occurrence has no independent input filter or retention obligation. */
    private static final class GeneratedInputResourceTracker implements IInputResourceTracker {
        @Override
        public ResourceLimit getResourceLimit() {
            return ResourceLimit.TAKE_ALL_LEAVE_NONE;
        }

        @Override
        public ResourceIdSet getExclusions() {
            return ResourceIdSet.EMPTY;
        }

        @Override
        public <STACK, CAP, ITEM> boolean isDone(
                ResourceType<STACK, ITEM, CAP> type,
                STACK stack
        ) {
            return false;
        }

        @Override
        public <STACK, ITEM, CAP> long getRetentionObligationForSlot(
                ResourceType<STACK, ITEM, CAP> resourceType,
                STACK key,
                BlockPos pos,
                int slot
        ) {
            return 0;
        }

        @Override
        public <STACK, ITEM, CAP> long getRemainingRetentionObligation(
                ResourceType<STACK, ITEM, CAP> resourceType,
                STACK key
        ) {
            return 0;
        }

        @Override
        public <STACK, ITEM, CAP> void trackRetentionObligation(
                ResourceType<STACK, ITEM, CAP> resourceType,
                STACK key,
                int slot,
                BlockPos pos,
                long dedicatingToObligation
        ) {
            if (dedicatingToObligation != 0) {
                throw new IllegalStateException("Generated inputs cannot have retention obligations");
            }
        }

        @Override
        public <STACK, ITEM, CAP> long getMaxTransferable(
                ResourceType<STACK, ITEM, CAP> resourceType,
                STACK stack
        ) {
            return Long.MAX_VALUE;
        }

        @Override
        public <STACK, ITEM, CAP> void trackTransfer(
                ResourceType<STACK, ITEM, CAP> resourceType,
                STACK stack,
                long amount
        ) {
        }

        @Override
        public boolean matchesStack(Object stack) {
            return stack instanceof ItemStack itemStack && !itemStack.isEmpty();
        }

        @Override
        public boolean matchesCapabilityType(Object capability) {
            return capability instanceof IItemHandler;
        }

        @Override
        public String toString() {
            return "GeneratedInputResourceTracker{}";
        }
    }
}
