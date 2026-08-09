// Generated from ./syntaxes/SFML.g4 by ANTLR 4.9.0-SNAPSHOT


import { ParseTreeListener } from "antlr4ts/tree/ParseTreeListener";

import { ResourceContext } from "./SFMLParser";
import { StringResourceContext } from "./SFMLParser";
import { BooleanTrueContext } from "./SFMLParser";
import { BooleanFalseContext } from "./SFMLParser";
import { BooleanParenContext } from "./SFMLParser";
import { BooleanNegationContext } from "./SFMLParser";
import { BooleanConjunctionContext } from "./SFMLParser";
import { BooleanDisjunctionContext } from "./SFMLParser";
import { BooleanHasContext } from "./SFMLParser";
import { BooleanRedstoneContext } from "./SFMLParser";
import { EachSideContext } from "./SFMLParser";
import { ListedSidesContext } from "./SFMLParser";
import { QuantityRetentionLimitContext } from "./SFMLParser";
import { RetentionLimitContext } from "./SFMLParser";
import { QuantityLimitContext } from "./SFMLParser";
import { TimerTriggerContext } from "./SFMLParser";
import { PulseTriggerContext } from "./SFMLParser";
import { RawLabelContext } from "./SFMLParser";
import { StringLabelContext } from "./SFMLParser";
import { WithParenContext } from "./SFMLParser";
import { WithNegationContext } from "./SFMLParser";
import { WithConjunctionContext } from "./SFMLParser";
import { WithDisjunctionContext } from "./SFMLParser";
import { WithTagContext } from "./SFMLParser";
import { IntervalSpaceContext } from "./SFMLParser";
import { IntervalNoSpaceContext } from "./SFMLParser";
import { ProgramContext } from "./SFMLParser";
import { NameContext } from "./SFMLParser";
import { TriggerContext } from "./SFMLParser";
import { IntervalContext } from "./SFMLParser";
import { BlockContext } from "./SFMLParser";
import { StatementContext } from "./SFMLParser";
import { ForgetStatementContext } from "./SFMLParser";
import { InputStatementContext } from "./SFMLParser";
import { OutputStatementContext } from "./SFMLParser";
import { InputResourceLimitsContext } from "./SFMLParser";
import { OutputResourceLimitsContext } from "./SFMLParser";
import { ResourceLimitListContext } from "./SFMLParser";
import { ResourceLimitContext } from "./SFMLParser";
import { LimitContext } from "./SFMLParser";
import { QuantityContext } from "./SFMLParser";
import { RetentionContext } from "./SFMLParser";
import { ResourceExclusionContext } from "./SFMLParser";
import { ResourceIdContext } from "./SFMLParser";
import { ResourceIdListContext } from "./SFMLParser";
import { ResourceIdDisjunctionContext } from "./SFMLParser";
import { WithContext } from "./SFMLParser";
import { WithClauseContext } from "./SFMLParser";
import { TagMatcherContext } from "./SFMLParser";
import { SidequalifierContext } from "./SFMLParser";
import { SideContext } from "./SFMLParser";
import { SlotqualifierContext } from "./SFMLParser";
import { RangesetContext } from "./SFMLParser";
import { RangeContext } from "./SFMLParser";
import { IfStatementContext } from "./SFMLParser";
import { BoolexprContext } from "./SFMLParser";
import { ComparisonOpContext } from "./SFMLParser";
import { SetOpContext } from "./SFMLParser";
import { LabelAccessContext } from "./SFMLParser";
import { RoundrobinContext } from "./SFMLParser";
import { LabelContext } from "./SFMLParser";
import { IdentifierContext } from "./SFMLParser";
import { StringContext } from "./SFMLParser";
import { NumberContext } from "./SFMLParser";


/**
 * This interface defines a complete listener for a parse tree produced by
 * `SFMLParser`.
 */
export interface SFMLListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by the `Resource`
	 * labeled alternative in `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 */
	enterResource?: (ctx: ResourceContext) => void;
	/**
	 * Exit a parse tree produced by the `Resource`
	 * labeled alternative in `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 */
	exitResource?: (ctx: ResourceContext) => void;

	/**
	 * Enter a parse tree produced by the `StringResource`
	 * labeled alternative in `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 */
	enterStringResource?: (ctx: StringResourceContext) => void;
	/**
	 * Exit a parse tree produced by the `StringResource`
	 * labeled alternative in `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 */
	exitStringResource?: (ctx: StringResourceContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanTrue`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanTrue?: (ctx: BooleanTrueContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanTrue`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanTrue?: (ctx: BooleanTrueContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanFalse`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanFalse?: (ctx: BooleanFalseContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanFalse`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanFalse?: (ctx: BooleanFalseContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanParen`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanParen?: (ctx: BooleanParenContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanParen`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanParen?: (ctx: BooleanParenContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanNegation`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanNegation?: (ctx: BooleanNegationContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanNegation`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanNegation?: (ctx: BooleanNegationContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanConjunction`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanConjunction?: (ctx: BooleanConjunctionContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanConjunction`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanConjunction?: (ctx: BooleanConjunctionContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanDisjunction`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanDisjunction?: (ctx: BooleanDisjunctionContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanDisjunction`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanDisjunction?: (ctx: BooleanDisjunctionContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanHas`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanHas?: (ctx: BooleanHasContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanHas`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanHas?: (ctx: BooleanHasContext) => void;

	/**
	 * Enter a parse tree produced by the `BooleanRedstone`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBooleanRedstone?: (ctx: BooleanRedstoneContext) => void;
	/**
	 * Exit a parse tree produced by the `BooleanRedstone`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBooleanRedstone?: (ctx: BooleanRedstoneContext) => void;

	/**
	 * Enter a parse tree produced by the `EachSide`
	 * labeled alternative in `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 */
	enterEachSide?: (ctx: EachSideContext) => void;
	/**
	 * Exit a parse tree produced by the `EachSide`
	 * labeled alternative in `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 */
	exitEachSide?: (ctx: EachSideContext) => void;

	/**
	 * Enter a parse tree produced by the `ListedSides`
	 * labeled alternative in `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 */
	enterListedSides?: (ctx: ListedSidesContext) => void;
	/**
	 * Exit a parse tree produced by the `ListedSides`
	 * labeled alternative in `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 */
	exitListedSides?: (ctx: ListedSidesContext) => void;

	/**
	 * Enter a parse tree produced by the `QuantityRetentionLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	enterQuantityRetentionLimit?: (ctx: QuantityRetentionLimitContext) => void;
	/**
	 * Exit a parse tree produced by the `QuantityRetentionLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	exitQuantityRetentionLimit?: (ctx: QuantityRetentionLimitContext) => void;

	/**
	 * Enter a parse tree produced by the `RetentionLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	enterRetentionLimit?: (ctx: RetentionLimitContext) => void;
	/**
	 * Exit a parse tree produced by the `RetentionLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	exitRetentionLimit?: (ctx: RetentionLimitContext) => void;

	/**
	 * Enter a parse tree produced by the `QuantityLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	enterQuantityLimit?: (ctx: QuantityLimitContext) => void;
	/**
	 * Exit a parse tree produced by the `QuantityLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	exitQuantityLimit?: (ctx: QuantityLimitContext) => void;

	/**
	 * Enter a parse tree produced by the `TimerTrigger`
	 * labeled alternative in `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 */
	enterTimerTrigger?: (ctx: TimerTriggerContext) => void;
	/**
	 * Exit a parse tree produced by the `TimerTrigger`
	 * labeled alternative in `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 */
	exitTimerTrigger?: (ctx: TimerTriggerContext) => void;

	/**
	 * Enter a parse tree produced by the `PulseTrigger`
	 * labeled alternative in `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 */
	enterPulseTrigger?: (ctx: PulseTriggerContext) => void;
	/**
	 * Exit a parse tree produced by the `PulseTrigger`
	 * labeled alternative in `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 */
	exitPulseTrigger?: (ctx: PulseTriggerContext) => void;

	/**
	 * Enter a parse tree produced by the `RawLabel`
	 * labeled alternative in `SFMLParser.label`.
	 * @param ctx the parse tree
	 */
	enterRawLabel?: (ctx: RawLabelContext) => void;
	/**
	 * Exit a parse tree produced by the `RawLabel`
	 * labeled alternative in `SFMLParser.label`.
	 * @param ctx the parse tree
	 */
	exitRawLabel?: (ctx: RawLabelContext) => void;

	/**
	 * Enter a parse tree produced by the `StringLabel`
	 * labeled alternative in `SFMLParser.label`.
	 * @param ctx the parse tree
	 */
	enterStringLabel?: (ctx: StringLabelContext) => void;
	/**
	 * Exit a parse tree produced by the `StringLabel`
	 * labeled alternative in `SFMLParser.label`.
	 * @param ctx the parse tree
	 */
	exitStringLabel?: (ctx: StringLabelContext) => void;

	/**
	 * Enter a parse tree produced by the `WithParen`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	enterWithParen?: (ctx: WithParenContext) => void;
	/**
	 * Exit a parse tree produced by the `WithParen`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	exitWithParen?: (ctx: WithParenContext) => void;

	/**
	 * Enter a parse tree produced by the `WithNegation`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	enterWithNegation?: (ctx: WithNegationContext) => void;
	/**
	 * Exit a parse tree produced by the `WithNegation`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	exitWithNegation?: (ctx: WithNegationContext) => void;

	/**
	 * Enter a parse tree produced by the `WithConjunction`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	enterWithConjunction?: (ctx: WithConjunctionContext) => void;
	/**
	 * Exit a parse tree produced by the `WithConjunction`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	exitWithConjunction?: (ctx: WithConjunctionContext) => void;

	/**
	 * Enter a parse tree produced by the `WithDisjunction`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	enterWithDisjunction?: (ctx: WithDisjunctionContext) => void;
	/**
	 * Exit a parse tree produced by the `WithDisjunction`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	exitWithDisjunction?: (ctx: WithDisjunctionContext) => void;

	/**
	 * Enter a parse tree produced by the `WithTag`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	enterWithTag?: (ctx: WithTagContext) => void;
	/**
	 * Exit a parse tree produced by the `WithTag`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	exitWithTag?: (ctx: WithTagContext) => void;

	/**
	 * Enter a parse tree produced by the `IntervalSpace`
	 * labeled alternative in `SFMLParser.interval`.
	 * @param ctx the parse tree
	 */
	enterIntervalSpace?: (ctx: IntervalSpaceContext) => void;
	/**
	 * Exit a parse tree produced by the `IntervalSpace`
	 * labeled alternative in `SFMLParser.interval`.
	 * @param ctx the parse tree
	 */
	exitIntervalSpace?: (ctx: IntervalSpaceContext) => void;

	/**
	 * Enter a parse tree produced by the `IntervalNoSpace`
	 * labeled alternative in `SFMLParser.interval`.
	 * @param ctx the parse tree
	 */
	enterIntervalNoSpace?: (ctx: IntervalNoSpaceContext) => void;
	/**
	 * Exit a parse tree produced by the `IntervalNoSpace`
	 * labeled alternative in `SFMLParser.interval`.
	 * @param ctx the parse tree
	 */
	exitIntervalNoSpace?: (ctx: IntervalNoSpaceContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.program`.
	 * @param ctx the parse tree
	 */
	enterProgram?: (ctx: ProgramContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.program`.
	 * @param ctx the parse tree
	 */
	exitProgram?: (ctx: ProgramContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.name`.
	 * @param ctx the parse tree
	 */
	enterName?: (ctx: NameContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.name`.
	 * @param ctx the parse tree
	 */
	exitName?: (ctx: NameContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 */
	enterTrigger?: (ctx: TriggerContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 */
	exitTrigger?: (ctx: TriggerContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.interval`.
	 * @param ctx the parse tree
	 */
	enterInterval?: (ctx: IntervalContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.interval`.
	 * @param ctx the parse tree
	 */
	exitInterval?: (ctx: IntervalContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.block`.
	 * @param ctx the parse tree
	 */
	enterBlock?: (ctx: BlockContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.block`.
	 * @param ctx the parse tree
	 */
	exitBlock?: (ctx: BlockContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.statement`.
	 * @param ctx the parse tree
	 */
	enterStatement?: (ctx: StatementContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.statement`.
	 * @param ctx the parse tree
	 */
	exitStatement?: (ctx: StatementContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.forgetStatement`.
	 * @param ctx the parse tree
	 */
	enterForgetStatement?: (ctx: ForgetStatementContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.forgetStatement`.
	 * @param ctx the parse tree
	 */
	exitForgetStatement?: (ctx: ForgetStatementContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.inputStatement`.
	 * @param ctx the parse tree
	 */
	enterInputStatement?: (ctx: InputStatementContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.inputStatement`.
	 * @param ctx the parse tree
	 */
	exitInputStatement?: (ctx: InputStatementContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.outputStatement`.
	 * @param ctx the parse tree
	 */
	enterOutputStatement?: (ctx: OutputStatementContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.outputStatement`.
	 * @param ctx the parse tree
	 */
	exitOutputStatement?: (ctx: OutputStatementContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.inputResourceLimits`.
	 * @param ctx the parse tree
	 */
	enterInputResourceLimits?: (ctx: InputResourceLimitsContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.inputResourceLimits`.
	 * @param ctx the parse tree
	 */
	exitInputResourceLimits?: (ctx: InputResourceLimitsContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.outputResourceLimits`.
	 * @param ctx the parse tree
	 */
	enterOutputResourceLimits?: (ctx: OutputResourceLimitsContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.outputResourceLimits`.
	 * @param ctx the parse tree
	 */
	exitOutputResourceLimits?: (ctx: OutputResourceLimitsContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.resourceLimitList`.
	 * @param ctx the parse tree
	 */
	enterResourceLimitList?: (ctx: ResourceLimitListContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.resourceLimitList`.
	 * @param ctx the parse tree
	 */
	exitResourceLimitList?: (ctx: ResourceLimitListContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.resourceLimit`.
	 * @param ctx the parse tree
	 */
	enterResourceLimit?: (ctx: ResourceLimitContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.resourceLimit`.
	 * @param ctx the parse tree
	 */
	exitResourceLimit?: (ctx: ResourceLimitContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	enterLimit?: (ctx: LimitContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.limit`.
	 * @param ctx the parse tree
	 */
	exitLimit?: (ctx: LimitContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.quantity`.
	 * @param ctx the parse tree
	 */
	enterQuantity?: (ctx: QuantityContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.quantity`.
	 * @param ctx the parse tree
	 */
	exitQuantity?: (ctx: QuantityContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.retention`.
	 * @param ctx the parse tree
	 */
	enterRetention?: (ctx: RetentionContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.retention`.
	 * @param ctx the parse tree
	 */
	exitRetention?: (ctx: RetentionContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.resourceExclusion`.
	 * @param ctx the parse tree
	 */
	enterResourceExclusion?: (ctx: ResourceExclusionContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.resourceExclusion`.
	 * @param ctx the parse tree
	 */
	exitResourceExclusion?: (ctx: ResourceExclusionContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 */
	enterResourceId?: (ctx: ResourceIdContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 */
	exitResourceId?: (ctx: ResourceIdContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.resourceIdList`.
	 * @param ctx the parse tree
	 */
	enterResourceIdList?: (ctx: ResourceIdListContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.resourceIdList`.
	 * @param ctx the parse tree
	 */
	exitResourceIdList?: (ctx: ResourceIdListContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.resourceIdDisjunction`.
	 * @param ctx the parse tree
	 */
	enterResourceIdDisjunction?: (ctx: ResourceIdDisjunctionContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.resourceIdDisjunction`.
	 * @param ctx the parse tree
	 */
	exitResourceIdDisjunction?: (ctx: ResourceIdDisjunctionContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.with`.
	 * @param ctx the parse tree
	 */
	enterWith?: (ctx: WithContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.with`.
	 * @param ctx the parse tree
	 */
	exitWith?: (ctx: WithContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	enterWithClause?: (ctx: WithClauseContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 */
	exitWithClause?: (ctx: WithClauseContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.tagMatcher`.
	 * @param ctx the parse tree
	 */
	enterTagMatcher?: (ctx: TagMatcherContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.tagMatcher`.
	 * @param ctx the parse tree
	 */
	exitTagMatcher?: (ctx: TagMatcherContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 */
	enterSidequalifier?: (ctx: SidequalifierContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 */
	exitSidequalifier?: (ctx: SidequalifierContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.side`.
	 * @param ctx the parse tree
	 */
	enterSide?: (ctx: SideContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.side`.
	 * @param ctx the parse tree
	 */
	exitSide?: (ctx: SideContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.slotqualifier`.
	 * @param ctx the parse tree
	 */
	enterSlotqualifier?: (ctx: SlotqualifierContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.slotqualifier`.
	 * @param ctx the parse tree
	 */
	exitSlotqualifier?: (ctx: SlotqualifierContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.rangeset`.
	 * @param ctx the parse tree
	 */
	enterRangeset?: (ctx: RangesetContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.rangeset`.
	 * @param ctx the parse tree
	 */
	exitRangeset?: (ctx: RangesetContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.range`.
	 * @param ctx the parse tree
	 */
	enterRange?: (ctx: RangeContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.range`.
	 * @param ctx the parse tree
	 */
	exitRange?: (ctx: RangeContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.ifStatement`.
	 * @param ctx the parse tree
	 */
	enterIfStatement?: (ctx: IfStatementContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.ifStatement`.
	 * @param ctx the parse tree
	 */
	exitIfStatement?: (ctx: IfStatementContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	enterBoolexpr?: (ctx: BoolexprContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 */
	exitBoolexpr?: (ctx: BoolexprContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.comparisonOp`.
	 * @param ctx the parse tree
	 */
	enterComparisonOp?: (ctx: ComparisonOpContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.comparisonOp`.
	 * @param ctx the parse tree
	 */
	exitComparisonOp?: (ctx: ComparisonOpContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.setOp`.
	 * @param ctx the parse tree
	 */
	enterSetOp?: (ctx: SetOpContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.setOp`.
	 * @param ctx the parse tree
	 */
	exitSetOp?: (ctx: SetOpContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.labelAccess`.
	 * @param ctx the parse tree
	 */
	enterLabelAccess?: (ctx: LabelAccessContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.labelAccess`.
	 * @param ctx the parse tree
	 */
	exitLabelAccess?: (ctx: LabelAccessContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.roundrobin`.
	 * @param ctx the parse tree
	 */
	enterRoundrobin?: (ctx: RoundrobinContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.roundrobin`.
	 * @param ctx the parse tree
	 */
	exitRoundrobin?: (ctx: RoundrobinContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.label`.
	 * @param ctx the parse tree
	 */
	enterLabel?: (ctx: LabelContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.label`.
	 * @param ctx the parse tree
	 */
	exitLabel?: (ctx: LabelContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.identifier`.
	 * @param ctx the parse tree
	 */
	enterIdentifier?: (ctx: IdentifierContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.identifier`.
	 * @param ctx the parse tree
	 */
	exitIdentifier?: (ctx: IdentifierContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.string`.
	 * @param ctx the parse tree
	 */
	enterString?: (ctx: StringContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.string`.
	 * @param ctx the parse tree
	 */
	exitString?: (ctx: StringContext) => void;

	/**
	 * Enter a parse tree produced by `SFMLParser.number`.
	 * @param ctx the parse tree
	 */
	enterNumber?: (ctx: NumberContext) => void;
	/**
	 * Exit a parse tree produced by `SFMLParser.number`.
	 * @param ctx the parse tree
	 */
	exitNumber?: (ctx: NumberContext) => void;
}

