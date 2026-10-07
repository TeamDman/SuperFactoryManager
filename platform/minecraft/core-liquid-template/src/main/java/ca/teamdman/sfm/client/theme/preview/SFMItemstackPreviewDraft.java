package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.action.SFMItemstackPreviewRuleAction;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import java.util.*;

/** A typed local preview; creating or discarding this value never changes the active theme. */
public record SFMItemstackPreviewDraft(SFMItemstackPreviewRuleAction.Request request,SFMClientTheme baseline,
        SFMItemstackPreviewRegistry.Snapshot registry,Optional<SFMItemstackPreviewSubject> subject) {
    public SFMItemstackPreviewDraft {
        Objects.requireNonNull(request);Objects.requireNonNull(baseline);Objects.requireNonNull(registry);Objects.requireNonNull(subject);
        if(request.predicate().validate(registry.operators())!=SFMItemstackPreviewOperators.Type.BOOLEAN)
            throw new IllegalArgumentException("Preview rule must be Boolean");
    }
    public SFMClientTheme previewTheme() {
        var rules=new ArrayList<>(baseline.previewRules());rules.removeIf(rule->rule.id().equals(request.rule().id()));rules.add(request.rule());
        return baseline.withPreviewRules(rules);
    }
    public Optional<SFMItemstackPreviewRules.Decision> decision() {
        return subject.map(value->SFMItemstackPreviewThemeResolver.resolve(previewTheme(),registry,value));
    }
    public String scopeText() { return "USER rule; matches exactly this BOOLEAN predicate:\n"+request.predicate().print(); }
}
