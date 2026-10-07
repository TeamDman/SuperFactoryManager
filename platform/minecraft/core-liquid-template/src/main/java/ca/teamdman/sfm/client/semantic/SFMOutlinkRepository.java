package ca.teamdman.sfm.client.semantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, failure-isolating provider repository. It keeps every
 * contribution so equal-priority providers never disappear behind an ambient
 * registration-order winner.
 */
public final class SFMOutlinkRepository implements AutoCloseable {
    public record Entry(String id, int priority, SFMSpatialSemanticProvider provider) {
        public Entry {
            requireProviderId(id);
            Objects.requireNonNull(provider, "provider");
            if (!id.equals(provider.id()) || priority != provider.priority()) {
                throw new IllegalArgumentException("provider registry identity disagrees with provider");
            }
        }
    }

    public record Result(
            SFMSpatialSemanticProvider.Request request,
            List<Match> matches,
            List<SFMSpatialSemanticContract.ProviderEvidence> evidence,
            boolean cancelled
    ) {
        public Result {
            Objects.requireNonNull(request, "request");
            matches = List.copyOf(matches);
            evidence = List.copyOf(evidence);
        }

        public Optional<Match> preferred() {
            return matches.stream().findFirst();
        }

        public List<SFMSpatialSemanticContract.Outlink> orderedOutlinks() {
            return matches.stream().flatMap(match -> match.contribution().outlinks().stream()).toList();
        }

        public boolean hasPriorityTie() {
            return matches.size() > 1 && matches.get(0).priority() == matches.get(1).priority();
        }
    }

    public record Match(
            String providerId,
            int priority,
            long providerGeneration,
            SFMSpatialSemanticProvider.Contribution contribution
    ) {
        public Match {
            requireProviderId(providerId);
            if (providerGeneration < 0) throw new IllegalArgumentException("provider generation must be non-negative");
            Objects.requireNonNull(contribution, "contribution");
        }
    }

    private static final Comparator<Entry> ORDER = Comparator
            .comparingInt(Entry::priority).reversed()
            .thenComparing(Entry::id);
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean closed;

    public synchronized void register(SFMSpatialSemanticProvider provider) {
        ensureOpen();
        Objects.requireNonNull(provider, "provider");
        requireProviderId(provider.id());
        Entry entry = new Entry(provider.id(), provider.priority(), provider);
        if (entries.putIfAbsent(provider.id(), entry) != null) {
            throw new IllegalArgumentException("Duplicate spatial provider: " + provider.id());
        }
    }

    public synchronized List<Entry> snapshot() {
        ensureOpen();
        return entries.values().stream().sorted(ORDER).toList();
    }

    public Result probe(
            SFMSpatialSemanticProvider.Request request,
            SFMSpatialSemanticProvider.Cancellation cancellation
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        List<Entry> providers = snapshot();
        ArrayList<Match> matches = new ArrayList<>();
        ArrayList<SFMSpatialSemanticContract.ProviderEvidence> evidence = new ArrayList<>();
        for (Entry entry : providers) {
            if (cancellation.isCancelled()) break;
            if (!entry.provider().available()) {
                evidence.add(new SFMSpatialSemanticContract.ProviderEvidence(
                        entry.id(), entry.priority(), "unavailable", null));
                continue;
            }
            try {
                SFMSpatialSemanticProvider.Contribution contribution = entry.provider().probe(request, cancellation);
                if (contribution == null) {
                    evidence.add(new SFMSpatialSemanticContract.ProviderEvidence(
                            entry.id(), entry.priority(), "unsupported", null));
                    continue;
                }
                if (!contribution.certifiedRegion().domainId().equals(request.queryDomainId())
                        || !contribution.certifiedRegion().contains(request.queryPoint())) {
                    throw new IllegalStateException("provider certified a region that does not contain the query");
                }
                if (entry.provider().generation() < 0) {
                    throw new IllegalStateException("provider generation must be non-negative");
                }
                matches.add(new Match(entry.id(), entry.priority(), entry.provider().generation(), contribution));
                evidence.add(new SFMSpatialSemanticContract.ProviderEvidence(
                        entry.id(), entry.priority(), "matched", null));
            } catch (java.util.concurrent.CancellationException cancelled) {
                cancellation.cancel();
                evidence.add(new SFMSpatialSemanticContract.ProviderEvidence(
                        entry.id(), entry.priority(), "cancelled", cancelled.getMessage()));
                break;
            } catch (RuntimeException failure) {
                evidence.add(new SFMSpatialSemanticContract.ProviderEvidence(
                        entry.id(), entry.priority(), "failed",
                        failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "")));
            }
        }
        matches.sort(Comparator.comparingInt(Match::priority).reversed().thenComparing(Match::providerId));
        if (matches.size() > 1 && matches.get(0).priority() == matches.get(1).priority()) {
            int priority = matches.get(0).priority();
            String tied = matches.stream().filter(match -> match.priority() == priority)
                    .map(Match::providerId).reduce((left, right) -> left + "," + right).orElse("");
            evidence.add(new SFMSpatialSemanticContract.ProviderEvidence(
                    "sfm:provider_resolution", priority, "priority-tie", tied));
        }
        return new Result(request, matches, evidence, cancellation.isCancelled());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException aggregate = null;
        for (Entry entry : entries.values().stream().sorted(ORDER).toList()) {
            try {
                entry.provider().close();
            } catch (RuntimeException failure) {
                if (aggregate == null) aggregate = failure;
                else aggregate.addSuppressed(failure);
            }
        }
        entries.clear();
        if (aggregate != null) throw aggregate;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Spatial provider repository is closed");
    }

    private static void requireProviderId(String id) {
        if (id == null || id.isBlank() || id.indexOf(':') <= 0 || id.endsWith(":")) {
            throw new IllegalArgumentException("provider id must be mod-qualified");
        }
    }
}
