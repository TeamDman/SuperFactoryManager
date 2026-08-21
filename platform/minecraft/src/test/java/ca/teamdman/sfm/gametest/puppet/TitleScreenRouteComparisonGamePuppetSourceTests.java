package ca.teamdman.sfm.gametest.puppet;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleScreenRouteComparisonGamePuppetSourceTests {
    private static final String DEFINITION =
            "src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/"
                    + "TitleScreenRouteComparisonGamePuppet.java";

    @Test
    void naturalJourneyUsesOnlyVisiblePaletteOperationsForComparisonAndSelection() throws Exception {
        Path path = projectRoot().resolve(DEFINITION);
        CompilationUnit unit = parse(path);
        String source = Files.readString(path);
        MethodDeclaration run = unit.findFirst(MethodDeclaration.class,
                        method -> method.getNameAsString().equals("run"))
                .orElseThrow();

        List<String> required = List.of(
                "sfm action invoke sfm:episode/trajectory/plan focused",
                "sfm action invoke sfm:review/comment/create/candidate/route focused left-route-review",
                "sfm action invoke sfm:episode/trajectory/replan focused",
                "sfm action invoke sfm:review/comment/create/candidate/route focused right-route-review",
                "sfm action invoke sfm:panel/open sfm:episode/route-comparison focused",
                "sfm action invoke sfm:episode/route-comparison/seek focused both 1",
                "sfm action invoke sfm:episode/route-comparison/mode/set focused independent",
                "sfm action invoke sfm:episode/route-comparison/seek focused left 0",
                "sfm action invoke sfm:episode/route-comparison/seek focused right 2",
                "sfm action invoke sfm:episode/route-comparison/disposition/set focused left preferred",
                "sfm action invoke sfm:episode/route-comparison/disposition/set focused right rejected",
                "sfm action invoke sfm:episode/route-comparison/trajectory/select focused left"
        );
        int cursor = -1;
        for (String command : required) {
            int next = source.indexOf(command, cursor + 1);
            assertTrue(next > cursor, "Missing or out-of-order route-comparison command: " + command);
            cursor = next;
        }

        assertEquals(6, run.findAll(MethodCallExpr.class).stream()
                        .filter(call -> call.getNameAsString().equals("checkpoint"))
                        .count(),
                "every required comparison state needs JSON plus visible screenshot evidence");
        assertEquals(1, run.findAll(MethodCallExpr.class).stream()
                        .filter(call -> call.getNameAsString().equals("resetRouteComparisonSession"))
                        .count(),
                "the production comparison store must begin deterministically");
        assertEquals(1, run.findAll(MethodCallExpr.class).stream()
                        .filter(call -> call.getNameAsString().equals("reloadRouteComparisonSession"))
                        .count(),
                "the natural journey must reload production persistence");
        assertFalse(unit.getImports().stream().anyMatch(importDeclaration -> {
            String name = importDeclaration.getNameAsString();
            return name.contains("SFMRouteComparisonRuntime")
                    || name.contains("SFMHistoryGraphRuntime")
                    || name.contains("SFMDecimalNumberingTrajectoryController");
        }), "the natural puppet definition must not import mutation/runtime internals");

        for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
            if (!literal.asString().contains("sfm:episode/route-comparison/")) continue;
            MethodCallExpr owner = literal.findAncestor(MethodCallExpr.class).orElseThrow();
            assertTrue(Set.of("palette", "contains").contains(owner.getNameAsString()),
                    "comparison behavior must enter through the palette: " + literal);
        }
    }

    @Test
    void evidenceActionsCannotSelectOrExecuteATrajectoryBehindTheUserSurface() throws Exception {
        Path actionRoot = projectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/action");
        CompilationUnit assertion = parse(actionRoot.resolve("AssertRouteComparisonPuppetAction.java"));
        CompilationUnit session = parse(actionRoot.resolve("RouteComparisonSessionPuppetAction.java"));

        assertNoCalls(assertion, Set.of(
                "execute", "apply", "selectRoute", "setMode", "seek", "setDisposition",
                "createCandidateComment", "editComment"
        ));
        assertNoCalls(session, Set.of(
                "execute", "apply", "selectRoute", "setMode", "seek", "setDisposition"
        ));
        assertTrue(session.findAll(MethodCallExpr.class).stream()
                        .map(MethodCallExpr::getNameAsString)
                        .anyMatch(name -> name.equals("reload")),
                "persistence proof must use the production runtime reload seam");
    }

    @Test
    void routeComparisonArtifactsFlattenOptionalMachineStateBeforeGsonSerialization() throws Exception {
        Path action = projectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/action/"
                        + "AssertRouteComparisonPuppetAction.java");
        String source = Files.readString(action);

        assertTrue(source.contains(
                        "journey.baseline == null ? null : journey.baseline.toArtifactMap()"),
                "baseline evidence must flatten Optional fields before Gson sees them");
        assertTrue(source.contains("MachineObservation.capture(machine).toArtifactMap()"),
                "current machine evidence must flatten Optional fields before Gson sees them");
        assertFalse(source.contains("answer.put(\"machine_baseline\", journey.baseline);"),
                "Java 17 forbids Minecraft's Gson from reflectively serializing Optional");
    }

    private static void assertNoCalls(CompilationUnit unit, Set<String> forbidden) {
        List<String> calls = unit.findAll(MethodCallExpr.class).stream()
                .map(MethodCallExpr::getNameAsString)
                .filter(forbidden::contains)
                .toList();
        assertTrue(calls.isEmpty(), "test evidence bypasses a public action: " + calls);
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

    private static CompilationUnit parse(Path path) throws Exception {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        return StaticJavaParser.parse(path);
    }
}
