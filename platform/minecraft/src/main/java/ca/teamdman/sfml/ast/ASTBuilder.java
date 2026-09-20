package ca.teamdman.sfml.ast;

import ca.teamdman.langs.SFMLBaseVisitor;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValuePattern;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;

public class ASTBuilder extends SFMLBaseVisitor<ASTNode> {
    /// Used for linting and for label gun pull behaviour
    private final Set<Label> USED_LABELS = new HashSet<>();

    /// Used for linting and for energy-specific timer minimum interval restrictions
    private final Set<ResourceIdentifier<?, ?, ?>> USED_RESOURCES = new HashSet<>();

    /// Used for program editor context actions; ctrl+space on a token
    private final List<Pair<WeakReference<ASTNode>, ParserRuleContext>> AST_NODE_CONTEXTS = new LinkedList<>();

    private final Map<String, SFMValuePattern> PATTERN_DEFINITIONS = new LinkedHashMap<>();

    private final Map<String, String> PLAYER_DEFINITIONS = new LinkedHashMap<>();

    // Strong references keep declaration nodes available to source mapping.
    private final List<ASTNode> DECLARATION_NODES = new ArrayList<>();

    /// @return hierarchy of nodes; e.g., Program > Trigger > Block > IOStatement > LabelAccess > Label
    public List<Pair<ASTNode, ParserRuleContext>> getNodesUnderCursor(int cursorPos) {

        return AST_NODE_CONTEXTS
                .stream()
                .filter(pair -> pair.getSecond() != null)
                .filter(pair -> pair.getSecond().start.getStartIndex() <= cursorPos
                                && pair.getSecond().stop.getStopIndex() >= cursorPos)
                .map(pair -> Pair.of(pair.getFirst().get(), pair.getSecond()))
                .filter(pair -> pair.getFirst() != null)
                .collect(Collectors.toList());
    }

    /// @return {@link #AST_NODE_CONTEXTS}.get({@code index})
    public Optional<ASTNode> getNodeAtIndex(int index) {

        if (index < 0 || index >= AST_NODE_CONTEXTS.size()) return Optional.empty();
        WeakReference<ASTNode> nodeRef = AST_NODE_CONTEXTS.get(index).getFirst();
        return Optional.ofNullable(nodeRef.get());
    }

    /// Used by {@link ForgetStatement} to track the provenance of dynamically generated {@link InputStatement} instances.
    /// We should use weak references for these dynamically generated nodes to let them get garbage collected; <a href="https://github.com/TeamDman/SuperFactoryManager/issues/405">#405</a>.
    public void setLocationFromOtherNode(
            ASTNode node,
            ASTNode otherNode
    ) {

        trackNode(node, AST_NODE_CONTEXTS.get(getIndexForNode(otherNode)).getSecond());
    }

    /// Used for client-server collaboration to make context menu actions work.
    /// The client calls {@link #getNodesUnderCursor(int)} and then {@link ca.teamdman.sfm.client.ProgramTokenContextActions#getContextAction(String, int)} to build the pick list.
    /// When a context action is invoked, a packet is sent to the server containing the index of the node so that the
    /// packet handler can do its work with the server's instance of the {@link ASTNode}.
    public int getIndexForNode(ASTNode node) {

        for (int i = 0; i < AST_NODE_CONTEXTS.size(); i++) {
            Pair<WeakReference<ASTNode>, ParserRuleContext> pair = AST_NODE_CONTEXTS.get(i);
            // Intentional reference equality check, don't forget the `.get()`!
            if (pair.getFirst().get() == node) {
                return i;
            }
        }
        return -1;
    }

    public Optional<ParserRuleContext> getContextForNode(ASTNode node) {

        return AST_NODE_CONTEXTS
                .stream()
                .filter(pair -> pair.getFirst().get() == node)
                .map(Pair::getSecond)
                .findFirst();
    }

    public String getLineColumnForNode(ASTNode node) {
        // todo: return TranslatableContents
        return getContextForNode(node)
                .map(ctx -> "Line " + ctx.start.getLine() + ", Column " + ctx.start.getCharPositionInLine())
                .orElse("Unknown location");
    }

    @Override
    public StringHolder visitName(@Nullable SFMLParser.NameContext ctx) {

        if (ctx == null) return new StringHolder("");
        StringHolder name = visitString(ctx.string());
        trackNode(new ProgramName(name), ctx);
        return name;
    }

    @Override
    public ASTNode visitResource(SFMLParser.ResourceContext ctx) {

        var str = ctx
                .children
                .stream()
                .map(ParseTree::getText)
                .collect(Collectors.joining())
                .replaceAll("::", ":*:")
                .replaceAll(":$", ":*")
                .toLowerCase(Locale.ROOT);

        str = Arrays.stream(str.split(":", -1))
                .map(SFMLLiteralGlob::toRegex)
                .collect(Collectors.joining(":"));

        var rtn = ResourceIdentifier.fromString(str);
        USED_RESOURCES.add(rtn);
        rtn.assertValid();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public ResourceIdentifier<?, ?, ?> visitStringResource(SFMLParser.StringResourceContext ctx) {

        var rtn = ResourceIdentifier.fromString(visitString(ctx.string()).value());
        USED_RESOURCES.add(rtn);
        rtn.assertValid();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public StringHolder visitString(SFMLParser.StringContext ctx) {

        var content = ctx.getText();
        String innerContent = content.substring(1, content.length() - 1).replaceAll("\\\\\"", "\"");
        StringHolder str = new StringHolder(innerContent);
        trackNode(str, ctx);
        return str;
    }

    @Override
    public Label visitRawLabel(SFMLParser.RawLabelContext ctx) {

        var label = new Label(ctx.getText());
        if (label.name().length() > Program.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label name cannot be longer than "
                    + Program.MAX_LABEL_LENGTH
                    + " characters."
            );
        }
        USED_LABELS.add(label);
        trackNode(label, ctx);
        return label;
    }

    @Override
    public Label visitStringLabel(SFMLParser.StringLabelContext ctx) {

        var label = new Label(visitString(ctx.string()).value());
        if (label.name().length() > Program.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label name cannot be longer than "
                    + Program.MAX_LABEL_LENGTH
                    + " characters."
            );
        }
        USED_LABELS.add(label);
        trackNode(label, ctx);
        return label;
    }

    @Override
    public Program visitProgram(SFMLParser.ProgramContext ctx) {

        if (SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.disableProgramExecution)) {
            throw new AssertionError("Program execution is disabled via config");
        }
        ProgramExecutionSideDeclaration executionSideDeclaration = ctx.executionSideDeclaration() == null
                                                                 ? null
                                                                 : visitExecutionSideDeclaration(ctx.executionSideDeclaration());
        var name = visitName(ctx.name());
        ctx.declaration().forEach(this::visit);
        var triggers = ctx
                .trigger()
                .stream()
                .map(this::visit)
                .map(Trigger.class::cast)
                .collect(Collectors.toList());
        var labels = USED_LABELS
                .stream()
                .map(Label::name)
                .collect(Collectors.toSet());
        Program program = new Program(
                this,
                name.value(),
                triggers,
                labels,
                USED_RESOURCES,
                new ProgramDefinitions(PATTERN_DEFINITIONS, PLAYER_DEFINITIONS),
                executionSideDeclaration
        );
        trackNode(program, ctx);
        return program;
    }

    @Override
    public ProgramExecutionSideDeclaration visitExecutionSideDeclaration(
            SFMLParser.ExecutionSideDeclarationContext ctx
    ) {
        ProgramExecutionSideDeclaration declaration = new ProgramExecutionSideDeclaration(
                ctx.CLIENT() != null ? ProgramExecutionSide.CLIENT : ProgramExecutionSide.SERVER
        );
        trackNode(declaration, ctx);
        return declaration;
    }

    @Override
    public ASTNode visitPlayerDeclaration(SFMLParser.PlayerDeclarationContext ctx) {
        String alias = ctx.identifier(0).getText();
        String playerName = ctx.identifier(1).getText();
        String normalized = alias.toLowerCase(Locale.ROOT);
        if (PLAYER_DEFINITIONS.putIfAbsent(normalized, playerName) != null) {
            throw new IllegalArgumentException("Duplicate player alias: " + alias);
        }
        ProgramPlayerDeclaration declaration = new ProgramPlayerDeclaration(alias, playerName);
        DECLARATION_NODES.add(declaration);
        trackNode(declaration, ctx);
        return declaration;
    }

    @Override
    public ASTNode visitPatternDeclaration(SFMLParser.PatternDeclarationContext ctx) {
        String alias = ctx.identifier().getText();
        SFMValuePattern pattern = ((PatternHolder) visit(ctx.valuePattern())).pattern();
        String normalized = alias.toLowerCase(Locale.ROOT);
        if (PATTERN_DEFINITIONS.putIfAbsent(normalized, pattern) != null) {
            throw new IllegalArgumentException("Duplicate pattern alias: " + alias);
        }
        ProgramPatternDeclaration declaration = new ProgramPatternDeclaration(alias, pattern);
        DECLARATION_NODES.add(declaration);
        trackNode(declaration, ctx);
        return declaration;
    }

    @Override
    public ASTNode visitGuidValuePattern(SFMLParser.GuidValuePatternContext ctx) {
        return new PatternHolder(SFMValuePattern.GUID);
    }

    @Override
    public ASTNode visitStringValuePattern(SFMLParser.StringValuePatternContext ctx) {
        return new PatternHolder(SFMValuePattern.STRING);
    }

    @Override
    public ASTNode visitLiteralValuePattern(SFMLParser.LiteralValuePatternContext ctx) {
        return new PatternHolder(new SFMValuePattern.LiteralPattern(SFMValue.of(visitString(ctx.string()).value())));
    }

    @Override
    public ASTNode visitAliasValuePattern(SFMLParser.AliasValuePatternContext ctx) {
        return new PatternHolder(resolvePattern(ctx.identifier().getText()));
    }

    @Override
    public ASTNode visitObjectValuePattern(SFMLParser.ObjectValuePatternContext ctx) {
        LinkedHashMap<String, SFMValuePattern> fields = new LinkedHashMap<>();
        for (SFMLParser.PatternFieldContext fieldContext : ctx.patternField()) {
            PatternFieldDefinition field = (PatternFieldDefinition) visit(fieldContext);
            if (fields.putIfAbsent(field.name(), field.pattern()) != null) {
                throw new IllegalArgumentException("Duplicate object pattern field: " + field.name());
            }
        }
        return new PatternHolder(new SFMValuePattern.ObjectPattern(fields));
    }

    @Override
    public ASTNode visitLiteralPatternField(SFMLParser.LiteralPatternFieldContext ctx) {
        PatternFieldDefinition field = new PatternFieldDefinition(
                ctx.identifier().getText(),
                new SFMValuePattern.LiteralPattern(SFMValue.of(visitString(ctx.string()).value()))
        );
        trackNode(field, ctx);
        return field;
    }

    @Override
    public ASTNode visitLikePatternField(SFMLParser.LikePatternFieldContext ctx) {
        String patternName = ctx.identifier(1).getText();
        PatternFieldDefinition field = new PatternFieldDefinition(
                ctx.identifier(0).getText(),
                resolvePattern(patternName)
        );
        trackNode(field, ctx);
        return field;
    }

    @Override
    public ASTNode visitAliasPatternField(SFMLParser.AliasPatternFieldContext ctx) {
        String name = ctx.identifier().getText();
        PatternFieldDefinition field = new PatternFieldDefinition(name, resolvePattern(name));
        trackNode(field, ctx);
        return field;
    }

    @Override
    public ASTNode visitTimerTrigger(SFMLParser.TimerTriggerContext ctx) {
        // create timer trigger
        var time = (Interval) visit(ctx.interval());
        var block = visitBlock(ctx.block());
        TimerTrigger timerTrigger = new TimerTrigger(time, block);

        // get default min interval
        int minInterval = timerTrigger.usesOnlyForgeEnergyResourceIO()
                          ? SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO)
                          : SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicks);

        // validate interval
        if (time.ticks() < minInterval) {
            throw new IllegalArgumentException("Minimum trigger interval is " + minInterval + " ticks.");
        }

        trackNode(timerTrigger, ctx);
        return timerTrigger;
    }

    @Override
    public ASTNode visitFrameTrigger(SFMLParser.FrameTriggerContext ctx) {
        List<Label> labels = ctx.frameLabels().label().stream()
                .map(this::visit)
                .map(Label.class::cast)
                .toList();
        FrameTrigger trigger = new FrameTrigger(labels, ctx.identifier().getText(), visitBlock(ctx.block()));
        trackNode(trigger, ctx);
        return trigger;
    }

    @Override
    public ASTNode visitRenderImageStatement(SFMLParser.RenderImageStatementContext ctx) {
        String image = visitString(ctx.string()).value();
        RenderImageStatement statement = new RenderImageStatement(
                new ResourceLocation(image), ctx.identifier().getText()
        );
        trackNode(statement, ctx);
        return statement;
    }

    @Override
    public BoolExpr visitBooleanFrameModulo(SFMLParser.BooleanFrameModuloContext ctx) {
        BoolExpr condition = new BoolFrameModulo(
                visitNumber(ctx.number(0)).value(),
                visitComparisonOp(ctx.comparisonOp()),
                visitNumber(ctx.number(1)).value()
        );
        trackNode(condition, ctx);
        return condition;
    }

    @Override
    public ASTNode visitBooleanRedstone(SFMLParser.BooleanRedstoneContext ctx) {

        ComparisonOperator comp = ComparisonOperator.GREATER_OR_EQUAL;
        Number num = new Number(0);
        if (ctx.comparisonOp() != null && ctx.number() != null) {
            comp = visitComparisonOp(ctx.comparisonOp());
            num = visitNumber(ctx.number());
        }
        if (num.value() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Redstone signal strength cannot be greater than " + Integer.MAX_VALUE);
        }
        BoolExpr boolExpr = new BoolRedstone(comp, (int) num.value());
        trackNode(boolExpr, ctx);
        return boolExpr;
    }

    @Override
    public ASTNode visitPulseTrigger(SFMLParser.PulseTriggerContext ctx) {

        var block = visitBlock(ctx.block());
        RedstoneTrigger redstoneTrigger = new RedstoneTrigger(block);
        trackNode(redstoneTrigger, ctx);
        return redstoneTrigger;
    }

    @Override
    public Number visitNumber(SFMLParser.NumberContext ctx) {

        Number number = new Number(Long.parseLong(ctx.getText()));
        trackNode(number, ctx);
        return number;
    }

    @Override
    public ASTNode visitIntervalSpace(SFMLParser.IntervalSpaceContext ctx) {
        Interval interval = buildInterval(
                ctx.period == null ? null : ctx.period.getText(),
                ctx.legacyOffset == null ? null : ctx.legacyOffset.getText(),
                ctx.newOffset == null ? null : ctx.newOffset.getText(),
                ctx.unit,
                ctx.offsetUnit,
                ctx.GLOBAL() == null ? Interval.IntervalAlignment.LOCAL : Interval.IntervalAlignment.GLOBAL
        );
        trackNode(interval, ctx);
        return interval;
    }

    @Override
    public ASTNode visitIntervalNoSpace(SFMLParser.IntervalNoSpaceContext ctx) {
        String suffixedPeriod = ctx.period.getText();
        Interval interval = buildInterval(
                suffixedPeriod.substring(0, suffixedPeriod.length() - 1),
                ctx.legacyOffset == null ? null : ctx.legacyOffset.getText(),
                ctx.newOffset == null ? null : ctx.newOffset.getText(),
                ctx.unit,
                ctx.offsetUnit,
                Interval.IntervalAlignment.GLOBAL
        );
        trackNode(interval, ctx);
        return interval;
    }

    private static Interval buildInterval(
            @Nullable String periodText,
            @Nullable String legacyOffsetText,
            @Nullable String newOffsetText,
            SFMLParser.TimeUnitContext unit,
            @Nullable SFMLParser.TimeUnitContext offsetUnit,
            Interval.IntervalAlignment alignment
    ) {
        if (legacyOffsetText != null && newOffsetText != null) {
            throw new IllegalArgumentException("Use either legacy + offset or OFFSET BY, not both");
        }
        int ticks = intervalTicks(periodText == null ? "1" : periodText, unit);
        int offset = legacyOffsetText != null
                     ? intervalTicks(legacyOffsetText, unit)
                     : newOffsetText != null
                       ? intervalTicks(newOffsetText, Objects.requireNonNull(offsetUnit))
                       : 0;
        return new Interval(ticks, alignment, offset, legacyOffsetText != null);
    }

    private static int intervalTicks(String number, SFMLParser.TimeUnitContext unit) {
        int value = Integer.parseInt(number);
        if (unit.SECOND() == null && unit.SECONDS() == null) {
            return value;
        }
        try {
            return Math.multiplyExact(value, 20);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Interval exceeds supported tick count", overflow);
        }
    }

    @Override
    public InputStatement visitInputStatement(SFMLParser.InputStatementContext ctx) {

        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var matchers = visitInputResourceLimits(ctx.inputResourceLimits());
        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        ProgramInputSelection selection = ctx.inputSelection() == null
                                          ? ProgramInputSelection.ANY
                                          : (ProgramInputSelection) visit(ctx.inputSelection());
        String bindingName = ctx.inputBinding() == null
                             ? null
                             : ctx.inputBinding().identifier().getText();
        InputStatement inputStatement = new InputStatement(
                labelAccess,
                matchers.withExclusions(exclusions),
                each,
                selection,
                bindingName
        );
        trackNode(inputStatement, ctx);
        return inputStatement;
    }

    @Override
    public ASTNode visitCapabilityInputSelection(SFMLParser.CapabilityInputSelectionContext ctx) {
        ProgramInputSelection selection = new ProgramInputSelection.CapabilitySelection(ctx.qualifiedId().getText());
        trackNode(selection, ctx);
        return selection;
    }

    @Override
    public ASTNode visitPatternInputSelection(SFMLParser.PatternInputSelectionContext ctx) {
        String alias = ctx.identifier().getText();
        ProgramInputSelection selection = new ProgramInputSelection.PatternSelection(alias, resolvePattern(alias));
        trackNode(selection, ctx);
        return selection;
    }

    @Override
    public ASTNode visitLetValueStatement(SFMLParser.LetValueStatementContext ctx) {
        LetStatement statement = new LetStatement(
                ctx.identifier().getText(),
                (ProgramValueExpression) visit(ctx.valueExpression())
        );
        trackNode(statement, ctx);
        return statement;
    }

    @Override
    public ASTNode visitInvokeTextValueExpression(SFMLParser.InvokeTextValueExpressionContext ctx) {
        TextReadValueExpression expression = new TextReadValueExpression(
                invokeActionId(ctx.invokeActionId()),
                ctx.identifier().getText()
        );
        trackNode(expression, ctx);
        return expression;
    }

    @Override
    public ASTNode visitClientJsonValueExpression(SFMLParser.ClientJsonValueExpressionContext ctx) {
        var expression = new ClientValueExpression.JsonLiteral(
                ca.teamdman.sfm.common.value.SFMValueSchema.decodeActionJson(visitString(ctx.string()).value()));
        trackNode(expression, ctx);
        return expression;
    }

    @Override
    public ASTNode visitClientInvokeValueExpression(SFMLParser.ClientInvokeValueExpressionContext ctx) {
        var expression = new ClientValueExpression.Invoke(new ResourceLocation(invokeActionId(ctx.invokeActionId())),
                ctx.identifier().getText());
        trackNode(expression, ctx);
        return expression;
    }

    private String invokeActionId(SFMLParser.InvokeActionIdContext ctx) {
        if (ctx.qualifiedId() != null) return ctx.qualifiedId().getText();
        String literal = visitString(ctx.string()).value();
        int separator = literal.indexOf(':');
        if (separator <= 0 || separator == literal.length() - 1) {
            throw new IllegalArgumentException("INVOKE action IDs require an explicit namespace and path");
        }
        return new ResourceLocation(literal).toString();
    }

    @Override
    public ASTNode visitClientFieldValueExpression(SFMLParser.ClientFieldValueExpressionContext ctx) {
        var expression = new ClientValueExpression.Field(visitString(ctx.string()).value(), ctx.identifier().getText());
        trackNode(expression, ctx);
        return expression;
    }

    @Override
    public ASTNode visitBooleanClientValueEquals(SFMLParser.BooleanClientValueEqualsContext ctx) {
        var condition = new BoolClientValueEquals(ctx.identifier().getText(),
                ca.teamdman.sfm.common.value.SFMValueSchema.decodeActionJson(visitString(ctx.string()).value()));
        trackNode(condition, ctx);
        return condition;
    }

    @Override
    public ASTNode visitObjectConstructionValueExpression(SFMLParser.ObjectConstructionValueExpressionContext ctx) {
        String alias = ctx.identifier().getText();
        SFMValuePattern pattern = resolvePattern(alias);
        if (!(pattern instanceof SFMValuePattern.ObjectPattern objectPattern)) {
            throw new IllegalArgumentException("Object construction requires an object pattern alias: " + alias);
        }
        LinkedHashMap<String, ObjectFieldValueExpression> fields = new LinkedHashMap<>();
        for (SFMLParser.ConstructionFieldContext fieldContext : ctx.constructionField()) {
            String fieldName = fieldContext.identifier().getText();
            ObjectFieldValueExpression value = (ObjectFieldValueExpression) visit(fieldContext.fieldValueExpression());
            if (fields.putIfAbsent(fieldName, value) != null) {
                throw new IllegalArgumentException("Duplicate object construction field: " + fieldName);
            }
        }
        ObjectConstructionValueExpression expression = new ObjectConstructionValueExpression(alias, objectPattern, fields);
        trackNode(expression, ctx);
        return expression;
    }

    @Override
    public ASTNode visitNewGuidFieldValue(SFMLParser.NewGuidFieldValueContext ctx) {
        ObjectFieldValueExpression expression = new ObjectFieldValueExpression.NewGuid();
        trackNode(expression, ctx);
        return expression;
    }

    @Override
    public ASTNode visitLiteralFieldValue(SFMLParser.LiteralFieldValueContext ctx) {
        ObjectFieldValueExpression expression = new ObjectFieldValueExpression.Literal(
                SFMValue.of(visitString(ctx.string()).value())
        );
        trackNode(expression, ctx);
        return expression;
    }

    @Override
    public ASTNode visitVariableFieldValue(SFMLParser.VariableFieldValueContext ctx) {
        ObjectFieldValueExpression expression = new ObjectFieldValueExpression.Variable(ctx.identifier().getText());
        trackNode(expression, ctx);
        return expression;
    }

    @Override
    public ASTNode visitCreateStatement(SFMLParser.CreateStatementContext ctx) {
        String carrierId = ctx.qualifiedId().getText();
        CreateInputStatement statement = new CreateInputStatement(carrierId, ctx.identifier().getText());
        ResourceIdentifier<?, ?, ?> packetResource = ResourceIdentifier.fromString(carrierId);
        packetResource.assertValid();
        USED_RESOURCES.add(packetResource);
        trackNode(statement, ctx);
        return statement;
    }

    @Override
    public ASTNode visitBroadcastStatement(SFMLParser.BroadcastStatementContext ctx) {
        String playerAlias = ctx.identifier().getText();
        if (!PLAYER_DEFINITIONS.containsKey(playerAlias.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unknown player alias: " + playerAlias);
        }
        BroadcastStatement statement = new BroadcastStatement(
                playerAlias,
                ctx.qualifiedId() == null ? null : new net.minecraft.resources.ResourceLocation(ctx.qualifiedId().getText())
        );
        trackNode(statement, ctx);
        return statement;
    }

    @Override
    public OutputStatement visitOutputStatement(SFMLParser.OutputStatementContext ctx) {

        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var matchers = visitOutputResourceLimits(ctx.outputResourceLimits());
        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        boolean emptySlotsOnly = ctx.emptyslots() != null;
        OutputStatement outputStatement = new OutputStatement(
                labelAccess,
                matchers.withExclusions(exclusions),
                each,
                emptySlotsOnly
        );
        trackNode(outputStatement, ctx);
        return outputStatement;
    }

    @Override
    public LabelAccess visitLabelAccess(SFMLParser.LabelAccessContext ctx) {

        var directionQualifierCtx = ctx.sidequalifier();
        SideQualifier sideQualifier;
        if (directionQualifierCtx == null) {
            sideQualifier = SideQualifier.NULL;
        } else {
            sideQualifier = (SideQualifier) visit(directionQualifierCtx);
        }
        LabelAccess labelAccess = new LabelAccess(
                ctx.label().stream().map(this::visit).map(Label.class::cast).collect(Collectors.toList()),
                sideQualifier,
                visitSlotqualifier(ctx.slotqualifier()),
                visitRoundrobin(ctx.roundrobin())
        );
        trackNode(labelAccess, ctx);
        return labelAccess;
    }

    @Override
    public RoundRobin visitRoundrobin(@Nullable SFMLParser.RoundrobinContext ctx) {

        if (ctx == null) return RoundRobin.disabled();
        RoundRobin rtn = ctx.BLOCK() != null
                         ? new RoundRobin(RoundRobin.Behaviour.BY_BLOCK)
                         : new RoundRobin(RoundRobin.Behaviour.BY_LABEL);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public IfStatement visitIfStatement(SFMLParser.IfStatementContext ctx) {

        var conditions = ctx
                .boolexpr()
                .stream()
                .map(this::visit)
                .map(BoolExpr.class::cast)
                .collect(Collectors.toCollection(ArrayDeque::new));
        var blocks = ctx.block().stream()
                .map(this::visitBlock)
                .collect(Collectors.toCollection(ArrayDeque::new));

        IfStatement nestedStatement;
        if (conditions.size() < blocks.size()) {
            Block elseBlock = blocks.removeLast();
            Block ifBlock = blocks.removeLast();
            nestedStatement = new IfStatement(
                    conditions.removeLast(),
                    ifBlock,
                    elseBlock
            );
        } else {
            nestedStatement = new IfStatement(
                    conditions.removeLast(),
                    blocks.removeLast(),
                    new Block(List.of())
            );
        }
        while (!blocks.isEmpty()) {
            nestedStatement = new IfStatement(
                    conditions.removeLast(),
                    blocks.removeLast(),
                    new Block(List.of(nestedStatement))
            );
        }
        if (!conditions.isEmpty()) {
            throw new IllegalStateException("If statement construction failed to consume all conditions");
        }

        trackNode(nestedStatement, ctx);
        return nestedStatement;
    }

    @Override
    public BoolExpr visitBooleanHas(SFMLParser.BooleanHasContext ctx) {

        var setOperator = visitSetOp(ctx.setOp());
        var labelAccess = visitLabelAccess(ctx.labelAccess());
        ComparisonOperator comparisonOperator = visitComparisonOp(ctx.comparisonOp());
        Number num = visitNumber(ctx.number());
        ResourceIdSet resourceIdSet;
        if (ctx.resourceIdDisjunction() == null) {
            resourceIdSet = ResourceIdSet.MATCH_ALL;
        } else {
            resourceIdSet = visitResourceIdDisjunction(ctx.resourceIdDisjunction());
        }
        With with;
        if (ctx.with() == null) {
            with = With.ALWAYS_TRUE;
        } else {
            with = (With) visit(ctx.with());
        }
        ResourceIdSet except;
        if (ctx.resourceIdList() == null) {
            except = ResourceIdSet.EMPTY;
        } else {
            except = visitResourceIdList(ctx.resourceIdList());
        }
        BoolHas rtn = new BoolHas(
                setOperator,
                labelAccess,
                comparisonOperator,
                num.value(),
                resourceIdSet,
                with,
                except
        );
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public SetOperator visitSetOp(@Nullable SFMLParser.SetOpContext ctx) {

        if (ctx == null) return SetOperator.OVERALL;
        SetOperator from = SetOperator.from(ctx.getText());
        trackNode(from, ctx);
        return from;
    }

    @Override
    public ComparisonOperator visitComparisonOp(SFMLParser.ComparisonOpContext ctx) {

        ComparisonOperator from = ComparisonOperator.from(ctx.getText());
        trackNode(from, ctx);
        return from;
    }

    @Override
    public BoolExpr visitBooleanTrue(SFMLParser.BooleanTrueContext ctx) {

        BoolExpr rtn = new BoolTrue();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanFalse(SFMLParser.BooleanFalseContext ctx) {

        BoolExpr rtn = new BoolFalse();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanParen(SFMLParser.BooleanParenContext ctx) {

        BoolExpr rtn = new BoolParen((BoolExpr) visit(ctx.boolexpr()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanNegation(SFMLParser.BooleanNegationContext ctx) {

        BoolExpr rtn = new BoolNegation((BoolExpr) visit(ctx.boolexpr()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanConjunction(SFMLParser.BooleanConjunctionContext ctx) {

        var left = (BoolExpr) visit(ctx.boolexpr(0));
        var right = (BoolExpr) visit(ctx.boolexpr(1));
        BoolExpr rtn = new BoolConjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanDisjunction(SFMLParser.BooleanDisjunctionContext ctx) {

        var left = (BoolExpr) visit(ctx.boolexpr(0));
        var right = (BoolExpr) visit(ctx.boolexpr(1));
        BoolExpr rtn = new BoolDisjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public Limit visitQuantityRetentionLimit(SFMLParser.QuantityRetentionLimitContext ctx) {

        var quantity = visitQuantity(ctx.quantity());
        var retain = visitRetention(ctx.retention());
        Limit limit = new Limit(quantity, retain);
        trackNode(limit, ctx);
        return limit;
    }

    @Override
    public ResourceIdSet visitResourceExclusion(@Nullable SFMLParser.ResourceExclusionContext ctx) {

        if (ctx == null) return ResourceIdSet.EMPTY;
        var resourceIdSet = visitResourceIdList(ctx.resourceIdList());
        trackNode(resourceIdSet, ctx);
        return resourceIdSet;
    }

    /// This one uses COMMA instead of OR to separate items
    @SuppressWarnings("DuplicatedCode")
    @Override
    public ResourceIdSet visitResourceIdList(@Nullable SFMLParser.ResourceIdListContext ctx) {

        if (ctx == null) return ResourceIdSet.EMPTY;
        HashSet<ResourceIdentifier<?, ?, ?>> ids = ctx
                .resourceId()
                .stream()
                .map(this::visit)
                .map(ResourceIdentifier.class::cast)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        ResourceIdSet resourceIdSet = new ResourceIdSet(ids);
        trackNode(resourceIdSet, ctx);
        return resourceIdSet;
    }

    /// This one uses OR instead of COMMA to separate items
    @SuppressWarnings("DuplicatedCode")
    @Override
    public ResourceIdSet visitResourceIdDisjunction(@Nullable SFMLParser.ResourceIdDisjunctionContext ctx) {

        if (ctx == null) return ResourceIdSet.EMPTY;
        HashSet<ResourceIdentifier<?, ?, ?>> ids = ctx
                .resourceId()
                .stream()
                .map(this::visit)
                .map(ResourceIdentifier.class::cast)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        ResourceIdSet resourceIdSet = new ResourceIdSet(ids);
        trackNode(resourceIdSet, ctx);
        return resourceIdSet;
    }

    @Override
    public ResourceLimits visitInputResourceLimits(@Nullable SFMLParser.InputResourceLimitsContext ctx) {

        if (ctx == null) {
            return new ResourceLimits(List.of(ResourceLimit.TAKE_ALL_LEAVE_NONE), ResourceIdSet.EMPTY);
        }
        ResourceLimits resourceLimits = visitResourceLimitList(ctx.resourceLimitList()).withDefaultLimit(Limit.MAX_QUANTITY_NO_RETENTION);
        trackNode(resourceLimits, ctx);
        return resourceLimits;
    }

    @Override
    public ResourceLimits visitOutputResourceLimits(@Nullable SFMLParser.OutputResourceLimitsContext ctx) {

        if (ctx == null) {
            return new ResourceLimits(List.of(ResourceLimit.ACCEPT_ALL_WITHOUT_RESTRAINT), ResourceIdSet.EMPTY);
        }
        ResourceLimits resourceLimits = visitResourceLimitList(ctx.resourceLimitList()).withDefaultLimit(Limit.MAX_QUANTITY_MAX_RETENTION);
        trackNode(resourceLimits, ctx);
        return resourceLimits;
    }

    @Override
    public ResourceLimits visitResourceLimitList(SFMLParser.ResourceLimitListContext ctx) {

        ResourceLimits resourceLimits = new ResourceLimits(
                ctx.resourceLimit().stream()
                        .map(this::visitResourceLimit)
                        .collect(Collectors.toList()),
                ResourceIdSet.EMPTY
        );
        trackNode(resourceLimits, ctx);
        return resourceLimits;
    }

    @Override
    public ResourceLimit visitResourceLimit(SFMLParser.ResourceLimitContext ctx) {

        ResourceIdSet resourceIds;
        if (ctx.resourceIdDisjunction() == null) {
            resourceIds = ResourceIdSet.MATCH_ALL;
        } else {
            resourceIds = visitResourceIdDisjunction(ctx.resourceIdDisjunction());
        }

        Limit limit;
        if (ctx.limit() == null) {
            limit = Limit.UNSET;
        } else {
            limit = (Limit) visit(ctx.limit());
        }

        With with;
        if (ctx.with() == null) {
            with = With.ALWAYS_TRUE;
        } else {
            with = (With) visit(ctx.with());
        }

        ResourceLimit resourceLimit = new ResourceLimit(resourceIds, limit, with);

        trackNode(resourceLimit, ctx);
        return resourceLimit;
    }

    @Override
    public ASTNode visitWith(SFMLParser.WithContext ctx) {

        WithClause clause = (WithClause) visit(ctx.withClause());
        With.WithMode mode = ctx.WITHOUT() != null ? With.WithMode.WITHOUT : With.WithMode.WITH;
        With rtn = new With(clause, mode);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithTag visitWithTag(SFMLParser.WithTagContext ctx) {

        WithTag rtn = new WithTag((TagMatcher) visit(ctx.tagMatcher()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithConjunction visitWithConjunction(SFMLParser.WithConjunctionContext ctx) {

        var left = (WithClause) visit(ctx.withClause(0));
        var right = (WithClause) visit(ctx.withClause(1));
        WithConjunction rtn = new WithConjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithParen visitWithParen(SFMLParser.WithParenContext ctx) {

        var inner = (WithClause) visit(ctx.withClause());
        WithParen rtn = new WithParen(inner);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithNegation visitWithNegation(SFMLParser.WithNegationContext ctx) {

        var inner = (WithClause) visit(ctx.withClause());
        WithNegation rtn = new WithNegation(inner);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public WithDisjunction visitWithDisjunction(SFMLParser.WithDisjunctionContext ctx) {

        var left = (WithClause) visit(ctx.withClause(0));
        var right = (WithClause) visit(ctx.withClause(1));
        WithDisjunction rtn = new WithDisjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public TagMatcher visitTagMatcher(SFMLParser.TagMatcherContext ctx) {

        ArrayDeque<String> identifiers = ctx
                .identifier()
                .stream()
                .map(ParseTree::getText)
                .map(SFMLLiteralGlob::toRegex)
                .collect(Collectors.toCollection(ArrayDeque::new));
        TagMatcher rtn;
        if (ctx.COLON() == null) {
            // wildcard namespace
            rtn = TagMatcher.fromPath(identifiers);
        } else {
            // namespace specified
            rtn = TagMatcher.fromNamespaceAndPath(identifiers.pop(), identifiers);
        }
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberRangeSet visitSlotqualifier(@Nullable SFMLParser.SlotqualifierContext ctx) {

        NumberRangeSet numberRangeSet = visitRangeset(ctx == null ? null : ctx.rangeset());
        if (ctx != null) {
            trackNode(numberRangeSet, ctx);
        }
        return numberRangeSet;
    }

    @Override
    public ForgetStatement visitForgetStatement(SFMLParser.ForgetStatementContext ctx) {

        Set<Label> labels = ctx
                .label()
                .stream()
                .map(this::visit)
                .map(Label.class::cast)
                .collect(Collectors.toSet());
        ForgetStatement rtn = labels.isEmpty()
                              ? ForgetStatement.allInputsStatement()
                              : new ForgetStatement(labels);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberRangeSet visitRangeset(@Nullable SFMLParser.RangesetContext ctx) {

        if (ctx == null) return NumberRangeSet.MAX_RANGE;
        NumberRangeSet numberRangeSet = new NumberRangeSet(
                ctx
                        .range()
                        .stream()
                        .map(this::visitRange)
                        .toArray(NumberRange[]::new)
        );
        trackNode(numberRangeSet, ctx);
        return numberRangeSet;
    }

    @Override
    public NumberRange visitRange(SFMLParser.RangeContext ctx) {

        var iter = ctx.number().stream().map(this::visitNumber).mapToLong(Number::value).iterator();
        var start = iter.next();
        if (iter.hasNext()) {
            var end = iter.next();
            NumberRange numberRange = new NumberRange(start, end);
            trackNode(numberRange, ctx);
            return numberRange;
        } else {
            NumberRange numberRange = new NumberRange(start, start);
            trackNode(numberRange, ctx);
            return numberRange;
        }
    }

    @Override
    public Limit visitRetentionLimit(SFMLParser.RetentionLimitContext ctx) {

        var retain = visitRetention(ctx.retention());
        Limit limit = new Limit(ResourceQuantity.UNSET, retain);
        trackNode(limit, ctx);
        return limit;
    }

    @Override
    public Limit visitQuantityLimit(SFMLParser.QuantityLimitContext ctx) {

        var quantity = visitQuantity(ctx.quantity());
        Limit limit = new Limit(quantity, ResourceQuantity.UNSET);
        trackNode(limit, ctx);
        return limit;
    }

    @Override
    public ResourceQuantity visitRetention(@Nullable SFMLParser.RetentionContext ctx) {

        if (ctx == null)
            return ResourceQuantity.UNSET;
        ResourceQuantity quantity = new ResourceQuantity(
                visitNumber(ctx.number()),
                ctx.EACH() != null
                ? ResourceQuantity.IdExpansionBehaviour.EXPAND
                : ResourceQuantity.IdExpansionBehaviour.NO_EXPAND
        );
        trackNode(quantity, ctx);
        return quantity;
    }

    @Override
    public ResourceQuantity visitQuantity(@Nullable SFMLParser.QuantityContext ctx) {

        if (ctx == null) return ResourceQuantity.MAX_QUANTITY;
        ResourceQuantity quantity = new ResourceQuantity(
                visitNumber(ctx.number()),
                ctx.EACH() != null
                ? ResourceQuantity.IdExpansionBehaviour.EXPAND
                : ResourceQuantity.IdExpansionBehaviour.NO_EXPAND
        );
        trackNode(quantity, ctx);
        return quantity;
    }

    @Override
    public SideQualifier visitEachSide(SFMLParser.EachSideContext ctx) {

        var rtn = SideQualifier.ALL;
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public SideQualifier visitListedSides(SFMLParser.ListedSidesContext ctx) {

        SideQualifier sideQualifier = new SideQualifier(
                ctx.side().stream()
                        .map(this::visitSide)
                        .toList()
        );
        trackNode(sideQualifier, ctx);
        return sideQualifier;
    }

    @Override
    public Side visitSide(SFMLParser.SideContext ctx) {

        Side side = Side.valueOf(ctx.getText().toUpperCase(Locale.ROOT));
        trackNode(side, ctx);
        return side;
    }

    @Override
    public Block visitBlock(@Nullable SFMLParser.BlockContext ctx) {

        if (ctx == null) return new Block(Collections.emptyList());
        var statements = ctx
                .statement()
                .stream()
                .map(this::visit)
                .map(Statement.class::cast)
                .collect(Collectors.toList());
        Block block = new Block(statements);
        trackNode(block, ctx);
        return block;
    }

    private SFMValuePattern resolvePattern(String name) {
        return new ProgramDefinitions(PATTERN_DEFINITIONS, PLAYER_DEFINITIONS)
                .pattern(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pattern alias: " + name));
    }

    private record PatternHolder(SFMValuePattern pattern) implements ASTNode {
        private PatternHolder {
            Objects.requireNonNull(pattern);
        }
    }

    /// Tracks an {@link ASTNode} and its {@link ParserRuleContext} for later retrieval for editor context actions.
    private void trackNode(
            ASTNode node,
            ParserRuleContext ctx
    ) {

        WeakReference<ASTNode> nodeRef = new WeakReference<>(node);

        AST_NODE_CONTEXTS.add(new Pair<>(nodeRef, ctx));
    }

}
