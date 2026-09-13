package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.net.SFMPacketValueDispatch;
import ca.teamdman.sfm.common.net.SFMPacketValueEnvelope;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPacketObservationRuntimeTests {
    @Test
    void rotatesOnlyWhenIntegratedServerIdentityChanges() {
        SFMPacketObservationLog log = new SFMPacketObservationLog();
        SFMPacketObservationRuntime runtime = new SFMPacketObservationRuntime(log);
        Object firstWorld = new Object();
        Object secondWorld = new Object();

        assertTrue(runtime.page(Optional.empty()).isEmpty());
        runtime.observeSessionIdentity(firstWorld);
        SFMPacketObservationLog.SessionId firstSession = runtime.currentSessionId().orElseThrow();
        runtime.append(firstWorld, SFMValue.of("first"));

        runtime.observeSessionIdentity(firstWorld);
        assertEquals(firstSession, runtime.currentSessionId().orElseThrow());
        assertEquals(1, runtime.page(Optional.empty()).orElseThrow().retainedEntryCount());

        runtime.observeSessionIdentity(secondWorld);
        SFMPacketObservationLog.SessionId secondSession = runtime.currentSessionId().orElseThrow();
        assertNotEquals(firstSession, secondSession);
        assertTrue(runtime.page(Optional.empty()).orElseThrow().entries().isEmpty());

        runtime.append(secondWorld, SFMValue.of("second"));
        assertEquals(1, runtime.page(Optional.empty()).orElseThrow().newestSequence());

        runtime.observeSessionIdentity(null);
        assertTrue(runtime.currentSessionId().isEmpty());
        assertTrue(runtime.page(Optional.empty()).isEmpty());
    }

    @Test
    void disabledEffectGateCannotAppendToTheActiveSession() {
        SFMPacketObservationRuntime runtime = new SFMPacketObservationRuntime(
                new SFMPacketObservationLog()
        );
        Object world = new Object();
        runtime.observeSessionIdentity(world);

        SFMPacketValueDispatch.Result result = SFMPacketValueDispatch.dispatch(
                false,
                SFMPacketValueEnvelope.fromValue(SFMValue.of("blocked")),
                value -> runtime.append(world, value)
        );

        assertEquals(SFMPacketValueDispatch.Result.EFFECTS_DISABLED, result);
        assertTrue(runtime.page(Optional.empty()).orElseThrow().entries().isEmpty());
    }
}
