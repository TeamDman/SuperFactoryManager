package com.bbscn;

import com.bbscn.ScreenRectangle.ScreenAxis;
import com.bbscn.ScreenRectangle.ScreenDirection;

public class ScreenPosition {
    private final int x;
    private final int y;

    public ScreenPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static ScreenPosition of(ScreenAxis axis, int primary, int secondary) {
        if (axis == ScreenAxis.HORIZONTAL) {
            return new ScreenPosition(primary, secondary);
        } else {
            return new ScreenPosition(secondary, primary);
        }
    }

    public ScreenPosition step(ScreenDirection dir) {
        switch (dir) {
            case DOWN:
                return new ScreenPosition(this.x, this.y + 1);
            case UP:
                return new ScreenPosition(this.x, this.y - 1);
            case LEFT:
                return new ScreenPosition(this.x - 1, this.y);
            case RIGHT:
                return new ScreenPosition(this.x + 1, this.y);
            default:
                return this;
        }
    }

    public int getCoordinate(ScreenAxis axis) {
        return axis == ScreenAxis.HORIZONTAL ? this.x : this.y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    @Override
    public String toString() {
        return "ScreenPosition{x=" + x + ", y=" + y + '}';
    }
}
