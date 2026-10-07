package ca.teamdman.sfm.client.screen.color;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

/** Pure responsive geometry shared by rendering, hit-testing, tests, and puppets. */
public record SFMColorInputPanelLayout(
        Rect panel,
        Rect hueSaturation,
        Rect valueSlider,
        Rect swatch,
        Rect hex,
        Rect order,
        Rect channels,
        Rect recents,
        Rect reset,
        Rect cancel,
        Rect confirm,
        boolean compact
) {
    public static SFMColorInputPanelLayout fit(SFMScreenPanelBounds bounds) {
        int margin = 10;
        int width = Math.max(1, Math.min(580, bounds.width() - margin * 2));
        int height = Math.max(1, Math.min(340, bounds.height() - margin * 2));
        int x = bounds.x() + (bounds.width() - width) / 2;
        int y = bounds.y() + (bounds.height() - height) / 2;
        Rect panel = new Rect(x, y, width, height);
        boolean compact = width < 500;
        int bottom = y + height - 12;
        int buttonGap = 6;
        int buttonWidth = Math.max(36, (width - 28 - buttonGap * 2) / 3);
        Rect reset = new Rect(x + 14, bottom - 22, buttonWidth, 20);
        Rect cancel = new Rect(reset.right() + buttonGap, bottom - 22, buttonWidth, 20);
        Rect confirm = new Rect(cancel.right() + buttonGap, bottom - 22,
                Math.max(36, x + width - 14 - (cancel.right() + buttonGap)), 20);
        if (height < 280 && width >= 260) {
            Rect field = new Rect(x + 14, y + 40, width - 28, 44);
            Rect value = new Rect(field.x(), field.bottom() + 6, field.width(), 8);
            int rowY = value.bottom() + 6;
            int rightX = x + 112;
            int orderWidth = 52;
            return new SFMColorInputPanelLayout(panel, field, value,
                    new Rect(x + 14, rowY, 90, 36),
                    new Rect(rightX, rowY, x + width - 14 - orderWidth - 6 - rightX, 20),
                    new Rect(x + width - 14 - orderWidth, rowY, orderWidth, 20),
                    new Rect(rightX, rowY + 26, x + width - 14 - rightX, 44),
                    new Rect(x + 14, rowY + 50, 90, 18),
                    reset, cancel, confirm, true);
        }
        if (height < 280) {
            Rect field = new Rect(x + 8, y + 40, Math.max(1, width - 16), 34);
            Rect value = new Rect(field.x(), field.bottom() + 6, field.width(), 8);
            int rowY = value.bottom() + 6;
            int rightX = x + 64;
            return new SFMColorInputPanelLayout(panel, field, value,
                    new Rect(x + 8, rowY, 46, 28),
                    new Rect(rightX, rowY, Math.max(1, x + width - 8 - rightX), 18),
                    new Rect(rightX, rowY + 22, Math.min(44, Math.max(1, width - 72)), 18),
                    new Rect(x + 8, rowY + 38, Math.max(1, width - 16), 40),
                    new Rect(rightX + 50, rowY + 22, Math.max(1, x + width - 8 - (rightX + 50)), 16),
                    reset, cancel, confirm, true);
        }
        if (!compact) {
            Rect field = new Rect(x + 14, y + 48, 270, 145);
            Rect value = new Rect(field.x(), field.bottom() + 10, field.width(), 12);
            int rightX = field.right() + 20;
            int rightWidth = x + width - 14 - rightX;
            return new SFMColorInputPanelLayout(panel, field, value,
                    new Rect(rightX, y + 48, rightWidth, 42),
                    new Rect(rightX, y + 100, rightWidth - 58, 20),
                    new Rect(rightX + rightWidth - 52, y + 100, 52, 20),
                    new Rect(rightX, y + 130, rightWidth, 80),
                    new Rect(rightX, y + 222, rightWidth, 22),
                    reset, cancel, confirm, false);
        }
        if (width >= 260) {
            Rect field = new Rect(x + 14, y + 44, width - 28, 90);
            Rect value = new Rect(field.x(), field.bottom() + 7, field.width(), 10);
            int rowY = value.bottom() + 9;
            int rightX = x + 112;
            int orderWidth = 52;
            return new SFMColorInputPanelLayout(panel, field, value,
                    new Rect(x + 14, rowY, 90, 42),
                    new Rect(rightX, rowY, x + width - 14 - orderWidth - 6 - rightX, 20),
                    new Rect(x + width - 14 - orderWidth, rowY, orderWidth, 20),
                    new Rect(rightX, rowY + 27, x + width - 14 - rightX, 72),
                    new Rect(x + 14, rowY + 66, 90, 20),
                    reset, cancel, confirm, true);
        }
        Rect field = new Rect(x + 8, y + 40, Math.max(1, width - 16), 72);
        Rect value = new Rect(field.x(), field.bottom() + 6, field.width(), 9);
        int rowY = value.bottom() + 8;
        int rightX = x + 64;
        return new SFMColorInputPanelLayout(panel, field, value,
                new Rect(x + 8, rowY, 50, 32),
                new Rect(rightX, rowY, Math.max(1, x + width - 8 - rightX), 18),
                new Rect(rightX, rowY + 22, Math.min(52, Math.max(1, width - 72)), 18),
                new Rect(x + 8, rowY + 52, Math.max(1, width - 16), 64),
                new Rect(x + 8, rowY + 132, Math.max(1, width - 16), 18),
                reset, cancel, confirm, true);
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
    }
}
