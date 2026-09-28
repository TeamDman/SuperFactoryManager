package ca.teamdman.sfml.test;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMDisplayUtils;
import ca.teamdman.sfml.ast.ASTNode;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ext_antlr4c3.CodeCompletionCore;
import ca.teamdman.sfml.intellisense.IntellisenseAction;
import ca.teamdman.sfml.intellisense.IntellisenseContext;
import ca.teamdman.sfml.intellisense.SFMLIntellisense;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;
import org.simmetrics.StringDistance;
import org.simmetrics.metrics.StringDistances;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class SFMLIntellisenseTests {

    private static final String SIMPLE_PROGRAM_STRING = """
            NAME "hello"
            EVERY 20 TICKS DO
              INPUT FROM a
              OUTPUT TO b
            END
            """.stripTrailing().stripIndent();

    /**
     * A small snippet of code where we have "INPUT 64 IRON_INGOT FROM Minecart"
     * The 'IRON_INGOT' portion is recognized by the grammar as resourceId,
     * and 'Minecart' is recognized as a label.
     */
    private static final String PROGRAM_SNIPPET = """
            EVERY 20 TICKS DO
              INPUT 64 iron_ingot FROM my_chest
            END
            """;

    @Test
    public void displayTokens() {

        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(SIMPLE_PROGRAM_STRING));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        int cursorPosition = 0;
        int i = 0;
        for (Token token : tokens.get(0, cursorPosition)) {
            if (i++ == cursorPosition) {
                System.out.print("|");
            }

            String text = token.getText();
            if (text.isBlank()) {
                System.out.printf("'%s'", text);
            } else {
                System.out.printf("%s", text);
            }
        }
    }

    @Test
    public void itWorksTest() {

        String programString = """
                NAME "hi"
                EVERY 20
                """.stripTrailing().stripIndent();
        for (int cursorPosition = 0; cursorPosition < countTokens(programString); cursorPosition++) {
            StringBuilder display = new StringBuilder();

            ProgramBuildResult buildResult = new ProgramBuilder(programString).build();
            List<IntellisenseAction> suggestions = SFMLIntellisense.getSuggestions(new IntellisenseContext(
                    buildResult,
                    cursorPosition,
                    0,
                    LabelPositionHolder.empty(),
                    SFMTextEditorIntellisenseLevel.BASIC
            ));
            for (IntellisenseAction suggestion : suggestions) {
                display.append("Suggestion: ");
                display.append(suggestion.getComponent().getString());
                display.append('\n');
            }
            display.append('\n');
            String finalDisplay = display.toString();
            System.out.println("Cursor position: " + cursorPosition);
            System.out.println(finalDisplay);
        }
    }

    @Test
    public void getNodesUnderCursor() {

        String programString = SIMPLE_PROGRAM_STRING;

        // Build the program
        AtomicReference<Program> program = new AtomicReference<>();

        new ProgramBuilder(programString).build()
                .caseSuccess((successProgram, metadata) -> program.set(successProgram))
                .caseFailure(result -> {
                    result.metadata().errors().forEach(error -> System.out.println(error.toString()));
                    throw new RuntimeException("Failed to compile program");
                });
        assertNotNull(program.get());

        // Put each cursor position as a new line in the stdout
        for (int cursorPos = 0; cursorPos < programString.length(); cursorPos++) {
            System.out.print(SFMDisplayUtils.getCursorPositionDisplay(programString, cursorPos));

            // print the tokens under the cursor
//            System.out.print(" [");
            for (Pair<ASTNode, ParserRuleContext> pair : program.get().astBuilder().getNodesUnderCursor(cursorPos)) {
                ASTNode node = pair.getFirst();
                ParserRuleContext nodeContext = pair.getSecond();
                System.out.printf("%s(%s) ", node.getClass().getSimpleName(), nodeContext.getText());
            }
//            System.out.print(" ]");
            System.out.println();
        }
    }

    @Test
    public void combinedTest() {

        String programString = SIMPLE_PROGRAM_STRING;

        ProgramBuildResult buildResult = new ProgramBuilder(programString).build().caseFailure(failure -> {
            failure.metadata().errors().forEach(error -> System.out.println(error.toString()));
            throw new RuntimeException("Failed to compile program");
        });
        Program program = Objects.requireNonNull(buildResult.program());
        for (int cursorPos = 0; cursorPos < programString.length(); cursorPos++) {
            System.out.print("||| ");
            System.out.printf("%s", SFMDisplayUtils.getCursorPositionDisplay(programString, cursorPos));
            System.out.print(" ||| ");
            System.out.printf("%s", SFMDisplayUtils.getTokenHierarchyDisplay(program, cursorPos));
            System.out.print(" ||| ");
            System.out.printf("%s", SFMDisplayUtils.getSuggestionsDisplay(buildResult, cursorPos));
            System.out.print(" |||");
            System.out.println();
        }
    }

    @Test
    public void shouldRankNameFirst() {
        // The user typed "NAME"
        String typed = "NAME";

        // The intellisense suggestions we want to rank
        List<String> suggestions = Arrays.asList("EOF", "NAME", "EVERY");

        // We'll use the Levenshtein distance from simmetrics
        StringDistance distance = StringDistances.levenshtein();

        // Make a copy of suggestions so we can sort them
        List<String> sorted = new ArrayList<>(suggestions);

        // Sort ascending by distance to the typed string
        sorted.sort(Comparator.comparing(s -> distance.distance(s, typed)));

        // Now "NAME" should be first because distance("NAME","NAME") = 0
        assertEquals("NAME", sorted.get(0));

        // Optional: Check the sorted order, just as an example
        // Not strictly necessary for a real test, but useful for demonstration
        System.out.println("Typed: " + typed + " => Sorted suggestions: " + sorted);
    }

    @Test
    public void lavaSearch() {

        String typed = "lava";
        List<String> suggestions = Arrays.asList(
                "TO",
                "WITH",
                "EXCEPT",
                "NUMBER",
                "RETAIN",
                "WITHOUT",
                "fluid:minecraft:lava",
                "botania:lava_pendant",
                "ars_nouveau:lava_lily",
                "minecraft:lava_bucket",
                "fluid:minecraft:flowing_lava",
                "botania:super_lava_pendant"
        );

        StringDistance[] distances = new StringDistance[]{
                StringDistances.blockDistance(),
                StringDistances.cosineSimilarity(),
                StringDistances.damerauLevenshtein(),
                StringDistances.dice(),
                StringDistances.euclideanDistance(),
                StringDistances.generalizedJaccard(),
                StringDistances.hammingDistance(),
                StringDistances.identity(),
                StringDistances.jaccard(),
                StringDistances.jaro(),
                StringDistances.jaroWinkler(),
                StringDistances.levenshtein(),
                StringDistances.longestCommonSubsequence(),
                StringDistances.longestCommonSubstring(),
                StringDistances.overlapCoefficient(),
                StringDistances.qGramsDistance(),
                StringDistances.simonWhite(),
                };
        String[] distanceNames = new String[]{
                "blockDistance",
                "cosineSimilarity",
                "damerauLevenshtein",
                "dice",
                "euclideanDistance",
                "generalizedJaccard",
                "hammingDistance",
                "identity",
                "jaccard",
                "jaro",
                "jaroWinkler",
                "levenshtein",
                "longestCommonSubsequence",
                "longestCommonSubstring",
                "overlapCoefficient",
                "qGramsDistance",
                "simonWhite",
                };
        List<String> sorted = new ArrayList<>(suggestions);
        for (int i = 0; i < distances.length; i++) {
            try {

                StringDistance distance = distances[i];
                String distanceName = distanceNames[i];
                System.out.printf("Using distance metric: %s and query %s\n", distanceName, typed);
                long startTime = System.nanoTime();
                sorted.sort(Comparator.comparing(s -> distance.distance(s, typed)));
                long endTime = System.nanoTime();
                System.out.println("Typed: " + typed + " => Sorted suggestions: " + sorted);
                for (String s : sorted) {
                    System.out.printf(
                            "Distance: %.2f %.2f, suggestion: %s\n",
                            distance.distance(s, typed),
                            distance.distance(typed, s),
                            s
                    );
                }
                System.out.printf("Time taken: %d ns\n", (endTime - startTime));
                System.out.println();
            } catch (IllegalArgumentException t) {
                //noinspection CallToPrintStackTrace
                t.printStackTrace();
            }
        }
        assertTrue(sorted.get(0).contains("lava"));
    }

    @Test
    public void getRulesTest1() {
//        String outerProgramString = """
//                EVERY
//                """.stripTrailing().stripIndent();
        String outerProgramString = SIMPLE_PROGRAM_STRING;
        boolean someRulesWereFound = false;

//        for (int chop = 0; chop < outerProgramString.length(); chop++) {
        for (int chop = outerProgramString.length() - 1; chop < outerProgramString.length(); chop++) {
            String programString = outerProgramString.substring(0, chop);

            ProgramBuildResult buildResult = new ProgramBuilder(programString).build();
            SFMLParser parser = buildResult.metadata().parser();
            var contexts = new ParserRuleContext[]{
                    parser.program(),
                    parser.inputStatement(),
                    parser.inputResourceLimits(),
                    parser.resourceLimitList(),
                    parser.resourceIdDisjunction(),
                    parser.resourceId()
            };
            CodeCompletionCore core = new CodeCompletionCore(
                    parser,
//                    IntStream.range(0, SFMLParser.ruleNames.length).boxed().collect(Collectors.toSet()),
                    Set.of(
                            SFMLParser.RULE_program,
                            SFMLParser.RULE_inputStatement,
                            SFMLParser.RULE_inputResourceLimits,
                            SFMLParser.RULE_resourceLimitList,
                            SFMLParser.RULE_resourceIdDisjunction,
                            SFMLParser.RULE_resourceId
                    ),
                    null
//                    Set.of(SFMLLexer.WS, SFMLLexer.EOF)
            );
            CommonTokenStream tokens = buildResult.metadata().tokens();
            for (ParserRuleContext context : contexts) {
                System.out.printf("Context: %s\n", context.getClass().getSimpleName());
                for (int caretTokenIndex = 0; caretTokenIndex < tokens.size(); caretTokenIndex++) {
                    System.out.printf(
                            "%s",
                            SFMDisplayUtils.getCursorPositionDisplay(
                                    programString,
                                    tokens.get(caretTokenIndex).getStartIndex()
                            )
                    );
                    CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(
                            caretTokenIndex,
                            context
                    );
                    Vocabulary vocabulary = parser.getVocabulary();
                    candidates.tokens.forEach((key, value) -> System.out.printf(
                            "\"%s\" ",
                            Stream
                                    .concat(
                                            Stream.of(key),
                                            value.stream()
                                    )
                                    .map(vocabulary::getSymbolicName)
                                    .collect(Collectors.joining(
                                            ", ",
                                            "[",
                                            "]"
                                    ))
                    ));
                    System.out.print(" ||| ");
                    candidates.rules.forEach((head, tail) -> System.out.printf(
                            "\"%s\" ",
                            Stream
                                    .concat(
                                            Stream.of(head),
                                            tail.stream()
                                    )
                                    .map(x -> SFMLParser.ruleNames[x])
                                    .collect(Collectors.joining(
                                            ", ",
                                            "[",
                                            "]"
                                    ))
                    ));
                    System.out.println();
                    if (!candidates.rules.isEmpty()) {
                        someRulesWereFound = true;
                    }
                }
            }
        }
        assertTrue(someRulesWereFound);
    }

    @Test
    public void getRulesTest2() {

        String programString = """
                EVERY 20 TICKS DO
                  INPUT stone FROM chest
                END
                """.stripTrailing().stripIndent();

        ProgramBuildResult buildResult = new ProgramBuilder(programString).build();
        SFMLParser parser = buildResult.metadata().parser();
        CodeCompletionCore core = new CodeCompletionCore(
                parser,
                Set.of(
                        SFMLParser.RULE_identifier,
                        SFMLParser.RULE_string,
                        SFMLParser.RULE_number
//                        SFMLParser.RULE_label,
//                        SFMLParser.RULE_resourceId
                ),
//                Collections.emptySet(),
                Set.of(SFMLLexer.WS, SFMLLexer.EOF)
        );
        String needle = "stone";
        Token caretToken = buildResult.getTokenAtCursorPosition(programString.indexOf(needle) + needle.length() / 2);
        assert caretToken != null;
        int caretTokenIndex = caretToken.getTokenIndex();
        System.out.printf("%s\n", SFMDisplayUtils.getCursorTokenDisplay(buildResult, caretToken.getStartIndex() + 1));
//        int caretTokenIndex = 1;
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(
                caretTokenIndex,
//                parser.program()
                null
        );
        Vocabulary vocabulary = parser.getVocabulary();
        String[] ruleNames = parser.getRuleNames();
        candidates.tokens.forEach((key, value) -> {
            System.out.print("(TOKENS) ");
            System.out.print(vocabulary.getSymbolicName(key) + ": ");
            System.out.print("[");
            for (int i : value) {
                System.out.print(vocabulary.getSymbolicName(i) + " ");
            }
            System.out.print("]");
            System.out.println();
        });
        candidates.rules.forEach((head, tail) -> {
            System.out.print("(RULES) ");
            System.out.print(ruleNames[head] + ": ");
            System.out.print("[");
            for (int i : tail) {
                System.out.print(ruleNames[i] + " ");
            }
            System.out.print("]");
            System.out.println();
        });
        assertFalse(candidates.rules.isEmpty());
    }

    @Test
    public void getRulesTest3() {

        String programString = """
                EVERY 20 TICKS DO
                  INPUT stone FROM chest
                END
                """.stripTrailing().stripIndent();

        ProgramBuildResult buildResult = new ProgramBuilder(programString).build();
        SFMLParser parser = buildResult.metadata().parser();
        CodeCompletionCore core = new CodeCompletionCore(
                parser,
                Set.of(
//                        SFMLParser.RULE_identifier,
//                        SFMLParser.RULE_string,
//                        SFMLParser.RULE_number,
                        SFMLParser.RULE_label,
                        SFMLParser.RULE_resourceId
                ),
//                Collections.emptySet(),
                Set.of(SFMLLexer.WS, SFMLLexer.EOF)
        );
        String needle = "chest";
        Token caretToken = buildResult.getTokenAtCursorPosition(programString.indexOf(needle) + needle.length() / 2);
        assert caretToken != null;
        int caretTokenIndex = caretToken.getTokenIndex();
        System.out.printf("%s\n", SFMDisplayUtils.getCursorTokenDisplay(buildResult, caretToken.getStartIndex() + 1));

//        int caretTokenIndex = 1;
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(
                caretTokenIndex,
//                parser.program()
                null
        );
        Vocabulary vocabulary = parser.getVocabulary();
        String[] ruleNames = parser.getRuleNames();
        candidates.tokens.forEach((key, value) -> {
            System.out.print("(TOKENS) ");
            System.out.print(vocabulary.getSymbolicName(key) + ": ");
            System.out.print("[");
            for (int i : value) {
                System.out.print(vocabulary.getSymbolicName(i) + " ");
            }
            System.out.print("]");
            System.out.println();
        });
        candidates.rules.forEach((head, tail) -> {
            System.out.print("(RULES) ");
            System.out.print(ruleNames[head] + ": ");
            System.out.print("[");
            for (int i : tail) {
                System.out.print(ruleNames[i] + " ");
            }
            System.out.print("]");
            System.out.println();
        });
        assertFalse(candidates.rules.isEmpty());
    }

    /**
     * Demonstrates that the caret inside "IRON_INGOT" is recognized
     * by the grammar as resourceId context (RULE_resourceId).
     */
    @Test
    public void testCursorInsideResourceId() {
        // 1) Build the snippet into a ProgramBuildResult

        ProgramBuildResult buildResult = new ProgramBuilder(PROGRAM_SNIPPET).build();
        assertTrue(buildResult.isBuildSuccessful(), "Snippet must parse successfully");

        // 2) Find a cursor position somewhere in IRON_INGOT
        int resourceCaret = PROGRAM_SNIPPET.indexOf("iron_ingot") + 4;

        // 3) Find the Token covering that caret position
        Token caretToken = buildResult.getTokenAtCursorPosition(resourceCaret);
        assertNotNull(caretToken, "Should find a token around IRON_INGOT");
        int caretTokenIndex = caretToken.getTokenIndex();

        // 4) Create the code completion engine, specifying resourceId & label as preferred
        SFMLParser parser = buildResult.metadata().parser();
        Set<Integer> preferredRules = Set.of(
                SFMLParser.RULE_resourceId,
                SFMLParser.RULE_label
        );
        Set<Integer> ignoredTokens = Set.of(SFMLLexer.WS, SFMLLexer.EOF);
        CodeCompletionCore core = new CodeCompletionCore(parser, preferredRules, ignoredTokens);

        // 5) Collect candidates from the top-level context, or from parser.program()
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(caretTokenIndex, null);

        // 6) Check which rules show up in candidates
        //    The candidates.rules map: key=ruleIndex, value=some path
        assertTrue(
                candidates.rules.containsKey(SFMLParser.RULE_resourceId),
                "Should contain resourceId in the rule candidates"
        );
        assertFalse(
                candidates.rules.containsKey(SFMLParser.RULE_label),
                "Should NOT contain label here, because we are in resourceId"
        );
    }

    /**
     * Demonstrates that the caret inside "Minecart" is recognized
     * by the grammar as label context (RULE_label).
     */
    @Test
    public void testCursorInsideLabel() {
        // Use the same snippet, but put the cursor in the label area

        ProgramBuildResult buildResult = new ProgramBuilder(PROGRAM_SNIPPET).build();
        assertTrue(buildResult.isBuildSuccessful(), "Snippet must parse successfully");

        // Cursor in "Minecart"
        int labelCaret = PROGRAM_SNIPPET.indexOf("my_chest") + 3;

        Token caretToken = buildResult.getTokenAtCursorPosition(labelCaret);
        assertNotNull(caretToken, "Should find a token around Minecart");
        int caretTokenIndex = caretToken.getTokenIndex();

        SFMLParser parser = buildResult.metadata().parser();
        Set<Integer> preferredRules = Set.of(
                SFMLParser.RULE_resourceId,
                SFMLParser.RULE_label
        );
        Set<Integer> ignoredTokens = Set.of(SFMLLexer.WS, SFMLLexer.EOF);

        CodeCompletionCore core = new CodeCompletionCore(parser, preferredRules, ignoredTokens);
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(caretTokenIndex, null);

        // Now we expect label, not resourceId
        assertFalse(
                candidates.rules.containsKey(SFMLParser.RULE_resourceId),
                "Should NOT contain resourceId for 'Minecart' location"
        );
        assertTrue(
                candidates.rules.containsKey(SFMLParser.RULE_label),
                "Should contain label in the rule candidates"
        );
    }

    private static int countTokens(String program) {

        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(program));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        return tokens.size();
    }

}
