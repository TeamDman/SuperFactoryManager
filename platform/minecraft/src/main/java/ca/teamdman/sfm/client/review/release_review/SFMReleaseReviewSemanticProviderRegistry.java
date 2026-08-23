package ca.teamdman.sfm.client.review.release_review;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded process-local registry for release-review semantic proposal providers.
 *
 * <p>A provider id is captured exactly once at registration and exposed through
 * an immutable wrapper, so later provider mutation cannot reorder a snapshot or
 * change its identity. Registrations are ephemeral evidence sources only: a
 * chosen proposal and its exact literal witness remain the durable authority in
 * the {@code sfm.release-review/1} document.</p>
 */
public final class SFMReleaseReviewSemanticProviderRegistry {
    public static final int MAX_PROVIDERS = 64;
    public static final int MAX_PROVIDER_ID_UTF8_BYTES = 256;

    public interface Registration extends AutoCloseable {
        String providerId();

        boolean active();

        @Override
        void close();
    }

    private record RegisteredProvider(
            String id,
            SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider delegate
    ) implements SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider {
        private RegisteredProvider {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence> propose(
                SFMReleaseReviewV1.PinnedSelection selection,
                SFMReleaseReviewSelectionAdapter.DocumentResolver documents
        ) throws Exception {
            return delegate.propose(selection, documents);
        }
    }

    private static final SFMReleaseReviewSemanticProviderRegistry GLOBAL =
            new SFMReleaseReviewSemanticProviderRegistry();

    private final Object lock = new Object();
    private final TreeMap<String, RegisteredProvider> providers = new TreeMap<>();
    private volatile List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> snapshot = List.of();

    public static SFMReleaseReviewSemanticProviderRegistry global() {
        return GLOBAL;
    }

    public Registration register(
            SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider provider
    ) {
        Objects.requireNonNull(provider, "provider");
        String providerId = requireStableId(provider.id());
        RegisteredProvider registered = new RegisteredProvider(providerId, provider);
        synchronized (lock) {
            if (providers.containsKey(providerId)) {
                throw new IllegalArgumentException("Duplicate release-review semantic provider: " + providerId);
            }
            if (providers.size() >= MAX_PROVIDERS) {
                throw new IllegalStateException(
                        "Release-review semantic provider registry reached its bounded capacity of "
                                + MAX_PROVIDERS);
            }
            providers.put(providerId, registered);
            publishSnapshot();
        }
        return new Registration() {
            private final AtomicBoolean active = new AtomicBoolean(true);

            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public boolean active() {
                return active.get();
            }

            @Override
            public void close() {
                if (!active.compareAndSet(true, false)) return;
                synchronized (lock) {
                    if (providers.remove(providerId, registered)) publishSnapshot();
                }
            }
        };
    }

    /** Returns one immutable provider-id-ordered point-in-time view. */
    public List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider> snapshot() {
        return snapshot;
    }

    public int size() {
        return snapshot.size();
    }

    private void publishSnapshot() {
        snapshot = List.copyOf(providers.values());
    }

    private static String requireStableId(String value) {
        String id = Objects.requireNonNull(value, "provider id");
        if (id.isBlank() || !id.equals(id.strip())) {
            throw new IllegalArgumentException("Release-review semantic provider id must be canonical and non-blank");
        }
        if (id.getBytes(StandardCharsets.UTF_8).length > MAX_PROVIDER_ID_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "Release-review semantic provider id exceeds " + MAX_PROVIDER_ID_UTF8_BYTES + " UTF-8 bytes");
        }
        return id;
    }
}
