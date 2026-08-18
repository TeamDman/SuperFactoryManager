package ca.teamdman.sfm.client.screen.workspace.toast;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Workspace-owned, monotonic-clock notification state.
 *
 * <p>The queue deliberately knows nothing about Minecraft rendering or input.
 * A screen can therefore present the same exact toast id through pointer,
 * keyboard, command-palette, narration, and automation paths without making
 * wall-clock timing part of those integrations.</p>
 */
public final class SFMWorkspaceToastQueue implements AutoCloseable {
    public static final int MAX_TOASTS = 8;
    public static final int MAX_TEXT_CODE_POINTS = 512;

    public record ToastId(long value) {
        public ToastId {
            if (value <= 0) throw new IllegalArgumentException("Toast ids must be positive");
        }

        public String commandArgument() {
            return Long.toString(value);
        }
    }

    public record Presentation(
            long durationNanos,
            long fadeInNanos,
            long fadeOutNanos,
            long shakeNanos,
            boolean shake
    ) {
        public static final long DEFAULT_DURATION_NANOS = 2_200_000_000L;
        public static final long DEFAULT_FADE_IN_NANOS = 140_000_000L;
        public static final long DEFAULT_FADE_OUT_NANOS = 650_000_000L;
        public static final long DEFAULT_SHAKE_NANOS = 480_000_000L;
        public static final long ACTIONABLE_DURATION_NANOS = 8_000_000_000L;

        public Presentation {
            if (durationNanos <= 0) throw new IllegalArgumentException("Toast duration must be positive");
            if (fadeInNanos < 0 || fadeOutNanos < 0 || shakeNanos < 0) {
                throw new IllegalArgumentException("Toast presentation durations must not be negative");
            }
            if (fadeInNanos > durationNanos || fadeOutNanos > durationNanos) {
                throw new IllegalArgumentException("Toast fades must fit within its lifetime");
            }
        }

        public static Presentation workspaceStatus(boolean shake) {
            return new Presentation(
                    DEFAULT_DURATION_NANOS,
                    DEFAULT_FADE_IN_NANOS,
                    DEFAULT_FADE_OUT_NANOS,
                    DEFAULT_SHAKE_NANOS,
                    shake
            );
        }

        public static Presentation actionableStatus(boolean shake) {
            return new Presentation(
                    ACTIONABLE_DURATION_NANOS,
                    DEFAULT_FADE_IN_NANOS,
                    DEFAULT_FADE_OUT_NANOS,
                    DEFAULT_SHAKE_NANOS,
                    shake
            );
        }
    }

    public record Snapshot(
            ToastId id,
            String replacementKey,
            String text,
            long activeElapsedNanos,
            long remainingNanos,
            long durationNanos,
            double remainingFraction,
            float opacity,
            int shakeOffset,
            boolean hovered,
            boolean pinned,
            int interactionLeases
    ) {
        public Snapshot {
            Objects.requireNonNull(id, "id");
            replacementKey = Objects.requireNonNull(replacementKey, "replacementKey");
            text = Objects.requireNonNull(text, "text");
            remainingFraction = Math.max(0.0D, Math.min(1.0D, remainingFraction));
            opacity = Math.max(0.0F, Math.min(1.0F, opacity));
            if (interactionLeases < 0) throw new IllegalArgumentException("Negative interaction lease count");
        }

        public boolean timerPaused() {
            return hovered || pinned || interactionLeases > 0;
        }
    }

    public enum MutationResult {
        APPLIED,
        UNCHANGED,
        STALE,
        DISPOSED
    }

    private final LongSupplier nanoTime;
    private final LinkedHashMap<ToastId, MutableToast> toasts = new LinkedHashMap<>();
    private final Map<String, ToastId> replacementIds = new LinkedHashMap<>();
    private long nextId = 1L;
    private boolean disposed;

    public SFMWorkspaceToastQueue() {
        this(System::nanoTime);
    }

    public SFMWorkspaceToastQueue(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /**
     * Publishes a new toast. A non-empty replacement key atomically retires the
     * previous toast with that key, but the replacement always receives a new
     * id so a delayed action cannot target it accidentally.
     */
    public ToastId publish(
            @Nullable String replacementKey,
            String text,
            Presentation presentation
    ) {
        List<InteractionLease> retired = new ArrayList<>();
        ToastId id;
        synchronized (this) {
            ensureOpen();
            long now = monotonicNow();
            retired.addAll(settleAndPurge(now));
            String normalizedKey = normalizeKey(replacementKey);
            if (!normalizedKey.isEmpty()) {
                ToastId replacedId = replacementIds.get(normalizedKey);
                if (replacedId != null) retired.addAll(removeInternal(replacedId));
            }
            while (toasts.size() >= MAX_TOASTS) {
                ToastId oldest = toasts.keySet().iterator().next();
                retired.addAll(removeInternal(oldest));
            }
            id = nextToastId();
            MutableToast toast = new MutableToast(
                    id,
                    normalizedKey,
                    normalizeText(text),
                    Objects.requireNonNull(presentation, "presentation"),
                    now
            );
            toasts.put(id, toast);
            if (!normalizedKey.isEmpty()) replacementIds.put(normalizedKey, id);
        }
        retired.forEach(InteractionLease::toastRemoved);
        return id;
    }

    public void tick() {
        List<InteractionLease> retired;
        synchronized (this) {
            if (disposed) return;
            retired = settleAndPurge(monotonicNow());
        }
        retired.forEach(InteractionLease::toastRemoved);
    }

    public List<Snapshot> snapshots() {
        List<InteractionLease> retired;
        List<Snapshot> answer;
        synchronized (this) {
            if (disposed) return List.of();
            long now = monotonicNow();
            retired = settleAndPurge(now);
            answer = toasts.values().stream().map(MutableToast::snapshot).toList();
        }
        retired.forEach(InteractionLease::toastRemoved);
        return answer;
    }

    public Optional<Snapshot> snapshot(ToastId id) {
        Objects.requireNonNull(id, "id");
        List<InteractionLease> retired;
        Optional<Snapshot> answer;
        synchronized (this) {
            if (disposed) return Optional.empty();
            retired = settleAndPurge(monotonicNow());
            MutableToast toast = toasts.get(id);
            answer = toast == null ? Optional.empty() : Optional.of(toast.snapshot());
        }
        retired.forEach(InteractionLease::toastRemoved);
        return answer;
    }

    public Optional<Snapshot> latestSnapshot() {
        List<Snapshot> snapshots = snapshots();
        return snapshots.isEmpty() ? Optional.empty() : Optional.of(snapshots.get(snapshots.size() - 1));
    }

    public List<ToastId> activeIds() {
        return snapshots().stream().map(Snapshot::id).toList();
    }

    /** Exactly one toast can own pointer hover in a workspace at a time. */
    public void setHovered(@Nullable ToastId hoveredId) {
        List<InteractionLease> retired;
        synchronized (this) {
            if (disposed) return;
            retired = settleAndPurge(monotonicNow());
            for (MutableToast toast : toasts.values()) toast.hovered = toast.id.equals(hoveredId);
        }
        retired.forEach(InteractionLease::toastRemoved);
    }

    public MutationResult pin(ToastId id) {
        return setPinned(id, true);
    }

    public MutationResult resume(ToastId id) {
        return setPinned(id, false);
    }

    private MutationResult setPinned(ToastId id, boolean pinned) {
        Objects.requireNonNull(id, "id");
        List<InteractionLease> retired;
        MutationResult result;
        synchronized (this) {
            if (disposed) return MutationResult.DISPOSED;
            retired = settleAndPurge(monotonicNow());
            MutableToast toast = toasts.get(id);
            if (toast == null) result = MutationResult.STALE;
            else if (toast.pinned == pinned) result = MutationResult.UNCHANGED;
            else {
                toast.pinned = pinned;
                result = MutationResult.APPLIED;
            }
        }
        retired.forEach(InteractionLease::toastRemoved);
        return result;
    }

    public MutationResult dismiss(ToastId id) {
        Objects.requireNonNull(id, "id");
        List<InteractionLease> retired = new ArrayList<>();
        MutationResult result;
        synchronized (this) {
            if (disposed) return MutationResult.DISPOSED;
            retired.addAll(settleAndPurge(monotonicNow()));
            if (!toasts.containsKey(id)) result = MutationResult.STALE;
            else {
                retired.addAll(removeInternal(id));
                result = MutationResult.APPLIED;
            }
        }
        retired.forEach(InteractionLease::toastRemoved);
        return result;
    }

    public Optional<String> text(ToastId id) {
        return snapshot(id).map(Snapshot::text);
    }

    public Optional<InteractionLease> acquireInteractionLease(ToastId id) {
        Objects.requireNonNull(id, "id");
        List<InteractionLease> retired;
        InteractionLease lease = null;
        synchronized (this) {
            if (disposed) return Optional.empty();
            retired = settleAndPurge(monotonicNow());
            MutableToast toast = toasts.get(id);
            if (toast != null) {
                lease = new InteractionLease(this, id);
                toast.leases.add(lease);
            }
        }
        retired.forEach(InteractionLease::toastRemoved);
        return Optional.ofNullable(lease);
    }

    private void release(InteractionLease lease) {
        synchronized (this) {
            if (disposed) return;
            long now = monotonicNow();
            MutableToast toast = toasts.get(lease.id());
            if (toast == null) return;
            toast.settle(now);
            toast.leases.remove(lease);
        }
    }

    public synchronized boolean disposed() {
        return disposed;
    }

    @Override
    public void close() {
        List<InteractionLease> retired = new ArrayList<>();
        synchronized (this) {
            if (disposed) return;
            disposed = true;
            for (ToastId id : List.copyOf(toasts.keySet())) retired.addAll(removeInternal(id));
            replacementIds.clear();
        }
        retired.forEach(InteractionLease::toastRemoved);
    }

    private List<InteractionLease> settleAndPurge(long now) {
        ArrayList<ToastId> expired = new ArrayList<>();
        for (MutableToast toast : toasts.values()) {
            toast.settle(now);
            if (toast.expired()) expired.add(toast.id);
        }
        ArrayList<InteractionLease> retired = new ArrayList<>();
        expired.forEach(id -> retired.addAll(removeInternal(id)));
        return retired;
    }

    private List<InteractionLease> removeInternal(ToastId id) {
        MutableToast removed = toasts.remove(id);
        if (removed == null) return List.of();
        if (!removed.replacementKey.isEmpty()) replacementIds.remove(removed.replacementKey, id);
        List<InteractionLease> leases = List.copyOf(removed.leases);
        removed.leases.clear();
        return leases;
    }

    private long monotonicNow() {
        return nanoTime.getAsLong();
    }

    private ToastId nextToastId() {
        if (nextId <= 0 || nextId == Long.MAX_VALUE) {
            throw new IllegalStateException("Workspace toast id space exhausted");
        }
        return new ToastId(nextId++);
    }

    private void ensureOpen() {
        if (disposed) throw new IllegalStateException("Workspace toast queue is disposed");
    }

    private static String normalizeKey(@Nullable String replacementKey) {
        return replacementKey == null ? "" : replacementKey.strip();
    }

    private static String normalizeText(String text) {
        String normalized = Objects.requireNonNull(text, "text")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
        if (normalized.isEmpty()) normalized = "(empty notification)";
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= MAX_TEXT_CODE_POINTS) return normalized;
        int end = normalized.offsetByCodePoints(0, MAX_TEXT_CODE_POINTS - 1);
        return normalized.substring(0, end) + "…";
    }

    private static final class MutableToast {
        private final ToastId id;
        private final String replacementKey;
        private final String text;
        private final Presentation presentation;
        private final Set<InteractionLease> leases = new LinkedHashSet<>();
        private long activeElapsedNanos;
        private long lastSettledNanos;
        private boolean hovered;
        private boolean pinned;

        private MutableToast(
                ToastId id,
                String replacementKey,
                String text,
                Presentation presentation,
                long now
        ) {
            this.id = id;
            this.replacementKey = replacementKey;
            this.text = text;
            this.presentation = presentation;
            this.lastSettledNanos = now;
        }

        private void settle(long now) {
            if (now <= lastSettledNanos) return;
            long elapsed = now - lastSettledNanos;
            lastSettledNanos = now;
            if (timerPaused()) return;
            long maximum = presentation.durationNanos();
            if (elapsed >= maximum - activeElapsedNanos) activeElapsedNanos = maximum;
            else activeElapsedNanos += elapsed;
        }

        private boolean timerPaused() {
            return hovered || pinned || !leases.isEmpty();
        }

        private boolean expired() {
            return !timerPaused() && activeElapsedNanos >= presentation.durationNanos();
        }

        private Snapshot snapshot() {
            long remaining = Math.max(0L, presentation.durationNanos() - activeElapsedNanos);
            double fraction = (double) remaining / (double) presentation.durationNanos();
            return new Snapshot(
                    id,
                    replacementKey,
                    text,
                    activeElapsedNanos,
                    remaining,
                    presentation.durationNanos(),
                    fraction,
                    opacity(),
                    shakeOffset(),
                    hovered,
                    pinned,
                    leases.size()
            );
        }

        private float opacity() {
            if (timerPaused()) return 1.0F;
            if (presentation.fadeInNanos() > 0 && activeElapsedNanos < presentation.fadeInNanos()) {
                return (float) activeElapsedNanos / (float) presentation.fadeInNanos();
            }
            long remaining = presentation.durationNanos() - activeElapsedNanos;
            if (presentation.fadeOutNanos() > 0 && remaining < presentation.fadeOutNanos()) {
                return Math.max(0.0F, (float) remaining / (float) presentation.fadeOutNanos());
            }
            return 1.0F;
        }

        private int shakeOffset() {
            if (!presentation.shake()
                    || presentation.shakeNanos() <= 0
                    || activeElapsedNanos >= presentation.shakeNanos()) return 0;
            double elapsedMillis = activeElapsedNanos / 1_000_000.0D;
            double strength = 1.0D - (double) activeElapsedNanos / presentation.shakeNanos();
            return (int) Math.round(
                    Math.sin(elapsedMillis / 24.0D * Math.PI * 2.0D) * 3.0D * strength);
        }
    }

    /** A live constrained-choice surface pauses only its exact toast. */
    public static final class InteractionLease implements AutoCloseable {
        private enum State { OPEN, CLOSED, TOAST_REMOVED }

        private final SFMWorkspaceToastQueue owner;
        private final ToastId id;
        private State state = State.OPEN;
        private Runnable onToastRemoved = () -> { };

        private InteractionLease(SFMWorkspaceToastQueue owner, ToastId id) {
            this.owner = owner;
            this.id = id;
        }

        public ToastId id() {
            return id;
        }

        /**
         * Installs the callback that closes the owned choice surface. If the
         * toast disappeared while that surface was opening, the callback runs
         * immediately.
         */
        public void onToastRemoved(Runnable callback) {
            Objects.requireNonNull(callback, "callback");
            boolean runNow;
            synchronized (this) {
                onToastRemoved = callback;
                runNow = state == State.TOAST_REMOVED;
            }
            if (runNow) callback.run();
        }

        private void toastRemoved() {
            Runnable callback;
            synchronized (this) {
                if (state != State.OPEN) return;
                state = State.TOAST_REMOVED;
                callback = onToastRemoved;
            }
            callback.run();
        }

        public synchronized boolean active() {
            return state == State.OPEN;
        }

        @Override
        public void close() {
            synchronized (this) {
                if (state != State.OPEN) return;
                state = State.CLOSED;
            }
            owner.release(this);
        }
    }
}
