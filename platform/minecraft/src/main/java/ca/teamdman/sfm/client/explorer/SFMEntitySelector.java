package ca.teamdman.sfm.client.explorer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** A pure, typed, set-valued selector. Evaluation belongs to domain repositories. */
public record SFMEntitySelector(Domain domain, Node node) {
    public enum Domain {
        GAME(true, false),
        PANE(true, false),
        PANEL_ENTRY(true, false),
        EXPLORER(true, false),
        EPISODE(true, false),
        SELECTION(false, true);

        private final boolean focusCapable;
        private final boolean nameCapable;

        Domain(boolean focusCapable, boolean nameCapable) {
            this.focusCapable = focusCapable;
            this.nameCapable = nameCapable;
        }

        public boolean focusCapable() {
            return focusCapable;
        }

        public boolean nameCapable() {
            return nameCapable;
        }
    }

    public sealed interface Node permits Id, Name, Focused, All, Union, Intersection, Difference {
        String canonical();
    }

    public record Id(String value) implements Node {
        public Id {
            requireValue(value, "selector.empty-id");
        }

        @Override
        public String canonical() {
            return "id(" + SFMCanonicalText.encodeComponent(value) + ")";
        }
    }

    public record Name(String value) implements Node {
        public Name {
            requireValue(value, "selector.empty-name");
        }

        @Override
        public String canonical() {
            return "name(" + SFMCanonicalText.encodeComponent(value) + ")";
        }
    }

    public record Focused() implements Node {
        @Override
        public String canonical() {
            return "focused";
        }
    }

    public record All() implements Node {
        @Override
        public String canonical() {
            return "all";
        }
    }

    public record Union(List<Node> selectors) implements Node {
        public Union {
            selectors = requireOperands(selectors, "selector.empty-union");
        }

        @Override
        public String canonical() {
            return call("union", selectors);
        }
    }

    public record Intersection(List<Node> selectors) implements Node {
        public Intersection {
            selectors = requireOperands(selectors, "selector.empty-intersection");
        }

        @Override
        public String canonical() {
            return call("intersection", selectors);
        }
    }

    public record Difference(Node include, List<Node> exclude) implements Node {
        public Difference {
            Objects.requireNonNull(include, "include");
            exclude = requireOperands(exclude, "selector.empty-difference");
        }

        @Override
        public String canonical() {
            return "difference(" + include.canonical() + ","
                    + exclude.stream().map(Node::canonical).collect(Collectors.joining(",")) + ")";
        }
    }

    public SFMEntitySelector {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(node, "node");
        validateDomain(domain, node);
    }

    public static SFMEntitySelector parse(Domain domain, String text) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(text, "text");
        SFMCanonicalText.requireValidUnicode(text, "selector.invalid-unicode");
        Node node = parseNode(text);
        return new SFMEntitySelector(domain, node);
    }

    /** Parse a selector at a durable action/wire boundary and reject aliases. */
    public static SFMEntitySelector parseCanonical(Domain domain, String text) {
        SFMEntitySelector selector = parse(domain, text);
        if (!selector.canonical().equals(text)) {
            throw new SFMParseException(
                    "selector.noncanonical",
                    "Selector must use its canonical spelling: " + selector.canonical(),
                    0
            );
        }
        return selector;
    }

    public static SFMEntitySelector exact(Domain domain, String id) {
        return new SFMEntitySelector(domain, new Id(id));
    }

    public String canonical() {
        return node.canonical();
    }

    @Override
    public String toString() {
        return canonical();
    }

    public boolean isExactIdentity() {
        return node instanceof Id;
    }

    private static Node parseNode(String text) {
        return switch (text) {
            case "focused" -> new Focused();
            case "all" -> new All();
            default -> parseCall(text);
        };
    }

    private static Node parseCall(String text) {
        SFMCanonicalText.FunctionCall call = SFMCanonicalText.parseFunction(text);
        List<String> arguments = call.arguments();
        return switch (call.name()) {
            case "id" -> new Id(decodeScalar(call.name(), arguments));
            case "name" -> new Name(decodeScalar(call.name(), arguments));
            case "union" -> new Union(arguments.stream().map(SFMEntitySelector::parseNode).toList());
            case "intersection" -> new Intersection(arguments.stream().map(SFMEntitySelector::parseNode).toList());
            case "difference" -> {
                if (arguments.size() < 2) {
                    throw new SFMParseException(
                            "selector.difference-arity",
                            "difference requires an include and at least one exclude selector",
                            0
                    );
                }
                yield new Difference(
                        parseNode(arguments.get(0)),
                        arguments.subList(1, arguments.size()).stream().map(SFMEntitySelector::parseNode).toList()
                );
            }
            default -> throw new SFMParseException(
                    "selector.unknown-function",
                    "Unknown selector function: " + call.name().toLowerCase(Locale.ROOT),
                    0
            );
        };
    }

    private static String decodeScalar(String name, List<String> arguments) {
        if (arguments.size() != 1) {
            throw new SFMParseException(
                    "selector.scalar-arity",
                    name + " requires exactly one encoded value",
                    0
            );
        }
        String value = arguments.get(0);
        if (value.indexOf('(') >= 0 || value.indexOf(')') >= 0 || value.indexOf(',') >= 0) {
            throw new SFMParseException(
                    "selector.invalid-scalar",
                    name + " requires one scalar value",
                    0
            );
        }
        return SFMCanonicalText.decodeComponent(value, name.length() + 1);
    }

    private static void validateDomain(Domain domain, Node node) {
        if (node instanceof Focused && !domain.focusCapable()) {
            throw new SFMParseException(
                    "selector.focus-unsupported",
                    "focused is not supported for " + domain.name().toLowerCase(Locale.ROOT),
                    0
            );
        }
        if (node instanceof Name && !domain.nameCapable()) {
            throw new SFMParseException(
                    "selector.name-unsupported",
                    "name is only supported for selections",
                    0
            );
        }
        if (node instanceof Union union) union.selectors().forEach(child -> validateDomain(domain, child));
        if (node instanceof Intersection intersection) {
            intersection.selectors().forEach(child -> validateDomain(domain, child));
        }
        if (node instanceof Difference difference) {
            validateDomain(domain, difference.include());
            difference.exclude().forEach(child -> validateDomain(domain, child));
        }
    }

    private static void requireValue(String value, String code) {
        Objects.requireNonNull(value, "value");
        SFMCanonicalText.requireValidUnicode(value, code);
        if (value.isEmpty()) throw new SFMParseException(code, "Selector value must not be empty", 0);
    }

    private static List<Node> requireOperands(List<Node> operands, String code) {
        Objects.requireNonNull(operands, "operands");
        operands = List.copyOf(operands);
        if (operands.isEmpty()) {
            throw new SFMParseException(code, "Set operation requires at least one operand", 0);
        }
        operands.forEach(operand -> Objects.requireNonNull(operand, "operand"));
        return operands;
    }

    private static String call(String name, List<Node> selectors) {
        return name + "(" + selectors.stream().map(Node::canonical).collect(Collectors.joining(",")) + ")";
    }
}
