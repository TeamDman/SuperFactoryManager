package ca.teamdman.sfm.client.symbol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Event-driven Ctrl-hover state machine. Callers feed hit-test, modifier, focus, document, and
 * screen-lifecycle events; render code may read {@link #snapshot()} without causing work.
 */
public final class SFMSymbolHoverStateMachine implements AutoCloseable {
    private static final int MAX_TERMINAL_CACHE_ENTRIES = 256;
    public enum Phase {
        IDLE,
        LOOKING_UP,
        ACTIONABLE,
        UNRESOLVED,
        AMBIGUOUS,
        UNAVAILABLE
    }

    public enum GestureKind {
        ACTIVATE_DEFINITION,
        FALLBACK_CLICK,
        FALLBACK_DRAG
    }

    @FunctionalInterface
    public interface DragThreshold {
        boolean isDrag(double pressX, double pressY, double releaseX, double releaseY);

        static DragThreshold euclidean(double pixels) {
            if (!Double.isFinite(pixels) || pixels < 0) {
                throw new IllegalArgumentException("pixels must be finite and non-negative");
            }
            double squared = pixels * pixels;
            return (pressX, pressY, releaseX, releaseY) -> {
                double dx = releaseX - pressX;
                double dy = releaseY - pressY;
                return dx * dx + dy * dy > squared;
            };
        }
    }

    /** Target from the editor's latest symbol hit test, before modifier intent is applied. */
    public record Target(
            SFMSymbolHoverIdentity.EditorOrigin editorOrigin,
            SFMSymbolHoverIdentity.DocumentVersion document,
            SFMSymbolHoverIdentity.TextGlyphRange range,
            SFMSymbolHoverIdentity.PointerState pointer
    ) {
        public Target {
            Objects.requireNonNull(editorOrigin, "editorOrigin");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(pointer, "pointer");
        }

        SFMSymbolHoverIdentity identity(SFMSymbolHoverIdentity.Modifiers modifiers) {
            return new SFMSymbolHoverIdentity(editorOrigin, document, range, pointer, modifiers);
        }
    }

    public record Snapshot(
            Phase phase,
            Optional<SFMSymbolHoverIdentity> identity,
            Optional<SFMSymbolHoverIdentity.TextGlyphRange> underlineRange,
            boolean ownsLinkCursor
    ) {
        public Snapshot {
            Objects.requireNonNull(phase, "phase");
            identity = Objects.requireNonNull(identity, "identity");
            underlineRange = Objects.requireNonNull(underlineRange, "underlineRange");
            if (ownsLinkCursor != underlineRange.isPresent()) {
                throw new IllegalArgumentException("Underline and link-cursor ownership must agree");
            }
            if (ownsLinkCursor && phase != Phase.ACTIONABLE) {
                throw new IllegalArgumentException("Only an actionable hover may own the link cursor");
            }
        }

        static Snapshot idle() {
            return new Snapshot(Phase.IDLE, Optional.empty(), Optional.empty(), false);
        }
    }

    public record GestureDecision(GestureKind kind, Optional<SFMSymbolHoverIdentity> identity) {
        public GestureDecision {
            Objects.requireNonNull(kind, "kind");
            identity = Objects.requireNonNull(identity, "identity");
            if ((kind == GestureKind.ACTIVATE_DEFINITION) != identity.isPresent()) {
                throw new IllegalArgumentException("Only activation carries a hover identity");
            }
        }
    }

    private record ActiveLookup(long generation, SFMSymbolHoverIdentity identity, SFMSymbolHoverLookup.Query query) {
    }

    private record Press(double x, double y, Optional<SFMSymbolHoverIdentity> actionableIdentity) {
    }

    private final SFMSymbolHoverLookup lookup;
    private final DragThreshold dragThreshold;
    private final Map<SFMSymbolHoverIdentity, SFMSymbolHoverLookup.Resolution> terminalCache =
            new LinkedHashMap<>(MAX_TERMINAL_CACHE_ENTRIES + 1, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<SFMSymbolHoverIdentity, SFMSymbolHoverLookup.Resolution> eldest) {
                    return size() > MAX_TERMINAL_CACHE_ENTRIES;
                }
            };
    private Optional<Target> target = Optional.empty();
    private SFMSymbolHoverIdentity.Modifiers modifiers = SFMSymbolHoverIdentity.Modifiers.NONE;
    private Snapshot snapshot = Snapshot.idle();
    private ActiveLookup activeLookup;
    private Press press;
    private long lookupGeneration;

    public SFMSymbolHoverStateMachine(SFMSymbolHoverLookup lookup, DragThreshold dragThreshold) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.dragThreshold = Objects.requireNonNull(dragThreshold, "dragThreshold");
    }

    /** Feed a completed hit test after pointer entry or movement. Empty means no symbol is hit. */
    public synchronized void observe(Optional<Target> newTarget) {
        Objects.requireNonNull(newTarget, "newTarget");
        if (target.equals(newTarget)) return;
        target = newTarget;
        press = null;
        refreshIdentity();
    }

    /** Modifier events are independent of movement, so pressing Ctrl over a stationary symbol works. */
    public synchronized void modifiersChanged(SFMSymbolHoverIdentity.Modifiers newModifiers) {
        Objects.requireNonNull(newModifiers, "newModifiers");
        if (modifiers.equals(newModifiers)) return;
        boolean releasedDefinitionModifier = modifiers.control() && !newModifiers.control();
        modifiers = newModifiers;
        if (releasedDefinitionModifier) {
            cancelActive();
            snapshot = Snapshot.idle();
            press = null;
        }
        refreshIdentity();
    }

    public synchronized void documentChanged() {
        clear(true);
    }

    public synchronized void focusChanged() {
        clear(true);
    }

    public synchronized void screenClosed() {
        clear(true);
    }

    public synchronized void pointerExited() {
        clear(true);
    }

    /** Captures the currently actionable identity, but leaves final click/drag policy to release. */
    public synchronized void primaryPressed(double x, double y) {
        press = new Press(x, y, snapshot.phase() == Phase.ACTIONABLE ? snapshot.identity() : Optional.empty());
    }

    public synchronized GestureDecision primaryReleased(double x, double y) {
        Press captured = press;
        press = null;
        if (captured == null) return fallback(GestureKind.FALLBACK_CLICK);
        if (dragThreshold.isDrag(captured.x(), captured.y(), x, y)) {
            return fallback(GestureKind.FALLBACK_DRAG);
        }
        if (captured.actionableIdentity().isPresent()
                && captured.actionableIdentity().equals(snapshot.identity())
                && snapshot.phase() == Phase.ACTIONABLE
                && snapshot.identity().orElseThrow().requestsDefinitionNavigation()) {
            return new GestureDecision(GestureKind.ACTIVATE_DEFINITION, captured.actionableIdentity());
        }
        return fallback(GestureKind.FALLBACK_CLICK);
    }

    public synchronized Snapshot snapshot() {
        return snapshot;
    }

    public synchronized int cachedIdentityCount() {
        return terminalCache.size();
    }

    @Override
    public synchronized void close() {
        screenClosed();
        terminalCache.clear();
    }

    private void refreshIdentity() {
        if (target.isEmpty() || !modifiers.control() || modifiers.alt()) {
            cancelActive();
            snapshot = Snapshot.idle();
            return;
        }
        SFMSymbolHoverIdentity identity = target.orElseThrow().identity(modifiers);
        if (snapshot.identity().filter(identity::equals).isPresent()) return;
        cancelActive();
        SFMSymbolHoverLookup.Resolution cached = terminalCache.get(identity);
        if (cached != null) {
            snapshot = snapshot(identity, cached);
            return;
        }
        long generation = ++lookupGeneration;
        SFMSymbolHoverLookup.Query query = Objects.requireNonNull(lookup.submit(identity), "lookup query");
        activeLookup = new ActiveLookup(generation, identity, query);
        snapshot = new Snapshot(Phase.LOOKING_UP, Optional.of(identity), Optional.empty(), false);
        query.result().whenComplete((resolution, failure) -> complete(generation, identity, resolution, failure));
    }

    private synchronized void complete(
            long generation,
            SFMSymbolHoverIdentity identity,
            SFMSymbolHoverLookup.Resolution resolution,
            Throwable failure
    ) {
        if (activeLookup == null
                || activeLookup.generation() != generation
                || !activeLookup.identity().equals(identity)) {
            return;
        }
        activeLookup = null;
        SFMSymbolHoverLookup.Resolution accepted = failure == null && resolution != null
                ? resolution
                : SFMSymbolHoverLookup.Resolution.UNAVAILABLE;
        terminalCache.put(identity, accepted);
        snapshot = snapshot(identity, accepted);
    }

    private static Snapshot snapshot(
            SFMSymbolHoverIdentity identity,
            SFMSymbolHoverLookup.Resolution resolution
    ) {
        if (resolution == SFMSymbolHoverLookup.Resolution.ACTIONABLE) {
            return new Snapshot(
                    Phase.ACTIONABLE,
                    Optional.of(identity),
                    Optional.of(identity.range()),
                    true
            );
        }
        Phase phase = switch (resolution) {
            case ACTIONABLE -> throw new IllegalStateException("handled above");
            case UNRESOLVED -> Phase.UNRESOLVED;
            case AMBIGUOUS -> Phase.AMBIGUOUS;
            case UNAVAILABLE -> Phase.UNAVAILABLE;
        };
        return new Snapshot(phase, Optional.of(identity), Optional.empty(), false);
    }

    private void clear(boolean forgetTarget) {
        cancelActive();
        snapshot = Snapshot.idle();
        press = null;
        if (forgetTarget) target = Optional.empty();
    }

    private void cancelActive() {
        if (activeLookup == null) return;
        activeLookup.query().cancel();
        activeLookup = null;
    }

    private static GestureDecision fallback(GestureKind kind) {
        return new GestureDecision(kind, Optional.empty());
    }
}
