package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.ProgramContext;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Client-only frame trigger; never evaluated through the server ProgramContext. */
public record FrameTrigger(List<Label> labels, String binding, Block block) implements Trigger, ToStringCondensed {
    public static final int MAX_BODY_NODES = 512;
    public static final int MAX_NESTING = 32;
    public FrameTrigger {
        labels = List.copyOf(labels);
        if (labels.isEmpty()) throw new IllegalArgumentException("A frame trigger needs at least one display label");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(block, "block");
        if (binding.isBlank()) throw new IllegalArgumentException("A frame display binding cannot be empty");
        validateBlock(block, binding, new int[]{0}, 0);
    }

    // This is the initial client-safe statement subset. Typed read and action
    // statements can extend it as their own consent and effect contracts land.
    // Unknown operations remain unavailable rather than inheriting server effects.
    private static void validateBlock(Block block, String binding, int[] nodes, int depth) {
        checkBudget(nodes, depth);
        for (Statement statement : block.statements()) {
            checkBudget(nodes, depth);
{% if features.client_frame_render %}
            if (statement instanceof RenderImageStatement render) {
                if (!render.binding().equalsIgnoreCase(binding)) {
                    throw new IllegalArgumentException("RENDER target must be the frame binding " + binding);
                }
{% if features.client_program_actions %}
            } else if (statement instanceof LetStatement let && let.expression() instanceof ClientValueExpression) {
{% else %}
            } else if (statement instanceof IfStatement condition) {
{% endif %}
{% elsif features.client_program_actions %}
            if (statement instanceof LetStatement let && let.expression() instanceof ClientValueExpression) {
{% else %}
            if (statement instanceof IfStatement condition) {
{% endif %}
{% if features.client_program_actions %}
                if (let.variableName().equalsIgnoreCase(binding)) {
                    throw new IllegalArgumentException("A value binding cannot replace the frame display binding");
                }
            } else if (statement instanceof IfStatement condition) {
{% endif %}
                validateBoolean(condition.condition(), nodes, depth + 1);
                validateBlock(condition.trueBlock(), binding, nodes, depth + 1);
                validateBlock(condition.falseBlock(), binding, nodes, depth + 1);
            } else {
                throw new IllegalArgumentException(
                        "Client frame body cannot execute server statement: " + statement.getClass().getSimpleName()
                );
            }
        }
    }

    private static void validateBoolean(BoolExpr condition, int[] nodes, int depth) {
        checkBudget(nodes, depth);
{% if features.client_program_actions %}
        if (condition instanceof BoolFrameModulo || condition instanceof BoolTrue || condition instanceof BoolFalse
            || condition instanceof BoolClientValueEquals) {
{% else %}
        if (condition instanceof BoolFrameModulo || condition instanceof BoolTrue || condition instanceof BoolFalse) {
{% endif %}
            return;
        }
        if (condition instanceof BoolParen paren) {
            validateBoolean(paren.inner(), nodes, depth + 1);
        } else if (condition instanceof BoolNegation negation) {
            validateBoolean(negation.inner(), nodes, depth + 1);
        } else if (condition instanceof BoolConjunction conjunction) {
            validateBoolean(conjunction.left(), nodes, depth + 1);
            validateBoolean(conjunction.right(), nodes, depth + 1);
        } else if (condition instanceof BoolDisjunction disjunction) {
            validateBoolean(disjunction.left(), nodes, depth + 1);
            validateBoolean(disjunction.right(), nodes, depth + 1);
        } else {
            throw new IllegalArgumentException(
                    "Client frame condition cannot read server state: " + condition.getClass().getSimpleName()
            );
        }
    }

    private static void checkBudget(int[] nodes, int depth) {
        if (++nodes[0] > MAX_BODY_NODES || depth > MAX_NESTING) {
            throw new IllegalArgumentException("Client frame exceeds its bounded statement/nesting budget");
        }
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public boolean shouldTick(ProgramContext context) {
        throw new IllegalStateException("A frame trigger cannot run in the server ProgramContext");
    }

    @Override
    public void tick(ProgramContext context) {
        throw new IllegalStateException("A frame trigger cannot run in the server ProgramContext");
    }

    @Override
    public String toString() {
        return "EVERY FRAME FOR " + labels.stream().map(Objects::toString).collect(Collectors.joining(", "))
               + " AS " + binding + " DO\n" + block.toString().indent(1).stripTrailing() + "\nEND";
    }

    @Override
    public String toStringCondensed() {
        return "EVERY FRAME FOR " + labels.stream().map(Objects::toString).collect(Collectors.joining(", "))
               + " AS " + binding + " DO";
    }
}
