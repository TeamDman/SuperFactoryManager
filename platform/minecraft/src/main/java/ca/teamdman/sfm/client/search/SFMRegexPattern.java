package ca.teamdman.sfm.client.search;

import java.util.*;
import java.util.function.IntPredicate;

/** Bounded prioritized NFA. No backtracking stack, reflection, external worker or java.util.regex. */
final class SFMRegexPattern {
    static final int MAX_STATES = 1024;
    static final int MAX_DEPTH = 24;
    static final int MAX_REPEAT = 256;
    record Span(int start, int end) { }
    private enum Op { CHARACTER, SPLIT, ASSERT, MATCH }
    @FunctionalInterface private interface CharacterTest { boolean test(int point, SFMMatchBudget budget); }
    @FunctionalInterface private interface Assertion { boolean test(int[] points, int position); }
    private static final class Instruction {
        final Op op;
        final CharacterTest character;
        final Assertion assertion;
        int first;
        final int second;
        Instruction(Op op, CharacterTest character, Assertion assertion, int first, int second) {
            this.op = op; this.character = character; this.assertion = assertion;
            this.first = first; this.second = second;
        }
    }
    private sealed interface Node permits CharacterNode, AssertionNode, Sequence, Alternative, Repeat { }
    private record CharacterNode(CharacterTest test) implements Node { }
    private record AssertionNode(Assertion test) implements Node { }
    private record Sequence(List<Node> nodes) implements Node { }
    private record Alternative(List<Node> nodes) implements Node { }
    private record Repeat(Node node, int min, int max, boolean lazy) implements Node { }
    private final List<Instruction> program;
    private final int entry;
    private final boolean wholeWord;

    SFMRegexPattern(String pattern, SFMTextMatchOptions options) {
        if (pattern.length() > SFMTextMatcher.MAX_QUERY_CODEPOINTS * 2
                || pattern.codePointCount(0, pattern.length()) > SFMTextMatcher.MAX_QUERY_CODEPOINTS)
            throw new SFMTextMatcher.LimitExceeded("Regex pattern length limit exceeded");
        Parser parser = new Parser(pattern, options);
        Node tree = parser.expression(0);
        if (!parser.end()) throw parser.error("Unexpected closing group");
        Compiler compiler = new Compiler();
        int terminal = compiler.emit(new Instruction(Op.MATCH, null, null, -1, -1));
        entry = compiler.compile(tree, terminal);
        program = List.copyOf(compiler.instructions);
        wholeWord = options.wholeWord();
    }

    List<Span> find(int[] points, SFMMatchBudget budget) {
        var matches = new ArrayList<Span>();
        int start = 0;
        while (start <= points.length) {
            budget.spend(1);
            int end = anchored(points, start, budget);
            if (end >= 0) {
                if (matches.size() == SFMTextMatcher.MAX_FRAGMENTS)
                    throw new SFMTextMatcher.LimitExceeded("Regex occurrence limit exceeded");
                matches.add(new Span(start, end));
                start = end > start ? end : start + 1;
            } else start++;
        }
        return List.copyOf(matches);
    }

    /** Higher-priority consuming states may extend a greedy match; lower-priority states cannot replace it. */
    private int anchored(int[] points, int start, SFMMatchBudget budget) {
        if (wholeWord && start > 0 && SFMTextMatcher.wordMember(points[start - 1])) return -1;
        List<Integer> ready = closure(List.of(entry), points, start, budget);
        int best = -1;
        for (int position = start; !ready.isEmpty(); position++) {
            var next = new ArrayList<Integer>();
            for (int index : ready) {
                budget.spend(1);
                Instruction instruction = program.get(index);
                if (instruction.op == Op.MATCH) {
                    if (!wholeWord || position == points.length || !SFMTextMatcher.wordMember(points[position])) {
                        best = position;
                        break;
                    }
                } else if (position < points.length && instruction.character.test(points[position], budget)) {
                    next.add(instruction.first);
                }
            }
            if (next.isEmpty() || position == points.length) return best;
            ready = closure(next, points, position + 1, budget);
        }
        return best;
    }

    /** An iterative epsilon closure visits each instruction once at one input position. */
    private List<Integer> closure(List<Integer> seeds, int[] points, int position, SFMMatchBudget budget) {
        var answer = new ArrayList<Integer>();
        var seen = new boolean[program.size()];
        var pending = new ArrayDeque<Integer>();
        for (int i = seeds.size() - 1; i >= 0; i--) pending.push(seeds.get(i));
        while (!pending.isEmpty()) {
            budget.spend(1);
            int index = pending.pop();
            if (seen[index]) continue;
            seen[index] = true;
            Instruction instruction = program.get(index);
            switch (instruction.op) {
                case SPLIT -> { pending.push(instruction.second); pending.push(instruction.first); }
                case ASSERT -> { if (instruction.assertion.test(points, position)) pending.push(instruction.first); }
                case CHARACTER, MATCH -> answer.add(index);
            }
        }
        return answer;
    }

    private static final class Compiler {
        final ArrayList<Instruction> instructions = new ArrayList<>();
        final SFMMatchBudget budget = new SFMMatchBudget(10_000);
        int emit(Instruction instruction) {
            budget.spend(1);
            if (instructions.size() == MAX_STATES) throw new SFMTextMatcher.LimitExceeded("Regex state limit exceeded");
            instructions.add(instruction);
            return instructions.size() - 1;
        }
        int split(int first, int second) { return emit(new Instruction(Op.SPLIT, null, null, first, second)); }
        int compile(Node node, int next) {
            budget.spend(1);
            if (node instanceof CharacterNode character) return emit(new Instruction(Op.CHARACTER, character.test(), null, next, -1));
            if (node instanceof AssertionNode assertion) return emit(new Instruction(Op.ASSERT, null, assertion.test(), next, -1));
            if (node instanceof Sequence sequence) {
                for (int i = sequence.nodes().size() - 1; i >= 0; i--) next = compile(sequence.nodes().get(i), next);
                return next;
            }
            if (node instanceof Alternative alternative) {
                int answer = compile(alternative.nodes().get(alternative.nodes().size() - 1), next);
                for (int i = alternative.nodes().size() - 2; i >= 0; i--)
                    answer = split(compile(alternative.nodes().get(i), next), answer);
                return answer;
            }
            Repeat repeat = (Repeat) node;
            if (repeat.max() == -1) {
                int fork = split(-1, next);
                int body = compile(repeat.node(), fork);
                instructions.get(fork).first = body;
                if (repeat.lazy()) {
                    instructions.set(fork, new Instruction(Op.SPLIT, null, null, next, body));
                }
                next = fork;
            } else {
                for (int i = repeat.min(); i < repeat.max(); i++) {
                    int body = compile(repeat.node(), next);
                    next = repeat.lazy() ? split(next, body) : split(body, next);
                }
            }
            for (int i = 0; i < repeat.min(); i++) next = compile(repeat.node(), next);
            return next;
        }
    }

    private static final class Parser {
        final int[] source;
        final SFMTextMatchOptions options;
        int cursor;
        Parser(String source, SFMTextMatchOptions options) {
            this.source = source.codePoints().toArray(); this.options = options;
        }
        boolean end() { return cursor == source.length; }
        int peek() { return end() ? -1 : source[cursor]; }
        boolean take(int point) { if (peek() != point) return false; cursor++; return true; }
        SFMTextMatcher.LimitExceeded error(String message) {
            return new SFMTextMatcher.LimitExceeded(message + " at pattern code point " + cursor);
        }
        Node expression(int depth) {
            if (depth > MAX_DEPTH) throw error("Regex group depth limit exceeded");
            var branches = new ArrayList<Node>();
            do {
                var sequence = new ArrayList<Node>();
                while (!end() && peek() != ')' && peek() != '|') sequence.add(repeated(atom(depth)));
                branches.add(new Sequence(List.copyOf(sequence)));
            } while (take('|'));
            return branches.size() == 1 ? branches.get(0) : new Alternative(List.copyOf(branches));
        }
        Node atom(int depth) {
            if (end()) throw error("Expected regex atom");
            int point = source[cursor++];
            return switch (point) {
                case '(' -> {
                    if (take('?') && !take(':')) throw error("Lookaround, named groups and inline flags are unsupported");
                    Node child = expression(depth + 1);
                    if (!take(')')) throw error("Unclosed regex group");
                    yield child;
                }
                case '[' -> characterSet();
                case '.' -> new CharacterNode((p, b) -> options.dotAll() || !lineBreak(p));
                case '^' -> new AssertionNode(SFMRegexPattern::lineStart);
                case '$' -> new AssertionNode(SFMRegexPattern::lineEnd);
                case '\\' -> escape(false);
                case '*', '+', '?', '{', '}', ']' -> throw error("Unexpected regex metacharacter");
                default -> literal(point);
            };
        }
        Node repeated(Node child) {
            int min, max;
            if (take('*')) { min = 0; max = -1; }
            else if (take('+')) { min = 1; max = -1; }
            else if (take('?')) { min = 0; max = 1; }
            else if (take('{')) {
                min = decimal(); max = min;
                if (take(',')) max = peek() == '}' ? -1 : decimal();
                if (!take('}')) throw error("Unclosed repetition");
                if (max != -1 && max < min) throw error("Repetition maximum is below minimum");
            } else return child;
            boolean lazy = take('?');
            if (peek() == '+' || peek() == '*' || peek() == '?' || peek() == '{')
                throw error("Possessive or stacked quantifiers are unsupported");
            return new Repeat(child, min, max, lazy);
        }
        int decimal() {
            if (peek() < '0' || peek() > '9') throw error("Expected decimal repetition count");
            int count = 0;
            while (peek() >= '0' && peek() <= '9') {
                count = count * 10 + source[cursor++] - '0';
                if (count > MAX_REPEAT) throw error("Regex repetition limit exceeded");
            }
            return count;
        }
        CharacterNode literal(int expected) {
            return new CharacterNode((point, budget) -> same(point, expected, options.matchCase()));
        }
        Node escape(boolean inSet) {
            if (end()) throw error("Trailing regex escape");
            int point = source[cursor++];
            return switch (point) {
                case 'd', 'D' -> predicate(Character::isDigit, point == 'D');
                case 's', 'S' -> predicate(p -> Character.isWhitespace(p) || Character.isSpaceChar(p), point == 'S');
                case 'w', 'W' -> predicate(SFMTextMatcher::wordMember, point == 'W');
                case 'b' -> inSet ? literal('\b') : new AssertionNode((p, at) -> wordBoundary(p, at));
                case 'B' -> {
                    if (inSet) throw error("Word-boundary assertion is not a character-set member");
                    yield new AssertionNode((p, at) -> !wordBoundary(p, at));
                }
                case 'A', 'z' -> {
                    if (inSet) throw error("Anchor is not a character-set member");
                    yield point == 'A' ? new AssertionNode((p, at) -> at == 0)
                            : new AssertionNode((p, at) -> at == p.length);
                }
                case 'n' -> literal('\n');
                case 'r' -> literal('\r');
                case 't' -> literal('\t');
                case 'f' -> literal('\f');
                case 'x' -> literal(hex(2));
                case 'u' -> literal(hex(4));
                default -> {
                    if (Character.isLetterOrDigit(point)) throw error("Unsupported regex escape or backreference");
                    yield literal(point);
                }
            };
        }
        CharacterNode predicate(IntPredicate predicate, boolean negate) {
            return new CharacterNode((point, budget) -> predicate.test(point) != negate);
        }
        int hex(int digits) {
            int value = 0;
            for (int i = 0; i < digits; i++) {
                int next = Character.digit(peek(), 16);
                if (next < 0) throw error("Invalid hexadecimal escape");
                cursor++; value = value * 16 + next;
            }
            if (value >= 0xd800 && value <= 0xdfff) throw error("Use a literal Unicode code point instead of surrogate escapes");
            return value;
        }
        CharacterNode characterSet() {
            boolean negate = take('^');
            var tests = new ArrayList<CharacterTest>();
            while (!end() && peek() != ']') {
                if (peek() == '[' || (peek() == '&' && cursor + 1 < source.length && source[cursor + 1] == '&'))
                    throw error("Nested/intersecting character sets are unsupported");
                int from = setLiteral();
                if (from == -1) {
                    tests.add(((CharacterNode) escape(true)).test());
                    if (peek() == '-' && cursor + 1 < source.length && source[cursor + 1] != ']')
                        throw error("Character class cannot be a range endpoint");
                    continue;
                }
                if (peek() == '-' && cursor + 1 < source.length && source[cursor + 1] != ']') {
                    cursor++;
                    int to = setLiteral();
                    if (to < 0) throw error("Character class cannot be a range endpoint");
                    if (from > to) throw error("Reversed character range");
                    tests.add((point, budget) -> inRange(point, from, to, options.matchCase()));
                } else tests.add(literal(from).test());
            }
            if (!take(']') || tests.isEmpty()) throw error("Empty or unclosed character set");
            var immutable = List.copyOf(tests);
            return new CharacterNode((point, budget) -> {
                budget.spend(immutable.size());
                return immutable.stream().anyMatch(test -> test.test(point, budget)) != negate;
            });
        }
        /** -1 leaves the cursor after the backslash for a shorthand class escape. */
        int setLiteral() {
            if (end()) throw error("Unclosed character set");
            int point = source[cursor++];
            if (point != '\\') return point;
            if (end()) throw error("Trailing character-set escape");
            int escaped = peek();
            if ("dDsSwW".indexOf(escaped) >= 0) return -1;
            cursor++;
            return switch (escaped) {
                case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case 'f' -> '\f'; case 'b' -> '\b';
                case 'x' -> hex(2); case 'u' -> hex(4);
                default -> {
                    if (Character.isLetterOrDigit(escaped)) throw error("Unsupported character-set escape");
                    yield escaped;
                }
            };
        }
    }

    private static boolean same(int left, int right, boolean sensitive) {
        return left == right || (!sensitive && fold(left) == fold(right));
    }
    private static int fold(int point) { return Character.toLowerCase(Character.toUpperCase(point)); }
    private static boolean inRange(int point, int from, int to, boolean sensitive) {
        return (point >= from && point <= to) || (!sensitive && ((Character.toLowerCase(point) >= from && Character.toLowerCase(point) <= to)
                || (Character.toUpperCase(point) >= from && Character.toUpperCase(point) <= to)));
    }
    private static boolean wordBoundary(int[] points, int position) {
        return (position > 0 && SFMTextMatcher.wordMember(points[position - 1]))
                != (position < points.length && SFMTextMatcher.wordMember(points[position]));
    }
    private static boolean lineBreak(int point) {
        return point == '\n' || point == '\r' || point == 0x85 || point == 0x2028 || point == 0x2029;
    }
    private static boolean lineStart(int[] points, int position) {
        return position == 0 || (lineBreak(points[position - 1])
                && !(points[position - 1] == '\r' && position < points.length && points[position] == '\n'));
    }
    private static boolean lineEnd(int[] points, int position) {
        return position == points.length || (lineBreak(points[position])
                && !(points[position] == '\n' && position > 0 && points[position - 1] == '\r'));
    }
}
