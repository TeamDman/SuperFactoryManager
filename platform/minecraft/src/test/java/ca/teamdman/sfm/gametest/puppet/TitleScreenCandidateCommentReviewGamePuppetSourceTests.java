package ca.teamdman.sfm.gametest.puppet;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
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

class TitleScreenCandidateCommentReviewGamePuppetSourceTests {
    private static final String DEFINITION =
            "src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/"
                    + "TitleScreenCandidateCommentReviewGamePuppet.java";

    @Test
    void naturalJourneyUsesPaletteActionsAndNeverMutatesTrajectoryInternals() throws Exception {
        CompilationUnit unit = parse(projectRoot().resolve(DEFINITION));
        MethodDeclaration run = unit.findFirst(MethodDeclaration.class,
                        method -> method.getNameAsString().equals("run"))
                .orElseThrow();
        String source = Files.readString(projectRoot().resolve(DEFINITION));

        List<String> requiredCommands = List.of(
                "sfm action invoke sfm:episode/trajectory/plan focused",
                "sfm action invoke sfm:review/comment/create/candidate/route focused route-retained",
                "sfm action invoke sfm:review/comment/edit focused candidate-human-1 route-retained-edited",
                "sfm action invoke sfm:review/comment/create/candidate/action focused action-select-hyphens",
                "sfm action invoke sfm:review/comment/create/candidate/state focused state-numbered-two",
                "sfm action invoke sfm:review/comment/create/candidate/glyph focused 3 9 glyph-apples-exact",
                "sfm action invoke sfm:review/comment/create/candidate/glyph focused 13 20 glyph-bananas-divergence",
                "sfm action invoke sfm:episode/trajectory/replan focused",
                "sfm action invoke sfm:episode/trajectory/route/select focused",
                "sfm action invoke sfm:review/comment/promote/exact focused candidate-human-4 candidate-comment-exact",
                "sfm action invoke sfm:review/comment/promote/exact focused candidate-human-5 candidate-comment-divergent",
                "sfm action invoke sfm:review/comment/migrate/witnessed focused candidate-human-5 25 32 ",
                "sfm action invoke sfm:review/comment/create/candidate/route focused unavailable-route",
                "sfm action invoke sfm:review/comment/create/candidate/action focused unavailable-action",
                "sfm action invoke sfm:review/comment/create/candidate/glyph focused 0 1 unavailable-glyph-rejected"
        );
        int cursor = -1;
        for (String command : requiredCommands) {
            int next = source.indexOf(command, cursor + 1);
            assertTrue(next > cursor, "Missing or out-of-order natural palette command: " + command);
            cursor = next;
        }

        assertTrue(source.contains("sfm action invoke sfm:review/comment/navigate focused \" + commentId"),
                "roundtrip navigation must use the ordinary palette action");
        assertEquals(2, run.findAll(MethodCallExpr.class).stream()
                        .filter(call -> call.getNameAsString().equals("reloadCandidateCommentSession"))
                        .count(),
                "both persisted sessions must be reloaded");
        assertEquals(7, run.findAll(MethodCallExpr.class).stream()
                        .filter(call -> Set.of("navigateMain", "navigateUnavailable")
                                .contains(call.getNameAsString()))
                        .count(),
                "every main and unavailable candidate target must be navigated after reload");
        assertTrue(run.findAll(MethodCallExpr.class).stream()
                        .anyMatch(call -> call.getNameAsString().equals("clickCandidateCommentRouteChoice")),
                "retained-route selection must click the constrained command palette");
        assertTrue(run.findAll(MethodCallExpr.class).stream()
                        .anyMatch(call -> call.getNameAsString().equals("rewindNaturalEdits")
                                && call.getArguments().size() == 2
                                && call.getArgument(1).toString().equals("12")),
                "the natural three-item fork must rewind every typed revision before route selection");
        assertTrue(source.contains("Stage.OLD_ROUTE_START_RESTORED"),
                "the journey must prove the retained route start before selecting its route");

        assertFalse(unit.getImports().stream().anyMatch(importDeclaration -> {
            String name = importDeclaration.getNameAsString();
            return name.contains("SFMDecimalNumberingTrajectoryController")
                    || name.contains("SFMReviewSessionRuntime")
                    || name.contains("SFMHistoryGraphRuntime");
        }), "the natural definition must not import controller or review-runtime internals");
        assertNoForbiddenMutationCalls(unit);

        for (StringLiteralExpr literal : unit.findAll(StringLiteralExpr.class)) {
            if (!literal.asString().contains("sfm:review/comment/")) continue;
            MethodCallExpr owner = literal.findAncestor(MethodCallExpr.class).orElseThrow();
            assertTrue(Set.of("palette", "contains").contains(owner.getNameAsString()),
                    "review behavior must enter through a palette command: " + literal);
        }
    }

    @Test
    void testActionsReadEvidenceButDoNotBypassPublicTrajectoryActions() throws Exception {
        Path actionRoot = projectRoot().resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/action");
        List<String> actionFiles = List.of(
                "AssertCandidateCommentReviewPuppetAction.java",
                "AssertCandidateCommentNavigationPuppetAction.java",
                "CandidateCommentSessionPuppetAction.java",
                "ClickCandidateCommentRouteChoicePuppetAction.java"
        );
        for (String file : actionFiles) {
            CompilationUnit unit = parse(actionRoot.resolve(file));
            assertNoForbiddenMutationCalls(unit);
            assertFalse(unit.getImports().stream().anyMatch(importDeclaration ->
                            importDeclaration.getNameAsString().contains(
                                    "SFMDecimalNumberingTrajectoryController")),
                    file + " must not acquire the chamber controller");
        }

        CompilationUnit click = parse(
                actionRoot.resolve("ClickCandidateCommentRouteChoicePuppetAction.java"));
        List<String> calls = click.findAll(MethodCallExpr.class).stream()
                .map(MethodCallExpr::getNameAsString)
                .toList();
        assertTrue(calls.contains("clickActionChoice"),
                "retained-route selection must activate the visible constrained choice");
        assertFalse(calls.contains("execute"),
                "the test route-choice action must not execute the history runtime directly");
    }

    private static void assertNoForbiddenMutationCalls(CompilationUnit unit) {
        Set<String> forbidden = Set.of(
                "projectCandidateRoute",
                "applyIntent",
                "commitNaturalEdit",
                "checkout",
                "undo",
                "selectRoute",
                "createCandidateComment",
                "editComment",
                "promoteExact",
                "migrateWitnessed"
        );
        List<String> used = unit.findAll(MethodCallExpr.class).stream()
                .map(MethodCallExpr::getNameAsString)
                .filter(forbidden::contains)
                .toList();
        assertTrue(used.isEmpty(), "test journey bypasses a public user action: " + used);
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
