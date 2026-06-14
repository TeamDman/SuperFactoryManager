package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SFMGameTestDiscovery {
    private static final String GAME_TEST_SELECTION_PROPERTY = "sfm.gametestSelection";

    public static final ResourceKey<TestEnvironmentDefinition<?>> SFM_TEST_ENVIRONMENT = ResourceKey.create(
            Registries.TEST_ENVIRONMENT,
            Identifier.fromNamespaceAndPath(SFM.MOD_ID, "default")
    );

    public static final DeferredRegister<Consumer<GameTestHelper>> SFM_TEST_FUNCTION = DeferredRegister.create(
            BuiltInRegistries.TEST_FUNCTION,
            SFM.MOD_ID
    );

    private static final List<SFMGameTestData> TESTS;
    static {
        // Discover our tests
        Collection<SFMGameTestDefinition> tests = gatherSelectedTests();

        TESTS = tests.stream().map(test -> {
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(
                    BuiltInRegistries.TEST_FUNCTION.key(),
                    Identifier.fromNamespaceAndPath(SFM.MOD_ID, test.testName())
            );

            SFM.LOGGER.info("Registering SFM game test: {}", test);

            SFM_TEST_FUNCTION.register(test.testName(), () -> test::intoTestFunction);
            return new SFMGameTestData(key, test);
        }).toList();

        SFM_TEST_FUNCTION.register(SFMEventBus.MOD_BUS);
    }

    @SFMSubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(SFM_TEST_ENVIRONMENT.identifier());

        for (SFMGameTestData testData : TESTS) {
            event.registerTest(
                    Identifier.fromNamespaceAndPath(SFM.MOD_ID, testData.definition().testName()),
                    testData.definition().intoTestInstance(testData.functionKey(), environment)
            );
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
            String text,
            String wildcardPattern
    ) {

        int textLength = text.length();
        int patternLength = wildcardPattern.length();
        boolean[][] matches = new boolean[textLength + 1][patternLength + 1];
        matches[0][0] = true;

        for (int patternIndex = 1; patternIndex <= patternLength; patternIndex++) {
            if (wildcardPattern.charAt(patternIndex - 1) == '*') {
                matches[0][patternIndex] = matches[0][patternIndex - 1];
            }
        }

        for (int textIndex = 1; textIndex <= textLength; textIndex++) {
            for (int patternIndex = 1; patternIndex <= patternLength; patternIndex++) {
                char patternCharacter = wildcardPattern.charAt(patternIndex - 1);
                if (patternCharacter == '*') {
                    matches[textIndex][patternIndex] = matches[textIndex][patternIndex - 1]
                                                       || matches[textIndex - 1][patternIndex];
                } else if (patternCharacter == '?' || patternCharacter == text.charAt(textIndex - 1)) {
                    matches[textIndex][patternIndex] = matches[textIndex - 1][patternIndex - 1];
                }
            }
        }

        return matches[textLength][patternLength];
    }

    private record SFMGameTestData(
            ResourceKey<Consumer<GameTestHelper>> functionKey,
            SFMGameTestDefinition definition
    ) {}
}
