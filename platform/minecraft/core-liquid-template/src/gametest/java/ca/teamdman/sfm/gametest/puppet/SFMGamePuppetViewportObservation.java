package ca.teamdman.sfm.gametest.puppet;

public record SFMGamePuppetViewportObservation(
        int windowWidth,
        int windowHeight,
        int framebufferWidth,
        int framebufferHeight,
        int effectiveGuiScale,
        int logicalWidth,
        int logicalHeight
) {
}
