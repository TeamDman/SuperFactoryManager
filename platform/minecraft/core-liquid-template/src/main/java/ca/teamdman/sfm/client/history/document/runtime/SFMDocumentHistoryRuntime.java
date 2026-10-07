package ca.teamdman.sfm.client.history.document.runtime;

import ca.teamdman.sfm.client.history.SFMDocumentHistoryHostController;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.SessionIdentity;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-local catalog of exact document-history sessions.
 *
 * <p>The catalog publishes lightweight immutable events. Projection and canvas
 * layout remain consumers' work, so a document mutation never blocks on UI
 * rendering. Registering a panel before its target exists is supported: a
 * later registration is pushed to every subscriber.</p>
 */
public final class SFMDocumentHistoryRuntime {
    private static final SFMDocumentHistoryRuntime INSTANCE = new SFMDocumentHistoryRuntime();

    public enum ChangeKind {
        CURRENT,
        REGISTERED,
        UNREGISTERED,
        FOCUSED,
        SESSION_CHANGED
    }

    public enum ResolutionStatus {
        RESOLVED,
        NO_FOCUSED_SESSION,
        NOT_REGISTERED
    }

    public record SessionEntry(
            SessionIdentity identity,
            SFMDocumentHistorySession session,
            long sessionGeneration,
            Optional<SFMDocumentHistoryHostController> controller
    ) {
        public SessionEntry {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(session, "session");
            if (!identity.equals(session.identity())) {
                throw new IllegalArgumentException("Session entry identity does not match its session");
            }
            if (sessionGeneration < 0) {
                throw new IllegalArgumentException("sessionGeneration must not be negative");
            }
            Objects.requireNonNull(controller, "controller");
        }

        public String sessionId() {
            return identity.sessionId();
        }
    }

    public record CatalogSnapshot(
            long revision,
            Optional<String> focusedSessionId,
            List<SessionEntry> sessions
    ) {
        public CatalogSnapshot {
            if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
            Objects.requireNonNull(focusedSessionId, "focusedSessionId");
            sessions = sessions.stream()
                    .map(value -> Objects.requireNonNull(value, "session"))
                    .sorted(Comparator.comparing(SessionEntry::sessionId))
                    .toList();
            if (focusedSessionId.isPresent() && sessions.stream().noneMatch(
                    entry -> entry.sessionId().equals(focusedSessionId.orElseThrow()))) {
                throw new IllegalArgumentException("Focused session is absent from the catalog");
            }
        }

        public Optional<SessionEntry> exact(String sessionId) {
            Objects.requireNonNull(sessionId, "sessionId");
            return sessions.stream().filter(entry -> entry.sessionId().equals(sessionId)).findFirst();
        }

        public Optional<SessionEntry> focused() {
            return focusedSessionId.flatMap(this::exact);
        }

        public Resolution resolve(SFMDocumentHistorySelector selector) {
            Objects.requireNonNull(selector, "selector");
            if (selector.kind() == SFMDocumentHistorySelector.Kind.FOCUSED) {
                return focused()
                        .map(entry -> new Resolution(ResolutionStatus.RESOLVED, selector, Optional.of(entry)))
                        .orElseGet(() -> new Resolution(
                                ResolutionStatus.NO_FOCUSED_SESSION,
                                selector,
                                Optional.empty()
                        ));
            }
            return exact(selector.sessionId().orElseThrow())
                    .map(entry -> new Resolution(ResolutionStatus.RESOLVED, selector, Optional.of(entry)))
                    .orElseGet(() -> new Resolution(
                            ResolutionStatus.NOT_REGISTERED,
                            selector,
                            Optional.empty()
                    ));
        }
    }

    public record Resolution(
            ResolutionStatus status,
            SFMDocumentHistorySelector selector,
            Optional<SessionEntry> session
    ) {
        public Resolution {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(session, "session");
            if ((status == ResolutionStatus.RESOLVED) != session.isPresent()) {
                throw new IllegalArgumentException("Only a resolved selector may contain a session");
            }
        }
    }

    public record CatalogEvent(
            ChangeKind kind,
            String subjectId,
            CatalogSnapshot snapshot
    ) {
        public CatalogEvent {
            Objects.requireNonNull(kind, "kind");
            subjectId = requireText(subjectId, "subjectId");
            Objects.requireNonNull(snapshot, "snapshot");
        }

        public long revision() {
            return snapshot.revision();
        }

        public Resolution resolve(SFMDocumentHistorySelector selector) {
            return snapshot.resolve(selector);
        }
    }

    @FunctionalInterface
    public interface Listener {
        void changed(CatalogEvent event);
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public final class Registration implements AutoCloseable {
        private final String sessionId;
        private final long token;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Registration(String sessionId, long token) {
            this.sessionId = sessionId;
            this.token = token;
        }

        public String sessionId() {
            return sessionId;
        }

        public boolean focus() {
            return !closed.get() && focusSession(sessionId);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) unregister(sessionId, token);
        }
    }

    private static final class RegisteredSession {
        private final long token;
        private final SFMDocumentHistorySession session;
        private final SFMDocumentHistorySession.Subscription subscription;
        private final Optional<SFMDocumentHistoryHostController> controller;
        private volatile long generation;

        private RegisteredSession(
                long token,
                SFMDocumentHistorySession session,
                SFMDocumentHistorySession.Subscription subscription,
                Optional<SFMDocumentHistoryHostController> controller,
                long generation
        ) {
            this.token = token;
            this.session = session;
            this.subscription = subscription;
            this.controller = controller;
            this.generation = generation;
        }
    }

    private final Object lock = new Object();
    private final LinkedHashMap<String, RegisteredSession> sessions = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private long revision;
    private long nextRegistrationToken = 1;
    private String focusedSessionId;

    public static SFMDocumentHistoryRuntime get() {
        return INSTANCE;
    }

    public Registration register(SFMDocumentHistorySession session) {
        return register(session, Optional.empty(), false);
    }

    public Registration registerFocused(SFMDocumentHistorySession session) {
        return register(session, Optional.empty(), true);
    }

    public Registration register(SFMDocumentHistoryHostController controller) {
        Objects.requireNonNull(controller, "controller");
        return register(controller.session(), Optional.of(controller), false);
    }

    public Registration registerFocused(SFMDocumentHistoryHostController controller) {
        Objects.requireNonNull(controller, "controller");
        return register(controller.session(), Optional.of(controller), true);
    }

    private Registration register(
            SFMDocumentHistorySession session,
            Optional<SFMDocumentHistoryHostController> controller,
            boolean focus
    ) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(controller, "controller");
        controller.ifPresent(value -> {
            if (value.session() != session) {
                throw new IllegalArgumentException("Document-history controller owns another session");
            }
        });
        String sessionId = session.identity().sessionId();
        long initialGeneration = session.generation();
        long token;
        synchronized (lock) {
            token = nextRegistrationToken++;
        }
        long registrationToken = token;
        SFMDocumentHistorySession.Subscription sessionSubscription = session.subscribe(
                notification -> sessionChanged(sessionId, registrationToken, notification.generation()),
                false
        );
        CatalogEvent event;
        synchronized (lock) {
            if (sessions.containsKey(sessionId)) {
                sessionSubscription.close();
                throw new IllegalStateException("Document-history session is already registered: " + sessionId);
            }
            sessions.put(sessionId, new RegisteredSession(
                    token,
                    session,
                    sessionSubscription,
                    controller,
                    Math.max(initialGeneration, session.generation())
            ));
            if (focus) focusedSessionId = sessionId;
            event = changedLocked(ChangeKind.REGISTERED, sessionId);
        }
        publish(event);
        return new Registration(sessionId, token);
    }

    public boolean focusSession(String sessionId) {
        sessionId = requireText(sessionId, "sessionId");
        CatalogEvent event;
        synchronized (lock) {
            if (!sessions.containsKey(sessionId)) return false;
            if (sessionId.equals(focusedSessionId)) return true;
            focusedSessionId = sessionId;
            event = changedLocked(ChangeKind.FOCUSED, sessionId);
        }
        publish(event);
        return true;
    }

    public boolean clearFocus(String sessionId) {
        sessionId = requireText(sessionId, "sessionId");
        CatalogEvent event;
        synchronized (lock) {
            if (!sessionId.equals(focusedSessionId)) return false;
            focusedSessionId = null;
            event = changedLocked(ChangeKind.FOCUSED, sessionId);
        }
        publish(event);
        return true;
    }

    public Optional<SessionEntry> exact(String sessionId) {
        return snapshot().exact(sessionId);
    }

    public Optional<SessionEntry> focused() {
        return snapshot().focused();
    }

    public Resolution resolve(SFMDocumentHistorySelector selector) {
        return snapshot().resolve(selector);
    }

    public List<String> sessionIds() {
        return snapshot().sessions().stream().map(SessionEntry::sessionId).toList();
    }

    public CatalogSnapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    /** Every subscription receives the current catalog synchronously before returning. */
    public Subscription subscribe(Listener listener) {
        return subscribe(listener, true);
    }

    public Subscription subscribe(Listener listener, boolean emitCurrent) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        if (emitCurrent) listener.changed(new CatalogEvent(ChangeKind.CURRENT, "catalog", snapshot()));
        return () -> listeners.remove(listener);
    }

    private void sessionChanged(String sessionId, long token, long sessionGeneration) {
        CatalogEvent event;
        synchronized (lock) {
            RegisteredSession registered = sessions.get(sessionId);
            if (registered == null || registered.token != token) return;
            registered.generation = Math.max(registered.generation, sessionGeneration);
            event = changedLocked(ChangeKind.SESSION_CHANGED, sessionId);
        }
        publish(event);
    }

    private void unregister(String sessionId, long token) {
        RegisteredSession removed;
        CatalogEvent event;
        synchronized (lock) {
            removed = sessions.get(sessionId);
            if (removed == null || removed.token != token) return;
            sessions.remove(sessionId);
            if (sessionId.equals(focusedSessionId)) focusedSessionId = null;
            event = changedLocked(ChangeKind.UNREGISTERED, sessionId);
        }
        removed.subscription.close();
        publish(event);
    }

    private CatalogEvent changedLocked(ChangeKind kind, String subjectId) {
        revision++;
        return new CatalogEvent(kind, subjectId, snapshotLocked());
    }

    private CatalogSnapshot snapshotLocked() {
        ArrayList<SessionEntry> entries = new ArrayList<>(sessions.size());
        for (Map.Entry<String, RegisteredSession> entry : sessions.entrySet()) {
            RegisteredSession registered = entry.getValue();
            entries.add(new SessionEntry(
                    registered.session.identity(),
                    registered.session,
                    registered.generation,
                    registered.controller
            ));
        }
        return new CatalogSnapshot(revision, Optional.ofNullable(focusedSessionId), entries);
    }

    private void publish(CatalogEvent event) {
        for (Listener listener : List.copyOf(listeners)) listener.changed(event);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
