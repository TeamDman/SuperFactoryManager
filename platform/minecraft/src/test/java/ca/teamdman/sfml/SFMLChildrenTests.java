package ca.teamdman.sfml;

import ca.teamdman.antlr.IAstNode;
import ca.teamdman.sfml.ast.SFMLProgram;
import ca.teamdman.sfml.ast.SfmlAstNode;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public class SFMLChildrenTests {
    /// The {@link SfmlAstNode#getChildNodes()} return value must include all fields.
    public void assertChildrenDiscoverable(IAstNode<?> node) {

        Class<? extends IAstNode> nodeClass = node.getClass();
        Field[] fields = nodeClass.getDeclaredFields();
        List<? extends IAstNode> exposedChildren = node.getChildNodes();
        // there must not exist a field that is ? extends SfmlAstNode that is not present in exposedChildren
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);
            Class<?> fieldType = field.getType();
            if (SfmlAstNode.class.isAssignableFrom(fieldType)) {
                boolean found = false;
                for (IAstNode exposedChild : exposedChildren) {
                    try {
                        if (exposedChild.equals(field.get(node))) {
                            found = true;
                            break;
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Transient fields should be EXCLUDED from children, not included
                boolean shouldInclude = !Modifier.isTransient(field.getModifiers());
                if (!found && shouldInclude) {
                    throw new RuntimeException("Field " + field + " not found in exposed children of " + node.getClass().getCanonicalName());
                } else if (found && !shouldInclude) {
                    throw new RuntimeException("Field " + field + " found in exposed children of " + node.getClass().getCanonicalName() + " but should not be");
                }
            }
        }
    }

    public void assertDescendantsDiscoverable(IAstNode<?> node) {
        node.getDescendantNodes().forEach(this::assertChildrenDiscoverable);
    }

    @Test
    public void programDiscoverable() {
        String programString = """
                NAME "bruh"
                EVERY 20 TICKS DO
                    INPUT FROM a
                    OUTPUT TO b
                END
                """.stripIndent().stripIndent();
        SFMLProgram program = new SFMLProgramBuilder(programString).build().maybeProgram();
        Assertions.assertNotNull(program);
        assertDescendantsDiscoverable(program);
    }
}
