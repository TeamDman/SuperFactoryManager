package ca.teamdman.sfm.common.registry.registration;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
{% endcase %}
import ca.teamdman.sfm.common.net.*;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
{% if features.packet_direction_validation %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endif %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% if features.packet_transport_private %}
import ca.teamdman.sfm.common.value.SFMValue;
{% endif %}
{% when "1.20.2", "1.20.3" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% when "1.20.4" %}
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
{% when "1.21", "1.21.1" %}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
{% endcase %}
import net.minecraft.server.level.ServerPlayer;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.network.NetworkRegistry;
{% if features.packet_direction_validation %}
import net.minecraftforge.network.NetworkDirection;
{% endif %}
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
{% when "1.20.2", "1.20.3" %}
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.simple.SimpleChannel;
{% when "1.20.4" %}
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;
{% when "1.21", "1.21.1" %}
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
{% when "26.1.2" %}
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
{% if features.packet_direction_validation %}
import java.util.Optional;
{% endif %}
{% when "1.20.2", "1.20.3" %}
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import java.util.IdentityHashMap;
import java.util.Locale;
{% endcase %}
import java.util.function.Supplier;

public class SFMPackets {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
{% if features.manager_operator_queries %}
    // The multiplayer and manager-show boundaries add message types unknown to older peers.
    public static final String SFM_CHANNEL_VERSION="1.5.0";
{% elsif features.multiplayer_packets %}
    // The separately negotiated multiplayer boundary adds message types unknown to older peers.
    public static final String SFM_CHANNEL_VERSION="1.4.0";
{% else %}
    public static final String SFM_CHANNEL_VERSION="1.0.0";
{% endif %}
    public static final SimpleChannel SFM_CHANNEL = NetworkRegistry.newSimpleChannel(
            SFMResourceLocation.fromSFMPath("manager"),
            SFM_CHANNEL_VERSION::toString,
            SFM_CHANNEL_VERSION::equals,
            SFM_CHANNEL_VERSION::equals
    );
{% when "1.20.2", "1.20.3" %}
    public static final String SFM_CHANNEL_VERSION="1.0.0";
    public static final SimpleChannel SFM_CHANNEL = NetworkRegistry.newSimpleChannel(
            SFMResourceLocation.fromSFMPath("manager"),
            SFM_CHANNEL_VERSION::toString,
            SFM_CHANNEL_VERSION::equals,
            SFM_CHANNEL_VERSION::equals
    );
{% when "1.20.4" %}
    private static final IdentityHashMap<Class<? extends SFMPacket>, SFMPacketDaddy<? extends SFMPacket>> DADDY_MAP = new IdentityHashMap<>();
{% when "1.21", "1.21.1", "26.1.2" %}
    private static final IdentityHashMap<Class<? extends SFMPacket>, SFMPacketDaddy<? extends SFMPacket>> DADDY_MAP = new IdentityHashMap<>();
    private static final IdentityHashMap<Class<? extends SFMPacket>, CustomPacketPayload.Type<? extends SFMWrappedPacket<? extends SFMPacket>>> TYPE_MAP = new IdentityHashMap<>();
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    private static int registrationIndex = 0;

{% if features.packet_direction_validation %}
    @MCVersionDependentBehaviour
{% endif %}
{% when "1.20.2", "1.20.3" %}
    private static int registrationIndex = 0;

{% when "1.20.4" %}
{% when "1.21", "1.21.1", "26.1.2" %}
    @SuppressWarnings("ExtractMethodRecommender")
{% endcase %}
    public static <T extends SFMPacket> void registerPacket(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
{% when "1.20.4" %}
            IPayloadRegistrar registrar,
{% when "1.21", "1.21.1", "26.1.2" %}
            PayloadRegistrar registrar,
{% endcase %}
            SFMPacketDaddy<T> packetDaddy
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
{% if features.packet_direction_validation %}
        NetworkDirection direction = switch (packetDaddy.getPacketDirection()) {
            case SERVERBOUND -> NetworkDirection.PLAY_TO_SERVER;
            case CLIENTBOUND -> NetworkDirection.PLAY_TO_CLIENT;
        };
        SFM_CHANNEL.registerMessage(
                registrationIndex++,
                packetDaddy.getPacketClass(),
                packetDaddy::encode,
                packetDaddy::decode,
                packetDaddy::handleOuter,
                Optional.of(direction)
        );
{% else %}
        switch (packetDaddy.getPacketDirection()) {
            case SERVERBOUND, CLIENTBOUND -> SFM_CHANNEL.registerMessage(
                    registrationIndex++,
                    packetDaddy.getPacketClass(),
                    packetDaddy::encode,
                    packetDaddy::decode,
                    packetDaddy::handleOuter
            );
        }
{% endif %}
{% when "1.20.2", "1.20.3" %}
        switch (packetDaddy.getPacketDirection()) {
            case SERVERBOUND, CLIENTBOUND -> SFM_CHANNEL.registerMessage(
                    registrationIndex++,
                    packetDaddy.getPacketClass(),
                    packetDaddy::encode,
                    packetDaddy::decode,
                    packetDaddy::handleOuter
            );
        }
{% when "1.20.4" %}
        DADDY_MAP.put(packetDaddy.getPacketClass(), packetDaddy);
        ResourceLocation packetId = getPacketId(packetDaddy.getPacketClass());
        switch (packetDaddy.getPacketDirection()) {
            case SERVERBOUND -> registrar.play(
                    packetId,
                    buf -> {
                        T packet = packetDaddy.decode(buf);
                        return new SFMWrappedPacket<>(packet);
                    },
                    handler -> handler.server(
                            (packet, context) -> packet.getDaddy().handleOuter(packet.inner(), context)
                    )
            );
            case CLIENTBOUND -> registrar.play(
                    packetId,
                    buf -> {
                        T packet = packetDaddy.decode(buf);
                        return new SFMWrappedPacket<>(packet);
                    },
                    handler -> handler.client(
                            (packet, context) -> packet.getDaddy().handleOuter(packet.inner(), context)
                    )
            );
        }
{% when "1.21", "1.21.1" %}
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
{% when "26.1.2" %}
        // track the daddy
        DADDY_MAP.put(packetDaddy.getPacketClass(), packetDaddy);

        // create and track the packet type
        Identifier packetId = getPacketId(packetDaddy.getPacketClass());
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
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    public static void register() {
{% when "1.20.4" %}
    @SFMSubscribeEvent
    public static void register(final RegisterPayloadHandlerEvent event) {
        final IPayloadRegistrar registrar = event.registrar(SFM.MOD_ID)
                .versioned("1.0.0");
{% when "1.21", "1.21.1", "26.1.2" %}
    @SFMSubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SFM.MOD_ID)
                .versioned("1.0.0");
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
        registerPacket(new ClientboundBoolExprStatementInspectionResultsPacket.Daddy());
        registerPacket(new ClientboundClientConfigCommandPacket.Daddy());
        registerPacket(new ClientboundContainerExportsInspectionResultsPacket.Daddy());
        registerPacket(new ClientboundIfStatementInspectionResultsPacket.Daddy());
        registerPacket(new ClientboundInputInspectionResultsPacket.Daddy());
        registerPacket(new ClientboundLabelGunUseResponsePacket.Daddy());
        registerPacket(new ClientboundLabelInspectionResultsPacket.Daddy());
        registerPacket(new ClientboundManagerGuiUpdatePacket.Daddy());
        registerPacket(new ClientboundManagerLogLevelUpdatedPacket.Daddy());
        registerPacket(new ClientboundManagerLogsPacket.Daddy());
        registerPacket(new ClientboundOutputInspectionResultsPacket.Daddy());
        registerPacket(new ClientboundServerConfigCommandPacket.Daddy());
        registerPacket(new ClientboundShowChangelogPacket.Daddy());
        registerPacket(new ServerboundBoolExprStatementInspectionRequestPacket.Daddy());
        registerPacket(new ServerboundContainerExportsInspectionRequestPacket.Daddy());
        registerPacket(new ServerboundDiskItemSetProgramPacket.Daddy());
        registerPacket(new ServerboundFacadePacket.Daddy());
        registerPacket(new ServerboundIfStatementInspectionRequestPacket.Daddy());
        registerPacket(new ServerboundInputInspectionRequestPacket.Daddy());
        registerPacket(new ServerboundLabelGunClearPacket.Daddy());
        registerPacket(new ServerboundLabelGunCycleViewModePacket.Daddy());
        registerPacket(new ServerboundLabelGunPrunePacket.Daddy());
        registerPacket(new ServerboundLabelGunSetActiveLabelPacket.Daddy());
        registerPacket(new ServerboundLabelGunUsePacket.Daddy());
        registerPacket(new ServerboundLabelInspectionRequestPacket.Daddy());
        registerPacket(new ServerboundManagerClearLogsPacket.Daddy());
        registerPacket(new ServerboundManagerFixPacket.Daddy());
        registerPacket(new ServerboundManagerLogDesireUpdatePacket.Daddy());
        registerPacket(new ServerboundManagerProgramPacket.Daddy());
        registerPacket(new ServerboundManagerRebuildPacket.Daddy());
        registerPacket(new ServerboundManagerResetPacket.Daddy());
        registerPacket(new ServerboundManagerSetLogLevelPacket.Daddy());
        registerPacket(new ServerboundNetworkToolToggleOverlayPacket.Daddy());
        registerPacket(new ServerboundNetworkToolUsePacket.Daddy());
        registerPacket(new ServerboundOutputInspectionRequestPacket.Daddy());
        registerPacket(new ServerboundServerConfigRequestPacket.Daddy());
        registerPacket(new ServerboundServerConfigUpdatePacket.Daddy());
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
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
{% endcase %}
{% if features.packet_transport_private %}
        // Packet IDs are append-only so existing packet discriminators remain stable.
        registerPacket(new ClientboundPacketObservationPacket.Daddy());
        registerPacket(new ServerboundPacketInsertionPacket.Daddy());
{% endif %}
{% if features.client_inbox %}
        registerPacket(new ClientboundClientInboxValuePacket.Daddy());
        registerPacket(new ServerboundClientInboxSubscriptionPacket.Daddy());
{% endif %}
{% if features.client_program_signing %}
        registerPacket(new ServerboundClientManagerSigningRequestPacket.Daddy());
        registerPacket(new ServerboundClientManagerSignaturePacket.Daddy());
        registerPacket(new ClientboundClientManagerSigningResponsePacket.Daddy());
{% endif %}
{% if features.multiplayer_packets %}
        registerPacket(new ca.teamdman.sfm.common.net.multiplayer.ServerboundMultiplayerPacket.Daddy());
        registerPacket(new ca.teamdman.sfm.common.net.multiplayer.ClientboundMultiplayerPacket.Daddy());
{% endif %}
{% if features.manager_operator_queries %}
        registerPacket(new ServerboundManagerShowPacket.Daddy());
        registerPacket(new ClientboundManagerShowPacket.Daddy());
{% endif %}
    }

    public static void sendToServer(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
            Object packet
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMPacket packet
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
        SFM_CHANNEL.sendToServer(packet);
{% when "1.20.4" %}
        PacketDistributor.SERVER.noArg().send(new SFMWrappedPacket<>(packet));
{% when "1.21", "1.21.1" %}
        PacketDistributor.sendToServer(new SFMWrappedPacket<>(packet));
{% when "26.1.2" %}
        ClientPacketDistributor.sendToServer(new SFMWrappedPacket<>(packet));
{% endcase %}
    }

    public static void sendToPlayer(
            Supplier<ServerPlayer> player,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
            Object packet
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMPacket packet
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
        SFM_CHANNEL.send(PacketDistributor.PLAYER.with(player), packet);
{% when "1.20.4" %}
        PacketDistributor.PLAYER.with(player.get()).send(new SFMWrappedPacket<>(packet));
{% when "1.21", "1.21.1", "26.1.2" %}
        PacketDistributor.sendToPlayer(player.get(), new SFMWrappedPacket<>(packet));
{% endcase %}
    }

    public static void sendToPlayer(
            ServerPlayer player,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
            Object packet
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMPacket packet
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
        SFM_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
{% when "1.20.4" %}
        PacketDistributor.PLAYER.with(player).send(new SFMWrappedPacket<>(packet));
{% when "1.21", "1.21.1", "26.1.2" %}
        PacketDistributor.sendToPlayer(player, new SFMWrappedPacket<>(packet));
{% endcase %}
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
{% when "1.20.4" %}

    private static ResourceLocation getPacketId(Class<? extends SFMPacket> clazz) {
        return new ResourceLocation(SFM.MOD_ID, clazz.getSimpleName().toLowerCase(Locale.ROOT));
    }

    private record SFMWrappedPacket<T extends SFMPacket>(T inner) implements CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            getDaddy().encode(inner, friendlyByteBuf);
        }

        @Override
        public ResourceLocation id() {
            return getPacketId(inner.getClass());
        }

        public SFMPacketDaddy<T> getDaddy() {
            //noinspection unchecked
            return (SFMPacketDaddy<T>) DADDY_MAP.get(inner.getClass());
        }
    }
{% when "1.21", "1.21.1" %}

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
{% when "26.1.2" %}

    private static Identifier getPacketId(Class<? extends SFMPacket> clazz) {
        return Identifier.fromNamespaceAndPath(SFM.MOD_ID, clazz.getSimpleName().toLowerCase(Locale.ROOT));
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
{% endcase %}
{% if features.packet_transport_private %}

    public static boolean sendPacketObservation(
            ServerPlayer player,
            SFMValue value
    ) {
        if (!SFMPacketEffectGate.allowsServerEffects(player)) {
            return false;
        }
        sendToPlayer(player, ClientboundPacketObservationPacket.fromValue(value));
        return true;
    }
{% endif %}
}
