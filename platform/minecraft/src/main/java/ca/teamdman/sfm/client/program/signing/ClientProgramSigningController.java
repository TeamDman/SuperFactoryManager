package ca.teamdman.sfm.client.program.signing;

import ca.teamdman.sfm.client.action.SFMClientProgramActionDispatcher;
import ca.teamdman.sfm.client.program.ClientFrameSourceBudget;
import ca.teamdman.sfm.client.program.ClientManagerTargetBindings;
import ca.teamdman.sfm.client.program.ClientProgramActionManifest;
import ca.teamdman.sfm.client.program.ClientProgramWorldIdentity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.net.ClientboundClientManagerSigningResponsePacket;
import ca.teamdman.sfm.common.net.ServerboundClientManagerSignaturePacket;
import ca.teamdman.sfm.common.net.ServerboundClientManagerSigningRequestPacket;
import ca.teamdman.sfm.common.program.signature.*;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * One explicit review/save/sign transaction. Ordinary methods belong to the
 * constructing thread; this pure controller does not assume a Minecraft thread.
 * The supplied worker must be bounded and off that thread. It owns and closes
 * every unlocked signer before publishing a public-only completion. No action
 * registry exposes this controller, its unlock operation, or a private key.
 */
public final class ClientProgramSigningController implements AutoCloseable {
    public static final long REQUEST_TIMEOUT_MILLIS = 30_000;
    public static final long REVIEW_TIMEOUT_MILLIS = 120_000;

    public enum State { IDLE, WAITING_ACK, WAITING_PROJECTION, READY, SIGNING, WAITING_RESULT, SIGNED, REJECTED, CANCELLED }
    public enum Problem {
        NONE, INVALID_PROGRAM, STALE_TARGET, EXPIRED, CANCELLED, INVALID_RESPONSE,
        MANIFEST_CHANGED, SERVER_REJECTED, TRANSPORT_FAILED, WORKER_UNAVAILABLE, SIGNING_FAILED
    }
    public record Target(UUID connectionId, ClientProgramWorldIdentity world, ResourceLocation dimension, BlockPos position) {
        public Target {
            Objects.requireNonNull(connectionId); Objects.requireNonNull(world); Objects.requireNonNull(dimension);
            position = Objects.requireNonNull(position).immutable();
        }
    }
    public record LiveRevision(Target target, UUID incarnation, long revision, ClientManagerSigningBody body) {
        public LiveRevision {
            Objects.requireNonNull(target); Objects.requireNonNull(incarnation); Objects.requireNonNull(body);
            if (revision < 0) throw new IllegalArgumentException("Invalid signing revision");
        }
    }
    public record View(State state, Problem problem, Optional<ClientManagerSigningAcknowledgement> review,
                       Optional<ClientManagerSigningState.Status> serverStatus, boolean workerInFlight) {}

    /** Must reflect the CURRENT connection/world and loaded block, not reconstruct the requested target's identity. */
    @FunctionalInterface public interface Environment { Optional<LiveRevision> current(Target target); }
    @FunctionalInterface public interface Compiler { Set<ResourceLocation> compile(ClientManagerSigningBody body); }
    @FunctionalInterface public interface Decoder { ClientManagerSigningAcknowledgement decode(byte[] bytes); }
    public interface Transport {
        void review(ServerboundClientManagerSigningRequestPacket request);
        void submit(ServerboundClientManagerSignaturePacket request);
    }
    /** Owns any temporary passphrase capture. close must clear that capture even when unlock is never called. */
    public interface UnlockOperation extends AutoCloseable {
        Signer unlock() throws Exception;
        @Override void close();
    }
    public interface Signer extends AutoCloseable {
        ProgramAttestation sign(ProgramSignatureDescriptor descriptor) throws Exception;
        @Override void close();
    }

    private final Thread owner = Thread.currentThread();
    private final Environment environment;
    private final Compiler compiler;
    private final Transport transport;
    private final LongSupplier clock;
    private final Supplier<UUID> requests;
    private final Executor worker;
    private final Executor completionExecutor;
    private final Consumer<View> changed;
    private final Decoder decoder;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Completion> completion = new AtomicReference<>();
    private State state = State.IDLE;
    private Problem problem = Problem.NONE;
    private ClientManagerSigningState.Status serverStatus;
    private LiveRevision base;
    private LiveRevision expected;
    private ProgramSignatureDescriptor expectedDescriptor;
    private ClientManagerSigningAcknowledgement acknowledgement;
    private UUID pendingRequest;
    private long startedAt;
    private long requestDeadline;
    private long reviewDeadline;
    private boolean save;
    private boolean workerInFlight;
    private boolean closed;

    public ClientProgramSigningController(Environment environment, Compiler compiler, Transport transport,
            LongSupplier monotonicMillis, Supplier<UUID> requests, Executor worker,
            Executor completionExecutor, Consumer<View> changed) {
        this(environment, compiler, transport, monotonicMillis, requests, worker, completionExecutor, changed,
                ClientManagerSigningCodec::decodeAcknowledgement);
    }

    // Decoder injection proves pre-decode admission without weakening production's bounded codec.
    ClientProgramSigningController(Environment environment, Compiler compiler, Transport transport,
            LongSupplier monotonicMillis, Supplier<UUID> requests, Executor worker,
            Executor completionExecutor, Consumer<View> changed, Decoder decoder) {
        this.environment = Objects.requireNonNull(environment); this.compiler = Objects.requireNonNull(compiler);
        this.transport = Objects.requireNonNull(transport); this.clock = Objects.requireNonNull(monotonicMillis);
        this.requests = Objects.requireNonNull(requests); this.worker = Objects.requireNonNull(worker);
        this.completionExecutor = Objects.requireNonNull(completionExecutor); this.changed = Objects.requireNonNull(changed);
        this.decoder = Objects.requireNonNull(decoder);
    }

    /** Runtime bridge uses this real parser/type linker for BOTH requested and acknowledged source. */
    public static Set<ResourceLocation> compileLocally(ClientManagerSigningBody body, SFMClientProgramActionDispatcher.Lookup actions) {
        if (!ClientFrameSourceBudget.permits(body.source())) throw new IllegalArgumentException("Client source exceeds its budget");
        var built = new ProgramBuilder(body.source()).useCache(false).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        if (!built.isBuildSuccessful() || built.program() == null || built.program().triggers().isEmpty()
            || built.program().triggers().size() > 32
            || built.program().triggers().stream().anyMatch(trigger -> !(trigger instanceof FrameTrigger))) {
            throw new IllegalArgumentException("Source is not an executable client frame program");
        }
        ClientManagerTargetBindings.canonical(built.program(),
                LabelPositionHolder.deserialize(body.toDiskProjection().getCompound("sfm:labels")));
        return ClientProgramActionManifest.compile(built.program(), actions).capabilities();
    }

    public View view() {
        requireOwner();
        return new View(state, problem, Optional.ofNullable(acknowledgement), Optional.ofNullable(serverStatus), workerInFlight);
    }

    public boolean review(LiveRevision current) { return begin(current, null); }
    public boolean save(LiveRevision current, String source) { return begin(current, Objects.requireNonNull(source)); }

    private boolean begin(LiveRevision current, String source) {
        requireOwner();
        tick();
        if (closed || workerInFlight || state == State.WAITING_ACK || state == State.WAITING_PROJECTION
            || state == State.WAITING_RESULT || state == State.SIGNING) return false;
        generation.incrementAndGet();
        acknowledgement = null; pendingRequest = null; serverStatus = null;
        base = Objects.requireNonNull(current);
        try {
            if (!environment.current(current.target()).filter(current::equals).isPresent()) {
                reject(Problem.STALE_TARGET); return false;
            }
            save = source != null;
            expected = save ? new LiveRevision(current.target(), current.incarnation(), Math.addExact(current.revision(), 1),
                    new ClientManagerSigningBody(source, current.body().labels())) : current;
            expectedDescriptor = ProgramSignatureDescriptor.fromSource(expected.body().source(), ClientManagerSigningSnapshot.RUNTIME,
                    compiler.compile(expected.body()));
            startedAt = clock.getAsLong();
            if (startedAt < 0 || startedAt > Long.MAX_VALUE - REVIEW_TIMEOUT_MILLIS) {
                reject(Problem.EXPIRED); return false;
            }
            reviewDeadline = startedAt + REVIEW_TIMEOUT_MILLIS;
            requestDeadline = startedAt + REQUEST_TIMEOUT_MILLIS;
            pendingRequest = Objects.requireNonNull(requests.get());
            change(State.WAITING_ACK, Problem.NONE);
            transport.review(new ServerboundClientManagerSigningRequestPacket(pendingRequest,
                    current.target().dimension(), current.target().position(), expectedDescriptor.capabilities(),
                    save ? Optional.of(new ServerboundClientManagerSigningRequestPacket.Save(current.incarnation(),
                            current.revision(), expected.body().source())) : Optional.empty()));
            return true;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            reject(Problem.INVALID_PROGRAM); return false;
        } catch (RuntimeException failed) {
            reject(Problem.TRANSPORT_FAILED); return false;
        }
    }

    /** Rejects unsolicited/stale/late/wrong-world envelopes BEFORE decoding any public signature metadata. */
    public boolean receive(ClientboundClientManagerSigningResponsePacket response) {
        requireOwner();
        Objects.requireNonNull(response);
        tick();
        if (closed || pendingRequest == null || !pendingRequest.equals(response.requestId())
            || !base.target().dimension().equals(response.dimension()) || !base.target().position().equals(response.position())
            || state != State.WAITING_ACK && state != State.WAITING_RESULT) return false;
        pendingRequest = null;
        serverStatus = response.status();
        if (state == State.WAITING_RESULT) {
            if (response.acknowledgement().isPresent()) reject(Problem.INVALID_RESPONSE);
            else if (response.status() == ClientManagerSigningState.Status.SIGNED
                     || response.status() == ClientManagerSigningState.Status.ALREADY_SIGNED) change(State.SIGNED, Problem.NONE);
            else reject(Problem.SERVER_REJECTED);
            return true;
        }
        if (response.status() != (save ? ClientManagerSigningState.Status.SAVED : ClientManagerSigningState.Status.REVIEWED)) {
            reject(Problem.SERVER_REJECTED); return true;
        }
        try {
            var received = decoder.decode(response.acknowledgement().orElseThrow());
            var snapshot = received.snapshot();
            if (!snapshot.incarnation().equals(expected.incarnation()) || snapshot.revision() != expected.revision()
                || !snapshot.body().equals(expected.body())) {
                reject(Problem.STALE_TARGET); return true;
            }
            var locallyDerived = snapshot.descriptor(compiler.compile(snapshot.body()));
            if (!locallyDerived.equals(received.descriptor()) || !locallyDerived.equals(expectedDescriptor)) {
                reject(Problem.MANIFEST_CHANGED); return true;
            }
            acknowledgement = received;
            change(State.WAITING_PROJECTION, Problem.NONE);
            tick();
        } catch (RuntimeException invalid) { reject(Problem.INVALID_RESPONSE); }
        return true;
    }

    /** Ownership of unlock is consumed even on rejection. UI calls this only after separate explicit Sign confirmation. */
    public boolean sign(String expectedFingerprint, UnlockOperation unlock) {
        requireOwner();
        Objects.requireNonNull(unlock);
        tick();
        if (closed || workerInFlight || state != State.READY || expectedFingerprint == null
            || !expectedFingerprint.matches("ed25519:sha256:[0-9a-f]{64}")) {
            unlock.close(); return false;
        }
        long token = generation.get();
        var descriptor = acknowledgement.descriptor();
        workerInFlight = true;
        change(State.SIGNING, Problem.NONE);
        try {
            worker.execute(() -> doSign(token, expectedFingerprint, descriptor, unlock));
            return true;
        } catch (RuntimeException unavailable) {
            // No worker accepted ownership and no private signer exists yet.
            unlock.close(); workerInFlight = false; reject(Problem.WORKER_UNAVAILABLE); return false;
        }
    }

    private void doSign(long token, String fingerprint, ProgramSignatureDescriptor descriptor, UnlockOperation operation) {
        ProgramAttestation signed = null;
        Problem failed = Problem.NONE;
        try (operation) {
            if (Thread.currentThread() == owner) failed = Problem.WORKER_UNAVAILABLE;
            else if (generation.get() == token) {
                try (Signer signer = Objects.requireNonNull(operation.unlock())) {
                    if (generation.get() == token) {
                        signed = signer.sign(descriptor);
                        if (signed == null || !signed.fingerprint().equals(fingerprint) || !signed.verifies(descriptor)) {
                            signed = null; failed = Problem.SIGNING_FAILED;
                        }
                    }
                }
            }
        } catch (Exception failure) { signed = null; failed = Problem.SIGNING_FAILED; }
        completion.set(new Completion(token, signed, failed));
        try {
            completionExecutor.execute(() -> { if (Thread.currentThread() == owner) tick(); });
        } catch (RuntimeException ignored) {
            // Public-only completion remains queued for the next owner tick; signer is already closed.
        }
    }

    /** UI-facing alias; both names run the same owner-thread freshness/cleanup pass. */
    public void update() { tick(); }

    /** Call every owner tick and after any block projection change; it never opens a UI. */
    public void tick() {
        requireOwner();
        refresh();
        Completion done = completion.getAndSet(null);
        if (done == null) return;
        workerInFlight = false;
        if (done.token() != generation.get() || state != State.SIGNING) { emit(); return; }
        if (done.problem() != Problem.NONE || done.attestation() == null) {
            reject(done.problem() == Problem.NONE ? Problem.SIGNING_FAILED : done.problem()); return;
        }
        try {
            pendingRequest = Objects.requireNonNull(requests.get());
            long now = clock.getAsLong();
            requestDeadline = now + Math.min(REQUEST_TIMEOUT_MILLIS, reviewDeadline - now);
            var reviewed = acknowledgement;
            change(State.WAITING_RESULT, Problem.NONE);
            transport.submit(new ServerboundClientManagerSignaturePacket(pendingRequest, expected.target().dimension(),
                    expected.target().position(), expected.incarnation(), expected.revision(), reviewed.challenge(),
                    ProgramAttestationCodec.encode(done.attestation())));
        } catch (RuntimeException failed) { reject(Problem.TRANSPORT_FAILED); }
    }

    private void refresh() {
        if (closed || !active()) return;
        long now = clock.getAsLong();
        if (now < startedAt || now >= reviewDeadline
            || (state == State.WAITING_ACK || state == State.WAITING_RESULT) && now >= requestDeadline) {
            cancel(Problem.EXPIRED); return;
        }
        try {
            var current = environment.current(base.target());
            boolean exact = current.filter(expected::equals).isPresent();
            boolean waitingForSave = save && (state == State.WAITING_ACK || state == State.WAITING_PROJECTION)
                    && current.filter(base::equals).isPresent();
            if (!exact && !waitingForSave) { cancel(Problem.STALE_TARGET); return; }
            if (state == State.WAITING_PROJECTION && exact) change(State.READY, Problem.NONE);
        } catch (RuntimeException unavailable) { cancel(Problem.STALE_TARGET); }
    }

    /** Editor edits, screen cancellation and explicit navigation away invalidate the current review. */
    public void cancel() { requireOwner(); cancel(Problem.CANCELLED); }
    private void cancel(Problem reason) {
        generation.incrementAndGet(); pendingRequest = null; acknowledgement = null;
        change(State.CANCELLED, reason);
    }
    private void reject(Problem reason) {
        generation.incrementAndGet(); pendingRequest = null; acknowledgement = null;
        change(State.REJECTED, reason);
    }
    @Override public void close() { requireOwner(); cancel(); closed = true; }
    private boolean active() {
        return state == State.WAITING_ACK || state == State.WAITING_PROJECTION || state == State.READY
               || state == State.SIGNING || state == State.WAITING_RESULT;
    }
    private void change(State next, Problem reason) { state = next; problem = reason; emit(); }
    private void emit() {
        try { changed.accept(view()); } catch (RuntimeException ignored) { /* Observer failure cannot grant signing authority. */ }
    }
    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Signing controller requires its owner thread");
    }
    private record Completion(long token, ProgramAttestation attestation, Problem problem) {}
}
