package ca.teamdman.sfm.client.program.signing;

import ca.teamdman.sfm.common.program.signature.ProgramAttestation;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Explicit, client-owned signing-key storage. No constructor/read operation generates a key.
 * Version 1 protects Ed25519 PKCS#8 with AES-256-GCM and PBKDF2-HMAC-SHA256 (600,000 iterations).
 * Every passphrase-taking method consumes and clears the supplied array, even on failure.
 * Clearing is best effort: JCA/JVM internals can retain copies, and this is not a hardware keystore.
 *
 * Publication links a forced, encrypted sibling staging file to an absent destination. This requires
 * hard-link support and fails closed without it. It guarantees no-clobber visibility, not portable
 * directory-fsync/power-loss durability. Directory ancestors and local filesystem administrators are
 * trusted; final-component symlinks are rejected. Windows files inherit the containing directory ACL.
 * Keep that directory private. Rotation means explicitly creating a NEW file/identity, never replacing
 * this one. The UI, revision acknowledgement and user signing confirmation are separate boundaries.
 */
public final class ClientSigningKeyStore {
    static final int MAX_FILE_BYTES = 16 * 1024;
    static final int ITERATIONS = 600_000;
    static final int MIN_PASSPHRASE_CHARS = 12;
    static final int MAX_PASSPHRASE_CHARS = 1024;
    private static final int MAGIC = 0x53464B53; // SFKS
    private static final int VERSION = 1;
    private static final int MAX_PUBLIC_BYTES = 128;
    private static final int MAX_PRIVATE_BYTES = 256;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final byte[] SELF_CHECK = "SFM signing-key self-check@1".getBytes(StandardCharsets.US_ASCII);

    private final Path path;
    private final SecureRandom random;
    private final Publisher publisher;
    private final KeyDeriver keyDeriver;

    public ClientSigningKeyStore(Path path) {
        this(path, new SecureRandom(), Files::createLink, ClientSigningKeyStore::deriveKey);
    }

    // Package-local fault-injection seams are not available through the client action registry.
    ClientSigningKeyStore(Path path, SecureRandom random, Publisher publisher, KeyDeriver keyDeriver) {
        this.path = Objects.requireNonNull(path).toAbsolutePath().normalize();
        if (this.path.getFileName() == null) throw new IllegalArgumentException("A key file is required");
        this.random = Objects.requireNonNull(random);
        this.publisher = Objects.requireNonNull(publisher);
        this.keyDeriver = Objects.requireNonNull(keyDeriver);
    }

    /** Public metadata is only an unverified claim until the encrypted envelope is unlocked. */
    public Optional<LockedIdentity> inspect() throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        return Optional.of(readEnvelope(path).identity());
    }

    public LockedIdentity create(char[] passphrase) throws IOException, GeneralSecurityException {
        PrivateKey privateKey = null;
        byte[] encodedPrivate = null;
        try {
            validatePassphrase(passphrase);
            requireAbsent(path);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            generator.initialize(NamedParameterSpec.ED25519, random);
            KeyPair pair = generator.generateKeyPair();
            privateKey = pair.getPrivate();
            encodedPrivate = privateKey.getEncoded();
            if (encodedPrivate == null || encodedPrivate.length == 0 || encodedPrivate.length > MAX_PRIVATE_BYTES) {
                throw new GeneralSecurityException("Unsupported private key encoding");
            }
            byte[] envelope = encrypt(pair.getPublic(), encodedPrivate, passphrase);
            LockedIdentity identity = parseEnvelope(envelope).identity();
            publish(path, envelope);
            return identity;
        } finally {
            clear(passphrase);
            clear(encodedPrivate);
            destroy(privateKey);
        }
    }

    /** Wrong passphrases, corrupt files and unsupported files have the same non-secret failure. */
    public UnlockedSigner unlock(char[] passphrase) throws KeyUnlockException {
        try {
            validatePassphrase(passphrase);
            return decrypt(readEnvelope(path), passphrase);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException invalid) {
            throw new KeyUnlockException();
        } finally {
            clear(passphrase);
        }
    }

    /** Authenticate once, then copy those exact encrypted bytes to a NEW destination. */
    public LockedIdentity backupTo(Path destination, char[] passphrase) throws IOException, KeyUnlockException {
        return protectedCopy(path, destination, passphrase);
    }

    /** Restore only into an absent store. Existing keys, including corrupt ones, are never replaced. */
    public LockedIdentity restoreFrom(Path backup, char[] passphrase) throws IOException, KeyUnlockException {
        return protectedCopy(backup, path, passphrase);
    }

    private LockedIdentity protectedCopy(Path source, Path destination, char[] passphrase)
            throws IOException, KeyUnlockException {
        try {
            Path target = Objects.requireNonNull(destination).toAbsolutePath().normalize();
            requireAbsent(target);
            Envelope envelope;
            try {
                validatePassphrase(passphrase);
                envelope = readEnvelope(source);
                try (UnlockedSigner ignored = decrypt(envelope, passphrase)) {
                    // Authentication and key-pair validation precede publication; no second source read.
                }
            } catch (IOException | GeneralSecurityException | IllegalArgumentException invalid) {
                throw new KeyUnlockException();
            }
            publish(target, envelope.encoded());
            return envelope.identity();
        } finally {
            clear(passphrase);
        }
    }

    private byte[] encrypt(PublicKey publicKey, byte[] encodedPrivate, char[] passphrase)
            throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(salt);
        random.nextBytes(nonce);
        byte[] publicBytes = publicKey.getEncoded();
        if (publicBytes.length == 0 || publicBytes.length > MAX_PUBLIC_BYTES) {
            throw new GeneralSecurityException("Unsupported public key encoding");
        }
        ByteBuffer header = ByteBuffer.allocate(49 + publicBytes.length);
        header.putInt(MAGIC).putInt(VERSION).put((byte) 1).put((byte) 1).put((byte) 1)
                .putInt(ITERATIONS).putShort((short) publicBytes.length).put(publicBytes)
                .put(salt).put(nonce).putInt(encodedPrivate.length + TAG_BYTES);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, encodedPrivate, header.array(), salt, nonce, passphrase);
        return ByteBuffer.allocate(header.capacity() + ciphertext.length).put(header.array()).put(ciphertext).array();
    }

    private UnlockedSigner decrypt(Envelope envelope, char[] passphrase) throws GeneralSecurityException {
        byte[] privateBytes = crypt(Cipher.DECRYPT_MODE, envelope.ciphertext(), envelope.header(),
                envelope.salt(), envelope.nonce(), passphrase);
        PrivateKey privateKey = null;
        try {
            privateKey = decodePrivate(privateBytes);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(SELF_CHECK);
            byte[] proof = signature.sign();
            signature.initVerify(envelope.publicKey());
            signature.update(SELF_CHECK);
            if (!signature.verify(proof)) throw new GeneralSecurityException("Key pair mismatch");
            UnlockedSigner result = new UnlockedSigner(envelope.publicKey(), envelope.identity().fingerprint(), privateBytes);
            privateBytes = null; // Ownership moves to the closeable handle, never a caller-visible key getter.
            return result;
        } finally {
            clear(privateBytes);
            destroy(privateKey);
        }
    }

    private byte[] crypt(int mode, byte[] input, byte[] header, byte[] salt, byte[] nonce, char[] passphrase)
            throws GeneralSecurityException {
        byte[] derived = null;
        SecretKeySpec key = null;
        try {
            derived = keyDeriver.derive(passphrase, salt, ITERATIONS);
            if (derived == null || derived.length != 32) throw new GeneralSecurityException("Invalid derived key");
            key = new SecretKeySpec(derived, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(header);
            return cipher.doFinal(input);
        } finally {
            clear(derived);
            destroy(key);
        }
    }

    static byte[] deriveKey(char[] passphrase, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, 256);
        javax.crypto.SecretKey derived = null;
        try {
            derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec);
            return derived.getEncoded();
        } finally {
            spec.clearPassword();
            destroy(derived);
        }
    }

    private static Envelope readEnvelope(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Signing key is not a regular file");
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            return parseEnvelope(input.readNBytes(MAX_FILE_BYTES + 1));
        }
    }

    /** All untrusted size, algorithm and work-factor checks happen before any password derivation. */
    private static Envelope parseEnvelope(byte[] encoded) throws IOException {
        try {
            if (encoded.length < 49 + 1 + TAG_BYTES + 1 || encoded.length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Invalid key file size");
            }
            ByteBuffer data = ByteBuffer.wrap(encoded);
            if (data.getInt() != MAGIC || data.getInt() != VERSION
                || data.get() != 1 || data.get() != 1 || data.get() != 1 || data.getInt() != ITERATIONS) {
                throw new IllegalArgumentException("Unsupported key file format");
            }
            int publicSize = Short.toUnsignedInt(data.getShort());
            if (publicSize == 0 || publicSize > MAX_PUBLIC_BYTES || data.remaining() < publicSize + 32) {
                throw new IllegalArgumentException("Invalid public key size");
            }
            byte[] publicBytes = new byte[publicSize];
            data.get(publicBytes);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicBytes));
            if (!(publicKey instanceof EdECPublicKey edwards) || !edwards.getParams().getName().equals("Ed25519")
                || !Arrays.equals(publicBytes, publicKey.getEncoded())) throw new IllegalArgumentException("Invalid public key");
            byte[] salt = new byte[SALT_BYTES];
            byte[] nonce = new byte[NONCE_BYTES];
            data.get(salt).get(nonce);
            int ciphertextSize = data.getInt();
            if (ciphertextSize <= TAG_BYTES || ciphertextSize > MAX_PRIVATE_BYTES + TAG_BYTES
                || ciphertextSize != data.remaining()) throw new IllegalArgumentException("Invalid encrypted key size");
            byte[] header = Arrays.copyOf(encoded, data.position());
            byte[] ciphertext = new byte[ciphertextSize];
            data.get(ciphertext);
            LockedIdentity identity = new LockedIdentity(Base64.getEncoder().encodeToString(publicBytes),
                    "ed25519:sha256:" + ProgramSignatureDescriptor.sha256(publicBytes));
            return new Envelope(encoded, header, publicKey, salt, nonce, ciphertext, identity);
        } catch (GeneralSecurityException | IllegalArgumentException | java.nio.BufferUnderflowException invalid) {
            throw new IOException("Invalid or unsupported signing key file");
        }
    }

    private static PrivateKey decodePrivate(byte[] bytes) throws GeneralSecurityException {
        if (bytes.length == 0 || bytes.length > MAX_PRIVATE_BYTES) throw new GeneralSecurityException("Invalid private key size");
        PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        byte[] canonical = key.getEncoded();
        try {
            if (!(key instanceof EdECPrivateKey edwards) || !edwards.getParams().getName().equals("Ed25519")
                || !Arrays.equals(bytes, canonical)) {
                destroy(key);
                throw new GeneralSecurityException("Invalid private key");
            }
            return key;
        } finally {
            clear(canonical);
        }
    }

    private void publish(Path destination, byte[] protectedBytes) throws IOException {
        requireAbsent(destination);
        Path parent = destination.getParent();
        if (parent == null || destination.getFileName() == null) throw new IOException("A key file is required");
        Files.createDirectories(parent);
        FileAttribute<?>[] attributes = Files.getFileStore(parent).supportsFileAttributeView("posix")
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))} : new FileAttribute<?>[0];
        Path staged = Files.createTempFile(parent, ".sfm-signing-", ".enc.tmp", attributes);
        boolean published = false;
        Throwable failure = null;
        try {
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(protectedBytes);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            publisher.publish(destination, staged);
            published = true;
        } catch (IOException | RuntimeException | Error problem) {
            failure = problem;
            throw problem;
        } finally {
            try {
                Files.delete(staged); // Only the staging file created by this invocation is removed.
            } catch (IOException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw new PublicationException(published);
            }
        }
    }

    private static void requireAbsent(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
    }

    private static void validatePassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length < MIN_PASSPHRASE_CHARS || passphrase.length > MAX_PASSPHRASE_CHARS) {
            throw new IllegalArgumentException("Passphrase must contain 12 to 1024 characters");
        }
        boolean nonzero = false;
        for (char value : passphrase) nonzero |= value != 0;
        if (!nonzero) throw new IllegalArgumentException("Passphrase has already been cleared");
    }

    private static void clear(char[] value) { if (value != null) Arrays.fill(value, '\0'); }
    private static void clear(byte[] value) { if (value != null) Arrays.fill(value, (byte) 0); }
    private static void destroy(Destroyable value) {
        if (value != null) {
            try { value.destroy(); } catch (DestroyFailedException ignored) { /* Best effort provider support. */ }
        }
    }

    public record LockedIdentity(String publicKey, String fingerprint) { }

    public static final class KeyUnlockException extends GeneralSecurityException {
        private KeyUnlockException() { super("Unable to unlock signing key"); }
    }

    /** A cleanup error after publication does not mean callers may regenerate or overwrite the file. */
    public static final class PublicationException extends IOException {
        private final boolean published;
        private PublicationException(boolean published) {
            super(published ? "Signing key was published, but encrypted staging cleanup failed"
                    : "Signing key publication and encrypted staging cleanup failed");
            this.published = published;
        }
        public boolean published() { return published; }
    }

    /** Sole unlocked operation is descriptor signing. Callers never receive private key objects or bytes. */
    public static final class UnlockedSigner implements AutoCloseable {
        private final PublicKey publicKey;
        private final String fingerprint;
        private byte[] privateBytes;
        private UnlockedSigner(PublicKey publicKey, String fingerprint, byte[] privateBytes) {
            this.publicKey = publicKey;
            this.fingerprint = fingerprint;
            this.privateBytes = privateBytes;
        }
        public String fingerprint() { return fingerprint; }
        public synchronized ProgramAttestation sign(ProgramSignatureDescriptor descriptor) throws GeneralSecurityException {
            if (privateBytes == null) throw new IllegalStateException("Signing key is locked");
            PrivateKey key = decodePrivate(privateBytes);
            try {
                return ProgramAttestation.sign(Objects.requireNonNull(descriptor), new KeyPair(publicKey, key));
            } finally {
                destroy(key);
            }
        }
        @Override public synchronized void close() {
            clear(privateBytes);
            privateBytes = null;
        }
    }

    @FunctionalInterface interface Publisher { void publish(Path destination, Path staged) throws IOException; }
    @FunctionalInterface interface KeyDeriver {
        byte[] derive(char[] passphrase, byte[] salt, int iterations) throws GeneralSecurityException;
    }
    private record Envelope(byte[] encoded, byte[] header, PublicKey publicKey, byte[] salt, byte[] nonce,
                            byte[] ciphertext, LockedIdentity identity) { }
}
