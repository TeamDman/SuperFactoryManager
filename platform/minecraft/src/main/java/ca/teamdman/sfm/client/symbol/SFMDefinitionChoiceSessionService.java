package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded one-shot definitions referenced by exact constrained-palette commands. */
public final class SFMDefinitionChoiceSessionService {
    private static final int MAXIMUM_SESSIONS = 16;
    private final Map<Long, Session> sessions = new LinkedHashMap<>();
    private long nextId;

    public record ChoiceSet(long sessionId, List<SFMActionChoice> choices) {
        public ChoiceSet {
            if (sessionId <= 0) throw new IllegalArgumentException("sessionId must be positive");
            choices = List.copyOf(choices);
            if (choices.isEmpty()) throw new IllegalArgumentException("choices must not be empty");
        }
    }

    public record Selection(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMSymbolServerProtocol.ServerHello hello,
            SFMDefinitionResult.Definition definition
    ) {
        public Selection {
            Objects.requireNonNull(workspace, "workspace");
            Objects.requireNonNull(sourcePanelId, "sourcePanelId");
            Objects.requireNonNull(hello, "hello");
            Objects.requireNonNull(definition, "definition");
        }
    }

    private record Candidate(String label, SFMDefinitionResult.Definition definition) {
    }

    private record Session(
            long id,
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMSymbolServerProtocol.ServerHello hello,
            List<Candidate> candidates
    ) {
    }

    public synchronized ChoiceSet create(
            ResourceLocation actionId,
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId sourcePanelId,
            SFMSymbolServerProtocol.ServerHello hello,
            List<SFMDefinitionResult.Definition> definitions
    ) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(sourcePanelId, "sourcePanelId");
        Objects.requireNonNull(hello, "hello");
        List<SFMDefinitionResult.Definition> ordered = stableDefinitions(definitions);
        if (ordered.isEmpty()) throw new IllegalArgumentException("definitions must not be empty");

        long id = increment(nextId);
        nextId = id;
        ArrayList<Candidate> candidates = new ArrayList<>(ordered.size());
        ArrayList<SFMActionChoice> choices = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            SFMDefinitionResult.Definition definition = ordered.get(index);
            String label = candidateLabel(definition);
            candidates.add(new Candidate(label, definition));
            choices.add(SFMActionChoice.invoke(
                    actionId,
                    "select " + id + " " + index + " " + label
            ));
        }
        sessions.put(id, new Session(id, workspace, sourcePanelId, hello, List.copyOf(candidates)));
        while (sessions.size() > MAXIMUM_SESSIONS) {
            sessions.remove(sessions.keySet().iterator().next());
        }
        return new ChoiceSet(id, choices);
    }

    public synchronized Optional<Selection> consume(
            long sessionId,
            int candidateIndex,
            String expectedLabel,
            SFMClientActionContext invocationContext
    ) {
        Objects.requireNonNull(expectedLabel, "expectedLabel");
        Objects.requireNonNull(invocationContext, "invocationContext");
        Session session = sessions.get(sessionId);
        if (session == null
                || invocationContext.originatingHost() != session.workspace()
                || !Objects.equals(invocationContext.originatingPanelId(), session.sourcePanelId())
                || candidateIndex < 0
                || candidateIndex >= session.candidates().size()) return Optional.empty();
        Candidate candidate = session.candidates().get(candidateIndex);
        if (!candidate.label().equals(expectedLabel)) return Optional.empty();
        sessions.remove(sessionId);
        return Optional.of(new Selection(
                session.workspace(),
                session.sourcePanelId(),
                session.hello(),
                candidate.definition()
        ));
    }

    synchronized int activeSessionCount() {
        return sessions.size();
    }

    static List<SFMDefinitionResult.Definition> stableDefinitions(
            List<SFMDefinitionResult.Definition> definitions
    ) {
        return definitions.stream()
                .map(value -> Objects.requireNonNull(value, "definition"))
                .distinct()
                .sorted(Comparator
                        .comparing((SFMDefinitionResult.Definition value) -> value.symbol().qualifiedName())
                        .thenComparing(value -> value.identifierSpan().address())
                        .thenComparingLong(value -> value.identifierSpan().startByte())
                        .thenComparing(value -> value.symbol().descriptor().orElse("")))
                .toList();
    }

    static String candidateLabel(SFMDefinitionResult.Definition definition) {
        SFMDefinitionResult.DefinitionSourceSpan span = definition.identifierSpan();
        String raw = definition.symbol().qualifiedName()
                + "@" + span.reportPath()
                + ":" + span.startLine()
                + ":" + span.startColumn();
        StringBuilder label = new StringBuilder(raw.length());
        for (int index = 0; index < raw.length(); index++) {
            char value = raw.charAt(index);
            label.append(Character.isWhitespace(value) ? '_' : value);
        }
        return label.toString();
    }

    private static long increment(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("Definition choice id space exhausted");
        return value + 1;
    }
}
