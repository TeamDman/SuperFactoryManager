package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramOccurrenceId;
import ca.teamdman.sfm.common.program.ProgramRelation;
import ca.teamdman.sfm.common.program.ProgramRelationRow;
import ca.teamdman.sfm.common.program.ProgramValueReference;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValuePattern;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Constructs one lazy object per occurrence of its one relation-valued operand. */
public final class ObjectConstructionValueExpression implements ProgramValueExpression {
    private final String alias;
    private final SFMValuePattern.ObjectPattern pattern;
    private final Map<String, ObjectFieldValueExpression> fields;
    private final Optional<String> relationVariable;

    public ObjectConstructionValueExpression(
            String alias,
            SFMValuePattern.ObjectPattern pattern,
            Map<String, ObjectFieldValueExpression> fields
    ) {
        this.alias = Objects.requireNonNull(alias);
        this.pattern = Objects.requireNonNull(pattern);
        this.fields = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(fields)));
        List<String> relationVariables = fields.values().stream()
                .flatMap(field -> field.relationVariable().stream())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (relationVariables.size() > 1) {
            throw new IllegalArgumentException(
                    "Object construction cannot combine unrelated relation variables: " + relationVariables
            );
        }
        relationVariable = relationVariables.stream().findFirst();
    }

    @Override
    public ProgramRelation evaluate(ProgramContext context) {
        if (relationVariable.isEmpty()) {
            ProgramOccurrenceId occurrenceId = ProgramOccurrenceId.create();
            return new ProgramRelation(List.of(new ProgramRelationRow(
                    occurrenceId,
                    ProgramValueReference.lazy(() -> construct(null))
            )));
        }
        ProgramRelation source = context.getVariableEnvironment().getRelation(relationVariable.get())
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: " + relationVariable.get()));
        return new ProgramRelation(source.rows().stream()
                .map(row -> row.mapValue(ProgramValueReference.lazy(() -> construct(row))))
                .toList());
    }

    private SFMValue construct(ProgramRelationRow sourceRow) {
        LinkedHashMap<String, SFMValue> values = new LinkedHashMap<>();
        pattern.fields().forEach((name, fieldPattern) -> {
            if (fieldPattern instanceof SFMValuePattern.LiteralPattern literal) {
                values.put(name, literal.expected());
            }
        });
        fields.forEach((name, expression) -> values.put(name, expression.evaluate(sourceRow)));
        SFMValue value = SFMValue.object(values);
        if (!pattern.matches(value)) {
            throw new IllegalArgumentException("Constructed value does not match pattern alias " + alias);
        }
        return value;
    }

    @Override
    public String toString() {
        return alias + " WITH " + fields.entrySet().stream()
                .map(field -> "FIELD " + field.getKey() + " OF " + field.getValue())
                .collect(java.util.stream.Collectors.joining(" AND "));
    }
}
