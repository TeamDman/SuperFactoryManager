package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.facade.FacadePlanner;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.facade.IFacadePlan;
import ca.teamdman.sfm.common.util.SFMPlayerUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.EnumHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public record ServerboundFacadePacket(
        BlockHitResult hitResult,
        FacadeSpreadLogic spreadLogic,
        ItemStack paintStack,
        EnumHand paintHand
) implements SFMPacket {
    public static void handle(
            ServerboundFacadePacket msg,
            Player sender
    ) {
        Level level = SFMPlayerUtils.getLevel(sender);
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
                ByteBuf buf
        ) {
            buf.writeBlockHitResult(msg.hitResult);
            buf.writeEnum(msg.spreadLogic);
            buf.writeItem(msg.paintStack);
            buf.writeEnum(msg.paintHand);
        }

        @Override
        public ServerboundFacadePacket decode(ByteBuf buf) {
            return new ServerboundFacadePacket(
                    buf.readBlockHitResult(),
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
            Player sender = context.sender();
            if (sender == null) return;
            ServerboundFacadePacket.handle(msg, sender);
        }

        @Override
        public Class<ServerboundFacadePacket> getPacketClass() {
            return ServerboundFacadePacket.class;
        }
    }
}
