package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Read-only traversal of resources selected by the active program inputs. */
public final class ProgramResourceObserver {
    private ProgramResourceObserver() {
    }

    /**
     * Snapshot resources that match {@code selector}, respecting current input
     * quantity and retention state without extracting or mutating live tracker
     * bookkeeping.
     *
     * <p>Equal values in different physical units remain separate. Duplicate
     * handles for the same resource type, handler identity, and slot contribute
     * at most that physical slot's largest eligible quantity.</p>
     */
    public static List<ProgramResourceObservation> observe(
            ProgramContext context,
            BiPredicate<ResourceType<?, ?, ?>, Object> selector
    ) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(selector);

        List<LimitedInputSlot<?, ?, ?>> slots = new ArrayList<>();
        for (ProgramInputSource inputSource : context.getInputs()) {
            inputSource.gatherSlots(context, slots::add);
        }

        IdentityHashMap<IInputResourceTracker, IInputResourceTracker> observationTrackers = new IdentityHashMap<>();
        Map<PhysicalResourceKey, ProgramResourceObservation> observations = new LinkedHashMap<>();
        for (LimitedInputSlot<?, ?, ?> slot : slots) {
            observeSlot(slot, selector, observationTrackers, observations);
        }
        return List.copyOf(observations.values());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void observeSlot(
            LimitedInputSlot slot,
            BiPredicate<ResourceType<?, ?, ?>, Object> selector,
            IdentityHashMap<IInputResourceTracker, IInputResourceTracker> observationTrackers,
            Map<PhysicalResourceKey, ProgramResourceObservation> observations
    ) {
        ResourceType type = slot.type;
        Object stack = slot.peekStackInSlot();
        if (type.isEmpty(stack) || !selector.test(type, type.copy(stack))) {
            return;
        }

        IInputResourceTracker tracker = observationTrackers.computeIfAbsent(
                slot.tracker,
                IInputResourceTracker::forkForObservation
        );
        if (!tracker.matchesStack(stack) || tracker.isDone(type, stack)) {
            return;
        }

        long amount = type.getAmount(stack);
        long promisedForSlot = Math.max(0, tracker.getRetentionObligationForSlot(
                type,
                stack,
                slot.pos,
                slot.slot
        ));
        amount = Math.max(0, amount - promisedForSlot);

        long remainingRetention = Math.max(0, tracker.getRemainingRetentionObligation(type, stack));
        long dedicatingToRetention = Math.min(remainingRetention, amount);
        if (dedicatingToRetention > 0) {
            tracker.trackRetentionObligation(type, stack, slot.slot, slot.pos, dedicatingToRetention);
            amount -= dedicatingToRetention;
        }

        amount = Math.min(amount, tracker.getMaxTransferable(type, stack));
        if (amount <= 0) {
            return;
        }
        tracker.trackTransfer(type, stack, amount);

        PhysicalResourceKey physicalKey = new PhysicalResourceKey(type, slot.handler, slot.slot);
        ProgramResourceObservation previous = observations.get(physicalKey);
        if (previous == null || amount > previous.amount()) {
            observations.put(physicalKey, new ProgramResourceObservation(
                    type,
                    type.withCount(stack, amount),
                    amount
            ));
        }
    }

    private static final class PhysicalResourceKey {
        private final ResourceType<?, ?, ?> resourceType;
        private final Object handler;
        private final int slot;

        private PhysicalResourceKey(
                ResourceType<?, ?, ?> resourceType,
                Object handler,
                int slot
        ) {
            this.resourceType = resourceType;
            this.handler = handler;
            this.slot = slot;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PhysicalResourceKey that
                   && resourceType.equals(that.resourceType)
                   && handler == that.handler
                   && slot == that.slot;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * resourceType.hashCode() + System.identityHashCode(handler)) + slot;
        }
    }
}
