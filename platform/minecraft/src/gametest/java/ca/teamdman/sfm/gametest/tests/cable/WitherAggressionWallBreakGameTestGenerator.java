package ca.teamdman.sfm.gametest.tests.cable;

import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SFMGameTestGenerator
public class WitherAggressionWallBreakGameTestGenerator extends SFMGameTestGeneratorBase {

    private static final int TEMPLATE_SIZE_X = 11;

    private static final int TEMPLATE_SIZE_Y = 7;

    private static final int TEMPLATE_SIZE_Z = 11;

    private static final int BOX_MIN_X = 2;

    private static final int BOX_MIN_Y = 1;

    private static final int BOX_MIN_Z = 3;

    private static final int BOX_SIZE_X = 7;

    private static final int BOX_SIZE_Y = 6;

    private static final int BOX_SIZE_Z = 7;

    private static final Direction TEST_WALL_SIDE = Direction.NORTH;

    private static final int TEST_WALL_WIDTH = 3;

    private static final int TEST_WALL_HEIGHT = 4;

    private static final int TEST_WALL_BOTTOM_Y_OFFSET = 1;

    private static final int SHEEP_PEN_SIZE = 3;

    private static final int SHEEP_PEN_FLOOR_Y = BOX_MIN_Y;

    private static final int SHEEP_PEN_FENCE_Y = BOX_MIN_Y + 1;

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario(
                    "wither_aggro_breaks_tough_cable_facaded_as_obsidian_wall",
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.OBSIDIAN.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "wither_aggro_does_not_break_tough_cable_facaded_as_bedrock_wall",
                    SFMBlocks.TOUGH_CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    false
            ),
            new Scenario(
                    "wither_aggro_breaks_normal_cable_facaded_as_bedrock_wall",
                    SFMBlocks.CABLE_FACADE::get,
                    Optional.of(Blocks.BEDROCK.defaultBlockState()),
                    true
            ),
            new Scenario(
                    "wither_aggro_breaks_vanilla_obsidian_wall",
                    () -> Blocks.OBSIDIAN,
                    Optional.empty(),
                    true
            )
    );

    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> testConsumer) {

        for (Scenario scenario : SCENARIOS) {
            testConsumer.accept(new WitherAggressionWallBreakScenarioGameTest(scenario));
        }
    }

    private record Scenario(
            String name,

            Supplier<Block> wallBlockSupplier,

            Optional<BlockState> facadeState,

            boolean expectedWallBreak
    ) {
    }

    private static class WitherAggressionWallBreakScenarioGameTest extends SFMGameTestDefinition {
        private final Scenario scenario;

        private WitherAggressionWallBreakScenarioGameTest(Scenario scenario) {

            this.scenario = scenario;
        }

        @Override
        public String template() {

            return TEMPLATE_SIZE_X + "x" + TEMPLATE_SIZE_Y + "x" + TEMPLATE_SIZE_Z;
        }

        @Override
        public String testName() {

            return scenario.name;
        }

        @Override
        public int maxTicks() {

            return 260;
        }

        @Override
        public void run(SFMGameTestHelper helper) {

            if (helper.getLevel().getDifficulty() == Difficulty.PEACEFUL) {
                helper.fail("Difficulty must not be PEACEFUL to run wither aggression scenario");
                return;
            }

            List<BlockPos> localWallPositions = buildArenaAndPlaceWall(helper);
            if (localWallPositions.isEmpty()) {
                return;
            }

            Sheep sheep = helper.spawn(EntityType.SHEEP, getSheepSpawnLocalVec());
            sheep.setNoAi(true);

            if (helper.getLevel().getDifficulty().equals(Difficulty.PEACEFUL)) {
                helper.fail("Difficulty must not be PEACEFUL to run wither test");
                return;
            }

            WitherBoss wither = helper.spawn(EntityType.WITHER, getWitherSpawnLocalVec());
            wither.setTarget(sheep);
            wither.setAlternativeTarget(0, sheep.getId());
            wither.setAlternativeTarget(1, sheep.getId());
            wither.setAlternativeTarget(2, sheep.getId());
            triggerWitherDestroyBlocksTickViaHurt(helper, wither);

            if (scenario.expectedWallBreak) {
                helper.failIfEver(() -> {
                    boolean sheepAlive = sheep.isAlive();
                    boolean wallBroken = isWallBroken(helper, localWallPositions);
                    if (!sheepAlive && !wallBroken) {
                        helper.fail(
                                "Scenario '" + scenario.name
                                + "' invalid state: sheep died but wall did not break"
                        );
                    }
                });

                helper.succeedWhen(() -> {
                    boolean sheepAlive = sheep.isAlive();
                    boolean wallBroken = isWallBroken(helper, localWallPositions);
                    helper.assertTrue(
                            wallBroken,
                            "Scenario '" + scenario.name + "' expected wall to break before success"
                    );
                    helper.assertTrue(
                            !sheepAlive,
                            "Scenario '" + scenario.name + "' expected sheep to die before success"
                    );

                    wither.discard();
                    sheep.discard();
                });
                return;
            }

            helper.runAfterDelay(
                    220, () -> {
                        boolean sheepAlive = sheep.isAlive();
                        boolean wallBroken = isWallBroken(helper, localWallPositions);

                        helper.assertTrue(
                                !wallBroken,
                                "Scenario '" + scenario.name + "' expected wall to remain intact"
                        );
                        helper.assertTrue(
                                sheepAlive,
                                "Scenario '" + scenario.name + "' expected sheep to remain alive"
                        );

                        wither.discard();
                        sheep.discard();
                        helper.succeed();
                    }
            );
        }

        @MCVersionDependentBehaviour
        private void triggerWitherDestroyBlocksTickViaHurt(SFMGameTestHelper helper, WitherBoss wither) {
            // Intentionally route through WitherBoss#hurt to trigger:
            //   if (this.destroyBlocksTick <= 0) { this.destroyBlocksTick = 20; }
            wither.hurt(helper.getLevel().damageSources().fellOutOfWorld(), 1.0F);
        }

        private boolean isWallBroken(
                SFMGameTestHelper helper,
                List<BlockPos> localWallPositions
        ) {

            Block expectedWallBlock = scenario.wallBlockSupplier.get();
            for (BlockPos wallPos : localWallPositions) {
                if (!helper.getBlockState(wallPos).is(expectedWallBlock)) {
                    return true;
                }
            }
            return false;
        }

        private List<BlockPos> buildArenaAndPlaceWall(SFMGameTestHelper helper) {

            List<BlockPos> localWallPositions = new ArrayList<>();

            for (int x = BOX_MIN_X; x <= getBoxMaxX(); x++) {
                for (int y = BOX_MIN_Y; y <= getBoxMaxY(); y++) {
                    for (int z = BOX_MIN_Z; z <= getBoxMaxZ(); z++) {
                        boolean isBoundary = x == BOX_MIN_X
                                             || x == getBoxMaxX()
                                             || y == BOX_MIN_Y
                                             || y == getBoxMaxY()
                                             || z == BOX_MIN_Z
                                             || z == getBoxMaxZ();
                        if (isBoundary) {
                            helper.setBlock(new BlockPos(x, y, z), Blocks.BEDROCK.defaultBlockState());
                        }
                    }
                }
            }

            int penMinX = getSheepPenMinX();
            int penMaxX = getSheepPenMaxX();
            int penMinZ = getSheepPenMinZ();
            int penMaxZ = getSheepPenMaxZ();

            for (int x = penMinX; x <= penMaxX; x++) {
                for (int z = penMinZ; z <= penMaxZ; z++) {
                    helper.setBlock(new BlockPos(x, SHEEP_PEN_FLOOR_Y, z), Blocks.BEDROCK.defaultBlockState());
                }
            }

            for (int x = penMinX; x <= penMaxX; x++) {
                for (int z = penMinZ; z <= penMaxZ; z++) {
                    boolean isFenceEdge = x == penMinX || x == penMaxX || z == penMinZ || z == penMaxZ;
                    if (isFenceEdge) {
                        helper.setBlock(new BlockPos(x, SHEEP_PEN_FENCE_Y, z), Blocks.OAK_FENCE.defaultBlockState());
                    }
                }
            }

            int wallCenterX = getBoxCenterX();
            int wallYMin = BOX_MIN_Y + TEST_WALL_BOTTOM_Y_OFFSET;
            int wallYMax = wallYMin + TEST_WALL_HEIGHT - 1;
            int wallHalfWidth = TEST_WALL_WIDTH / 2;

            for (int y = wallYMin; y <= wallYMax; y++) {
                for (int x = wallCenterX - wallHalfWidth; x <= wallCenterX + wallHalfWidth; x++) {
                    BlockPos localWallPos = switch (TEST_WALL_SIDE) {
                        case NORTH -> new BlockPos(x, y, BOX_MIN_Z);
                        case SOUTH -> new BlockPos(x, y, getBoxMaxZ());
                        default -> throw new IllegalStateException("Unsupported wall side: " + TEST_WALL_SIDE);
                    };
                    helper.setBlock(localWallPos, scenario.wallBlockSupplier.get().defaultBlockState());
                    localWallPositions.add(localWallPos);

                    scenario.facadeState.ifPresent(mimicBlockState -> helper.setFacade(localWallPos, mimicBlockState));

                }
            }

            return localWallPositions;
        }

        private int getBoxMaxX() {

            return BOX_MIN_X + BOX_SIZE_X - 1;
        }

        private int getBoxMaxY() {

            return BOX_MIN_Y + BOX_SIZE_Y - 1;
        }

        private int getBoxMaxZ() {

            return BOX_MIN_Z + BOX_SIZE_Z - 1;
        }

        private int getBoxCenterX() {

            return BOX_MIN_X + BOX_SIZE_X / 2;
        }

        private int getBoxCenterZ() {

            return BOX_MIN_Z + BOX_SIZE_Z / 2;
        }

        private int getSheepPenMinX() {

            return getBoxCenterX() - SHEEP_PEN_SIZE / 2;
        }

        private int getSheepPenMaxX() {

            return getSheepPenMinX() + SHEEP_PEN_SIZE - 1;
        }

        private int getSheepPenMinZ() {

            return switch (TEST_WALL_SIDE) {
                case NORTH -> BOX_MIN_Z - SHEEP_PEN_SIZE;
                case SOUTH -> getBoxMaxZ() + 1;
                default -> throw new IllegalStateException("Unsupported wall side: " + TEST_WALL_SIDE);
            };
        }

        private int getSheepPenMaxZ() {

            return getSheepPenMinZ() + SHEEP_PEN_SIZE - 1;
        }

        private BlockPos getSheepCheckLocalPos() {

            return new BlockPos(
                    getBoxCenterX(),
                    SHEEP_PEN_FLOOR_Y + 1,
                    getSheepPenMinZ() + SHEEP_PEN_SIZE / 2
            );
        }

        private Vec3 getSheepSpawnLocalVec() {

            BlockPos pos = getSheepCheckLocalPos();
            return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }

        private Vec3 getWitherSpawnLocalVec() {

            return new Vec3(
                    getBoxCenterX() + 0.5,
                    BOX_MIN_Y,
                    getBoxCenterZ() + 0.5
            );
        }

    }

}
