package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.*;
{% if features.client_program_actions %}
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
{% endif %}
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Interprets the initial safe client-frame AST without constructing a server ProgramContext. */
public final class ClientFrameEvaluator {
    private ClientFrameEvaluator() {}

    public static Optional<ResourceLocation> evaluate(FrameTrigger trigger, long frameIndex) {
{% if features.client_program_actions %}
        return evaluate(trigger, frameIndex, (action, input) -> {
            throw new IllegalStateException("No client action service supplied");
        }, true).image();
{% else %}
        if (frameIndex < 0) throw new IllegalArgumentException("Frame index must be non-negative");
        return evaluateBlock(trigger.block(), frameIndex, true);
{% endif %}
    }

{% if features.client_program_actions %}
    @FunctionalInterface
    public interface Actions { SFMValue invoke(ResourceLocation action, SFMValue input); }

    public record Evaluation(Optional<ResourceLocation> image, Map<String, SFMValue> values) {
        public Evaluation { values = Map.copyOf(values); }
    }

    public static Evaluation evaluate(FrameTrigger trigger, long frameIndex, Actions actions, boolean renderAllowed) {
        if (frameIndex < 0) throw new IllegalArgumentException("Frame index must be non-negative");
        Map<String, SFMValue> values = new HashMap<>();
        return new Evaluation(evaluateBlock(trigger.block(), frameIndex, values, actions, renderAllowed), values);
    }

{% endif %}
{% if features.client_program_actions %}
    private static Optional<ResourceLocation> evaluateBlock(Block block, long frameIndex,
            Map<String, SFMValue> values, Actions actions, boolean renderAllowed) {
{% else %}
    private static Optional<ResourceLocation> evaluateBlock(Block block, long frameIndex, boolean renderAllowed) {
{% endif %}
        Optional<ResourceLocation> last = Optional.empty();
        for (Statement statement : block.statements()) {
{% if features.client_frame_render %}
            if (statement instanceof RenderImageStatement render) {
                if (renderAllowed) last = Optional.of(render.image());
{% endif %}
{% if features.client_program_actions %}
{% if features.client_frame_render %}
            } else if (statement instanceof LetStatement let) {
{% else %}
            if (statement instanceof LetStatement let) {
{% endif %}
                SFMValue value;
                if (let.expression() instanceof ClientValueExpression.JsonLiteral json) value = json.value();
                else if (let.expression() instanceof ClientValueExpression.Field field) {
                    value = ClientProgramActionManifest.field(require(values, field.variable()), field.field());
                } else if (let.expression() instanceof ClientValueExpression.Invoke invoke) {
                    value = actions.invoke(invoke.action(), require(values, invoke.argument()));
                } else throw new IllegalStateException("Unexpected client value expression");
                values.put(ClientProgramActionManifest.key(let.variableName()), Objects.requireNonNull(value));
                if (values.size() > ClientProgramActionManifest.MAX_VARIABLES
                    || values.values().stream().mapToInt(SFMValueSchema::boundedEncodedBytes).sum() > 64 * 1024) {
                    throw new IllegalStateException("Client value binding budget exceeded");
                }
{% endif %}
{% if features.client_frame_render or features.client_program_actions %}
            } else if (statement instanceof IfStatement branch) {
{% else %}
            if (statement instanceof IfStatement branch) {
{% endif %}
                Optional<ResourceLocation> nested = evaluateBlock(
{% if features.client_program_actions %}
                        evaluateCondition(branch.condition(), frameIndex, values)
{% else %}
                        evaluateCondition(branch.condition(), frameIndex)
{% endif %}
                                ? branch.trueBlock() : branch.falseBlock(), frameIndex,
{% if features.client_program_actions %}
                        new HashMap<>(values), actions, renderAllowed
{% else %}
                        renderAllowed
{% endif %}
                );
                if (nested.isPresent()) last = nested;
            } else {
                throw new IllegalStateException("Unexpected client frame statement: " + statement.getClass());
            }
        }
        return last;
    }

{% if features.client_program_actions %}
    private static SFMValue require(Map<String, SFMValue> values, String name) {
        SFMValue value = values.get(ClientProgramActionManifest.key(name));
        if (value == null) throw new IllegalStateException("Unbound client value: " + name);
        return value;
    }

{% endif %}
{% if features.client_program_actions %}
    private static boolean evaluateCondition(BoolExpr condition, long frameIndex, Map<String, SFMValue> values) {
{% else %}
    private static boolean evaluateCondition(BoolExpr condition, long frameIndex) {
{% endif %}
{% if features.client_program_actions %}
        if (condition instanceof BoolClientValueEquals comparison) {
            return require(values, comparison.variable()).equals(comparison.expected());
        }
{% endif %}
        if (condition instanceof BoolFrameModulo frame) return frame.testFrame(frameIndex);
        if (condition instanceof BoolTrue) return true;
        if (condition instanceof BoolFalse) return false;
        if (condition instanceof BoolParen parenthesized) {
{% if features.client_program_actions %}
            return evaluateCondition(parenthesized.inner(), frameIndex, values);
{% else %}
            return evaluateCondition(parenthesized.inner(), frameIndex);
{% endif %}
        }
        if (condition instanceof BoolNegation negated) {
{% if features.client_program_actions %}
            return !evaluateCondition(negated.inner(), frameIndex, values);
{% else %}
            return !evaluateCondition(negated.inner(), frameIndex);
{% endif %}
        }
        if (condition instanceof BoolConjunction both) {
{% if features.client_program_actions %}
            return evaluateCondition(both.left(), frameIndex, values)
{% else %}
            return evaluateCondition(both.left(), frameIndex)
{% endif %}
{% if features.client_program_actions %}
                   && evaluateCondition(both.right(), frameIndex, values);
{% else %}
                   && evaluateCondition(both.right(), frameIndex);
{% endif %}
        }
        if (condition instanceof BoolDisjunction either) {
{% if features.client_program_actions %}
            return evaluateCondition(either.left(), frameIndex, values)
{% else %}
            return evaluateCondition(either.left(), frameIndex)
{% endif %}
{% if features.client_program_actions %}
                   || evaluateCondition(either.right(), frameIndex, values);
{% else %}
                   || evaluateCondition(either.right(), frameIndex);
{% endif %}
        }
        throw new IllegalStateException("Unexpected client frame condition: " + condition.getClass());
    }
}
