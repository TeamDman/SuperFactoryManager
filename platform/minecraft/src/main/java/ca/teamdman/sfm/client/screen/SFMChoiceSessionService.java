package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import net.minecraft.resources.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Process-local bounded owner of ephemeral constrained-palette sessions. */
public final class SFMChoiceSessionService {
    private static final int MAX_SESSIONS = 16;
    private static final Map<Long, SFMChoiceSession> SESSIONS = new LinkedHashMap<>();
    private static long nextId = 1;

    private SFMChoiceSessionService() {
    }

    public static SFMChoiceSession create(
            List<SFMActionChoice> candidates,
            SFMClientActionContext context
    ) {
        return create(candidates, context, id -> SFMClientActions.registry().get(id),
                SFMClientActions.commandTree());
    }

    static synchronized SFMChoiceSession create(
            List<SFMActionChoice> candidates,
            SFMClientActionContext context,
            Function<ResourceLocation, SFMClientAction<?>> actionLookup,
            SFMClientActionCommandTree actionTree
    ) {
        while (SESSIONS.size() >= MAX_SESSIONS) {
            Iterator<SFMChoiceSession> iterator = SESSIONS.values().iterator();
            if (!iterator.hasNext()) break;
            SFMChoiceSession expired = iterator.next();
            iterator.remove();
            expired.invalidate();
        }
        long id = nextId++;
        if (id <= 0) throw new IllegalStateException("SFM choice-session id space exhausted");
        SFMChoiceSession session = new SFMChoiceSession(
                id, candidates, context, actionLookup, actionTree);
        SESSIONS.put(id, session);
        return session;
    }

    static synchronized boolean isCurrent(SFMChoiceSession session) {
        return SESSIONS.get(session.id()) == session;
    }

    static synchronized void consume(SFMChoiceSession session) {
        if (SESSIONS.remove(session.id(), session)) session.invalidate();
    }

    public static synchronized void invalidate(SFMChoiceSession session) {
        SESSIONS.remove(session.id(), session);
        session.invalidate();
    }

    static synchronized void clearForTests() {
        SESSIONS.values().forEach(SFMChoiceSession::invalidate);
        SESSIONS.clear();
        nextId = 1;
    }
}
