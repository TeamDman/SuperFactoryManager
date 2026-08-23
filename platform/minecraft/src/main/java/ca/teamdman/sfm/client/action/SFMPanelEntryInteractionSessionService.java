package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMPanelEntryAffordanceLayout;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceStackId;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Bounded one-shot leases for delayed panel-entry pointer actions. */
public final class SFMPanelEntryInteractionSessionService {
    private static final int MAX_SESSIONS = 32;
    private static final Map<Long, Session> SESSIONS = new LinkedHashMap<>();
    private static long nextId = 1;

    private SFMPanelEntryInteractionSessionService() {
    }

    public static synchronized Optional<Session> create(
            SFMScreenMultiplexer workspace,
            SFMPanelEntryAffordanceLayout.HitRegion hit
    ) {
        if (workspace.panelInstance(hit.entryId()) != hit.capturedPanel()
                || !workspace.panelStackId(hit.entryId()).filter(hit.paneId()::equals).isPresent()) {
            return Optional.empty();
        }
        while (SESSIONS.size() >= MAX_SESSIONS) {
            Iterator<Session> iterator = SESSIONS.values().iterator();
            if (!iterator.hasNext()) break;
            Session expired = iterator.next();
            iterator.remove();
            expired.invalidate();
        }
        long id = nextId++;
        if (id <= 0) throw new IllegalStateException("Panel-entry interaction id space exhausted");
        Session session = new Session(
                id,
                workspace,
                hit.paneId(),
                hit.entryId(),
                hit.capturedPanel(),
                hit.zeroBasedIndex(),
                hit.entryCount()
        );
        SESSIONS.put(id, session);
        return Optional.of(session);
    }

    public static synchronized Optional<Session> consume(long id, SFMScreenMultiplexer workspace) {
        Session session = SESSIONS.remove(id);
        if (session == null) return Optional.empty();
        boolean current = session.matches(workspace);
        session.invalidate();
        return current ? Optional.of(session) : Optional.empty();
    }

    public static synchronized void invalidate(Session session) {
        SESSIONS.remove(session.id(), session);
        session.invalidate();
    }

    public static synchronized java.util.List<Long> activeIds(SFMScreenMultiplexer workspace) {
        return SESSIONS.values().stream()
                .filter(Session::active)
                .filter(session -> session.workspace() == workspace)
                .map(Session::id)
                .toList();
    }

    static synchronized void clearForTests() {
        SESSIONS.values().forEach(Session::invalidate);
        SESSIONS.clear();
        nextId = 1;
    }

    public static final class Session {
        private final long id;
        private final SFMScreenMultiplexer workspace;
        private final SFMWorkspaceStackId paneId;
        private final SFMWorkspacePanelId entryId;
        private final SFMScreenPanel panel;
        private final int zeroBasedIndex;
        private final int entryCount;
        private boolean active = true;

        private Session(
                long id,
                SFMScreenMultiplexer workspace,
                SFMWorkspaceStackId paneId,
                SFMWorkspacePanelId entryId,
                SFMScreenPanel panel,
                int zeroBasedIndex,
                int entryCount
        ) {
            this.id = id;
            this.workspace = workspace;
            this.paneId = paneId;
            this.entryId = entryId;
            this.panel = panel;
            this.zeroBasedIndex = zeroBasedIndex;
            this.entryCount = entryCount;
        }

        public long id() { return id; }
        public SFMScreenMultiplexer workspace() { return workspace; }
        public SFMWorkspaceStackId paneId() { return paneId; }
        public SFMWorkspacePanelId entryId() { return entryId; }
        public SFMScreenPanel panel() { return panel; }
        public int oneBasedIndex() { return zeroBasedIndex + 1; }
        public int entryCount() { return entryCount; }
        public boolean active() { return active; }
        public String commandArgument() { return Long.toString(id); }
        public String stableId() { return "panel-entry-" + entryId.value(); }

        private boolean matches(SFMScreenMultiplexer candidate) {
            if (!active
                    || workspace != candidate
                    || candidate.panelInstance(entryId) != panel
                    || !candidate.panelStackId(entryId).filter(paneId::equals).isPresent()) {
                return false;
            }
            var currentEntries = candidate.panelSlotEntries(entryId);
            return currentEntries.size() == entryCount
                    && zeroBasedIndex < currentEntries.size()
                    && currentEntries.get(zeroBasedIndex).id().equals(entryId)
                    && currentEntries.get(zeroBasedIndex).panel() == panel;
        }

        private void invalidate() {
            active = false;
        }
    }
}
