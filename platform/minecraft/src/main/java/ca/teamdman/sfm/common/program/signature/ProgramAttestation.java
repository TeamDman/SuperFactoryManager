package ca.teamdman.sfm.common.program.signature;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Public metadata only. Each signer attests the descriptor, never the list of other signatures. */
public record ProgramAttestation(ProgramSignatureDescriptor descriptor, String publicKey, String signature) {
    private static final int MAX_PUBLIC_KEY_BYTES = 128;
    private static final int SIGNATURE_BYTES = 64;

    public ProgramAttestation {
        Objects.requireNonNull(descriptor);
        decodePublicKey(publicKey);
        decodeCanonical(signature, SIGNATURE_BYTES, SIGNATURE_BYTES);
    }

    /** Pure signing primitive; authoring must first obtain explicit consent and a stored-revision acknowledgement. */
    public static ProgramAttestation sign(ProgramSignatureDescriptor descriptor, KeyPair author) {
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(author);
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(author.getPrivate());
            signer.update(descriptor.canonicalBytes());
            ProgramAttestation attestation = new ProgramAttestation(descriptor,
                    Base64.getEncoder().encodeToString(author.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(signer.sign()));
            if (!attestation.verifies(descriptor)) throw new IllegalArgumentException("Author keys do not match");
            return attestation;
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalArgumentException("Unable to sign with the supplied Ed25519 key", unavailable);
        }
    }

    public boolean verifies(ProgramSignatureDescriptor expected) {
        if (!descriptor.equals(expected)) return false;
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(decodePublicKey(publicKey));
            verifier.update(descriptor.canonicalBytes());
            return verifier.verify(decodeCanonical(signature, SIGNATURE_BYTES, SIGNATURE_BYTES));
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            return false;
        }
    }

    public String fingerprint() {
        return "ed25519:sha256:" + ProgramSignatureDescriptor.sha256(
                decodeCanonical(publicKey, 1, MAX_PUBLIC_KEY_BYTES));
    }

    private static PublicKey decodePublicKey(String value) {
        byte[] encoded = decodeCanonical(value, 1, MAX_PUBLIC_KEY_BYTES);
        try {
            PublicKey decoded = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            if (!(decoded instanceof EdECPublicKey edwards) || !edwards.getParams().getName().equals("Ed25519")
                || !Arrays.equals(encoded, decoded.getEncoded())) throw new IllegalArgumentException("Non-canonical Ed25519 public key");
            return decoded;
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("Invalid Ed25519 public key", invalid);
        }
    }

    private static byte[] decodeCanonical(String value, int minBytes, int maxBytes) {
        Objects.requireNonNull(value);
        if (value.length() > ((maxBytes + 2) / 3) * 4) throw new IllegalArgumentException("Encoded signature data is too large");
        byte[] bytes = Base64.getDecoder().decode(value);
        if (bytes.length < minBytes || bytes.length > maxBytes
            || !Base64.getEncoder().encodeToString(bytes).equals(value)) {
            throw new IllegalArgumentException("Invalid canonical signature encoding");
        }
        return bytes;
    }
}
