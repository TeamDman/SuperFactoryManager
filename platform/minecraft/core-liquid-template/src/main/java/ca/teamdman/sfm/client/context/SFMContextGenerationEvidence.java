package ca.teamdman.sfm.client.context;

/** Immutable contributor-local revisions captured with one projection. */
public record SFMContextGenerationEvidence(
        long contributorGeneration,
        long contentGeneration,
        long selectionGeneration,
        long relationGeneration
) {
    public static final SFMContextGenerationEvidence INITIAL =
            new SFMContextGenerationEvidence(0, 0, 0, 0);

    public SFMContextGenerationEvidence {
        requireNonNegative(contributorGeneration, "contributorGeneration");
        requireNonNegative(contentGeneration, "contentGeneration");
        requireNonNegative(selectionGeneration, "selectionGeneration");
        requireNonNegative(relationGeneration, "relationGeneration");
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }
}
