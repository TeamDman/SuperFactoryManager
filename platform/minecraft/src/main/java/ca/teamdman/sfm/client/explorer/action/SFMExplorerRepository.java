package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMSelectorRepository;
import ca.teamdman.sfm.client.explorer.SFMSelectorRepositoryEntry;
import ca.teamdman.sfm.client.explorer.SFMSelectorRepositorySnapshot;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Stable registry of live explorer components.
 *
 * <p>Membership and focus recency use one monotonic generation. Session state
 * has its own revision and is deliberately not folded into selector identity.</p>
 */
public final class SFMExplorerRepository implements SFMSelectorRepository<SFMExplorerId> {
    public record Explorer(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerPathPolicy pathPolicy,
            int defaultPageSize
    ) {
        public Explorer {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(pathPolicy, "pathPolicy");
            if (defaultPageSize <= 0) throw new IllegalArgumentException("Default page size must be positive");
        }

        public SFMExplorerId id() {
            return session.snapshot().id();
        }
    }

    public record StateSnapshot(
            long generation,
            Map<SFMExplorerId, Explorer> explorers,
            Optional<SFMExplorerId> focused,
            Map<SFMExplorerId, Long> focusRecency
    ) {
        public StateSnapshot {
            if (generation < 0) throw new IllegalArgumentException("Generation must not be negative");
            Comparator<SFMExplorerId> ids = Comparator.comparing(SFMExplorerId::value);
            TreeMap<SFMExplorerId, Explorer> explorerCopy = new TreeMap<>(ids);
            explorerCopy.putAll(explorers);
            explorers = Collections.unmodifiableMap(explorerCopy);
            Objects.requireNonNull(focused, "focused");
            TreeMap<SFMExplorerId, Long> recencyCopy = new TreeMap<>(ids);
            recencyCopy.putAll(focusRecency);
            focusRecency = Collections.unmodifiableMap(recencyCopy);
        }
    }

    private final Map<SFMExplorerId, Explorer> explorers = new TreeMap<>(Comparator.comparing(SFMExplorerId::value));
    private final Map<SFMExplorerId, Long> focusRecency = new TreeMap<>(Comparator.comparing(SFMExplorerId::value));
    private long generation;
    private long nextFocusOrdinal = 1;

    @Override
    public synchronized SFMSelectorRepositorySnapshot<SFMExplorerId> snapshot() {
        Optional<SFMExplorerId> focused = focusedLocked();
        ArrayList<SFMSelectorRepositoryEntry<SFMExplorerId>> entries = new ArrayList<>();
        for (SFMExplorerId id : explorers.keySet()) {
            entries.add(SFMSelectorRepositoryEntry.unnamed(id, focused.equals(Optional.of(id))));
        }
        return new SFMSelectorRepositorySnapshot<>(generation, entries);
    }

    public synchronized StateSnapshot stateSnapshot() {
        return new StateSnapshot(generation, explorers, focusedLocked(), focusRecency);
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized Optional<Explorer> find(SFMExplorerId id) {
        return Optional.ofNullable(explorers.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Explorer register(Explorer explorer, boolean focus) {
        Objects.requireNonNull(explorer, "explorer");
        SFMExplorerId id = explorer.id();
        if (explorers.putIfAbsent(id, explorer) != null) {
            throw new IllegalArgumentException("Explorer id is already registered: " + id.value());
        }
        generation++;
        if (focus) focusLocked(id);
        return explorer;
    }

    public synchronized Optional<Explorer> unregister(SFMExplorerId id) {
        Objects.requireNonNull(id, "id");
        Explorer removed = explorers.remove(id);
        if (removed != null) {
            focusRecency.remove(id);
            generation++;
        }
        return Optional.ofNullable(removed);
    }

    public synchronized void focus(SFMExplorerId id) {
        Objects.requireNonNull(id, "id");
        if (!explorers.containsKey(id)) {
            throw new IllegalArgumentException("Explorer is not registered: " + id.value());
        }
        focusLocked(id);
    }

    public synchronized boolean isCurrent(SFMExplorerId id, Explorer expected) {
        return explorers.get(Objects.requireNonNull(id, "id")) == Objects.requireNonNull(expected, "expected");
    }

    public synchronized List<Explorer> explorersInStableOrder() {
        return List.copyOf(explorers.values());
    }

    private void focusLocked(SFMExplorerId id) {
        focusRecency.put(id, nextFocusOrdinal++);
        generation++;
    }

    private Optional<SFMExplorerId> focusedLocked() {
        return focusRecency.entrySet().stream()
                .filter(entry -> explorers.containsKey(entry.getKey()))
                .max(Map.Entry.<SFMExplorerId, Long>comparingByValue()
                        .thenComparing(entry -> entry.getKey().value()))
                .map(Map.Entry::getKey);
    }
}
