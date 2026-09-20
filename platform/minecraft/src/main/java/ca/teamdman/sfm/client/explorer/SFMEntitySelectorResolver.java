package ca.teamdman.sfm.client.explorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Pure, deterministic evaluator for the typed {@link SFMEntitySelector} AST. */
public final class SFMEntitySelectorResolver {
    private SFMEntitySelectorResolver() {
    }

    public static <I> SFMSelectorResolution<I> resolve(
            SFMEntitySelector selector,
            SFMSelectorDomain<I> domain
    ) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(domain, "domain");

        // Capture exactly once. Every node in this evaluation observes the
        // same immutable repository generation.
        SFMSelectorRepositorySnapshot<I> snapshot = Objects.requireNonNull(
                domain.repository().snapshot(),
                "selector repository snapshot"
        );

        if (selector.domain() != domain.domain()) {
            return failure(
                    selector,
                    domain,
                    snapshot.generation(),
                    SFMSelectorResolution.Completeness.INVALID,
                    new SFMSelectorResolution.Diagnostic(
                            "selector.domain-mismatch",
                            SFMSelectorResolution.Severity.ERROR,
                            "Selector domain does not match the typed repository domain"
                    )
            );
        }
        if (!domain.supportsResolution()) {
            return failure(
                    selector,
                    domain,
                    snapshot.generation(),
                    SFMSelectorResolution.Completeness.UNSUPPORTED,
                    domain.unsupportedDiagnostic().orElseThrow()
            );
        }

        Comparator<I> comparator = Comparator.comparing(domain::stableText);
        LinkedHashMap<String, SFMSelectorRepositoryEntry<I>> byStableText = new LinkedHashMap<>();
        for (SFMSelectorRepositoryEntry<I> entry : snapshot.entries()) {
            String stableText = domain.stableText(entry.id());
            if (byStableText.putIfAbsent(stableText, entry) != null) {
                return failure(
                        selector,
                        domain,
                        snapshot.generation(),
                        SFMSelectorResolution.Completeness.INVALID,
                        new SFMSelectorResolution.Diagnostic(
                                "selector.duplicate-stable-id",
                                SFMSelectorResolution.Severity.ERROR,
                                "Repository snapshot maps more than one typed identity to " + stableText
                        )
                );
            }
        }

        ArrayList<SFMSelectorResolution.Diagnostic> diagnostics = new ArrayList<>();
        Evaluation<I> evaluation = new Evaluation<>(domain, byStableText, comparator, diagnostics);
        TreeSet<I> identities = evaluation.evaluate(selector.node());
        return new SFMSelectorResolution<>(
                domain.domain(),
                selector.canonical(),
                snapshot.generation(),
                SFMSelectorResolution.Completeness.COMPLETE,
                List.copyOf(identities),
                diagnostics
        );
    }

    private static <I> SFMSelectorResolution<I> failure(
            SFMEntitySelector selector,
            SFMSelectorDomain<I> domain,
            long generation,
            SFMSelectorResolution.Completeness completeness,
            SFMSelectorResolution.Diagnostic diagnostic
    ) {
        return new SFMSelectorResolution<>(
                domain.domain(),
                selector.canonical(),
                generation,
                completeness,
                List.of(),
                List.of(diagnostic)
        );
    }

    private static final class Evaluation<I> {
        private final SFMSelectorDomain<I> domain;
        private final Map<String, SFMSelectorRepositoryEntry<I>> entries;
        private final Comparator<I> comparator;
        private final List<SFMSelectorResolution.Diagnostic> diagnostics;

        private Evaluation(
                SFMSelectorDomain<I> domain,
                Map<String, SFMSelectorRepositoryEntry<I>> entries,
                Comparator<I> comparator,
                List<SFMSelectorResolution.Diagnostic> diagnostics
        ) {
            this.domain = domain;
            this.entries = entries;
            this.comparator = comparator;
            this.diagnostics = diagnostics;
        }

        private TreeSet<I> evaluate(SFMEntitySelector.Node node) {
            if (node instanceof SFMEntitySelector.Id id) {
                return exact(id.value());
            }
            if (node instanceof SFMEntitySelector.Name name) {
                return named(name.value());
            }
            if (node instanceof SFMEntitySelector.Focused) {
                return focused();
            }
            if (node instanceof SFMEntitySelector.All) {
                return all();
            }
            if (node instanceof SFMEntitySelector.Union union) {
                TreeSet<I> answer = empty();
                union.selectors().forEach(child -> answer.addAll(evaluate(child)));
                return answer;
            }
            if (node instanceof SFMEntitySelector.Intersection intersection) {
                TreeSet<I> answer = evaluate(intersection.selectors().get(0));
                for (int index = 1; index < intersection.selectors().size(); index++) {
                    answer.retainAll(evaluate(intersection.selectors().get(index)));
                }
                return answer;
            }
            if (node instanceof SFMEntitySelector.Difference difference) {
                TreeSet<I> answer = evaluate(difference.include());
                difference.exclude().forEach(child -> answer.removeAll(evaluate(child)));
                return answer;
            }
            throw new IllegalStateException("Unsupported selector node: " + node.getClass().getName());
        }

        private TreeSet<I> exact(String stableText) {
            TreeSet<I> answer = empty();
            SFMSelectorRepositoryEntry<I> entry = entries.get(stableText);
            if (entry == null) {
                diagnostics.add(new SFMSelectorResolution.Diagnostic(
                        "selector.exact-miss",
                        SFMSelectorResolution.Severity.WARNING,
                        "No entity has the exact stable id " + stableText
                ));
            } else {
                answer.add(entry.id());
            }
            return answer;
        }

        private TreeSet<I> named(String name) {
            if (!domain.supportsNames()) {
                throw new IllegalStateException("Name node reached a domain that does not support names");
            }
            TreeSet<I> answer = empty();
            entries.values().stream()
                    .filter(entry -> entry.name().filter(name::equals).isPresent())
                    .map(SFMSelectorRepositoryEntry::id)
                    .forEach(answer::add);
            if (answer.isEmpty()) {
                diagnostics.add(new SFMSelectorResolution.Diagnostic(
                        "selector.name-miss",
                        SFMSelectorResolution.Severity.WARNING,
                        "No selection has the name " + name
                ));
            } else if (answer.size() > 1) {
                diagnostics.add(new SFMSelectorResolution.Diagnostic(
                        "selector.name-multiple",
                        SFMSelectorResolution.Severity.INFO,
                        "Selection name resolved to " + answer.size() + " identities"
                ));
            }
            return answer;
        }

        private TreeSet<I> focused() {
            if (!domain.supportsFocus()) {
                throw new IllegalStateException("Focused node reached a domain that does not support focus");
            }
            TreeSet<I> answer = empty();
            entries.values().stream()
                    .filter(SFMSelectorRepositoryEntry::focused)
                    .map(SFMSelectorRepositoryEntry::id)
                    .forEach(answer::add);
            if (answer.isEmpty()) {
                diagnostics.add(new SFMSelectorResolution.Diagnostic(
                        "selector.focused-miss",
                        SFMSelectorResolution.Severity.WARNING,
                        "No entity is focused in the captured repository generation"
                ));
            }
            return answer;
        }

        private TreeSet<I> all() {
            TreeSet<I> answer = empty();
            entries.values().stream().map(SFMSelectorRepositoryEntry::id).forEach(answer::add);
            return answer;
        }

        private TreeSet<I> empty() {
            return new TreeSet<>(comparator);
        }
    }
}
