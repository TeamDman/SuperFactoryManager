package ca.teamdman.sfm.common.net;


import ca.teamdman.sfm.common.facade.FacadePlanner;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.facade.IFacadePlan;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Desugar
public record ServerboundFacadePacket(
        BlockPos pos,
        EnumFacing facing,
        FacadeSpreadLogic spreadLogic,
        ItemStack paintStack,
        EnumHand hand
) implements SFMPacket<ServerboundFacadePacket> {
    public static void handle(
            ServerboundFacadePacket msg,
            EntityPlayerMP sender
    ) {
        World level = sender.getServerWorld();
        IFacadePlan facadePlan = FacadePlanner.getFacadePlan(sender, level, msg);
        if (facadePlan == null) {
            return;
        }
        facadePlan.apply(level);
    }

    public static class Daddy implements SFMPacketDaddy<ServerboundFacadePacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }
        @Override
        public void encode(
                ServerboundFacadePacket msg,
                FriendlyByteBuf buf
        ) {
            buf.writeBlockPos(msg.pos);
            buf.writeEnum(msg.facing);
            buf.writeEnum(msg.spreadLogic);
            buf.writeItem(msg.paintStack);
            buf.writeEnum(msg.hand);
        }

        @Override
        public ServerboundFacadePacket decode(FriendlyByteBuf buf) {
            return new ServerboundFacadePacket(
                    buf.readBlockPos(),
                    buf.readEnum(EnumFacing.class),
                    buf.readEnum(FacadeSpreadLogic.class),
                    buf.readItem(),
                    buf.readEnum(EnumHand.class)
            );
        }

        @Override
        public void handle(
                ServerboundFacadePacket msg,
                SFMPacketHandlingContext context
        ) {
            EntityPlayerMP sender = context.sender();
            if (sender == null) return;
            ServerboundFacadePacket.handle(msg, sender);
        }

        @Override
        public Class<Packet> getPacketClass() {
            return Packet.class;
        }

    }


    public static final Daddy daddy = new Daddy();

    public static class Packet extends Wrapper<ServerboundFacadePacket> {

        @Override
        SFMPacketDaddy<ServerboundFacadePacket> getDaddy() {
            return daddy;
        }
    }


    @Override
    public Wrapper<ServerboundFacadePacket> wrap() {
        var wrapper = new Packet();
        wrapper.ourRecord = this;
        return wrapper;
    }
}
