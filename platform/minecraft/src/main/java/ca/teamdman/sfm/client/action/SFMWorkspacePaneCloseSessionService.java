package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePaneCloseCapture;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Bounded owner of pane-close confirmation witnesses. */
final class SFMWorkspacePaneCloseSessionService {
    private static final int MAX_SESSIONS = 16;
    private static final Map<Long, Session> SESSIONS = new LinkedHashMap<>();
    private static long nextId = 1;

    private SFMWorkspacePaneCloseSessionService() {
    }

    static synchronized Session create(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePaneCloseCapture capture
    ) {
        if (!workspace.matchesPaneCloseCapture(capture)) {
            throw new IllegalArgumentException("Cannot create a session for a stale pane capture");
        }
        while (SESSIONS.size() >= MAX_SESSIONS) {
            Iterator<Session> iterator = SESSIONS.values().iterator();
            if (!iterator.hasNext()) break;
            Session expired = iterator.next();
            iterator.remove();
            expired.invalidate();
        }
        long id = nextId++;
        if (id <= 0) throw new IllegalStateException("Pane-close session id space exhausted");
        Session session = new Session(id, workspace, capture);
        SESSIONS.put(id, session);
        return session;
    }

    static synchronized Optional<Session> consume(long id, SFMScreenMultiplexer workspace) {
        Session session = SESSIONS.remove(id);
        if (session == null) return Optional.empty();
        boolean current = session.active
                && session.workspace == workspace
                && workspace.matchesPaneCloseCapture(session.capture);
        session.invalidate();
        return current ? Optional.of(session) : Optional.empty();
    }

    static synchronized void invalidate(Session session) {
        SESSIONS.remove(session.id, session);
        session.invalidate();
    }

    static synchronized java.util.List<Long> activeIds(SFMScreenMultiplexer workspace) {
        return SESSIONS.values().stream()
                .filter(session -> session.active && session.workspace == workspace)
                .map(session -> session.id)
                .toList();
    }

    static synchronized void clearForTests() {
        SESSIONS.values().forEach(Session::invalidate);
        SESSIONS.clear();
        nextId = 1;
    }

    static final class Session {
        private final long id;
        private final SFMScreenMultiplexer workspace;
        private final SFMWorkspacePaneCloseCapture capture;
        private boolean active = true;

        private Session(long id, SFMScreenMultiplexer workspace, SFMWorkspacePaneCloseCapture capture) {
            this.id = id;
            this.workspace = workspace;
            this.capture = capture;
        }

        long id() { return id; }
        SFMWorkspacePaneCloseCapture capture() { return capture; }
        String commandArgument() { return Long.toString(id); }

        private void invalidate() { active = false; }
    }
}
