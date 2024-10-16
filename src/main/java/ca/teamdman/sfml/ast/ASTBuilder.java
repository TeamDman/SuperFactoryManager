package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.SFMConfig;
import ca.teamdman.sfml.SFMLBaseVisitor;
import ca.teamdman.sfml.SFMLParser;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ASTBuilder extends SFMLBaseVisitor<ASTNode> {
    private final Set<Label> USED_LABELS = new HashSet<>();
    private final Set<ResourceIdentifier<?, ?, ?>> USED_RESOURCES = new HashSet<>();
    private final List<Pair<ASTNode, ParserRuleContext>> AST_NODE_CONTEXTS = new LinkedList<>();
    private final List<? extends String> BLACKLISTED_RESOURCES = SFMConfig.getOrDefault(SFMConfig.COMMON.blacklistedResourceTypesForTransfer);

    public List<Pair<ASTNode, ParserRuleContext>> getNodesUnderCursor(int cursorPos) {
        return AST_NODE_CONTEXTS
                .stream()
                .filter(pair -> pair.getSecond() != null)
                .filter(pair -> pair.getSecond().start.getStartIndex() <= cursorPos
                                && pair.getSecond().stop.getStopIndex() >= cursorPos)
                .collect(Collectors.toList());
    }

    public Optional<ASTNode> getNodeAtIndex(int index) {
        if (index < 0 || index >= AST_NODE_CONTEXTS.size()) return Optional.empty();
        return Optional.ofNullable(AST_NODE_CONTEXTS.get(index).getFirst());
    }

    public void setLocationFromOtherNode(
            ASTNode node,
            ASTNode otherNode
    ) {
        AST_NODE_CONTEXTS.add(new Pair<>(node, AST_NODE_CONTEXTS.get(getIndexForNode(otherNode)).getSecond()));
    }

    public int getIndexForNode(ASTNode node) {
        return AST_NODE_CONTEXTS
                .stream()
                .filter(pair -> pair.getFirst() == node)
                .map(AST_NODE_CONTEXTS::indexOf)
                .findFirst()
                .orElse(-1);
    }

    public Optional<ParserRuleContext> getContextForNode(ASTNode node) {
        return AST_NODE_CONTEXTS
                .stream()
                .filter(pair -> pair.getFirst() == node)
                .map(Pair::getSecond)
                .findFirst();
    }

    public String getLineColumnForNode(ASTNode node) {
        return getContextForNode(node)
                .map(ctx -> "Line " + ctx.start.getLine() + ", Column " + ctx.start.getCharPositionInLine())
                .orElse("Unknown location");
    }

    @Override
    public StringHolder visitName(@Nullable SFMLParser.NameContext ctx) {
        if (ctx == null) return new StringHolder("");
        StringHolder name = visitString(ctx.string());
        AST_NODE_CONTEXTS.add(new Pair<>(name, ctx));
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
                .replaceAll("\\*", ".*");
        var rtn = ResourceIdentifier.fromString(str);
        USED_RESOURCES.add(rtn);
        rtn.assertValid();
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
        return rtn;
    }

    @Override
    public ResourceIdentifier<?, ?, ?> visitStringResource(SFMLParser.StringResourceContext ctx) {
        var rtn = ResourceIdentifier.fromString(visitString(ctx.string()).value());
        USED_RESOURCES.add(rtn);
        rtn.assertValid();
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
        return rtn;
    }

    @Override
    public StringHolder visitString(SFMLParser.StringContext ctx) {
        var content = ctx.getText();
        StringHolder str = new StringHolder(content.substring(1, content.length() - 1));
        AST_NODE_CONTEXTS.add(new Pair<>(str, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(label, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(label, ctx));
        return label;
    }

    @Override
    public Program visitProgram(SFMLParser.ProgramContext ctx) {
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
        AST_NODE_CONTEXTS.add(new Pair<>(program, ctx));
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
                          ? SFMConfig.getOrDefault(SFMConfig.COMMON.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO)
                          : SFMConfig.getOrDefault(SFMConfig.COMMON.timerTriggerMinimumIntervalInTicks);

        // validate interval
        if (time.getTicks() < minInterval) {
            throw new IllegalArgumentException("Minimum trigger interval is " + minInterval + " ticks.");
        }

        AST_NODE_CONTEXTS.add(new Pair<>(timerTrigger, ctx));
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

        ComparisonOperator finalComp = comp;
        if (num.value() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Redstone signal strength cannot be greater than " + Integer.MAX_VALUE);
        }
        //noinspection ExtractMethodRecommender
        int finalNum = (int) num.value();
        //noinspection DataFlowIssue // if the program is ticking, level shouldn't be null
        BoolExpr boolExpr = new BoolExpr(
                programContext -> finalComp.test(
                        (long) programContext
                                .getManager()
                                .getLevel()
                                .getBestNeighborSignal(
                                        programContext
                                                .getManager()
                                                .getBlockPos()
                                ),
                        (long) finalNum
                ),
                ctx.getText()
        );
        AST_NODE_CONTEXTS.add(new Pair<>(boolExpr, ctx));
        return boolExpr;
    }

    @Override
    public ASTNode visitPulseTrigger(SFMLParser.PulseTriggerContext ctx) {
        var block = visitBlock(ctx.block());
        RedstoneTrigger redstoneTrigger = new RedstoneTrigger(block);
        AST_NODE_CONTEXTS.add(new Pair<>(redstoneTrigger, ctx));
        return redstoneTrigger;
    }

    @Override
    public Number visitNumber(SFMLParser.NumberContext ctx) {
        Number number = new Number(Long.parseLong(ctx.getText()));
        AST_NODE_CONTEXTS.add(new Pair<>(number, ctx));
        return number;
    }

    @Override
    public Interval visitTick(SFMLParser.TickContext ctx) {
        Interval interval = Interval.fromTicks(1);
        AST_NODE_CONTEXTS.add(new Pair<>(interval, ctx));
        return interval;
    }

    @Override
    public Interval visitTicks(SFMLParser.TicksContext ctx) {
        var num = visitNumber(ctx.number());
        if (num.value() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("interval cannot be greater than " + Integer.MAX_VALUE + " ticks.");
        }
        Interval interval = Interval.fromTicks((int) num.value());
        AST_NODE_CONTEXTS.add(new Pair<>(interval, ctx));
        return interval;
    }

    @Override
    public Interval visitSeconds(SFMLParser.SecondsContext ctx) {
        var num = visitNumber(ctx.number());
        if (num.value() > Integer.MAX_VALUE / 20) {
            throw new IllegalArgumentException("interval cannot be greater than " + Integer.MAX_VALUE + " ticks.");
        }
        Interval interval = Interval.fromSeconds((int) num.value());
        AST_NODE_CONTEXTS.add(new Pair<>(interval, ctx));
        return interval;
    }

    @Override
    public InputStatement visitInputStatement(SFMLParser.InputStatementContext ctx) {
        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var matchers = visitInputResourceLimits(ctx.inputResourceLimits());

        matchers.resourceLimitList().forEach(resourceLimit -> {
            resourceLimit.resourceIds().stream().forEach(resourceId -> {
                if (BLACKLISTED_RESOURCES.contains(resourceId.resourceTypeName))
                    throw new IllegalArgumentException("Resource type \"" + resourceId.resourceTypeName + "\" is blacklisted");
            });
        });

        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        InputStatement inputStatement = new InputStatement(labelAccess, matchers.withExclusions(exclusions), each);
        AST_NODE_CONTEXTS.add(new Pair<>(inputStatement, ctx));
        return inputStatement;
    }

    @Override
    public OutputStatement visitOutputStatement(SFMLParser.OutputStatementContext ctx) {
        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var matchers = visitOutputResourceLimits(ctx.outputResourceLimits());

        matchers.resourceLimitList().forEach(resourceLimit -> {
            resourceLimit.resourceIds().stream().forEach(resourceId -> {
                if (BLACKLISTED_RESOURCES.contains(resourceId.resourceTypeName))
                    throw new IllegalArgumentException("Resource type \"" + resourceId.resourceTypeName + "\" is blacklisted");
            });
        });

        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        OutputStatement outputStatement = new OutputStatement(labelAccess, matchers.withExclusions(exclusions), each);
        AST_NODE_CONTEXTS.add(new Pair<>(outputStatement, ctx));
        return outputStatement;
    }

    @Override
    public LabelAccess visitLabelAccess(SFMLParser.LabelAccessContext ctx) {
        var directionQualifierCtx = ctx.sidequalifier();
        DirectionQualifier directionQualifier;
        if (directionQualifierCtx == null) {
            directionQualifier = DirectionQualifier.NULL_DIRECTION;
        } else {
            directionQualifier = (DirectionQualifier) visit(directionQualifierCtx);
        }
        LabelAccess labelAccess = new LabelAccess(
                ctx.label().stream().map(this::visit).map(Label.class::cast).collect(Collectors.toList()),
                directionQualifier,
                visitSlotqualifier(ctx.slotqualifier()),
                visitRoundrobin(ctx.roundrobin())
        );
        AST_NODE_CONTEXTS.add(new Pair<>(labelAccess, ctx));
        return labelAccess;
    }

    @Override
    public RoundRobin visitRoundrobin(@Nullable SFMLParser.RoundrobinContext ctx) {
        if (ctx == null) return RoundRobin.disabled();
        RoundRobin rtn = ctx.BLOCK() != null
                         ? new RoundRobin(RoundRobin.Behaviour.BY_BLOCK)
                         : new RoundRobin(RoundRobin.Behaviour.BY_LABEL);
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
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

        AST_NODE_CONTEXTS.add(new Pair<>(nestedStatement, ctx));
        return nestedStatement;
    }

    @Override
    public BoolExpr visitBooleanTrue(SFMLParser.BooleanTrueContext ctx) {
        BoolExpr boolExpr = BoolExpr.TRUE;
        AST_NODE_CONTEXTS.add(new Pair<>(boolExpr, ctx));
        return boolExpr;
    }

    @Override
    public BoolExpr visitBooleanHas(SFMLParser.BooleanHasContext ctx) {
        var setOp = visitSetOp(ctx.setOp());
        var labelAccess = visitLabelAccess(ctx.labelAccess());
        var comparison = visitResourcecomparison(ctx.resourcecomparison());
        BoolExpr booleanExpression = comparison.toBooleanExpression(
                setOp,
                labelAccess,
                setOp.name().toUpperCase() + " " + labelAccess + " HAS " + comparison
        );
        AST_NODE_CONTEXTS.add(new Pair<>(booleanExpression, ctx));
        return booleanExpression;
    }

    @Override
    public SetOperator visitSetOp(@Nullable SFMLParser.SetOpContext ctx) {
        if (ctx == null) return SetOperator.OVERALL;
        SetOperator from = SetOperator.from(ctx.getText());
        AST_NODE_CONTEXTS.add(new Pair<>(from, ctx));
        return from;
    }

    @Override
    public ResourceComparer<?, ?, ?> visitResourcecomparison(SFMLParser.ResourcecomparisonContext ctx) {
        ComparisonOperator op = visitComparisonOp(ctx.comparisonOp());
        Number num = visitNumber(ctx.number());
        ResourceQuantity quantity = new ResourceQuantity(num, ResourceQuantity.IdExpansionBehaviour.NO_EXPAND);

        ResourceIdentifier<?, ?, ?> item;
        if (ctx.resourceId() == null) {
            item = ResourceIdentifier.MATCH_ALL;
        } else {
            item = (ResourceIdentifier<?, ?, ?>) visit(ctx.resourceId());
        }

        ResourceComparer<?, ?, ?> resourceComparer = new ResourceComparer<>(op, quantity, item);
        AST_NODE_CONTEXTS.add(new Pair<>(resourceComparer, ctx));
        return resourceComparer;
    }

    @Override
    public ComparisonOperator visitComparisonOp(SFMLParser.ComparisonOpContext ctx) {
        ComparisonOperator from = ComparisonOperator.from(ctx.getText());
        AST_NODE_CONTEXTS.add(new Pair<>(from, ctx));
        return from;
    }

    @Override
    public BoolExpr visitBooleanConjunction(SFMLParser.BooleanConjunctionContext ctx) {
        var left = (BoolExpr) visit(ctx.boolexpr(0));
        var right = (BoolExpr) visit(ctx.boolexpr(1));
        BoolExpr boolExpr = new BoolExpr(left.and(right), left.sourceCode() + " AND " + right.sourceCode());
        AST_NODE_CONTEXTS.add(new Pair<>(boolExpr, ctx));
        return boolExpr;
    }

    @Override
    public BoolExpr visitBooleanDisjunction(SFMLParser.BooleanDisjunctionContext ctx) {
        var left = (BoolExpr) visit(ctx.boolexpr(0));
        var right = (BoolExpr) visit(ctx.boolexpr(1));
        BoolExpr boolExpr = new BoolExpr(left.or(right), left.sourceCode() + " OR " + right.sourceCode());
        AST_NODE_CONTEXTS.add(new Pair<>(boolExpr, ctx));
        return boolExpr;
    }

    @Override
    public BoolExpr visitBooleanFalse(SFMLParser.BooleanFalseContext ctx) {
        BoolExpr boolExpr = BoolExpr.FALSE;
        AST_NODE_CONTEXTS.add(new Pair<>(boolExpr, ctx));
        return boolExpr;
    }

    @Override
    public BoolExpr visitBooleanParen(SFMLParser.BooleanParenContext ctx) {
        BoolExpr expr = (BoolExpr) visit(ctx.boolexpr());
        AST_NODE_CONTEXTS.add(new Pair<>(expr, ctx));
        return expr;
    }

    @Override
    public BoolExpr visitBooleanNegation(SFMLParser.BooleanNegationContext ctx) {
        var x = (BoolExpr) visit(ctx.boolexpr());
        BoolExpr boolExpr = new BoolExpr(x.negate(), "NOT " + x.sourceCode());
        AST_NODE_CONTEXTS.add(new Pair<>(boolExpr, ctx));
        return boolExpr;
    }

    @Override
    public Limit visitQuantityRetentionLimit(SFMLParser.QuantityRetentionLimitContext ctx) {
        var quantity = visitQuantity(ctx.quantity());
        var retain = visitRetention(ctx.retention());
        Limit limit = new Limit(quantity, retain);
        AST_NODE_CONTEXTS.add(new Pair<>(limit, ctx));
        return limit;
    }

    @Override
    public ResourceIdSet visitResourceExclusion(@Nullable SFMLParser.ResourceExclusionContext ctx) {
        if (ctx == null) return ResourceIdSet.EMPTY;
        var resourceIdSet = visitResourceIdList(ctx.resourceIdList());
        AST_NODE_CONTEXTS.add(new Pair<>(resourceIdSet, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(resourceIdSet, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(resourceIdSet, ctx));
        return resourceIdSet;
    }

    @Override
    public ResourceLimits visitInputResourceLimits(@Nullable SFMLParser.InputResourceLimitsContext ctx) {
        if (ctx == null) {
            return new ResourceLimits(List.of(ResourceLimit.TAKE_ALL_LEAVE_NONE), ResourceIdSet.EMPTY);
        }
        ResourceLimits resourceLimits = visitResourceLimitList(ctx.resourceLimitList()).withDefaultLimit(Limit.MAX_QUANTITY_NO_RETENTION);
        AST_NODE_CONTEXTS.add(new Pair<>(resourceLimits, ctx));
        return resourceLimits;
    }

    @Override
    public ResourceLimits visitOutputResourceLimits(@Nullable SFMLParser.OutputResourceLimitsContext ctx) {
        if (ctx == null) {
            return new ResourceLimits(List.of(ResourceLimit.ACCEPT_ALL_WITHOUT_RESTRAINT), ResourceIdSet.EMPTY);
        }
        ResourceLimits resourceLimits = visitResourceLimitList(ctx.resourceLimitList()).withDefaultLimit(Limit.MAX_QUANTITY_MAX_RETENTION);
        AST_NODE_CONTEXTS.add(new Pair<>(resourceLimits, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(resourceLimits, ctx));
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

        AST_NODE_CONTEXTS.add(new Pair<>(resourceLimit, ctx));
        return resourceLimit;
    }

    @Override
    public ASTNode visitWith(SFMLParser.WithContext ctx) {
        WithClause clause = (WithClause) visit(ctx.withClause());
        With.WithMode mode = ctx.WITHOUT() != null ? With.WithMode.WITHOUT : With.WithMode.WITH;
        With rtn = new With(clause, mode, ctx.getText());
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
        return rtn;
    }

    @Override
    public WithTag visitWithTag(SFMLParser.WithTagContext ctx) {
        WithTag rtn = new WithTag((TagMatcher) visit(ctx.tagMatcher()));
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
        return rtn;
    }

    @Override
    public NumberRangeSet visitSlotqualifier(@Nullable SFMLParser.SlotqualifierContext ctx) {
        NumberRangeSet numberRangeSet = visitRangeset(ctx == null ? null : ctx.rangeset());
        AST_NODE_CONTEXTS.add(new Pair<>(numberRangeSet, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(numberRangeSet, ctx));
        return numberRangeSet;
    }

    @Override
    public NumberRange visitRange(SFMLParser.RangeContext ctx) {
        var iter = ctx.number().stream().map(this::visitNumber).mapToLong(Number::value).iterator();
        var start = iter.next();
        if (iter.hasNext()) {
            var end = iter.next();
            NumberRange numberRange = new NumberRange(start, end);
            AST_NODE_CONTEXTS.add(new Pair<>(numberRange, ctx));
            return numberRange;
        } else {
            NumberRange numberRange = new NumberRange(start, start);
            AST_NODE_CONTEXTS.add(new Pair<>(numberRange, ctx));
            return numberRange;
        }
    }

    @Override
    public Limit visitRetentionLimit(SFMLParser.RetentionLimitContext ctx) {
        var retain = visitRetention(ctx.retention());
        Limit limit = new Limit(ResourceQuantity.UNSET, retain);
        AST_NODE_CONTEXTS.add(new Pair<>(limit, ctx));
        return limit;
    }

    @Override
    public Limit visitQuantityLimit(SFMLParser.QuantityLimitContext ctx) {
        var quantity = visitQuantity(ctx.quantity());
        Limit limit = new Limit(quantity, ResourceQuantity.UNSET);
        AST_NODE_CONTEXTS.add(new Pair<>(limit, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(quantity, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(quantity, ctx));
        return quantity;
    }

    @Override
    public DirectionQualifier visitEachSide(SFMLParser.EachSideContext ctx) {
        var rtn = DirectionQualifier.EVERY_DIRECTION;
        AST_NODE_CONTEXTS.add(new Pair<>(rtn, ctx));
        return rtn;
    }

    @Override
    public DirectionQualifier visitListedSides(SFMLParser.ListedSidesContext ctx) {
        DirectionQualifier directionQualifier = new DirectionQualifier(
                EnumSet.copyOf(
                        ctx.side().stream()
                                .map(this::visitSide)
                                .map(DirectionQualifier::lookup)
                                .toList()
                )
        );
        AST_NODE_CONTEXTS.add(new Pair<>(directionQualifier, ctx));
        return directionQualifier;
    }

    @Override
    public Side visitSide(SFMLParser.SideContext ctx) {
        Side side = Side.valueOf(ctx.getText().toUpperCase(Locale.ROOT));
        AST_NODE_CONTEXTS.add(new Pair<>(side, ctx));
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
        AST_NODE_CONTEXTS.add(new Pair<>(block, ctx));
        return block;
    }
}
