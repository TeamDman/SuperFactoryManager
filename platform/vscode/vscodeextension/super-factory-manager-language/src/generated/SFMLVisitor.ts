// Generated from ./syntaxes/SFML.g4 by ANTLR 4.9.0-SNAPSHOT


import { ParseTreeVisitor } from "antlr4ts/tree/ParseTreeVisitor";

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
 * This interface defines a complete generic visitor for a parse tree produced
 * by `SFMLParser`.
 *
 * @param <Result> The return type of the visit operation. Use `void` for
 * operations with no return type.
 */
export interface SFMLVisitor<Result> extends ParseTreeVisitor<Result> {
	/**
	 * Visit a parse tree produced by the `Resource`
	 * labeled alternative in `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitResource?: (ctx: ResourceContext) => Result;

	/**
	 * Visit a parse tree produced by the `StringResource`
	 * labeled alternative in `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStringResource?: (ctx: StringResourceContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanTrue`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanTrue?: (ctx: BooleanTrueContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanFalse`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanFalse?: (ctx: BooleanFalseContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanParen`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanParen?: (ctx: BooleanParenContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanNegation`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanNegation?: (ctx: BooleanNegationContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanConjunction`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanConjunction?: (ctx: BooleanConjunctionContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanDisjunction`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanDisjunction?: (ctx: BooleanDisjunctionContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanHas`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanHas?: (ctx: BooleanHasContext) => Result;

	/**
	 * Visit a parse tree produced by the `BooleanRedstone`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanRedstone?: (ctx: BooleanRedstoneContext) => Result;

	/**
	 * Visit a parse tree produced by the `EachSide`
	 * labeled alternative in `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitEachSide?: (ctx: EachSideContext) => Result;

	/**
	 * Visit a parse tree produced by the `ListedSides`
	 * labeled alternative in `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitListedSides?: (ctx: ListedSidesContext) => Result;

	/**
	 * Visit a parse tree produced by the `QuantityRetentionLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitQuantityRetentionLimit?: (ctx: QuantityRetentionLimitContext) => Result;

	/**
	 * Visit a parse tree produced by the `RetentionLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRetentionLimit?: (ctx: RetentionLimitContext) => Result;

	/**
	 * Visit a parse tree produced by the `QuantityLimit`
	 * labeled alternative in `SFMLParser.limit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitQuantityLimit?: (ctx: QuantityLimitContext) => Result;

	/**
	 * Visit a parse tree produced by the `TimerTrigger`
	 * labeled alternative in `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitTimerTrigger?: (ctx: TimerTriggerContext) => Result;

	/**
	 * Visit a parse tree produced by the `PulseTrigger`
	 * labeled alternative in `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitPulseTrigger?: (ctx: PulseTriggerContext) => Result;

	/**
	 * Visit a parse tree produced by the `RawLabel`
	 * labeled alternative in `SFMLParser.label`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRawLabel?: (ctx: RawLabelContext) => Result;

	/**
	 * Visit a parse tree produced by the `StringLabel`
	 * labeled alternative in `SFMLParser.label`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStringLabel?: (ctx: StringLabelContext) => Result;

	/**
	 * Visit a parse tree produced by the `WithParen`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWithParen?: (ctx: WithParenContext) => Result;

	/**
	 * Visit a parse tree produced by the `WithNegation`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWithNegation?: (ctx: WithNegationContext) => Result;

	/**
	 * Visit a parse tree produced by the `WithConjunction`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWithConjunction?: (ctx: WithConjunctionContext) => Result;

	/**
	 * Visit a parse tree produced by the `WithDisjunction`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWithDisjunction?: (ctx: WithDisjunctionContext) => Result;

	/**
	 * Visit a parse tree produced by the `WithTag`
	 * labeled alternative in `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWithTag?: (ctx: WithTagContext) => Result;

	/**
	 * Visit a parse tree produced by the `IntervalSpace`
	 * labeled alternative in `SFMLParser.interval`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitIntervalSpace?: (ctx: IntervalSpaceContext) => Result;

	/**
	 * Visit a parse tree produced by the `IntervalNoSpace`
	 * labeled alternative in `SFMLParser.interval`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitIntervalNoSpace?: (ctx: IntervalNoSpaceContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.program`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitProgram?: (ctx: ProgramContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.name`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitName?: (ctx: NameContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitTrigger?: (ctx: TriggerContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.interval`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitInterval?: (ctx: IntervalContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.block`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBlock?: (ctx: BlockContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.statement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStatement?: (ctx: StatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.forgetStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitForgetStatement?: (ctx: ForgetStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.inputStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitInputStatement?: (ctx: InputStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.outputStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitOutputStatement?: (ctx: OutputStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.inputResourceLimits`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitInputResourceLimits?: (ctx: InputResourceLimitsContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.outputResourceLimits`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitOutputResourceLimits?: (ctx: OutputResourceLimitsContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.resourceLimitList`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitResourceLimitList?: (ctx: ResourceLimitListContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.resourceLimit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitResourceLimit?: (ctx: ResourceLimitContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.limit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLimit?: (ctx: LimitContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.quantity`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitQuantity?: (ctx: QuantityContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.retention`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRetention?: (ctx: RetentionContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.resourceExclusion`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitResourceExclusion?: (ctx: ResourceExclusionContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.resourceId`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitResourceId?: (ctx: ResourceIdContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.resourceIdList`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitResourceIdList?: (ctx: ResourceIdListContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.resourceIdDisjunction`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitResourceIdDisjunction?: (ctx: ResourceIdDisjunctionContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.with`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWith?: (ctx: WithContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.withClause`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWithClause?: (ctx: WithClauseContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.tagMatcher`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitTagMatcher?: (ctx: TagMatcherContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.sidequalifier`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitSidequalifier?: (ctx: SidequalifierContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.side`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitSide?: (ctx: SideContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.slotqualifier`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitSlotqualifier?: (ctx: SlotqualifierContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.rangeset`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRangeset?: (ctx: RangesetContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.range`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRange?: (ctx: RangeContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.ifStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitIfStatement?: (ctx: IfStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBoolexpr?: (ctx: BoolexprContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.comparisonOp`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitComparisonOp?: (ctx: ComparisonOpContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.setOp`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitSetOp?: (ctx: SetOpContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.labelAccess`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLabelAccess?: (ctx: LabelAccessContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.roundrobin`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRoundrobin?: (ctx: RoundrobinContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.label`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLabel?: (ctx: LabelContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.identifier`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitIdentifier?: (ctx: IdentifierContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.string`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitString?: (ctx: StringContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.number`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitNumber?: (ctx: NumberContext) => Result;
}

