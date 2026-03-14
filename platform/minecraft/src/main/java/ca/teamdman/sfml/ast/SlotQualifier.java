package ca.teamdman.sfml.ast;

import java.util.List;

public record SlotQualifier(
        boolean each,

        NumberSet numberSet
) implements SfmlAstNode {
    public static final SlotQualifier DEFAULT = new SlotQualifier(false, NumberSet.MAX_RANGE);

    public boolean isDefault() {

        return !each && numberSet.equals(NumberSet.MAX_RANGE);
    }

    public boolean contains(int slot) {

        return numberSet().contains(slot);
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        if (each) sb.append("EACH ");
        sb.append("SLOTS ");
        sb.append(numberSet);
        return sb.toString();
    }

    @Override
    public List<NumberSet> getChildNodes() {

        return List.of(numberSet);
    }

}
