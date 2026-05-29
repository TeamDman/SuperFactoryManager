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
    public static final DeferredRegister<Consumer<GameTestHelper>> SFM_TEST_FUNCTION = DeferredRegister.create(
            BuiltInRegistries.TEST_FUNCTION,
            SFM.MOD_ID
    );

    private static final List<SFMGameTestData> TESTS;
    static {
        // Discover our tests
        Collection<SFMGameTestDefinition> tests = SFMGameTestDiscovery.gatherTests().toList();

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
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Registries.TEST_ENVIRONMENT.identifier());

        for (SFMGameTestData testData : TESTS) {
            event.registerTest(
                    Identifier.fromNamespaceAndPath(SFM.MOD_ID, testData.definition().testName()),
                    testData.definition().intoTestInstance(testData.functionKey(), environment)
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

    private record SFMGameTestData(
            ResourceKey<Consumer<GameTestHelper>> functionKey,
            SFMGameTestDefinition definition
    ) {}
}
