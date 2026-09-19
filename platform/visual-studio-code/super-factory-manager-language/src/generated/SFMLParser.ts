// Generated from SFML.g4 by ANTLR 4.9.0-SNAPSHOT


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
	public static readonly FRAME = 65;
	public static readonly FOR = 66;
	public static readonly MOD = 67;
	public static readonly RENDER = 68;
	public static readonly IMAGE = 69;
	public static readonly REDSTONE = 70;
	public static readonly PULSE = 71;
	public static readonly DO = 72;
	public static readonly END = 73;
	public static readonly NAME = 74;
	public static readonly LET = 75;
	public static readonly BE = 76;
	public static readonly PLAYER = 77;
	public static readonly OF = 78;
	public static readonly LIKE = 79;
	public static readonly OBJECT = 80;
	public static readonly FIELD = 81;
	public static readonly GUID = 82;
	public static readonly STRING_TYPE = 83;
	public static readonly INVOKE = 84;
	public static readonly CAPABILITY = 85;
	public static readonly AS = 86;
	public static readonly CREATE = 87;
	public static readonly BROADCAST = 88;
	public static readonly CHANNEL = 89;
	public static readonly NEW = 90;
	public static readonly CLIENT = 91;
	public static readonly SERVER = 92;
	public static readonly BTW = 93;
	public static readonly EVERY = 94;
	public static readonly COMMA = 95;
	public static readonly COLON = 96;
	public static readonly SLASH = 97;
	public static readonly DASH = 98;
	public static readonly LPAREN = 99;
	public static readonly RPAREN = 100;
	public static readonly NUMBER_WITH_G_SUFFIX = 101;
	public static readonly NUMBER = 102;
	public static readonly IDENTIFIER = 103;
	public static readonly STRING = 104;
	public static readonly LINE_COMMENT = 105;
	public static readonly WS = 106;
	public static readonly UNUSED = 107;
	public static readonly RULE_program = 0;
	public static readonly RULE_executionSideDeclaration = 1;
	public static readonly RULE_name = 2;
	public static readonly RULE_declaration = 3;
	public static readonly RULE_valuePattern = 4;
	public static readonly RULE_patternField = 5;
	public static readonly RULE_trigger = 6;
	public static readonly RULE_frameLabels = 7;
	public static readonly RULE_interval = 8;
	public static readonly RULE_timeUnit = 9;
	public static readonly RULE_block = 10;
	public static readonly RULE_statement = 11;
	public static readonly RULE_renderImageStatement = 12;
	public static readonly RULE_letValueStatement = 13;
	public static readonly RULE_valueExpression = 14;
	public static readonly RULE_constructionField = 15;
	public static readonly RULE_fieldValueExpression = 16;
	public static readonly RULE_createStatement = 17;
	public static readonly RULE_broadcastStatement = 18;
	public static readonly RULE_forgetStatement = 19;
	public static readonly RULE_inputStatement = 20;
	public static readonly RULE_inputSelection = 21;
	public static readonly RULE_inputBinding = 22;
	public static readonly RULE_outputStatement = 23;
	public static readonly RULE_inputResourceLimits = 24;
	public static readonly RULE_outputResourceLimits = 25;
	public static readonly RULE_resourceLimitList = 26;
	public static readonly RULE_resourceLimit = 27;
	public static readonly RULE_limit = 28;
	public static readonly RULE_quantity = 29;
	public static readonly RULE_retention = 30;
	public static readonly RULE_resourceExclusion = 31;
	public static readonly RULE_resourceId = 32;
	public static readonly RULE_resourceIdList = 33;
	public static readonly RULE_resourceIdDisjunction = 34;
	public static readonly RULE_with = 35;
	public static readonly RULE_withClause = 36;
	public static readonly RULE_tagMatcher = 37;
	public static readonly RULE_qualifiedId = 38;
	public static readonly RULE_sidequalifier = 39;
	public static readonly RULE_side = 40;
	public static readonly RULE_slotqualifier = 41;
	public static readonly RULE_rangeset = 42;
	public static readonly RULE_range = 43;
	public static readonly RULE_ifStatement = 44;
	public static readonly RULE_boolexpr = 45;
	public static readonly RULE_comparisonOp = 46;
	public static readonly RULE_setOp = 47;
	public static readonly RULE_labelAccess = 48;
	public static readonly RULE_roundrobin = 49;
	public static readonly RULE_label = 50;
	public static readonly RULE_emptyslots = 51;
	public static readonly RULE_identifier = 52;
	public static readonly RULE_string = 53;
	public static readonly RULE_number = 54;
	// tslint:disable:no-trailing-whitespace
	public static readonly ruleNames: string[] = [
		"program", "executionSideDeclaration", "name", "declaration", "valuePattern",
		"patternField", "trigger", "frameLabels", "interval", "timeUnit", "block",
		"statement", "renderImageStatement", "letValueStatement", "valueExpression",
		"constructionField", "fieldValueExpression", "createStatement", "broadcastStatement",
		"forgetStatement", "inputStatement", "inputSelection", "inputBinding",
		"outputStatement", "inputResourceLimits", "outputResourceLimits", "resourceLimitList",
		"resourceLimit", "limit", "quantity", "retention", "resourceExclusion",
		"resourceId", "resourceIdList", "resourceIdDisjunction", "with", "withClause",
		"tagMatcher", "qualifiedId", "sidequalifier", "side", "slotqualifier",
		"rangeset", "range", "ifStatement", "boolexpr", "comparisonOp", "setOp",
		"labelAccess", "roundrobin", "label", "emptyslots", "identifier", "string",
		"number",
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
		undefined, undefined, undefined, undefined, undefined, undefined, undefined,
		undefined, undefined, undefined, "','", "':'", "'/'", "'-'", "'('", "')'",
	];
	private static readonly _SYMBOLIC_NAMES: Array<string | undefined> = [
		undefined, "IF", "THEN", "ELSE", "HAS", "OVERALL", "SOME", "ONE", "LONE",
		"TRUE", "FALSE", "NOT", "AND", "OR", "GT", "GT_SYMBOL", "LT", "LT_SYMBOL",
		"EQ", "EQ_SYMBOL", "LE", "LE_SYMBOL", "GE", "GE_SYMBOL", "FROM", "TO",
		"INPUT", "OUTPUT", "WHERE", "SLOTS", "SLOT", "RETAIN", "EACH", "EXCEPT",
		"FORGET", "EMPTY", "IN", "WITHOUT", "WITH", "TAG", "HASHTAG", "ROUND",
		"ROBIN", "BY", "LABEL", "BLOCK", "TOP", "BOTTOM", "NORTH", "EAST", "SOUTH",
		"WEST", "SIDE", "LEFT", "RIGHT", "FRONT", "BACK", "NULL", "TICKS", "TICK",
		"SECONDS", "SECOND", "GLOBAL", "PLUS", "OFFSET", "FRAME", "FOR", "MOD",
		"RENDER", "IMAGE", "REDSTONE", "PULSE", "DO", "END", "NAME", "LET", "BE",
		"PLAYER", "OF", "LIKE", "OBJECT", "FIELD", "GUID", "STRING_TYPE", "INVOKE",
		"CAPABILITY", "AS", "CREATE", "BROADCAST", "CHANNEL", "NEW", "CLIENT",
		"SERVER", "BTW", "EVERY", "COMMA", "COLON", "SLASH", "DASH", "LPAREN",
		"RPAREN", "NUMBER_WITH_G_SUFFIX", "NUMBER", "IDENTIFIER", "STRING", "LINE_COMMENT",
		"WS", "UNUSED",
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
			this.state = 111;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.CLIENT || _la === SFMLParser.SERVER) {
				{
				this.state = 110;
				this.executionSideDeclaration();
				}
			}

			this.state = 114;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.NAME) {
				{
				this.state = 113;
				this.name();
				}
			}

			this.state = 119;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.LET) {
				{
				{
				this.state = 116;
				this.declaration();
				}
				}
				this.state = 121;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 125;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.EVERY) {
				{
				{
				this.state = 122;
				this.trigger();
				}
				}
				this.state = 127;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 128;
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
			this.state = 130;
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
			this.state = 131;
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
			this.state = 133;
			this.match(SFMLParser.NAME);
			this.state = 134;
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
			this.state = 149;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 4, this._ctx) ) {
			case 1:
				_localctx = new PlayerDeclarationContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 136;
				this.match(SFMLParser.LET);
				this.state = 137;
				this.identifier();
				this.state = 138;
				this.match(SFMLParser.BE);
				this.state = 139;
				this.match(SFMLParser.PLAYER);
				this.state = 140;
				this.match(SFMLParser.OF);
				this.state = 141;
				this.identifier();
				}
				break;

			case 2:
				_localctx = new PatternDeclarationContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 143;
				this.match(SFMLParser.LET);
				this.state = 144;
				this.identifier();
				this.state = 145;
				this.match(SFMLParser.BE);
				this.state = 146;
				this.match(SFMLParser.LIKE);
				this.state = 147;
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
			this.state = 167;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 6, this._ctx) ) {
			case 1:
				_localctx = new GuidValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 151;
				this.match(SFMLParser.GUID);
				}
				break;

			case 2:
				_localctx = new StringValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 152;
				this.match(SFMLParser.STRING_TYPE);
				}
				break;

			case 3:
				_localctx = new LiteralValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 153;
				this.string();
				}
				break;

			case 4:
				_localctx = new ObjectValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 154;
				this.match(SFMLParser.OBJECT);
				this.state = 155;
				this.match(SFMLParser.WITH);
				this.state = 156;
				this.match(SFMLParser.FIELD);
				this.state = 157;
				this.patternField();
				this.state = 163;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === SFMLParser.AND) {
					{
					{
					this.state = 158;
					this.match(SFMLParser.AND);
					this.state = 159;
					this.match(SFMLParser.FIELD);
					this.state = 160;
					this.patternField();
					}
					}
					this.state = 165;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				}
				break;

			case 5:
				_localctx = new AliasValuePatternContext(_localctx);
				this.enterOuterAlt(_localctx, 5);
				{
				this.state = 166;
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
			this.state = 178;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 7, this._ctx) ) {
			case 1:
				_localctx = new LiteralPatternFieldContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 169;
				this.identifier();
				this.state = 170;
				this.match(SFMLParser.OF);
				this.state = 171;
				this.string();
				}
				break;

			case 2:
				_localctx = new LikePatternFieldContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 173;
				this.identifier();
				this.state = 174;
				this.match(SFMLParser.LIKE);
				this.state = 175;
				this.identifier();
				}
				break;

			case 3:
				_localctx = new AliasPatternFieldContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 177;
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
			this.state = 203;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 8, this._ctx) ) {
			case 1:
				_localctx = new TimerTriggerContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 180;
				this.match(SFMLParser.EVERY);
				this.state = 181;
				this.interval();
				this.state = 182;
				this.match(SFMLParser.DO);
				this.state = 183;
				this.block();
				this.state = 184;
				this.match(SFMLParser.END);
				}
				break;

			case 2:
				_localctx = new PulseTriggerContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 186;
				this.match(SFMLParser.EVERY);
				this.state = 187;
				this.match(SFMLParser.REDSTONE);
				this.state = 188;
				this.match(SFMLParser.PULSE);
				this.state = 189;
				this.match(SFMLParser.DO);
				this.state = 190;
				this.block();
				this.state = 191;
				this.match(SFMLParser.END);
				}
				break;

			case 3:
				_localctx = new FrameTriggerContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 193;
				this.match(SFMLParser.EVERY);
				this.state = 194;
				this.match(SFMLParser.FRAME);
				this.state = 195;
				this.match(SFMLParser.FOR);
				this.state = 196;
				this.frameLabels();
				this.state = 197;
				this.match(SFMLParser.AS);
				this.state = 198;
				this.identifier();
				this.state = 199;
				this.match(SFMLParser.DO);
				this.state = 200;
				this.block();
				this.state = 201;
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
	public frameLabels(): FrameLabelsContext {
		let _localctx: FrameLabelsContext = new FrameLabelsContext(this._ctx, this.state);
		this.enterRule(_localctx, 14, SFMLParser.RULE_frameLabels);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 205;
			this.label();
			this.state = 210;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.COMMA) {
				{
				{
				this.state = 206;
				this.match(SFMLParser.COMMA);
				this.state = 207;
				this.label();
				}
				}
				this.state = 212;
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
	public interval(): IntervalContext {
		let _localctx: IntervalContext = new IntervalContext(this._ctx, this.state);
		this.enterRule(_localctx, 16, SFMLParser.RULE_interval);
		let _la: number;
		try {
			this.state = 242;
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
				this.state = 214;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.NUMBER) {
					{
					this.state = 213;
					(_localctx as IntervalSpaceContext)._period = this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 217;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.GLOBAL) {
					{
					this.state = 216;
					this.match(SFMLParser.GLOBAL);
					}
				}

				this.state = 221;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.PLUS) {
					{
					this.state = 219;
					this.match(SFMLParser.PLUS);
					this.state = 220;
					(_localctx as IntervalSpaceContext)._legacyOffset = this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 223;
				(_localctx as IntervalSpaceContext)._unit = this.timeUnit();
				this.state = 228;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.OFFSET) {
					{
					this.state = 224;
					this.match(SFMLParser.OFFSET);
					this.state = 225;
					this.match(SFMLParser.BY);
					this.state = 226;
					(_localctx as IntervalSpaceContext)._newOffset = this.match(SFMLParser.NUMBER);
					this.state = 227;
					(_localctx as IntervalSpaceContext)._offsetUnit = this.timeUnit();
					}
				}

				}
				break;
			case SFMLParser.NUMBER_WITH_G_SUFFIX:
				_localctx = new IntervalNoSpaceContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 230;
				(_localctx as IntervalNoSpaceContext)._period = this.match(SFMLParser.NUMBER_WITH_G_SUFFIX);
				this.state = 233;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.PLUS) {
					{
					this.state = 231;
					this.match(SFMLParser.PLUS);
					this.state = 232;
					(_localctx as IntervalNoSpaceContext)._legacyOffset = this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 235;
				(_localctx as IntervalNoSpaceContext)._unit = this.timeUnit();
				this.state = 240;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.OFFSET) {
					{
					this.state = 236;
					this.match(SFMLParser.OFFSET);
					this.state = 237;
					this.match(SFMLParser.BY);
					this.state = 238;
					(_localctx as IntervalNoSpaceContext)._newOffset = this.match(SFMLParser.NUMBER);
					this.state = 239;
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
		this.enterRule(_localctx, 18, SFMLParser.RULE_timeUnit);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 244;
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
		this.enterRule(_localctx, 20, SFMLParser.RULE_block);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 249;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << SFMLParser.IF) | (1 << SFMLParser.FROM) | (1 << SFMLParser.TO) | (1 << SFMLParser.INPUT) | (1 << SFMLParser.OUTPUT))) !== 0) || _la === SFMLParser.FORGET || ((((_la - 68)) & ~0x1F) === 0 && ((1 << (_la - 68)) & ((1 << (SFMLParser.RENDER - 68)) | (1 << (SFMLParser.LET - 68)) | (1 << (SFMLParser.CREATE - 68)) | (1 << (SFMLParser.BROADCAST - 68)))) !== 0)) {
				{
				{
				this.state = 246;
				this.statement();
				}
				}
				this.state = 251;
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
		this.enterRule(_localctx, 22, SFMLParser.RULE_statement);
		try {
			this.state = 260;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.FROM:
			case SFMLParser.INPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 252;
				this.inputStatement();
				}
				break;
			case SFMLParser.TO:
			case SFMLParser.OUTPUT:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 253;
				this.outputStatement();
				}
				break;
			case SFMLParser.IF:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 254;
				this.ifStatement();
				}
				break;
			case SFMLParser.FORGET:
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 255;
				this.forgetStatement();
				}
				break;
			case SFMLParser.LET:
				this.enterOuterAlt(_localctx, 5);
				{
				this.state = 256;
				this.letValueStatement();
				}
				break;
			case SFMLParser.CREATE:
				this.enterOuterAlt(_localctx, 6);
				{
				this.state = 257;
				this.createStatement();
				}
				break;
			case SFMLParser.BROADCAST:
				this.enterOuterAlt(_localctx, 7);
				{
				this.state = 258;
				this.broadcastStatement();
				}
				break;
			case SFMLParser.RENDER:
				this.enterOuterAlt(_localctx, 8);
				{
				this.state = 259;
				this.renderImageStatement();
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
	public renderImageStatement(): RenderImageStatementContext {
		let _localctx: RenderImageStatementContext = new RenderImageStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 24, SFMLParser.RULE_renderImageStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 262;
			this.match(SFMLParser.RENDER);
			this.state = 263;
			this.match(SFMLParser.IMAGE);
			this.state = 264;
			this.string();
			this.state = 265;
			this.match(SFMLParser.TO);
			this.state = 266;
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
	public letValueStatement(): LetValueStatementContext {
		let _localctx: LetValueStatementContext = new LetValueStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 26, SFMLParser.RULE_letValueStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 268;
			this.match(SFMLParser.LET);
			this.state = 269;
			this.identifier();
			this.state = 270;
			this.match(SFMLParser.BE);
			this.state = 271;
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
		this.enterRule(_localctx, 28, SFMLParser.RULE_valueExpression);
		let _la: number;
		try {
			this.state = 292;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 20, this._ctx) ) {
			case 1:
				_localctx = new InvokeTextValueExpressionContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 273;
				this.match(SFMLParser.STRING_TYPE);
				this.state = 274;
				this.match(SFMLParser.OF);
				this.state = 275;
				this.match(SFMLParser.INVOKE);
				this.state = 276;
				this.qualifiedId();
				this.state = 277;
				this.match(SFMLParser.WITH);
				this.state = 278;
				this.identifier();
				}
				break;

			case 2:
				_localctx = new ObjectConstructionValueExpressionContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 280;
				this.identifier();
				this.state = 281;
				this.match(SFMLParser.WITH);
				this.state = 282;
				this.match(SFMLParser.FIELD);
				this.state = 283;
				this.constructionField();
				this.state = 289;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === SFMLParser.AND) {
					{
					{
					this.state = 284;
					this.match(SFMLParser.AND);
					this.state = 285;
					this.match(SFMLParser.FIELD);
					this.state = 286;
					this.constructionField();
					}
					}
					this.state = 291;
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
		this.enterRule(_localctx, 30, SFMLParser.RULE_constructionField);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 294;
			this.identifier();
			this.state = 295;
			this.match(SFMLParser.OF);
			this.state = 296;
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
		this.enterRule(_localctx, 32, SFMLParser.RULE_fieldValueExpression);
		try {
			this.state = 302;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 21, this._ctx) ) {
			case 1:
				_localctx = new NewGuidFieldValueContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 298;
				this.match(SFMLParser.NEW);
				this.state = 299;
				this.match(SFMLParser.GUID);
				}
				break;

			case 2:
				_localctx = new LiteralFieldValueContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 300;
				this.string();
				}
				break;

			case 3:
				_localctx = new VariableFieldValueContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 301;
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
		this.enterRule(_localctx, 34, SFMLParser.RULE_createStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 304;
			this.match(SFMLParser.CREATE);
			this.state = 305;
			this.match(SFMLParser.INPUT);
			this.state = 306;
			this.qualifiedId();
			this.state = 307;
			this.match(SFMLParser.WITH);
			this.state = 308;
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
		this.enterRule(_localctx, 36, SFMLParser.RULE_broadcastStatement);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 310;
			this.match(SFMLParser.BROADCAST);
			this.state = 311;
			this.match(SFMLParser.TO);
			this.state = 312;
			this.identifier();
			this.state = 315;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.CHANNEL) {
				{
				this.state = 313;
				this.match(SFMLParser.CHANNEL);
				this.state = 314;
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
		this.enterRule(_localctx, 38, SFMLParser.RULE_forgetStatement);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 317;
			this.match(SFMLParser.FORGET);
			this.state = 319;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 23, this._ctx) ) {
			case 1:
				{
				this.state = 318;
				this.label();
				}
				break;
			}
			this.state = 325;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 24, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 321;
					this.match(SFMLParser.COMMA);
					this.state = 322;
					this.label();
					}
					}
				}
				this.state = 327;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 24, this._ctx);
			}
			this.state = 329;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.COMMA) {
				{
				this.state = 328;
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
		this.enterRule(_localctx, 40, SFMLParser.RULE_inputStatement);
		let _la: number;
		try {
			this.state = 367;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.INPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 331;
				this.match(SFMLParser.INPUT);
				this.state = 333;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 26, this._ctx) ) {
				case 1:
					{
					this.state = 332;
					this.inputSelection();
					}
					break;
				}
				this.state = 336;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 31)) & ~0x1F) === 0 && ((1 << (_la - 31)) & ((1 << (SFMLParser.RETAIN - 31)) | (1 << (SFMLParser.WITHOUT - 31)) | (1 << (SFMLParser.WITH - 31)) | (1 << (SFMLParser.TOP - 31)) | (1 << (SFMLParser.BOTTOM - 31)) | (1 << (SFMLParser.LEFT - 31)) | (1 << (SFMLParser.RIGHT - 31)) | (1 << (SFMLParser.FRONT - 31)) | (1 << (SFMLParser.BACK - 31)) | (1 << (SFMLParser.SECONDS - 31)) | (1 << (SFMLParser.SECOND - 31)) | (1 << (SFMLParser.GLOBAL - 31)))) !== 0) || ((((_la - 64)) & ~0x1F) === 0 && ((1 << (_la - 64)) & ((1 << (SFMLParser.OFFSET - 64)) | (1 << (SFMLParser.FRAME - 64)) | (1 << (SFMLParser.FOR - 64)) | (1 << (SFMLParser.MOD - 64)) | (1 << (SFMLParser.RENDER - 64)) | (1 << (SFMLParser.IMAGE - 64)) | (1 << (SFMLParser.REDSTONE - 64)) | (1 << (SFMLParser.LET - 64)) | (1 << (SFMLParser.BE - 64)) | (1 << (SFMLParser.PLAYER - 64)) | (1 << (SFMLParser.OF - 64)) | (1 << (SFMLParser.LIKE - 64)) | (1 << (SFMLParser.OBJECT - 64)) | (1 << (SFMLParser.FIELD - 64)) | (1 << (SFMLParser.GUID - 64)) | (1 << (SFMLParser.STRING_TYPE - 64)) | (1 << (SFMLParser.INVOKE - 64)) | (1 << (SFMLParser.CAPABILITY - 64)) | (1 << (SFMLParser.AS - 64)) | (1 << (SFMLParser.CREATE - 64)) | (1 << (SFMLParser.BROADCAST - 64)) | (1 << (SFMLParser.CHANNEL - 64)) | (1 << (SFMLParser.NEW - 64)) | (1 << (SFMLParser.CLIENT - 64)) | (1 << (SFMLParser.SERVER - 64)) | (1 << (SFMLParser.BTW - 64)))) !== 0) || ((((_la - 102)) & ~0x1F) === 0 && ((1 << (_la - 102)) & ((1 << (SFMLParser.NUMBER - 102)) | (1 << (SFMLParser.IDENTIFIER - 102)) | (1 << (SFMLParser.STRING - 102)))) !== 0)) {
					{
					this.state = 335;
					this.inputResourceLimits();
					}
				}

				this.state = 339;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 338;
					this.resourceExclusion();
					}
				}

				this.state = 341;
				this.match(SFMLParser.FROM);
				this.state = 343;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 342;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 345;
				this.labelAccess();
				this.state = 347;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.AS) {
					{
					this.state = 346;
					this.inputBinding();
					}
				}

				}
				break;
			case SFMLParser.FROM:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 349;
				this.match(SFMLParser.FROM);
				this.state = 351;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 350;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 353;
				this.labelAccess();
				this.state = 354;
				this.match(SFMLParser.INPUT);
				this.state = 356;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 32, this._ctx) ) {
				case 1:
					{
					this.state = 355;
					this.inputSelection();
					}
					break;
				}
				this.state = 359;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 33, this._ctx) ) {
				case 1:
					{
					this.state = 358;
					this.inputResourceLimits();
					}
					break;
				}
				this.state = 362;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 361;
					this.resourceExclusion();
					}
				}

				this.state = 365;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.AS) {
					{
					this.state = 364;
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
		this.enterRule(_localctx, 42, SFMLParser.RULE_inputSelection);
		try {
			this.state = 374;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.WITH:
				_localctx = new CapabilityInputSelectionContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 369;
				this.match(SFMLParser.WITH);
				this.state = 370;
				this.match(SFMLParser.CAPABILITY);
				this.state = 371;
				this.qualifiedId();
				}
				break;
			case SFMLParser.LIKE:
				_localctx = new PatternInputSelectionContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 372;
				this.match(SFMLParser.LIKE);
				this.state = 373;
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
		this.enterRule(_localctx, 44, SFMLParser.RULE_inputBinding);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 376;
			this.match(SFMLParser.AS);
			this.state = 377;
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
		this.enterRule(_localctx, 46, SFMLParser.RULE_outputStatement);
		let _la: number;
		try {
			this.state = 409;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.OUTPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 379;
				this.match(SFMLParser.OUTPUT);
				this.state = 381;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 31)) & ~0x1F) === 0 && ((1 << (_la - 31)) & ((1 << (SFMLParser.RETAIN - 31)) | (1 << (SFMLParser.WITHOUT - 31)) | (1 << (SFMLParser.WITH - 31)) | (1 << (SFMLParser.TOP - 31)) | (1 << (SFMLParser.BOTTOM - 31)) | (1 << (SFMLParser.LEFT - 31)) | (1 << (SFMLParser.RIGHT - 31)) | (1 << (SFMLParser.FRONT - 31)) | (1 << (SFMLParser.BACK - 31)) | (1 << (SFMLParser.SECONDS - 31)) | (1 << (SFMLParser.SECOND - 31)) | (1 << (SFMLParser.GLOBAL - 31)))) !== 0) || ((((_la - 64)) & ~0x1F) === 0 && ((1 << (_la - 64)) & ((1 << (SFMLParser.OFFSET - 64)) | (1 << (SFMLParser.FRAME - 64)) | (1 << (SFMLParser.FOR - 64)) | (1 << (SFMLParser.MOD - 64)) | (1 << (SFMLParser.RENDER - 64)) | (1 << (SFMLParser.IMAGE - 64)) | (1 << (SFMLParser.REDSTONE - 64)) | (1 << (SFMLParser.LET - 64)) | (1 << (SFMLParser.BE - 64)) | (1 << (SFMLParser.PLAYER - 64)) | (1 << (SFMLParser.OF - 64)) | (1 << (SFMLParser.LIKE - 64)) | (1 << (SFMLParser.OBJECT - 64)) | (1 << (SFMLParser.FIELD - 64)) | (1 << (SFMLParser.GUID - 64)) | (1 << (SFMLParser.STRING_TYPE - 64)) | (1 << (SFMLParser.INVOKE - 64)) | (1 << (SFMLParser.CAPABILITY - 64)) | (1 << (SFMLParser.AS - 64)) | (1 << (SFMLParser.CREATE - 64)) | (1 << (SFMLParser.BROADCAST - 64)) | (1 << (SFMLParser.CHANNEL - 64)) | (1 << (SFMLParser.NEW - 64)) | (1 << (SFMLParser.CLIENT - 64)) | (1 << (SFMLParser.SERVER - 64)) | (1 << (SFMLParser.BTW - 64)))) !== 0) || ((((_la - 102)) & ~0x1F) === 0 && ((1 << (_la - 102)) & ((1 << (SFMLParser.NUMBER - 102)) | (1 << (SFMLParser.IDENTIFIER - 102)) | (1 << (SFMLParser.STRING - 102)))) !== 0)) {
					{
					this.state = 380;
					this.outputResourceLimits();
					}
				}

				this.state = 384;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 383;
					this.resourceExclusion();
					}
				}

				this.state = 386;
				this.match(SFMLParser.TO);
				this.state = 388;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EMPTY) {
					{
					this.state = 387;
					this.emptyslots();
					}
				}

				this.state = 391;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 390;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 393;
				this.labelAccess();
				}
				break;
			case SFMLParser.TO:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 394;
				this.match(SFMLParser.TO);
				this.state = 396;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EMPTY) {
					{
					this.state = 395;
					this.emptyslots();
					}
				}

				this.state = 399;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 398;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 401;
				this.labelAccess();
				this.state = 402;
				this.match(SFMLParser.OUTPUT);
				this.state = 404;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 44, this._ctx) ) {
				case 1:
					{
					this.state = 403;
					this.outputResourceLimits();
					}
					break;
				}
				this.state = 407;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 406;
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
		this.enterRule(_localctx, 48, SFMLParser.RULE_inputResourceLimits);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 411;
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
		this.enterRule(_localctx, 50, SFMLParser.RULE_outputResourceLimits);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 413;
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
		this.enterRule(_localctx, 52, SFMLParser.RULE_resourceLimitList);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 415;
			this.resourceLimit();
			this.state = 420;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 47, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 416;
					this.match(SFMLParser.COMMA);
					this.state = 417;
					this.resourceLimit();
					}
					}
				}
				this.state = 422;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 47, this._ctx);
			}
			this.state = 424;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.COMMA) {
				{
				this.state = 423;
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
		this.enterRule(_localctx, 54, SFMLParser.RULE_resourceLimit);
		let _la: number;
		try {
			this.state = 438;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 52, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 427;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.RETAIN || _la === SFMLParser.NUMBER) {
					{
					this.state = 426;
					this.limit();
					}
				}

				this.state = 429;
				this.resourceIdDisjunction();
				this.state = 431;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.WITHOUT || _la === SFMLParser.WITH) {
					{
					this.state = 430;
					this.with();
					}
				}

				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 433;
				this.limit();
				this.state = 435;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.WITHOUT || _la === SFMLParser.WITH) {
					{
					this.state = 434;
					this.with();
					}
				}

				}
				break;

			case 3:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 437;
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
		this.enterRule(_localctx, 56, SFMLParser.RULE_limit);
		try {
			this.state = 445;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 53, this._ctx) ) {
			case 1:
				_localctx = new QuantityRetentionLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 440;
				this.quantity();
				this.state = 441;
				this.retention();
				}
				break;

			case 2:
				_localctx = new RetentionLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 443;
				this.retention();
				}
				break;

			case 3:
				_localctx = new QuantityLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 444;
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
		this.enterRule(_localctx, 58, SFMLParser.RULE_quantity);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 447;
			this.number();
			this.state = 449;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.EACH) {
				{
				this.state = 448;
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
		this.enterRule(_localctx, 60, SFMLParser.RULE_retention);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 451;
			this.match(SFMLParser.RETAIN);
			this.state = 452;
			this.number();
			this.state = 454;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.EACH) {
				{
				this.state = 453;
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
		this.enterRule(_localctx, 62, SFMLParser.RULE_resourceExclusion);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 456;
			this.match(SFMLParser.EXCEPT);
			this.state = 457;
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
		this.enterRule(_localctx, 64, SFMLParser.RULE_resourceId);
		try {
			this.state = 479;
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
			case SFMLParser.FRAME:
			case SFMLParser.FOR:
			case SFMLParser.MOD:
			case SFMLParser.RENDER:
			case SFMLParser.IMAGE:
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
				this.state = 459;
				this.identifier();
				}
				this.state = 476;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 61, this._ctx) ) {
				case 1:
					{
					this.state = 460;
					this.match(SFMLParser.COLON);
					this.state = 462;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 56, this._ctx) ) {
					case 1:
						{
						this.state = 461;
						this.identifier();
						}
						break;
					}
					this.state = 474;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 60, this._ctx) ) {
					case 1:
						{
						this.state = 464;
						this.match(SFMLParser.COLON);
						this.state = 466;
						this._errHandler.sync(this);
						switch ( this.interpreter.adaptivePredict(this._input, 57, this._ctx) ) {
						case 1:
							{
							this.state = 465;
							this.identifier();
							}
							break;
						}
						this.state = 472;
						this._errHandler.sync(this);
						switch ( this.interpreter.adaptivePredict(this._input, 59, this._ctx) ) {
						case 1:
							{
							this.state = 468;
							this.match(SFMLParser.COLON);
							this.state = 470;
							this._errHandler.sync(this);
							switch ( this.interpreter.adaptivePredict(this._input, 58, this._ctx) ) {
							case 1:
								{
								this.state = 469;
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
				this.state = 478;
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
		this.enterRule(_localctx, 66, SFMLParser.RULE_resourceIdList);
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 481;
			this.resourceId();
			this.state = 486;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 63, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 482;
					this.match(SFMLParser.COMMA);
					this.state = 483;
					this.resourceId();
					}
					}
				}
				this.state = 488;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 63, this._ctx);
			}
			this.state = 490;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 64, this._ctx) ) {
			case 1:
				{
				this.state = 489;
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
		this.enterRule(_localctx, 68, SFMLParser.RULE_resourceIdDisjunction);
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 492;
			this.resourceId();
			this.state = 497;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 65, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 493;
					this.match(SFMLParser.OR);
					this.state = 494;
					this.resourceId();
					}
					}
				}
				this.state = 499;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 65, this._ctx);
			}
			this.state = 501;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 66, this._ctx) ) {
			case 1:
				{
				this.state = 500;
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
		this.enterRule(_localctx, 70, SFMLParser.RULE_with);
		try {
			this.state = 507;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.WITH:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 503;
				this.match(SFMLParser.WITH);
				this.state = 504;
				this.withClause(0);
				}
				break;
			case SFMLParser.WITHOUT:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 505;
				this.match(SFMLParser.WITHOUT);
				this.state = 506;
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
		let _startState: number = 72;
		this.enterRecursionRule(_localctx, 72, SFMLParser.RULE_withClause, _p);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 524;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.LPAREN:
				{
				_localctx = new WithParenContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;

				this.state = 510;
				this.match(SFMLParser.LPAREN);
				this.state = 511;
				this.withClause(0);
				this.state = 512;
				this.match(SFMLParser.RPAREN);
				}
				break;
			case SFMLParser.NOT:
				{
				_localctx = new WithNegationContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 514;
				this.match(SFMLParser.NOT);
				this.state = 515;
				this.withClause(4);
				}
				break;
			case SFMLParser.TAG:
			case SFMLParser.HASHTAG:
				{
				_localctx = new WithTagContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 521;
				this._errHandler.sync(this);
				switch (this._input.LA(1)) {
				case SFMLParser.TAG:
					{
					this.state = 516;
					this.match(SFMLParser.TAG);
					this.state = 518;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
					if (_la === SFMLParser.HASHTAG) {
						{
						this.state = 517;
						this.match(SFMLParser.HASHTAG);
						}
					}

					}
					break;
				case SFMLParser.HASHTAG:
					{
					this.state = 520;
					this.match(SFMLParser.HASHTAG);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				this.state = 523;
				this.tagMatcher();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			this._ctx._stop = this._input.tryLT(-1);
			this.state = 534;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 72, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					if (this._parseListeners != null) {
						this.triggerExitRuleEvent();
					}
					_prevctx = _localctx;
					{
					this.state = 532;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 71, this._ctx) ) {
					case 1:
						{
						_localctx = new WithConjunctionContext(new WithClauseContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_withClause);
						this.state = 526;
						if (!(this.precpred(this._ctx, 3))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 3)");
						}
						this.state = 527;
						this.match(SFMLParser.AND);
						this.state = 528;
						this.withClause(4);
						}
						break;

					case 2:
						{
						_localctx = new WithDisjunctionContext(new WithClauseContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_withClause);
						this.state = 529;
						if (!(this.precpred(this._ctx, 2))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 2)");
						}
						this.state = 530;
						this.match(SFMLParser.OR);
						this.state = 531;
						this.withClause(3);
						}
						break;
					}
					}
				}
				this.state = 536;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 72, this._ctx);
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
		this.enterRule(_localctx, 74, SFMLParser.RULE_tagMatcher);
		try {
			let _alt: number;
			this.state = 555;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 75, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 537;
				this.identifier();
				this.state = 538;
				this.match(SFMLParser.COLON);
				this.state = 539;
				this.identifier();
				this.state = 544;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 73, this._ctx);
				while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
					if (_alt === 1) {
						{
						{
						this.state = 540;
						this.match(SFMLParser.SLASH);
						this.state = 541;
						this.identifier();
						}
						}
					}
					this.state = 546;
					this._errHandler.sync(this);
					_alt = this.interpreter.adaptivePredict(this._input, 73, this._ctx);
				}
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 547;
				this.identifier();
				this.state = 552;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 74, this._ctx);
				while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
					if (_alt === 1) {
						{
						{
						this.state = 548;
						this.match(SFMLParser.SLASH);
						this.state = 549;
						this.identifier();
						}
						}
					}
					this.state = 554;
					this._errHandler.sync(this);
					_alt = this.interpreter.adaptivePredict(this._input, 74, this._ctx);
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
		this.enterRule(_localctx, 76, SFMLParser.RULE_qualifiedId);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 557;
			this.identifier();
			this.state = 558;
			this.match(SFMLParser.COLON);
			this.state = 559;
			this.identifier();
			this.state = 564;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.SLASH) {
				{
				{
				this.state = 560;
				this.match(SFMLParser.SLASH);
				this.state = 561;
				this.identifier();
				}
				}
				this.state = 566;
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
		this.enterRule(_localctx, 78, SFMLParser.RULE_sidequalifier);
		let _la: number;
		try {
			this.state = 579;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.EACH:
				_localctx = new EachSideContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 567;
				this.match(SFMLParser.EACH);
				this.state = 568;
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
				this.state = 569;
				this.side();
				this.state = 574;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === SFMLParser.COMMA) {
					{
					{
					this.state = 570;
					this.match(SFMLParser.COMMA);
					this.state = 571;
					this.side();
					}
					}
					this.state = 576;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				this.state = 577;
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
		this.enterRule(_localctx, 80, SFMLParser.RULE_side);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 581;
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
		this.enterRule(_localctx, 82, SFMLParser.RULE_slotqualifier);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 583;
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
			this.state = 584;
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
		this.enterRule(_localctx, 84, SFMLParser.RULE_rangeset);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 586;
			this.range();
			this.state = 591;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.COMMA) {
				{
				{
				this.state = 587;
				this.match(SFMLParser.COMMA);
				this.state = 588;
				this.range();
				}
				}
				this.state = 593;
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
		this.enterRule(_localctx, 86, SFMLParser.RULE_range);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 594;
			this.number();
			this.state = 597;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.DASH) {
				{
				this.state = 595;
				this.match(SFMLParser.DASH);
				this.state = 596;
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
		this.enterRule(_localctx, 88, SFMLParser.RULE_ifStatement);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 599;
			this.match(SFMLParser.IF);
			this.state = 600;
			this.boolexpr(0);
			this.state = 601;
			this.match(SFMLParser.THEN);
			this.state = 602;
			this.block();
			this.state = 611;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 81, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 603;
					this.match(SFMLParser.ELSE);
					this.state = 604;
					this.match(SFMLParser.IF);
					this.state = 605;
					this.boolexpr(0);
					this.state = 606;
					this.match(SFMLParser.THEN);
					this.state = 607;
					this.block();
					}
					}
				}
				this.state = 613;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 81, this._ctx);
			}
			this.state = 616;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.ELSE) {
				{
				this.state = 614;
				this.match(SFMLParser.ELSE);
				this.state = 615;
				this.block();
				}
			}

			this.state = 618;
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
		let _startState: number = 90;
		this.enterRecursionRule(_localctx, 90, SFMLParser.RULE_boolexpr, _p);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 658;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 88, this._ctx) ) {
			case 1:
				{
				_localctx = new BooleanTrueContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;

				this.state = 621;
				this.match(SFMLParser.TRUE);
				}
				break;

			case 2:
				{
				_localctx = new BooleanFalseContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 622;
				this.match(SFMLParser.FALSE);
				}
				break;

			case 3:
				{
				_localctx = new BooleanParenContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 623;
				this.match(SFMLParser.LPAREN);
				this.state = 624;
				this.boolexpr(0);
				this.state = 625;
				this.match(SFMLParser.RPAREN);
				}
				break;

			case 4:
				{
				_localctx = new BooleanNegationContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 627;
				this.match(SFMLParser.NOT);
				this.state = 628;
				this.boolexpr(6);
				}
				break;

			case 5:
				{
				_localctx = new BooleanHasContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 630;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 5)) & ~0x1F) === 0 && ((1 << (_la - 5)) & ((1 << (SFMLParser.OVERALL - 5)) | (1 << (SFMLParser.SOME - 5)) | (1 << (SFMLParser.ONE - 5)) | (1 << (SFMLParser.LONE - 5)) | (1 << (SFMLParser.EACH - 5)))) !== 0) || _la === SFMLParser.EVERY) {
					{
					this.state = 629;
					this.setOp();
					}
				}

				this.state = 632;
				this.labelAccess();
				this.state = 633;
				this.match(SFMLParser.HAS);
				this.state = 634;
				this.comparisonOp();
				this.state = 635;
				this.number();
				this.state = 637;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 84, this._ctx) ) {
				case 1:
					{
					this.state = 636;
					this.resourceIdDisjunction();
					}
					break;
				}
				this.state = 640;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 85, this._ctx) ) {
				case 1:
					{
					this.state = 639;
					this.with();
					}
					break;
				}
				this.state = 644;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 86, this._ctx) ) {
				case 1:
					{
					this.state = 642;
					this.match(SFMLParser.EXCEPT);
					this.state = 643;
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
				this.state = 646;
				this.match(SFMLParser.REDSTONE);
				this.state = 650;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 87, this._ctx) ) {
				case 1:
					{
					this.state = 647;
					this.comparisonOp();
					this.state = 648;
					this.number();
					}
					break;
				}
				}
				break;

			case 7:
				{
				_localctx = new BooleanFrameModuloContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 652;
				this.match(SFMLParser.FRAME);
				this.state = 653;
				this.match(SFMLParser.MOD);
				this.state = 654;
				this.number();
				this.state = 655;
				this.comparisonOp();
				this.state = 656;
				this.number();
				}
				break;
			}
			this._ctx._stop = this._input.tryLT(-1);
			this.state = 668;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 90, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					if (this._parseListeners != null) {
						this.triggerExitRuleEvent();
					}
					_prevctx = _localctx;
					{
					this.state = 666;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 89, this._ctx) ) {
					case 1:
						{
						_localctx = new BooleanConjunctionContext(new BoolexprContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_boolexpr);
						this.state = 660;
						if (!(this.precpred(this._ctx, 5))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 5)");
						}
						this.state = 661;
						this.match(SFMLParser.AND);
						this.state = 662;
						this.boolexpr(6);
						}
						break;

					case 2:
						{
						_localctx = new BooleanDisjunctionContext(new BoolexprContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_boolexpr);
						this.state = 663;
						if (!(this.precpred(this._ctx, 4))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 4)");
						}
						this.state = 664;
						this.match(SFMLParser.OR);
						this.state = 665;
						this.boolexpr(5);
						}
						break;
					}
					}
				}
				this.state = 670;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 90, this._ctx);
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
		this.enterRule(_localctx, 92, SFMLParser.RULE_comparisonOp);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 671;
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
		this.enterRule(_localctx, 94, SFMLParser.RULE_setOp);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 673;
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
		this.enterRule(_localctx, 96, SFMLParser.RULE_labelAccess);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 675;
			this.label();
			this.state = 680;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.COMMA) {
				{
				{
				this.state = 676;
				this.match(SFMLParser.COMMA);
				this.state = 677;
				this.label();
				}
				}
				this.state = 682;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 684;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.ROUND) {
				{
				this.state = 683;
				this.roundrobin();
				}
			}

			this.state = 687;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 32)) & ~0x1F) === 0 && ((1 << (_la - 32)) & ((1 << (SFMLParser.EACH - 32)) | (1 << (SFMLParser.TOP - 32)) | (1 << (SFMLParser.BOTTOM - 32)) | (1 << (SFMLParser.NORTH - 32)) | (1 << (SFMLParser.EAST - 32)) | (1 << (SFMLParser.SOUTH - 32)) | (1 << (SFMLParser.WEST - 32)) | (1 << (SFMLParser.LEFT - 32)) | (1 << (SFMLParser.RIGHT - 32)) | (1 << (SFMLParser.FRONT - 32)) | (1 << (SFMLParser.BACK - 32)) | (1 << (SFMLParser.NULL - 32)))) !== 0)) {
				{
				this.state = 686;
				this.sidequalifier();
				}
			}

			this.state = 690;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.SLOTS || _la === SFMLParser.SLOT) {
				{
				this.state = 689;
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
		this.enterRule(_localctx, 98, SFMLParser.RULE_roundrobin);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 692;
			this.match(SFMLParser.ROUND);
			this.state = 693;
			this.match(SFMLParser.ROBIN);
			this.state = 694;
			this.match(SFMLParser.BY);
			this.state = 695;
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
		this.enterRule(_localctx, 100, SFMLParser.RULE_label);
		try {
			this.state = 699;
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
			case SFMLParser.FRAME:
			case SFMLParser.FOR:
			case SFMLParser.MOD:
			case SFMLParser.RENDER:
			case SFMLParser.IMAGE:
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
				this.state = 697;
				this.identifier();
				}
				}
				break;
			case SFMLParser.STRING:
				_localctx = new StringLabelContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 698;
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
		this.enterRule(_localctx, 102, SFMLParser.RULE_emptyslots);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 701;
			this.match(SFMLParser.EMPTY);
			this.state = 702;
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
			this.state = 703;
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
		this.enterRule(_localctx, 104, SFMLParser.RULE_identifier);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 705;
			_la = this._input.LA(1);
			if (!(((((_la - 46)) & ~0x1F) === 0 && ((1 << (_la - 46)) & ((1 << (SFMLParser.TOP - 46)) | (1 << (SFMLParser.BOTTOM - 46)) | (1 << (SFMLParser.LEFT - 46)) | (1 << (SFMLParser.RIGHT - 46)) | (1 << (SFMLParser.FRONT - 46)) | (1 << (SFMLParser.BACK - 46)) | (1 << (SFMLParser.SECONDS - 46)) | (1 << (SFMLParser.SECOND - 46)) | (1 << (SFMLParser.GLOBAL - 46)) | (1 << (SFMLParser.OFFSET - 46)) | (1 << (SFMLParser.FRAME - 46)) | (1 << (SFMLParser.FOR - 46)) | (1 << (SFMLParser.MOD - 46)) | (1 << (SFMLParser.RENDER - 46)) | (1 << (SFMLParser.IMAGE - 46)) | (1 << (SFMLParser.REDSTONE - 46)) | (1 << (SFMLParser.LET - 46)) | (1 << (SFMLParser.BE - 46)) | (1 << (SFMLParser.PLAYER - 46)))) !== 0) || ((((_la - 78)) & ~0x1F) === 0 && ((1 << (_la - 78)) & ((1 << (SFMLParser.OF - 78)) | (1 << (SFMLParser.LIKE - 78)) | (1 << (SFMLParser.OBJECT - 78)) | (1 << (SFMLParser.FIELD - 78)) | (1 << (SFMLParser.GUID - 78)) | (1 << (SFMLParser.STRING_TYPE - 78)) | (1 << (SFMLParser.INVOKE - 78)) | (1 << (SFMLParser.CAPABILITY - 78)) | (1 << (SFMLParser.AS - 78)) | (1 << (SFMLParser.CREATE - 78)) | (1 << (SFMLParser.BROADCAST - 78)) | (1 << (SFMLParser.CHANNEL - 78)) | (1 << (SFMLParser.NEW - 78)) | (1 << (SFMLParser.CLIENT - 78)) | (1 << (SFMLParser.SERVER - 78)) | (1 << (SFMLParser.BTW - 78)) | (1 << (SFMLParser.IDENTIFIER - 78)))) !== 0))) {
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
		this.enterRule(_localctx, 106, SFMLParser.RULE_string);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 707;
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
		this.enterRule(_localctx, 108, SFMLParser.RULE_number);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 709;
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
		case 36:
			return this.withClause_sempred(_localctx as WithClauseContext, predIndex);

		case 45:
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
			return this.precpred(this._ctx, 5);

		case 3:
			return this.precpred(this._ctx, 4);
		}
		return true;
	}

	private static readonly _serializedATNSegments: number = 2;
	private static readonly _serializedATNSegment0: string =
		"\x03\uC91D\uCABA\u058D\uAFBA\u4F53\u0607\uEA8B\uC241\x03m\u02CA\x04\x02" +
		"\t\x02\x04\x03\t\x03\x04\x04\t\x04\x04\x05\t\x05\x04\x06\t\x06\x04\x07" +
		"\t\x07\x04\b\t\b\x04\t\t\t\x04\n\t\n\x04\v\t\v\x04\f\t\f\x04\r\t\r\x04" +
		"\x0E\t\x0E\x04\x0F\t\x0F\x04\x10\t\x10\x04\x11\t\x11\x04\x12\t\x12\x04" +
		"\x13\t\x13\x04\x14\t\x14\x04\x15\t\x15\x04\x16\t\x16\x04\x17\t\x17\x04" +
		"\x18\t\x18\x04\x19\t\x19\x04\x1A\t\x1A\x04\x1B\t\x1B\x04\x1C\t\x1C\x04" +
		"\x1D\t\x1D\x04\x1E\t\x1E\x04\x1F\t\x1F\x04 \t \x04!\t!\x04\"\t\"\x04#" +
		"\t#\x04$\t$\x04%\t%\x04&\t&\x04\'\t\'\x04(\t(\x04)\t)\x04*\t*\x04+\t+" +
		"\x04,\t,\x04-\t-\x04.\t.\x04/\t/\x040\t0\x041\t1\x042\t2\x043\t3\x044" +
		"\t4\x045\t5\x046\t6\x047\t7\x048\t8\x03\x02\x05\x02r\n\x02\x03\x02\x05" +
		"\x02u\n\x02\x03\x02\x07\x02x\n\x02\f\x02\x0E\x02{\v\x02\x03\x02\x07\x02" +
		"~\n\x02\f\x02\x0E\x02\x81\v\x02\x03\x02\x03\x02\x03\x03\x03\x03\x03\x03" +
		"\x03\x04\x03\x04\x03\x04\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05" +
		"\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x03\x05\x05\x05\x98\n" +
		"\x05\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03" +
		"\x06\x03\x06\x07\x06\xA4\n\x06\f\x06\x0E\x06\xA7\v\x06\x03\x06\x05\x06" +
		"\xAA\n\x06\x03\x07\x03\x07\x03\x07\x03\x07\x03\x07\x03\x07\x03\x07\x03" +
		"\x07\x03\x07\x05\x07\xB5\n\x07\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03" +
		"\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03\b\x03" +
		"\b\x03\b\x03\b\x03\b\x03\b\x05\b\xCE\n\b\x03\t\x03\t\x03\t\x07\t\xD3\n" +
		"\t\f\t\x0E\t\xD6\v\t\x03\n\x05\n\xD9\n\n\x03\n\x05\n\xDC\n\n\x03\n\x03" +
		"\n\x05\n\xE0\n\n\x03\n\x03\n\x03\n\x03\n\x03\n\x05\n\xE7\n\n\x03\n\x03" +
		"\n\x03\n\x05\n\xEC\n\n\x03\n\x03\n\x03\n\x03\n\x03\n\x05\n\xF3\n\n\x05" +
		"\n\xF5\n\n\x03\v\x03\v\x03\f\x07\f\xFA\n\f\f\f\x0E\f\xFD\v\f\x03\r\x03" +
		"\r\x03\r\x03\r\x03\r\x03\r\x03\r\x03\r\x05\r\u0107\n\r\x03\x0E\x03\x0E" +
		"\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0F\x03\x0F\x03\x0F\x03\x0F\x03\x0F" +
		"\x03\x10\x03\x10\x03\x10\x03\x10\x03\x10\x03\x10\x03\x10\x03\x10\x03\x10" +
		"\x03\x10\x03\x10\x03\x10\x03\x10\x03\x10\x07\x10\u0122\n\x10\f\x10\x0E" +
		"\x10\u0125\v\x10\x05\x10\u0127\n\x10\x03\x11\x03\x11\x03\x11\x03\x11\x03" +
		"\x12\x03\x12\x03\x12\x03\x12\x05\x12\u0131\n\x12\x03\x13\x03\x13\x03\x13" +
		"\x03\x13\x03\x13\x03\x13\x03\x14\x03\x14\x03\x14\x03\x14\x03\x14\x05\x14" +
		"\u013E\n\x14\x03\x15\x03\x15\x05\x15\u0142\n\x15\x03\x15\x03\x15\x07\x15" +
		"\u0146\n\x15\f\x15\x0E\x15\u0149\v\x15\x03\x15\x05\x15\u014C\n\x15\x03" +
		"\x16\x03\x16\x05\x16\u0150\n\x16\x03\x16\x05\x16\u0153\n\x16\x03\x16\x05" +
		"\x16\u0156\n\x16\x03\x16\x03\x16\x05\x16\u015A\n\x16\x03\x16\x03\x16\x05" +
		"\x16\u015E\n\x16\x03\x16\x03\x16\x05\x16\u0162\n\x16\x03\x16\x03\x16\x03" +
		"\x16\x05\x16\u0167\n\x16\x03\x16\x05\x16\u016A\n\x16\x03\x16\x05\x16\u016D" +
		"\n\x16\x03\x16\x05\x16\u0170\n\x16\x05\x16\u0172\n\x16\x03\x17\x03\x17" +
		"\x03\x17\x03\x17\x03\x17\x05\x17\u0179\n\x17\x03\x18\x03\x18\x03\x18\x03" +
		"\x19\x03\x19\x05\x19\u0180\n\x19\x03\x19\x05\x19\u0183\n\x19\x03\x19\x03" +
		"\x19\x05\x19\u0187\n\x19\x03\x19\x05\x19\u018A\n\x19\x03\x19\x03\x19\x03" +
		"\x19\x05\x19\u018F\n\x19\x03\x19\x05\x19\u0192\n\x19\x03\x19\x03\x19\x03" +
		"\x19\x05\x19\u0197\n\x19\x03\x19\x05\x19\u019A\n\x19\x05\x19\u019C\n\x19" +
		"\x03\x1A\x03\x1A\x03\x1B\x03\x1B\x03\x1C\x03\x1C\x03\x1C\x07\x1C\u01A5" +
		"\n\x1C\f\x1C\x0E\x1C\u01A8\v\x1C\x03\x1C\x05\x1C\u01AB\n\x1C\x03\x1D\x05" +
		"\x1D\u01AE\n\x1D\x03\x1D\x03\x1D\x05\x1D\u01B2\n\x1D\x03\x1D\x03\x1D\x05" +
		"\x1D\u01B6\n\x1D\x03\x1D\x05\x1D\u01B9\n\x1D\x03\x1E\x03\x1E\x03\x1E\x03" +
		"\x1E\x03\x1E\x05\x1E\u01C0\n\x1E\x03\x1F\x03\x1F\x05\x1F\u01C4\n\x1F\x03" +
		" \x03 \x03 \x05 \u01C9\n \x03!\x03!\x03!\x03\"\x03\"\x03\"\x05\"\u01D1" +
		"\n\"\x03\"\x03\"\x05\"\u01D5\n\"\x03\"\x03\"\x05\"\u01D9\n\"\x05\"\u01DB" +
		"\n\"\x05\"\u01DD\n\"\x05\"\u01DF\n\"\x03\"\x05\"\u01E2\n\"\x03#\x03#\x03" +
		"#\x07#\u01E7\n#\f#\x0E#\u01EA\v#\x03#\x05#\u01ED\n#\x03$\x03$\x03$\x07" +
		"$\u01F2\n$\f$\x0E$\u01F5\v$\x03$\x05$\u01F8\n$\x03%\x03%\x03%\x03%\x05" +
		"%\u01FE\n%\x03&\x03&\x03&\x03&\x03&\x03&\x03&\x03&\x03&\x05&\u0209\n&" +
		"\x03&\x05&\u020C\n&\x03&\x05&\u020F\n&\x03&\x03&\x03&\x03&\x03&\x03&\x07" +
		"&\u0217\n&\f&\x0E&\u021A\v&\x03\'\x03\'\x03\'\x03\'\x03\'\x07\'\u0221" +
		"\n\'\f\'\x0E\'\u0224\v\'\x03\'\x03\'\x03\'\x07\'\u0229\n\'\f\'\x0E\'\u022C" +
		"\v\'\x05\'\u022E\n\'\x03(\x03(\x03(\x03(\x03(\x07(\u0235\n(\f(\x0E(\u0238" +
		"\v(\x03)\x03)\x03)\x03)\x03)\x07)\u023F\n)\f)\x0E)\u0242\v)\x03)\x03)" +
		"\x05)\u0246\n)\x03*\x03*\x03+\x03+\x03+\x03,\x03,\x03,\x07,\u0250\n,\f" +
		",\x0E,\u0253\v,\x03-\x03-\x03-\x05-\u0258\n-\x03.\x03.\x03.\x03.\x03." +
		"\x03.\x03.\x03.\x03.\x03.\x07.\u0264\n.\f.\x0E.\u0267\v.\x03.\x03.\x05" +
		".\u026B\n.\x03.\x03.\x03/\x03/\x03/\x03/\x03/\x03/\x03/\x03/\x03/\x03" +
		"/\x05/\u0279\n/\x03/\x03/\x03/\x03/\x03/\x05/\u0280\n/\x03/\x05/\u0283" +
		"\n/\x03/\x03/\x05/\u0287\n/\x03/\x03/\x03/\x03/\x05/\u028D\n/\x03/\x03" +
		"/\x03/\x03/\x03/\x03/\x05/\u0295\n/\x03/\x03/\x03/\x03/\x03/\x03/\x07" +
		"/\u029D\n/\f/\x0E/\u02A0\v/\x030\x030\x031\x031\x032\x032\x032\x072\u02A9" +
		"\n2\f2\x0E2\u02AC\v2\x032\x052\u02AF\n2\x032\x052\u02B2\n2\x032\x052\u02B5" +
		"\n2\x033\x033\x033\x033\x033\x034\x034\x054\u02BE\n4\x035\x035\x035\x03" +
		"5\x036\x036\x037\x037\x038\x038\x038\x02\x02\x04J\\9\x02\x02\x04\x02\x06" +
		"\x02\b\x02\n\x02\f\x02\x0E\x02\x10\x02\x12\x02\x14\x02\x16\x02\x18\x02" +
		"\x1A\x02\x1C\x02\x1E\x02 \x02\"\x02$\x02&\x02(\x02*\x02,\x02.\x020\x02" +
		"2\x024\x026\x028\x02:\x02<\x02>\x02@\x02B\x02D\x02F\x02H\x02J\x02L\x02" +
		"N\x02P\x02R\x02T\x02V\x02X\x02Z\x02\\\x02^\x02`\x02b\x02d\x02f\x02h\x02" +
		"j\x02l\x02n\x02\x02\n\x03\x02]^\x03\x02<?\x04\x02057;\x03\x02\x1F \x03" +
		"\x02\x10\x19\x05\x02\x07\n\"\"``\x03\x02./\b\x02017:>@BHM_ii\x02\u0306" +
		"\x02q\x03\x02\x02\x02\x04\x84\x03\x02\x02\x02\x06\x87\x03\x02\x02\x02" +
		"\b\x97\x03\x02\x02\x02\n\xA9\x03\x02\x02\x02\f\xB4\x03\x02\x02\x02\x0E" +
		"\xCD\x03\x02\x02\x02\x10\xCF\x03\x02\x02\x02\x12\xF4\x03\x02\x02\x02\x14" +
		"\xF6\x03\x02\x02\x02\x16\xFB\x03\x02\x02\x02\x18\u0106\x03\x02\x02\x02" +
		"\x1A\u0108\x03\x02\x02\x02\x1C\u010E\x03\x02\x02\x02\x1E\u0126\x03\x02" +
		"\x02\x02 \u0128\x03\x02\x02\x02\"\u0130\x03\x02\x02\x02$\u0132\x03\x02" +
		"\x02\x02&\u0138\x03\x02\x02\x02(\u013F\x03\x02\x02\x02*\u0171\x03\x02" +
		"\x02\x02,\u0178\x03\x02\x02\x02.\u017A\x03\x02\x02\x020\u019B\x03\x02" +
		"\x02\x022\u019D\x03\x02\x02\x024\u019F\x03\x02\x02\x026\u01A1\x03\x02" +
		"\x02\x028\u01B8\x03\x02\x02\x02:\u01BF\x03\x02\x02\x02<\u01C1\x03\x02" +
		"\x02\x02>\u01C5\x03\x02\x02\x02@\u01CA\x03\x02\x02\x02B\u01E1\x03\x02" +
		"\x02\x02D\u01E3\x03\x02\x02\x02F\u01EE\x03\x02\x02\x02H\u01FD\x03\x02" +
		"\x02\x02J\u020E\x03\x02\x02\x02L\u022D\x03\x02\x02\x02N\u022F\x03\x02" +
		"\x02\x02P\u0245\x03\x02\x02\x02R\u0247\x03\x02\x02\x02T\u0249\x03\x02" +
		"\x02\x02V\u024C\x03\x02\x02\x02X\u0254\x03\x02\x02\x02Z\u0259\x03\x02" +
		"\x02\x02\\\u0294\x03\x02\x02\x02^\u02A1\x03\x02\x02\x02`\u02A3\x03\x02" +
		"\x02\x02b\u02A5\x03\x02\x02\x02d\u02B6\x03\x02\x02\x02f\u02BD\x03\x02" +
		"\x02\x02h\u02BF\x03\x02\x02\x02j\u02C3\x03\x02\x02\x02l\u02C5\x03\x02" +
		"\x02\x02n\u02C7\x03\x02\x02\x02pr\x05\x04\x03\x02qp\x03\x02\x02\x02qr" +
		"\x03\x02\x02\x02rt\x03\x02\x02\x02su\x05\x06\x04\x02ts\x03\x02\x02\x02" +
		"tu\x03\x02\x02\x02uy\x03\x02\x02\x02vx\x05\b\x05\x02wv\x03\x02\x02\x02" +
		"x{\x03\x02\x02\x02yw\x03\x02\x02\x02yz\x03\x02\x02\x02z\x7F\x03\x02\x02" +
		"\x02{y\x03\x02\x02\x02|~\x05\x0E\b\x02}|\x03\x02\x02\x02~\x81\x03\x02" +
		"\x02\x02\x7F}\x03\x02\x02\x02\x7F\x80\x03\x02\x02\x02\x80\x82\x03\x02" +
		"\x02\x02\x81\x7F\x03\x02\x02\x02\x82\x83\x07\x02\x02\x03\x83\x03\x03\x02" +
		"\x02\x02\x84\x85\t\x02\x02\x02\x85\x86\x07_\x02\x02\x86\x05\x03\x02\x02" +
		"\x02\x87\x88\x07L\x02\x02\x88\x89\x05l7\x02\x89\x07\x03\x02\x02\x02\x8A" +
		"\x8B\x07M\x02\x02\x8B\x8C\x05j6\x02\x8C\x8D\x07N\x02\x02\x8D\x8E\x07O" +
		"\x02\x02\x8E\x8F\x07P\x02\x02\x8F\x90\x05j6\x02\x90\x98\x03\x02\x02\x02" +
		"\x91\x92\x07M\x02\x02\x92\x93\x05j6\x02\x93\x94\x07N\x02\x02\x94\x95\x07" +
		"Q\x02\x02\x95\x96\x05\n\x06\x02\x96\x98\x03\x02\x02\x02\x97\x8A\x03\x02" +
		"\x02\x02\x97\x91\x03\x02\x02\x02\x98\t\x03\x02\x02\x02\x99\xAA\x07T\x02" +
		"\x02\x9A\xAA\x07U\x02\x02\x9B\xAA\x05l7\x02\x9C\x9D\x07R\x02\x02\x9D\x9E" +
		"\x07(\x02\x02\x9E\x9F\x07S\x02\x02\x9F\xA5\x05\f\x07\x02\xA0\xA1\x07\x0E" +
		"\x02\x02\xA1\xA2\x07S\x02\x02\xA2\xA4\x05\f\x07\x02\xA3\xA0\x03\x02\x02" +
		"\x02\xA4\xA7\x03\x02\x02\x02\xA5\xA3\x03\x02\x02\x02\xA5\xA6\x03\x02\x02" +
		"\x02\xA6\xAA\x03\x02\x02\x02\xA7\xA5\x03\x02\x02\x02\xA8\xAA\x05j6\x02" +
		"\xA9\x99\x03\x02\x02\x02\xA9\x9A\x03\x02\x02\x02\xA9\x9B\x03\x02\x02\x02" +
		"\xA9\x9C\x03\x02\x02\x02\xA9\xA8\x03\x02\x02\x02\xAA\v\x03\x02\x02\x02" +
		"\xAB\xAC\x05j6\x02\xAC\xAD\x07P\x02\x02\xAD\xAE\x05l7\x02\xAE\xB5\x03" +
		"\x02\x02\x02\xAF\xB0\x05j6\x02\xB0\xB1\x07Q\x02\x02\xB1\xB2\x05j6\x02" +
		"\xB2\xB5\x03\x02\x02\x02\xB3\xB5\x05j6\x02\xB4\xAB\x03\x02\x02\x02\xB4" +
		"\xAF\x03\x02\x02\x02\xB4\xB3\x03\x02\x02\x02\xB5\r\x03\x02\x02\x02\xB6" +
		"\xB7\x07`\x02\x02\xB7\xB8\x05\x12\n\x02\xB8\xB9\x07J\x02\x02\xB9\xBA\x05" +
		"\x16\f\x02\xBA\xBB\x07K\x02\x02\xBB\xCE\x03\x02\x02\x02\xBC\xBD\x07`\x02" +
		"\x02\xBD\xBE\x07H\x02\x02\xBE\xBF\x07I\x02\x02\xBF\xC0\x07J\x02\x02\xC0" +
		"\xC1\x05\x16\f\x02\xC1\xC2\x07K\x02\x02\xC2\xCE\x03\x02\x02\x02\xC3\xC4" +
		"\x07`\x02\x02\xC4\xC5\x07C\x02\x02\xC5\xC6\x07D\x02\x02\xC6\xC7\x05\x10" +
		"\t\x02\xC7\xC8\x07X\x02\x02\xC8\xC9\x05j6\x02\xC9\xCA\x07J\x02\x02\xCA" +
		"\xCB\x05\x16\f\x02\xCB\xCC\x07K\x02\x02\xCC\xCE\x03\x02\x02\x02\xCD\xB6" +
		"\x03\x02\x02\x02\xCD\xBC\x03\x02\x02\x02\xCD\xC3\x03\x02\x02\x02\xCE\x0F" +
		"\x03\x02\x02\x02\xCF\xD4\x05f4\x02\xD0\xD1\x07a\x02\x02\xD1\xD3\x05f4" +
		"\x02\xD2\xD0\x03\x02\x02\x02\xD3\xD6\x03\x02\x02\x02\xD4\xD2\x03\x02\x02" +
		"\x02\xD4\xD5\x03\x02\x02\x02\xD5\x11\x03\x02\x02\x02\xD6\xD4\x03\x02\x02" +
		"\x02\xD7\xD9\x07h\x02\x02\xD8\xD7\x03\x02\x02\x02\xD8\xD9\x03\x02\x02" +
		"\x02\xD9\xDB\x03\x02\x02\x02\xDA\xDC\x07@\x02\x02\xDB\xDA\x03\x02\x02" +
		"\x02\xDB\xDC\x03\x02\x02\x02\xDC\xDF\x03\x02\x02\x02\xDD\xDE\x07A\x02" +
		"\x02\xDE\xE0\x07h\x02\x02\xDF\xDD\x03\x02\x02\x02\xDF\xE0\x03\x02\x02" +
		"\x02\xE0\xE1\x03\x02\x02\x02\xE1\xE6\x05\x14\v\x02\xE2\xE3\x07B\x02\x02" +
		"\xE3\xE4\x07-\x02\x02\xE4\xE5\x07h\x02\x02\xE5\xE7\x05\x14\v\x02\xE6\xE2" +
		"\x03\x02\x02\x02\xE6\xE7\x03\x02\x02\x02\xE7\xF5\x03\x02\x02\x02\xE8\xEB" +
		"\x07g\x02\x02\xE9\xEA\x07A\x02\x02\xEA\xEC\x07h\x02\x02\xEB\xE9\x03\x02" +
		"\x02\x02\xEB\xEC\x03\x02\x02\x02\xEC\xED\x03\x02\x02\x02\xED\xF2\x05\x14" +
		"\v\x02\xEE\xEF\x07B\x02\x02\xEF\xF0\x07-\x02\x02\xF0\xF1\x07h\x02\x02" +
		"\xF1\xF3\x05\x14\v\x02\xF2\xEE\x03\x02\x02\x02\xF2\xF3\x03\x02\x02\x02" +
		"\xF3\xF5\x03\x02\x02\x02\xF4\xD8\x03\x02\x02\x02\xF4\xE8\x03\x02\x02\x02" +
		"\xF5\x13\x03\x02\x02\x02\xF6\xF7\t\x03\x02\x02\xF7\x15\x03\x02\x02\x02" +
		"\xF8\xFA\x05\x18\r\x02\xF9\xF8\x03\x02\x02\x02\xFA\xFD\x03\x02\x02\x02" +
		"\xFB\xF9\x03\x02\x02\x02\xFB\xFC\x03\x02\x02\x02\xFC\x17\x03\x02\x02\x02" +
		"\xFD\xFB\x03\x02\x02\x02\xFE\u0107\x05*\x16\x02\xFF\u0107\x050\x19\x02" +
		"\u0100\u0107\x05Z.\x02\u0101\u0107\x05(\x15\x02\u0102\u0107\x05\x1C\x0F" +
		"\x02\u0103\u0107\x05$\x13\x02\u0104\u0107\x05&\x14\x02\u0105\u0107\x05" +
		"\x1A\x0E\x02\u0106\xFE\x03\x02\x02\x02\u0106\xFF\x03\x02\x02\x02\u0106" +
		"\u0100\x03\x02\x02\x02\u0106\u0101\x03\x02\x02\x02\u0106\u0102\x03\x02" +
		"\x02\x02\u0106\u0103\x03\x02\x02\x02\u0106\u0104\x03\x02\x02\x02\u0106" +
		"\u0105\x03\x02\x02\x02\u0107\x19\x03\x02\x02\x02\u0108\u0109\x07F\x02" +
		"\x02\u0109\u010A\x07G\x02\x02\u010A\u010B\x05l7\x02\u010B\u010C\x07\x1B" +
		"\x02\x02\u010C\u010D\x05j6\x02\u010D\x1B\x03\x02\x02\x02\u010E\u010F\x07" +
		"M\x02\x02\u010F\u0110\x05j6\x02\u0110\u0111\x07N\x02\x02\u0111\u0112\x05" +
		"\x1E\x10\x02\u0112\x1D\x03\x02\x02\x02\u0113\u0114\x07U\x02\x02\u0114" +
		"\u0115\x07P\x02\x02\u0115\u0116\x07V\x02\x02\u0116\u0117\x05N(\x02\u0117" +
		"\u0118\x07(\x02\x02\u0118\u0119\x05j6\x02\u0119\u0127\x03\x02\x02\x02" +
		"\u011A\u011B\x05j6\x02\u011B\u011C\x07(\x02\x02\u011C\u011D\x07S\x02\x02" +
		"\u011D\u0123\x05 \x11\x02\u011E\u011F\x07\x0E\x02\x02\u011F\u0120\x07" +
		"S\x02\x02\u0120\u0122\x05 \x11\x02\u0121\u011E\x03\x02\x02\x02\u0122\u0125" +
		"\x03\x02\x02\x02\u0123\u0121\x03\x02\x02\x02\u0123\u0124\x03\x02\x02\x02" +
		"\u0124\u0127\x03\x02\x02\x02\u0125\u0123\x03\x02\x02\x02\u0126\u0113\x03" +
		"\x02\x02\x02\u0126\u011A\x03\x02\x02\x02\u0127\x1F\x03\x02\x02\x02\u0128" +
		"\u0129\x05j6\x02\u0129\u012A\x07P\x02\x02\u012A\u012B\x05\"\x12\x02\u012B" +
		"!\x03\x02\x02\x02\u012C\u012D\x07\\\x02\x02\u012D\u0131\x07T\x02\x02\u012E" +
		"\u0131\x05l7\x02\u012F\u0131\x05j6\x02\u0130\u012C\x03\x02\x02\x02\u0130" +
		"\u012E\x03\x02\x02\x02\u0130\u012F\x03\x02\x02\x02\u0131#\x03\x02\x02" +
		"\x02\u0132\u0133\x07Y\x02\x02\u0133\u0134\x07\x1C\x02\x02\u0134\u0135" +
		"\x05N(\x02\u0135\u0136\x07(\x02\x02\u0136\u0137\x05j6\x02\u0137%\x03\x02" +
		"\x02\x02\u0138\u0139\x07Z\x02\x02\u0139\u013A\x07\x1B\x02\x02\u013A\u013D" +
		"\x05j6\x02\u013B\u013C\x07[\x02\x02\u013C\u013E\x05N(\x02\u013D\u013B" +
		"\x03\x02\x02\x02\u013D\u013E\x03\x02\x02\x02\u013E\'\x03\x02\x02\x02\u013F" +
		"\u0141\x07$\x02\x02\u0140\u0142\x05f4\x02\u0141\u0140\x03\x02\x02\x02" +
		"\u0141\u0142\x03\x02\x02\x02\u0142\u0147\x03\x02\x02\x02\u0143\u0144\x07" +
		"a\x02\x02\u0144\u0146\x05f4\x02\u0145\u0143\x03\x02\x02\x02\u0146\u0149" +
		"\x03\x02\x02\x02\u0147\u0145\x03\x02\x02\x02\u0147\u0148\x03\x02\x02\x02" +
		"\u0148\u014B\x03\x02\x02\x02\u0149\u0147\x03\x02\x02\x02\u014A\u014C\x07" +
		"a\x02\x02\u014B\u014A\x03\x02\x02\x02\u014B\u014C\x03\x02\x02\x02\u014C" +
		")\x03\x02\x02\x02\u014D\u014F\x07\x1C\x02\x02\u014E\u0150\x05,\x17\x02" +
		"\u014F\u014E\x03\x02\x02\x02\u014F\u0150\x03\x02\x02\x02\u0150\u0152\x03" +
		"\x02\x02\x02\u0151\u0153\x052\x1A\x02\u0152\u0151\x03\x02\x02\x02\u0152" +
		"\u0153\x03\x02\x02\x02\u0153\u0155\x03\x02\x02\x02\u0154\u0156\x05@!\x02" +
		"\u0155\u0154\x03\x02\x02\x02\u0155\u0156\x03\x02\x02\x02\u0156\u0157\x03" +
		"\x02\x02\x02\u0157\u0159\x07\x1A\x02\x02\u0158\u015A\x07\"\x02\x02\u0159" +
		"\u0158\x03\x02\x02\x02\u0159\u015A\x03\x02\x02\x02\u015A\u015B\x03\x02" +
		"\x02\x02\u015B\u015D\x05b2\x02\u015C\u015E\x05.\x18\x02\u015D\u015C\x03" +
		"\x02\x02\x02\u015D\u015E\x03\x02\x02\x02\u015E\u0172\x03\x02\x02\x02\u015F" +
		"\u0161\x07\x1A\x02\x02\u0160\u0162\x07\"\x02\x02\u0161\u0160\x03\x02\x02" +
		"\x02\u0161\u0162\x03\x02\x02\x02\u0162\u0163\x03\x02\x02\x02\u0163\u0164" +
		"\x05b2\x02\u0164\u0166\x07\x1C\x02\x02\u0165\u0167\x05,\x17\x02\u0166" +
		"\u0165\x03\x02\x02\x02\u0166\u0167\x03\x02\x02\x02\u0167\u0169\x03\x02" +
		"\x02\x02\u0168\u016A\x052\x1A\x02\u0169\u0168\x03\x02\x02\x02\u0169\u016A" +
		"\x03\x02\x02\x02\u016A\u016C\x03\x02\x02\x02\u016B\u016D\x05@!\x02\u016C" +
		"\u016B\x03\x02\x02\x02\u016C\u016D\x03\x02\x02\x02\u016D\u016F\x03\x02" +
		"\x02\x02\u016E\u0170\x05.\x18\x02\u016F\u016E\x03\x02\x02\x02\u016F\u0170" +
		"\x03\x02\x02\x02\u0170\u0172\x03\x02\x02\x02\u0171\u014D\x03\x02\x02\x02" +
		"\u0171\u015F\x03\x02\x02\x02\u0172+\x03\x02\x02\x02\u0173\u0174\x07(\x02" +
		"\x02\u0174\u0175\x07W\x02\x02\u0175\u0179\x05N(\x02\u0176\u0177\x07Q\x02" +
		"\x02\u0177\u0179\x05j6\x02\u0178\u0173\x03\x02\x02\x02\u0178\u0176\x03" +
		"\x02\x02\x02\u0179-\x03\x02\x02\x02\u017A\u017B\x07X\x02\x02\u017B\u017C" +
		"\x05j6\x02\u017C/\x03\x02\x02\x02\u017D\u017F\x07\x1D\x02\x02\u017E\u0180" +
		"\x054\x1B\x02\u017F\u017E\x03\x02\x02\x02\u017F\u0180\x03\x02\x02\x02" +
		"\u0180\u0182\x03\x02\x02\x02\u0181\u0183\x05@!\x02\u0182\u0181\x03\x02" +
		"\x02\x02\u0182\u0183\x03\x02\x02\x02\u0183\u0184\x03\x02\x02\x02\u0184" +
		"\u0186\x07\x1B\x02\x02\u0185\u0187\x05h5\x02\u0186\u0185\x03\x02\x02\x02" +
		"\u0186\u0187\x03\x02\x02\x02\u0187\u0189\x03\x02\x02\x02\u0188\u018A\x07" +
		"\"\x02\x02\u0189\u0188\x03\x02\x02\x02\u0189\u018A\x03\x02\x02\x02\u018A" +
		"\u018B\x03\x02\x02\x02\u018B\u019C\x05b2\x02\u018C\u018E\x07\x1B\x02\x02" +
		"\u018D\u018F\x05h5\x02\u018E\u018D\x03\x02\x02\x02\u018E\u018F\x03\x02" +
		"\x02\x02\u018F\u0191\x03\x02\x02\x02\u0190\u0192\x07\"\x02\x02\u0191\u0190" +
		"\x03\x02\x02\x02\u0191\u0192\x03\x02\x02\x02\u0192\u0193\x03\x02\x02\x02" +
		"\u0193\u0194\x05b2\x02\u0194\u0196\x07\x1D\x02\x02\u0195\u0197\x054\x1B" +
		"\x02\u0196\u0195\x03\x02\x02\x02\u0196\u0197\x03\x02\x02\x02\u0197\u0199" +
		"\x03\x02\x02\x02\u0198\u019A\x05@!\x02\u0199\u0198\x03\x02\x02\x02\u0199" +
		"\u019A\x03\x02\x02\x02\u019A\u019C\x03\x02\x02\x02\u019B\u017D\x03\x02" +
		"\x02\x02\u019B\u018C\x03\x02\x02\x02\u019C1\x03\x02\x02\x02\u019D\u019E" +
		"\x056\x1C\x02\u019E3\x03\x02\x02\x02\u019F\u01A0\x056\x1C\x02\u01A05\x03" +
		"\x02\x02\x02\u01A1\u01A6\x058\x1D\x02\u01A2\u01A3\x07a\x02\x02\u01A3\u01A5" +
		"\x058\x1D\x02\u01A4\u01A2\x03\x02\x02\x02\u01A5\u01A8\x03\x02\x02\x02" +
		"\u01A6\u01A4\x03\x02\x02\x02\u01A6\u01A7\x03\x02\x02\x02\u01A7\u01AA\x03" +
		"\x02\x02\x02\u01A8\u01A6\x03\x02\x02\x02\u01A9\u01AB\x07a\x02\x02\u01AA" +
		"\u01A9\x03\x02\x02\x02\u01AA\u01AB\x03\x02\x02\x02\u01AB7\x03\x02\x02" +
		"\x02\u01AC\u01AE\x05:\x1E\x02\u01AD\u01AC\x03\x02\x02\x02\u01AD\u01AE" +
		"\x03\x02\x02\x02\u01AE\u01AF\x03\x02\x02\x02\u01AF\u01B1\x05F$\x02\u01B0" +
		"\u01B2\x05H%\x02\u01B1\u01B0\x03\x02\x02\x02\u01B1\u01B2\x03\x02\x02\x02" +
		"\u01B2\u01B9\x03\x02\x02\x02\u01B3\u01B5\x05:\x1E\x02\u01B4\u01B6\x05" +
		"H%\x02\u01B5\u01B4\x03\x02\x02\x02\u01B5\u01B6\x03\x02\x02\x02\u01B6\u01B9" +
		"\x03\x02\x02\x02\u01B7\u01B9\x05H%\x02\u01B8\u01AD\x03\x02\x02\x02\u01B8" +
		"\u01B3\x03\x02\x02\x02\u01B8\u01B7\x03\x02\x02\x02\u01B99\x03\x02\x02" +
		"\x02\u01BA\u01BB\x05<\x1F\x02\u01BB\u01BC\x05> \x02\u01BC\u01C0\x03\x02" +
		"\x02\x02\u01BD\u01C0\x05> \x02\u01BE\u01C0\x05<\x1F\x02\u01BF\u01BA\x03" +
		"\x02\x02\x02\u01BF\u01BD\x03\x02\x02\x02\u01BF\u01BE\x03\x02\x02\x02\u01C0" +
		";\x03\x02\x02\x02\u01C1\u01C3\x05n8\x02\u01C2\u01C4\x07\"\x02\x02\u01C3" +
		"\u01C2\x03\x02\x02\x02\u01C3\u01C4\x03\x02\x02\x02\u01C4=\x03\x02\x02" +
		"\x02\u01C5\u01C6\x07!\x02\x02\u01C6\u01C8\x05n8\x02\u01C7\u01C9\x07\"" +
		"\x02\x02\u01C8\u01C7\x03\x02\x02\x02\u01C8\u01C9\x03\x02\x02\x02\u01C9" +
		"?\x03\x02\x02\x02\u01CA\u01CB\x07#\x02\x02\u01CB\u01CC\x05D#\x02\u01CC" +
		"A\x03\x02\x02\x02\u01CD\u01DE\x05j6\x02\u01CE\u01D0\x07b\x02\x02\u01CF" +
		"\u01D1\x05j6\x02\u01D0\u01CF\x03\x02\x02\x02\u01D0\u01D1\x03\x02\x02\x02" +
		"\u01D1\u01DC\x03\x02\x02\x02\u01D2\u01D4\x07b\x02\x02\u01D3\u01D5\x05" +
		"j6\x02\u01D4\u01D3\x03\x02\x02\x02\u01D4\u01D5\x03\x02\x02\x02\u01D5\u01DA" +
		"\x03\x02\x02\x02\u01D6\u01D8\x07b\x02\x02\u01D7\u01D9\x05j6\x02\u01D8" +
		"\u01D7\x03\x02\x02\x02\u01D8\u01D9\x03\x02\x02\x02\u01D9\u01DB\x03\x02" +
		"\x02\x02\u01DA\u01D6\x03\x02\x02\x02\u01DA\u01DB\x03\x02\x02\x02\u01DB" +
		"\u01DD\x03\x02\x02\x02\u01DC\u01D2\x03\x02\x02\x02\u01DC\u01DD\x03\x02" +
		"\x02\x02\u01DD\u01DF\x03\x02\x02\x02\u01DE\u01CE\x03\x02\x02\x02\u01DE" +
		"\u01DF\x03\x02\x02\x02\u01DF\u01E2\x03\x02\x02\x02\u01E0\u01E2\x05l7\x02" +
		"\u01E1\u01CD\x03\x02\x02\x02\u01E1\u01E0\x03\x02\x02\x02\u01E2C\x03\x02" +
		"\x02\x02\u01E3\u01E8\x05B\"\x02\u01E4\u01E5\x07a\x02\x02\u01E5\u01E7\x05" +
		"B\"\x02\u01E6\u01E4\x03\x02\x02\x02\u01E7\u01EA\x03\x02\x02\x02\u01E8" +
		"\u01E6\x03\x02\x02\x02\u01E8\u01E9\x03\x02\x02\x02\u01E9\u01EC\x03\x02" +
		"\x02\x02\u01EA\u01E8\x03\x02\x02\x02\u01EB\u01ED\x07a\x02\x02\u01EC\u01EB" +
		"\x03\x02\x02\x02\u01EC\u01ED\x03\x02\x02\x02\u01EDE\x03\x02\x02\x02\u01EE" +
		"\u01F3\x05B\"\x02\u01EF\u01F0\x07\x0F\x02\x02\u01F0\u01F2\x05B\"\x02\u01F1" +
		"\u01EF\x03\x02\x02\x02\u01F2\u01F5\x03\x02\x02\x02\u01F3\u01F1\x03\x02" +
		"\x02\x02\u01F3\u01F4\x03\x02\x02\x02\u01F4\u01F7\x03\x02\x02\x02\u01F5" +
		"\u01F3\x03\x02\x02\x02\u01F6\u01F8\x07\x0F\x02\x02\u01F7\u01F6\x03\x02" +
		"\x02\x02\u01F7\u01F8\x03\x02\x02\x02\u01F8G\x03\x02\x02\x02\u01F9\u01FA" +
		"\x07(\x02\x02\u01FA\u01FE\x05J&\x02\u01FB\u01FC\x07\'\x02\x02\u01FC\u01FE" +
		"\x05J&\x02\u01FD\u01F9\x03\x02\x02\x02\u01FD\u01FB\x03\x02\x02\x02\u01FE" +
		"I\x03\x02\x02\x02\u01FF\u0200\b&\x01\x02\u0200\u0201\x07e\x02\x02\u0201" +
		"\u0202\x05J&\x02\u0202\u0203\x07f\x02\x02\u0203\u020F\x03\x02\x02\x02" +
		"\u0204\u0205\x07\r\x02\x02\u0205\u020F\x05J&\x06\u0206\u0208\x07)\x02" +
		"\x02\u0207\u0209\x07*\x02\x02\u0208\u0207\x03\x02\x02\x02\u0208\u0209" +
		"\x03\x02\x02\x02\u0209";
	private static readonly _serializedATNSegment1: string =
		"\u020C\x03\x02\x02\x02\u020A\u020C\x07*\x02\x02\u020B\u0206\x03\x02\x02" +
		"\x02\u020B\u020A\x03\x02\x02\x02\u020C\u020D\x03\x02\x02\x02\u020D\u020F" +
		"\x05L\'\x02\u020E\u01FF\x03\x02\x02\x02\u020E\u0204\x03\x02\x02\x02\u020E" +
		"\u020B\x03\x02\x02\x02\u020F\u0218\x03\x02\x02\x02\u0210\u0211\f\x05\x02" +
		"\x02\u0211\u0212\x07\x0E\x02\x02\u0212\u0217\x05J&\x06\u0213\u0214\f\x04" +
		"\x02\x02\u0214\u0215\x07\x0F\x02\x02\u0215\u0217\x05J&\x05\u0216\u0210" +
		"\x03\x02\x02\x02\u0216\u0213\x03\x02\x02\x02\u0217\u021A\x03\x02\x02\x02" +
		"\u0218\u0216\x03\x02\x02\x02\u0218\u0219\x03\x02\x02\x02\u0219K\x03\x02" +
		"\x02\x02\u021A\u0218\x03\x02\x02\x02\u021B\u021C\x05j6\x02\u021C\u021D" +
		"\x07b\x02\x02\u021D\u0222\x05j6\x02\u021E\u021F\x07c\x02\x02\u021F\u0221" +
		"\x05j6\x02\u0220\u021E\x03\x02\x02\x02\u0221\u0224\x03\x02\x02\x02\u0222" +
		"\u0220\x03\x02\x02\x02\u0222\u0223\x03\x02\x02\x02\u0223\u022E\x03\x02" +
		"\x02\x02\u0224\u0222\x03\x02\x02\x02\u0225\u022A\x05j6\x02\u0226\u0227" +
		"\x07c\x02\x02\u0227\u0229\x05j6\x02\u0228\u0226\x03\x02\x02\x02\u0229" +
		"\u022C\x03\x02\x02\x02\u022A\u0228\x03\x02\x02\x02\u022A\u022B\x03\x02" +
		"\x02\x02\u022B\u022E\x03\x02\x02\x02\u022C\u022A\x03\x02\x02\x02\u022D" +
		"\u021B\x03\x02\x02\x02\u022D\u0225\x03\x02\x02\x02\u022EM\x03\x02\x02" +
		"\x02\u022F\u0230\x05j6\x02\u0230\u0231\x07b\x02\x02\u0231\u0236\x05j6" +
		"\x02\u0232\u0233\x07c\x02\x02\u0233\u0235\x05j6\x02\u0234\u0232\x03\x02" +
		"\x02\x02\u0235\u0238\x03\x02\x02\x02\u0236\u0234\x03\x02\x02\x02\u0236" +
		"\u0237\x03\x02\x02\x02\u0237O\x03\x02\x02\x02\u0238\u0236\x03\x02\x02" +
		"\x02\u0239\u023A\x07\"\x02\x02\u023A\u0246\x076\x02\x02\u023B\u0240\x05" +
		"R*\x02\u023C\u023D\x07a\x02\x02\u023D\u023F\x05R*\x02\u023E\u023C\x03" +
		"\x02\x02\x02\u023F\u0242\x03\x02\x02\x02\u0240\u023E\x03\x02\x02\x02\u0240" +
		"\u0241\x03\x02\x02\x02\u0241\u0243\x03\x02\x02\x02\u0242\u0240\x03\x02" +
		"\x02\x02\u0243\u0244\x076\x02\x02\u0244\u0246\x03\x02\x02\x02\u0245\u0239" +
		"\x03\x02\x02\x02\u0245\u023B\x03\x02\x02\x02\u0246Q\x03\x02\x02\x02\u0247" +
		"\u0248\t\x04\x02\x02\u0248S\x03\x02\x02\x02\u0249\u024A\t\x05\x02\x02" +
		"\u024A\u024B\x05V,\x02\u024BU\x03\x02\x02\x02\u024C\u0251\x05X-\x02\u024D" +
		"\u024E\x07a\x02\x02\u024E\u0250\x05X-\x02\u024F\u024D\x03\x02\x02\x02" +
		"\u0250\u0253\x03\x02\x02\x02\u0251\u024F\x03\x02\x02\x02\u0251\u0252\x03" +
		"\x02\x02\x02\u0252W\x03\x02\x02\x02\u0253\u0251\x03\x02\x02\x02\u0254" +
		"\u0257\x05n8\x02\u0255\u0256\x07d\x02\x02\u0256\u0258\x05n8\x02\u0257" +
		"\u0255\x03\x02\x02\x02\u0257\u0258\x03\x02\x02\x02\u0258Y\x03\x02\x02" +
		"\x02\u0259\u025A\x07\x03\x02\x02\u025A\u025B\x05\\/\x02\u025B\u025C\x07" +
		"\x04\x02\x02\u025C\u0265\x05\x16\f\x02\u025D\u025E\x07\x05\x02\x02\u025E" +
		"\u025F\x07\x03\x02\x02\u025F\u0260\x05\\/\x02\u0260\u0261\x07\x04\x02" +
		"\x02\u0261\u0262\x05\x16\f\x02\u0262\u0264\x03\x02\x02\x02\u0263\u025D" +
		"\x03\x02\x02\x02\u0264\u0267\x03\x02\x02\x02\u0265\u0263\x03\x02\x02\x02" +
		"\u0265\u0266\x03\x02\x02\x02\u0266\u026A\x03\x02\x02\x02\u0267\u0265\x03" +
		"\x02\x02\x02\u0268\u0269\x07\x05\x02\x02\u0269\u026B\x05\x16\f\x02\u026A" +
		"\u0268\x03\x02\x02\x02\u026A\u026B\x03\x02\x02\x02\u026B\u026C\x03\x02" +
		"\x02\x02\u026C\u026D\x07K\x02\x02\u026D[\x03\x02\x02\x02\u026E\u026F\b" +
		"/\x01\x02\u026F\u0295\x07\v\x02\x02\u0270\u0295\x07\f\x02\x02\u0271\u0272" +
		"\x07e\x02\x02\u0272\u0273\x05\\/\x02\u0273\u0274\x07f\x02\x02\u0274\u0295" +
		"\x03\x02\x02\x02\u0275\u0276\x07\r\x02\x02\u0276\u0295\x05\\/\b\u0277" +
		"\u0279\x05`1\x02\u0278\u0277\x03\x02\x02\x02\u0278\u0279\x03\x02\x02\x02" +
		"\u0279\u027A\x03\x02\x02\x02\u027A\u027B\x05b2\x02\u027B\u027C\x07\x06" +
		"\x02\x02\u027C\u027D\x05^0\x02\u027D\u027F\x05n8\x02\u027E\u0280\x05F" +
		"$\x02\u027F\u027E\x03\x02\x02\x02\u027F\u0280\x03\x02\x02\x02\u0280\u0282" +
		"\x03\x02\x02\x02\u0281\u0283\x05H%\x02\u0282\u0281\x03\x02\x02\x02\u0282" +
		"\u0283\x03\x02\x02\x02\u0283\u0286\x03\x02\x02\x02\u0284\u0285\x07#\x02" +
		"\x02\u0285\u0287\x05D#\x02\u0286\u0284\x03\x02\x02\x02\u0286\u0287\x03" +
		"\x02\x02\x02\u0287\u0295\x03\x02\x02\x02\u0288\u028C\x07H\x02\x02\u0289" +
		"\u028A\x05^0\x02\u028A\u028B\x05n8\x02\u028B\u028D\x03\x02\x02\x02\u028C" +
		"\u0289\x03\x02\x02\x02\u028C\u028D\x03\x02\x02\x02\u028D\u0295\x03\x02" +
		"\x02\x02\u028E\u028F\x07C\x02\x02\u028F\u0290\x07E\x02\x02\u0290\u0291" +
		"\x05n8\x02\u0291\u0292\x05^0\x02\u0292\u0293\x05n8\x02\u0293\u0295\x03" +
		"\x02\x02\x02\u0294\u026E\x03\x02\x02\x02\u0294\u0270\x03\x02\x02\x02\u0294" +
		"\u0271\x03\x02\x02\x02\u0294\u0275\x03\x02\x02\x02\u0294\u0278\x03\x02" +
		"\x02\x02\u0294\u0288\x03\x02\x02\x02\u0294\u028E\x03\x02\x02\x02\u0295" +
		"\u029E\x03\x02\x02\x02\u0296\u0297\f\x07\x02\x02\u0297\u0298\x07\x0E\x02" +
		"\x02\u0298\u029D\x05\\/\b\u0299\u029A\f\x06\x02\x02\u029A\u029B\x07\x0F" +
		"\x02\x02\u029B\u029D\x05\\/\x07\u029C\u0296\x03\x02\x02\x02\u029C\u0299" +
		"\x03\x02\x02\x02\u029D\u02A0\x03\x02\x02\x02\u029E\u029C\x03\x02\x02\x02" +
		"\u029E\u029F\x03\x02\x02\x02\u029F]\x03\x02\x02\x02\u02A0\u029E\x03\x02" +
		"\x02\x02\u02A1\u02A2\t\x06\x02\x02\u02A2_\x03\x02\x02\x02\u02A3\u02A4" +
		"\t\x07\x02\x02\u02A4a\x03\x02\x02\x02\u02A5\u02AA\x05f4\x02\u02A6\u02A7" +
		"\x07a\x02\x02\u02A7\u02A9\x05f4\x02\u02A8\u02A6\x03\x02\x02\x02\u02A9" +
		"\u02AC\x03\x02\x02\x02\u02AA\u02A8\x03\x02\x02\x02\u02AA\u02AB\x03\x02" +
		"\x02\x02\u02AB\u02AE\x03\x02\x02\x02\u02AC\u02AA\x03\x02\x02\x02\u02AD" +
		"\u02AF\x05d3\x02\u02AE\u02AD\x03\x02\x02\x02\u02AE\u02AF\x03\x02\x02\x02" +
		"\u02AF\u02B1\x03\x02\x02\x02\u02B0\u02B2\x05P)\x02\u02B1\u02B0\x03\x02" +
		"\x02\x02\u02B1\u02B2\x03\x02\x02\x02\u02B2\u02B4\x03\x02\x02\x02\u02B3" +
		"\u02B5\x05T+\x02\u02B4\u02B3\x03\x02\x02\x02\u02B4\u02B5\x03\x02\x02\x02" +
		"\u02B5c\x03\x02\x02\x02\u02B6\u02B7\x07+\x02\x02\u02B7\u02B8\x07,\x02" +
		"\x02\u02B8\u02B9\x07-\x02\x02\u02B9\u02BA\t\b\x02\x02\u02BAe\x03\x02\x02" +
		"\x02\u02BB\u02BE\x05j6\x02\u02BC\u02BE\x05l7\x02\u02BD\u02BB\x03\x02\x02" +
		"\x02\u02BD\u02BC\x03\x02\x02\x02\u02BEg\x03\x02\x02\x02\u02BF\u02C0\x07" +
		"%\x02\x02\u02C0\u02C1\t\x05\x02\x02\u02C1\u02C2\x07&\x02\x02\u02C2i\x03" +
		"\x02\x02\x02\u02C3\u02C4\t\t\x02\x02\u02C4k\x03\x02\x02\x02\u02C5\u02C6" +
		"\x07j\x02\x02\u02C6m\x03\x02\x02\x02\u02C7\u02C8\x07h\x02\x02\u02C8o\x03" +
		"\x02\x02\x02bqty\x7F\x97\xA5\xA9\xB4\xCD\xD4\xD8\xDB\xDF\xE6\xEB\xF2\xF4" +
		"\xFB\u0106\u0123\u0126\u0130\u013D\u0141\u0147\u014B\u014F\u0152\u0155" +
		"\u0159\u015D\u0161\u0166\u0169\u016C\u016F\u0171\u0178\u017F\u0182\u0186" +
		"\u0189\u018E\u0191\u0196\u0199\u019B\u01A6\u01AA\u01AD\u01B1\u01B5\u01B8" +
		"\u01BF\u01C3\u01C8\u01D0\u01D4\u01D8\u01DA\u01DC\u01DE\u01E1\u01E8\u01EC" +
		"\u01F3\u01F7\u01FD\u0208\u020B\u020E\u0216\u0218\u0222\u022A\u022D\u0236" +
		"\u0240\u0245\u0251\u0257\u0265\u026A\u0278\u027F\u0282\u0286\u028C\u0294" +
		"\u029C\u029E\u02AA\u02AE\u02B1\u02B4\u02BD";
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
export class FrameTriggerContext extends TriggerContext {
	public EVERY(): TerminalNode { return this.getToken(SFMLParser.EVERY, 0); }
	public FRAME(): TerminalNode { return this.getToken(SFMLParser.FRAME, 0); }
	public FOR(): TerminalNode { return this.getToken(SFMLParser.FOR, 0); }
	public frameLabels(): FrameLabelsContext {
		return this.getRuleContext(0, FrameLabelsContext);
	}
	public AS(): TerminalNode { return this.getToken(SFMLParser.AS, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
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
		if (listener.enterFrameTrigger) {
			listener.enterFrameTrigger(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitFrameTrigger) {
			listener.exitFrameTrigger(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitFrameTrigger) {
			return visitor.visitFrameTrigger(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class FrameLabelsContext extends ParserRuleContext {
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
	public get ruleIndex(): number { return SFMLParser.RULE_frameLabels; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterFrameLabels) {
			listener.enterFrameLabels(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitFrameLabels) {
			listener.exitFrameLabels(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitFrameLabels) {
			return visitor.visitFrameLabels(this);
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
	public renderImageStatement(): RenderImageStatementContext | undefined {
		return this.tryGetRuleContext(0, RenderImageStatementContext);
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


export class RenderImageStatementContext extends ParserRuleContext {
	public RENDER(): TerminalNode { return this.getToken(SFMLParser.RENDER, 0); }
	public IMAGE(): TerminalNode { return this.getToken(SFMLParser.IMAGE, 0); }
	public string(): StringContext {
		return this.getRuleContext(0, StringContext);
	}
	public TO(): TerminalNode { return this.getToken(SFMLParser.TO, 0); }
	public identifier(): IdentifierContext {
		return this.getRuleContext(0, IdentifierContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return SFMLParser.RULE_renderImageStatement; }
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterRenderImageStatement) {
			listener.enterRenderImageStatement(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitRenderImageStatement) {
			listener.exitRenderImageStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitRenderImageStatement) {
			return visitor.visitRenderImageStatement(this);
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
export class BooleanFrameModuloContext extends BoolexprContext {
	public FRAME(): TerminalNode { return this.getToken(SFMLParser.FRAME, 0); }
	public MOD(): TerminalNode { return this.getToken(SFMLParser.MOD, 0); }
	public number(): NumberContext[];
	public number(i: number): NumberContext;
	public number(i?: number): NumberContext | NumberContext[] {
		if (i === undefined) {
			return this.getRuleContexts(NumberContext);
		} else {
			return this.getRuleContext(i, NumberContext);
		}
	}
	public comparisonOp(): ComparisonOpContext {
		return this.getRuleContext(0, ComparisonOpContext);
	}
	constructor(ctx: BoolexprContext) {
		super(ctx.parent, ctx.invokingState);
		this.copyFrom(ctx);
	}
	// @Override
	public enterRule(listener: SFMLListener): void {
		if (listener.enterBooleanFrameModulo) {
			listener.enterBooleanFrameModulo(this);
		}
	}
	// @Override
	public exitRule(listener: SFMLListener): void {
		if (listener.exitBooleanFrameModulo) {
			listener.exitBooleanFrameModulo(this);
		}
	}
	// @Override
	public accept<Result>(visitor: SFMLVisitor<Result>): Result {
		if (visitor.visitBooleanFrameModulo) {
			return visitor.visitBooleanFrameModulo(this);
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
	public FRAME(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.FRAME, 0); }
	public FOR(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.FOR, 0); }
	public MOD(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.MOD, 0); }
	public RENDER(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.RENDER, 0); }
	public IMAGE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.IMAGE, 0); }
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


