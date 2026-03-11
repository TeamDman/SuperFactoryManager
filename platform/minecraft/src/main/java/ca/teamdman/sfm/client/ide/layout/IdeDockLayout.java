package ca.teamdman.sfm.client.ide.layout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IdeDockLayout {
    private IdeDockLayout() {
    }

    public static <T> Map<T, IdeArea> calculate(IdeArea root, List<IdeDockPiece<T>> pieces) {
        LinkedHashMap<T, IdeArea> carvedAreas = new LinkedHashMap<>();
        IdeArea remaining = root;

        for (int i = pieces.size() - 1; i >= 0; i--) {
            IdeDockPiece<T> piece = pieces.get(i);
            IdeArea allocated = carve(remaining, piece.direction(), piece.size());
            carvedAreas.put(piece.target(), allocated);
            remaining = subtract(remaining, piece.direction(), allocated);
        }

        LinkedHashMap<T, IdeArea> orderedResult = new LinkedHashMap<>();
        for (IdeDockPiece<T> piece : pieces) {
            orderedResult.put(piece.target(), carvedAreas.get(piece.target()));
        }
        return orderedResult;
    }

    private static IdeArea carve(IdeArea source, IdeDockDirection direction, int requestedSize) {
        if (source == null || source.isEmpty()) {
            return new IdeArea(0, 0, 0, 0);
        }

        return switch (direction) {
            case LEFT -> {
                int width = Math.max(0, Math.min(requestedSize, source.width()));
                yield new IdeArea(source.x(), source.y(), width, source.height());
            }
            case RIGHT -> {
                int width = Math.max(0, Math.min(requestedSize, source.width()));
                yield new IdeArea(source.right() - width, source.y(), width, source.height());
            }
            case UP -> {
                int height = Math.max(0, Math.min(requestedSize, source.height()));
                yield new IdeArea(source.x(), source.y(), source.width(), height);
            }
            case DOWN -> {
                int height = Math.max(0, Math.min(requestedSize, source.height()));
                yield new IdeArea(source.x(), source.bottom() - height, source.width(), height);
            }
            case CENTER -> source;
        };
    }

    private static IdeArea subtract(IdeArea source, IdeDockDirection direction, IdeArea allocated) {
        if (source == null || source.isEmpty()) {
            return new IdeArea(0, 0, 0, 0);
        }

        return switch (direction) {
            case LEFT -> new IdeArea(
                    allocated.right(),
                    source.y(),
                    Math.max(0, source.right() - allocated.right()),
                    source.height()
            );
            case RIGHT -> new IdeArea(
                    source.x(),
                    source.y(),
                    Math.max(0, allocated.x() - source.x()),
                    source.height()
            );
            case UP -> new IdeArea(
                    source.x(),
                    allocated.bottom(),
                    source.width(),
                    Math.max(0, source.bottom() - allocated.bottom())
            );
            case DOWN -> new IdeArea(
                    source.x(),
                    source.y(),
                    source.width(),
                    Math.max(0, allocated.y() - source.y())
            );
            case CENTER -> new IdeArea(0, 0, 0, 0);
        };
    }
}
