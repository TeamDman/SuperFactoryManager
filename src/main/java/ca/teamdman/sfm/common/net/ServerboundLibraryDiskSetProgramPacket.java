package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.LibraryBlockEntity;
import ca.teamdman.sfm.common.containermenu.LibraryContainerMenu;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Packet sent from client to server to update a disk's program in a library block slot.
 */
public record ServerboundLibraryDiskSetProgramPacket(
        int containerId,
        BlockPos libraryPos,
        int slotIndex,
        String programString
) implements SFMPacket {

    public static class Daddy implements SFMPacketDaddy<ServerboundLibraryDiskSetProgramPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public void encode(
                ServerboundLibraryDiskSetProgramPacket msg,
                FriendlyByteBuf buf
        ) {
            buf.writeVarInt(msg.containerId);
            buf.writeBlockPos(msg.libraryPos);
            buf.writeVarInt(msg.slotIndex);
            buf.writeUtf(msg.programString, Program.MAX_PROGRAM_LENGTH);
        }

        @Override
        public ServerboundLibraryDiskSetProgramPacket decode(FriendlyByteBuf buf) {
            return new ServerboundLibraryDiskSetProgramPacket(
                    buf.readVarInt(),
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readUtf(Program.MAX_PROGRAM_LENGTH)
            );
        }

        @Override
        public void handle(
                ServerboundLibraryDiskSetProgramPacket msg,
                SFMPacketHandlingContext context
        ) {
            var sender = context.sender();
            if (sender == null) {
                return;
            }

            // Verify the player has the menu open
            if (!(sender.containerMenu instanceof LibraryContainerMenu menu)) {
                return;
            }
            if (menu.containerId != msg.containerId) {
                return;
            }

            // Verify the library block exists at the position
            if (!(sender.level.getBlockEntity(msg.libraryPos) instanceof LibraryBlockEntity library)) {
                return;
            }

            // Verify slot index is valid
            if (msg.slotIndex < 0 || msg.slotIndex >= LibraryBlockEntity.DISK_SLOT_COUNT) {
                return;
            }

            // Get the disk in the slot
            ItemStack disk = library.getItem(msg.slotIndex);
            if (!DiskItem.isValidDisk(disk)) {
                return;
            }

            // Update the disk's program
            DiskItem.setProgram(disk, msg.programString);

            // Create a library resolver so USE statements can resolve other libraries on the network
            var libraryResolver = library.createLibraryResolver();
            DiskItem.compileAndUpdateErrorsAndWarnings(disk, null, true, libraryResolver);
            DiskItem.pruneIfDefault(disk);
            library.setChanged();

            // Update the menu's library entries
            menu.libraryEntries.clear();
            menu.libraryEntries.addAll(library.getLibraryEntries());
        }

        @Override
        public Class<ServerboundLibraryDiskSetProgramPacket> getPacketClass() {
            return ServerboundLibraryDiskSetProgramPacket.class;
        }
    }
}
