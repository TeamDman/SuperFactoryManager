package ca.teamdman.sfm.common.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPacketEffectGateTests {
    @Test
    void permitsOnlyPrivateIntegratedWorldOwnedByLocalPlayer() {
        assertTrue(SFMPacketEffectGate.allowsPrivateIntegratedWorld(true, false, true));

        assertFalse(SFMPacketEffectGate.allowsPrivateIntegratedWorld(false, false, true));
        assertFalse(SFMPacketEffectGate.allowsPrivateIntegratedWorld(true, true, true));
        assertFalse(SFMPacketEffectGate.allowsPrivateIntegratedWorld(true, false, false));
        assertFalse(SFMPacketEffectGate.allowsPrivateIntegratedWorld(false, true, false));
    }
}
