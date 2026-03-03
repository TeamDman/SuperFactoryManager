package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public record ServerboundManagerIdeExplorerDropPacket(
        int windowId,
        BlockPos pos,
        int sourceMenuSlot,
        TargetKind targetKind,
        int targetMenuSlot
) implements SFMPacket {

    public enum TargetKind {
        SLOT,
        MANAGER_CONTAINER,
        PLAYER_CONTAINER
    }

    public static class Daddy implements SFMPacketDaddy<ServerboundManagerIdeExplorerDropPacket> {
        @Override
        public PacketDirection getPacketDirection() {
            return PacketDirection.SERVERBOUND;
        }

        @Override
        public Class<ServerboundManagerIdeExplorerDropPacket> getPacketClass() {
            return ServerboundManagerIdeExplorerDropPacket.class;
        }

        @Override
        public void encode(ServerboundManagerIdeExplorerDropPacket msg, FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(msg.windowId());
            friendlyByteBuf.writeBlockPos(msg.pos());
            friendlyByteBuf.writeVarInt(msg.sourceMenuSlot());
            friendlyByteBuf.writeEnum(msg.targetKind());
            friendlyByteBuf.writeVarInt(msg.targetMenuSlot());
        }

        @Override
        public ServerboundManagerIdeExplorerDropPacket decode(FriendlyByteBuf friendlyByteBuf) {
            return new ServerboundManagerIdeExplorerDropPacket(
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readBlockPos(),
                    friendlyByteBuf.readVarInt(),
                    friendlyByteBuf.readEnum(TargetKind.class),
                    friendlyByteBuf.readVarInt()
            );
        }

        @Override
        public void handle(ServerboundManagerIdeExplorerDropPacket msg, SFMPacketHandlingContext context) {
            context.handleServerboundContainerPacket(
                    ManagerContainerMenu.class,
                    ManagerBlockEntity.class,
                    msg.pos,
                    msg.windowId,
                    (menu, manager) -> {
                        if (msg.sourceMenuSlot() < 0 || msg.sourceMenuSlot() >= menu.slots.size()) {
                            return;
                        }
                        Slot source = menu.getSlot(msg.sourceMenuSlot());
                        if (!source.hasItem()) {
                            return;
                        }

                        switch (msg.targetKind()) {
                            case SLOT -> moveToSlot(menu, msg.sourceMenuSlot(), msg.targetMenuSlot());
                            case MANAGER_CONTAINER -> moveToRange(menu, msg.sourceMenuSlot(), 0, 1);
                            case PLAYER_CONTAINER -> moveToRange(menu, msg.sourceMenuSlot(), 1, menu.slots.size());
                        }
                        menu.broadcastChanges();
                    }
            );
        }

        private static void moveToRange(ManagerContainerMenu menu, int sourceIndex, int startInclusive, int endExclusive) {
            if (sourceIndex >= startInclusive && sourceIndex < endExclusive) {
                return;
            }
            Slot source = menu.getSlot(sourceIndex);
            if (!source.hasItem()) {
                return;
            }
            for (int i = startInclusive; i < endExclusive; i++) {
                if (i == sourceIndex) {
                    continue;
                }
                if (mergeIntoSlot(source, menu.getSlot(i), false)) {
                    if (!source.hasItem()) {
                        return;
                    }
                }
            }
            for (int i = startInclusive; i < endExclusive; i++) {
                if (i == sourceIndex) {
                    continue;
                }
                if (mergeIntoSlot(source, menu.getSlot(i), true)) {
                    if (!source.hasItem()) {
                        return;
                    }
                }
            }
        }

        private static void moveToSlot(ManagerContainerMenu menu, int sourceIndex, int targetIndex) {
            if (targetIndex < 0 || targetIndex >= menu.slots.size() || sourceIndex == targetIndex) {
                return;
            }
            Slot source = menu.getSlot(sourceIndex);
            Slot target = menu.getSlot(targetIndex);
            mergeIntoSlot(source, target, false);
            if (source.hasItem()) {
                mergeIntoSlot(source, target, true);
            }
        }

        private static boolean mergeIntoSlot(Slot source, Slot target, boolean requireEmptyTarget) {
            if (!source.hasItem()) {
                return false;
            }
            ItemStack sourceStack = source.getItem();
            ItemStack targetStack = target.getItem();

            if (!target.mayPlace(sourceStack)) {
                return false;
            }
            if (requireEmptyTarget && !targetStack.isEmpty()) {
                return false;
            }

            int maxStack = Math.min(target.getMaxStackSize(sourceStack), sourceStack.getMaxStackSize());
            if (targetStack.isEmpty()) {
                int move = Math.min(sourceStack.getCount(), maxStack);
                ItemStack moved = sourceStack.copy();
                moved.setCount(move);
                target.set(moved);
                sourceStack.shrink(move);
                if (sourceStack.isEmpty()) {
                    source.set(ItemStack.EMPTY);
                }
                source.setChanged();
                target.setChanged();
                return move > 0;
            }

            if (!ItemStack.isSameItemSameTags(sourceStack, targetStack)) {
                return false;
            }
            if (targetStack.getCount() >= maxStack) {
                return false;
            }

            int move = Math.min(sourceStack.getCount(), maxStack - targetStack.getCount());
            if (move <= 0) {
                return false;
            }

            targetStack.grow(move);
            sourceStack.shrink(move);
            if (sourceStack.isEmpty()) {
                source.set(ItemStack.EMPTY);
            }
            source.setChanged();
            target.setChanged();
            return true;
        }
    }
}
