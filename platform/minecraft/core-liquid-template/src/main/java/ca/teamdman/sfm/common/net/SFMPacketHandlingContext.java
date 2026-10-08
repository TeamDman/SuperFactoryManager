package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
{% if features.multiplayer_packets %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% elsif features.packet_direction_validation %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endif %}
import ca.teamdman.sfm.common.util.SFMEntityUtils;
import ca.teamdman.sfml.ast.Program;
{% if features.sfml_execution_side %}
import ca.teamdman.sfml.ast.ProgramExecutionSide;
{% endif %}
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.network.NetworkEvent;
{% when "1.20.2", "1.20.3" %}
import net.neoforged.neoforge.network.NetworkEvent;
{% when "1.20.4" %}
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
{% when "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.network.handling.IPayloadContext;
{% endcase %}
{% if features.packet_direction_validation %}
import net.minecraftforge.network.NetworkDirection;
{% endif %}
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
import java.util.function.Supplier;
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}

public class SFMPacketHandlingContext {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    private final NetworkEvent.Context inner;
{% when "1.20.4" %}
    private final PlayPayloadContext inner;
{% when "1.21", "1.21.1", "26.1.2" %}
    private final IPayloadContext inner;
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
    public SFMPacketHandlingContext(Supplier<NetworkEvent.Context> inner) {
        this.inner = inner.get();
{% when "1.20.4" %}
    public SFMPacketHandlingContext(PlayPayloadContext inner) {
        this.inner = inner;
{% when "1.21", "1.21.1", "26.1.2" %}
    public SFMPacketHandlingContext(IPayloadContext inner) {
        this.inner = inner;
{% endcase %}
    }

    public @Nullable ServerPlayer sender() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}

        return inner.getSender();
{% when "1.20.4" %}
        if (inner.player().orElse(null) instanceof ServerPlayer player) {
            return player;
        } else {
            return null;
        }
{% when "1.21", "1.21.1", "26.1.2" %}
        if (inner.player() instanceof ServerPlayer player) {
            return player;
        } else {
            return null;
        }
{% endcase %}
    }

{% if features.multiplayer_packets %}
    /** Local transport identity, never serialized or accepted from a peer. */
    @MCVersionDependentBehaviour
    public Object networkConnectionIdentity() {
        return inner.getNetworkManager();
    }

{% endif %}
{% if features.packet_direction_validation %}
    @MCVersionDependentBehaviour
    public boolean hasExpectedDirection(SFMPacketDaddy.PacketDirection expected) {
        NetworkDirection actual = inner.getDirection();
        return switch (expected) {
            case SERVERBOUND -> actual == NetworkDirection.PLAY_TO_SERVER;
            case CLIENTBOUND -> actual == NetworkDirection.PLAY_TO_CLIENT;
        };
    }

{% endif %}
    public void finish() {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
        inner.setPacketHandled(true);
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
//        inner.setPacketHandled(true);
{% endcase %}
    }

    public void enqueueAndFinish(Runnable runnable) {

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.21", "1.21.1", "26.1.2" %}
        inner.enqueueWork(runnable);
{% when "1.20.4" %}
        inner.workHandler().submitAsync(runnable);
{% endcase %}
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

{% if features.manager_menu_request_validation %}
        if (!menu.stillValid(sender)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: menu is no longer valid",
                    sender.getName().getString()
            );
            return;
        }
        if (menu instanceof ManagerContainerMenu managerMenu && !managerMenu.MANAGER_POSITION.equals(pos)) {
            SFM.LOGGER.warn(
                    "Invalid packet received from {}: target does not match open manager",
                    sender.getName().getString()
            );
            return;
        }

{% endif %}
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
{% if features.sfml_execution_side %}
                .forExecutionSide(ProgramExecutionSide.SERVER)
{% endif %}
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
