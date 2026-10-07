package ca.teamdman.sfm.common.blockentity;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.CompressedBlockPosSet;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientManagerProgramProjectionTests {
    @Test
    void projectionDropsUnrelatedItemDataAndRoundTripsBoundedLegacyLabels() {
        CompoundTag raw = new CompoundTag();
        raw.putString("sfm:program", "CLIENT BTW\n");
        raw.putByteArray("unrelated", new byte[200_000]);
        BlockPosSet positions = new BlockPosSet();
        positions.add(new BlockPos(1, 2, 3));
        positions.add(new BlockPos(1, 2, 4));
        CompoundTag labels = new CompoundTag();
        labels.put("displays", CompressedBlockPosSet.from(positions).asTag());
        raw.put("sfm:labels", labels);

        CompoundTag projection = ClientManagerProgramProjection.project(raw).orElseThrow();
        assertEquals(Set.of("sfm:program", "sfm:labels"), projection.getAllKeys());
        assertEquals("CLIENT BTW\n", projection.getString("sfm:program"));
        assertEquals(2, LabelPositionHolder.deserialize(projection.getCompound("sfm:labels"))
                .getPositions("displays").size());
        assertTrue(raw.contains("unrelated"), "Projection must not destroy the server's full disk");
    }

    @Test
    void tinyCompressedExpansionBombFailsBeforePositionAllocation() {
        CompoundTag labels = new CompoundTag();
        labels.put("displays", volume(1, Integer.MAX_VALUE));
        CompoundTag raw = new CompoundTag();
        raw.put("sfm:labels", labels);
        assertTrue(ClientManagerProgramProjection.project(raw).isEmpty());
        labels.put("displays", volume(Integer.MAX_VALUE, 0));
        assertTrue(ClientManagerProgramProjection.project(raw).isEmpty());
        labels.put("displays", new ByteArrayTag(new byte[]{-1, -1, -1, -1, -1, -1}));
        assertTrue(ClientManagerProgramProjection.project(raw).isEmpty());
    }

    @Test
    void totalPositionBudgetAppliesAcrossLabels() {
        CompoundTag labels = new CompoundTag();
        labels.put("first", volume(1, 32));
        labels.put("second", volume(1, 32));
        CompoundTag raw = new CompoundTag();
        raw.put("sfm:labels", labels);
        assertTrue(ClientManagerProgramProjection.project(raw).isEmpty());
        labels.put("second", volume(1, 30));
        assertTrue(ClientManagerProgramProjection.project(raw).isPresent());
        raw.putString("sfm:program", "x".repeat(ClientManagerProgramProjection.MAX_SOURCE_BYTES));
        assertTrue(ClientManagerProgramProjection.project(raw).isEmpty());
    }

    @Test
    void sourceProjectionNormalizesOnlyLineEndingsAndRejectsMalformedUnicode() {
        CompoundTag raw = new CompoundTag();
        raw.putString("sfm:program", "CLIENT BTW\r\n-- comments  \rNAME \"name\"\n");
        assertEquals("CLIENT BTW\n-- comments  \nNAME \"name\"\n",
                ClientManagerProgramProjection.project(raw).orElseThrow().getString("sfm:program"));
        assertTrue(raw.getString("sfm:program").contains("\r"), "Projection must not mutate the original disk");
        raw.putString("sfm:program", "\ud800");
        assertTrue(ClientManagerProgramProjection.project(raw).isEmpty());
    }

    private static ByteArrayTag volume(int count, int extension) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(count);
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeEnum(Direction.EAST);
            buffer.writeVarInt(extension);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new ByteArrayTag(bytes);
        } finally {
            buffer.release();
        }
    }
}
