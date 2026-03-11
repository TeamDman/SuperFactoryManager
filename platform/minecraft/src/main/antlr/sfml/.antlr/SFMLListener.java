// Generated from d:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft/src/main/antlr/sfml/SFML.g4 by ANTLR 4.13.1

package ca.teamdman.langs;

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link SFMLParser}.
 */
public interface SFMLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link SFMLParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(SFMLParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(SFMLParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#name}.
	 * @param ctx the parse tree
	 */
	void enterName(SFMLParser.NameContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#name}.
	 * @param ctx the parse tree
	 */
	void exitName(SFMLParser.NameContext ctx);
	/**
	 * Enter a parse tree produced by the {@code TimerTrigger}
	 * labeled alternative in {@link SFMLParser#trigger}.
	 * @param ctx the parse tree
	 */
	void enterTimerTrigger(SFMLParser.TimerTriggerContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TimerTrigger}
	 * labeled alternative in {@link SFMLParser#trigger}.
	 * @param ctx the parse tree
	 */
	void exitTimerTrigger(SFMLParser.TimerTriggerContext ctx);
	/**
	 * Enter a parse tree produced by the {@code PulseTrigger}
	 * labeled alternative in {@link SFMLParser#trigger}.
	 * @param ctx the parse tree
	 */
	void enterPulseTrigger(SFMLParser.PulseTriggerContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PulseTrigger}
	 * labeled alternative in {@link SFMLParser#trigger}.
	 * @param ctx the parse tree
	 */
	void exitPulseTrigger(SFMLParser.PulseTriggerContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IntervalSpace}
	 * labeled alternative in {@link SFMLParser#interval}.
	 * @param ctx the parse tree
	 */
	void enterIntervalSpace(SFMLParser.IntervalSpaceContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IntervalSpace}
	 * labeled alternative in {@link SFMLParser#interval}.
	 * @param ctx the parse tree
	 */
	void exitIntervalSpace(SFMLParser.IntervalSpaceContext ctx);
	/**
	 * Enter a parse tree produced by the {@code IntervalNoSpace}
	 * labeled alternative in {@link SFMLParser#interval}.
	 * @param ctx the parse tree
	 */
	void enterIntervalNoSpace(SFMLParser.IntervalNoSpaceContext ctx);
	/**
	 * Exit a parse tree produced by the {@code IntervalNoSpace}
	 * labeled alternative in {@link SFMLParser#interval}.
	 * @param ctx the parse tree
	 */
	void exitIntervalNoSpace(SFMLParser.IntervalNoSpaceContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#block}.
	 * @param ctx the parse tree
	 */
	void enterBlock(SFMLParser.BlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#block}.
	 * @param ctx the parse tree
	 */
	void exitBlock(SFMLParser.BlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(SFMLParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(SFMLParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#forgetStatement}.
	 * @param ctx the parse tree
	 */
	void enterForgetStatement(SFMLParser.ForgetStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#forgetStatement}.
	 * @param ctx the parse tree
	 */
	void exitForgetStatement(SFMLParser.ForgetStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#inputStatement}.
	 * @param ctx the parse tree
	 */
	void enterInputStatement(SFMLParser.InputStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#inputStatement}.
	 * @param ctx the parse tree
	 */
	void exitInputStatement(SFMLParser.InputStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#outputStatement}.
	 * @param ctx the parse tree
	 */
	void enterOutputStatement(SFMLParser.OutputStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#outputStatement}.
	 * @param ctx the parse tree
	 */
	void exitOutputStatement(SFMLParser.OutputStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#inputResourceLimits}.
	 * @param ctx the parse tree
	 */
	void enterInputResourceLimits(SFMLParser.InputResourceLimitsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#inputResourceLimits}.
	 * @param ctx the parse tree
	 */
	void exitInputResourceLimits(SFMLParser.InputResourceLimitsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#outputResourceLimits}.
	 * @param ctx the parse tree
	 */
	void enterOutputResourceLimits(SFMLParser.OutputResourceLimitsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#outputResourceLimits}.
	 * @param ctx the parse tree
	 */
	void exitOutputResourceLimits(SFMLParser.OutputResourceLimitsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#resourceLimitList}.
	 * @param ctx the parse tree
	 */
	void enterResourceLimitList(SFMLParser.ResourceLimitListContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#resourceLimitList}.
	 * @param ctx the parse tree
	 */
	void exitResourceLimitList(SFMLParser.ResourceLimitListContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#resourceLimit}.
	 * @param ctx the parse tree
	 */
	void enterResourceLimit(SFMLParser.ResourceLimitContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#resourceLimit}.
	 * @param ctx the parse tree
	 */
	void exitResourceLimit(SFMLParser.ResourceLimitContext ctx);
	/**
	 * Enter a parse tree produced by the {@code QuantityRetentionLimit}
	 * labeled alternative in {@link SFMLParser#limit}.
	 * @param ctx the parse tree
	 */
	void enterQuantityRetentionLimit(SFMLParser.QuantityRetentionLimitContext ctx);
	/**
	 * Exit a parse tree produced by the {@code QuantityRetentionLimit}
	 * labeled alternative in {@link SFMLParser#limit}.
	 * @param ctx the parse tree
	 */
	void exitQuantityRetentionLimit(SFMLParser.QuantityRetentionLimitContext ctx);
	/**
	 * Enter a parse tree produced by the {@code RetentionLimit}
	 * labeled alternative in {@link SFMLParser#limit}.
	 * @param ctx the parse tree
	 */
	void enterRetentionLimit(SFMLParser.RetentionLimitContext ctx);
	/**
	 * Exit a parse tree produced by the {@code RetentionLimit}
	 * labeled alternative in {@link SFMLParser#limit}.
	 * @param ctx the parse tree
	 */
	void exitRetentionLimit(SFMLParser.RetentionLimitContext ctx);
	/**
	 * Enter a parse tree produced by the {@code QuantityLimit}
	 * labeled alternative in {@link SFMLParser#limit}.
	 * @param ctx the parse tree
	 */
	void enterQuantityLimit(SFMLParser.QuantityLimitContext ctx);
	/**
	 * Exit a parse tree produced by the {@code QuantityLimit}
	 * labeled alternative in {@link SFMLParser#limit}.
	 * @param ctx the parse tree
	 */
	void exitQuantityLimit(SFMLParser.QuantityLimitContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#quantity}.
	 * @param ctx the parse tree
	 */
	void enterQuantity(SFMLParser.QuantityContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#quantity}.
	 * @param ctx the parse tree
	 */
	void exitQuantity(SFMLParser.QuantityContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#retention}.
	 * @param ctx the parse tree
	 */
	void enterRetention(SFMLParser.RetentionContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#retention}.
	 * @param ctx the parse tree
	 */
	void exitRetention(SFMLParser.RetentionContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#resourceExclusion}.
	 * @param ctx the parse tree
	 */
	void enterResourceExclusion(SFMLParser.ResourceExclusionContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#resourceExclusion}.
	 * @param ctx the parse tree
	 */
	void exitResourceExclusion(SFMLParser.ResourceExclusionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code Resource}
	 * labeled alternative in {@link SFMLParser#resourceId}.
	 * @param ctx the parse tree
	 */
	void enterResource(SFMLParser.ResourceContext ctx);
	/**
	 * Exit a parse tree produced by the {@code Resource}
	 * labeled alternative in {@link SFMLParser#resourceId}.
	 * @param ctx the parse tree
	 */
	void exitResource(SFMLParser.ResourceContext ctx);
	/**
	 * Enter a parse tree produced by the {@code StringResource}
	 * labeled alternative in {@link SFMLParser#resourceId}.
	 * @param ctx the parse tree
	 */
	void enterStringResource(SFMLParser.StringResourceContext ctx);
	/**
	 * Exit a parse tree produced by the {@code StringResource}
	 * labeled alternative in {@link SFMLParser#resourceId}.
	 * @param ctx the parse tree
	 */
	void exitStringResource(SFMLParser.StringResourceContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#resourceIdList}.
	 * @param ctx the parse tree
	 */
	void enterResourceIdList(SFMLParser.ResourceIdListContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#resourceIdList}.
	 * @param ctx the parse tree
	 */
	void exitResourceIdList(SFMLParser.ResourceIdListContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#resourceIdDisjunction}.
	 * @param ctx the parse tree
	 */
	void enterResourceIdDisjunction(SFMLParser.ResourceIdDisjunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#resourceIdDisjunction}.
	 * @param ctx the parse tree
	 */
	void exitResourceIdDisjunction(SFMLParser.ResourceIdDisjunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#with}.
	 * @param ctx the parse tree
	 */
	void enterWith(SFMLParser.WithContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#with}.
	 * @param ctx the parse tree
	 */
	void exitWith(SFMLParser.WithContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WithConjunction}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void enterWithConjunction(SFMLParser.WithConjunctionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WithConjunction}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void exitWithConjunction(SFMLParser.WithConjunctionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WithParen}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void enterWithParen(SFMLParser.WithParenContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WithParen}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void exitWithParen(SFMLParser.WithParenContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WithNegation}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void enterWithNegation(SFMLParser.WithNegationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WithNegation}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void exitWithNegation(SFMLParser.WithNegationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WithTag}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void enterWithTag(SFMLParser.WithTagContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WithTag}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void exitWithTag(SFMLParser.WithTagContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WithDisjunction}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void enterWithDisjunction(SFMLParser.WithDisjunctionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WithDisjunction}
	 * labeled alternative in {@link SFMLParser#withClause}.
	 * @param ctx the parse tree
	 */
	void exitWithDisjunction(SFMLParser.WithDisjunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#tagMatcher}.
	 * @param ctx the parse tree
	 */
	void enterTagMatcher(SFMLParser.TagMatcherContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#tagMatcher}.
	 * @param ctx the parse tree
	 */
	void exitTagMatcher(SFMLParser.TagMatcherContext ctx);
	/**
	 * Enter a parse tree produced by the {@code EachSide}
	 * labeled alternative in {@link SFMLParser#sidequalifier}.
	 * @param ctx the parse tree
	 */
	void enterEachSide(SFMLParser.EachSideContext ctx);
	/**
	 * Exit a parse tree produced by the {@code EachSide}
	 * labeled alternative in {@link SFMLParser#sidequalifier}.
	 * @param ctx the parse tree
	 */
	void exitEachSide(SFMLParser.EachSideContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ListedSides}
	 * labeled alternative in {@link SFMLParser#sidequalifier}.
	 * @param ctx the parse tree
	 */
	void enterListedSides(SFMLParser.ListedSidesContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ListedSides}
	 * labeled alternative in {@link SFMLParser#sidequalifier}.
	 * @param ctx the parse tree
	 */
	void exitListedSides(SFMLParser.ListedSidesContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#side}.
	 * @param ctx the parse tree
	 */
	void enterSide(SFMLParser.SideContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#side}.
	 * @param ctx the parse tree
	 */
	void exitSide(SFMLParser.SideContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#slotqualifier}.
	 * @param ctx the parse tree
	 */
	void enterSlotqualifier(SFMLParser.SlotqualifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#slotqualifier}.
	 * @param ctx the parse tree
	 */
	void exitSlotqualifier(SFMLParser.SlotqualifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#rangeset}.
	 * @param ctx the parse tree
	 */
	void enterRangeset(SFMLParser.RangesetContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#rangeset}.
	 * @param ctx the parse tree
	 */
	void exitRangeset(SFMLParser.RangesetContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#range}.
	 * @param ctx the parse tree
	 */
	void enterRange(SFMLParser.RangeContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#range}.
	 * @param ctx the parse tree
	 */
	void exitRange(SFMLParser.RangeContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void enterIfStatement(SFMLParser.IfStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void exitIfStatement(SFMLParser.IfStatementContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanHas}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanHas(SFMLParser.BooleanHasContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanHas}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanHas(SFMLParser.BooleanHasContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanConjunction}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanConjunction(SFMLParser.BooleanConjunctionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanConjunction}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanConjunction(SFMLParser.BooleanConjunctionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanRedstone}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanRedstone(SFMLParser.BooleanRedstoneContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanRedstone}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanRedstone(SFMLParser.BooleanRedstoneContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanDisjunction}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanDisjunction(SFMLParser.BooleanDisjunctionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanDisjunction}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanDisjunction(SFMLParser.BooleanDisjunctionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanFalse}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanFalse(SFMLParser.BooleanFalseContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanFalse}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanFalse(SFMLParser.BooleanFalseContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanParen}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanParen(SFMLParser.BooleanParenContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanParen}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanParen(SFMLParser.BooleanParenContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanNegation}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanNegation(SFMLParser.BooleanNegationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanNegation}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanNegation(SFMLParser.BooleanNegationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BooleanTrue}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void enterBooleanTrue(SFMLParser.BooleanTrueContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BooleanTrue}
	 * labeled alternative in {@link SFMLParser#boolexpr}.
	 * @param ctx the parse tree
	 */
	void exitBooleanTrue(SFMLParser.BooleanTrueContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#comparisonOp}.
	 * @param ctx the parse tree
	 */
	void enterComparisonOp(SFMLParser.ComparisonOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#comparisonOp}.
	 * @param ctx the parse tree
	 */
	void exitComparisonOp(SFMLParser.ComparisonOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#setOp}.
	 * @param ctx the parse tree
	 */
	void enterSetOp(SFMLParser.SetOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#setOp}.
	 * @param ctx the parse tree
	 */
	void exitSetOp(SFMLParser.SetOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#labelAccess}.
	 * @param ctx the parse tree
	 */
	void enterLabelAccess(SFMLParser.LabelAccessContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#labelAccess}.
	 * @param ctx the parse tree
	 */
	void exitLabelAccess(SFMLParser.LabelAccessContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#roundrobin}.
	 * @param ctx the parse tree
	 */
	void enterRoundrobin(SFMLParser.RoundrobinContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#roundrobin}.
	 * @param ctx the parse tree
	 */
	void exitRoundrobin(SFMLParser.RoundrobinContext ctx);
	/**
	 * Enter a parse tree produced by the {@code RawLabel}
	 * labeled alternative in {@link SFMLParser#label}.
	 * @param ctx the parse tree
	 */
	void enterRawLabel(SFMLParser.RawLabelContext ctx);
	/**
	 * Exit a parse tree produced by the {@code RawLabel}
	 * labeled alternative in {@link SFMLParser#label}.
	 * @param ctx the parse tree
	 */
	void exitRawLabel(SFMLParser.RawLabelContext ctx);
	/**
	 * Enter a parse tree produced by the {@code StringLabel}
	 * labeled alternative in {@link SFMLParser#label}.
	 * @param ctx the parse tree
	 */
	void enterStringLabel(SFMLParser.StringLabelContext ctx);
	/**
	 * Exit a parse tree produced by the {@code StringLabel}
	 * labeled alternative in {@link SFMLParser#label}.
	 * @param ctx the parse tree
	 */
	void exitStringLabel(SFMLParser.StringLabelContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#emptyslots}.
	 * @param ctx the parse tree
	 */
	void enterEmptyslots(SFMLParser.EmptyslotsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#emptyslots}.
	 * @param ctx the parse tree
	 */
	void exitEmptyslots(SFMLParser.EmptyslotsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(SFMLParser.IdentifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(SFMLParser.IdentifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#string}.
	 * @param ctx the parse tree
	 */
	void enterString(SFMLParser.StringContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#string}.
	 * @param ctx the parse tree
	 */
	void exitString(SFMLParser.StringContext ctx);
	/**
	 * Enter a parse tree produced by {@link SFMLParser#number}.
	 * @param ctx the parse tree
	 */
	void enterNumber(SFMLParser.NumberContext ctx);
	/**
	 * Exit a parse tree produced by {@link SFMLParser#number}.
	 * @param ctx the parse tree
	 */
	void exitNumber(SFMLParser.NumberContext ctx);
}