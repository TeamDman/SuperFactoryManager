package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Transparent resolver decorator that gives selected leaf entries mountable children. */
public final class SFMMountingExplorerResolver implements SFMExplorerResolver {
    private final SFMExplorerResolver delegate;
    private final List<SFMExplorerMountProvider> providers;

    public SFMMountingExplorerResolver(
            SFMExplorerResolver delegate,
            List<SFMExplorerMountProvider> providers
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        ArrayList<SFMExplorerMountProvider> ordered = new ArrayList<>(Objects.requireNonNull(providers, "providers"));
        ordered.sort(Comparator.comparing(SFMExplorerMountProvider::id));
        for (int index = 0; index < ordered.size(); index++) {
            SFMExplorerMountProvider provider = Objects.requireNonNull(ordered.get(index), "mount provider");
            if (provider.id().isBlank()) throw new IllegalArgumentException("Mount provider id must not be blank");
            if (index > 0 && ordered.get(index - 1).id().equals(provider.id())) {
                throw new IllegalArgumentException("Duplicate mount provider " + provider.id());
            }
        }
        this.providers = List.copyOf(ordered);
    }

    @Override
    public String scheme() {
        return delegate.scheme();
    }

    @Override
    public long generation() {
        return delegate.generation();
    }

    @Override
    public CompletableFuture<SFMExplorerEntry> describe(
            SFMPath path,
            SFMExplorerCancellationToken cancellation
    ) {
        Optional<SFMExplorerMountProvider> provider = providerFor(path);
        return delegate.describe(path, cancellation).thenApply(entry -> provider
                .filter(ignored -> isFile(entry))
                .map(value -> mounted(entry, value.id()))
                .orElse(entry));
    }

    @Override
    public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
        Optional<SFMExplorerMountProvider> provider = providerFor(request.parent());
        if (provider.isEmpty()) {
            return delegate.resolveChildren(request).thenApply(this::decorateDiscoveredMounts);
        }
        long expected = request.expectedResolverGeneration();
        if (expected != generation()) {
            return CompletableFuture.failedFuture(new StaleGenerationException(expected, generation()));
        }
        SFMExplorerMountProvider selected = provider.orElseThrow();
        return delegate.describe(request.parent(), request.cancellation()).thenCompose(entry -> {
            request.cancellation().throwIfCancelled();
            if (!isFile(entry)) return delegate.resolveChildren(request);
            return selected.resolveChildren(request, entry).thenApply(page -> {
                request.cancellation().throwIfCancelled();
                if (generation() != expected) throw new StaleGenerationException(expected, generation());
                return new ChildPage(
                        request.parent(),
                        page.entries(),
                        page.continuation(),
                        expected,
                        page.diagnostics(),
                        page.observedEntries()
                );
            });
        });
    }

    @Override
    public boolean permitsCrossSchemeChildren(SFMPath parent) {
        return providerFor(parent).isPresent() || delegate.permitsCrossSchemeChildren(parent);
    }

    @Override
    public boolean supportsTextRead() {
        return delegate.supportsTextRead();
    }

    @Override
    public CompletableFuture<SFMResolverTextResult> readText(SFMResolverTextRequest request) {
        return delegate.readText(request);
    }

    private Optional<SFMExplorerMountProvider> providerFor(SFMPath path) {
        List<SFMExplorerMountProvider> matches = providers.stream().filter(provider -> provider.supports(path)).toList();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous Explorer mount for " + path.canonical() + ": "
                    + matches.stream().map(SFMExplorerMountProvider::id).toList());
        }
        return matches.stream().findFirst();
    }

    private ChildPage decorateDiscoveredMounts(ChildPage page) {
        List<SFMExplorerEntry> entries = page.entries().stream().map(entry -> providerFor(entry.path())
                .filter(ignored -> isFile(entry))
                .map(provider -> mounted(entry, provider.id()))
                .orElse(entry)).toList();
        return new ChildPage(
                page.parent(),
                entries,
                page.continuation(),
                page.resolverGeneration(),
                page.diagnostics(),
                page.observedEntries()
        );
    }

    private static boolean isFile(SFMExplorerEntry entry) {
        return entry.sortKey(SFMExplorerEntry.SUBJECT_KIND).value().filter("file"::equals).isPresent();
    }

    private static SFMExplorerEntry mounted(SFMExplorerEntry entry, String providerId) {
        TreeMap<String, SFMExplorerEntry.SortKey> keys = new TreeMap<>(entry.sortKeys());
        keys.put(SFMExplorerEntry.PRIMARY_ACTION_OPEN, SFMExplorerEntry.SortKey.available("true"));
        keys.put(SFMExplorerEntry.MOUNT_PROVIDER, SFMExplorerEntry.SortKey.available(providerId));
        return new SFMExplorerEntry(
                entry.path(),
                entry.label(),
                true,
                keys,
                entry.searchTerms(),
                entry.diagnostics()
        );
    }
}
