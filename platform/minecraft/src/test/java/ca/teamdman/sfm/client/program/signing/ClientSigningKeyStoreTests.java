package ca.teamdman.sfm.client.program.signing;

import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ClientSigningKeyStoreTests {
    @TempDir Path directory;

    private static char[] passphrase() { return "synthetic test passphrase only".toCharArray(); }
    private static ProgramSignatureDescriptor descriptor() {
        return ProgramSignatureDescriptor.fromSource("CLIENT BTW\n", "sfm:client_manager@1",
                List.of(new ResourceLocation("sfm:client_program/execute")));
    }
    private static void assertCleared(char[] value) { assertArrayEquals(new char[value.length], value); }
    private void assertNoStagingFiles() throws IOException {
        try (var children = Files.list(directory)) {
            assertTrue(children.noneMatch(file -> file.getFileName().toString().startsWith(".sfm-signing-")));
        }
    }

    @Test
    void explicitCreateReloadAndCloseableSignerKeepIdentityWithoutExposingPrivateKey() throws Exception {
        Path file = directory.resolve("author.key");
        var store = new ClientSigningKeyStore(file);
        assertTrue(store.inspect().isEmpty());
        assertFalse(Files.exists(file));
        char[] createPassword = passphrase();
        var identity = store.create(createPassword);
        assertCleared(createPassword);
        assertEquals(identity, new ClientSigningKeyStore(file).inspect().orElseThrow());
        assertTrue(identity.fingerprint().startsWith("ed25519:sha256:"));
        char[] unlockPassword = passphrase();
        var signer = new ClientSigningKeyStore(file).unlock(unlockPassword);
        assertCleared(unlockPassword);
        var signed = signer.sign(descriptor());
        assertTrue(signed.verifies(descriptor()));
        assertEquals(identity.fingerprint(), signer.fingerprint());
        assertEquals(identity.fingerprint(), signed.fingerprint());
        assertEquals(identity.publicKey(), signed.publicKey());
        signer.close();
        signer.close();
        assertThrows(IllegalStateException.class, () -> signer.sign(descriptor()));
        assertNoStagingFiles();
    }

    @Test
    void existingOrCorruptKeyIsNeverSilentlyReplacedOrRegenerated() throws Exception {
        Path file = directory.resolve("author.key");
        var store = new ClientSigningKeyStore(file);
        store.create(passphrase());
        byte[] original = Files.readAllBytes(file);
        char[] password = passphrase();
        assertThrows(FileAlreadyExistsException.class, () -> store.create(password));
        assertCleared(password);
        assertArrayEquals(original, Files.readAllBytes(file));
        Files.writeString(file, "corrupt fixture, not a key");
        byte[] corrupt = Files.readAllBytes(file);
        assertThrows(IOException.class, store::inspect);
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> store.unlock(passphrase()));
        assertThrows(FileAlreadyExistsException.class, () -> store.create(passphrase()));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
        assertNoStagingFiles();
    }

    @Test
    void wrongPasswordAndAuthenticatedHeaderOrCiphertextTamperingFailUniformly() throws Exception {
        Path file = directory.resolve("author.key");
        var store = new ClientSigningKeyStore(file);
        store.create(passphrase());
        byte[] original = Files.readAllBytes(file);
        char[] wrong = "another synthetic passphrase".toCharArray();
        var error = assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> store.unlock(wrong));
        assertEquals("Unable to unlock signing key", error.getMessage());
        assertNull(error.getCause());
        assertCleared(wrong);
        assertArrayEquals(original, Files.readAllBytes(file));
        int publicLength = Short.toUnsignedInt(ByteBuffer.wrap(original).getShort(15));
        for (int offset : new int[]{17 + publicLength, 17 + publicLength + 16, original.length - 1}) {
            byte[] altered = original.clone();
            altered[offset] ^= 1;
            Files.write(file, altered);
            // Salt/nonce/ciphertext corruption leaves the locked public fingerprint readable,
            // but that display is explicitly not proof of successful key authentication.
            assertTrue(store.inspect().isPresent());
            char[] password = passphrase();
            var failure = assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> store.unlock(password));
            assertEquals(error.getMessage(), failure.getMessage());
            assertNull(failure.getCause());
            assertCleared(password);
            assertArrayEquals(altered, Files.readAllBytes(file));
        }
    }

    @Test
    void malformedLengthsAlgorithmsAndWorkFactorsAreRejectedBeforePasswordDerivation() throws Exception {
        Path file = directory.resolve("author.key");
        new ClientSigningKeyStore(file).create(passphrase());
        byte[] valid = Files.readAllBytes(file);
        AtomicInteger derivations = new AtomicInteger();
        var guarded = new ClientSigningKeyStore(file, new SecureRandom(), Files::createLink,
                (password, salt, iterations) -> { derivations.incrementAndGet(); throw new AssertionError("KDF reached"); });
        byte[] version = valid.clone();
        ByteBuffer.wrap(version).putInt(4, 2);
        byte[] iterations = valid.clone();
        ByteBuffer.wrap(iterations).putInt(11, Integer.MAX_VALUE);
        byte[] algorithm = valid.clone();
        algorithm[8] = 2;
        byte[] publicLength = valid.clone();
        ByteBuffer.wrap(publicLength).putShort(15, (short) 32767);
        byte[] ciphertextLength = valid.clone();
        int headerEnd = 49 + Short.toUnsignedInt(ByteBuffer.wrap(valid).getShort(15));
        ByteBuffer.wrap(ciphertextLength).putInt(headerEnd - 4, Integer.MAX_VALUE);
        for (byte[] malformed : List.of(version, iterations, algorithm, publicLength, ciphertextLength,
                Arrays.copyOf(valid, valid.length + 1), Arrays.copyOf(valid, valid.length - 1),
                new byte[ClientSigningKeyStore.MAX_FILE_BYTES + 1], new byte[0])) {
            Files.write(file, malformed);
            assertThrows(IOException.class, guarded::inspect);
            char[] password = passphrase();
            assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> guarded.unlock(password));
            assertCleared(password);
        }
        assertEquals(0, derivations.get());
    }

    @Test
    void freshKeysUseDistinctIdentitySaltAndNonceAndBindPublicKeyToCiphertext() throws Exception {
        Path first = directory.resolve("first.key");
        Path second = directory.resolve("second.key");
        var firstStore = new ClientSigningKeyStore(first);
        var secondStore = new ClientSigningKeyStore(second);
        assertNotEquals(firstStore.create(passphrase()), secondStore.create(passphrase()));
        byte[] a = Files.readAllBytes(first);
        byte[] b = Files.readAllBytes(second);
        int publicLength = Short.toUnsignedInt(ByteBuffer.wrap(a).getShort(15));
        assertEquals(publicLength, Short.toUnsignedInt(ByteBuffer.wrap(b).getShort(15)));
        int saltStart = 17 + publicLength;
        assertFalse(Arrays.equals(Arrays.copyOfRange(a, saltStart, saltStart + 16),
                Arrays.copyOfRange(b, saltStart, saltStart + 16)));
        assertFalse(Arrays.equals(Arrays.copyOfRange(a, saltStart + 16, saltStart + 28),
                Arrays.copyOfRange(b, saltStart + 16, saltStart + 28)));
        System.arraycopy(b, 17, a, 17, publicLength);
        Files.write(first, a);
        assertEquals(secondStore.inspect(), firstStore.inspect());
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> firstStore.unlock(passphrase()));
    }

    @Test
    void protectedBackupAndRestoreAuthenticateExactBytesAndPreserveIdentity() throws Exception {
        Path source = directory.resolve("source.key");
        Path backup = directory.resolve("backup.key");
        Path restored = directory.resolve("restored.key");
        var original = new ClientSigningKeyStore(source);
        var identity = original.create(passphrase());
        char[] backupPassword = passphrase();
        assertEquals(identity, original.backupTo(backup, backupPassword));
        assertCleared(backupPassword);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(backup));
        var restoredStore = new ClientSigningKeyStore(restored);
        char[] restorePassword = passphrase();
        assertEquals(identity, restoredStore.restoreFrom(backup, restorePassword));
        assertCleared(restorePassword);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(restored));
        try (var signer = restoredStore.unlock(passphrase())) {
            assertTrue(signer.sign(descriptor()).verifies(descriptor()));
            assertEquals(identity.fingerprint(), signer.fingerprint());
        }
        assertThrows(FileAlreadyExistsException.class, () -> restoredStore.restoreFrom(backup, passphrase()));
        assertThrows(FileAlreadyExistsException.class, () -> original.backupTo(backup, passphrase()));
        Path failed = directory.resolve("failed.key");
        char[] wrong = "incorrect synthetic password".toCharArray();
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class,
                () -> new ClientSigningKeyStore(failed).restoreFrom(backup, wrong));
        assertCleared(wrong);
        assertFalse(Files.exists(failed));
        assertNoStagingFiles();
    }

    @Test
    void unsupportedPublicationFailsClosedAndOnlyOwnedStagingFileIsCleaned() throws Exception {
        Path destination = directory.resolve("author.key");
        Path unrelated = directory.resolve("keep.txt");
        Files.writeString(unrelated, "unrelated fixture");
        var store = new ClientSigningKeyStore(destination, new SecureRandom(),
                (target, staged) -> { throw new IOException("fixture hard links unsupported"); },
                ClientSigningKeyStore::deriveKey);
        char[] password = passphrase();
        assertThrows(IOException.class, () -> store.create(password));
        assertCleared(password);
        assertFalse(Files.exists(destination));
        assertEquals("unrelated fixture", Files.readString(unrelated));
        assertNoStagingFiles();
    }

    @Test
    void concurrentDestinationCreationCannotBeClobberedByAtomicPublication() throws Exception {
        Path destination = directory.resolve("author.key");
        var store = new ClientSigningKeyStore(destination, new SecureRandom(), (target, staged) -> {
            Files.writeString(target, "racing creator owns this file");
            Files.createLink(target, staged);
        }, ClientSigningKeyStore::deriveKey);
        char[] password = passphrase();
        assertThrows(FileAlreadyExistsException.class, () -> store.create(password));
        assertCleared(password);
        assertEquals("racing creator owns this file", Files.readString(destination));
        assertNoStagingFiles();
    }

    @Test
    void publishedKeySurvivesStagingCleanupFailureAndReportsItsPublishedState() throws Exception {
        Path destination = directory.resolve("author.key");
        var store = new ClientSigningKeyStore(destination, new SecureRandom(), (target, staged) -> {
            Files.createLink(target, staged);
            Files.delete(staged); // Force the production cleanup branch to observe an owned-file cleanup error.
        }, ClientSigningKeyStore::deriveKey);
        char[] password = passphrase();
        var failure = assertThrows(ClientSigningKeyStore.PublicationException.class, () -> store.create(password));
        assertTrue(failure.published());
        assertCleared(password);
        try (var signer = new ClientSigningKeyStore(destination).unlock(passphrase())) {
            assertTrue(signer.sign(descriptor()).verifies(descriptor()));
        }
        assertThrows(FileAlreadyExistsException.class, () -> store.create(passphrase()));
        assertNoStagingFiles();
    }

    @Test
    void corruptProtectedBackupCannotBeRestoredOrCopiedToANewDestination() throws Exception {
        Path backup = directory.resolve("backup.key");
        Path restored = directory.resolve("restored.key");
        Path copied = directory.resolve("copied.key");
        var backupStore = new ClientSigningKeyStore(backup);
        backupStore.create(passphrase());
        byte[] corrupt = Files.readAllBytes(backup);
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(backup, corrupt);
        char[] password = passphrase();
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class,
                () -> new ClientSigningKeyStore(restored).restoreFrom(backup, password));
        assertCleared(password);
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class,
                () -> backupStore.backupTo(copied, passphrase()));
        assertFalse(Files.exists(restored));
        assertFalse(Files.exists(copied));
        assertArrayEquals(corrupt, Files.readAllBytes(backup));
        assertNoStagingFiles();
    }

    @Test
    void unicodePassphraseRoundTripsWithoutImplicitNormalization() throws Exception {
        Path file = directory.resolve("unicode.key");
        var store = new ClientSigningKeyStore(file);
        // Synthetic passphrase: precomposed e-acute, CJK and a supplementary-code-point pair.
        String synthetic = "test-\u00e9-\u65e5\u672c-\ud83d\udd11-only";
        char[] createPassword = synthetic.toCharArray();
        var identity = store.create(createPassword);
        assertCleared(createPassword);
        char[] unlockPassword = synthetic.toCharArray();
        try (var signer = new ClientSigningKeyStore(file).unlock(unlockPassword)) {
            assertEquals(identity.fingerprint(), signer.sign(descriptor()).fingerprint());
        }
        assertCleared(unlockPassword);
        char[] visuallySimilar = synthetic.replace("\u00e9", "e\u0301").toCharArray();
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> store.unlock(visuallySimilar));
        assertCleared(visuallySimilar);
    }

    @Test
    void passphraseBoundsMissingKeysAndDirectoriesFailWithoutWritingOrDeriving() throws Exception {
        Path destination = directory.resolve("missing-parent").resolve("author.key");
        AtomicInteger derivations = new AtomicInteger();
        var store = new ClientSigningKeyStore(destination, new SecureRandom(), Files::createLink,
                (password, salt, iterations) -> { derivations.incrementAndGet(); throw new AssertionError("KDF reached"); });
        for (char[] password : List.of(new char[12], "short".toCharArray(), new char[1025])) {
            assertThrows(IllegalArgumentException.class, () -> store.create(password));
            assertCleared(password);
        }
        char[] password = passphrase();
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> store.unlock(password));
        assertCleared(password);
        assertFalse(Files.exists(destination.getParent()));
        var directoryStore = new ClientSigningKeyStore(directory);
        assertThrows(IOException.class, directoryStore::inspect);
        assertThrows(FileAlreadyExistsException.class, () -> directoryStore.create(passphrase()));
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> directoryStore.unlock(passphrase()));
        assertEquals(0, derivations.get());
    }

    @Test
    void finalComponentSymlinkIsNeverReadOrReplaced() throws Exception {
        Path target = directory.resolve("target.key");
        Path link = directory.resolve("linked.key");
        Files.writeString(target, "preserve this target");
        boolean supported;
        try {
            Files.createSymbolicLink(link, target);
            supported = true;
        } catch (IOException | UnsupportedOperationException | SecurityException unavailable) {
            supported = false;
        }
        assumeTrue(supported, "Filesystem or current account does not permit symlink fixtures");
        var store = new ClientSigningKeyStore(link);
        assertThrows(IOException.class, store::inspect);
        assertThrows(ClientSigningKeyStore.KeyUnlockException.class, () -> store.unlock(passphrase()));
        assertThrows(FileAlreadyExistsException.class, () -> store.create(passphrase()));
        assertTrue(Files.isSymbolicLink(link));
        assertEquals("preserve this target", Files.readString(target));
    }
}
