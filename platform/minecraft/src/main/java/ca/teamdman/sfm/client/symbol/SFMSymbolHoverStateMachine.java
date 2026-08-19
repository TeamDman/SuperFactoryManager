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

    public enum CancellationCause {
        NONE,
        TARGET_CHANGED,
        MODIFIERS_CHANGED,
        DOCUMENT_CHANGED,
        FOCUS_CHANGED,
        SCREEN_CLOSED,
        POINTER_EXITED
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
            SFMSymbolHoverIdentity.SemanticContext semanticContext
    ) {
        public Target {
            Objects.requireNonNull(editorOrigin, "editorOrigin");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(semanticContext, "semanticContext");
        }

        SFMSymbolHoverIdentity identity(SFMSymbolHoverIdentity.Modifiers modifiers) {
            return new SFMSymbolHoverIdentity(editorOrigin, document, range, semanticContext, modifiers);
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
            if (ownsLinkCursor && phase != Phase.LOOKING_UP
                    && phase != Phase.ACTIONABLE
                    && phase != Phase.AMBIGUOUS) {
                throw new IllegalArgumentException("Only a viable navigation hover may own the link cursor");
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

    private record Press(double x, double y, Optional<SFMSymbolHoverIdentity> navigationIdentity) {
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
    private CancellationCause lastCancellationCause = CancellationCause.NONE;

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
        refreshIdentity(CancellationCause.TARGET_CHANGED);
    }

    /** Modifier events are independent of movement, so pressing Ctrl over a stationary symbol works. */
    public synchronized void modifiersChanged(SFMSymbolHoverIdentity.Modifiers newModifiers) {
        Objects.requireNonNull(newModifiers, "newModifiers");
        if (modifiers.equals(newModifiers)) return;
        boolean releasedDefinitionModifier = modifiers.control() && !newModifiers.control();
        modifiers = newModifiers;
        if (releasedDefinitionModifier) {
            cancelActive(CancellationCause.MODIFIERS_CHANGED);
            snapshot = Snapshot.idle();
            press = null;
        }
        refreshIdentity(CancellationCause.MODIFIERS_CHANGED);
    }

    public synchronized void documentChanged() {
        clear(true, CancellationCause.DOCUMENT_CHANGED);
    }

    public synchronized void focusChanged() {
        clear(true, CancellationCause.FOCUS_CHANGED);
    }

    public synchronized void screenClosed() {
        clear(true, CancellationCause.SCREEN_CLOSED);
    }

    public synchronized void pointerExited() {
        clear(true, CancellationCause.POINTER_EXITED);
    }

    /** Captures the exact viable navigation identity, but leaves final click/drag policy to release. */
    public synchronized void primaryPressed(double x, double y) {
        press = new Press(x, y, snapshot.ownsLinkCursor() ? snapshot.identity() : Optional.empty());
    }

    public synchronized GestureDecision primaryReleased(double x, double y) {
        Press captured = press;
        press = null;
        if (captured == null) return fallback(GestureKind.FALLBACK_CLICK);
        if (dragThreshold.isDrag(captured.x(), captured.y(), x, y)) {
            return fallback(GestureKind.FALLBACK_DRAG);
        }
        if (captured.navigationIdentity().isPresent()
                && captured.navigationIdentity().equals(snapshot.identity())
                && snapshot.identity().orElseThrow().requestsDefinitionNavigation()) {
            return new GestureDecision(GestureKind.ACTIVATE_DEFINITION, captured.navigationIdentity());
        }
        return fallback(GestureKind.FALLBACK_CLICK);
    }

    public synchronized Snapshot snapshot() {
        return snapshot;
    }

    public synchronized int cachedIdentityCount() {
        return terminalCache.size();
    }

    public synchronized CancellationCause lastCancellationCause() {
        return lastCancellationCause;
    }

    @Override
    public synchronized void close() {
        screenClosed();
        terminalCache.clear();
    }

    private void refreshIdentity(CancellationCause cancellationCause) {
        if (target.isEmpty() || !modifiers.control() || modifiers.alt()) {
            cancelActive(cancellationCause);
            snapshot = Snapshot.idle();
            return;
        }
        SFMSymbolHoverIdentity identity = target.orElseThrow().identity(modifiers);
        if (snapshot.identity().filter(identity::equals).isPresent()) return;
        cancelActive(cancellationCause);
        SFMSymbolHoverLookup.Resolution cached = terminalCache.get(identity);
        if (cached != null) {
            lastCancellationCause = CancellationCause.NONE;
            snapshot = snapshot(identity, cached);
            return;
        }
        long generation = ++lookupGeneration;
        SFMSymbolHoverLookup.Query query = Objects.requireNonNull(lookup.submit(identity), "lookup query");
        activeLookup = new ActiveLookup(generation, identity, query);
        lastCancellationCause = CancellationCause.NONE;
        snapshot = new Snapshot(
                Phase.LOOKING_UP,
                Optional.of(identity),
                Optional.of(identity.range()),
                true
        );
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
        if (resolution == SFMSymbolHoverLookup.Resolution.ACTIONABLE
                || resolution == SFMSymbolHoverLookup.Resolution.AMBIGUOUS) {
            return new Snapshot(
                    resolution == SFMSymbolHoverLookup.Resolution.ACTIONABLE ? Phase.ACTIONABLE : Phase.AMBIGUOUS,
                    Optional.of(identity),
                    Optional.of(identity.range()),
                    true
            );
        }
        Phase phase = switch (resolution) {
            case ACTIONABLE, AMBIGUOUS -> throw new IllegalStateException("handled above");
            case UNRESOLVED -> Phase.UNRESOLVED;
            case UNAVAILABLE -> Phase.UNAVAILABLE;
        };
        return new Snapshot(phase, Optional.of(identity), Optional.empty(), false);
    }

    private void clear(boolean forgetTarget, CancellationCause cancellationCause) {
        cancelActive(cancellationCause);
        snapshot = Snapshot.idle();
        press = null;
        if (forgetTarget) target = Optional.empty();
    }

    private void cancelActive(CancellationCause cancellationCause) {
        if (activeLookup == null) return;
        lastCancellationCause = Objects.requireNonNull(cancellationCause, "cancellationCause");
        activeLookup.query().cancel();
        activeLookup = null;
    }

    private static GestureDecision fallback(GestureKind kind) {
        return new GestureDecision(kind, Optional.empty());
    }
}
