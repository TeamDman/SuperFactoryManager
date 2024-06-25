package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundLabelGunClearPacket(
        InteractionHand hand
) {
    public static void encode(ServerboundLabelGunClearPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
    }

    public static ServerboundLabelGunClearPacket decode(
            FriendlyByteBuf buf
    ) {
        return new ServerboundLabelGunClearPacket(buf.readEnum(InteractionHand.class));
    }

    public static void handle(
            ServerboundLabelGunClearPacket msg, NetworkEvent.Context context
    ) {
        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender == null) {
                return;
            }
            var stack = sender.getItemInHand(msg.hand);
            if (stack.getItem() instanceof LabelGunItem) {
                LabelPositionHolder.empty().save(stack);
            }
        });
        context.setPacketHandled(true);
    }
}
