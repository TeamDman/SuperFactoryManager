package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import ca.teamdman.sfm.common.event_bus.SFMEventBus;
{% endcase %}
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
{% if features.gametest_sides %}
import ca.teamdman.sfm.common.util.SFMDist;
{% endif %}
{% if features.client_properties %}
import ca.teamdman.sfm.properties.SFMProperties;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.event.RegisterGameTestsEvent;
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
{% when "26.1.2" %}
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
{% endcase %}
import java.util.ArrayList;
import java.util.Collection;
{% if features.gametest_sides %}
import java.util.EnumSet;
{% endif %}
import java.util.List;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import java.util.regex.Pattern;
{% when "26.1.2" %}
import java.util.function.Consumer;
{% endcase %}
import java.util.stream.Stream;

public class SFMGameTestDiscovery {
{% if features.client_properties %}
{% else %}
    private static final String GAME_TEST_SELECTION_PROPERTY = "sfm.gametestSelection";

{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @SFMSubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
{% when "26.1.2" %}
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
{% endcase %}
        // Discover our tests
        Collection<SFMGameTestDefinition> tests = gatherSelectedTests();

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        // Discover the test registry
        Collection<TestFunction> allTestFunctions = GameTestRegistry.getAllTestFunctions();
        Collection<String> allTestClassNames = GameTestRegistry.getAllTestClassNames();
{% when "26.1.2" %}
        TESTS = tests.stream().map(test -> {
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(
                    BuiltInRegistries.TEST_FUNCTION.key(),
                    Identifier.fromNamespaceAndPath(SFM.MOD_ID, test.testName())
            );
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        // Manually register the tests
        for (SFMGameTestDefinition test : tests) {
            allTestFunctions.add(test.intoTestFunction());
            allTestClassNames.add(test.testName());
{% when "26.1.2" %}
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
{% endcase %}
        }
    }

    public static Collection<SFMGameTestDefinition> gatherSelectedTests() {
        return filterSelectedTests(SFMGameTestDiscovery.gatherTests().toList());
    }

    public static Stream<SFMGameTestDefinition> gatherTests() {

        Stream<SFMGameTestDefinition> annotatedTests = SFMAnnotationUtils.discoverAnnotations(SFMGameTest.class)
{% if features.gametest_sides %}
                .filter(SFMGameTestDiscovery::isCompatibleWithCurrentDist)
{% endif %}
                .map(SFMAnnotationUtils.SFMAnnotationData::tryLoadClass)
                .map(clazz -> SFMAnnotationUtils.tryConstruct(clazz, SFMGameTestDefinition.class))
                .peek(sfmGameTestDefinition -> SFM.LOGGER.info(
                        "Discovered SFM game test: {}",
                        sfmGameTestDefinition.testName()
                ));

        Stream<SFMGameTestDefinition> generatedTests = gatherGeneratedTests();

        return Stream.concat(annotatedTests, generatedTests);
    }

{% if features.gametest_sides %}
    private static boolean isCompatibleWithCurrentDist(
            SFMAnnotationUtils.SFMAnnotationData annotationData
    ) {

        EnumSet<SFMDist> compatibleDists = annotationData.getEnumSet("value", SFMDist.class);
        if (compatibleDists.isEmpty()) {
            compatibleDists = EnumSet.allOf(SFMDist.class);
        }

        SFMDist currentDist = SFMDist.current();
        boolean compatible = compatibleDists.contains(currentDist);
        if (!compatible) {
            SFM.LOGGER.info(
                    "Skipping SFM game test on physical side {}: {} (compatible sides: {})",
                    currentDist,
                    annotationData.clazz().getClassName(),
                    compatibleDists
            );
        }
        return compatible;
    }

{% endif %}
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

{% if features.client_properties %}
        String rawSelection = SFMProperties.gameTestSelection();
{% else %}
        String rawSelection = System.getProperty(GAME_TEST_SELECTION_PROPERTY, "").trim();
{% endif %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            String candidate,
            String selector
{% when "26.1.2" %}
            String text,
            String wildcardPattern
{% endcase %}
    ) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < selector.length(); i++) {
            char ch = selector.charAt(i);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(ch)));
{% when "26.1.2" %}
        int textLength = text.length();
        int patternLength = wildcardPattern.length();
        boolean[][] matches = new boolean[textLength + 1][patternLength + 1];
        matches[0][0] = true;

        for (int patternIndex = 1; patternIndex <= patternLength; patternIndex++) {
            if (wildcardPattern.charAt(patternIndex - 1) == '*') {
                matches[0][patternIndex] = matches[0][patternIndex - 1];
{% endcase %}
            }
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        regex.append('$');
        return candidate.matches(regex.toString());
{% when "26.1.2" %}

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
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}

    private record SFMGameTestData(
            ResourceKey<Consumer<GameTestHelper>> functionKey,
            SFMGameTestDefinition definition
    ) {}
{% endcase %}
}
