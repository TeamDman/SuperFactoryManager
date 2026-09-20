package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lazy, read-only adapter for the Minecraft item registry.
 *
 * <p>The resolver owns no live Minecraft objects. A source captures immutable
 * key/label data only when a worker executes the first request for a resolver
 * generation. The captured identifiers are retained for stable paging, while
 * {@link SFMExplorerEntry} instances are created only for the requested page or
 * described item.</p>
 */
public final class SFMItemRegistryExplorerResolver implements SFMExplorerResolver {
    public static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");

    private static final int MAXIMUM_DIAGNOSTICS = 32;
    private static final String CONTINUATION_PREFIX = "item-registry-v";

    /** Minecraft-independent value captured from one registry entry. */
    public record ItemKeyLabel(String namespace, String path, String label) {
        public ItemKeyLabel {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(label, "label");
            if (label.isBlank()) {
                throw new IllegalArgumentException("Item registry labels must not be blank");
            }
            // Constructing the path validates ResourceLocation-compatible text,
            // slash segmentation, and the canonical explorer shape.
            itemPath(namespace, path);
        }

        public String identifier() {
            return namespace + ":" + path;
        }

        public SFMPath explorerPath() {
            return itemPath(namespace, path);
        }
    }

    /** Immutable source result; useful for pure tests without a live registry. */
    public record ItemSnapshot(List<ItemKeyLabel> entries, List<String> diagnostics) {
        public ItemSnapshot {
            entries = List.copyOf(entries);
            entries.forEach(entry -> Objects.requireNonNull(entry, "entry"));
            diagnostics = List.copyOf(diagnostics);
            diagnostics.forEach(diagnostic -> Objects.requireNonNull(diagnostic, "diagnostic"));
        }

        public ItemSnapshot(List<ItemKeyLabel> entries) {
            this(entries, List.of());
        }
    }

    @FunctionalInterface
    public interface ItemSnapshotSource {
        ItemSnapshot capture();
    }

    private record CachedSnapshot(
            long generation,
            List<ItemKeyLabel> entries,
            Map<SFMPath, ItemKeyLabel> entriesByPath,
            List<String> diagnostics
    ) {
    }

    private record Continuation(long generation, int offset) {
    }

    private final ItemSnapshotSource source;
    private final Executor executor;
    private final int maximumPageSize;
    private final AtomicLong generation = new AtomicLong(1);
    private volatile CachedSnapshot cachedSnapshot;

    public SFMItemRegistryExplorerResolver(
            ItemSnapshotSource source,
            Executor executor,
            int maximumPageSize
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (maximumPageSize <= 0) {
            throw new IllegalArgumentException("Maximum page size must be positive");
        }
        this.maximumPageSize = maximumPageSize;
    }

    /**
     * Creates the production adapter without touching the registry eagerly.
     * Registry keys and translated hover labels are captured on the supplied
     * executor when the first request for a generation runs.
     */
    public static SFMItemRegistryExplorerResolver minecraft(
            Executor executor,
            int maximumPageSize
    ) {
        return new SFMItemRegistryExplorerResolver(
                SFMItemRegistryExplorerResolver::captureMinecraftItems,
                executor,
                maximumPageSize
        );
    }

    @Override
    public String scheme() {
        return "registry";
    }

    @Override
    public long generation() {
        return generation.get();
    }

    /**
     * Invalidates the cached key/label snapshot and every outstanding request.
     * The Minecraft item registry is normally frozen, but this explicit seam is
     * useful for reload-aware integration and deterministic tests.
     */
    public synchronized long invalidate() {
        long next = generation.incrementAndGet();
        cachedSnapshot = null;
        return next;
    }

    @Override
    public CompletableFuture<SFMExplorerEntry> describe(
            SFMPath path,
            SFMExplorerCancellationToken cancellation
    ) {
        PathKind pathKind = requireItemRegistryPath(path);
        Objects.requireNonNull(cancellation, "cancellation");
        return CompletableFuture.supplyAsync(() -> {
            cancellation.throwIfCancelled();
            long expectedGeneration = generation();
            SFMExplorerEntry answer;
            if (pathKind == PathKind.ROOT) {
                answer = rootEntry();
            } else {
                CachedSnapshot snapshot = snapshot(expectedGeneration, cancellation);
                ItemKeyLabel item = snapshot.entriesByPath().get(path);
                if (item == null) {
                    throw new IllegalArgumentException("Unknown item registry path: " + path);
                }
                answer = itemEntry(item);
            }
            cancellation.throwIfCancelled();
            assertGeneration(expectedGeneration);
            return answer;
        }, executor);
    }

    @Override
    public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
        PathKind pathKind = requireItemRegistryPath(request.parent());
        Continuation continuation = parseContinuation(request.continuation());
        int pageSize = Math.min(request.pageSize(), maximumPageSize);
        return CompletableFuture.supplyAsync(() -> {
            request.cancellation().throwIfCancelled();
            assertGeneration(request.expectedResolverGeneration());
            if (continuation.generation() >= 0
                    && continuation.generation() != request.expectedResolverGeneration()) {
                throw new StaleGenerationException(
                        continuation.generation(),
                        request.expectedResolverGeneration()
                );
            }

            CachedSnapshot snapshot = snapshot(
                    request.expectedResolverGeneration(),
                    request.cancellation()
            );
            if (pathKind == PathKind.ITEM) {
                if (continuation.offset() != 0) {
                    throw new IllegalArgumentException("Item leaves do not accept continuation tokens");
                }
                if (!snapshot.entriesByPath().containsKey(request.parent())) {
                    throw new IllegalArgumentException("Unknown item registry path: " + request.parent());
                }
                assertGeneration(request.expectedResolverGeneration());
                return new ChildPage(
                        request.parent(),
                        List.of(),
                        Optional.empty(),
                        request.expectedResolverGeneration(),
                        snapshot.diagnostics(),
                        0
                );
            }

            int offset = continuation.offset();
            if (offset > snapshot.entries().size()) {
                throw new IllegalArgumentException("Continuation is beyond the item registry snapshot");
            }
            int end = Math.min(snapshot.entries().size(), offset + pageSize);
            ArrayList<SFMExplorerEntry> entries = new ArrayList<>(end - offset);
            for (int index = offset; index < end; index++) {
                request.cancellation().throwIfCancelled();
                entries.add(itemEntry(snapshot.entries().get(index)));
            }
            request.cancellation().throwIfCancelled();
            assertGeneration(request.expectedResolverGeneration());
            boolean hasMore = end < snapshot.entries().size();
            Optional<String> next = hasMore
                    ? Optional.of(continuation(request.expectedResolverGeneration(), end))
                    : Optional.empty();
            return new ChildPage(
                    request.parent(),
                    entries,
                    next,
                    request.expectedResolverGeneration(),
                    snapshot.diagnostics(),
                    entries.size() + (hasMore ? 1 : 0)
            );
        }, executor);
    }

    private CachedSnapshot snapshot(
            long expectedGeneration,
            SFMExplorerCancellationToken cancellation
    ) {
        assertGeneration(expectedGeneration);
        CachedSnapshot existing = cachedSnapshot;
        if (existing != null && existing.generation() == expectedGeneration) {
            return existing;
        }

        cancellation.throwIfCancelled();
        ItemSnapshot captured = Objects.requireNonNull(source.capture(), "item registry snapshot");
        cancellation.throwIfCancelled();
        ArrayList<ItemKeyLabel> entries = new ArrayList<>(captured.entries());
        entries.sort(Comparator.comparing(ItemKeyLabel::identifier));
        HashMap<SFMPath, ItemKeyLabel> entriesByPath = new HashMap<>();
        for (ItemKeyLabel entry : entries) {
            cancellation.throwIfCancelled();
            SFMPath path = entry.explorerPath();
            if (entriesByPath.put(path, entry) != null) {
                throw new IllegalArgumentException("Duplicate item registry key: " + entry.identifier());
            }
        }
        CachedSnapshot candidate = new CachedSnapshot(
                expectedGeneration,
                List.copyOf(entries),
                Collections.unmodifiableMap(entriesByPath),
                boundedDiagnostics(captured.diagnostics())
        );
        synchronized (this) {
            assertGeneration(expectedGeneration);
            CachedSnapshot winner = cachedSnapshot;
            if (winner != null && winner.generation() == expectedGeneration) {
                return winner;
            }
            cachedSnapshot = candidate;
            return candidate;
        }
    }

    private void assertGeneration(long expected) {
        long actual = generation();
        if (actual != expected) throw new StaleGenerationException(expected, actual);
    }

    private static SFMExplorerEntry rootEntry() {
        TreeMap<String, SFMExplorerEntry.SortKey> keys = new TreeMap<>();
        keys.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available("Items"));
        keys.put(
                SFMExplorerEntry.SORT_EXTENSION,
                SFMExplorerEntry.SortKey.unavailable("item registries do not have file extensions")
        );
        keys.put(SFMExplorerEntry.SORT_ICON, SFMExplorerEntry.SortKey.available("minecraft:chest"));
        return new SFMExplorerEntry(ROOT, "Items", true, keys, List.of());
    }

    private static SFMExplorerEntry itemEntry(ItemKeyLabel item) {
        TreeMap<String, SFMExplorerEntry.SortKey> keys = new TreeMap<>();
        keys.put(SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available(item.label()));
        keys.put(
                SFMExplorerEntry.SORT_EXTENSION,
                SFMExplorerEntry.SortKey.unavailable("item registry entries do not have file extensions")
        );
        keys.put(SFMExplorerEntry.SORT_ICON, SFMExplorerEntry.SortKey.available(item.identifier()));
        return new SFMExplorerEntry(item.explorerPath(), item.label(), false, keys, List.of());
    }

    private static ItemSnapshot captureMinecraftItems() {
        ArrayList<ResourceLocation> ids = new ArrayList<>(SFMWellKnownRegistries.ITEMS.keys());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        ArrayList<ItemKeyLabel> entries = new ArrayList<>(ids.size());
        ArrayList<String> diagnostics = new ArrayList<>();
        for (ResourceLocation id : ids) {
            Item item = SFMWellKnownRegistries.ITEMS.get(id);
            String label = id.toString();
            if (item == null) {
                diagnostics.add("Item registry key has no value: " + id);
            } else {
                try {
                    String hoverName = new ItemStack(item).getHoverName().getString();
                    if (!hoverName.isBlank()) label = hoverName;
                } catch (RuntimeException failure) {
                    diagnostics.add(
                            "Could not resolve item label for " + id + ": "
                                    + failure.getClass().getSimpleName()
                    );
                }
            }
            entries.add(new ItemKeyLabel(id.getNamespace(), id.getPath(), label));
        }
        return new ItemSnapshot(entries, diagnostics);
    }

    private static List<String> boundedDiagnostics(List<String> diagnostics) {
        if (diagnostics.size() <= MAXIMUM_DIAGNOSTICS) return List.copyOf(diagnostics);
        ArrayList<String> answer = new ArrayList<>(diagnostics.subList(0, MAXIMUM_DIAGNOSTICS - 1));
        answer.add((diagnostics.size() - answer.size()) + " additional diagnostics suppressed");
        return List.copyOf(answer);
    }

    private static SFMPath itemPath(String namespace, String path) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        return SFMPath.parse(ROOT.canonical() + namespace + "/" + path);
    }

    private static PathKind requireItemRegistryPath(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (path.equals(ROOT)) return PathKind.ROOT;
        if (path.kind() != SFMPath.Kind.REGISTRY
                || !path.authority().equals(ROOT.authority())
                || path.segments().size() < 3
                || !path.segments().get(0).equals("item")) {
            throw new IllegalArgumentException(
                    "Item registry resolver only accepts " + ROOT + " and its item children"
            );
        }
        return PathKind.ITEM;
    }

    private static String continuation(long generation, int offset) {
        return CONTINUATION_PREFIX + generation + "-offset-" + offset;
    }

    private static Continuation parseContinuation(Optional<String> continuation) {
        Objects.requireNonNull(continuation, "continuation");
        if (continuation.isEmpty()) return new Continuation(-1, 0);
        String value = continuation.orElseThrow();
        if (!value.startsWith(CONTINUATION_PREFIX)) {
            throw new IllegalArgumentException("Unsupported item registry continuation token: " + value);
        }
        int separator = value.indexOf("-offset-", CONTINUATION_PREFIX.length());
        if (separator < 0) {
            throw new IllegalArgumentException("Invalid item registry continuation token: " + value);
        }
        try {
            long generation = Long.parseLong(value.substring(CONTINUATION_PREFIX.length(), separator));
            int offset = Integer.parseInt(value.substring(separator + "-offset-".length()));
            if (generation < 0 || offset <= 0) throw new NumberFormatException();
            return new Continuation(generation, offset);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Invalid item registry continuation token: " + value);
        }
    }

    private enum PathKind {
        ROOT,
        ITEM
    }
}
