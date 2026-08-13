package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Exact scheme-to-resolver registry; no resolver is inferred from display text. */
public final class SFMExplorerResolverRegistry {
    private final Map<String, SFMExplorerResolver> resolvers = new TreeMap<>();

    public synchronized void register(SFMExplorerResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        String scheme = resolver.scheme();
        if (scheme == null || !scheme.matches("[a-z][a-z0-9+.-]*")) {
            throw new IllegalArgumentException("Resolver scheme must use canonical URI scheme syntax");
        }
        SFMExplorerResolver previous = resolvers.putIfAbsent(scheme, resolver);
        if (previous != null) {
            throw new IllegalArgumentException("A resolver is already registered for scheme `" + scheme + "`");
        }
    }

    public synchronized Optional<SFMExplorerResolver> find(String scheme) {
        return Optional.ofNullable(resolvers.get(Objects.requireNonNull(scheme, "scheme")));
    }

    public synchronized SFMExplorerResolver require(SFMPath path) {
        Objects.requireNonNull(path, "path");
        SFMExplorerResolver resolver = resolvers.get(path.scheme());
        if (resolver == null) {
            throw new IllegalArgumentException(
                    "No lazy explorer resolver is registered for scheme `" + path.scheme() + "`"
            );
        }
        return resolver;
    }

    public synchronized Map<String, SFMExplorerResolver> snapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(resolvers));
    }
}
