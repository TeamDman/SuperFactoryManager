package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.theme.SFMClientTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression.*;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRules.*;

/** One pure evaluation path for legacy maps and authored rules; no path probing or hashing. */
public final class SFMItemstackPreviewThemeResolver {
    private SFMItemstackPreviewThemeResolver() {}

    public static List<Rule> lowerLegacy(SFMClientTheme theme) {
        var rules = new ArrayList<Rule>();
        theme.fileIcons().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            String key=entry.getKey().toLowerCase(Locale.ROOT);
            SFMItemstackPreviewExpression predicate;
            if (key.equals("directory")) predicate=call("sfm:entry/is_container");
            else if (key.equals("unknown")) predicate=call("sfm:entry/is_file");
            else if (key.equals("extensionless")) predicate=call("sfm:bool/and",call("sfm:entry/is_file"),call("sfm:entry/is_extensionless"));
            else if (key.startsWith(".") && key.length()>1) predicate=call("sfm:bool/and",call("sfm:entry/is_file"),call("sfm:entry/has_suffix",literal(key)));
            else return;
            rules.add(new Rule("sfm:legacy-icon/"+sha256(key),Layer.DEFAULT,predicate,entry.getValue()));
        });
        return List.copyOf(rules);
    }

    public static Decision resolve(SFMClientTheme theme, SFMItemstackPreviewRegistry.Snapshot registry, SFMItemstackPreviewSubject subject) {
        return prepare(theme, registry).resolve(subject);
    }

    /** Reuses immutable rule lowering for bulk analysis without caching path-dependent decisions. */
    public static Prepared prepare(SFMClientTheme theme, SFMItemstackPreviewRegistry.Snapshot registry) {
        return new Prepared(theme, registry, lowerLegacy(theme));
    }

    public record Prepared(SFMClientTheme theme, SFMItemstackPreviewRegistry.Snapshot registry, List<Rule> legacyRules) {
    public Prepared { legacyRules = List.copyOf(legacyRules); }
    public Decision resolve(SFMItemstackPreviewSubject subject) {
        boolean legacyFileSubject=subject.path().kind()==ca.teamdman.sfm.client.explorer.SFMPath.Kind.FILE
                || subject.metadata().containsKey(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry.SUBJECT_KIND);
        Decision legacy= SFMItemstackPreviewRules.resolve(legacyFileSubject ? legacyRules : List.of(),registry.operators(),subject);
        var candidates=new ArrayList<Rule>(registry.rules());
        if (legacyFileSubject) candidates.addAll(SFMItemstackPreviewDefaultNames.RULES);
        candidates.addAll(theme.previewRules());
        legacy.winner().ifPresent(rule -> {
            boolean explicit=theme.explicitFileIcons().stream().anyMatch(key -> rule.id().equals("sfm:legacy-icon/"+sha256(key.toLowerCase(Locale.ROOT))));
            Rule adapted=new Rule(rule.id(),explicit ? Layer.USER : Layer.DEFAULT,rule.predicate(),rule.icon());
            // Authored replacement of the same predicate is explicit, not an order-dependent tie.
            if (!explicit || theme.previewRules().stream().noneMatch(value -> value.predicate().equals(adapted.predicate()))) candidates.add(adapted);
        });
        if (legacy.status()==Status.AMBIGUOUS || legacy.status()==Status.UNAVAILABLE) return legacy;
        return SFMItemstackPreviewRules.resolve(candidates,registry.operators(),subject);
    }
    }
}
