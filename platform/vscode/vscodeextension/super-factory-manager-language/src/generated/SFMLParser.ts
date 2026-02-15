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
	public static readonly RETAIN = 30;
	public static readonly EACH = 31;
	public static readonly EXCEPT = 32;
	public static readonly FORGET = 33;
	public static readonly WITHOUT = 34;
	public static readonly WITH = 35;
	public static readonly TAG = 36;
	public static readonly HASHTAG = 37;
	public static readonly ROUND = 38;
	public static readonly ROBIN = 39;
	public static readonly BY = 40;
	public static readonly LABEL = 41;
	public static readonly BLOCK = 42;
	public static readonly TOP = 43;
	public static readonly BOTTOM = 44;
	public static readonly NORTH = 45;
	public static readonly EAST = 46;
	public static readonly SOUTH = 47;
	public static readonly WEST = 48;
	public static readonly SIDE = 49;
	public static readonly TICKS = 50;
	public static readonly TICK = 51;
	public static readonly SECONDS = 52;
	public static readonly SECOND = 53;
	public static readonly GLOBAL = 54;
	public static readonly PLUS = 55;
	public static readonly REDSTONE = 56;
	public static readonly PULSE = 57;
	public static readonly DO = 58;
	public static readonly END = 59;
	public static readonly NAME = 60;
	public static readonly EVERY = 61;
	public static readonly COMMA = 62;
	public static readonly COLON = 63;
	public static readonly SLASH = 64;
	public static readonly DASH = 65;
	public static readonly LPAREN = 66;
	public static readonly RPAREN = 67;
	public static readonly NUMBER_WITH_G_SUFFIX = 68;
	public static readonly NUMBER = 69;
	public static readonly IDENTIFIER = 70;
	public static readonly STRING = 71;
	public static readonly LINE_COMMENT = 72;
	public static readonly WS = 73;
	public static readonly UNUSED = 74;
	public static readonly RULE_program = 0;
	public static readonly RULE_name = 1;
	public static readonly RULE_trigger = 2;
	public static readonly RULE_interval = 3;
	public static readonly RULE_block = 4;
	public static readonly RULE_statement = 5;
	public static readonly RULE_forgetStatement = 6;
	public static readonly RULE_inputStatement = 7;
	public static readonly RULE_outputStatement = 8;
	public static readonly RULE_inputResourceLimits = 9;
	public static readonly RULE_outputResourceLimits = 10;
	public static readonly RULE_resourceLimitList = 11;
	public static readonly RULE_resourceLimit = 12;
	public static readonly RULE_limit = 13;
	public static readonly RULE_quantity = 14;
	public static readonly RULE_retention = 15;
	public static readonly RULE_resourceExclusion = 16;
	public static readonly RULE_resourceId = 17;
	public static readonly RULE_resourceIdList = 18;
	public static readonly RULE_resourceIdDisjunction = 19;
	public static readonly RULE_with = 20;
	public static readonly RULE_withClause = 21;
	public static readonly RULE_tagMatcher = 22;
	public static readonly RULE_sidequalifier = 23;
	public static readonly RULE_side = 24;
	public static readonly RULE_slotqualifier = 25;
	public static readonly RULE_rangeset = 26;
	public static readonly RULE_range = 27;
	public static readonly RULE_ifStatement = 28;
	public static readonly RULE_boolexpr = 29;
	public static readonly RULE_comparisonOp = 30;
	public static readonly RULE_setOp = 31;
	public static readonly RULE_labelAccess = 32;
	public static readonly RULE_roundrobin = 33;
	public static readonly RULE_label = 34;
	public static readonly RULE_identifier = 35;
	public static readonly RULE_string = 36;
	public static readonly RULE_number = 37;
	// tslint:disable:no-trailing-whitespace
	public static readonly ruleNames: string[] = [
		"program", "name", "trigger", "interval", "block", "statement", "forgetStatement", 
		"inputStatement", "outputStatement", "inputResourceLimits", "outputResourceLimits", 
		"resourceLimitList", "resourceLimit", "limit", "quantity", "retention", 
		"resourceExclusion", "resourceId", "resourceIdList", "resourceIdDisjunction", 
		"with", "withClause", "tagMatcher", "sidequalifier", "side", "slotqualifier", 
		"rangeset", "range", "ifStatement", "boolexpr", "comparisonOp", "setOp", 
		"labelAccess", "roundrobin", "label", "identifier", "string", "number",
	];

	private static readonly _LITERAL_NAMES: Array<string | undefined> = [
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, "'>'", undefined, "'<'", undefined, "'='", undefined, "'<='", 
		undefined, "'>='", undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, "'#'", undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, "','", "':'", "'/'", 
		"'-'", "'('", "')'",
	];
	private static readonly _SYMBOLIC_NAMES: Array<string | undefined> = [
		undefined, "IF", "THEN", "ELSE", "HAS", "OVERALL", "SOME", "ONE", "LONE", 
		"TRUE", "FALSE", "NOT", "AND", "OR", "GT", "GT_SYMBOL", "LT", "LT_SYMBOL", 
		"EQ", "EQ_SYMBOL", "LE", "LE_SYMBOL", "GE", "GE_SYMBOL", "FROM", "TO", 
		"INPUT", "OUTPUT", "WHERE", "SLOTS", "RETAIN", "EACH", "EXCEPT", "FORGET", 
		"WITHOUT", "WITH", "TAG", "HASHTAG", "ROUND", "ROBIN", "BY", "LABEL", 
		"BLOCK", "TOP", "BOTTOM", "NORTH", "EAST", "SOUTH", "WEST", "SIDE", "TICKS", 
		"TICK", "SECONDS", "SECOND", "GLOBAL", "PLUS", "REDSTONE", "PULSE", "DO", 
		"END", "NAME", "EVERY", "COMMA", "COLON", "SLASH", "DASH", "LPAREN", "RPAREN", 
		"NUMBER_WITH_G_SUFFIX", "NUMBER", "IDENTIFIER", "STRING", "LINE_COMMENT", 
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
			this.state = 77;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.NAME) {
				{
				this.state = 76;
				this.name();
				}
			}

			this.state = 82;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.EVERY) {
				{
				{
				this.state = 79;
				this.trigger();
				}
				}
				this.state = 84;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 85;
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
	public name(): NameContext {
		let _localctx: NameContext = new NameContext(this._ctx, this.state);
		this.enterRule(_localctx, 2, SFMLParser.RULE_name);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 87;
			this.match(SFMLParser.NAME);
			this.state = 88;
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
	public trigger(): TriggerContext {
		let _localctx: TriggerContext = new TriggerContext(this._ctx, this.state);
		this.enterRule(_localctx, 4, SFMLParser.RULE_trigger);
		try {
			this.state = 103;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 2, this._ctx) ) {
			case 1:
				_localctx = new TimerTriggerContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 90;
				this.match(SFMLParser.EVERY);
				this.state = 91;
				this.interval();
				this.state = 92;
				this.match(SFMLParser.DO);
				this.state = 93;
				this.block();
				this.state = 94;
				this.match(SFMLParser.END);
				}
				break;

			case 2:
				_localctx = new PulseTriggerContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 96;
				this.match(SFMLParser.EVERY);
				this.state = 97;
				this.match(SFMLParser.REDSTONE);
				this.state = 98;
				this.match(SFMLParser.PULSE);
				this.state = 99;
				this.match(SFMLParser.DO);
				this.state = 100;
				this.block();
				this.state = 101;
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
		this.enterRule(_localctx, 6, SFMLParser.RULE_interval);
		let _la: number;
		try {
			this.state = 122;
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
				this.state = 106;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.NUMBER) {
					{
					this.state = 105;
					this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 109;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.GLOBAL) {
					{
					this.state = 108;
					this.match(SFMLParser.GLOBAL);
					}
				}

				this.state = 113;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.PLUS) {
					{
					this.state = 111;
					this.match(SFMLParser.PLUS);
					this.state = 112;
					this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 115;
				_la = this._input.LA(1);
				if (!(((((_la - 50)) & ~0x1F) === 0 && ((1 << (_la - 50)) & ((1 << (SFMLParser.TICKS - 50)) | (1 << (SFMLParser.TICK - 50)) | (1 << (SFMLParser.SECONDS - 50)) | (1 << (SFMLParser.SECOND - 50)))) !== 0))) {
				this._errHandler.recoverInline(this);
				} else {
					if (this._input.LA(1) === Token.EOF) {
						this.matchedEOF = true;
					}

					this._errHandler.reportMatch(this);
					this.consume();
				}
				}
				break;
			case SFMLParser.NUMBER_WITH_G_SUFFIX:
				_localctx = new IntervalNoSpaceContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 116;
				this.match(SFMLParser.NUMBER_WITH_G_SUFFIX);
				this.state = 119;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.PLUS) {
					{
					this.state = 117;
					this.match(SFMLParser.PLUS);
					this.state = 118;
					this.match(SFMLParser.NUMBER);
					}
				}

				this.state = 121;
				_la = this._input.LA(1);
				if (!(((((_la - 50)) & ~0x1F) === 0 && ((1 << (_la - 50)) & ((1 << (SFMLParser.TICKS - 50)) | (1 << (SFMLParser.TICK - 50)) | (1 << (SFMLParser.SECONDS - 50)) | (1 << (SFMLParser.SECOND - 50)))) !== 0))) {
				this._errHandler.recoverInline(this);
				} else {
					if (this._input.LA(1) === Token.EOF) {
						this.matchedEOF = true;
					}

					this._errHandler.reportMatch(this);
					this.consume();
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
	public block(): BlockContext {
		let _localctx: BlockContext = new BlockContext(this._ctx, this.state);
		this.enterRule(_localctx, 8, SFMLParser.RULE_block);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 127;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << SFMLParser.IF) | (1 << SFMLParser.FROM) | (1 << SFMLParser.TO) | (1 << SFMLParser.INPUT) | (1 << SFMLParser.OUTPUT))) !== 0) || _la === SFMLParser.FORGET) {
				{
				{
				this.state = 124;
				this.statement();
				}
				}
				this.state = 129;
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
		this.enterRule(_localctx, 10, SFMLParser.RULE_statement);
		try {
			this.state = 134;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.FROM:
			case SFMLParser.INPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 130;
				this.inputStatement();
				}
				break;
			case SFMLParser.TO:
			case SFMLParser.OUTPUT:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 131;
				this.outputStatement();
				}
				break;
			case SFMLParser.IF:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 132;
				this.ifStatement();
				}
				break;
			case SFMLParser.FORGET:
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 133;
				this.forgetStatement();
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
	public forgetStatement(): ForgetStatementContext {
		let _localctx: ForgetStatementContext = new ForgetStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 12, SFMLParser.RULE_forgetStatement);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 136;
			this.match(SFMLParser.FORGET);
			this.state = 138;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 52)) & ~0x1F) === 0 && ((1 << (_la - 52)) & ((1 << (SFMLParser.SECONDS - 52)) | (1 << (SFMLParser.SECOND - 52)) | (1 << (SFMLParser.GLOBAL - 52)) | (1 << (SFMLParser.REDSTONE - 52)) | (1 << (SFMLParser.IDENTIFIER - 52)) | (1 << (SFMLParser.STRING - 52)))) !== 0)) {
				{
				this.state = 137;
				this.label();
				}
			}

			this.state = 144;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 11, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 140;
					this.match(SFMLParser.COMMA);
					this.state = 141;
					this.label();
					}
					}
				}
				this.state = 146;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 11, this._ctx);
			}
			this.state = 148;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.COMMA) {
				{
				this.state = 147;
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
		this.enterRule(_localctx, 14, SFMLParser.RULE_inputStatement);
		let _la: number;
		try {
			this.state = 174;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.INPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 150;
				this.match(SFMLParser.INPUT);
				this.state = 152;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 30)) & ~0x1F) === 0 && ((1 << (_la - 30)) & ((1 << (SFMLParser.RETAIN - 30)) | (1 << (SFMLParser.WITHOUT - 30)) | (1 << (SFMLParser.WITH - 30)) | (1 << (SFMLParser.SECONDS - 30)) | (1 << (SFMLParser.SECOND - 30)) | (1 << (SFMLParser.GLOBAL - 30)) | (1 << (SFMLParser.REDSTONE - 30)))) !== 0) || ((((_la - 69)) & ~0x1F) === 0 && ((1 << (_la - 69)) & ((1 << (SFMLParser.NUMBER - 69)) | (1 << (SFMLParser.IDENTIFIER - 69)) | (1 << (SFMLParser.STRING - 69)))) !== 0)) {
					{
					this.state = 151;
					this.inputResourceLimits();
					}
				}

				this.state = 155;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 154;
					this.resourceExclusion();
					}
				}

				this.state = 157;
				this.match(SFMLParser.FROM);
				this.state = 159;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 158;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 161;
				this.labelAccess();
				}
				break;
			case SFMLParser.FROM:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 162;
				this.match(SFMLParser.FROM);
				this.state = 164;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 163;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 166;
				this.labelAccess();
				this.state = 167;
				this.match(SFMLParser.INPUT);
				this.state = 169;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 30)) & ~0x1F) === 0 && ((1 << (_la - 30)) & ((1 << (SFMLParser.RETAIN - 30)) | (1 << (SFMLParser.WITHOUT - 30)) | (1 << (SFMLParser.WITH - 30)) | (1 << (SFMLParser.SECONDS - 30)) | (1 << (SFMLParser.SECOND - 30)) | (1 << (SFMLParser.GLOBAL - 30)) | (1 << (SFMLParser.REDSTONE - 30)))) !== 0) || ((((_la - 69)) & ~0x1F) === 0 && ((1 << (_la - 69)) & ((1 << (SFMLParser.NUMBER - 69)) | (1 << (SFMLParser.IDENTIFIER - 69)) | (1 << (SFMLParser.STRING - 69)))) !== 0)) {
					{
					this.state = 168;
					this.inputResourceLimits();
					}
				}

				this.state = 172;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 171;
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
	public outputStatement(): OutputStatementContext {
		let _localctx: OutputStatementContext = new OutputStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 16, SFMLParser.RULE_outputStatement);
		let _la: number;
		try {
			this.state = 200;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.OUTPUT:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 176;
				this.match(SFMLParser.OUTPUT);
				this.state = 178;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 30)) & ~0x1F) === 0 && ((1 << (_la - 30)) & ((1 << (SFMLParser.RETAIN - 30)) | (1 << (SFMLParser.WITHOUT - 30)) | (1 << (SFMLParser.WITH - 30)) | (1 << (SFMLParser.SECONDS - 30)) | (1 << (SFMLParser.SECOND - 30)) | (1 << (SFMLParser.GLOBAL - 30)) | (1 << (SFMLParser.REDSTONE - 30)))) !== 0) || ((((_la - 69)) & ~0x1F) === 0 && ((1 << (_la - 69)) & ((1 << (SFMLParser.NUMBER - 69)) | (1 << (SFMLParser.IDENTIFIER - 69)) | (1 << (SFMLParser.STRING - 69)))) !== 0)) {
					{
					this.state = 177;
					this.outputResourceLimits();
					}
				}

				this.state = 181;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 180;
					this.resourceExclusion();
					}
				}

				this.state = 183;
				this.match(SFMLParser.TO);
				this.state = 185;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 184;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 187;
				this.labelAccess();
				}
				break;
			case SFMLParser.TO:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 188;
				this.match(SFMLParser.TO);
				this.state = 190;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EACH) {
					{
					this.state = 189;
					this.match(SFMLParser.EACH);
					}
				}

				this.state = 192;
				this.labelAccess();
				this.state = 193;
				this.match(SFMLParser.OUTPUT);
				this.state = 195;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (((((_la - 30)) & ~0x1F) === 0 && ((1 << (_la - 30)) & ((1 << (SFMLParser.RETAIN - 30)) | (1 << (SFMLParser.WITHOUT - 30)) | (1 << (SFMLParser.WITH - 30)) | (1 << (SFMLParser.SECONDS - 30)) | (1 << (SFMLParser.SECOND - 30)) | (1 << (SFMLParser.GLOBAL - 30)) | (1 << (SFMLParser.REDSTONE - 30)))) !== 0) || ((((_la - 69)) & ~0x1F) === 0 && ((1 << (_la - 69)) & ((1 << (SFMLParser.NUMBER - 69)) | (1 << (SFMLParser.IDENTIFIER - 69)) | (1 << (SFMLParser.STRING - 69)))) !== 0)) {
					{
					this.state = 194;
					this.outputResourceLimits();
					}
				}

				this.state = 198;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.EXCEPT) {
					{
					this.state = 197;
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
		this.enterRule(_localctx, 18, SFMLParser.RULE_inputResourceLimits);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 202;
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
		this.enterRule(_localctx, 20, SFMLParser.RULE_outputResourceLimits);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 204;
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
		this.enterRule(_localctx, 22, SFMLParser.RULE_resourceLimitList);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 206;
			this.resourceLimit();
			this.state = 211;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 27, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 207;
					this.match(SFMLParser.COMMA);
					this.state = 208;
					this.resourceLimit();
					}
					}
				}
				this.state = 213;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 27, this._ctx);
			}
			this.state = 215;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.COMMA) {
				{
				this.state = 214;
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
		this.enterRule(_localctx, 24, SFMLParser.RULE_resourceLimit);
		let _la: number;
		try {
			this.state = 229;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 32, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 218;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.RETAIN || _la === SFMLParser.NUMBER) {
					{
					this.state = 217;
					this.limit();
					}
				}

				this.state = 220;
				this.resourceIdDisjunction();
				this.state = 222;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.WITHOUT || _la === SFMLParser.WITH) {
					{
					this.state = 221;
					this.with();
					}
				}

				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 224;
				this.limit();
				this.state = 226;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === SFMLParser.WITHOUT || _la === SFMLParser.WITH) {
					{
					this.state = 225;
					this.with();
					}
				}

				}
				break;

			case 3:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 228;
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
		this.enterRule(_localctx, 26, SFMLParser.RULE_limit);
		try {
			this.state = 236;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 33, this._ctx) ) {
			case 1:
				_localctx = new QuantityRetentionLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 231;
				this.quantity();
				this.state = 232;
				this.retention();
				}
				break;

			case 2:
				_localctx = new RetentionLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 234;
				this.retention();
				}
				break;

			case 3:
				_localctx = new QuantityLimitContext(_localctx);
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 235;
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
		this.enterRule(_localctx, 28, SFMLParser.RULE_quantity);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 238;
			this.number();
			this.state = 240;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.EACH) {
				{
				this.state = 239;
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
		this.enterRule(_localctx, 30, SFMLParser.RULE_retention);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 242;
			this.match(SFMLParser.RETAIN);
			this.state = 243;
			this.number();
			this.state = 245;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.EACH) {
				{
				this.state = 244;
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
		this.enterRule(_localctx, 32, SFMLParser.RULE_resourceExclusion);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 247;
			this.match(SFMLParser.EXCEPT);
			this.state = 248;
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
		this.enterRule(_localctx, 34, SFMLParser.RULE_resourceId);
		try {
			this.state = 270;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.SECONDS:
			case SFMLParser.SECOND:
			case SFMLParser.GLOBAL:
			case SFMLParser.REDSTONE:
			case SFMLParser.IDENTIFIER:
				_localctx = new ResourceContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				{
				this.state = 250;
				this.identifier();
				}
				this.state = 267;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 41, this._ctx) ) {
				case 1:
					{
					this.state = 251;
					this.match(SFMLParser.COLON);
					this.state = 253;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 36, this._ctx) ) {
					case 1:
						{
						this.state = 252;
						this.identifier();
						}
						break;
					}
					this.state = 265;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 40, this._ctx) ) {
					case 1:
						{
						this.state = 255;
						this.match(SFMLParser.COLON);
						this.state = 257;
						this._errHandler.sync(this);
						switch ( this.interpreter.adaptivePredict(this._input, 37, this._ctx) ) {
						case 1:
							{
							this.state = 256;
							this.identifier();
							}
							break;
						}
						this.state = 263;
						this._errHandler.sync(this);
						switch ( this.interpreter.adaptivePredict(this._input, 39, this._ctx) ) {
						case 1:
							{
							this.state = 259;
							this.match(SFMLParser.COLON);
							this.state = 261;
							this._errHandler.sync(this);
							switch ( this.interpreter.adaptivePredict(this._input, 38, this._ctx) ) {
							case 1:
								{
								this.state = 260;
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
				this.state = 269;
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
		this.enterRule(_localctx, 36, SFMLParser.RULE_resourceIdList);
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 272;
			this.resourceId();
			this.state = 277;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 43, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 273;
					this.match(SFMLParser.COMMA);
					this.state = 274;
					this.resourceId();
					}
					}
				}
				this.state = 279;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 43, this._ctx);
			}
			this.state = 281;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 44, this._ctx) ) {
			case 1:
				{
				this.state = 280;
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
		this.enterRule(_localctx, 38, SFMLParser.RULE_resourceIdDisjunction);
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 283;
			this.resourceId();
			this.state = 288;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 45, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 284;
					this.match(SFMLParser.OR);
					this.state = 285;
					this.resourceId();
					}
					}
				}
				this.state = 290;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 45, this._ctx);
			}
			this.state = 292;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 46, this._ctx) ) {
			case 1:
				{
				this.state = 291;
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
		this.enterRule(_localctx, 40, SFMLParser.RULE_with);
		try {
			this.state = 298;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.WITH:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 294;
				this.match(SFMLParser.WITH);
				this.state = 295;
				this.withClause(0);
				}
				break;
			case SFMLParser.WITHOUT:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 296;
				this.match(SFMLParser.WITHOUT);
				this.state = 297;
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
		let _startState: number = 42;
		this.enterRecursionRule(_localctx, 42, SFMLParser.RULE_withClause, _p);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 315;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.LPAREN:
				{
				_localctx = new WithParenContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;

				this.state = 301;
				this.match(SFMLParser.LPAREN);
				this.state = 302;
				this.withClause(0);
				this.state = 303;
				this.match(SFMLParser.RPAREN);
				}
				break;
			case SFMLParser.NOT:
				{
				_localctx = new WithNegationContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 305;
				this.match(SFMLParser.NOT);
				this.state = 306;
				this.withClause(4);
				}
				break;
			case SFMLParser.TAG:
			case SFMLParser.HASHTAG:
				{
				_localctx = new WithTagContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 312;
				this._errHandler.sync(this);
				switch (this._input.LA(1)) {
				case SFMLParser.TAG:
					{
					this.state = 307;
					this.match(SFMLParser.TAG);
					this.state = 309;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
					if (_la === SFMLParser.HASHTAG) {
						{
						this.state = 308;
						this.match(SFMLParser.HASHTAG);
						}
					}

					}
					break;
				case SFMLParser.HASHTAG:
					{
					this.state = 311;
					this.match(SFMLParser.HASHTAG);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				this.state = 314;
				this.tagMatcher();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			this._ctx._stop = this._input.tryLT(-1);
			this.state = 325;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 52, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					if (this._parseListeners != null) {
						this.triggerExitRuleEvent();
					}
					_prevctx = _localctx;
					{
					this.state = 323;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 51, this._ctx) ) {
					case 1:
						{
						_localctx = new WithConjunctionContext(new WithClauseContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_withClause);
						this.state = 317;
						if (!(this.precpred(this._ctx, 3))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 3)");
						}
						this.state = 318;
						this.match(SFMLParser.AND);
						this.state = 319;
						this.withClause(4);
						}
						break;

					case 2:
						{
						_localctx = new WithDisjunctionContext(new WithClauseContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_withClause);
						this.state = 320;
						if (!(this.precpred(this._ctx, 2))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 2)");
						}
						this.state = 321;
						this.match(SFMLParser.OR);
						this.state = 322;
						this.withClause(3);
						}
						break;
					}
					}
				}
				this.state = 327;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 52, this._ctx);
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
		this.enterRule(_localctx, 44, SFMLParser.RULE_tagMatcher);
		try {
			let _alt: number;
			this.state = 346;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 55, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 328;
				this.identifier();
				this.state = 329;
				this.match(SFMLParser.COLON);
				this.state = 330;
				this.identifier();
				this.state = 335;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 53, this._ctx);
				while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
					if (_alt === 1) {
						{
						{
						this.state = 331;
						this.match(SFMLParser.SLASH);
						this.state = 332;
						this.identifier();
						}
						}
					}
					this.state = 337;
					this._errHandler.sync(this);
					_alt = this.interpreter.adaptivePredict(this._input, 53, this._ctx);
				}
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 338;
				this.identifier();
				this.state = 343;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 54, this._ctx);
				while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
					if (_alt === 1) {
						{
						{
						this.state = 339;
						this.match(SFMLParser.SLASH);
						this.state = 340;
						this.identifier();
						}
						}
					}
					this.state = 345;
					this._errHandler.sync(this);
					_alt = this.interpreter.adaptivePredict(this._input, 54, this._ctx);
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
	public sidequalifier(): SidequalifierContext {
		let _localctx: SidequalifierContext = new SidequalifierContext(this._ctx, this.state);
		this.enterRule(_localctx, 46, SFMLParser.RULE_sidequalifier);
		let _la: number;
		try {
			this.state = 360;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.EACH:
				_localctx = new EachSideContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 348;
				this.match(SFMLParser.EACH);
				this.state = 349;
				this.match(SFMLParser.SIDE);
				}
				break;
			case SFMLParser.TOP:
			case SFMLParser.BOTTOM:
			case SFMLParser.NORTH:
			case SFMLParser.EAST:
			case SFMLParser.SOUTH:
			case SFMLParser.WEST:
				_localctx = new ListedSidesContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 350;
				this.side();
				this.state = 355;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === SFMLParser.COMMA) {
					{
					{
					this.state = 351;
					this.match(SFMLParser.COMMA);
					this.state = 352;
					this.side();
					}
					}
					this.state = 357;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				this.state = 358;
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
		this.enterRule(_localctx, 48, SFMLParser.RULE_side);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 362;
			_la = this._input.LA(1);
			if (!(((((_la - 43)) & ~0x1F) === 0 && ((1 << (_la - 43)) & ((1 << (SFMLParser.TOP - 43)) | (1 << (SFMLParser.BOTTOM - 43)) | (1 << (SFMLParser.NORTH - 43)) | (1 << (SFMLParser.EAST - 43)) | (1 << (SFMLParser.SOUTH - 43)) | (1 << (SFMLParser.WEST - 43)))) !== 0))) {
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
		this.enterRule(_localctx, 50, SFMLParser.RULE_slotqualifier);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 364;
			this.match(SFMLParser.SLOTS);
			this.state = 365;
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
		this.enterRule(_localctx, 52, SFMLParser.RULE_rangeset);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 367;
			this.range();
			this.state = 372;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.COMMA) {
				{
				{
				this.state = 368;
				this.match(SFMLParser.COMMA);
				this.state = 369;
				this.range();
				}
				}
				this.state = 374;
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
		this.enterRule(_localctx, 54, SFMLParser.RULE_range);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 375;
			this.number();
			this.state = 378;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.DASH) {
				{
				this.state = 376;
				this.match(SFMLParser.DASH);
				this.state = 377;
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
		this.enterRule(_localctx, 56, SFMLParser.RULE_ifStatement);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 380;
			this.match(SFMLParser.IF);
			this.state = 381;
			this.boolexpr(0);
			this.state = 382;
			this.match(SFMLParser.THEN);
			this.state = 383;
			this.block();
			this.state = 392;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 60, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					{
					{
					this.state = 384;
					this.match(SFMLParser.ELSE);
					this.state = 385;
					this.match(SFMLParser.IF);
					this.state = 386;
					this.boolexpr(0);
					this.state = 387;
					this.match(SFMLParser.THEN);
					this.state = 388;
					this.block();
					}
					}
				}
				this.state = 394;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 60, this._ctx);
			}
			this.state = 397;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.ELSE) {
				{
				this.state = 395;
				this.match(SFMLParser.ELSE);
				this.state = 396;
				this.block();
				}
			}

			this.state = 399;
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
		let _startState: number = 58;
		this.enterRecursionRule(_localctx, 58, SFMLParser.RULE_boolexpr, _p);
		let _la: number;
		try {
			let _alt: number;
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 433;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 67, this._ctx) ) {
			case 1:
				{
				_localctx = new BooleanTrueContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;

				this.state = 402;
				this.match(SFMLParser.TRUE);
				}
				break;

			case 2:
				{
				_localctx = new BooleanFalseContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 403;
				this.match(SFMLParser.FALSE);
				}
				break;

			case 3:
				{
				_localctx = new BooleanParenContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 404;
				this.match(SFMLParser.LPAREN);
				this.state = 405;
				this.boolexpr(0);
				this.state = 406;
				this.match(SFMLParser.RPAREN);
				}
				break;

			case 4:
				{
				_localctx = new BooleanNegationContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 408;
				this.match(SFMLParser.NOT);
				this.state = 409;
				this.boolexpr(5);
				}
				break;

			case 5:
				{
				_localctx = new BooleanHasContext(_localctx);
				this._ctx = _localctx;
				_prevctx = _localctx;
				this.state = 411;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << SFMLParser.OVERALL) | (1 << SFMLParser.SOME) | (1 << SFMLParser.ONE) | (1 << SFMLParser.LONE) | (1 << SFMLParser.EACH))) !== 0) || _la === SFMLParser.EVERY) {
					{
					this.state = 410;
					this.setOp();
					}
				}

				this.state = 413;
				this.labelAccess();
				this.state = 414;
				this.match(SFMLParser.HAS);
				this.state = 415;
				this.comparisonOp();
				this.state = 416;
				this.number();
				this.state = 418;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 63, this._ctx) ) {
				case 1:
					{
					this.state = 417;
					this.resourceIdDisjunction();
					}
					break;
				}
				this.state = 421;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 64, this._ctx) ) {
				case 1:
					{
					this.state = 420;
					this.with();
					}
					break;
				}
				this.state = 425;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 65, this._ctx) ) {
				case 1:
					{
					this.state = 423;
					this.match(SFMLParser.EXCEPT);
					this.state = 424;
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
				this.state = 427;
				this.match(SFMLParser.REDSTONE);
				this.state = 431;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 66, this._ctx) ) {
				case 1:
					{
					this.state = 428;
					this.comparisonOp();
					this.state = 429;
					this.number();
					}
					break;
				}
				}
				break;
			}
			this._ctx._stop = this._input.tryLT(-1);
			this.state = 443;
			this._errHandler.sync(this);
			_alt = this.interpreter.adaptivePredict(this._input, 69, this._ctx);
			while (_alt !== 2 && _alt !== ATN.INVALID_ALT_NUMBER) {
				if (_alt === 1) {
					if (this._parseListeners != null) {
						this.triggerExitRuleEvent();
					}
					_prevctx = _localctx;
					{
					this.state = 441;
					this._errHandler.sync(this);
					switch ( this.interpreter.adaptivePredict(this._input, 68, this._ctx) ) {
					case 1:
						{
						_localctx = new BooleanConjunctionContext(new BoolexprContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_boolexpr);
						this.state = 435;
						if (!(this.precpred(this._ctx, 4))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 4)");
						}
						this.state = 436;
						this.match(SFMLParser.AND);
						this.state = 437;
						this.boolexpr(5);
						}
						break;

					case 2:
						{
						_localctx = new BooleanDisjunctionContext(new BoolexprContext(_parentctx, _parentState));
						this.pushNewRecursionContext(_localctx, _startState, SFMLParser.RULE_boolexpr);
						this.state = 438;
						if (!(this.precpred(this._ctx, 3))) {
							throw this.createFailedPredicateException("this.precpred(this._ctx, 3)");
						}
						this.state = 439;
						this.match(SFMLParser.OR);
						this.state = 440;
						this.boolexpr(4);
						}
						break;
					}
					}
				}
				this.state = 445;
				this._errHandler.sync(this);
				_alt = this.interpreter.adaptivePredict(this._input, 69, this._ctx);
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
		this.enterRule(_localctx, 60, SFMLParser.RULE_comparisonOp);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 446;
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
		this.enterRule(_localctx, 62, SFMLParser.RULE_setOp);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 448;
			_la = this._input.LA(1);
			if (!((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << SFMLParser.OVERALL) | (1 << SFMLParser.SOME) | (1 << SFMLParser.ONE) | (1 << SFMLParser.LONE) | (1 << SFMLParser.EACH))) !== 0) || _la === SFMLParser.EVERY)) {
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
		this.enterRule(_localctx, 64, SFMLParser.RULE_labelAccess);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 450;
			this.label();
			this.state = 455;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === SFMLParser.COMMA) {
				{
				{
				this.state = 451;
				this.match(SFMLParser.COMMA);
				this.state = 452;
				this.label();
				}
				}
				this.state = 457;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 459;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.ROUND) {
				{
				this.state = 458;
				this.roundrobin();
				}
			}

			this.state = 462;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 31)) & ~0x1F) === 0 && ((1 << (_la - 31)) & ((1 << (SFMLParser.EACH - 31)) | (1 << (SFMLParser.TOP - 31)) | (1 << (SFMLParser.BOTTOM - 31)) | (1 << (SFMLParser.NORTH - 31)) | (1 << (SFMLParser.EAST - 31)) | (1 << (SFMLParser.SOUTH - 31)) | (1 << (SFMLParser.WEST - 31)))) !== 0)) {
				{
				this.state = 461;
				this.sidequalifier();
				}
			}

			this.state = 465;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === SFMLParser.SLOTS) {
				{
				this.state = 464;
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
		this.enterRule(_localctx, 66, SFMLParser.RULE_roundrobin);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 467;
			this.match(SFMLParser.ROUND);
			this.state = 468;
			this.match(SFMLParser.ROBIN);
			this.state = 469;
			this.match(SFMLParser.BY);
			this.state = 470;
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
		this.enterRule(_localctx, 68, SFMLParser.RULE_label);
		try {
			this.state = 474;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case SFMLParser.SECONDS:
			case SFMLParser.SECOND:
			case SFMLParser.GLOBAL:
			case SFMLParser.REDSTONE:
			case SFMLParser.IDENTIFIER:
				_localctx = new RawLabelContext(_localctx);
				this.enterOuterAlt(_localctx, 1);
				{
				{
				this.state = 472;
				this.identifier();
				}
				}
				break;
			case SFMLParser.STRING:
				_localctx = new StringLabelContext(_localctx);
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 473;
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
	public identifier(): IdentifierContext {
		let _localctx: IdentifierContext = new IdentifierContext(this._ctx, this.state);
		this.enterRule(_localctx, 70, SFMLParser.RULE_identifier);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 476;
			_la = this._input.LA(1);
			if (!(((((_la - 52)) & ~0x1F) === 0 && ((1 << (_la - 52)) & ((1 << (SFMLParser.SECONDS - 52)) | (1 << (SFMLParser.SECOND - 52)) | (1 << (SFMLParser.GLOBAL - 52)) | (1 << (SFMLParser.REDSTONE - 52)) | (1 << (SFMLParser.IDENTIFIER - 52)))) !== 0))) {
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
		this.enterRule(_localctx, 72, SFMLParser.RULE_string);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 478;
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
		this.enterRule(_localctx, 74, SFMLParser.RULE_number);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 480;
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
		case 21:
			return this.withClause_sempred(_localctx as WithClauseContext, predIndex);

		case 29:
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

	public static readonly _serializedATN: string =
		"\x03\uC91D\uCABA\u058D\uAFBA\u4F53\u0607\uEA8B\uC241\x03L\u01E5\x04\x02" +
		"\t\x02\x04\x03\t\x03\x04\x04\t\x04\x04\x05\t\x05\x04\x06\t\x06\x04\x07" +
		"\t\x07\x04\b\t\b\x04\t\t\t\x04\n\t\n\x04\v\t\v\x04\f\t\f\x04\r\t\r\x04" +
		"\x0E\t\x0E\x04\x0F\t\x0F\x04\x10\t\x10\x04\x11\t\x11\x04\x12\t\x12\x04" +
		"\x13\t\x13\x04\x14\t\x14\x04\x15\t\x15\x04\x16\t\x16\x04\x17\t\x17\x04" +
		"\x18\t\x18\x04\x19\t\x19\x04\x1A\t\x1A\x04\x1B\t\x1B\x04\x1C\t\x1C\x04" +
		"\x1D\t\x1D\x04\x1E\t\x1E\x04\x1F\t\x1F\x04 \t \x04!\t!\x04\"\t\"\x04#" +
		"\t#\x04$\t$\x04%\t%\x04&\t&\x04\'\t\'\x03\x02\x05\x02P\n\x02\x03\x02\x07" +
		"\x02S\n\x02\f\x02\x0E\x02V\v\x02\x03\x02\x03\x02\x03\x03\x03\x03\x03\x03" +
		"\x03\x04\x03\x04\x03\x04\x03\x04\x03\x04\x03\x04\x03\x04\x03\x04\x03\x04" +
		"\x03\x04\x03\x04\x03\x04\x03\x04\x05\x04j\n\x04\x03\x05\x05\x05m\n\x05" +
		"\x03\x05\x05\x05p\n\x05\x03\x05\x03\x05\x05\x05t\n\x05\x03\x05\x03\x05" +
		"\x03\x05\x03\x05\x05\x05z\n\x05\x03\x05\x05\x05}\n\x05\x03\x06\x07\x06" +
		"\x80\n\x06\f\x06\x0E\x06\x83\v\x06\x03\x07\x03\x07\x03\x07\x03\x07\x05" +
		"\x07\x89\n\x07\x03\b\x03\b\x05\b\x8D\n\b\x03\b\x03\b\x07\b\x91\n\b\f\b" +
		"\x0E\b\x94\v\b\x03\b\x05\b\x97\n\b\x03\t\x03\t\x05\t\x9B\n\t\x03\t\x05" +
		"\t\x9E\n\t\x03\t\x03\t\x05\t\xA2\n\t\x03\t\x03\t\x03\t\x05\t\xA7\n\t\x03" +
		"\t\x03\t\x03\t\x05\t\xAC\n\t\x03\t\x05\t\xAF\n\t\x05\t\xB1\n\t\x03\n\x03" +
		"\n\x05\n\xB5\n\n\x03\n\x05\n\xB8\n\n\x03\n\x03\n\x05\n\xBC\n\n\x03\n\x03" +
		"\n\x03\n\x05\n\xC1\n\n\x03\n\x03\n\x03\n\x05\n\xC6\n\n\x03\n\x05\n\xC9" +
		"\n\n\x05\n\xCB\n\n\x03\v\x03\v\x03\f\x03\f\x03\r\x03\r\x03\r\x07\r\xD4" +
		"\n\r\f\r\x0E\r\xD7\v\r\x03\r\x05\r\xDA\n\r\x03\x0E\x05\x0E\xDD\n\x0E\x03" +
		"\x0E\x03\x0E\x05\x0E\xE1\n\x0E\x03\x0E\x03\x0E\x05\x0E\xE5\n\x0E\x03\x0E" +
		"\x05\x0E\xE8\n\x0E\x03\x0F\x03\x0F\x03\x0F\x03\x0F\x03\x0F\x05\x0F\xEF" +
		"\n\x0F\x03\x10\x03\x10\x05\x10\xF3\n\x10\x03\x11\x03\x11\x03\x11\x05\x11" +
		"\xF8\n\x11\x03\x12\x03\x12\x03\x12\x03\x13\x03\x13\x03\x13\x05\x13\u0100" +
		"\n\x13\x03\x13\x03\x13\x05\x13\u0104\n\x13\x03\x13\x03\x13\x05\x13\u0108" +
		"\n\x13\x05\x13\u010A\n\x13\x05\x13\u010C\n\x13\x05\x13\u010E\n\x13\x03" +
		"\x13\x05\x13\u0111\n\x13\x03\x14\x03\x14\x03\x14\x07\x14\u0116\n\x14\f" +
		"\x14\x0E\x14\u0119\v\x14\x03\x14\x05\x14\u011C\n\x14\x03\x15\x03\x15\x03" +
		"\x15\x07\x15\u0121\n\x15\f\x15\x0E\x15\u0124\v\x15\x03\x15\x05\x15\u0127" +
		"\n\x15\x03\x16\x03\x16\x03\x16\x03\x16\x05\x16\u012D\n\x16\x03\x17\x03" +
		"\x17\x03\x17\x03\x17\x03\x17\x03\x17\x03\x17\x03\x17\x03\x17\x05\x17\u0138" +
		"\n\x17\x03\x17\x05\x17\u013B\n\x17\x03\x17\x05\x17\u013E\n\x17\x03\x17" +
		"\x03\x17\x03\x17\x03\x17\x03\x17\x03\x17\x07\x17\u0146\n\x17\f\x17\x0E" +
		"\x17\u0149\v\x17\x03\x18\x03\x18\x03\x18\x03\x18\x03\x18\x07\x18\u0150" +
		"\n\x18\f\x18\x0E\x18\u0153\v\x18\x03\x18\x03\x18\x03\x18\x07\x18\u0158" +
		"\n\x18\f\x18\x0E\x18\u015B\v\x18\x05\x18\u015D\n\x18\x03\x19\x03\x19\x03" +
		"\x19\x03\x19\x03\x19\x07\x19\u0164\n\x19\f\x19\x0E\x19\u0167\v\x19\x03" +
		"\x19\x03\x19\x05\x19\u016B\n\x19\x03\x1A\x03\x1A\x03\x1B\x03\x1B\x03\x1B" +
		"\x03\x1C\x03\x1C\x03\x1C\x07\x1C\u0175\n\x1C\f\x1C\x0E\x1C\u0178\v\x1C" +
		"\x03\x1D\x03\x1D\x03\x1D\x05\x1D\u017D\n\x1D\x03\x1E\x03\x1E\x03\x1E\x03" +
		"\x1E\x03\x1E\x03\x1E\x03\x1E\x03\x1E\x03\x1E\x03\x1E\x07\x1E\u0189\n\x1E" +
		"\f\x1E\x0E\x1E\u018C\v\x1E\x03\x1E\x03\x1E\x05\x1E\u0190\n\x1E\x03\x1E" +
		"\x03\x1E\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F" +
		"\x03\x1F\x03\x1F\x05\x1F\u019E\n\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x03" +
		"\x1F\x05\x1F\u01A5\n\x1F\x03\x1F\x05\x1F\u01A8\n\x1F\x03\x1F\x03\x1F\x05" +
		"\x1F\u01AC\n\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x05\x1F\u01B2\n\x1F\x05" +
		"\x1F\u01B4\n\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x03\x1F\x07\x1F" +
		"\u01BC\n\x1F\f\x1F\x0E\x1F\u01BF\v\x1F\x03 \x03 \x03!\x03!\x03\"\x03\"" +
		"\x03\"\x07\"\u01C8\n\"\f\"\x0E\"\u01CB\v\"\x03\"\x05\"\u01CE\n\"\x03\"" +
		"\x05\"\u01D1\n\"\x03\"\x05\"\u01D4\n\"\x03#\x03#\x03#\x03#\x03#\x03$\x03" +
		"$\x05$\u01DD\n$\x03%\x03%\x03&\x03&\x03\'\x03\'\x03\'\x02\x02\x04,<(\x02" +
		"\x02\x04\x02\x06\x02\b\x02\n\x02\f\x02\x0E\x02\x10\x02\x12\x02\x14\x02" +
		"\x16\x02\x18\x02\x1A\x02\x1C\x02\x1E\x02 \x02\"\x02$\x02&\x02(\x02*\x02" +
		",\x02.\x020\x022\x024\x026\x028\x02:\x02<\x02>\x02@\x02B\x02D\x02F\x02" +
		"H\x02J\x02L\x02\x02\b\x03\x0247\x03\x02-2\x03\x02\x10\x19\x05\x02\x07" +
		"\n!!??\x03\x02+,\x05\x0268::HH\x02\u0212\x02O\x03\x02\x02\x02\x04Y\x03" +
		"\x02\x02\x02\x06i\x03\x02\x02\x02\b|\x03\x02\x02\x02\n\x81\x03\x02\x02" +
		"\x02\f\x88\x03\x02\x02\x02\x0E\x8A\x03\x02\x02\x02\x10\xB0\x03\x02\x02" +
		"\x02\x12\xCA\x03\x02\x02\x02\x14\xCC\x03\x02\x02\x02\x16\xCE\x03\x02\x02" +
		"\x02\x18\xD0\x03\x02\x02\x02\x1A\xE7\x03\x02\x02\x02\x1C\xEE\x03\x02\x02" +
		"\x02\x1E\xF0\x03\x02\x02\x02 \xF4\x03\x02\x02\x02\"\xF9\x03\x02\x02\x02" +
		"$\u0110\x03\x02\x02\x02&\u0112\x03\x02\x02\x02(\u011D\x03\x02\x02\x02" +
		"*\u012C\x03\x02\x02\x02,\u013D\x03\x02\x02\x02.\u015C\x03\x02\x02\x02" +
		"0\u016A\x03\x02\x02\x022\u016C\x03\x02\x02\x024\u016E\x03\x02\x02\x02" +
		"6\u0171\x03\x02\x02\x028\u0179\x03\x02\x02\x02:\u017E\x03\x02\x02\x02" +
		"<\u01B3\x03\x02\x02\x02>\u01C0\x03\x02\x02\x02@\u01C2\x03\x02\x02\x02" +
		"B\u01C4\x03\x02\x02\x02D\u01D5\x03\x02\x02\x02F\u01DC\x03\x02\x02\x02" +
		"H\u01DE\x03\x02\x02\x02J\u01E0\x03\x02\x02\x02L\u01E2\x03\x02\x02\x02" +
		"NP\x05\x04\x03\x02ON\x03\x02\x02\x02OP\x03\x02\x02\x02PT\x03\x02\x02\x02" +
		"QS\x05\x06\x04\x02RQ\x03\x02\x02\x02SV\x03\x02\x02\x02TR\x03\x02\x02\x02" +
		"TU\x03\x02\x02\x02UW\x03\x02\x02\x02VT\x03\x02\x02\x02WX\x07\x02\x02\x03" +
		"X\x03\x03\x02\x02\x02YZ\x07>\x02\x02Z[\x05J&\x02[\x05\x03\x02\x02\x02" +
		"\\]\x07?\x02\x02]^\x05\b\x05\x02^_\x07<\x02\x02_`\x05\n\x06\x02`a\x07" +
		"=\x02\x02aj\x03\x02\x02\x02bc\x07?\x02\x02cd\x07:\x02\x02de\x07;\x02\x02" +
		"ef\x07<\x02\x02fg\x05\n\x06\x02gh\x07=\x02\x02hj\x03\x02\x02\x02i\\\x03" +
		"\x02\x02\x02ib\x03\x02\x02\x02j\x07\x03\x02\x02\x02km\x07G\x02\x02lk\x03" +
		"\x02\x02\x02lm\x03\x02\x02\x02mo\x03\x02\x02\x02np\x078\x02\x02on\x03" +
		"\x02\x02\x02op\x03\x02\x02\x02ps\x03\x02\x02\x02qr\x079\x02\x02rt\x07" +
		"G\x02\x02sq\x03\x02\x02\x02st\x03\x02\x02\x02tu\x03\x02\x02\x02u}\t\x02" +
		"\x02\x02vy\x07F\x02\x02wx\x079\x02\x02xz\x07G\x02\x02yw\x03\x02\x02\x02" +
		"yz\x03\x02\x02\x02z{\x03\x02\x02\x02{}\t\x02\x02\x02|l\x03\x02\x02\x02" +
		"|v\x03\x02\x02\x02}\t\x03\x02\x02\x02~\x80\x05\f\x07\x02\x7F~\x03\x02" +
		"\x02\x02\x80\x83\x03\x02\x02\x02\x81\x7F\x03\x02\x02\x02\x81\x82\x03\x02" +
		"\x02\x02\x82\v\x03\x02\x02\x02\x83\x81\x03\x02\x02\x02\x84\x89\x05\x10" +
		"\t\x02\x85\x89\x05\x12\n\x02\x86\x89\x05:\x1E\x02\x87\x89\x05\x0E\b\x02" +
		"\x88\x84\x03\x02\x02\x02\x88\x85\x03\x02\x02\x02\x88\x86\x03\x02\x02\x02" +
		"\x88\x87\x03\x02\x02\x02\x89\r\x03\x02\x02\x02\x8A\x8C\x07#\x02\x02\x8B" +
		"\x8D\x05F$\x02\x8C\x8B\x03\x02\x02\x02\x8C\x8D\x03\x02\x02\x02\x8D\x92" +
		"\x03\x02\x02\x02\x8E\x8F\x07@\x02\x02\x8F\x91\x05F$\x02\x90\x8E\x03\x02" +
		"\x02\x02\x91\x94\x03\x02\x02\x02\x92\x90\x03\x02\x02\x02\x92\x93\x03\x02" +
		"\x02\x02\x93\x96\x03\x02\x02\x02\x94\x92\x03\x02\x02\x02\x95\x97\x07@" +
		"\x02\x02\x96\x95\x03\x02\x02\x02\x96\x97\x03\x02\x02\x02\x97\x0F\x03\x02" +
		"\x02\x02\x98\x9A\x07\x1C\x02\x02\x99\x9B\x05\x14\v\x02\x9A\x99\x03\x02" +
		"\x02\x02\x9A\x9B\x03\x02\x02\x02\x9B\x9D\x03\x02\x02\x02\x9C\x9E\x05\"" +
		"\x12\x02\x9D\x9C\x03\x02\x02\x02\x9D\x9E\x03\x02\x02\x02\x9E\x9F\x03\x02" +
		"\x02\x02\x9F\xA1\x07\x1A\x02\x02\xA0\xA2\x07!\x02\x02\xA1\xA0\x03\x02" +
		"\x02\x02\xA1\xA2\x03\x02\x02\x02\xA2\xA3\x03\x02\x02\x02\xA3\xB1\x05B" +
		"\"\x02\xA4\xA6\x07\x1A\x02\x02\xA5\xA7\x07!\x02\x02\xA6\xA5\x03\x02\x02" +
		"\x02\xA6\xA7\x03\x02\x02\x02\xA7\xA8\x03\x02\x02\x02\xA8\xA9\x05B\"\x02" +
		"\xA9\xAB\x07\x1C\x02\x02\xAA\xAC\x05\x14\v\x02\xAB\xAA\x03\x02\x02\x02" +
		"\xAB\xAC\x03\x02\x02\x02\xAC\xAE\x03\x02\x02\x02\xAD\xAF\x05\"\x12\x02" +
		"\xAE\xAD\x03\x02\x02\x02\xAE\xAF\x03\x02\x02\x02\xAF\xB1\x03\x02\x02\x02" +
		"\xB0\x98\x03\x02\x02\x02\xB0\xA4\x03\x02\x02\x02\xB1\x11\x03\x02\x02\x02" +
		"\xB2\xB4\x07\x1D\x02\x02\xB3\xB5\x05\x16\f\x02\xB4\xB3\x03\x02\x02\x02" +
		"\xB4\xB5\x03\x02\x02\x02\xB5\xB7\x03\x02\x02\x02\xB6\xB8\x05\"\x12\x02" +
		"\xB7\xB6\x03\x02\x02\x02\xB7\xB8\x03\x02\x02\x02\xB8\xB9\x03\x02\x02\x02" +
		"\xB9\xBB\x07\x1B\x02\x02\xBA\xBC\x07!\x02\x02\xBB\xBA\x03\x02\x02\x02" +
		"\xBB\xBC\x03\x02\x02\x02\xBC\xBD\x03\x02\x02\x02\xBD\xCB\x05B\"\x02\xBE" +
		"\xC0\x07\x1B\x02\x02\xBF\xC1\x07!\x02\x02\xC0\xBF\x03\x02\x02\x02\xC0" +
		"\xC1\x03\x02\x02\x02\xC1\xC2\x03\x02\x02\x02\xC2\xC3\x05B\"\x02\xC3\xC5" +
		"\x07\x1D\x02\x02\xC4\xC6\x05\x16\f\x02\xC5\xC4\x03\x02\x02\x02\xC5\xC6" +
		"\x03\x02\x02\x02\xC6\xC8\x03\x02\x02\x02\xC7\xC9\x05\"\x12\x02\xC8\xC7" +
		"\x03\x02\x02\x02\xC8\xC9\x03\x02\x02\x02\xC9\xCB\x03\x02\x02\x02\xCA\xB2" +
		"\x03\x02\x02\x02\xCA\xBE\x03\x02\x02\x02\xCB\x13\x03\x02\x02\x02\xCC\xCD" +
		"\x05\x18\r\x02\xCD\x15\x03\x02\x02\x02\xCE\xCF\x05\x18\r\x02\xCF\x17\x03" +
		"\x02\x02\x02\xD0\xD5\x05\x1A\x0E\x02\xD1\xD2\x07@\x02\x02\xD2\xD4\x05" +
		"\x1A\x0E\x02\xD3\xD1\x03\x02\x02\x02\xD4\xD7\x03\x02\x02\x02\xD5\xD3\x03" +
		"\x02\x02\x02\xD5\xD6\x03\x02\x02\x02\xD6\xD9\x03\x02\x02\x02\xD7\xD5\x03" +
		"\x02\x02\x02\xD8\xDA\x07@\x02\x02\xD9\xD8\x03\x02\x02\x02\xD9\xDA\x03" +
		"\x02\x02\x02\xDA\x19\x03\x02\x02\x02\xDB\xDD\x05\x1C\x0F\x02\xDC\xDB\x03" +
		"\x02\x02\x02\xDC\xDD\x03\x02\x02\x02\xDD\xDE\x03\x02\x02\x02\xDE\xE0\x05" +
		"(\x15\x02\xDF\xE1\x05*\x16\x02\xE0\xDF\x03\x02\x02\x02\xE0\xE1\x03\x02" +
		"\x02\x02\xE1\xE8\x03\x02\x02\x02\xE2\xE4\x05\x1C\x0F\x02\xE3\xE5\x05*" +
		"\x16\x02\xE4\xE3\x03\x02\x02\x02\xE4\xE5\x03\x02\x02\x02\xE5\xE8\x03\x02" +
		"\x02\x02\xE6\xE8\x05*\x16\x02\xE7\xDC\x03\x02\x02\x02\xE7\xE2\x03\x02" +
		"\x02\x02\xE7\xE6\x03\x02\x02\x02\xE8\x1B\x03\x02\x02\x02\xE9\xEA\x05\x1E" +
		"\x10\x02\xEA\xEB\x05 \x11\x02\xEB\xEF\x03\x02\x02\x02\xEC\xEF\x05 \x11" +
		"\x02\xED\xEF\x05\x1E\x10\x02\xEE\xE9\x03\x02\x02\x02\xEE\xEC\x03\x02\x02" +
		"\x02\xEE\xED\x03\x02\x02\x02\xEF\x1D\x03\x02\x02\x02\xF0\xF2\x05L\'\x02" +
		"\xF1\xF3\x07!\x02\x02\xF2\xF1\x03\x02\x02\x02\xF2\xF3\x03\x02\x02\x02" +
		"\xF3\x1F\x03\x02\x02\x02\xF4\xF5\x07 \x02\x02\xF5\xF7\x05L\'\x02\xF6\xF8" +
		"\x07!\x02\x02\xF7\xF6\x03\x02\x02\x02\xF7\xF8\x03\x02\x02\x02\xF8!\x03" +
		"\x02\x02\x02\xF9\xFA\x07\"\x02\x02\xFA\xFB\x05&\x14\x02\xFB#\x03\x02\x02" +
		"\x02\xFC\u010D\x05H%\x02\xFD\xFF\x07A\x02\x02\xFE\u0100\x05H%\x02\xFF" +
		"\xFE\x03\x02\x02\x02\xFF\u0100\x03\x02\x02\x02\u0100\u010B\x03\x02\x02" +
		"\x02\u0101\u0103\x07A\x02\x02\u0102\u0104\x05H%\x02\u0103\u0102\x03\x02" +
		"\x02\x02\u0103\u0104\x03\x02\x02\x02\u0104\u0109\x03\x02\x02\x02\u0105" +
		"\u0107\x07A\x02\x02\u0106\u0108\x05H%\x02\u0107\u0106\x03\x02\x02\x02" +
		"\u0107\u0108\x03\x02\x02\x02\u0108\u010A\x03\x02\x02\x02\u0109\u0105\x03" +
		"\x02\x02\x02\u0109\u010A\x03\x02\x02\x02\u010A\u010C\x03\x02\x02\x02\u010B" +
		"\u0101\x03\x02\x02\x02\u010B\u010C\x03\x02\x02\x02\u010C\u010E\x03\x02" +
		"\x02\x02\u010D\xFD\x03\x02\x02\x02\u010D\u010E\x03\x02\x02\x02\u010E\u0111" +
		"\x03\x02\x02\x02\u010F\u0111\x05J&\x02\u0110\xFC\x03\x02\x02\x02\u0110" +
		"\u010F\x03\x02\x02\x02\u0111%\x03\x02\x02\x02\u0112\u0117\x05$\x13\x02" +
		"\u0113\u0114\x07@\x02\x02\u0114\u0116\x05$\x13\x02\u0115\u0113\x03\x02" +
		"\x02\x02\u0116\u0119\x03\x02\x02\x02\u0117\u0115\x03\x02\x02\x02\u0117" +
		"\u0118\x03\x02\x02\x02\u0118\u011B\x03\x02\x02\x02\u0119\u0117\x03\x02" +
		"\x02\x02\u011A\u011C\x07@\x02\x02\u011B\u011A\x03\x02\x02\x02\u011B\u011C" +
		"\x03\x02\x02\x02\u011C\'\x03\x02\x02\x02\u011D\u0122\x05$\x13\x02\u011E" +
		"\u011F\x07\x0F\x02\x02\u011F\u0121\x05$\x13\x02\u0120\u011E\x03\x02\x02" +
		"\x02\u0121\u0124\x03\x02\x02\x02\u0122\u0120\x03\x02\x02\x02\u0122\u0123" +
		"\x03\x02\x02\x02\u0123\u0126\x03\x02\x02\x02\u0124\u0122\x03\x02\x02\x02" +
		"\u0125\u0127\x07\x0F\x02\x02\u0126\u0125\x03\x02\x02\x02\u0126\u0127\x03" +
		"\x02\x02\x02\u0127)\x03\x02\x02\x02\u0128\u0129\x07%\x02\x02\u0129\u012D" +
		"\x05,\x17\x02\u012A\u012B\x07$\x02\x02\u012B\u012D\x05,\x17\x02\u012C" +
		"\u0128\x03\x02\x02\x02\u012C\u012A\x03\x02\x02\x02\u012D+\x03\x02\x02" +
		"\x02\u012E\u012F\b\x17\x01\x02\u012F\u0130\x07D\x02\x02\u0130\u0131\x05" +
		",\x17\x02\u0131\u0132\x07E\x02\x02\u0132\u013E\x03\x02\x02\x02\u0133\u0134" +
		"\x07\r\x02\x02\u0134\u013E\x05,\x17\x06\u0135\u0137\x07&\x02\x02\u0136" +
		"\u0138\x07\'\x02\x02\u0137\u0136\x03\x02\x02\x02\u0137\u0138\x03\x02\x02" +
		"\x02\u0138\u013B\x03\x02\x02\x02\u0139\u013B\x07\'\x02\x02\u013A\u0135" +
		"\x03\x02\x02\x02\u013A\u0139\x03\x02\x02\x02\u013B\u013C\x03\x02\x02\x02" +
		"\u013C\u013E\x05.\x18\x02\u013D\u012E\x03\x02\x02\x02\u013D\u0133\x03" +
		"\x02\x02\x02\u013D\u013A\x03\x02\x02\x02\u013E\u0147\x03\x02\x02\x02\u013F" +
		"\u0140\f\x05\x02\x02\u0140\u0141\x07\x0E\x02\x02\u0141\u0146\x05,\x17" +
		"\x06\u0142\u0143\f\x04\x02\x02\u0143\u0144\x07\x0F\x02\x02\u0144\u0146" +
		"\x05,\x17\x05\u0145\u013F\x03\x02\x02\x02\u0145\u0142\x03\x02\x02\x02" +
		"\u0146\u0149\x03\x02\x02\x02\u0147\u0145\x03\x02\x02\x02\u0147\u0148\x03" +
		"\x02\x02\x02\u0148-\x03\x02\x02\x02\u0149\u0147\x03\x02\x02\x02\u014A" +
		"\u014B\x05H%\x02\u014B\u014C\x07A\x02\x02\u014C\u0151\x05H%\x02\u014D" +
		"\u014E\x07B\x02\x02\u014E\u0150\x05H%\x02\u014F\u014D\x03\x02\x02\x02" +
		"\u0150\u0153\x03\x02\x02\x02\u0151\u014F\x03\x02\x02\x02\u0151\u0152\x03" +
		"\x02\x02\x02\u0152\u015D\x03\x02\x02\x02\u0153\u0151\x03\x02\x02\x02\u0154" +
		"\u0159\x05H%\x02\u0155\u0156\x07B\x02\x02\u0156\u0158\x05H%\x02\u0157" +
		"\u0155\x03\x02\x02\x02\u0158\u015B\x03\x02\x02\x02\u0159\u0157\x03\x02" +
		"\x02\x02\u0159\u015A\x03\x02\x02\x02\u015A\u015D\x03\x02\x02\x02\u015B" +
		"\u0159\x03\x02\x02\x02\u015C\u014A\x03\x02\x02\x02\u015C\u0154\x03\x02" +
		"\x02\x02\u015D/\x03\x02\x02\x02\u015E\u015F\x07!\x02\x02\u015F\u016B\x07" +
		"3\x02\x02\u0160\u0165\x052\x1A\x02\u0161\u0162\x07@\x02\x02\u0162\u0164" +
		"\x052\x1A\x02\u0163\u0161\x03\x02\x02\x02\u0164\u0167\x03\x02\x02\x02" +
		"\u0165\u0163\x03\x02\x02\x02\u0165\u0166\x03\x02\x02\x02\u0166\u0168\x03" +
		"\x02\x02\x02\u0167\u0165\x03\x02\x02\x02\u0168\u0169\x073\x02\x02\u0169" +
		"\u016B\x03\x02\x02\x02\u016A\u015E\x03\x02\x02\x02\u016A\u0160\x03\x02" +
		"\x02\x02\u016B1\x03\x02\x02\x02\u016C\u016D\t\x03\x02\x02\u016D3\x03\x02" +
		"\x02\x02\u016E\u016F\x07\x1F\x02\x02\u016F\u0170\x056\x1C\x02\u01705\x03" +
		"\x02\x02\x02\u0171\u0176\x058\x1D\x02\u0172\u0173\x07@\x02\x02\u0173\u0175" +
		"\x058\x1D\x02\u0174\u0172\x03\x02\x02\x02\u0175\u0178\x03\x02\x02\x02" +
		"\u0176\u0174\x03\x02\x02\x02\u0176\u0177\x03\x02\x02\x02\u01777\x03\x02" +
		"\x02\x02\u0178\u0176\x03\x02\x02\x02\u0179\u017C\x05L\'\x02\u017A\u017B" +
		"\x07C\x02\x02\u017B\u017D\x05L\'\x02\u017C\u017A\x03\x02\x02\x02\u017C" +
		"\u017D\x03\x02\x02\x02\u017D9\x03\x02\x02\x02\u017E\u017F\x07\x03\x02" +
		"\x02\u017F\u0180\x05<\x1F\x02\u0180\u0181\x07\x04\x02\x02\u0181\u018A" +
		"\x05\n\x06\x02\u0182\u0183\x07\x05\x02\x02\u0183\u0184\x07\x03\x02\x02" +
		"\u0184\u0185\x05<\x1F\x02\u0185\u0186\x07\x04\x02\x02\u0186\u0187\x05" +
		"\n\x06\x02\u0187\u0189\x03\x02\x02\x02\u0188\u0182\x03\x02\x02\x02\u0189" +
		"\u018C\x03\x02\x02\x02\u018A\u0188\x03\x02\x02\x02\u018A\u018B\x03\x02" +
		"\x02\x02\u018B\u018F\x03\x02\x02\x02\u018C\u018A\x03\x02\x02\x02\u018D" +
		"\u018E\x07\x05\x02\x02\u018E\u0190\x05\n\x06\x02\u018F\u018D\x03\x02\x02" +
		"\x02\u018F\u0190\x03\x02\x02\x02\u0190\u0191\x03\x02\x02\x02\u0191\u0192" +
		"\x07=\x02\x02\u0192;\x03\x02\x02\x02\u0193\u0194\b\x1F\x01\x02\u0194\u01B4" +
		"\x07\v\x02\x02\u0195\u01B4\x07\f\x02\x02\u0196\u0197\x07D\x02\x02\u0197" +
		"\u0198\x05<\x1F\x02\u0198\u0199\x07E\x02\x02\u0199\u01B4\x03\x02\x02\x02" +
		"\u019A\u019B\x07\r\x02\x02\u019B\u01B4\x05<\x1F\x07\u019C\u019E\x05@!" +
		"\x02\u019D\u019C\x03\x02\x02\x02\u019D\u019E\x03\x02\x02\x02\u019E\u019F" +
		"\x03\x02\x02\x02\u019F\u01A0\x05B\"\x02\u01A0\u01A1\x07\x06\x02\x02\u01A1" +
		"\u01A2\x05> \x02\u01A2\u01A4\x05L\'\x02\u01A3\u01A5\x05(\x15\x02\u01A4" +
		"\u01A3\x03\x02\x02\x02\u01A4\u01A5\x03\x02\x02\x02\u01A5\u01A7\x03\x02" +
		"\x02\x02\u01A6\u01A8\x05*\x16\x02\u01A7\u01A6\x03\x02\x02\x02\u01A7\u01A8" +
		"\x03\x02\x02\x02\u01A8\u01AB\x03\x02\x02\x02\u01A9\u01AA\x07\"\x02\x02" +
		"\u01AA\u01AC\x05&\x14\x02\u01AB\u01A9\x03\x02\x02\x02\u01AB\u01AC\x03" +
		"\x02\x02\x02\u01AC\u01B4\x03\x02\x02\x02\u01AD\u01B1\x07:\x02\x02\u01AE" +
		"\u01AF\x05> \x02\u01AF\u01B0\x05L\'\x02\u01B0\u01B2\x03\x02\x02\x02\u01B1" +
		"\u01AE\x03\x02\x02\x02\u01B1\u01B2\x03\x02\x02\x02\u01B2\u01B4\x03\x02" +
		"\x02\x02\u01B3\u0193\x03\x02\x02\x02\u01B3\u0195\x03\x02\x02\x02\u01B3" +
		"\u0196\x03\x02\x02\x02\u01B3\u019A\x03\x02\x02\x02\u01B3\u019D\x03\x02" +
		"\x02\x02\u01B3\u01AD\x03\x02\x02\x02\u01B4\u01BD\x03\x02\x02\x02\u01B5" +
		"\u01B6\f\x06\x02\x02\u01B6\u01B7\x07\x0E\x02\x02\u01B7\u01BC\x05<\x1F" +
		"\x07\u01B8\u01B9\f\x05\x02\x02\u01B9\u01BA\x07\x0F\x02\x02\u01BA\u01BC" +
		"\x05<\x1F\x06\u01BB\u01B5\x03\x02\x02\x02\u01BB\u01B8\x03\x02\x02\x02" +
		"\u01BC\u01BF\x03\x02\x02\x02\u01BD\u01BB\x03\x02\x02\x02\u01BD\u01BE\x03" +
		"\x02\x02\x02\u01BE=\x03\x02\x02\x02\u01BF\u01BD\x03\x02\x02\x02\u01C0" +
		"\u01C1\t\x04\x02\x02\u01C1?\x03\x02\x02\x02\u01C2\u01C3\t\x05\x02\x02" +
		"\u01C3A\x03\x02\x02\x02\u01C4\u01C9\x05F$\x02\u01C5\u01C6\x07@\x02\x02" +
		"\u01C6\u01C8\x05F$\x02\u01C7\u01C5\x03\x02\x02\x02\u01C8\u01CB\x03\x02" +
		"\x02\x02\u01C9\u01C7\x03\x02\x02\x02\u01C9\u01CA\x03\x02\x02\x02\u01CA" +
		"\u01CD\x03\x02\x02\x02\u01CB\u01C9\x03\x02\x02\x02\u01CC\u01CE\x05D#\x02" +
		"\u01CD\u01CC\x03\x02\x02\x02\u01CD\u01CE\x03\x02\x02\x02\u01CE\u01D0\x03" +
		"\x02\x02\x02\u01CF\u01D1\x050\x19\x02\u01D0\u01CF\x03\x02\x02\x02\u01D0" +
		"\u01D1\x03\x02\x02\x02\u01D1\u01D3\x03\x02\x02\x02\u01D2\u01D4\x054\x1B" +
		"\x02\u01D3\u01D2\x03\x02\x02\x02\u01D3\u01D4\x03\x02\x02\x02\u01D4C\x03" +
		"\x02\x02\x02\u01D5\u01D6\x07(\x02\x02\u01D6\u01D7\x07)\x02\x02\u01D7\u01D8" +
		"\x07*\x02\x02\u01D8\u01D9\t\x06\x02\x02\u01D9E\x03\x02\x02\x02\u01DA\u01DD" +
		"\x05H%\x02\u01DB\u01DD\x05J&\x02\u01DC\u01DA\x03\x02\x02\x02\u01DC\u01DB" +
		"\x03\x02\x02\x02\u01DDG\x03\x02\x02\x02\u01DE\u01DF\t\x07\x02\x02\u01DF" +
		"I\x03\x02\x02\x02\u01E0\u01E1\x07I\x02\x02\u01E1K\x03\x02\x02\x02\u01E2" +
		"\u01E3\x07G\x02\x02\u01E3M\x03\x02\x02\x02MOTilosy|\x81\x88\x8C\x92\x96" +
		"\x9A\x9D\xA1\xA6\xAB\xAE\xB0\xB4\xB7\xBB\xC0\xC5\xC8\xCA\xD5\xD9\xDC\xE0" +
		"\xE4\xE7\xEE\xF2\xF7\xFF\u0103\u0107\u0109\u010B\u010D\u0110\u0117\u011B" +
		"\u0122\u0126\u012C\u0137\u013A\u013D\u0145\u0147\u0151\u0159\u015C\u0165" +
		"\u016A\u0176\u017C\u018A\u018F\u019D\u01A4\u01A7\u01AB\u01B1\u01B3\u01BB" +
		"\u01BD\u01C9\u01CD\u01D0\u01D3\u01DC";
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
	public name(): NameContext | undefined {
		return this.tryGetRuleContext(0, NameContext);
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
	public TICKS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TICKS, 0); }
	public TICK(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TICK, 0); }
	public SECONDS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECONDS, 0); }
	public SECOND(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECOND, 0); }
	public NUMBER(): TerminalNode[];
	public NUMBER(i: number): TerminalNode;
	public NUMBER(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(SFMLParser.NUMBER);
		} else {
			return this.getToken(SFMLParser.NUMBER, i);
		}
	}
	public GLOBAL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GLOBAL, 0); }
	public PLUS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.PLUS, 0); }
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
	public NUMBER_WITH_G_SUFFIX(): TerminalNode { return this.getToken(SFMLParser.NUMBER_WITH_G_SUFFIX, 0); }
	public TICKS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TICKS, 0); }
	public TICK(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.TICK, 0); }
	public SECONDS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECONDS, 0); }
	public SECOND(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECOND, 0); }
	public PLUS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.PLUS, 0); }
	public NUMBER(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.NUMBER, 0); }
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
	public inputResourceLimits(): InputResourceLimitsContext | undefined {
		return this.tryGetRuleContext(0, InputResourceLimitsContext);
	}
	public resourceExclusion(): ResourceExclusionContext | undefined {
		return this.tryGetRuleContext(0, ResourceExclusionContext);
	}
	public EACH(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.EACH, 0); }
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
	public SLOTS(): TerminalNode { return this.getToken(SFMLParser.SLOTS, 0); }
	public rangeset(): RangesetContext {
		return this.getRuleContext(0, RangesetContext);
	}
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


export class IdentifierContext extends ParserRuleContext {
	public IDENTIFIER(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.IDENTIFIER, 0); }
	public REDSTONE(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.REDSTONE, 0); }
	public GLOBAL(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.GLOBAL, 0); }
	public SECOND(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECOND, 0); }
	public SECONDS(): TerminalNode | undefined { return this.tryGetToken(SFMLParser.SECONDS, 0); }
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


