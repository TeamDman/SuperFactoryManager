package ca.teamdman.sfm.common.label;

import ca.teamdman.sfm.common.cablenetwork.CableNetwork;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.net.ServerboundLabelGunUsePacket;
import ca.teamdman.sfm.common.util.SFMStreamUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.common.util.SFMStreamUtils.get3DNeighbours;
import static ca.teamdman.sfm.common.util.SFMStreamUtils.get3DNeighboursIncludingKittyCorner;

public record LabelGunPlanTargets(
        Set<BlockPos> positions,
        Set<BlockPos> warnBecauseNoCableNeighbour
) {
    public static LabelGunPlanTargets getTargets(
            Level level,
            ServerboundLabelGunUsePacket msg
    ) {
        // get the block type of the target position
        Block targetBlock = level.getBlockState(msg.pos()).getBlock();

        if (!msg.isContiguousModifierActive()) {
            return new LabelGunPlanTargets(Set.of(msg.pos()), Set.of());
        }
        Set<BlockPos> targets;

        // find all cable positions so that we only include blocks adjacent to a cable
        Set<BlockPos> cablePositions;
        if (level.isClientSide()) {
            // There are no cable networks on the client, so we need to discover the cable positions
            // We need to know this to determine how large the change is and if we need to ask the client for confirmation
            cablePositions = get3DNeighbours(msg.pos())
                    .filter(pos -> CableNetwork.isCable(level, pos))
                    .flatMap(cablePos -> CableNetwork.discoverCables(level, cablePos))
                    .collect(Collectors.toSet());
        } else {
            cablePositions = get3DNeighbours(msg.pos())
                    .map(suspected_cable_pos -> CableNetworkManager.getOrRegisterNetworkFromCablePosition(
                            level,
                            suspected_cable_pos
                    ))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .flatMap(CableNetwork::getCablePositions)
                    .collect(Collectors.toSet());
        }

        Set<BlockPos> warnBecauseNoCableNeighbour = new HashSet<>();
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
                .collect(Collectors.toSet());
        return new LabelGunPlanTargets(targets, warnBecauseNoCableNeighbour);
    }
}
