package ca.teamdman.sfm.client.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Deterministic finite-canvas sampling policies used by the coverage service. */
public final class SFMSpatialSamplingPolicies {
    private SFMSpatialSamplingPolicies() {
    }

    public record Cell(int x, int y) {
        public Cell {
            if (x < 0 || y < 0) throw new IllegalArgumentException("cell coordinates must be non-negative");
        }

        public long ordinal(int width) {
            return (long) y * width + x;
        }

        public double distanceSquared(Cell other) {
            long dx = (long) x - other.x;
            long dy = (long) y - other.y;
            return (double) dx * dx + (double) dy * dy;
        }
    }

    public interface Policy {
        String id();

        String version();

        List<Cell> select(int width, int height, long seed, long budget);
    }

    public static Policy exhaustive() {
        return policy("exhaustive", (width, height, seed, budget) -> {
            ArrayList<Cell> cells = allCells(width, height);
            return List.copyOf(cells.subList(0, Math.min(cells.size(), checkedBudget(budget))));
        });
    }

    public static Policy uniformRandom() {
        return policy("uniform-random", (width, height, seed, budget) -> {
            ArrayList<Cell> cells = allCells(width, height);
            java.util.Collections.shuffle(cells, new Random(seed));
            return List.copyOf(cells.subList(0, Math.min(cells.size(), checkedBudget(budget))));
        });
    }

    public static Policy stratified() {
        return policy("stratified", (width, height, seed, budget) -> {
            int limit = Math.min(checkedBudget(budget), Math.multiplyExact(width, height));
            if (limit == 0) return List.of();
            int columns = Math.max(1, (int) Math.ceil(Math.sqrt(limit * (width / (double) height))));
            int rows = Math.max(1, (int) Math.ceil(limit / (double) columns));
            Set<Cell> selected = new LinkedHashSet<>();
            for (int row = 0; row < rows && selected.size() < limit; row++) {
                for (int column = 0; column < columns && selected.size() < limit; column++) {
                    int x = Math.min(width - 1, (int) Math.floor((column + 0.5D) * width / columns));
                    int y = Math.min(height - 1, (int) Math.floor((row + 0.5D) * height / rows));
                    selected.add(new Cell(x, y));
                }
            }
            if (selected.size() < limit) {
                for (Cell cell : allCells(width, height)) {
                    selected.add(cell);
                    if (selected.size() == limit) break;
                }
            }
            return List.copyOf(selected);
        });
    }

    public static Policy maximin() {
        return policy("maximin", (width, height, seed, budget) -> {
            int limit = Math.min(checkedBudget(budget), Math.multiplyExact(width, height));
            if (limit == 0) return List.of();
            ArrayList<Cell> remaining = allCells(width, height);
            ArrayList<Cell> selected = new ArrayList<>();
            Cell first = remaining.remove((int) Math.floorMod(seed, remaining.size()));
            selected.add(first);
            while (selected.size() < limit) {
                Cell next = remaining.stream().max(Comparator
                        .comparingDouble((Cell cell) -> selected.stream()
                                .mapToDouble(cell::distanceSquared).min().orElse(0))
                        .thenComparingLong(cell -> -cell.ordinal(width))).orElseThrow();
                remaining.remove(next);
                selected.add(next);
            }
            return List.copyOf(selected);
        });
    }

    /**
     * Static adaptive seed order: sparse farthest-next coverage first, then
     * deterministic local neighbours. Runtime failure feedback is applied by
     * the coverage service when it schedules fallback probes.
     */
    public static Policy adaptiveFailureSeeking() {
        return policy("adaptive-failure-seeking", (width, height, seed, budget) -> {
            int limit = Math.min(checkedBudget(budget), Math.multiplyExact(width, height));
            List<Cell> far = maximin().select(width, height, seed, Math.max(1, (limit + 1L) / 2L));
            LinkedHashSet<Cell> selected = new LinkedHashSet<>(far);
            for (Cell origin : far) {
                for (int[] delta : List.of(new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, -1}, new int[]{0, 1})) {
                    int x = origin.x + delta[0];
                    int y = origin.y + delta[1];
                    if (x >= 0 && y >= 0 && x < width && y < height) selected.add(new Cell(x, y));
                    if (selected.size() >= limit) return List.copyOf(selected).subList(0, limit);
                }
            }
            for (Cell cell : allCells(width, height)) {
                selected.add(cell);
                if (selected.size() >= limit) break;
            }
            return List.copyOf(selected);
        });
    }

    private interface Selector {
        List<Cell> select(int width, int height, long seed, long budget);
    }

    private static Policy policy(String id, Selector selector) {
        return new Policy() {
            @Override public String id() { return id; }
            @Override public String version() { return "1"; }

            @Override
            public List<Cell> select(int width, int height, long seed, long budget) {
                validateSurface(width, height);
                if (seed < 0) throw new IllegalArgumentException("seed must be non-negative");
                if (budget <= 0) throw new IllegalArgumentException("budget must be positive");
                List<Cell> result = List.copyOf(selector.select(width, height, seed, budget));
                if (result.stream().anyMatch(cell -> cell.x >= width || cell.y >= height)) {
                    throw new IllegalStateException("sampling policy emitted an out-of-bounds cell");
                }
                if (result.size() != new LinkedHashSet<>(result).size()) {
                    throw new IllegalStateException("sampling policy emitted duplicate cells");
                }
                return result;
            }
        };
    }

    private static ArrayList<Cell> allCells(int width, int height) {
        validateSurface(width, height);
        ArrayList<Cell> result = new ArrayList<>(Math.multiplyExact(width, height));
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) result.add(new Cell(x, y));
        return result;
    }

    private static int checkedBudget(long budget) {
        if (budget > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.toIntExact(budget);
    }

    private static void validateSurface(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("surface dimensions must be positive");
        Math.multiplyExact(width, height);
    }
}
