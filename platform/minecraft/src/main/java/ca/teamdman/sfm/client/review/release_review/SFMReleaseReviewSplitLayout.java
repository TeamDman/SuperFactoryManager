package ca.teamdman.sfm.client.review.release_review;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSurfaceV1.*;
import static ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1.SnapshotSide;

/** Two-column presentation rows over verified source slices, never addresses into padding. */
public final class SFMReleaseReviewSplitLayout {
    public record Cell(String exactText, SourceRange source, MappingKind kind, Utf8Range surfaceRange) {
        public String displayText() {
            if (exactText.endsWith("\r\n")) return exactText.substring(0, exactText.length() - 2);
            if (exactText.endsWith("\n") || exactText.endsWith("\r")) return exactText.substring(0, exactText.length() - 1);
            return exactText;
        }
    }
    public record Row(Optional<Cell> before, Optional<Cell> after) {
        public Optional<Cell> cell(SnapshotSide side) { return side == SnapshotSide.BEFORE ? before : after; }
    }
    public record Selection(List<SourceRange> ranges, String text) {
        public Selection { ranges = List.copyOf(ranges); }
    }
    private final List<Row> rows;
    private SFMReleaseReviewSplitLayout(List<Row> rows) { this.rows = List.copyOf(rows); }
    public List<Row> rows() { return rows; }

    public record Position(int row, int column) { }
    public enum Motion { LEFT, RIGHT, UP, DOWN, HOME, END }

    /** Keyboard movement stays on one source side and skips alignment padding. */
    public Optional<Position> move(SnapshotSide side, Position from, Motion motion, boolean control) {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).cell(side).isPresent()) available.add(i);
        if (available.isEmpty()) return Optional.empty();
        int index = 0;
        if (from != null) {
            while (index + 1 < available.size() && available.get(index) < from.row()) index++;
        }
        String text = rows.get(available.get(index)).cell(side).orElseThrow().displayText();
        int[] points = text.codePoints().toArray();
        int column = Math.min(points.length, from == null ? 0 : from.column());
        switch (motion) {
            case HOME -> { if (control) index = 0; column = 0; }
            case END -> { if (control) index = available.size() - 1; column = Integer.MAX_VALUE; }
            case UP -> index = Math.max(0, index - 1);
            case DOWN -> index = Math.min(available.size() - 1, index + 1);
            case LEFT -> {
                if (column == 0 && index > 0) { index--; column = Integer.MAX_VALUE; }
                else if (control) {
                    while (column > 0 && !word(points[column - 1])) column--;
                    while (column > 0 && word(points[column - 1])) column--;
                } else column = Math.max(0, column - 1);
            }
            case RIGHT -> {
                if (column == points.length && index + 1 < available.size()) { index++; column = 0; }
                else if (control) {
                    while (column < points.length && word(points[column])) column++;
                    while (column < points.length && !word(points[column])) column++;
                } else column = Math.min(points.length, column + 1);
            }
        }
        String destination = rows.get(available.get(index)).cell(side).orElseThrow().displayText();
        return Optional.of(new Position(available.get(index), Math.min(column, destination.codePointCount(0, destination.length()))));
    }

    private static boolean word(int codepoint) {
        return Character.isLetterOrDigit(codepoint) || codepoint == '_';
    }

    /** Select displayed source slices, including original final line endings, never the other column. */
    public Selection selectAll(SnapshotSide side) {
        List<SourceRange> ranges = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (Row row : rows) row.cell(side).ifPresent(cell -> {
            ranges.add(cell.source()); text.append(cell.exactText());
        });
        return new Selection(ranges, text.toString());
    }

    public static SFMReleaseReviewSplitLayout from(Surface surface, Request request) {
        surface.validateAgainst(request);
        if (!surface.complete()) throw new IllegalArgumentException("Split layout requires a complete surface");
        List<Row> rows = new ArrayList<>();
        List<Cell> pendingBefore = new ArrayList<>(), pendingAfter = new ArrayList<>();
        byte[] bytes = surface.text().getBytes(StandardCharsets.UTF_8);
        for (Mapping mapping : surface.mappings()) {
            List<Cell> before = new ArrayList<>(), after = new ArrayList<>();
            for (SourceRange source : mapping.sourceRanges()) {
                List<Cell> target = source.side() == SnapshotSide.BEFORE ? before : after;
                int start = mapping.surfaceRange().startByte(), limit = mapping.surfaceRange().endByte();
                while (start < limit) {
                    if (target.size() + rows.size() + pendingBefore.size() + pendingAfter.size() >= DEFAULT_MAX_MAPPINGS * 2)
                        throw new IllegalArgumentException("Split layout exceeds bounded row count");
                    int end = start;
                    while (end < limit && bytes[end] != '\r' && bytes[end] != '\n') end++;
                    if (end < limit) {
                        if (bytes[end++] == '\r' && end < limit && bytes[end] == '\n') end++;
                    }
                    int sourceStart = source.range().startByte() + start - mapping.surfaceRange().startByte();
                    target.add(new Cell(new String(bytes, start, end - start, StandardCharsets.UTF_8),
                            new SourceRange(source.side(), source.documentRevisionId(), source.documentSha256(),
                                    source.path(), new Utf8Range(sourceStart, sourceStart + end - start)), mapping.kind(),
                            new Utf8Range(start, end)));
                    start = end;
                }
            }
            if (!before.isEmpty() && !after.isEmpty()) {
                flush(rows, pendingBefore, pendingAfter);
                flush(rows, before, after);
            } else {
                // A new deletion after additions begins a new change block; never
                // zip separate changes across an intervening unchanged context.
                if (!before.isEmpty() && !pendingAfter.isEmpty()) flush(rows, pendingBefore, pendingAfter);
                appendCells(pendingBefore, before);
                appendCells(pendingAfter, after);
            }
            if (rows.size() + pendingBefore.size() + pendingAfter.size() > DEFAULT_MAX_MAPPINGS * 2)
                throw new IllegalArgumentException("Split layout exceeds bounded row count");
        }
        flush(rows, pendingBefore, pendingAfter);
        return new SFMReleaseReviewSplitLayout(rows);
    }

    private static void appendCells(List<Cell> target, List<Cell> fragments) {
        for (Cell next : fragments) {
            if (!target.isEmpty()) {
                Cell previous = target.get(target.size() - 1);
                boolean incompleteLine = !previous.exactText().endsWith("\n")
                        && (!previous.exactText().endsWith("\r") || next.exactText().startsWith("\n"));
                if (incompleteLine && previous.surfaceRange().endByte() == next.surfaceRange().startByte()
                        && previous.source().documentRevisionId().equals(next.source().documentRevisionId())
                        && previous.source().range().endByte() == next.source().range().startByte()) {
                    SourceRange source = previous.source();
                    target.set(target.size() - 1, new Cell(previous.exactText() + next.exactText(),
                            new SourceRange(source.side(), source.documentRevisionId(), source.documentSha256(), source.path(),
                                    new Utf8Range(source.range().startByte(), next.source().range().endByte())),
                            previous.kind() == next.kind() ? previous.kind() : MappingKind.STRUCTURAL_CORRESPONDENCE,
                            new Utf8Range(previous.surfaceRange().startByte(), next.surfaceRange().endByte())));
                    continue;
                }
            }
            target.add(next);
        }
    }

    private static void flush(List<Row> rows, List<Cell> before, List<Cell> after) {
        for (int index = 0; index < Math.max(before.size(), after.size()); index++) {
            rows.add(new Row(index < before.size() ? Optional.of(before.get(index)) : Optional.empty(),
                    index < after.size() ? Optional.of(after.get(index)) : Optional.empty()));
        }
        before.clear(); after.clear();
    }

    /** Unicode-scalar columns; drag remains on its starting side, including over empty opposite cells. */
    public Selection select(SnapshotSide side, int anchorRow, int anchorColumn, int activeRow, int activeColumn) {
        if (anchorRow < 0 || activeRow < 0 || anchorRow >= rows.size() || activeRow >= rows.size()
                || anchorColumn < 0 || activeColumn < 0) throw new IllegalArgumentException("Invalid split selection");
        boolean forward = anchorRow < activeRow || (anchorRow == activeRow && anchorColumn <= activeColumn);
        int firstRow = forward ? anchorRow : activeRow, lastRow = forward ? activeRow : anchorRow;
        int firstColumn = forward ? anchorColumn : activeColumn, lastColumn = forward ? activeColumn : anchorColumn;
        List<SourceRange> ranges = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        for (int row = firstRow; row <= lastRow; row++) {
            Optional<Cell> candidate = rows.get(row).cell(side);
            if (candidate.isEmpty()) continue;
            Cell cell = candidate.orElseThrow();
            int columns = cell.displayText().codePointCount(0, cell.displayText().length());
            int start = row == firstRow ? cell.exactText().offsetByCodePoints(0, Math.min(firstColumn, columns)) : 0;
            int end = row == lastRow ? cell.exactText().offsetByCodePoints(0, Math.min(lastColumn, columns)) : cell.exactText().length();
            if (start >= end) continue;
            int offset = cell.exactText().substring(0, start).getBytes(StandardCharsets.UTF_8).length;
            String selected = cell.exactText().substring(start, end);
            SourceRange source = cell.source();
            ranges.add(new SourceRange(side, source.documentRevisionId(), source.documentSha256(), source.path(),
                    new Utf8Range(source.range().startByte() + offset,
                            source.range().startByte() + offset + selected.getBytes(StandardCharsets.UTF_8).length)));
            text.append(selected);
        }
        return new Selection(ranges, text.toString());
    }
}
