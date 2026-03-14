package ca.teamdman.sfml.ast;

import ca.teamdman.antlr.IAstBuilder;
import ca.teamdman.langs.SFMLBaseVisitor;
import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfm.common.config.SFMConfig;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.stream.Collectors;

public class SfmlAstBuilder extends SFMLBaseVisitor<SfmlAstNode> implements IAstBuilder<SfmlAstNode> {
    /// Used for linting and for label gun pull behaviour
    private final Set<Label> usedLabels = new HashSet<>();

    /// Used for linting and for energy-specific timer minimum interval restrictions
    private final Set<ResourceIdentifier<?, ?, ?>> usedResources = new HashSet<>();

    private final List<Pair<WeakReference<SfmlAstNode>, ParserRuleContext>> contexts = new LinkedList<>();

    @Override
    public List<Pair<WeakReference<SfmlAstNode>, ParserRuleContext>> contexts() {

        return contexts;
    }

    @Override
    public StringHolder visitName(@Nullable SFMLParser.NameContext ctx) {

        if (ctx == null) return new StringHolder("");
        StringHolder name = visitString(ctx.string());
        trackNode(new ProgramName(name), ctx);
        return name;
    }

    @Override
    public ResourceIdentifier<?,?,?> visitResource(SFMLParser.ResourceContext ctx) {

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
        usedResources.add(rtn);
        rtn.assertValid();
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public ResourceIdentifier<?, ?, ?> visitStringResource(SFMLParser.StringResourceContext ctx) {

        var rtn = ResourceIdentifier.fromString(visitString(ctx.string()).value());
        usedResources.add(rtn);
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
        if (label.value().length() > SFMLProgram.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label value cannot be longer than "
                    + SFMLProgram.MAX_LABEL_LENGTH
                    + " characters."
            );
        }
        usedLabels.add(label);
        trackNode(label, ctx);
        return label;
    }

    @Override
    public Label visitStringLabel(SFMLParser.StringLabelContext ctx) {

        var label = new Label(visitString(ctx.string()).value());
        if (label.value().length() > SFMLProgram.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label value cannot be longer than "
                    + SFMLProgram.MAX_LABEL_LENGTH
                    + " characters."
            );
        }
        usedLabels.add(label);
        trackNode(label, ctx);
        return label;
    }

    @Override
    public SFMLProgram visitProgram(SFMLParser.ProgramContext ctx) {

        if (SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.disableProgramExecution)) {
            throw new AssertionError("Program execution is disabled via config");
        }
        var name = visitName(ctx.name());
        
        List<Trigger> triggers = new ArrayList<>();
        List<LogExpression> topLevelLogExpressions = new ArrayList<>();
        
        for (var outerExpr : ctx.outerExpression()) {
            var node = visit(outerExpr);
            if (node instanceof Trigger trigger) {
                triggers.add(trigger);
            } else if (node instanceof LogExpression logExpr) {
                topLevelLogExpressions.add(logExpr);
            } else {
                throw new IllegalStateException("Unexpected outer expression type: " + node.getClass().getCanonicalName());
            }
        }
        
        var labels = usedLabels
                .stream()
                .map(Label::value)
                .collect(Collectors.toSet());
        SFMLProgram program = new SFMLProgram(this, name.value(), triggers, topLevelLogExpressions, labels, usedResources);
        trackNode(program, ctx);
        return program;
    }

    @Override
    public SfmlAstNode visitTimerTrigger(SFMLParser.TimerTriggerContext ctx) {
        // create timer trigger
        var time = (Interval) visit(ctx.interval());
        var block = visitBlock(ctx.block());
        TimerTrigger timerTrigger = new TimerTrigger(time, block);

        // get default min interval
        int minInterval = timerTrigger.usesOnlyForgeEnergyResourceIO()
                          ? SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO)
                          : SFMConfig.getOrDefault(SFMConfig.SERVER_CONFIG.timerTriggerMinimumIntervalInTicks);

        // validate interval
        if (time.intervalTicks() < minInterval) {
            throw new IllegalArgumentException("Minimum trigger interval is " + minInterval + " ticks.");
        }

        trackNode(timerTrigger, ctx);
        return timerTrigger;
    }

    @Override
    public SfmlAstNode visitBooleanRedstone(SFMLParser.BooleanRedstoneContext ctx) {

        ComparisonOperator comp = ComparisonOperator.GREATER_OR_EQUAL;
        NumberExpression num = NumberExpression.fromLiteral(0);
        if (ctx.comparisonOp() != null && ctx.numberExpression() != null) {
            comp = visitComparisonOp(ctx.comparisonOp());
            num = (NumberExpression) visit(ctx.numberExpression());
        }
        if (num.value() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Redstone signal strength cannot be greater than " + Integer.MAX_VALUE);
        }
        BoolExpr boolExpr = new BoolRedstone(comp, num);
        trackNode(boolExpr, ctx);
        return boolExpr;
    }

    @Override
    public SfmlAstNode visitPulseTrigger(SFMLParser.PulseTriggerContext ctx) {

        var block = visitBlock(ctx.block());
        RedstonePulseTrigger redstonePulseTrigger = new RedstonePulseTrigger(block);
        trackNode(redstonePulseTrigger, ctx);
        return redstonePulseTrigger;
    }

    @Override
    public NumberExpression visitNumberExpressionMultiplication(SFMLParser.NumberExpressionMultiplicationContext ctx) {

        NumberExpression left = (NumberExpression) visit(ctx.numberExpression(0));
        NumberExpression right = (NumberExpression) visit(ctx.numberExpression(1));
        NumberMultiplication expr = new NumberMultiplication(left, right);
        Number value = new Number(left.value() * right.value());
        NumberExpression rtn = new NumberExpression(value, expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberExpression visitNumberExpressionLiteral(SFMLParser.NumberExpressionLiteralContext ctx) {

        long value = parseNumberLiteral(ctx.getText());
        NumberLiteral expr = new NumberLiteral(value);
        NumberExpression rtn = new NumberExpression(new Number(value), expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    /**
     * Parse a number literal, supporting underscores as digit separators (e.g., 1_000_000).
     */
    private long parseNumberLiteral(String text) {

        // Remove underscores for readability support (e.g., 1_000_000)
        String normalized = text.replace("_", "");
        if (normalized.isEmpty() || !normalized.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid number literal: " + text);
        }
        return Long.parseLong(normalized);
    }

    @Override
    public NumberExpression visitNumberExpressionAddition(SFMLParser.NumberExpressionAdditionContext ctx) {

        NumberExpression left = (NumberExpression) visit(ctx.numberExpression(0));
        NumberExpression right = (NumberExpression) visit(ctx.numberExpression(1));
        NumberAddition expr = new NumberAddition(left, right);
        Number value = new Number(left.value() + right.value());
        NumberExpression rtn = new NumberExpression(value, expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberExpression visitNumberExpressionSubtraction(SFMLParser.NumberExpressionSubtractionContext ctx) {

        NumberExpression left = (NumberExpression) visit(ctx.numberExpression(0));
        NumberExpression right = (NumberExpression) visit(ctx.numberExpression(1));
        NumberSubtraction expr = new NumberSubtraction(left, right);
        Number value = new Number(left.value() - right.value());
        NumberExpression rtn = new NumberExpression(value, expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberExpression visitNumberExpressionModulus(SFMLParser.NumberExpressionModulusContext ctx) {

        NumberExpression left = (NumberExpression) visit(ctx.numberExpression(0));
        NumberExpression right = (NumberExpression) visit(ctx.numberExpression(1));
        NumberModulus expr = new NumberModulus(left, right);
        Number value = new Number(left.value() % right.value());
        NumberExpression rtn = new NumberExpression(value, expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberExpression visitNumberExpressionExponential(SFMLParser.NumberExpressionExponentialContext ctx) {

        NumberExpression left = (NumberExpression) visit(ctx.numberExpression(0));
        NumberExpression right = (NumberExpression) visit(ctx.numberExpression(1));
        NumberExponential expr = new NumberExponential(left, right);
        Number value = new Number((long) Math.pow(left.value(), right.value()));
        NumberExpression rtn = new NumberExpression(value, expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberExpression visitNumberExpressionDivision(SFMLParser.NumberExpressionDivisionContext ctx) {

        NumberExpression left = (NumberExpression) visit(ctx.numberExpression(0));
        NumberExpression right = (NumberExpression) visit(ctx.numberExpression(1));
        if (right.value() == 0) {
            throw new IllegalArgumentException("Division by zero at "
                                               + IAstBuilder.getLineColumnForContext(ctx.numberExpression(1)));
        }
        NumberDivision expr = new NumberDivision(left, right);
        Number value = new Number(left.value() / right.value());
        NumberExpression rtn = new NumberExpression(value, expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public NumberExpression visitNumberExpressionParen(SFMLParser.NumberExpressionParenContext ctx) {

        NumberExpression inner = (NumberExpression) visit(ctx.numberExpression());
        NumberParen expr = new NumberParen(inner);
        NumberExpression rtn = new NumberExpression(inner.number(), expr);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public Interval visitInterval(SFMLParser.IntervalContext ctx) {

        SFMLParser.NumberExpressionContext intervalCtx = ctx.numberExpression(0);
        NumberExpression interval;
        if (intervalCtx == null) {
            interval = NumberExpression.fromLiteral(1);
        } else {
            interval = (NumberExpression) visit(intervalCtx);
        }
        DurationUnit intervalUnit = visitDurationUnit(ctx.durationUnit(0));

        SFMLParser.NumberExpressionContext offsetCtx = ctx.numberExpression(1);

        @Nullable NumberExpression offset;
        if (offsetCtx == null) {
            offset = NumberExpression.fromLiteral(0);
        } else {
            offset = (NumberExpression) visit(offsetCtx);
        }

        DurationUnit offsetUnit;
        if (ctx.durationUnit(1) != null) {
            offsetUnit = visitDurationUnit(ctx.durationUnit(1));
        } else {
            offsetUnit = intervalUnit;
        }

        Interval.IntervalAlignment alignment;
        if (ctx.GLOBAL() != null) {
            alignment = Interval.IntervalAlignment.GLOBAL;
        } else {
            alignment = Interval.IntervalAlignment.LOCAL;
        }

        Interval rtn = new Interval(
                interval,
                intervalUnit,
                alignment,
                offset,
                offsetUnit
        );
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public DurationUnit visitDurationUnit(SFMLParser.DurationUnitContext ctx) {

        DurationUnit rtn;
        if (ctx.SECOND() != null || ctx.SECONDS() != null) {
            rtn = DurationUnit.SECONDS;
        } else if (ctx.TICK() != null || ctx.TICKS() != null) {
            rtn = DurationUnit.TICKS;
        } else {
            throw new IllegalArgumentException("Invalid duration unit: " + ctx.getText());
        }
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public InputStatement visitInputStatement(SFMLParser.InputStatementContext ctx) {

        var resourceAccess = visitResourceAccess(ctx.resourceAccess());
        var matchers = visitInputResourceLimits(ctx.inputResourceLimits());
        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        InputStatement inputStatement = new InputStatement(resourceAccess, matchers.withExclusions(exclusions), each);
        trackNode(inputStatement, ctx);
        return inputStatement;
    }

    @Override
    public OutputStatement visitOutputStatement(SFMLParser.OutputStatementContext ctx) {

        var resourceAccess = visitResourceAccess(ctx.resourceAccess());
        var matchers = visitOutputResourceLimits(ctx.outputResourceLimits());
        var exclusions = visitResourceExclusion(ctx.resourceExclusion());
        var each = ctx.EACH() != null;
        boolean emptySlotsOnly = ctx.emptyslots() != null;
        OutputStatement outputStatement = new OutputStatement(
                resourceAccess,
                matchers.withExclusions(exclusions),
                each,
                emptySlotsOnly
        );
        trackNode(outputStatement, ctx);
        return outputStatement;
    }

    @Override
    public ResourceAccess visitResourceAccess(SFMLParser.ResourceAccessContext ctx) {

        List<LabelExpression> labelExpressions = ctx
                .labelExpression()
                .stream()
                .map(this::visit)
                .map(LabelExpression.class::cast)
                .toList();

        RoundRobin roundRobin = visitRoundrobin(ctx.roundrobin());

        SFMLParser.SideQualifierContext sideCtx = ctx.sideQualifier();

        SideQualifier sides;
        if (sideCtx == null) {
            sides = SideQualifier.NULL;
        } else {
            sides = (SideQualifier) visit(sideCtx);
        }

        SlotQualifier slots = visitSlotQualifier(ctx.slotQualifier());

        ResourceAccess resourceAccess = new ResourceAccess(
                labelExpressions,
                roundRobin,
                sides,
                slots
        );

        trackNode(resourceAccess, ctx);
        return resourceAccess;
    }

    @Override
    public RoundRobin visitRoundrobin(@Nullable SFMLParser.RoundrobinContext ctx) {

        if (ctx == null) return new RoundRobin(RoundRobinBehaviour.UNMODIFIED);
        RoundRobin rtn = ctx.BLOCK() != null
                         ? new RoundRobin(RoundRobinBehaviour.BY_BLOCK)
                         : new RoundRobin(RoundRobinBehaviour.BY_LABEL);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public LabelExpression visitLabelExpressionExclusion(SFMLParser.LabelExpressionExclusionContext ctx) {

        LabelExpression left = (LabelExpression) visit(ctx.labelExpression(0));
        LabelExpression right = (LabelExpression) visit(ctx.labelExpression(1));
        LabelExpression rtn = new LabelExpressionExclusion(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public LabelExpression visitLabelExpressionSingle(SFMLParser.LabelExpressionSingleContext ctx) {

        LabelExpression expr = new LabelExpressionSingle((Label) visit(ctx.label()));
        trackNode(expr, ctx);
        return expr;
    }

    @Override
    public LabelExpression visitLabelExpressionUnion(SFMLParser.LabelExpressionUnionContext ctx) {

        LabelExpression left = (LabelExpression) visit(ctx.labelExpression(0));
        LabelExpression right = (LabelExpression) visit(ctx.labelExpression(1));
        LabelExpression rtn = new LabelExpressionUnion(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public LabelExpression visitLabelExpressionParen(SFMLParser.LabelExpressionParenContext ctx) {

        LabelExpression expr = (LabelExpression) visit(ctx.labelExpression());
        trackNode(expr, ctx);
        return expr;
    }

    @Override
    public LabelExpression visitLabelExpressionIntersection(SFMLParser.LabelExpressionIntersectionContext ctx) {

        LabelExpression left = (LabelExpression) visit(ctx.labelExpression(0));
        LabelExpression right = (LabelExpression) visit(ctx.labelExpression(1));
        LabelExpression rtn = new LabelExpressionIntersection(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public IfStatement visitIfStatement(SFMLParser.IfStatementContext ctx) {

        var conditions = ctx
                .boolExpr()
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
        var resourceAccess = visitResourceAccess(ctx.resourceAccess());
        ComparisonOperator comparisonOperator = visitComparisonOp(ctx.comparisonOp());
        NumberExpression num = (NumberExpression) visit(ctx.numberExpression());
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
                resourceAccess,
                comparisonOperator,
                num,
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

        BoolExpr rtn = new BoolParen((BoolExpr) visit(ctx.boolExpr()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanNegation(SFMLParser.BooleanNegationContext ctx) {

        BoolExpr rtn = new BoolNegation((BoolExpr) visit(ctx.boolExpr()));
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanConjunction(SFMLParser.BooleanConjunctionContext ctx) {

        var left = (BoolExpr) visit(ctx.boolExpr(0));
        var right = (BoolExpr) visit(ctx.boolExpr(1));
        BoolExpr rtn = new BoolConjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanDisjunction(SFMLParser.BooleanDisjunctionContext ctx) {

        var left = (BoolExpr) visit(ctx.boolExpr(0));
        var right = (BoolExpr) visit(ctx.boolExpr(1));
        BoolExpr rtn = new BoolDisjunction(left, right);
        trackNode(rtn, ctx);
        return rtn;
    }

    @Override
    public BoolExpr visitBooleanComparison(SFMLParser.BooleanComparisonContext ctx) {

        var left = (NumberExpression) visit(ctx.numberExpression(0));
        var op = visitComparisonOp(ctx.comparisonOp());
        var right = (NumberExpression) visit(ctx.numberExpression(1));
        BoolExpr rtn = new BoolNumberComparison(left, op, right);
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
    public SfmlAstNode visitWith(SFMLParser.WithContext ctx) {

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
    public SlotQualifier visitSlotQualifier(@Nullable SFMLParser.SlotQualifierContext ctx) {

        if (ctx == null) {
            return new SlotQualifier(false, NumberSet.MAX_RANGE);
        } else {
            boolean each = ctx.EACH() != null;
            NumberSet numberSet = visitNumberSet(ctx.numberSet());
            SlotQualifier slotQualifier = new SlotQualifier(each, numberSet);
            trackNode(slotQualifier, ctx);
            return slotQualifier;
        }
    }


    @Override
    public NumberSet visitNumberSet(SFMLParser.NumberSetContext ctx) {

        List<NumberRange> include = new ArrayList<>();
        List<NumberRange> exclude = new ArrayList<>();
        for (SFMLParser.NumberRangeContext numberRangeContext : ctx.numberRange()) {
            NumberRange range = visitNumberRange(numberRangeContext);
            if (numberRangeContext.NOT() == null) {
                include.add(range);
            } else {
                exclude.add(range);
            }
        }
        NumberSet numberSet = NumberSet.of(
                include.toArray(NumberRange[]::new),
                exclude.toArray(NumberRange[]::new)
        );
        trackNode(numberSet, ctx);
        return numberSet;
    }

    @Override
    public NumberRange visitNumberRange(SFMLParser.NumberRangeContext ctx) {

        NumberExpression start = (NumberExpression) visit(ctx.numberExpression(0));
        NumberExpression end;
        SFMLParser.NumberExpressionContext endCtx = ctx.numberExpression(1);
        if (endCtx == null) {
            end = start;
        } else {
            end = (NumberExpression) visit(endCtx);
        }

        NumberRange numberRange = NumberRange.ofInclusive(
                start,
                end
        );
        trackNode(numberRange, ctx);
        return numberRange;
    }


    @Override
    public ForgetStatement visitForgetStatement(SFMLParser.ForgetStatementContext ctx) {

        Set<LabelExpression> labelExpressionsToForget = ctx
                .labelExpression()
                .stream()
                .map(this::visit)
                .map(LabelExpression.class::cast)
                .collect(Collectors.toSet());
        ForgetStatement rtn = new ForgetStatement(labelExpressionsToForget);
        trackNode(rtn, ctx);
        return rtn;
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

        if (ctx == null) {
            return ResourceQuantity.UNSET;
        }
        ResourceQuantity quantity = new ResourceQuantity(
                ctx.EACH() != null
                ? ResourceQuantity.IdExpansionBehaviour.EXPAND
                : ResourceQuantity.IdExpansionBehaviour.NO_EXPAND, (NumberExpression) visit(ctx.numberExpression())
        );
        trackNode(quantity, ctx);
        return quantity;
    }

    @Override
    public ResourceQuantity visitQuantity(@Nullable SFMLParser.QuantityContext ctx) {

        if (ctx == null) return ResourceQuantity.MAX_QUANTITY;
        ResourceQuantity quantity = new ResourceQuantity(
                ctx.EACH() != null
                ? ResourceQuantity.IdExpansionBehaviour.EXPAND
                : ResourceQuantity.IdExpansionBehaviour.NO_EXPAND, (NumberExpression) visit(ctx.numberExpression())
        );
        trackNode(quantity, ctx);
        return quantity;
    }

    @Override
    public SideQualifier visitAllSides(SFMLParser.AllSidesContext ctx) {

        // we aren't tracking this since we would have to clone the obj, means no ctrl+space actions supported here
        return SideQualifier.ALL;
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
                .map(Tickable.class::cast)
                .collect(Collectors.toList());
        Block block = new Block(statements);
        trackNode(block, ctx);
        return block;
    }

    @Override
    public SfmlAstNode visitDisplayableString(SFMLParser.DisplayableStringContext ctx) {

        return super.visitDisplayableString(ctx);
    }

    @Override
    public SfmlAstNode visitDisplayableNumberExpression(SFMLParser.DisplayableNumberExpressionContext ctx) {

        return super.visitDisplayableNumberExpression(ctx);
    }

    @Override
    public SfmlLogLevel visitLogLevel(SFMLParser.LogLevelContext ctx) {
        Level level;
        String text = ctx.getText().toUpperCase(Locale.ROOT);
        if (text.equals("WARNING")) {
            level = Level.WARN;
        } else {
            level = Level.getLevel(text);
        }
        SfmlLogLevel logLevel = new SfmlLogLevel(level);
        trackNode(logLevel, ctx);
        return logLevel;
    }

    @Override
    public LogExpression visitLogStatement(SFMLParser.LogStatementContext ctx) {
        SfmlLogLevel logLevel = visitLogLevel(ctx.logLevel());
        Displayable message = (Displayable) visit(ctx.displayable());
        LogExpression logExpression = new LogExpression(logLevel, message);
        trackNode(logExpression, ctx);
        return logExpression;
    }

    @Override
    public LogExpression visitPrintStatement(SFMLParser.PrintStatementContext ctx) {
        SfmlLogLevel logLevel = new SfmlLogLevel(Level.INFO);
        Displayable message = (Displayable) visit(ctx.displayable());
        LogExpression logExpression = new LogExpression(logLevel, message);
        trackNode(logExpression, ctx);
        return logExpression;
    }

    @Override
    public LogExpression visitInfoStatement(SFMLParser.InfoStatementContext ctx) {
        SfmlLogLevel logLevel = new SfmlLogLevel(Level.INFO);
        Displayable message = (Displayable) visit(ctx.displayable());
        LogExpression logExpression = new LogExpression(logLevel, message);
        trackNode(logExpression, ctx);
        return logExpression;
    }

    @Override
    public LogExpression visitDebugStatement(SFMLParser.DebugStatementContext ctx) {
        SfmlLogLevel logLevel = new SfmlLogLevel(Level.DEBUG);
        Displayable message = (Displayable) visit(ctx.displayable());
        LogExpression logExpression = new LogExpression(logLevel, message);
        trackNode(logExpression, ctx);
        return logExpression;
    }

    @Override
    public LogExpression visitWarnStatement(SFMLParser.WarnStatementContext ctx) {
        SfmlLogLevel logLevel = new SfmlLogLevel(Level.WARN);
        Displayable message = (Displayable) visit(ctx.displayable());
        LogExpression logExpression = new LogExpression(logLevel, message);
        trackNode(logExpression, ctx);
        return logExpression;
    }

    @Override
    public LogExpression visitErrorStatement(SFMLParser.ErrorStatementContext ctx) {
        SfmlLogLevel logLevel = new SfmlLogLevel(Level.ERROR);
        Displayable message = (Displayable) visit(ctx.displayable());
        LogExpression logExpression = new LogExpression(logLevel, message);
        trackNode(logExpression, ctx);
        return logExpression;
    }

    @Override
    public LogExpression visitTraceStatement(SFMLParser.TraceStatementContext ctx) {
        SfmlLogLevel logLevel = new SfmlLogLevel(Level.TRACE);
        Displayable message = (Displayable) visit(ctx.displayable());
        LogExpression logExpression = new LogExpression(logLevel, message);
        trackNode(logExpression, ctx);
        return logExpression;
    }

}
