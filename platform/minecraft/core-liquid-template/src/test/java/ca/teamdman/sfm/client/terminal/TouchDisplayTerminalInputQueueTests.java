package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.net.SFMClientInbox;
import ca.teamdman.sfm.client.program.*;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.value.*;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TouchDisplayTerminalInputQueueTests {
    static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    static final ResourceLocation CHANNEL = new ResourceLocation("sfm", "terminal_fixture");
    static final ClientProgramIdentity OWNER = ClientProgramIdentity.fromStoredSource("CLIENT BTW", ProgramExecutionSide.CLIENT,
            ClientProgramWorldIdentity.integrated(new UUID(0, 1)), DIMENSION, BlockPos.ZERO, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
            Set.of(ClientProgramConsentGate.EXECUTE, TouchDisplayTerminalBroker.SESSION, TouchDisplayTerminalBroker.READ,
                    TouchDisplayTerminalBroker.INPUT, ClientProgramInboxReadSurface.READ));
    static final TouchDisplayTerminalBinding BINDING = new TouchDisplayTerminalBinding(OWNER, new BlockPos(2, 64, 2), CHANNEL, true);

    @Test void declarationRoundTripsWithoutAnyProcessOrSessionFields() {
        assertEquals(BINDING, TouchDisplayTerminalBinding.parse(OWNER, BINDING.value()));
        Map<String, SFMValue> extra = new HashMap<>(((SFMValue.ObjectValue) BINDING.value()).fields());
        extra.put("executable", SFMValue.of("ignored"));
        assertThrows(IllegalArgumentException.class, () -> TouchDisplayTerminalBinding.parse(OWNER, SFMValue.object(extra)));
    }

    @Test void explicitEnableSkipsRetainedHistoryButEqualFuturePacketsRemainDistinctEvents() {
        Fixture f = new Fixture();
        f.append(touch());
        f.queue.pump(Direction.NORTH, ignored -> { fail("Retained history must not replay"); return true; });
        f.append(touch()); f.append(touch());
        AtomicInteger sent = new AtomicInteger();
        for (int i = 0; i < 3; i++) f.queue.pump(Direction.NORTH, ignored -> { sent.incrementAndGet(); return true; });
        assertEquals(2, sent.get());
        assertEquals(2, f.queue.attempted());
        assertEquals(0, f.queue.rejected());
    }

    @Test void rejectedOrBusyEffectIsConsumedOnceWithoutRetry() {
        Fixture f = new Fixture(); f.arm(); f.append(touch());
        f.queue.pump(Direction.NORTH, ignored -> false);
        for (int i = 0; i < 3; i++) f.queue.pump(Direction.NORTH, ignored -> { fail("Rejected effect must not retry"); return true; });
        assertEquals(1, f.queue.attempted()); assertEquals(1, f.queue.rejected());
        assertEquals("input_rejected", f.queue.status());
    }

    @Test void exactAddressFaceActionSchemaAndFiniteUvAreRequired() {
        Map<String, SFMValue> replacements = Map.of(
                "schema", SFMValue.of("sfm:touch@2"), "dimension", SFMValue.of("minecraft:the_nether"),
                "x", SFMValue.of(3), "y", SFMValue.of(63), "z", SFMValue.of(3), "face", SFMValue.of("south"),
                "action", SFMValue.of("release"), "u", SFMValue.of(-0.1), "v", SFMValue.of(1.1), "contentRevision", SFMValue.of(-1));
        for (var replacement : replacements.entrySet()) {
            Map<String, SFMValue> fields = new HashMap<>(touch().fields()); fields.put(replacement.getKey(), replacement.getValue());
            assertTrue(TouchDisplayTerminalInputQueue.touch(BINDING, Direction.NORTH, SFMValue.object(fields)).isEmpty(), replacement.getKey());
        }
        var touch = TouchDisplayTerminalInputQueue.touch(BINDING, Direction.NORTH, touch()).orElseThrow();
        assertEquals(.25, touch.u()); assertEquals(.75, touch.v());
        Map<String, SFMValue> wrongType = new HashMap<>(touch().fields()); wrongType.put("u", SFMValue.of("0.25"));
        assertTrue(TouchDisplayTerminalInputQueue.touch(BINDING, Direction.NORTH, SFMValue.object(wrongType)).isEmpty());
    }

    @Test void malformedEntryIsDroppedWithoutInvokingTheEffect() {
        Fixture f = new Fixture(); f.arm(); f.append(SFMValue.of("not a touch"));
        f.queue.pump(Direction.NORTH, ignored -> { fail("Invalid packet became desktop input"); return true; });
        assertEquals(0, f.queue.attempted()); assertEquals(1, f.queue.rejected());
    }

    @Test void evictionOrSessionChangeSuspendsInputWithoutReplayingRetainedPackets() {
        for (boolean changed : new boolean[]{false, true}) {
            Fixture f = new Fixture(); f.arm();
            if (changed) { f.session = UUID.randomUUID(); f.inbox.beginSession(f.session, f.recipient, DIMENSION); f.inbox.subscribe(CHANNEL); }
            for (int i = 0; i < (changed ? 1 : 65); i++) f.append(touch());
            f.queue.pump(Direction.NORTH, ignored -> { fail("Discontinuous stream replayed input"); return true; });
            assertTrue(f.queue.closed()); assertEquals("input_continuity_lost", f.queue.status());
        }
    }

    @Test void absentInboxCannotInventAnEmptySuccessfulSubscription() {
        var queue = new TouchDisplayTerminalInputQueue(BINDING, cursor -> Optional.empty());
        queue.pump(Direction.NORTH, ignored -> { fail("No inbox must mean no input"); return true; });
        assertFalse(queue.closed()); assertEquals("awaiting_inbox", queue.status()); assertEquals(0, queue.attempted());
    }

    static SFMValue.ObjectValue touch() { return SFMTouchValue.press(DIMENSION, BINDING.display(), Direction.NORTH, .25, .75, 1, SFMValue.nullValue()); }
    private static final class Fixture {
        final SFMClientInbox inbox = new SFMClientInbox();
        final UUID recipient = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        final TouchDisplayTerminalInputQueue queue;
        Fixture() { inbox.beginSession(session, recipient, DIMENSION); inbox.subscribe(CHANNEL); queue = new TouchDisplayTerminalInputQueue(BINDING, cursor -> inbox.page(CHANNEL, cursor, 1)); }
        void append(SFMValue value) { assertTrue(inbox.append(session, new SFMClientInboxAddress(recipient, DIMENSION, CHANNEL), value)); }
        void arm() { queue.pump(Direction.NORTH, ignored -> { fail("Arming cannot send"); return true; }); }
    }
}
