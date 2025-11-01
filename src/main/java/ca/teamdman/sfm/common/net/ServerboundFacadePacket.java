package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.facade.FacadePlanner;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.facade.IFacadePlan;
import ca.teamdman.sfm.common.util.SFMPlayerUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ServerboundFacadePacket extends SFMPacket<ServerboundFacadePacket> {
    private RayTraceResult hitResult;
    private FacadeSpreadLogic spreadLogic;
    private ItemStack paintStack;
    private EnumHand paintHand;

    public ServerboundFacadePacket(RayTraceResult hitResult, FacadeSpreadLogic spreadLogic, ItemStack paintStack, EnumHand paintHand) {
        this.hitResult = hitResult;
        this.spreadLogic = spreadLogic;
        this.paintStack = paintStack;
        this.paintHand = paintHand;
    }

    public ServerboundFacadePacket() {
    }

    public static void handle(
            ServerboundFacadePacket msg,
            EntityPlayerMP sender
    ) {
        World world = SFMPlayerUtils.getWorld(sender);
        IFacadePlan facadePlan = FacadePlanner.getFacadePlan(sender, world, msg);
        if (facadePlan == null) {
            return;
        }
        facadePlan.apply(world);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        hitResult = new RayTraceResult(packetBuffer.readVec3d(), packetBuffer.readEnumValue(net.minecraft.util.EnumFacing.class), packetBuffer.readBlockPos());
        spreadLogic = packetBuffer.readEnumValue(FacadeSpreadLogic.class);
        paintStack = ByteBufUtils.readItemStack(packetBuffer);
        paintHand = packetBuffer.readEnumValue(EnumHand.class);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeVec3d(hitResult.hitVec);
        packetBuffer.writeEnumValue(hitResult.sideHit);
        packetBuffer.writeBlockPos(hitResult.getBlockPos());
        packetBuffer.writeEnumValue(spreadLogic);
        ByteBufUtils.writeItemStack(packetBuffer, paintStack);
        packetBuffer.writeEnumValue(paintHand);
    }

    @Override
    public IMessage onMessage(ServerboundFacadePacket message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> {
            ServerboundFacadePacket.handle(message, player);
        });
        return null;
    }

    public RayTraceResult getHitResult() {
        return hitResult;
    }

    public FacadeSpreadLogic getSpreadLogic() {
        return spreadLogic;
    }

    public ItemStack getPaintStack() {
        return paintStack;
    }

    public EnumHand getPaintHand() {
        return paintHand;
    }
}