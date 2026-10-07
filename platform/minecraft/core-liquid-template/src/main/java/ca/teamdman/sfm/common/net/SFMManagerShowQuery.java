package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.common.authorization.SFMManagerOperatorAuthorization;
import ca.teamdman.sfm.common.blockentity.ClientManagerProgramProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Server-thread, read-only projection for one exact loaded manager. */
public final class SFMManagerShowQuery {
    private SFMManagerShowQuery() { }

    public static ClientboundManagerShowPacket evaluate(
            SFMPacketHandlingContext context, UUID requestId, ResourceLocation dimension, BlockPos position
    ) {
        var decision = SFMManagerOperatorAuthorization.authorize(context, dimension, position);
        if (!decision.allowed()) {
            return ClientboundManagerShowPacket.denied(requestId, dimension, position,
                    ClientboundManagerShowPacket.Status.valueOf(decision.status().name()));
        }
        ItemStack disk = decision.manager().getDisk();
        CompoundTag raw = disk == null || disk.getTag() == null ? new CompoundTag() : disk.getTag();
        var projected = ClientManagerProgramProjection.project(raw);
        if (projected.isEmpty()) {
            return ClientboundManagerShowPacket.denied(requestId, dimension, position,
                    ClientboundManagerShowPacket.Status.DATA_TOO_LARGE);
        }
        String program = projected.orElseThrow().getString("sfm:program");
        Map<String, List<BlockPos>> labels = new TreeMap<>();
        CompoundTag encodedLabels = projected.orElseThrow().getCompound("sfm:labels");
        for (String name : encodedLabels.getAllKeys()) {
            ListTag positions = encodedLabels.getList(name, 4);
            List<BlockPos> unpacked = new ArrayList<>(positions.size());
            for (int i = 0; i < positions.size(); i++) {
                unpacked.add(BlockPos.of(((LongTag) positions.get(i)).getAsLong()));
            }
            labels.put(name, unpacked);
        }
        try {
            return new ClientboundManagerShowPacket(requestId, dimension, position,
                    ClientboundManagerShowPacket.Status.ALLOWED, program, labels);
        } catch (IllegalArgumentException invalid) {
            return ClientboundManagerShowPacket.denied(requestId, dimension, position,
                    ClientboundManagerShowPacket.Status.DATA_TOO_LARGE);
        }
    }
}
