package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.tests.general.ClientManagerSigningGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** File-driven right-click proof for the dedicated Client Manager GUI. */
@SFMGamePuppet(timeoutTicks = 20 * 60 * 5)
public final class ClientManagerMenuGamePuppet {
    private static final BlockPos MANAGER = new BlockPos(1, 2, 1);

    private ClientManagerMenuGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        puppet.runGameTest(SFMGameTestDefinition.testNameFor(ClientManagerSigningGameTest.class));
        puppet.captureClientManagerAt(
                "client-manager-menu",
                MANAGER,
                Component.literal("Client Manager GUI: ")
                        .append(Component.literal("program projection synchronized").withStyle(ChatFormatting.AQUA))
        );
    }
}
