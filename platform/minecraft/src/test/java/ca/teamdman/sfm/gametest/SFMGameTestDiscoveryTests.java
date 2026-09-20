package ca.teamdman.sfm.gametest;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JUnit source set does not load the GameTest source set, so these tests
 * guard the discovery pipeline without requiring a Minecraft client or server.
 */
class SFMGameTestDiscoveryTests {
    @Test
    void clientOnlyMetadataIsFilteredBeforeTheAnnotatedClassLoads() throws Exception {
        var source = StaticJavaParser.parse(gameTestSource("SFMGameTestDiscovery.java"));
        MethodDeclaration gatherTests = source.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals("gatherTests"))
                .findFirst()
                .orElseThrow();
        VariableDeclarator annotatedTests = gatherTests.findAll(VariableDeclarator.class).stream()
                .filter(variable -> variable.getNameAsString().equals("annotatedTests"))
                .findFirst()
                .orElseThrow();

        List<MethodCallExpr> calls = callChain(
                annotatedTests.getInitializer().orElseThrow().asMethodCallExpr()
        );
        assertEquals(List.of("discoverAnnotations", "filter", "map", "map", "peek"),
                calls.stream().map(MethodCallExpr::getNameAsString).toList());
        assertEquals("SFMGameTest.class", calls.get(0).getArgument(0).toString());
        assertEquals("SFMGameTestDiscovery::isCompatibleWithCurrentDist",
                calls.get(1).getArgument(0).toString());
        assertEquals("SFMAnnotationUtils.SFMAnnotationData::tryLoadClass",
                calls.get(2).getArgument(0).toString(),
                "The client-only class must not load on a dedicated server");

        MethodDeclaration predicate = source.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals("isCompatibleWithCurrentDist"))
                .findFirst()
                .orElseThrow();
        assertTrue(predicate.findAll(MethodCallExpr.class).stream().anyMatch(call ->
                call.getNameAsString().equals("getEnumSet")
                        && call.getArgument(0).toString().equals("\"value\"")
                        && call.getArgument(1).toString().equals("SFMDist.class")));
        assertFalse(predicate.findAll(MethodCallExpr.class).stream().anyMatch(call ->
                call.getNameAsString().equals("tryLoadClass")),
                "The side predicate must inspect scan metadata, not load the test class");
    }

    @Test
    void omittedSideMetadataStillDefaultsToClientAndDedicatedServer() throws Exception {
        var annotation = StaticJavaParser.parse(gameTestSource("SFMGameTest.java"));
        AnnotationMemberDeclaration value = annotation.findAll(AnnotationMemberDeclaration.class).stream()
                .filter(member -> member.getNameAsString().equals("value"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("SFMDist.CLIENT", "SFMDist.DEDICATED_SERVER"),
                value.getDefaultValue().orElseThrow().asArrayInitializerExpr()
                        .getValues().stream().map(Object::toString).toList());
    }

    private static List<MethodCallExpr> callChain(MethodCallExpr outermost) {
        List<MethodCallExpr> calls = new ArrayList<>();
        MethodCallExpr current = outermost;
        while (true) {
            calls.add(0, current);
            Expression scope = current.getScope().orElseThrow();
            if (!scope.isMethodCallExpr()) return calls;
            current = scope.asMethodCallExpr();
        }
    }

    private static Path gameTestSource(String fileName) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path direct = current.resolve("src/gametest/java/ca/teamdman/sfm/gametest")
                    .resolve(fileName);
            if (Files.isRegularFile(direct)) return direct;
            Path nested = current.resolve("platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest")
                    .resolve(fileName);
            if (Files.isRegularFile(nested)) return nested;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate GameTest source: " + fileName);
    }
}
