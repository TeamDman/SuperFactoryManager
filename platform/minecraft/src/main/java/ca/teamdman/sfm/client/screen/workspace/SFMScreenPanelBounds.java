package ca.teamdman.sfm.client.screen.workspace;

public record SFMScreenPanelBounds(int x, int y, int width, int height) {
    public SFMScreenPanelBounds inset(int pixels) {
        if (pixels < 0) throw new IllegalArgumentException("Inset must be non-negative");
        int horizontal = Math.min(width, pixels * 2);
        int vertical = Math.min(height, pixels * 2);
        return new SFMScreenPanelBounds(
                x + Math.min(pixels, width),
                y + Math.min(pixels, height),
                width - horizontal,
                height - vertical
        );
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
