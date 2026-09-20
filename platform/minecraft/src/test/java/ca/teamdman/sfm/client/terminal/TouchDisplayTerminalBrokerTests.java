package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterFrame;
import ca.teamdman.sfm.client.raster.TouchDisplayRasterInbox;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static ca.teamdman.sfm.client.terminal.TouchDisplayTerminalBroker.*;
import static org.junit.jupiter.api.Assertions.*;

class TouchDisplayTerminalBrokerTests {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final ClientProgramIdentity IDENTITY = identity("CLIENT BTW", Set.of(
            ClientProgramConsentGate.EXECUTE, SESSION, READ, INPUT));

    @Test void creationRequiresDeclaredAndCurrentlyGrantedSessionPermissionBeforeFactoryRuns() {
        Fixture f = new Fixture();
        f.allowed.remove(SESSION);
        assertTrue(f.broker.create(IDENTITY, () -> { fail("Denied factory must not run"); return null; }).isEmpty());
        f.allowed.add(SESSION);
        var undeclared = identity("CLIENT BTW", Set.of(ClientProgramConsentGate.EXECUTE));
        assertTrue(f.broker.create(undeclared, () -> { fail("Undeclared factory must not run"); return null; }).isEmpty());
        assertTrue(f.broker.sessions().isEmpty());
    }

    @Test void destructiveFrameHandoffIsDrainedOnceAndFannedOutWithNewestRetry() {
        Fixture f = new Fixture();
        var session = f.create();
        Target first = f.mount(session, 1);
        Target second = f.mount(session, 2);
        second.result = TouchDisplayRasterInbox.OfferResult.REJECTED_INGRESS_BUDGET;
        f.transport.next = frame(1);
        f.broker.pump();
        assertEquals(1, f.transport.drains);
        assertEquals(List.of(1L), first.sequences);
        assertEquals(List.of(1L), second.sequences);
        f.transport.next = frame(3);
        second.result = TouchDisplayRasterInbox.OfferResult.ACCEPTED;
        f.broker.pump();
        f.broker.pump();
        assertEquals(3, f.transport.drains);
        assertEquals(List.of(1L, 3L), first.sequences);
        assertEquals(List.of(1L, 3L), second.sequences);
        Target later = f.mount(session, 3);
        assertEquals(List.of(3L), later.sequences, "A new viewer gets the retained newest full frame");
        assertEquals(3, f.transport.drains, "Mounting cannot destructively drain another frame");
    }

    @Test void mountRequiresExactIdentityAndUniqueWorldDisplayAddress() {
        Fixture f = new Fixture();
        var session = f.create();
        Target target = f.mount(session, 1);
        Target duplicate = new Target(target.scope);
        assertTrue(f.broker.mount(session, duplicate).isEmpty());
        assertEquals(0, duplicate.closes, "Rejected target ownership remains with caller");
        Target otherSource = new Target(new Scope(identity("CLIENT BTW -- changed", IDENTITY.requestedCapabilities()),
                target.scope.display(), 2));
        assertTrue(f.broker.mount(session, otherSource).isEmpty());
        f.allowed.remove(READ);
        assertTrue(f.broker.mount(session, new Target(scope(2))).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Scope(IDENTITY,
                new TouchDisplayRasterInbox.Display(UUID.randomUUID(), DIMENSION.toString(), 1, 64, 1), 1));
        assertThrows(IllegalArgumentException.class, () -> new Scope(IDENTITY, target.scope.display(), 0));
    }

    @Test void inputNeedsSeparatePermissionAndSingleOpaqueWriterLease() {
        Fixture f = new Fixture();
        var session = f.create();
        var first = f.broker.mount(session, new Target(scope(1))).orElseThrow();
        var second = f.broker.mount(session, new Target(scope(2))).orElseThrow();
        f.allowed.remove(INPUT);
        assertTrue(f.broker.acquireInput(first).isEmpty());
        f.allowed.add(INPUT);
        var lease = f.broker.acquireInput(first).orElseThrow();
        assertSame(lease, f.broker.acquireInput(first).orElseThrow());
        assertTrue(f.broker.acquireInput(second).isEmpty());
        assertFalse(f.broker.touch(null, .2, .8));
        assertFalse(f.broker.touch(lease, Double.NaN, .8));
        assertFalse(f.broker.touch(lease, .2, 1.01));
        f.transport.ready = false;
        assertFalse(f.broker.touch(lease, .2, .8));
        f.transport.ready = true;
        assertTrue(f.broker.touch(lease, -0.0, 1));
        assertEquals(List.of(new Touch(1, 0, 1)), f.transport.touches);
        f.transport.acceptTouch = false;
        assertFalse(f.broker.touch(lease, .2, .8));
        f.transport.acceptTouch = true;
        assertTrue(f.broker.touch(lease, .2, .8));
        assertEquals(2, f.transport.touches.get(1).id(), "Rejected input must not consume an ID");
    }

    @Test void inputRevocationClosesSessionAndOldLeaseCannotRevive() {
        Fixture f = new Fixture();
        var session = f.create();
        Target target = new Target(scope(1));
        var mount = f.broker.mount(session, target).orElseThrow();
        var lease = f.broker.acquireInput(mount).orElseThrow();
        f.allowed.remove(INPUT);
        assertFalse(f.broker.touch(lease, .5, .5));
        assertTrue(session.closed());
        assertEquals(1, target.closes);
        assertEquals(1, f.transport.closes);
        f.allowed.add(INPUT);
        assertFalse(f.broker.touch(lease, .5, .5));
        assertTrue(f.transport.touches.isEmpty());
    }

    @Test void hiddenMountAuthorityIsMaintainedWithoutFrameOrInputCalls() {
        for (int scenario = 0; scenario < 4; scenario++) {
            Fixture f = new Fixture();
            var session = f.create();
            Target target = new Target(scope(1));
            var mount = f.broker.mount(session, target).orElseThrow();
            f.broker.acquireInput(mount).orElseThrow();
            switch (scenario) {
                case 0 -> target.current = false; // unload/replacement/source change
                case 1 -> target.scope = new Scope(IDENTITY, target.scope.display(), 2);
                case 2 -> f.allowed.remove(READ);
                case 3 -> f.allowed.remove(SESSION);
            }
            f.broker.pump();
            assertTrue(session.closed());
            assertEquals(1, target.closes);
            assertEquals(1, f.transport.closes);
        }
    }

    @Test void readOnlyUnmountLeavesOtherViewerAliveButInputOwnerUnmountClosesSession() {
        Fixture f = new Fixture();
        var session = f.create();
        Target first = new Target(scope(1));
        Target second = new Target(scope(2));
        var firstMount = f.broker.mount(session, first).orElseThrow();
        var secondMount = f.broker.mount(session, second).orElseThrow();
        f.broker.unmount(firstMount);
        assertFalse(session.closed());
        assertEquals(1, first.closes);
        assertEquals(0, second.closes);
        f.broker.acquireInput(secondMount).orElseThrow();
        f.broker.unmount(secondMount);
        assertTrue(session.closed());
        assertEquals(1, second.closes);
    }

    @Test void boundedSessionsAndMountsDoNotReplaceExistingOwnership() {
        Fixture f = new Fixture();
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < MAX_SESSIONS; i++) sessions.add(f.broker.create(IDENTITY, FakeTransport::new).orElseThrow());
        assertTrue(f.broker.create(IDENTITY, () -> { fail("Session cap must precede factory"); return null; }).isEmpty());
        for (int i = 0; i < MAX_MOUNTS; i++) assertTrue(f.broker.mount(sessions.get(i % MAX_SESSIONS), new Target(scope(i))).isPresent());
        Target rejected = new Target(scope(100));
        assertTrue(f.broker.mount(sessions.get(0), rejected).isEmpty());
        assertEquals(0, rejected.closes);
        f.broker.close();
        assertTrue(f.broker.sessions().isEmpty());
        assertTrue(sessions.stream().allMatch(Session::closed));
    }

    @Test void inactiveOrInvalidFullFrameTransportReleasesEveryMount() {
        for (int scenario = 0; scenario < 2; scenario++) {
            Fixture f = new Fixture();
            var session = f.create();
            Target first = f.mount(session, 1);
            Target second = f.mount(session, 2);
            if (scenario == 0) f.transport.active = false;
            else f.transport.next = TouchDisplayRasterFrame.dirty(1, 0, 1, 1, 0, 0, 1, 1, new byte[4]);
            f.broker.pump();
            assertTrue(session.closed());
            assertEquals(1, first.closes);
            assertEquals(1, second.closes);
            assertEquals(1, f.transport.closes);
        }
    }

    @Test void cleanupFailureCannotLeakLaterMountsOrSessions() {
        Fixture f = new Fixture();
        var first = f.create();
        Target broken = f.mount(first, 1);
        broken.closeFailure = true;
        Target other = f.mount(first, 2);
        FakeTransport secondTransport = new FakeTransport();
        var second = f.broker.create(IDENTITY, () -> secondTransport).orElseThrow();
        Target last = f.mount(second, 3);
        assertThrows(IllegalStateException.class, f.broker::close);
        assertTrue(f.broker.sessions().isEmpty());
        assertEquals(1, other.closes);
        assertEquals(1, last.closes);
        assertEquals(1, f.transport.closes);
        assertEquals(1, secondTransport.closes);
        assertDoesNotThrow(() -> { f.broker.close(); });
    }

    @Test void pumpCleanupFailureCannotStarveOtherSessionsAcrossAllFailurePaths() {
        for (int path = 0; path < 3; path++) for (boolean transportFailure : new boolean[]{false, true}) {
            Fixture f = new Fixture();
            var brokenSession = f.create();
            Target broken = new Target(scope(1));
            var brokenMount = f.broker.mount(brokenSession, broken).orElseThrow();
            Target sibling = f.mount(brokenSession, 2);
            broken.closeFailure = !transportFailure;
            f.transport.closeFailure = transportFailure;
            FakeTransport healthyTransport = new FakeTransport();
            var healthySession = f.broker.create(IDENTITY, () -> healthyTransport).orElseThrow();
            Target healthy = f.mount(healthySession, 3);
            healthyTransport.next = frame(1);
            switch (path) {
                case 0 -> f.transport.active = false;
                case 1 -> {
                    f.broker.acquireInput(brokenMount).orElseThrow();
                    broken.current = false;
                }
                case 2 -> f.transport.pumpFailure = true;
            }
            assertDoesNotThrow(() -> { f.broker.pump(); });
            assertTrue(brokenSession.closed());
            assertEquals(Optional.of("terminal_cleanup_failed"), brokenSession.failure());
            assertEquals(1, broken.closes);
            assertEquals(1, sibling.closes);
            assertEquals(1, f.transport.closes);
            assertFalse(healthySession.closed());
            assertEquals(List.of(1L), healthy.sequences, "Later session must receive its frame in the same pump");
            assertEquals(List.of(healthySession), f.broker.sessions());
            f.broker.pump();
            assertEquals(1, f.transport.closes, "Closed resources must not be retried");
            f.broker.close();
        }
    }

    @Test void brokerRejectsOtherThreadAndOtherBrokerHandles() throws InterruptedException {
        Fixture f = new Fixture();
        var session = f.create();
        Fixture other = new Fixture();
        assertTrue(other.broker.mount(session, new Target(scope(1))).isEmpty());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { f.broker.pump(); } catch (Throwable t) { failure.set(t); }
        });
        thread.start(); thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
    }

    private static ClientProgramIdentity identity(String source, Set<ResourceLocation> capabilities) {
        return ClientProgramIdentity.fromStoredSource(source, ProgramExecutionSide.CLIENT,
                ClientProgramWorldIdentity.integrated(new UUID(0, 1)), DIMENSION, new BlockPos(0, 64, 0),
                ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, capabilities);
    }

    private static Scope scope(int x) {
        return new Scope(IDENTITY, new TouchDisplayRasterInbox.Display(IDENTITY.world().worldId(), DIMENSION.toString(), x, 64, 1), 1);
    }

    private static TouchDisplayRasterFrame frame(long sequence) { return TouchDisplayRasterFrame.full(sequence, 1, 1, new byte[4]); }

    private static final class Fixture {
        final Set<ResourceLocation> allowed = new HashSet<>(Set.of(SESSION, READ, INPUT));
        final TouchDisplayTerminalBroker broker = new TouchDisplayTerminalBroker((identity, capability) -> allowed.contains(capability));
        final FakeTransport transport = new FakeTransport();
        Session create() { return broker.create(IDENTITY, () -> transport).orElseThrow(); }
        Target mount(Session session, int x) {
            Target target = new Target(scope(x));
            broker.mount(session, target).orElseThrow();
            return target;
        }
    }

    private record Touch(long id, double u, double v) {}
    private static final class FakeTransport implements Transport {
        boolean active = true, ready = true, acceptTouch = true, pumpFailure, closeFailure;
        int closes, drains;
        TouchDisplayRasterFrame next;
        final List<Touch> touches = new ArrayList<>();
        public void start() {}
        public void pump() { if (pumpFailure) throw new IllegalStateException("fixture pump failure"); }
        public boolean active() { return active; }
        public boolean ready() { return ready; }
        public Optional<TouchDisplayRasterFrame> latestFrame() { drains++; var result = Optional.ofNullable(next); next = null; return result; }
        public boolean touch(long id, double u, double v) { if (!acceptTouch) return false; touches.add(new Touch(id, u, v)); return true; }
        public void close() { closes++; active = false; if (closeFailure) throw new IllegalStateException("fixture transport cleanup failure"); }
    }

    private static final class Target implements RasterTarget {
        Scope scope;
        boolean current = true, closeFailure;
        int closes;
        TouchDisplayRasterInbox.OfferResult result = TouchDisplayRasterInbox.OfferResult.ACCEPTED;
        final List<Long> sequences = new ArrayList<>();
        Target(Scope scope) { this.scope = scope; }
        public Scope scope() { return scope; }
        public boolean current() { return current; }
        public TouchDisplayRasterInbox.OfferResult offer(TouchDisplayRasterFrame frame) { sequences.add(frame.sequence()); return result; }
        public void close() { closes++; current = false; if (closeFailure) throw new IllegalStateException("fixture cleanup failure"); }
    }
}
