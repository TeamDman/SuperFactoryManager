package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerPanel;
import ca.teamdman.sfm.client.screen.theme_settings.SFMItemstackPreviewDraftPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.common.localization.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Optional draft/picker and reset routes over the very same typed add contract. */
public final class SFMItemstackPreviewRuleToolsAction implements SFMClientAction<SFMClientActionContext>,SFMClientActionCompletion {
    @SFMLocalizationDatagen public static final LocalizationEntry PICK=new LocalizationEntry("gui.sfm.preview_rule.pick","Choose ItemStack for preview rule");
    @SFMLocalizationDatagen public static final LocalizationEntry PREVIEW=new LocalizationEntry("gui.sfm.preview_rule.preview","Preview ItemStack rule without saving");
    @SFMLocalizationDatagen public static final LocalizationEntry RESET=new LocalizationEntry("gui.sfm.preview_rule.reset","Reset this authored ItemStack rule");
    @SFMLocalizationDatagen public static final LocalizationEntry COPY=new LocalizationEntry("gui.sfm.preview_rule.command.copy","Copy complete ItemStack rule command");
    public enum Kind {
        PICK("pick",SFMItemstackPreviewRuleToolsAction.PICK),PREVIEW("preview",SFMItemstackPreviewRuleToolsAction.PREVIEW),
        RESET("reset",SFMItemstackPreviewRuleToolsAction.RESET),COPY("command/copy",SFMItemstackPreviewRuleToolsAction.COPY);
        public final String path;public final LocalizationEntry title;
        Kind(String suffix,LocalizationEntry title) { path="explorer/itemstack_preview_rule/"+suffix;this.title=title; }
        public ResourceLocation id() { return new ResourceLocation("sfm",path); }
        public String prefix() { return "sfm action invoke "+id(); }
    }
    private final Kind kind;
    public SFMItemstackPreviewRuleToolsAction(Kind kind) { this.kind=kind; }
    @Override public Component title() { return kind.title.getComponent(); }
    @Override public Component description() { return kind.title.getComponent(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() { return new SFMItemstackPreviewRuleAction().requirement(); }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> root) {
        var theme=RequiredArgumentBuilder.<SFMClientActionSource,String>argument(SFMItemstackPreviewRuleAction.THEME,new SFMItemstackPreviewRuleAction.ThemeArgument());
        if(kind==Kind.RESET) {
            theme.then(RequiredArgumentBuilder.<SFMClientActionSource,String>argument("rule_id",SFMCanonicalTokenArgument.token())
                    .suggests((context,builder)->{SFMClientThemeService.active().previewRules().forEach(rule->{if(rule.id().contains(builder.getRemaining()))builder.suggest(rule.id());});return builder.buildFuture();})
                    .executes(this::invoke));
        } else {
            var predicate=RequiredArgumentBuilder.<SFMClientActionSource,SFMItemstackPreviewExpression>argument(SFMItemstackPreviewRuleAction.PREDICATE,new SFMItemstackPreviewRuleAction.PredicateArgument());
            if(kind==Kind.PICK) predicate.executes(this::invoke);
            else predicate.then(RequiredArgumentBuilder.<SFMClientActionSource,String>argument(SFMItemstackPreviewRuleAction.ITEM,SFMCanonicalTokenArgument.token()).executes(this::invoke));
            theme.then(predicate);
        }
        root.then(theme);
    }
    @Override public int execute(SFMClientActionContext target,CommandContext<SFMClientActionSource> command) throws CommandSyntaxException {
        try {
            var theme=SFMItemstackPreviewThemeTarget.parse(StringArgumentType.getString(command,SFMItemstackPreviewRuleAction.THEME));
            requireAuthority(theme);
            if(kind==Kind.RESET) {
                String id=SFMCanonicalTokenArgument.get(command,"rule_id");
                resetRules(SFMClientThemeService.active().previewRules(),id); // reject before scheduling, then validate again against committed input
                SFMItemstackPreviewRuleAction.submitConfirmed(theme,before->resetRules(before,id),command.getSource()::sendFeedback);return 1;
            }
            var predicate=command.getArgument(SFMItemstackPreviewRuleAction.PREDICATE,SFMItemstackPreviewExpression.class);
            var snapshot=SFMItemstackPreviewRegistry.snapshot();
            if(kind==Kind.PICK) {
                var captured=SFMItemstackPreviewCaptures.forContext(target).map(SFMItemstackPreviewCaptures.Capture::inspection);
                var initial=captured.flatMap(SFMItemstackPreviewInspection::requested).orElse(SFMItemIcon.vanilla("paper","Draft icon"));
                var picker=SFMItemPickerPanel.fromRegistry(initial,icon->{
                    try {
                        requireAuthority(theme);
                        if(snapshot!=SFMItemstackPreviewRegistry.snapshot()) throw new IllegalArgumentException("Rule registry changed while picking");
                        if(target.originatingHost() instanceof SFMScreenMultiplexer workspace && target.originatingPanelId()!=null && !workspace.containsPanel(target.originatingPanelId()))
                            throw new IllegalArgumentException("The originating panel closed while picking");
                        var request=new SFMItemstackPreviewRuleAction.Request(theme,predicate,icon.requestedItem());
                        var draft=new SFMItemstackPreviewDraft(request,SFMClientThemeService.active(),snapshot,captured.map(SFMItemstackPreviewInspection::subject));
                        // The picker closes after this callback. Queue the next surface after that close.
                        Minecraft.getInstance().tell(()->{
                            if(!SFMClientThemeService.activeAuthority().filter(theme::equals).isPresent()) return;
                            if(target.originatingHost() instanceof SFMScreenMultiplexer workspace
                                    && (Minecraft.getInstance().screen!=workspace || target.originatingPanelId()!=null && !workspace.containsPanel(target.originatingPanelId()))) return;
                            var origin=SFMCommandPaletteScreen.createOriginContext();
                            OpenPanelAction.openPanel(origin,new SFMItemstackPreviewDraftPanel(draft),OpenPanelAction.Direction.FOCUSED);
                        });
                    } catch(RuntimeException failure) { command.getSource().sendFeedback(Component.literal("Rule draft unavailable: "+failure.getMessage())); }
                },()->command.getSource().sendFeedback(Component.literal("Rule picker cancelled; theme unchanged")));
                return OpenPanelAction.openPanel(target,picker,OpenPanelAction.Direction.FOCUSED);
            }
            var request=SFMItemstackPreviewRuleAction.request(command);
            if(!SFMItemstackPreviewRuleAction.itemAvailable(request.item())) throw new IllegalArgumentException("Unknown or empty item ID "+request.item());
            if(kind==Kind.COPY) {
                Minecraft.getInstance().keyboardHandler.setClipboard(request.command());command.getSource().sendFeedback(Component.literal("Copied complete rule command; not executed"));return 1;
            }
            var subject=SFMItemstackPreviewCaptures.forContext(target).map(capture->capture.inspection().subject());
            return OpenPanelAction.openPanel(target,new SFMItemstackPreviewDraftPanel(new SFMItemstackPreviewDraft(request,SFMClientThemeService.active(),snapshot,subject)),OpenPanelAction.Direction.FOCUSED);
        } catch(RuntimeException failure) { throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create(); }
    }
    public static List<SFMItemstackPreviewRules.Rule> resetRules(List<SFMItemstackPreviewRules.Rule> before,String id) {
        if(before.stream().noneMatch(rule->rule.id().equals(id) && rule.layer()==SFMItemstackPreviewRules.Layer.USER))
            throw new IllegalArgumentException("No such authored user rule; inherited rules cannot be reset here");
        return before.stream().filter(rule->!rule.id().equals(id)).toList();
    }
    public static void requireAuthority(SFMItemstackPreviewThemeTarget theme) {
        if(SFMClientThemeService.activeAuthority().filter(theme::equals).isEmpty()) throw new IllegalArgumentException("Theme authority/revision changed; inspect the entry again");
    }
    @Override public List<Continuation> contextualContinuations(SFMClientActionContext context) {
        if(kind==Kind.RESET) return List.of();
        return new SFMItemstackPreviewRuleAction().contextualContinuations(context);
    }
    @Override public boolean acceptsContinuation(String command,SFMClientActionContext context) {
        return context.originatingHostIsCurrent().getAsBoolean() && (command.equals(kind.prefix()) || command.startsWith(kind.prefix()+" "));
    }
    @Override public Optional<List<SFMPaletteCandidate>> argumentCandidates(String command,int start,int cursor,SFMClientActionContext context) {
        return kind==Kind.RESET ? Optional.empty() : new SFMItemstackPreviewRuleAction().argumentCandidates(command,start,cursor,context);
    }
    public static List<SFMActionChoice> choices(SFMItemstackPreviewInspection inspection) {
        if(inspection.theme().isEmpty()) return List.of();
        var result=new ArrayList<SFMActionChoice>();String theme=inspection.theme().orElseThrow().argument();
        SFMItemstackPreviewRuleAction.contextualPredicates(inspection.subject()).stream().limit(4).forEach(choice->result.add(
                SFMActionChoice.invoke(Kind.PICK.id(),theme+" "+choice.predicate().print(),PICK.getComponent().getString()+" · "+choice.description())));
        inspection.decision().candidates().stream().filter(rule->rule.layer()==SFMItemstackPreviewRules.Layer.USER && !rule.id().startsWith("sfm:legacy-icon/"))
                .forEach(rule->result.add(SFMActionChoice.invoke(Kind.RESET.id(),theme+" "+rule.id(),RESET.getComponent().getString()+" · "+rule.predicate().print())));
        return List.copyOf(result);
    }
}
