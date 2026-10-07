package ca.teamdman.sfm.client.program.signing;

import ca.teamdman.sfm.client.program.ClientProgramConsentGate;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.common.net.ClientboundClientManagerSigningResponsePacket;
import ca.teamdman.sfm.common.net.ServerboundClientManagerSignaturePacket;
import ca.teamdman.sfm.common.net.ServerboundClientManagerSigningRequestPacket;
import ca.teamdman.sfm.common.program.signature.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static ca.teamdman.sfm.client.program.signing.ClientProgramSigningController.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramSigningControllerTests {
    private static final ResourceLocation EXECUTE = ClientProgramConsentGate.EXECUTE;
    private static final ResourceLocation RENDER = ClientProgramConsentGate.RENDER;
    private static final ResourceLocation SEND = new ResourceLocation("sfm", "packet/send");
    private static final Set<ResourceLocation> CAPS = Set.of(EXECUTE, RENDER);
    private static final String SOURCE = "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
            + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display\nEND";
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");

    @Test void localCompilerRebuildsRealClientAstAndRequiresBoundLabels() {
        assertEquals(CAPS, compileLocally(body(SOURCE), action -> Optional.empty()));
        for (String invalid : List.of("", "CLIENT BTW", "SERVER BTW\nEVERY 20 TICKS DO END",
                "CLIENT BTW\nEVERY FRAME FOR absent AS display DO END",
                "CLIENT BTW\nEVERY FRAME FOR displays AS display DO\nLET x BE JSON \"{}\"\n"
                        + "LET y BE INVOKE sfm:unknown WITH x\nEND")) {
            assertThrows(IllegalArgumentException.class, () -> compileLocally(body(invalid), action -> Optional.empty()));
        }
    }

    @Test void reviewAckNeverSignsUntilSeparateExplicitActionAndWorkerClosesBeforeTransport() throws Exception {
        Fixture f = new Fixture();
        assertTrue(f.controller.review(f.live));
        assertEquals(State.WAITING_ACK, f.controller.view().state());
        var premature = f.operation();
        assertFalse(f.controller.sign(f.fingerprint(), premature));
        assertEquals(0, premature.opens.get());
        assertEquals(1, premature.operationCloses.get());
        assertTrue(f.controller.receive(f.response(ClientManagerSigningState.Status.REVIEWED, f.ack(f.live, CAPS))));
        assertEquals(State.READY, f.controller.view().state());
        assertEquals(List.of(f.live.body(), f.live.body()), f.compiled);
        assertTrue(f.submissions.isEmpty());

        var operation = f.operation();
        assertTrue(f.controller.sign(f.fingerprint(), operation));
        assertEquals(State.SIGNING, f.controller.view().state());
        assertEquals(0, operation.opens.get());
        f.runWorker();
        assertEquals(1, operation.opens.get());
        assertEquals(1, operation.signerCloses.get());
        assertEquals(1, operation.operationCloses.get());
        assertTrue(f.submissions.isEmpty(), "Worker cannot call the network transport or touch owner state");
        f.pump();
        assertEquals(State.WAITING_RESULT, f.controller.view().state());
        assertEquals(1, f.submissions.size());
        var submitted = f.submissions.get(0);
        var publicAttestation = ProgramAttestationCodec.decode(submitted.attestation());
        assertTrue(publicAttestation.verifies(f.ack(f.live, CAPS).descriptor()));
        assertEquals(f.live.incarnation(), submitted.incarnation());
        assertEquals(f.live.revision(), submitted.revision());
        assertTrue(f.controller.receive(new ClientboundClientManagerSigningResponsePacket(submitted.requestId(), DIMENSION,
                f.live.target().position(), ClientManagerSigningState.Status.SIGNED, Optional.empty())));
        assertEquals(State.SIGNED, f.controller.view().state());
        assertTrue(f.states.containsAll(List.of(State.WAITING_ACK, State.READY, State.SIGNING, State.WAITING_RESULT, State.SIGNED)));
    }

    @Test void unsolicitedWrongAddressAndLateRequestsAreRejectedBeforeDecode() throws Exception {
        Fixture f = new Fixture();
        var bogus = new ClientboundClientManagerSigningResponsePacket(UUID.randomUUID(), DIMENSION, f.live.target().position(),
                ClientManagerSigningState.Status.REVIEWED, Optional.of(new byte[]{1}));
        assertFalse(f.controller.receive(bogus));
        assertEquals(0, f.decodes.get());
        f.controller.review(f.live);
        UUID request = f.requests.get(0).requestId();
        assertFalse(f.controller.receive(new ClientboundClientManagerSigningResponsePacket(request, DIMENSION,
                f.live.target().position().above(), ClientManagerSigningState.Status.REVIEWED, Optional.of(new byte[]{1}))));
        assertFalse(f.controller.receive(new ClientboundClientManagerSigningResponsePacket(request,
                new ResourceLocation("minecraft", "the_nether"), f.live.target().position(),
                ClientManagerSigningState.Status.REVIEWED, Optional.of(new byte[]{1}))));
        assertEquals(0, f.decodes.get());
        f.time.addAndGet(REQUEST_TIMEOUT_MILLIS);
        assertFalse(f.controller.receive(new ClientboundClientManagerSigningResponsePacket(request, DIMENSION, f.live.target().position(),
                ClientManagerSigningState.Status.REVIEWED, Optional.of(new byte[]{1}))));
        assertEquals(0, f.decodes.get());
        assertEquals(Problem.EXPIRED, f.controller.view().problem());
    }

    @Test void reconnectToSameWorldAndRevisionRejectsAckBeforeDecode() throws Exception {
        Fixture f = new Fixture();
        f.controller.review(f.live);
        var target = f.live.target();
        f.current.set(new LiveRevision(new Target(UUID.randomUUID(), target.world(), target.dimension(), target.position()),
                f.live.incarnation(), f.live.revision(), f.live.body()));
        assertFalse(f.controller.receive(f.response(ClientManagerSigningState.Status.REVIEWED, f.ack(f.live, CAPS))));
        assertEquals(0, f.decodes.get());
        assertEquals(Problem.STALE_TARGET, f.controller.view().problem());
    }

    @Test void acknowledgementCannotChangeIncarnationRevisionSourceOrAnyLabel() throws Exception {
        for (int changed = 0; changed < 4; changed++) {
            Fixture f = new Fixture();
            f.controller.review(f.live);
            var altered = switch (changed) {
                case 0 -> new LiveRevision(f.live.target(), UUID.randomUUID(), f.live.revision(), f.live.body());
                case 1 -> new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision() + 1, f.live.body());
                case 2 -> new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision(), body(SOURCE + "\n-- altered"));
                default -> new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision(),
                        new ClientManagerSigningBody(SOURCE, Map.of("displays", List.of(7L), "unused", List.of(8L))));
            };
            assertTrue(f.controller.receive(f.response(ClientManagerSigningState.Status.REVIEWED, f.ack(altered, CAPS))));
            assertEquals(State.REJECTED, f.controller.view().state());
            assertEquals(Problem.STALE_TARGET, f.controller.view().problem());
            assertTrue(f.submissions.isEmpty());
        }
    }

    @Test void returnedOrLocallyChangedCapabilityManifestCannotEnableSigning() throws Exception {
        Fixture widenedServer = new Fixture();
        widenedServer.controller.review(widenedServer.live);
        widenedServer.controller.receive(widenedServer.response(ClientManagerSigningState.Status.REVIEWED,
                widenedServer.ack(widenedServer.live, Set.of(EXECUTE, RENDER, SEND))));
        assertEquals(Problem.MANIFEST_CHANGED, widenedServer.controller.view().problem());
        Fixture changedRegistry = new Fixture();
        changedRegistry.controller.review(changedRegistry.live);
        changedRegistry.capabilities.set(Set.of(EXECUTE, RENDER, SEND));
        changedRegistry.controller.receive(changedRegistry.response(ClientManagerSigningState.Status.REVIEWED,
                changedRegistry.ack(changedRegistry.live, CAPS)));
        assertEquals(Problem.MANIFEST_CHANGED, changedRegistry.controller.view().problem());
    }

    @Test void malformedMatchingResponseRejectsButDeniedResponseNeverDecodes() throws Exception {
        Fixture malformed = new Fixture();
        malformed.controller.review(malformed.live);
        assertTrue(malformed.controller.receive(new ClientboundClientManagerSigningResponsePacket(
                malformed.requests.get(0).requestId(), DIMENSION, malformed.live.target().position(),
                ClientManagerSigningState.Status.REVIEWED, Optional.of(new byte[]{1}))));
        assertEquals(1, malformed.decodes.get());
        assertEquals(Problem.INVALID_RESPONSE, malformed.controller.view().problem());
        Fixture denied = new Fixture();
        denied.controller.review(denied.live);
        denied.controller.receive(new ClientboundClientManagerSigningResponsePacket(denied.requests.get(0).requestId(),
                DIMENSION, denied.live.target().position(), ClientManagerSigningState.Status.UNAUTHORIZED, Optional.of(new byte[]{1})));
        assertEquals(0, denied.decodes.get());
        assertEquals(Problem.SERVER_REJECTED, denied.controller.view().problem());
    }

    @Test void saveUsesCasAndWaitsForExactAcknowledgedProjectionBeforeSign() throws Exception {
        Fixture f = new Fixture();
        String changed = SOURCE.replace("\n", "\r\n") + "\r\n-- saved";
        assertTrue(f.controller.save(f.live, changed));
        var request = f.requests.get(0);
        assertEquals(f.live.incarnation(), request.save().orElseThrow().incarnation());
        assertEquals(f.live.revision(), request.save().orElseThrow().revision());
        assertFalse(request.save().orElseThrow().source().contains("\r"));
        var next = new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision() + 1,
                new ClientManagerSigningBody(changed, f.live.body().labels()));
        f.controller.receive(f.response(ClientManagerSigningState.Status.SAVED, f.ack(next, CAPS)));
        assertEquals(State.WAITING_PROJECTION, f.controller.view().state());
        var premature = f.operation();
        assertFalse(f.controller.sign(f.fingerprint(), premature));
        assertEquals(0, premature.opens.get());
        f.current.set(next);
        f.controller.update();
        assertEquals(State.READY, f.controller.view().state());
        assertEquals(next.body(), f.controller.view().review().orElseThrow().snapshot().body());
        assertEquals(next.body().labels(), f.live.body().labels());
    }

    @Test void saveDoesNotAcceptAnUnrelatedIntermediateEditAsProjection() throws Exception {
        Fixture f = new Fixture();
        f.controller.save(f.live, SOURCE + "\n-- wanted");
        f.current.set(new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision() + 1,
                body(SOURCE + "\n-- someone else's edit")));
        f.controller.tick();
        assertEquals(State.CANCELLED, f.controller.view().state());
        assertEquals(Problem.STALE_TARGET, f.controller.view().problem());
        assertEquals(0, f.decodes.get());
    }

    @Test void sourceBindingsReplacementRemovalAndWorldChangesInvalidateReadyReview() throws Exception {
        for (int changed = 0; changed < 6; changed++) {
            Fixture f = new Fixture();
            f.ready();
            LiveRevision next = switch (changed) {
                case 0 -> new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision(), body(SOURCE + " "));
                case 1 -> new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision(),
                        new ClientManagerSigningBody(SOURCE, Map.of("displays", List.of(99L))));
                case 2 -> new LiveRevision(f.live.target(), UUID.randomUUID(), f.live.revision(), f.live.body());
                case 3 -> null;
                case 4 -> new LiveRevision(new Target(f.live.target().connectionId(),
                        ClientProgramWorldIdentity.integrated(UUID.randomUUID()), DIMENSION, f.live.target().position()),
                        f.live.incarnation(), f.live.revision(), f.live.body());
                default -> new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision() + 1, f.live.body());
            };
            f.current.set(next);
            f.controller.tick();
            assertEquals(Problem.STALE_TARGET, f.controller.view().problem());
            assertTrue(f.controller.view().review().isEmpty());
        }
    }

    @Test void localExpiryAndClockRegressionCancelReadyReviews() throws Exception {
        for (boolean backwards : List.of(false, true)) {
            Fixture f = new Fixture();
            f.ready();
            f.time.set(backwards ? 0 : 100 + REVIEW_TIMEOUT_MILLIS);
            f.controller.tick();
            assertEquals(Problem.EXPIRED, f.controller.view().problem());
            var operation = f.operation();
            assertFalse(f.controller.sign(f.fingerprint(), operation));
            assertEquals(0, operation.opens.get());
            assertEquals(1, operation.operationCloses.get());
        }
    }

    @Test void cancellingQueuedUnlockClearsItsCaptureWithoutOpeningKey() throws Exception {
        Fixture f = new Fixture();
        f.ready();
        var operation = f.operation();
        f.controller.sign(f.fingerprint(), operation);
        f.controller.cancel();
        assertFalse(f.controller.review(f.live), "An outstanding worker cannot be replaced by another key operation");
        f.runWorker();
        f.pump();
        assertEquals(0, operation.opens.get());
        assertEquals(1, operation.operationCloses.get());
        assertTrue(f.submissions.isEmpty());
        assertFalse(f.controller.view().workerInFlight());
        assertTrue(f.controller.review(f.live));
    }

    @Test void cancellationDuringUnlockClosesReturnedSignerWithoutSigningOrSubmitting() throws Exception {
        Fixture f = new Fixture();
        f.ready();
        var operation = f.operation();
        CountDownLatch unlocking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        operation.beforeUnlock = () -> {
            unlocking.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
        };
        f.controller.sign(f.fingerprint(), operation);
        Thread thread = f.startWorker();
        assertTrue(unlocking.await(5, TimeUnit.SECONDS));
        f.controller.cancel();
        release.countDown();
        thread.join(5000);
        assertFalse(thread.isAlive());
        f.pump();
        assertEquals(1, operation.opens.get());
        assertEquals(0, operation.signs.get());
        assertEquals(1, operation.signerCloses.get());
        assertEquals(1, operation.operationCloses.get());
        assertTrue(f.submissions.isEmpty());
    }

    @Test void completedWorkerCannotSubmitAfterCancellationExpiryOrSourceChange() throws Exception {
        for (int invalidation = 0; invalidation < 3; invalidation++) {
            Fixture f = new Fixture();
            f.ready();
            var operation = f.operation();
            f.controller.sign(f.fingerprint(), operation);
            f.runWorker();
            if (invalidation == 0) f.controller.cancel();
            else if (invalidation == 1) f.time.addAndGet(REVIEW_TIMEOUT_MILLIS);
            else f.current.set(new LiveRevision(f.live.target(), f.live.incarnation(), f.live.revision() + 1, f.live.body()));
            f.pump();
            assertTrue(f.submissions.isEmpty());
            assertEquals(1, operation.signerCloses.get());
            assertFalse(f.controller.view().workerInFlight());
        }
    }

    @Test void wrongFingerprintOrWrongDescriptorCannotEscapeWorker() throws Exception {
        for (boolean wrongDescriptor : List.of(false, true)) {
            Fixture f = new Fixture();
            f.ready();
            var operation = f.operation();
            operation.wrongDescriptor = wrongDescriptor;
            String fingerprint = wrongDescriptor ? f.fingerprint() : "ed25519:sha256:" + "0".repeat(64);
            f.controller.sign(fingerprint, operation);
            f.runWorker();
            f.pump();
            assertEquals(Problem.SIGNING_FAILED, f.controller.view().problem());
            assertTrue(f.submissions.isEmpty());
            assertEquals(1, operation.signerCloses.get());
            assertEquals(1, operation.operationCloses.get());
        }
    }

    @Test void inlineOrRejectedWorkerNeverUnlocksOnOwnerThread() throws Exception {
        for (boolean inline : List.of(false, true)) {
            Fixture f = new Fixture();
            f.workerOverride = inline ? Runnable::run : task -> { throw new RejectedExecutionException(); };
            f.ready();
            var operation = f.operation();
            f.controller.sign(f.fingerprint(), operation);
            f.pump();
            assertEquals(0, operation.opens.get());
            assertEquals(1, operation.operationCloses.get());
            assertEquals(Problem.WORKER_UNAVAILABLE, f.controller.view().problem());
            assertTrue(f.submissions.isEmpty());
        }
    }

    @Test void failedWakeupRetainsOnlyPublicCompletionUntilNextOwnerUpdate() throws Exception {
        Fixture f = new Fixture();
        f.rejectWakeup = true;
        f.ready();
        var operation = f.operation();
        f.controller.sign(f.fingerprint(), operation);
        f.runWorker();
        assertTrue(f.submissions.isEmpty());
        assertEquals(1, operation.signerCloses.get());
        f.controller.update();
        assertEquals(1, f.submissions.size());
    }

    @Test void duplicateRequestsAndStaleResultCannotOverrideCurrentTransaction() throws Exception {
        Fixture f = new Fixture();
        f.controller.review(f.live);
        assertFalse(f.controller.review(f.live));
        assertFalse(f.controller.save(f.live, SOURCE));
        assertEquals(1, f.requests.size());
        f.controller.receive(f.response(ClientManagerSigningState.Status.REVIEWED, f.ack(f.live, CAPS)));
        var operation = f.operation();
        f.controller.sign(f.fingerprint(), operation);
        var duplicate = f.operation();
        assertFalse(f.controller.sign(f.fingerprint(), duplicate));
        assertEquals(1, duplicate.operationCloses.get());
        f.runWorker();
        f.pump();
        var submit = f.submissions.get(0);
        assertFalse(f.controller.receive(new ClientboundClientManagerSigningResponsePacket(UUID.randomUUID(), DIMENSION,
                f.live.target().position(), ClientManagerSigningState.Status.SIGNED, Optional.empty())));
        assertEquals(State.WAITING_RESULT, f.controller.view().state());
        assertTrue(f.controller.receive(new ClientboundClientManagerSigningResponsePacket(submit.requestId(), DIMENSION,
                f.live.target().position(), ClientManagerSigningState.Status.STALE_REVISION, Optional.empty())));
        assertEquals(Problem.SERVER_REJECTED, f.controller.view().problem());
        assertEquals(1, f.decodes.get(), "Signature-result status does not parse another review payload");
    }

    private static ClientManagerSigningBody body(String source) {
        return new ClientManagerSigningBody(source, Map.of("displays", List.of(new BlockPos(1, 64, 2).asLong())));
    }

    private static final class Fixture {
        final Thread owner = Thread.currentThread();
        final KeyPair key = key();
        final AtomicLong time = new AtomicLong(100);
        final LiveRevision live = new LiveRevision(new Target(UUID.randomUUID(),
                ClientProgramWorldIdentity.integrated(UUID.randomUUID()), DIMENSION, new BlockPos(0, 64, 0)),
                UUID.randomUUID(), 5, body(SOURCE));
        final AtomicReference<LiveRevision> current = new AtomicReference<>(live);
        final AtomicReference<Set<ResourceLocation>> capabilities = new AtomicReference<>(CAPS);
        final List<ClientManagerSigningBody> compiled = new ArrayList<>();
        final List<ServerboundClientManagerSigningRequestPacket> requests = new ArrayList<>();
        final List<ServerboundClientManagerSignaturePacket> submissions = new ArrayList<>();
        final List<State> states = new ArrayList<>();
        final AtomicInteger decodes = new AtomicInteger();
        final Queue<Runnable> workers = new ArrayDeque<>();
        final Queue<Runnable> callbacks = new java.util.concurrent.ConcurrentLinkedQueue<>();
        Executor workerOverride;
        boolean rejectWakeup;
        final ClientProgramSigningController controller = new ClientProgramSigningController(
                target -> Optional.ofNullable(current.get()), requested -> { compiled.add(requested); return capabilities.get(); },
                new Transport() {
                    public void review(ServerboundClientManagerSigningRequestPacket request) {
                        assertSame(owner, Thread.currentThread()); requests.add(request);
                    }
                    public void submit(ServerboundClientManagerSignaturePacket request) {
                        assertSame(owner, Thread.currentThread()); submissions.add(request);
                    }
                }, time::get, UUID::randomUUID,
                task -> { if (workerOverride != null) workerOverride.execute(task); else workers.add(task); },
                task -> { if (rejectWakeup) throw new RejectedExecutionException(); else callbacks.add(task); },
                view -> states.add(view.state()), bytes -> {
                    decodes.incrementAndGet(); return ClientManagerSigningCodec.decodeAcknowledgement(bytes);
                });

        void ready() {
            assertTrue(controller.review(live));
            assertTrue(controller.receive(response(ClientManagerSigningState.Status.REVIEWED, ack(live, CAPS))));
            assertEquals(State.READY, controller.view().state());
        }
        ClientManagerSigningAcknowledgement ack(LiveRevision value, Set<ResourceLocation> requested) {
            var snapshot = new ClientManagerSigningSnapshot(value.incarnation(), value.revision(), value.body(), List.of());
            return new ClientManagerSigningAcknowledgement(snapshot, snapshot.descriptor(requested), new UUID(4, 4), 2400);
        }
        ClientboundClientManagerSigningResponsePacket response(ClientManagerSigningState.Status status,
                                                               ClientManagerSigningAcknowledgement ack) {
            var request = requests.get(requests.size() - 1);
            return new ClientboundClientManagerSigningResponsePacket(request.requestId(), DIMENSION, live.target().position(),
                    status, Optional.of(ClientManagerSigningCodec.encodeAcknowledgement(ack)));
        }
        String fingerprint() { return ProgramAttestation.sign(ack(live, CAPS).descriptor(), key).fingerprint(); }
        Operation operation() { return new Operation(this); }
        Thread startWorker() {
            Runnable task = Objects.requireNonNull(workers.poll());
            Thread thread = new Thread(task, "signing-controller-test");
            thread.start();
            return thread;
        }
        void runWorker() throws InterruptedException {
            Thread thread = startWorker();
            thread.join(5000);
            assertFalse(thread.isAlive(), "Signing worker did not terminate");
        }
        void pump() {
            Runnable callback;
            while ((callback = callbacks.poll()) != null) callback.run();
            controller.tick();
        }
        private static KeyPair key() {
            try { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }
    }

    private static final class Operation implements UnlockOperation {
        final Fixture fixture;
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger signs = new AtomicInteger();
        final AtomicInteger signerCloses = new AtomicInteger();
        final AtomicInteger operationCloses = new AtomicInteger();
        Runnable beforeUnlock = () -> {};
        boolean wrongDescriptor;
        Operation(Fixture fixture) { this.fixture = fixture; }
        public Signer unlock() {
            assertNotSame(fixture.owner, Thread.currentThread(), "Key unlock ran on the owner thread");
            opens.incrementAndGet();
            beforeUnlock.run();
            return new Signer() {
                public ProgramAttestation sign(ProgramSignatureDescriptor descriptor) {
                    assertNotSame(fixture.owner, Thread.currentThread(), "Private-key signing ran on the owner thread");
                    signs.incrementAndGet();
                    return ProgramAttestation.sign(wrongDescriptor ? ProgramSignatureDescriptor.fromSource(SOURCE + " ",
                            ClientManagerSigningSnapshot.RUNTIME, CAPS) : descriptor, fixture.key);
                }
                public void close() {
                    assertNotSame(fixture.owner, Thread.currentThread(), "Private signer was closed on the owner thread");
                    signerCloses.incrementAndGet();
                }
            };
        }
        public void close() { operationCloses.incrementAndGet(); }
    }
}
