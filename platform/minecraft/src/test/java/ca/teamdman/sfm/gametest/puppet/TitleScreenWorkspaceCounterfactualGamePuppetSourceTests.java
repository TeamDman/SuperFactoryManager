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

class TitleScreenWorkspaceCounterfactualGamePuppetSourceTests {
    @Test
    void journeyUsesPaletteExplorerKeysAndOrdinaryEditorInputInOrder() throws Exception {
        Path sourcePath = projectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/"
                        + "TitleScreenWorkspaceCounterfactualGamePuppet.java"
        );
        var unit = StaticJavaParser.parse(sourcePath);
        MethodDeclaration run = unit.findFirst(MethodDeclaration.class,
                        method -> method.getNameAsString().equals("run"))
                .orElseThrow();
        List<MethodCallExpr> calls = run.findAll(MethodCallExpr.class).stream()
                .sorted(Comparator.comparing(call -> call.getBegin().orElseThrow()))
                .toList();
        ArrayList<String> journey = new ArrayList<>();
        for (MethodCallExpr call : calls) {
            switch (call.getNameAsString()) {
                case "checkpoint" -> journey.add("checkpoint:"
                        + call.getArgument(1).asFieldAccessExpr().getNameAsString());
                case "palette" -> journey.add("command:"
                        + call.getArgument(1).asStringLiteralExpr().asString());
                case "pressScreenKey" -> journey.add("key:" + fieldName(call));
                case "typeScreenText" -> journey.add("type:" + call.getArgument(0));
                default -> { }
            }
        }

        assertEquals(List.of(
                "checkpoint:INITIAL",
                "key:GLFW_KEY_1",
                "key:GLFW_KEY_RIGHT",
                "key:GLFW_KEY_ENTER",
                "checkpoint:A_OPEN",
                "key:GLFW_KEY_END",
                "key:GLFW_KEY_ENTER",
                "type:EDIT_SUFFIX_BODY",
                "checkpoint:A_EDITED",
                "command:sfm action invoke sfm:episode/workspace-counterfactual/fork/before-selection focused",
                "key:GLFW_KEY_1",
                "key:GLFW_KEY_DOWN",
                "checkpoint:B_SELECTED",
                "command:sfm action invoke sfm:episode/workspace-counterfactual/checkout/recorded-a focused",
                "key:GLFW_KEY_2",
                "checkpoint:RECORDED_CHECKOUT",
                "command:sfm action invoke sfm:episode/workspace-counterfactual/replay/frozen-a focused",
                "checkpoint:FROZEN_A",
                "command:sfm action invoke sfm:episode/workspace-counterfactual/replay/reevaluate-selected focused",
                "checkpoint:REEVALUATED_BARRIER"
        ), journey);

        assertTrue(calls.stream().anyMatch(call ->
                call.getNameAsString().equals("executeCommandPaletteAndWaitForScreen")
                        && call.getArgument(0).asStringLiteralExpr().asString().contains(
                        "sfm:chamber/workspace-counterfactual")));
        assertFalse(unit.getImports().stream().anyMatch(importDeclaration ->
                        importDeclaration.getNameAsString().contains("SFMWorkspaceCounterfactualController")),
                "The natural puppet definition must not import its controller");
        assertFalse(calls.stream().anyMatch(call -> List.of(
                        "selectDocument", "openSelectedDocument", "acceptEditorText",
                        "forkBeforeSelection", "checkoutRecordedA", "replayFrozenAWitness",
                        "reevaluateIntentOnB").contains(call.getNameAsString())),
                "The natural puppet must not mutate state through controller internals");
    }

    @Test
    void finalAssertionRequiresCanonicalFreshRestoreAndNoAmbientMutation() throws Exception {
        String source = Files.readString(projectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/"
                        + "AssertWorkspaceCounterfactualPuppetAction.java"
        ));
        assertTrue(source.contains("SFMWorkspaceCounterfactualJsonCodec.write(artifact)"));
        assertTrue(source.contains("SFMWorkspaceCounterfactualJsonCodec.read(canonical)"));
        assertTrue(source.contains("SFMWorkspaceCounterfactualController.restore("));
        assertTrue(source.contains("ambientBeforeHash().equals(artifact.ambientAfterHash())"));
        assertTrue(source.contains("operation.resultStateId().isEmpty()"));
    }

    private static String fieldName(MethodCallExpr call) {
        return ((FieldAccessExpr) call.getArgument(0)).getNameAsString();
    }

    private static Path projectRoot() {
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
