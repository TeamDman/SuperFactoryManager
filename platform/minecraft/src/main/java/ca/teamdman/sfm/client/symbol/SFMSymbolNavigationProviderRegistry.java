package ca.teamdman.sfm.client.symbol;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic registry/discovery surface for replaceable symbol providers. */
public final class SFMSymbolNavigationProviderRegistry implements AutoCloseable {
    public record Entry(ResourceLocation id, int priority, SFMSymbolNavigationProvider provider) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
            if (!id.equals(provider.id())) {
                throw new IllegalArgumentException("Provider id disagrees with its registry entry");
            }
        }
    }

    private static final Comparator<Entry> ORDER = Comparator
            .comparingInt(Entry::priority).reversed()
            .thenComparing(entry -> entry.id().toString());
    private final Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();

    public synchronized void register(int priority, SFMSymbolNavigationProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Entry previous = entries.putIfAbsent(provider.id(), new Entry(provider.id(), priority, provider));
        if (previous != null) throw new IllegalArgumentException("Duplicate symbol provider: " + provider.id());
    }

    public synchronized Optional<Entry> get(ResourceLocation id) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized List<Entry> snapshot() {
        ArrayList<Entry> result = new ArrayList<>(entries.values());
        result.sort(ORDER);
        return List.copyOf(result);
    }

    public synchronized Optional<Entry> preferredAvailable() {
        return snapshot().stream().filter(entry -> entry.provider().available()).findFirst();
    }

    @Override
    public synchronized void close() {
        List<Entry> closing = snapshot();
        entries.clear();
        RuntimeException failure = null;
        for (Entry entry : closing) {
            try {
                entry.provider().close();
            } catch (RuntimeException caught) {
                if (failure == null) failure = caught;
                else failure.addSuppressed(caught);
            }
        }
        if (failure != null) throw failure;
    }
}
