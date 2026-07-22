package ca.teamdman.sfm.client.review.repository;

import java.util.List;

public final class SFMRepositoryReviewException extends RuntimeException {
    public enum Code { NO_BUNDLES, NOT_FOUND, AMBIGUOUS_NAME, INVALID_BUNDLE, IO_ERROR }

    private final Code code;
    private final List<String> diagnostics;

    public SFMRepositoryReviewException(Code code, String message) {
        this(code, message, List.of());
    }

    public SFMRepositoryReviewException(Code code, String message, List<String> diagnostics) {
        super(message);
        this.code = code;
        this.diagnostics = List.copyOf(diagnostics);
    }

    public Code code() { return code; }
    public List<String> diagnostics() { return diagnostics; }
}
