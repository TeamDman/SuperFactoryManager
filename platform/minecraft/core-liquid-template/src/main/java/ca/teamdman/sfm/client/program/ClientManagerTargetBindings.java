package ca.teamdman.sfm.client.program;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.Label;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** Canonicalises only labels referenced by frame triggers for exact consent identity. */
public final class ClientManagerTargetBindings {
    public static final int MAX_LABELS = 32;
    public static final int MAX_POSITIONS = 64;

    private ClientManagerTargetBindings() {}

    public static String canonical(Program program, LabelPositionHolder labels) {
        Set<String> names = new TreeSet<>();
        program.triggers().stream().filter(FrameTrigger.class::isInstance)
                .map(FrameTrigger.class::cast)
                .flatMap(trigger -> trigger.labels().stream())
                .map(Label::name).forEach(names::add);
        if (names.size() > MAX_LABELS) throw new IllegalArgumentException("Too many Client Manager labels");

        StringBuilder result = new StringBuilder();
        int positions = 0;
        for (String name : names) {
            long[] sorted = labels.getPositions(name).longStream().sorted().toArray();
            if (sorted.length == 0) throw new IllegalArgumentException("Missing Client Manager label: " + name);
            positions += sorted.length;
            if (positions > MAX_POSITIONS) {
                throw new IllegalArgumentException("Too many Client Manager label positions");
            }
            result.append(name.length()).append(':').append(name).append('=').append(sorted.length).append(':');
            Arrays.stream(sorted).forEach(value -> result.append(Long.toHexString(value)).append(','));
            result.append(';');
        }
        return result.toString();
    }

    public static boolean contains(FrameTrigger trigger, LabelPositionHolder labels, BlockPos display) {
        return trigger.labels().stream().map(Label::name)
                .anyMatch(name -> labels.getPositions(name).contains(display));
    }
}
