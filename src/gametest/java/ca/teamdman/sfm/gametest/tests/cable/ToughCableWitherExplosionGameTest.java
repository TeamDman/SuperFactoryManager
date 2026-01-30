package ca.teamdman.sfm.gametest.tests.cable;

import ca.teamdman.sfm.common.facade.FacadeData;
import ca.teamdman.sfm.common.facade.FacadeTextureMode;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

@SFMGameTest
public class ToughCableWitherExplosionGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        // 9x9x9 to fit a 7x7 hollow cube with some margin
        return "7x8x7";
    }

    @Override
    public int maxTicks() {
        // Wither has 220 invulnerable ticks before explosion, plus buffer for verification
        return 300;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // Build a 7x7x7 hollow cube of tough cable facades starting at (1, 1, 1)
        // This places the cube in the center of the 9x9x9 template
        List<BlockPos> cablePositions = new ArrayList<>();
        int cubeSize = 7;
        int startX = 0;
        int startY = 2;
        int startZ = 0;

        for (int x = 0; x < cubeSize; x++) {
            for (int y = 0; y < cubeSize; y++) {
                for (int z = 0; z < cubeSize; z++) {
                    // Only place blocks on the faces of the cube (hollow interior)
                    boolean isOnFace = x == 0 || x == cubeSize - 1 ||
                                       y == 0 || y == cubeSize - 1 ||
                                       z == 0 || z == cubeSize - 1;
                    if (isOnFace) {
                        BlockPos localPos = new BlockPos(startX + x, startY + y, startZ + z);
                        BlockPos absolute = helper.absolutePos(localPos);
                        cablePositions.add(absolute);

                        // Place tough cable facade
                        helper.getLevel().setBlock(absolute, SFMBlocks.TOUGH_CABLE_FACADE_BLOCK.get().defaultBlockState(), 3);

                        // Set facade to mimic bedrock
                        var be = helper.getLevel().getBlockEntity(absolute);
                        if (be instanceof ca.teamdman.sfm.common.blockentity.IFacadeBlockEntity facade) {
                            facade.updateFacadeData(new FacadeData(Blocks.BEDROCK.defaultBlockState(), Direction.NORTH, FacadeTextureMode.FILL));
                        }
                    }
                }
            }
        }

        if (helper.getLevel().getDifficulty().equals(Difficulty.PEACEFUL)) {
            helper.fail("Difficulty must not be PEACEFUL to run wither test");
            return;
        }

        // Spawn the Wither in the center of the hollow cube
        double centerX = startX + cubeSize / 2.0;
        double centerY = startY + cubeSize / 2.0;
        double centerZ = startZ + cubeSize / 2.0;
        var spawnVec = helper.absoluteVec(new net.minecraft.world.phys.Vec3(centerX, centerY, centerZ));

        WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
        if (wither == null) {
            helper.fail("Failed to create Wither entity");
            return;
        }
        wither.moveTo(spawnVec.x, spawnVec.y, spawnVec.z, 0, 0);
        wither.makeInvulnerable(); // Start the 220 tick invulnerability countdown that ends in explosion
        helper.getLevel().addFreshEntity(wither);

        // Check after 250 ticks (well after the 220 tick explosion) that all cables still exist
        helper.runAfterDelay(250, () -> {
            BlockPos failPos = null;
            for (BlockPos absolute : cablePositions) {
                if (!helper.getLevel().getBlockState(absolute).is(SFMBlocks.TOUGH_CABLE_FACADE_BLOCK.get())) {
                    failPos = absolute;
                    break;
                }
            }

            // Clean up the wither to prevent it from causing further issues
            // Always do this even if the test fails
            wither.discard();

            if (failPos != null) {
                helper.fail("Tough cable facade at " + failPos + " was destroyed by Wither explosion");
            } else {
                helper.succeed();
            }
        });
    }
}
