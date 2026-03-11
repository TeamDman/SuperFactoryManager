package ca.teamdman.sfm.client.ide.layout;

public record IdeDockPiece<T>(T target, IdeDockDirection direction, int size) {
}
