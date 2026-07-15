package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.tests.general.Move1StackDirectGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

@SFMGamePuppet
public final class Move1StackDirectWalkthroughGamePuppet {
    private static final BlockPos MANAGER = new BlockPos(1, 2, 0);
    private static final BlockPos DESTINATION_BARREL = new BlockPos(0, 2, 0);
    private static final BlockPos SOURCE_BARREL = new BlockPos(2, 2, 0);

    private Move1StackDirectWalkthroughGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        String testName = SFMGameTestDefinition.testNameFor(Move1StackDirectGameTest.class);
        puppet.runGameTest(testName);
        puppet.captureOrbit(
                "overview",
                MANAGER,
                8,
                7D,
                5D,
                Component.literal("Completed ")
                        .append(Component.literal(testName).withStyle(ChatFormatting.GOLD))
                        .append(" overview.")
        );
        puppet.captureContainerAt(
                "source-barrel",
                SOURCE_BARREL,
                Component.literal("Source barrel after transfer: ")
                        .append(Component.literal("empty").withStyle(ChatFormatting.GREEN))
        );
        puppet.captureContainerAt(
                "destination-barrel",
                DESTINATION_BARREL,
                Component.literal("Destination barrel after transfer: ")
                        .append(Component.literal("64 dirt").withStyle(ChatFormatting.GREEN))
        );
        puppet.captureManagerAt(
                "manager",
                MANAGER,
                Component.literal("Factory Manager running the transfer program.")
        );
        puppet.captureManagerProgramEditor(
                "disk-program",
                Component.literal("Disk program: ")
                        .append(Component.literal("input a").withStyle(ChatFormatting.AQUA))
                        .append(" to ")
                        .append(Component.literal("output b").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(" every 20 ticks.")
        );
    }
}
