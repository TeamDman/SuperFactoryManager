package ca.teamdman.sfm.gametest.puppet;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleScreenCandidateHistoryScrubbingGamePuppetSourceTests {
    @Test
    void journeyUsesOnlyPublicActionsAndTimelineKeysInTheExpectedOrder() throws Exception {
        Path sourcePath = locateMinecraftProjectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/"
                        + "TitleScreenCandidateHistoryScrubbingGamePuppet.java");
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
                case "statusCheckpoint" -> journey.add("status:" + call.getArgument(1)
                        .asFieldAccessExpr().getNameAsString());
                case "registerCandidateHistoryStatusFixture" -> journey.add("fixture:register");
                case "unregisterCandidateHistoryStatusFixture" -> journey.add("fixture:unregister");
                case "pressScreenKey" -> journey.add("key:" + fieldName(call));
                case "typeScreenText" -> journey.add("type:" + call.getArgument(0)
                        .asStringLiteralExpr().asString());
                default -> {
                }
            }
        }

        assertEquals(List.of(
                "command:sfm action invoke sfm:episode/trajectory/plan focused",
                "command:sfm action invoke sfm:panel/open sfm:episode/candidate-history focused",
                "checkpoint:OLD_ROUTE_START",
                "key:GLFW_KEY_RIGHT",
                "checkpoint:OLD_ROUTE_SELECTED",
                "key:GLFW_KEY_RIGHT",
                "checkpoint:OLD_ROUTE_NUMBERED",
                "command:sfm action invoke sfm:episode/trajectory/run focused 32",
                "key:GLFW_KEY_1",
                "key:GLFW_KEY_Z",
                "key:GLFW_KEY_HOME",
                "key:GLFW_KEY_END",
                "key:GLFW_KEY_ENTER",
                "type:- apricots",
                "command:sfm action invoke sfm:episode/trajectory/replan focused",
                "key:GLFW_KEY_2",
                "checkpoint:OLD_ROUTE_RETAINED_AFTER_REPLAN",
                "command:sfm action invoke sfm:panel/open sfm:episode/candidate-history",
                "checkpoint:NEW_ROUTE_START",
                "key:GLFW_KEY_RIGHT",
                "key:GLFW_KEY_RIGHT",
                "checkpoint:NEW_ROUTE_NUMBERED",
                "fixture:register",
                "command:sfm action invoke sfm:panel/open sfm:episode/candidate-history focused",
                "status:MATERIALIZED",
                "key:GLFW_KEY_END",
                "status:UNKNOWN",
                "key:GLFW_KEY_HOME",
                "key:GLFW_KEY_RIGHT",
                "key:GLFW_KEY_RIGHT",
                "status:INVALIDATED",
                "key:GLFW_KEY_RIGHT",
                "status:EXTERNAL_BARRIER",
                "key:GLFW_KEY_LEFT",
                "key:GLFW_KEY_LEFT",
                "status:UNAVAILABLE",
                "fixture:unregister"
        ), journey);

        assertFalse(compilationUnit.getImports().stream().anyMatch(importDeclaration ->
                        importDeclaration.getNameAsString().contains("SFMDecimalNumberingTrajectoryController")),
                "The natural puppet must not import the chamber controller");
        assertFalse(calls.stream().anyMatch(call -> List.of(
                        "projectCandidateRoute", "applyIntent", "commitNaturalEdit", "checkout", "undo")
                        .contains(call.getNameAsString())),
                "The natural puppet must not mutate or project through controller internals");
        assertTrue(calls.stream().anyMatch(call -> call.getNameAsString().equals("pressScreenKey")),
                "Timeline seeking must use the same key surface as a user");
    }

    private static String fieldName(MethodCallExpr call) {
        if (call.getArgument(0) instanceof FieldAccessExpr fieldAccess) return fieldAccess.getNameAsString();
        throw new AssertionError("Expected a GLFW field access but found " + call.getArgument(0));
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
