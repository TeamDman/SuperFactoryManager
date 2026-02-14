package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.timing.SFMDurationNetworkUtils;
import ca.teamdman.sfml.ast.Program;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import java.time.Duration;

@Desugar
public record ClientboundManagerGuiUpdatePacket(
        int windowId,
        String program,
        ManagerBlockEntity.State state,
        Duration[] tickTimes,
        Duration[] externalTickTimes
) implements SFMPacket<ClientboundManagerGuiUpdatePacket> {
    public ClientboundManagerGuiUpdatePacket cloneWithWindowId(int windowId) {
        return new ClientboundManagerGuiUpdatePacket(windowId, program(), state(), tickTimes(), externalTickTimes());
    }

    public static class Daddy implements SFMPacketDaddy<ClientboundManagerGuiUpdatePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }

        @Override
        public void encode(
                ClientboundManagerGuiUpdatePacket msg,
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeString(SFMPacketDaddy.truncate(msg.program(), Program.MAX_PROGRAM_LENGTH));
            friendlyByteBuf.writeEnumValue(msg.state());
            SFMDurationNetworkUtils.writeDurationArray(msg.tickTimes, friendlyByteBuf);
            SFMDurationNetworkUtils.writeDurationArray(msg.externalTickTimes, friendlyByteBuf);
        }

        @Override
        public ClientboundManagerGuiUpdatePacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundManagerGuiUpdatePacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readString(Program.MAX_PROGRAM_LENGTH),
                    friendlyByteBuf.readEnumValue(ManagerBlockEntity.State.class),
                    SFMDurationNetworkUtils.readDurationArray(friendlyByteBuf.readLongArray(new long[]{}, ManagerBlockEntity.TICK_TIME_HISTORY_SIZE * 2)),
                    SFMDurationNetworkUtils.readDurationArray(friendlyByteBuf.readLongArray(new long[]{}, ManagerBlockEntity.TICK_TIME_HISTORY_SIZE * 2))
            );
        }

        @Override
        public void handle(
                ClientboundManagerGuiUpdatePacket msg,
                SFMPacketHandlingContext context
        ) {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null
                    || !(player.openContainer instanceof ManagerContainerMenu menu)
                    || menu.windowId != msg.windowId()) {
                // we don't log here because this is a common occurrence when the player closes the menu
//                SFM.LOGGER.error("Invalid manager gui packet received, ignoring.");
                return;
            }
            menu.tickTimes = msg.tickTimes();
            menu.externalTickTimes = msg.externalTickTimes();
            menu.state = msg.state();
            menu.program = msg.program();
            return;
        }

    }

    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ClientboundManagerGuiUpdatePacket> {

        @Override
        SFMPacketDaddy<ClientboundManagerGuiUpdatePacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ClientboundManagerGuiUpdatePacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}