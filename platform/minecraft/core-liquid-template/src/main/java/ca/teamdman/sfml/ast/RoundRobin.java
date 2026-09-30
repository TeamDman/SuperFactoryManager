package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class RoundRobin implements ASTNode {
    private final Behaviour behaviour;
    private int nextIndex = 0;

    public RoundRobin(Behaviour behaviour) {
        this.behaviour = behaviour;
    }

    public static RoundRobin disabled() {
        return new RoundRobin(Behaviour.UNMODIFIED);
    }

    public Behaviour getBehaviour() {
        return behaviour;
    }

    public int next(int length) {
        // this never exists long enough to roll over
        return nextIndex++ % length;
    }

    @Override
    public String toString() {
        return switch (behaviour) {
            case UNMODIFIED -> "NOT ROUND ROBIN";
            case BY_BLOCK -> "ROUND ROBIN BY BLOCK";
            case BY_LABEL -> "ROUND ROBIN BY LABEL";
        };
    }

    public boolean isEnabled() {
        return behaviour != Behaviour.UNMODIFIED;
    }

    public ArrayList<Pair<Label, BlockPos>> getPositionsForLabels(
            List<Label> labels,
            LabelPositionHolder labelPositionHolder
    ) {
        ArrayList<Pair<Label, BlockPos>> positions = new ArrayList<>();
        switch (getBehaviour()) {
            case BY_LABEL -> {
                int index = next(labels.size());
                Label label = labels.get(index);
                BlockPosSet labelPositions = labelPositionHolder.getPositions(label.name());
                positions.ensureCapacity(labelPositions.size());
                for (BlockPos.MutableBlockPos pos : labelPositions.blockPosIterator()) {
                    positions.add(Pair.of(label, pos.immutable()));
                }
            }
            case BY_BLOCK -> {
                // This can be optimized
                // - ensure capacity where possible
                // - determine index beforehand and stop collecting positions once we have enough
                // to determine the next index do we not need to know the number of candidates?
                // might be able to cache inside the object, as long as the object is nuked when labels are modified
                List<Pair<Label, BlockPos>> candidates = new ArrayList<>();
                BlockPosSet seen = new BlockPosSet();
                for (Label label : labels) {
                    BlockPosSet positionsForLabel = labelPositionHolder.getPositions(label.name());
                    for (BlockPos.MutableBlockPos pos : positionsForLabel.blockPosIterator()) {
                        if (!seen.add(pos)) continue;
                        candidates.add(Pair.of(label, pos.immutable()));
                    }
                }
                if (!candidates.isEmpty()) {
                    positions.add(candidates.get(next(candidates.size())));
                }
            }
            case UNMODIFIED -> {
                for (Label label : labels) {
                    var labelPositions = labelPositionHolder.getPositions(label.name());
                    positions.ensureCapacity(labelPositions.size());
                    for (BlockPos.MutableBlockPos pos : labelPositions.blockPosIterator()) {
                        positions.add(Pair.of(label, pos.immutable()));
                    }
                }
            }
        }
        return positions;
    }

    public enum Behaviour {
        UNMODIFIED,
        BY_BLOCK,
        BY_LABEL
    }
}
