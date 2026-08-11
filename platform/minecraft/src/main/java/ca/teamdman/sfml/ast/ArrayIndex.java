package ca.teamdman.sfml.ast;

/**
 * AST node representing an array index in NBT path expressions.
 * Can be a number (e.g., [0]), star (e.g., [*]), or filter (e.g., [?id == "minecraft:sharpness"]).
 */
public sealed interface ArrayIndex extends ASTNode permits
        ArrayIndex.NumberIndex,
        ArrayIndex.StarIndex,
        ArrayIndex.FilterIndex {

    /**
     * Convert this array index to its JMESPath representation (without brackets).
     */
    String toJmesPath();

    record NumberIndex(long value) implements ArrayIndex {
        @Override
        public String toJmesPath() {
            return String.valueOf(value);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record StarIndex() implements ArrayIndex {
        @Override
        public String toJmesPath() {
            return "*";
        }

        @Override
        public String toString() {
            return "*";
        }
    }

    record FilterIndex(NbtFilterExpr filter) implements ArrayIndex {
        @Override
        public String toJmesPath() {
            return "?" + filter.toJmesPath();
        }

        @Override
        public String toString() {
            return "?" + filter;
        }
    }
}
