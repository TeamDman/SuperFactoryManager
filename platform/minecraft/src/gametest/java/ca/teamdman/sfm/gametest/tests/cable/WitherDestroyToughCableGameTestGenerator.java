package ca.teamdman.sfm.gametest.tests.cable;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestGenerator;
import ca.teamdman.sfm.gametest.SFMGameTestGeneratorBase;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SFMGameTestGenerator
public class WitherDestroyToughCableGameTestGenerator extends SFMGameTestGeneratorBase {

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario(
                    "destroy_event_tough_cable_facaded_as_bedrock_denied",
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    false
            ),
            new Scenario(
                    "destroy_event_tough_cable_facaded_as_obsidian_allowed",
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "destroy_event_tough_fancy_cable_facaded_as_bedrock_denied",
                    SFMBlocks.TOUGH_FANCY_CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    false
            ),
            new Scenario(
                    "destroy_event_tough_fancy_cable_facaded_as_obsidian_allowed",
                    SFMBlocks.TOUGH_FANCY_CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "destroy_event_cable_facaded_as_bedrock_allowed",
                    SFMBlocks.CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "destroy_event_cable_facaded_as_obsidian_allowed",
                    SFMBlocks.CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "destroy_event_fancy_cable_facaded_as_bedrock_allowed",
                    SFMBlocks.FANCY_CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "destroy_event_fancy_cable_facaded_as_obsidian_allowed",
                    SFMBlocks.FANCY_CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "destroy_event_tough_cable_unfacaded_allowed",
                    SFMBlocks.TOUGH_CABLE::get,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "destroy_event_cable_unfacaded_allowed",
                    SFMBlocks.CABLE::get,
                    Optional.empty(),
                    true
            )
    );

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> testConsumer) {

        for (Scenario scenario : SCENARIOS) {
            testConsumer.accept(new ToughCableDestroyEventScenarioGameTest(scenario));
        }
    }

    private record Scenario(
            String name,

            Supplier<Block> blockSupplier,

            Optional<BlockState> facadeState,

            boolean expectedAllowDestroy
    ) {
    }

    private static class ToughCableDestroyEventScenarioGameTest extends SFMGameTestDefinition {
        private final Scenario scenario;

        private ToughCableDestroyEventScenarioGameTest(Scenario scenario) {

            this.scenario = scenario;
        }

        @Override
        public String template() {

            return "1x4x1";
        }

        @Override
        public String testName() {

            return scenario.name;
        }

        @Override
        public int maxTicks() {

            return 80;
        }

        @Override
        public void run(SFMGameTestHelper helper) {

            if (helper.getLevel().getDifficulty() == Difficulty.PEACEFUL) {
                helper.fail("Difficulty must not be PEACEFUL to run wither destroy-event scenario");
                return;
            }

            BlockPos targetBlockLocalPos = new BlockPos(0, 2, 0);
            helper.setBlock(targetBlockLocalPos, scenario.blockSupplier.get().defaultBlockState());
            scenario.facadeState.ifPresent(mimicBlockState -> helper.setFacade(targetBlockLocalPos, mimicBlockState));


            if (helper.getLevel().getDifficulty().equals(Difficulty.PEACEFUL)) {
                helper.fail("Difficulty must not be PEACEFUL to run wither test");
                return;
            }

            Vec3 witherSpawn = helper.absoluteVec(new Vec3(0.5, 3.0, 0.5));
            WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
            assert wither != null;
            wither.setNoAi(true);
            wither.moveTo(witherSpawn.x, witherSpawn.y, witherSpawn.z, 0, 0);
            helper.getLevel().addFreshEntity(wither);

            BlockState state = helper.getBlockState(targetBlockLocalPos);
            boolean canEntityDestroy = state.canEntityDestroy(
                    helper.getLevel(),
                    helper.absolutePos(targetBlockLocalPos),
                    wither
            );

            if (canEntityDestroy != scenario.expectedAllowDestroy) {
                wither.discard();
                helper.fail(
                        "Scenario '" + scenario.name + "' had unexpected onEntityDestroyBlock result: "
                        + "expected allowDestroy=" + scenario.expectedAllowDestroy
                        + ", canEntityDestroy=" + canEntityDestroy,
                        targetBlockLocalPos
                );
            }

            helper.runAfterDelay(
                    20, () -> {
                        wither.discard();
                        helper.succeed();
                    }
            );
        }

    }

}
