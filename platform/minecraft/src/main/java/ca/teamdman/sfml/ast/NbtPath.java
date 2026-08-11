package ca.teamdman.sfml.ast;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * AST node representing a full NBT path expression.
 * Examples: damage, productivebees:gene_group.purity, enchantments[0].id
 */
public record NbtPath(
        NbtComponent component,
        @Nullable ArrayIndex componentArrayIndex,
        List<NbtPathElement> elements
) implements ASTNode {

    /**
     * Create a simple path with just a component.
     */
    public static NbtPath simple(NbtComponent component) {
        return new NbtPath(component, null, List.of());
    }

    /**
     * Check if this path has a component array index.
     */
    public boolean hasComponentArrayIndex() {
        return componentArrayIndex != null;
    }

    /**
     * Convert this path to its JMESPath representation.
     */
    public String toJmesPath() {
        StringBuilder sb = new StringBuilder();
        sb.append(component.toJmesPath());
        if (hasComponentArrayIndex()) {
            sb.append("[").append(componentArrayIndex.toJmesPath()).append("]");
        }
        for (NbtPathElement element : elements) {
            if (element.hasField()) {
                sb.append(".");
            }
            sb.append(element.toJmesPath());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(component);
        if (hasComponentArrayIndex()) {
            sb.append("[").append(componentArrayIndex).append("]");
        }
        for (NbtPathElement element : elements) {
            if (element.hasField()) {
                sb.append(".");
            }
            sb.append(element);
        }
        return sb.toString();
    }
}
