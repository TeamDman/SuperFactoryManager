package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class SFMPacketHandlingContext {
    private final IPayloadContext inner;

    public SFMPacketHandlingContext(IPayloadContext inner) {
        this.inner = inner;
    }

    public @Nullable ServerPlayer sender() {
        if (inner.player() instanceof ServerPlayer player) {
            return player;
        } else {
            return null;
        }
    }

    public void finish() {

//        inner.setPacketHandled(true);
    }

    public void enqueueAndFinish(Runnable runnable) {

        inner.enqueueWork(runnable);
        finish();
    }

    public <MENU extends AbstractContainerMenu, BE extends BlockEntity> void handleServerboundContainerPacket(
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

    public static <MENU extends AbstractContainerMenu, BE extends BlockEntity> void handleServerboundContainerPacket(
            SFMPacketHandlingContext ctx,
            Class<MENU> menuClass,
            Class<BE> blockEntityClass,
            BlockPos pos,
            int containerId,
            BiConsumer<MENU, BE> callback
    ) {

        var sender = ctx.sender();
        if (sender == null) {
            SFM.LOGGER.warn("Invalid packet received: no sender");
            return;
        }
        if (sender.isSpectator()) {
            SFM.LOGGER.warn("Invalid packet received from {}: sender is spectator", sender.getName().getString());
            return;
        }

        var menu = sender.containerMenu;
        if (!menuClass.isInstance(menu)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: menu is not instance of expected class",
                    sender.getName().getString()
            );
            return;
        }
        if (menu.containerId != containerId) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: containerId does not match",
                    sender.getName().getString()
            );
            return;
        }

        var level = SFMEntityUtils.getLevel(sender);
        //noinspection ConstantValue
        if (level == null) {
            SFM.LOGGER.warn("Invalid packet received from {}: level is null", sender.getName().getString());
            return;
        }
        if (!level.isLoaded(pos)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: block entity is not loaded",
                    sender.getName().getString()
            );
            return;
        }

        var blockEntity = level.getBlockEntity(pos);
        if (!blockEntityClass.isInstance(blockEntity)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: block entity is not instance of expected class",
                    sender.getName().getString()
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

        ServerPlayer player = this.sender();
        if (player == null) return;
        ManagerBlockEntity manager;
        if (player.containerMenu instanceof ManagerContainerMenu mcm) {
            if (SFMEntityUtils.getLevel(player).getBlockEntity(mcm.MANAGER_POSITION) instanceof ManagerBlockEntity mbe) {
                manager = mbe;
            } else {
                return;
            }
        } else {
            //todo: localize
            SFMPackets.sendToPlayer(
                    () -> player, new ClientboundInputInspectionResultsPacket(
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
                            () -> player,
                            new ClientboundOutputInspectionResultsPacket("failed to compile program")
                    );
                });
    }

    @FunctionalInterface
    public interface ProgramConsumer {
        void accept(
                Program program,
                ServerPlayer player,
                ManagerBlockEntity managerBlockEntity
        );

    }

}
