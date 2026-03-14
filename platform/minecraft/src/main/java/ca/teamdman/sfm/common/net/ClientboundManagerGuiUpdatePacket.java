package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.timing.SFMDurationNetworkUtils;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

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
                FriendlyByteBuf friendlyByteBuf
        ) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeUtf(msg.program(), SFMLProgram.MAX_PROGRAM_LENGTH);
            friendlyByteBuf.writeEnum(msg.state());
            SFMDurationNetworkUtils.writeDurationArray(msg.tickTimes, friendlyByteBuf);
        }

        @Override
        public ClientboundManagerGuiUpdatePacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ClientboundManagerGuiUpdatePacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readUtf(SFMLProgram.MAX_PROGRAM_LENGTH),
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
