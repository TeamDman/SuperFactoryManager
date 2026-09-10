package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.theme.SFMClientTheme;
import java.util.LinkedHashMap;

/** Bounded derived evidence, invalidated by immutable theme/registry replacement. Never persisted or hydrated. */
public final class SFMItemstackPreviewCache {
    public static final int LIMIT=2048;
    private SFMClientTheme theme;
    private SFMItemstackPreviewRegistry.Snapshot registry;
    private final LinkedHashMap<SFMItemstackPreviewSubject,SFMItemstackPreviewRules.Decision> entries=new LinkedHashMap<>(64,.75f,true);
    public synchronized SFMItemstackPreviewRules.Decision resolve(SFMClientTheme currentTheme,
            SFMItemstackPreviewRegistry.Snapshot currentRegistry,SFMItemstackPreviewSubject subject) {
        if (theme!=currentTheme || registry!=currentRegistry) {
            entries.clear(); theme=currentTheme; registry=currentRegistry;
        }
        var known=entries.get(subject);
        if (known!=null) return known;
        var resolved=SFMItemstackPreviewThemeResolver.resolve(theme,registry,subject);
        if (entries.size()>=LIMIT) entries.remove(entries.keySet().iterator().next());
        entries.put(subject,resolved);
        return resolved;
    }
    public synchronized int size() { return entries.size(); }
}
