package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.registry.SFMPackets;
import ca.teamdman.sfm.common.util.SFMUtils;
import ca.teamdman.sfml.ast.InputStatement;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Supplier;

public record ServerboundInputInspectionRequestPacket(
        String programString,
        int inputNodeIndex
) {
    public static void encode(ServerboundInputInspectionRequestPacket msg, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
        friendlyByteBuf.writeInt(msg.inputNodeIndex());
    }

    public static ServerboundInputInspectionRequestPacket decode(FriendlyByteBuf friendlyByteBuf) {
        return new ServerboundInputInspectionRequestPacket(
                friendlyByteBuf.readUtf(Program.MAX_PROGRAM_LENGTH),
                friendlyByteBuf.readInt()
        );
    }

    public static void handle(
            ServerboundInputInspectionRequestPacket msg, NetworkEvent.Context context
    ) {
        context.enqueueWork(() -> {
            // todo: duplicate code
            // we don't know if the player has the program edit screen open from a manager or a disk in hand
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ManagerBlockEntity manager;
            if (player.containerMenu instanceof ManagerContainerMenu mcm) {
                if (player.level().getBlockEntity(mcm.MANAGER_POSITION) instanceof ManagerBlockEntity mbe) {
                    manager = mbe;
                } else {
                    return;
                }
            } else {
                //todo: localize
                SFMPackets.INSPECTION_CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new ClientboundInputInspectionResultsPacket(
                                "This inspection is only available when editing inside a manager.")
                );
                return;
            }
            Program.compile(
                    msg.programString,
                    (successProgram, builder) -> builder
                            .getNodeAtIndex(msg.inputNodeIndex)
                            .filter(InputStatement.class::isInstance)
                            .map(InputStatement.class::cast)
                            .ifPresent(inputStatement -> {
                                StringBuilder payload = new StringBuilder();
                                payload
                                        .append(inputStatement.toStringPretty())
                                        .append("\n-- peek results --\n");

                                ProgramContext programContext = new ProgramContext(
                                        successProgram,
                                        manager,
                                        ProgramContext.ExecutionPolicy.EXPLORE_BRANCHES
                                );
                                int preLen = payload.length();
                                inputStatement.gatherSlots(
                                        programContext,
                                        slot -> SFMUtils
                                                .getInputStatementForSlot(
                                                        slot,
                                                        inputStatement.labelAccess()
                                                )
                                                .ifPresent(is -> payload
                                                        .append(is.toStringPretty())
                                                        .append("\n"))
                                );
                                if (payload.length() == preLen) {
                                    payload.append("none");
                                }

                                SFMPackets.INSPECTION_CHANNEL.send(
                                        PacketDistributor.PLAYER.with(() -> player),
                                        new ClientboundInputInspectionResultsPacket(
                                                SFMUtils.truncate(
                                                        payload.toString(),
                                                        ClientboundInputInspectionResultsPacket.MAX_RESULTS_LENGTH
                                                ))
                                );
                            }),
                    failure -> {
                    }
            );
        });
        context.setPacketHandled(true);
    }
}
