package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMTerminalTransportIdTests {
    @Test
    void wireIdsAreStableAndRoundTripExactly() {
        assertEquals("full-png", SFMTerminalTransportId.FULL_PNG.wireId());
        assertEquals("full-raw-rgba", SFMTerminalTransportId.FULL_RAW_RGBA.wireId());
        assertEquals("dirty-raw-rgba", SFMTerminalTransportId.DIRTY_RAW_RGBA.wireId());

        for (SFMTerminalTransportId transportId : SFMTerminalTransportId.values()) {
            assertEquals(transportId, SFMTerminalTransportId.fromWireId(transportId.wireId()));
        }
        assertEquals(
                SFMTerminalTransportId.values().length,
                new HashSet<>(Arrays.stream(SFMTerminalTransportId.values())
                        .map(SFMTerminalTransportId::wireId)
                        .toList()).size());
    }

    @Test
    void unknownAndNonCanonicalIdsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SFMTerminalTransportId.fromWireId("FULL-RAW-RGBA"));
        assertThrows(IllegalArgumentException.class,
                () -> SFMTerminalTransportId.fromWireId("raw-rgba"));
        assertThrows(IllegalArgumentException.class,
                () -> SFMTerminalTransportId.fromWireId(null));
    }
}
