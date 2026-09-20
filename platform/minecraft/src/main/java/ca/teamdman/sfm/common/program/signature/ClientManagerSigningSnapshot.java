package ca.teamdman.sfm.common.program.signature;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Server-owned revision evidence. The declared manifest is authorship data, never permission authority. */
public record ClientManagerSigningSnapshot(UUID incarnation, long revision, ClientManagerSigningBody body,
                                           List<ProgramAttestation> history) {
    public static final String RUNTIME = "sfm:client_manager@1";
    public ClientManagerSigningSnapshot {
        Objects.requireNonNull(incarnation);
        Objects.requireNonNull(body);
        ProgramAttestationCodec.require(revision >= 0);
        ProgramAttestationCodec.require(history.size() <= ProgramAttestationHistory.MAX_ATTESTATIONS);
        history = List.copyOf(history);
        ProgramAttestationCodec.encodeHistory(history); // Aggregate count/byte bound, no signature work here.
    }
    public ProgramSignatureDescriptor descriptor(Collection<ResourceLocation> declaredCapabilities) {
        return ProgramSignatureDescriptor.fromSource(body.source(), RUNTIME, declaredCapabilities);
    }
    public int chargedBytes() { return body.chargedBytes() + ProgramAttestationCodec.encodeHistory(history).length + 64; }
}
