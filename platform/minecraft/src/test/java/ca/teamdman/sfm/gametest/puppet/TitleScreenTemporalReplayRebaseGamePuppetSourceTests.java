package ca.teamdman.sfm.gametest.puppet;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
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

class TitleScreenTemporalReplayRebaseGamePuppetSourceTests {
    @Test
    void journeyUsesRealInputAndOrdinaryRegisteredActionSurfacesInOrder() throws Exception {
        Path projectRoot = locateMinecraftProjectRoot();
        Path sourcePath = projectRoot.resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/"
                        + "TitleScreenTemporalReplayRebaseGamePuppet.java"
        );
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
                case "checkpoint" -> journey.add("checkpoint:"
                        + call.getArgument(1).asFieldAccessExpr().getNameAsString());
                case "palette" -> journey.add("command:"
                        + call.getArgument(1).asStringLiteralExpr().asString());
                case "replay" -> journey.add("replay:"
                        + call.getArgument(1).asFieldAccessExpr().getNameAsString());
                case "pressScreenKey" -> journey.add("key:" + fieldName(call.getArgument(0)));
                case "typeScreenText" -> journey.add("type:"
                        + call.getArgument(0).asStringLiteralExpr().asString());
                default -> {
                }
            }
        }

        assertEquals(List.of(
                "checkpoint:INITIAL",
                "key:GLFW_KEY_1",
                "key:GLFW_KEY_J",
                "checkpoint:DYNAMIC_SELECTED",
                "command:sfm action invoke sfm:text/selection/replace/decimal_sequence focused",
                "key:GLFW_KEY_2",
                "checkpoint:NUMBERED_TWO",
                "checkpoint:EXACT_REPLAYED",
                "key:GLFW_KEY_1",
                "key:GLFW_KEY_Z",
                "key:GLFW_KEY_Z",
                "key:GLFW_KEY_HOME",
                "key:GLFW_KEY_END",
                "key:GLFW_KEY_ENTER",
                "type:- apricots",
                "checkpoint:THREE_ITEM_PARENT",
                "replay:EXACT_REPLAY",
                "checkpoint:EXACT_MISMATCH",
                "replay:SEMANTIC_REBASE",
                "key:GLFW_KEY_2",
                "checkpoint:SEMANTIC_REBASED"
        ), journey);

        MethodCallExpr physicalChord = calls.stream()
                .filter(call -> call.getNameAsString().equals("pressScreenKey"))
                .filter(call -> fieldName(call.getArgument(0)).equals("GLFW_KEY_J"))
                .findFirst()
                .orElseThrow();
        BinaryExpr modifiers = physicalChord.getArgument(1).asBinaryExpr();
        assertEquals(BinaryExpr.Operator.BINARY_OR, modifiers.getOperator());
        assertTrue(modifiers.toString().contains("GLFW_MOD_CONTROL"));
        assertTrue(modifiers.toString().contains("GLFW_MOD_ALT"));

        assertTrue(calls.stream().anyMatch(call ->
                        call.getNameAsString().equals("executeCommandPalette")
                                && call.getArguments().size() == 1
                                && call.getArgument(0).isStringLiteralExpr()
                                && call.getArgument(0).asStringLiteralExpr().asString().equals(
                                "sfm action invoke sfm:episode/replay/exact focused")),
                "Exact replay must first exercise the selector-only constrained palette surface");
        assertTrue(calls.stream().anyMatch(call ->
                        call.getNameAsString().equals("clickTemporalExactReplayChoice")),
                "Exact replay must be chosen from the visible constrained palette");
        assertFalse(compilationUnit.getImports().stream().anyMatch(importDeclaration ->
                        importDeclaration.getNameAsString().contains(
                                "SFMDecimalNumberingTrajectoryController")),
                "The puppet definition must not import its controller");
        assertFalse(calls.stream().anyMatch(call -> List.of(
                        "applyIntent", "commitNaturalEdit", "checkout", "undo",
                        "exactReplay", "semanticRebase").contains(call.getNameAsString())),
                "The puppet must not mutate temporal state through repository/controller internals");
    }

    @Test
    void finalAssertionParsesAndRestoresTheGeneratedCanonicalArchive() throws Exception {
        Path sourcePath = locateMinecraftProjectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/"
                        + "AssertTemporalReplayRebasePuppetAction.java"
        );
        String source = Files.readString(sourcePath);
        assertTrue(source.contains("SFMTemporalReplayArchiveJsonCodec.write(archive)"),
                "The canonical codec must encode the generated archive");
        assertTrue(source.contains("SFMTemporalReplayArchiveJsonCodec.read(canonical)"),
                "The generated canonical artifact must be parsed");
        assertTrue(source.contains("SFMTemporalReplayRuntime.restore(decoded)"),
                "A fresh replay runtime must restore the parsed archive");
        assertTrue(source.contains("restored.encodeCanonical()"),
                "The restored runtime must reproduce canonical output bytes");
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
