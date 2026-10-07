package ca.teamdman.sfm.client.theme.preview;

import java.util.ArrayList;
import java.util.List;

/** Mod extension point: immutable snapshots; contributors must remain pure and bounded. */
public final class SFMItemstackPreviewRegistry {
    public record Snapshot(long revision, SFMItemstackPreviewOperators operators, List<SFMItemstackPreviewRules.Rule> rules) {
        public Snapshot { rules = List.copyOf(rules); }
    }
    private static volatile Snapshot active = new Snapshot(0, SFMItemstackPreviewOperators.builtins(), List.of());
    private SFMItemstackPreviewRegistry() {}
    public static Snapshot snapshot() { return active; }
    public static synchronized void registerOperator(SFMItemstackPreviewOperators.Operator operator) {
        active = new Snapshot(active.revision()+1, active.operators().with(operator), active.rules());
    }
    public static synchronized void registerRule(SFMItemstackPreviewRules.Rule rule) {
        if (rule.layer()!=SFMItemstackPreviewRules.Layer.MOD) throw new IllegalArgumentException("Contributed rules must use MOD authority");
        if (active.rules().size()>=256 || active.rules().stream().anyMatch(existing -> existing.id().equals(rule.id())))
            throw new IllegalArgumentException("Duplicate or excessive contributed rule");
        if (rule.predicate().validate(active.operators())!=SFMItemstackPreviewOperators.Type.BOOLEAN)
            throw new IllegalArgumentException("Contributed rule must be Boolean");
        var rules = new ArrayList<>(active.rules()); rules.add(rule);
        active = new Snapshot(active.revision()+1, active.operators(), rules);
    }
}
