package ca.teamdman.sfm.client.theme.preview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/** Finite typed schemas are shared by parsing, evaluation, completion and clipboard documentation. */
public final class SFMItemstackPreviewOperators {
    public enum Type { STRING, BOOLEAN }
    public enum ExportPolicy { OPERATOR_SIGNATURES, SCHEMA_ONLY }
    public record Operand(String name, Type type) {
        public Operand { Objects.requireNonNull(name); Objects.requireNonNull(type); }
    }
    public record Operator(String id, String description, Type result, List<Operand> operands,
                           BiFunction<SFMItemstackPreviewSubject, List<Object>, Optional<Object>> evaluator) {
        public Operator {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid operator id " + id);
            Objects.requireNonNull(description); Objects.requireNonNull(result); Objects.requireNonNull(evaluator);
            operands = List.copyOf(operands);
            if (operands.size() > 8) throw new IllegalArgumentException("Operator arity exceeds 8");
        }
    }

    private final Map<String, Operator> operators;
    public SFMItemstackPreviewOperators(List<Operator> values) {
        if (values.size() > 256) throw new IllegalArgumentException("Operator registry exceeds 256 signatures");
        LinkedHashMap<String, Operator> all = new LinkedHashMap<>();
        values.stream().sorted(java.util.Comparator.comparing(Operator::id)).forEach(value -> {
            if (all.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("Duplicate operator " + value.id());
        });
        operators = java.util.Collections.unmodifiableMap(all);
    }
    public List<Operator> descriptors() { return List.copyOf(operators.values()); }
    public Operator require(String id) {
        Operator operator = operators.get(id);
        if (operator == null) throw new IllegalArgumentException("Unknown rule operator " + id);
        return operator;
    }
    public SFMItemstackPreviewOperators with(Operator operator) {
        ArrayList<Operator> values = new ArrayList<>(descriptors()); values.add(operator);
        return new SFMItemstackPreviewOperators(values);
    }
    public static String folded(String value) { return value.toLowerCase(Locale.ROOT); }
    public static SFMItemstackPreviewOperators builtins() { return BUILTINS; }
    private static final SFMItemstackPreviewOperators BUILTINS = build();
    private static SFMItemstackPreviewOperators build() {
        ArrayList<Operator> all = new ArrayList<>();
        var bools = List.of(new Operand("left", Type.BOOLEAN), new Operand("right", Type.BOOLEAN));
        var strings = List.of(new Operand("value", Type.STRING), new Operand("pattern", Type.STRING));
        all.add(op("sfm:bool/and", "Both Boolean predicates must hold", Type.BOOLEAN, bools,
                (s, a) -> (Boolean) a.get(0) && (Boolean) a.get(1)));
        all.add(op("sfm:bool/or", "At least one Boolean predicate must hold", Type.BOOLEAN, bools,
                (s, a) -> (Boolean) a.get(0) || (Boolean) a.get(1)));
        all.add(op("sfm:bool/not", "Negate a known Boolean predicate", Type.BOOLEAN,
                List.of(new Operand("predicate", Type.BOOLEAN)), (s, a) -> !(Boolean) a.get(0)));
        all.add(op("sfm:string/equals", "Case-insensitive exact string equality (Locale.ROOT)", Type.BOOLEAN, strings,
                (s, a) -> folded((String) a.get(0)).equals(folded((String) a.get(1)))));
        all.add(op("sfm:string/starts_with", "Case-insensitive string prefix (Locale.ROOT)", Type.BOOLEAN, strings,
                (s, a) -> folded((String) a.get(0)).startsWith(folded((String) a.get(1)))));
        all.add(op("sfm:string/ends_with", "Case-insensitive string suffix (Locale.ROOT)", Type.BOOLEAN, strings,
                (s, a) -> folded((String) a.get(0)).endsWith(folded((String) a.get(1)))));
        all.add(op("sfm:entry/name", "Structured entry name, not its whole path", Type.STRING, List.of(), (s,a) -> s.name()));
        all.add(op("sfm:entry/basename", "Name without its final non-leading, non-trailing extension", Type.STRING, List.of(), (s,a) -> s.basename()));
        all.add(op("sfm:entry/path", "Canonical entry address", Type.STRING, List.of(), (s,a) -> s.path().canonical()));
        all.add(op("sfm:entry/resolver", "Captured resolver identity", Type.STRING, List.of(), (s,a) -> s.resolverId()));
        all.add(op("sfm:entry/is_file", "Known file kind, not inferred from its suffix", Type.BOOLEAN, List.of(),
                (s,a) -> s.kind() == SFMItemstackPreviewSubject.Kind.FILE));
        all.add(op("sfm:entry/is_container", "Known hierarchy container kind", Type.BOOLEAN, List.of(),
                (s,a) -> s.kind() == SFMItemstackPreviewSubject.Kind.CONTAINER));
        all.add(op("sfm:entry/is_extensionless", "Final dot is absent, leading, or trailing (legacy file semantics)", Type.BOOLEAN, List.of(),
                (s,a) -> s.name().lastIndexOf('.')<=0 || s.name().lastIndexOf('.')==s.name().length()-1));
        all.add(op("sfm:entry/has_suffix", "Case-insensitive non-leading dot suffix, including compound suffixes", Type.BOOLEAN,
                List.of(new Operand("suffix", Type.STRING)),
                (s,a) -> s.suffixes().stream().anyMatch(value -> folded(value).equals(folded((String) a.get(0))))));
        return new SFMItemstackPreviewOperators(all);
    }
    private static Operator op(String id, String description, Type result, List<Operand> args,
                               BiFunction<SFMItemstackPreviewSubject,List<Object>,Object> eval) {
        return new Operator(id, description, result, args, (s,a) -> Optional.of(eval.apply(s,a)));
    }
}
