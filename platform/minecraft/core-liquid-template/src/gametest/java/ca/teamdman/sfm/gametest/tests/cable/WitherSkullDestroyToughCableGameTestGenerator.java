package ca.teamdman.sfm.gametest.tests.cable;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestGenerator;
import ca.teamdman.sfm.gametest.SFMGameTestGeneratorBase;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.world.entity.EntitySpawnReason;
{% endcase %}
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.world.entity.projectile.WitherSkull;
{% when '26.1.2' %}
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
{% endcase %}
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SFMGameTestGenerator
public class WitherSkullDestroyToughCableGameTestGenerator extends SFMGameTestGeneratorBase {

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario(
                    "wither_skull_destroys_obsidian",
                    () -> Blocks.OBSIDIAN,
                    Optional.empty(),
                    false
            ),
            new Scenario(
                    "wither_skull_does_not_destroy_bedrock",
                    () -> Blocks.BEDROCK,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "wither_skull_destroys_tough_cable_unfacaded",
                    SFMBlocks.TOUGH_CABLE::get,
                    Optional.empty(),
                    false
            ),
            new Scenario(
                    "wither_skull_destroys_tough_cable_facaded_as_obsidian",
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    false
            ),
            new Scenario(
                    "wither_skull_does_not_destroy_tough_cable_facaded_as_bedrock",
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    true
            )
    );

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> testConsumer) {

        for (Scenario scenario : SCENARIOS) {
            testConsumer.accept(new WitherSkullDestroyScenarioGameTest(scenario));
        }
    }

    private record Scenario(
            String name,

            Supplier<Block> blockSupplier,

            Optional<BlockState> facadeState,

            boolean shouldSurvive
    ) {
    }

    private static class WitherSkullDestroyScenarioGameTest extends SFMGameTestDefinition {
        private final Scenario scenario;

        private WitherSkullDestroyScenarioGameTest(Scenario scenario) {

            this.scenario = scenario;
        }

        @Override
        public String template() {

            return "5x4x5";
        }

        @Override
        public String testName() {

            return scenario.name;
        }

        @Override
        public int maxTicks() {

            return 100;
        }

        @Override
        public void run(SFMGameTestHelper helper) {

            BlockPos targetBlockLocalPos = new BlockPos(2, 2, 2);
            helper.setBlock(targetBlockLocalPos, scenario.blockSupplier.get().defaultBlockState());
            scenario.facadeState.ifPresent(mimicBlockState -> helper.setFacade(targetBlockLocalPos, mimicBlockState));


            Vec3 targetCenter = helper.absoluteVec(new Vec3(
                    targetBlockLocalPos.getX() + 0.5,
                    targetBlockLocalPos.getY() + 0.5,
                    targetBlockLocalPos.getZ() + 0.5
            ));
            Vec3 skullSpawn = targetCenter.add(0, 1, 0);
            Vec3 direction = targetCenter.subtract(skullSpawn).normalize();

            if (helper.getLevel().getDifficulty().equals(Difficulty.PEACEFUL)) {
                helper.fail("Difficulty must not be PEACEFUL to run wither test");
                return;
            }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
{% when '26.1.2' %}
            WitherBoss wither = EntityType.WITHER.create(helper.getLevel(), EntitySpawnReason.MOB_SUMMONED);
{% endcase %}
            if (wither == null) {
                helper.fail("Failed to create wither owner for wither skull");
                return;
            }
            wither.setNoAi(true);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            wither.moveTo(skullSpawn.x, skullSpawn.y - 1.0, skullSpawn.z, 0, 0);
{% when '26.1.2' %}
            wither.moveOrInterpolateTo(skullSpawn.add(0, -1, 0), 0, 0);
{% endcase %}
            helper.getLevel().addFreshEntity(wither);

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            WitherSkull witherSkull = new WitherSkull(helper.getLevel(), wither, direction.x, direction.y, direction.z);
{% when '1.21', '1.21.1', '26.1.2' %}
            WitherSkull witherSkull = new WitherSkull(helper.getLevel(), wither, direction);
{% endcase %}
            witherSkull.setDangerous(true);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            witherSkull.moveTo(skullSpawn.x, skullSpawn.y, skullSpawn.z, 0, 0);
{% when '26.1.2' %}
            witherSkull.moveOrInterpolateTo(skullSpawn, 0, 0);
{% endcase %}
            witherSkull.setDeltaMovement(direction.scale(0.9));
            helper.getLevel().addFreshEntity(witherSkull);

            helper.runAfterDelay(
                    40, () -> {
                        verifyResult(helper, targetBlockLocalPos);
                        witherSkull.discard();
                        wither.discard();
                    }
            );
        }

        private void verifyResult(
                SFMGameTestHelper helper,
                BlockPos targetBlockLocalPos
        ) {

            boolean stillExists = helper.getBlockState(targetBlockLocalPos).is(scenario.blockSupplier.get());
            if (stillExists != scenario.shouldSurvive) {
                helper.fail(
                        "Scenario '"
                        + scenario.name
                        + "' had unexpected result after dangerous wither skull explosion"
                        + ": expected survive="
                        + scenario.shouldSurvive
                        + ", actual survive="
                        + stillExists,
                        targetBlockLocalPos
                );
            } else {
                helper.succeed();
            }
        }

    }

}
