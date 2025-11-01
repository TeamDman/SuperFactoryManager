package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.logging.log4j.Level;

import java.io.IOException;

public class ServerboundManagerSetLogLevelPacket extends SFMPacket<ServerboundManagerSetLogLevelPacket> {
    public static final int MAX_LOG_LEVEL_NAME_LENGTH = 64;

    private int windowId;
    private BlockPos pos;
    private String logLevel;

    public ServerboundManagerSetLogLevelPacket(int windowId, BlockPos pos, String logLevel) {
        this.windowId = windowId;
        this.pos = pos;
        this.logLevel = logLevel;
    }

    public ServerboundManagerSetLogLevelPacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        windowId = packetBuffer.readVarInt();
        pos = packetBuffer.readBlockPos();
        try {
            logLevel = packetBuffer.readString(MAX_LOG_LEVEL_NAME_LENGTH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVarInt(windowId);
        packetBuffer.writeBlockPos(pos);
        packetBuffer.writeString(logLevel);
    }

    @Override
    public IMessage onMessage(ServerboundManagerSetLogLevelPacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            if (player.openContainer instanceof ManagerContainerMenu && player.openContainer.windowId == message.windowId) {
                TileEntity te = player.world.getTileEntity(message.pos);
                if (te instanceof ManagerBlockEntity) {
                    ManagerBlockEntity manager = (ManagerBlockEntity) te;
                    Level logLevelObj = Level.getLevel(message.logLevel);
                    manager.setLogLevel(logLevelObj);
                    manager.logger.info(x -> x.accept(LocalizationKeys.LOG_LEVEL_UPDATED.get(
                            message.logLevel)));

                    SFM.LOGGER.debug(
                            "{} updated manager {} {} log level to {}",
                            player.getName(),
                            message.pos,
                            manager.getWorld(),
                            message.logLevel
                    );
                }
            }
        });
        return null;
    }
}