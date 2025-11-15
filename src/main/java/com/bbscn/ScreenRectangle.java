package com.bbscn;

public class ScreenRectangle {
    private final ScreenPosition position;
    private final int width;
    private final int height;

    public static final ScreenRectangle EMPTY = new ScreenRectangle(0, 0, 0, 0);

    public ScreenRectangle(ScreenPosition position, int width, int height) {
        this.position = position;
        this.width = width;
        this.height = height;
    }

    public ScreenRectangle(int x, int y, int width, int height) {
        this(new ScreenPosition(x, y), width, height);
    }

    public static ScreenRectangle empty() {
        return EMPTY;
    }

    public static ScreenRectangle of(ScreenAxis axis, int pPrimary, int pSecondary, int pPrimaryLength, int pSecondaryLength) {
        if (axis == ScreenAxis.HORIZONTAL) {
            return new ScreenRectangle(pPrimary, pSecondary, pPrimaryLength, pSecondaryLength);
        } else {
            return new ScreenRectangle(pSecondary, pPrimary, pSecondaryLength, pPrimaryLength);
        }
    }

    public ScreenRectangle step(ScreenDirection dir) {
        return new ScreenRectangle(this.position.step(dir), this.width, this.height);
    }

    public int getLength(ScreenAxis axis) {
        return axis == ScreenAxis.HORIZONTAL ? this.width : this.height;
    }

    public int getBoundInDirection(ScreenDirection dir) {
        ScreenAxis axis = dir.getAxis();
        int base = this.position.getCoordinate(axis);
        return dir.isPositive() ? base + this.getLength(axis) - 1 : base;
    }

    public ScreenRectangle getBorder(ScreenDirection dir) {
        int bound = this.getBoundInDirection(dir);
        ScreenAxis ortho = dir.getAxis().orthogonal();
        int base = this.getBoundInDirection(ortho.getNegative());
        int length = this.getLength(ortho);
        return of(dir.getAxis(), bound, base, 1, length).step(dir);
    }

    public boolean overlaps(ScreenRectangle other) {
        return this.overlapsInAxis(other, ScreenAxis.HORIZONTAL) && this.overlapsInAxis(other, ScreenAxis.VERTICAL);
    }

    public boolean overlapsInAxis(ScreenRectangle other, ScreenAxis axis) {
        int a1 = this.getBoundInDirection(axis.getNegative());
        int b1 = other.getBoundInDirection(axis.getNegative());
        int a2 = this.getBoundInDirection(axis.getPositive());
        int b2 = other.getBoundInDirection(axis.getPositive());
        return Math.max(a1, b1) <= Math.min(a2, b2);
    }

    public int getCenterInAxis(ScreenAxis axis) {
        return (this.getBoundInDirection(axis.getPositive()) + this.getBoundInDirection(axis.getNegative())) / 2;
    }

    public ScreenRectangle intersection(ScreenRectangle other) {
        int left = Math.max(this.left(), other.left());
        int top = Math.max(this.top(), other.top());
        int right = Math.min(this.right(), other.right());
        int bottom = Math.min(this.bottom(), other.bottom());
        if (left < right && top < bottom) {
            return new ScreenRectangle(left, top, right - left, bottom - top);
        }
        return null;
    }

    public int top() { return this.position.y(); }
    public int bottom() { return this.position.y() + this.height; }
    public int left() { return this.position.x(); }
    public int right() { return this.position.x() + this.width; }

    public boolean containsPoint(int x, int y) {
        return x >= left() && x < right() && y >= top() && y < bottom();
    }

    // 额外的 getter 如果需要：
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public ScreenPosition getPosition() { return position; }


    public enum ScreenAxis {
        HORIZONTAL, VERTICAL;

        public ScreenAxis orthogonal() {
            return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
        }

        public ScreenDirection getNegative() {
            return this == HORIZONTAL ? ScreenDirection.LEFT : ScreenDirection.UP;
        }

        public ScreenDirection getPositive() {
            return this == HORIZONTAL ? ScreenDirection.RIGHT : ScreenDirection.DOWN;
        }
    }

    public enum ScreenDirection {
        LEFT(-1, 0, ScreenAxis.HORIZONTAL),
        RIGHT(1, 0, ScreenAxis.HORIZONTAL),
        UP(0, -1, ScreenAxis.VERTICAL),
        DOWN(0, 1, ScreenAxis.VERTICAL);

        private final int stepX;
        private final int stepY;
        private final ScreenAxis axis;

        ScreenDirection(int dx, int dy, ScreenAxis axis) {
            this.stepX = dx;
            this.stepY = dy;
            this.axis = axis;
        }

        public int getStepX() { return stepX; }
        public int getStepY() { return stepY; }
        public ScreenAxis getAxis() { return axis; }

        public boolean isPositive() {
            return this == RIGHT || this == DOWN;
        }

        public ScreenDirection getNegative() {
            switch (this) {
                case LEFT: return RIGHT;
                case RIGHT: return LEFT;
                case UP: return DOWN;
                case DOWN: return UP;
            }
            return this;
        }
    }

}
