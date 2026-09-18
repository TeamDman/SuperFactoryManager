// Generated from ./syntaxes/SFML.g4 by ANTLR 4.9.0-SNAPSHOT


import { ATN } from "antlr4ts/atn/ATN";
import { ATNDeserializer } from "antlr4ts/atn/ATNDeserializer";
import { FailedPredicateException } from "antlr4ts/FailedPredicateException";
import { NotNull } from "antlr4ts/Decorators";
import { NoViableAltException } from "antlr4ts/NoViableAltException";
import { Override } from "antlr4ts/Decorators";
import { Parser } from "antlr4ts/Parser";
import { ParserRuleContext } from "antlr4ts/ParserRuleContext";
import { ParserATNSimulator } from "antlr4ts/atn/ParserATNSimulator";
import { ParseTreeListener } from "antlr4ts/tree/ParseTreeListener";
import { ParseTreeVisitor } from "antlr4ts/tree/ParseTreeVisitor";
import { RecognitionException } from "antlr4ts/RecognitionException";
import { RuleContext } from "antlr4ts/RuleContext";
//import { RuleVersion } from "antlr4ts/RuleVersion";
import { TerminalNode } from "antlr4ts/tree/TerminalNode";
import { Token } from "antlr4ts/Token";
import { TokenStream } from "antlr4ts/TokenStream";
import { Vocabulary } from "antlr4ts/Vocabulary";
import { VocabularyImpl } from "antlr4ts/VocabularyImpl";

import * as Utils from "antlr4ts/misc/Utils";

import { SFMLListener } from "./SFMLListener";
import { SFMLVisitor } from "./SFMLVisitor";


export class SFMLParser extends Parser {
	public static readonly IF = 1;
	public static readonly THEN = 2;
	public static readonly ELSE = 3;
	public static readonly HAS = 4;
	public static readonly OVERALL = 5;
	public static readonly SOME = 6;
	public static readonly ONE = 7;
	public static readonly LONE = 8;
	public static readonly TRUE = 9;
	public static readonly FALSE = 10;
	public static readonly NOT = 11;
	public static readonly AND = 12;
	public static readonly OR = 13;
	public static readonly GT = 14;
	public static readonly GT_SYMBOL = 15;
	public static readonly LT = 16;
	public static readonly LT_SYMBOL = 17;
	public static readonly EQ = 18;
	public static readonly EQ_SYMBOL = 19;
	public static readonly LE = 20;
	public static readonly LE_SYMBOL = 21;
	public static readonly GE = 22;
	public static readonly GE_SYMBOL = 23;
	public static readonly FROM = 24;
	public static readonly TO = 25;
	public static readonly INPUT = 26;
	public static readonly OUTPUT = 27;
	public static readonly WHERE = 28;
	public static readonly SLOTS = 29;
	public static readonly SLOT = 30;
	public static readonly RETAIN = 31;
	public static readonly EACH = 32;
	public static readonly EXCEPT = 33;
	public static readonly FORGET = 34;
	public static readonly EMPTY = 35;
	public static readonly IN = 36;
	public static readonly WITHOUT = 37;
	public static readonly WITH = 38;
	public static readonly TAG = 39;
	public static readonly HASHTAG = 40;
	public static readonly ROUND = 41;
	public static readonly ROBIN = 42;
	public static readonly BY = 43;
	public static readonly LABEL = 44;
	public static readonly BLOCK = 45;
	public static readonly TOP = 46;
	public static readonly BOTTOM = 47;
	public static readonly NORTH = 48;
	public static readonly EAST = 49;
	public static readonly SOUTH = 50;
	public static readonly WEST = 51;
	public static readonly SIDE = 52;
	public static readonly LEFT = 53;
	public static readonly RIGHT = 54;
	public static readonly FRONT = 55;
	public static readonly BACK = 56;
	public static readonly NULL = 57;
	public static readonly TICKS = 58;
	public static readonly TICK = 59;
	public static readonly SECONDS = 60;
	public static readonly SECOND = 61;
	public static readonly GLOBAL = 62;
	public static readonly PLUS = 63;
	public static readonly OFFSET = 64;
	public static readonly REDSTONE = 65;
	public static readonly PULSE = 66;
	public static readonly DO = 67;
	public static readonly END = 68;
	public static readonly NAME = 69;
	public static readonly LET = 70;
	public static readonly BE = 71;
	public static readonly PLAYER = 72;
	public static readonly OF = 73;
	public static readonly LIKE = 74;
	public static readonly OBJECT = 75;
	public static readonly FIELD = 76;
	public static readonly GUID = 77;
	public static readonly STRING_TYPE = 78;
	public static readonly INVOKE = 79;
	public static readonly CAPABILITY = 80;
	public static readonly AS = 81;
	public static readonly CREATE = 82;
	public static readonly BROADCAST = 83;
	public static readonly CHANNEL = 84;
	public static readonly NEW = 85;
	public static readonly CLIENT = 86;
	public static readonly SERVER = 87;
	public static readonly BTW = 88;
	public static readonly EVERY = 89;
	public static readonly COMMA = 90;
	public static readonly COLON = 91;
	public static readonly SLASH = 92;
	public static readonly DASH = 93;
	public static readonly LPAREN = 94;
	public static readonly RPAREN = 95;
	public static readonly NUMBER_WITH_G_SUFFIX = 96;
	public static readonly NUMBER = 97;
	public static readonly IDENTIFIER = 98;
	public static readonly STRING = 99;
	public static readonly LINE_COMMENT = 100;
	public static readonly WS = 101;
	public static readonly UNUSED = 102;
	public static readonly RULE_program = 0;
	public static readonly RULE_executionSideDeclaration = 1;
	public static readonly RULE_name = 2;
	public static readonly RULE_declaration = 3;
	public static readonly RULE_valuePattern = 4;
	public static readonly RULE_patternField = 5;
	public static readonly RULE_trigger = 6;
	public static readonly RULE_interval = 7;
	public static readonly RULE_timeUnit = 8;
	public static readonly RULE_block = 9;
	public static readonly RULE_statement = 10;
	public static readonly RULE_letValueStatement = 11;
	public static readonly RULE_valueExpression = 12;
	public static readonly RULE_constructionField = 13;
	public static readonly RULE_fieldValueExpression = 14;
	public static readonly RULE_createStatement = 15;
	public static readonly RULE_broadcastStatement = 16;
	public static readonly RULE_forgetStatement = 17;
	public static readonly RULE_inputStatement = 18;
	public static readonly RULE_inputSelection = 19;
	public static readonly RULE_inputBinding = 20;
	public static readonly RULE_outputStatement = 21;
	public static readonly RULE_inputResourceLimits = 22;
	public static readonly RULE_outputResourceLimits = 23;
	public static readonly RULE_resourceLimitList = 24;
	public static readonly RULE_resourceLimit = 25;
	public static readonly RULE_limit = 26;
	public static readonly RULE_quantity = 27;
	public static readonly RULE_retention = 28;
	public static readonly RULE_resourceExclusion = 29;
	public static readonly RULE_resourceId = 30;
	public static readonly RULE_resourceIdList = 31;
	public static readonly RULE_resourceIdDisjunction = 32;
	public static readonly RULE_with = 33;
	public static readonly RULE_withClause = 34;
	public static readonly RULE_tagMatcher = 35;
	public static readonly RULE_qualifiedId = 36;
	public static readonly RULE_sidequalifier = 37;
	public static readonly RULE_side = 38;
	public static readonly RULE_slotqualifier = 39;
	public static readonly RULE_rangeset = 40;
	public static readonly RULE_range = 41;
	public static readonly RULE_ifStatement = 42;
	public static readonly RULE_boolexpr = 43;
	public static readonly RULE_comparisonOp = 44;
	public static readonly RULE_setOp = 45;
	public static readonly RULE_labelAccess = 46;
	public static readonly RULE_roundrobin = 47;
	public static readonly RULE_label = 48;
	public static readonly RULE_emptyslots = 49;
	public static readonly RULE_identifier = 50;
	public static readonly RULE_string = 51;
	public static readonly RULE_number = 52;
	// tslint:disable:no-trailing-whitespace
	public static readonly ruleNames: string[] = [
		"program", "executionSideDeclaration", "name", "declaration", "valuePattern", 
		"patternField", "trigger", "interval", "timeUnit", "block", "statement", 
		"letValueStatement", "valueExpression", "constructionField", "fieldValueExpression", 
		"createStatement", "broadcastStatement", "forgetStatement", "inputStatement", 
		"inputSelection", "inputBinding", "outputStatement", "inputResourceLimits", 
		"outputResourceLimits", "resourceLimitList", "resourceLimit", "limit", 
		"quantity", "retention", "resourceExclusion", "resourceId", "resourceIdList", 
		"resourceIdDisjunction", "with", "withClause", "tagMatcher", "qualifiedId", 
		"sidequalifier", "side", "slotqualifier", "rangeset", "range", "ifStatement", 
		"boolexpr", "comparisonOp", "setOp", "labelAccess", "roundrobin", "label", 
		"emptyslots", "identifier", "string", "number",
	];

	private static readonly _LITERAL_NAMES: Array<string | undefined> = [
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, "'>'", undefined, "'<'", undefined, "'='", undefined, "'<='", 
		undefined, "'>='", undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, "'#'", undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, "','", "':'", "'/'", 
		"'-'", "'('", "')'",
	];
	private static readonly _SYMBOLIC_NAMES: Array<string | undefined> = [
		undefined, "IF", "THEN", "ELSE", "HAS", "OVERALL", "SOME", "ONE", "LONE", 
		"TRUE", "FALSE", "NOT", "AND", "OR", "GT", "GT_SYMBOL", "LT", "LT_SYMBOL", 
		"EQ", "EQ_SYMBOL", "LE", "LE_SYMBOL", "GE", "GE_SYMBOL", "FROM", "TO", 
		"INPUT", "OUTPUT", "WHERE", "SLOTS", "SLOT", "RETAIN", "EACH", "EXCEPT", 
		"FORGET", "EMPTY", "IN", "WITHOUT", "WITH", "TAG", "HASHTAG", "ROUND", 
		"ROBIN", "BY", "LABEL", "BLOCK", "TOP", "BOTTOM", "NORTH", "EAST", "SOUTH", 
		"WEST", "SIDE", "LEFT", "RIGHT", "FRONT", "BACK", "NULL", "TICKS", "TICK", 
		"SECONDS", "SECOND", "GLOBAL", "PLUS", "OFFSET", "REDSTONE", "PULSE", 
		"DO", "END", "NAME", "LET", "BE", "PLAYER", "OF", "LIKE", "OBJECT", "FIELD", 
		"GUID", "STRING_TYPE", "INVOKE", "CAPABILITY", "AS", "CREATE", "BROADCAST", 
		"CHANNEL", "NEW", "CLIENT", "SERVER", "BTW", "EVERY", "COMMA", "COLON", 
		"SLASH", "DASH", "LPAREN", "RPAREN", "NUMBER_WITH_G_SUFFIX", "NUMBER", 
		"IDENTIFIER", "STRING", "LINE_COMMENT", "WS", "UNUSED",
	];
	public static readonly VOCABULARY: Vocabulary = new VocabularyImpl(SFMLParser._LITERAL_NAMES, SFMLParser._SYMBOLIC_NAMES, []);

	// @Override
	// @NotNull
	public get vocabulary(): Vocabulary {
		return SFMLParser.VOCABULARY;
	}
	// tslint:enable:no-trailing-whitespace

	// @Override
	public get grammarFileName(): string { return "SFML.g4"; }

	// @Override
	public get ruleNames(): string[] { return SFMLParser.ruleNames; }

	// @Override
	public get serializedATN(): string { return SFMLParser._serializedATN; }

	protected createFailedPredicateException(predicate?: string, message?: string): FailedPredicateException {
		return new FailedPredicateException(this, predicate, message);
	}

	constructor(input: TokenStream) {
		super(input);
		this._interp = new ParserATNSimulator(SFMLParser._ATN, this);
	}
	// @RuleVersion(0)
	public program(): ProgramContext {
		let _localctx: ProgramContext = new ProgramContext(this._ctx, this.state);
		this.enterRule(_localctx, 0, SFMLParser.RULE_program);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 107;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.CLIENT || _la === SFMLParser.SERVER) {
				{
				this.state = 106;
				this.executionSideDeclaration();
				}
			}

			this.state = 110;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.NAME) {
				{
				this.state = 109;
				this.name();
				}
			}

			this.state = 115;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.LET) {
				{
				{
				this.state = 112;
				this.declaration();
				}
				}
				this.state = 117;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 121;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.EVERY) {
				{
				{
				this.state = 118;
				this.trigger();
				}
				}
				this.state = 123;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 124;
			this.match(SFMLParser.EOF);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public executionSideDeclaration(): ExecutionSideDeclarationContext {
		let _localctx: ExecutionSideDeclarationContext = new ExecutionSideDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 2, SFMLParser.RULE_executionSideDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 126;
			_la = this._input.LA(1);
			if (!(_la === SFMLParser.CLIENT || _la === SFMLParser.SERVER)) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			this.state = 127;
			this.match(SFMLParser.BTW);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public name(): NameContext {
		let _localctx: NameContext = new NameContext(this._ctx, this.state);
		this.enterRule(_localctx, 4, SFMLParser.RULE_name);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 129;
			this.match(SFMLParser.NAME);
			this.state = 130;
			this.string();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public declaration(): DeclarationContext {
		let _localctx: DeclarationContext = new DeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 6, SFMLParser.RULE_declaration);
		try {
			this.state = 145;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 4, this._ctx) ) {
			case 1:
				_localctx = new PlayerDeclarationContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 132;
				this.match(SFMLParser.LET);
				this.state = 133;
				this.identifier();
				this.state = 134;
				this.match(SFMLParser.BE);
				this.state = 135;
				this.match(SFMLParser.PLAYER);
				this.state = 136;
				this.match(SFMLParser.OF);
				this.state = 137;
				this.identifier();
				}
				break;

			case 2:
				_localctx = new PatternDeclarationContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 139;
				this.match(SFMLParser.LET);
				this.state = 140;
				this.identifier();
				this.state = 141;
				this.match(SFMLParser.BE);
				this.state = 142;
				this.match(SFMLParser.LIKE);
				this.state = 143;
				this.valuePattern();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public valuePattern(): ValuePatternContext {
		let _localctx: ValuePatternContext = new ValuePatternContext(this._ctx, this.state);
		this.enterRule(_localctx, 8, SFMLParser.RULE_valuePattern);
		let _la: number;
		try {
			this.state = 163;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 6, this._ctx) ) {
			case 1:
				_localctx = new GuidValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 147;
				this.match(SFMLParser.GUID);
				}
				break;

			case 2:
				_localctx = new StringValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 148;
				this.match(SFMLParser.STRING_TYPE);
				}
				break;

			case 3:
				_localctx = new LiteralValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 149;
				this.string();
				}
				break;

			case 4:
				_localctx = new ObjectValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 150;
				this.match(SFMLParser.OBJECT);
				this.state = 151;
				this.match(SFMLParser.WITH);
				this.state = 152;
				this.match(SFMLParser.FIELD);
				this.state = 153;
				this.patternField();
				this.state = 159;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === SFMLParser.AND) {
					{
					{
					this.state = 154;
					this.match(SFMLParser.AND);
					this.state = 155;
					this.match(SFMLParser.FIELD);
					this.state = 156;
					this.patternField();
					}
					}
					this.state = 161;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				}
				break;

			case 5:
				_localctx = new AliasValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 5);
				{
				this.state = 162;
				this.identifier();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public patternField(): PatternFieldContext {
		let _localctx: PatternFieldContext = new PatternFieldContext(this._ctx, this.state);
		this.enterRule(_localctx, 10, SFMLParser.RULE_patternField);
		try {
			this.state = 174;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 7, this._ctx) ) {
			case 1:
				_localctx = new LiteralPatternFieldContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 165;
				this.identifier();
				this.state = 166;
				this.match(SFMLParser.OF);
				this.state = 167;
				this.string();
				}
				break;

			case 2:
				_localctx = new LikePatternFieldContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 169;
				this.identifier();
				this.state = 170;
				this.match(SFMLParser.LIKE);
				this.state = 171;
				this.identifier();
				}
				break;

			case 3:
				_localctx = new AliasPatternFieldContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 173;
				this.identifier();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public trigger(): TriggerContext {
		let _localctx: TriggerContext = new TriggerContext(this._ctx, this.state);
		this.enterRule(_localctx, 12, SFMLParser.RULE_trigger);
		try {
			this.state = 189;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 8, this._ctx) ) {
			case 1:
				_localctx = new TimerTriggerContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 176;
				this.match(SFMLParser.EVERY);
				this.state = 177;
				this.interval();
				this.state = 178;
				this.match(SFMLParser.DO);
				this.state = 179;
				this.block();
				this.state = 180;
				this.match(SFMLParser.END);
				}
				break;

			case 2:
				_localctx = new PulseTriggerContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 182;
				this.match(SFMLParser.EVERY);
				this.state = 183;
				this.match(SFMLParser.REDSTONE);
				this.state = 184;
				this.match(SFMLParser.PULSE);
				this.state = 185;
				this.match(SFMLParser.DO);
				this.state = 186;
				this.block();
				this.state = 187;
				this.match(SFMLParser.END);
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public interval(): IntervalContext {
		let _localctx: IntervalContext = new IntervalContext(this._ctx, this.state);
		this.enterRule(_localctx, 14, SFMLParser.RULE_interval);
		let _la: number;
		try {
			this.state = 220;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.TICKS:
			case SFMLParser.TICK:
			case SFMLParser.SECONDS:
			case SFMLParser.SECOND:
			case SFMLParser.GLOBAL:
			case SFMLParser.PLUS:
			case SFMLParser.NUMBER:
				_localctx = new IntervalSpaceContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 192;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.NUMBER) {
					{
					this.state = 191;
					(_localctx as IntervalSpaceContext)._period = this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 195;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.GLOBAL) {
					{
					this.state = 194;
					this.match(SFMLParser.GLOBAL);
					}
				}

				this.state = 199;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.PLUS) {
					{
					this.state = 197;
					this.match(SFMLParser.PLUS);
					this.state = 198;
					(_localctx as IntervalSpaceContext)._legacyOffset = this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 201;
				(_localctx as IntervalSpaceContext)._unit = this.timeUnit();
				this.state = 206;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.OFFSET) {
					{
					this.state = 202;
					this.match(SFMLParser.OFFSET);
					this.state = 203;
					this.match(SFMLParser.BY);
					this.state = 204;
					(_localctx as IntervalSpaceContext)._newOffset = this.match(SFMLParser.NUMBER);
					this.state = 205;
					(_localctx as IntervalSpaceContext)._offsetUnit = this.timeUnit();
					}
				}

				}
				break;
			case SFMLParser.NUMBER_WITH_G_SUFFIX:
				_localctx = new IntervalNoSpaceContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 208;
				(_localctx as IntervalNoSpaceContext)._period = this.match(SFMLParser.NUMBER_WITH_G_SUFFIX);
				this.state = 211;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.PLUS) {
					{
					this.state = 209;
					this.match(SFMLParser.PLUS);
					this.state = 210;
					(_localctx as IntervalNoSpaceContext)._legacyOffset = this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 213;
				(_localctx as IntervalNoSpaceContext)._unit = this.timeUnit();
				this.state = 218;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.OFFSET) {
					{
					this.state = 214;
					this.match(SFMLParser.OFFSET);
					this.state = 215;
					this.match(SFMLParser.BY);
					this.state = 216;
					(_localctx as IntervalNoSpaceContext)._newOffset = this.match(SFMLParser.NUMBER);
					this.state = 217;
					(_localctx as IntervalNoSpaceContext)._offsetUnit = this.timeUnit();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public timeUnit(): TimeUnitContext {
		let _localctx: TimeUnitContext = new TimeUnitContext(this._ctx, this.state);
		this.enterRule(_localctx, 16, SFMLParser.RULE_timeUnit);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 222;
			_la = this._input.LA(1);
			if (!(((((_la - 58)) & ~0x1F) === 0 && ((1 << (_la - 58)) & ((1 << (SFMLParser.TICKS - 58)) | (1 << (SFMLParser.TICK - 58)) | (1 << (SFMLParser.SECONDS - 58)) | (1 << (SFMLParser.SECOND - 58)))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public block(): BlockContext {
		let _localctx: BlockContext = new BlockContext(this._ctx, this.state);
		this.enterRule(_localctx, 18, SFMLParser.RULE_block);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 227;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << SFMLParser.IF) | (1 << SFMLParser.FROM) | (1 << SFMLParser.TO) | (1 << SFMLParser.INPUT) | (1 << SFMLParser.OUTPUT))) !== 0) || _la === SFMLParser.FORGET || ((((_la - 70)) & ~0x1F) === 0 && ((1 << (_la - 70)) & ((1 << (SFMLParser.LET - 70)) | (1 << (SFMLParser.CREATE - 70)) | (1 << (SFMLParser.BROADCAST - 70)))) !== 0)) {
				{
				{
				this.state = 224;
				this.statement();
				}
				}
				this.state = 229;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public statement(): StatementContext {
		let _localctx: StatementContext = new StatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 20, SFMLParser.RULE_statement);
		try {
			this.state = 237;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.FROM:
			case SFMLParser.INPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 230;
				this.inputStatement();
				}
				break;
			case SFMLParser.TO:
			case SFMLParser.OUTPUT:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 231;
				this.outputStatement();
				}
				break;
			case SFMLParser.IF:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 232;
				this.ifStatement();
				}
				break;
			case SFMLParser.FORGET:
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 233;
				this.forgetStatement();
				}
				break;
			case SFMLParser.LET:
				this.enterOuterAlt(_localctx, 5);
				{
				this.state = 234;
				this.letValueStatement();
				}
				break;
			case SFMLParser.CREATE:
				this.enterOuterAlt(_localctx, 6);
				{
				this.state = 235;
				this.createStatement();
				}
				break;
			case SFMLParser.BROADCAST:
				this.enterOuterAlt(_localctx, 7);
				{
				this.state = 236;
				this.broadcastStatement();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public letValueStatement(): LetValueStatementContext {
		let _localctx: LetValueStatementContext = new LetValueStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 22, SFMLParser.RULE_letValueStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 239;
			this.match(SFMLParser.LET);
			this.state = 240;
			this.identifier();
			this.state = 241;
			this.match(SFMLParser.BE);
			this.state = 242;
			this.valueExpression();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public valueExpression(): ValueExpressionContext {
		let _localctx: ValueExpressionContext = new ValueExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 24, SFMLParser.RULE_valueExpression);
		let _la: number;
		try {
			this.state = 263;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 19, this._ctx) ) {
			case 1:
				_localctx = new InvokeTextValueExpressionContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 244;
				this.match(SFMLParser.STRING_TYPE);
				this.state = 245;
				this.match(SFMLParser.OF);
				this.state = 246;
				this.match(SFMLParser.INVOKE);
				this.state = 247;
				this.qualifiedId();
				this.state = 248;
				this.match(SFMLParser.WITH);
				this.state = 249;
				this.identifier();
				}
				break;

			case 2:
				_localctx = new ObjectConstructionValueExpressionContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 251;
				this.identifier();
				this.state = 252;
				this.match(SFMLParser.WITH);
				this.state = 253;
				this.match(SFMLParser.FIELD);
				this.state = 254;
				this.constructionField();
				this.state = 260;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === SFMLParser.AND) {
					{
					{
					this.state = 255;
					this.match(SFMLParser.AND);
					this.state = 256;
					this.match(SFMLParser.FIELD);
					this.state = 257;
					this.constructionField();
					}
					}
					this.state = 262;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public constructionField(): ConstructionFieldContext {
		let _localctx: ConstructionFieldContext = new ConstructionFieldContext(this._ctx, this.state);
		this.enterRule(_localctx, 26, SFMLParser.RULE_constructionField);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 265;
			this.identifier();
			this.state = 266;
			this.match(SFMLParser.OF);
			this.state = 267;
			this.fieldValueExpression();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public fieldValueExpression(): FieldValueExpressionContext {
		let _localctx: FieldValueExpressionContext = new FieldValueExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 28, SFMLParser.RULE_fieldValueExpression);
		try {
			this.state = 273;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 20, this._ctx) ) {
			case 1:
				_localctx = new NewGuidFieldValueContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 269;
				this.match(SFMLParser.NEW);
				this.state = 270;
				this.match(SFMLParser.GUID);
				}
				break;

			case 2:
				_localctx = new LiteralFieldValueContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 271;
				this.string();
				}
				break;

			case 3:
				_localctx = new VariableFieldValueContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 272;
				this.identifier();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public createStatement(): CreateStatementContext {
		let _localctx: CreateStatementContext = new CreateStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 30, SFMLParser.RULE_createStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 275;
			this.match(SFMLParser.CREATE);
			this.state = 276;
			this.match(SFMLParser.INPUT);
			this.state = 277;
			this.qualifiedId();
			this.state = 278;
			this.match(SFMLParser.WITH);
			this.state = 279;
			this.identifier();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public broadcastStatement(): BroadcastStatementContext {
		let _localctx: BroadcastStatementContext = new BroadcastStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 32, SFMLParser.RULE_broadcastStatement);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 281;
			this.match(SFMLParser.BROADCAST);
			this.state = 282;
			this.match(SFMLParser.TO);
			this.state = 283;
			this.identifier();
			this.state = 286;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.CHANNEL) {
				{
				this.state = 284;
				this.match(SFMLParser.CHANNEL);
				this.state = 285;
				this.qualifiedId();
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public forgetStatement(): ForgetStatementContext {
		let _localctx: ForgetStatementContext = new ForgetStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 34, SFMLParser.RULE_forgetStatement);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 288;
			this.match(SFMLParser.FORGET);
			this.state = 290;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 22, this._ctx) ) {
			case 1:
				{
				this.state = 289;
				this.label();
				}
				break;
			}
			this.state = 296;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 23, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 292;
					this.match(SFMLParser.COMMA);
					this.state = 293;
					this.label();
					}
					}
				}
				this.state = 298;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 23, this._ctx);
			}
			this.state = 300;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.COMMA) {
				{
				this.state = 299;
				this.match(SFMLParser.COMMA);
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public inputStatement(): InputStatementContext {
		let _localctx: InputStatementContext = new InputStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 36, SFMLParser.RULE_inputStatement);
		let _la: number;
		try {
			this.state = 338;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.INPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 302;
				this.match(SFMLParser.INPUT);
				this.state = 304;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 25, this._ctx) ) {
				case 1:
					{
					this.state = 303;
					this.inputSelection();
					}
					break;
				}
				this.state = 307;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 31)) & ~0x1F) === 0 && ((1 << (_la - 31)) & ((1 << (SFMLParser.RETAIN - 31)) | (1 << (SFMLParser.WITHOUT - 31)) | (1 << (SFMLParser.WITH - 31)) | (1 << (SFMLParser.TOP - 31)) | (1 << (SFMLParser.BOTTOM - 31)) | (1 << (SFMLParser.LEFT - 31)) | (1 << (SFMLParser.RIGHT - 31)) | (1 << (SFMLParser.FRONT - 31)) | (1 << (SFMLParser.BACK - 31)) | (1 << (SFMLParser.SECONDS - 31)) | (1 << (SFMLParser.SECOND - 31)) | (1 << (SFMLParser.GLOBAL - 31)))) !== 0) || ((((_la - 64)) & ~0x1F) === 0 && ((1 << (_la - 64)) & ((1 << (SFMLParser.OFFSET - 64)) | (1 << (SFMLParser.REDSTONE - 64)) | (1 << (SFMLParser.LET - 64)) | (1 << (SFMLParser.BE - 64)) | (1 << (SFMLParser.PLAYER - 64)) | (1 << (SFMLParser.OF - 64)) | (1 << (SFMLParser.LIKE - 64)) | (1 << (SFMLParser.OBJECT - 64)) | (1 << (SFMLParser.FIELD - 64)) | (1 << (SFMLParser.GUID - 64)) | (1 << (SFMLParser.STRING_TYPE - 64)) | (1 << (SFMLParser.INVOKE - 64)) | (1 << (SFMLParser.CAPABILITY - 64)) | (1 << (SFMLParser.AS - 64)) | (1 << (SFMLParser.CREATE - 64)) | (1 << (SFMLParser.BROADCAST - 64)) | (1 << (SFMLParser.CHANNEL - 64)) | (1 << (SFMLParser.NEW - 64)) | (1 << (SFMLParser.CLIENT - 64)) | (1 << (SFMLParser.SERVER - 64)) | (1 << (SFMLParser.BTW - 64)))) !== 0) || ((((_la - 97)) & ~0x1F) === 0 && ((1 << (_la - 97)) & ((1 << (SFMLParser.NUMBER - 97)) | (1 << (SFMLParser.IDENTIFIER - 97)) | (1 << (SFMLParser.STRING - 97)))) !== 0)) {
					{
					this.state = 306;
					this.inputResourceLimits();
					}
				}

				this.state = 310;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 309;
					this.resourceExclusion();
					}
				}

				this.state = 312;
				this.match(SFMLParser.FROM);
				this.state = 314;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 313;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 316;
				this.labelAccess();
				this.state = 318;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.AS) {
					{
					this.state = 317;
					this.inputBinding();
					}
				}

				}
				break;
			case SFMLParser.FROM:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 320;
				this.match(SFMLParser.FROM);
				this.state = 322;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 321;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 324;
				this.labelAccess();
				this.state = 325;
				this.match(SFMLParser.INPUT);
				this.state = 327;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 31, this._ctx) ) {
				case 1:
					{
					this.state = 326;
					this.inputSelection();
					}
					break;
				}
				this.state = 330;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 32, this._ctx) ) {
				case 1:
					{
					this.state = 329;
					this.inputResourceLimits();
					}
					break;
				}
				this.state = 333;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 332;
					this.resourceExclusion();
					}
				}

				this.state = 336;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.AS) {
					{
					this.state = 335;
					this.inputBinding();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public inputSelection(): InputSelectionContext {
		let _localctx: InputSelectionContext = new InputSelectionContext(this._ctx, this.state);
		this.enterRule(_localctx, 38, SFMLParser.RULE_inputSelection);
		try {
			this.state = 345;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.WITH:
				_localctx = new CapabilityInputSelectionContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 340;
				this.match(SFMLParser.WITH);
				this.state = 341;
				this.match(SFMLParser.CAPABILITY);
				this.state = 342;
				this.qualifiedId();
				}
				break;
			case SFMLParser.LIKE:
				_localctx = new PatternInputSelectionContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 343;
				this.match(SFMLParser.LIKE);
				this.state = 344;
				this.identifier();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public inputBinding(): InputBindingContext {
		let _localctx: InputBindingContext = new InputBindingContext(this._ctx, this.state);
		this.enterRule(_localctx, 40, SFMLParser.RULE_inputBinding);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 347;
			this.match(SFMLParser.AS);
			this.state = 348;
			this.identifier();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public outputStatement(): OutputStatementContext {
		let _localctx: OutputStatementContext = new OutputStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 42, SFMLParser.RULE_outputStatement);
		let _la: number;
		try {
			this.state = 380;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.OUTPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 350;
				this.match(SFMLParser.OUTPUT);
				this.state = 352;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 31)) & ~0x1F) === 0 && ((1 << (_la - 31)) & ((1 << (SFMLParser.RETAIN - 31)) | (1 << (SFMLParser.WITHOUT - 31)) | (1 << (SFMLParser.WITH - 31)) | (1 << (SFMLParser.TOP - 31)) | (1 << (SFMLParser.BOTTOM - 31)) | (1 << (SFMLParser.LEFT - 31)) | (1 << (SFMLParser.RIGHT - 31)) | (1 << (SFMLParser.FRONT - 31)) | (1 << (SFMLParser.BACK - 31)) | (1 << (SFMLParser.SECONDS - 31)) | (1 << (SFMLParser.SECOND - 31)) | (1 << (SFMLParser.GLOBAL - 31)))) !== 0) || ((((_la - 64)) & ~0x1F) === 0 && ((1 << (_la - 64)) & ((1 << (SFMLParser.OFFSET - 64)) | (1 << (SFMLParser.REDSTONE - 64)) | (1 << (SFMLParser.LET - 64)) | (1 << (SFMLParser.BE - 64)) | (1 << (SFMLParser.PLAYER - 64)) | (1 << (SFMLParser.OF - 64)) | (1 << (SFMLParser.LIKE - 64)) | (1 << (SFMLParser.OBJECT - 64)) | (1 << (SFMLParser.FIELD - 64)) | (1 << (SFMLParser.GUID - 64)) | (1 << (SFMLParser.STRING_TYPE - 64)) | (1 << (SFMLParser.INVOKE - 64)) | (1 << (SFMLParser.CAPABILITY - 64)) | (1 << (SFMLParser.AS - 64)) | (1 << (SFMLParser.CREATE - 64)) | (1 << (SFMLParser.BROADCAST - 64)) | (1 << (SFMLParser.CHANNEL - 64)) | (1 << (SFMLParser.NEW - 64)) | (1 << (SFMLParser.CLIENT - 64)) | (1 << (SFMLParser.SERVER - 64)) | (1 << (SFMLParser.BTW - 64)))) !== 0) || ((((_la - 97)) & ~0x1F) === 0 && ((1 << (_la - 97)) & ((1 << (SFMLParser.NUMBER - 97)) | (1 << (SFMLParser.IDENTIFIER - 97)) | (1 << (SFMLParser.STRING - 97)))) !== 0)) {
					{
					this.state = 351;
					this.outputResourceLimits();
					}
				}

				this.state = 355;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 354;
					this.resourceExclusion();
					}
				}

				this.state = 357;
				this.match(SFMLParser.TO);
				this.state = 359;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EMPTY) {
					{
					this.state = 358;
					this.emptyslots();
					}
				}

				this.state = 362;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 361;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 364;
				this.labelAccess();
				}
				break;
			case SFMLParser.TO:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 365;
				this.match(SFMLParser.TO);
				this.state = 367;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EMPTY) {
					{
					this.state = 366;
					this.emptyslots();
					}
				}

				this.state = 370;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 369;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 372;
				this.labelAccess();
				this.state = 373;
				this.match(SFMLParser.OUTPUT);
				this.state = 375;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 43, this._ctx) ) {
				case 1:
					{
					this.state = 374;
					this.outputResourceLimits();
					}
					break;
				}
				this.state = 378;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 377;
					this.resourceExclusion();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public inputResourceLimits(): InputResourceLimitsContext {
		let _localctx: InputResourceLimitsContext = new InputResourceLimitsContext(this._ctx, this.state);
		this.enterRule(_localctx, 44, SFMLParser.RULE_inputResourceLimits);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 382;
			this.resourceLimitList();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public outputResourceLimits(): OutputResourceLimitsContext {
		let _localctx: OutputResourceLimitsContext = new OutputResourceLimitsContext(this._ctx, this.state);
		this.enterRule(_localctx, 46, SFMLParser.RULE_outputResourceLimits);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 384;
			this.resourceLimitList();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public resourceLimitList(): ResourceLimitListContext {
		let _localctx: ResourceLimitListContext = new ResourceLimitListContext(this._ctx, this.state);
		this.enterRule(_localctx, 48, SFMLParser.RULE_resourceLimitList);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 386;
			this.resourceLimit();
			this.state = 391;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 46, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 387;
					this.match(SFMLParser.COMMA);
					this.state = 388;
					this.resourceLimit();
					}
					}
				}
				this.state = 393;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 46, this._ctx);
			}
			this.state = 395;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.COMMA) {
				{
				this.state = 394;
				this.match(SFMLParser.COMMA);
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public resourceLimit(): ResourceLimitContext {
		let _localctx: ResourceLimitContext = new ResourceLimitContext(this._ctx, this.state);
		this.enterRule(_localctx, 50, SFMLParser.RULE_resourceLimit);
		let _la: number;
		try {
			this.state = 409;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 51, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 398;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.RETAIN || _la === SFMLParser.NUMBER) {
					{
					this.state = 397;
					this.limit();
					}
				}

				this.state = 400;
				this.resourceIdDisjunction();
				this.state = 402;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.WITHOUT || _la === SFMLParser.WITH) {
					{
					this.state = 401;
					this.with();
					}
				}

				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 404;
				this.limit();
				this.state = 406;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.WITHOUT || _la === SFMLParser.WITH) {
					{
					this.state = 405;
					this.with();
					}
				}

				}
				break;

			case 3:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 408;
				this.with();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public limit(): LimitContext {
		let _localctx: LimitContext = new LimitContext(this._ctx, this.state);
		this.enterRule(_localctx, 52, SFMLParser.RULE_limit);
		try {
			this.state = 416;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 52, this._ctx) ) {
			case 1:
				_localctx = new QuantityRetentionLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 411;
				this.quantity();
				this.state = 412;
				this.retention();
				}
				break;

			case 2:
				_localctx = new RetentionLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 414;
				this.retention();
				}
				break;

			case 3:
				_localctx = new QuantityLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 415;
				this.quantity();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public quantity(): QuantityContext {
		let _localctx: QuantityContext = new QuantityContext(this._ctx, this.state);
		this.enterRule(_localctx, 54, SFMLParser.RULE_quantity);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 418;
			this.number();
			this.state = 420;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.EACH) {
				{
				this.state = 419;
				this.match(SFMLParser.EACH);
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public retention(): RetentionContext {
		let _localctx: RetentionContext = new RetentionContext(this._ctx, this.state);
		this.enterRule(_localctx, 56, SFMLParser.RULE_retention);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 422;
			this.match(SFMLParser.RETAIN);
			this.state = 423;
			this.number();
			this.state = 425;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.EACH) {
				{
				this.state = 424;
				this.match(SFMLParser.EACH);
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public resourceExclusion(): ResourceExclusionContext {
		let _localctx: ResourceExclusionContext = new ResourceExclusionContext(this._ctx, this.state);
		this.enterRule(_localctx, 58, SFMLParser.RULE_resourceExclusion);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 427;
			this.match(SFMLParser.EXCEPT);
			this.state = 428;
			this.resourceIdList();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public resourceId(): ResourceIdContext {
		let _localctx: ResourceIdContext = new ResourceIdContext(this._ctx, this.state);
		this.enterRule(_localctx, 60, SFMLParser.RULE_resourceId);
		try {
			this.state = 450;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.TOP:
			case SFMLParser.BOTTOM:
			case SFMLParser.LEFT:
			case SFMLParser.RIGHT:
			case SFMLParser.FRONT:
			case SFMLParser.BACK:
			case SFMLParser.SECONDS:
			case SFMLParser.SECOND:
			case SFMLParser.GLOBAL:
			case SFMLParser.OFFSET:
			case SFMLParser.REDSTONE:
			case SFMLParser.LET:
			case SFMLParser.BE:
			case SFMLParser.PLAYER:
			case SFMLParser.OF:
			case SFMLParser.LIKE:
			case SFMLParser.OBJECT:
			case SFMLParser.FIELD:
			case SFMLParser.GUID:
			case SFMLParser.STRING_TYPE:
			case SFMLParser.INVOKE:
			case SFMLParser.CAPABILITY:
			case SFMLParser.AS:
			case SFMLParser.CREATE:
			case SFMLParser.BROADCAST:
			case SFMLParser.CHANNEL:
			case SFMLParser.NEW:
			case SFMLParser.CLIENT:
			case SFMLParser.SERVER:
			case SFMLParser.BTW:
			case SFMLParser.IDENTIFIER:
				_localctx = new ResourceContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				{
				this.state = 430;
				this.identifier();
				}
				this.state = 447;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 60, this._ctx) ) {
				case 1:
					{
					this.state = 431;
					this.match(SFMLParser.COLON);
					this.state = 433;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 55, this._ctx) ) {
					case 1:
						{
						this.state = 432;
						this.identifier();
						}
						break;
					}
					this.state = 445;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 59, this._ctx) ) {
					case 1:
						{
						this.state = 435;
						this.match(SFMLParser.COLON);
						this.state = 437;
						this._errHandler.sync(this);
						switch ( this.interpreter.adaptivePredict(this._input, 56, this._ctx) ) {
						case 1:
							{
							this.state = 436;
							this.identifier();
							}
							break;
						}
						this.state = 443;
						this._errHandler.sync(this);
						switch ( this.interpreter.adaptivePredict(this._input, 58, this._ctx) ) {
						case 1:
							{
							this.state = 439;
							this.match(SFMLParser.COLON);
							this.state = 441;
							this._errHandler.sync(this);
							switch ( this.interpreter.adaptivePredict(this._input, 57, this._ctx) ) {
							case 1:
								{
								this.state = 440;
								this.identifier();
								}
								break;
							}
							}
							break;
						}
						}
						break;
					}
					}
					break;
				}
				}
				break;
			case SFMLParser.STRING:
				_localctx = new StringResourceContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 449;
				this.string();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public resourceIdList(): ResourceIdListContext {
		let _localctx: ResourceIdListContext = new ResourceIdListContext(this._ctx, this.state);
		this.enterRule(_localctx, 62, SFMLParser.RULE_resourceIdList);
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 452;
			this.resourceId();
			this.state = 457;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 62, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 453;
					this.match(SFMLParser.COMMA);
					this.state = 454;
					this.resourceId();
					}
					}
				}
				this.state = 459;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 62, this._ctx);
			}
			this.state = 461;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 63, this._ctx) ) {
			case 1:
				{
				this.state = 460;
				this.match(SFMLParser.COMMA);
				}
				break;
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public resourceIdDisjunction(): ResourceIdDisjunctionContext {
		let _localctx: ResourceIdDisjunctionContext = new ResourceIdDisjunctionContext(this._ctx, this.state);
		this.enterRule(_localctx, 64, SFMLParser.RULE_resourceIdDisjunction);
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 463;
			this.resourceId();
			this.state = 468;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 64, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 464;
					this.match(SFMLParser.OR);
					this.state = 465;
					this.resourceId();
					}
					}
				}
				this.state = 470;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 64, this._ctx);
			}
			this.state = 472;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 65, this._ctx) ) {
			case 1:
				{
				this.state = 471;
				this.match(SFMLParser.OR);
				}
				break;
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public with(): WithContext {
		let _localctx: WithContext = new WithContext(this._ctx, this.state);
		this.enterRule(_localctx, 66, SFMLParser.RULE_with);
		try {
			this.state = 478;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.WITH:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 474;
				this.match(SFMLParser.WITH);
				this.state = 475;
				this.withClause(0);
				}
				break;
			case SFMLParser.WITHOUT:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 476;
				this.match(SFMLParser.WITHOUT);
				this.state = 477;
				this.withClause(0);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public withClause(): WithClauseContext;
	public withClause(_p: number): WithClauseContext;
	// @RuleVersion(0)
	public withClause(_p?: number): WithClauseContext {
		if (_p === undefined) {
			_p = 0;
		}

		let _parentctx: ParserRuleContext = this._ctx;
		let _parentState: number = this.state;
		let _localctx: WithClauseContext = new WithClauseContext(this._ctx, _parentState);
		let _prevctx: WithClauseContext = _localctx;
		let _startState: number = 68;
		this.enterRecursionRule(_localctx, 68, SFMLParser.RULE_withClause, _p);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 495;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.LPAREN:
				{
				_localctx = new WithParenContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;

				this.state = 481;
				this.match(SFMLParser.LPAREN);
				this.state = 482;
				this.withClause(0);
				this.state = 483;
				this.match(SFMLParser.RPAREN);
				}
				break;
			case SFMLParser.NOT:
				{
				_localctx = new WithNegationContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 485;
				this.match(SFMLParser.NOT);
				this.state = 486;
				this.withClause(4);
				}
				break;
			case SFMLParser.TAG:
			case SFMLParser.HASHTAG:
				{
				_localctx = new WithTagContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 492;
				this._errHandler.sync(this);
				switch (this._input.LA(1)) {
				case SFMLParser.TAG:
					{
					this.state = 487;
					this.match(SFMLParser.TAG);
					this.state = 489;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
					if (_la === SFMLParser.HASHTAG) {
						{
						this.state = 488;
						this.match(SFMLParser.HASHTAG);
						}
					}

					}
					break;
				case SFMLParser.HASHTAG:
					{
					this.state = 491;
					this.match(SFMLParser.HASHTAG);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				this.state = 494;
				this.tagMatcher();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			this._ctx._stop = this._input.tryLT(-1);
			this.state = 505;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 71, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					if (this._parseListeners != null) {
						this.triggerExitRuleEvent();
					}
					_prevctx = _localctx;
					{
					this.state = 503;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 70, this._ctx) ) {
					case 1:
						{
						_localctx = new WithConjunctionContext(new WithClauseContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_withClause);
						this.state = 497;
						if (!(this.precpred(this._ctx, 3))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 3)");
						}
						this.state = 498;
						this.match(SFMLParser.AND);
						this.state = 499;
						this.withClause(4);
						}
						break;

					case 2:
						{
						_localctx = new WithDisjunctionContext(new WithClauseContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_withClause);
						this.state = 500;
						if (!(this.precpred(this._ctx, 2))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 2)");
						}
						this.state = 501;
						this.match(SFMLParser.OR);
						this.state = 502;
						this.withClause(3);
						}
						break;
					}
					}
				}
				this.state = 507;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 71, this._ctx);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public tagMatcher(): TagMatcherContext {
		let _localctx: TagMatcherContext = new TagMatcherContext(this._ctx, this.state);
		this.enterRule(_localctx, 70, SFMLParser.RULE_tagMatcher);
		try {
			let _alt: number;
			this.state = 526;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 74, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 508;
				this.identifier();
				this.state = 509;
				this.match(SFMLParser.COLON);
				this.state = 510;
				this.identifier();
				this.state = 515;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 72, this._ctx);
				while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
					if (_alt === 1) {
						{
						{
						this.state = 511;
						this.match(SFMLParser.SLASH);
						this.state = 512;
						this.identifier();
						}
						}
					}
					this.state = 517;
					this._errHandler.sync(this);
					_alt = this.interpreter.adaptivePredict(this._input, 72, this._ctx);
				}
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 518;
				this.identifier();
				this.state = 523;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 73, this._ctx);
				while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
					if (_alt === 1) {
						{
						{
						this.state = 519;
						this.match(SFMLParser.SLASH);
						this.state = 520;
						this.identifier();
						}
						}
					}
					this.state = 525;
					this._errHandler.sync(this);
					_alt = this.interpreter.adaptivePredict(this._input, 73, this._ctx);
				}
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public qualifiedId(): QualifiedIdContext {
		let _localctx: QualifiedIdContext = new QualifiedIdContext(this._ctx, this.state);
		this.enterRule(_localctx, 72, SFMLParser.RULE_qualifiedId);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 528;
			this.identifier();
			this.state = 529;
			this.match(SFMLParser.COLON);
			this.state = 530;
			this.identifier();
			this.state = 535;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.SLASH) {
				{
				{
				this.state = 531;
				this.match(SFMLParser.SLASH);
				this.state = 532;
				this.identifier();
				}
				}
				this.state = 537;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public sidequalifier(): SidequalifierContext {
		let _localctx: SidequalifierContext = new SidequalifierContext(this._ctx, this.state);
		this.enterRule(_localctx, 74, SFMLParser.RULE_sidequalifier);
		let _la: number;
		try {
			this.state = 550;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.EACH:
				_localctx = new EachSideContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 538;
				this.match(SFMLParser.EACH);
				this.state = 539;
				this.match(SFMLParser.SIDE);
				}
				break;
			case SFMLParser.TOP:
			case SFMLParser.BOTTOM:
			case SFMLParser.NORTH:
			case SFMLParser.EAST:
			case SFMLParser.SOUTH:
			case SFMLParser.WEST:
			case SFMLParser.LEFT:
			case SFMLParser.RIGHT:
			case SFMLParser.FRONT:
			case SFMLParser.BACK:
			case SFMLParser.NULL:
				_localctx = new ListedSidesContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 540;
				this.side();
				this.state = 545;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === SFMLParser.COMMA) {
					{
					{
					this.state = 541;
					this.match(SFMLParser.COMMA);
					this.state = 542;
					this.side();
					}
					}
					this.state = 547;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				this.state = 548;
				this.match(SFMLParser.SIDE);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public side(): SideContext {
		let _localctx: SideContext = new SideContext(this._ctx, this.state);
		this.enterRule(_localctx, 76, SFMLParser.RULE_side);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 552;
			_la = this._input.LA(1);
			if (!(((((_la - 46)) & ~0x1F) === 0 && ((1 << (_la - 46)) & ((1 << (SFMLParser.TOP - 46)) | (1 << (SFMLParser.BOTTOM - 46)) | (1 << (SFMLParser.NORTH - 46)) | (1 << (SFMLParser.EAST - 46)) | (1 << (SFMLParser.SOUTH - 46)) | (1 << (SFMLParser.WEST - 46)) | (1 << (SFMLParser.LEFT - 46)) | (1 << (SFMLParser.RIGHT - 46)) | (1 << (SFMLParser.FRONT - 46)) | (1 << (SFMLParser.BACK - 46)) | (1 << (SFMLParser.NULL - 46)))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public slotqualifier(): SlotqualifierContext {
		let _localctx: SlotqualifierContext = new SlotqualifierContext(this._ctx, this.state);
		this.enterRule(_localctx, 78, SFMLParser.RULE_slotqualifier);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 554;
			_la = this._input.LA(1);
			if (!(_la === SFMLParser.SLOTS || _la === SFMLParser.SLOT)) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			this.state = 555;
			this.rangeset();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public rangeset(): RangesetContext {
		let _localctx: RangesetContext = new RangesetContext(this._ctx, this.state);
		this.enterRule(_localctx, 80, SFMLParser.RULE_rangeset);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 557;
			this.range();
			this.state = 562;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.COMMA) {
				{
				{
				this.state = 558;
				this.match(SFMLParser.COMMA);
				this.state = 559;
				this.range();
				}
				}
				this.state = 564;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public range(): RangeContext {
		let _localctx: RangeContext = new RangeContext(this._ctx, this.state);
		this.enterRule(_localctx, 82, SFMLParser.RULE_range);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 565;
			this.number();
			this.state = 568;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.DASH) {
				{
				this.state = 566;
				this.match(SFMLParser.DASH);
				this.state = 567;
				this.number();
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public ifStatement(): IfStatementContext {
		let _localctx: IfStatementContext = new IfStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 84, SFMLParser.RULE_ifStatement);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 570;
			this.match(SFMLParser.IF);
			this.state = 571;
			this.boolexpr(0);
			this.state = 572;
			this.match(SFMLParser.THEN);
			this.state = 573;
			this.block();
			this.state = 582;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 80, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 574;
					this.match(SFMLParser.ELSE);
					this.state = 575;
					this.match(SFMLParser.IF);
					this.state = 576;
					this.boolexpr(0);
					this.state = 577;
					this.match(SFMLParser.THEN);
					this.state = 578;
					this.block();
					}
					}
				}
				this.state = 584;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 80, this._ctx);
			}
			this.state = 587;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.ELSE) {
				{
				this.state = 585;
				this.match(SFMLParser.ELSE);
				this.state = 586;
				this.block();
				}
			}

			this.state = 589;
			this.match(SFMLParser.END);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public boolexpr(): BoolexprContext;
	public boolexpr(_p: number): BoolexprContext;
	// @RuleVersion(0)
	public boolexpr(_p?: number): BoolexprContext {
		if (_p === undefined) {
			_p = 0;
		}

		let _parentctx: ParserRuleContext = this._ctx;
		let _parentState: number = this.state;
		let _localctx: BoolexprContext = new BoolexprContext(this._ctx, _parentState);
		let _prevctx: BoolexprContext = _localctx;
		let _startState: number = 86;
		this.enterRecursionRule(_localctx, 86, SFMLParser.RULE_boolexpr, _p);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 623;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 87, this._ctx) ) {
			case 1:
				{
				_localctx = new BooleanTrueContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;

				this.state = 592;
				this.match(SFMLParser.TRUE);
				}
				break;

			case 2:
				{
				_localctx = new BooleanFalseContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 593;
				this.match(SFMLParser.FALSE);
				}
				break;

			case 3:
				{
				_localctx = new BooleanParenContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 594;
				this.match(SFMLParser.LPAREN);
				this.state = 595;
				this.boolexpr(0);
				this.state = 596;
				this.match(SFMLParser.RPAREN);
				}
				break;

			case 4:
				{
				_localctx = new BooleanNegationContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 598;
				this.match(SFMLParser.NOT);
				this.state = 599;
				this.boolexpr(5);
				}
				break;

			case 5:
				{
				_localctx = new BooleanHasContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 601;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 5)) & ~0x1F) === 0 && ((1 << (_la - 5)) & ((1 << (SFMLParser.OVERALL - 5)) | (1 << (SFMLParser.SOME - 5)) | (1 << (SFMLParser.ONE - 5)) | (1 << (SFMLParser.LONE - 5)) | (1 << (SFMLParser.EACH - 5)))) !== 0) || _la === SFMLParser.EVERY) {
					{
					this.state = 600;
					this.setOp();
					}
				}

				this.state = 603;
				this.labelAccess();
				this.state = 604;
				this.match(SFMLParser.HAS);
				this.state = 605;
				this.comparisonOp();
				this.state = 606;
				this.number();
				this.state = 608;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 83, this._ctx) ) {
				case 1:
					{
					this.state = 607;
					this.resourceIdDisjunction();
					}
					break;
				}
				this.state = 611;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 84, this._ctx) ) {
				case 1:
					{
					this.state = 610;
					this.with();
					}
					break;
				}
				this.state = 615;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 85, this._ctx) ) {
				case 1:
					{
					this.state = 613;
					this.match(SFMLParser.EXCEPT);
					this.state = 614;
					this.resourceIdList();
					}
					break;
				}
				}
				break;

			case 6:
				{
				_localctx = new BooleanRedstoneContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 617;
				this.match(SFMLParser.REDSTONE);
				this.state = 621;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 86, this._ctx) ) {
				case 1:
					{
					this.state = 618;
					this.comparisonOp();
					this.state = 619;
					this.number();
					}
					break;
				}
				}
				break;
			}
			this._ctx._stop = this._input.tryLT(-1);
			this.state = 633;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 89, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					if (this._parseListeners != null) {
						this.triggerExitRuleEvent();
					}
					_prevctx = _localctx;
					{
					this.state = 631;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 88, this._ctx) ) {
					case 1:
						{
						_localctx = new BooleanConjunctionContext(new BoolexprContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_boolexpr);
						this.state = 625;
						if (!(this.precpred(this._ctx, 4))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 4)");
						}
						this.state = 626;
						this.match(SFMLParser.AND);
						this.state = 627;
						this.boolexpr(5);
						}
						break;

					case 2:
						{
						_localctx = new BooleanDisjunctionContext(new BoolexprContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_boolexpr);
						this.state = 628;
						if (!(this.precpred(this._ctx, 3))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 3)");
						}
						this.state = 629;
						this.match(SFMLParser.OR);
						this.state = 630;
						this.boolexpr(4);
						}
						break;
					}
					}
				}
				this.state = 635;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 89, this._ctx);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public comparisonOp(): ComparisonOpContext {
		let _localctx: ComparisonOpContext = new ComparisonOpContext(this._ctx, this.state);
		this.enterRule(_localctx, 88, SFMLParser.RULE_comparisonOp);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 636;
			_la = this._input.LA(1);
			if (!((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << SFMLParser.GT) | (1 << SFMLParser.GT_SYMBOL) | (1 << SFMLParser.LT) | (1 << SFMLParser.LT_SYMBOL) | (1 << SFMLParser.EQ) | (1 << SFMLParser.EQ_SYMBOL) | (1 << SFMLParser.LE) | (1 << SFMLParser.LE_SYMBOL) | (1 << SFMLParser.GE) | (1 << SFMLParser.GE_SYMBOL))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public setOp(): SetOpContext {
		let _localctx: SetOpContext = new SetOpContext(this._ctx, this.state);
		this.enterRule(_localctx, 90, SFMLParser.RULE_setOp);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 638;
			_la = this._input.LA(1);
			if (!(((((_la - 5)) & ~0x1F) === 0 && ((1 << (_la - 5)) & ((1 << (SFMLParser.OVERALL - 5)) | (1 << (SFMLParser.SOME - 5)) | (1 << (SFMLParser.ONE - 5)) | (1 << (SFMLParser.LONE - 5)) | (1 << (SFMLParser.EACH - 5)))) !== 0) || _la === SFMLParser.EVERY)) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public labelAccess(): LabelAccessContext {
		let _localctx: LabelAccessContext = new LabelAccessContext(this._ctx, this.state);
		this.enterRule(_localctx, 92, SFMLParser.RULE_labelAccess);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 640;
			this.label();
			this.state = 645;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.COMMA) {
				{
				{
				this.state = 641;
				this.match(SFMLParser.COMMA);
				this.state = 642;
				this.label();
				}
				}
				this.state = 647;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 649;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.ROUND) {
				{
				this.state = 648;
				this.roundrobin();
				}
			}

			this.state = 652;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 32)) & ~0x1F) === 0 && ((1 << (_la - 32)) & ((1 << (SFMLParser.EACH - 32)) | (1 << (SFMLParser.TOP - 32)) | (1 << (SFMLParser.BOTTOM - 32)) | (1 << (SFMLParser.NORTH - 32)) | (1 << (SFMLParser.EAST - 32)) | (1 << (SFMLParser.SOUTH - 32)) | (1 << (SFMLParser.WEST - 32)) | (1 << (SFMLParser.LEFT - 32)) | (1 << (SFMLParser.RIGHT - 32)) | (1 << (SFMLParser.FRONT - 32)) | (1 << (SFMLParser.BACK - 32)) | (1 << (SFMLParser.NULL - 32)))) !== 0)) {
				{
				this.state = 651;
				this.sidequalifier();
				}
			}

			this.state = 655;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.SLOTS || _la === SFMLParser.SLOT) {
				{
				this.state = 654;
				this.slotqualifier();
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public roundrobin(): RoundrobinContext {
		let _localctx: RoundrobinContext = new RoundrobinContext(this._ctx, this.state);
		this.enterRule(_localctx, 94, SFMLParser.RULE_roundrobin);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 657;
			this.match(SFMLParser.ROUND);
			this.state = 658;
			this.match(SFMLParser.ROBIN);
			this.state = 659;
			this.match(SFMLParser.BY);
			this.state = 660;
			_la = this._input.LA(1);
			if (!(_la === SFMLParser.LABEL || _la === SFMLParser.BLOCK)) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public label(): LabelContext {
		let _localctx: LabelContext = new LabelContext(this._ctx, this.state);
		this.enterRule(_localctx, 96, SFMLParser.RULE_label);
		try {
			this.state = 664;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.TOP:
			case SFMLParser.BOTTOM:
			case SFMLParser.LEFT:
			case SFMLParser.RIGHT:
			case SFMLParser.FRONT:
			case SFMLParser.BACK:
			case SFMLParser.SECONDS:
			case SFMLParser.SECOND:
			case SFMLParser.GLOBAL:
			case SFMLParser.OFFSET:
			case SFMLParser.REDSTONE:
			case SFMLParser.LET:
			case SFMLParser.BE:
			case SFMLParser.PLAYER:
			case SFMLParser.OF:
			case SFMLParser.LIKE:
			case SFMLParser.OBJECT:
			case SFMLParser.FIELD:
			case SFMLParser.GUID:
			case SFMLParser.STRING_TYPE:
			case SFMLParser.INVOKE:
			case SFMLParser.CAPABILITY:
			case SFMLParser.AS:
			case SFMLParser.CREATE:
			case SFMLParser.BROADCAST:
			case SFMLParser.CHANNEL:
			case SFMLParser.NEW:
			case SFMLParser.CLIENT:
			case SFMLParser.SERVER:
			case SFMLParser.BTW:
			case SFMLParser.IDENTIFIER:
				_localctx = new RawLabelContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				{
				this.state = 662;
				this.identifier();
				}
				}
				break;
			case SFMLParser.STRING:
				_localctx = new StringLabelContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 663;
				this.string();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public emptyslots(): EmptyslotsContext {
		let _localctx: EmptyslotsContext = new EmptyslotsContext(this._ctx, this.state);
		this.enterRule(_localctx, 98, SFMLParser.RULE_emptyslots);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 666;
			this.match(SFMLParser.EMPTY);
			this.state = 667;
			_la = this._input.LA(1);
			if (!(_la === SFMLParser.SLOTS || _la === SFMLParser.SLOT)) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			this.state = 668;
			this.match(SFMLParser.IN);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public identifier(): IdentifierContext {
		let _localctx: IdentifierContext = new IdentifierContext(this._ctx, this.state);
		this.enterRule(_localctx, 100, SFMLParser.RULE_identifier);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 670;
			_la = this._input.LA(1);
			if (!(((((_la - 46)) & ~0x1F) === 0 && ((1 << (_la - 46)) & ((1 << (SFMLParser.TOP - 46)) | (1 << (SFMLParser.BOTTOM - 46)) | (1 << (SFMLParser.LEFT - 46)) | (1 << (SFMLParser.RIGHT - 46)) | (1 << (SFMLParser.FRONT - 46)) | (1 << (SFMLParser.BACK - 46)) | (1 << (SFMLParser.SECONDS - 46)) | (1 << (SFMLParser.SECOND - 46)) | (1 << (SFMLParser.GLOBAL - 46)) | (1 << (SFMLParser.OFFSET - 46)) | (1 << (SFMLParser.REDSTONE - 46)) | (1 << (SFMLParser.LET - 46)) | (1 << (SFMLParser.BE - 46)) | (1 << (SFMLParser.PLAYER - 46)) | (1 << (SFMLParser.OF - 46)) | (1 << (SFMLParser.LIKE - 46)) | (1 << (SFMLParser.OBJECT - 46)) | (1 << (SFMLParser.FIELD - 46)) | (1 << (SFMLParser.GUID - 46)))) !== 0) || ((((_la - 78)) & ~0x1F) === 0 && ((1 << (_la - 78)) & ((1 << (SFMLParser.STRING_TYPE - 78)) | (1 << (SFMLParser.INVOKE - 78)) | (1 << (SFMLParser.CAPABILITY - 78)) | (1 << (SFMLParser.AS - 78)) | (1 << (SFMLParser.CREATE - 78)) | (1 << (SFMLParser.BROADCAST - 78)) | (1 << (SFMLParser.CHANNEL - 78)) | (1 << (SFMLParser.NEW - 78)) | (1 << (SFMLParser.CLIENT - 78)) | (1 << (SFMLParser.SERVER - 78)) | (1 << (SFMLParser.BTW - 78)) | (1 << (SFMLParser.IDENTIFIER - 78)))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public string(): StringContext {
		let _localctx: StringContext = new StringContext(this._ctx, this.state);
		this.enterRule(_localctx, 102, SFMLParser.RULE_string);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 672;
			this.match(SFMLParser.STRING);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public number(): NumberContext {
		let _localctx: NumberContext = new NumberContext(this._ctx, this.state);
		this.enterRule(_localctx, 104, SFMLParser.RULE_number);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 674;
			this.match(SFMLParser.NUMBER);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	public sempred(_localctx: RuleContext, ruleIndex: number, predIndex: number): boolean {
		switch (ruleIndex) {
		case 34:
			return this.withClause_sempred(_localctx as WithClauseContext, predIndex);

		case 43:
			return this.boolexpr_sempred(_localctx as BoolexprContext, predIndex);
		}
		return true;
	}
	private withClause_sempred(_localctx: WithClauseContext, predIndex: number): boolean {
		switch (predIndex) {
		case 0:
			return this.precpred(this._ctx, 3);

		case 1:
			return this.precpred(this._ctx, 2);
		}
		return true;
	}
	private boolexpr_sempred(_localctx: BoolexprContext, predIndex: number): boolean {
		switch (predIndex) {
		case 2:
			return this.precpred(this._ctx, 4);

		case 3:
			return this.precpred(this._ctx, 3);
		}
		return true;
	}

	private static readonly _serializedATNSegments: number = 2;
	private static readonly _serializedATNSegment0: string =
		"\x03\uC91D\uCABA\u058D\uAFBA\u4F53\u0607\uEA8B\uC241\x03h\u02A7\x04\x02" +
		"\t\x02\x04\x03\t\x03\x04\x04\t\x04\x04\x05\t\x05\x04\x06\t\x06\x04\x07" +
		"\t\x07\x04\b\t\b\x04\t\t\t\x04\n\t\n\x04\v\t\v\x04\f\t\f\x04\r\t\r\x04" +
		"\x0E\t\x0E\x04\x0F\t\x0F\x04\x10\t\x10\x04\x11\t\x11\x04\x12\t\x12\x04" +
		"\x13\t\x13\x04\x14\t\x14\x04\x15\t\x15\x04\x16\t\x16\x04\x17\t\x17\x04" +
		"\x18\t\x18\x04\x19\t\x19\x04\x1A\t\x1A\x04\x1B\t\x1B\x04\x1C\t\x1C\x04" +
		"\x1D\t\x1D\x04\x1E\t\x1E\x04\x1F\t\x1F\x04 \t \x04!\t!\x04\"\t\"\x04#" +
		"\t#\x04$\t$\x04%\t%\x04&\t&\x04\'\t\'\x04(\t(\x04)\t)\x04*\t*\x04+\t+" +
		"\x04,\t,\x04-\t-\x04.\t.\x04/\t/\x040\t0\x041\t1\x042\t2\x043\t3\x044" +
		"\t4\x045\t5\x046\t6\x03\x02\x05\x02n\n\x02\x03\x02\x05\x02q\n\x02\x03" +
		"\x02\x07\x02t\n\x02\f\x02\x0E\x02w\v\x02\x03\x02\x07\x02z\n\x02\f\x02" +
		"\x0E\x02}\v\x02\x03\x02\x03\x02\x03\x03\x03\x03\x03\x03\x03\x04\x03\x04" +
		"\x03\x04\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05" +
		"\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x05\x05\x94\n\x05\x03\x06\x03" +
		"\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x07" +
		"\x06\xA0\n\x06\f\x06\x0E\x06\xA3\v\x06\x03\x06\x05\x06\xA6\n\x06\x03\x07" +
		"\x03\x07\x03\x07\x03\x07\x03\x07\x03\x07\x03\x07\x03\x07\x03\x07\x05\x07" +
		"\xB1\n\x07\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b" +
		"\x03\b\x03\b\x03\b\x05\b\xC0\n\b\x03\t\x05\t\xC3\n\t\x03\t\x05\t\xC6\n" +
		"\t\x03\t\x03\t\x05\t\xCA\n\t\x03\t\x03\t\x03\t\x03\t\x03\t\x05\t\xD1\n" +
		"\t\x03\t\x03\t\x03\t\x05\t\xD6\n\t\x03\t\x03\t\x03\t\x03\t\x03\t\x05\t" +
		"\xDD\n\t\x05\t\xDF\n\t\x03\n\x03\n\x03\v\x07\v\xE4\n\v\f\v\x0E\v\xE7\v" +
		"\v\x03\f\x03\f\x03\f\x03\f\x03\f\x03\f\x03\f\x05\f\xF0\n\f\x03\r\x03\r" +
		"\x03\r\x03\r\x03\r\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03" +
		"\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x07\x0E\u0105" +
		"\n\x0E\f\x0E\x0E\x0E\u0108\v\x0E\x05\x0E\u010A\n\x0E\x03\x0F\x03\x0F\x03" +
		"\x0F\x03\x0F\x03\x10\x03\x10\x03\x10\x03\x10\x05\x10\u0114\n\x10\x03\x11" +
		"\x03\x11\x03\x11\x03\x11\x03\x11\x03\x11\x03\x12\x03\x12\x03\x12\x03\x12" +
		"\x03\x12\x05\x12\u0121\n\x12\x03\x13\x03\x13\x05\x13\u0125\n\x13\x03\x13" +
		"\x03\x13\x07\x13\u0129\n\x13\f\x13\x0E\x13\u012C\v\x13\x03\x13\x05\x13" +
		"\u012F\n\x13\x03\x14\x03\x14\x05\x14\u0133\n\x14\x03\x14\x05\x14\u0136" +
		"\n\x14\x03\x14\x05\x14\u0139\n\x14\x03\x14\x03\x14\x05\x14\u013D\n\x14" +
		"\x03\x14\x03\x14\x05\x14\u0141\n\x14\x03\x14\x03\x14\x05\x14\u0145\n\x14" +
		"\x03\x14\x03\x14\x03\x14\x05\x14\u014A\n\x14\x03\x14\x05\x14\u014D\n\x14" +
		"\x03\x14\x05\x14\u0150\n\x14\x03\x14\x05\x14\u0153\n\x14\x05\x14\u0155" +
		"\n\x14\x03\x15\x03\x15\x03\x15\x03\x15\x03\x15\x05\x15\u015C\n\x15\x03" +
		"\x16\x03\x16\x03\x16\x03\x17\x03\x17\x05\x17\u0163\n\x17\x03\x17\x05\x17" +
		"\u0166\n\x17\x03\x17\x03\x17\x05\x17\u016A\n\x17\x03\x17\x05\x17\u016D" +
		"\n\x17\x03\x17\x03\x17\x03\x17\x05\x17\u0172\n\x17\x03\x17\x05\x17\u0175" +
		"\n\x17\x03\x17\x03\x17\x03\x17\x05\x17\u017A\n\x17\x03\x17\x05\x17\u017D" +
		"\n\x17\x05\x17\u017F\n\x17\x03\x18\x03\x18\x03\x19\x03\x19\x03\x1A\x03" +
		"\x1A\x03\x1A\x07\x1A\u0188\n\x1A\f\x1A\x0E\x1A\u018B\v\x1A\x03\x1A\x05" +
		"\x1A\u018E\n\x1A\x03\x1B\x05\x1B\u0191\n\x1B\x03\x1B\x03\x1B\x05\x1B\u0195" +
		"\n\x1B\x03\x1B\x03\x1B\x05\x1B\u0199\n\x1B\x03\x1B\x05\x1B\u019C\n\x1B" +
		"\x03\x1C\x03\x1C\x03\x1C\x03\x1C\x03\x1C\x05\x1C\u01A3\n\x1C\x03\x1D\x03" +
		"\x1D\x05\x1D\u01A7\n\x1D\x03\x1E\x03\x1E\x03\x1E\x05\x1E\u01AC\n\x1E\x03" +
		"\x1F\x03\x1F\x03\x1F\x03 \x03 \x03 \x05 \u01B4\n \x03 \x03 \x05 \u01B8" +
		"\n \x03 \x03 \x05 \u01BC\n \x05 \u01BE\n \x05 \u01C0\n \x05 \u01C2\n " +
		"\x03 \x05 \u01C5\n \x03!\x03!\x03!\x07!\u01CA\n!\f!\x0E!\u01CD\v!\x03" +
		"!\x05!\u01D0\n!\x03\"\x03\"\x03\"\x07\"\u01D5\n\"\f\"\x0E\"\u01D8\v\"" +
		"\x03\"\x05\"\u01DB\n\"\x03#\x03#\x03#\x03#\x05#\u01E1\n#\x03$\x03$\x03" +
		"$\x03$\x03$\x03$\x03$\x03$\x03$\x05$\u01EC\n$\x03$\x05$\u01EF\n$\x03$" +
		"\x05$\u01F2\n$\x03$\x03$\x03$\x03$\x03$\x03$\x07$\u01FA\n$\f$\x0E$\u01FD" +
		"\v$\x03%\x03%\x03%\x03%\x03%\x07%\u0204\n%\f%\x0E%\u0207\v%\x03%\x03%" +
		"\x03%\x07%\u020C\n%\f%\x0E%\u020F\v%\x05%\u0211\n%\x03&\x03&\x03&\x03" +
		"&\x03&\x07&\u0218\n&\f&\x0E&\u021B\v&\x03\'\x03\'\x03\'\x03\'\x03\'\x07" +
		"\'\u0222\n\'\f\'\x0E\'\u0225\v\'\x03\'\x03\'\x05\'\u0229\n\'\x03(\x03" +
		"(\x03)\x03)\x03)\x03*\x03*\x03*\x07*\u0233\n*\f*\x0E*\u0236\v*\x03+\x03" +
		"+\x03+\x05+\u023B\n+\x03,\x03,\x03,\x03,\x03,\x03,\x03,\x03,\x03,\x03" +
		",\x07,\u0247\n,\f,\x0E,\u024A\v,\x03,\x03,\x05,\u024E\n,\x03,\x03,\x03" +
		"-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x05-\u025C\n-\x03-\x03" +
		"-\x03-\x03-\x03-\x05-\u0263\n-\x03-\x05-\u0266\n-\x03-\x03-\x05-\u026A" +
		"\n-\x03-\x03-\x03-\x03-\x05-\u0270\n-\x05-\u0272\n-\x03-\x03-\x03-\x03" +
		"-\x03-\x03-\x07-\u027A\n-\f-\x0E-\u027D\v-\x03.\x03.\x03/\x03/\x030\x03" +
		"0\x030\x070\u0286\n0\f0\x0E0\u0289\v0\x030\x050\u028C\n0\x030\x050\u028F" +
		"\n0\x030\x050\u0292\n0\x031\x031\x031\x031\x031\x032\x032\x052\u029B\n" +
		"2\x033\x033\x033\x033\x034\x034\x035\x035\x036\x036\x036\x02\x02\x04F" +
		"X7\x02\x02\x04\x02\x06\x02\b\x02\n\x02\f\x02\x0E\x02\x10\x02\x12\x02\x14" +
		"\x02\x16\x02\x18\x02\x1A\x02\x1C\x02\x1E\x02 \x02\"\x02$\x02&\x02(\x02" +
		"*\x02,\x02.\x020\x022\x024\x026\x028\x02:\x02<\x02>\x02@\x02B\x02D\x02" +
		"F\x02H\x02J\x02L\x02N\x02P\x02R\x02T\x02V\x02X\x02Z\x02\\\x02^\x02`\x02" +
		"b\x02d\x02f\x02h\x02j\x02\x02\n\x03\x02XY\x03\x02<?\x04\x02057;\x03\x02" +
		"\x1F \x03\x02\x10\x19\x05\x02\x07\n\"\"[[\x03\x02./\b\x02017:>@BCHZdd" +
		"\x02\u02E1\x02m\x03\x02\x02\x02\x04\x80\x03\x02\x02\x02\x06\x83\x03\x02" +
		"\x02\x02\b\x93\x03\x02\x02\x02\n\xA5\x03\x02\x02\x02\f\xB0\x03\x02\x02" +
		"\x02\x0E\xBF\x03\x02\x02\x02\x10\xDE\x03\x02\x02\x02\x12\xE0\x03\x02\x02" +
		"\x02\x14\xE5\x03\x02\x02\x02\x16\xEF\x03\x02\x02\x02\x18\xF1\x03\x02\x02" +
		"\x02\x1A\u0109\x03\x02\x02\x02\x1C\u010B\x03\x02\x02\x02\x1E\u0113\x03" +
		"\x02\x02\x02 \u0115\x03\x02\x02\x02\"\u011B\x03\x02\x02\x02$\u0122\x03" +
		"\x02\x02\x02&\u0154\x03\x02\x02\x02(\u015B\x03\x02\x02\x02*\u015D\x03" +
		"\x02\x02\x02,\u017E\x03\x02\x02\x02.\u0180\x03\x02\x02\x020\u0182\x03" +
		"\x02\x02\x022\u0184\x03\x02\x02\x024\u019B\x03\x02\x02\x026\u01A2\x03" +
		"\x02\x02\x028\u01A4\x03\x02\x02\x02:\u01A8\x03\x02\x02\x02<\u01AD\x03" +
		"\x02\x02\x02>\u01C4\x03\x02\x02\x02@\u01C6\x03\x02\x02\x02B\u01D1\x03" +
		"\x02\x02\x02D\u01E0\x03\x02\x02\x02F\u01F1\x03\x02\x02\x02H\u0210\x03" +
		"\x02\x02\x02J\u0212\x03\x02\x02\x02L\u0228\x03\x02\x02\x02N\u022A\x03" +
		"\x02\x02\x02P\u022C\x03\x02\x02\x02R\u022F\x03\x02\x02\x02T\u0237\x03" +
		"\x02\x02\x02V\u023C\x03\x02\x02\x02X\u0271\x03\x02\x02\x02Z\u027E\x03" +
		"\x02\x02\x02\\\u0280\x03\x02\x02\x02^\u0282\x03\x02\x02\x02`\u0293\x03" +
		"\x02\x02\x02b\u029A\x03\x02\x02\x02d\u029C\x03\x02\x02\x02f\u02A0\x03" +
		"\x02\x02\x02h\u02A2\x03\x02\x02\x02j\u02A4\x03\x02\x02\x02ln\x05\x04\x03" +
		"\x02ml\x03\x02\x02\x02mn\x03\x02\x02\x02np\x03\x02\x02\x02oq\x05\x06\x04" +
		"\x02po\x03\x02\x02\x02pq\x03\x02\x02\x02qu\x03\x02\x02\x02rt\x05\b\x05" +
		"\x02sr\x03\x02\x02\x02tw\x03\x02\x02\x02us\x03\x02\x02\x02uv\x03\x02\x02" +
		"\x02v{\x03\x02\x02\x02wu\x03\x02\x02\x02xz\x05\x0E\b\x02yx\x03\x02\x02" +
		"\x02z}\x03\x02\x02\x02{y\x03\x02\x02\x02{|\x03\x02\x02\x02|~\x03\x02\x02" +
		"\x02}{\x03\x02\x02\x02~\x7F\x07\x02\x02\x03\x7F\x03\x03\x02\x02\x02\x80" +
		"\x81\t\x02\x02\x02\x81\x82\x07Z\x02\x02\x82\x05\x03\x02\x02\x02\x83\x84" +
		"\x07G\x02\x02\x84\x85\x05h5\x02\x85\x07\x03\x02\x02\x02\x86\x87\x07H\x02" +
		"\x02\x87\x88\x05f4\x02\x88\x89\x07I\x02\x02\x89\x8A\x07J\x02\x02\x8A\x8B" +
		"\x07K\x02\x02\x8B\x8C\x05f4\x02\x8C\x94\x03\x02\x02\x02\x8D\x8E\x07H\x02" +
		"\x02\x8E\x8F\x05f4\x02\x8F\x90\x07I\x02\x02\x90\x91\x07L\x02\x02\x91\x92" +
		"\x05\n\x06\x02\x92\x94\x03\x02\x02\x02\x93\x86\x03\x02\x02\x02\x93\x8D" +
		"\x03\x02\x02\x02\x94\t\x03\x02\x02\x02\x95\xA6\x07O\x02\x02\x96\xA6\x07" +
		"P\x02\x02\x97\xA6\x05h5\x02\x98\x99\x07M\x02\x02\x99\x9A\x07(\x02\x02" +
		"\x9A\x9B\x07N\x02\x02\x9B\xA1\x05\f\x07\x02\x9C\x9D\x07\x0E\x02\x02\x9D" +
		"\x9E\x07N\x02\x02\x9E\xA0\x05\f\x07\x02\x9F\x9C\x03\x02\x02\x02\xA0\xA3" +
		"\x03\x02\x02\x02\xA1\x9F\x03\x02\x02\x02\xA1\xA2\x03\x02\x02\x02\xA2\xA6" +
		"\x03\x02\x02\x02\xA3\xA1\x03\x02\x02\x02\xA4\xA6\x05f4\x02\xA5\x95\x03" +
		"\x02\x02\x02\xA5\x96\x03\x02\x02\x02\xA5\x97\x03\x02\x02\x02\xA5\x98\x03" +
		"\x02\x02\x02\xA5\xA4\x03\x02\x02\x02\xA6\v\x03\x02\x02\x02\xA7\xA8\x05" +
		"f4\x02\xA8\xA9\x07K\x02\x02\xA9\xAA\x05h5\x02\xAA\xB1\x03\x02\x02\x02" +
		"\xAB\xAC\x05f4\x02\xAC\xAD\x07L\x02\x02\xAD\xAE\x05f4\x02\xAE\xB1\x03" +
		"\x02\x02\x02\xAF\xB1\x05f4\x02\xB0\xA7\x03\x02\x02\x02\xB0\xAB\x03\x02" +
		"\x02\x02\xB0\xAF\x03\x02\x02\x02\xB1\r\x03\x02\x02\x02\xB2\xB3\x07[\x02" +
		"\x02\xB3\xB4\x05\x10\t\x02\xB4\xB5\x07E\x02\x02\xB5\xB6\x05\x14\v\x02" +
		"\xB6\xB7\x07F\x02\x02\xB7\xC0\x03\x02\x02\x02\xB8\xB9\x07[\x02\x02\xB9" +
		"\xBA\x07C\x02\x02\xBA\xBB\x07D\x02\x02\xBB\xBC\x07E\x02\x02\xBC\xBD\x05" +
		"\x14\v\x02\xBD\xBE\x07F\x02\x02\xBE\xC0\x03\x02\x02\x02\xBF\xB2\x03\x02" +
		"\x02\x02\xBF\xB8\x03\x02\x02\x02\xC0\x0F\x03\x02\x02\x02\xC1\xC3\x07c" +
		"\x02\x02\xC2\xC1\x03\x02\x02\x02\xC2\xC3\x03\x02\x02\x02\xC3\xC5\x03\x02" +
		"\x02\x02\xC4\xC6\x07@\x02\x02\xC5\xC4\x03\x02\x02\x02\xC5\xC6\x03\x02" +
		"\x02\x02\xC6\xC9\x03\x02\x02\x02\xC7\xC8\x07A\x02\x02\xC8\xCA\x07c\x02" +
		"\x02\xC9\xC7\x03\x02\x02\x02\xC9\xCA\x03\x02\x02\x02\xCA\xCB\x03\x02\x02" +
		"\x02\xCB\xD0\x05\x12\n\x02\xCC\xCD\x07B\x02\x02\xCD\xCE\x07-\x02\x02\xCE" +
		"\xCF\x07c\x02\x02\xCF\xD1\x05\x12\n\x02\xD0\xCC\x03\x02\x02\x02\xD0\xD1" +
		"\x03\x02\x02\x02\xD1\xDF\x03\x02\x02\x02\xD2\xD5\x07b\x02\x02\xD3\xD4" +
		"\x07A\x02\x02\xD4\xD6\x07c\x02\x02\xD5\xD3\x03\x02\x02\x02\xD5\xD6\x03" +
		"\x02\x02\x02\xD6\xD7\x03\x02\x02\x02\xD7\xDC\x05\x12\n\x02\xD8\xD9\x07" +
		"B\x02\x02\xD9\xDA\x07-\x02\x02\xDA\xDB\x07c\x02\x02\xDB\xDD\x05\x12\n" +
		"\x02\xDC\xD8\x03\x02\x02\x02\xDC\xDD\x03\x02\x02\x02\xDD\xDF\x03\x02\x02" +
		"\x02\xDE\xC2\x03\x02\x02\x02\xDE\xD2\x03\x02\x02\x02\xDF\x11\x03\x02\x02" +
		"\x02\xE0\xE1\t\x03\x02\x02\xE1\x13\x03\x02\x02\x02\xE2\xE4\x05\x16\f\x02" +
		"\xE3\xE2\x03\x02\x02\x02\xE4\xE7\x03\x02\x02\x02\xE5\xE3\x03\x02\x02\x02" +
		"\xE5\xE6\x03\x02\x02\x02\xE6\x15\x03\x02\x02\x02\xE7\xE5\x03\x02\x02\x02" +
		"\xE8\xF0\x05&\x14\x02\xE9\xF0\x05,\x17\x02\xEA\xF0\x05V,\x02\xEB\xF0\x05" +
		"$\x13\x02\xEC\xF0\x05\x18\r\x02\xED\xF0\x05 \x11\x02\xEE\xF0\x05\"\x12" +
		"\x02\xEF\xE8\x03\x02\x02\x02\xEF\xE9\x03\x02\x02\x02\xEF\xEA\x03\x02\x02" +
		"\x02\xEF\xEB\x03\x02\x02\x02\xEF\xEC\x03\x02\x02\x02\xEF\xED\x03\x02\x02" +
		"\x02\xEF\xEE\x03\x02\x02\x02\xF0\x17\x03\x02\x02\x02\xF1\xF2\x07H\x02" +
		"\x02\xF2\xF3\x05f4\x02\xF3\xF4\x07I\x02\x02\xF4\xF5\x05\x1A\x0E\x02\xF5" +
		"\x19\x03\x02\x02\x02\xF6\xF7\x07P\x02\x02\xF7\xF8\x07K\x02\x02\xF8\xF9" +
		"\x07Q\x02\x02\xF9\xFA\x05J&\x02\xFA\xFB\x07(\x02\x02\xFB\xFC\x05f4\x02" +
		"\xFC\u010A\x03\x02\x02\x02\xFD\xFE\x05f4\x02\xFE\xFF\x07(\x02\x02\xFF" +
		"\u0100\x07N\x02\x02\u0100\u0106\x05\x1C\x0F\x02\u0101\u0102\x07\x0E\x02" +
		"\x02\u0102\u0103\x07N\x02\x02\u0103\u0105\x05\x1C\x0F\x02\u0104\u0101" +
		"\x03\x02\x02\x02\u0105\u0108\x03\x02\x02\x02\u0106\u0104\x03\x02\x02\x02" +
		"\u0106\u0107\x03\x02\x02\x02\u0107\u010A\x03\x02\x02\x02\u0108\u0106\x03" +
		"\x02\x02\x02\u0109\xF6\x03\x02\x02\x02\u0109\xFD\x03\x02\x02\x02\u010A" +
		"\x1B\x03\x02\x02\x02\u010B\u010C\x05f4\x02\u010C\u010D\x07K\x02\x02\u010D" +
		"\u010E\x05\x1E\x10\x02\u010E\x1D\x03\x02\x02\x02\u010F\u0110\x07W\x02" +
		"\x02\u0110\u0114\x07O\x02\x02\u0111\u0114\x05h5\x02\u0112\u0114\x05f4" +
		"\x02\u0113\u010F\x03\x02\x02\x02\u0113\u0111\x03\x02\x02\x02\u0113\u0112" +
		"\x03\x02\x02\x02\u0114\x1F\x03\x02\x02\x02\u0115\u0116\x07T\x02\x02\u0116" +
		"\u0117\x07\x1C\x02\x02\u0117\u0118\x05J&\x02\u0118\u0119\x07(\x02\x02" +
		"\u0119\u011A\x05f4\x02\u011A!\x03\x02\x02\x02\u011B\u011C\x07U\x02\x02" +
		"\u011C\u011D\x07\x1B\x02\x02\u011D\u0120\x05f4\x02\u011E\u011F\x07V\x02" +
		"\x02\u011F\u0121\x05J&\x02\u0120\u011E\x03\x02\x02\x02\u0120\u0121\x03" +
		"\x02\x02\x02\u0121#\x03\x02\x02\x02\u0122\u0124\x07$\x02\x02\u0123\u0125" +
		"\x05b2\x02\u0124\u0123\x03\x02\x02\x02\u0124\u0125\x03\x02\x02\x02\u0125" +
		"\u012A\x03\x02\x02\x02\u0126\u0127\x07\\\x02\x02\u0127\u0129\x05b2\x02" +
		"\u0128\u0126\x03\x02\x02\x02\u0129\u012C\x03\x02\x02\x02\u012A\u0128\x03" +
		"\x02\x02\x02\u012A\u012B\x03\x02\x02\x02\u012B\u012E\x03\x02\x02\x02\u012C" +
		"\u012A\x03\x02\x02\x02\u012D\u012F\x07\\\x02\x02\u012E\u012D\x03\x02\x02" +
		"\x02\u012E\u012F\x03\x02\x02\x02\u012F%\x03\x02\x02\x02\u0130\u0132\x07" +
		"\x1C\x02\x02\u0131\u0133\x05(\x15\x02\u0132\u0131\x03\x02\x02\x02\u0132" +
		"\u0133\x03\x02\x02\x02\u0133\u0135\x03\x02\x02\x02\u0134\u0136\x05.\x18" +
		"\x02\u0135\u0134\x03\x02\x02\x02\u0135\u0136\x03\x02\x02\x02\u0136\u0138" +
		"\x03\x02\x02\x02\u0137\u0139\x05<\x1F\x02\u0138\u0137\x03\x02\x02\x02" +
		"\u0138\u0139\x03\x02\x02\x02\u0139\u013A\x03\x02\x02\x02\u013A\u013C\x07" +
		"\x1A\x02\x02\u013B\u013D\x07\"\x02\x02\u013C\u013B\x03\x02\x02\x02\u013C" +
		"\u013D\x03\x02\x02\x02\u013D\u013E\x03\x02\x02\x02\u013E\u0140\x05^0\x02" +
		"\u013F\u0141\x05*\x16\x02\u0140\u013F\x03\x02\x02\x02\u0140\u0141\x03" +
		"\x02\x02\x02\u0141\u0155\x03\x02\x02\x02\u0142\u0144\x07\x1A\x02\x02\u0143" +
		"\u0145\x07\"\x02\x02\u0144\u0143\x03\x02\x02\x02\u0144\u0145\x03\x02\x02" +
		"\x02\u0145\u0146\x03\x02\x02\x02\u0146\u0147\x05^0\x02\u0147\u0149\x07" +
		"\x1C\x02\x02\u0148\u014A\x05(\x15\x02\u0149\u0148\x03\x02\x02\x02\u0149" +
		"\u014A\x03\x02\x02\x02\u014A\u014C\x03\x02\x02\x02\u014B\u014D\x05.\x18" +
		"\x02\u014C\u014B\x03\x02\x02\x02\u014C\u014D\x03\x02\x02\x02\u014D\u014F" +
		"\x03\x02\x02\x02\u014E\u0150\x05<\x1F\x02\u014F\u014E\x03\x02\x02\x02" +
		"\u014F\u0150\x03\x02\x02\x02\u0150\u0152\x03\x02\x02\x02\u0151\u0153\x05" +
		"*\x16\x02\u0152\u0151\x03\x02\x02\x02\u0152\u0153\x03\x02\x02\x02\u0153" +
		"\u0155\x03\x02\x02\x02\u0154\u0130\x03\x02\x02\x02\u0154\u0142\x03\x02" +
		"\x02\x02\u0155\'\x03\x02\x02\x02\u0156\u0157\x07(\x02\x02\u0157\u0158" +
		"\x07R\x02\x02\u0158\u015C\x05J&\x02\u0159\u015A\x07L\x02\x02\u015A\u015C" +
		"\x05f4\x02\u015B\u0156\x03\x02\x02\x02\u015B\u0159\x03\x02\x02\x02\u015C" +
		")\x03\x02\x02\x02\u015D\u015E\x07S\x02\x02\u015E\u015F\x05f4\x02\u015F" +
		"+\x03\x02\x02\x02\u0160\u0162\x07\x1D\x02\x02\u0161\u0163\x050\x19\x02" +
		"\u0162\u0161\x03\x02\x02\x02\u0162\u0163\x03\x02\x02\x02\u0163\u0165\x03" +
		"\x02\x02\x02\u0164\u0166\x05<\x1F\x02\u0165\u0164\x03\x02\x02\x02\u0165" +
		"\u0166\x03\x02\x02\x02\u0166\u0167\x03\x02\x02\x02\u0167\u0169\x07\x1B" +
		"\x02\x02\u0168\u016A\x05d3\x02\u0169\u0168\x03\x02\x02\x02\u0169\u016A" +
		"\x03\x02\x02\x02\u016A\u016C\x03\x02\x02\x02\u016B\u016D\x07\"\x02\x02" +
		"\u016C\u016B\x03\x02\x02\x02\u016C\u016D\x03\x02\x02\x02\u016D\u016E\x03" +
		"\x02\x02\x02\u016E\u017F\x05^0\x02\u016F\u0171\x07\x1B\x02\x02\u0170\u0172" +
		"\x05d3\x02\u0171\u0170\x03\x02\x02\x02\u0171\u0172\x03\x02\x02\x02\u0172" +
		"\u0174\x03\x02\x02\x02\u0173\u0175\x07\"\x02\x02\u0174\u0173\x03\x02\x02" +
		"\x02\u0174\u0175\x03\x02\x02\x02\u0175\u0176\x03\x02\x02\x02\u0176\u0177" +
		"\x05^0\x02\u0177\u0179\x07\x1D\x02\x02\u0178\u017A\x050\x19\x02\u0179" +
		"\u0178\x03\x02\x02\x02\u0179\u017A\x03\x02\x02\x02\u017A\u017C\x03\x02" +
		"\x02\x02\u017B\u017D\x05<\x1F\x02\u017C\u017B\x03\x02\x02\x02\u017C\u017D" +
		"\x03\x02\x02\x02\u017D\u017F\x03\x02\x02\x02\u017E\u0160\x03\x02\x02\x02" +
		"\u017E\u016F\x03\x02\x02\x02\u017F-\x03\x02\x02\x02\u0180\u0181\x052\x1A" +
		"\x02\u0181/\x03\x02\x02\x02\u0182\u0183\x052\x1A\x02\u01831\x03\x02\x02" +
		"\x02\u0184\u0189\x054\x1B\x02\u0185\u0186\x07\\\x02\x02\u0186\u0188\x05" +
		"4\x1B\x02\u0187\u0185\x03\x02\x02\x02\u0188\u018B\x03\x02\x02\x02\u0189" +
		"\u0187\x03\x02\x02\x02\u0189\u018A\x03\x02\x02\x02\u018A\u018D\x03\x02" +
		"\x02\x02\u018B\u0189\x03\x02\x02\x02\u018C\u018E\x07\\\x02\x02\u018D\u018C" +
		"\x03\x02\x02\x02\u018D\u018E\x03\x02\x02\x02\u018E3\x03\x02\x02\x02\u018F" +
		"\u0191\x056\x1C\x02\u0190\u018F\x03\x02\x02\x02\u0190\u0191\x03\x02\x02" +
		"\x02\u0191\u0192\x03\x02\x02\x02\u0192\u0194\x05B\"\x02\u0193\u0195\x05" +
		"D#\x02\u0194\u0193\x03\x02\x02\x02\u0194\u0195\x03\x02\x02\x02\u0195\u019C" +
		"\x03\x02\x02\x02\u0196\u0198\x056\x1C\x02\u0197\u0199\x05D#\x02\u0198" +
		"\u0197\x03\x02\x02\x02\u0198\u0199\x03\x02\x02\x02\u0199\u019C\x03\x02" +
		"\x02\x02\u019A\u019C\x05D#\x02\u019B\u0190\x03\x02\x02\x02\u019B\u0196" +
		"\x03\x02\x02\x02\u019B\u019A\x03\x02\x02\x02\u019C5\x03\x02\x02\x02\u019D" +
		"\u019E\x058\x1D\x02\u019E\u019F\x05:\x1E\x02\u019F\u01A3\x03\x02\x02\x02" +
		"\u01A0\u01A3\x05:\x1E\x02\u01A1\u01A3\x058\x1D\x02\u01A2\u019D\x03\x02" +
		"\x02\x02\u01A2\u01A0\x03\x02\x02\x02\u01A2\u01A1\x03\x02\x02\x02\u01A3" +
		"7\x03\x02\x02\x02\u01A4\u01A6\x05j6\x02\u01A5\u01A7\x07\"\x02\x02\u01A6" +
		"\u01A5\x03\x02\x02\x02\u01A6\u01A7\x03\x02\x02\x02\u01A79\x03\x02\x02" +
		"\x02\u01A8\u01A9\x07!\x02\x02\u01A9\u01AB\x05j6\x02\u01AA\u01AC\x07\"" +
		"\x02\x02\u01AB\u01AA\x03\x02\x02\x02\u01AB\u01AC\x03\x02\x02\x02\u01AC" +
		";\x03\x02\x02\x02\u01AD\u01AE\x07#\x02\x02\u01AE\u01AF\x05@!\x02\u01AF" +
		"=\x03\x02\x02\x02\u01B0\u01C1\x05f4\x02\u01B1\u01B3\x07]\x02\x02\u01B2" +
		"\u01B4\x05f4\x02\u01B3\u01B2\x03\x02\x02\x02\u01B3\u01B4\x03\x02\x02\x02" +
		"\u01B4\u01BF\x03\x02\x02\x02\u01B5\u01B7\x07]\x02\x02\u01B6\u01B8\x05" +
		"f4\x02\u01B7\u01B6\x03\x02\x02\x02\u01B7\u01B8\x03\x02\x02\x02\u01B8\u01BD" +
		"\x03\x02\x02\x02\u01B9\u01BB\x07]\x02\x02\u01BA\u01BC\x05f4\x02\u01BB" +
		"\u01BA\x03\x02\x02\x02\u01BB\u01BC\x03\x02\x02\x02\u01BC\u01BE\x03\x02" +
		"\x02\x02\u01BD\u01B9\x03\x02\x02\x02\u01BD\u01BE\x03\x02\x02\x02\u01BE" +
		"\u01C0\x03\x02\x02\x02\u01BF\u01B5\x03\x02\x02\x02\u01BF\u01C0\x03\x02" +
		"\x02\x02\u01C0\u01C2\x03\x02\x02\x02\u01C1\u01B1\x03\x02\x02\x02\u01C1" +
		"\u01C2\x03\x02\x02\x02\u01C2\u01C5\x03\x02\x02\x02\u01C3\u01C5\x05h5\x02" +
		"\u01C4\u01B0\x03\x02\x02\x02\u01C4\u01C3\x03\x02\x02\x02\u01C5?\x03\x02" +
		"\x02\x02\u01C6\u01CB\x05> \x02\u01C7\u01C8\x07\\\x02\x02\u01C8\u01CA\x05" +
		"> \x02\u01C9\u01C7\x03\x02\x02\x02\u01CA\u01CD\x03\x02\x02\x02\u01CB\u01C9" +
		"\x03\x02\x02\x02\u01CB\u01CC\x03\x02\x02\x02\u01CC\u01CF\x03\x02\x02\x02" +
		"\u01CD\u01CB\x03\x02\x02\x02\u01CE\u01D0\x07\\\x02\x02\u01CF\u01CE\x03" +
		"\x02\x02\x02\u01CF\u01D0\x03\x02\x02\x02\u01D0A\x03\x02\x02\x02\u01D1" +
		"\u01D6\x05> \x02\u01D2\u01D3\x07\x0F\x02\x02\u01D3\u01D5\x05> \x02\u01D4" +
		"\u01D2\x03\x02\x02\x02\u01D5\u01D8\x03\x02\x02\x02\u01D6\u01D4\x03\x02" +
		"\x02\x02\u01D6\u01D7\x03\x02\x02\x02\u01D7\u01DA\x03\x02\x02\x02\u01D8" +
		"\u01D6\x03\x02\x02\x02\u01D9\u01DB\x07\x0F\x02\x02\u01DA\u01D9\x03\x02" +
		"\x02\x02\u01DA\u01DB\x03\x02\x02\x02\u01DBC\x03\x02\x02\x02\u01DC\u01DD" +
		"\x07(\x02\x02\u01DD\u01E1\x05F$\x02\u01DE\u01DF\x07\'\x02\x02\u01DF\u01E1" +
		"\x05F$\x02\u01E0\u01DC\x03\x02\x02\x02\u01E0\u01DE\x03\x02\x02\x02\u01E1" +
		"E\x03\x02\x02\x02\u01E2\u01E3\b$\x01\x02\u01E3\u01E4\x07`\x02\x02\u01E4" +
		"\u01E5\x05F$\x02\u01E5\u01E6\x07a\x02\x02\u01E6\u01F2\x03\x02\x02\x02" +
		"\u01E7\u01E8\x07\r\x02\x02\u01E8\u01F2\x05F$\x06\u01E9\u01EB\x07)\x02" +
		"\x02\u01EA\u01EC\x07*\x02\x02\u01EB\u01EA\x03\x02\x02\x02\u01EB\u01EC" +
		"\x03\x02\x02\x02\u01EC\u01EF\x03\x02\x02\x02\u01ED\u01EF\x07*\x02\x02" +
		"\u01EE\u01E9\x03\x02\x02\x02\u01EE\u01ED\x03\x02\x02\x02\u01EF\u01F0\x03" +
		"\x02\x02\x02\u01F0\u01F2\x05H%\x02\u01F1\u01E2\x03\x02\x02\x02\u01F1\u01E7" +
		"\x03\x02\x02\x02\u01F1\u01EE\x03\x02\x02\x02\u01F2\u01FB\x03\x02\x02\x02" +
		"\u01F3\u01F4\f\x05\x02\x02\u01F4\u01F5\x07\x0E\x02\x02\u01F5\u01FA\x05" +
		"F$\x06\u01F6\u01F7\f\x04\x02\x02\u01F7\u01F8\x07\x0F\x02\x02\u01F8\u01FA" +
		"\x05F$\x05\u01F9\u01F3\x03\x02\x02\x02\u01F9\u01F6\x03\x02\x02\x02\u01FA" +
		"\u01FD\x03\x02\x02\x02\u01FB\u01F9\x03\x02\x02\x02\u01FB\u01FC\x03\x02" +
		"\x02\x02\u01FCG\x03\x02\x02\x02\u01FD\u01FB\x03\x02\x02\x02\u01FE\u01FF" +
		"\x05f4\x02\u01FF\u0200\x07]\x02\x02\u0200\u0205\x05f4\x02\u0201\u0202" +
		"\x07^\x02\x02\u0202\u0204\x05f4\x02\u0203\u0201\x03\x02\x02\x02\u0204" +
		"\u0207\x03\x02\x02\x02\u0205\u0203\x03\x02\x02\x02\u0205\u0206\x03\x02" +
		"\x02\x02\u0206\u0211\x03\x02\x02\x02\u0207\u0205\x03\x02\x02\x02\u0208" +
		"\u020D\x05f4\x02\u0209\u020A\x07^\x02\x02\u020A\u020C\x05f4\x02\u020B" +
		"\u0209\x03\x02\x02\x02\u020C\u020F\x03\x02\x02\x02\u020D\u020B\x03\x02" +
		"\x02\x02\u020D\u020E\x03\x02\x02\x02\u020E\u0211\x03\x02\x02\x02\u020F" +
		"\u020D\x03\x02\x02";
	private static readonly _serializedATNSegment1: string =
		"\x02\u0210\u01FE\x03\x02\x02\x02\u0210\u0208\x03\x02\x02\x02\u0211I\x03" +
		"\x02\x02\x02\u0212\u0213\x05f4\x02\u0213\u0214\x07]\x02\x02\u0214\u0219" +
		"\x05f4\x02\u0215\u0216\x07^\x02\x02\u0216\u0218\x05f4\x02\u0217\u0215" +
		"\x03\x02\x02\x02\u0218\u021B\x03\x02\x02\x02\u0219\u0217\x03\x02\x02\x02" +
		"\u0219\u021A\x03\x02\x02\x02\u021AK\x03\x02\x02\x02\u021B\u0219\x03\x02" +
		"\x02\x02\u021C\u021D\x07\"\x02\x02\u021D\u0229\x076\x02\x02\u021E\u0223" +
		"\x05N(\x02\u021F\u0220\x07\\\x02\x02\u0220\u0222\x05N(\x02\u0221\u021F" +
		"\x03\x02\x02\x02\u0222\u0225\x03\x02\x02\x02\u0223\u0221\x03\x02\x02\x02" +
		"\u0223\u0224\x03\x02\x02\x02\u0224\u0226\x03\x02\x02\x02\u0225\u0223\x03" +
		"\x02\x02\x02\u0226\u0227\x076\x02\x02\u0227\u0229\x03\x02\x02\x02\u0228" +
		"\u021C\x03\x02\x02\x02\u0228\u021E\x03\x02\x02\x02\u0229M\x03\x02\x02" +
		"\x02\u022A\u022B\t\x04\x02\x02\u022BO\x03\x02\x02\x02\u022C\u022D\t\x05" +
		"\x02\x02\u022D\u022E\x05R*\x02\u022EQ\x03\x02\x02\x02\u022F\u0234\x05" +
		"T+\x02\u0230\u0231\x07\\\x02\x02\u0231\u0233\x05T+\x02\u0232\u0230\x03" +
		"\x02\x02\x02\u0233\u0236\x03\x02\x02\x02\u0234\u0232\x03\x02\x02\x02\u0234" +
		"\u0235\x03\x02\x02\x02\u0235S\x03\x02\x02\x02\u0236\u0234\x03\x02\x02" +
		"\x02\u0237\u023A\x05j6\x02\u0238\u0239\x07_\x02\x02\u0239\u023B\x05j6" +
		"\x02\u023A\u0238\x03\x02\x02\x02\u023A\u023B\x03\x02\x02\x02\u023BU\x03" +
		"\x02\x02\x02\u023C\u023D\x07\x03\x02\x02\u023D\u023E\x05X-\x02\u023E\u023F" +
		"\x07\x04\x02\x02\u023F\u0248\x05\x14\v\x02\u0240\u0241\x07\x05\x02\x02" +
		"\u0241\u0242\x07\x03\x02\x02\u0242\u0243\x05X-\x02\u0243\u0244\x07\x04" +
		"\x02\x02\u0244\u0245\x05\x14\v\x02\u0245\u0247\x03\x02\x02\x02\u0246\u0240" +
		"\x03\x02\x02\x02\u0247\u024A\x03\x02\x02\x02\u0248\u0246\x03\x02\x02\x02" +
		"\u0248\u0249\x03\x02\x02\x02\u0249\u024D\x03\x02\x02\x02\u024A\u0248\x03" +
		"\x02\x02\x02\u024B\u024C\x07\x05\x02\x02\u024C\u024E\x05\x14\v\x02\u024D" +
		"\u024B\x03\x02\x02\x02\u024D\u024E\x03\x02\x02\x02\u024E\u024F\x03\x02" +
		"\x02\x02\u024F\u0250\x07F\x02\x02\u0250W\x03\x02\x02\x02\u0251\u0252\b" +
		"-\x01\x02\u0252\u0272\x07\v\x02\x02\u0253\u0272\x07\f\x02\x02\u0254\u0255" +
		"\x07`\x02\x02\u0255\u0256\x05X-\x02\u0256\u0257\x07a\x02\x02\u0257\u0272" +
		"\x03\x02\x02\x02\u0258\u0259\x07\r\x02\x02\u0259\u0272\x05X-\x07\u025A" +
		"\u025C\x05\\/\x02\u025B\u025A\x03\x02\x02\x02\u025B\u025C\x03\x02\x02" +
		"\x02\u025C\u025D\x03\x02\x02\x02\u025D\u025E\x05^0\x02\u025E\u025F\x07" +
		"\x06\x02\x02\u025F\u0260\x05Z.\x02\u0260\u0262\x05j6\x02\u0261\u0263\x05" +
		"B\"\x02\u0262\u0261\x03\x02\x02\x02\u0262\u0263\x03\x02\x02\x02\u0263" +
		"\u0265\x03\x02\x02\x02\u0264\u0266\x05D#\x02\u0265\u0264\x03\x02\x02\x02" +
		"\u0265\u0266\x03\x02\x02\x02\u0266\u0269\x03\x02\x02\x02\u0267\u0268\x07" +
		"#\x02\x02\u0268\u026A\x05@!\x02\u0269\u0267\x03\x02\x02\x02\u0269\u026A" +
		"\x03\x02\x02\x02\u026A\u0272\x03\x02\x02\x02\u026B\u026F\x07C\x02\x02" +
		"\u026C\u026D\x05Z.\x02\u026D\u026E\x05j6\x02\u026E\u0270\x03\x02\x02\x02" +
		"\u026F\u026C\x03\x02\x02\x02\u026F\u0270\x03\x02\x02\x02\u0270\u0272\x03" +
		"\x02\x02\x02\u0271\u0251\x03\x02\x02\x02\u0271\u0253\x03\x02\x02\x02\u0271" +
		"\u0254\x03\x02\x02\x02\u0271\u0258\x03\x02\x02\x02\u0271\u025B\x03\x02" +
		"\x02\x02\u0271\u026B\x03\x02\x02\x02\u0272\u027B\x03\x02\x02\x02\u0273" +
		"\u0274\f\x06\x02\x02\u0274\u0275\x07\x0E\x02\x02\u0275\u027A\x05X-\x07" +
		"\u0276\u0277\f\x05\x02\x02\u0277\u0278\x07\x0F\x02\x02\u0278\u027A\x05" +
		"X-\x06\u0279\u0273\x03\x02\x02\x02\u0279\u0276\x03\x02\x02\x02\u027A\u027D" +
		"\x03\x02\x02\x02\u027B\u0279\x03\x02\x02\x02\u027B\u027C\x03\x02\x02\x02" +
		"\u027CY\x03\x02\x02\x02\u027D\u027B\x03\x02\x02\x02\u027E\u027F\t\x06" +
		"\x02\x02\u027F[\x03\x02\x02\x02\u0280\u0281\t\x07\x02\x02\u0281]\x03\x02" +
		"\x02\x02\u0282\u0287\x05b2\x02\u0283\u0284\x07\\\x02\x02\u0284\u0286\x05" +
		"b2\x02\u0285\u0283\x03\x02\x02\x02\u0286\u0289\x03\x02\x02\x02\u0287\u0285" +
		"\x03\x02\x02\x02\u0287\u0288\x03\x02\x02\x02\u0288\u028B\x03\x02\x02\x02" +
		"\u0289\u0287\x03\x02\x02\x02\u028A\u028C\x05`1\x02\u028B\u028A\x03\x02" +
		"\x02\x02\u028B\u028C\x03\x02\x02\x02\u028C\u028E\x03\x02\x02\x02\u028D" +
		"\u028F\x05L\'\x02\u028E\u028D\x03\x02\x02\x02\u028E\u028F\x03\x02\x02" +
		"\x02\u028F\u0291\x03\x02\x02\x02\u0290\u0292\x05P)\x02\u0291\u0290\x03" +
		"\x02\x02\x02\u0291\u0292\x03\x02\x02\x02\u0292_\x03\x02\x02\x02\u0293" +
		"\u0294\x07+\x02\x02\u0294\u0295\x07,\x02\x02\u0295\u0296\x07-\x02\x02" +
		"\u0296\u0297\t\b\x02\x02\u0297a\x03\x02\x02\x02\u0298\u029B\x05f4\x02" +
		"\u0299\u029B\x05h5\x02\u029A\u0298\x03\x02\x02\x02\u029A\u0299\x03\x02" +
		"\x02\x02\u029Bc\x03\x02\x02\x02\u029C\u029D\x07%\x02\x02\u029D\u029E\t" +
		"\x05\x02\x02\u029E\u029F\x07&\x02\x02\u029Fe\x03\x02\x02\x02\u02A0\u02A1" +
		"\t\t\x02\x02\u02A1g\x03\x02\x02\x02\u02A2\u02A3\x07e\x02\x02\u02A3i\x03" +
		"\x02\x02\x02\u02A4\u02A5\x07c\x02\x02\u02A5k\x03\x02\x02\x02ampu{\x93" +
		"\xA1\xA5\xB0\xBF\xC2\xC5\xC9\xD0\xD5\xDC\xDE\xE5\xEF\u0106\u0109\u0113" +
		"\u0120\u0124\u012A\u012E\u0132\u0135\u0138\u013C\u0140\u0144\u0149\u014C" +
		"\u014F\u0152\u0154\u015B\u0162\u0165\u0169\u016C\u0171\u0174\u0179\u017C" +
		"\u017E\u0189\u018D\u0190\u0194\u0198\u019B\u01A2\u01A6\u01AB\u01B3\u01B7" +
		"\u01BB\u01BD\u01BF\u01C1\u01C4\u01CB\u01CF\u01D6\u01DA\u01E0\u01EB\u01EE" +
		"\u01F1\u01F9\u01FB\u0205\u020D\u0210\u0219\u0223\u0228\u0234\u023A\u0248" +
		"\u024D\u025B\u0262\u0265\u0269\u026F\u0271\u0279\u027B\u0287\u028B\u028E" +
		"\u0291\u029A";
	public static readonly _serializedATN: string = Utils.join(
		[
			SFMLParser._serializedATNSegment0,
			SFMLParser._serializedATNSegment1,
		],
		"",
	);
	public static __ATN: ATN;
	public static get _ATN(): ATN {
		if (!SFMLParser.__ATN) {
			SFMLParser.__ATN = new ATNDeserializer().deserialize(Utils.toCharArray(SFMLParser._serializedATN));
		}

		return SFMLParser.__ATN;
	}

}

export class ProgramContext extends ParserRuleContext {
	public EOF(): TerminalNode { return this.getToken(SFMLParser.EOF, 0); }
	public executionSideDeclaration(): ExecutionSideDeclarationContext | undefined {
		return this.tryGetRuleContext(0, ExecutionSideDeclarationContext);
	}
	public name(): NameContext | undefined {
		return this.tryGetRuleContext(0, NameContext);
	}
	public declaration(): DeclarationContext[];
	public declaration(i: number): DeclarationContext;
	public declaration(i?: number): DeclarationContext | DeclarationContext[] {
		if (i === undefined) {
			return this.getRuleContexts(DeclarationContext);
		} else {
			return this.getRuleContext(i, DeclarationContext);
		}
	}
	public trigger(): TriggerContext[];
	public trigger(i: number): TriggerContext;
	public trigger(i?: number): TriggerContext | TriggerContext[] {
		if (i === undefined) {
			return this.getRuleContexts(TriggerContext);
		} else {
			return this.getRuleContext(i, TriggerContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_program; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterProgram) {
			listener.enterProgram(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitProgram) {
			listener.exitProgram(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitProgram) {
			return visitor.visitProgram(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ExecutionSideDeclarationContext extends ParserRuleContext {
	public BTW(): TerminalNode { return this.getToken(SFMLParser.BTW, 0); }
	public CLIENT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.CLIENT, 0); }
	public SERVER(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SERVER, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_executionSideDeclaration; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterExecutionSideDeclaration) {
			listener.enterExecutionSideDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitExecutionSideDeclaration) {
			listener.exitExecutionSideDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitExecutionSideDeclaration) {
			return visitor.visitExecutionSideDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class NameContext extends ParserRuleContext {
	public NAME(): TerminalNode { return this.getToken(SFMLParser.NAME, 0); }
	public string(): StringContext {
		return this.getRuleContext(0, StringContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_name; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterName) {
			listener.enterName(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitName) {
			listener.exitName(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitName) {
			return visitor.visitName(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class DeclarationContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_declaration; }
	public copyFrom(ctx: DeclarationContext): void {
		super.copyFrom(ctx);
	}
}
export class PlayerDeclarationContext extends DeclarationContext {
	public LET(): TerminalNode { return this.getToken(SFMLParser.LET, 0); }
	public identifier(): IdentifierContext[];
	public identifier(i: number): IdentifierContext;
	public identifier(i?: number): IdentifierContext | IdentifierContext[] {
		if (i === undefined) {
			return this.getRuleContexts(IdentifierContext);
		} else {
			return this.getRuleContext(i, IdentifierContext);
		}
	}
	public BE(): TerminalNode { return this.getToken(SFMLParser.BE, 0); }
	public PLAYER(): TerminalNode { return this.getToken(SFMLParser.PLAYER, 0); }
	public OF(): TerminalNode { return this.getToken(SFMLParser.OF, 0); }
	constructor(ctx: DeclarationContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterPlayerDeclaration) {
			listener.enterPlayerDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitPlayerDeclaration) {
			listener.exitPlayerDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitPlayerDeclaration) {
			return visitor.visitPlayerDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class PatternDeclarationContext extends DeclarationContext {
	public LET(): TerminalNode { return this.getToken(SFMLParser.LET, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	public BE(): TerminalNode { return this.getToken(SFMLParser.BE, 0); }
	public LIKE(): TerminalNode { return this.getToken(SFMLParser.LIKE, 0); }
	public valuePattern(): ValuePatternContext {
		return this.getRuleContext(0, ValuePatternContext);
	}
	constructor(ctx: DeclarationContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterPatternDeclaration) {
			listener.enterPatternDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitPatternDeclaration) {
			listener.exitPatternDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitPatternDeclaration) {
			return visitor.visitPatternDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ValuePatternContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_valuePattern; }
	public copyFrom(ctx: ValuePatternContext): void {
		super.copyFrom(ctx);
	}
}
export class GuidValuePatternContext extends ValuePatternContext {
	public GUID(): TerminalNode { return this.getToken(SFMLParser.GUID, 0); }
	constructor(ctx: ValuePatternContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterGuidValuePattern) {
			listener.enterGuidValuePattern(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitGuidValuePattern) {
			listener.exitGuidValuePattern(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitGuidValuePattern) {
			return visitor.visitGuidValuePattern(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class StringValuePatternContext extends ValuePatternContext {
	public STRING_TYPE(): TerminalNode { return this.getToken(SFMLParser.STRING_TYPE, 0); }
	constructor(ctx: ValuePatternContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterStringValuePattern) {
			listener.enterStringValuePattern(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitStringValuePattern) {
			listener.exitStringValuePattern(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitStringValuePattern) {
			return visitor.visitStringValuePattern(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class LiteralValuePatternContext extends ValuePatternContext {
	public string(): StringContext {
		return this.getRuleContext(0, StringContext);
	}
	constructor(ctx: ValuePatternContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterLiteralValuePattern) {
			listener.enterLiteralValuePattern(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitLiteralValuePattern) {
			listener.exitLiteralValuePattern(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitLiteralValuePattern) {
			return visitor.visitLiteralValuePattern(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class ObjectValuePatternContext extends ValuePatternContext {
	public OBJECT(): TerminalNode { return this.getToken(SFMLParser.OBJECT, 0); }
	public WITH(): TerminalNode { return this.getToken(SFMLParser.WITH, 0); }
	public FIELD(): TerminalNode[];
	public FIELD(i: number): TerminalNode;
	public FIELD(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.FIELD);
		} else {
			return this.getToken(SFMLParser.FIELD, i);
		}
	}
	public patternField(): PatternFieldContext[];
	public patternField(i: number): PatternFieldContext;
	public patternField(i?: number): PatternFieldContext | PatternFieldContext[] {
		if (i === undefined) {
			return this.getRuleContexts(PatternFieldContext);
		} else {
			return this.getRuleContext(i, PatternFieldContext);
		}
	}
	public AND(): TerminalNode[];
	public AND(i: number): TerminalNode;
	public AND(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.AND);
		} else {
			return this.getToken(SFMLParser.AND, i);
		}
	}
	constructor(ctx: ValuePatternContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterObjectValuePattern) {
			listener.enterObjectValuePattern(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitObjectValuePattern) {
			listener.exitObjectValuePattern(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitObjectValuePattern) {
			return visitor.visitObjectValuePattern(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class AliasValuePatternContext extends ValuePatternContext {
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(ctx: ValuePatternContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterAliasValuePattern) {
			listener.enterAliasValuePattern(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitAliasValuePattern) {
			listener.exitAliasValuePattern(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitAliasValuePattern) {
			return visitor.visitAliasValuePattern(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class PatternFieldContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_patternField; }
	public copyFrom(ctx: PatternFieldContext): void {
		super.copyFrom(ctx);
	}
}
export class LiteralPatternFieldContext extends PatternFieldContext {
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	public OF(): TerminalNode { return this.getToken(SFMLParser.OF, 0); }
	public string(): StringContext {
		return this.getRuleContext(0, StringContext);
	}
	constructor(ctx: PatternFieldContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterLiteralPatternField) {
			listener.enterLiteralPatternField(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitLiteralPatternField) {
			listener.exitLiteralPatternField(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitLiteralPatternField) {
			return visitor.visitLiteralPatternField(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class LikePatternFieldContext extends PatternFieldContext {
	public identifier(): IdentifierContext[];
	public identifier(i: number): IdentifierContext;
	public identifier(i?: number): IdentifierContext | IdentifierContext[] {
		if (i === undefined) {
			return this.getRuleContexts(IdentifierContext);
		} else {
			return this.getRuleContext(i, IdentifierContext);
		}
	}
	public LIKE(): TerminalNode { return this.getToken(SFMLParser.LIKE, 0); }
	constructor(ctx: PatternFieldContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterLikePatternField) {
			listener.enterLikePatternField(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitLikePatternField) {
			listener.exitLikePatternField(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitLikePatternField) {
			return visitor.visitLikePatternField(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class AliasPatternFieldContext extends PatternFieldContext {
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(ctx: PatternFieldContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterAliasPatternField) {
			listener.enterAliasPatternField(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitAliasPatternField) {
			listener.exitAliasPatternField(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitAliasPatternField) {
			return visitor.visitAliasPatternField(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class TriggerContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_trigger; }
	public copyFrom(ctx: TriggerContext): void {
		super.copyFrom(ctx);
	}
}
export class TimerTriggerContext extends TriggerContext {
	public EVERY(): TerminalNode { return this.getToken(SFMLParser.EVERY, 0); }
	public interval(): IntervalContext {
		return this.getRuleContext(0, IntervalContext);
	}
	public DO(): TerminalNode { return this.getToken(SFMLParser.DO, 0); }
	public block(): BlockContext {
		return this.getRuleContext(0, BlockContext);
	}
	public END(): TerminalNode { return this.getToken(SFMLParser.END, 0); }
	constructor(ctx: TriggerContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterTimerTrigger) {
			listener.enterTimerTrigger(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitTimerTrigger) {
			listener.exitTimerTrigger(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitTimerTrigger) {
			return visitor.visitTimerTrigger(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class PulseTriggerContext extends TriggerContext {
	public EVERY(): TerminalNode { return this.getToken(SFMLParser.EVERY, 0); }
	public REDSTONE(): TerminalNode { return this.getToken(SFMLParser.REDSTONE, 0); }
	public PULSE(): TerminalNode { return this.getToken(SFMLParser.PULSE, 0); }
	public DO(): TerminalNode { return this.getToken(SFMLParser.DO, 0); }
	public block(): BlockContext {
		return this.getRuleContext(0, BlockContext);
	}
	public END(): TerminalNode { return this.getToken(SFMLParser.END, 0); }
	constructor(ctx: TriggerContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterPulseTrigger) {
			listener.enterPulseTrigger(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitPulseTrigger) {
			listener.exitPulseTrigger(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitPulseTrigger) {
			return visitor.visitPulseTrigger(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class IntervalContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_interval; }
	public copyFrom(ctx: IntervalContext): void {
		super.copyFrom(ctx);
	}
}
export class IntervalSpaceContext extends IntervalContext {
	public _period!: Token;
	public _legacyOffset!: Token;
	public _unit!: TimeUnitContext;
	public _newOffset!: Token;
	public _offsetUnit!: TimeUnitContext;
	public timeUnit(): TimeUnitContext[];
	public timeUnit(i: number): TimeUnitContext;
	public timeUnit(i?: number): TimeUnitContext | TimeUnitContext[] {
		if (i === undefined) {
			return this.getRuleContexts(TimeUnitContext);
		} else {
			return this.getRuleContext(i, TimeUnitContext);
		}
	}
	public GLOBAL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GLOBAL, 0); }
	public PLUS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.PLUS, 0); }
	public OFFSET(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.OFFSET, 0); }
	public BY(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BY, 0); }
	public NUMBER(): TerminalNode[];
	public NUMBER(i: number): TerminalNode;
	public NUMBER(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.NUMBER);
		} else {
			return this.getToken(SFMLParser.NUMBER, i);
		}
	}
	constructor(ctx: IntervalContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterIntervalSpace) {
			listener.enterIntervalSpace(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitIntervalSpace) {
			listener.exitIntervalSpace(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitIntervalSpace) {
			return visitor.visitIntervalSpace(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class IntervalNoSpaceContext extends IntervalContext {
	public _period!: Token;
	public _legacyOffset!: Token;
	public _unit!: TimeUnitContext;
	public _newOffset!: Token;
	public _offsetUnit!: TimeUnitContext;
	public NUMBER_WITH_G_SUFFIX(): TerminalNode { return this.getToken(SFMLParser.NUMBER_WITH_G_SUFFIX, 0); }
	public timeUnit(): TimeUnitContext[];
	public timeUnit(i: number): TimeUnitContext;
	public timeUnit(i?: number): TimeUnitContext | TimeUnitContext[] {
		if (i === undefined) {
			return this.getRuleContexts(TimeUnitContext);
		} else {
			return this.getRuleContext(i, TimeUnitContext);
		}
	}
	public PLUS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.PLUS, 0); }
	public OFFSET(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.OFFSET, 0); }
	public BY(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BY, 0); }
	public NUMBER(): TerminalNode[];
	public NUMBER(i: number): TerminalNode;
	public NUMBER(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.NUMBER);
		} else {
			return this.getToken(SFMLParser.NUMBER, i);
		}
	}
	constructor(ctx: IntervalContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterIntervalNoSpace) {
			listener.enterIntervalNoSpace(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitIntervalNoSpace) {
			listener.exitIntervalNoSpace(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitIntervalNoSpace) {
			return visitor.visitIntervalNoSpace(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class TimeUnitContext extends ParserRuleContext {
	public TICKS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TICKS, 0); }
	public TICK(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TICK, 0); }
	public SECONDS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECONDS, 0); }
	public SECOND(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECOND, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_timeUnit; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterTimeUnit) {
			listener.enterTimeUnit(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitTimeUnit) {
			listener.exitTimeUnit(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitTimeUnit) {
			return visitor.visitTimeUnit(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class BlockContext extends ParserRuleContext {
	public statement(): StatementContext[];
	public statement(i: number): StatementContext;
	public statement(i?: number): StatementContext | StatementContext[] {
		if (i === undefined) {
			return this.getRuleContexts(StatementContext);
		} else {
			return this.getRuleContext(i, StatementContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_block; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBlock) {
			listener.enterBlock(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBlock) {
			listener.exitBlock(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBlock) {
			return visitor.visitBlock(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class StatementContext extends ParserRuleContext {
	public inputStatement(): InputStatementContext | undefined {
		return this.tryGetRuleContext(0, InputStatementContext);
	}
	public outputStatement(): OutputStatementContext | undefined {
		return this.tryGetRuleContext(0, OutputStatementContext);
	}
	public ifStatement(): IfStatementContext | undefined {
		return this.tryGetRuleContext(0, IfStatementContext);
	}
	public forgetStatement(): ForgetStatementContext | undefined {
		return this.tryGetRuleContext(0, ForgetStatementContext);
	}
	public letValueStatement(): LetValueStatementContext | undefined {
		return this.tryGetRuleContext(0, LetValueStatementContext);
	}
	public createStatement(): CreateStatementContext | undefined {
		return this.tryGetRuleContext(0, CreateStatementContext);
	}
	public broadcastStatement(): BroadcastStatementContext | undefined {
		return this.tryGetRuleContext(0, BroadcastStatementContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_statement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterStatement) {
			listener.enterStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitStatement) {
			listener.exitStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitStatement) {
			return visitor.visitStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class LetValueStatementContext extends ParserRuleContext {
	public LET(): TerminalNode { return this.getToken(SFMLParser.LET, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	public BE(): TerminalNode { return this.getToken(SFMLParser.BE, 0); }
	public valueExpression(): ValueExpressionContext {
		return this.getRuleContext(0, ValueExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_letValueStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterLetValueStatement) {
			listener.enterLetValueStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitLetValueStatement) {
			listener.exitLetValueStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitLetValueStatement) {
			return visitor.visitLetValueStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ValueExpressionContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_valueExpression; }
	public copyFrom(ctx: ValueExpressionContext): void {
		super.copyFrom(ctx);
	}
}
export class InvokeTextValueExpressionContext extends ValueExpressionContext {
	public STRING_TYPE(): TerminalNode { return this.getToken(SFMLParser.STRING_TYPE, 0); }
	public OF(): TerminalNode { return this.getToken(SFMLParser.OF, 0); }
	public INVOKE(): TerminalNode { return this.getToken(SFMLParser.INVOKE, 0); }
	public qualifiedId(): QualifiedIdContext {
		return this.getRuleContext(0, QualifiedIdContext);
	}
	public WITH(): TerminalNode { return this.getToken(SFMLParser.WITH, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(ctx: ValueExpressionContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterInvokeTextValueExpression) {
			listener.enterInvokeTextValueExpression(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitInvokeTextValueExpression) {
			listener.exitInvokeTextValueExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitInvokeTextValueExpression) {
			return visitor.visitInvokeTextValueExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class ObjectConstructionValueExpressionContext extends ValueExpressionContext {
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	public WITH(): TerminalNode { return this.getToken(SFMLParser.WITH, 0); }
	public FIELD(): TerminalNode[];
	public FIELD(i: number): TerminalNode;
	public FIELD(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.FIELD);
		} else {
			return this.getToken(SFMLParser.FIELD, i);
		}
	}
	public constructionField(): ConstructionFieldContext[];
	public constructionField(i: number): ConstructionFieldContext;
	public constructionField(i?: number): ConstructionFieldContext | ConstructionFieldContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ConstructionFieldContext);
		} else {
			return this.getRuleContext(i, ConstructionFieldContext);
		}
	}
	public AND(): TerminalNode[];
	public AND(i: number): TerminalNode;
	public AND(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.AND);
		} else {
			return this.getToken(SFMLParser.AND, i);
		}
	}
	constructor(ctx: ValueExpressionContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterObjectConstructionValueExpression) {
			listener.enterObjectConstructionValueExpression(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitObjectConstructionValueExpression) {
			listener.exitObjectConstructionValueExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitObjectConstructionValueExpression) {
			return visitor.visitObjectConstructionValueExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ConstructionFieldContext extends ParserRuleContext {
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	public OF(): TerminalNode { return this.getToken(SFMLParser.OF, 0); }
	public fieldValueExpression(): FieldValueExpressionContext {
		return this.getRuleContext(0, FieldValueExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_constructionField; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterConstructionField) {
			listener.enterConstructionField(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitConstructionField) {
			listener.exitConstructionField(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitConstructionField) {
			return visitor.visitConstructionField(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class FieldValueExpressionContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_fieldValueExpression; }
	public copyFrom(ctx: FieldValueExpressionContext): void {
		super.copyFrom(ctx);
	}
}
export class NewGuidFieldValueContext extends FieldValueExpressionContext {
	public NEW(): TerminalNode { return this.getToken(SFMLParser.NEW, 0); }
	public GUID(): TerminalNode { return this.getToken(SFMLParser.GUID, 0); }
	constructor(ctx: FieldValueExpressionContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterNewGuidFieldValue) {
			listener.enterNewGuidFieldValue(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitNewGuidFieldValue) {
			listener.exitNewGuidFieldValue(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitNewGuidFieldValue) {
			return visitor.visitNewGuidFieldValue(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class LiteralFieldValueContext extends FieldValueExpressionContext {
	public string(): StringContext {
		return this.getRuleContext(0, StringContext);
	}
	constructor(ctx: FieldValueExpressionContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterLiteralFieldValue) {
			listener.enterLiteralFieldValue(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitLiteralFieldValue) {
			listener.exitLiteralFieldValue(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitLiteralFieldValue) {
			return visitor.visitLiteralFieldValue(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class VariableFieldValueContext extends FieldValueExpressionContext {
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(ctx: FieldValueExpressionContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterVariableFieldValue) {
			listener.enterVariableFieldValue(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitVariableFieldValue) {
			listener.exitVariableFieldValue(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitVariableFieldValue) {
			return visitor.visitVariableFieldValue(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class CreateStatementContext extends ParserRuleContext {
	public CREATE(): TerminalNode { return this.getToken(SFMLParser.CREATE, 0); }
	public INPUT(): TerminalNode { return this.getToken(SFMLParser.INPUT, 0); }
	public qualifiedId(): QualifiedIdContext {
		return this.getRuleContext(0, QualifiedIdContext);
	}
	public WITH(): TerminalNode { return this.getToken(SFMLParser.WITH, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_createStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterCreateStatement) {
			listener.enterCreateStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitCreateStatement) {
			listener.exitCreateStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitCreateStatement) {
			return visitor.visitCreateStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class BroadcastStatementContext extends ParserRuleContext {
	public BROADCAST(): TerminalNode { return this.getToken(SFMLParser.BROADCAST, 0); }
	public TO(): TerminalNode { return this.getToken(SFMLParser.TO, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	public CHANNEL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.CHANNEL, 0); }
	public qualifiedId(): QualifiedIdContext | undefined {
		return this.tryGetRuleContext(0, QualifiedIdContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_broadcastStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBroadcastStatement) {
			listener.enterBroadcastStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBroadcastStatement) {
			listener.exitBroadcastStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBroadcastStatement) {
			return visitor.visitBroadcastStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ForgetStatementContext extends ParserRuleContext {
	public FORGET(): TerminalNode { return this.getToken(SFMLParser.FORGET, 0); }
	public label(): LabelContext[];
	public label(i: number): LabelContext;
	public label(i?: number): LabelContext | LabelContext[] {
		if (i === undefined) {
			return this.getRuleContexts(LabelContext);
		} else {
			return this.getRuleContext(i, LabelContext);
		}
	}
	public COMMA(): TerminalNode[];
	public COMMA(i: number): TerminalNode;
	public COMMA(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.COMMA);
		} else {
			return this.getToken(SFMLParser.COMMA, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_forgetStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterForgetStatement) {
			listener.enterForgetStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitForgetStatement) {
			listener.exitForgetStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitForgetStatement) {
			return visitor.visitForgetStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class InputStatementContext extends ParserRuleContext {
	public INPUT(): TerminalNode { return this.getToken(SFMLParser.INPUT, 0); }
	public FROM(): TerminalNode { return this.getToken(SFMLParser.FROM, 0); }
	public labelAccess(): LabelAccessContext {
		return this.getRuleContext(0, LabelAccessContext);
	}
	public inputSelection(): InputSelectionContext | undefined {
		return this.tryGetRuleContext(0, InputSelectionContext);
	}
	public inputResourceLimits(): InputResourceLimitsContext | undefined {
		return this.tryGetRuleContext(0, InputResourceLimitsContext);
	}
	public resourceExclusion(): ResourceExclusionContext | undefined {
		return this.tryGetRuleContext(0, ResourceExclusionContext);
	}
	public EACH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EACH, 0); }
	public inputBinding(): InputBindingContext | undefined {
		return this.tryGetRuleContext(0, InputBindingContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_inputStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterInputStatement) {
			listener.enterInputStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitInputStatement) {
			listener.exitInputStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitInputStatement) {
			return visitor.visitInputStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class InputSelectionContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_inputSelection; }
	public copyFrom(ctx: InputSelectionContext): void {
		super.copyFrom(ctx);
	}
}
export class CapabilityInputSelectionContext extends InputSelectionContext {
	public WITH(): TerminalNode { return this.getToken(SFMLParser.WITH, 0); }
	public CAPABILITY(): TerminalNode { return this.getToken(SFMLParser.CAPABILITY, 0); }
	public qualifiedId(): QualifiedIdContext {
		return this.getRuleContext(0, QualifiedIdContext);
	}
	constructor(ctx: InputSelectionContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterCapabilityInputSelection) {
			listener.enterCapabilityInputSelection(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitCapabilityInputSelection) {
			listener.exitCapabilityInputSelection(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitCapabilityInputSelection) {
			return visitor.visitCapabilityInputSelection(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class PatternInputSelectionContext extends InputSelectionContext {
	public LIKE(): TerminalNode { return this.getToken(SFMLParser.LIKE, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(ctx: InputSelectionContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterPatternInputSelection) {
			listener.enterPatternInputSelection(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitPatternInputSelection) {
			listener.exitPatternInputSelection(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitPatternInputSelection) {
			return visitor.visitPatternInputSelection(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class InputBindingContext extends ParserRuleContext {
	public AS(): TerminalNode { return this.getToken(SFMLParser.AS, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_inputBinding; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterInputBinding) {
			listener.enterInputBinding(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitInputBinding) {
			listener.exitInputBinding(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitInputBinding) {
			return visitor.visitInputBinding(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class OutputStatementContext extends ParserRuleContext {
	public OUTPUT(): TerminalNode { return this.getToken(SFMLParser.OUTPUT, 0); }
	public TO(): TerminalNode { return this.getToken(SFMLParser.TO, 0); }
	public labelAccess(): LabelAccessContext {
		return this.getRuleContext(0, LabelAccessContext);
	}
	public outputResourceLimits(): OutputResourceLimitsContext | undefined {
		return this.tryGetRuleContext(0, OutputResourceLimitsContext);
	}
	public resourceExclusion(): ResourceExclusionContext | undefined {
		return this.tryGetRuleContext(0, ResourceExclusionContext);
	}
	public emptyslots(): EmptyslotsContext | undefined {
		return this.tryGetRuleContext(0, EmptyslotsContext);
	}
	public EACH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EACH, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_outputStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterOutputStatement) {
			listener.enterOutputStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitOutputStatement) {
			listener.exitOutputStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitOutputStatement) {
			return visitor.visitOutputStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class InputResourceLimitsContext extends ParserRuleContext {
	public resourceLimitList(): ResourceLimitListContext {
		return this.getRuleContext(0, ResourceLimitListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_inputResourceLimits; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterInputResourceLimits) {
			listener.enterInputResourceLimits(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitInputResourceLimits) {
			listener.exitInputResourceLimits(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitInputResourceLimits) {
			return visitor.visitInputResourceLimits(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class OutputResourceLimitsContext extends ParserRuleContext {
	public resourceLimitList(): ResourceLimitListContext {
		return this.getRuleContext(0, ResourceLimitListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_outputResourceLimits; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterOutputResourceLimits) {
			listener.enterOutputResourceLimits(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitOutputResourceLimits) {
			listener.exitOutputResourceLimits(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitOutputResourceLimits) {
			return visitor.visitOutputResourceLimits(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ResourceLimitListContext extends ParserRuleContext {
	public resourceLimit(): ResourceLimitContext[];
	public resourceLimit(i: number): ResourceLimitContext;
	public resourceLimit(i?: number): ResourceLimitContext | ResourceLimitContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ResourceLimitContext);
		} else {
			return this.getRuleContext(i, ResourceLimitContext);
		}
	}
	public COMMA(): TerminalNode[];
	public COMMA(i: number): TerminalNode;
	public COMMA(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.COMMA);
		} else {
			return this.getToken(SFMLParser.COMMA, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_resourceLimitList; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterResourceLimitList) {
			listener.enterResourceLimitList(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitResourceLimitList) {
			listener.exitResourceLimitList(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitResourceLimitList) {
			return visitor.visitResourceLimitList(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ResourceLimitContext extends ParserRuleContext {
	public resourceIdDisjunction(): ResourceIdDisjunctionContext | undefined {
		return this.tryGetRuleContext(0, ResourceIdDisjunctionContext);
	}
	public limit(): LimitContext | undefined {
		return this.tryGetRuleContext(0, LimitContext);
	}
	public with(): WithContext | undefined {
		return this.tryGetRuleContext(0, WithContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_resourceLimit; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterResourceLimit) {
			listener.enterResourceLimit(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitResourceLimit) {
			listener.exitResourceLimit(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitResourceLimit) {
			return visitor.visitResourceLimit(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class LimitContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_limit; }
	public copyFrom(ctx: LimitContext): void {
		super.copyFrom(ctx);
	}
}
export class QuantityRetentionLimitContext extends LimitContext {
	public quantity(): QuantityContext {
		return this.getRuleContext(0, QuantityContext);
	}
	public retention(): RetentionContext {
		return this.getRuleContext(0, RetentionContext);
	}
	constructor(ctx: LimitContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterQuantityRetentionLimit) {
			listener.enterQuantityRetentionLimit(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitQuantityRetentionLimit) {
			listener.exitQuantityRetentionLimit(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitQuantityRetentionLimit) {
			return visitor.visitQuantityRetentionLimit(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class RetentionLimitContext extends LimitContext {
	public retention(): RetentionContext {
		return this.getRuleContext(0, RetentionContext);
	}
	constructor(ctx: LimitContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterRetentionLimit) {
			listener.enterRetentionLimit(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitRetentionLimit) {
			listener.exitRetentionLimit(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitRetentionLimit) {
			return visitor.visitRetentionLimit(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class QuantityLimitContext extends LimitContext {
	public quantity(): QuantityContext {
		return this.getRuleContext(0, QuantityContext);
	}
	constructor(ctx: LimitContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterQuantityLimit) {
			listener.enterQuantityLimit(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitQuantityLimit) {
			listener.exitQuantityLimit(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitQuantityLimit) {
			return visitor.visitQuantityLimit(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class QuantityContext extends ParserRuleContext {
	public number(): NumberContext {
		return this.getRuleContext(0, NumberContext);
	}
	public EACH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EACH, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_quantity; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterQuantity) {
			listener.enterQuantity(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitQuantity) {
			listener.exitQuantity(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitQuantity) {
			return visitor.visitQuantity(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class RetentionContext extends ParserRuleContext {
	public RETAIN(): TerminalNode { return this.getToken(SFMLParser.RETAIN, 0); }
	public number(): NumberContext {
		return this.getRuleContext(0, NumberContext);
	}
	public EACH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EACH, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_retention; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterRetention) {
			listener.enterRetention(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitRetention) {
			listener.exitRetention(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitRetention) {
			return visitor.visitRetention(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ResourceExclusionContext extends ParserRuleContext {
	public EXCEPT(): TerminalNode { return this.getToken(SFMLParser.EXCEPT, 0); }
	public resourceIdList(): ResourceIdListContext {
		return this.getRuleContext(0, ResourceIdListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_resourceExclusion; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterResourceExclusion) {
			listener.enterResourceExclusion(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitResourceExclusion) {
			listener.exitResourceExclusion(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitResourceExclusion) {
			return visitor.visitResourceExclusion(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ResourceIdContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_resourceId; }
	public copyFrom(ctx: ResourceIdContext): void {
		super.copyFrom(ctx);
	}
}
export class ResourceContext extends ResourceIdContext {
	public identifier(): IdentifierContext[];
	public identifier(i: number): IdentifierContext;
	public identifier(i?: number): IdentifierContext | IdentifierContext[] {
		if (i === undefined) {
			return this.getRuleContexts(IdentifierContext);
		} else {
			return this.getRuleContext(i, IdentifierContext);
		}
	}
	public COLON(): TerminalNode[];
	public COLON(i: number): TerminalNode;
	public COLON(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.COLON);
		} else {
			return this.getToken(SFMLParser.COLON, i);
		}
	}
	constructor(ctx: ResourceIdContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterResource) {
			listener.enterResource(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitResource) {
			listener.exitResource(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitResource) {
			return visitor.visitResource(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class StringResourceContext extends ResourceIdContext {
	public string(): StringContext {
		return this.getRuleContext(0, StringContext);
	}
	constructor(ctx: ResourceIdContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterStringResource) {
			listener.enterStringResource(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitStringResource) {
			listener.exitStringResource(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitStringResource) {
			return visitor.visitStringResource(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ResourceIdListContext extends ParserRuleContext {
	public resourceId(): ResourceIdContext[];
	public resourceId(i: number): ResourceIdContext;
	public resourceId(i?: number): ResourceIdContext | ResourceIdContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ResourceIdContext);
		} else {
			return this.getRuleContext(i, ResourceIdContext);
		}
	}
	public COMMA(): TerminalNode[];
	public COMMA(i: number): TerminalNode;
	public COMMA(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.COMMA);
		} else {
			return this.getToken(SFMLParser.COMMA, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_resourceIdList; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterResourceIdList) {
			listener.enterResourceIdList(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitResourceIdList) {
			listener.exitResourceIdList(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitResourceIdList) {
			return visitor.visitResourceIdList(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ResourceIdDisjunctionContext extends ParserRuleContext {
	public resourceId(): ResourceIdContext[];
	public resourceId(i: number): ResourceIdContext;
	public resourceId(i?: number): ResourceIdContext | ResourceIdContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ResourceIdContext);
		} else {
			return this.getRuleContext(i, ResourceIdContext);
		}
	}
	public OR(): TerminalNode[];
	public OR(i: number): TerminalNode;
	public OR(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.OR);
		} else {
			return this.getToken(SFMLParser.OR, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_resourceIdDisjunction; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterResourceIdDisjunction) {
			listener.enterResourceIdDisjunction(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitResourceIdDisjunction) {
			listener.exitResourceIdDisjunction(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitResourceIdDisjunction) {
			return visitor.visitResourceIdDisjunction(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class WithContext extends ParserRuleContext {
	public WITH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.WITH, 0); }
	public withClause(): WithClauseContext {
		return this.getRuleContext(0, WithClauseContext);
	}
	public WITHOUT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.WITHOUT, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_with; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterWith) {
			listener.enterWith(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitWith) {
			listener.exitWith(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitWith) {
			return visitor.visitWith(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class WithClauseContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_withClause; }
	public copyFrom(ctx: WithClauseContext): void {
		super.copyFrom(ctx);
	}
}
export class WithParenContext extends WithClauseContext {
	public LPAREN(): TerminalNode { return this.getToken(SFMLParser.LPAREN, 0); }
	public withClause(): WithClauseContext {
		return this.getRuleContext(0, WithClauseContext);
	}
	public RPAREN(): TerminalNode { return this.getToken(SFMLParser.RPAREN, 0); }
	constructor(ctx: WithClauseContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterWithParen) {
			listener.enterWithParen(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitWithParen) {
			listener.exitWithParen(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitWithParen) {
			return visitor.visitWithParen(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class WithNegationContext extends WithClauseContext {
	public NOT(): TerminalNode { return this.getToken(SFMLParser.NOT, 0); }
	public withClause(): WithClauseContext {
		return this.getRuleContext(0, WithClauseContext);
	}
	constructor(ctx: WithClauseContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterWithNegation) {
			listener.enterWithNegation(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitWithNegation) {
			listener.exitWithNegation(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitWithNegation) {
			return visitor.visitWithNegation(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class WithConjunctionContext extends WithClauseContext {
	public withClause(): WithClauseContext[];
	public withClause(i: number): WithClauseContext;
	public withClause(i?: number): WithClauseContext | WithClauseContext[] {
		if (i === undefined) {
			return this.getRuleContexts(WithClauseContext);
		} else {
			return this.getRuleContext(i, WithClauseContext);
		}
	}
	public AND(): TerminalNode { return this.getToken(SFMLParser.AND, 0); }
	constructor(ctx: WithClauseContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterWithConjunction) {
			listener.enterWithConjunction(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitWithConjunction) {
			listener.exitWithConjunction(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitWithConjunction) {
			return visitor.visitWithConjunction(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class WithDisjunctionContext extends WithClauseContext {
	public withClause(): WithClauseContext[];
	public withClause(i: number): WithClauseContext;
	public withClause(i?: number): WithClauseContext | WithClauseContext[] {
		if (i === undefined) {
			return this.getRuleContexts(WithClauseContext);
		} else {
			return this.getRuleContext(i, WithClauseContext);
		}
	}
	public OR(): TerminalNode { return this.getToken(SFMLParser.OR, 0); }
	constructor(ctx: WithClauseContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterWithDisjunction) {
			listener.enterWithDisjunction(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitWithDisjunction) {
			listener.exitWithDisjunction(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitWithDisjunction) {
			return visitor.visitWithDisjunction(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class WithTagContext extends WithClauseContext {
	public tagMatcher(): TagMatcherContext {
		return this.getRuleContext(0, TagMatcherContext);
	}
	public TAG(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TAG, 0); }
	public HASHTAG(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.HASHTAG, 0); }
	constructor(ctx: WithClauseContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterWithTag) {
			listener.enterWithTag(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitWithTag) {
			listener.exitWithTag(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitWithTag) {
			return visitor.visitWithTag(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class TagMatcherContext extends ParserRuleContext {
	public identifier(): IdentifierContext[];
	public identifier(i: number): IdentifierContext;
	public identifier(i?: number): IdentifierContext | IdentifierContext[] {
		if (i === undefined) {
			return this.getRuleContexts(IdentifierContext);
		} else {
			return this.getRuleContext(i, IdentifierContext);
		}
	}
	public COLON(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.COLON, 0); }
	public SLASH(): TerminalNode[];
	public SLASH(i: number): TerminalNode;
	public SLASH(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.SLASH);
		} else {
			return this.getToken(SFMLParser.SLASH, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_tagMatcher; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterTagMatcher) {
			listener.enterTagMatcher(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitTagMatcher) {
			listener.exitTagMatcher(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitTagMatcher) {
			return visitor.visitTagMatcher(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class QualifiedIdContext extends ParserRuleContext {
	public identifier(): IdentifierContext[];
	public identifier(i: number): IdentifierContext;
	public identifier(i?: number): IdentifierContext | IdentifierContext[] {
		if (i === undefined) {
			return this.getRuleContexts(IdentifierContext);
		} else {
			return this.getRuleContext(i, IdentifierContext);
		}
	}
	public COLON(): TerminalNode { return this.getToken(SFMLParser.COLON, 0); }
	public SLASH(): TerminalNode[];
	public SLASH(i: number): TerminalNode;
	public SLASH(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.SLASH);
		} else {
			return this.getToken(SFMLParser.SLASH, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_qualifiedId; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterQualifiedId) {
			listener.enterQualifiedId(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitQualifiedId) {
			listener.exitQualifiedId(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitQualifiedId) {
			return visitor.visitQualifiedId(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class SidequalifierContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_sidequalifier; }
	public copyFrom(ctx: SidequalifierContext): void {
		super.copyFrom(ctx);
	}
}
export class EachSideContext extends SidequalifierContext {
	public EACH(): TerminalNode { return this.getToken(SFMLParser.EACH, 0); }
	public SIDE(): TerminalNode { return this.getToken(SFMLParser.SIDE, 0); }
	constructor(ctx: SidequalifierContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterEachSide) {
			listener.enterEachSide(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitEachSide) {
			listener.exitEachSide(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitEachSide) {
			return visitor.visitEachSide(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class ListedSidesContext extends SidequalifierContext {
	public side(): SideContext[];
	public side(i: number): SideContext;
	public side(i?: number): SideContext | SideContext[] {
		if (i === undefined) {
			return this.getRuleContexts(SideContext);
		} else {
			return this.getRuleContext(i, SideContext);
		}
	}
	public SIDE(): TerminalNode { return this.getToken(SFMLParser.SIDE, 0); }
	public COMMA(): TerminalNode[];
	public COMMA(i: number): TerminalNode;
	public COMMA(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.COMMA);
		} else {
			return this.getToken(SFMLParser.COMMA, i);
		}
	}
	constructor(ctx: SidequalifierContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterListedSides) {
			listener.enterListedSides(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitListedSides) {
			listener.exitListedSides(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitListedSides) {
			return visitor.visitListedSides(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class SideContext extends ParserRuleContext {
	public TOP(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TOP, 0); }
	public BOTTOM(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BOTTOM, 0); }
	public NORTH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.NORTH, 0); }
	public EAST(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EAST, 0); }
	public SOUTH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SOUTH, 0); }
	public WEST(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.WEST, 0); }
	public LEFT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LEFT, 0); }
	public RIGHT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.RIGHT, 0); }
	public FRONT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.FRONT, 0); }
	public BACK(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BACK, 0); }
	public NULL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.NULL, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_side; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterSide) {
			listener.enterSide(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitSide) {
			listener.exitSide(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitSide) {
			return visitor.visitSide(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class SlotqualifierContext extends ParserRuleContext {
	public rangeset(): RangesetContext {
		return this.getRuleContext(0, RangesetContext);
	}
	public SLOTS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SLOTS, 0); }
	public SLOT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SLOT, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_slotqualifier; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterSlotqualifier) {
			listener.enterSlotqualifier(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitSlotqualifier) {
			listener.exitSlotqualifier(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitSlotqualifier) {
			return visitor.visitSlotqualifier(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class RangesetContext extends ParserRuleContext {
	public range(): RangeContext[];
	public range(i: number): RangeContext;
	public range(i?: number): RangeContext | RangeContext[] {
		if (i === undefined) {
			return this.getRuleContexts(RangeContext);
		} else {
			return this.getRuleContext(i, RangeContext);
		}
	}
	public COMMA(): TerminalNode[];
	public COMMA(i: number): TerminalNode;
	public COMMA(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.COMMA);
		} else {
			return this.getToken(SFMLParser.COMMA, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_rangeset; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterRangeset) {
			listener.enterRangeset(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitRangeset) {
			listener.exitRangeset(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitRangeset) {
			return visitor.visitRangeset(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class RangeContext extends ParserRuleContext {
	public number(): NumberContext[];
	public number(i: number): NumberContext;
	public number(i?: number): NumberContext | NumberContext[] {
		if (i === undefined) {
			return this.getRuleContexts(NumberContext);
		} else {
			return this.getRuleContext(i, NumberContext);
		}
	}
	public DASH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.DASH, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_range; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterRange) {
			listener.enterRange(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitRange) {
			listener.exitRange(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitRange) {
			return visitor.visitRange(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class IfStatementContext extends ParserRuleContext {
	public IF(): TerminalNode[];
	public IF(i: number): TerminalNode;
	public IF(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.IF);
		} else {
			return this.getToken(SFMLParser.IF, i);
		}
	}
	public boolexpr(): BoolexprContext[];
	public boolexpr(i: number): BoolexprContext;
	public boolexpr(i?: number): BoolexprContext | BoolexprContext[] {
		if (i === undefined) {
			return this.getRuleContexts(BoolexprContext);
		} else {
			return this.getRuleContext(i, BoolexprContext);
		}
	}
	public THEN(): TerminalNode[];
	public THEN(i: number): TerminalNode;
	public THEN(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.THEN);
		} else {
			return this.getToken(SFMLParser.THEN, i);
		}
	}
	public block(): BlockContext[];
	public block(i: number): BlockContext;
	public block(i?: number): BlockContext | BlockContext[] {
		if (i === undefined) {
			return this.getRuleContexts(BlockContext);
		} else {
			return this.getRuleContext(i, BlockContext);
		}
	}
	public END(): TerminalNode { return this.getToken(SFMLParser.END, 0); }
	public ELSE(): TerminalNode[];
	public ELSE(i: number): TerminalNode;
	public ELSE(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.ELSE);
		} else {
			return this.getToken(SFMLParser.ELSE, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_ifStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterIfStatement) {
			listener.enterIfStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitIfStatement) {
			listener.exitIfStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitIfStatement) {
			return visitor.visitIfStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class BoolexprContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_boolexpr; }
	public copyFrom(ctx: BoolexprContext): void {
		super.copyFrom(ctx);
	}
}
export class BooleanTrueContext extends BoolexprContext {
	public TRUE(): TerminalNode { return this.getToken(SFMLParser.TRUE, 0); }
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanTrue) {
			listener.enterBooleanTrue(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanTrue) {
			listener.exitBooleanTrue(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanTrue) {
			return visitor.visitBooleanTrue(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class BooleanFalseContext extends BoolexprContext {
	public FALSE(): TerminalNode { return this.getToken(SFMLParser.FALSE, 0); }
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanFalse) {
			listener.enterBooleanFalse(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanFalse) {
			listener.exitBooleanFalse(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanFalse) {
			return visitor.visitBooleanFalse(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class BooleanParenContext extends BoolexprContext {
	public LPAREN(): TerminalNode { return this.getToken(SFMLParser.LPAREN, 0); }
	public boolexpr(): BoolexprContext {
		return this.getRuleContext(0, BoolexprContext);
	}
	public RPAREN(): TerminalNode { return this.getToken(SFMLParser.RPAREN, 0); }
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanParen) {
			listener.enterBooleanParen(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanParen) {
			listener.exitBooleanParen(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanParen) {
			return visitor.visitBooleanParen(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class BooleanNegationContext extends BoolexprContext {
	public NOT(): TerminalNode { return this.getToken(SFMLParser.NOT, 0); }
	public boolexpr(): BoolexprContext {
		return this.getRuleContext(0, BoolexprContext);
	}
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanNegation) {
			listener.enterBooleanNegation(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanNegation) {
			listener.exitBooleanNegation(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanNegation) {
			return visitor.visitBooleanNegation(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class BooleanConjunctionContext extends BoolexprContext {
	public boolexpr(): BoolexprContext[];
	public boolexpr(i: number): BoolexprContext;
	public boolexpr(i?: number): BoolexprContext | BoolexprContext[] {
		if (i === undefined) {
			return this.getRuleContexts(BoolexprContext);
		} else {
			return this.getRuleContext(i, BoolexprContext);
		}
	}
	public AND(): TerminalNode { return this.getToken(SFMLParser.AND, 0); }
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanConjunction) {
			listener.enterBooleanConjunction(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanConjunction) {
			listener.exitBooleanConjunction(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanConjunction) {
			return visitor.visitBooleanConjunction(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class BooleanDisjunctionContext extends BoolexprContext {
	public boolexpr(): BoolexprContext[];
	public boolexpr(i: number): BoolexprContext;
	public boolexpr(i?: number): BoolexprContext | BoolexprContext[] {
		if (i === undefined) {
			return this.getRuleContexts(BoolexprContext);
		} else {
			return this.getRuleContext(i, BoolexprContext);
		}
	}
	public OR(): TerminalNode { return this.getToken(SFMLParser.OR, 0); }
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanDisjunction) {
			listener.enterBooleanDisjunction(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanDisjunction) {
			listener.exitBooleanDisjunction(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanDisjunction) {
			return visitor.visitBooleanDisjunction(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class BooleanHasContext extends BoolexprContext {
	public labelAccess(): LabelAccessContext {
		return this.getRuleContext(0, LabelAccessContext);
	}
	public HAS(): TerminalNode { return this.getToken(SFMLParser.HAS, 0); }
	public comparisonOp(): ComparisonOpContext {
		return this.getRuleContext(0, ComparisonOpContext);
	}
	public number(): NumberContext {
		return this.getRuleContext(0, NumberContext);
	}
	public setOp(): SetOpContext | undefined {
		return this.tryGetRuleContext(0, SetOpContext);
	}
	public resourceIdDisjunction(): ResourceIdDisjunctionContext | undefined {
		return this.tryGetRuleContext(0, ResourceIdDisjunctionContext);
	}
	public with(): WithContext | undefined {
		return this.tryGetRuleContext(0, WithContext);
	}
	public EXCEPT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EXCEPT, 0); }
	public resourceIdList(): ResourceIdListContext | undefined {
		return this.tryGetRuleContext(0, ResourceIdListContext);
	}
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanHas) {
			listener.enterBooleanHas(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanHas) {
			listener.exitBooleanHas(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanHas) {
			return visitor.visitBooleanHas(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class BooleanRedstoneContext extends BoolexprContext {
	public REDSTONE(): TerminalNode { return this.getToken(SFMLParser.REDSTONE, 0); }
	public comparisonOp(): ComparisonOpContext | undefined {
		return this.tryGetRuleContext(0, ComparisonOpContext);
	}
	public number(): NumberContext | undefined {
		return this.tryGetRuleContext(0, NumberContext);
	}
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanRedstone) {
			listener.enterBooleanRedstone(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanRedstone) {
			listener.exitBooleanRedstone(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanRedstone) {
			return visitor.visitBooleanRedstone(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ComparisonOpContext extends ParserRuleContext {
	public GT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GT, 0); }
	public LT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LT, 0); }
	public EQ(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EQ, 0); }
	public LE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LE, 0); }
	public GE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GE, 0); }
	public GT_SYMBOL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GT_SYMBOL, 0); }
	public LT_SYMBOL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LT_SYMBOL, 0); }
	public EQ_SYMBOL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EQ_SYMBOL, 0); }
	public LE_SYMBOL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LE_SYMBOL, 0); }
	public GE_SYMBOL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GE_SYMBOL, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_comparisonOp; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterComparisonOp) {
			listener.enterComparisonOp(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitComparisonOp) {
			listener.exitComparisonOp(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitComparisonOp) {
			return visitor.visitComparisonOp(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class SetOpContext extends ParserRuleContext {
	public OVERALL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.OVERALL, 0); }
	public SOME(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SOME, 0); }
	public EVERY(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EVERY, 0); }
	public EACH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EACH, 0); }
	public ONE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.ONE, 0); }
	public LONE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LONE, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_setOp; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterSetOp) {
			listener.enterSetOp(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitSetOp) {
			listener.exitSetOp(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitSetOp) {
			return visitor.visitSetOp(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class LabelAccessContext extends ParserRuleContext {
	public label(): LabelContext[];
	public label(i: number): LabelContext;
	public label(i?: number): LabelContext | LabelContext[] {
		if (i === undefined) {
			return this.getRuleContexts(LabelContext);
		} else {
			return this.getRuleContext(i, LabelContext);
		}
	}
	public COMMA(): TerminalNode[];
	public COMMA(i: number): TerminalNode;
	public COMMA(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.COMMA);
		} else {
			return this.getToken(SFMLParser.COMMA, i);
		}
	}
	public roundrobin(): RoundrobinContext | undefined {
		return this.tryGetRuleContext(0, RoundrobinContext);
	}
	public sidequalifier(): SidequalifierContext | undefined {
		return this.tryGetRuleContext(0, SidequalifierContext);
	}
	public slotqualifier(): SlotqualifierContext | undefined {
		return this.tryGetRuleContext(0, SlotqualifierContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_labelAccess; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterLabelAccess) {
			listener.enterLabelAccess(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitLabelAccess) {
			listener.exitLabelAccess(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitLabelAccess) {
			return visitor.visitLabelAccess(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class RoundrobinContext extends ParserRuleContext {
	public ROUND(): TerminalNode { return this.getToken(SFMLParser.ROUND, 0); }
	public ROBIN(): TerminalNode { return this.getToken(SFMLParser.ROBIN, 0); }
	public BY(): TerminalNode { return this.getToken(SFMLParser.BY, 0); }
	public LABEL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LABEL, 0); }
	public BLOCK(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BLOCK, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_roundrobin; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterRoundrobin) {
			listener.enterRoundrobin(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitRoundrobin) {
			listener.exitRoundrobin(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitRoundrobin) {
			return visitor.visitRoundrobin(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class LabelContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_label; }
	public copyFrom(ctx: LabelContext): void {
		super.copyFrom(ctx);
	}
}
export class RawLabelContext extends LabelContext {
	public identifier(): IdentifierContext | undefined {
		return this.tryGetRuleContext(0, IdentifierContext);
	}
	constructor(ctx: LabelContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterRawLabel) {
			listener.enterRawLabel(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitRawLabel) {
			listener.exitRawLabel(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitRawLabel) {
			return visitor.visitRawLabel(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}
export class StringLabelContext extends LabelContext {
	public string(): StringContext {
		return this.getRuleContext(0, StringContext);
	}
	constructor(ctx: LabelContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterStringLabel) {
			listener.enterStringLabel(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitStringLabel) {
			listener.exitStringLabel(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitStringLabel) {
			return visitor.visitStringLabel(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class EmptyslotsContext extends ParserRuleContext {
	public EMPTY(): TerminalNode { return this.getToken(SFMLParser.EMPTY, 0); }
	public IN(): TerminalNode { return this.getToken(SFMLParser.IN, 0); }
	public SLOTS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SLOTS, 0); }
	public SLOT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SLOT, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_emptyslots; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterEmptyslots) {
			listener.enterEmptyslots(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitEmptyslots) {
			listener.exitEmptyslots(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitEmptyslots) {
			return visitor.visitEmptyslots(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class IdentifierContext extends ParserRuleContext {
	public IDENTIFIER(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.IDENTIFIER, 0); }
	public REDSTONE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.REDSTONE, 0); }
	public GLOBAL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GLOBAL, 0); }
	public SECOND(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECOND, 0); }
	public SECONDS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECONDS, 0); }
	public TOP(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TOP, 0); }
	public BOTTOM(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BOTTOM, 0); }
	public LEFT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LEFT, 0); }
	public RIGHT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.RIGHT, 0); }
	public FRONT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.FRONT, 0); }
	public BACK(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BACK, 0); }
	public LET(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LET, 0); }
	public BE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BE, 0); }
	public PLAYER(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.PLAYER, 0); }
	public OF(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.OF, 0); }
	public LIKE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.LIKE, 0); }
	public OBJECT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.OBJECT, 0); }
	public FIELD(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.FIELD, 0); }
	public GUID(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GUID, 0); }
	public STRING_TYPE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.STRING_TYPE, 0); }
	public INVOKE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.INVOKE, 0); }
	public CAPABILITY(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.CAPABILITY, 0); }
	public AS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.AS, 0); }
	public CREATE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.CREATE, 0); }
	public BROADCAST(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BROADCAST, 0); }
	public CHANNEL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.CHANNEL, 0); }
	public NEW(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.NEW, 0); }
	public CLIENT(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.CLIENT, 0); }
	public SERVER(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SERVER, 0); }
	public BTW(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.BTW, 0); }
	public OFFSET(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.OFFSET, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_identifier; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterIdentifier) {
			listener.enterIdentifier(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitIdentifier) {
			listener.exitIdentifier(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitIdentifier) {
			return visitor.visitIdentifier(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class StringContext extends ParserRuleContext {
	public STRING(): TerminalNode { return this.getToken(SFMLParser.STRING, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_string; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterString) {
			listener.enterString(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitString) {
			listener.exitString(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitString) {
			return visitor.visitString(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class NumberContext extends ParserRuleContext {
	public NUMBER(): TerminalNode { return this.getToken(SFMLParser.NUMBER, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_number; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterNumber) {
			listener.enterNumber(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitNumber) {
			listener.exitNumber(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitNumber) {
			return visitor.visitNumber(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


