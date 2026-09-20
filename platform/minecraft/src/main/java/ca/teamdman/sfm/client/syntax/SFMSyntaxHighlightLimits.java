package ca.teamdman.sfm.client.syntax;

/** Process-level bounds shared by syntax-highlight requests and results. */
public record SFMSyntaxHighlightLimits(
        int maximumSourceBytes,
        int maximumSpans,
        int maximumDiagnostics
) {
    public static final int DEFAULT_MAXIMUM_SOURCE_BYTES = 4 * 1024 * 1024;
    public static final int DEFAULT_MAXIMUM_SPANS = 262_144;
    public static final int DEFAULT_MAXIMUM_DIAGNOSTICS = 256;

    public SFMSyntaxHighlightLimits {
        if (maximumSourceBytes <= 0 || maximumSpans <= 0 || maximumDiagnostics <= 0) {
            throw new IllegalArgumentException("Syntax-highlight limits must be positive");
        }
    }

    public static SFMSyntaxHighlightLimits defaults() {
        return new SFMSyntaxHighlightLimits(
                DEFAULT_MAXIMUM_SOURCE_BYTES,
                DEFAULT_MAXIMUM_SPANS,
                DEFAULT_MAXIMUM_DIAGNOSTICS
        );
    }
}
