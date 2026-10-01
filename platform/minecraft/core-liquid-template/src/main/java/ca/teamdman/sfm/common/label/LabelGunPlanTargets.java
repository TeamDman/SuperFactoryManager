package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.SFMStreamUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.function.Predicate;

import static ca.teamdman.sfm.common.util.SFMBlockPosUtils.get3DNeighbours;
import static ca.teamdman.sfm.common.util.SFMBlockPosUtils.get3DNeighboursIncludingKittyCorner;

public record LabelGunPlanTargets(
        BlockPosSet positions,
        BlockPosSet warnBecauseNoCableNeighbour
) {
    public static LabelGunPlanTargets getTargets(
            Level level,
            ServerboundLabelGunUsePacket msg
    ) {
{% if features.computercraft %}

        return getTargets(level, msg.pos(), msg.isContiguousModifierActive());
    }

    /**
     * Finds the same target set used by a player label gun without requiring a player packet.
     * This is used by automation such as CC:Tweaked turtles so contiguous selection stays
     * behaviourally identical to the player tool.
     */
    public static LabelGunPlanTargets getTargets(
            Level level,
            BlockPos targetPos,
            boolean contiguous
    ) {
{% else %}
{% endif %}
        // get the block type of the target position
{% if features.computercraft %}
        Block targetBlock = level.getBlockState(targetPos).getBlock();
{% else %}
        Block targetBlock = level.getBlockState(msg.pos()).getBlock();
{% endif %}

{% if features.computercraft %}
        if (!contiguous) {
            return new LabelGunPlanTargets(BlockPosSet.of(targetPos), new BlockPosSet());
{% else %}
        if (!msg.isContiguousModifierActive()) {
            return new LabelGunPlanTargets(BlockPosSet.of(msg.pos()), new BlockPosSet());
{% endif %}
        }
        BlockPosSet targets;

        // find all cable positions so that we only include blocks adjacent to a cable
        BlockPosSet cablePositions = new BlockPosSet();
        if (level.isClientSide()) {
            // There are no cable networks on the client, so we need to discover the cable positions
            // We need to know this to determine how large the change is and if we need to ask the client for confirmation
{% if features.computercraft %}
            get3DNeighbours(targetPos)
{% else %}
            get3DNeighbours(msg.pos())
{% endif %}
                    .filter(pos -> CableNetwork.isCable(level, pos))
                    .flatMap(cablePos -> CableNetwork.discoverCables(level, cablePos))
                    .forEach(cablePositions::add);
        } else {
{% if features.computercraft %}
            get3DNeighbours(targetPos)
{% else %}
            get3DNeighbours(msg.pos())
{% endif %}
                    .map(suspected_cable_pos -> CableNetworkManager.getOrRegisterNetworkFromCablePosition(
                            level,
                            suspected_cable_pos
                    ))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(network -> network.getCablePositions().forEach(cablePositions::add));
        }

        BlockPosSet warnBecauseNoCableNeighbour = new BlockPosSet();
        Predicate<BlockPos> isAdjacentToCable = p -> {
            boolean isAdjacent = get3DNeighbours(p).anyMatch(cablePositions::contains);
            if (!isAdjacent) {
                warnBecauseNoCableNeighbour.add(p);
            }
            return isAdjacent;
        };
        targets = SFMStreamUtils.<BlockPos, BlockPos>getRecursiveStream(
                        (current, nextQueue, results) -> {
                            results.accept(current);
                            get3DNeighboursIncludingKittyCorner(current)
                                    .filter(p -> level.getBlockState(p).getBlock() == targetBlock)
                                    .filter(isAdjacentToCable)
                                    .forEach(nextQueue);
{% if features.computercraft %}
                        }, targetPos
{% else %}
                        }, msg.pos()
{% endif %}
                )
                .collect(BlockPosSet.collector());
        return new LabelGunPlanTargets(targets, warnBecauseNoCableNeighbour);
    }
}
