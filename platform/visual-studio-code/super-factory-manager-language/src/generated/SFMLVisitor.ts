// Generated from SFML.g4 by ANTLR 4.9.0-SNAPSHOT


import { ParseTreeVisitor } from "antlr4ts/tree/ParseTreeVisitor";

import { ResourceContext } from "./SFMLParser";
import { StringResourceContext } from "./SFMLParser";
import { LiteralPatternFieldContext } from "./SFMLParser";
import { LikePatternFieldContext } from "./SFMLParser";
import { AliasPatternFieldContext } from "./SFMLParser";
import { EachSideContext } from "./SFMLParser";
import { ListedSidesContext } from "./SFMLParser";
import { CapabilityInputSelectionContext } from "./SFMLParser";
import { PatternInputSelectionContext } from "./SFMLParser";
import { QuantityRetentionLimitContext } from "./SFMLParser";
import { RetentionLimitContext } from "./SFMLParser";
import { QuantityLimitContext } from "./SFMLParser";
import { IntervalSpaceContext } from "./SFMLParser";
import { IntervalNoSpaceContext } from "./SFMLParser";
import { NewGuidFieldValueContext } from "./SFMLParser";
import { LiteralFieldValueContext } from "./SFMLParser";
import { VariableFieldValueContext } from "./SFMLParser";
import { InvokeTextValueExpressionContext } from "./SFMLParser";
import { ObjectConstructionValueExpressionContext } from "./SFMLParser";
import { BooleanTrueContext } from "./SFMLParser";
import { BooleanFalseContext } from "./SFMLParser";
import { BooleanParenContext } from "./SFMLParser";
import { BooleanNegationContext } from "./SFMLParser";
import { BooleanConjunctionContext } from "./SFMLParser";
import { BooleanDisjunctionContext } from "./SFMLParser";
import { BooleanHasContext } from "./SFMLParser";
import { BooleanRedstoneContext } from "./SFMLParser";
import { BooleanFrameModuloContext } from "./SFMLParser";
import { GuidValuePatternContext } from "./SFMLParser";
import { StringValuePatternContext } from "./SFMLParser";
import { LiteralValuePatternContext } from "./SFMLParser";
import { ObjectValuePatternContext } from "./SFMLParser";
import { AliasValuePatternContext } from "./SFMLParser";
import { TimerTriggerContext } from "./SFMLParser";
import { PulseTriggerContext } from "./SFMLParser";
import { FrameTriggerContext } from "./SFMLParser";
import { RawLabelContext } from "./SFMLParser";
import { StringLabelContext } from "./SFMLParser";
import { WithParenContext } from "./SFMLParser";
import { WithNegationContext } from "./SFMLParser";
import { WithConjunctionContext } from "./SFMLParser";
import { WithDisjunctionContext } from "./SFMLParser";
import { WithTagContext } from "./SFMLParser";
import { PlayerDeclarationContext } from "./SFMLParser";
import { PatternDeclarationContext } from "./SFMLParser";
import { ProgramContext } from "./SFMLParser";
import { ExecutionSideDeclarationContext } from "./SFMLParser";
import { NameContext } from "./SFMLParser";
import { DeclarationContext } from "./SFMLParser";
import { ValuePatternContext } from "./SFMLParser";
import { PatternFieldContext } from "./SFMLParser";
import { TriggerContext } from "./SFMLParser";
import { FrameLabelsContext } from "./SFMLParser";
import { IntervalContext } from "./SFMLParser";
import { TimeUnitContext } from "./SFMLParser";
import { BlockContext } from "./SFMLParser";
import { StatementContext } from "./SFMLParser";
import { RenderImageStatementContext } from "./SFMLParser";
import { LetValueStatementContext } from "./SFMLParser";
import { ValueExpressionContext } from "./SFMLParser";
import { ConstructionFieldContext } from "./SFMLParser";
import { FieldValueExpressionContext } from "./SFMLParser";
import { CreateStatementContext } from "./SFMLParser";
import { BroadcastStatementContext } from "./SFMLParser";
import { ForgetStatementContext } from "./SFMLParser";
import { InputStatementContext } from "./SFMLParser";
import { InputSelectionContext } from "./SFMLParser";
import { InputBindingContext } from "./SFMLParser";
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
import { QualifiedIdContext } from "./SFMLParser";
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
import { EmptyslotsContext } from "./SFMLParser";
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
	 * Visit a parse tree produced by the `LiteralPatternField`
	 * labeled alternative in `SFMLParser.patternField`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLiteralPatternField?: (ctx: LiteralPatternFieldContext) => Result;

	/**
	 * Visit a parse tree produced by the `LikePatternField`
	 * labeled alternative in `SFMLParser.patternField`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLikePatternField?: (ctx: LikePatternFieldContext) => Result;

	/**
	 * Visit a parse tree produced by the `AliasPatternField`
	 * labeled alternative in `SFMLParser.patternField`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitAliasPatternField?: (ctx: AliasPatternFieldContext) => Result;

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
	 * Visit a parse tree produced by the `CapabilityInputSelection`
	 * labeled alternative in `SFMLParser.inputSelection`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitCapabilityInputSelection?: (ctx: CapabilityInputSelectionContext) => Result;

	/**
	 * Visit a parse tree produced by the `PatternInputSelection`
	 * labeled alternative in `SFMLParser.inputSelection`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitPatternInputSelection?: (ctx: PatternInputSelectionContext) => Result;

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
	 * Visit a parse tree produced by the `NewGuidFieldValue`
	 * labeled alternative in `SFMLParser.fieldValueExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitNewGuidFieldValue?: (ctx: NewGuidFieldValueContext) => Result;

	/**
	 * Visit a parse tree produced by the `LiteralFieldValue`
	 * labeled alternative in `SFMLParser.fieldValueExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLiteralFieldValue?: (ctx: LiteralFieldValueContext) => Result;

	/**
	 * Visit a parse tree produced by the `VariableFieldValue`
	 * labeled alternative in `SFMLParser.fieldValueExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitVariableFieldValue?: (ctx: VariableFieldValueContext) => Result;

	/**
	 * Visit a parse tree produced by the `InvokeTextValueExpression`
	 * labeled alternative in `SFMLParser.valueExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitInvokeTextValueExpression?: (ctx: InvokeTextValueExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by the `ObjectConstructionValueExpression`
	 * labeled alternative in `SFMLParser.valueExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitObjectConstructionValueExpression?: (ctx: ObjectConstructionValueExpressionContext) => Result;

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
	 * Visit a parse tree produced by the `BooleanFrameModulo`
	 * labeled alternative in `SFMLParser.boolexpr`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBooleanFrameModulo?: (ctx: BooleanFrameModuloContext) => Result;

	/**
	 * Visit a parse tree produced by the `GuidValuePattern`
	 * labeled alternative in `SFMLParser.valuePattern`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitGuidValuePattern?: (ctx: GuidValuePatternContext) => Result;

	/**
	 * Visit a parse tree produced by the `StringValuePattern`
	 * labeled alternative in `SFMLParser.valuePattern`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStringValuePattern?: (ctx: StringValuePatternContext) => Result;

	/**
	 * Visit a parse tree produced by the `LiteralValuePattern`
	 * labeled alternative in `SFMLParser.valuePattern`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLiteralValuePattern?: (ctx: LiteralValuePatternContext) => Result;

	/**
	 * Visit a parse tree produced by the `ObjectValuePattern`
	 * labeled alternative in `SFMLParser.valuePattern`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitObjectValuePattern?: (ctx: ObjectValuePatternContext) => Result;

	/**
	 * Visit a parse tree produced by the `AliasValuePattern`
	 * labeled alternative in `SFMLParser.valuePattern`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitAliasValuePattern?: (ctx: AliasValuePatternContext) => Result;

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
	 * Visit a parse tree produced by the `FrameTrigger`
	 * labeled alternative in `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFrameTrigger?: (ctx: FrameTriggerContext) => Result;

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
	 * Visit a parse tree produced by the `PlayerDeclaration`
	 * labeled alternative in `SFMLParser.declaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitPlayerDeclaration?: (ctx: PlayerDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by the `PatternDeclaration`
	 * labeled alternative in `SFMLParser.declaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitPatternDeclaration?: (ctx: PatternDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.program`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitProgram?: (ctx: ProgramContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.executionSideDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitExecutionSideDeclaration?: (ctx: ExecutionSideDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.name`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitName?: (ctx: NameContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.declaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitDeclaration?: (ctx: DeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.valuePattern`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitValuePattern?: (ctx: ValuePatternContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.patternField`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitPatternField?: (ctx: PatternFieldContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.trigger`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitTrigger?: (ctx: TriggerContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.frameLabels`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFrameLabels?: (ctx: FrameLabelsContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.interval`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitInterval?: (ctx: IntervalContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.timeUnit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitTimeUnit?: (ctx: TimeUnitContext) => Result;

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
	 * Visit a parse tree produced by `SFMLParser.renderImageStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRenderImageStatement?: (ctx: RenderImageStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.letValueStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitLetValueStatement?: (ctx: LetValueStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.valueExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitValueExpression?: (ctx: ValueExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.constructionField`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitConstructionField?: (ctx: ConstructionFieldContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.fieldValueExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFieldValueExpression?: (ctx: FieldValueExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.createStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitCreateStatement?: (ctx: CreateStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.broadcastStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBroadcastStatement?: (ctx: BroadcastStatementContext) => Result;

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
	 * Visit a parse tree produced by `SFMLParser.inputSelection`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitInputSelection?: (ctx: InputSelectionContext) => Result;

	/**
	 * Visit a parse tree produced by `SFMLParser.inputBinding`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitInputBinding?: (ctx: InputBindingContext) => Result;

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
	 * Visit a parse tree produced by `SFMLParser.qualifiedId`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitQualifiedId?: (ctx: QualifiedIdContext) => Result;

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
	 * Visit a parse tree produced by `SFMLParser.emptyslots`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitEmptyslots?: (ctx: EmptyslotsContext) => Result;

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

