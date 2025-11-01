package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.TomlEditScreenOpenContext;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ClientboundClientConfigCommandPacket extends SFMPacket<ClientboundClientConfigCommandPacket> {
    private ConfigCommandBehaviourInput requestingEditMode;

    public ClientboundClientConfigCommandPacket(ConfigCommandBehaviourInput requestingEditMode) {
        this.requestingEditMode = requestingEditMode;
    }

    public ClientboundClientConfigCommandPacket() {
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
    public IMessage onMessage(ClientboundClientConfigCommandPacket message, MessageContext ctx) {
        String configTomlString = SFMConfigReadWriter.getConfigToml(SFMConfig.CLIENT_CONFIG_SPEC);
        if (configTomlString == null) {
            SFM.LOGGER.error("Unable to get client config");
            return null;
        }
        configTomlString = configTomlString.replaceAll("\\r", "");
        switch (message.requestingEditMode) {
            case SHOW: 
                SFMScreenChangeHelpers.showTomlEditScreen(new TomlEditScreenOpenContext(
                    configTomlString,
                    $ -> {
                    }
            ));
            break;
            case EDIT:
                SFMScreenChangeHelpers.showTomlEditScreen(new TomlEditScreenOpenContext(
                    configTomlString,
                    this::handleNewClientConfig
            ));
            break;
        }
        return null;
    }

    public void handleNewClientConfig(String newConfigToml) {
        SFMConfigReadWriter.ConfigSyncResult configSyncResult = SFMConfigReadWriter.updateClientConfig(newConfigToml);
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(configSyncResult.component());
        }
    }
}