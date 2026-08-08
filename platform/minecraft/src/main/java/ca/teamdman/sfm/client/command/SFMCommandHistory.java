package ca.teamdman.sfm.client.command;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Thread-safe, bounded command history. Entries are retained in document order
 * (oldest first); callers asking for suggestions receive a newest-first,
 * duplicate-free view without destroying the document's repeat history.
 */
public final class SFMCommandHistory {
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_COMMAND_LENGTH = 16 * 1024;

    private final Deque<String> entries = new ArrayDeque<>();
    private final Consumer<List<String>> persistence;
    private final Executor persistenceExecutor;

    public SFMCommandHistory(
            List<String> initialEntries,
            Consumer<List<String>> persistence,
            Executor persistenceExecutor
    ) {
        this.persistence = Objects.requireNonNull(persistence);
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor);
        Objects.requireNonNull(initialEntries).forEach(this::appendLoaded);
    }

    public static SFMCommandHistory inMemory() {
        return new SFMCommandHistory(List.of(), ignored -> { }, Runnable::run);
    }

    /** Records a successful, already-normalized palette command. */
    public void record(String command) {
        String normalized = normalize(command);
        if (normalized == null) return;
        List<String> snapshot;
        synchronized (this) {
            entries.addLast(normalized);
            trimToBound();
            snapshot = entriesOldestFirst();
        }
        persistAsync(snapshot);
    }

    public void clear() {
        List<String> snapshot;
        synchronized (this) {
            entries.clear();
            snapshot = List.of();
        }
        persistAsync(snapshot);
    }

    /** Returns the exact bounded document order, oldest command first. */
    public synchronized List<String> entriesOldestFirst() {
        return List.copyOf(entries);
    }

    /** Returns unique commands in most-recently-used order. */
    public synchronized List<String> suggestionsNewestFirst() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        var newest = entries.descendingIterator();
        while (newest.hasNext()) unique.add(newest.next());
        return List.copyOf(unique);
    }

    /** Returns a point-in-time document suitable for the read-only editor. */
    public synchronized String documentText() {
        return String.join("\n", entries);
    }

    public static String normalize(String command) {
        if (command == null) return null;
        String normalized = command.strip();
        if (normalized.startsWith("/")) normalized = normalized.substring(1).stripLeading();
        if (normalized.isBlank()
                || normalized.length() > MAX_COMMAND_LENGTH
                || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0) {
            return null;
        }
        return normalized;
    }

    private void appendLoaded(String command) {
        String normalized = normalize(command);
        if (normalized != null) {
            entries.addLast(normalized);
            trimToBound();
        }
    }

    private void trimToBound() {
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
    }

    private void persistAsync(List<String> snapshot) {
        persistenceExecutor.execute(() -> {
            try {
                persistence.accept(snapshot);
            } catch (RuntimeException exception) {
                // Persistence must never turn a successful action into a failed
                // action or run on the completion callback's thread.
            }
        });
    }
}
