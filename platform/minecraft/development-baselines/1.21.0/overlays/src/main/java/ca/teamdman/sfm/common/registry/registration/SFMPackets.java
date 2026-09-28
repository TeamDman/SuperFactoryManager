package ca.teamdman.sfm.common.registry.registration;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.net.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.function.Supplier;

public class SFMPackets {
    private static final IdentityHashMap<Class<? extends SFMPacket>, SFMPacketDaddy<? extends SFMPacket>> DADDY_MAP = new IdentityHashMap<>();
    private static final IdentityHashMap<Class<? extends SFMPacket>, CustomPacketPayload.Type<? extends SFMWrappedPacket<? extends SFMPacket>>> TYPE_MAP = new IdentityHashMap<>();

    @SuppressWarnings("ExtractMethodRecommender")
    public static <T extends SFMPacket> void registerPacket(
            PayloadRegistrar registrar,
            SFMPacketDaddy<T> packetDaddy
    ) {
        // track the daddy
        DADDY_MAP.put(packetDaddy.getPacketClass(), packetDaddy);

        // create and track the packet type
        ResourceLocation packetId = getPacketId(packetDaddy.getPacketClass());
        CustomPacketPayload.Type<SFMWrappedPacket<T>> type = new CustomPacketPayload.Type<>(packetId);
        TYPE_MAP.put(packetDaddy.getPacketClass(), type);

        // create the codec
        StreamCodec<RegistryFriendlyByteBuf, T> codec = StreamCodec.ofMember(
                packetDaddy::encode,
                packetDaddy::decode
        );
        StreamCodec<RegistryFriendlyByteBuf, SFMWrappedPacket<T>> wrappedCodec = StreamCodec.ofMember(
                (wrappedPacket, friendlyByteBuf) -> wrappedPacket
                        .getDaddy()
                        .encode(wrappedPacket.inner, friendlyByteBuf),
                (buf) -> {
                    T packet = codec.decode(buf);
                    return new SFMWrappedPacket<>(packet);
                }
        );

        // register the packet
        switch (packetDaddy.getPacketDirection()) {
            case SERVERBOUND -> registrar.playToServer(
                    type,
                    wrappedCodec,
                    (msg, ctx) -> packetDaddy.handleOuter(msg.inner, ctx)
            );
            case CLIENTBOUND -> registrar.playToClient(
                    type,
                    wrappedCodec,
                    (msg, ctx) -> packetDaddy.handleOuter(msg.inner, ctx)
            );
        }
    }

    @SFMSubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SFM.MOD_ID)
                .versioned("1.0.0");
        registerPacket(registrar, new ClientboundBoolExprStatementInspectionResultsPacket.Daddy());
        registerPacket(registrar, new ClientboundClientConfigCommandPacket.Daddy());
        registerPacket(registrar, new ClientboundContainerExportsInspectionResultsPacket.Daddy());
        registerPacket(registrar, new ClientboundIfStatementInspectionResultsPacket.Daddy());
        registerPacket(registrar, new ClientboundInputInspectionResultsPacket.Daddy());
        registerPacket(registrar, new ClientboundLabelGunUseResponsePacket.Daddy());
        registerPacket(registrar, new ClientboundLabelInspectionResultsPacket.Daddy());
        registerPacket(registrar, new ClientboundManagerGuiUpdatePacket.Daddy());
        registerPacket(registrar, new ClientboundManagerLogLevelUpdatedPacket.Daddy());
        registerPacket(registrar, new ClientboundManagerLogsPacket.Daddy());
        registerPacket(registrar, new ClientboundOutputInspectionResultsPacket.Daddy());
        registerPacket(registrar, new ClientboundServerConfigCommandPacket.Daddy());
        registerPacket(registrar, new ClientboundShowChangelogPacket.Daddy());
        registerPacket(registrar, new ServerboundBoolExprStatementInspectionRequestPacket.Daddy());
        registerPacket(registrar, new ServerboundContainerExportsInspectionRequestPacket.Daddy());
        registerPacket(registrar, new ServerboundDiskItemSetProgramPacket.Daddy());
        registerPacket(registrar, new ServerboundFacadePacket.Daddy());
        registerPacket(registrar, new ServerboundIfStatementInspectionRequestPacket.Daddy());
        registerPacket(registrar, new ServerboundInputInspectionRequestPacket.Daddy());
        registerPacket(registrar, new ServerboundLabelGunClearPacket.Daddy());
        registerPacket(registrar, new ServerboundLabelGunCycleViewModePacket.Daddy());
        registerPacket(registrar, new ServerboundLabelGunPrunePacket.Daddy());
        registerPacket(registrar, new ServerboundLabelGunSetActiveLabelPacket.Daddy());
        registerPacket(registrar, new ServerboundLabelGunUsePacket.Daddy());
        registerPacket(registrar, new ServerboundLabelInspectionRequestPacket.Daddy());
        registerPacket(registrar, new ServerboundManagerClearLogsPacket.Daddy());
        registerPacket(registrar, new ServerboundManagerFixPacket.Daddy());
        registerPacket(registrar, new ServerboundManagerLogDesireUpdatePacket.Daddy());
        registerPacket(registrar, new ServerboundManagerProgramPacket.Daddy());
        registerPacket(registrar, new ServerboundManagerRebuildPacket.Daddy());
        registerPacket(registrar, new ServerboundManagerResetPacket.Daddy());
        registerPacket(registrar, new ServerboundManagerSetLogLevelPacket.Daddy());
        registerPacket(registrar, new ServerboundNetworkToolToggleOverlayPacket.Daddy());
        registerPacket(registrar, new ServerboundNetworkToolUsePacket.Daddy());
        registerPacket(registrar, new ServerboundOutputInspectionRequestPacket.Daddy());
        registerPacket(registrar, new ServerboundServerConfigRequestPacket.Daddy());
        registerPacket(registrar, new ServerboundServerConfigUpdatePacket.Daddy());
    }

    public static void sendToServer(
            SFMPacket packet
    ) {
        PacketDistributor.sendToServer(new SFMWrappedPacket<>(packet));
    }

    public static void sendToPlayer(
            Supplier<ServerPlayer> player,
            SFMPacket packet
    ) {
        PacketDistributor.sendToPlayer(player.get(), new SFMWrappedPacket<>(packet));
    }

    public static void sendToPlayer(
            ServerPlayer player,
            SFMPacket packet
    ) {
        PacketDistributor.sendToPlayer(player, new SFMWrappedPacket<>(packet));
    }

    private static ResourceLocation getPacketId(Class<? extends SFMPacket> clazz) {
        return ResourceLocation.fromNamespaceAndPath(SFM.MOD_ID, clazz.getSimpleName().toLowerCase(Locale.ROOT));
    }

    private record SFMWrappedPacket<T extends SFMPacket>(T inner) implements CustomPacketPayload {
        public SFMPacketDaddy<T> getDaddy() {
            //noinspection unchecked
            return (SFMPacketDaddy<T>) DADDY_MAP.get(inner.getClass());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE_MAP.get(inner.getClass());
        }

    }
}
