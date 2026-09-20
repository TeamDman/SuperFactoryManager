package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable hit map supplied by a canvas adapter after it copies mutable glyph state. */
public record SFMContextCanvasTextMap(List<HitRegion> regions) {
    public SFMContextCanvasTextMap {
        regions = List.copyOf(regions);
    }

    public Optional<SFMTextDocumentPosition> positionAt(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Canvas coordinates must be finite");
        }
        SFMTextDocumentPosition result = null;
        for (HitRegion region : regions) {
            if (!region.contains(x, y)) continue;
            if (result != null && !result.equals(region.position())) return Optional.empty();
            result = region.position();
        }
        return Optional.ofNullable(result);
    }

    public SFMContextPosition.Canvas canvasPosition(double x, double y) {
        return new SFMContextPosition.Canvas(x, y, positionAt(x, y));
    }

    public record HitRegion(
            double x,
            double y,
            double width,
            double height,
            SFMTextDocumentPosition position
    ) {
        public HitRegion {
            if (!Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(width) || !Double.isFinite(height)
                    || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Canvas hit region must have finite positive bounds");
            }
            Objects.requireNonNull(position, "position");
        }

        boolean contains(double hitX, double hitY) {
            return hitX >= x && hitX < x + width && hitY >= y && hitY < y + height;
        }
    }
}
