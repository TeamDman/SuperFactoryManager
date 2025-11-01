package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ServerboundServerConfigUpdatePacket extends SFMPacket<ServerboundServerConfigUpdatePacket> {
    public static final int MAX_CONFIG_LENGTH = 32767;
    private String newConfig;

    public ServerboundServerConfigUpdatePacket(String newConfig) {
        this.newConfig = newConfig;
    }

    public ServerboundServerConfigUpdatePacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            newConfig = packetBuffer.readString(MAX_CONFIG_LENGTH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeString(newConfig);
    }

    @Override
    public IMessage onMessage(ServerboundServerConfigUpdatePacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (!player.canUseCommand(4, "")) {
                SFM.LOGGER.fatal(
                        "Player {} tried to WRITE server config but does not have the necessary permissions, this should never happen o-o",
                        player.getName()
                );
                return;
            }
            SFMConfigReadWriter.ConfigSyncResult result = SFMConfigReadWriter.updateAndSyncServerConfig(message.newConfig);
            player.sendMessage(result.component());
        });
        return null;
    }
}