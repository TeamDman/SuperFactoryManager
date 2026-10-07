package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Exact local consent key. Routing addresses must not use this source-bound identity. */
public record ClientProgramIdentity(
        String sourceSha256,
        String bindingSha256,
        ProgramExecutionSide hostSide,
        ClientProgramWorldIdentity world,
        ResourceLocation dimension,
        BlockPos managerPosition,
        String runtimeRevision,
        Set<ResourceLocation> requestedCapabilities
) {
    public static final String CLIENT_MANAGER_RUNTIME = "sfm:client_manager@1";

    public ClientProgramIdentity {
        Objects.requireNonNull(sourceSha256, "sourceSha256");
        Objects.requireNonNull(bindingSha256, "bindingSha256");
        Objects.requireNonNull(hostSide, "hostSide");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(managerPosition, "managerPosition");
        managerPosition = managerPosition.immutable();
        Objects.requireNonNull(runtimeRevision, "runtimeRevision");
        Objects.requireNonNull(requestedCapabilities, "requestedCapabilities");
        if (!sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-256 source digest");
        }
        if (!bindingSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-256 binding digest");
        }
        if (!runtimeRevision.matches("[a-z0-9_.-]+:[a-z0-9/._-]+@[1-9][0-9]*")) {
            throw new IllegalArgumentException("Expected a versioned runtime identifier");
        }
        requestedCapabilities = Set.copyOf(requestedCapabilities);
        if (!requestedCapabilities.contains(ClientProgramConsentGate.EXECUTE)) {
            throw new IllegalArgumentException("A program must request the execute capability");
        }
    }

    /** Hash the exact UTF-8 source acknowledged as stored by the world; do not hash pretty-printed AST text. */
    public static ClientProgramIdentity fromStoredSource(
            String storedSource,
            ProgramExecutionSide hostSide,
            ClientProgramWorldIdentity world,
            ResourceLocation dimension,
            BlockPos managerPosition,
            String runtimeRevision,
            Collection<ResourceLocation> requestedCapabilities
    ) {
        return fromStoredSourceAndBindings(storedSource, "", hostSide, world, dimension,
                managerPosition, runtimeRevision, requestedCapabilities);
    }

    /** Canonical bindings are scope, not source; changing a label target must renew consent. */
    public static ClientProgramIdentity fromStoredSourceAndBindings(
            String storedSource,
            String canonicalBindings,
            ProgramExecutionSide hostSide,
            ClientProgramWorldIdentity world,
            ResourceLocation dimension,
            BlockPos managerPosition,
            String runtimeRevision,
            Collection<ResourceLocation> requestedCapabilities
    ) {
        Objects.requireNonNull(storedSource, "storedSource");
        Objects.requireNonNull(canonicalBindings, "canonicalBindings");
        Objects.requireNonNull(requestedCapabilities, "requestedCapabilities");
        return new ClientProgramIdentity(
                digest(storedSource), digest(canonicalBindings), hostSide, world, dimension, managerPosition,
                runtimeRevision, Set.copyOf(requestedCapabilities)
        );
    }

    private static String digest(String text) {
        final byte[] bytes;
        try {
            bytes = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM does not provide SHA-256", e);
        }
        return HexFormat.of().formatHex(bytes);
    }
}
