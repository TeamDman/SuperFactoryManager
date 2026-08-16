package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Append-only, session-scoped storage for immutable symbol-reference results.
 *
 * <p>Appending advances the repository generation, but never rewrites a path
 * that was handed out for an earlier result. This lets an explorer retain an
 * old references tab while later queries complete.</p>
 */
public final class SFMSymbolReferenceResultRepository {
    public static final String SCHEME = "symbol-references";

    public record ResultId(String value) {
        public ResultId {
            Objects.requireNonNull(value, "value");
            if (!value.matches("[a-z0-9][a-z0-9._-]*")) {
                throw new IllegalArgumentException("Reference result id is not canonical: " + value);
            }
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /** One immutable resolver node and its already-materialized child paths. */
    public record Node(SFMExplorerEntry entry, List<SFMPath> children) {
        public Node {
            Objects.requireNonNull(entry, "entry");
            children = List.copyOf(children);
            children.forEach(child -> Objects.requireNonNull(child, "child"));
        }
    }

    /** Exact source-navigation payload retained for one reference leaf. */
    public record LeafLookup(
            ResultId resultId,
            SFMUsageAtPositionResult.Usage usage,
            SFMDefinitionResult.DefinitionSourceSpan sourceSpan,
            SFMSymbolServerProtocol.ServerHello serverHello
    ) {
        public LeafLookup {
            Objects.requireNonNull(resultId, "resultId");
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(sourceSpan, "sourceSpan");
            Objects.requireNonNull(serverHello, "serverHello");
            if (!usage.span().equals(sourceSpan)) {
                throw new IllegalArgumentException("Reference leaf span must be the usage's exact span");
            }
        }
    }

    /** Stable handle returned to the caller that initiated a references query. */
    public record StoredResult(
            ResultId id,
            SFMPath rootPath,
            SFMUsageAtPositionResult result,
            SFMSymbolServerProtocol.ServerHello serverHello
    ) {
        public StoredResult {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(rootPath, "rootPath");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(serverHello, "serverHello");
        }
    }

    /** Immutable resolver view captured at one repository generation. */
    public record Snapshot(long generation, Map<SFMPath, Node> nodes) {
        public Snapshot {
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            nodes = Map.copyOf(nodes);
        }
    }

    private final Map<ResultId, StoredResult> results = new LinkedHashMap<>();
    private final Map<SFMPath, Node> nodes = new LinkedHashMap<>();
    private final Map<SFMPath, LeafLookup> leaves = new LinkedHashMap<>();
    private long nextResultNumber = 1;
    private long generation;

    public synchronized StoredResult append(
            SFMUsageAtPositionResult result,
            SFMSymbolServerProtocol.ServerHello serverHello
    ) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(serverHello, "serverHello");
        ResultId id = new ResultId("result-" + nextResultNumber++);
        SFMPath root = rootPath(id);
        Tree tree = buildTree(id, root, result, serverHello);
        tree.nodes().forEach((path, node) -> {
            if (nodes.putIfAbsent(path, node) != null) {
                throw new IllegalStateException("Reference result path was already present: " + path);
            }
        });
        tree.leaves().forEach((path, leaf) -> {
            if (leaves.putIfAbsent(path, leaf) != null) {
                throw new IllegalStateException("Reference result leaf was already present: " + path);
            }
        });
        StoredResult stored = new StoredResult(id, root, result, serverHello);
        results.put(id, stored);
        generation++;
        return stored;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized Snapshot snapshot(long expectedGeneration) {
        if (expectedGeneration != generation) {
            throw new ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver.StaleGenerationException(
                    expectedGeneration,
                    generation
            );
        }
        return new Snapshot(generation, nodes);
    }

    public synchronized Optional<Node> node(SFMPath path) {
        requireScheme(path);
        return Optional.ofNullable(nodes.get(path));
    }

    public synchronized Optional<StoredResult> result(ResultId id) {
        return Optional.ofNullable(results.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Optional<LeafLookup> lookupLeaf(SFMPath path) {
        requireScheme(path);
        return Optional.ofNullable(leaves.get(path));
    }

    public static SFMPath rootPath(ResultId id) {
        Objects.requireNonNull(id, "id");
        return SFMPath.parse(SCHEME + "://" + id.value() + "/");
    }

    public static Optional<ResultId> parseRoot(SFMPath path) {
        requireScheme(path);
        if (!path.segments().isEmpty() || !path.trailingSlash()) return Optional.empty();
        try {
            return Optional.of(new ResultId(path.authority()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Tree buildTree(
            ResultId id,
            SFMPath root,
            SFMUsageAtPositionResult result,
            SFMSymbolServerProtocol.ServerHello hello
    ) {
        LinkedHashMap<SFMPath, MutableNode> mutable = new LinkedHashMap<>();
        LinkedHashMap<SFMPath, LeafLookup> leafLookups = new LinkedHashMap<>();
        addNode(mutable, root, rootLabel(result), true, "symbol-references:root", List.of());

        SFMPath analysis = child(root, "analysis", true);
        addNode(mutable, analysis, "Analysis", true, "symbol-references:analysis", List.of());
        addChild(mutable, root, analysis);
        addAnalysisTree(mutable, analysis, result, hello);

        EnumMap<SFMUsageAtPositionResult.UsageKind, List<SFMUsageAtPositionResult.Usage>> byKind =
                new EnumMap<>(SFMUsageAtPositionResult.UsageKind.class);
        result.usages().forEach(usage -> byKind.computeIfAbsent(usage.kind(), ignored -> new ArrayList<>())
                .add(usage));
        for (SFMUsageAtPositionResult.UsageKind kind : SFMUsageAtPositionResult.UsageKind.values()) {
            List<SFMUsageAtPositionResult.Usage> usages = byKind.get(kind);
            if (usages == null || usages.isEmpty()) continue;
            usages.sort(USAGE_ORDER);
            SFMPath category = child(root, kind.wireName(), true);
            addNode(
                    mutable,
                    category,
                    displayWireName(kind.wireName()) + " (" + usages.size() + ")",
                    true,
                    "symbol-references:category",
                    List.of()
            );
            addChild(mutable, root, category);

            TreeMap<String, List<SFMUsageAtPositionResult.Usage>> byFile = new TreeMap<>();
            usages.forEach(usage -> byFile.computeIfAbsent(usage.span().address(), ignored -> new ArrayList<>())
                    .add(usage));
            for (Map.Entry<String, List<SFMUsageAtPositionResult.Usage>> file : byFile.entrySet()) {
                SFMPath filePath = child(category, file.getKey(), true);
                addNode(
                        mutable,
                        filePath,
                        fileLabel(file.getValue().get(0).span()) + " (" + file.getValue().size() + ")",
                        true,
                        "symbol-references:file",
                        List.of()
                );
                addChild(mutable, category, filePath);
                List<SFMUsageAtPositionResult.Usage> fileUsages = new ArrayList<>(file.getValue());
                fileUsages.sort(USAGE_ORDER);
                for (int index = 0; index < fileUsages.size(); index++) {
                    SFMUsageAtPositionResult.Usage usage = fileUsages.get(index);
                    SFMPath leaf = child(filePath, String.format("span-%06d", index + 1), false);
                    addNode(
                            mutable,
                            leaf,
                            spanLabel(usage),
                            false,
                            "symbol-references:span",
                            List.of()
                    );
                    addChild(mutable, filePath, leaf);
                    leafLookups.put(leaf, new LeafLookup(id, usage, usage.span(), hello));
                }
            }
        }

        LinkedHashMap<SFMPath, Node> immutable = new LinkedHashMap<>();
        mutable.forEach((path, node) -> immutable.put(path, node.freeze()));
        return new Tree(
                Collections.unmodifiableMap(immutable),
                Collections.unmodifiableMap(leafLookups)
        );
    }

    private static void addAnalysisTree(
            Map<SFMPath, MutableNode> nodes,
            SFMPath analysis,
            SFMUsageAtPositionResult result,
            SFMSymbolServerProtocol.ServerHello hello
    ) {
        addInformationLeaf(nodes, analysis, "outcome", "Outcome: " + result.outcome().wireName());
        addInformationLeaf(
                nodes,
                analysis,
                "completeness",
                "Completeness: " + result.completeness().wireName()
        );
        addInformationLeaf(nodes, analysis, "usage-count", "References: " + result.usages().size());
        addInformationLeaf(nodes, analysis, "target-count", "Targets: " + result.targets().size());
        addInformationLeaf(
                nodes,
                analysis,
                "worker",
                "Worker: " + hello.serverName() + " " + hello.serverVersion()
        );
        addInformationLeaf(
                nodes,
                analysis,
                "workspace-generation",
                "Workspace generation: " + result.workspaceGeneration()
        );

        SFMPath diagnostics = child(analysis, "diagnostics", true);
        addNode(
                nodes,
                diagnostics,
                "Diagnostics (" + result.diagnostics().size() + ")",
                true,
                "symbol-references:diagnostics",
                List.of()
        );
        addChild(nodes, analysis, diagnostics);
        for (int index = 0; index < result.diagnostics().size(); index++) {
            SFMDefinitionResult.Diagnostic diagnostic = result.diagnostics().get(index);
            addInformationLeaf(
                    nodes,
                    diagnostics,
                    String.format("diagnostic-%06d", index + 1),
                    diagnostic.severity() + " " + diagnostic.code() + ": " + diagnostic.message()
            );
        }

        SFMPath recovery = child(analysis, "recovery-actions", true);
        addNode(
                nodes,
                recovery,
                "Recovery actions (" + result.recoveryActions().size() + ")",
                true,
                "symbol-references:recovery-actions",
                List.of()
        );
        addChild(nodes, analysis, recovery);
        for (int index = 0; index < result.recoveryActions().size(); index++) {
            SFMDefinitionResult.RecoveryAction action = result.recoveryActions().get(index);
            String command = action.command().map(value -> " — " + value).orElse("");
            addInformationLeaf(
                    nodes,
                    recovery,
                    String.format("recovery-%06d", index + 1),
                    action.label() + command
            );
        }

        SFMPath skipped = child(analysis, "skipped-categories", true);
        addNode(
                nodes,
                skipped,
                "Skipped categories (" + result.skippedCategories().size() + ")",
                true,
                "symbol-references:skipped-categories",
                List.of()
        );
        addChild(nodes, analysis, skipped);
        List<SFMUsageAtPositionResult.SkippedCategory> skippedValues = new ArrayList<>(
                result.skippedCategories()
        );
        skippedValues.sort(Comparator.comparing(value -> value.category().wireName()));
        for (int index = 0; index < skippedValues.size(); index++) {
            SFMUsageAtPositionResult.SkippedCategory skippedCategory = skippedValues.get(index);
            addInformationLeaf(
                    nodes,
                    skipped,
                    String.format("skipped-%06d", index + 1),
                    displayWireName(skippedCategory.category().wireName()) + ": " + skippedCategory.reason()
            );
        }
    }

    private static void addInformationLeaf(
            Map<SFMPath, MutableNode> nodes,
            SFMPath parent,
            String segment,
            String label
    ) {
        SFMPath leaf = child(parent, segment, false);
        addNode(nodes, leaf, label, false, "symbol-references:information", List.of());
        addChild(nodes, parent, leaf);
    }

    private static void addNode(
            Map<SFMPath, MutableNode> nodes,
            SFMPath path,
            String label,
            boolean expandable,
            String icon,
            List<String> diagnostics
    ) {
        MutableNode previous = nodes.putIfAbsent(
                path,
                new MutableNode(new SFMExplorerEntry(
                        path,
                        label,
                        expandable,
                        Map.of(
                                SFMExplorerEntry.SORT_NAME,
                                SFMExplorerEntry.SortKey.available(label),
                                SFMExplorerEntry.SORT_ICON,
                                SFMExplorerEntry.SortKey.available(icon)
                        ),
                        diagnostics
                ))
        );
        if (previous != null) throw new IllegalStateException("Duplicate reference explorer node: " + path);
    }

    private static void addChild(Map<SFMPath, MutableNode> nodes, SFMPath parent, SFMPath child) {
        MutableNode parentNode = nodes.get(parent);
        if (parentNode == null || !nodes.containsKey(child)) {
            throw new IllegalStateException("Reference explorer edge names an unknown node");
        }
        parentNode.children.add(child);
    }

    private static SFMPath child(SFMPath parent, String segment, boolean trailingSlash) {
        ArrayList<String> segments = new ArrayList<>(parent.segments());
        segments.add(segment);
        return new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                SCHEME,
                parent.authority(),
                segments,
                Optional.empty(),
                trailingSlash
        );
    }

    private static String rootLabel(SFMUsageAtPositionResult result) {
        String target = result.targets().isEmpty()
                ? "captured position"
                : result.targets().get(0).qualifiedName();
        return "References for " + target + " (" + result.usages().size() + ")";
    }

    private static String fileLabel(SFMDefinitionResult.DefinitionSourceSpan span) {
        return span.rootRelativePath().replace('\\', '/');
    }

    private static String spanLabel(SFMUsageAtPositionResult.Usage usage) {
        SFMDefinitionResult.DefinitionSourceSpan span = usage.span();
        return "line " + span.startLine() + ":" + span.startColumn()
                + "–" + span.endLine() + ":" + span.endColumn()
                + "  " + usage.target().qualifiedName()
                + "  [" + usage.confidence() + "]";
    }

    private static String displayWireName(String wireName) {
        String[] words = wireName.split("-");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static void requireScheme(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (!SCHEME.equals(path.scheme())) {
            throw new IllegalArgumentException("Reference result repository only accepts " + SCHEME + " paths");
        }
    }

    private static final Comparator<SFMUsageAtPositionResult.Usage> USAGE_ORDER = Comparator
            .comparing((SFMUsageAtPositionResult.Usage usage) -> usage.span().address())
            .thenComparing(usage -> usage.span().resolverId())
            .thenComparing(usage -> usage.span().rootId())
            .thenComparing(usage -> usage.span().rootRelativePath())
            .thenComparing(usage -> usage.span().reportPath())
            .thenComparing(usage -> usage.span().sourceSet())
            .thenComparing(usage -> usage.span().sourceHash())
            .thenComparing(usage -> usage.span().sourceSha256().orElse(""))
            .thenComparingLong(usage -> usage.span().startByte())
            .thenComparingLong(usage -> usage.span().endByte())
            .thenComparingLong(usage -> usage.span().startLine())
            .thenComparingLong(usage -> usage.span().startColumn())
            .thenComparingLong(usage -> usage.span().endLine())
            .thenComparingLong(usage -> usage.span().endColumn())
            .thenComparing(usage -> usage.target().kind())
            .thenComparing(usage -> usage.target().owner())
            .thenComparing(usage -> usage.target().name())
            .thenComparing(usage -> usage.target().descriptor().orElse(""))
            .thenComparing(usage -> usage.target().qualifiedName())
            .thenComparing(SFMUsageAtPositionResult.Usage::confidence);

    private record Tree(Map<SFMPath, Node> nodes, Map<SFMPath, LeafLookup> leaves) {
    }

    private static final class MutableNode {
        private final SFMExplorerEntry entry;
        private final List<SFMPath> children = new ArrayList<>();

        private MutableNode(SFMExplorerEntry entry) {
            this.entry = entry;
        }

        private Node freeze() {
            return new Node(entry, children);
        }
    }
}
