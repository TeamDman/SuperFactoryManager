package ca.teamdman.sfm.common.registry.registration;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.net.*;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.network.packets.PacketEventHandler;

public class SFMPackets {

    public static SimpleNetworkWrapper SFM_CHANNEL;
    public static FMLEventChannel VISUAL_MANAGER_EVENT_CHANNEL;

    private static int registrationIndex = 0;

    public static void registerChannels() {
        SFM_CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(SFM.MOD_ID);
        VISUAL_MANAGER_EVENT_CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(SuperFactoryManager.CHANNEL);


        VISUAL_MANAGER_EVENT_CHANNEL.register(new PacketEventHandler());
    }

    public static void register() {
        registerPacket(ClientboundBoolExprStatementInspectionResultsPacket.daddy);

        registerPacket(ClientboundContainerExportsInspectionResultsPacket.daddy);
        registerPacket(ClientboundIfStatementInspectionResultsPacket.daddy);
        registerPacket(ClientboundInputInspectionResultsPacket.daddy);
        registerPacket(ClientboundLabelGunUseResponsePacket.daddy);
        registerPacket(ClientboundLabelInspectionResultsPacket.daddy);
        registerPacket(ClientboundManagerGuiUpdatePacket.daddy);
        registerPacket(ClientboundManagerLogLevelUpdatedPacket.daddy);
        registerPacket(ClientboundManagerLogsPacket.daddy);
        registerPacket(ClientboundOutputInspectionResultsPacket.daddy);
        registerPacket(ClientboundShowChangelogPacket.daddy);
        registerPacket(ServerboundBoolExprStatementInspectionRequestPacket.daddy);
        registerPacket(ServerboundContainerExportsInspectionRequestPacket.daddy);
        registerPacket(ServerboundDiskItemSetProgramPacket.daddy);
        registerPacket(ServerboundIfStatementInspectionRequestPacket.daddy);
        registerPacket(ServerboundInputInspectionRequestPacket.daddy);
        registerPacket(ServerboundLabelGunClearPacket.daddy);
        registerPacket(ServerboundLabelGunCycleViewModePacket.daddy);
        registerPacket(ServerboundLabelGunPrunePacket.daddy);
        registerPacket(ServerboundLabelGunSetActiveLabelPacket.daddy);
        registerPacket(ServerboundLabelGunUsePacket.daddy);
        registerPacket(ServerboundLabelInspectionRequestPacket.daddy);
        registerPacket(ServerboundManagerClearLogsPacket.daddy);
        registerPacket(ServerboundManagerFixPacket.daddy);
        registerPacket(ServerboundManagerLogDesireUpdatePacket.daddy);
        registerPacket(ServerboundManagerProgramPacket.daddy);
        registerPacket(ServerboundManagerRebuildPacket.daddy);
        registerPacket(ServerboundManagerResetPacket.daddy);
        registerPacket(ServerboundManagerSetLogLevelPacket.daddy);
        registerPacket(ServerboundNetworkToolToggleOverlayPacket.daddy);
        registerPacket(ServerboundNetworkToolUsePacket.daddy);
        registerPacket(ServerboundOutputInspectionRequestPacket.daddy);
        registerPacket(ServerboundFacadePacket.daddy);
    }

    public static <T> void registerPacket(SFMPacketDaddy<T> daddy) {

        SFM_CHANNEL.registerMessage(
                daddy,
                daddy.getPacketClass(),
                registrationIndex++,
                daddy.getPacketDirection().toSide()
        );

    }

    public static void sendToPlayer(EntityPlayerMP player, SFMPacket<?> packet) {
        SFM_CHANNEL.sendTo(packet.wrap(), player);
    }

    public static void sendToServer(SFMPacket<?> packet) {
        SFM_CHANNEL.sendToServer(packet.wrap());
    }
}
