package ca.teamdman.sfm.common.program.signature;

import java.util.Objects;
import java.util.UUID;

/** A client must compile this exact source and compare its resolved manifest before enabling Sign. */
public record ClientManagerSigningAcknowledgement(ClientManagerSigningSnapshot snapshot,
                                                   ProgramSignatureDescriptor descriptor,
                                                   UUID challenge, long expiresAtTick) {
    public ClientManagerSigningAcknowledgement {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(challenge);
        ProgramAttestationCodec.require(expiresAtTick >= 0
                && descriptor.equals(snapshot.descriptor(descriptor.capabilities())));
    }
    public int chargedBytes() { return snapshot.chargedBytes() + descriptor.canonicalBytes().length + 32; }
}
