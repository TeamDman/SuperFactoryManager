package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.facade.FacadePlanner;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.facade.IFacadePlan;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.network.FriendlyByteBuf;
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.network.RegistryFriendlyByteBuf;
{% endcase %}
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

        Level level = SFMEntityUtils.getLevel(sender);
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                FriendlyByteBuf buf
{% when '1.21', '1.21.1', '26.1.2' %}
                RegistryFriendlyByteBuf buf
{% endcase %}
        ) {
            buf.writeBlockHitResult(msg.hitResult);
            buf.writeEnum(msg.spreadLogic);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            buf.writeItem(msg.paintStack);
{% when '1.21', '1.21.1', '26.1.2' %}
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.paintStack);
{% endcase %}
            buf.writeEnum(msg.paintHand);
        }

        @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        public ServerboundFacadePacket decode(FriendlyByteBuf buf) {
{% when '1.21', '1.21.1', '26.1.2' %}
        public ServerboundFacadePacket decode(RegistryFriendlyByteBuf buf) {
{% endcase %}
            return new ServerboundFacadePacket(
                    buf.readBlockHitResult(),
                    buf.readEnum(FacadeSpreadLogic.class),
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                    buf.readItem(),
{% when '1.21', '1.21.1', '26.1.2' %}
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
{% endcase %}
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
