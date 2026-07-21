package ca.teamdman.sfm.client.screen.workspace;

public record SFMScreenPanelBounds(int x, int y, int width, int height) {
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
