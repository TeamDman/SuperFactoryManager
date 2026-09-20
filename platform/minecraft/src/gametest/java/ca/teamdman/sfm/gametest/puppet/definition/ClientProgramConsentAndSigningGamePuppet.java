package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.common.block.TouchDisplayBlock;
import ca.teamdman.sfm.common.blockentity.ClientManagerBlockEntity;
import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.gametest.puppet.*;
import ca.teamdman.sfm.gametest.puppet.action.ExploreClientProgramSigningPuppetAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in file-driven P8 signing acceptance. Not an ambient GameTest and never enrolled by run-all. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE, timeoutTicks = 20 * 60 * 30)
public final class ClientProgramConsentAndSigningGamePuppet {
    private ClientProgramConsentAndSigningGamePuppet() {}
    public static void run(SFMGamePuppetHelper puppet) {
        AtomicBoolean proofComplete = new AtomicBoolean();
        var fixture = new SigningFixtureGameTest(proofComplete);
        puppet.createFreshFlatWorld();
        puppet.startGameTest(fixture);
        puppet.waitTicks(20);
        puppet.exploreClientProgramSigningInteractively(proofComplete);
        puppet.waitForGameTest(fixture.testName());
    }

    private static final class SigningFixtureGameTest extends SFMGameTestDefinition {
        private final AtomicBoolean proofComplete;
        private SigningFixtureGameTest(AtomicBoolean proofComplete) { this.proofComplete = proofComplete; }
        @Override public String template() { return "5x5x5"; }
        @Override public int maxTicks() { return 20 * 60 * 29; }
        @Override public void run(SFMGameTestHelper helper) {
            for (int x = 1; x <= 3; x++) for (int y = 1; y <= 3; y++) for (int z = 0; z <= 1; z++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
            helper.setBlock(new BlockPos(4, 3, 2), Blocks.GLOWSTONE);
            BlockPos displayPosition = ExploreClientProgramSigningPuppetAction.DISPLAY;
            helper.setBlock(displayPosition, SFMBlocks.TOUCH_DISPLAY.get().defaultBlockState()
                    .setValue(TouchDisplayBlock.FACING, Direction.NORTH));
            helper.getBlockEntity(displayPosition, TouchDisplayBlockEntity.class)
                    .commitContent(TouchDisplayBlockEntity.RED_FIXTURE_IMAGE, SFMValue.of("server-owned fixture"));
            BlockPos managerPosition = ExploreClientProgramSigningPuppetAction.MANAGER;
            helper.setBlock(managerPosition, SFMBlocks.CLIENT_MANAGER.get());
            ItemStack disk = new ItemStack(SFMItems.DISK.get());
            DiskItem.setProgram(disk, ExploreClientProgramSigningPuppetAction.source(false));
            LabelPositionHolder.empty().add("displays", helper.absolutePos(displayPosition)).save(disk);
            helper.getBlockEntity(managerPosition, ClientManagerBlockEntity.class).setDisk(disk);
            helper.succeedWhen(() -> {
                helper.assertTrue(proofComplete.get(), "The file-driven signing journey has not completed its evidence gates");
                helper.assertTrue(!helper.getBlockEntity(managerPosition, ClientManagerBlockEntity.class).attestations().isEmpty(),
                        "No real network signature reached authoritative manager storage");
            });
        }
    }
}
