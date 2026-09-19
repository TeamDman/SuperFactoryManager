package ca.teamdman.sfm.client.program;

import ca.teamdman.sfml.ast.*;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Interprets the initial safe client-frame AST without constructing a server ProgramContext. */
public final class ClientFrameEvaluator {
    private ClientFrameEvaluator() {}

    public static Optional<ResourceLocation> evaluate(FrameTrigger trigger, long frameIndex) {
        if (frameIndex < 0) throw new IllegalArgumentException("Frame index must be non-negative");
        return evaluateBlock(trigger.block(), frameIndex);
    }

    private static Optional<ResourceLocation> evaluateBlock(Block block, long frameIndex) {
        Optional<ResourceLocation> last = Optional.empty();
        for (Statement statement : block.statements()) {
            if (statement instanceof RenderImageStatement render) {
                last = Optional.of(render.image());
            } else if (statement instanceof IfStatement branch) {
                Optional<ResourceLocation> nested = evaluateBlock(
                        evaluateCondition(branch.condition(), frameIndex)
                                ? branch.trueBlock() : branch.falseBlock(), frameIndex
                );
                if (nested.isPresent()) last = nested;
            } else {
                throw new IllegalStateException("Unexpected client frame statement: " + statement.getClass());
            }
        }
        return last;
    }

    private static boolean evaluateCondition(BoolExpr condition, long frameIndex) {
        if (condition instanceof BoolFrameModulo frame) return frame.testFrame(frameIndex);
        if (condition instanceof BoolTrue) return true;
        if (condition instanceof BoolFalse) return false;
        if (condition instanceof BoolParen parenthesized) {
            return evaluateCondition(parenthesized.inner(), frameIndex);
        }
        if (condition instanceof BoolNegation negated) {
            return !evaluateCondition(negated.inner(), frameIndex);
        }
        if (condition instanceof BoolConjunction both) {
            return evaluateCondition(both.left(), frameIndex)
                   && evaluateCondition(both.right(), frameIndex);
        }
        if (condition instanceof BoolDisjunction either) {
            return evaluateCondition(either.left(), frameIndex)
                   || evaluateCondition(either.right(), frameIndex);
        }
        throw new IllegalStateException("Unexpected client frame condition: " + condition.getClass());
    }
}
