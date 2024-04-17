package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.Constants;
import ca.teamdman.sfm.common.program.LabelPositionHolder;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class RoundRobin implements ASTNode {
    private final Behaviour behaviour;
    private int index = 0;

    public RoundRobin(Behaviour behaviour) {
        this.behaviour = behaviour;
    }

    public static RoundRobin disabled() {
        return new RoundRobin(Behaviour.UNMODIFIED);
    }

    public @Nullable Constants.LocalizationKeys.LocalizationEntry getSmell(LabelAccess labelAccess, boolean each) {
        if (behaviour == Behaviour.BY_BLOCK && each) {
            return Constants.LocalizationKeys.PROGRAM_WARNING_ROUND_ROBIN_SMELLY_EACH;
        }
        if (behaviour == Behaviour.BY_LABEL && labelAccess.labels().size() == 1) {
            return Constants.LocalizationKeys.PROGRAM_WARNING_ROUND_ROBIN_SMELLY_COUNT;
        }

        return null;
    }

    public Stream<BlockPos> gather(LabelAccess labelAccess, LabelPositionHolder labelPositions) {
        return switch (behaviour) {
            case BY_LABEL -> {
                int index = this.next(labelAccess.labels().size());
                yield labelPositions.getPositions(labelAccess.labels().get(index).name()).stream();
            }
            case BY_BLOCK -> {
                List<BlockPos> positions = labelAccess.labels().stream()
                        .map(Label::name)
                        .map(labelPositions::getPositions)
                        .flatMap(Collection::stream)
                        .distinct()
                        .toList();
                if (positions.isEmpty()) {
                    yield Stream.empty();
                }
                yield Stream.of(positions.get(this.next(positions.size())));
            }
            case UNMODIFIED -> labelAccess.labels().stream()
                    .map(Label::name)
                    .map(labelPositions::getPositions)
                    .flatMap(Collection::stream);
        };
    }

    public int next(int max) {
        return index++ % max;
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

    public enum Behaviour {
        UNMODIFIED,
        BY_BLOCK,
        BY_LABEL
    }
}
