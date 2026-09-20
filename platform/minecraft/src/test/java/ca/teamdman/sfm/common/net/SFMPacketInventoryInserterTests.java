package ca.teamdman.sfm.common.net;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SFMPacketInventoryInserterTests {
    @Test
    void requestedSideIsLookedUpExactlyOnceWithoutFallback() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Direction> observed = new AtomicReference<>();

        String found = SFMPacketInventoryInserter.lookupRequestedSide(
                Optional.of(Direction.WEST),
                side -> {
                    calls.incrementAndGet();
                    observed.set(side);
                    return null;
                }
        );

        assertNull(found);
        assertEquals(1, calls.get());
        assertEquals(Direction.WEST, observed.get());
    }

    @Test
    void unsidedAddressPerformsOneNullSideLookup() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Direction> observed = new AtomicReference<>(Direction.UP);

        String found = SFMPacketInventoryInserter.lookupRequestedSide(
                Optional.empty(),
                side -> {
                    calls.incrementAndGet();
                    observed.set(side);
                    return "unsided";
                }
        );

        assertEquals("unsided", found);
        assertEquals(1, calls.get());
        assertNull(observed.get());
    }
}
