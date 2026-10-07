package ca.teamdman.sfm.client.theme.preview;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Version-1 immutable typed expression tree; operator identities are not executable commands. */
public record SFMItemstackPreviewExpression(String operator, String literal,
                                            List<SFMItemstackPreviewExpression> operands) {
    public static final int MAX_CHARS = 8192, MAX_NODES = 128, MAX_DEPTH = 16, MAX_LITERAL_CODEPOINTS = 1024;
    private static final Gson JSON = new Gson();
    public SFMItemstackPreviewExpression {
        operands = List.copyOf(operands);
        if ((operator == null) == (literal == null)) throw new IllegalArgumentException("Expected operator or literal");
        if (literal != null) {
            if (!operands.isEmpty()) throw new IllegalArgumentException("Literal has operands");
            if (literal.codePointCount(0, literal.length()) > MAX_LITERAL_CODEPOINTS) throw new IllegalArgumentException("Rule literal exceeds limit");
            for (int i=0; i<literal.length(); i++) {
                char c=literal.charAt(i);
                if (Character.isHighSurrogate(c)) {
                    if (++i>=literal.length() || !Character.isLowSurrogate(literal.charAt(i)))
                        throw new IllegalArgumentException("Invalid Unicode in rule literal");
                } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid Unicode in rule literal");
            }
        }
    }
    public static SFMItemstackPreviewExpression literal(String text) { return new SFMItemstackPreviewExpression(null, text, List.of()); }
    public static SFMItemstackPreviewExpression call(String id, SFMItemstackPreviewExpression... args) {
        return new SFMItemstackPreviewExpression(id, null, List.of(args));
    }
    public static String quote(String value) { return JSON.toJson(value); }
    public String print() {
        if (literal != null) return quote(literal);
        return operator + (operands.isEmpty() ? "" : " " + operands.stream().map(SFMItemstackPreviewExpression::print)
                .collect(java.util.stream.Collectors.joining(" ")));
    }
    public SFMItemstackPreviewOperators.Type validate(SFMItemstackPreviewOperators registry) {
        return validate(registry, 0, new int[]{0});
    }
    private SFMItemstackPreviewOperators.Type validate(SFMItemstackPreviewOperators registry, int depth, int[] nodes) {
        limit(depth, ++nodes[0]);
        if (literal != null) return SFMItemstackPreviewOperators.Type.STRING;
        var descriptor = registry.require(operator);
        if (operands.size() != descriptor.operands().size()) throw new IllegalArgumentException("Wrong arity for " + operator);
        for (int i=0; i<operands.size(); i++) {
            if (operands.get(i).validate(registry, depth+1, nodes) != descriptor.operands().get(i).type())
                throw new IllegalArgumentException("Wrong type for " + operator + "." + descriptor.operands().get(i).name());
        }
        return descriptor.result();
    }
    public Optional<Object> evaluate(SFMItemstackPreviewOperators registry, SFMItemstackPreviewSubject subject) {
        validate(registry);
        return evaluateKnown(registry, subject);
    }
    private Optional<Object> evaluateKnown(SFMItemstackPreviewOperators registry, SFMItemstackPreviewSubject subject) {
        if (literal != null) return Optional.of(literal);
        ArrayList<Object> args = new ArrayList<>();
        for (var operand : operands) {
            var result = operand.evaluateKnown(registry, subject);
            if (result.isEmpty()) return Optional.empty();
            args.add(result.get());
        }
        var descriptor = registry.require(operator);
        var result = Objects.requireNonNull(descriptor.evaluator().apply(subject, List.copyOf(args)));
        result.ifPresent(value -> {
            if (!(descriptor.result() == SFMItemstackPreviewOperators.Type.STRING && value instanceof String)
                    && !(descriptor.result() == SFMItemstackPreviewOperators.Type.BOOLEAN && value instanceof Boolean))
                throw new IllegalArgumentException("Operator returned the wrong type: " + operator);
        });
        return result;
    }
    public record Parsed(SFMItemstackPreviewExpression expression, int end) {}
    public static Parsed parse(String text, int start, SFMItemstackPreviewOperators.Type expected,
                               SFMItemstackPreviewOperators registry) {
        Reader reader = new Reader(text, start, registry);
        var result = reader.read(expected, 0);
        if (reader.cursor - start > MAX_CHARS) throw new IllegalArgumentException("Rule expression exceeds character limit");
        return new Parsed(result, reader.cursor);
    }
    public static SFMItemstackPreviewExpression parse(String text, SFMItemstackPreviewOperators registry) {
        if (text.length() > MAX_CHARS) throw new IllegalArgumentException("Rule expression exceeds character limit");
        Parsed parsed = parse(text, 0, SFMItemstackPreviewOperators.Type.BOOLEAN, registry);
        if (!text.substring(parsed.end()).isBlank()) throw new IllegalArgumentException("Unexpected trailing rule text at " + parsed.end());
        return parsed.expression();
    }
    private static void limit(int depth, int nodes) {
        if (depth > MAX_DEPTH || nodes > MAX_NODES) throw new IllegalArgumentException("Rule expression depth/node limit exceeded");
    }
    private static final class Reader {
        final String text; final int start; final SFMItemstackPreviewOperators registry; int cursor, nodes;
        Reader(String text, int start, SFMItemstackPreviewOperators registry) {
            this.text=Objects.requireNonNull(text); this.start=start; this.cursor=start; this.registry=registry;
        }
        SFMItemstackPreviewExpression read(SFMItemstackPreviewOperators.Type expected, int depth) {
            limit(depth, ++nodes);
            while (cursor<text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
            if (cursor>=text.length()) throw new IllegalArgumentException("Missing " + expected + " expression at " + cursor);
            int begin=cursor;
            if (text.charAt(cursor)=='"') {
                cursor++; boolean escaped=false, closed=false;
                while(cursor<text.length()) {
                    char c=text.charAt(cursor++);
                    if(c<0x20) throw new IllegalArgumentException("Unescaped control character in JSON string at " + (cursor-1));
                    if(escaped && "\"\\/bfnrtu".indexOf(c)<0)
                        throw new IllegalArgumentException("Invalid JSON string escape at " + (cursor-1));
                    if(!escaped && c=='"') { closed=true; break; }
                    if(!escaped && c=='\\') escaped=true; else escaped=false;
                    if(cursor-start>MAX_CHARS) throw new IllegalArgumentException("Rule expression exceeds character limit");
                }
                if(!closed) throw new IllegalArgumentException("Unclosed string literal at " + begin);
                if(expected!=SFMItemstackPreviewOperators.Type.STRING) throw new IllegalArgumentException("Expected " + expected + " at " + begin);
                return literal(JsonParser.parseString(text.substring(begin,cursor)).getAsString());
            }
            while(cursor<text.length() && !Character.isWhitespace(text.charAt(cursor))) {
                cursor++; if(cursor-start>MAX_CHARS) throw new IllegalArgumentException("Rule expression exceeds character limit");
            }
            String id=text.substring(begin,cursor);
            var descriptor=registry.require(id);
            if(descriptor.result()!=expected) throw new IllegalArgumentException("Expected " + expected + ", got " + descriptor.result() + " at " + begin);
            ArrayList<SFMItemstackPreviewExpression> args=new ArrayList<>();
            for(var arg:descriptor.operands()) args.add(read(arg.type(),depth+1));
            return new SFMItemstackPreviewExpression(id,null,args);
        }
    }
}
