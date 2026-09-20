package ca.teamdman.sfm.common.net.multiplayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerPacketProtocol.*;

/** Server-owned exact ACL. Mutation is for operator configuration, never for wire requests. */
public final class SFMMultiplayerPacketPolicy {
    public static final int MAX_GRANTS = 4_096;
    private final Map<UUID, Grant> grants = new LinkedHashMap<>();

    public record Grant(
            UUID id, UUID player, Action action, Scope scope,
            Optional<ProgramClaim> program, OptionalLong expiresAtEpochMillis
    ) {
        public Grant {
            Objects.requireNonNull(id);
            Objects.requireNonNull(player);
            requireScope(action, scope);
            program = Objects.requireNonNull(program);
            expiresAtEpochMillis = Objects.requireNonNull(expiresAtEpochMillis);
            if (expiresAtEpochMillis.isPresent() && expiresAtEpochMillis.getAsLong() < 0) {
                throw new IllegalArgumentException("Negative grant expiry");
            }
            if (action == Action.INBOX_DELIVER && program.isPresent()) {
                throw new IllegalArgumentException("Delivery names a trusted publisher, not a client program");
            }
        }
    }

    public synchronized void grant(Grant grant) {
        Objects.requireNonNull(grant);
        if (!grants.containsKey(grant.id()) && grants.size() >= MAX_GRANTS) {
            throw new IllegalStateException("Multiplayer packet grant capacity reached");
        }
        grants.put(grant.id(), grant);
    }

    public synchronized boolean revoke(UUID id) { return grants.remove(Objects.requireNonNull(id)) != null; }

    public synchronized List<Grant> snapshot() { return List.copyOf(grants.values()); }

    public synchronized boolean allows(
            UUID player, Action action, Scope scope, Optional<ProgramClaim> program, long epochMillis
    ) {
        requireScope(action, scope);
        Objects.requireNonNull(player);
        Objects.requireNonNull(program);
        if (epochMillis < 0) return false;
        return grants.values().stream().anyMatch(grant -> grant.player().equals(player)
                && grant.action() == action && grant.scope().equals(scope)
                && (grant.program().isEmpty() || grant.program().equals(program))
                && (grant.expiresAtEpochMillis().isEmpty()
                    || epochMillis < grant.expiresAtEpochMillis().getAsLong()));
    }
}
