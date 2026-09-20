package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure, bounded RCS-3 corpus index and selector evaluator.
 *
 * <p>The release-review wire format deliberately keeps its frozen literal/set
 * selection algebra. Syntax, symbol, and diff evidence enter through the
 * prepared in-memory boundary below. No parser is run here and no ambiguous
 * candidate is selected merely because it sorted first.</p>
 */
public final class SFMReleaseReviewEvaluator {
    private static final Comparator<SFMReleaseReviewV1.AddressedRange> RANGE_ORDER = Comparator
            .comparing(SFMReleaseReviewV1.AddressedRange::documentRevisionId)
            .thenComparingInt(SFMReleaseReviewV1.AddressedRange::startByte)
            .thenComparingInt(SFMReleaseReviewV1.AddressedRange::endByte);
    private static final Comparator<SFMReleaseReviewV1.InvalidationKey> KEY_ORDER = Comparator
            .comparing(SFMReleaseReviewV1.InvalidationKey::owner)
            .thenComparing(SFMReleaseReviewV1.InvalidationKey::generation)
            .thenComparing(SFMReleaseReviewV1.InvalidationKey::fingerprint);

    private SFMReleaseReviewEvaluator() {
    }

    public enum DiffSide { BEFORE, AFTER }

    /** A bounded evaluation scope. Empty dimensions mean all indexed values. */
    public record Scope(
            List<String> laneIds,
            List<String> paths,
            List<String> languages,
            List<DiffSide> diffSides
    ) {
        public Scope {
            laneIds = canonicalStrings(laneIds, "scope lane");
            paths = canonicalStrings(paths, "scope path");
            languages = canonicalStrings(languages, "scope language");
            diffSides = Objects.requireNonNull(diffSides, "diffSides").stream()
                    .map(value -> Objects.requireNonNull(value, "diff side"))
                    .distinct().sorted().toList();
        }

        public static Scope all() {
            return new Scope(List.of(), List.of(), List.of(), List.of());
        }

        boolean includes(DocumentRecord document) {
            return (laneIds.isEmpty() || laneIds.contains(document.laneId()))
                    && (paths.isEmpty() || paths.contains(document.path()))
                    && (languages.isEmpty() || languages.contains(document.language()))
                    && (diffSides.isEmpty() || diffSides.contains(document.side()));
        }

        boolean includes(PreparedScope evidence) {
            return (laneIds.isEmpty() || laneIds.contains(evidence.laneId()))
                    && (paths.isEmpty() || paths.contains(evidence.path()))
                    && (languages.isEmpty() || languages.contains(evidence.language()))
                    && (diffSides.isEmpty() || diffSides.contains(evidence.side()));
        }
    }

    public record Limits(long maxDocuments, long maxCandidates, long maxComparedBytes, long maxOperations) {
        public Limits {
            if (maxDocuments <= 0 || maxCandidates <= 0 || maxComparedBytes <= 0 || maxOperations <= 0) {
                throw new IllegalArgumentException("Evaluation limits must be positive");
            }
        }

        public static Limits defaults() {
            return new Limits(20_000, 200_000, 256L * 1024L * 1024L, 2_000_000);
        }
    }

    public record Work(long documentsVisited, long candidatesVisited, long comparedBytes, long operations) {
    }

    public record Evaluation(SFMReleaseReviewV1.EvaluationResult result, Work work) {
        public Evaluation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(work, "work");
        }
    }

    /** Coverage declaration for prepared semantic/diff evidence. */
    public record PreparedScope(
            String provider,
            String generation,
            String fingerprint,
            String laneId,
            String path,
            String language,
            DiffSide side,
            boolean syntaxComplete,
            boolean symbolsComplete,
            boolean diffComplete,
            List<String> diagnostics
    ) {
        public PreparedScope {
            provider = requireText(provider, "scope provider");
            generation = requireText(generation, "scope generation");
            fingerprint = requireSha256(fingerprint, "scope fingerprint");
            laneId = requireText(laneId, "scope lane");
            path = requireText(path, "scope path");
            language = requireText(language, "scope language");
            Objects.requireNonNull(side, "side");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /** One complete candidate for a syntax-region or symbol semantic key. */
    public record PreparedSemanticCandidate(
            String provider,
            String generation,
            String fingerprint,
            String laneId,
            String path,
            String language,
            DiffSide side,
            SFMReleaseReviewV1.SelectorKind kind,
            String semanticKey,
            List<SFMReleaseReviewV1.AddressedRange> ranges,
            String contentSha256,
            List<String> diagnostics
    ) {
        public PreparedSemanticCandidate {
            provider = requireText(provider, "semantic provider");
            generation = requireText(generation, "semantic generation");
            fingerprint = requireSha256(fingerprint, "semantic fingerprint");
            laneId = requireText(laneId, "semantic lane");
            path = requireText(path, "semantic path");
            language = requireText(language, "semantic language");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(kind, "kind");
            if (kind == SFMReleaseReviewV1.SelectorKind.LITERAL
                    || kind == SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION) {
                throw new IllegalArgumentException("Prepared semantic evidence requires a semantic selector kind");
            }
            semanticKey = requireText(semanticKey, "semantic key");
            ranges = canonicalRanges(ranges, "semantic range");
            if (ranges.isEmpty()) throw new IllegalArgumentException("Semantic candidate requires a range");
            contentSha256 = requireSha256(contentSha256, "semantic content hash");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /** One prepared diff-region candidate; diff computation remains outside this evaluator. */
    public record PreparedDiffCandidate(
            String provider,
            String generation,
            String fingerprint,
            String laneId,
            String path,
            String language,
            DiffSide side,
            String diffKey,
            List<SFMReleaseReviewV1.AddressedRange> ranges,
            String contentSha256,
            List<String> diagnostics
    ) {
        public PreparedDiffCandidate {
            provider = requireText(provider, "diff provider");
            generation = requireText(generation, "diff generation");
            fingerprint = requireSha256(fingerprint, "diff fingerprint");
            laneId = requireText(laneId, "diff lane");
            path = requireText(path, "diff path");
            language = requireText(language, "diff language");
            Objects.requireNonNull(side, "side");
            diffKey = requireText(diffKey, "diff key");
            ranges = canonicalRanges(ranges, "diff range");
            if (ranges.isEmpty()) throw new IllegalArgumentException("Diff candidate requires a range");
            contentSha256 = requireSha256(contentSha256, "diff content hash");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record PreparedEvidence(
            List<PreparedScope> scopes,
            List<PreparedSemanticCandidate> semanticCandidates,
            List<PreparedDiffCandidate> diffCandidates
    ) {
        public PreparedEvidence {
            scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
            semanticCandidates = List.copyOf(Objects.requireNonNull(semanticCandidates, "semanticCandidates"));
            diffCandidates = List.copyOf(Objects.requireNonNull(diffCandidates, "diffCandidates"));
        }

        public static PreparedEvidence empty() {
            return new PreparedEvidence(List.of(), List.of(), List.of());
        }
    }

    public record Witness(List<SFMReleaseReviewV1.AddressedRange> ranges, String contentSha256) {
        public Witness {
            ranges = canonicalRanges(ranges, "witness range");
            if (ranges.isEmpty()) throw new IllegalArgumentException("A witness requires a range");
            contentSha256 = requireSha256(contentSha256, "witness content hash");
        }
    }

    /** In-memory evaluator algebra; only Literal/Union/Intersection/Difference are persisted in v1. */
    public sealed interface Rule permits LiteralRule, TextRule, SemanticRule, DiffRegionRule,
            UnionRule, IntersectionRule, DifferenceRule {
    }

    public record LiteralRule(SFMReviewSessionV1.LiteralUtf8Range literal) implements Rule {
        public LiteralRule { Objects.requireNonNull(literal, "literal"); }
    }

    /** Text matching with an explicit UTF-8 witness, useful when the original revision is unavailable. */
    public record TextRule(byte[] utf8Witness) implements Rule {
        public TextRule {
            utf8Witness = Arrays.copyOf(Objects.requireNonNull(utf8Witness, "utf8Witness"), utf8Witness.length);
        }

        @Override
        public byte[] utf8Witness() {
            return Arrays.copyOf(utf8Witness, utf8Witness.length);
        }
    }

    public record SemanticRule(
            SFMReleaseReviewV1.SelectorKind kind,
            String semanticKey,
            Optional<Witness> witness
    ) implements Rule {
        public SemanticRule {
            Objects.requireNonNull(kind, "kind");
            if (kind == SFMReleaseReviewV1.SelectorKind.LITERAL
                    || kind == SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION) {
                throw new IllegalArgumentException("Semantic rule requires a semantic kind");
            }
            semanticKey = requireText(semanticKey, "semantic key");
            witness = Objects.requireNonNull(witness, "witness");
        }
    }

    public record DiffRegionRule(String diffKey, DiffSide side, Optional<Witness> witness) implements Rule {
        public DiffRegionRule {
            diffKey = requireText(diffKey, "diff key");
            Objects.requireNonNull(side, "side");
            witness = Objects.requireNonNull(witness, "witness");
        }
    }

    public record UnionRule(List<Rule> rules) implements Rule {
        public UnionRule {
            rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
            if (rules.isEmpty()) throw new IllegalArgumentException("Union requires an operand");
        }
    }

    public record IntersectionRule(List<Rule> rules) implements Rule {
        public IntersectionRule {
            rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
            if (rules.isEmpty()) throw new IllegalArgumentException("Intersection requires an operand");
        }
    }

    public record DifferenceRule(Rule include, List<Rule> exclude) implements Rule {
        public DifferenceRule {
            Objects.requireNonNull(include, "include");
            exclude = List.copyOf(Objects.requireNonNull(exclude, "exclude"));
        }
    }

    private record DocumentRecord(
            String id,
            String laneId,
            String path,
            String language,
            DiffSide side,
            String sha256,
            SFMReleaseReviewV1.Materialization materialization,
            Optional<byte[]> bytes,
            String sourceOwner,
            String sourceLocator
    ) {
        DocumentRecord {
            bytes = bytes.map(value -> Arrays.copyOf(value, value.length));
        }

        @Override
        public Optional<byte[]> bytes() {
            return bytes.map(value -> Arrays.copyOf(value, value.length));
        }
    }

    private record SemanticLookup(SFMReleaseReviewV1.SelectorKind kind, String key) {
    }

    /** Immutable deterministic index. */
    public static final class Index {
        private final List<DocumentRecord> documents;
        private final Map<String, DocumentRecord> documentsByRevision;
        private final Map<String, List<DocumentRecord>> documentsByLane;
        private final Map<String, List<DocumentRecord>> documentsByPath;
        private final Map<String, List<DocumentRecord>> documentsByLanguage;
        private final Map<String, List<DocumentRecord>> documentsByContentHash;
        private final Map<SemanticLookup, List<PreparedSemanticCandidate>> semantics;
        private final Map<String, List<PreparedDiffCandidate>> diffs;
        private final List<PreparedScope> scopes;
        private final Limits limits;

        private Index(
                List<DocumentRecord> documents,
                Map<SemanticLookup, List<PreparedSemanticCandidate>> semantics,
                Map<String, List<PreparedDiffCandidate>> diffs,
                List<PreparedScope> scopes,
                Limits limits
        ) {
            this.documents = List.copyOf(documents);
            this.documentsByRevision = uniqueDocuments(documents);
            this.documentsByLane = groupDocuments(documents, DocumentRecord::laneId);
            this.documentsByPath = groupDocuments(documents, DocumentRecord::path);
            this.documentsByLanguage = groupDocuments(documents, DocumentRecord::language);
            this.documentsByContentHash = groupDocuments(documents, DocumentRecord::sha256);
            this.semantics = Map.copyOf(semantics);
            this.diffs = Map.copyOf(diffs);
            this.scopes = List.copyOf(scopes);
            this.limits = limits;
        }

        public static Index build(SFMReleaseReviewV1 review, PreparedEvidence prepared, Limits limits) {
            Objects.requireNonNull(review, "review");
            Objects.requireNonNull(prepared, "prepared");
            Objects.requireNonNull(limits, "limits");
            SFMReleaseReviewCorpus corpus = SFMReleaseReviewCorpus.from(review);
            Map<String, String> languages = languages(review.reviewUnits());
            ArrayList<DocumentRecord> documents = new ArrayList<>();
            for (SFMReleaseReviewCorpus.DocumentView view : corpus.documents()) {
                SFMReleaseReviewV1.CorpusDocument binding = view.binding();
                DiffSide side = binding.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.BEFORE
                        ? DiffSide.BEFORE : DiffSide.AFTER;
                String language = languages.getOrDefault(languageAddress(
                        binding.laneId(), binding.path(), side), "unknown");
                documents.add(new DocumentRecord(
                        binding.documentRevisionId(), binding.laneId(), binding.path(), language, side,
                        binding.sha256(), binding.materialization(), view.utf8Bytes(),
                        binding.sourceOwner(), binding.sourceLocator()
                ));
            }
            documents.sort(documentOrder());

            TreeMap<SemanticLookup, List<PreparedSemanticCandidate>> semantics = new TreeMap<>(Comparator
                    .comparing((SemanticLookup value) -> value.kind().ordinal())
                    .thenComparing(SemanticLookup::key));
            prepared.semanticCandidates().stream().sorted(semanticOrder()).forEach(candidate ->
                    semantics.computeIfAbsent(new SemanticLookup(candidate.kind(), candidate.semanticKey()),
                            ignored -> new ArrayList<>()).add(candidate));
            semantics.replaceAll((ignored, values) -> List.copyOf(values));

            TreeMap<String, List<PreparedDiffCandidate>> diffs = new TreeMap<>();
            prepared.diffCandidates().stream().sorted(diffOrder()).forEach(candidate ->
                    diffs.computeIfAbsent(candidate.diffKey(), ignored -> new ArrayList<>()).add(candidate));
            diffs.replaceAll((ignored, values) -> List.copyOf(values));

            List<PreparedScope> scopes = prepared.scopes().stream().sorted(scopeOrder()).toList();
            return new Index(documents, semantics, diffs, scopes, limits);
        }

        public int documentCount() { return documents.size(); }
        public List<String> documentIdsForLane(String lane) { return ids(documentsByLane.getOrDefault(lane, List.of())); }
        public List<String> documentIdsForPath(String path) { return ids(documentsByPath.getOrDefault(path, List.of())); }
        public List<String> documentIdsForLanguage(String language) { return ids(documentsByLanguage.getOrDefault(language, List.of())); }
        public List<String> documentIdsForContentHash(String hash) { return ids(documentsByContentHash.getOrDefault(hash, List.of())); }

        public Evaluation evaluate(String selectorId, SFMReviewSessionV1.SelectionRule rule, Scope scope) {
            return evaluate(selectorId, adapt(rule), scope);
        }

        public Evaluation evaluate(String selectorId, Rule rule, Scope scope) {
            selectorId = requireText(selectorId, "selector id");
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(scope, "scope");
            Budget budget = new Budget(limits);
            Node node;
            try {
                node = evaluateRule(rule, scope, budget);
            } catch (BudgetExceeded exceeded) {
                node = new Node(
                        SFMReleaseReviewV1.EvaluationStatus.INVALID, List.of(), List.of(),
                        exceeded.keys, List.of("review.evaluation-budget-exceeded: " + exceeded.getMessage())
                );
            } catch (IllegalArgumentException exception) {
                node = new Node(
                        SFMReleaseReviewV1.EvaluationStatus.INVALID, List.of(), List.of(), List.of(),
                        List.of("review.invalid-selector: " + exception.getMessage())
                );
            }
            return new Evaluation(new SFMReleaseReviewV1.EvaluationResult(
                    selectorId,
                    node.status,
                    canonicalRanges(node.ranges, "evaluation range"),
                    canonicalRanges(node.candidates, "evaluation candidate"),
                    canonicalKeys(node.keys),
                    node.diagnostics.stream().distinct().sorted().toList()
            ), budget.snapshot());
        }

        /** Evaluates a selected RCS-2 proposal through literal or prepared semantic evidence. */
        public Evaluation evaluate(String selectorId, SFMReleaseReviewV1.SelectorProposal proposal, Scope scope) {
            Objects.requireNonNull(proposal, "proposal");
            if (proposal.kind() == SFMReleaseReviewV1.SelectorKind.LITERAL
                    || proposal.kind() == SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION
                    || proposal.semanticKey().isEmpty()) {
                return evaluate(selectorId, proposal.selectionRule(), scope);
            }
            Optional<Witness> witness = witness(proposal.selectionRule());
            return evaluate(selectorId, new SemanticRule(
                    proposal.kind(), proposal.semanticKey().orElseThrow(), witness), scope);
        }

        private Optional<Witness> witness(SFMReviewSessionV1.SelectionRule rule) {
            List<SFMReviewSessionV1.LiteralUtf8Range> leaves = literalLeaves(rule);
            if (leaves.isEmpty()) return Optional.empty();
            ArrayList<SFMReleaseReviewV1.AddressedRange> ranges = new ArrayList<>();
            ArrayList<byte[]> parts = new ArrayList<>();
            for (SFMReviewSessionV1.LiteralUtf8Range leaf : leaves) {
                DocumentRecord document = documentsByRevision.get(leaf.documentRevisionId());
                if (document == null || document.bytes().isEmpty()) return Optional.empty();
                byte[] bytes = document.bytes().orElseThrow();
                validateRange(bytes, leaf.startByte(), leaf.endByte());
                byte[] part = Arrays.copyOfRange(bytes, leaf.startByte(), leaf.endByte());
                if (!sha256(part).equals(leaf.selectedTextSha256())) return Optional.empty();
                ranges.add(new SFMReleaseReviewV1.AddressedRange(
                        leaf.documentRevisionId(), leaf.startByte(), leaf.endByte()));
                parts.add(part);
            }
            return Optional.of(new Witness(ranges, hashParts(parts)));
        }

        private Node evaluateRule(Rule rule, Scope scope, Budget budget) {
            budget.operation();
            if (rule instanceof LiteralRule literal) return literal(literal.literal(), scope, budget);
            if (rule instanceof TextRule text) return text(text.utf8Witness(), scope, budget);
            if (rule instanceof SemanticRule semantic) return semantic(semantic, scope, budget);
            if (rule instanceof DiffRegionRule diff) return diff(diff, scope, budget);
            if (rule instanceof UnionRule union) {
                return combine(union.rules(), scope, budget, SetOperation.UNION);
            }
            if (rule instanceof IntersectionRule intersection) {
                return combine(intersection.rules(), scope, budget, SetOperation.INTERSECTION);
            }
            if (rule instanceof DifferenceRule difference) {
                Node answer = evaluateRule(difference.include(), scope, budget);
                for (Rule exclude : difference.exclude()) {
                    answer = set(answer, evaluateRule(exclude, scope, budget), SetOperation.DIFFERENCE, budget);
                }
                return answer;
            }
            throw new IllegalArgumentException("Unknown evaluator rule " + rule.getClass().getName());
        }

        private Node literal(SFMReviewSessionV1.LiteralUtf8Range literal, Scope scope, Budget budget) {
            if (literal.startByte() < 0 || literal.endByte() < literal.startByte()
                    || !isSha256(literal.documentSha256()) || !isSha256(literal.selectedTextSha256())) {
                return invalid("review.literal-invalid: malformed range or hash");
            }
            DocumentRecord original = documentsByRevision.get(literal.documentRevisionId());
            byte[] witness = null;
            ArrayList<SFMReleaseReviewV1.InvalidationKey> keys = new ArrayList<>();
            if (original != null) {
                budget.document(keys, original);
                if (original.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE
                        || original.bytes().isEmpty()) {
                    return scopeMissing(keys, "review.literal-scope-missing: source document is not complete");
                }
                byte[] bytes = original.bytes().orElseThrow();
                try {
                    validateRange(bytes, literal.startByte(), literal.endByte());
                    witness = Arrays.copyOfRange(bytes, literal.startByte(), literal.endByte());
                    budget.bytes(witness.length, keys);
                } catch (IllegalArgumentException exception) {
                    return invalidWith(keys, "review.literal-invalid: " + exception.getMessage());
                }
                if (original.sha256().equals(literal.documentSha256())
                        && sha256(witness).equals(literal.selectedTextSha256())
                        && scope.includes(original)) {
                    var range = new SFMReleaseReviewV1.AddressedRange(
                            original.id(), literal.startByte(), literal.endByte());
                    return exact(List.of(range), keys);
                }
                if (!sha256(witness).equals(literal.selectedTextSha256())) witness = null;
            }
            List<SFMReleaseReviewV1.AddressedRange> matches = witness == null
                    ? scanByHash(literal.selectedTextSha256(), literal.endByte() - literal.startByte(), scope, budget, keys)
                    : scan(witness, scope, budget, keys);
            if (matches.size() == 1) return relocated(matches, keys);
            if (matches.size() > 1) return ambiguous(matches, keys, "review.text-ambiguous");

            Optional<SFMReleaseReviewV1.AddressedRange> changed = sameAddressCandidate(original, literal, scope);
            if (changed.isPresent()) {
                return changed(List.of(changed.orElseThrow()), keys, "review.literal-content-changed");
            }
            return missing(keys, "review.literal-missing");
        }

        private Node text(byte[] witness, Scope scope, Budget budget) {
            if (witness.length == 0) return invalid("review.text-invalid: empty text witnesses are unbounded");
            ArrayList<SFMReleaseReviewV1.InvalidationKey> keys = new ArrayList<>();
            List<SFMReleaseReviewV1.AddressedRange> matches = scan(witness, scope, budget, keys);
            if (matches.size() == 1) return relocated(matches, keys);
            if (matches.size() > 1) return ambiguous(matches, keys, "review.text-ambiguous");
            return missing(keys, "review.text-missing");
        }

        private Node semantic(SemanticRule rule, Scope scope, Budget budget) {
            List<PreparedScope> covered = scopes.stream()
                    .filter(scope::includes)
                    .filter(value -> rule.kind() == SFMReleaseReviewV1.SelectorKind.SYMBOL
                            ? value.symbolsComplete() : value.syntaxComplete())
                    .toList();
            ArrayList<SFMReleaseReviewV1.InvalidationKey> keys = new ArrayList<>();
            covered.forEach(value -> keys.add(key(value.provider(), value.generation(), value.fingerprint())));
            if (covered.isEmpty()) {
                List<String> diagnostics = scopes.stream().filter(scope::includes)
                        .flatMap(value -> value.diagnostics().stream()).sorted().toList();
                return new Node(SFMReleaseReviewV1.EvaluationStatus.SCOPE_MISSING, List.of(), List.of(), keys,
                        append(diagnostics, "review.semantic-scope-missing: no complete prepared scope"));
            }
            List<PreparedSemanticCandidate> candidates = semantics
                    .getOrDefault(new SemanticLookup(rule.kind(), rule.semanticKey()), List.of()).stream()
                    .filter(value -> candidateInScope(value.laneId(), value.path(), value.language(), value.side(), scope))
                    .filter(value -> covered.stream().anyMatch(coverage -> covers(coverage,
                            value.laneId(), value.path(), value.language(), value.side())))
                    .toList();
            for (PreparedSemanticCandidate candidate : candidates) {
                budget.candidate(keys, candidate.provider(), candidate.generation(), candidate.fingerprint());
            }
            return candidates(rule.witness(), candidates.stream().map(value -> new Candidate(
                    value.ranges(), value.contentSha256(), value.diagnostics())).toList(), keys,
                    "review.semantic-missing", "review.semantic-ambiguous", "review.semantic-content-changed");
        }

        private Node diff(DiffRegionRule rule, Scope scope, Budget budget) {
            List<PreparedScope> covered = scopes.stream().filter(scope::includes)
                    .filter(value -> value.side() == rule.side() && value.diffComplete()).toList();
            ArrayList<SFMReleaseReviewV1.InvalidationKey> keys = new ArrayList<>();
            covered.forEach(value -> keys.add(key(value.provider(), value.generation(), value.fingerprint())));
            if (covered.isEmpty()) {
                return scopeMissing(keys, "review.diff-scope-missing: no complete prepared diff scope");
            }
            List<PreparedDiffCandidate> candidates = diffs.getOrDefault(rule.diffKey(), List.of()).stream()
                    .filter(value -> value.side() == rule.side())
                    .filter(value -> candidateInScope(value.laneId(), value.path(), value.language(), value.side(), scope))
                    .filter(value -> covered.stream().anyMatch(coverage -> covers(coverage,
                            value.laneId(), value.path(), value.language(), value.side())))
                    .toList();
            for (PreparedDiffCandidate candidate : candidates) {
                budget.candidate(keys, candidate.provider(), candidate.generation(), candidate.fingerprint());
            }
            return candidates(rule.witness(), candidates.stream().map(value -> new Candidate(
                    value.ranges(), value.contentSha256(), value.diagnostics())).toList(), keys,
                    "review.diff-missing", "review.diff-ambiguous", "review.diff-content-changed");
        }

        private Node candidates(
                Optional<Witness> witness,
                List<Candidate> candidates,
                List<SFMReleaseReviewV1.InvalidationKey> keys,
                String missing,
                String ambiguous,
                String changed
        ) {
            List<SFMReleaseReviewV1.AddressedRange> all = candidates.stream()
                    .flatMap(value -> value.ranges.stream()).sorted(RANGE_ORDER).distinct().toList();
            List<String> diagnostics = candidates.stream().flatMap(value -> value.diagnostics.stream()).toList();
            if (candidates.isEmpty()) return missing(keys, missing);
            if (candidates.size() > 1) return new Node(
                    SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS, List.of(), all, keys,
                    append(diagnostics, ambiguous + ": " + candidates.size() + " candidates retained"));
            Candidate candidate = candidates.get(0);
            if (witness.isEmpty()) {
                return new Node(SFMReleaseReviewV1.EvaluationStatus.EXACT, candidate.ranges, List.of(), keys,
                        append(diagnostics, "review.prepared-exact: no migration witness requested"));
            }
            Witness expected = witness.orElseThrow();
            if (!expected.contentSha256().equals(candidate.contentSha256)) {
                return new Node(SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED, List.of(), candidate.ranges, keys,
                        append(diagnostics, changed));
            }
            if (expected.ranges().equals(candidate.ranges)) {
                return new Node(SFMReleaseReviewV1.EvaluationStatus.EXACT, candidate.ranges, List.of(), keys, diagnostics);
            }
            return new Node(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, candidate.ranges, List.of(), keys,
                    append(diagnostics, "review.prepared-relocated"));
        }

        private Node combine(List<Rule> rules, Scope scope, Budget budget, SetOperation operation) {
            Node answer = evaluateRule(rules.get(0), scope, budget);
            for (int index = 1; index < rules.size(); index++) {
                answer = set(answer, evaluateRule(rules.get(index), scope, budget), operation, budget);
            }
            return answer;
        }

        private Node set(Node left, Node right, SetOperation operation, Budget budget) {
            budget.operation();
            List<SFMReleaseReviewV1.AddressedRange> ranges = switch (operation) {
                case UNION -> union(left.ranges, right.ranges);
                case INTERSECTION -> intersection(left.ranges, right.ranges);
                case DIFFERENCE -> difference(left.ranges, right.ranges);
            };
            List<SFMReleaseReviewV1.AddressedRange> candidates = union(left.candidates, right.candidates);
            SFMReleaseReviewV1.EvaluationStatus status = dominant(left.status, right.status);
            if (ranges.isEmpty() && status == SFMReleaseReviewV1.EvaluationStatus.EXACT) {
                status = SFMReleaseReviewV1.EvaluationStatus.MISSING;
            }
            return new Node(status, ranges, candidates, unionKeys(left.keys, right.keys),
                    append(left.diagnostics, right.diagnostics));
        }

        private List<SFMReleaseReviewV1.AddressedRange> scan(
                byte[] witness,
                Scope scope,
                Budget budget,
                List<SFMReleaseReviewV1.InvalidationKey> keys
        ) {
            ArrayList<SFMReleaseReviewV1.AddressedRange> matches = new ArrayList<>();
            for (DocumentRecord document : documents) {
                if (!scope.includes(document)) continue;
                budget.document(keys, document);
                if (document.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE
                        || document.bytes().isEmpty()) continue;
                byte[] bytes = document.bytes().orElseThrow();
                for (int offset = 0; offset + witness.length <= bytes.length; offset++) {
                    budget.bytes(witness.length, keys);
                    if (matchesAt(bytes, witness, offset)) {
                        matches.add(new SFMReleaseReviewV1.AddressedRange(
                                document.id(), offset, offset + witness.length));
                        budget.candidate(keys, "text-scan", "1", sha256(witness));
                    }
                }
            }
            return matches.stream().sorted(RANGE_ORDER).distinct().toList();
        }

        private List<SFMReleaseReviewV1.AddressedRange> scanByHash(
                String expectedHash,
                int length,
                Scope scope,
                Budget budget,
                List<SFMReleaseReviewV1.InvalidationKey> keys
        ) {
            if (length <= 0) return List.of();
            ArrayList<SFMReleaseReviewV1.AddressedRange> matches = new ArrayList<>();
            for (DocumentRecord document : documents) {
                if (!scope.includes(document)) continue;
                budget.document(keys, document);
                if (document.materialization() != SFMReleaseReviewV1.Materialization.COMPLETE
                        || document.bytes().isEmpty()) continue;
                byte[] bytes = document.bytes().orElseThrow();
                for (int offset = 0; offset + length <= bytes.length; offset++) {
                    budget.bytes(length, keys);
                    byte[] candidate = Arrays.copyOfRange(bytes, offset, offset + length);
                    if (sha256(candidate).equals(expectedHash)) {
                        matches.add(new SFMReleaseReviewV1.AddressedRange(document.id(), offset, offset + length));
                        budget.candidate(keys, "text-hash-scan", "1", expectedHash);
                    }
                }
            }
            return matches.stream().sorted(RANGE_ORDER).distinct().toList();
        }
    }

    public static Rule adapt(SFMReviewSessionV1.SelectionRule rule) {
        Objects.requireNonNull(rule, "rule");
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) return new LiteralRule(literal);
        if (rule instanceof SFMReviewSessionV1.Union union) {
            return new UnionRule(union.rules().stream().map(SFMReleaseReviewEvaluator::adapt).toList());
        }
        if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            return new IntersectionRule(intersection.rules().stream().map(SFMReleaseReviewEvaluator::adapt).toList());
        }
        if (rule instanceof SFMReviewSessionV1.Difference difference) {
            return new DifferenceRule(adapt(difference.include()), difference.exclude().stream()
                    .map(SFMReleaseReviewEvaluator::adapt).toList());
        }
        throw new IllegalArgumentException("Unknown persisted selection rule " + rule.getClass().getName());
    }

    private enum SetOperation { UNION, INTERSECTION, DIFFERENCE }

    private record Candidate(
            List<SFMReleaseReviewV1.AddressedRange> ranges,
            String contentSha256,
            List<String> diagnostics
    ) {
    }

    private record Node(
            SFMReleaseReviewV1.EvaluationStatus status,
            List<SFMReleaseReviewV1.AddressedRange> ranges,
            List<SFMReleaseReviewV1.AddressedRange> candidates,
            List<SFMReleaseReviewV1.InvalidationKey> keys,
            List<String> diagnostics
    ) {
    }

    private static final class Budget {
        private final Limits limits;
        private long documents;
        private long candidates;
        private long bytes;
        private long operations;

        private Budget(Limits limits) { this.limits = limits; }

        void document(List<SFMReleaseReviewV1.InvalidationKey> keys, DocumentRecord document) {
            documents++;
            keys.add(key("document:" + document.id(), document.sourceOwner(), document.sha256()));
            check(keys);
        }

        void candidate(List<SFMReleaseReviewV1.InvalidationKey> keys, String owner, String generation, String fingerprint) {
            candidates++;
            keys.add(key(owner, generation, fingerprint));
            check(keys);
        }

        void bytes(long amount, List<SFMReleaseReviewV1.InvalidationKey> keys) {
            bytes = Math.addExact(bytes, amount);
            check(keys);
        }

        void operation() {
            operations++;
            check(List.of());
        }

        private void check(List<SFMReleaseReviewV1.InvalidationKey> keys) {
            if (documents > limits.maxDocuments()) throw new BudgetExceeded("document limit", keys);
            if (candidates > limits.maxCandidates()) throw new BudgetExceeded("candidate limit", keys);
            if (bytes > limits.maxComparedBytes()) throw new BudgetExceeded("byte comparison limit", keys);
            if (operations > limits.maxOperations()) throw new BudgetExceeded("operation limit", keys);
        }

        Work snapshot() { return new Work(documents, candidates, bytes, operations); }
    }

    private static final class BudgetExceeded extends RuntimeException {
        private final List<SFMReleaseReviewV1.InvalidationKey> keys;

        private BudgetExceeded(String message, Collection<SFMReleaseReviewV1.InvalidationKey> keys) {
            super(message);
            this.keys = canonicalKeys(keys);
        }
    }

    private static Map<String, String> languages(List<SFMReleaseReviewV1.ReviewUnit> units) {
        TreeMap<String, TreeSet<String>> values = new TreeMap<>();
        for (SFMReleaseReviewV1.ReviewUnit unit : units) {
            unit.pathBefore().ifPresent(path -> values.computeIfAbsent(
                    languageAddress(unit.laneId(), path, DiffSide.BEFORE), ignored -> new TreeSet<>()).add(unit.language()));
            unit.pathAfter().ifPresent(path -> values.computeIfAbsent(
                    languageAddress(unit.laneId(), path, DiffSide.AFTER), ignored -> new TreeSet<>()).add(unit.language()));
        }
        TreeMap<String, String> result = new TreeMap<>();
        values.forEach((key, set) -> result.put(key, String.join("+", set)));
        return Map.copyOf(result);
    }

    private static String languageAddress(String lane, String path, DiffSide side) {
        return lane + "\u0000" + path + "\u0000" + side;
    }

    private static Comparator<DocumentRecord> documentOrder() {
        return Comparator.comparing(DocumentRecord::laneId).thenComparing(DocumentRecord::path)
                .thenComparing(value -> value.side().ordinal()).thenComparing(DocumentRecord::id);
    }

    private static Comparator<PreparedScope> scopeOrder() {
        return Comparator.comparing(PreparedScope::laneId).thenComparing(PreparedScope::path)
                .thenComparing(value -> value.side().ordinal()).thenComparing(PreparedScope::language)
                .thenComparing(PreparedScope::provider).thenComparing(PreparedScope::fingerprint);
    }

    private static Comparator<PreparedSemanticCandidate> semanticOrder() {
        return Comparator.comparing((PreparedSemanticCandidate value) -> value.kind().ordinal())
                .thenComparing(PreparedSemanticCandidate::semanticKey)
                .thenComparing(PreparedSemanticCandidate::laneId).thenComparing(PreparedSemanticCandidate::path)
                .thenComparing(value -> value.side().ordinal()).thenComparing(value -> canonicalRangeIdentity(value.ranges()))
                .thenComparing(PreparedSemanticCandidate::fingerprint);
    }

    private static Comparator<PreparedDiffCandidate> diffOrder() {
        return Comparator.comparing(PreparedDiffCandidate::diffKey).thenComparing(PreparedDiffCandidate::laneId)
                .thenComparing(PreparedDiffCandidate::path).thenComparing(value -> value.side().ordinal())
                .thenComparing(value -> canonicalRangeIdentity(value.ranges()))
                .thenComparing(PreparedDiffCandidate::fingerprint);
    }

    private static Map<String, DocumentRecord> uniqueDocuments(List<DocumentRecord> documents) {
        LinkedHashMap<String, DocumentRecord> result = new LinkedHashMap<>();
        for (DocumentRecord document : documents) {
            if (result.putIfAbsent(document.id(), document) != null) {
                throw new IllegalArgumentException("Duplicate indexed document revision " + document.id());
            }
        }
        return Map.copyOf(result);
    }

    private interface DocumentClassifier { String value(DocumentRecord document); }

    private static Map<String, List<DocumentRecord>> groupDocuments(
            List<DocumentRecord> documents, DocumentClassifier classifier) {
        TreeMap<String, List<DocumentRecord>> result = new TreeMap<>();
        for (DocumentRecord document : documents) {
            result.computeIfAbsent(classifier.value(document), ignored -> new ArrayList<>()).add(document);
        }
        result.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(result);
    }

    private static List<String> ids(List<DocumentRecord> documents) {
        return documents.stream().map(DocumentRecord::id).toList();
    }

    private static boolean covers(PreparedScope scope, String lane, String path, String language, DiffSide side) {
        return scope.laneId().equals(lane) && scope.path().equals(path)
                && scope.language().equals(language) && scope.side() == side;
    }

    private static boolean candidateInScope(
            String lane, String path, String language, DiffSide side, Scope scope) {
        return (scope.laneIds().isEmpty() || scope.laneIds().contains(lane))
                && (scope.paths().isEmpty() || scope.paths().contains(path))
                && (scope.languages().isEmpty() || scope.languages().contains(language))
                && (scope.diffSides().isEmpty() || scope.diffSides().contains(side));
    }

    private static Optional<SFMReleaseReviewV1.AddressedRange> sameAddressCandidate(
            DocumentRecord original, SFMReviewSessionV1.LiteralUtf8Range literal, Scope scope) {
        if (original == null || !scope.includes(original) || original.bytes().isEmpty()) return Optional.empty();
        byte[] bytes = original.bytes().orElseThrow();
        if (literal.startByte() > bytes.length) return Optional.empty();
        int end = Math.min(bytes.length, literal.endByte());
        return Optional.of(new SFMReleaseReviewV1.AddressedRange(original.id(), literal.startByte(), end));
    }

    private static List<SFMReviewSessionV1.LiteralUtf8Range> literalLeaves(
            SFMReviewSessionV1.SelectionRule rule) {
        ArrayList<SFMReviewSessionV1.LiteralUtf8Range> leaves = new ArrayList<>();
        collectLiteralLeaves(rule, leaves);
        return leaves;
    }

    private static void collectLiteralLeaves(
            SFMReviewSessionV1.SelectionRule rule,
            List<SFMReviewSessionV1.LiteralUtf8Range> leaves
    ) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) leaves.add(literal);
        else if (rule instanceof SFMReviewSessionV1.Union union) union.rules().forEach(value -> collectLiteralLeaves(value, leaves));
        else if (rule instanceof SFMReviewSessionV1.Intersection intersection) intersection.rules().forEach(value -> collectLiteralLeaves(value, leaves));
        else if (rule instanceof SFMReviewSessionV1.Difference difference) {
            collectLiteralLeaves(difference.include(), leaves);
            difference.exclude().forEach(value -> collectLiteralLeaves(value, leaves));
        }
    }

    private static SFMReleaseReviewV1.EvaluationStatus dominant(
            SFMReleaseReviewV1.EvaluationStatus left,
            SFMReleaseReviewV1.EvaluationStatus right
    ) {
        return severity(left) >= severity(right) ? left : right;
    }

    private static int severity(SFMReleaseReviewV1.EvaluationStatus status) {
        return switch (status) {
            case EXACT -> 0;
            case RELOCATED -> 1;
            case CONTENT_CHANGED -> 2;
            case MISSING -> 3;
            case AMBIGUOUS -> 4;
            case SCOPE_MISSING -> 5;
            case INVALID -> 6;
        };
    }

    private static Node exact(List<SFMReleaseReviewV1.AddressedRange> ranges,
                              List<SFMReleaseReviewV1.InvalidationKey> keys) {
        return new Node(SFMReleaseReviewV1.EvaluationStatus.EXACT, ranges, List.of(), keys, List.of());
    }

    private static Node relocated(List<SFMReleaseReviewV1.AddressedRange> ranges,
                                  List<SFMReleaseReviewV1.InvalidationKey> keys) {
        return new Node(SFMReleaseReviewV1.EvaluationStatus.RELOCATED, ranges, List.of(), keys,
                List.of("review.text-relocated"));
    }

    private static Node changed(List<SFMReleaseReviewV1.AddressedRange> candidates,
                                List<SFMReleaseReviewV1.InvalidationKey> keys, String diagnostic) {
        return new Node(SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED, List.of(), candidates, keys,
                List.of(diagnostic));
    }

    private static Node ambiguous(List<SFMReleaseReviewV1.AddressedRange> candidates,
                                  List<SFMReleaseReviewV1.InvalidationKey> keys, String diagnostic) {
        return new Node(SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS, List.of(), candidates, keys,
                List.of(diagnostic + ": all candidates retained"));
    }

    private static Node missing(List<SFMReleaseReviewV1.InvalidationKey> keys, String diagnostic) {
        return new Node(SFMReleaseReviewV1.EvaluationStatus.MISSING, List.of(), List.of(), keys,
                List.of(diagnostic));
    }

    private static Node scopeMissing(List<SFMReleaseReviewV1.InvalidationKey> keys, String diagnostic) {
        return new Node(SFMReleaseReviewV1.EvaluationStatus.SCOPE_MISSING, List.of(), List.of(), keys,
                List.of(diagnostic));
    }

    private static Node invalid(String diagnostic) { return invalidWith(List.of(), diagnostic); }

    private static Node invalidWith(List<SFMReleaseReviewV1.InvalidationKey> keys, String diagnostic) {
        return new Node(SFMReleaseReviewV1.EvaluationStatus.INVALID, List.of(), List.of(), keys,
                List.of(diagnostic));
    }

    private static List<SFMReleaseReviewV1.AddressedRange> union(
            List<SFMReleaseReviewV1.AddressedRange> left,
            List<SFMReleaseReviewV1.AddressedRange> right) {
        ArrayList<SFMReleaseReviewV1.AddressedRange> values = new ArrayList<>(left);
        values.addAll(right);
        return normalize(values);
    }

    private static List<SFMReleaseReviewV1.AddressedRange> intersection(
            List<SFMReleaseReviewV1.AddressedRange> left,
            List<SFMReleaseReviewV1.AddressedRange> right) {
        ArrayList<SFMReleaseReviewV1.AddressedRange> result = new ArrayList<>();
        for (var a : left) for (var b : right) {
            if (!a.documentRevisionId().equals(b.documentRevisionId())) continue;
            int start = Math.max(a.startByte(), b.startByte());
            int end = Math.min(a.endByte(), b.endByte());
            if (start < end) result.add(new SFMReleaseReviewV1.AddressedRange(a.documentRevisionId(), start, end));
        }
        return normalize(result);
    }

    private static List<SFMReleaseReviewV1.AddressedRange> difference(
            List<SFMReleaseReviewV1.AddressedRange> include,
            List<SFMReleaseReviewV1.AddressedRange> exclude) {
        ArrayList<SFMReleaseReviewV1.AddressedRange> result = new ArrayList<>();
        for (var source : normalize(include)) {
            List<SFMReleaseReviewV1.AddressedRange> fragments = List.of(source);
            for (var cut : normalize(exclude)) {
                ArrayList<SFMReleaseReviewV1.AddressedRange> next = new ArrayList<>();
                for (var fragment : fragments) {
                    if (!fragment.documentRevisionId().equals(cut.documentRevisionId())
                            || cut.endByte() <= fragment.startByte() || fragment.endByte() <= cut.startByte()) {
                        next.add(fragment);
                        continue;
                    }
                    if (fragment.startByte() < cut.startByte()) next.add(new SFMReleaseReviewV1.AddressedRange(
                            fragment.documentRevisionId(), fragment.startByte(), cut.startByte()));
                    if (cut.endByte() < fragment.endByte()) next.add(new SFMReleaseReviewV1.AddressedRange(
                            fragment.documentRevisionId(), cut.endByte(), fragment.endByte()));
                }
                fragments = next;
            }
            result.addAll(fragments);
        }
        return normalize(result);
    }

    private static List<SFMReleaseReviewV1.AddressedRange> normalize(
            List<SFMReleaseReviewV1.AddressedRange> values) {
        List<SFMReleaseReviewV1.AddressedRange> sorted = values.stream().sorted(RANGE_ORDER).toList();
        ArrayList<SFMReleaseReviewV1.AddressedRange> result = new ArrayList<>();
        for (var value : sorted) {
            if (value.startByte() == value.endByte()) continue;
            if (result.isEmpty()) { result.add(value); continue; }
            var previous = result.get(result.size() - 1);
            if (previous.documentRevisionId().equals(value.documentRevisionId())
                    && value.startByte() <= previous.endByte()) {
                result.set(result.size() - 1, new SFMReleaseReviewV1.AddressedRange(
                        previous.documentRevisionId(), previous.startByte(), Math.max(previous.endByte(), value.endByte())));
            } else result.add(value);
        }
        return List.copyOf(result);
    }

    private static List<SFMReleaseReviewV1.InvalidationKey> unionKeys(
            List<SFMReleaseReviewV1.InvalidationKey> left,
            List<SFMReleaseReviewV1.InvalidationKey> right) {
        ArrayList<SFMReleaseReviewV1.InvalidationKey> result = new ArrayList<>(left);
        result.addAll(right);
        return canonicalKeys(result);
    }

    private static List<SFMReleaseReviewV1.InvalidationKey> canonicalKeys(
            Collection<SFMReleaseReviewV1.InvalidationKey> keys) {
        return keys.stream().sorted(KEY_ORDER).distinct().toList();
    }

    private static SFMReleaseReviewV1.InvalidationKey key(String owner, String generation, String fingerprint) {
        return new SFMReleaseReviewV1.InvalidationKey(owner, generation, fingerprint);
    }

    private static List<SFMReleaseReviewV1.AddressedRange> canonicalRanges(
            Collection<SFMReleaseReviewV1.AddressedRange> ranges, String label) {
        Objects.requireNonNull(ranges, label);
        return ranges.stream().map(value -> Objects.requireNonNull(value, label)).sorted(RANGE_ORDER).distinct().toList();
    }

    private static String canonicalRangeIdentity(List<SFMReleaseReviewV1.AddressedRange> ranges) {
        return ranges.stream().sorted(RANGE_ORDER).map(value -> value.documentRevisionId() + ":"
                + value.startByte() + ":" + value.endByte()).reduce((a, b) -> a + "|" + b).orElse("");
    }

    private static List<String> canonicalStrings(List<String> values, String label) {
        return Objects.requireNonNull(values, label).stream().map(value -> requireText(value, label))
                .distinct().sorted().toList();
    }

    private static List<String> append(List<String> left, String right) {
        ArrayList<String> result = new ArrayList<>(left); result.add(right); return List.copyOf(result);
    }

    private static List<String> append(List<String> left, List<String> right) {
        ArrayList<String> result = new ArrayList<>(left); result.addAll(right); return List.copyOf(result);
    }

    private static boolean matchesAt(byte[] haystack, byte[] needle, int offset) {
        for (int index = 0; index < needle.length; index++) if (haystack[offset + index] != needle[index]) return false;
        return true;
    }

    private static void validateRange(byte[] bytes, int start, int end) {
        if (start < 0 || end < start || end > bytes.length) throw new IllegalArgumentException("range is outside UTF-8 bytes");
        if (!isUtf8Boundary(bytes, start) || !isUtf8Boundary(bytes, end)) {
            throw new IllegalArgumentException("range is not on a UTF-8 boundary");
        }
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        return offset == 0 || offset == bytes.length || (bytes[offset] & 0xC0) != 0x80;
    }

    private static String hashParts(List<byte[]> parts) {
        if (parts.size() == 1) return sha256(parts.get(0));
        int length = parts.stream().mapToInt(value -> value.length + 8).sum();
        byte[] bytes = new byte[length];
        int cursor = 0;
        for (byte[] part : parts) {
            long size = part.length;
            for (int shift = 56; shift >= 0; shift -= 8) bytes[cursor++] = (byte) (size >>> shift);
            System.arraycopy(part, 0, bytes, cursor, part.length);
            cursor += part.length;
        }
        return sha256(bytes);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean isSha256(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static String requireSha256(String value, String label) {
        value = requireText(value, label);
        if (!isSha256(value)) throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        return value;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
