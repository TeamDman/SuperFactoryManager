package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueSchema;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Interprets the initial safe client-frame AST without constructing a server ProgramContext. */
public final class ClientFrameEvaluator {
    private ClientFrameEvaluator() {}

    public static Optional<ResourceLocation> evaluate(FrameTrigger trigger, long frameIndex) {
        return evaluate(trigger, frameIndex, (action, input) -> {
            throw new IllegalStateException("No client action service supplied");
        }, true).image();
    }

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

    private static Optional<ResourceLocation> evaluateBlock(Block block, long frameIndex,
            Map<String, SFMValue> values, Actions actions, boolean renderAllowed) {
        Optional<ResourceLocation> last = Optional.empty();
        for (Statement statement : block.statements()) {
            if (statement instanceof RenderImageStatement render) {
                if (renderAllowed) last = Optional.of(render.image());
            } else if (statement instanceof LetStatement let) {
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
            } else if (statement instanceof IfStatement branch) {
                Optional<ResourceLocation> nested = evaluateBlock(
                        evaluateCondition(branch.condition(), frameIndex, values)
                                ? branch.trueBlock() : branch.falseBlock(), frameIndex,
                        new HashMap<>(values), actions, renderAllowed
                );
                if (nested.isPresent()) last = nested;
            } else {
                throw new IllegalStateException("Unexpected client frame statement: " + statement.getClass());
            }
        }
        return last;
    }

    private static SFMValue require(Map<String, SFMValue> values, String name) {
        SFMValue value = values.get(ClientProgramActionManifest.key(name));
        if (value == null) throw new IllegalStateException("Unbound client value: " + name);
        return value;
    }

    private static boolean evaluateCondition(BoolExpr condition, long frameIndex, Map<String, SFMValue> values) {
        if (condition instanceof BoolClientValueEquals comparison) {
            return require(values, comparison.variable()).equals(comparison.expected());
        }
        if (condition instanceof BoolFrameModulo frame) return frame.testFrame(frameIndex);
        if (condition instanceof BoolTrue) return true;
        if (condition instanceof BoolFalse) return false;
        if (condition instanceof BoolParen parenthesized) {
            return evaluateCondition(parenthesized.inner(), frameIndex, values);
        }
        if (condition instanceof BoolNegation negated) {
            return !evaluateCondition(negated.inner(), frameIndex, values);
        }
        if (condition instanceof BoolConjunction both) {
            return evaluateCondition(both.left(), frameIndex, values)
                   && evaluateCondition(both.right(), frameIndex, values);
        }
        if (condition instanceof BoolDisjunction either) {
            return evaluateCondition(either.left(), frameIndex, values)
                   || evaluateCondition(either.right(), frameIndex, values);
        }
        throw new IllegalStateException("Unexpected client frame condition: " + condition.getClass());
    }
}
