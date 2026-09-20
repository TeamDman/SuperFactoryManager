package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/** Injectable resolver-I/O evidence used by tests, diagnostics, and puppets. */
public final class SFMExplorerIoCounter {
    public static final int DEFAULT_EVENT_CAPACITY = 4096;

    public enum OperationKind {
        METADATA_READ("metadata-read"),
        DIRECTORY_ENUMERATION("directory-enumeration"),
        ENTRY_OBSERVED("entry-observed"),
        CONTAINMENT_REJECTION("containment-rejection");

        private final String id;

        OperationKind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    public record Event(
            long sequence,
            OperationKind operation,
            Optional<String> canonicalPath,
            String workerThread
    ) {
        public Event {
            if (sequence <= 0) throw new IllegalArgumentException("Event sequence must be positive");
            Objects.requireNonNull(operation, "operation");
            canonicalPath = Objects.requireNonNull(canonicalPath, "canonicalPath");
            canonicalPath = canonicalPath.map(value -> {
                if (value.isBlank()) throw new IllegalArgumentException("Canonical event path cannot be blank");
                return value;
            });
            workerThread = Objects.requireNonNull(workerThread, "workerThread");
        }
    }

    public record Snapshot(
            long metadataReads,
            long directoryEnumerations,
            long entriesObserved,
            long containmentRejections,
            long renderThreadViolations,
            List<String> observedThreads,
            int eventCapacity,
            long eventsDropped,
            long latestSequence,
            List<Event> events
    ) {
        public Snapshot {
            observedThreads = List.copyOf(observedThreads);
            events = List.copyOf(events);
        }

        public List<Event> eventsAfter(long exclusiveSequence) {
            return events.stream().filter(event -> event.sequence() > exclusiveSequence).toList();
        }
    }

    private final int eventCapacity;
    private final ArrayDeque<Event> events;
    private final TreeSet<String> observedThreads = new TreeSet<>();
    private long metadataReads;
    private long directoryEnumerations;
    private long entriesObserved;
    private long containmentRejections;
    private long renderThreadViolations;
    private long eventsDropped;
    private long latestSequence;

    public SFMExplorerIoCounter() {
        this(DEFAULT_EVENT_CAPACITY);
    }

    public SFMExplorerIoCounter(int eventCapacity) {
        if (eventCapacity <= 0) throw new IllegalArgumentException("Event capacity must be positive");
        this.eventCapacity = eventCapacity;
        events = new ArrayDeque<>(eventCapacity);
    }

    public synchronized void metadataRead(SFMPath path) {
        metadataReads++;
        record(OperationKind.METADATA_READ, path);
    }

    public synchronized void directoryEnumerated(SFMPath path) {
        directoryEnumerations++;
        record(OperationKind.DIRECTORY_ENUMERATION, path);
    }

    public synchronized void entryObserved(SFMPath path) {
        entriesObserved++;
        record(OperationKind.ENTRY_OBSERVED, path);
    }

    public synchronized void containmentRejected(SFMPath path) {
        containmentRejections++;
        record(OperationKind.CONTAINMENT_REJECTION, path);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                metadataReads,
                directoryEnumerations,
                entriesObserved,
                containmentRejections,
                renderThreadViolations,
                List.copyOf(observedThreads),
                eventCapacity,
                eventsDropped,
                latestSequence,
                new ArrayList<>(events)
        );
    }

    private void record(OperationKind operation, SFMPath path) {
        Objects.requireNonNull(path, "path");
        String name = Thread.currentThread().getName();
        observedThreads.add(name);
        if (name.equals("Render thread") || name.equals("Client thread")) {
            renderThreadViolations++;
        }
        Event event = new Event(
                ++latestSequence,
                operation,
                Optional.of(path.canonical()),
                name
        );
        if (events.size() == eventCapacity) {
            events.removeFirst();
            eventsDropped++;
        }
        events.addLast(event);
    }
}
