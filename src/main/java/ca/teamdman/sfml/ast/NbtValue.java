package ca.teamdman.sfml.ast;

/**
 * AST node representing a value in NBT expressions (number, string, or boolean).
 * Used in comparisons like: damage > 10, id == "minecraft:sharpness", etc.
 */
public sealed interface NbtValue extends ASTNode permits
        NbtValue.NbtNumber,
        NbtValue.NbtString,
        NbtValue.NbtBoolean {

    /**
     * Convert this value to its JMESPath representation.
     */
    String toJmesPath();

    record NbtNumber(long value) implements NbtValue {
        @Override
        public String toJmesPath() {
            return "`" + value + "`";
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    record NbtString(String value) implements NbtValue {
        @Override
        public String toJmesPath() {
            // JMESPath uses single quotes for string literals
            return "'" + value.replace("'", "\\'") + "'";
        }

        @Override
        public String toString() {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
    }

    record NbtBoolean(boolean value) implements NbtValue {
        @Override
        public String toJmesPath() {
            return "`" + value + "`";
        }

        @Override
        public String toString() {
            return value ? "true" : "false";
        }
    }
}
