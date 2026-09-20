package ca.teamdman.sfm.common.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Exact inventory target carried by a serverbound packet insertion request. */
public record SFMPacketInventoryAddress(
        ResourceLocation dimension,
        BlockPos position,
        Optional<Direction> side
) {
    public static final int MAX_DIMENSION_ID_CHARACTERS = 256;

    public SFMPacketInventoryAddress {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.toString().length() > MAX_DIMENSION_ID_CHARACTERS) {
            throw new IllegalArgumentException(
                    "Packet target dimension exceeds "
                    + MAX_DIMENSION_ID_CHARACTERS
                    + " characters"
            );
        }
        position = Objects.requireNonNull(position, "position").immutable();
        side = Objects.requireNonNull(side, "side");
    }

    public void encode(FriendlyByteBuf target) {
        target.writeUtf(dimension.toString(), MAX_DIMENSION_ID_CHARACTERS);
        target.writeBlockPos(position);
        target.writeBoolean(side.isPresent());
        side.ifPresent(target::writeEnum);
    }

    public static SFMPacketInventoryAddress decode(FriendlyByteBuf source) {
        ResourceLocation dimension = new ResourceLocation(source.readUtf(MAX_DIMENSION_ID_CHARACTERS));
        BlockPos position = source.readBlockPos();
        Optional<Direction> side = source.readBoolean()
                                   ? Optional.of(source.readEnum(Direction.class))
                                   : Optional.empty();
        return new SFMPacketInventoryAddress(dimension, position, side);
    }
}
