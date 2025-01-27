package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.LabelPositionHolder;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
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

    public @NotNull HashMap<Pair<Label, BlockPos>, Integer> getPositionsForLabels(
            LabelAccess labelAccess,
            LabelPositionHolder labelPositionHolder
    ) {
        HashMap<Pair<Label, BlockPos>, Integer> positions = new HashMap<>();

        switch (getBehaviour()) {
            case BY_LABEL -> {
                int index = next(labelAccess.labels().size());
                Label label = labelAccess.labels().get(index);
                for (BlockPos pos : labelPositionHolder.getPositions(label.name())) {
                    Pair<Label, BlockPos> pair = Pair.of(label, pos);
                    positions.put(pair, positions.getOrDefault(pair, 0) + 1);
                }
            }
            case BY_BLOCK -> {
                List<Pair<Label, BlockPos>> candidates = new ArrayList<>();
                LongOpenHashSet seen = new LongOpenHashSet();
                for (Label label : labelAccess.labels()) {
                    for (BlockPos pos : labelPositionHolder.getPositions(label.name())) {
                        if (!seen.add(pos.asLong())) continue;
                        candidates.add(Pair.of(label, pos));
                    }
                }
                if (!candidates.isEmpty()) {
                    Pair<Label, BlockPos> pair = candidates.get(next(candidates.size()));
                    positions.put(pair, positions.getOrDefault(pair, 0) + 1);
                }
            }
            case UNMODIFIED -> {
                for (Label label : labelAccess.labels()) {
                    for (BlockPos pos : labelPositionHolder.getPositions(label.name())) {
                        Pair<Label, BlockPos> pair = Pair.of(label, pos);
                        positions.put(pair, positions.getOrDefault(pair, 0) + 1);
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
