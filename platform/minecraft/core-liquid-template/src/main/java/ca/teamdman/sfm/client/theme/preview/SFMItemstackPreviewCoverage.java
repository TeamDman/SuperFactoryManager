package ca.teamdman.sfm.client.theme.preview;

import java.util.Set;

/** Requested-icon coverage, independent of rendering context or filesystem IO. */
public final class SFMItemstackPreviewCoverage {
    public record Result(boolean covered, String reason, String item, String rule, String predicate) {}
    private static final Set<String> GENERIC_ITEMS = Set.of("minecraft:paper", "minecraft:chest", "minecraft:barrel");
    private SFMItemstackPreviewCoverage() {}
    public static Result classify(SFMItemstackPreviewRules.Decision decision) {
        if (decision.status()!=SFMItemstackPreviewRules.Status.MATCHED || decision.winner().isEmpty())
            return new Result(false, decision.status().name(), "", "", "");
        var winner=decision.winner().orElseThrow();
        String item=winner.icon().requestedItem().toString(), predicate=winner.predicate().print();
        boolean genericPredicate=Set.of("sfm:entry/is_file", "sfm:entry/is_container",
                "sfm:bool/and sfm:entry/is_file sfm:entry/is_extensionless").contains(predicate);
        String reason=GENERIC_ITEMS.contains(item) ? "GENERIC_ICON" : genericPredicate ? "GENERIC_RULE" : "SPECIFIC";
        return new Result(reason.equals("SPECIFIC"), reason, item, winner.id(), predicate);
    }
}
