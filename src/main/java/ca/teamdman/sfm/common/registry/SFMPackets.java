package ca.teamdman.sfm.common.registry;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.net.*;

public class SFMPackets {

    public static final SimpleNetworkWrapper SFM_CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(SFM.MOD_ID);

    private static int registrationIndex = 0;

    public static <T extends SFMPacket<T>> void registerPacket(Class<T> packetClass, Side side) {
        SFM_CHANNEL.registerMessage(packetClass, packetClass, registrationIndex++, side);
    }

    public static void register() {
        registerPacket(ClientboundBoolExprStatementInspectionResultsPacket.class, Side.CLIENT);

        registerPacket(ClientboundContainerExportsInspectionResultsPacket.class, Side.CLIENT);
        registerPacket(ClientboundIfStatementInspectionResultsPacket.class, Side.CLIENT);
        registerPacket(ClientboundInputInspectionResultsPacket.class, Side.CLIENT);
        registerPacket(ClientboundLabelGunUseResponsePacket.class, Side.CLIENT);
        registerPacket(ClientboundLabelInspectionResultsPacket.class, Side.CLIENT);
        registerPacket(ClientboundManagerGuiUpdatePacket.class, Side.CLIENT);
        registerPacket(ClientboundManagerLogLevelUpdatedPacket.class, Side.CLIENT);
        registerPacket(ClientboundManagerLogsPacket.class, Side.CLIENT);
        registerPacket(ClientboundOutputInspectionResultsPacket.class, Side.CLIENT);
        registerPacket(ClientboundShowChangelogPacket.class, Side.CLIENT);
        registerPacket(ServerboundBoolExprStatementInspectionRequestPacket.class, Side.SERVER);
        registerPacket(ServerboundContainerExportsInspectionRequestPacket.class, Side.SERVER);
        registerPacket(ServerboundDiskItemSetProgramPacket.class, Side.SERVER);
        registerPacket(ServerboundIfStatementInspectionRequestPacket.class, Side.SERVER);
        registerPacket(ServerboundInputInspectionRequestPacket.class, Side.SERVER);
        registerPacket(ServerboundLabelGunClearPacket.class, Side.SERVER);
        registerPacket(ServerboundLabelGunCycleViewModePacket.class, Side.SERVER);
        registerPacket(ServerboundLabelGunPrunePacket.class, Side.SERVER);
        registerPacket(ServerboundLabelGunSetActiveLabelPacket.class, Side.SERVER);
        registerPacket(ServerboundLabelGunUsePacket.class, Side.SERVER);
        registerPacket(ServerboundLabelInspectionRequestPacket.class, Side.SERVER);
        registerPacket(ServerboundManagerClearLogsPacket.class, Side.SERVER);
        registerPacket(ServerboundManagerFixPacket.class, Side.SERVER);
        registerPacket(ServerboundManagerLogDesireUpdatePacket.class, Side.SERVER);
        registerPacket(ServerboundManagerProgramPacket.class, Side.SERVER);
        registerPacket(ServerboundManagerRebuildPacket.class, Side.SERVER);
        registerPacket(ServerboundManagerResetPacket.class, Side.SERVER);
        registerPacket(ServerboundManagerSetLogLevelPacket.class, Side.SERVER);
        registerPacket(ServerboundNetworkToolToggleOverlayPacket.class, Side.SERVER);
        registerPacket(ServerboundNetworkToolUsePacket.class, Side.SERVER);
        registerPacket(ServerboundOutputInspectionRequestPacket.class, Side.SERVER);
    }

    public static void sendToPlayer(EntityPlayerMP player, SFMPacket<?> packet) {
        SFM_CHANNEL.sendTo(packet, player);
    }

    public static void sendToServer(SFMPacket<?> packet) {
        SFM_CHANNEL.sendToServer(packet);
    }
}
