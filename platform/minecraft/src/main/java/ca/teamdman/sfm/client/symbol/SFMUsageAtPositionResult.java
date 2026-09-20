package ca.teamdman.sfm.client.symbol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable provider-neutral references result for one exact editor location. */
public record SFMUsageAtPositionResult(
        String schema,
        long requestId,
        long requestGeneration,
        long workspaceGeneration,
        SFMDefinitionResult.Outcome outcome,
        SFMDefinitionResult.AnalysisContext context,
        SFMDefinitionResult.DocumentIdentity document,
        SFMDefinitionRequest.Position position,
        List<SFMDefinitionResult.SymbolIdentity> targets,
        List<SFMDefinitionResult.Definition> definitions,
        List<Usage> usages,
        List<SkippedCategory> skippedCategories,
        SFMDefinitionResult.Completeness completeness,
        List<SFMDefinitionResult.Diagnostic> diagnostics,
        List<SFMDefinitionResult.RecoveryAction> recoveryActions,
        Optional<SFMDefinitionResult.DependencyIndex> dependencyIndex
) {
    public static final String SCHEMA = "sfm.usage-at-position-result/1";
    private static final Set<String> CONFIDENCES = Set.of(
            "resolved", "partially-resolved", "unresolved"
    );

    public enum UsageKind {
        DECLARATION("declaration"),
        IMPORT("import"),
        TYPE_REFERENCE("type-reference"),
        FIELD_REFERENCE("field-reference"),
        INVOCATION("invocation"),
        METHOD_REFERENCE("method-reference"),
        LOCAL_REFERENCE("local-reference");

        private final String wireName;

        UsageKind(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static UsageKind fromWireName(String value) {
            for (UsageKind kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown usage kind: " + value);
        }
    }

    public enum SkippedCategoryKind {
        READ_WRITE_CLASSIFICATION("read-write-classification"),
        OVERRIDE_IMPLEMENTATION("override-implementation"),
        JAVADOC("javadoc"),
        STRING_LITERAL("string-literal"),
        REFLECTION("reflection"),
        DYNAMIC_DISPATCH("dynamic-dispatch");

        private final String wireName;

        SkippedCategoryKind(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static SkippedCategoryKind fromWireName(String value) {
            for (SkippedCategoryKind kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown skipped usage category: " + value);
        }
    }

    public record Usage(
            SFMDefinitionResult.SymbolIdentity target,
            UsageKind kind,
            SFMDefinitionResult.DefinitionSourceSpan span,
            String confidence
    ) {
        public Usage {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(span, "span");
            if (!CONFIDENCES.contains(confidence)) {
                throw new IllegalArgumentException("Unknown usage confidence: " + confidence);
            }
        }
    }

    public record SkippedCategory(SkippedCategoryKind category, String reason) {
        public SkippedCategory {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public SFMUsageAtPositionResult {
        if (!SCHEMA.equals(schema)) {
            throw new IllegalArgumentException("Unsupported usage-at-position result schema");
        }
        if (requestId < 0 || requestGeneration < 0 || workspaceGeneration < 0) {
            throw new IllegalArgumentException("Usage-at-position result identities must not be negative");
        }
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(position, "position");
        targets = List.copyOf(targets);
        definitions = List.copyOf(definitions);
        usages = List.copyOf(usages);
        skippedCategories = List.copyOf(skippedCategories);
        Objects.requireNonNull(completeness, "completeness");
        diagnostics = List.copyOf(diagnostics);
        recoveryActions = List.copyOf(recoveryActions);
        dependencyIndex = Objects.requireNonNull(dependencyIndex, "dependencyIndex");
    }

    public boolean matches(SFMUsageAtPositionRequest request) {
        Objects.requireNonNull(request, "request");
        return requestId == request.requestId()
                && requestGeneration == request.requestGeneration()
                && workspaceGeneration == request.workspace().workspaceGeneration()
                && context.branch().equals(request.workspace().branch())
                && context.classpathMode() == request.workspace().classpathMode()
                && context.sourceRoots().equals(request.workspace().sourceRoots())
                && context.classpathFingerprint().equals(request.workspace().classpathFingerprint())
                && document.equals(new SFMDefinitionResult.DocumentIdentity(
                        request.document().address(),
                        request.document().rootId(),
                        request.document().rootRelativePath(),
                        request.document().reportPath(),
                        request.document().sourceSet(),
                        request.document().contentHash(),
                        request.document().diskContentHash()
                ))
                && position.equals(request.position());
    }
}
