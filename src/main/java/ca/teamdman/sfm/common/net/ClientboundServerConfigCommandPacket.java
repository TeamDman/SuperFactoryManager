package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.TomlEditScreenOpenContext;
import ca.teamdman.sfm.common.command.ConfigCommandBehaviourInput;
import ca.teamdman.sfm.common.registry.SFMPackets;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;

public class ClientboundServerConfigCommandPacket extends SFMPacket<ClientboundServerConfigCommandPacket> {
    private String configToml;
    private ConfigCommandBehaviourInput requestingEditMode;

    public ClientboundServerConfigCommandPacket(String configToml, ConfigCommandBehaviourInput requestingEditMode) {
        this.configToml = configToml;
        this.requestingEditMode = requestingEditMode;
    }

    public ClientboundServerConfigCommandPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            configToml = packetBuffer.readString(20480);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        requestingEditMode = ConfigCommandBehaviourInput.values()[packetBuffer.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeString(configToml);
        packetBuffer.writeInt(requestingEditMode.ordinal());
    }

    @Override
    public IMessage onMessage(ClientboundServerConfigCommandPacket message, MessageContext ctx) {
        String configTomlString = message.configToml;
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
                        (newContent) -> SFMPackets.SFM_CHANNEL.sendToServer(new ServerboundServerConfigUpdatePacket(newContent))
                ));
                break;
        }
        return null;
    }
}