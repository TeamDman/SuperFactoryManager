package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection.Row;
import java.util.*;

/** Row-grid scroll anchoring; selection is deliberately not an input. */
public record SFMExplorerScrollAnchor(List<Row> before, int index, int offsetRows) {
    public SFMExplorerScrollAnchor { before = List.copyOf(before); }
    public static SFMExplorerScrollAnchor capture(List<Row> rows, int scroll, int columns, Optional<SFMPath> local) {
        int index = -1;
        if (local.isPresent()) for (int i = 0; i < rows.size(); i++) if (rows.get(i).contains(local.orElseThrow())) { index = i; break; }
        if (index < 0) index = Math.min(Math.max(0, rows.size() - 1), scroll * columns);
        return new SFMExplorerScrollAnchor(rows, index, index / columns - scroll);
    }
    public int restore(List<Row> after, int columns) {
        if (before.isEmpty() || after.isEmpty()) return 0;
        var indices = new HashMap<SFMPath, Integer>();
        for (int i = 0; i < after.size(); i++) for (var path : after.get(i).paths()) indices.putIfAbsent(path, i);
        int found = find(before.get(index), indices);
        // The old row's nearest visible ancestor is a better fallback than a remote selection.
        int depth = before.get(index).depth();
        for (int i = index - 1; found < 0 && i >= 0; i--) if (before.get(i).depth() < depth) {
            found = find(before.get(i), indices); depth = before.get(i).depth();
        }
        for (int distance = 1; found < 0 && distance < before.size(); distance++) {
            if (index + distance < before.size()) found = find(before.get(index + distance), indices);
            if (found < 0 && index >= distance) found = find(before.get(index - distance), indices);
        }
        return found < 0 ? 0 : Math.max(0, found / columns - offsetRows);
    }
    private static int find(Row row, Map<SFMPath, Integer> indices) {
        for (var path : row.paths()) if (indices.containsKey(path)) return indices.get(path);
        return -1;
    }
}
