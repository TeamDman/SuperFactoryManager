package ca.teamdman.sfm.client.explorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Pure evaluator over one immutable selection and relation capture. */
public final class SFMPathExpressionResolver {
    private SFMPathExpressionResolver() {
    }

    public static SFMPathExpressionResolution resolve(
            SFMPathExpression expression,
            SFMSelectionRepository selections,
            SFMChildRelationRepository relations
    ) {
        if (expression == null) throw new NullPointerException("expression");
        if (selections == null) throw new NullPointerException("selections");
        if (relations == null) throw new NullPointerException("relations");
        SFMSelectionRepository.StateSnapshot selectionSnapshot = selections.stateSnapshot();
        SFMChildRelationRepository.Snapshot relationSnapshot = relations.snapshot();
        return resolve(expression, selectionSnapshot, relationSnapshot);
    }

    public static SFMPathExpressionResolution resolve(
            SFMPathExpression expression,
            SFMSelectionRepository.StateSnapshot selections,
            SFMChildRelationRepository.Snapshot relations
    ) {
        if (expression == null) throw new NullPointerException("expression");
        if (selections == null) throw new NullPointerException("selections");
        if (relations == null) throw new NullPointerException("relations");
        Evaluation evaluation = new Evaluation(selections, relations);
        EvaluationResult result = evaluation.evaluate(expression);
        return new SFMPathExpressionResolution(
                expression.canonical(),
                selections.generation(),
                relations.relation().id(),
                relations.statusGeneration(),
                result.completeness(),
                result.paths(),
                evaluation.capturedHeads,
                evaluation.diagnostics
        );
    }

    private record EvaluationResult(
            SFMPathExpressionResolution.Completeness completeness,
            TreeSet<SFMPath> paths
    ) {
    }

    private static final class Evaluation {
        private final SFMSelectionRepository.StateSnapshot selections;
        private final SFMChildRelationRepository.Snapshot relations;
        private final TreeMap<SFMSelectionId, Long> capturedHeads = new TreeMap<>(
                Comparator.comparing(SFMSelectionId::value)
        );
        private final ArrayList<SFMSelectorResolution.Diagnostic> diagnostics = new ArrayList<>();

        private Evaluation(
                SFMSelectionRepository.StateSnapshot selections,
                SFMChildRelationRepository.Snapshot relations
        ) {
            this.selections = selections;
            this.relations = relations;
        }

        private EvaluationResult evaluate(SFMPathExpression expression) {
            if (expression instanceof SFMPathExpression.Literal literal) {
                return complete(Set.of(literal.path()));
            }
            if (expression instanceof SFMPathExpression.Members members) {
                return members(members.selectionSelector());
            }
            if (expression instanceof SFMPathExpression.Children children) {
                return children(evaluate(children.expression()));
            }
            if (expression instanceof SFMPathExpression.Union union) {
                return union.expressions().stream().map(this::evaluate).reduce(
                        complete(Set.of()),
                        this::union
                );
            }
            if (expression instanceof SFMPathExpression.Intersection intersection) {
                List<EvaluationResult> operands = intersection.expressions().stream()
                        .map(this::evaluate)
                        .toList();
                TreeSet<SFMPath> paths = new TreeSet<>(operands.get(0).paths());
                operands.subList(1, operands.size()).forEach(operand -> paths.retainAll(operand.paths()));
                return new EvaluationResult(completeness(operands), paths);
            }
            if (expression instanceof SFMPathExpression.Difference difference) {
                EvaluationResult include = evaluate(difference.include());
                ArrayList<EvaluationResult> operands = new ArrayList<>();
                operands.add(include);
                TreeSet<SFMPath> paths = new TreeSet<>(include.paths());
                for (SFMPathExpression excluded : difference.exclude()) {
                    EvaluationResult result = evaluate(excluded);
                    operands.add(result);
                    paths.removeAll(result.paths());
                }
                return new EvaluationResult(completeness(operands), paths);
            }
            throw new IllegalStateException("Unsupported path-expression node: " + expression.getClass().getName());
        }

        private EvaluationResult members(SFMEntitySelector selector) {
            SFMSelectorResolution<SFMSelectionId> resolved = SFMEntitySelectorResolver.resolve(
                    selector,
                    SFMSelectorDomains.selections(SFMSelectorRepository.immutable(selectionSelectorSnapshot()))
            );
            diagnostics.addAll(resolved.diagnostics());
            if (!resolved.complete()) {
                return new EvaluationResult(SFMPathExpressionResolution.Completeness.INVALID, new TreeSet<>());
            }
            TreeSet<SFMPath> paths = new TreeSet<>();
            for (SFMSelectionId id : resolved.identities()) {
                SFMSelection selection = selections.selections().get(id);
                if (selection == null) {
                    invalid(
                            "selection.capture-missing",
                            "Captured selector identity has no selection state: " + id.value()
                    );
                    return new EvaluationResult(
                            SFMPathExpressionResolution.Completeness.INVALID,
                            new TreeSet<>()
                    );
                }
                SFMSelectionRevision revision = selections.revisions().get(selection.headRevisionId());
                if (revision == null || !revision.selectionId().equals(id)) {
                    invalid(
                            "selection.head-missing",
                            "Captured selection head is absent or belongs to another selection: " + id.value()
                    );
                    return new EvaluationResult(
                            SFMPathExpressionResolution.Completeness.INVALID,
                            new TreeSet<>()
                    );
                }
                capturedHeads.put(id, revision.id());
                paths.addAll(revision.members());
            }
            return new EvaluationResult(SFMPathExpressionResolution.Completeness.COMPLETE, paths);
        }

        private EvaluationResult children(EvaluationResult parents) {
            TreeSet<SFMPath> children = new TreeSet<>();
            SFMPathExpressionResolution.Completeness completeness = parents.completeness();
            for (SFMPath parent : parents.paths()) {
                EvaluationResult resolved = parent.kind() == SFMPath.Kind.SELECTION
                        ? selectionChildren(parent)
                        : relationChildren(parent);
                children.addAll(resolved.paths());
                completeness = merge(completeness, resolved.completeness());
            }
            return new EvaluationResult(completeness, children);
        }

        private EvaluationResult selectionChildren(SFMPath parent) {
            SFMSelectionId id = new SFMSelectionId(parent.authority());
            SFMSelection selection = selections.selections().get(id);
            if (selection == null) {
                for (SFMSelection candidate : selections.selections().values()) {
                    if (candidate.name().filter(parent.authority()::equals).isPresent()) {
                        id = candidate.id();
                        selection = candidate;
                        break;
                    }
                }
            }
            if (selection == null) {
                invalid(
                        "selection.missing",
                        "No selection has the stable id or name " + parent.authority()
                );
                return invalidResult();
            }
            long revisionId;
            if (parent.revision().isPresent()) {
                Optional<Long> parsed = parseRevisionToken(parent.revision().orElseThrow());
                if (parsed.isEmpty()) {
                    invalid(
                            "selection.invalid-revision-token",
                            "Selection revision must use revision-<positive integer>"
                    );
                    return invalidResult();
                }
                revisionId = parsed.orElseThrow();
            } else {
                revisionId = selection.headRevisionId();
                capturedHeads.put(id, revisionId);
            }
            SFMSelectionRevision revision = selections.revisions().get(revisionId);
            if (revision == null || !revision.selectionId().equals(id)) {
                invalid(
                        "selection.revision-missing",
                        "The requested revision does not belong to selection " + id.value()
                );
                return invalidResult();
            }
            return complete(revision.members());
        }

        private EvaluationResult relationChildren(SFMPath parent) {
            TreeSet<SFMPath> children = new TreeSet<>(relations.relation().childrenOf(parent));
            SFMChildRelationRepository.PageState pageState = relations.pageStates().get(parent);
            if (pageState == null) {
                diagnostics.add(new SFMSelectorResolution.Diagnostic(
                        "relation.children-unmaterialized",
                        SFMSelectorResolution.Severity.INFO,
                        "Immediate children have not been materialized for " + parent.canonical()
                ));
                return new EvaluationResult(SFMPathExpressionResolution.Completeness.PARTIAL, children);
            }
            pageState.diagnostics().forEach(message -> diagnostics.add(
                    new SFMSelectorResolution.Diagnostic(
                            "relation.resolver-diagnostic",
                            SFMSelectorResolution.Severity.WARNING,
                            message
                    )
            ));
            SFMPathExpressionResolution.Completeness completeness =
                    pageState.materialization()
                            == SFMChildRelationRepository.PageState.Materialization.MATERIALIZED
                            && pageState.completeness() == SFMChildPage.Completeness.COMPLETE
                            ? SFMPathExpressionResolution.Completeness.COMPLETE
                            : SFMPathExpressionResolution.Completeness.PARTIAL;
            if (completeness == SFMPathExpressionResolution.Completeness.PARTIAL) {
                diagnostics.add(new SFMSelectorResolution.Diagnostic(
                        "relation.children-partial",
                        SFMSelectorResolution.Severity.INFO,
                        "More immediate children are available for " + parent.canonical()
                ));
            }
            return new EvaluationResult(completeness, children);
        }

        private SFMSelectorRepositorySnapshot<SFMSelectionId> selectionSelectorSnapshot() {
            ArrayList<SFMSelectorRepositoryEntry<SFMSelectionId>> entries = new ArrayList<>();
            selections.selections().values().forEach(selection -> entries.add(
                    new SFMSelectorRepositoryEntry<>(selection.id(), selection.name(), false)
            ));
            return new SFMSelectorRepositorySnapshot<>(selections.generation(), entries);
        }

        private EvaluationResult union(EvaluationResult left, EvaluationResult right) {
            TreeSet<SFMPath> paths = new TreeSet<>(left.paths());
            paths.addAll(right.paths());
            return new EvaluationResult(merge(left.completeness(), right.completeness()), paths);
        }

        private void invalid(String code, String message) {
            diagnostics.add(new SFMSelectorResolution.Diagnostic(
                    code,
                    SFMSelectorResolution.Severity.ERROR,
                    message
            ));
        }

        private EvaluationResult invalidResult() {
            return new EvaluationResult(SFMPathExpressionResolution.Completeness.INVALID, new TreeSet<>());
        }

        private static EvaluationResult complete(Set<SFMPath> paths) {
            return new EvaluationResult(
                    SFMPathExpressionResolution.Completeness.COMPLETE,
                    new TreeSet<>(paths)
            );
        }

        private static SFMPathExpressionResolution.Completeness completeness(
                List<EvaluationResult> operands
        ) {
            SFMPathExpressionResolution.Completeness answer =
                    SFMPathExpressionResolution.Completeness.COMPLETE;
            for (EvaluationResult operand : operands) answer = merge(answer, operand.completeness());
            return answer;
        }

        private static SFMPathExpressionResolution.Completeness merge(
                SFMPathExpressionResolution.Completeness left,
                SFMPathExpressionResolution.Completeness right
        ) {
            if (left == SFMPathExpressionResolution.Completeness.INVALID
                    || right == SFMPathExpressionResolution.Completeness.INVALID) {
                return SFMPathExpressionResolution.Completeness.INVALID;
            }
            if (left == SFMPathExpressionResolution.Completeness.PARTIAL
                    || right == SFMPathExpressionResolution.Completeness.PARTIAL) {
                return SFMPathExpressionResolution.Completeness.PARTIAL;
            }
            return SFMPathExpressionResolution.Completeness.COMPLETE;
        }

        private static Optional<Long> parseRevisionToken(String token) {
            if (!token.startsWith("revision-") || token.length() == "revision-".length()) {
                return Optional.empty();
            }
            try {
                long value = Long.parseLong(token.substring("revision-".length()));
                return value > 0 ? Optional.of(value) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
    }
}
