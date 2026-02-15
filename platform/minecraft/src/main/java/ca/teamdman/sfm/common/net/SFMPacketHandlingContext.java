package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.function.BiConsumer;

public class SFMPacketHandlingContext {
    private final MessageContext inner;

    public SFMPacketHandlingContext(MessageContext inner) {

        this.inner = inner;
    }

    public EntityPlayerMP serverPlayer() {
        if (!inner.side.isServer()) {
            throw new IllegalStateException("Attempted to get server player from client side");
        }

        return inner.getServerHandler().player;
    }

    public EntityPlayerMP sender() {

        return this.serverPlayer();
    }

    public void enqueueAndFinish(Runnable runnable) {

        if (inner.side.isServer()) {
            serverPlayer().getServerWorld().addScheduledTask(runnable);
        } else {
            Minecraft.getMinecraft().addScheduledTask(runnable);
        }
    }

    public <MENU extends Container, BE extends TileEntity> void handleServerboundContainerPacket(
            Class<MENU> menuClass,
            Class<BE> blockEntityClass,
            BlockPos pos,
            int containerId,
            BiConsumer<MENU, BE> callback
    ) {

        handleServerboundContainerPacket(
                this,
                menuClass,
                blockEntityClass,
                pos,
                containerId,
                callback
        );
    }

    public static <MENU extends Container, BE extends TileEntity> void handleServerboundContainerPacket(
            SFMPacketHandlingContext ctx,
            Class<MENU> menuClass,
            Class<BE> blockEntityClass,
            BlockPos pos,
            int containerId,
            BiConsumer<MENU, BE> callback
    ) {

        var sender = ctx.inner.getServerHandler().player;
        if (sender == null) {
            SFM.LOGGER.warn("Invalid packet received: no sender");
            return;
        }
        if (sender.isSpectator()) {
            SFM.LOGGER.warn("Invalid packet received from {}: sender is spectator", sender.getName());
            return;
        }

        var menu = sender.openContainer;
        if (!menuClass.isInstance(menu)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: menu is not instance of expected class",
                    sender.getName()
            );
            return;
        }
        if (menu.windowId != containerId) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: containerId does not match",
                    sender.getName()
            );
            return;
        }

        var level = SFMEntityUtils.getLevel(sender);
        //noinspection ConstantValue
        if (level == null) {
            SFM.LOGGER.warn("Invalid packet received from {}: level is null", sender.getName());
            return;
        }
        if (!level.isBlockLoaded(pos)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: tile entity is not loaded",
                    sender.getName()
            );
            return;
        }

        var blockEntity = level.getTileEntity(pos);
        if (!blockEntityClass.isInstance(blockEntity)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: block entity is not instance of expected class",
                    sender.getName()
            );
            return;
        }
        //noinspection unchecked
        callback.accept((MENU) menu, (BE) blockEntity);
    }

    public void compileAndThen(
            String programString,
            boolean willMutateProgram,
            ProgramConsumer callback
    ) {

        EntityPlayerMP player = this.serverPlayer();
        if (player == null) return;
        ManagerBlockEntity manager;
        if (player.openContainer instanceof ManagerContainerMenu mcm) {
            if (SFMEntityUtils.getLevel(player).getTileEntity(mcm.MANAGER_POSITION) instanceof ManagerBlockEntity mbe) {
                manager = mbe;
            } else {
                return;
            }
        } else {
            //todo: localize
            SFMPackets.sendToPlayer(
                    player, new ClientboundInputInspectionResultsPacket(
                            "This inspection is only available when editing inside a manager.")
            );
            return;
        }
        //todo: localize

        new ProgramBuilder(programString)
                .useCache(!willMutateProgram)
                .build()
                .caseSuccess((program, metadata) -> callback.accept(
                        program,
                        player,
                        manager
                ))
                .caseFailure(result -> {
                    //todo: localize
                    SFMPackets.sendToPlayer(
                            player,
                            new ClientboundOutputInspectionResultsPacket("failed to compile program")
                    );
                });
    }

    @FunctionalInterface
    public interface ProgramConsumer {
        void accept(
                Program program,
                EntityPlayerMP player,
                ManagerBlockEntity managerBlockEntity
        );

    }

}
