package ca.teamdman.sfm.client.explorer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** A pure expression that resolves to zero or more concrete {@link SFMPath} values. */
public sealed interface SFMPathExpression permits
        SFMPathExpression.Literal,
        SFMPathExpression.Members,
        SFMPathExpression.Children,
        SFMPathExpression.Union,
        SFMPathExpression.Intersection,
        SFMPathExpression.Difference {

    String canonical();

    record Literal(SFMPath path) implements SFMPathExpression {
        public Literal {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public String canonical() {
            return path.canonical();
        }
    }

    record Members(SFMEntitySelector selectionSelector) implements SFMPathExpression {
        public Members {
            Objects.requireNonNull(selectionSelector, "selectionSelector");
            if (selectionSelector.domain() != SFMEntitySelector.Domain.SELECTION) {
                throw new IllegalArgumentException("members requires a selection-domain selector");
            }
        }

        @Override
        public String canonical() {
            return "members(" + selectionSelector.canonical() + ")";
        }
    }

    record Children(SFMPathExpression expression) implements SFMPathExpression {
        public Children {
            Objects.requireNonNull(expression, "expression");
        }

        @Override
        public String canonical() {
            return "children(" + expression.canonical() + ")";
        }
    }

    record Union(List<SFMPathExpression> expressions) implements SFMPathExpression {
        public Union {
            expressions = requireOperands(expressions, "path-expression.empty-union");
        }

        @Override
        public String canonical() {
            return call("union", expressions);
        }
    }

    record Intersection(List<SFMPathExpression> expressions) implements SFMPathExpression {
        public Intersection {
            expressions = requireOperands(expressions, "path-expression.empty-intersection");
        }

        @Override
        public String canonical() {
            return call("intersection", expressions);
        }
    }

    record Difference(SFMPathExpression include, List<SFMPathExpression> exclude)
            implements SFMPathExpression {
        public Difference {
            Objects.requireNonNull(include, "include");
            exclude = requireOperands(exclude, "path-expression.empty-difference");
        }

        @Override
        public String canonical() {
            return "difference(" + include.canonical() + ","
                    + exclude.stream().map(SFMPathExpression::canonical).collect(Collectors.joining(","))
                    + ")";
        }
    }

    static SFMPathExpression parse(String text) {
        Objects.requireNonNull(text, "text");
        SFMCanonicalText.requireValidUnicode(text, "path-expression.invalid-unicode");
        if (text.indexOf('|') >= 0) {
            throw new SFMParseException(
                    "path-expression.pipe-aggregate-forbidden",
                    "Pipe-concatenated paths are not a path expression",
                    text.indexOf('|')
            );
        }
        if (text.contains("://") && !looksLikeFunction(text)) {
            return new Literal(SFMPath.parse(text));
        }
        SFMCanonicalText.FunctionCall call = SFMCanonicalText.parseFunction(text);
        List<String> arguments = call.arguments();
        return switch (call.name()) {
            case "members" -> {
                requireArity(call.name(), arguments, 1);
                yield new Members(SFMEntitySelector.parse(
                        SFMEntitySelector.Domain.SELECTION,
                        arguments.get(0)
                ));
            }
            case "children" -> {
                requireArity(call.name(), arguments, 1);
                yield new Children(parse(arguments.get(0)));
            }
            case "union" -> new Union(arguments.stream().map(SFMPathExpression::parse).toList());
            case "intersection" -> new Intersection(arguments.stream().map(SFMPathExpression::parse).toList());
            case "difference" -> {
                if (arguments.size() < 2) {
                    throw new SFMParseException(
                            "path-expression.difference-arity",
                            "difference requires an include and at least one exclude expression",
                            0
                    );
                }
                yield new Difference(
                        parse(arguments.get(0)),
                        arguments.subList(1, arguments.size()).stream().map(SFMPathExpression::parse).toList()
                );
            }
            default -> throw new SFMParseException(
                    "path-expression.unknown-function",
                    "Unknown path-expression function: " + call.name(),
                    0
            );
        };
    }

    private static boolean looksLikeFunction(String text) {
        int firstParen = text.indexOf('(');
        int scheme = text.indexOf("://");
        return firstParen >= 0 && firstParen < scheme;
    }

    private static void requireArity(String name, List<String> arguments, int expected) {
        if (arguments.size() != expected) {
            throw new SFMParseException(
                    "path-expression.arity",
                    name + " requires exactly " + expected + " argument(s)",
                    0
            );
        }
    }

    private static List<SFMPathExpression> requireOperands(
            List<SFMPathExpression> expressions,
            String code
    ) {
        Objects.requireNonNull(expressions, "expressions");
        expressions = List.copyOf(expressions);
        if (expressions.isEmpty()) {
            throw new SFMParseException(code, "Set operation requires at least one operand", 0);
        }
        expressions.forEach(expression -> Objects.requireNonNull(expression, "expression"));
        return expressions;
    }

    private static String call(String name, List<SFMPathExpression> expressions) {
        return name + "("
                + expressions.stream().map(SFMPathExpression::canonical).collect(Collectors.joining(","))
                + ")";
    }
}
