package ca.teamdman.sfm.common.net;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static ca.teamdman.sfm.common.net.SFMBoundedEffectBudget.Result.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMBoundedEffectBudgetTests {
    @Test
    void operationAndByteLimitsAreIndependentAndRejectedRequestsDoNotCharge() {
        UUID sender = UUID.randomUUID();
        SFMBoundedEffectBudget budget = new SFMBoundedEffectBudget(3, 10);
        assertEquals(REQUEST_TOO_LARGE, budget.reserve(sender, 1, 11));
        assertEquals(REQUEST_TOO_LARGE, budget.reserve(sender, 1, -1));
        assertEquals(ALLOWED, budget.reserve(sender, 1, 6));
        assertEquals(BYTES_EXHAUSTED, budget.reserve(sender, 1, 5));
        assertEquals(ALLOWED, budget.reserve(sender, 1, 4));
        assertEquals(ALLOWED, budget.reserve(sender, 1, 0));
        assertEquals(OPERATIONS_EXHAUSTED, budget.reserve(sender, 1, 0));
    }

    @Test
    void sendersAreIsolatedAndOnlyForwardWindowChangesReplenish() {
        UUID sender = UUID.randomUUID();
        SFMBoundedEffectBudget budget = new SFMBoundedEffectBudget(1, 10);
        assertEquals(ALLOWED, budget.reserve(sender, 5, 10));
        assertEquals(ALLOWED, budget.reserve(UUID.randomUUID(), 5, 10));
        assertEquals(OPERATIONS_EXHAUSTED, budget.reserve(sender, 4, 1));
        assertEquals(OPERATIONS_EXHAUSTED, budget.reserve(sender, 5, 1));
        assertEquals(ALLOWED, budget.reserve(sender, 6, 10));
        budget.remove(sender);
        assertEquals(ALLOWED, budget.reserve(sender, 6, 10));
        budget.clear();
        assertEquals(ALLOWED, budget.reserve(sender, 6, 10));
    }

    @Test
    void identityChurnCannotEvictAnActiveSendersExhaustedQuota() {
        SFMBoundedEffectBudget budget = new SFMBoundedEffectBudget(1, 10);
        UUID first = new UUID(0, 0);
        for (int i = 0; i < SFMBoundedEffectBudget.MAX_TRACKED_PRINCIPALS; i++) {
            assertEquals(ALLOWED, budget.reserve(new UUID(0, i), 1, 1));
        }
        assertEquals(PRINCIPAL_CAPACITY_EXHAUSTED, budget.reserve(UUID.randomUUID(), 1, 1));
        assertEquals(OPERATIONS_EXHAUSTED, budget.reserve(first, 1, 1));
        assertEquals(ALLOWED, budget.reserve(UUID.randomUUID(), 2, 1), "expired windows may be reclaimed");
        assertEquals(ALLOWED, budget.reserve(first, 2, 1));
    }
}
