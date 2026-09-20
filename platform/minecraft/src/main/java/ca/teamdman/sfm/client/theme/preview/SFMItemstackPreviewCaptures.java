package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Bounded immutable captures; context equality includes its original host-validity witness. */
public final class SFMItemstackPreviewCaptures {
    public record Capture(long id,SFMClientActionContext context,SFMItemstackPreviewInspection inspection) {}
    private static final LinkedHashMap<Long,Capture> captures=new LinkedHashMap<>();
    private static long next=1;
    private SFMItemstackPreviewCaptures() {}
    public static synchronized Capture retain(SFMClientActionContext context,SFMItemstackPreviewInspection inspection) {
        Capture capture=new Capture(next++,context,inspection); captures.put(capture.id(),capture);
        while(captures.size()>256) captures.remove(captures.keySet().iterator().next());
        return capture;
    }
    public static synchronized Optional<Capture> find(long id) { return Optional.ofNullable(captures.get(id)); }
    public static synchronized Optional<Capture> forContext(SFMClientActionContext context) {
        return captures.values().stream().filter(capture->capture.context()==context).reduce((a,b)->b);
    }
    public static synchronized void inherit(SFMClientActionContext from,SFMClientActionContext to) {
        forContext(from).ifPresent(capture->retain(to,capture.inspection()));
    }
}
