package ca.teamdman.sfm.client.history;

import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonKernel;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Process-wide owner of persisted route-comparison sessions.
 *
 * <p>This runtime deliberately has no trajectory-machine operation dependency. Mode,
 * cursor, and disposition changes can therefore persist review state but cannot move
 * the actual history head, instruction pointer, or selected trajectory. Explicit
 * trajectory selection lives in the action adapter instead.</p>
 */
public final class SFMRouteComparisonRuntime {
    private static final SFMRouteComparisonRuntime INSTANCE = new SFMRouteComparisonRuntime(
            SFMRouteComparisonRuntime::defaultStore
    );

    private final Function<String, SFMRouteComparisonStore> storeFactory;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public SFMRouteComparisonRuntime(Function<String, SFMRouteComparisonStore> storeFactory) {
        this.storeFactory = Objects.requireNonNull(storeFactory, "storeFactory");
    }

    public static SFMRouteComparisonRuntime get() {
        return INSTANCE;
    }

    /** Open the deterministic comparison of the final two routes in the immutable plan book. */
    public synchronized SFMRouteComparisonSession openLatest(
            SFMHistoryGraphRuntime.MachineSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<RouteDescriptor> routes = retainedRoutes(snapshot);
        if (routes.size() < 2) {
            throw new IllegalArgumentException(
                    "Trajectory machine " + snapshot.machineId() + " retains fewer than two routes"
            );
        }
        RouteDescriptor left = routes.get(routes.size() - 2);
        RouteDescriptor right = routes.get(routes.size() - 1);
        return open(snapshot, left.address(), right.address(), Optional.empty());
    }

    /** Open an explicit immutable route pair, reusing its deterministic persisted identity. */
    public synchronized SFMRouteComparisonSession open(
            SFMHistoryGraphRuntime.MachineSnapshot snapshot,
            SFMRouteComparisonSession.RouteAddress left,
            SFMRouteComparisonSession.RouteAddress right,
            Optional<String> requestedSessionId
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(requestedSessionId, "requestedSessionId");
        if (!snapshot.machineId().equals(left.machineId())
                || !snapshot.machineId().equals(right.machineId())) {
            throw new IllegalArgumentException("Compared routes must belong to the supplied trajectory machine");
        }
        RouteDescriptor leftRoute = describe(snapshot, left).orElseThrow(() ->
                new IllegalArgumentException("Left retained route is unavailable: " + canonical(left)));
        RouteDescriptor rightRoute = describe(snapshot, right).orElseThrow(() ->
                new IllegalArgumentException("Right retained route is unavailable: " + canonical(right)));
        String id = requestedSessionId
                .map(value -> requireText(value, "sessionId"))
                .orElseGet(() -> deterministicSessionId(left, right));
        return createOrLoad(id, leftRoute, rightRoute);
    }

    /** Load a session by stable ID without fabricating one when no persisted state exists. */
    public synchronized Optional<SFMRouteComparisonSession> find(String sessionId) {
        sessionId = requireText(sessionId, "sessionId");
        Entry cached = entries.get(sessionId);
        if (cached != null) return Optional.of(cached.session);
        SFMRouteComparisonStore store = storeFactory.apply(sessionId);
        try {
            Optional<SFMRouteComparisonSession> loaded = store.load();
            if (loaded.isEmpty()) return Optional.empty();
            SFMRouteComparisonSession session = loaded.orElseThrow();
            if (!session.id().equals(sessionId)) {
                throw new IllegalStateException("Persisted comparison ID does not match its lookup key");
            }
            entries.put(sessionId, new Entry(store, session));
            return Optional.of(session);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException(
                    "Unable to load route comparison " + sessionId + ": " + failureMessage(failure),
                    failure
            );
        }
    }

    public synchronized SFMRouteComparisonSession require(String sessionId) {
        return find(sessionId).orElseThrow(() ->
                new IllegalArgumentException("Unknown route-comparison session " + sessionId));
    }

    /** IDs materialized in this process; focused panels materialize persisted sessions on reopen. */
    public synchronized List<String> loadedSessionIds() {
        return List.copyOf(entries.keySet());
    }

    public synchronized SFMRouteComparisonSession setMode(
            String sessionId,
            SFMRouteComparisonSession.Mode mode
    ) {
        return mutate(sessionId, session -> SFMRouteComparisonKernel.setMode(session, mode));
    }

    public synchronized SFMRouteComparisonSession seek(
            String sessionId,
            SFMRouteComparisonSession.Side side,
            int position
    ) {
        return mutate(sessionId, session -> SFMRouteComparisonKernel.seek(session, side, position));
    }

    public synchronized SFMRouteComparisonSession setDisposition(
            String sessionId,
            SFMRouteComparisonSession.Side side,
            SFMRouteComparisonSession.Disposition disposition
    ) {
        return mutate(sessionId, session ->
                SFMRouteComparisonKernel.setDisposition(session, side, disposition));
    }

    /** Restore default review state while preserving the exact immutable route pair and bounds. */
    public synchronized SFMRouteComparisonSession reset(String sessionId) {
        SFMRouteComparisonSession before = require(sessionId);
        SFMRouteComparisonSession reset = SFMRouteComparisonSession.create(
                before.id(),
                before.left(),
                before.right(),
                before.leftLastPosition(),
                before.rightLastPosition()
        );
        Entry entry = entries.get(before.id());
        save(entry.store, reset);
        entry.session = reset;
        return reset;
    }

    /**
     * Evict and decode persisted state without rewriting it, preserving the store's exact
     * canonical bytes for deterministic puppet round-trip assertions.
     */
    public synchronized SFMRouteComparisonSession reload(String sessionId) {
        String resolvedSessionId = requireText(sessionId, "sessionId");
        entries.remove(resolvedSessionId);
        return find(resolvedSessionId).orElseThrow(() ->
                new IllegalArgumentException("No persisted route-comparison session " + resolvedSessionId));
    }

    public static List<RouteDescriptor> retainedRoutes(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ArrayList<RouteDescriptor> result = new ArrayList<>();
        for (SFMTrajectoryContract.TrajectoryPlanRevision plan : snapshot.planBook().plans()) {
            for (SFMTrajectoryContract.TrajectoryRoute route : plan.routes()) {
                result.add(descriptor(snapshot.machineId(), plan, route));
            }
        }
        return List.copyOf(result);
    }

    public static Optional<RouteDescriptor> describe(
            SFMHistoryGraphRuntime.MachineSnapshot snapshot,
            SFMRouteComparisonSession.RouteAddress address
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(address, "address");
        if (!snapshot.machineId().equals(address.machineId())) return Optional.empty();
        return snapshot.planBook().plans().stream()
                .filter(plan -> plan.id().equals(address.planRevisionId()))
                .flatMap(plan -> plan.routes().stream()
                        .filter(route -> route.id().equals(address.routeId()))
                        .map(route -> descriptor(snapshot.machineId(), plan, route)))
                .findFirst();
    }

    /** Stable, order-sensitive identity: swapping left and right creates a distinct comparison. */
    public static String deterministicSessionId(
            SFMRouteComparisonSession.RouteAddress left,
            SFMRouteComparisonSession.RouteAddress right
    ) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        String seed = canonical(left) + "\n" + canonical(right);
        return "route-comparison-" + sha256(seed).substring(0, 20);
    }

    /** Resolve the palette selector forms accepted by the X3 actions. */
    public synchronized String resolveSelector(String selector, Optional<String> focusedSessionId) {
        selector = requireText(selector, "comparison selector");
        Objects.requireNonNull(focusedSessionId, "focusedSessionId");
        if (selector.equals("focused")) {
            return focusedSessionId.orElseThrow(() ->
                    new IllegalArgumentException("No focused route-comparison panel is available"));
        }
        if (selector.startsWith("id(") && selector.endsWith(")") && selector.length() > 4) {
            selector = selector.substring(3, selector.length() - 1);
        }
        require(selector);
        return selector;
    }

    private SFMRouteComparisonSession createOrLoad(
            String id,
            RouteDescriptor left,
            RouteDescriptor right
    ) {
        Optional<SFMRouteComparisonSession> existing = find(id);
        if (existing.isPresent()) {
            SFMRouteComparisonSession session = existing.orElseThrow();
            if (!session.left().equals(left.address())
                    || !session.right().equals(right.address())
                    || session.leftLastPosition() != left.lastPosition()
                    || session.rightLastPosition() != right.lastPosition()) {
                throw new IllegalStateException(
                        "Persisted comparison " + id + " does not match the requested immutable route pair"
                );
            }
            return session;
        }
        SFMRouteComparisonSession created = SFMRouteComparisonSession.create(
                id,
                left.address(),
                right.address(),
                left.lastPosition(),
                right.lastPosition()
        );
        SFMRouteComparisonStore store = storeFactory.apply(id);
        save(store, created);
        entries.put(id, new Entry(store, created));
        return created;
    }

    private SFMRouteComparisonSession mutate(
            String sessionId,
            Function<SFMRouteComparisonSession, SFMRouteComparisonSession> transition
    ) {
        SFMRouteComparisonSession before = require(sessionId);
        SFMRouteComparisonSession after = Objects.requireNonNull(transition.apply(before), "transition result");
        if (!after.id().equals(before.id())
                || !after.left().equals(before.left())
                || !after.right().equals(before.right())) {
            throw new IllegalStateException("Comparison transitions must preserve session and route identities");
        }
        if (after == before) return before;
        Entry entry = entries.get(before.id());
        save(entry.store, after);
        entry.session = after;
        return after;
    }

    private static RouteDescriptor descriptor(
            String machineId,
            SFMTrajectoryContract.TrajectoryPlanRevision plan,
            SFMTrajectoryContract.TrajectoryRoute route
    ) {
        String finalStateId = route.steps().isEmpty()
                ? route.startStateId()
                : route.steps().get(route.steps().size() - 1).predictedStateId();
        return new RouteDescriptor(
                new SFMRouteComparisonSession.RouteAddress(machineId, plan.id(), route.id()),
                route.steps().size(),
                route.totalCost(),
                route.status(),
                finalStateId
        );
    }

    private static SFMRouteComparisonStore defaultStore(String sessionId) {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path root = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".local", "share")
                : Path.of(localAppData);
        return new SFMRouteComparisonStore(root.resolve("teamdman").resolve("SFM")
                .resolve("route-comparisons")
                .resolve(sha256(sessionId) + ".route-comparison"));
    }

    private static void save(SFMRouteComparisonStore store, SFMRouteComparisonSession session) {
        try {
            store.save(session);
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Unable to persist route comparison " + session.id() + ": " + failureMessage(failure),
                    failure
            );
        }
    }

    private static String canonical(SFMRouteComparisonSession.RouteAddress address) {
        return address.machineId() + "#" + address.planRevisionId() + "#" + address.routeId();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static String failureMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    public record RouteDescriptor(
            SFMRouteComparisonSession.RouteAddress address,
            int lastPosition,
            long totalCost,
            SFMHistoryGraphContract.ProjectionStatus status,
            String finalStateId
    ) {
        public RouteDescriptor {
            Objects.requireNonNull(address, "address");
            if (lastPosition < 0) throw new IllegalArgumentException("lastPosition must not be negative");
            if (totalCost < 0) throw new IllegalArgumentException("totalCost must not be negative");
            Objects.requireNonNull(status, "status");
            finalStateId = requireText(finalStateId, "finalStateId");
        }
    }

    private static final class Entry {
        private final SFMRouteComparisonStore store;
        private SFMRouteComparisonSession session;

        private Entry(SFMRouteComparisonStore store, SFMRouteComparisonSession session) {
            this.store = Objects.requireNonNull(store, "store");
            this.session = Objects.requireNonNull(session, "session");
        }
    }
}
