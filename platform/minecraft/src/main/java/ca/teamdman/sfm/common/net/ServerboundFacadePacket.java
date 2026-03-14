package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.facade.FacadePlanner;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.facade.IFacadePlan;
import ca.teamdman.sfm.common.util.SFMPlayerUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public record ServerboundFacadePacket(
        BlockHitResult hitResult,
        FacadeSpreadLogic spreadLogic,
        ItemStack paintStack,
        InteractionHand paintHand
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
                FriendlyByteBuf buf
        ) {
            buf.writeBlockHitResult(msg.hitResult);
            buf.writeEnum(msg.spreadLogic);
            buf.writeItem(msg.paintStack);
            buf.writeEnum(msg.paintHand);
        }

        @Override
        public ServerboundFacadePacket decode(FriendlyByteBuf buf) {
            return new ServerboundFacadePacket(
                    buf.readBlockHitResult(),
                    buf.readEnum(FacadeSpreadLogic.class),
                    buf.readItem(),
                    buf.readEnum(InteractionHand.class)
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
