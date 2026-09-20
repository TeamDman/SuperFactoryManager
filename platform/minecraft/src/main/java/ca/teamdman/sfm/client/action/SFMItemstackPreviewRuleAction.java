package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.localization.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.*;
import com.mojang.brigadier.exceptions.*;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.concurrent.*;

/** Typed semantic command; completion is pure, and only explicit Execute schedules a guarded write. */
public final class SFMItemstackPreviewRuleAction implements SFMClientAction<SFMClientActionContext>,SFMClientActionCompletion {
    public static final ResourceLocation ID=new ResourceLocation("sfm:explorer/itemstack_preview_rule/add");
    public static final String PREFIX="sfm action invoke "+ID;
    public static final String THEME="theme_target", PREDICATE="predicate", ITEM="itemstack";
    @SFMLocalizationDatagen public static final LocalizationEntry TITLE=new LocalizationEntry("gui.sfm.preview_rule.add","Add ItemStack preview rule");
    @SFMLocalizationDatagen public static final LocalizationEntry DESCRIPTION=new LocalizationEntry("gui.sfm.preview_rule.add.description","Construct a typed predicate and save an icon rule to an explicit theme revision");
    @SFMLocalizationDatagen public static final LocalizationEntry GENERIC=new LocalizationEntry("gui.sfm.preview_rule.generic","Add ItemStack preview rule…");
    @SFMLocalizationDatagen public static final LocalizationEntry SCOPE=new LocalizationEntry("gui.sfm.preview_rule.scope","Add icon rule · %s");
    @SFMLocalizationDatagen public static final LocalizationEntry SAVING=new LocalizationEntry("gui.sfm.preview_rule.saving","Saving ItemStack preview rule…");
    @SFMLocalizationDatagen public static final LocalizationEntry SAVED=new LocalizationEntry("gui.sfm.preview_rule.saved","Saved ItemStack preview rule");
    private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(r->{Thread thread=new Thread(r,"sfm-theme-writer");thread.setDaemon(true);return thread;});
    public record Request(SFMItemstackPreviewThemeTarget theme,SFMItemstackPreviewExpression predicate,ResourceLocation item) {
        public String command() { return PREFIX+" "+theme.argument()+" "+predicate.print()+" "+item; }
        public SFMItemstackPreviewRules.Rule rule() { return SFMItemstackPreviewRules.Rule.user(predicate,new SFMItemIcon(item,SFMItemIcon.PAPER,"rule icon "+item)); }
    }
    public record PredicateChoice(SFMItemstackPreviewExpression predicate,String description) {}
    public static List<PredicateChoice> contextualPredicates(SFMItemstackPreviewSubject subject) {
        var choices=new ArrayList<PredicateChoice>();
        java.util.function.BiConsumer<SFMItemstackPreviewExpression,String> add=(predicate,label)->{
            var typed=subject.kind()==SFMItemstackPreviewSubject.Kind.FILE
                    ? SFMItemstackPreviewExpression.call("sfm:bool/and",SFMItemstackPreviewExpression.call("sfm:entry/is_file"),predicate) : predicate;
            choices.add(new PredicateChoice(typed,(subject.kind()==SFMItemstackPreviewSubject.Kind.FILE ? "files: " : "entries: ")+label));
        };
        subject.suffixes().stream().filter(SFMItemstackPreviewRuleAction::literalFits).limit(32).forEach(suffix->
                add.accept(SFMItemstackPreviewExpression.call("sfm:entry/has_suffix",
                        SFMItemstackPreviewExpression.literal(suffix.toLowerCase(Locale.ROOT))),
                        "suffix is "+SFMItemstackPreviewExpression.quote(suffix.toLowerCase(Locale.ROOT))));
        if(literalFits(subject.name())) add.accept(compare("equals","name",subject.name()),"name equals "+SFMItemstackPreviewExpression.quote(subject.name()));
        if(literalFits(subject.basename())) add.accept(compare("equals","basename",subject.basename()),"basename equals "+SFMItemstackPreviewExpression.quote(subject.basename()));
        // Small contextual menu, exhaustive prefixes remain reachable at the typed literal frontier.
        subject.prefixes().stream().limit(16).forEach(prefix->add.accept(compare("starts_with","name",prefix),"name starts with "+SFMItemstackPreviewExpression.quote(prefix)));
        return List.copyOf(choices);
    }
    private static boolean literalFits(String value) { return value.codePointCount(0,value.length())<=SFMItemstackPreviewExpression.MAX_LITERAL_CODEPOINTS; }
    private static SFMItemstackPreviewExpression compare(String operation,String field,String value) {
        return SFMItemstackPreviewExpression.call("sfm:string/"+operation,SFMItemstackPreviewExpression.call("sfm:entry/"+field),SFMItemstackPreviewExpression.literal(value));
    }
    @Override public Component title() { return TITLE.getComponent(); }
    @Override public Component description() { return DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context->context.originatingHostIsCurrent().getAsBoolean() ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource,String>argument(THEME,new ThemeArgument())
                .then(RequiredArgumentBuilder.<SFMClientActionSource,SFMItemstackPreviewExpression>argument(PREDICATE,new PredicateArgument())
                        .then(RequiredArgumentBuilder.<SFMClientActionSource,String>argument(ITEM,SFMCanonicalTokenArgument.token())
                                .suggests((context,builder)->{
                                    String query=builder.getRemainingLowerCase(); int count=0;
                                    for(var id:SFMWellKnownRegistries.ITEMS.keys()) if(id.toString().contains(query)) {
                                        builder.suggest(id.toString()); if(++count>=256) break;
                                    }
                                    return builder.buildFuture();
                                }).executes(this::invoke))));
    }
    public static Request request(CommandContext<SFMClientActionSource> context) {
        return new Request(SFMItemstackPreviewThemeTarget.parse(StringArgumentType.getString(context,THEME)),
                context.getArgument(PREDICATE,SFMItemstackPreviewExpression.class),new ResourceLocation(SFMCanonicalTokenArgument.get(context,ITEM)));
    }
    public static boolean itemAvailable(ResourceLocation id) {
        var item=SFMWellKnownRegistries.ITEMS.get(id);
        return item!=null && item!=Items.AIR && id.equals(SFMWellKnownRegistries.ITEMS.getId(item));
    }
    public static SFMItemstackPreviewRulePrompt.Contract promptContract() {
        var root=new SFMItemstackPreviewRuleAction().createCommandNode(ID).build();
        var theme=root.getChild(THEME);var predicate=theme.getChild(PREDICATE);var item=predicate.getChild(ITEM);
        return new SFMItemstackPreviewRulePrompt.Contract(root,Map.of(
                theme,new SFMItemstackPreviewRulePrompt.Argument(SFMItemstackPreviewRulePrompt.Role.LITERAL,
                        "JSON-quoted file URI followed by #sha256=<64 lowercase hex digits>","Exact active theme authority and captured file revision"),
                predicate,new SFMItemstackPreviewRulePrompt.Argument(SFMItemstackPreviewRulePrompt.Role.TYPED_EXPRESSION,
                        "BOOLEAN prefix expression from the exported operator signatures","The set of structured entries receiving this user rule"),
                item,new SFMItemstackPreviewRulePrompt.Argument(SFMItemstackPreviewRulePrompt.Role.REGISTRY_VALUE,
                        "One unquoted namespace:path item registry ID; allowed characters [a-z0-9_.-]+:[a-z0-9_./-]+",
                        "Must resolve to a registered non-air item in the running game; normal completion/picker supplies values, export never enumerates them")));
    }
    @Override public int execute(SFMClientActionContext target,CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        Request request;
        try {
            request=request(context);
            validateRequest(request,SFMItemstackPreviewRuleAction::itemAvailable,SFMClientThemeService.activeAuthority());
        } catch(RuntimeException failure) { throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).create(); }
        submitConfirmed(request.theme(),before->{
            var after=new ArrayList<>(before); after.removeIf(rule->rule.id().equals(request.rule().id())); after.add(request.rule()); return after;
        },context.getSource()::sendFeedback);
        return 1;
    }
    public static void submitConfirmed(SFMItemstackPreviewThemeTarget theme,
            java.util.function.UnaryOperator<List<SFMItemstackPreviewRules.Rule>> mutation,
            java.util.function.Consumer<Component> feedback) {
        feedback.accept(SAVING.getComponent());
        // Execute has already confirmed this exact transaction. Closing its transient palette is normal,
        // not a cancellation signal; the worker only observes immutable data and the theme revision.
        CompletableFuture.supplyAsync(()->SFMClientThemeService.mutatePreviewRules(theme,mutation,()->true),WRITER)
                .whenComplete((result,failure)->Minecraft.getInstance().execute(()->{
            feedback.accept(failure==null && result.valid() ? SAVED.getComponent()
                    : Component.literal("Preview rule not saved: "+(failure!=null ? failure.getMessage() : String.join("; ",result.diagnostics()))));
        }));
    }
    public static void validateRequest(Request request,java.util.function.Predicate<ResourceLocation> itemAvailable,
            Optional<SFMItemstackPreviewThemeTarget> authority) {
        if(!itemAvailable.test(request.item())) throw new IllegalArgumentException("Unknown or empty item registry ID "+request.item());
        if(authority.filter(request.theme()::equals).isEmpty()) throw new IllegalArgumentException("Theme target is stale or not the active authority; reopen icon actions or reload the theme");
        if(request.predicate().validate(SFMItemstackPreviewRegistry.snapshot().operators())!=SFMItemstackPreviewOperators.Type.BOOLEAN)
            throw new IllegalArgumentException("Rule predicate must be Boolean");
    }
    @Override public List<Continuation> contextualContinuations(SFMClientActionContext context) {
        var capture=SFMItemstackPreviewCaptures.forContext(context);
        var theme=capture.isPresent() ? capture.orElseThrow().inspection().theme() : SFMClientThemeService.activeAuthority();
        if(theme.isEmpty()) return List.of();
        var answer=new ArrayList<Continuation>(); answer.add(new Continuation(theme.get().argument(),GENERIC.getComponent().getString()));
        capture.ifPresent(value->contextualPredicates(value.inspection().subject()).forEach(choice->answer.add(new Continuation(
                theme.get().argument()+" "+choice.predicate().print(),SCOPE.getComponent(choice.description()).getString()))));
        return answer;
    }
    @Override public boolean acceptsContinuation(String command,SFMClientActionContext context) {
        return context.originatingHostIsCurrent().getAsBoolean() && (command.equals(PREFIX) || command.startsWith(PREFIX+" "));
    }
    @Override public Optional<List<SFMPaletteCandidate>> argumentCandidates(String command,int start,int cursor,SFMClientActionContext context) {
        if(command.length()>16384) return Optional.of(List.of());
        StringReader reader=new StringReader(command);reader.setCursor(start);
        while(reader.canRead() && Character.isWhitespace(reader.peek())) reader.skip();
        int themeStart=reader.getCursor(),themeEnd;
        try { new ThemeArgument().parse(reader);themeEnd=reader.getCursor(); }
        catch(CommandSyntaxException partial) { themeEnd=command.length(); }
        if(cursor<=themeEnd || themeStart==command.length()) {
            var capture=SFMItemstackPreviewCaptures.forContext(context);
            var theme=capture.isPresent() ? capture.orElseThrow().inspection().theme() : SFMClientThemeService.activeAuthority();
            var result=new ArrayList<SFMPaletteCandidate>();
            StringRange range=StringRange.between(themeStart,themeEnd);
            result.add(SFMPaletteCandidate.usageHint(range,"theme_target : exact file URI + #sha256 revision",SFMPaletteCandidate.Origin.SMART_USAGE,ID,THEME));
            theme.ifPresent(value->result.add(candidate(range,value.argument(),"Theme · "+value.path().getFileName(),THEME)));
            return Optional.of(result);
        }
        reader.setCursor(themeEnd); while(reader.canRead() && Character.isWhitespace(reader.peek())) reader.skip();
        var subject=SFMItemstackPreviewCaptures.forContext(context).map(value->value.inspection().subject());
        var frontier=SFMItemstackPreviewCompletion.complete(command,reader.getCursor(),cursor,SFMItemstackPreviewRegistry.snapshot().operators(),subject);
        return frontier.map(value->{
            var range=StringRange.between(value.start(),value.end()); var result=new ArrayList<SFMPaletteCandidate>();
            result.add(SFMPaletteCandidate.usageHint(range,value.expected(),SFMPaletteCandidate.Origin.SMART_USAGE,ID,PREDICATE));
            value.options().forEach(option->result.add(candidate(range,option.replacement(),option.label(),PREDICATE)));return List.copyOf(result);
        });
    }
    private static SFMPaletteCandidate candidate(StringRange range,String replacement,String label,String frontier) {
        return SFMPaletteCandidate.activatable(new Suggestion(range,replacement),label,SFMPaletteCandidate.Kind.ARGUMENT_VALUE,
                SFMPaletteCandidate.Origin.BRIGADIER,ID,frontier,-1,null);
    }
    public static final class PredicateArgument implements ArgumentType<SFMItemstackPreviewExpression> {
        @Override public SFMItemstackPreviewExpression parse(StringReader reader) throws CommandSyntaxException {
            try {
                var parsed=SFMItemstackPreviewExpression.parse(reader.getString(),reader.getCursor(),SFMItemstackPreviewOperators.Type.BOOLEAN,SFMItemstackPreviewRegistry.snapshot().operators());
                reader.setCursor(parsed.end()); return parsed.expression();
            } catch(RuntimeException failure) { throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).createWithContext(reader); }
        }
        @Override public Collection<String> getExamples() { return List.of("sfm:entry/is_file","sfm:string/ends_with sfm:entry/name \".json\""); }
    }
    /** Same JSON literal contract as predicate constants, not Brigadier's narrower escaping. */
    public static final class ThemeArgument implements ArgumentType<String> {
        @Override public String parse(StringReader reader) throws CommandSyntaxException {
            try {
                if(!reader.canRead() || reader.peek()!='"') throw new IllegalArgumentException("Expected a JSON-quoted theme target");
                var parsed=SFMItemstackPreviewExpression.parse(reader.getString(),reader.getCursor(),
                        SFMItemstackPreviewOperators.Type.STRING,SFMItemstackPreviewRegistry.snapshot().operators());
                String value=parsed.expression().literal();
                SFMItemstackPreviewThemeTarget.parse(value);
                reader.setCursor(parsed.end()); return value;
            } catch(RuntimeException failure) { throw new SimpleCommandExceptionType(Component.literal(failure.getMessage())).createWithContext(reader); }
        }
    }
}
