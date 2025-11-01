package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import ca.teamdman.sfm.common.registry.SFMPackets;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundServerConfigRequestPacket extends SFMPacket<ServerboundServerConfigRequestPacket> {
    private ConfigCommandBehaviourInput requestingEditMode;

    public ServerboundServerConfigRequestPacket(ConfigCommandBehaviourInput requestingEditMode) {
        this.requestingEditMode = requestingEditMode;
    }

    public ServerboundServerConfigRequestPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        requestingEditMode = ConfigCommandBehaviourInput.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(requestingEditMode.ordinal());
    }

    @Override
    public IMessage onMessage(ServerboundServerConfigRequestPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (!player.canUseCommand(4, "")
                    && message.requestingEditMode == ConfigCommandBehaviourInput.EDIT) {
                SFM.LOGGER.warn(
                        "Player {} tried to request server config for editing but does not have the necessary permissions, this should never happen o-o",
                        player.getName()
                );
                return;
            }
            String configToml = SFMConfigReadWriter.getConfigToml(SFMConfig.SERVER_CONFIG_SPEC);
            if (configToml == null) {
                SFM.LOGGER.warn("Unable to get server config for player {}", player.getName());
                player.sendMessage(SFMConfigReadWriter.ConfigSyncResult.INTERNAL_FAILURE.component());
                return;
            }
            configToml = configToml.replaceAll("(?m)^#", "--");
            configToml = configToml.replaceAll("\\r", "");
            SFM.LOGGER.info("Sending config to player: {}", player.getName());
            SFMPackets.SFM_CHANNEL.sendTo(
                    new ClientboundServerConfigCommandPacket(configToml, message.requestingEditMode),
                    player
            );
        });
        return null;
    }
}