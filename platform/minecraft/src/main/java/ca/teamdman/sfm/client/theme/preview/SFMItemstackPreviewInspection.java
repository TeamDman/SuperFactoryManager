package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerRowInspection;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelViewport;
import java.util.Objects;
import java.util.Optional;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression.quote;

/** Shared immutable entry evidence for inspection and the user-mediated prompt export. */
public record SFMItemstackPreviewInspection(SFMExplorerRowInspection row,SFMItemstackPreviewSubject subject,
        Optional<SFMItemstackPreviewThemeTarget> theme,SFMItemstackPreviewRules.Decision decision,
        String provider,Optional<SFMItemIcon> requested,Optional<Rendered> rendered,
        Optional<SFMExplorerPanelViewport.Rect> bounds) {
    public static final String SCHEMA="sfm.itemstack-preview-inspection/1";
    public record Rendered(String itemId,boolean fallback,boolean levelAvailable,String reason) {}
    public SFMItemstackPreviewInspection {
        Objects.requireNonNull(row); Objects.requireNonNull(subject); Objects.requireNonNull(theme);
        Objects.requireNonNull(decision); Objects.requireNonNull(provider); Objects.requireNonNull(requested);
        Objects.requireNonNull(rendered); Objects.requireNonNull(bounds);
        if (!row.rowAddress().equals(subject.path())) throw new IllegalArgumentException("Inspection subject must match captured row");
    }
    public String detailsPayload() { return row.detailsPayload()+"\n"+subjectPayload()+"\n"+idsPayload(); }
    public String subjectPayload() {
        StringBuilder out=new StringBuilder("preview.schema: "+SCHEMA+"\n");
        out.append("subject.path: ").append(quote(subject.path().canonical())).append('\n');
        out.append("subject.resolver: ").append(quote(subject.resolverId())).append('\n');
        out.append("subject.kind: ").append(subject.kind()).append('\n');
        out.append("subject.name: ").append(quote(subject.name())).append('\n');
        out.append("subject.basename: ").append(quote(subject.basename())).append('\n');
        out.append("subject.suffixes: ").append(subject.suffixes().stream().map(SFMItemstackPreviewExpression::quote).toList()).append('\n');
        subject.metadata().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
                out.append("subject.metadata[").append(quote(entry.getKey())).append("]: ").append(quote(entry.getValue())).append('\n'));
        out.append("subject.lazy-facts: unavailable; no content or filesystem lookup performed\n");
        out.append("theme.target: ").append(theme.map(SFMItemstackPreviewThemeTarget::argument).orElse("unavailable")).append('\n');
        return out.toString();
    }
    public String idsPayload() {
        StringBuilder out=new StringBuilder("presentation.provider: "+quote(provider)+"\n");
        out.append("presentation.requested-item: ").append(requested.map(icon->quote(icon.requestedItem().toString())).orElse("unavailable")).append('\n');
        out.append("presentation.rendered-item: ").append(rendered.map(value->quote(value.itemId())).orElse("unavailable")).append('\n');
        out.append("rules.status: ").append(decision.status()).append('\n');
        decision.candidates().forEach(rule->out.append("rule: ").append(quote(rule.id())).append(" layer=").append(rule.layer())
                .append(" predicate=").append(quote(rule.predicate().print())).append(" item=").append(quote(rule.icon().requestedItem().toString())).append('\n'));
        return out.toString();
    }
    public String ruleExplanation() {
        return "Icon rule decision (captured snapshot)\n\n"+subjectPayload()+idsPayload()
                +"\nAuthority: user > mod > default. Within one layer, only supported implication proves a narrower match.\n"
                +"Legacy file maps choose their own suffix/kind fallback first. Resolver-specific defaults remain their own presentation.\n"
                +"An ambiguous or unavailable authored decision retains the baseline icon and displays !.\n"
                +decision.diagnostics().stream().map(SFMItemstackPreviewExpression::quote).collect(java.util.stream.Collectors.joining("\n"));
    }
    public String renderingExplanation() {
        return "ItemStack rendering at the title screen\n\n"+idsPayload()
                +"rendering: "+rendered.map(value->"level-available="+value.levelAvailable()+", fallback="+value.fallback()+", reason="+quote(value.reason())).orElse("unavailable")
                +"\nVanilla chests CAN render without a level: Minecraft has a level-independent custom renderer.\n"
                +"Ordinary baked models also work. An unproven mod custom renderer may need a world/player; at the title screen SFM uses its declared fallback, then paper if necessary.\n"
                +"Missing registry items also use a fallback. ! marks degraded rendering. Rule choice and rendering capability are separate facts.\n";
    }
    public String boundsPayload() { return "Icon bounds (panel-local GUI units, half-open edges): "+bounds.map(Object::toString).orElse("unavailable")+"\n"; }
    public String cachePayload() {
        return "Icon decision cache\n\nIn-memory only, bounded to "+SFMItemstackPreviewCache.LIMIT+" subjects.\n"
                +"Key: immutable structured subject + theme snapshot + operator/rule registry snapshot.\n"
                +"Theme/registry replacement invalidates decisions. There is no persistent content-analysis cache or cache file for this provider.\n"
                +"No source reads, hashing, network or AI inference occur during icon resolution.\n";
    }
}
