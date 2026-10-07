package ca.teamdman.sfm.client.screen;

import java.util.Optional;

/**
 * Pure layout for EditorV3's non-interactive read-only status chrome.
 */
final class SFMTextEditorReadOnlyChrome {
    private static final int MAX_CONTROL_GAP = 2;
    private static final int HORIZONTAL_TEXT_PADDING = 4;
    private static final int VERTICAL_TEXT_PADDING = 2;

    private SFMTextEditorReadOnlyChrome() {
    }

    static Optional<Layout> layout(
            boolean readOnly,
            Rect configButton,
            Rect doneButton,
            int naturalTextWidth,
            int lineHeight
    ) {
        if (!readOnly) return Optional.empty();
        if (naturalTextWidth < 0) throw new IllegalArgumentException("naturalTextWidth must not be negative");
        if (lineHeight <= 0) throw new IllegalArgumentException("lineHeight must be positive");

        int laneLeft = configButton.right();
        int laneRight = doneButton.x();
        int laneTop = Math.max(configButton.y(), doneButton.y());
        int laneBottom = Math.min(configButton.bottom(), doneButton.bottom());
        int laneWidth = laneRight - laneLeft;
        int laneHeight = laneBottom - laneTop;
        if (laneWidth <= 0 || laneHeight <= 0) return Optional.empty();

        // Preserve a small visual gap where space permits, but never consume the
        // final pixel of a narrow lane or alter either neighbouring hit target.
        int controlGap = Math.min(MAX_CONTROL_GAP, Math.max(0, (laneWidth - 1) / 2));
        int availableLeft = laneLeft + controlGap;
        int availableWidth = laneWidth - controlGap * 2;
        int naturalBackgroundWidth = naturalTextWidth + HORIZONTAL_TEXT_PADDING * 2;
        int backgroundWidth = Math.max(1, Math.min(availableWidth, naturalBackgroundWidth));
        int backgroundHeight = Math.max(1, Math.min(
                laneHeight,
                lineHeight + VERTICAL_TEXT_PADDING * 2
        ));
        int backgroundX = availableLeft + (availableWidth - backgroundWidth) / 2;
        int backgroundY = laneTop + (laneHeight - backgroundHeight) / 2;
        Rect background = new Rect(backgroundX, backgroundY, backgroundWidth, backgroundHeight);

        int horizontalPadding = Math.min(
                HORIZONTAL_TEXT_PADDING,
                Math.max(0, (backgroundWidth - 1) / 2)
        );
        int textWidth = Math.max(1, backgroundWidth - horizontalPadding * 2);
        int textHeight = Math.min(lineHeight, backgroundHeight);
        Rect textArea = new Rect(
                background.x() + horizontalPadding,
                background.y() + (background.height() - textHeight) / 2,
                textWidth,
                textHeight
        );
        return Optional.of(new Layout(background, textArea));
    }

    record Layout(Rect background, Rect textArea) {
        Layout {
            if (!background.contains(textArea)) {
                throw new IllegalArgumentException("text area must be contained by its background");
            }
        }
    }

    record Rect(int x, int y, int width, int height) {
        Rect {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("rectangle dimensions must not be negative");
            }
        }

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(Rect other) {
            return x <= other.x
                   && y <= other.y
                   && right() >= other.right()
                   && bottom() >= other.bottom();
        }

        boolean overlaps(Rect other) {
            return x < other.right()
                   && right() > other.x
                   && y < other.bottom()
                   && bottom() > other.y;
        }
    }
}
