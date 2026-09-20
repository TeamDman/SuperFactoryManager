package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.common.program.signature.ProgramAttestation;
import ca.teamdman.sfm.common.program.signature.ProgramAttestationHistory;
import ca.teamdman.sfm.common.program.signature.ProgramSignatureDescriptor;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** A local, exact-location signer rule; matching never bypasses policy or an explicit user denial/revocation. */
public record ClientProgramSignerTrustGrant(String fingerprint, String runtime,
                                           ClientProgramWorldIdentity world, ResourceLocation dimension,
                                           BlockPos managerPosition, String bindingSha256,
                                           Set<ResourceLocation> allowedCapabilities,
                                           @Nullable Long expiresAt) {
    public ClientProgramSignerTrustGrant {
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(world);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(managerPosition);
        Objects.requireNonNull(bindingSha256);
        managerPosition = managerPosition.immutable();
        allowedCapabilities = Set.copyOf(allowedCapabilities);
        if (!fingerprint.matches("ed25519:sha256:[0-9a-f]{64}")
            || !bindingSha256.matches("[0-9a-f]{64}") || expiresAt != null && expiresAt <= 0) {
            throw new IllegalArgumentException("Invalid signer trust scope");
        }
        // Reuse descriptor validation for runtime and resolved, non-wildcard capability IDs.
        new ProgramSignatureDescriptor("0".repeat(64), runtime, java.util.List.copyOf(allowedCapabilities));
    }

    /** Reports a matching trust rule only. The gate must still apply current policy and exact-decision precedence. */
    public boolean matches(ClientProgramIdentity identity, String exactStoredSource, ResourceLocation capability,
                           Collection<ProgramAttestation> attestations, long now) {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(exactStoredSource);
        Objects.requireNonNull(capability);
        Objects.requireNonNull(attestations);
        if (now < 0 || expiresAt != null && now >= expiresAt
            || identity.hostSide() != ProgramExecutionSide.CLIENT
            || !runtime.equals(identity.runtimeRevision()) || !world.equals(identity.world())
            || !dimension.equals(identity.dimension()) || !managerPosition.equals(identity.managerPosition())
            || !bindingSha256.equals(identity.bindingSha256())
            || !identity.requestedCapabilities().contains(capability)
            || !allowedCapabilities.containsAll(identity.requestedCapabilities())
            || attestations.size() > ProgramAttestationHistory.MAX_ATTESTATIONS) return false;
        final ProgramSignatureDescriptor expected;
        try {
            expected = ProgramSignatureDescriptor.fromSource(exactStoredSource, runtime, identity.requestedCapabilities());
        } catch (IllegalArgumentException invalidSource) {
            return false;
        }
        // Consent hashes exact source, while author signatures normalize only line endings.
        if (!identity.sourceSha256().equals(ProgramSignatureDescriptor.sha256(
                exactStoredSource.getBytes(StandardCharsets.UTF_8)))) return false;
        return attestations.stream().filter(Objects::nonNull)
                .anyMatch(attestation -> fingerprint.equals(attestation.fingerprint()) && attestation.verifies(expected));
    }
}
