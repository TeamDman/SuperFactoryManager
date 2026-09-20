package ca.teamdman.sfm.client.program;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.EXECUTE;
import static ca.teamdman.sfm.client.program.ClientProgramConsentGate.RENDER;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgramSignerTrustStoreTests {
    @TempDir Path directory;
    private static final ClientProgramWorldIdentity WORLD = ClientProgramWorldIdentity.integrated(
            UUID.fromString("09f58c60-f733-40a3-823f-e793b697f140"));
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private static final BlockPos POSITION = new BlockPos(1, 64, -7);
    private static final String ALICE = "ed25519:sha256:" + "a".repeat(64);
    private static final String BOB = "ed25519:sha256:" + "b".repeat(64);

    @Test void explicitRoundTripPreservesAllScopeFieldsWithoutImplicitCreationOrApproval() throws IOException {
        Path file = directory.resolve("trust.bin");
        var missing = ClientProgramSignerTrustStore.load(file);
        assertTrue(missing.diagnostics().isEmpty());
        assertTrue(missing.store().snapshot().isEmpty());
        assertFalse(Files.exists(file));
        var store = missing.store();
        var grant = grant(ALICE, POSITION, Set.of(EXECUTE, RENDER), 10000L);
        assertTrue(store.put(grant));
        assertFalse(store.put(grant));
        store.save(file);
        var loaded = ClientProgramSignerTrustStore.load(file);
        assertTrue(loaded.diagnostics().isEmpty());
        assertEquals(List.of(grant), loaded.store().snapshot());
        assertThrows(UnsupportedOperationException.class, () -> loaded.store().snapshot().clear());
        assertThrows(UnsupportedOperationException.class, () -> loaded.store().snapshot().get(0).allowedCapabilities().add(new ResourceLocation("sfm", "other")));
        // The persistence model does not own or mutate a consent gate.
        assertTrue(new ClientProgramConsentGate().store().snapshots().isEmpty());
    }

    @Test void sameScopeReplacesCapabilitiesAndExpiryWhileOtherScopesRemainIndependent() {
        var store = new ClientProgramSignerTrustStore();
        var original = grant(ALICE, POSITION, Set.of(EXECUTE, RENDER), null);
        store.put(original);
        var narrowed = grant(ALICE, POSITION, Set.of(EXECUTE), 5000L);
        assertTrue(store.put(narrowed));
        assertEquals(List.of(narrowed), store.snapshot());
        store.put(grant(BOB, POSITION, Set.of(EXECUTE), null));
        store.put(grant(ALICE, POSITION.east(), Set.of(EXECUTE), null));
        store.put(new ClientProgramSignerTrustGrant(ALICE, original.runtime(), original.world(), original.dimension(),
                POSITION, "1".repeat(64), Set.of(EXECUTE), null));
        store.put(new ClientProgramSignerTrustGrant(ALICE, "sfm:client_manager@2", original.world(), original.dimension(),
                POSITION, original.bindingSha256(), Set.of(EXECUTE), null));
        store.put(new ClientProgramSignerTrustGrant(ALICE, original.runtime(),
                ClientProgramWorldIdentity.integrated(UUID.randomUUID()), original.dimension(), POSITION,
                original.bindingSha256(), Set.of(EXECUTE), null));
        store.put(new ClientProgramSignerTrustGrant(ALICE, original.runtime(), original.world(), new ResourceLocation("minecraft", "the_nether"),
                POSITION, original.bindingSha256(), Set.of(EXECUTE), null));
        assertEquals(7, store.snapshot().size());
        assertTrue(store.remove(original), "An old value still names the replaced exact scope");
        assertFalse(store.remove(original));
        assertEquals(6, store.snapshot().size());
    }

    @Test void locationRevocationAndClearSurviveRestartWithoutConsultingOldBackup() throws IOException {
        Path file = directory.resolve("trust.bin");
        var store = new ClientProgramSignerTrustStore();
        store.put(grant(ALICE, POSITION, Set.of(EXECUTE, RENDER), null));
        store.put(grant(BOB, POSITION, Set.of(EXECUTE), null));
        store.put(grant(ALICE, POSITION.east(), Set.of(EXECUTE), null));
        store.save(file);
        Files.copy(file, directory.resolve("trust.bin.bak"));
        assertEquals(2, store.revokeAtLocation(WORLD, DIMENSION, POSITION));
        store.save(file);
        assertEquals(store.snapshot(), ClientProgramSignerTrustStore.load(file).store().snapshot());
        assertEquals(1, store.clear());
        assertEquals(0, store.clear());
        store.save(file);
        assertTrue(ClientProgramSignerTrustStore.load(file).store().snapshot().isEmpty());
        Files.write(file, new byte[]{1});
        assertRejected(file);
        assertTrue(Files.size(directory.resolve("trust.bin.bak")) > 12);
    }

    @Test void duplicateScopesCapabilitiesTrailingAndTruncatedFilesRejectWholeStore() throws IOException {
        Path file = directory.resolve("trust.bin");
        var grant = grant(ALICE, POSITION, Set.of(EXECUTE, RENDER), null);
        var store = new ClientProgramSignerTrustStore(); store.put(grant); store.save(file);
        byte[] valid = Files.readAllBytes(file);
        byte[] doubled = new byte[12 + 2 * (valid.length - 12)];
        System.arraycopy(valid, 0, doubled, 0, valid.length);
        System.arraycopy(valid, 12, doubled, valid.length, valid.length - 12);
        ByteBuffer.wrap(doubled).putInt(8, 2);
        for (byte[] invalid : List.of(doubled, fixture(grant, List.of(EXECUTE, EXECUTE)),
                Arrays.copyOf(valid, valid.length + 1), Arrays.copyOf(valid, valid.length - 1),
                Arrays.copyOf(valid, 11), new byte[0])) {
            Files.write(file, invalid); assertRejected(file);
        }
    }

    @Test void invalidFormatCountsUtf8ExpiryAndNonCanonicalResourceRejectWholeStore() throws IOException {
        Path file = directory.resolve("trust.bin");
        var grant = grant(ALICE, POSITION, Set.of(EXECUTE), 5L);
        byte[] valid = fixture(grant, List.of(EXECUTE));
        List<byte[]> invalid = new ArrayList<>();
        byte[] version = valid.clone(); ByteBuffer.wrap(version).putInt(4, 99); invalid.add(version);
        byte[] count = valid.clone(); ByteBuffer.wrap(count).putInt(8, ClientProgramSignerTrustStore.MAX_GRANTS + 1); invalid.add(count);
        byte[] length = valid.clone(); ByteBuffer.wrap(length).putInt(12, Integer.MAX_VALUE); invalid.add(length);
        byte[] negativeLength = valid.clone(); ByteBuffer.wrap(negativeLength).putInt(12, -1); invalid.add(negativeLength);
        byte[] utf8 = valid.clone(); utf8[16] = (byte) 0x80; invalid.add(utf8);
        byte[] expiry = valid.clone(); ByteBuffer.wrap(expiry).putLong(expiry.length - 8, 0); invalid.add(expiry);
        byte[] emptyCapabilities = fixture(grant, List.of()); invalid.add(emptyCapabilities);
        byte[] nonCanonical = fixture(grant, List.of(EXECUTE));
        int dimensionStart = find(nonCanonical, "minecraft:overworld".getBytes(StandardCharsets.UTF_8));
        nonCanonical[dimensionStart] = 'M'; invalid.add(nonCanonical);
        for (byte[] bytes : invalid) { Files.write(file, bytes); assertRejected(file); }
        Files.write(file, new byte[ClientProgramSignerTrustStore.MAX_FILE_BYTES + 1]); assertRejected(file);
    }

    @Test void countAndByteCapsFailWithoutEvictingOrMutatingExistingRules() throws IOException {
        var store = new ClientProgramSignerTrustStore();
        for (int i = 0; i < ClientProgramSignerTrustStore.MAX_GRANTS; i++) store.put(grant(ALICE, POSITION.offset(i, 0, 0), Set.of(EXECUTE), null));
        var before = store.snapshot();
        assertThrows(IllegalStateException.class, () -> store.put(grant(ALICE, POSITION.offset(129, 0, 0), Set.of(EXECUTE), null)));
        assertEquals(before, store.snapshot());
        assertTrue(store.put(grant(ALICE, POSITION, Set.of(EXECUTE, RENDER), 10L)), "An exact-scope update works when count is full");
        assertTrue(store.remove(before.get(0)));
        assertTrue(store.put(grant(ALICE, POSITION.offset(129, 0, 0), Set.of(EXECUTE), null)));

        Set<ResourceLocation> capabilities = new HashSet<>(Set.of(EXECUTE));
        for (int i = 0; i < 63; i++) capabilities.add(new ResourceLocation("sfm", "x".repeat(249) + String.format(Locale.ROOT, "%03d", i)));
        var large = new ClientProgramSignerTrustStore();
        boolean byteLimited = false;
        for (int i = 0; i < ClientProgramSignerTrustStore.MAX_GRANTS; i++) {
            var previous = large.snapshot();
            try { large.put(grant(ALICE, POSITION.offset(i, 0, 0), capabilities, null)); }
            catch (IllegalStateException full) { assertEquals(previous, large.snapshot()); byteLimited = true; break; }
        }
        assertTrue(byteLimited);
        Path file = directory.resolve("bounded.bin"); large.save(file);
        assertTrue(Files.size(file) <= ClientProgramSignerTrustStore.MAX_FILE_BYTES);
        assertEquals(large.snapshot(), ClientProgramSignerTrustStore.load(file).store().snapshot());
    }

    @Test void unavailableAtomicReplacementPreservesOldFileAndCleansOnlyOwnedStaging() throws IOException {
        Path file = directory.resolve("trust.bin");
        Path unrelated = directory.resolve("keep.txt"); Files.writeString(unrelated, "keep");
        var original = new ClientProgramSignerTrustStore(); original.put(grant(ALICE, POSITION, Set.of(EXECUTE), null)); original.save(file);
        byte[] before = Files.readAllBytes(file);
        var failed = new ClientProgramSignerTrustStore((staged, destination) -> {
            throw new AtomicMoveNotSupportedException(staged.toString(), destination.toString(), "fixture");
        });
        assertThrows(IOException.class, () -> failed.save(file));
        assertArrayEquals(before, Files.readAllBytes(file));
        assertEquals("keep", Files.readString(unrelated));
        try (var entries = Files.list(directory)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith(".sfm-signer-trust-")));
        }
    }

    @Test void malformedLocalGrantTextAndNonFileLoadsFailClosed() {
        var store = new ClientProgramSignerTrustStore();
        var tooLongDimension = new ClientProgramSignerTrustGrant(ALICE, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME,
                WORLD, new ResourceLocation("sfm", "x".repeat(257)), POSITION, "0".repeat(64), Set.of(EXECUTE), null);
        assertThrows(IllegalArgumentException.class, () -> store.put(tooLongDimension));
        assertTrue(store.snapshot().isEmpty());
        assertRejected(directory);
    }

    private static ClientProgramSignerTrustGrant grant(String signer, BlockPos position, Set<ResourceLocation> capabilities, Long expiry) {
        return new ClientProgramSignerTrustGrant(signer, ClientProgramIdentity.CLIENT_MANAGER_RUNTIME, WORLD,
                DIMENSION, position, "0".repeat(64), capabilities, expiry);
    }
    private static void assertRejected(Path file) {
        var result = ClientProgramSignerTrustStore.load(file);
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.store().snapshot().isEmpty());
        assertEquals(1, result.diagnostics().size());
    }
    private static byte[] fixture(ClientProgramSignerTrustGrant grant, List<ResourceLocation> capabilities) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0x53465354); out.writeInt(1); out.writeInt(1);
            text(out, grant.fingerprint()); text(out, grant.runtime()); text(out, grant.world().serverEndpoint());
            out.writeLong(grant.world().worldId().getMostSignificantBits()); out.writeLong(grant.world().worldId().getLeastSignificantBits());
            text(out, grant.dimension().toString());
            out.writeInt(grant.managerPosition().getX()); out.writeInt(grant.managerPosition().getY()); out.writeInt(grant.managerPosition().getZ());
            text(out, grant.bindingSha256()); out.writeInt(capabilities.size());
            for (var capability : capabilities) text(out, capability.toString());
            out.writeLong(grant.expiresAt() == null ? -1 : grant.expiresAt());
        }
        return bytes.toByteArray();
    }
    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private static int find(byte[] bytes, byte[] wanted) {
        outer: for (int i = 0; i <= bytes.length - wanted.length; i++) {
            for (int j = 0; j < wanted.length; j++) if (bytes[i + j] != wanted[j]) continue outer;
            return i;
        }
        throw new AssertionError("Fixture marker missing");
    }
}
