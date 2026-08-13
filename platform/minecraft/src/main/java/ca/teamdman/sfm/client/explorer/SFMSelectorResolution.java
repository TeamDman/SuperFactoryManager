package ca.teamdman.sfm.client.explorer;

import java.util.List;
import java.util.Objects;

/** Immutable evidence produced by one pure selector evaluation. */
public record SFMSelectorResolution<I>(
        SFMEntitySelector.Domain domain,
        String selector,
        long repositoryGeneration,
        Completeness completeness,
        List<I> identities,
        List<Diagnostic> diagnostics
) {
    public enum Completeness {
        COMPLETE,
        UNSUPPORTED,
        INVALID
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public record Diagnostic(String code, Severity severity, String message) {
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
            if (code.isEmpty() || message.isEmpty()) {
                throw new IllegalArgumentException("Selector diagnostics require code and message");
            }
        }
    }

    public SFMSelectorResolution {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(completeness, "completeness");
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(diagnostics, "diagnostics");
        identities = List.copyOf(identities);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean complete() {
        return completeness == Completeness.COMPLETE;
    }

    public boolean hasDiagnostic(String code) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals(code));
    }
}
