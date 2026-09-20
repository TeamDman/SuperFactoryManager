package ca.teamdman.sfm.client.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local public signer rules only. Loading a rule does not approve a program or bypass the consent gate. */
public final class ClientProgramSignerTrustStore {
    public static final int MAX_GRANTS = 128;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x53465354; // SFST
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 12;
    private final Map<Scope, Stored> grants = new LinkedHashMap<>();
    private final AtomicReplacement replacement;
    private int encodedBytes = HEADER_BYTES;

    public ClientProgramSignerTrustStore() {
        this((source, destination) -> Files.move(source, destination,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }
    ClientProgramSignerTrustStore(AtomicReplacement replacement) { this.replacement = Objects.requireNonNull(replacement); }

    public record LoadResult(ClientProgramSignerTrustStore store, List<String> diagnostics) {
        public LoadResult { Objects.requireNonNull(store); diagnostics = List.copyOf(diagnostics); }
    }
    private record Scope(String fingerprint, String runtime, ClientProgramWorldIdentity world,
                         ResourceLocation dimension, BlockPos position, String binding) {
        static Scope of(ClientProgramSignerTrustGrant grant) {
            return new Scope(grant.fingerprint(), grant.runtime(), grant.world(), grant.dimension(),
                    grant.managerPosition(), grant.bindingSha256());
        }
    }
    private record Stored(ClientProgramSignerTrustGrant grant, int bytes) {}

    /** Replaces the same signer/runtime/world/location/binding scope; capabilities and expiry are explicit new values. */
    public synchronized boolean put(ClientProgramSignerTrustGrant grant) {
        Objects.requireNonNull(grant);
        Scope scope = Scope.of(grant);
        Stored previous = grants.get(scope);
        if (previous != null && previous.grant().equals(grant)) return false;
        int bytes;
        try { bytes = encodeGrant(grant).length; }
        catch (IOException invalid) { throw new IllegalArgumentException("Invalid signer trust rule", invalid); }
        if (previous == null && grants.size() >= MAX_GRANTS) throw new IllegalStateException("Signer trust store is full");
        int nextBytes = encodedBytes - (previous == null ? 0 : previous.bytes()) + bytes;
        if (nextBytes > MAX_FILE_BYTES) throw new IllegalStateException("Signer trust store exceeds its byte budget");
        grants.put(scope, new Stored(grant, bytes)); encodedBytes = nextBytes;
        return true;
    }

    /** Removes this scope even if its capability set or expiry was subsequently replaced. */
    public synchronized boolean remove(ClientProgramSignerTrustGrant grant) {
        Stored removed = grants.remove(Scope.of(Objects.requireNonNull(grant)));
        if (removed == null) return false;
        encodedBytes -= removed.bytes(); return true;
    }
    public synchronized int clear() {
        int removed = grants.size(); grants.clear(); encodedBytes = HEADER_BYTES; return removed;
    }
    public synchronized int revokeAtLocation(ClientProgramWorldIdentity world, ResourceLocation dimension, BlockPos position) {
        Objects.requireNonNull(world); Objects.requireNonNull(dimension); Objects.requireNonNull(position);
        int removed = 0;
        var iterator = grants.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().world().equals(world) && entry.getKey().dimension().equals(dimension)
                    && entry.getKey().position().equals(position)) {
                encodedBytes -= entry.getValue().bytes(); iterator.remove(); removed++;
            }
        }
        return removed;
    }
    public synchronized List<ClientProgramSignerTrustGrant> snapshot() {
        return grants.values().stream().map(Stored::grant).toList();
    }

    /** Atomic replacement only. A failed save leaves the prior file, and must be reported to the user as unsaved. */
    public synchronized void save(Path destination) throws IOException {
        Objects.requireNonNull(destination);
        byte[] bytes = encode();
        Path target = destination.toAbsolutePath().normalize();
        if (target.getFileName() == null || Files.isSymbolicLink(target)) throw new IOException("Invalid signer trust destination");
        Path parent = target.getParent();
        if (parent == null) throw new IOException("Signer trust destination needs a parent");
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".sfm-signer-trust-", ".tmp");
        Throwable failure = null;
        try {
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            replacement.replace(staged, target);
        } catch (IOException | RuntimeException | Error problem) {
            failure = problem; throw problem;
        } finally {
            try { Files.deleteIfExists(staged); }
            catch (IOException cleanup) { if (failure != null) failure.addSuppressed(cleanup); else throw cleanup; }
        }
    }

    /** Corruption discards the whole file. Never consult an older backup that could resurrect a revoked signer. */
    public static LoadResult load(Path path) {
        Objects.requireNonNull(path);
        ClientProgramSignerTrustStore store = new ClientProgramSignerTrustStore();
        try {
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return new LoadResult(store, List.of());
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Trust store is not a regular file");
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("Trust store exceeds its byte budget");
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unknown trust store format");
                int count = boundedCount(in, MAX_GRANTS);
                for (int i = 0; i < count; i++) {
                    ClientProgramSignerTrustGrant grant = readGrant(in);
                    if (store.grants.containsKey(Scope.of(grant))) throw new IOException("Duplicate signer trust scope");
                    store.put(grant);
                }
                if (in.read() != -1) throw new IOException("Trailing trust store data");
            }
            return new LoadResult(store, List.of());
        } catch (IOException | RuntimeException invalid) {
            return new LoadResult(new ClientProgramSignerTrustStore(),
                    List.of("Signer trust storage could not be read; no saved signer rules were loaded"));
        }
    }

    private byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(encodedBytes);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(grants.size());
            for (Stored value : grants.values()) out.write(encodeGrant(value.grant()));
        }
        if (bytes.size() != encodedBytes || bytes.size() > MAX_FILE_BYTES) throw new IOException("Invalid signer trust size");
        return bytes.toByteArray();
    }
    private static byte[] encodeGrant(ClientProgramSignerTrustGrant grant) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            text(out, grant.fingerprint(), 128); text(out, grant.runtime(), 128);
            text(out, grant.world().serverEndpoint(), 1024);
            out.writeLong(grant.world().worldId().getMostSignificantBits());
            out.writeLong(grant.world().worldId().getLeastSignificantBits());
            text(out, grant.dimension().toString(), 256);
            out.writeInt(grant.managerPosition().getX()); out.writeInt(grant.managerPosition().getY()); out.writeInt(grant.managerPosition().getZ());
            text(out, grant.bindingSha256(), 64);
            var capabilities = grant.allowedCapabilities().stream().map(ResourceLocation::toString).sorted().toList();
            out.writeInt(capabilities.size());
            for (String capability : capabilities) text(out, capability, 256);
            out.writeLong(grant.expiresAt() == null ? -1 : grant.expiresAt());
        }
        return bytes.toByteArray();
    }
    private static ClientProgramSignerTrustGrant readGrant(DataInputStream in) throws IOException {
        String fingerprint = text(in, 128), runtime = text(in, 128), endpoint = text(in, 1024);
        ClientProgramWorldIdentity world = new ClientProgramWorldIdentity(endpoint, new UUID(in.readLong(), in.readLong()));
        if (!world.serverEndpoint().equals(endpoint)) throw new IOException("Non-canonical server identity");
        ResourceLocation dimension = resource(in);
        BlockPos position = new BlockPos(in.readInt(), in.readInt(), in.readInt());
        String binding = text(in, 64);
        int count = boundedCount(in, ClientProgramConsentStore.MAX_CAPABILITIES);
        Set<ResourceLocation> capabilities = new HashSet<>();
        for (int i = 0; i < count; i++) {
            if (!capabilities.add(resource(in))) throw new IOException("Duplicate signer capability");
        }
        long expiry = in.readLong();
        if (expiry != -1 && expiry <= 0) throw new IOException("Invalid signer expiry");
        return new ClientProgramSignerTrustGrant(fingerprint, runtime, world, dimension, position, binding,
                capabilities, expiry == -1 ? null : expiry);
    }
    private static ResourceLocation resource(DataInputStream in) throws IOException {
        String text = text(in, 256);
        ResourceLocation value = new ResourceLocation(text);
        if (!value.toString().equals(text)) throw new IOException("Non-canonical resource identity");
        return value;
    }
    private static int boundedCount(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IOException("Signer trust collection exceeds its budget");
        return count;
    }
    private static void text(DataOutputStream out, String value, int maximum) throws IOException {
        if (value.length() > maximum) throw new IOException("Signer trust text exceeds its byte budget");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximum || !value.equals(new String(bytes, StandardCharsets.UTF_8))) throw new IOException("Invalid signer trust text");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String text(DataInputStream in, int maximum) throws IOException {
        int length = boundedCount(in, maximum);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Truncated signer trust text");
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) throw new IOException("Malformed signer trust UTF-8");
        return value;
    }
    @FunctionalInterface interface AtomicReplacement { void replace(Path staged, Path destination) throws IOException; }
}
