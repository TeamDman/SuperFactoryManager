package ca.teamdman.sfm.gametest.tests.cable;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestGenerator;
import ca.teamdman.sfm.gametest.SFMGameTestGeneratorBase;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Note that {@link Blocks#OBSIDIAN} is resistant to the initial wither explosion.
/// It is in {@link WitherBoss#canDestroy(BlockState)} that checks {@link BlockTags#WITHER_IMMUNE} which is called
/// by {@link WitherBoss#customServerAiStep()} that causes the Wither to later break obsidian.
@SFMGameTestGenerator
public class ToughCableExplosionGameTestGenerator extends SFMGameTestGeneratorBase {

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario(
                    "tough_cable_unfacaded_resists_tnt",
                    ExplosionType.TNT,
                    SFMBlocks.TOUGH_CABLE::get,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "tough_cable_facaded_as_grass_resists_tnt",
                    ExplosionType.TNT,
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.GRASS_BLOCK.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "tough_cable_facaded_as_obsidian_resists_tnt",
                    ExplosionType.TNT,
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "tough_fancy_cable_unfacaded_resists_tnt",
                    ExplosionType.TNT,
                    SFMBlocks.TOUGH_FANCY_CABLE::get,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "fancy_cable_unfacaded_succumbs_to_tnt_explosion",
                    ExplosionType.TNT,
                    SFMBlocks.FANCY_CABLE::get,
                    Optional.empty(),
                    false
            ),
            new Scenario(
                    "tough_cable_unfacaded_resists_wither_explosion",
                    ExplosionType.WITHER,
                    SFMBlocks.TOUGH_CABLE::get,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "tough_fancy_cable_unfacaded_resists_wither_explosion",
                    ExplosionType.WITHER,
                    SFMBlocks.TOUGH_FANCY_CABLE::get,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "cable_unfacaded_succumbs_to_tnt_explosion",
                    ExplosionType.TNT,
                    SFMBlocks.CABLE::get,
                    Optional.empty(),
                    false
            ),
            new Scenario(
                    "cable_unfacaded_succumbs_to_wither_explosion",
                    ExplosionType.WITHER,
                    SFMBlocks.CABLE::get,
                    Optional.empty(),
                    false
            ),
            new Scenario(
                    "tough_cable_facaded_as_bedrock_resists_wither_explosion",
                    ExplosionType.WITHER,
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "tough_cable_facaded_as_obsidian_resists_wither_explosion",
                    ExplosionType.WITHER,
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "obsidian_resists_wither_explosion",
                    ExplosionType.WITHER,
                    () -> Blocks.OBSIDIAN,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "bedrock_resists_wither_explosion",
                    ExplosionType.WITHER,
                    () -> Blocks.BEDROCK,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "obsidian_resists_tnt_explosion",
                    ExplosionType.TNT,
                    () -> Blocks.OBSIDIAN,
                    Optional.empty(),
                    true
            ),
            new Scenario(
                    "bedrock_resists_tnt_explosion",
                    ExplosionType.TNT,
                    () -> Blocks.BEDROCK,
                    Optional.empty(),
                    true
            )
    );

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> testConsumer) {

        for (Scenario scenario : SCENARIOS) {
            testConsumer.accept(new ToughCableExplosionScenarioGameTest(scenario));
        }
    }

    private enum ExplosionType {
        TNT,
        WITHER;

        public int explosionRadius() {

            return switch (this) {
                case TNT -> 4;
                case WITHER -> 7;
            };
        }
    }

    private record Scenario(
            String name,

            ExplosionType explosionType,

            Supplier<Block> blockSupplier,

            Optional<BlockState> facadeState,

            boolean shouldSurvive
    ) {
    }

    private static class ToughCableExplosionScenarioGameTest extends SFMGameTestDefinition {
        private final Scenario scenario;

        private ToughCableExplosionScenarioGameTest(Scenario scenario) {

            this.scenario = scenario;
        }

        @Override
        public String template() {

            int explosionRadius = scenario.explosionType().explosionRadius();
            int diameter = explosionRadius * 2 + 1;
            return diameter + "x" + explosionRadius + "x" + diameter;
        }

        @Override
        public String testName() {

            return scenario.name;
        }

        @Override
        public int maxTicks() {

            return scenario.explosionType == ExplosionType.TNT ? 100 : 300;
        }

        @Override
        public void run(SFMGameTestHelper helper) {

            if (scenario.explosionType == ExplosionType.TNT) {
                runTntScenario(helper);
                return;
            }
            runWitherScenario(helper);
        }

        private BlockPos getPlacementPos() {

            int explosionRadius = scenario.explosionType().explosionRadius();
            return new BlockPos(
                    explosionRadius,
                    explosionRadius / 2,
                    explosionRadius
            );
        }

        private BlockPos placeBlock(SFMGameTestHelper helper) {

            BlockPos localPos = getPlacementPos();
            helper.setBlock(localPos, scenario.blockSupplier.get().defaultBlockState());
            helper.assertTrue(
                    helper.getBlockState(localPos).is(scenario.blockSupplier.get()),
                    "Scenario '" + scenario.name + "' failed to place test block"
            );
            scenario.facadeState.ifPresent(mimicBlockState -> helper.setFacade(localPos, mimicBlockState));
            return localPos;
        }

        private void runTntScenario(SFMGameTestHelper helper) {

            BlockPos localPos = placeBlock(helper);

            Vec3 spawnVec = helper.absoluteVec(new Vec3(
                    localPos.getX() + 0.5,
                    localPos.getY() + 1.5,
                    localPos.getZ() + 0.5
            ));
            PrimedTnt primed = new PrimedTnt(helper.getLevel(), spawnVec.x, spawnVec.y, spawnVec.z, null);
            primed.setDeltaMovement(Vec3.ZERO);
            primed.setFuse((short) 20);
            helper.getLevel().addFreshEntity(primed);

            helper.runAfterDelay(40, () -> verifyResult(helper, localPos, "TNT explosion", scenario.shouldSurvive));
        }


        private void runWitherScenario(SFMGameTestHelper helper) {

            if (helper.getLevel().getDifficulty().equals(Difficulty.PEACEFUL)) {
                helper.fail("Difficulty must not be PEACEFUL to run wither scenario");
                return;
            }

            BlockPos localPos = placeBlock(helper);
            helper.assertTrue(
                    helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING),
                    "mobGriefing must be enabled to run wither explosion scenario"
            );

            Vec3 spawnVec = helper.absoluteVec(new Vec3(
                    localPos.getX() + 0.5,
                    localPos.getY() + 2.5,
                    localPos.getZ() + 0.5
            ));
            WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
            assert wither != null;
            wither.moveTo(spawnVec.x, spawnVec.y, spawnVec.z, 0, 0);
            wither.makeInvulnerable(); // initialize explosion sequence
            helper.getLevel().addFreshEntity(wither);

            helper.runAfterDelay(
                    250, () -> {
                        wither.discard();
                        verifyResult(helper, localPos, "Wither explosion", scenario.shouldSurvive);
                    }
            );
        }

        private void verifyResult(
                SFMGameTestHelper helper,
                BlockPos localBlockPos,
                String explosionDescription,
                boolean shouldSurvive
        ) {

            boolean stillExists = helper.getBlockState(localBlockPos).is(scenario.blockSupplier.get());
            if (stillExists != shouldSurvive) {
                helper.fail("Scenario '" + scenario.name + "' had unexpected result after " + explosionDescription
                            + ": expected survive=" + shouldSurvive + ", actual survive=" + stillExists);
            } else {
                helper.succeed();
            }
        }

    }

}
