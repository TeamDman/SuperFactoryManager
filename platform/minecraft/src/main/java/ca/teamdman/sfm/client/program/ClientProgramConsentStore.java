package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded local consent history. Corrupt storage never restores an older grant. */
public final class ClientProgramConsentStore {
    public static final int MAX_PROGRAMS = 128;
    public static final int MAX_SOURCE_BYTES = 64 * 1024;
    public static final int MAX_CAPABILITIES = 64;
    public static final int MAX_HISTORY = 32;
    public static final int MAX_FILE_BYTES = 12 * 1024 * 1024;
    private static final int MAGIC = 0x53464353;
    private static final int VERSION = 2;

    public record Evidence(String source, String bindings, Map<String, String> versions, long observedAt) {
        public Evidence {
            bounded(source, MAX_SOURCE_BYTES);
            bounded(bindings, 16 * 1024);
            if (versions.size() > 16 || observedAt < 0) throw new IllegalArgumentException("Invalid consent evidence");
            versions.forEach((key, value) -> { bounded(key, 128); bounded(value, 256); });
            versions = Map.copyOf(versions);
        }
    }

    public record DecisionEntry(ClientProgramConsentGate.ConsentState state, long decidedAt,
                                @Nullable Long expiresAt, @Nullable Long retryAfter) {
        public DecisionEntry {
            Objects.requireNonNull(state);
            if (decidedAt < 0 || expiresAt != null && expiresAt <= decidedAt
                || retryAfter != null && retryAfter <= decidedAt
                || state != ClientProgramConsentGate.ConsentState.APPROVED && expiresAt != null
                || state != ClientProgramConsentGate.ConsentState.DENIED && retryAfter != null) {
                throw new IllegalArgumentException("Invalid consent decision lifetime");
            }
        }

        public ClientProgramConsentGate.ConsentState stateAt(long now) {
            if (expiresAt != null && now >= expiresAt || retryAfter != null && now >= retryAfter) {
                return ClientProgramConsentGate.ConsentState.ABSENT;
            }
            return state;
        }
    }

    public record Change(ResourceLocation capability, DecisionEntry decision, Map<String, String> versionContext) {
        public Change { versionContext = Map.copyOf(versionContext); }
    }

    public record Snapshot(ClientProgramIdentity identity, Optional<Evidence> evidence,
                           Map<ResourceLocation, DecisionEntry> decisions, List<Change> history) {
        public Snapshot {
            evidence = Objects.requireNonNull(evidence);
            decisions = Map.copyOf(decisions);
            history = List.copyOf(history);
        }
    }

    public record LoadResult(ClientProgramConsentStore store, List<String> diagnostics) {
        public LoadResult { diagnostics = List.copyOf(diagnostics); }
    }

    private static final class Entry {
        @Nullable Evidence evidence;
        final Map<ResourceLocation, DecisionEntry> decisions = new LinkedHashMap<>();
        final ArrayDeque<Change> history = new ArrayDeque<>();
    }

    private final Map<ClientProgramIdentity, Entry> entries = new LinkedHashMap<>();
    private final LongSupplier clock;
    private boolean stoppedAll;

    public ClientProgramConsentStore() { this(System::currentTimeMillis); }
    public ClientProgramConsentStore(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    public synchronized boolean stoppedAll() { return stoppedAll; }

    /** A durable policy switch. Resuming does not resurrect grants revoked by stopping. */
    public synchronized void setStoppedAll(boolean stopped) {
        if (stopped) revokeAll();
        stoppedAll = stopped;
    }

    /** Register only client-observed source and bindings; hashes are recomputed before accepting evidence. */
    public synchronized void observe(ClientProgramIdentity identity, String source, String bindings,
                                     Map<String, String> versions) {
        Evidence evidence = new Evidence(source, bindings, versions, now());
        ClientProgramIdentity verified = ClientProgramIdentity.fromStoredSourceAndBindings(
                source, bindings, identity.hostSide(), identity.world(), identity.dimension(),
                identity.managerPosition(), identity.runtimeRevision(), identity.requestedCapabilities());
        if (!identity.equals(verified)) throw new IllegalArgumentException("Consent source or bindings do not match identity");
        entry(identity).evidence = evidence;
    }

    public synchronized ClientProgramConsentGate.ConsentState state(ClientProgramIdentity identity,
                                                                    ResourceLocation capability) {
        checkCapability(identity, capability);
        Entry entry = entries.get(identity);
        DecisionEntry decision = entry == null ? null : entry.decisions.get(capability);
        return decision == null ? ClientProgramConsentGate.ConsentState.ABSENT : decision.stateAt(now());
    }

    /** Even an expired or revoked exact decision must not silently fall back to signer authority. */
    public synchronized boolean hasRecordedDecision(ClientProgramIdentity identity, ResourceLocation capability) {
        checkCapability(identity, capability);
        Entry entry = entries.get(identity);
        return entry != null && entry.decisions.containsKey(capability);
    }

    public synchronized ClientProgramConsentGate.RequestResult request(ClientProgramIdentity identity,
                                                                      ResourceLocation capability) {
        var state = state(identity, capability);
        if (state != ClientProgramConsentGate.ConsentState.ABSENT) {
            return new ClientProgramConsentGate.RequestResult(state, false);
        }
        change(identity, capability, new DecisionEntry(ClientProgramConsentGate.ConsentState.PENDING, now(), null, null));
        return new ClientProgramConsentGate.RequestResult(ClientProgramConsentGate.ConsentState.PENDING, true);
    }

    /** Must be called from an explicit user review gesture, never from program execution. */
    public synchronized ClientProgramConsentGate.RequestResult reopenDenied(ClientProgramIdentity identity,
                                                                           ResourceLocation capability) {
        if (state(identity, capability) != ClientProgramConsentGate.ConsentState.DENIED) return request(identity, capability);
        change(identity, capability, new DecisionEntry(ClientProgramConsentGate.ConsentState.PENDING, now(), null, null));
        return new ClientProgramConsentGate.RequestResult(ClientProgramConsentGate.ConsentState.PENDING, true);
    }

    public synchronized void decide(ClientProgramIdentity identity, ResourceLocation capability,
                                    ClientProgramConsentGate.Decision decision,
                                    @Nullable Long expiresAt, @Nullable Long retryAfter) {
        Objects.requireNonNull(decision);
        if (state(identity, capability) != ClientProgramConsentGate.ConsentState.PENDING) {
            throw new IllegalStateException("Consent is not pending");
        }
        var next = decision == ClientProgramConsentGate.Decision.APPROVE
                ? ClientProgramConsentGate.ConsentState.APPROVED : ClientProgramConsentGate.ConsentState.DENIED;
        change(identity, capability, new DecisionEntry(next, now(), expiresAt, retryAfter));
    }

    public synchronized void revoke(ClientProgramIdentity identity, ResourceLocation capability) {
        checkCapability(identity, capability);
        if (!entries.containsKey(identity)) return;
        change(identity, capability, new DecisionEntry(ClientProgramConsentGate.ConsentState.ABSENT, now(), null, null));
    }

    public synchronized void revokeAll() {
        for (var record : entries.entrySet()) {
            for (ResourceLocation capability : record.getKey().requestedCapabilities()) {
                revoke(record.getKey(), capability);
            }
        }
    }

    /** Explicit review-panel removal also revokes every grant for this exact identity. */
    public synchronized boolean forget(ClientProgramIdentity identity) {
        return entries.remove(Objects.requireNonNull(identity)) != null;
    }

    public synchronized List<Snapshot> snapshots() {
        return entries.entrySet().stream().map(e -> new Snapshot(e.getKey(), Optional.ofNullable(e.getValue().evidence),
                e.getValue().decisions, List.copyOf(e.getValue().history))).toList();
    }

    public synchronized List<Snapshot> atLocation(ClientProgramIdentity current) {
        return snapshots().stream().filter(s -> s.identity().world().equals(current.world())
                && s.identity().dimension().equals(current.dimension())
                && s.identity().managerPosition().equals(current.managerPosition())).toList();
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) throw new IllegalStateException("Consent clock is before the epoch");
        return value;
    }

    private Entry entry(ClientProgramIdentity identity) {
        Objects.requireNonNull(identity);
        if (identity.requestedCapabilities().size() > MAX_CAPABILITIES) throw new IllegalArgumentException("Too many capabilities");
        Entry entry = entries.get(identity);
        if (entry != null) return entry;
        if (entries.size() >= MAX_PROGRAMS) throw new IllegalStateException("Consent store is full; review stored programs");
        entry = new Entry();
        entries.put(identity, entry);
        return entry;
    }

    private static void checkCapability(ClientProgramIdentity identity, ResourceLocation capability) {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(capability);
        if (!identity.requestedCapabilities().contains(capability)) {
            throw new IllegalArgumentException("Capability was not declared by this program: " + capability);
        }
    }

    private void change(ClientProgramIdentity identity, ResourceLocation capability, DecisionEntry decision) {
        checkCapability(identity, capability);
        Entry entry = entry(identity);
        entry.decisions.put(capability, decision);
        if (entry.history.size() == MAX_HISTORY) entry.history.removeFirst();
        entry.history.addLast(new Change(capability, decision,
                entry.evidence == null ? Map.of() : entry.evidence.versions()));
    }

    /** Write one atomic replacement. No last-valid fallback: that could resurrect a revoked approval. */
    public synchronized void save(Path destination) throws IOException {
        save(destination, Set.copyOf(entries.keySet()));
    }

    /** Persist only service-owned durable records, never incidental test/observer decisions. */
    public synchronized void save(Path destination, Set<ClientProgramIdentity> durableIdentities) throws IOException {
        Objects.requireNonNull(durableIdentities);
        var selected = entries.entrySet().stream().filter(e -> durableIdentities.contains(e.getKey())).toList();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeBoolean(stoppedAll);
            out.writeInt(selected.size());
            for (var item : selected) {
                writeEntry(out, item.getKey(), item.getValue());
                if (bytes.size() > MAX_FILE_BYTES) throw new IOException("Consent store exceeds its byte budget");
            }
        }
        if (bytes.size() > MAX_FILE_BYTES) throw new IOException("Consent store exceeds its byte budget");
        Path absolute = destination.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "consents-", ".tmp");
        try {
            Files.write(temporary, bytes.toByteArray());
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                // Failing preserves the old file and makes the UI report that this decision was not saved.
                throw new IOException("Atomic consent replacement is unavailable", unavailable);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static LoadResult load(Path path, LongSupplier clock) {
        ClientProgramConsentStore store = new ClientProgramConsentStore(clock);
        // Only proven absence is a fresh store. Unknown/access-denied must become diagnosed load failure.
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return new LoadResult(store, List.of());
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Consent store exceeds its byte budget");
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readInt() != MAGIC) throw new IOException("Unknown consent store format");
                int version = in.readInt();
                if (version < 1 || version > VERSION) throw new IOException("Unknown consent store format");
                store.stoppedAll = version >= 2 && in.readBoolean();
                int count = count(in, MAX_PROGRAMS);
                for (int i = 0; i < count; i++) store.readEntry(in);
                if (in.read() != -1) throw new IOException("Trailing consent store data");
            }
            return new LoadResult(store, List.of());
        } catch (IOException | RuntimeException invalid) {
            return new LoadResult(new ClientProgramConsentStore(clock),
                    List.of("Consent storage could not be read; no saved approvals were loaded"));
        }
    }

    private static void writeEntry(DataOutputStream out, ClientProgramIdentity id, Entry entry) throws IOException {
        Evidence evidence = entry.evidence;
        if (evidence == null) throw new IOException("Cannot persist consent without its source and binding evidence");
        text(out, evidence.source(), MAX_SOURCE_BYTES);
        text(out, evidence.bindings(), 16 * 1024);
        text(out, id.sourceSha256(), 64);
        text(out, id.bindingSha256(), 64);
        text(out, id.hostSide().name(), 16);
        text(out, id.world().serverEndpoint(), 1024);
        text(out, id.world().worldId().toString(), 36);
        text(out, id.dimension().toString(), 256);
        out.writeInt(id.managerPosition().getX());
        out.writeInt(id.managerPosition().getY());
        out.writeInt(id.managerPosition().getZ());
        text(out, id.runtimeRevision(), 256);
        List<String> capabilities = id.requestedCapabilities().stream().map(Object::toString).sorted().toList();
        out.writeInt(capabilities.size());
        for (String capability : capabilities) text(out, capability, 256);
        out.writeLong(evidence.observedAt());
        out.writeInt(evidence.versions().size());
        for (var version : new TreeMap<>(evidence.versions()).entrySet()) {
            text(out, version.getKey(), 128);
            text(out, version.getValue(), 256);
        }
        out.writeInt(entry.decisions.size());
        for (var decision : entry.decisions.entrySet()) writeDecision(out, decision.getKey(), decision.getValue());
        out.writeInt(entry.history.size());
        for (Change change : entry.history) {
            writeDecision(out, change.capability(), change.decision());
            out.writeInt(change.versionContext().size());
            for (var version : new TreeMap<>(change.versionContext()).entrySet()) {
                text(out, version.getKey(), 128);
                text(out, version.getValue(), 256);
            }
        }
    }

    private void readEntry(DataInputStream in) throws IOException {
        String source = text(in, MAX_SOURCE_BYTES);
        String bindings = text(in, 16 * 1024);
        String sourceHash = text(in, 64);
        String bindingHash = text(in, 64);
        ProgramExecutionSide side = ProgramExecutionSide.valueOf(text(in, 16));
        ClientProgramWorldIdentity world = new ClientProgramWorldIdentity(text(in, 1024), UUID.fromString(text(in, 36)));
        ResourceLocation dimension = new ResourceLocation(text(in, 256));
        BlockPos position = new BlockPos(in.readInt(), in.readInt(), in.readInt());
        String runtime = text(in, 256);
        Set<ResourceLocation> capabilities = new HashSet<>();
        int capabilityCount = count(in, MAX_CAPABILITIES);
        for (int i = 0; i < capabilityCount; i++) {
            if (!capabilities.add(new ResourceLocation(text(in, 256)))) throw new IOException("Duplicate capability");
        }
        ClientProgramIdentity id = new ClientProgramIdentity(sourceHash, bindingHash, side, world, dimension, position, runtime, capabilities);
        if (entries.containsKey(id)) throw new IOException("Duplicate consent identity");
        long observedAt = in.readLong();
        Map<String, String> versions = new LinkedHashMap<>();
        int versionCount = count(in, 16);
        for (int i = 0; i < versionCount; i++) {
            if (versions.put(text(in, 128), text(in, 256)) != null) throw new IOException("Duplicate version context");
        }
        observe(id, source, bindings, versions);
        Entry entry = entries.get(id);
        entry.evidence = new Evidence(source, bindings, versions, observedAt);
        int decisionCount = count(in, MAX_CAPABILITIES);
        for (int i = 0; i < decisionCount; i++) {
            Change change = readDecision(in, id);
            if (entry.decisions.put(change.capability(), change.decision()) != null) throw new IOException("Duplicate decision");
        }
        int historyCount = count(in, MAX_HISTORY);
        for (int i = 0; i < historyCount; i++) {
            Change change = readDecision(in, id);
            Map<String, String> context = new LinkedHashMap<>();
            int contextCount = count(in, 16);
            for (int j = 0; j < contextCount; j++) {
                if (context.put(text(in, 128), text(in, 256)) != null) throw new IOException("Duplicate historical version context");
            }
            entry.history.addLast(new Change(change.capability(), change.decision(), context));
        }
    }

    private static void writeDecision(DataOutputStream out, ResourceLocation capability, DecisionEntry decision) throws IOException {
        text(out, capability.toString(), 256);
        text(out, decision.state().name(), 16);
        out.writeLong(decision.decidedAt());
        out.writeLong(decision.expiresAt() == null ? -1 : decision.expiresAt());
        out.writeLong(decision.retryAfter() == null ? -1 : decision.retryAfter());
    }

    private static Change readDecision(DataInputStream in, ClientProgramIdentity id) throws IOException {
        ResourceLocation capability = new ResourceLocation(text(in, 256));
        checkCapability(id, capability);
        var state = ClientProgramConsentGate.ConsentState.valueOf(text(in, 16));
        long time = in.readLong(), expiry = in.readLong(), retry = in.readLong();
        if (expiry < -1 || retry < -1) throw new IOException("Invalid consent lifetime");
        return new Change(capability, new DecisionEntry(state, time, expiry == -1 ? null : expiry, retry == -1 ? null : retry), Map.of());
    }

    private static int count(DataInputStream in, int limit) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > limit) throw new IOException("Consent collection exceeds its budget");
        return count;
    }

    private static void bounded(String value, int limit) {
        Objects.requireNonNull(value);
        if (value.length() > limit || value.getBytes(StandardCharsets.UTF_8).length > limit) {
            throw new IllegalArgumentException("Consent text exceeds its byte budget");
        }
        if (!value.equals(new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Consent text contains malformed Unicode");
        }
    }

    private static void text(DataOutputStream out, String text, int limit) throws IOException {
        bounded(text, limit);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String text(DataInputStream in, int limit) throws IOException {
        int length = count(in, limit);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8))) throw new IOException("Invalid UTF-8 consent text");
        return text;
    }
}
