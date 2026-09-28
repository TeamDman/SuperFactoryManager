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
        // get the block type of the target position
        Block targetBlock = level.getBlockState(msg.pos()).getBlock();

        if (!msg.isContiguousModifierActive()) {
            return new LabelGunPlanTargets(BlockPosSet.of(msg.pos()), new BlockPosSet());
        }
        BlockPosSet targets;

        // find all cable positions so that we only include blocks adjacent to a cable
        BlockPosSet cablePositions = new BlockPosSet();
        if (level.isClientSide()) {
            // There are no cable networks on the client, so we need to discover the cable positions
            // We need to know this to determine how large the change is and if we need to ask the client for confirmation
            get3DNeighbours(msg.pos())
                    .filter(pos -> CableNetwork.isCable(level, pos))
                    .flatMap(cablePos -> CableNetwork.discoverCables(level, cablePos))
                    .forEach(cablePositions::add);
        } else {
            get3DNeighbours(msg.pos())
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
                        }, msg.pos()
                )
                .collect(BlockPosSet.collector());
        return new LabelGunPlanTargets(targets, warnBecauseNoCableNeighbour);
    }
}
