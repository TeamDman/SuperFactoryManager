package ca.teamdman.sfm.common.tutorial.lobby;

public record LobbyId(int value) {
    public LobbyId {
        if (value <= 0) {
            throw new IllegalArgumentException("Lobby id must be greater than zero, got " + value);
        }
    }

    public static LobbyId fromInt(int value) {
        return new LobbyId(value);
    }
}