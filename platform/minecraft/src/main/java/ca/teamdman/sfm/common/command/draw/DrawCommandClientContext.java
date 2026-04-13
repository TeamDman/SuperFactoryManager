package ca.teamdman.sfm.common.command.draw;

public record DrawCommandClientContext(
        double cameraX,
        double cameraY,
        double zoom,
        double mouseCanvasX,
        double mouseCanvasY,
        double mouseScreenX,
        double mouseScreenY
) {
}