package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class SFMGameTestDiscovery {
    @SFMSubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // Discover our tests
        Collection<SFMGameTestDefinition> tests = SFMGameTestDiscovery.gatherTests().toList();

        // Discover the test registry
        Collection<TestFunction> allTestFunctions = GameTestRegistry.getAllTestFunctions();
        Collection<String> allTestClassNames = GameTestRegistry.getAllTestClassNames();

        // Manually register the tests
        for (SFMGameTestDefinition test : tests) {
            allTestFunctions.add(test.intoTestFunction());
            allTestClassNames.add(test.testName());
        }
    }

    public static Stream<SFMGameTestDefinition> gatherTests() {

        Stream<SFMGameTestDefinition> annotatedTests = SFMAnnotationUtils.discoverAnnotations(SFMGameTest.class)
                .map(SFMAnnotationUtils::tryLoadAnnotatedClass)
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
                .map(SFMAnnotationUtils::tryLoadAnnotatedClass)
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

}
