package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.timing.SFMDurationNetworkUtils;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}

import java.time.Duration;

public record ClientboundManagerGuiUpdatePacket(
        int windowId,
        String program,
        ManagerBlockEntity.State state,
        Duration[] tickTimes
) implements SFMPacket {
    public ClientboundManagerGuiUpdatePacket cloneWithWindowId(int windowId) {
        return new ClientboundManagerGuiUpdatePacket(windowId, program(), state(), tickTimes());
    }

    public static class Daddy implements SFMPacketDaddy<ClientboundManagerGuiUpdatePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.CLIENTBOUND;
        }
        @Override
        public Class<ClientboundManagerGuiUpdatePacket> getPacketClass() {
            return ClientboundManagerGuiUpdatePacket.class;
        }

        @Override
        public void encode(
                ClientboundManagerGuiUpdatePacket msg,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf friendlyByteBuf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf friendlyByteBuf
{% endcase %}
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeUtf(msg.program(), Program.MAX_PROGRAM_LENGTH);
            friendlyByteBuf.writeEnum(msg.state());
            SFMDurationNetworkUtils.writeDurationArray(msg.tickTimes, friendlyByteBuf);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ClientboundManagerGuiUpdatePacket decode(FriendlyByteBuf friendlyByteBuf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ClientboundManagerGuiUpdatePacket decode(RegistryFriendlyByteBuf friendlyByteBuf) {
{% endcase %}
            return new ClientboundManagerGuiUpdatePacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
                    friendlyByteBuf.readEnum(ManagerBlockEntity.State.class),
                    SFMDurationNetworkUtils.readDurationArray(friendlyByteBuf.readLongArray())
            );
        }

        @Override
        public void handle(
                ClientboundManagerGuiUpdatePacket msg,
                SFMPacketHandlingContext context
        ) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null
                || !(player.containerMenu instanceof ManagerContainerMenu menu)
                || menu.containerId != msg.windowId()) {
                // we don't log here because this is a common occurrence when the player closes the menu
//                SFM.LOGGER.error("Invalid manager gui packet received, ignoring.");
                return;
            }
            menu.tickTimes = msg.tickTimes();
            menu.state = msg.state();
            menu.program = msg.program();
        }

    }
}
