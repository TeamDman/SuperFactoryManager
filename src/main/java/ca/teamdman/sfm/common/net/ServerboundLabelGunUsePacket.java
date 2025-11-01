package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.label.LabelGunPlanner;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundLabelGunUsePacket extends SFMPacket<ServerboundLabelGunUsePacket> {
    private EnumHand hand;
    private BlockPos pos;
    private boolean isContiguousModifierActive;
    private boolean isPickBlockModifierActive;
    private boolean isClearModifierActive;
    private boolean isPullModifierActive;
    private boolean isTargetManagerModifierActive;

    public ServerboundLabelGunUsePacket(EnumHand hand, BlockPos pos, boolean isContiguousModifierActive, boolean isPickBlockModifierActive, boolean isClearModifierActive, boolean isPullModifierActive, boolean isTargetManagerModifierActive) {
        this.hand = hand;
        this.pos = pos;
        this.isContiguousModifierActive = isContiguousModifierActive;
        this.isPickBlockModifierActive = isPickBlockModifierActive;
        this.isClearModifierActive = isClearModifierActive;
        this.isPullModifierActive = isPullModifierActive;
        this.isTargetManagerModifierActive = isTargetManagerModifierActive;
    }

    public ServerboundLabelGunUsePacket() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = EnumHand.values()[buf.readInt()];
        pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        isContiguousModifierActive = buf.readBoolean();
        isPickBlockModifierActive = buf.readBoolean();
        isClearModifierActive = buf.readBoolean();
        isPullModifierActive = buf.readBoolean();
        isTargetManagerModifierActive = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(hand.ordinal());
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeBoolean(isContiguousModifierActive);
        buf.writeBoolean(isPickBlockModifierActive);
        buf.writeBoolean(isClearModifierActive);
        buf.writeBoolean(isPullModifierActive);
        buf.writeBoolean(isTargetManagerModifierActive);
    }

    @Override
    public IMessage onMessage(ServerboundLabelGunUsePacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            var plan = LabelGunPlanner.getLabelGunPlan(player, message, true);
            if (plan != null) {
                plan.run();
            }
        });
        return null;
    }

    public EnumHand getHand() {
        return hand;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isContiguousModifierActive() {
        return isContiguousModifierActive;
    }

    public boolean isPickBlockModifierActive() {
        return isPickBlockModifierActive;
    }

    public boolean isClearModifierActive() {
        return isClearModifierActive;
    }

    public boolean isPullModifierActive() {
        return isPullModifierActive;
    }

    public boolean isTargetManagerModifierActive() {
        return isTargetManagerModifierActive;
    }
}