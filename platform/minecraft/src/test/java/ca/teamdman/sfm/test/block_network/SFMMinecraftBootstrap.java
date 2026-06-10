package ca.teamdman.sfm.test.block_network;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

final class SFMMinecraftBootstrap {

    private SFMMinecraftBootstrap() {
    }

    static void bootStrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

}
