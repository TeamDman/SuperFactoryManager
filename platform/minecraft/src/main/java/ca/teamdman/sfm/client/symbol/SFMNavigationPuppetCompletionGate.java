package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * One-shot completion gate for deterministic natural-input game puppets.
 *
 * <p>The production controller receives the direct pass-through hook unless
 * the JVM is both an IDE/development launch and explicitly in GAME_PUPPET run
 * mode. Merely loading this class cannot delay a normal client request. An
 * armed gate also has a mandatory bounded timeout which releases held work.</p>
 */
public final class SFMNavigationPuppetCompletionGate implements SFMNavigationCompletionGate {
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofMinutes(1);
    private static final AtomicLong NEXT_GATE_ID = new AtomicLong();
    private static final SFMNavigationPuppetCompletionGate SHARED = new SFMNavigationPuppetCompletionGate(
            SFMNavigationPuppetCompletionGate::sharedArmingAllowed,
            System::nanoTime,
            (timeout, action) -> CompletableFuture.delayedExecutor(
                    timeout.toNanos(),
                    TimeUnit.NANOSECONDS
            ).execute(action)
    );

    @FunctionalInterface
    interface TimeoutScheduler {
        void schedule(Duration timeout, Runnable action);
    }

    /** Source-free evidence exposed to the game-puppet action. */
    public record Observation(
            long gateId,
            long armedNanos,
            boolean accepted,
            long acceptedNanos,
            boolean completionHeld,
            long completionHeldNanos,
            boolean released,
            long releasedNanos,
            boolean timedOut,
            long requestGeneration,
            long sourcePanelId,
            String originId,
            String editorId,
            Optional<String> documentAddress,
            Optional<String> authorizedRoot,
            String documentSha256,
            long providerGeneration,
            Optional<String> rejectionCode,
            Optional<String> rejectionDescription,
            long rejectionNanos
    ) {
        public Observation {
            Objects.requireNonNull(originId, "originId");
            Objects.requireNonNull(editorId, "editorId");
            documentAddress = Objects.requireNonNull(documentAddress, "documentAddress");
            authorizedRoot = Objects.requireNonNull(authorizedRoot, "authorizedRoot");
            Objects.requireNonNull(documentSha256, "documentSha256");
            rejectionCode = Objects.requireNonNull(rejectionCode, "rejectionCode");
            rejectionDescription = Objects.requireNonNull(rejectionDescription, "rejectionDescription");
        }
    }

    public static final class Lease implements AutoCloseable {
        private final SFMNavigationPuppetCompletionGate owner;
        private final Slot slot;

        private Lease(SFMNavigationPuppetCompletionGate owner, Slot slot) {
            this.owner = owner;
            this.slot = slot;
        }

        public Observation observation() {
            return owner.observe(slot);
        }

        /** Releases the exact held completion; idempotent for cleanup. */
        public void release() {
            owner.release(slot, false);
        }

        @Override
        public void close() {
            owner.close(slot);
        }
    }

    private static final class Slot {
        private final long gateId;
        private final long armedNanos;
        private SFMNavigationRequestWitness witness;
        private long acceptedNanos;
        private Runnable heldCompletion;
        private long completionHeldNanos;
        private boolean releaseRequested;
        private long releasedNanos;
        private boolean timedOut;
        private SFMNavigationRequestWitness.RejectionReason rejection;
        private long rejectionNanos;
        private boolean closed;

        private Slot(long gateId, long armedNanos) {
            this.gateId = gateId;
            this.armedNanos = armedNanos;
        }
    }

    private final Object lock = new Object();
    private final BooleanSupplier armingAllowed;
    private final LongSupplier nanoTime;
    private final TimeoutScheduler timeoutScheduler;
    private Slot active;

    private SFMNavigationPuppetCompletionGate(
            BooleanSupplier armingAllowed,
            LongSupplier nanoTime,
            TimeoutScheduler timeoutScheduler
    ) {
        this.armingAllowed = Objects.requireNonNull(armingAllowed, "armingAllowed");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler");
    }

    /** Returns a pass-through hook for every non-puppet production controller. */
    static SFMNavigationCompletionGate productionHook() {
        return sharedArmingAllowed() ? SHARED : SFMNavigationCompletionGate.DIRECT;
    }

    /** Arms the next natural jump request in the running game puppet. */
    public static Lease armNextJump(Duration timeout) {
        return SHARED.arm(timeout);
    }

    static SFMNavigationPuppetCompletionGate forTests(
            BooleanSupplier armingAllowed,
            LongSupplier nanoTime,
            TimeoutScheduler timeoutScheduler
    ) {
        return new SFMNavigationPuppetCompletionGate(armingAllowed, nanoTime, timeoutScheduler);
    }

    Lease arm(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (!armingAllowed.getAsBoolean()) {
            throw new IllegalStateException(
                    "The navigation completion gate is available only in an IDE GAME_PUPPET launch"
            );
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAXIMUM_TIMEOUT) > 0) {
            throw new IllegalArgumentException("Gate timeout must be positive and at most " + MAXIMUM_TIMEOUT);
        }
        Slot slot;
        synchronized (lock) {
            if (active != null) throw new IllegalStateException("A navigation completion gate is already armed");
            slot = new Slot(NEXT_GATE_ID.incrementAndGet(), nanoTime.getAsLong());
            active = slot;
        }
        try {
            timeoutScheduler.schedule(timeout, () -> timeout(slot));
        } catch (RuntimeException failure) {
            synchronized (lock) {
                if (active == slot) active = null;
            }
            throw failure;
        }
        SFM.LOGGER.info("SFM_NAVIGATION_PUPPET_GATE_ARMED gate={} timeout_ms={}",
                slot.gateId, timeout.toMillis());
        return new Lease(this, slot);
    }

    @Override
    public void accepted(SFMNavigationRequestWitness witness) {
        Objects.requireNonNull(witness, "witness");
        Slot slot;
        synchronized (lock) {
            slot = active;
            if (slot == null || slot.closed || slot.witness != null) return;
            slot.witness = witness;
            slot.acceptedNanos = nanoTime.getAsLong();
        }
        SFM.LOGGER.info(
                "SFM_NAVIGATION_PUPPET_GATE_ACCEPTED gate={} request={} panel={} document_hash={}",
                slot.gateId,
                witness.requestGeneration(),
                witness.sourcePanelId().value(),
                witness.document().contentSha256()
        );
    }

    @Override
    public void dispatchCompletion(SFMNavigationRequestWitness witness, Runnable completion) {
        Objects.requireNonNull(witness, "witness");
        Objects.requireNonNull(completion, "completion");
        Slot slot;
        boolean dispatch;
        boolean gated;
        long gateId;
        synchronized (lock) {
            slot = active;
            if (slot == null || slot.closed || slot.witness != witness) {
                dispatch = true;
                gated = false;
                gateId = 0L;
            } else if (slot.rejection != null) {
                return;
            } else {
                if (slot.heldCompletion != null) {
                    throw new IllegalStateException("The gated request published more than one completion");
                }
                slot.completionHeldNanos = nanoTime.getAsLong();
                dispatch = slot.releaseRequested;
                if (!dispatch) slot.heldCompletion = completion;
                gated = true;
                gateId = slot.gateId;
            }
        }
        if (gated) {
            SFM.LOGGER.info(
                    "SFM_NAVIGATION_PUPPET_GATE_COMPLETION_HELD gate={} request={} release_requested={}",
                    gateId,
                    witness.requestGeneration(),
                    dispatch
            );
        }
        if (dispatch) completion.run();
    }

    @Override
    public void rejected(
            SFMNavigationRequestWitness witness,
            SFMNavigationRequestWitness.RejectionReason reason
    ) {
        Objects.requireNonNull(witness, "witness");
        Objects.requireNonNull(reason, "reason");
        Slot slot;
        synchronized (lock) {
            slot = active;
            if (slot == null || slot.closed || slot.witness != witness) return;
            if (slot.rejection == null) {
                slot.rejection = reason;
                slot.rejectionNanos = nanoTime.getAsLong();
            }
            // A superseded request may be rejected while its completion is held.
            slot.heldCompletion = null;
        }
        SFM.LOGGER.info(
                "SFM_NAVIGATION_PUPPET_GATE_REJECTED gate={} request={} reason={} description={}",
                slot.gateId,
                witness.requestGeneration(),
                reason.name(),
                reason.description()
        );
    }

    private Observation observe(Slot slot) {
        synchronized (lock) {
            SFMNavigationRequestWitness witness = slot.witness;
            return new Observation(
                    slot.gateId,
                    slot.armedNanos,
                    witness != null,
                    slot.acceptedNanos,
                    slot.completionHeldNanos != 0L,
                    slot.completionHeldNanos,
                    slot.releaseRequested,
                    slot.releasedNanos,
                    slot.timedOut,
                    witness == null ? 0L : witness.requestGeneration(),
                    witness == null ? -1L : witness.sourcePanelId().value(),
                    witness == null ? "" : witness.originId().canonical(),
                    witness == null ? "" : witness.document().editorId(),
                    witness == null ? Optional.empty() : witness.document().address(),
                    witness == null ? Optional.empty() : witness.document().authorizedRoot(),
                    witness == null ? "" : witness.document().contentSha256(),
                    witness == null ? -1L : witness.semanticRequest().providerGeneration(),
                    Optional.ofNullable(slot.rejection).map(Enum::name),
                    Optional.ofNullable(slot.rejection).map(
                            SFMNavigationRequestWitness.RejectionReason::description),
                    slot.rejectionNanos
            );
        }
    }

    private void release(Slot slot, boolean timedOut) {
        Runnable completion;
        boolean firstRelease;
        synchronized (lock) {
            if (slot.closed) return;
            if (timedOut && (slot.releaseRequested || slot.rejection != null)) return;
            firstRelease = !slot.releaseRequested;
            if (firstRelease) {
                slot.releaseRequested = true;
                slot.releasedNanos = nanoTime.getAsLong();
            }
            slot.timedOut |= timedOut;
            completion = slot.heldCompletion;
            slot.heldCompletion = null;
            if (timedOut && active == slot) active = null;
        }
        if (firstRelease) {
            SFM.LOGGER.info(
                    "SFM_NAVIGATION_PUPPET_GATE_RELEASED gate={} timed_out={} completion_ready={}",
                    slot.gateId,
                    timedOut,
                    completion != null
            );
        }
        if (completion != null) completion.run();
    }

    private void timeout(Slot slot) {
        Observation before = observe(slot);
        if (before.released() || before.rejectionCode().isPresent()) return;
        SFM.LOGGER.warn("SFM_NAVIGATION_PUPPET_GATE_TIMEOUT gate={} accepted={} completion_held={}",
                slot.gateId, before.accepted(), before.completionHeld());
        release(slot, true);
    }

    private void close(Slot slot) {
        release(slot, false);
        synchronized (lock) {
            slot.closed = true;
            if (active == slot) active = null;
        }
    }

    private static boolean sharedArmingAllowed() {
        return puppetHookAllowed(SFMProperties.clientRunMode(), !FMLEnvironment.production);
    }

    static boolean puppetHookAllowed(SFMProperties.ClientRunMode runMode, boolean inIde) {
        return Objects.requireNonNull(runMode, "runMode") == SFMProperties.ClientRunMode.GAME_PUPPET
                && inIde;
    }
}
