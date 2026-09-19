package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramRelation;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Common AST data only. Client registry lookup belongs to the client compiler. */
public sealed interface ClientValueExpression extends ProgramValueExpression {
    @Override
    default ProgramRelation evaluate(ProgramContext context) {
        throw new IllegalStateException("Client value expressions require a Client Manager");
    }

    record JsonLiteral(SFMValue value) implements ClientValueExpression {
        public JsonLiteral {
            Objects.requireNonNull(value);
            SFMValueSchema.canonicalActionJson(value);
        }

        @Override public String toString() {
            return "JSON \"" + SFMValueSchema.canonicalActionJson(value).replace("\"", "\\\"") + "\"";
        }
    }

    record Invoke(ResourceLocation action, String argument) implements ClientValueExpression {
        public Invoke { Objects.requireNonNull(action); Objects.requireNonNull(argument); }
        @Override public String toString() { return "INVOKE " + action + " WITH " + argument; }
    }

    record Field(String field, String variable) implements ClientValueExpression {
        public Field { Objects.requireNonNull(field); Objects.requireNonNull(variable); }
        @Override public String toString() { return "FIELD \"" + field.replace("\"", "\\\"") + "\" OF " + variable; }
    }
}
