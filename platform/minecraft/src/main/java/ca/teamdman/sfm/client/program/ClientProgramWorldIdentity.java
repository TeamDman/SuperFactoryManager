package ca.teamdman.sfm.client.program;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** The world boundary of a local program grant, independent of the player's current session. */
public record ClientProgramWorldIdentity(String serverEndpoint, UUID worldId) {
    public static final int MAX_ENDPOINT_LENGTH = 255;

    public ClientProgramWorldIdentity {
        Objects.requireNonNull(serverEndpoint, "serverEndpoint");
        Objects.requireNonNull(worldId, "worldId");
        serverEndpoint = serverEndpoint.trim().toLowerCase(Locale.ROOT);
        if (serverEndpoint.isEmpty() || serverEndpoint.length() > MAX_ENDPOINT_LENGTH
            || serverEndpoint.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid server endpoint identity");
        }
    }

    public static ClientProgramWorldIdentity integrated(UUID persistedWorldId) {
        return new ClientProgramWorldIdentity("integrated", persistedWorldId);
    }
}
