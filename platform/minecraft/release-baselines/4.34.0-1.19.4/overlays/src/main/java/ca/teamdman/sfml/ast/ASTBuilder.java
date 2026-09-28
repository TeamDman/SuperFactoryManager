package ca.teamdman.sfml.ast;

import ca.teamdman.langs.SFMLBaseVisitor;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.common.config.SFMConfig;
import com.mojang.datafixers.util.Pair;
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
                .replaceAll("\\*", ".*")
                .toLowerCase(Locale.ROOT);

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
        var name = visitName(ctx.name());
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
        Program program = new Program(this, name.value(), triggers, labels, USED_RESOURCES);
        trackNode(program, ctx);
        return program;
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

        TerminalNode firstNumber = ctx.NUMBER(0);
        int ticks;
        if (firstNumber == null) {
            ticks = 1;
        } else {
            ticks = Integer.parseInt(firstNumber.getText());
        }
        if (ctx.SECONDS() != null || ctx.SECOND() != null) {
            ticks *= 20;
        }

        Interval.IntervalAlignment alignment = Interval.IntervalAlignment.LOCAL;
        if (ctx.GLOBAL() != null) {
            alignment = Interval.IntervalAlignment.GLOBAL;
        }

        int offset = 0;
        TerminalNode secondNumber = ctx.NUMBER(1);
        if (secondNumber != null) {
            offset = Integer.parseInt(secondNumber.getText());
            if (ctx.SECONDS() != null || ctx.SECOND() != null) {
                offset *= 20;
            }
        }

        Interval interval = new Interval(ticks, alignment, offset);
        trackNode(interval, ctx);
        return interval;
    }

    @Override
    public ASTNode visitIntervalNoSpace(SFMLParser.IntervalNoSpaceContext ctx) {

        String firstNumber = ctx.NUMBER_WITH_G_SUFFIX().getText();
        String front = firstNumber.substring(0, firstNumber.length() - 1);
        int ticks = Integer.parseInt(front);
        if (ctx.SECONDS() != null || ctx.SECOND() != null) {
            ticks *= 20;
        }

        Interval.IntervalAlignment alignment = Interval.IntervalAlignment.GLOBAL;

        int offset = 0;
        TerminalNode secondNumber = ctx.NUMBER();
        if (secondNumber != null) {
            offset = Integer.parseInt(secondNumber.getText());
            if (ctx.SECONDS() != null || ctx.SECOND() != null) {
                offset *= 20;
            }
        }

        Interval interval = new Interval(ticks, alignment, offset);
        trackNode(interval, ctx);
        return interval;
    }

    @Override
    public InputStatement visitInputStatement(SFMLParser.InputStatementContext ctx) {

        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var matchers = visitInputResourceLimits(ctx.inputResourceLimits());
        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        InputStatement inputStatement = new InputStatement(labelAccess, matchers.withExclusions(exclusions), each);
        trackNode(inputStatement, ctx);
        return inputStatement;
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
                .map(s -> s.replaceAll("\\*", ".*")) // convert * to .*
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
        if (labels.isEmpty()) {
            labels = USED_LABELS;
        }
        ForgetStatement rtn = new ForgetStatement(labels);
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

    /// Tracks an {@link ASTNode} and its {@link ParserRuleContext} for later retrieval for editor context actions.
    private void trackNode(
            ASTNode node,
            ParserRuleContext ctx
    ) {

        WeakReference<ASTNode> nodeRef = new WeakReference<>(node);

        AST_NODE_CONTEXTS.add(new Pair<>(nodeRef, ctx));
    }

}
