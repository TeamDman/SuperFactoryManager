package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramRelationRow;
import ca.teamdman.sfm.common.program.ProgramValueReference;
import ca.teamdman.sfm.common.value.SFMValue;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public sealed interface ObjectFieldValueExpression extends ASTNode permits
        ObjectFieldValueExpression.Literal,
        ObjectFieldValueExpression.Variable,
        ObjectFieldValueExpression.NewGuid {
    Optional<String> relationVariable();

    SFMValue evaluate(@Nullable ProgramRelationRow sourceRow);

    record Literal(SFMValue value) implements ObjectFieldValueExpression {
        public Literal {
            Objects.requireNonNull(value);
        }

        @Override
        public Optional<String> relationVariable() {
            return Optional.empty();
        }

        @Override
        public SFMValue evaluate(@Nullable ProgramRelationRow sourceRow) {
            return value;
        }

        @Override
        public String toString() {
            if (value instanceof SFMValue.StringValue stringValue) {
                return "\"" + stringValue.value().replace("\"", "\\\"") + "\"";
            }
            return value.toString();
        }
    }

    record Variable(String name) implements ObjectFieldValueExpression {
        public Variable {
            Objects.requireNonNull(name);
        }

        @Override
        public Optional<String> relationVariable() {
            return Optional.of(name);
        }

        @Override
        public SFMValue evaluate(@Nullable ProgramRelationRow sourceRow) {
            if (sourceRow == null) {
                throw new IllegalStateException("Variable field has no relation row: " + name);
            }
            Object value = sourceRow.value();
            if (value instanceof ProgramValueReference reference) {
                return reference.get();
            }
            if (value instanceof SFMValue sfmValue) {
                return sfmValue;
            }
            throw new IllegalArgumentException("Variable is not an SFM value: " + name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    record NewGuid() implements ObjectFieldValueExpression {
        @Override
        public Optional<String> relationVariable() {
            return Optional.empty();
        }

        @Override
        public SFMValue evaluate(@Nullable ProgramRelationRow sourceRow) {
            return SFMValue.of(UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return "NEW GUID";
        }
    }
}
