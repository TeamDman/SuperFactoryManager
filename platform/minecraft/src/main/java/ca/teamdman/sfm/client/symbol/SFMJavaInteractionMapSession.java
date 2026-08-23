package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One editor-owned asynchronous semantic-map cache. Publications are accepted
 * only for the exact document generation and content hash that requested them.
 */
public final class SFMJavaInteractionMapSession implements AutoCloseable {
    private final SFMJavaInteractionMapLookupService service;
    private SFMJavaInteractionMapLookupService.Submission active;
    private volatile Publication publication;
    private long requestEpoch;
    private boolean closed;

    public SFMJavaInteractionMapSession(SFMJavaInteractionMapLookupService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public synchronized void refresh(
            SFMContextContribution contribution,
            long documentGeneration,
            String contentHash
    ) {
        Objects.requireNonNull(contribution, "contribution");
        if (documentGeneration < 0) throw new IllegalArgumentException("documentGeneration must not be negative");
        requireText(contentHash, "contentHash");
        if (closed) return;
        long epoch = ++requestEpoch;
        if (active != null) active.cancel();
        active = null;
        publication = null;
        Optional<SFMDefinitionContextAdapter.DiagnosticCode> unavailable =
                structurallyUnavailable(contribution);
        if (unavailable.isPresent()) {
            SFM.LOGGER.debug(
                    "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=SKIPPED epoch={} expected_generation={} reason={}",
                    epoch,
                    documentGeneration,
                    unavailable.orElseThrow().name().toLowerCase(Locale.ROOT)
            );
            return;
        }
        SFMJavaInteractionMapLookupService.Submission submitted = service.queryInteractionMap(contribution);
        active = submitted;
        submitted.result().whenComplete((lookup, failure) -> {
            synchronized (SFMJavaInteractionMapSession.this) {
                if (closed || requestEpoch != epoch || active != submitted) {
                    SFM.LOGGER.debug(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_STALE epoch={} current_epoch={} closed={}",
                            epoch,
                            requestEpoch,
                            closed
                    );
                    return;
                }
                if (failure != null) {
                    Throwable rootFailure = unwrap(failure);
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=FAILED epoch={} expected_generation={} failure_type={} failure_code={}",
                            epoch,
                            documentGeneration,
                            rootFailure.getClass().getSimpleName(),
                            privacySafeFailureCode(rootFailure)
                    );
                    return;
                }
                if (lookup == null) {
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_NULL epoch={} expected_generation={}",
                            epoch,
                            documentGeneration
                    );
                    return;
                }
                SFMJavaInteractionMap.Result result = lookup.result();
                if (result.outcome() != SFMJavaInteractionMap.Outcome.SUCCESS) {
                    FailureSummary summary = failureSummary(result);
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_OUTCOME epoch={} outcome={} diagnostic_codes={} failure_category={} next_action={} address_scheme={} root_id={} source_set={}",
                            epoch,
                            result.outcome(),
                            summary.diagnosticCodes(),
                            summary.category(),
                            summary.nextAction(),
                            addressScheme(result.document().address()),
                            safeLogToken(result.document().rootId()),
                            safeLogToken(result.document().sourceSet())
                    );
                    return;
                }
                if (result.documentGeneration() != documentGeneration) {
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_GENERATION epoch={} expected={} actual={}",
                            epoch,
                            documentGeneration,
                            result.documentGeneration()
                    );
                    return;
                }
                if (!result.document().contentHash().equals(contentHash)) {
                    SFM.LOGGER.warn(
                            "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=REJECTED_CONTENT epoch={} generation={}",
                            epoch,
                            documentGeneration
                    );
                    return;
                }
                publication = new Publication(documentGeneration, contentHash, result);
                SFM.LOGGER.info(
                        "SFM_JAVA_INTERACTION_MAP_PUBLICATION status=PUBLISHED epoch={} generation={} semantic_generation={} address={} root_id={} report_path={} source_set={} regions={} classifications={} outlinks={} diagnostic_codes={}",
                        epoch,
                        documentGeneration,
                        result.semanticGeneration(),
                        result.document().address(),
                        result.document().rootId(),
                        result.document().reportPath(),
                        result.document().sourceSet(),
                        result.regions().size(),
                        result.classifications().size(),
                        result.outlinks().size(),
                        String.join(",", result.diagnostics().stream()
                                .map(SFMDefinitionResult.Diagnostic::code)
                                .distinct()
                                .limit(8)
                                .toList())
                );
            }
        });
    }

    /**
     * Rejects immutable document shapes that the Java worker can never adapt.
     * Scratch buffers are valid editor documents, but they have no resolver
     * authority and therefore must not produce one failed worker request per
     * edit revision.
     */
    static Optional<SFMDefinitionContextAdapter.DiagnosticCode> structurallyUnavailable(
            SFMContextContribution contribution
    ) {
        Objects.requireNonNull(contribution, "contribution");
        if (!(contribution.projection() instanceof SFMContextDocumentProjection document)) {
            return Optional.of(SFMDefinitionContextAdapter.DiagnosticCode.ORIGIN_IS_NOT_DOCUMENT);
        }
        var baseline = document.baseline();
        if (!baseline.ready()) {
            return Optional.of(SFMDefinitionContextAdapter.DiagnosticCode.DOCUMENT_NOT_READY);
        }
        if (baseline.path().isEmpty()) {
            return Optional.of(SFMDefinitionContextAdapter.DiagnosticCode.DOCUMENT_PATH_ABSENT);
        }
        if (baseline.authorizedRoot().isEmpty()) {
            return Optional.of(SFMDefinitionContextAdapter.DiagnosticCode.AUTHORIZED_ROOT_ABSENT);
        }
        if (baseline.path().orElseThrow().kind() != SFMPath.Kind.FILE) {
            return Optional.of(SFMDefinitionContextAdapter.DiagnosticCode.DOCUMENT_PATH_NOT_FILE);
        }
        if (baseline.authorizedRoot().orElseThrow().kind() != SFMPath.Kind.FILE) {
            return Optional.of(SFMDefinitionContextAdapter.DiagnosticCode.AUTHORIZED_ROOT_NOT_FILE);
        }
        return Optional.empty();
    }

    public Optional<SFMJavaInteractionMap.Result> current(
            long documentGeneration,
            String contentHash
    ) {
        Publication value = publication;
        if (value == null
                || value.documentGeneration != documentGeneration
                || !value.contentHash.equals(contentHash)) return Optional.empty();
        return Optional.of(value.result);
    }

    public boolean pending() {
        SFMJavaInteractionMapLookupService.Submission value = active;
        return value != null && !value.result().isDone();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        requestEpoch++;
        if (active != null) active.cancel();
        active = null;
        publication = null;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }

    /**
     * Reduces worker prose to stable operational categories without retaining
     * source text, absolute paths, report paths, or the diagnostic message.
     */
    static FailureSummary failureSummary(SFMJavaInteractionMap.Result result) {
        Objects.requireNonNull(result, "result");
        List<String> codes = result.diagnostics().stream()
                .map(SFMDefinitionResult.Diagnostic::code)
                .map(SFMJavaInteractionMapSession::safeLogToken)
                .distinct()
                .sorted()
                .limit(8)
                .toList();
        String category = result.diagnostics().stream()
                .map(SFMDefinitionResult.Diagnostic::message)
                .map(message -> message.toLowerCase(Locale.ROOT))
                .map(SFMJavaInteractionMapSession::failureCategory)
                .filter(value -> !value.equals("worker-rejected-request"))
                .findFirst()
                .orElse("worker-rejected-request");
        String action = switch (category) {
            case "document-not-indexed" -> "verify-negotiated-root-or-refresh-index";
            case "document-identity-ambiguous" -> "inspect-negotiated-root-overlap";
            case "report-path-mismatch", "source-set-mismatch", "source-root-projection-mismatch",
                    "workspace-fingerprint-mismatch", "classpath-fingerprint-mismatch",
                    "dependency-index-mismatch" -> "restart-symbol-worker";
            case "disk-snapshot-stale", "content-hash-mismatch" -> "reload-document-and-retry";
            default -> "inspect-worker-diagnostic-code";
        };
        return new FailureSummary(
                codes.isEmpty() ? "none" : String.join(",", codes),
                category,
                action
        );
    }

    private static String failureCategory(String message) {
        if (message.contains("matched 0 workspace files")
                || message.contains("has no editable, managed-jdk, or acquired-dependency source authority")) {
            return "document-not-indexed";
        }
        if (message.contains("matched") && message.contains("workspace files")) {
            return "document-identity-ambiguous";
        }
        if (message.contains("report path")) return "report-path-mismatch";
        if (message.contains("source set")) return "source-set-mismatch";
        if (message.contains("source-root projection")) return "source-root-projection-mismatch";
        if (message.contains("workspace fingerprint")) return "workspace-fingerprint-mismatch";
        if (message.contains("classpath fingerprint")) return "classpath-fingerprint-mismatch";
        if (message.contains("dependency-index identity")) return "dependency-index-mismatch";
        if (message.contains("content hash")) return "content-hash-mismatch";
        if (message.contains("changed on disk") || message.contains("verify the captured document")) {
            return "disk-snapshot-stale";
        }
        return "worker-rejected-request";
    }

    private static String privacySafeFailureCode(Throwable failure) {
        if (!(failure instanceof SFMSymbolNavigationRuntime.ContextUnavailableException unavailable)) {
            return "none";
        }
        return unavailable.adaptation().diagnostics().stream()
                .map(diagnostic -> diagnostic.code().name().toLowerCase(Locale.ROOT))
                .findFirst()
                .orElse("context-unavailable");
    }

    private static String addressScheme(String address) {
        try {
            return SFMPath.parse(address).scheme();
        } catch (RuntimeException ignored) {
            return "invalid";
        }
    }

    private static String safeLogToken(String value) {
        StringBuilder safe = new StringBuilder(Math.min(value.length(), 128));
        for (int index = 0; index < value.length() && safe.length() < 128; index++) {
            char character = value.charAt(index);
            safe.append(Character.isLetterOrDigit(character)
                    || character == ':' || character == '.' || character == '_' || character == '-'
                    ? character
                    : '_');
        }
        return safe.isEmpty() ? "none" : safe.toString();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    record FailureSummary(String diagnosticCodes, String category, String nextAction) {
        FailureSummary {
            requireText(diagnosticCodes, "diagnosticCodes");
            requireText(category, "category");
            requireText(nextAction, "nextAction");
        }
    }

    private record Publication(
            long documentGeneration,
            String contentHash,
            SFMJavaInteractionMap.Result result
    ) {
    }
}
