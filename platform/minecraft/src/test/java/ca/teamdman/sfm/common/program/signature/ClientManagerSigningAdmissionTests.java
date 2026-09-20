package ca.teamdman.sfm.common.program.signature;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientManagerSigningAdmissionTests {
    @Test
    void operationsAreBoundedPerPlayerAndAcrossManagersAndActors() {
        var budget = new ClientManagerSigningAdmission();
        var player = new UUID(1, 1);
        for (int i = 0; i < ClientManagerSigningAdmission.MAX_OPERATIONS_PER_PLAYER; i++) {
            assertTrue(budget.reserve(player, 10, 1));
        }
        assertFalse(budget.reserve(player, 10, 1));
        for (int i = ClientManagerSigningAdmission.MAX_OPERATIONS_PER_PLAYER;
             i < ClientManagerSigningAdmission.MAX_OPERATIONS_GLOBAL; i++) {
            assertTrue(budget.reserve(new UUID(2, i), 10, 1));
        }
        assertFalse(budget.reserve(new UUID(3, 0), 10, 1));
        assertFalse(budget.reserve(player, 29, 1));
        assertTrue(budget.reserve(player, 30, 1));
    }

    @Test
    void byteChargesIncludeAcknowledgementsAndRejectedReservationsDoNotAllocateOrConsume() {
        var budget = new ClientManagerSigningAdmission();
        var player = new UUID(1, 1);
        assertFalse(budget.reserve(player, 0, -1));
        assertFalse(budget.reserve(player, -1, 1));
        assertFalse(budget.reserve(player, 0, Integer.MAX_VALUE));
        assertTrue(budget.reserve(player, 0, ClientManagerSigningAdmission.MAX_BYTES_PER_PLAYER));
        assertFalse(budget.reserve(player, 0, 1));
        for (int i = 1; i < 4; i++) {
            assertTrue(budget.reserve(new UUID(2, i), 0, ClientManagerSigningAdmission.MAX_BYTES_PER_PLAYER));
        }
        assertFalse(budget.reserve(new UUID(3, 0), 0, 1));
        assertTrue(budget.reserve(player, 20, ClientManagerSigningCodec.MAX_ACKNOWLEDGEMENT_BYTES + 128));
        budget.clear();
        assertTrue(budget.reserve(player, 20, ClientManagerSigningAdmission.MAX_BYTES_PER_PLAYER));
    }
}
