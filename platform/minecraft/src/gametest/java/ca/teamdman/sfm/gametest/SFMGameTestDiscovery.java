package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SFMGameTestDiscovery {
    private static final String GAME_TEST_SELECTION_PROPERTY = "sfm.gametestSelection";

    @SFMSubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // Discover our tests
        Collection<SFMGameTestDefinition> tests = gatherSelectedTests();

        // Discover the test registry
        Collection<TestFunction> allTestFunctions = GameTestRegistry.getAllTestFunctions();
        Collection<String> allTestClassNames = GameTestRegistry.getAllTestClassNames();

        // Manually register the tests
        for (SFMGameTestDefinition test : tests) {
            allTestFunctions.add(test.intoTestFunction());
            allTestClassNames.add(test.testName());
        }
    }

    public static Collection<SFMGameTestDefinition> gatherSelectedTests() {
        return filterSelectedTests(SFMGameTestDiscovery.gatherTests().toList());
    }

    public static Stream<SFMGameTestDefinition> gatherTests() {

        Stream<SFMGameTestDefinition> annotatedTests = SFMAnnotationUtils.discoverAnnotations(SFMGameTest.class)
                .map(SFMAnnotationUtils.SFMAnnotationData::tryLoadClass)
                .map(clazz -> SFMAnnotationUtils.tryConstruct(clazz, SFMGameTestDefinition.class))
                .peek(sfmGameTestDefinition -> SFM.LOGGER.info(
                        "Discovered SFM game test: {}",
                        sfmGameTestDefinition.testName()
                ));

        Stream<SFMGameTestDefinition> generatedTests = gatherGeneratedTests();

        return Stream.concat(annotatedTests, generatedTests);
    }

    public static Stream<SFMGameTestDefinition> gatherGeneratedTests() {

        List<SFMGameTestDefinition> generatedTests = new ArrayList<>();

        SFMAnnotationUtils.discoverAnnotations(SFMGameTestGenerator.class)
                .map(SFMAnnotationUtils.SFMAnnotationData::tryLoadClass)
                .map(clazz -> SFMAnnotationUtils.tryConstruct(clazz, SFMGameTestGeneratorBase.class))
                .forEach(generator -> {
                    SFM.LOGGER.info("Invoking SFM game test generator: {}", generator.getClass().getSimpleName());
                    generator.generateTests(test -> {
                        SFM.LOGGER.info("Generated SFM game test: {}", test.testName());
                        generatedTests.add(test);
                    });
                });

        return generatedTests.stream();
    }

    private static Collection<SFMGameTestDefinition> filterSelectedTests(Collection<SFMGameTestDefinition> tests) {

        String rawSelection = System.getProperty(GAME_TEST_SELECTION_PROPERTY, "").trim();
        if (rawSelection.isEmpty()) {
            return tests;
        }

        List<String> selectors = Stream.of(rawSelection.split(","))
                .map(String::trim)
                .filter(selector -> !selector.isEmpty())
                .map(SFMGameTestDiscovery::normalizeSelector)
                .toList();

        List<SFMGameTestDefinition> matchedTests = tests.stream()
                .filter(test -> matchesAnySelector(test, selectors))
                .toList();

        SFM.LOGGER.info(
                "Applying SFM game test selection '{}': matched {} of {} tests",
                rawSelection,
                matchedTests.size(),
                tests.size()
        );

        matchedTests.forEach(test -> SFM.LOGGER.info(
                "Selected SFM game test: {}",
                qualifyTestName(test)
        ));

        if (matchedTests.isEmpty()) {
            throw new IllegalStateException(
                    "SFM game test selection '" + rawSelection
                    + "' matched zero tests. Try an exact test name or a wildcard like 'sfm:wither_aggro_*'."
            );
        }

        return matchedTests;
    }

    private static boolean matchesAnySelector(
            SFMGameTestDefinition test,
            List<String> selectors
    ) {

        String qualifiedTestName = qualifyTestName(test);
        return selectors.stream().anyMatch(selector -> wildcardMatches(qualifiedTestName, selector));
    }

    private static String qualifyTestName(SFMGameTestDefinition test) {

        return SFM.MOD_ID + ":" + test.testName();
    }

    private static String normalizeSelector(String selector) {

        return selector.contains(":") ? selector : SFM.MOD_ID + ":" + selector;
    }

    private static boolean wildcardMatches(
            String candidate,
            String selector
    ) {

        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < selector.length(); i++) {
            char ch = selector.charAt(i);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        regex.append('$');
        return candidate.matches(regex.toString());
    }
}
