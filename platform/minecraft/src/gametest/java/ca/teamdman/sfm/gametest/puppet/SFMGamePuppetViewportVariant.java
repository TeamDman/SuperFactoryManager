package ca.teamdman.sfm.gametest.puppet;

public record SFMGamePuppetViewportVariant(int width, int height, int guiScale) {
    public SFMGamePuppetViewportVariant {
        if (width < 320 || height < 320) throw new IllegalArgumentException("Viewport must be at least 320x320");
        if (guiScale < 0) throw new IllegalArgumentException("GUI scale must be Auto (0) or positive");
    }

    public String id() {
        return width + "x" + height + "@" + (guiScale == 0 ? "auto" : guiScale);
    }

    public String requestedScaleName() {
        return guiScale == 0 ? "auto" : Integer.toString(guiScale);
    }
}
