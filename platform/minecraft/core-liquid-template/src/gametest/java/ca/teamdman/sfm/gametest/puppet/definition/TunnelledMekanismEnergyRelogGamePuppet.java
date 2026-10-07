package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.tests.compat.mekanism.TunnelledMekanismEnergyRecoveryGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** A real save/leave/rejoin probe, not merely a block-entity replacement test. */
@SFMGamePuppet(timeoutTicks = 20 * 8 * 60)
public final class TunnelledMekanismEnergyRelogGamePuppet {
    private static final BlockPos TUNNEL = new BlockPos(1, 2, 1);

    private TunnelledMekanismEnergyRelogGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        puppet.createFreshFlatWorld();
        String testName = new TunnelledMekanismEnergyRecoveryGameTest().testName();
        puppet.runGameTest(testName);
        puppet.captureOrbit("before-relog", TUNNEL, 1, 5D, 3D,
                Component.literal("Mekanism direct and tunnelled transfer before rejoining the save."));
        puppet.rejoinCurrentWorld();
        puppet.captureOrbit("after-relog", TUNNEL, 1, 5D, 3D,
                Component.literal("The same Mekanism and SFM blocks after a new integrated server loaded the save."));
        puppet.assertMekanismTunnelEnergyAfterRejoin();
    }
}
