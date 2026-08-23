package ca.teamdman.sfm.client.review.release_review;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Parser and normalized AST for release-review set queries. */
public final class SFMReleaseReviewQuery {
    public sealed interface Expression permits Atom, Effective, Binary {
        String normalized();
    }

    public record Atom(String value) implements Expression {
        public Atom {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) throw new IllegalArgumentException("Query atom must not be blank");
        }

        @Override
        public String normalized() {
            return value;
        }
    }

    public record Effective(Expression expression) implements Expression {
        public Effective {
            Objects.requireNonNull(expression, "expression");
        }

        @Override
        public String normalized() {
            return "effective(" + expression.normalized() + ")";
        }
    }

    public enum Operator { UNION, INTERSECT, DIFFERENCE }

    public record Binary(Expression left, Operator operator, Expression right) implements Expression {
        public Binary {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(right, "right");
        }

        @Override
        public String normalized() {
            return "(" + left.normalized() + " " + operator.name().toLowerCase(Locale.ROOT)
                    + " " + right.normalized() + ")";
        }
    }

    private enum Kind { WORD, LEFT, RIGHT, END }
    private record Token(Kind kind, String text, int offset) {}

    private final List<Token> tokens;
    private int position;

    private SFMReleaseReviewQuery(String input) {
        tokens = tokenize(input);
    }

    public static Expression parse(String input) {
        SFMReleaseReviewQuery parser = new SFMReleaseReviewQuery(input);
        Expression answer = parser.union();
        Token trailing = parser.peek();
        if (trailing.kind() != Kind.END) {
            throw parser.error("Unexpected token '" + trailing.text() + "'", trailing);
        }
        return answer;
    }

    public static String normalize(String input) {
        return parse(input).normalized();
    }

    private Expression union() {
        Expression answer = difference();
        while (word("union")) answer = new Binary(answer, Operator.UNION, difference());
        return answer;
    }

    private Expression difference() {
        Expression answer = intersection();
        while (word("difference")) answer = new Binary(answer, Operator.DIFFERENCE, intersection());
        return answer;
    }

    private Expression intersection() {
        Expression answer = unary();
        while (true) {
            if (word("intersect")) {
                answer = new Binary(answer, Operator.INTERSECT, unary());
            } else if (startsUnary(peek())) {
                answer = new Binary(answer, Operator.INTERSECT, unary());
            } else {
                return answer;
            }
        }
    }

    private Expression unary() {
        if (peekWord("effective")) {
            take();
            expect(Kind.LEFT, "Expected '(' after effective");
            Expression child = union();
            expect(Kind.RIGHT, "Expected ')' after effective expression");
            return new Effective(child);
        }
        return primary();
    }

    private Expression primary() {
        Token token = take();
        if (token.kind() == Kind.LEFT) {
            Expression child = union();
            expect(Kind.RIGHT, "Expected ')' to close query group");
            return child;
        }
        if (token.kind() != Kind.WORD || keyword(token.text())) {
            throw error("Expected a query atom", token);
        }
        return new Atom(token.text());
    }

    private boolean word(String value) {
        if (!peekWord(value)) return false;
        take();
        return true;
    }

    private boolean peekWord(String value) {
        Token token = peek();
        return token.kind() == Kind.WORD && token.text().equalsIgnoreCase(value);
    }

    private void expect(Kind kind, String message) {
        Token token = take();
        if (token.kind() != kind) throw error(message, token);
    }

    private Token peek() {
        return tokens.get(position);
    }

    private Token take() {
        Token answer = peek();
        if (answer.kind() != Kind.END) position++;
        return answer;
    }

    private IllegalArgumentException error(String message, Token token) {
        return new IllegalArgumentException(message + " at query offset " + token.offset());
    }

    private static boolean startsUnary(Token token) {
        if (token.kind() == Kind.LEFT) return true;
        return token.kind() == Kind.WORD && !Set.of("union", "intersect", "difference")
                .contains(token.text().toLowerCase(Locale.ROOT));
    }

    private static boolean keyword(String text) {
        return Set.of("union", "intersect", "difference", "effective")
                .contains(text.toLowerCase(Locale.ROOT));
    }

    private static List<Token> tokenize(String input) {
        Objects.requireNonNull(input, "input");
        List<Token> answer = new ArrayList<>();
        int index = 0;
        while (index < input.length()) {
            char current = input.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '(') {
                answer.add(new Token(Kind.LEFT, "(", index++));
                continue;
            }
            if (current == ')') {
                answer.add(new Token(Kind.RIGHT, ")", index++));
                continue;
            }
            int start = index;
            while (index < input.length()) {
                current = input.charAt(index);
                if (Character.isWhitespace(current) || current == '(' || current == ')') break;
                index++;
            }
            answer.add(new Token(Kind.WORD, input.substring(start, index), start));
        }
        answer.add(new Token(Kind.END, "", input.length()));
        return List.copyOf(answer);
    }
}
