// Generated from d:/Repos/Minecraft/SFM/repos2/1.19.2/platform/minecraft/src/main/antlr/sfml/SFML.g4 by ANTLR 4.13.1

package ca.teamdman.langs;

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class SFMLParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		IF=1, THEN=2, ELSE=3, HAS=4, OVERALL=5, SOME=6, ONE=7, LONE=8, TRUE=9, 
		FALSE=10, NOT=11, AND=12, OR=13, GT=14, GT_SYMBOL=15, LT=16, LT_SYMBOL=17, 
		EQ=18, EQ_SYMBOL=19, LE=20, LE_SYMBOL=21, GE=22, GE_SYMBOL=23, FROM=24, 
		TO=25, INPUT=26, OUTPUT=27, WHERE=28, SLOTS=29, SLOT=30, RETAIN=31, EACH=32, 
		EXCEPT=33, FORGET=34, EMPTY=35, IN=36, WITHOUT=37, WITH=38, TAG=39, HASHTAG=40, 
		ROUND=41, ROBIN=42, BY=43, LABEL=44, BLOCK=45, TOP=46, BOTTOM=47, NORTH=48, 
		EAST=49, SOUTH=50, WEST=51, SIDE=52, LEFT=53, RIGHT=54, FRONT=55, BACK=56, 
		NULL=57, TICKS=58, TICK=59, SECONDS=60, SECOND=61, GLOBAL=62, PLUS=63, 
		REDSTONE=64, PULSE=65, DO=66, END=67, NAME=68, EVERY=69, COMMA=70, COLON=71, 
		SLASH=72, DASH=73, LPAREN=74, RPAREN=75, NUMBER_WITH_G_SUFFIX=76, NUMBER=77, 
		IDENTIFIER=78, STRING=79, LINE_COMMENT=80, WS=81, UNUSED=82;
	public static final int
		RULE_program = 0, RULE_name = 1, RULE_trigger = 2, RULE_interval = 3, 
		RULE_block = 4, RULE_statement = 5, RULE_forgetStatement = 6, RULE_inputStatement = 7, 
		RULE_outputStatement = 8, RULE_inputResourceLimits = 9, RULE_outputResourceLimits = 10, 
		RULE_resourceLimitList = 11, RULE_resourceLimit = 12, RULE_limit = 13, 
		RULE_quantity = 14, RULE_retention = 15, RULE_resourceExclusion = 16, 
		RULE_resourceId = 17, RULE_resourceIdList = 18, RULE_resourceIdDisjunction = 19, 
		RULE_with = 20, RULE_withClause = 21, RULE_tagMatcher = 22, RULE_sidequalifier = 23, 
		RULE_side = 24, RULE_slotqualifier = 25, RULE_rangeset = 26, RULE_range = 27, 
		RULE_ifStatement = 28, RULE_boolexpr = 29, RULE_comparisonOp = 30, RULE_setOp = 31, 
		RULE_labelAccess = 32, RULE_roundrobin = 33, RULE_label = 34, RULE_emptyslots = 35, 
		RULE_identifier = 36, RULE_string = 37, RULE_number = 38;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "name", "trigger", "interval", "block", "statement", "forgetStatement", 
			"inputStatement", "outputStatement", "inputResourceLimits", "outputResourceLimits", 
			"resourceLimitList", "resourceLimit", "limit", "quantity", "retention", 
			"resourceExclusion", "resourceId", "resourceIdList", "resourceIdDisjunction", 
			"with", "withClause", "tagMatcher", "sidequalifier", "side", "slotqualifier", 
			"rangeset", "range", "ifStatement", "boolexpr", "comparisonOp", "setOp", 
			"labelAccess", "roundrobin", "label", "emptyslots", "identifier", "string", 
			"number"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, "'>'", null, "'<'", null, "'='", null, "'<='", null, 
			"'>='", null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, "'#'", null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, "','", 
			"':'", "'/'", "'-'", "'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "IF", "THEN", "ELSE", "HAS", "OVERALL", "SOME", "ONE", "LONE", 
			"TRUE", "FALSE", "NOT", "AND", "OR", "GT", "GT_SYMBOL", "LT", "LT_SYMBOL", 
			"EQ", "EQ_SYMBOL", "LE", "LE_SYMBOL", "GE", "GE_SYMBOL", "FROM", "TO", 
			"INPUT", "OUTPUT", "WHERE", "SLOTS", "SLOT", "RETAIN", "EACH", "EXCEPT", 
			"FORGET", "EMPTY", "IN", "WITHOUT", "WITH", "TAG", "HASHTAG", "ROUND", 
			"ROBIN", "BY", "LABEL", "BLOCK", "TOP", "BOTTOM", "NORTH", "EAST", "SOUTH", 
			"WEST", "SIDE", "LEFT", "RIGHT", "FRONT", "BACK", "NULL", "TICKS", "TICK", 
			"SECONDS", "SECOND", "GLOBAL", "PLUS", "REDSTONE", "PULSE", "DO", "END", 
			"NAME", "EVERY", "COMMA", "COLON", "SLASH", "DASH", "LPAREN", "RPAREN", 
			"NUMBER_WITH_G_SUFFIX", "NUMBER", "IDENTIFIER", "STRING", "LINE_COMMENT", 
			"WS", "UNUSED"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "SFML.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public SFMLParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgramContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SFMLParser.EOF, 0); }
		public NameContext name() {
			return getRuleContext(NameContext.class,0);
		}
		public List<TriggerContext> trigger() {
			return getRuleContexts(TriggerContext.class);
		}
		public TriggerContext trigger(int i) {
			return getRuleContext(TriggerContext.class,i);
		}
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitProgram(this);
		}
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(79);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(78);
				name();
				}
			}

			setState(84);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EVERY) {
				{
				{
				setState(81);
				trigger();
				}
				}
				setState(86);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(87);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NameContext extends ParserRuleContext {
		public TerminalNode NAME() { return getToken(SFMLParser.NAME, 0); }
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public NameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitName(this);
		}
	}

	public final NameContext name() throws RecognitionException {
		NameContext _localctx = new NameContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(89);
			match(NAME);
			setState(90);
			string();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TriggerContext extends ParserRuleContext {
		public TriggerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_trigger; }
	 
		public TriggerContext() { }
		public void copyFrom(TriggerContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class PulseTriggerContext extends TriggerContext {
		public TerminalNode EVERY() { return getToken(SFMLParser.EVERY, 0); }
		public TerminalNode REDSTONE() { return getToken(SFMLParser.REDSTONE, 0); }
		public TerminalNode PULSE() { return getToken(SFMLParser.PULSE, 0); }
		public TerminalNode DO() { return getToken(SFMLParser.DO, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode END() { return getToken(SFMLParser.END, 0); }
		public PulseTriggerContext(TriggerContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterPulseTrigger(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitPulseTrigger(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class TimerTriggerContext extends TriggerContext {
		public TerminalNode EVERY() { return getToken(SFMLParser.EVERY, 0); }
		public IntervalContext interval() {
			return getRuleContext(IntervalContext.class,0);
		}
		public TerminalNode DO() { return getToken(SFMLParser.DO, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode END() { return getToken(SFMLParser.END, 0); }
		public TimerTriggerContext(TriggerContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterTimerTrigger(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitTimerTrigger(this);
		}
	}

	public final TriggerContext trigger() throws RecognitionException {
		TriggerContext _localctx = new TriggerContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_trigger);
		try {
			setState(105);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				_localctx = new TimerTriggerContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(92);
				match(EVERY);
				setState(93);
				interval();
				setState(94);
				match(DO);
				setState(95);
				block();
				setState(96);
				match(END);
				}
				break;
			case 2:
				_localctx = new PulseTriggerContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(98);
				match(EVERY);
				setState(99);
				match(REDSTONE);
				setState(100);
				match(PULSE);
				setState(101);
				match(DO);
				setState(102);
				block();
				setState(103);
				match(END);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IntervalContext extends ParserRuleContext {
		public IntervalContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_interval; }
	 
		public IntervalContext() { }
		public void copyFrom(IntervalContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IntervalNoSpaceContext extends IntervalContext {
		public TerminalNode NUMBER_WITH_G_SUFFIX() { return getToken(SFMLParser.NUMBER_WITH_G_SUFFIX, 0); }
		public TerminalNode TICKS() { return getToken(SFMLParser.TICKS, 0); }
		public TerminalNode TICK() { return getToken(SFMLParser.TICK, 0); }
		public TerminalNode SECONDS() { return getToken(SFMLParser.SECONDS, 0); }
		public TerminalNode SECOND() { return getToken(SFMLParser.SECOND, 0); }
		public TerminalNode PLUS() { return getToken(SFMLParser.PLUS, 0); }
		public TerminalNode NUMBER() { return getToken(SFMLParser.NUMBER, 0); }
		public IntervalNoSpaceContext(IntervalContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterIntervalNoSpace(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitIntervalNoSpace(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class IntervalSpaceContext extends IntervalContext {
		public TerminalNode TICKS() { return getToken(SFMLParser.TICKS, 0); }
		public TerminalNode TICK() { return getToken(SFMLParser.TICK, 0); }
		public TerminalNode SECONDS() { return getToken(SFMLParser.SECONDS, 0); }
		public TerminalNode SECOND() { return getToken(SFMLParser.SECOND, 0); }
		public List<TerminalNode> NUMBER() { return getTokens(SFMLParser.NUMBER); }
		public TerminalNode NUMBER(int i) {
			return getToken(SFMLParser.NUMBER, i);
		}
		public TerminalNode GLOBAL() { return getToken(SFMLParser.GLOBAL, 0); }
		public TerminalNode PLUS() { return getToken(SFMLParser.PLUS, 0); }
		public IntervalSpaceContext(IntervalContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterIntervalSpace(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitIntervalSpace(this);
		}
	}

	public final IntervalContext interval() throws RecognitionException {
		IntervalContext _localctx = new IntervalContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_interval);
		int _la;
		try {
			setState(124);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TICKS:
			case TICK:
			case SECONDS:
			case SECOND:
			case GLOBAL:
			case PLUS:
			case NUMBER:
				_localctx = new IntervalSpaceContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(108);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==NUMBER) {
					{
					setState(107);
					match(NUMBER);
					}
				}

				setState(111);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==GLOBAL) {
					{
					setState(110);
					match(GLOBAL);
					}
				}

				setState(115);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==PLUS) {
					{
					setState(113);
					match(PLUS);
					setState(114);
					match(NUMBER);
					}
				}

				setState(117);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 4323455642275676160L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case NUMBER_WITH_G_SUFFIX:
				_localctx = new IntervalNoSpaceContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(118);
				match(NUMBER_WITH_G_SUFFIX);
				setState(121);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==PLUS) {
					{
					setState(119);
					match(PLUS);
					setState(120);
					match(NUMBER);
					}
				}

				setState(123);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 4323455642275676160L) != 0)) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BlockContext extends ParserRuleContext {
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBlock(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBlock(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(129);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 17431527426L) != 0)) {
				{
				{
				setState(126);
				statement();
				}
				}
				setState(131);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public InputStatementContext inputStatement() {
			return getRuleContext(InputStatementContext.class,0);
		}
		public OutputStatementContext outputStatement() {
			return getRuleContext(OutputStatementContext.class,0);
		}
		public IfStatementContext ifStatement() {
			return getRuleContext(IfStatementContext.class,0);
		}
		public ForgetStatementContext forgetStatement() {
			return getRuleContext(ForgetStatementContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitStatement(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_statement);
		try {
			setState(136);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case FROM:
			case INPUT:
				enterOuterAlt(_localctx, 1);
				{
				setState(132);
				inputStatement();
				}
				break;
			case TO:
			case OUTPUT:
				enterOuterAlt(_localctx, 2);
				{
				setState(133);
				outputStatement();
				}
				break;
			case IF:
				enterOuterAlt(_localctx, 3);
				{
				setState(134);
				ifStatement();
				}
				break;
			case FORGET:
				enterOuterAlt(_localctx, 4);
				{
				setState(135);
				forgetStatement();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ForgetStatementContext extends ParserRuleContext {
		public TerminalNode FORGET() { return getToken(SFMLParser.FORGET, 0); }
		public List<LabelContext> label() {
			return getRuleContexts(LabelContext.class);
		}
		public LabelContext label(int i) {
			return getRuleContext(LabelContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SFMLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SFMLParser.COMMA, i);
		}
		public ForgetStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forgetStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterForgetStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitForgetStatement(this);
		}
	}

	public final ForgetStatementContext forgetStatement() throws RecognitionException {
		ForgetStatementContext _localctx = new ForgetStatementContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_forgetStatement);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(138);
			match(FORGET);
			setState(140);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 46)) & ~0x3f) == 0 && ((1L << (_la - 46)) & 12885280643L) != 0)) {
				{
				setState(139);
				label();
				}
			}

			setState(146);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(142);
					match(COMMA);
					setState(143);
					label();
					}
					} 
				}
				setState(148);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,11,_ctx);
			}
			setState(150);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(149);
				match(COMMA);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InputStatementContext extends ParserRuleContext {
		public TerminalNode INPUT() { return getToken(SFMLParser.INPUT, 0); }
		public TerminalNode FROM() { return getToken(SFMLParser.FROM, 0); }
		public LabelAccessContext labelAccess() {
			return getRuleContext(LabelAccessContext.class,0);
		}
		public InputResourceLimitsContext inputResourceLimits() {
			return getRuleContext(InputResourceLimitsContext.class,0);
		}
		public ResourceExclusionContext resourceExclusion() {
			return getRuleContext(ResourceExclusionContext.class,0);
		}
		public TerminalNode EACH() { return getToken(SFMLParser.EACH, 0); }
		public InputStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inputStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterInputStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitInputStatement(this);
		}
	}

	public final InputStatementContext inputStatement() throws RecognitionException {
		InputStatementContext _localctx = new InputStatementContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_inputStatement);
		int _la;
		try {
			setState(176);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INPUT:
				enterOuterAlt(_localctx, 1);
				{
				setState(152);
				match(INPUT);
				setState(154);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 31)) & ~0x3f) == 0 && ((1L << (_la - 31)) & 492593620287681L) != 0)) {
					{
					setState(153);
					inputResourceLimits();
					}
				}

				setState(157);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EXCEPT) {
					{
					setState(156);
					resourceExclusion();
					}
				}

				setState(159);
				match(FROM);
				setState(161);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EACH) {
					{
					setState(160);
					match(EACH);
					}
				}

				setState(163);
				labelAccess();
				}
				break;
			case FROM:
				enterOuterAlt(_localctx, 2);
				{
				setState(164);
				match(FROM);
				setState(166);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EACH) {
					{
					setState(165);
					match(EACH);
					}
				}

				setState(168);
				labelAccess();
				setState(169);
				match(INPUT);
				setState(171);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 31)) & ~0x3f) == 0 && ((1L << (_la - 31)) & 492593620287681L) != 0)) {
					{
					setState(170);
					inputResourceLimits();
					}
				}

				setState(174);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EXCEPT) {
					{
					setState(173);
					resourceExclusion();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class OutputStatementContext extends ParserRuleContext {
		public TerminalNode OUTPUT() { return getToken(SFMLParser.OUTPUT, 0); }
		public TerminalNode TO() { return getToken(SFMLParser.TO, 0); }
		public LabelAccessContext labelAccess() {
			return getRuleContext(LabelAccessContext.class,0);
		}
		public OutputResourceLimitsContext outputResourceLimits() {
			return getRuleContext(OutputResourceLimitsContext.class,0);
		}
		public ResourceExclusionContext resourceExclusion() {
			return getRuleContext(ResourceExclusionContext.class,0);
		}
		public EmptyslotsContext emptyslots() {
			return getRuleContext(EmptyslotsContext.class,0);
		}
		public TerminalNode EACH() { return getToken(SFMLParser.EACH, 0); }
		public OutputStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_outputStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterOutputStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitOutputStatement(this);
		}
	}

	public final OutputStatementContext outputStatement() throws RecognitionException {
		OutputStatementContext _localctx = new OutputStatementContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_outputStatement);
		int _la;
		try {
			setState(208);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OUTPUT:
				enterOuterAlt(_localctx, 1);
				{
				setState(178);
				match(OUTPUT);
				setState(180);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 31)) & ~0x3f) == 0 && ((1L << (_la - 31)) & 492593620287681L) != 0)) {
					{
					setState(179);
					outputResourceLimits();
					}
				}

				setState(183);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EXCEPT) {
					{
					setState(182);
					resourceExclusion();
					}
				}

				setState(185);
				match(TO);
				setState(187);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EMPTY) {
					{
					setState(186);
					emptyslots();
					}
				}

				setState(190);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EACH) {
					{
					setState(189);
					match(EACH);
					}
				}

				setState(192);
				labelAccess();
				}
				break;
			case TO:
				enterOuterAlt(_localctx, 2);
				{
				setState(193);
				match(TO);
				setState(195);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EMPTY) {
					{
					setState(194);
					emptyslots();
					}
				}

				setState(198);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EACH) {
					{
					setState(197);
					match(EACH);
					}
				}

				setState(200);
				labelAccess();
				setState(201);
				match(OUTPUT);
				setState(203);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 31)) & ~0x3f) == 0 && ((1L << (_la - 31)) & 492593620287681L) != 0)) {
					{
					setState(202);
					outputResourceLimits();
					}
				}

				setState(206);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EXCEPT) {
					{
					setState(205);
					resourceExclusion();
					}
				}

				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class InputResourceLimitsContext extends ParserRuleContext {
		public ResourceLimitListContext resourceLimitList() {
			return getRuleContext(ResourceLimitListContext.class,0);
		}
		public InputResourceLimitsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inputResourceLimits; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterInputResourceLimits(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitInputResourceLimits(this);
		}
	}

	public final InputResourceLimitsContext inputResourceLimits() throws RecognitionException {
		InputResourceLimitsContext _localctx = new InputResourceLimitsContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_inputResourceLimits);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(210);
			resourceLimitList();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class OutputResourceLimitsContext extends ParserRuleContext {
		public ResourceLimitListContext resourceLimitList() {
			return getRuleContext(ResourceLimitListContext.class,0);
		}
		public OutputResourceLimitsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_outputResourceLimits; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterOutputResourceLimits(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitOutputResourceLimits(this);
		}
	}

	public final OutputResourceLimitsContext outputResourceLimits() throws RecognitionException {
		OutputResourceLimitsContext _localctx = new OutputResourceLimitsContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_outputResourceLimits);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(212);
			resourceLimitList();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResourceLimitListContext extends ParserRuleContext {
		public List<ResourceLimitContext> resourceLimit() {
			return getRuleContexts(ResourceLimitContext.class);
		}
		public ResourceLimitContext resourceLimit(int i) {
			return getRuleContext(ResourceLimitContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SFMLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SFMLParser.COMMA, i);
		}
		public ResourceLimitListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_resourceLimitList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterResourceLimitList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitResourceLimitList(this);
		}
	}

	public final ResourceLimitListContext resourceLimitList() throws RecognitionException {
		ResourceLimitListContext _localctx = new ResourceLimitListContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_resourceLimitList);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(214);
			resourceLimit();
			setState(219);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,29,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(215);
					match(COMMA);
					setState(216);
					resourceLimit();
					}
					} 
				}
				setState(221);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,29,_ctx);
			}
			setState(223);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(222);
				match(COMMA);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResourceLimitContext extends ParserRuleContext {
		public ResourceIdDisjunctionContext resourceIdDisjunction() {
			return getRuleContext(ResourceIdDisjunctionContext.class,0);
		}
		public LimitContext limit() {
			return getRuleContext(LimitContext.class,0);
		}
		public WithContext with() {
			return getRuleContext(WithContext.class,0);
		}
		public ResourceLimitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_resourceLimit; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterResourceLimit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitResourceLimit(this);
		}
	}

	public final ResourceLimitContext resourceLimit() throws RecognitionException {
		ResourceLimitContext _localctx = new ResourceLimitContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_resourceLimit);
		int _la;
		try {
			setState(237);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,34,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(226);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==RETAIN || _la==NUMBER) {
					{
					setState(225);
					limit();
					}
				}

				setState(228);
				resourceIdDisjunction();
				setState(230);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==WITHOUT || _la==WITH) {
					{
					setState(229);
					with();
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(232);
				limit();
				setState(234);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==WITHOUT || _la==WITH) {
					{
					setState(233);
					with();
					}
				}

				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(236);
				with();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LimitContext extends ParserRuleContext {
		public LimitContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_limit; }
	 
		public LimitContext() { }
		public void copyFrom(LimitContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class RetentionLimitContext extends LimitContext {
		public RetentionContext retention() {
			return getRuleContext(RetentionContext.class,0);
		}
		public RetentionLimitContext(LimitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterRetentionLimit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitRetentionLimit(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class QuantityRetentionLimitContext extends LimitContext {
		public QuantityContext quantity() {
			return getRuleContext(QuantityContext.class,0);
		}
		public RetentionContext retention() {
			return getRuleContext(RetentionContext.class,0);
		}
		public QuantityRetentionLimitContext(LimitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterQuantityRetentionLimit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitQuantityRetentionLimit(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class QuantityLimitContext extends LimitContext {
		public QuantityContext quantity() {
			return getRuleContext(QuantityContext.class,0);
		}
		public QuantityLimitContext(LimitContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterQuantityLimit(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitQuantityLimit(this);
		}
	}

	public final LimitContext limit() throws RecognitionException {
		LimitContext _localctx = new LimitContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_limit);
		try {
			setState(244);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,35,_ctx) ) {
			case 1:
				_localctx = new QuantityRetentionLimitContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(239);
				quantity();
				setState(240);
				retention();
				}
				break;
			case 2:
				_localctx = new RetentionLimitContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(242);
				retention();
				}
				break;
			case 3:
				_localctx = new QuantityLimitContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(243);
				quantity();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class QuantityContext extends ParserRuleContext {
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public TerminalNode EACH() { return getToken(SFMLParser.EACH, 0); }
		public QuantityContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_quantity; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterQuantity(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitQuantity(this);
		}
	}

	public final QuantityContext quantity() throws RecognitionException {
		QuantityContext _localctx = new QuantityContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_quantity);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(246);
			number();
			setState(248);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EACH) {
				{
				setState(247);
				match(EACH);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RetentionContext extends ParserRuleContext {
		public TerminalNode RETAIN() { return getToken(SFMLParser.RETAIN, 0); }
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public TerminalNode EACH() { return getToken(SFMLParser.EACH, 0); }
		public RetentionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_retention; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterRetention(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitRetention(this);
		}
	}

	public final RetentionContext retention() throws RecognitionException {
		RetentionContext _localctx = new RetentionContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_retention);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(250);
			match(RETAIN);
			setState(251);
			number();
			setState(253);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EACH) {
				{
				setState(252);
				match(EACH);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResourceExclusionContext extends ParserRuleContext {
		public TerminalNode EXCEPT() { return getToken(SFMLParser.EXCEPT, 0); }
		public ResourceIdListContext resourceIdList() {
			return getRuleContext(ResourceIdListContext.class,0);
		}
		public ResourceExclusionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_resourceExclusion; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterResourceExclusion(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitResourceExclusion(this);
		}
	}

	public final ResourceExclusionContext resourceExclusion() throws RecognitionException {
		ResourceExclusionContext _localctx = new ResourceExclusionContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_resourceExclusion);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(255);
			match(EXCEPT);
			setState(256);
			resourceIdList();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResourceIdContext extends ParserRuleContext {
		public ResourceIdContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_resourceId; }
	 
		public ResourceIdContext() { }
		public void copyFrom(ResourceIdContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringResourceContext extends ResourceIdContext {
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public StringResourceContext(ResourceIdContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterStringResource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitStringResource(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ResourceContext extends ResourceIdContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public List<TerminalNode> COLON() { return getTokens(SFMLParser.COLON); }
		public TerminalNode COLON(int i) {
			return getToken(SFMLParser.COLON, i);
		}
		public ResourceContext(ResourceIdContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterResource(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitResource(this);
		}
	}

	public final ResourceIdContext resourceId() throws RecognitionException {
		ResourceIdContext _localctx = new ResourceIdContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_resourceId);
		try {
			setState(278);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOP:
			case BOTTOM:
			case LEFT:
			case RIGHT:
			case FRONT:
			case BACK:
			case SECONDS:
			case SECOND:
			case GLOBAL:
			case REDSTONE:
			case IDENTIFIER:
				_localctx = new ResourceContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(258);
				identifier();
				}
				setState(275);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,43,_ctx) ) {
				case 1:
					{
					setState(259);
					match(COLON);
					setState(261);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,38,_ctx) ) {
					case 1:
						{
						setState(260);
						identifier();
						}
						break;
					}
					setState(273);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,42,_ctx) ) {
					case 1:
						{
						setState(263);
						match(COLON);
						setState(265);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
						case 1:
							{
							setState(264);
							identifier();
							}
							break;
						}
						setState(271);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,41,_ctx) ) {
						case 1:
							{
							setState(267);
							match(COLON);
							setState(269);
							_errHandler.sync(this);
							switch ( getInterpreter().adaptivePredict(_input,40,_ctx) ) {
							case 1:
								{
								setState(268);
								identifier();
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
			case STRING:
				_localctx = new StringResourceContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(277);
				string();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResourceIdListContext extends ParserRuleContext {
		public List<ResourceIdContext> resourceId() {
			return getRuleContexts(ResourceIdContext.class);
		}
		public ResourceIdContext resourceId(int i) {
			return getRuleContext(ResourceIdContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SFMLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SFMLParser.COMMA, i);
		}
		public ResourceIdListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_resourceIdList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterResourceIdList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitResourceIdList(this);
		}
	}

	public final ResourceIdListContext resourceIdList() throws RecognitionException {
		ResourceIdListContext _localctx = new ResourceIdListContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_resourceIdList);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(280);
			resourceId();
			setState(285);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,45,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(281);
					match(COMMA);
					setState(282);
					resourceId();
					}
					} 
				}
				setState(287);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,45,_ctx);
			}
			setState(289);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,46,_ctx) ) {
			case 1:
				{
				setState(288);
				match(COMMA);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResourceIdDisjunctionContext extends ParserRuleContext {
		public List<ResourceIdContext> resourceId() {
			return getRuleContexts(ResourceIdContext.class);
		}
		public ResourceIdContext resourceId(int i) {
			return getRuleContext(ResourceIdContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(SFMLParser.OR); }
		public TerminalNode OR(int i) {
			return getToken(SFMLParser.OR, i);
		}
		public ResourceIdDisjunctionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_resourceIdDisjunction; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterResourceIdDisjunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitResourceIdDisjunction(this);
		}
	}

	public final ResourceIdDisjunctionContext resourceIdDisjunction() throws RecognitionException {
		ResourceIdDisjunctionContext _localctx = new ResourceIdDisjunctionContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_resourceIdDisjunction);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(291);
			resourceId();
			setState(296);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,47,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(292);
					match(OR);
					setState(293);
					resourceId();
					}
					} 
				}
				setState(298);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,47,_ctx);
			}
			setState(300);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,48,_ctx) ) {
			case 1:
				{
				setState(299);
				match(OR);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WithContext extends ParserRuleContext {
		public TerminalNode WITH() { return getToken(SFMLParser.WITH, 0); }
		public WithClauseContext withClause() {
			return getRuleContext(WithClauseContext.class,0);
		}
		public TerminalNode WITHOUT() { return getToken(SFMLParser.WITHOUT, 0); }
		public WithContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_with; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterWith(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitWith(this);
		}
	}

	public final WithContext with() throws RecognitionException {
		WithContext _localctx = new WithContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_with);
		try {
			setState(306);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case WITH:
				enterOuterAlt(_localctx, 1);
				{
				setState(302);
				match(WITH);
				setState(303);
				withClause(0);
				}
				break;
			case WITHOUT:
				enterOuterAlt(_localctx, 2);
				{
				setState(304);
				match(WITHOUT);
				setState(305);
				withClause(0);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class WithClauseContext extends ParserRuleContext {
		public WithClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_withClause; }
	 
		public WithClauseContext() { }
		public void copyFrom(WithClauseContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WithConjunctionContext extends WithClauseContext {
		public List<WithClauseContext> withClause() {
			return getRuleContexts(WithClauseContext.class);
		}
		public WithClauseContext withClause(int i) {
			return getRuleContext(WithClauseContext.class,i);
		}
		public TerminalNode AND() { return getToken(SFMLParser.AND, 0); }
		public WithConjunctionContext(WithClauseContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterWithConjunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitWithConjunction(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WithParenContext extends WithClauseContext {
		public TerminalNode LPAREN() { return getToken(SFMLParser.LPAREN, 0); }
		public WithClauseContext withClause() {
			return getRuleContext(WithClauseContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SFMLParser.RPAREN, 0); }
		public WithParenContext(WithClauseContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterWithParen(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitWithParen(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WithNegationContext extends WithClauseContext {
		public TerminalNode NOT() { return getToken(SFMLParser.NOT, 0); }
		public WithClauseContext withClause() {
			return getRuleContext(WithClauseContext.class,0);
		}
		public WithNegationContext(WithClauseContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterWithNegation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitWithNegation(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WithTagContext extends WithClauseContext {
		public TagMatcherContext tagMatcher() {
			return getRuleContext(TagMatcherContext.class,0);
		}
		public TerminalNode TAG() { return getToken(SFMLParser.TAG, 0); }
		public TerminalNode HASHTAG() { return getToken(SFMLParser.HASHTAG, 0); }
		public WithTagContext(WithClauseContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterWithTag(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitWithTag(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class WithDisjunctionContext extends WithClauseContext {
		public List<WithClauseContext> withClause() {
			return getRuleContexts(WithClauseContext.class);
		}
		public WithClauseContext withClause(int i) {
			return getRuleContext(WithClauseContext.class,i);
		}
		public TerminalNode OR() { return getToken(SFMLParser.OR, 0); }
		public WithDisjunctionContext(WithClauseContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterWithDisjunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitWithDisjunction(this);
		}
	}

	public final WithClauseContext withClause() throws RecognitionException {
		return withClause(0);
	}

	private WithClauseContext withClause(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		WithClauseContext _localctx = new WithClauseContext(_ctx, _parentState);
		WithClauseContext _prevctx = _localctx;
		int _startState = 42;
		enterRecursionRule(_localctx, 42, RULE_withClause, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(323);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case LPAREN:
				{
				_localctx = new WithParenContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(309);
				match(LPAREN);
				setState(310);
				withClause(0);
				setState(311);
				match(RPAREN);
				}
				break;
			case NOT:
				{
				_localctx = new WithNegationContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(313);
				match(NOT);
				setState(314);
				withClause(4);
				}
				break;
			case TAG:
			case HASHTAG:
				{
				_localctx = new WithTagContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(320);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case TAG:
					{
					setState(315);
					match(TAG);
					setState(317);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==HASHTAG) {
						{
						setState(316);
						match(HASHTAG);
						}
					}

					}
					break;
				case HASHTAG:
					{
					setState(319);
					match(HASHTAG);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(322);
				tagMatcher();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(333);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(331);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,53,_ctx) ) {
					case 1:
						{
						_localctx = new WithConjunctionContext(new WithClauseContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_withClause);
						setState(325);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(326);
						match(AND);
						setState(327);
						withClause(4);
						}
						break;
					case 2:
						{
						_localctx = new WithDisjunctionContext(new WithClauseContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_withClause);
						setState(328);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(329);
						match(OR);
						setState(330);
						withClause(3);
						}
						break;
					}
					} 
				}
				setState(335);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,54,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TagMatcherContext extends ParserRuleContext {
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public TerminalNode COLON() { return getToken(SFMLParser.COLON, 0); }
		public List<TerminalNode> SLASH() { return getTokens(SFMLParser.SLASH); }
		public TerminalNode SLASH(int i) {
			return getToken(SFMLParser.SLASH, i);
		}
		public TagMatcherContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_tagMatcher; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterTagMatcher(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitTagMatcher(this);
		}
	}

	public final TagMatcherContext tagMatcher() throws RecognitionException {
		TagMatcherContext _localctx = new TagMatcherContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_tagMatcher);
		try {
			int _alt;
			setState(354);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,57,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(336);
				identifier();
				setState(337);
				match(COLON);
				setState(338);
				identifier();
				setState(343);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(339);
						match(SLASH);
						setState(340);
						identifier();
						}
						} 
					}
					setState(345);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(346);
				identifier();
				setState(351);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(347);
						match(SLASH);
						setState(348);
						identifier();
						}
						} 
					}
					setState(353);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
				}
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SidequalifierContext extends ParserRuleContext {
		public SidequalifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sidequalifier; }
	 
		public SidequalifierContext() { }
		public void copyFrom(SidequalifierContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class EachSideContext extends SidequalifierContext {
		public TerminalNode EACH() { return getToken(SFMLParser.EACH, 0); }
		public TerminalNode SIDE() { return getToken(SFMLParser.SIDE, 0); }
		public EachSideContext(SidequalifierContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterEachSide(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitEachSide(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class ListedSidesContext extends SidequalifierContext {
		public List<SideContext> side() {
			return getRuleContexts(SideContext.class);
		}
		public SideContext side(int i) {
			return getRuleContext(SideContext.class,i);
		}
		public TerminalNode SIDE() { return getToken(SFMLParser.SIDE, 0); }
		public List<TerminalNode> COMMA() { return getTokens(SFMLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SFMLParser.COMMA, i);
		}
		public ListedSidesContext(SidequalifierContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterListedSides(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitListedSides(this);
		}
	}

	public final SidequalifierContext sidequalifier() throws RecognitionException {
		SidequalifierContext _localctx = new SidequalifierContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_sidequalifier);
		int _la;
		try {
			setState(368);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EACH:
				_localctx = new EachSideContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(356);
				match(EACH);
				setState(357);
				match(SIDE);
				}
				break;
			case TOP:
			case BOTTOM:
			case NORTH:
			case EAST:
			case SOUTH:
			case WEST:
			case LEFT:
			case RIGHT:
			case FRONT:
			case BACK:
			case NULL:
				_localctx = new ListedSidesContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(358);
				side();
				setState(363);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==COMMA) {
					{
					{
					setState(359);
					match(COMMA);
					setState(360);
					side();
					}
					}
					setState(365);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(366);
				match(SIDE);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SideContext extends ParserRuleContext {
		public TerminalNode TOP() { return getToken(SFMLParser.TOP, 0); }
		public TerminalNode BOTTOM() { return getToken(SFMLParser.BOTTOM, 0); }
		public TerminalNode NORTH() { return getToken(SFMLParser.NORTH, 0); }
		public TerminalNode EAST() { return getToken(SFMLParser.EAST, 0); }
		public TerminalNode SOUTH() { return getToken(SFMLParser.SOUTH, 0); }
		public TerminalNode WEST() { return getToken(SFMLParser.WEST, 0); }
		public TerminalNode LEFT() { return getToken(SFMLParser.LEFT, 0); }
		public TerminalNode RIGHT() { return getToken(SFMLParser.RIGHT, 0); }
		public TerminalNode FRONT() { return getToken(SFMLParser.FRONT, 0); }
		public TerminalNode BACK() { return getToken(SFMLParser.BACK, 0); }
		public TerminalNode NULL() { return getToken(SFMLParser.NULL, 0); }
		public SideContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_side; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterSide(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitSide(this);
		}
	}

	public final SideContext side() throws RecognitionException {
		SideContext _localctx = new SideContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_side);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(370);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 283656407780163584L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SlotqualifierContext extends ParserRuleContext {
		public RangesetContext rangeset() {
			return getRuleContext(RangesetContext.class,0);
		}
		public TerminalNode SLOTS() { return getToken(SFMLParser.SLOTS, 0); }
		public TerminalNode SLOT() { return getToken(SFMLParser.SLOT, 0); }
		public SlotqualifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_slotqualifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterSlotqualifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitSlotqualifier(this);
		}
	}

	public final SlotqualifierContext slotqualifier() throws RecognitionException {
		SlotqualifierContext _localctx = new SlotqualifierContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_slotqualifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(372);
			_la = _input.LA(1);
			if ( !(_la==SLOTS || _la==SLOT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(373);
			rangeset();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RangesetContext extends ParserRuleContext {
		public List<RangeContext> range() {
			return getRuleContexts(RangeContext.class);
		}
		public RangeContext range(int i) {
			return getRuleContext(RangeContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SFMLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SFMLParser.COMMA, i);
		}
		public RangesetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_rangeset; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterRangeset(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitRangeset(this);
		}
	}

	public final RangesetContext rangeset() throws RecognitionException {
		RangesetContext _localctx = new RangesetContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_rangeset);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(375);
			range();
			setState(380);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(376);
				match(COMMA);
				setState(377);
				range();
				}
				}
				setState(382);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RangeContext extends ParserRuleContext {
		public List<NumberContext> number() {
			return getRuleContexts(NumberContext.class);
		}
		public NumberContext number(int i) {
			return getRuleContext(NumberContext.class,i);
		}
		public TerminalNode DASH() { return getToken(SFMLParser.DASH, 0); }
		public RangeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_range; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterRange(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitRange(this);
		}
	}

	public final RangeContext range() throws RecognitionException {
		RangeContext _localctx = new RangeContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_range);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(383);
			number();
			setState(386);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==DASH) {
				{
				setState(384);
				match(DASH);
				setState(385);
				number();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IfStatementContext extends ParserRuleContext {
		public List<TerminalNode> IF() { return getTokens(SFMLParser.IF); }
		public TerminalNode IF(int i) {
			return getToken(SFMLParser.IF, i);
		}
		public List<BoolexprContext> boolexpr() {
			return getRuleContexts(BoolexprContext.class);
		}
		public BoolexprContext boolexpr(int i) {
			return getRuleContext(BoolexprContext.class,i);
		}
		public List<TerminalNode> THEN() { return getTokens(SFMLParser.THEN); }
		public TerminalNode THEN(int i) {
			return getToken(SFMLParser.THEN, i);
		}
		public List<BlockContext> block() {
			return getRuleContexts(BlockContext.class);
		}
		public BlockContext block(int i) {
			return getRuleContext(BlockContext.class,i);
		}
		public TerminalNode END() { return getToken(SFMLParser.END, 0); }
		public List<TerminalNode> ELSE() { return getTokens(SFMLParser.ELSE); }
		public TerminalNode ELSE(int i) {
			return getToken(SFMLParser.ELSE, i);
		}
		public IfStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ifStatement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterIfStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitIfStatement(this);
		}
	}

	public final IfStatementContext ifStatement() throws RecognitionException {
		IfStatementContext _localctx = new IfStatementContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_ifStatement);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(388);
			match(IF);
			setState(389);
			boolexpr(0);
			setState(390);
			match(THEN);
			setState(391);
			block();
			setState(400);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,62,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(392);
					match(ELSE);
					setState(393);
					match(IF);
					setState(394);
					boolexpr(0);
					setState(395);
					match(THEN);
					setState(396);
					block();
					}
					} 
				}
				setState(402);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,62,_ctx);
			}
			setState(405);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(403);
				match(ELSE);
				setState(404);
				block();
				}
			}

			setState(407);
			match(END);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class BoolexprContext extends ParserRuleContext {
		public BoolexprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_boolexpr; }
	 
		public BoolexprContext() { }
		public void copyFrom(BoolexprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanHasContext extends BoolexprContext {
		public LabelAccessContext labelAccess() {
			return getRuleContext(LabelAccessContext.class,0);
		}
		public TerminalNode HAS() { return getToken(SFMLParser.HAS, 0); }
		public ComparisonOpContext comparisonOp() {
			return getRuleContext(ComparisonOpContext.class,0);
		}
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public SetOpContext setOp() {
			return getRuleContext(SetOpContext.class,0);
		}
		public ResourceIdDisjunctionContext resourceIdDisjunction() {
			return getRuleContext(ResourceIdDisjunctionContext.class,0);
		}
		public WithContext with() {
			return getRuleContext(WithContext.class,0);
		}
		public TerminalNode EXCEPT() { return getToken(SFMLParser.EXCEPT, 0); }
		public ResourceIdListContext resourceIdList() {
			return getRuleContext(ResourceIdListContext.class,0);
		}
		public BooleanHasContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanHas(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanHas(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanConjunctionContext extends BoolexprContext {
		public List<BoolexprContext> boolexpr() {
			return getRuleContexts(BoolexprContext.class);
		}
		public BoolexprContext boolexpr(int i) {
			return getRuleContext(BoolexprContext.class,i);
		}
		public TerminalNode AND() { return getToken(SFMLParser.AND, 0); }
		public BooleanConjunctionContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanConjunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanConjunction(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanRedstoneContext extends BoolexprContext {
		public TerminalNode REDSTONE() { return getToken(SFMLParser.REDSTONE, 0); }
		public ComparisonOpContext comparisonOp() {
			return getRuleContext(ComparisonOpContext.class,0);
		}
		public NumberContext number() {
			return getRuleContext(NumberContext.class,0);
		}
		public BooleanRedstoneContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanRedstone(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanRedstone(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanDisjunctionContext extends BoolexprContext {
		public List<BoolexprContext> boolexpr() {
			return getRuleContexts(BoolexprContext.class);
		}
		public BoolexprContext boolexpr(int i) {
			return getRuleContext(BoolexprContext.class,i);
		}
		public TerminalNode OR() { return getToken(SFMLParser.OR, 0); }
		public BooleanDisjunctionContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanDisjunction(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanDisjunction(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanFalseContext extends BoolexprContext {
		public TerminalNode FALSE() { return getToken(SFMLParser.FALSE, 0); }
		public BooleanFalseContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanFalse(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanFalse(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanParenContext extends BoolexprContext {
		public TerminalNode LPAREN() { return getToken(SFMLParser.LPAREN, 0); }
		public BoolexprContext boolexpr() {
			return getRuleContext(BoolexprContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(SFMLParser.RPAREN, 0); }
		public BooleanParenContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanParen(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanParen(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanNegationContext extends BoolexprContext {
		public TerminalNode NOT() { return getToken(SFMLParser.NOT, 0); }
		public BoolexprContext boolexpr() {
			return getRuleContext(BoolexprContext.class,0);
		}
		public BooleanNegationContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanNegation(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanNegation(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class BooleanTrueContext extends BoolexprContext {
		public TerminalNode TRUE() { return getToken(SFMLParser.TRUE, 0); }
		public BooleanTrueContext(BoolexprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterBooleanTrue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitBooleanTrue(this);
		}
	}

	public final BoolexprContext boolexpr() throws RecognitionException {
		return boolexpr(0);
	}

	private BoolexprContext boolexpr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		BoolexprContext _localctx = new BoolexprContext(_ctx, _parentState);
		BoolexprContext _prevctx = _localctx;
		int _startState = 58;
		enterRecursionRule(_localctx, 58, RULE_boolexpr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(441);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,69,_ctx) ) {
			case 1:
				{
				_localctx = new BooleanTrueContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(410);
				match(TRUE);
				}
				break;
			case 2:
				{
				_localctx = new BooleanFalseContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(411);
				match(FALSE);
				}
				break;
			case 3:
				{
				_localctx = new BooleanParenContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(412);
				match(LPAREN);
				setState(413);
				boolexpr(0);
				setState(414);
				match(RPAREN);
				}
				break;
			case 4:
				{
				_localctx = new BooleanNegationContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(416);
				match(NOT);
				setState(417);
				boolexpr(5);
				}
				break;
			case 5:
				{
				_localctx = new BooleanHasContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(419);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 4294967776L) != 0) || _la==EVERY) {
					{
					setState(418);
					setOp();
					}
				}

				setState(421);
				labelAccess();
				setState(422);
				match(HAS);
				setState(423);
				comparisonOp();
				setState(424);
				number();
				setState(426);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,65,_ctx) ) {
				case 1:
					{
					setState(425);
					resourceIdDisjunction();
					}
					break;
				}
				setState(429);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,66,_ctx) ) {
				case 1:
					{
					setState(428);
					with();
					}
					break;
				}
				setState(433);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,67,_ctx) ) {
				case 1:
					{
					setState(431);
					match(EXCEPT);
					setState(432);
					resourceIdList();
					}
					break;
				}
				}
				break;
			case 6:
				{
				_localctx = new BooleanRedstoneContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(435);
				match(REDSTONE);
				setState(439);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,68,_ctx) ) {
				case 1:
					{
					setState(436);
					comparisonOp();
					setState(437);
					number();
					}
					break;
				}
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(451);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,71,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(449);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,70,_ctx) ) {
					case 1:
						{
						_localctx = new BooleanConjunctionContext(new BoolexprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_boolexpr);
						setState(443);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(444);
						match(AND);
						setState(445);
						boolexpr(5);
						}
						break;
					case 2:
						{
						_localctx = new BooleanDisjunctionContext(new BoolexprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_boolexpr);
						setState(446);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(447);
						match(OR);
						setState(448);
						boolexpr(4);
						}
						break;
					}
					} 
				}
				setState(453);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,71,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ComparisonOpContext extends ParserRuleContext {
		public TerminalNode GT() { return getToken(SFMLParser.GT, 0); }
		public TerminalNode LT() { return getToken(SFMLParser.LT, 0); }
		public TerminalNode EQ() { return getToken(SFMLParser.EQ, 0); }
		public TerminalNode LE() { return getToken(SFMLParser.LE, 0); }
		public TerminalNode GE() { return getToken(SFMLParser.GE, 0); }
		public TerminalNode GT_SYMBOL() { return getToken(SFMLParser.GT_SYMBOL, 0); }
		public TerminalNode LT_SYMBOL() { return getToken(SFMLParser.LT_SYMBOL, 0); }
		public TerminalNode EQ_SYMBOL() { return getToken(SFMLParser.EQ_SYMBOL, 0); }
		public TerminalNode LE_SYMBOL() { return getToken(SFMLParser.LE_SYMBOL, 0); }
		public TerminalNode GE_SYMBOL() { return getToken(SFMLParser.GE_SYMBOL, 0); }
		public ComparisonOpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparisonOp; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterComparisonOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitComparisonOp(this);
		}
	}

	public final ComparisonOpContext comparisonOp() throws RecognitionException {
		ComparisonOpContext _localctx = new ComparisonOpContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_comparisonOp);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(454);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 16760832L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class SetOpContext extends ParserRuleContext {
		public TerminalNode OVERALL() { return getToken(SFMLParser.OVERALL, 0); }
		public TerminalNode SOME() { return getToken(SFMLParser.SOME, 0); }
		public TerminalNode EVERY() { return getToken(SFMLParser.EVERY, 0); }
		public TerminalNode EACH() { return getToken(SFMLParser.EACH, 0); }
		public TerminalNode ONE() { return getToken(SFMLParser.ONE, 0); }
		public TerminalNode LONE() { return getToken(SFMLParser.LONE, 0); }
		public SetOpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_setOp; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterSetOp(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitSetOp(this);
		}
	}

	public final SetOpContext setOp() throws RecognitionException {
		SetOpContext _localctx = new SetOpContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_setOp);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(456);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 4294967776L) != 0) || _la==EVERY) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LabelAccessContext extends ParserRuleContext {
		public List<LabelContext> label() {
			return getRuleContexts(LabelContext.class);
		}
		public LabelContext label(int i) {
			return getRuleContext(LabelContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SFMLParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SFMLParser.COMMA, i);
		}
		public RoundrobinContext roundrobin() {
			return getRuleContext(RoundrobinContext.class,0);
		}
		public SidequalifierContext sidequalifier() {
			return getRuleContext(SidequalifierContext.class,0);
		}
		public SlotqualifierContext slotqualifier() {
			return getRuleContext(SlotqualifierContext.class,0);
		}
		public LabelAccessContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_labelAccess; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterLabelAccess(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitLabelAccess(this);
		}
	}

	public final LabelAccessContext labelAccess() throws RecognitionException {
		LabelAccessContext _localctx = new LabelAccessContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_labelAccess);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(458);
			label();
			setState(463);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(459);
				match(COMMA);
				setState(460);
				label();
				}
				}
				setState(465);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(467);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ROUND) {
				{
				setState(466);
				roundrobin();
				}
			}

			setState(470);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 283656412075130880L) != 0)) {
				{
				setState(469);
				sidequalifier();
				}
			}

			setState(473);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SLOTS || _la==SLOT) {
				{
				setState(472);
				slotqualifier();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RoundrobinContext extends ParserRuleContext {
		public TerminalNode ROUND() { return getToken(SFMLParser.ROUND, 0); }
		public TerminalNode ROBIN() { return getToken(SFMLParser.ROBIN, 0); }
		public TerminalNode BY() { return getToken(SFMLParser.BY, 0); }
		public TerminalNode LABEL() { return getToken(SFMLParser.LABEL, 0); }
		public TerminalNode BLOCK() { return getToken(SFMLParser.BLOCK, 0); }
		public RoundrobinContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_roundrobin; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterRoundrobin(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitRoundrobin(this);
		}
	}

	public final RoundrobinContext roundrobin() throws RecognitionException {
		RoundrobinContext _localctx = new RoundrobinContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_roundrobin);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(475);
			match(ROUND);
			setState(476);
			match(ROBIN);
			setState(477);
			match(BY);
			setState(478);
			_la = _input.LA(1);
			if ( !(_la==LABEL || _la==BLOCK) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LabelContext extends ParserRuleContext {
		public LabelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_label; }
	 
		public LabelContext() { }
		public void copyFrom(LabelContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class RawLabelContext extends LabelContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public RawLabelContext(LabelContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterRawLabel(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitRawLabel(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class StringLabelContext extends LabelContext {
		public StringContext string() {
			return getRuleContext(StringContext.class,0);
		}
		public StringLabelContext(LabelContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterStringLabel(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitStringLabel(this);
		}
	}

	public final LabelContext label() throws RecognitionException {
		LabelContext _localctx = new LabelContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_label);
		try {
			setState(482);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case TOP:
			case BOTTOM:
			case LEFT:
			case RIGHT:
			case FRONT:
			case BACK:
			case SECONDS:
			case SECOND:
			case GLOBAL:
			case REDSTONE:
			case IDENTIFIER:
				_localctx = new RawLabelContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				{
				setState(480);
				identifier();
				}
				}
				break;
			case STRING:
				_localctx = new StringLabelContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(481);
				string();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class EmptyslotsContext extends ParserRuleContext {
		public TerminalNode EMPTY() { return getToken(SFMLParser.EMPTY, 0); }
		public TerminalNode IN() { return getToken(SFMLParser.IN, 0); }
		public TerminalNode SLOTS() { return getToken(SFMLParser.SLOTS, 0); }
		public TerminalNode SLOT() { return getToken(SFMLParser.SLOT, 0); }
		public EmptyslotsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_emptyslots; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterEmptyslots(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitEmptyslots(this);
		}
	}

	public final EmptyslotsContext emptyslots() throws RecognitionException {
		EmptyslotsContext _localctx = new EmptyslotsContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_emptyslots);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(484);
			match(EMPTY);
			setState(485);
			_la = _input.LA(1);
			if ( !(_la==SLOTS || _la==SLOT) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(486);
			match(IN);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class IdentifierContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(SFMLParser.IDENTIFIER, 0); }
		public TerminalNode REDSTONE() { return getToken(SFMLParser.REDSTONE, 0); }
		public TerminalNode GLOBAL() { return getToken(SFMLParser.GLOBAL, 0); }
		public TerminalNode SECOND() { return getToken(SFMLParser.SECOND, 0); }
		public TerminalNode SECONDS() { return getToken(SFMLParser.SECONDS, 0); }
		public TerminalNode TOP() { return getToken(SFMLParser.TOP, 0); }
		public TerminalNode BOTTOM() { return getToken(SFMLParser.BOTTOM, 0); }
		public TerminalNode LEFT() { return getToken(SFMLParser.LEFT, 0); }
		public TerminalNode RIGHT() { return getToken(SFMLParser.RIGHT, 0); }
		public TerminalNode FRONT() { return getToken(SFMLParser.FRONT, 0); }
		public TerminalNode BACK() { return getToken(SFMLParser.BACK, 0); }
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterIdentifier(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitIdentifier(this);
		}
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_identifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(488);
			_la = _input.LA(1);
			if ( !(((((_la - 46)) & ~0x3f) == 0 && ((1L << (_la - 46)) & 4295346051L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StringContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(SFMLParser.STRING, 0); }
		public StringContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_string; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterString(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitString(this);
		}
	}

	public final StringContext string() throws RecognitionException {
		StringContext _localctx = new StringContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_string);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(490);
			match(STRING);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class NumberContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(SFMLParser.NUMBER, 0); }
		public NumberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_number; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).enterNumber(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SFMLListener ) ((SFMLListener)listener).exitNumber(this);
		}
	}

	public final NumberContext number() throws RecognitionException {
		NumberContext _localctx = new NumberContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_number);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(492);
			match(NUMBER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 21:
			return withClause_sempred((WithClauseContext)_localctx, predIndex);
		case 29:
			return boolexpr_sempred((BoolexprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean withClause_sempred(WithClauseContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 3);
		case 1:
			return precpred(_ctx, 2);
		}
		return true;
	}
	private boolean boolexpr_sempred(BoolexprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 2:
			return precpred(_ctx, 4);
		case 3:
			return precpred(_ctx, 3);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001R\u01ef\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002"+
		"\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002"+
		"\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002"+
		"\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002"+
		"\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f"+
		"\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012"+
		"\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015"+
		"\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018"+
		"\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b"+
		"\u0002\u001c\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e"+
		"\u0002\u001f\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002"+
		"#\u0007#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0001\u0000\u0003\u0000"+
		"P\b\u0000\u0001\u0000\u0005\u0000S\b\u0000\n\u0000\f\u0000V\t\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003"+
		"\u0002j\b\u0002\u0001\u0003\u0003\u0003m\b\u0003\u0001\u0003\u0003\u0003"+
		"p\b\u0003\u0001\u0003\u0001\u0003\u0003\u0003t\b\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0003\u0003z\b\u0003\u0001\u0003\u0003"+
		"\u0003}\b\u0003\u0001\u0004\u0005\u0004\u0080\b\u0004\n\u0004\f\u0004"+
		"\u0083\t\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0003\u0005"+
		"\u0089\b\u0005\u0001\u0006\u0001\u0006\u0003\u0006\u008d\b\u0006\u0001"+
		"\u0006\u0001\u0006\u0005\u0006\u0091\b\u0006\n\u0006\f\u0006\u0094\t\u0006"+
		"\u0001\u0006\u0003\u0006\u0097\b\u0006\u0001\u0007\u0001\u0007\u0003\u0007"+
		"\u009b\b\u0007\u0001\u0007\u0003\u0007\u009e\b\u0007\u0001\u0007\u0001"+
		"\u0007\u0003\u0007\u00a2\b\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003"+
		"\u0007\u00a7\b\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007\u00ac"+
		"\b\u0007\u0001\u0007\u0003\u0007\u00af\b\u0007\u0003\u0007\u00b1\b\u0007"+
		"\u0001\b\u0001\b\u0003\b\u00b5\b\b\u0001\b\u0003\b\u00b8\b\b\u0001\b\u0001"+
		"\b\u0003\b\u00bc\b\b\u0001\b\u0003\b\u00bf\b\b\u0001\b\u0001\b\u0001\b"+
		"\u0003\b\u00c4\b\b\u0001\b\u0003\b\u00c7\b\b\u0001\b\u0001\b\u0001\b\u0003"+
		"\b\u00cc\b\b\u0001\b\u0003\b\u00cf\b\b\u0003\b\u00d1\b\b\u0001\t\u0001"+
		"\t\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b\u0005\u000b\u00da"+
		"\b\u000b\n\u000b\f\u000b\u00dd\t\u000b\u0001\u000b\u0003\u000b\u00e0\b"+
		"\u000b\u0001\f\u0003\f\u00e3\b\f\u0001\f\u0001\f\u0003\f\u00e7\b\f\u0001"+
		"\f\u0001\f\u0003\f\u00eb\b\f\u0001\f\u0003\f\u00ee\b\f\u0001\r\u0001\r"+
		"\u0001\r\u0001\r\u0001\r\u0003\r\u00f5\b\r\u0001\u000e\u0001\u000e\u0003"+
		"\u000e\u00f9\b\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0003\u000f\u00fe"+
		"\b\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0011\u0001\u0011\u0001"+
		"\u0011\u0003\u0011\u0106\b\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u010a"+
		"\b\u0011\u0001\u0011\u0001\u0011\u0003\u0011\u010e\b\u0011\u0003\u0011"+
		"\u0110\b\u0011\u0003\u0011\u0112\b\u0011\u0003\u0011\u0114\b\u0011\u0001"+
		"\u0011\u0003\u0011\u0117\b\u0011\u0001\u0012\u0001\u0012\u0001\u0012\u0005"+
		"\u0012\u011c\b\u0012\n\u0012\f\u0012\u011f\t\u0012\u0001\u0012\u0003\u0012"+
		"\u0122\b\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0005\u0013\u0127\b"+
		"\u0013\n\u0013\f\u0013\u012a\t\u0013\u0001\u0013\u0003\u0013\u012d\b\u0013"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0003\u0014\u0133\b\u0014"+
		"\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015"+
		"\u0001\u0015\u0001\u0015\u0001\u0015\u0003\u0015\u013e\b\u0015\u0001\u0015"+
		"\u0003\u0015\u0141\b\u0015\u0001\u0015\u0003\u0015\u0144\b\u0015\u0001"+
		"\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0005"+
		"\u0015\u014c\b\u0015\n\u0015\f\u0015\u014f\t\u0015\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0016\u0005\u0016\u0156\b\u0016\n\u0016"+
		"\f\u0016\u0159\t\u0016\u0001\u0016\u0001\u0016\u0001\u0016\u0005\u0016"+
		"\u015e\b\u0016\n\u0016\f\u0016\u0161\t\u0016\u0003\u0016\u0163\b\u0016"+
		"\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0001\u0017\u0005\u0017"+
		"\u016a\b\u0017\n\u0017\f\u0017\u016d\t\u0017\u0001\u0017\u0001\u0017\u0003"+
		"\u0017\u0171\b\u0017\u0001\u0018\u0001\u0018\u0001\u0019\u0001\u0019\u0001"+
		"\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0005\u001a\u017b\b\u001a\n"+
		"\u001a\f\u001a\u017e\t\u001a\u0001\u001b\u0001\u001b\u0001\u001b\u0003"+
		"\u001b\u0183\b\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0001"+
		"\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001c\u0005"+
		"\u001c\u018f\b\u001c\n\u001c\f\u001c\u0192\t\u001c\u0001\u001c\u0001\u001c"+
		"\u0003\u001c\u0196\b\u001c\u0001\u001c\u0001\u001c\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001d\u0003\u001d\u01a4\b\u001d\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001d\u0001\u001d\u0003\u001d\u01ab\b\u001d\u0001\u001d"+
		"\u0003\u001d\u01ae\b\u001d\u0001\u001d\u0001\u001d\u0003\u001d\u01b2\b"+
		"\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0001\u001d\u0003\u001d\u01b8"+
		"\b\u001d\u0003\u001d\u01ba\b\u001d\u0001\u001d\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001d\u0001\u001d\u0005\u001d\u01c2\b\u001d\n\u001d"+
		"\f\u001d\u01c5\t\u001d\u0001\u001e\u0001\u001e\u0001\u001f\u0001\u001f"+
		"\u0001 \u0001 \u0001 \u0005 \u01ce\b \n \f \u01d1\t \u0001 \u0003 \u01d4"+
		"\b \u0001 \u0003 \u01d7\b \u0001 \u0003 \u01da\b \u0001!\u0001!\u0001"+
		"!\u0001!\u0001!\u0001\"\u0001\"\u0003\"\u01e3\b\"\u0001#\u0001#\u0001"+
		"#\u0001#\u0001$\u0001$\u0001%\u0001%\u0001&\u0001&\u0001&\u0000\u0002"+
		"*:\'\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018"+
		"\u001a\u001c\u001e \"$&(*,.02468:<>@BDFHJL\u0000\u0007\u0001\u0000:=\u0002"+
		"\u0000.359\u0001\u0000\u001d\u001e\u0001\u0000\u000e\u0017\u0003\u0000"+
		"\u0005\b  EE\u0001\u0000,-\u0005\u0000./58<>@@NN\u021d\u0000O\u0001\u0000"+
		"\u0000\u0000\u0002Y\u0001\u0000\u0000\u0000\u0004i\u0001\u0000\u0000\u0000"+
		"\u0006|\u0001\u0000\u0000\u0000\b\u0081\u0001\u0000\u0000\u0000\n\u0088"+
		"\u0001\u0000\u0000\u0000\f\u008a\u0001\u0000\u0000\u0000\u000e\u00b0\u0001"+
		"\u0000\u0000\u0000\u0010\u00d0\u0001\u0000\u0000\u0000\u0012\u00d2\u0001"+
		"\u0000\u0000\u0000\u0014\u00d4\u0001\u0000\u0000\u0000\u0016\u00d6\u0001"+
		"\u0000\u0000\u0000\u0018\u00ed\u0001\u0000\u0000\u0000\u001a\u00f4\u0001"+
		"\u0000\u0000\u0000\u001c\u00f6\u0001\u0000\u0000\u0000\u001e\u00fa\u0001"+
		"\u0000\u0000\u0000 \u00ff\u0001\u0000\u0000\u0000\"\u0116\u0001\u0000"+
		"\u0000\u0000$\u0118\u0001\u0000\u0000\u0000&\u0123\u0001\u0000\u0000\u0000"+
		"(\u0132\u0001\u0000\u0000\u0000*\u0143\u0001\u0000\u0000\u0000,\u0162"+
		"\u0001\u0000\u0000\u0000.\u0170\u0001\u0000\u0000\u00000\u0172\u0001\u0000"+
		"\u0000\u00002\u0174\u0001\u0000\u0000\u00004\u0177\u0001\u0000\u0000\u0000"+
		"6\u017f\u0001\u0000\u0000\u00008\u0184\u0001\u0000\u0000\u0000:\u01b9"+
		"\u0001\u0000\u0000\u0000<\u01c6\u0001\u0000\u0000\u0000>\u01c8\u0001\u0000"+
		"\u0000\u0000@\u01ca\u0001\u0000\u0000\u0000B\u01db\u0001\u0000\u0000\u0000"+
		"D\u01e2\u0001\u0000\u0000\u0000F\u01e4\u0001\u0000\u0000\u0000H\u01e8"+
		"\u0001\u0000\u0000\u0000J\u01ea\u0001\u0000\u0000\u0000L\u01ec\u0001\u0000"+
		"\u0000\u0000NP\u0003\u0002\u0001\u0000ON\u0001\u0000\u0000\u0000OP\u0001"+
		"\u0000\u0000\u0000PT\u0001\u0000\u0000\u0000QS\u0003\u0004\u0002\u0000"+
		"RQ\u0001\u0000\u0000\u0000SV\u0001\u0000\u0000\u0000TR\u0001\u0000\u0000"+
		"\u0000TU\u0001\u0000\u0000\u0000UW\u0001\u0000\u0000\u0000VT\u0001\u0000"+
		"\u0000\u0000WX\u0005\u0000\u0000\u0001X\u0001\u0001\u0000\u0000\u0000"+
		"YZ\u0005D\u0000\u0000Z[\u0003J%\u0000[\u0003\u0001\u0000\u0000\u0000\\"+
		"]\u0005E\u0000\u0000]^\u0003\u0006\u0003\u0000^_\u0005B\u0000\u0000_`"+
		"\u0003\b\u0004\u0000`a\u0005C\u0000\u0000aj\u0001\u0000\u0000\u0000bc"+
		"\u0005E\u0000\u0000cd\u0005@\u0000\u0000de\u0005A\u0000\u0000ef\u0005"+
		"B\u0000\u0000fg\u0003\b\u0004\u0000gh\u0005C\u0000\u0000hj\u0001\u0000"+
		"\u0000\u0000i\\\u0001\u0000\u0000\u0000ib\u0001\u0000\u0000\u0000j\u0005"+
		"\u0001\u0000\u0000\u0000km\u0005M\u0000\u0000lk\u0001\u0000\u0000\u0000"+
		"lm\u0001\u0000\u0000\u0000mo\u0001\u0000\u0000\u0000np\u0005>\u0000\u0000"+
		"on\u0001\u0000\u0000\u0000op\u0001\u0000\u0000\u0000ps\u0001\u0000\u0000"+
		"\u0000qr\u0005?\u0000\u0000rt\u0005M\u0000\u0000sq\u0001\u0000\u0000\u0000"+
		"st\u0001\u0000\u0000\u0000tu\u0001\u0000\u0000\u0000u}\u0007\u0000\u0000"+
		"\u0000vy\u0005L\u0000\u0000wx\u0005?\u0000\u0000xz\u0005M\u0000\u0000"+
		"yw\u0001\u0000\u0000\u0000yz\u0001\u0000\u0000\u0000z{\u0001\u0000\u0000"+
		"\u0000{}\u0007\u0000\u0000\u0000|l\u0001\u0000\u0000\u0000|v\u0001\u0000"+
		"\u0000\u0000}\u0007\u0001\u0000\u0000\u0000~\u0080\u0003\n\u0005\u0000"+
		"\u007f~\u0001\u0000\u0000\u0000\u0080\u0083\u0001\u0000\u0000\u0000\u0081"+
		"\u007f\u0001\u0000\u0000\u0000\u0081\u0082\u0001\u0000\u0000\u0000\u0082"+
		"\t\u0001\u0000\u0000\u0000\u0083\u0081\u0001\u0000\u0000\u0000\u0084\u0089"+
		"\u0003\u000e\u0007\u0000\u0085\u0089\u0003\u0010\b\u0000\u0086\u0089\u0003"+
		"8\u001c\u0000\u0087\u0089\u0003\f\u0006\u0000\u0088\u0084\u0001\u0000"+
		"\u0000\u0000\u0088\u0085\u0001\u0000\u0000\u0000\u0088\u0086\u0001\u0000"+
		"\u0000\u0000\u0088\u0087\u0001\u0000\u0000\u0000\u0089\u000b\u0001\u0000"+
		"\u0000\u0000\u008a\u008c\u0005\"\u0000\u0000\u008b\u008d\u0003D\"\u0000"+
		"\u008c\u008b\u0001\u0000\u0000\u0000\u008c\u008d\u0001\u0000\u0000\u0000"+
		"\u008d\u0092\u0001\u0000\u0000\u0000\u008e\u008f\u0005F\u0000\u0000\u008f"+
		"\u0091\u0003D\"\u0000\u0090\u008e\u0001\u0000\u0000\u0000\u0091\u0094"+
		"\u0001\u0000\u0000\u0000\u0092\u0090\u0001\u0000\u0000\u0000\u0092\u0093"+
		"\u0001\u0000\u0000\u0000\u0093\u0096\u0001\u0000\u0000\u0000\u0094\u0092"+
		"\u0001\u0000\u0000\u0000\u0095\u0097\u0005F\u0000\u0000\u0096\u0095\u0001"+
		"\u0000\u0000\u0000\u0096\u0097\u0001\u0000\u0000\u0000\u0097\r\u0001\u0000"+
		"\u0000\u0000\u0098\u009a\u0005\u001a\u0000\u0000\u0099\u009b\u0003\u0012"+
		"\t\u0000\u009a\u0099\u0001\u0000\u0000\u0000\u009a\u009b\u0001\u0000\u0000"+
		"\u0000\u009b\u009d\u0001\u0000\u0000\u0000\u009c\u009e\u0003 \u0010\u0000"+
		"\u009d\u009c\u0001\u0000\u0000\u0000\u009d\u009e\u0001\u0000\u0000\u0000"+
		"\u009e\u009f\u0001\u0000\u0000\u0000\u009f\u00a1\u0005\u0018\u0000\u0000"+
		"\u00a0\u00a2\u0005 \u0000\u0000\u00a1\u00a0\u0001\u0000\u0000\u0000\u00a1"+
		"\u00a2\u0001\u0000\u0000\u0000\u00a2\u00a3\u0001\u0000\u0000\u0000\u00a3"+
		"\u00b1\u0003@ \u0000\u00a4\u00a6\u0005\u0018\u0000\u0000\u00a5\u00a7\u0005"+
		" \u0000\u0000\u00a6\u00a5\u0001\u0000\u0000\u0000\u00a6\u00a7\u0001\u0000"+
		"\u0000\u0000\u00a7\u00a8\u0001\u0000\u0000\u0000\u00a8\u00a9\u0003@ \u0000"+
		"\u00a9\u00ab\u0005\u001a\u0000\u0000\u00aa\u00ac\u0003\u0012\t\u0000\u00ab"+
		"\u00aa\u0001\u0000\u0000\u0000\u00ab\u00ac\u0001\u0000\u0000\u0000\u00ac"+
		"\u00ae\u0001\u0000\u0000\u0000\u00ad\u00af\u0003 \u0010\u0000\u00ae\u00ad"+
		"\u0001\u0000\u0000\u0000\u00ae\u00af\u0001\u0000\u0000\u0000\u00af\u00b1"+
		"\u0001\u0000\u0000\u0000\u00b0\u0098\u0001\u0000\u0000\u0000\u00b0\u00a4"+
		"\u0001\u0000\u0000\u0000\u00b1\u000f\u0001\u0000\u0000\u0000\u00b2\u00b4"+
		"\u0005\u001b\u0000\u0000\u00b3\u00b5\u0003\u0014\n\u0000\u00b4\u00b3\u0001"+
		"\u0000\u0000\u0000\u00b4\u00b5\u0001\u0000\u0000\u0000\u00b5\u00b7\u0001"+
		"\u0000\u0000\u0000\u00b6\u00b8\u0003 \u0010\u0000\u00b7\u00b6\u0001\u0000"+
		"\u0000\u0000\u00b7\u00b8\u0001\u0000\u0000\u0000\u00b8\u00b9\u0001\u0000"+
		"\u0000\u0000\u00b9\u00bb\u0005\u0019\u0000\u0000\u00ba\u00bc\u0003F#\u0000"+
		"\u00bb\u00ba\u0001\u0000\u0000\u0000\u00bb\u00bc\u0001\u0000\u0000\u0000"+
		"\u00bc\u00be\u0001\u0000\u0000\u0000\u00bd\u00bf\u0005 \u0000\u0000\u00be"+
		"\u00bd\u0001\u0000\u0000\u0000\u00be\u00bf\u0001\u0000\u0000\u0000\u00bf"+
		"\u00c0\u0001\u0000\u0000\u0000\u00c0\u00d1\u0003@ \u0000\u00c1\u00c3\u0005"+
		"\u0019\u0000\u0000\u00c2\u00c4\u0003F#\u0000\u00c3\u00c2\u0001\u0000\u0000"+
		"\u0000\u00c3\u00c4\u0001\u0000\u0000\u0000\u00c4\u00c6\u0001\u0000\u0000"+
		"\u0000\u00c5\u00c7\u0005 \u0000\u0000\u00c6\u00c5\u0001\u0000\u0000\u0000"+
		"\u00c6\u00c7\u0001\u0000\u0000\u0000\u00c7\u00c8\u0001\u0000\u0000\u0000"+
		"\u00c8\u00c9\u0003@ \u0000\u00c9\u00cb\u0005\u001b\u0000\u0000\u00ca\u00cc"+
		"\u0003\u0014\n\u0000\u00cb\u00ca\u0001\u0000\u0000\u0000\u00cb\u00cc\u0001"+
		"\u0000\u0000\u0000\u00cc\u00ce\u0001\u0000\u0000\u0000\u00cd\u00cf\u0003"+
		" \u0010\u0000\u00ce\u00cd\u0001\u0000\u0000\u0000\u00ce\u00cf\u0001\u0000"+
		"\u0000\u0000\u00cf\u00d1\u0001\u0000\u0000\u0000\u00d0\u00b2\u0001\u0000"+
		"\u0000\u0000\u00d0\u00c1\u0001\u0000\u0000\u0000\u00d1\u0011\u0001\u0000"+
		"\u0000\u0000\u00d2\u00d3\u0003\u0016\u000b\u0000\u00d3\u0013\u0001\u0000"+
		"\u0000\u0000\u00d4\u00d5\u0003\u0016\u000b\u0000\u00d5\u0015\u0001\u0000"+
		"\u0000\u0000\u00d6\u00db\u0003\u0018\f\u0000\u00d7\u00d8\u0005F\u0000"+
		"\u0000\u00d8\u00da\u0003\u0018\f\u0000\u00d9\u00d7\u0001\u0000\u0000\u0000"+
		"\u00da\u00dd\u0001\u0000\u0000\u0000\u00db\u00d9\u0001\u0000\u0000\u0000"+
		"\u00db\u00dc\u0001\u0000\u0000\u0000\u00dc\u00df\u0001\u0000\u0000\u0000"+
		"\u00dd\u00db\u0001\u0000\u0000\u0000\u00de\u00e0\u0005F\u0000\u0000\u00df"+
		"\u00de\u0001\u0000\u0000\u0000\u00df\u00e0\u0001\u0000\u0000\u0000\u00e0"+
		"\u0017\u0001\u0000\u0000\u0000\u00e1\u00e3\u0003\u001a\r\u0000\u00e2\u00e1"+
		"\u0001\u0000\u0000\u0000\u00e2\u00e3\u0001\u0000\u0000\u0000\u00e3\u00e4"+
		"\u0001\u0000\u0000\u0000\u00e4\u00e6\u0003&\u0013\u0000\u00e5\u00e7\u0003"+
		"(\u0014\u0000\u00e6\u00e5\u0001\u0000\u0000\u0000\u00e6\u00e7\u0001\u0000"+
		"\u0000\u0000\u00e7\u00ee\u0001\u0000\u0000\u0000\u00e8\u00ea\u0003\u001a"+
		"\r\u0000\u00e9\u00eb\u0003(\u0014\u0000\u00ea\u00e9\u0001\u0000\u0000"+
		"\u0000\u00ea\u00eb\u0001\u0000\u0000\u0000\u00eb\u00ee\u0001\u0000\u0000"+
		"\u0000\u00ec\u00ee\u0003(\u0014\u0000\u00ed\u00e2\u0001\u0000\u0000\u0000"+
		"\u00ed\u00e8\u0001\u0000\u0000\u0000\u00ed\u00ec\u0001\u0000\u0000\u0000"+
		"\u00ee\u0019\u0001\u0000\u0000\u0000\u00ef\u00f0\u0003\u001c\u000e\u0000"+
		"\u00f0\u00f1\u0003\u001e\u000f\u0000\u00f1\u00f5\u0001\u0000\u0000\u0000"+
		"\u00f2\u00f5\u0003\u001e\u000f\u0000\u00f3\u00f5\u0003\u001c\u000e\u0000"+
		"\u00f4\u00ef\u0001\u0000\u0000\u0000\u00f4\u00f2\u0001\u0000\u0000\u0000"+
		"\u00f4\u00f3\u0001\u0000\u0000\u0000\u00f5\u001b\u0001\u0000\u0000\u0000"+
		"\u00f6\u00f8\u0003L&\u0000\u00f7\u00f9\u0005 \u0000\u0000\u00f8\u00f7"+
		"\u0001\u0000\u0000\u0000\u00f8\u00f9\u0001\u0000\u0000\u0000\u00f9\u001d"+
		"\u0001\u0000\u0000\u0000\u00fa\u00fb\u0005\u001f\u0000\u0000\u00fb\u00fd"+
		"\u0003L&\u0000\u00fc\u00fe\u0005 \u0000\u0000\u00fd\u00fc\u0001\u0000"+
		"\u0000\u0000\u00fd\u00fe\u0001\u0000\u0000\u0000\u00fe\u001f\u0001\u0000"+
		"\u0000\u0000\u00ff\u0100\u0005!\u0000\u0000\u0100\u0101\u0003$\u0012\u0000"+
		"\u0101!\u0001\u0000\u0000\u0000\u0102\u0113\u0003H$\u0000\u0103\u0105"+
		"\u0005G\u0000\u0000\u0104\u0106\u0003H$\u0000\u0105\u0104\u0001\u0000"+
		"\u0000\u0000\u0105\u0106\u0001\u0000\u0000\u0000\u0106\u0111\u0001\u0000"+
		"\u0000\u0000\u0107\u0109\u0005G\u0000\u0000\u0108\u010a\u0003H$\u0000"+
		"\u0109\u0108\u0001\u0000\u0000\u0000\u0109\u010a\u0001\u0000\u0000\u0000"+
		"\u010a\u010f\u0001\u0000\u0000\u0000\u010b\u010d\u0005G\u0000\u0000\u010c"+
		"\u010e\u0003H$\u0000\u010d\u010c\u0001\u0000\u0000\u0000\u010d\u010e\u0001"+
		"\u0000\u0000\u0000\u010e\u0110\u0001\u0000\u0000\u0000\u010f\u010b\u0001"+
		"\u0000\u0000\u0000\u010f\u0110\u0001\u0000\u0000\u0000\u0110\u0112\u0001"+
		"\u0000\u0000\u0000\u0111\u0107\u0001\u0000\u0000\u0000\u0111\u0112\u0001"+
		"\u0000\u0000\u0000\u0112\u0114\u0001\u0000\u0000\u0000\u0113\u0103\u0001"+
		"\u0000\u0000\u0000\u0113\u0114\u0001\u0000\u0000\u0000\u0114\u0117\u0001"+
		"\u0000\u0000\u0000\u0115\u0117\u0003J%\u0000\u0116\u0102\u0001\u0000\u0000"+
		"\u0000\u0116\u0115\u0001\u0000\u0000\u0000\u0117#\u0001\u0000\u0000\u0000"+
		"\u0118\u011d\u0003\"\u0011\u0000\u0119\u011a\u0005F\u0000\u0000\u011a"+
		"\u011c\u0003\"\u0011\u0000\u011b\u0119\u0001\u0000\u0000\u0000\u011c\u011f"+
		"\u0001\u0000\u0000\u0000\u011d\u011b\u0001\u0000\u0000\u0000\u011d\u011e"+
		"\u0001\u0000\u0000\u0000\u011e\u0121\u0001\u0000\u0000\u0000\u011f\u011d"+
		"\u0001\u0000\u0000\u0000\u0120\u0122\u0005F\u0000\u0000\u0121\u0120\u0001"+
		"\u0000\u0000\u0000\u0121\u0122\u0001\u0000\u0000\u0000\u0122%\u0001\u0000"+
		"\u0000\u0000\u0123\u0128\u0003\"\u0011\u0000\u0124\u0125\u0005\r\u0000"+
		"\u0000\u0125\u0127\u0003\"\u0011\u0000\u0126\u0124\u0001\u0000\u0000\u0000"+
		"\u0127\u012a\u0001\u0000\u0000\u0000\u0128\u0126\u0001\u0000\u0000\u0000"+
		"\u0128\u0129\u0001\u0000\u0000\u0000\u0129\u012c\u0001\u0000\u0000\u0000"+
		"\u012a\u0128\u0001\u0000\u0000\u0000\u012b\u012d\u0005\r\u0000\u0000\u012c"+
		"\u012b\u0001\u0000\u0000\u0000\u012c\u012d\u0001\u0000\u0000\u0000\u012d"+
		"\'\u0001\u0000\u0000\u0000\u012e\u012f\u0005&\u0000\u0000\u012f\u0133"+
		"\u0003*\u0015\u0000\u0130\u0131\u0005%\u0000\u0000\u0131\u0133\u0003*"+
		"\u0015\u0000\u0132\u012e\u0001\u0000\u0000\u0000\u0132\u0130\u0001\u0000"+
		"\u0000\u0000\u0133)\u0001\u0000\u0000\u0000\u0134\u0135\u0006\u0015\uffff"+
		"\uffff\u0000\u0135\u0136\u0005J\u0000\u0000\u0136\u0137\u0003*\u0015\u0000"+
		"\u0137\u0138\u0005K\u0000\u0000\u0138\u0144\u0001\u0000\u0000\u0000\u0139"+
		"\u013a\u0005\u000b\u0000\u0000\u013a\u0144\u0003*\u0015\u0004\u013b\u013d"+
		"\u0005\'\u0000\u0000\u013c\u013e\u0005(\u0000\u0000\u013d\u013c\u0001"+
		"\u0000\u0000\u0000\u013d\u013e\u0001\u0000\u0000\u0000\u013e\u0141\u0001"+
		"\u0000\u0000\u0000\u013f\u0141\u0005(\u0000\u0000\u0140\u013b\u0001\u0000"+
		"\u0000\u0000\u0140\u013f\u0001\u0000\u0000\u0000\u0141\u0142\u0001\u0000"+
		"\u0000\u0000\u0142\u0144\u0003,\u0016\u0000\u0143\u0134\u0001\u0000\u0000"+
		"\u0000\u0143\u0139\u0001\u0000\u0000\u0000\u0143\u0140\u0001\u0000\u0000"+
		"\u0000\u0144\u014d\u0001\u0000\u0000\u0000\u0145\u0146\n\u0003\u0000\u0000"+
		"\u0146\u0147\u0005\f\u0000\u0000\u0147\u014c\u0003*\u0015\u0004\u0148"+
		"\u0149\n\u0002\u0000\u0000\u0149\u014a\u0005\r\u0000\u0000\u014a\u014c"+
		"\u0003*\u0015\u0003\u014b\u0145\u0001\u0000\u0000\u0000\u014b\u0148\u0001"+
		"\u0000\u0000\u0000\u014c\u014f\u0001\u0000\u0000\u0000\u014d\u014b\u0001"+
		"\u0000\u0000\u0000\u014d\u014e\u0001\u0000\u0000\u0000\u014e+\u0001\u0000"+
		"\u0000\u0000\u014f\u014d\u0001\u0000\u0000\u0000\u0150\u0151\u0003H$\u0000"+
		"\u0151\u0152\u0005G\u0000\u0000\u0152\u0157\u0003H$\u0000\u0153\u0154"+
		"\u0005H\u0000\u0000\u0154\u0156\u0003H$\u0000\u0155\u0153\u0001\u0000"+
		"\u0000\u0000\u0156\u0159\u0001\u0000\u0000\u0000\u0157\u0155\u0001\u0000"+
		"\u0000\u0000\u0157\u0158\u0001\u0000\u0000\u0000\u0158\u0163\u0001\u0000"+
		"\u0000\u0000\u0159\u0157\u0001\u0000\u0000\u0000\u015a\u015f\u0003H$\u0000"+
		"\u015b\u015c\u0005H\u0000\u0000\u015c\u015e\u0003H$\u0000\u015d\u015b"+
		"\u0001\u0000\u0000\u0000\u015e\u0161\u0001\u0000\u0000\u0000\u015f\u015d"+
		"\u0001\u0000\u0000\u0000\u015f\u0160\u0001\u0000\u0000\u0000\u0160\u0163"+
		"\u0001\u0000\u0000\u0000\u0161\u015f\u0001\u0000\u0000\u0000\u0162\u0150"+
		"\u0001\u0000\u0000\u0000\u0162\u015a\u0001\u0000\u0000\u0000\u0163-\u0001"+
		"\u0000\u0000\u0000\u0164\u0165\u0005 \u0000\u0000\u0165\u0171\u00054\u0000"+
		"\u0000\u0166\u016b\u00030\u0018\u0000\u0167\u0168\u0005F\u0000\u0000\u0168"+
		"\u016a\u00030\u0018\u0000\u0169\u0167\u0001\u0000\u0000\u0000\u016a\u016d"+
		"\u0001\u0000\u0000\u0000\u016b\u0169\u0001\u0000\u0000\u0000\u016b\u016c"+
		"\u0001\u0000\u0000\u0000\u016c\u016e\u0001\u0000\u0000\u0000\u016d\u016b"+
		"\u0001\u0000\u0000\u0000\u016e\u016f\u00054\u0000\u0000\u016f\u0171\u0001"+
		"\u0000\u0000\u0000\u0170\u0164\u0001\u0000\u0000\u0000\u0170\u0166\u0001"+
		"\u0000\u0000\u0000\u0171/\u0001\u0000\u0000\u0000\u0172\u0173\u0007\u0001"+
		"\u0000\u0000\u01731\u0001\u0000\u0000\u0000\u0174\u0175\u0007\u0002\u0000"+
		"\u0000\u0175\u0176\u00034\u001a\u0000\u01763\u0001\u0000\u0000\u0000\u0177"+
		"\u017c\u00036\u001b\u0000\u0178\u0179\u0005F\u0000\u0000\u0179\u017b\u0003"+
		"6\u001b\u0000\u017a\u0178\u0001\u0000\u0000\u0000\u017b\u017e\u0001\u0000"+
		"\u0000\u0000\u017c\u017a\u0001\u0000\u0000\u0000\u017c\u017d\u0001\u0000"+
		"\u0000\u0000\u017d5\u0001\u0000\u0000\u0000\u017e\u017c\u0001\u0000\u0000"+
		"\u0000\u017f\u0182\u0003L&\u0000\u0180\u0181\u0005I\u0000\u0000\u0181"+
		"\u0183\u0003L&\u0000\u0182\u0180\u0001\u0000\u0000\u0000\u0182\u0183\u0001"+
		"\u0000\u0000\u0000\u01837\u0001\u0000\u0000\u0000\u0184\u0185\u0005\u0001"+
		"\u0000\u0000\u0185\u0186\u0003:\u001d\u0000\u0186\u0187\u0005\u0002\u0000"+
		"\u0000\u0187\u0190\u0003\b\u0004\u0000\u0188\u0189\u0005\u0003\u0000\u0000"+
		"\u0189\u018a\u0005\u0001\u0000\u0000\u018a\u018b\u0003:\u001d\u0000\u018b"+
		"\u018c\u0005\u0002\u0000\u0000\u018c\u018d\u0003\b\u0004\u0000\u018d\u018f"+
		"\u0001\u0000\u0000\u0000\u018e\u0188\u0001\u0000\u0000\u0000\u018f\u0192"+
		"\u0001\u0000\u0000\u0000\u0190\u018e\u0001\u0000\u0000\u0000\u0190\u0191"+
		"\u0001\u0000\u0000\u0000\u0191\u0195\u0001\u0000\u0000\u0000\u0192\u0190"+
		"\u0001\u0000\u0000\u0000\u0193\u0194\u0005\u0003\u0000\u0000\u0194\u0196"+
		"\u0003\b\u0004\u0000\u0195\u0193\u0001\u0000\u0000\u0000\u0195\u0196\u0001"+
		"\u0000\u0000\u0000\u0196\u0197\u0001\u0000\u0000\u0000\u0197\u0198\u0005"+
		"C\u0000\u0000\u01989\u0001\u0000\u0000\u0000\u0199\u019a\u0006\u001d\uffff"+
		"\uffff\u0000\u019a\u01ba\u0005\t\u0000\u0000\u019b\u01ba\u0005\n\u0000"+
		"\u0000\u019c\u019d\u0005J\u0000\u0000\u019d\u019e\u0003:\u001d\u0000\u019e"+
		"\u019f\u0005K\u0000\u0000\u019f\u01ba\u0001\u0000\u0000\u0000\u01a0\u01a1"+
		"\u0005\u000b\u0000\u0000\u01a1\u01ba\u0003:\u001d\u0005\u01a2\u01a4\u0003"+
		">\u001f\u0000\u01a3\u01a2\u0001\u0000\u0000\u0000\u01a3\u01a4\u0001\u0000"+
		"\u0000\u0000\u01a4\u01a5\u0001\u0000\u0000\u0000\u01a5\u01a6\u0003@ \u0000"+
		"\u01a6\u01a7\u0005\u0004\u0000\u0000\u01a7\u01a8\u0003<\u001e\u0000\u01a8"+
		"\u01aa\u0003L&\u0000\u01a9\u01ab\u0003&\u0013\u0000\u01aa\u01a9\u0001"+
		"\u0000\u0000\u0000\u01aa\u01ab\u0001\u0000\u0000\u0000\u01ab\u01ad\u0001"+
		"\u0000\u0000\u0000\u01ac\u01ae\u0003(\u0014\u0000\u01ad\u01ac\u0001\u0000"+
		"\u0000\u0000\u01ad\u01ae\u0001\u0000\u0000\u0000\u01ae\u01b1\u0001\u0000"+
		"\u0000\u0000\u01af\u01b0\u0005!\u0000\u0000\u01b0\u01b2\u0003$\u0012\u0000"+
		"\u01b1\u01af\u0001\u0000\u0000\u0000\u01b1\u01b2\u0001\u0000\u0000\u0000"+
		"\u01b2\u01ba\u0001\u0000\u0000\u0000\u01b3\u01b7\u0005@\u0000\u0000\u01b4"+
		"\u01b5\u0003<\u001e\u0000\u01b5\u01b6\u0003L&\u0000\u01b6\u01b8\u0001"+
		"\u0000\u0000\u0000\u01b7\u01b4\u0001\u0000\u0000\u0000\u01b7\u01b8\u0001"+
		"\u0000\u0000\u0000\u01b8\u01ba\u0001\u0000\u0000\u0000\u01b9\u0199\u0001"+
		"\u0000\u0000\u0000\u01b9\u019b\u0001\u0000\u0000\u0000\u01b9\u019c\u0001"+
		"\u0000\u0000\u0000\u01b9\u01a0\u0001\u0000\u0000\u0000\u01b9\u01a3\u0001"+
		"\u0000\u0000\u0000\u01b9\u01b3\u0001\u0000\u0000\u0000\u01ba\u01c3\u0001"+
		"\u0000\u0000\u0000\u01bb\u01bc\n\u0004\u0000\u0000\u01bc\u01bd\u0005\f"+
		"\u0000\u0000\u01bd\u01c2\u0003:\u001d\u0005\u01be\u01bf\n\u0003\u0000"+
		"\u0000\u01bf\u01c0\u0005\r\u0000\u0000\u01c0\u01c2\u0003:\u001d\u0004"+
		"\u01c1\u01bb\u0001\u0000\u0000\u0000\u01c1\u01be\u0001\u0000\u0000\u0000"+
		"\u01c2\u01c5\u0001\u0000\u0000\u0000\u01c3\u01c1\u0001\u0000\u0000\u0000"+
		"\u01c3\u01c4\u0001\u0000\u0000\u0000\u01c4;\u0001\u0000\u0000\u0000\u01c5"+
		"\u01c3\u0001\u0000\u0000\u0000\u01c6\u01c7\u0007\u0003\u0000\u0000\u01c7"+
		"=\u0001\u0000\u0000\u0000\u01c8\u01c9\u0007\u0004\u0000\u0000\u01c9?\u0001"+
		"\u0000\u0000\u0000\u01ca\u01cf\u0003D\"\u0000\u01cb\u01cc\u0005F\u0000"+
		"\u0000\u01cc\u01ce\u0003D\"\u0000\u01cd\u01cb\u0001\u0000\u0000\u0000"+
		"\u01ce\u01d1\u0001\u0000\u0000\u0000\u01cf\u01cd\u0001\u0000\u0000\u0000"+
		"\u01cf\u01d0\u0001\u0000\u0000\u0000\u01d0\u01d3\u0001\u0000\u0000\u0000"+
		"\u01d1\u01cf\u0001\u0000\u0000\u0000\u01d2\u01d4\u0003B!\u0000\u01d3\u01d2"+
		"\u0001\u0000\u0000\u0000\u01d3\u01d4\u0001\u0000\u0000\u0000\u01d4\u01d6"+
		"\u0001\u0000\u0000\u0000\u01d5\u01d7\u0003.\u0017\u0000\u01d6\u01d5\u0001"+
		"\u0000\u0000\u0000\u01d6\u01d7\u0001\u0000\u0000\u0000\u01d7\u01d9\u0001"+
		"\u0000\u0000\u0000\u01d8\u01da\u00032\u0019\u0000\u01d9\u01d8\u0001\u0000"+
		"\u0000\u0000\u01d9\u01da\u0001\u0000\u0000\u0000\u01daA\u0001\u0000\u0000"+
		"\u0000\u01db\u01dc\u0005)\u0000\u0000\u01dc\u01dd\u0005*\u0000\u0000\u01dd"+
		"\u01de\u0005+\u0000\u0000\u01de\u01df\u0007\u0005\u0000\u0000\u01dfC\u0001"+
		"\u0000\u0000\u0000\u01e0\u01e3\u0003H$\u0000\u01e1\u01e3\u0003J%\u0000"+
		"\u01e2\u01e0\u0001\u0000\u0000\u0000\u01e2\u01e1\u0001\u0000\u0000\u0000"+
		"\u01e3E\u0001\u0000\u0000\u0000\u01e4\u01e5\u0005#\u0000\u0000\u01e5\u01e6"+
		"\u0007\u0002\u0000\u0000\u01e6\u01e7\u0005$\u0000\u0000\u01e7G\u0001\u0000"+
		"\u0000\u0000\u01e8\u01e9\u0007\u0006\u0000\u0000\u01e9I\u0001\u0000\u0000"+
		"\u0000\u01ea\u01eb\u0005O\u0000\u0000\u01ebK\u0001\u0000\u0000\u0000\u01ec"+
		"\u01ed\u0005M\u0000\u0000\u01edM\u0001\u0000\u0000\u0000MOTilosy|\u0081"+
		"\u0088\u008c\u0092\u0096\u009a\u009d\u00a1\u00a6\u00ab\u00ae\u00b0\u00b4"+
		"\u00b7\u00bb\u00be\u00c3\u00c6\u00cb\u00ce\u00d0\u00db\u00df\u00e2\u00e6"+
		"\u00ea\u00ed\u00f4\u00f8\u00fd\u0105\u0109\u010d\u010f\u0111\u0113\u0116"+
		"\u011d\u0121\u0128\u012c\u0132\u013d\u0140\u0143\u014b\u014d\u0157\u015f"+
		"\u0162\u016b\u0170\u017c\u0182\u0190\u0195\u01a3\u01aa\u01ad\u01b1\u01b7"+
		"\u01b9\u01c1\u01c3\u01cf\u01d3\u01d6\u01d9\u01e2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}