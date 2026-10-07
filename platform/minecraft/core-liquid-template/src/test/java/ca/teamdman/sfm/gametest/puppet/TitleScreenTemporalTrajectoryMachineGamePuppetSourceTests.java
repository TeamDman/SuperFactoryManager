package ca.teamdman.sfm.gametest.puppet;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleScreenTemporalTrajectoryMachineGamePuppetSourceTests {
    @Test
    void journeyUsesThePublicScreenSurfaceInTheExactSupervisedOrder() throws Exception {
        Path sourcePath = locateMinecraftProjectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/"
                        + "TitleScreenTemporalTrajectoryMachineGamePuppet.java");
        var compilationUnit = StaticJavaParser.parse(sourcePath);
        MethodDeclaration run = compilationUnit.findFirst(MethodDeclaration.class,
                        method -> method.getNameAsString().equals("run"))
                .orElseThrow();
        List<MethodCallExpr> calls = run.findAll(MethodCallExpr.class).stream()
                .sorted(Comparator.comparing(call -> call.getBegin().orElseThrow()))
                .toList();

        List<String> journey = new ArrayList<>();
        for (MethodCallExpr call : calls) {
            switch (call.getNameAsString()) {
                case "palette" -> journey.add("command:" + call.getArgument(1)
                        .asStringLiteralExpr().asString());
                case "checkpoint" -> journey.add("checkpoint:" + call.getArgument(1)
                        .asFieldAccessExpr().getNameAsString());
                case "pressScreenKey" -> journey.add("key:" + fieldName(call.getArgument(0)));
                case "typeScreenText" -> journey.add("type:" + call.getArgument(0)
                        .asStringLiteralExpr().asString());
                default -> {
                }
            }
        }

        assertEquals(List.of(
                "checkpoint:INITIAL",
                "command:sfm action invoke sfm:episode/trajectory/plan focused",
                "checkpoint:PLANNED",
                "command:sfm action invoke sfm:episode/trajectory/step focused",
                "checkpoint:FIRST_STEP",
                "command:sfm action invoke sfm:episode/trajectory/run focused 32",
                "checkpoint:NUMBERED_TWO",
                "key:GLFW_KEY_1",
                "key:GLFW_KEY_Z",
                "checkpoint:POST_UNDO",
                "key:GLFW_KEY_HOME",
                "key:GLFW_KEY_END",
                "key:GLFW_KEY_ENTER",
                "type:- apricots",
                "checkpoint:THREE_ITEM_FORK",
                "command:sfm action invoke sfm:episode/trajectory/step focused",
                "checkpoint:STALE_STEP",
                "command:sfm action invoke sfm:episode/trajectory/replan focused",
                "checkpoint:REPLANNED",
                "command:sfm action invoke sfm:episode/trajectory/run focused 32",
                "checkpoint:NUMBERED_THREE"
        ), journey);

        assertTrue(calls.stream().anyMatch(call -> call.getNameAsString().equals("typeScreenText")));
        assertFalse(compilationUnit.getImports().stream().anyMatch(importDeclaration ->
                importDeclaration.getNameAsString().contains("SFMDecimalNumberingTrajectoryController")));
        assertFalse(calls.stream().anyMatch(call -> call.getScope()
                .map(Object::toString)
                .orElse("")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("controller")),
                "The puppet must exercise the same screen/action surface as a user");
        assertFalse(calls.stream().anyMatch(call -> List.of(
                        "applyIntent", "commitNaturalEdit", "checkout", "undo").contains(
                        call.getNameAsString())),
                "The puppet must not mutate temporal state through controller/repository internals");

        MethodDeclaration palette = compilationUnit.findFirst(MethodDeclaration.class,
                        method -> method.getNameAsString().equals("palette"))
                .orElseThrow();
        assertTrue(palette.findAll(MethodCallExpr.class).stream().anyMatch(call ->
                        call.getNameAsString().equals("executeCommandPalette")
                                && call.getArguments().size() == 1
                                && call.getArgument(0).isStringLiteralExpr()
                                && call.getArgument(0).asStringLiteralExpr().asString().equals(
                                "sfm action invoke sfm:palette/close")),
                "Every trajectory command must restore the workspace before evidence is sampled");
    }

    private static String fieldName(com.github.javaparser.ast.expr.Expression expression) {
        if (expression instanceof FieldAccessExpr fieldAccess) return fieldAccess.getNameAsString();
        throw new AssertionError("Expected a GLFW field access but found " + expression);
    }

    private static Path locateMinecraftProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/gametest/java"))) return current;
            Path nested = current.resolve("platform/minecraft");
            if (Files.isDirectory(nested.resolve("src/gametest/java"))) return nested;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the Minecraft project root");
    }
}
