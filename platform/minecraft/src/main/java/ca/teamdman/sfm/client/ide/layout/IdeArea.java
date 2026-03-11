package ca.teamdman.sfm.client.ide.layout;

public record IdeArea(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
}
