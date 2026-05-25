package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMAnnotationUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestEnvironments;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class SFMGameTestDiscovery {
    @SFMSubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // Discover our tests
        Collection<SFMGameTestDefinition> tests = SFMGameTestDiscovery.gatherTests().toList();

        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(SFM.MOD_ID, "default")
        );

        for (SFMGameTestDefinition test : tests) {
            event.registerTest(
                    Identifier.fromNamespaceAndPath(SFM.MOD_ID, test.testName()),
                    test.intoTestInstance(env)
            );
        }
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

}
