package ca.teamdman.sfm.common.program;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.Label;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;

/**
 * A pool of {@link LimitedOutputSlot} objects to avoid the garbage collector
 */
public class LimitedOutputSlotObjectPool {
    public static final IdentityHashMap<LimitedOutputSlot<?, ?, ?>, Boolean> LEASED = new IdentityHashMap<>();
    @SuppressWarnings("rawtypes")
    private static LimitedOutputSlot[] pool = new LimitedOutputSlot[27];
    private static int index = -1;

    /**
     * Acquire a {@link LimitedOutputSlot} from the pool, or creates a new one if none available
     */
    public static <STACK, ITEM, CAP> LimitedOutputSlot<STACK, ITEM, CAP> acquire(
            Label label,
            BlockPos pos,
            Direction direction,
            int slot,
            CAP handler,
            IOutputResourceTracker tracker,
            STACK stack,
            ResourceType<STACK, ITEM, CAP> type
    ) {
        if (index == -1) {
            var rtn = new LimitedOutputSlot<>(label, pos, direction, slot, handler, tracker, stack, type);
            if (LEASED.put(rtn, true) != null) {
                SFM.LOGGER.warn("new output slot was somehow already leased, this should literally never happen: {}", rtn);
            }
            return rtn;
        } else {
            @SuppressWarnings("unchecked") LimitedOutputSlot<STACK, ITEM, CAP> obj = pool[index];
            index--;
            obj.init(handler, label, pos, direction, slot, tracker, stack, type);
            if (LEASED.put(obj, true) != null) {
                SFM.LOGGER.warn("tried to lease output slot a second time: {}", obj);
            }
            return obj;
        }
    }

    /**
     * Release a {@link LimitedOutputSlot} back into the pool for it to be reused instead of garbage collected
     */
    public static void release(LimitedOutputSlot<?, ?, ?> slot) {
        if (slot.freed) {
            SFM.LOGGER.warn("Release called on already freed output slot {}", slot);
            return;
        }
        slot.freed = true;
        if (LEASED.remove(slot) == null) {
            SFM.LOGGER.warn("Freed an output slot that wasn't tracked as leased: {}", slot);
        }
        if (index == pool.length - 1) {
            // we need to grow the array
            pool = Arrays.copyOf(pool, pool.length * 2);
        }
        pool[++index] = slot;
    }

    /**
     * Release a {@link LimitedOutputSlot} back into the pool for it to be reused instead of garbage collected
     * <p>
     * After acquiring slots, the end the index after release should be {@code check + slots.size()}
     */
    @SuppressWarnings("rawtypes")
    public static void release(Collection<LimitedOutputSlot> slots) {
        // handle resizing
        if (index + slots.size() >= pool.length) {
            int slotsFree = pool.length - index - 1;
            int newLength = pool.length + slots.size() - slotsFree;
            pool = Arrays.copyOf(pool, newLength);
        }
        // add to pool
        for (LimitedOutputSlot<?, ?, ?> slot : slots) {
            if (slot.freed) {
                SFM.LOGGER.warn("Release batch called on already freed output slot {}", slot);
                continue;
            }
            slot.freed = true;
            index++;
            pool[index] = slot;
            if (LEASED.remove(slot) == null) {
                SFM.LOGGER.warn("Freed in batch an output slot that wasn't tracked as leased: {}", slot);
            }
        }
    }

    public static void checkInvariant() {
        if (!LEASED.isEmpty()) {
            SFM.LOGGER.warn("Leased objects not released: {}", LEASED);
            LEASED.clear();
        }
    }
}
