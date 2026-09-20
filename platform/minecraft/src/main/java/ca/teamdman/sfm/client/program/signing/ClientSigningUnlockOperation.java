package ca.teamdman.sfm.client.program.signing;

import ca.teamdman.sfm.common.program.signature.ProgramAttestation;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/** Consumes a UI-only secret capture; the controller owns this operation after Sign is pressed. */
public final class ClientSigningUnlockOperation implements ClientProgramSigningController.UnlockOperation {
    private final Path file;
    private char[] passphrase;
    public ClientSigningUnlockOperation(Path file, char[] passphrase) {
        this.file = Objects.requireNonNull(file);
        this.passphrase = Objects.requireNonNull(passphrase);
    }
    @Override public synchronized ClientProgramSigningController.Signer unlock() throws Exception {
        if (passphrase == null) throw new IllegalStateException("Passphrase already consumed");
        char[] captured = passphrase;
        passphrase = null;
        ClientSigningKeyStore.UnlockedSigner signer = new ClientSigningKeyStore(file).unlock(captured);
        return new ClientProgramSigningController.Signer() {
            public ProgramAttestation sign(ProgramSignatureDescriptor descriptor) throws Exception { return signer.sign(descriptor); }
            public void close() { signer.close(); }
        };
    }
    @Override public synchronized void close() {
        if (passphrase != null) Arrays.fill(passphrase, '\0');
        passphrase = null;
    }
}
