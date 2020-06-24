/*
 * Copyright (c) 2017-2020, Oracle and/or its affiliates.
 * Copyright (c) 2014 by Bart Kiers
 *
 * The MIT License (MIT)
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
// Checkstyle: stop
// JaCoCo Exclude
//@formatter:off
// Generated from graalpython/com.oracle.graal.python/src/com/oracle/graal/python/parser/antlr/Python3.g4 by ANTLR 4.7.2
package com.oracle.graal.python.parser.antlr;

import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.PythonSSTNodeFactory;
import com.oracle.graal.python.parser.ScopeEnvironment;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.parser.sst.*;

import com.oracle.graal.python.parser.sst.SSTNode;

import com.oracle.truffle.api.frame.FrameDescriptor;

import java.util.Arrays;

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings("all")
public class Python3Parser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		STRING=1, DEF=2, RETURN=3, RAISE=4, FROM=5, IMPORT=6, AS=7, GLOBAL=8, 
		NONLOCAL=9, ASSERT=10, IF=11, ELIF=12, ELSE=13, WHILE=14, FOR=15, IN=16, 
		TRY=17, FINALLY=18, WITH=19, EXCEPT=20, LAMBDA=21, OR=22, AND=23, NOT=24, 
		IS=25, NONE=26, TRUE=27, FALSE=28, CLASS=29, YIELD=30, DEL=31, PASS=32, 
		CONTINUE=33, BREAK=34, ASYNC=35, AWAIT=36, NEWLINE=37, NAME=38, STRING_LITERAL=39, 
		BYTES_LITERAL=40, DECIMAL_INTEGER=41, OCT_INTEGER=42, HEX_INTEGER=43, 
		BIN_INTEGER=44, FLOAT_NUMBER=45, IMAG_NUMBER=46, DOT=47, ELLIPSIS=48, 
		STAR=49, OPEN_PAREN=50, CLOSE_PAREN=51, COMMA=52, COLON=53, SEMI_COLON=54, 
		POWER=55, ASSIGN=56, OPEN_BRACK=57, CLOSE_BRACK=58, OR_OP=59, XOR=60, 
		AND_OP=61, LEFT_SHIFT=62, RIGHT_SHIFT=63, ADD=64, MINUS=65, DIV=66, MOD=67, 
		IDIV=68, NOT_OP=69, OPEN_BRACE=70, CLOSE_BRACE=71, LESS_THAN=72, GREATER_THAN=73, 
		EQUALS=74, GT_EQ=75, LT_EQ=76, NOT_EQ_1=77, NOT_EQ_2=78, AT=79, ARROW=80, 
		ADD_ASSIGN=81, SUB_ASSIGN=82, MULT_ASSIGN=83, AT_ASSIGN=84, DIV_ASSIGN=85, 
		MOD_ASSIGN=86, AND_ASSIGN=87, OR_ASSIGN=88, XOR_ASSIGN=89, LEFT_SHIFT_ASSIGN=90, 
		RIGHT_SHIFT_ASSIGN=91, POWER_ASSIGN=92, IDIV_ASSIGN=93, LONG_QUOTES1=94, 
		LONG_QUOTES2=95, SKIP_=96, BOM=97, UNKNOWN_CHAR=98, INDENT=99, DEDENT=100, 
		INDENT_ERROR=101, TAB_ERROR=102;
	public static final int
		RULE_single_input = 0, RULE_file_input = 1, RULE_withArguments_input = 2, 
		RULE_eval_input = 3, RULE_decorator = 4, RULE_decorators = 5, RULE_decorated = 6, 
		RULE_async_funcdef = 7, RULE_funcdef = 8, RULE_parameters = 9, RULE_typedargslist = 10, 
		RULE_defparameter = 11, RULE_splatparameter = 12, RULE_kwargsparameter = 13, 
		RULE_varargslist = 14, RULE_vdefparameter = 15, RULE_vsplatparameter = 16, 
		RULE_vkwargsparameter = 17, RULE_stmt = 18, RULE_simple_stmt = 19, RULE_small_stmt = 20, 
		RULE_expr_stmt = 21, RULE_testlist_star_expr = 22, RULE_augassign = 23, 
		RULE_del_stmt = 24, RULE_flow_stmt = 25, RULE_return_stmt = 26, RULE_yield_stmt = 27, 
		RULE_raise_stmt = 28, RULE_import_stmt = 29, RULE_import_name = 30, RULE_import_from = 31, 
		RULE_import_as_name = 32, RULE_import_as_names = 33, RULE_dotted_name = 34, 
		RULE_dotted_as_name = 35, RULE_dotted_as_names = 36, RULE_global_stmt = 37, 
		RULE_nonlocal_stmt = 38, RULE_assert_stmt = 39, RULE_compound_stmt = 40, 
		RULE_async_stmt = 41, RULE_if_stmt = 42, RULE_elif_stmt = 43, RULE_while_stmt = 44, 
		RULE_for_stmt = 45, RULE_try_stmt = 46, RULE_except_clause = 47, RULE_with_stmt = 48, 
		RULE_with_item = 49, RULE_suite = 50, RULE_test = 51, RULE_test_nocond = 52, 
		RULE_lambdef = 53, RULE_lambdef_nocond = 54, RULE_or_test = 55, RULE_and_test = 56, 
		RULE_not_test = 57, RULE_comparison = 58, RULE_comp_op = 59, RULE_star_expr = 60, 
		RULE_expr = 61, RULE_xor_expr = 62, RULE_and_expr = 63, RULE_shift_expr = 64, 
		RULE_arith_expr = 65, RULE_term = 66, RULE_factor = 67, RULE_power = 68, 
		RULE_atom_expr = 69, RULE_atom = 70, RULE_subscriptlist = 71, RULE_subscript = 72, 
		RULE_exprlist = 73, RULE_testlist = 74, RULE_dictmaker = 75, RULE_setlisttuplemaker = 76, 
		RULE_classdef = 77, RULE_arglist = 78, RULE_argument = 79, RULE_comp_for = 80, 
		RULE_encoding_decl = 81, RULE_yield_expr = 82;
	private static String[] makeRuleNames() {
		return new String[] {
			"single_input", "file_input", "withArguments_input", "eval_input", "decorator", 
			"decorators", "decorated", "async_funcdef", "funcdef", "parameters", 
			"typedargslist", "defparameter", "splatparameter", "kwargsparameter", 
			"varargslist", "vdefparameter", "vsplatparameter", "vkwargsparameter", 
			"stmt", "simple_stmt", "small_stmt", "expr_stmt", "testlist_star_expr", 
			"augassign", "del_stmt", "flow_stmt", "return_stmt", "yield_stmt", "raise_stmt", 
			"import_stmt", "import_name", "import_from", "import_as_name", "import_as_names", 
			"dotted_name", "dotted_as_name", "dotted_as_names", "global_stmt", "nonlocal_stmt", 
			"assert_stmt", "compound_stmt", "async_stmt", "if_stmt", "elif_stmt", 
			"while_stmt", "for_stmt", "try_stmt", "except_clause", "with_stmt", "with_item", 
			"suite", "test", "test_nocond", "lambdef", "lambdef_nocond", "or_test", 
			"and_test", "not_test", "comparison", "comp_op", "star_expr", "expr", 
			"xor_expr", "and_expr", "shift_expr", "arith_expr", "term", "factor", 
			"power", "atom_expr", "atom", "subscriptlist", "subscript", "exprlist", 
			"testlist", "dictmaker", "setlisttuplemaker", "classdef", "arglist", 
			"argument", "comp_for", "encoding_decl", "yield_expr"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, "'def'", "'return'", "'raise'", "'from'", "'import'", "'as'", 
			"'global'", "'nonlocal'", "'assert'", "'if'", "'elif'", "'else'", "'while'", 
			"'for'", "'in'", "'try'", "'finally'", "'with'", "'except'", "'lambda'", 
			"'or'", "'and'", "'not'", "'is'", "'None'", "'True'", "'False'", "'class'", 
			"'yield'", "'del'", "'pass'", "'continue'", "'break'", "'async'", "'await'", 
			null, null, null, null, null, null, null, null, null, null, "'.'", "'...'", 
			"'*'", "'('", "')'", "','", "':'", "';'", "'**'", "'='", "'['", "']'", 
			"'|'", "'^'", "'&'", "'<<'", "'>>'", "'+'", "'-'", "'/'", "'%'", "'//'", 
			"'~'", "'{'", "'}'", "'<'", "'>'", "'=='", "'>='", "'<='", "'<>'", "'!='", 
			"'@'", "'->'", "'+='", "'-='", "'*='", "'@='", "'/='", "'%='", "'&='", 
			"'|='", "'^='", "'<<='", "'>>='", "'**='", "'//='", "'\"\"\"'", "'''''", 
			null, "'\uFEFF'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "STRING", "DEF", "RETURN", "RAISE", "FROM", "IMPORT", "AS", "GLOBAL", 
			"NONLOCAL", "ASSERT", "IF", "ELIF", "ELSE", "WHILE", "FOR", "IN", "TRY", 
			"FINALLY", "WITH", "EXCEPT", "LAMBDA", "OR", "AND", "NOT", "IS", "NONE", 
			"TRUE", "FALSE", "CLASS", "YIELD", "DEL", "PASS", "CONTINUE", "BREAK", 
			"ASYNC", "AWAIT", "NEWLINE", "NAME", "STRING_LITERAL", "BYTES_LITERAL", 
			"DECIMAL_INTEGER", "OCT_INTEGER", "HEX_INTEGER", "BIN_INTEGER", "FLOAT_NUMBER", 
			"IMAG_NUMBER", "DOT", "ELLIPSIS", "STAR", "OPEN_PAREN", "CLOSE_PAREN", 
			"COMMA", "COLON", "SEMI_COLON", "POWER", "ASSIGN", "OPEN_BRACK", "CLOSE_BRACK", 
			"OR_OP", "XOR", "AND_OP", "LEFT_SHIFT", "RIGHT_SHIFT", "ADD", "MINUS", 
			"DIV", "MOD", "IDIV", "NOT_OP", "OPEN_BRACE", "CLOSE_BRACE", "LESS_THAN", 
			"GREATER_THAN", "EQUALS", "GT_EQ", "LT_EQ", "NOT_EQ_1", "NOT_EQ_2", "AT", 
			"ARROW", "ADD_ASSIGN", "SUB_ASSIGN", "MULT_ASSIGN", "AT_ASSIGN", "DIV_ASSIGN", 
			"MOD_ASSIGN", "AND_ASSIGN", "OR_ASSIGN", "XOR_ASSIGN", "LEFT_SHIFT_ASSIGN", 
			"RIGHT_SHIFT_ASSIGN", "POWER_ASSIGN", "IDIV_ASSIGN", "LONG_QUOTES1", 
			"LONG_QUOTES2", "SKIP_", "BOM", "UNKNOWN_CHAR", "INDENT", "DEDENT", "INDENT_ERROR", 
			"TAB_ERROR"
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
	public String getGrammarFileName() { return "Python3.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }


	    private static class LoopState {
	        public boolean containsBreak;
	        public boolean containsContinue;
	    }
		private PythonSSTNodeFactory factory;
		private ScopeEnvironment scopeEnvironment;
		private LoopState loopState;

		public final LoopState startLoop() {
		    try {
		        return loopState;
		    } finally {
		        loopState = new LoopState();
		    }
		}
		
		public final LoopState saveLoopState() {
			try {
		        return loopState;
		    } finally {
		        loopState = null;
		    }
		}
		
		private Object[] stack = new Object[8];
		private int stackIndex;
		
		public final int start() {
			return stackIndex;
		}
		
		public final void push(Object value) {
			if (stackIndex >= stack.length) {
				stack = Arrays.copyOf(stack, stack.length * 2);
			}
			stack[stackIndex++] = value;
		}
		
		public final Object[] getArray(int start) {
			try {
				return Arrays.copyOfRange(stack, start, stackIndex);
			} finally {
				stackIndex = start;
			}
		}

		public final <T> T[] getArray(int start, Class<? extends T[]> clazz) {
			try {
				return Arrays.copyOfRange(stack, start, stackIndex, clazz);
			} finally {
				stackIndex = start;
			}
		}

		private String[] stringStack = new String[8];
		private int stringStackIndex;
		
		public final int stringStart() {
			return stringStackIndex;
		}
		
		public final void pushString(String value) {
			if (stringStackIndex >= stringStack.length) {
				stringStack = Arrays.copyOf(stringStack, stringStack.length * 2);
			}
			stringStack[stringStackIndex++] = value;
		}
		
		public final String[] getStringArray(int start) {
			try {
				return Arrays.copyOfRange(stringStack, start, stringStackIndex);
			} finally {
				stringStackIndex = start;
			}
		}
	    

	        public void setFactory(PythonSSTNodeFactory factory) {
	            this.factory = factory;
	            scopeEnvironment = factory.getScopeEnvironment();
	        }

	    private static class PythonRecognitionException extends RecognitionException{
	        static final long serialVersionUID = 1L;
	            
	        public PythonRecognitionException(String message, Recognizer<?, ?> recognizer, IntStream input, ParserRuleContext ctx, Token offendingToken) {
	            super(message, recognizer, input, ctx);
	            setOffendingToken(offendingToken);
	        }

	    }

	    private int getStartIndex(RuleNode node) { 
	        return ((ParserRuleContext) node).getStart().getStartIndex();
	    }

	    private int getStartIndex(Token token) {
	        return token.getStartIndex();
	    }

	    private int getStopIndex(RuleNode node) {
	        // add 1 to fit truffle source sections
	        return ((ParserRuleContext) node).getStop().getStopIndex() + 1;
	    }

	    private int getStopIndex(Token token) {
	        int stopIndex;
	        if (token.getType() != NEWLINE) {
	            stopIndex = token.getStopIndex();
	        } else {
	            // We don't have to have new lines in the source section
	            int tokenIndex = token.getTokenIndex();
	            Token tmp = token;
	            while(tmp.getType() == NEWLINE && tokenIndex > 0) {
	                tmp = getTokenStream().get(--tokenIndex);
	            }
	            stopIndex = tmp.getStopIndex();
	        }
	        // add 1 to fit truffle source sections
	        return stopIndex + 1;
	    }

	    /** Get the last offset of the context */
	    private int getLastIndex(ParserRuleContext ctx) {
	    	// ignores ctx
	        return getStopIndex(this._input.get(this._input.index() - 1));
	    }

	public Python3Parser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class Single_inputContext extends ParserRuleContext {
		public boolean interactive;
		public FrameDescriptor curInlineLocals;
		public SSTNode result;
		public com.oracle.graal.python.parser.ScopeInfo scope;
		public ArrayList<StatementNode> list;
		public TerminalNode EOF() { return getToken(Python3Parser.EOF, 0); }
		public List<TerminalNode> NEWLINE() { return getTokens(Python3Parser.NEWLINE); }
		public TerminalNode NEWLINE(int i) {
			return getToken(Python3Parser.NEWLINE, i);
		}
		public Simple_stmtContext simple_stmt() {
			return getRuleContext(Simple_stmtContext.class,0);
		}
		public Compound_stmtContext compound_stmt() {
			return getRuleContext(Compound_stmtContext.class,0);
		}
		public TerminalNode BOM() { return getToken(Python3Parser.BOM, 0); }
		public Single_inputContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public Single_inputContext(ParserRuleContext parent, int invokingState, boolean interactive, FrameDescriptor curInlineLocals) {
			super(parent, invokingState);
			this.interactive = interactive;
			this.curInlineLocals = curInlineLocals;
		}
		@Override public int getRuleIndex() { return RULE_single_input; }
	}

	public final Single_inputContext single_input(boolean interactive,FrameDescriptor curInlineLocals) throws RecognitionException {
		Single_inputContext _localctx = new Single_inputContext(_ctx, getState(), interactive, curInlineLocals);
		enterRule(_localctx, 0, RULE_single_input);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

				    if (!_localctx.interactive && _localctx.curInlineLocals != null) {
			                ScopeInfo functionScope = scopeEnvironment.pushScope("<single_input>", ScopeInfo.ScopeKind.Function, _localctx.curInlineLocals);
			                functionScope.setHasAnnotations(true);
			            } else {
			                scopeEnvironment.pushScope(_localctx.toString(), ScopeInfo.ScopeKind.Module);
			            }
				
			 loopState = null; 
			 int start = start(); 
			setState(170);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==BOM) {
				{
				setState(169);
				match(BOM);
				}
			}

			setState(175);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NEWLINE:
				{
				setState(172);
				match(NEWLINE);
				}
				break;
			case STRING:
			case RETURN:
			case RAISE:
			case FROM:
			case IMPORT:
			case GLOBAL:
			case NONLOCAL:
			case ASSERT:
			case LAMBDA:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case YIELD:
			case DEL:
			case PASS:
			case CONTINUE:
			case BREAK:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case STAR:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				{
				setState(173);
				simple_stmt();
				}
				break;
			case DEF:
			case IF:
			case WHILE:
			case FOR:
			case TRY:
			case WITH:
			case CLASS:
			case ASYNC:
			case AT:
				{
				setState(174);
				compound_stmt();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(180);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NEWLINE) {
				{
				{
				setState(177);
				match(NEWLINE);
				}
				}
				setState(182);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(183);
			match(EOF);
			 _localctx.result =  new BlockSSTNode(getArray(start, SSTNode[].class), getStartIndex(_localctx),  getLastIndex(_localctx)); 

			            if (_localctx.interactive || _localctx.curInlineLocals != null) {
			               scopeEnvironment.popScope();
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

	public static class File_inputContext extends ParserRuleContext {
		public SSTNode result;
		public com.oracle.graal.python.parser.ScopeInfo scope;
		public ArrayList<StatementNode> list;
		public StmtContext stmt;
		public TerminalNode EOF() { return getToken(Python3Parser.EOF, 0); }
		public TerminalNode BOM() { return getToken(Python3Parser.BOM, 0); }
		public List<TerminalNode> NEWLINE() { return getTokens(Python3Parser.NEWLINE); }
		public TerminalNode NEWLINE(int i) {
			return getToken(Python3Parser.NEWLINE, i);
		}
		public List<StmtContext> stmt() {
			return getRuleContexts(StmtContext.class);
		}
		public StmtContext stmt(int i) {
			return getRuleContext(StmtContext.class,i);
		}
		public File_inputContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_file_input; }
	}

	public final File_inputContext file_input() throws RecognitionException {
		File_inputContext _localctx = new File_inputContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_file_input);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			  _localctx.scope = scopeEnvironment.pushScope(_localctx.toString(), ScopeInfo.ScopeKind.Module); 
			 loopState = null; 
			 int start = start(); 
			setState(191);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==BOM) {
				{
				setState(190);
				match(BOM);
				}
			}

			setState(197);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << DEF) | (1L << RETURN) | (1L << RAISE) | (1L << FROM) | (1L << IMPORT) | (1L << GLOBAL) | (1L << NONLOCAL) | (1L << ASSERT) | (1L << IF) | (1L << WHILE) | (1L << FOR) | (1L << TRY) | (1L << WITH) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << CLASS) | (1L << YIELD) | (1L << DEL) | (1L << PASS) | (1L << CONTINUE) | (1L << BREAK) | (1L << ASYNC) | (1L << AWAIT) | (1L << NEWLINE) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)) | (1L << (AT - 64)))) != 0)) {
				{
				setState(195);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case NEWLINE:
					{
					setState(193);
					match(NEWLINE);
					}
					break;
				case STRING:
				case DEF:
				case RETURN:
				case RAISE:
				case FROM:
				case IMPORT:
				case GLOBAL:
				case NONLOCAL:
				case ASSERT:
				case IF:
				case WHILE:
				case FOR:
				case TRY:
				case WITH:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case CLASS:
				case YIELD:
				case DEL:
				case PASS:
				case CONTINUE:
				case BREAK:
				case ASYNC:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case STAR:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
				case AT:
					{
					setState(194);
					_localctx.stmt = stmt();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(199);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(200);
			match(EOF);
			 
			            Token stopToken = (_localctx.stmt!=null?(_localctx.stmt.stop):null);
			            _localctx.result =  new BlockSSTNode(getArray(start, SSTNode[].class), getStartIndex(_localctx), 
			                stopToken != null ?  getStopIndex(stopToken) : getLastIndex(_localctx)); 
			 
			           scopeEnvironment.popScope(); 
			        
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

	public static class WithArguments_inputContext extends ParserRuleContext {
		public boolean interactive;
		public FrameDescriptor curInlineLocals;
		public SSTNode result;
		public com.oracle.graal.python.parser.ScopeInfo scope;
		public ArrayList<StatementNode> list;
		public TerminalNode EOF() { return getToken(Python3Parser.EOF, 0); }
		public TerminalNode BOM() { return getToken(Python3Parser.BOM, 0); }
		public List<TerminalNode> NEWLINE() { return getTokens(Python3Parser.NEWLINE); }
		public TerminalNode NEWLINE(int i) {
			return getToken(Python3Parser.NEWLINE, i);
		}
		public List<StmtContext> stmt() {
			return getRuleContexts(StmtContext.class);
		}
		public StmtContext stmt(int i) {
			return getRuleContext(StmtContext.class,i);
		}
		public WithArguments_inputContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public WithArguments_inputContext(ParserRuleContext parent, int invokingState, boolean interactive, FrameDescriptor curInlineLocals) {
			super(parent, invokingState);
			this.interactive = interactive;
			this.curInlineLocals = curInlineLocals;
		}
		@Override public int getRuleIndex() { return RULE_withArguments_input; }
	}

	public final WithArguments_inputContext withArguments_input(boolean interactive,FrameDescriptor curInlineLocals) throws RecognitionException {
		WithArguments_inputContext _localctx = new WithArguments_inputContext(_ctx, getState(), interactive, curInlineLocals);
		enterRule(_localctx, 4, RULE_withArguments_input);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{

			            ScopeInfo functionScope = scopeEnvironment.pushScope("<withArguments_input>", ScopeInfo.ScopeKind.Function, _localctx.curInlineLocals);
			            functionScope.setHasAnnotations(true);
				    
				
			 loopState = null; 
			 int start = start(); 
			setState(208);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==BOM) {
				{
				setState(207);
				match(BOM);
				}
			}

			setState(214);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << DEF) | (1L << RETURN) | (1L << RAISE) | (1L << FROM) | (1L << IMPORT) | (1L << GLOBAL) | (1L << NONLOCAL) | (1L << ASSERT) | (1L << IF) | (1L << WHILE) | (1L << FOR) | (1L << TRY) | (1L << WITH) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << CLASS) | (1L << YIELD) | (1L << DEL) | (1L << PASS) | (1L << CONTINUE) | (1L << BREAK) | (1L << ASYNC) | (1L << AWAIT) | (1L << NEWLINE) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)) | (1L << (AT - 64)))) != 0)) {
				{
				setState(212);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case NEWLINE:
					{
					setState(210);
					match(NEWLINE);
					}
					break;
				case STRING:
				case DEF:
				case RETURN:
				case RAISE:
				case FROM:
				case IMPORT:
				case GLOBAL:
				case NONLOCAL:
				case ASSERT:
				case IF:
				case WHILE:
				case FOR:
				case TRY:
				case WITH:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case CLASS:
				case YIELD:
				case DEL:
				case PASS:
				case CONTINUE:
				case BREAK:
				case ASYNC:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case STAR:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
				case AT:
					{
					setState(211);
					stmt();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(216);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(217);
			match(EOF);
			 _localctx.result =  new BlockSSTNode(getArray(start, SSTNode[].class), getStartIndex(_localctx),  getLastIndex(_localctx)); 

			            scopeEnvironment.popScope();
				
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

	public static class Eval_inputContext extends ParserRuleContext {
		public SSTNode result;
		public com.oracle.graal.python.parser.ScopeInfo scope;
		public TestlistContext testlist;
		public TestlistContext testlist() {
			return getRuleContext(TestlistContext.class,0);
		}
		public TerminalNode EOF() { return getToken(Python3Parser.EOF, 0); }
		public TerminalNode BOM() { return getToken(Python3Parser.BOM, 0); }
		public List<TerminalNode> NEWLINE() { return getTokens(Python3Parser.NEWLINE); }
		public TerminalNode NEWLINE(int i) {
			return getToken(Python3Parser.NEWLINE, i);
		}
		public Eval_inputContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_eval_input; }
	}

	public final Eval_inputContext eval_input() throws RecognitionException {
		Eval_inputContext _localctx = new Eval_inputContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_eval_input);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 scopeEnvironment.pushScope(_localctx.toString(), ScopeInfo.ScopeKind.Module); 
			setState(223);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==BOM) {
				{
				setState(222);
				match(BOM);
				}
			}

			setState(225);
			_localctx.testlist = testlist();
			setState(229);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NEWLINE) {
				{
				{
				setState(226);
				match(NEWLINE);
				}
				}
				setState(231);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(232);
			match(EOF);
			 _localctx.result =  _localctx.testlist.result; 
			scopeEnvironment.popScope(); 
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

	public static class DecoratorContext extends ParserRuleContext {
		public Dotted_nameContext dotted_name;
		public ArglistContext arglist;
		public TerminalNode AT() { return getToken(Python3Parser.AT, 0); }
		public Dotted_nameContext dotted_name() {
			return getRuleContext(Dotted_nameContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(Python3Parser.NEWLINE, 0); }
		public TerminalNode OPEN_PAREN() { return getToken(Python3Parser.OPEN_PAREN, 0); }
		public ArglistContext arglist() {
			return getRuleContext(ArglistContext.class,0);
		}
		public TerminalNode CLOSE_PAREN() { return getToken(Python3Parser.CLOSE_PAREN, 0); }
		public DecoratorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_decorator; }
	}

	public final DecoratorContext decorator() throws RecognitionException {
		DecoratorContext _localctx = new DecoratorContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_decorator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 ArgListBuilder args = null ;
			setState(237);
			match(AT);
			setState(238);
			_localctx.dotted_name = dotted_name();
			setState(244);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN_PAREN) {
				{
				setState(239);
				match(OPEN_PAREN);
				setState(240);
				_localctx.arglist = arglist();
				setState(241);
				match(CLOSE_PAREN);
				args = _localctx.arglist.result; 
				}
			}

			setState(246);
			match(NEWLINE);
			   
			        String dottedName = _localctx.dotted_name.result;
			        if (dottedName.contains(".")) {
			            factory.getScopeEnvironment().addSeenVar(dottedName.split("\\.")[0]);
			        } else {
			            factory.getScopeEnvironment().addSeenVar(dottedName);
			        }
			        push( new DecoratorSSTNode(dottedName, args, getStartIndex(_localctx), getLastIndex(_localctx))); 
			    
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

	public static class DecoratorsContext extends ParserRuleContext {
		public DecoratorSSTNode[] result;
		public List<DecoratorContext> decorator() {
			return getRuleContexts(DecoratorContext.class);
		}
		public DecoratorContext decorator(int i) {
			return getRuleContext(DecoratorContext.class,i);
		}
		public DecoratorsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_decorators; }
	}

	public final DecoratorsContext decorators() throws RecognitionException {
		DecoratorsContext _localctx = new DecoratorsContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_decorators);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			int start = start();
			setState(251); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(250);
				decorator();
				}
				}
				setState(253); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==AT );
			_localctx.result =  getArray(start, DecoratorSSTNode[].class);
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

	public static class DecoratedContext extends ParserRuleContext {
		public DecoratorsContext decorators;
		public DecoratorsContext decorators() {
			return getRuleContext(DecoratorsContext.class,0);
		}
		public ClassdefContext classdef() {
			return getRuleContext(ClassdefContext.class,0);
		}
		public FuncdefContext funcdef() {
			return getRuleContext(FuncdefContext.class,0);
		}
		public Async_funcdefContext async_funcdef() {
			return getRuleContext(Async_funcdefContext.class,0);
		}
		public DecoratedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_decorated; }
	}

	public final DecoratedContext decorated() throws RecognitionException {
		DecoratedContext _localctx = new DecoratedContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_decorated);
		try {
			enterOuterAlt(_localctx, 1);
			{
			 SSTNode decor; 
			setState(258);
			_localctx.decorators = decorators();
			setState(262);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CLASS:
				{
				setState(259);
				classdef();
				}
				break;
			case DEF:
				{
				setState(260);
				funcdef();
				}
				break;
			case ASYNC:
				{
				setState(261);
				async_funcdef();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			 stack[stackIndex-1] = new DecoratedSSTNode(_localctx.decorators.result, (SSTNode)stack[stackIndex-1], getStartIndex(_localctx), getLastIndex(_localctx)); 
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

	public static class Async_funcdefContext extends ParserRuleContext {
		public TerminalNode ASYNC() { return getToken(Python3Parser.ASYNC, 0); }
		public FuncdefContext funcdef() {
			return getRuleContext(FuncdefContext.class,0);
		}
		public Async_funcdefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_async_funcdef; }
	}

	public final Async_funcdefContext async_funcdef() throws RecognitionException {
		Async_funcdefContext _localctx = new Async_funcdefContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_async_funcdef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(266);
			match(ASYNC);
			setState(267);
			funcdef();
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

	public static class FuncdefContext extends ParserRuleContext {
		public Token n;
		public ParametersContext parameters;
		public SuiteContext s;
		public TerminalNode DEF() { return getToken(Python3Parser.DEF, 0); }
		public ParametersContext parameters() {
			return getRuleContext(ParametersContext.class,0);
		}
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public SuiteContext suite() {
			return getRuleContext(SuiteContext.class,0);
		}
		public TerminalNode ARROW() { return getToken(Python3Parser.ARROW, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public FuncdefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_funcdef; }
	}

	public final FuncdefContext funcdef() throws RecognitionException {
		FuncdefContext _localctx = new FuncdefContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_funcdef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(269);
			match(DEF);
			setState(270);
			_localctx.n = match(NAME);
			setState(271);
			_localctx.parameters = parameters();
			setState(274);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ARROW) {
				{
				setState(272);
				match(ARROW);
				setState(273);
				test();
				}
			}

			setState(276);
			match(COLON);
			 
			            String name = _localctx.n.getText(); 
			            ScopeInfo enclosingScope = scopeEnvironment.getCurrentScope();
			            String enclosingClassName = enclosingScope.isInClassScope() ? enclosingScope.getScopeId() : null;
			            ScopeInfo functionScope = scopeEnvironment.pushScope(name, ScopeInfo.ScopeKind.Function);
			            LoopState savedLoopState = saveLoopState();
			            functionScope.setHasAnnotations(true);
			            _localctx.parameters.result.defineParamsInScope(functionScope); 
			        
			setState(278);
			_localctx.s = suite();
			 
			            SSTNode funcDef = new FunctionDefSSTNode(scopeEnvironment.getCurrentScope(), name, enclosingClassName, _localctx.parameters.result, _localctx.s.result, getStartIndex(_localctx), getStopIndex(_localctx.s));
			            scopeEnvironment.popScope();
			            loopState = savedLoopState;
			            push(funcDef);
			        
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

	public static class ParametersContext extends ParserRuleContext {
		public ArgDefListBuilder result;
		public TerminalNode OPEN_PAREN() { return getToken(Python3Parser.OPEN_PAREN, 0); }
		public TerminalNode CLOSE_PAREN() { return getToken(Python3Parser.CLOSE_PAREN, 0); }
		public TypedargslistContext typedargslist() {
			return getRuleContext(TypedargslistContext.class,0);
		}
		public ParametersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameters; }
	}

	public final ParametersContext parameters() throws RecognitionException {
		ParametersContext _localctx = new ParametersContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_parameters);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 ArgDefListBuilder args = new ArgDefListBuilder(); 
			setState(282);
			match(OPEN_PAREN);
			setState(284);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(283);
				typedargslist(args);
				}
			}

			setState(286);
			match(CLOSE_PAREN);
			 _localctx.result =  args; 
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

	public static class TypedargslistContext extends ParserRuleContext {
		public ArgDefListBuilder args;
		public List<DefparameterContext> defparameter() {
			return getRuleContexts(DefparameterContext.class);
		}
		public DefparameterContext defparameter(int i) {
			return getRuleContext(DefparameterContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public TerminalNode DIV() { return getToken(Python3Parser.DIV, 0); }
		public SplatparameterContext splatparameter() {
			return getRuleContext(SplatparameterContext.class,0);
		}
		public KwargsparameterContext kwargsparameter() {
			return getRuleContext(KwargsparameterContext.class,0);
		}
		public TypedargslistContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public TypedargslistContext(ParserRuleContext parent, int invokingState, ArgDefListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_typedargslist; }
	}

	public final TypedargslistContext typedargslist(ArgDefListBuilder args) throws RecognitionException {
		TypedargslistContext _localctx = new TypedargslistContext(_ctx, getState(), args);
		enterRule(_localctx, 20, RULE_typedargslist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(392);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
			case 1:
				{
				setState(289);
				defparameter(args);
				setState(294);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,16,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(290);
						match(COMMA);
						setState(291);
						defparameter(args);
						}
						} 
					}
					setState(296);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,16,_ctx);
				}
				setState(297);
				match(COMMA);
				setState(298);
				match(DIV);
				args.markPositionalOnlyIndex();
				setState(309);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
				case 1:
					{
					setState(300);
					match(COMMA);
					setState(301);
					defparameter(args);
					setState(306);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(302);
							match(COMMA);
							setState(303);
							defparameter(args);
							}
							} 
						}
						setState(308);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
					}
					}
					break;
				}
				setState(335);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(311);
					match(COMMA);
					setState(333);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(312);
						splatparameter(args);
						setState(317);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,19,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(313);
								match(COMMA);
								setState(314);
								defparameter(args);
								}
								} 
							}
							setState(319);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,19,_ctx);
						}
						setState(327);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(320);
							match(COMMA);
							setState(325);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(321);
								kwargsparameter(args);
								setState(323);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(322);
									match(COMMA);
									}
								}

								}
							}

							}
						}

						}
						break;
					case POWER:
						{
						setState(329);
						kwargsparameter(args);
						setState(331);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(330);
							match(COMMA);
							}
						}

						}
						break;
					case CLOSE_PAREN:
						break;
					default:
						break;
					}
					}
				}

				}
				break;
			case 2:
				{
				setState(337);
				defparameter(args);
				setState(342);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,26,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(338);
						match(COMMA);
						setState(339);
						defparameter(args);
						}
						} 
					}
					setState(344);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,26,_ctx);
				}
				setState(369);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(345);
					match(COMMA);
					setState(367);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(346);
						splatparameter(args);
						setState(351);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,27,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(347);
								match(COMMA);
								setState(348);
								defparameter(args);
								}
								} 
							}
							setState(353);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,27,_ctx);
						}
						setState(361);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(354);
							match(COMMA);
							setState(359);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(355);
								kwargsparameter(args);
								setState(357);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(356);
									match(COMMA);
									}
								}

								}
							}

							}
						}

						}
						break;
					case POWER:
						{
						setState(363);
						kwargsparameter(args);
						setState(365);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(364);
							match(COMMA);
							}
						}

						}
						break;
					case CLOSE_PAREN:
						break;
					default:
						break;
					}
					}
				}

				}
				break;
			case 3:
				{
				setState(371);
				splatparameter(args);
				setState(376);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,34,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(372);
						match(COMMA);
						setState(373);
						defparameter(args);
						}
						} 
					}
					setState(378);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,34,_ctx);
				}
				setState(386);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(379);
					match(COMMA);
					setState(384);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==POWER) {
						{
						setState(380);
						kwargsparameter(args);
						setState(382);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(381);
							match(COMMA);
							}
						}

						}
					}

					}
				}

				}
				break;
			case 4:
				{
				setState(388);
				kwargsparameter(args);
				setState(390);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(389);
					match(COMMA);
					}
				}

				}
				break;
			}

			        if (!args.validateArgumentsAfterSplat()) {
			            throw new PythonRecognitionException("named arguments must follow bare *", this, _input, _localctx, getCurrentToken());
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

	public static class DefparameterContext extends ParserRuleContext {
		public ArgDefListBuilder args;
		public Token NAME;
		public TestContext test;
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public TerminalNode ASSIGN() { return getToken(Python3Parser.ASSIGN, 0); }
		public DefparameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public DefparameterContext(ParserRuleContext parent, int invokingState, ArgDefListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_defparameter; }
	}

	public final DefparameterContext defparameter(ArgDefListBuilder args) throws RecognitionException {
		DefparameterContext _localctx = new DefparameterContext(_ctx, getState(), args);
		enterRule(_localctx, 22, RULE_defparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(396);
			_localctx.NAME = match(NAME);
			 SSTNode type = null; SSTNode defValue = null; 
			setState(402);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(398);
				match(COLON);
				setState(399);
				_localctx.test = test();
				 type = _localctx.test.result; 
				}
			}

			setState(408);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(404);
				match(ASSIGN);
				setState(405);
				_localctx.test = test();
				 defValue = _localctx.test.result; 
				}
			}

			 
			            ArgDefListBuilder.AddParamResult result = args.addParam((_localctx.NAME!=null?_localctx.NAME.getText():null), type, defValue); 
			            switch(result) {
			                case NONDEFAULT_FOLLOWS_DEFAULT:
			                    throw new PythonRecognitionException("non-default argument follows default argument", this, _input, _localctx, getCurrentToken());
			                case DUPLICATED_ARGUMENT:
			                    throw new PythonRecognitionException("duplicate argument '" + (_localctx.NAME!=null?_localctx.NAME.getText():null) + "' in function definition", this, _input, _localctx, getCurrentToken());
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

	public static class SplatparameterContext extends ParserRuleContext {
		public ArgDefListBuilder args;
		public Token NAME;
		public TestContext test;
		public TerminalNode STAR() { return getToken(Python3Parser.STAR, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public SplatparameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public SplatparameterContext(ParserRuleContext parent, int invokingState, ArgDefListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_splatparameter; }
	}

	public final SplatparameterContext splatparameter(ArgDefListBuilder args) throws RecognitionException {
		SplatparameterContext _localctx = new SplatparameterContext(_ctx, getState(), args);
		enterRule(_localctx, 24, RULE_splatparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(412);
			match(STAR);
			 String name = null; SSTNode type = null; 
			setState(422);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(414);
				_localctx.NAME = match(NAME);
				 name = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				setState(420);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COLON) {
					{
					setState(416);
					match(COLON);
					setState(417);
					_localctx.test = test();
					 type = _localctx.test.result; 
					}
				}

				}
			}

			 args.addSplat(name, type); 
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

	public static class KwargsparameterContext extends ParserRuleContext {
		public ArgDefListBuilder args;
		public Token NAME;
		public TestContext test;
		public TerminalNode POWER() { return getToken(Python3Parser.POWER, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public KwargsparameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public KwargsparameterContext(ParserRuleContext parent, int invokingState, ArgDefListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_kwargsparameter; }
	}

	public final KwargsparameterContext kwargsparameter(ArgDefListBuilder args) throws RecognitionException {
		KwargsparameterContext _localctx = new KwargsparameterContext(_ctx, getState(), args);
		enterRule(_localctx, 26, RULE_kwargsparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(426);
			match(POWER);
			setState(427);
			_localctx.NAME = match(NAME);
			 SSTNode type = null; 
			setState(433);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(429);
				match(COLON);
				setState(430);
				_localctx.test = test();
				 type = _localctx.test.result; 
				}
			}

			 args.addKwargs((_localctx.NAME!=null?_localctx.NAME.getText():null), type); 
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

	public static class VarargslistContext extends ParserRuleContext {
		public ArgDefListBuilder result;
		public List<VdefparameterContext> vdefparameter() {
			return getRuleContexts(VdefparameterContext.class);
		}
		public VdefparameterContext vdefparameter(int i) {
			return getRuleContext(VdefparameterContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public TerminalNode DIV() { return getToken(Python3Parser.DIV, 0); }
		public VsplatparameterContext vsplatparameter() {
			return getRuleContext(VsplatparameterContext.class,0);
		}
		public VkwargsparameterContext vkwargsparameter() {
			return getRuleContext(VkwargsparameterContext.class,0);
		}
		public VarargslistContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_varargslist; }
	}

	public final VarargslistContext varargslist() throws RecognitionException {
		VarargslistContext _localctx = new VarargslistContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_varargslist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 ArgDefListBuilder args = new ArgDefListBuilder(); 
			setState(541);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,68,_ctx) ) {
			case 1:
				{
				setState(438);
				vdefparameter(args);
				setState(443);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,45,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(439);
						match(COMMA);
						setState(440);
						vdefparameter(args);
						}
						} 
					}
					setState(445);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,45,_ctx);
				}
				setState(446);
				match(COMMA);
				setState(447);
				match(DIV);
				args.markPositionalOnlyIndex();
				setState(458);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,47,_ctx) ) {
				case 1:
					{
					setState(449);
					match(COMMA);
					setState(450);
					vdefparameter(args);
					setState(455);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,46,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(451);
							match(COMMA);
							setState(452);
							vdefparameter(args);
							}
							} 
						}
						setState(457);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,46,_ctx);
					}
					}
					break;
				}
				setState(484);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(460);
					match(COMMA);
					setState(482);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(461);
						vsplatparameter(args);
						setState(466);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,48,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(462);
								match(COMMA);
								setState(463);
								vdefparameter(args);
								}
								} 
							}
							setState(468);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,48,_ctx);
						}
						setState(476);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(469);
							match(COMMA);
							setState(474);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(470);
								vkwargsparameter(args);
								setState(472);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(471);
									match(COMMA);
									}
								}

								}
							}

							}
						}

						}
						break;
					case POWER:
						{
						setState(478);
						vkwargsparameter(args);
						setState(480);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(479);
							match(COMMA);
							}
						}

						}
						break;
					case COLON:
						break;
					default:
						break;
					}
					}
				}

				}
				break;
			case 2:
				{
				setState(486);
				vdefparameter(args);
				setState(491);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(487);
						match(COMMA);
						setState(488);
						vdefparameter(args);
						}
						} 
					}
					setState(493);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
				}
				setState(518);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(494);
					match(COMMA);
					setState(516);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(495);
						vsplatparameter(args);
						setState(500);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(496);
								match(COMMA);
								setState(497);
								vdefparameter(args);
								}
								} 
							}
							setState(502);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,56,_ctx);
						}
						setState(510);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(503);
							match(COMMA);
							setState(508);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(504);
								vkwargsparameter(args);
								setState(506);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(505);
									match(COMMA);
									}
								}

								}
							}

							}
						}

						}
						break;
					case POWER:
						{
						setState(512);
						vkwargsparameter(args);
						setState(514);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(513);
							match(COMMA);
							}
						}

						}
						break;
					case COLON:
						break;
					default:
						break;
					}
					}
				}

				}
				break;
			case 3:
				{
				setState(520);
				vsplatparameter(args);
				setState(525);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,63,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(521);
						match(COMMA);
						setState(522);
						vdefparameter(args);
						}
						} 
					}
					setState(527);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,63,_ctx);
				}
				setState(535);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(528);
					match(COMMA);
					setState(533);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==POWER) {
						{
						setState(529);
						vkwargsparameter(args);
						setState(531);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(530);
							match(COMMA);
							}
						}

						}
					}

					}
				}

				}
				break;
			case 4:
				{
				setState(537);
				vkwargsparameter(args);
				setState(539);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(538);
					match(COMMA);
					}
				}

				}
				break;
			}

			        if (!args.validateArgumentsAfterSplat()) {
			            throw new PythonRecognitionException("named arguments must follow bare *", this, _input, _localctx, getCurrentToken());
			        }
			    
			 _localctx.result =  args; 
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

	public static class VdefparameterContext extends ParserRuleContext {
		public ArgDefListBuilder args;
		public Token NAME;
		public TestContext test;
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public TerminalNode ASSIGN() { return getToken(Python3Parser.ASSIGN, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public VdefparameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public VdefparameterContext(ParserRuleContext parent, int invokingState, ArgDefListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_vdefparameter; }
	}

	public final VdefparameterContext vdefparameter(ArgDefListBuilder args) throws RecognitionException {
		VdefparameterContext _localctx = new VdefparameterContext(_ctx, getState(), args);
		enterRule(_localctx, 30, RULE_vdefparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(546);
			_localctx.NAME = match(NAME);
			 SSTNode defValue = null; 
			setState(552);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(548);
				match(ASSIGN);
				setState(549);
				_localctx.test = test();
				 defValue = _localctx.test.result; 
				}
			}

			 
			            ArgDefListBuilder.AddParamResult result = args.addParam((_localctx.NAME!=null?_localctx.NAME.getText():null), null, defValue); 
			            switch(result) {
			                case NONDEFAULT_FOLLOWS_DEFAULT:
			                    throw new PythonRecognitionException("non-default argument follows default argument", this, _input, _localctx, getCurrentToken());
			                case DUPLICATED_ARGUMENT:
			                    throw new PythonRecognitionException("duplicate argument '" + (_localctx.NAME!=null?_localctx.NAME.getText():null) + "' in function definition", this, _input, _localctx, getCurrentToken());
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

	public static class VsplatparameterContext extends ParserRuleContext {
		public ArgDefListBuilder args;
		public Token NAME;
		public TerminalNode STAR() { return getToken(Python3Parser.STAR, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public VsplatparameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public VsplatparameterContext(ParserRuleContext parent, int invokingState, ArgDefListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_vsplatparameter; }
	}

	public final VsplatparameterContext vsplatparameter(ArgDefListBuilder args) throws RecognitionException {
		VsplatparameterContext _localctx = new VsplatparameterContext(_ctx, getState(), args);
		enterRule(_localctx, 32, RULE_vsplatparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(556);
			match(STAR);
			 String name = null; 
			setState(560);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(558);
				_localctx.NAME = match(NAME);
				 name = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				}
			}

			 args.addSplat(name, null);
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

	public static class VkwargsparameterContext extends ParserRuleContext {
		public ArgDefListBuilder args;
		public Token NAME;
		public TerminalNode POWER() { return getToken(Python3Parser.POWER, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public VkwargsparameterContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public VkwargsparameterContext(ParserRuleContext parent, int invokingState, ArgDefListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_vkwargsparameter; }
	}

	public final VkwargsparameterContext vkwargsparameter(ArgDefListBuilder args) throws RecognitionException {
		VkwargsparameterContext _localctx = new VkwargsparameterContext(_ctx, getState(), args);
		enterRule(_localctx, 34, RULE_vkwargsparameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(564);
			match(POWER);
			setState(565);
			_localctx.NAME = match(NAME);
			args.addKwargs((_localctx.NAME!=null?_localctx.NAME.getText():null), null);
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

	public static class StmtContext extends ParserRuleContext {
		public Simple_stmtContext simple_stmt() {
			return getRuleContext(Simple_stmtContext.class,0);
		}
		public Compound_stmtContext compound_stmt() {
			return getRuleContext(Compound_stmtContext.class,0);
		}
		public StmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stmt; }
	}

	public final StmtContext stmt() throws RecognitionException {
		StmtContext _localctx = new StmtContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_stmt);
		try {
			setState(570);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case RETURN:
			case RAISE:
			case FROM:
			case IMPORT:
			case GLOBAL:
			case NONLOCAL:
			case ASSERT:
			case LAMBDA:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case YIELD:
			case DEL:
			case PASS:
			case CONTINUE:
			case BREAK:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case STAR:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				enterOuterAlt(_localctx, 1);
				{
				setState(568);
				simple_stmt();
				}
				break;
			case DEF:
			case IF:
			case WHILE:
			case FOR:
			case TRY:
			case WITH:
			case CLASS:
			case ASYNC:
			case AT:
				enterOuterAlt(_localctx, 2);
				{
				setState(569);
				compound_stmt();
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

	public static class Simple_stmtContext extends ParserRuleContext {
		public List<Small_stmtContext> small_stmt() {
			return getRuleContexts(Small_stmtContext.class);
		}
		public Small_stmtContext small_stmt(int i) {
			return getRuleContext(Small_stmtContext.class,i);
		}
		public TerminalNode NEWLINE() { return getToken(Python3Parser.NEWLINE, 0); }
		public List<TerminalNode> SEMI_COLON() { return getTokens(Python3Parser.SEMI_COLON); }
		public TerminalNode SEMI_COLON(int i) {
			return getToken(Python3Parser.SEMI_COLON, i);
		}
		public Simple_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_simple_stmt; }
	}

	public final Simple_stmtContext simple_stmt() throws RecognitionException {
		Simple_stmtContext _localctx = new Simple_stmtContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_simple_stmt);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(572);
			small_stmt();
			setState(577);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,72,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(573);
					match(SEMI_COLON);
					setState(574);
					small_stmt();
					}
					} 
				}
				setState(579);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,72,_ctx);
			}
			setState(581);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEMI_COLON) {
				{
				setState(580);
				match(SEMI_COLON);
				}
			}

			setState(583);
			match(NEWLINE);
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

	public static class Small_stmtContext extends ParserRuleContext {
		public Token p;
		public Expr_stmtContext expr_stmt() {
			return getRuleContext(Expr_stmtContext.class,0);
		}
		public Del_stmtContext del_stmt() {
			return getRuleContext(Del_stmtContext.class,0);
		}
		public TerminalNode PASS() { return getToken(Python3Parser.PASS, 0); }
		public Flow_stmtContext flow_stmt() {
			return getRuleContext(Flow_stmtContext.class,0);
		}
		public Import_stmtContext import_stmt() {
			return getRuleContext(Import_stmtContext.class,0);
		}
		public Global_stmtContext global_stmt() {
			return getRuleContext(Global_stmtContext.class,0);
		}
		public Nonlocal_stmtContext nonlocal_stmt() {
			return getRuleContext(Nonlocal_stmtContext.class,0);
		}
		public Assert_stmtContext assert_stmt() {
			return getRuleContext(Assert_stmtContext.class,0);
		}
		public Small_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_small_stmt; }
	}

	public final Small_stmtContext small_stmt() throws RecognitionException {
		Small_stmtContext _localctx = new Small_stmtContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_small_stmt);
		try {
			setState(594);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case LAMBDA:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case STAR:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				enterOuterAlt(_localctx, 1);
				{
				setState(585);
				expr_stmt();
				}
				break;
			case DEL:
				enterOuterAlt(_localctx, 2);
				{
				setState(586);
				del_stmt();
				}
				break;
			case PASS:
				enterOuterAlt(_localctx, 3);
				{
				setState(587);
				_localctx.p = match(PASS);
				 
				                int start = _localctx.p.getStartIndex(); 
				                push(new SimpleSSTNode(SimpleSSTNode.Type.PASS, start, start + 4 ));
				            
				}
				break;
			case RETURN:
			case RAISE:
			case YIELD:
			case CONTINUE:
			case BREAK:
				enterOuterAlt(_localctx, 4);
				{
				setState(589);
				flow_stmt();
				}
				break;
			case FROM:
			case IMPORT:
				enterOuterAlt(_localctx, 5);
				{
				setState(590);
				import_stmt();
				}
				break;
			case GLOBAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(591);
				global_stmt();
				}
				break;
			case NONLOCAL:
				enterOuterAlt(_localctx, 7);
				{
				setState(592);
				nonlocal_stmt();
				}
				break;
			case ASSERT:
				enterOuterAlt(_localctx, 8);
				{
				setState(593);
				assert_stmt();
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

	public static class Expr_stmtContext extends ParserRuleContext {
		public Testlist_star_exprContext lhs;
		public Testlist_star_exprContext testlist_star_expr;
		public TestContext t;
		public TestContext test;
		public AugassignContext augassign;
		public Yield_exprContext yield_expr;
		public TestlistContext testlist;
		public List<Testlist_star_exprContext> testlist_star_expr() {
			return getRuleContexts(Testlist_star_exprContext.class);
		}
		public Testlist_star_exprContext testlist_star_expr(int i) {
			return getRuleContext(Testlist_star_exprContext.class,i);
		}
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public AugassignContext augassign() {
			return getRuleContext(AugassignContext.class,0);
		}
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public List<Yield_exprContext> yield_expr() {
			return getRuleContexts(Yield_exprContext.class);
		}
		public Yield_exprContext yield_expr(int i) {
			return getRuleContext(Yield_exprContext.class,i);
		}
		public TestlistContext testlist() {
			return getRuleContext(TestlistContext.class,0);
		}
		public List<TerminalNode> ASSIGN() { return getTokens(Python3Parser.ASSIGN); }
		public TerminalNode ASSIGN(int i) {
			return getToken(Python3Parser.ASSIGN, i);
		}
		public Expr_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr_stmt; }
	}

	public final Expr_stmtContext expr_stmt() throws RecognitionException {
		Expr_stmtContext _localctx = new Expr_stmtContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_expr_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(596);
			_localctx.lhs = _localctx.testlist_star_expr = testlist_star_expr();
			 SSTNode rhs = null; 
			          int rhsStopIndex = 0;
			        
			setState(637);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(598);
				match(COLON);
				setState(599);
				_localctx.t = _localctx.test = test();
				setState(604);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ASSIGN) {
					{
					setState(600);
					match(ASSIGN);
					setState(601);
					_localctx.test = test();
					 rhs = _localctx.test.result;
					}
				}

				 
				                    rhsStopIndex = getStopIndex((_localctx.test!=null?(_localctx.test.stop):null));
				                    if (rhs == null) {
				                        rhs = new SimpleSSTNode(SimpleSSTNode.Type.NONE,  -1, -1);
				                    }
				                    push(factory.createAnnAssignment(_localctx.lhs.result, _localctx.t.result, rhs, getStartIndex(_localctx), rhsStopIndex)); 
				                
				}
				break;
			case ADD_ASSIGN:
			case SUB_ASSIGN:
			case MULT_ASSIGN:
			case AT_ASSIGN:
			case DIV_ASSIGN:
			case MOD_ASSIGN:
			case AND_ASSIGN:
			case OR_ASSIGN:
			case XOR_ASSIGN:
			case LEFT_SHIFT_ASSIGN:
			case RIGHT_SHIFT_ASSIGN:
			case POWER_ASSIGN:
			case IDIV_ASSIGN:
				{
				setState(608);
				_localctx.augassign = augassign();
				setState(615);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case YIELD:
					{
					setState(609);
					_localctx.yield_expr = yield_expr();
					 rhs = _localctx.yield_expr.result; rhsStopIndex = getStopIndex((_localctx.yield_expr!=null?(_localctx.yield_expr.stop):null));
					}
					break;
				case STRING:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
					{
					setState(612);
					_localctx.testlist = testlist();
					 rhs = _localctx.testlist.result; rhsStopIndex = getStopIndex((_localctx.testlist!=null?(_localctx.testlist.stop):null));
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 push(factory.createAugAssignment(_localctx.lhs.result, (_localctx.augassign!=null?_input.getText(_localctx.augassign.start,_localctx.augassign.stop):null), rhs, getStartIndex(_localctx), rhsStopIndex));
				}
				break;
			case NEWLINE:
			case SEMI_COLON:
			case ASSIGN:
				{
				 int start = start(); 
				 SSTNode value = _localctx.lhs.result; 
				setState(633);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==ASSIGN) {
					{
					{
					setState(621);
					match(ASSIGN);
					 push(value); 
					setState(629);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case YIELD:
						{
						setState(623);
						_localctx.yield_expr = yield_expr();
						 value = _localctx.yield_expr.result; rhsStopIndex = getStopIndex((_localctx.yield_expr!=null?(_localctx.yield_expr.stop):null)); 
						}
						break;
					case STRING:
					case LAMBDA:
					case NOT:
					case NONE:
					case TRUE:
					case FALSE:
					case AWAIT:
					case NAME:
					case DECIMAL_INTEGER:
					case OCT_INTEGER:
					case HEX_INTEGER:
					case BIN_INTEGER:
					case FLOAT_NUMBER:
					case IMAG_NUMBER:
					case ELLIPSIS:
					case STAR:
					case OPEN_PAREN:
					case OPEN_BRACK:
					case ADD:
					case MINUS:
					case NOT_OP:
					case OPEN_BRACE:
						{
						setState(626);
						_localctx.testlist_star_expr = testlist_star_expr();
						 value = _localctx.testlist_star_expr.result; rhsStopIndex = getStopIndex((_localctx.testlist_star_expr!=null?(_localctx.testlist_star_expr.stop):null));
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					}
					setState(635);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				 
				                    if (value instanceof StarSSTNode) {
				                        throw new PythonRecognitionException("can't use starred expression here", this, _input, _localctx, _localctx.start);
				                    }
				                    if (start == start()) {
				                        push(new ExpressionStatementSSTNode(value));
				                    } else {
				                        SSTNode[] lhs = getArray(start, SSTNode[].class);
				                        if (lhs.length == 1 && lhs[0] instanceof StarSSTNode) {
				                            throw new PythonRecognitionException("starred assignment target must be in a list or tuple", this, _input, _localctx, _localctx.start);
				                        }
				                        push(factory.createAssignment(lhs, value, getStartIndex(_localctx), rhsStopIndex));
				                    }
				                
				}
				break;
			default:
				throw new NoViableAltException(this);
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

	public static class Testlist_star_exprContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext test;
		public Star_exprContext star_expr;
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public List<Star_exprContext> star_expr() {
			return getRuleContexts(Star_exprContext.class);
		}
		public Star_exprContext star_expr(int i) {
			return getRuleContext(Star_exprContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public Testlist_star_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_testlist_star_expr; }
	}

	public final Testlist_star_exprContext testlist_star_expr() throws RecognitionException {
		Testlist_star_exprContext _localctx = new Testlist_star_exprContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_testlist_star_expr);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(645);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case LAMBDA:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				{
				setState(639);
				_localctx.test = test();
				 _localctx.result =  _localctx.test.result; 
				}
				break;
			case STAR:
				{
				setState(642);
				_localctx.star_expr = star_expr();
				  _localctx.result =  _localctx.star_expr.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(677);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 
				                    int start = start(); 
				                    push(_localctx.result);
				                
				setState(648);
				match(COMMA);
				setState(674);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(655);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STRING:
					case LAMBDA:
					case NOT:
					case NONE:
					case TRUE:
					case FALSE:
					case AWAIT:
					case NAME:
					case DECIMAL_INTEGER:
					case OCT_INTEGER:
					case HEX_INTEGER:
					case BIN_INTEGER:
					case FLOAT_NUMBER:
					case IMAG_NUMBER:
					case ELLIPSIS:
					case OPEN_PAREN:
					case OPEN_BRACK:
					case ADD:
					case MINUS:
					case NOT_OP:
					case OPEN_BRACE:
						{
						setState(649);
						_localctx.test = test();
						 push(_localctx.test.result); 
						}
						break;
					case STAR:
						{
						setState(652);
						_localctx.star_expr = star_expr();
						 push(_localctx.star_expr.result); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(668);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,83,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(657);
							match(COMMA);
							setState(664);
							_errHandler.sync(this);
							switch (_input.LA(1)) {
							case STRING:
							case LAMBDA:
							case NOT:
							case NONE:
							case TRUE:
							case FALSE:
							case AWAIT:
							case NAME:
							case DECIMAL_INTEGER:
							case OCT_INTEGER:
							case HEX_INTEGER:
							case BIN_INTEGER:
							case FLOAT_NUMBER:
							case IMAG_NUMBER:
							case ELLIPSIS:
							case OPEN_PAREN:
							case OPEN_BRACK:
							case ADD:
							case MINUS:
							case NOT_OP:
							case OPEN_BRACE:
								{
								setState(658);
								_localctx.test = test();
								 push(_localctx.test.result); 
								}
								break;
							case STAR:
								{
								setState(661);
								_localctx.star_expr = star_expr();
								 push(_localctx.star_expr.result); 
								}
								break;
							default:
								throw new NoViableAltException(this);
							}
							}
							} 
						}
						setState(670);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,83,_ctx);
					}
					setState(672);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(671);
						match(COMMA);
						}
					}

					}
				}

				 _localctx.result =  new CollectionSSTNode(getArray(start, SSTNode[].class), PythonBuiltinClassType.PTuple, getStartIndex(_localctx), getLastIndex(_localctx)); 
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

	public static class AugassignContext extends ParserRuleContext {
		public TerminalNode ADD_ASSIGN() { return getToken(Python3Parser.ADD_ASSIGN, 0); }
		public TerminalNode SUB_ASSIGN() { return getToken(Python3Parser.SUB_ASSIGN, 0); }
		public TerminalNode MULT_ASSIGN() { return getToken(Python3Parser.MULT_ASSIGN, 0); }
		public TerminalNode AT_ASSIGN() { return getToken(Python3Parser.AT_ASSIGN, 0); }
		public TerminalNode DIV_ASSIGN() { return getToken(Python3Parser.DIV_ASSIGN, 0); }
		public TerminalNode MOD_ASSIGN() { return getToken(Python3Parser.MOD_ASSIGN, 0); }
		public TerminalNode AND_ASSIGN() { return getToken(Python3Parser.AND_ASSIGN, 0); }
		public TerminalNode OR_ASSIGN() { return getToken(Python3Parser.OR_ASSIGN, 0); }
		public TerminalNode XOR_ASSIGN() { return getToken(Python3Parser.XOR_ASSIGN, 0); }
		public TerminalNode LEFT_SHIFT_ASSIGN() { return getToken(Python3Parser.LEFT_SHIFT_ASSIGN, 0); }
		public TerminalNode RIGHT_SHIFT_ASSIGN() { return getToken(Python3Parser.RIGHT_SHIFT_ASSIGN, 0); }
		public TerminalNode POWER_ASSIGN() { return getToken(Python3Parser.POWER_ASSIGN, 0); }
		public TerminalNode IDIV_ASSIGN() { return getToken(Python3Parser.IDIV_ASSIGN, 0); }
		public AugassignContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_augassign; }
	}

	public final AugassignContext augassign() throws RecognitionException {
		AugassignContext _localctx = new AugassignContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_augassign);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(679);
			_la = _input.LA(1);
			if ( !(((((_la - 81)) & ~0x3f) == 0 && ((1L << (_la - 81)) & ((1L << (ADD_ASSIGN - 81)) | (1L << (SUB_ASSIGN - 81)) | (1L << (MULT_ASSIGN - 81)) | (1L << (AT_ASSIGN - 81)) | (1L << (DIV_ASSIGN - 81)) | (1L << (MOD_ASSIGN - 81)) | (1L << (AND_ASSIGN - 81)) | (1L << (OR_ASSIGN - 81)) | (1L << (XOR_ASSIGN - 81)) | (1L << (LEFT_SHIFT_ASSIGN - 81)) | (1L << (RIGHT_SHIFT_ASSIGN - 81)) | (1L << (POWER_ASSIGN - 81)) | (1L << (IDIV_ASSIGN - 81)))) != 0)) ) {
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

	public static class Del_stmtContext extends ParserRuleContext {
		public ExprlistContext exprlist;
		public TerminalNode DEL() { return getToken(Python3Parser.DEL, 0); }
		public ExprlistContext exprlist() {
			return getRuleContext(ExprlistContext.class,0);
		}
		public Del_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_del_stmt; }
	}

	public final Del_stmtContext del_stmt() throws RecognitionException {
		Del_stmtContext _localctx = new Del_stmtContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_del_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(681);
			match(DEL);
			setState(682);
			_localctx.exprlist = exprlist();
			 push(new DelSSTNode(_localctx.exprlist.result, getStartIndex(_localctx), getStopIndex((_localctx.exprlist!=null?(_localctx.exprlist.stop):null)))); 
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

	public static class Flow_stmtContext extends ParserRuleContext {
		public Token b;
		public Token c;
		public TerminalNode BREAK() { return getToken(Python3Parser.BREAK, 0); }
		public TerminalNode CONTINUE() { return getToken(Python3Parser.CONTINUE, 0); }
		public Return_stmtContext return_stmt() {
			return getRuleContext(Return_stmtContext.class,0);
		}
		public Raise_stmtContext raise_stmt() {
			return getRuleContext(Raise_stmtContext.class,0);
		}
		public Yield_stmtContext yield_stmt() {
			return getRuleContext(Yield_stmtContext.class,0);
		}
		public Flow_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_flow_stmt; }
	}

	public final Flow_stmtContext flow_stmt() throws RecognitionException {
		Flow_stmtContext _localctx = new Flow_stmtContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_flow_stmt);
		try {
			setState(692);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BREAK:
				enterOuterAlt(_localctx, 1);
				{
				setState(685);
				_localctx.b = match(BREAK);

				            if (loopState == null) {
				                throw new PythonRecognitionException("'break' outside loop", this, _input, _localctx, _localctx.b);
				            }
				            push(new SimpleSSTNode(SimpleSSTNode.Type.BREAK, getStartIndex(_localctx.b), getStopIndex(_localctx.b)));
				            loopState.containsBreak = true;
				        
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(687);
				_localctx.c = match(CONTINUE);

					        if (loopState == null) {
					            throw new PythonRecognitionException("'continue' not properly in loop", this, _input, _localctx, _localctx.c);
					        }
				            push(new SimpleSSTNode(SimpleSSTNode.Type.CONTINUE, getStartIndex(_localctx.c), getStopIndex(_localctx.c)));
				            loopState.containsContinue = true;
				        
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 3);
				{
				setState(689);
				return_stmt();
				}
				break;
			case RAISE:
				enterOuterAlt(_localctx, 4);
				{
				setState(690);
				raise_stmt();
				}
				break;
			case YIELD:
				enterOuterAlt(_localctx, 5);
				{
				setState(691);
				yield_stmt();
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

	public static class Return_stmtContext extends ParserRuleContext {
		public Testlist_star_exprContext testlist_star_expr;
		public TerminalNode RETURN() { return getToken(Python3Parser.RETURN, 0); }
		public Testlist_star_exprContext testlist_star_expr() {
			return getRuleContext(Testlist_star_exprContext.class,0);
		}
		public Return_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_return_stmt; }
	}

	public final Return_stmtContext return_stmt() throws RecognitionException {
		Return_stmtContext _localctx = new Return_stmtContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_return_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(694);
			match(RETURN);
			 SSTNode value = null; 
			setState(699);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(696);
				_localctx.testlist_star_expr = testlist_star_expr();
				 value = _localctx.testlist_star_expr.result; 
				}
			}

			 push(new ReturnSSTNode(value, getStartIndex(_localctx), getLastIndex(_localctx)));
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

	public static class Yield_stmtContext extends ParserRuleContext {
		public Yield_exprContext yield_expr;
		public Yield_exprContext yield_expr() {
			return getRuleContext(Yield_exprContext.class,0);
		}
		public Yield_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_yield_stmt; }
	}

	public final Yield_stmtContext yield_stmt() throws RecognitionException {
		Yield_stmtContext _localctx = new Yield_stmtContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_yield_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(703);
			_localctx.yield_expr = yield_expr();
			 push(new ExpressionStatementSSTNode(_localctx.yield_expr.result)); 
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

	public static class Raise_stmtContext extends ParserRuleContext {
		public TestContext test;
		public TerminalNode RAISE() { return getToken(Python3Parser.RAISE, 0); }
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public TerminalNode FROM() { return getToken(Python3Parser.FROM, 0); }
		public Raise_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_raise_stmt; }
	}

	public final Raise_stmtContext raise_stmt() throws RecognitionException {
		Raise_stmtContext _localctx = new Raise_stmtContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_raise_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 SSTNode value = null; SSTNode from = null; 
			setState(707);
			match(RAISE);
			setState(716);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(708);
				_localctx.test = test();
				 value = _localctx.test.result; 
				setState(714);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FROM) {
					{
					setState(710);
					match(FROM);
					setState(711);
					_localctx.test = test();
					 from = _localctx.test.result; 
					}
				}

				}
			}

			 push(new RaiseSSTNode(value, from, getStartIndex(_localctx), getLastIndex(_localctx))); 
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

	public static class Import_stmtContext extends ParserRuleContext {
		public Import_nameContext import_name() {
			return getRuleContext(Import_nameContext.class,0);
		}
		public Import_fromContext import_from() {
			return getRuleContext(Import_fromContext.class,0);
		}
		public Import_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_import_stmt; }
	}

	public final Import_stmtContext import_stmt() throws RecognitionException {
		Import_stmtContext _localctx = new Import_stmtContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_import_stmt);
		try {
			setState(722);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IMPORT:
				enterOuterAlt(_localctx, 1);
				{
				setState(720);
				import_name();
				}
				break;
			case FROM:
				enterOuterAlt(_localctx, 2);
				{
				setState(721);
				import_from();
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

	public static class Import_nameContext extends ParserRuleContext {
		public TerminalNode IMPORT() { return getToken(Python3Parser.IMPORT, 0); }
		public Dotted_as_namesContext dotted_as_names() {
			return getRuleContext(Dotted_as_namesContext.class,0);
		}
		public Import_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_import_name; }
	}

	public final Import_nameContext import_name() throws RecognitionException {
		Import_nameContext _localctx = new Import_nameContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_import_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(724);
			match(IMPORT);
			setState(725);
			dotted_as_names();
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

	public static class Import_fromContext extends ParserRuleContext {
		public Dotted_nameContext dotted_name;
		public Import_as_namesContext import_as_names;
		public TerminalNode FROM() { return getToken(Python3Parser.FROM, 0); }
		public TerminalNode IMPORT() { return getToken(Python3Parser.IMPORT, 0); }
		public Dotted_nameContext dotted_name() {
			return getRuleContext(Dotted_nameContext.class,0);
		}
		public TerminalNode STAR() { return getToken(Python3Parser.STAR, 0); }
		public TerminalNode OPEN_PAREN() { return getToken(Python3Parser.OPEN_PAREN, 0); }
		public Import_as_namesContext import_as_names() {
			return getRuleContext(Import_as_namesContext.class,0);
		}
		public TerminalNode CLOSE_PAREN() { return getToken(Python3Parser.CLOSE_PAREN, 0); }
		public List<TerminalNode> DOT() { return getTokens(Python3Parser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(Python3Parser.DOT, i);
		}
		public List<TerminalNode> ELLIPSIS() { return getTokens(Python3Parser.ELLIPSIS); }
		public TerminalNode ELLIPSIS(int i) {
			return getToken(Python3Parser.ELLIPSIS, i);
		}
		public Import_fromContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_import_from; }
	}

	public final Import_fromContext import_from() throws RecognitionException {
		Import_fromContext _localctx = new Import_fromContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_import_from);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(727);
			match(FROM);
			 String name = ""; 
			setState(749);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,96,_ctx) ) {
			case 1:
				{
				setState(735);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==DOT || _la==ELLIPSIS) {
					{
					setState(733);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case DOT:
						{
						setState(729);
						match(DOT);
						 name += '.'; 
						}
						break;
					case ELLIPSIS:
						{
						setState(731);
						match(ELLIPSIS);
						 name += "..."; 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					setState(737);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(738);
				_localctx.dotted_name = dotted_name();
				 name += _localctx.dotted_name.result; 
				}
				break;
			case 2:
				{
				setState(745); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					setState(745);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case DOT:
						{
						setState(741);
						match(DOT);
						 name += '.'; 
						}
						break;
					case ELLIPSIS:
						{
						setState(743);
						match(ELLIPSIS);
						 name += "..."; 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					setState(747); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==DOT || _la==ELLIPSIS );
				}
				break;
			}
			setState(751);
			match(IMPORT);
			 String[][] asNames = null; 
			setState(762);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STAR:
				{
				setState(753);
				match(STAR);
				}
				break;
			case OPEN_PAREN:
				{
				setState(754);
				match(OPEN_PAREN);
				setState(755);
				_localctx.import_as_names = import_as_names();
				 asNames = _localctx.import_as_names.result; 
				setState(757);
				match(CLOSE_PAREN);
				}
				break;
			case NAME:
				{
				setState(759);
				_localctx.import_as_names = import_as_names();
				 asNames = _localctx.import_as_names.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			 push(factory.createImportFrom(name, asNames, getStartIndex(_localctx), getLastIndex(_localctx))); 
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

	public static class Import_as_nameContext extends ParserRuleContext {
		public String[] result;
		public Token n;
		public Token NAME;
		public List<TerminalNode> NAME() { return getTokens(Python3Parser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(Python3Parser.NAME, i);
		}
		public TerminalNode AS() { return getToken(Python3Parser.AS, 0); }
		public Import_as_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_import_as_name; }
	}

	public final Import_as_nameContext import_as_name() throws RecognitionException {
		Import_as_nameContext _localctx = new Import_as_nameContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_import_as_name);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(766);
			_localctx.n = match(NAME);
			 String asName = null; 
			setState(771);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(768);
				match(AS);
				setState(769);
				_localctx.NAME = match(NAME);
				 asName = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				}
			}

			 _localctx.result =  new String[]{(_localctx.n!=null?_localctx.n.getText():null), asName}; 
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

	public static class Import_as_namesContext extends ParserRuleContext {
		public String[][] result;
		public Import_as_nameContext import_as_name;
		public List<Import_as_nameContext> import_as_name() {
			return getRuleContexts(Import_as_nameContext.class);
		}
		public Import_as_nameContext import_as_name(int i) {
			return getRuleContext(Import_as_nameContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public Import_as_namesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_import_as_names; }
	}

	public final Import_as_namesContext import_as_names() throws RecognitionException {
		Import_as_namesContext _localctx = new Import_as_namesContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_import_as_names);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 int start = start(); 
			setState(776);
			_localctx.import_as_name = import_as_name();
			 push(_localctx.import_as_name.result); 
			setState(784);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,99,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(778);
					match(COMMA);
					setState(779);
					_localctx.import_as_name = import_as_name();
					 push(_localctx.import_as_name.result); 
					}
					} 
				}
				setState(786);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,99,_ctx);
			}
			setState(788);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(787);
				match(COMMA);
				}
			}

			 _localctx.result =  getArray(start, String[][].class); 
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

	public static class Dotted_nameContext extends ParserRuleContext {
		public String result;
		public Token NAME;
		public List<TerminalNode> NAME() { return getTokens(Python3Parser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(Python3Parser.NAME, i);
		}
		public List<TerminalNode> DOT() { return getTokens(Python3Parser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(Python3Parser.DOT, i);
		}
		public Dotted_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dotted_name; }
	}

	public final Dotted_nameContext dotted_name() throws RecognitionException {
		Dotted_nameContext _localctx = new Dotted_nameContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_dotted_name);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(792);
			_localctx.NAME = match(NAME);
			 _localctx.result =  (_localctx.NAME!=null?_localctx.NAME.getText():null); 
			setState(799);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(794);
				match(DOT);
				setState(795);
				_localctx.NAME = match(NAME);
				 _localctx.result =  _localctx.result + "." + (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				}
				}
				setState(801);
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

	public static class Dotted_as_nameContext extends ParserRuleContext {
		public Dotted_nameContext dotted_name;
		public Token NAME;
		public Dotted_nameContext dotted_name() {
			return getRuleContext(Dotted_nameContext.class,0);
		}
		public TerminalNode AS() { return getToken(Python3Parser.AS, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public Dotted_as_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dotted_as_name; }
	}

	public final Dotted_as_nameContext dotted_as_name() throws RecognitionException {
		Dotted_as_nameContext _localctx = new Dotted_as_nameContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_dotted_as_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(802);
			_localctx.dotted_name = dotted_name();
			setState(807);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AS:
				{
				setState(803);
				match(AS);
				setState(804);
				_localctx.NAME = match(NAME);
				 push(factory.createImport(_localctx.dotted_name.result, (_localctx.NAME!=null?_localctx.NAME.getText():null), getStartIndex(_localctx), getLastIndex(_localctx)));
				}
				break;
			case NEWLINE:
			case COMMA:
			case SEMI_COLON:
				{
				 push(factory.createImport(_localctx.dotted_name.result, null, getStartIndex(_localctx), getLastIndex(_localctx)));
				}
				break;
			default:
				throw new NoViableAltException(this);
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

	public static class Dotted_as_namesContext extends ParserRuleContext {
		public List<Dotted_as_nameContext> dotted_as_name() {
			return getRuleContexts(Dotted_as_nameContext.class);
		}
		public Dotted_as_nameContext dotted_as_name(int i) {
			return getRuleContext(Dotted_as_nameContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public Dotted_as_namesContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dotted_as_names; }
	}

	public final Dotted_as_namesContext dotted_as_names() throws RecognitionException {
		Dotted_as_namesContext _localctx = new Dotted_as_namesContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_dotted_as_names);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(809);
			dotted_as_name();
			setState(814);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(810);
				match(COMMA);
				setState(811);
				dotted_as_name();
				}
				}
				setState(816);
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

	public static class Global_stmtContext extends ParserRuleContext {
		public Token NAME;
		public TerminalNode GLOBAL() { return getToken(Python3Parser.GLOBAL, 0); }
		public List<TerminalNode> NAME() { return getTokens(Python3Parser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(Python3Parser.NAME, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public Global_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_global_stmt; }
	}

	public final Global_stmtContext global_stmt() throws RecognitionException {
		Global_stmtContext _localctx = new Global_stmtContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_global_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 int start = stringStart(); 
			setState(818);
			match(GLOBAL);
			setState(819);
			_localctx.NAME = match(NAME);
			 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
			setState(826);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(821);
				match(COMMA);
				setState(822);
				_localctx.NAME = match(NAME);
				 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
				}
				}
				setState(828);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			 push(factory.registerGlobal(getStringArray(start), getStartIndex(_localctx), getLastIndex(_localctx))); 
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

	public static class Nonlocal_stmtContext extends ParserRuleContext {
		public Token NAME;
		public TerminalNode NONLOCAL() { return getToken(Python3Parser.NONLOCAL, 0); }
		public List<TerminalNode> NAME() { return getTokens(Python3Parser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(Python3Parser.NAME, i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public Nonlocal_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nonlocal_stmt; }
	}

	public final Nonlocal_stmtContext nonlocal_stmt() throws RecognitionException {
		Nonlocal_stmtContext _localctx = new Nonlocal_stmtContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_nonlocal_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 int start = stringStart(); 
			setState(832);
			match(NONLOCAL);
			setState(833);
			_localctx.NAME = match(NAME);
			 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
			setState(840);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(835);
				match(COMMA);
				setState(836);
				_localctx.NAME = match(NAME);
				 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
				}
				}
				setState(842);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			 push(factory.registerNonLocal(getStringArray(start), getStartIndex(_localctx), getLastIndex(_localctx))); 
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

	public static class Assert_stmtContext extends ParserRuleContext {
		public TestContext e;
		public TestContext test;
		public TerminalNode ASSERT() { return getToken(Python3Parser.ASSERT, 0); }
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public TerminalNode COMMA() { return getToken(Python3Parser.COMMA, 0); }
		public Assert_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assert_stmt; }
	}

	public final Assert_stmtContext assert_stmt() throws RecognitionException {
		Assert_stmtContext _localctx = new Assert_stmtContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_assert_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(845);
			match(ASSERT);
			setState(846);
			_localctx.e = _localctx.test = test();
			 SSTNode message = null; 
			setState(852);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(848);
				match(COMMA);
				setState(849);
				_localctx.test = test();
				 message = _localctx.test.result; 
				}
			}

			 push(new AssertSSTNode(_localctx.e.result, message, getStartIndex(_localctx), getLastIndex(_localctx))); 
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

	public static class Compound_stmtContext extends ParserRuleContext {
		public If_stmtContext if_stmt() {
			return getRuleContext(If_stmtContext.class,0);
		}
		public While_stmtContext while_stmt() {
			return getRuleContext(While_stmtContext.class,0);
		}
		public For_stmtContext for_stmt() {
			return getRuleContext(For_stmtContext.class,0);
		}
		public Try_stmtContext try_stmt() {
			return getRuleContext(Try_stmtContext.class,0);
		}
		public With_stmtContext with_stmt() {
			return getRuleContext(With_stmtContext.class,0);
		}
		public FuncdefContext funcdef() {
			return getRuleContext(FuncdefContext.class,0);
		}
		public ClassdefContext classdef() {
			return getRuleContext(ClassdefContext.class,0);
		}
		public DecoratedContext decorated() {
			return getRuleContext(DecoratedContext.class,0);
		}
		public Async_stmtContext async_stmt() {
			return getRuleContext(Async_stmtContext.class,0);
		}
		public Compound_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_compound_stmt; }
	}

	public final Compound_stmtContext compound_stmt() throws RecognitionException {
		Compound_stmtContext _localctx = new Compound_stmtContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_compound_stmt);
		try {
			setState(865);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IF:
				enterOuterAlt(_localctx, 1);
				{
				setState(856);
				if_stmt();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 2);
				{
				setState(857);
				while_stmt();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 3);
				{
				setState(858);
				for_stmt();
				}
				break;
			case TRY:
				enterOuterAlt(_localctx, 4);
				{
				setState(859);
				try_stmt();
				}
				break;
			case WITH:
				enterOuterAlt(_localctx, 5);
				{
				setState(860);
				with_stmt();
				}
				break;
			case DEF:
				enterOuterAlt(_localctx, 6);
				{
				setState(861);
				funcdef();
				}
				break;
			case CLASS:
				enterOuterAlt(_localctx, 7);
				{
				setState(862);
				classdef();
				}
				break;
			case AT:
				enterOuterAlt(_localctx, 8);
				{
				setState(863);
				decorated();
				}
				break;
			case ASYNC:
				enterOuterAlt(_localctx, 9);
				{
				setState(864);
				async_stmt();
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

	public static class Async_stmtContext extends ParserRuleContext {
		public TerminalNode ASYNC() { return getToken(Python3Parser.ASYNC, 0); }
		public FuncdefContext funcdef() {
			return getRuleContext(FuncdefContext.class,0);
		}
		public With_stmtContext with_stmt() {
			return getRuleContext(With_stmtContext.class,0);
		}
		public For_stmtContext for_stmt() {
			return getRuleContext(For_stmtContext.class,0);
		}
		public Async_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_async_stmt; }
	}

	public final Async_stmtContext async_stmt() throws RecognitionException {
		Async_stmtContext _localctx = new Async_stmtContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_async_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(867);
			match(ASYNC);
			setState(871);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEF:
				{
				setState(868);
				funcdef();
				}
				break;
			case WITH:
				{
				setState(869);
				with_stmt();
				}
				break;
			case FOR:
				{
				setState(870);
				for_stmt();
				}
				break;
			default:
				throw new NoViableAltException(this);
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

	public static class If_stmtContext extends ParserRuleContext {
		public TestContext if_test;
		public SuiteContext if_suite;
		public Elif_stmtContext elif_stmt;
		public TerminalNode IF() { return getToken(Python3Parser.IF, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public Elif_stmtContext elif_stmt() {
			return getRuleContext(Elif_stmtContext.class,0);
		}
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public SuiteContext suite() {
			return getRuleContext(SuiteContext.class,0);
		}
		public If_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_if_stmt; }
	}

	public final If_stmtContext if_stmt() throws RecognitionException {
		If_stmtContext _localctx = new If_stmtContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_if_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(873);
			match(IF);
			setState(874);
			_localctx.if_test = test();
			setState(875);
			match(COLON);
			setState(876);
			_localctx.if_suite = suite();
			setState(877);
			_localctx.elif_stmt = elif_stmt();
			 push(new IfSSTNode(_localctx.if_test.result, _localctx.if_suite.result, _localctx.elif_stmt.result, getStartIndex(_localctx), getStopIndex((_localctx.elif_stmt!=null?(_localctx.elif_stmt.stop):null))));
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

	public static class Elif_stmtContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext test;
		public SuiteContext suite;
		public Elif_stmtContext elif_stmt;
		public TerminalNode ELIF() { return getToken(Python3Parser.ELIF, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public SuiteContext suite() {
			return getRuleContext(SuiteContext.class,0);
		}
		public Elif_stmtContext elif_stmt() {
			return getRuleContext(Elif_stmtContext.class,0);
		}
		public TerminalNode ELSE() { return getToken(Python3Parser.ELSE, 0); }
		public Elif_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_elif_stmt; }
	}

	public final Elif_stmtContext elif_stmt() throws RecognitionException {
		Elif_stmtContext _localctx = new Elif_stmtContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_elif_stmt);
		try {
			setState(893);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ELIF:
				enterOuterAlt(_localctx, 1);
				{
				setState(880);
				match(ELIF);
				setState(881);
				_localctx.test = test();
				setState(882);
				match(COLON);
				setState(883);
				_localctx.suite = suite();
				setState(884);
				_localctx.elif_stmt = elif_stmt();
				 _localctx.result =  new IfSSTNode(_localctx.test.result, _localctx.suite.result, _localctx.elif_stmt.result, getStartIndex(_localctx), getStopIndex(_localctx.elif_stmt.stop)); 
				}
				break;
			case ELSE:
				enterOuterAlt(_localctx, 2);
				{
				setState(887);
				match(ELSE);
				setState(888);
				match(COLON);
				setState(889);
				_localctx.suite = suite();
				 _localctx.result =  _localctx.suite.result; 
				}
				break;
			case EOF:
			case STRING:
			case DEF:
			case RETURN:
			case RAISE:
			case FROM:
			case IMPORT:
			case GLOBAL:
			case NONLOCAL:
			case ASSERT:
			case IF:
			case WHILE:
			case FOR:
			case TRY:
			case WITH:
			case LAMBDA:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case CLASS:
			case YIELD:
			case DEL:
			case PASS:
			case CONTINUE:
			case BREAK:
			case ASYNC:
			case AWAIT:
			case NEWLINE:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case STAR:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
			case AT:
			case DEDENT:
				enterOuterAlt(_localctx, 3);
				{
				 _localctx.result =  null; 
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

	public static class While_stmtContext extends ParserRuleContext {
		public TestContext test;
		public SuiteContext suite;
		public TerminalNode WHILE() { return getToken(Python3Parser.WHILE, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public List<TerminalNode> COLON() { return getTokens(Python3Parser.COLON); }
		public TerminalNode COLON(int i) {
			return getToken(Python3Parser.COLON, i);
		}
		public List<SuiteContext> suite() {
			return getRuleContexts(SuiteContext.class);
		}
		public SuiteContext suite(int i) {
			return getRuleContext(SuiteContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(Python3Parser.ELSE, 0); }
		public While_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_while_stmt; }
	}

	public final While_stmtContext while_stmt() throws RecognitionException {
		While_stmtContext _localctx = new While_stmtContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_while_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(895);
			match(WHILE);
			setState(896);
			_localctx.test = test();
			setState(897);
			match(COLON);
			 LoopState savedState = startLoop(); 
			setState(899);
			_localctx.suite = suite();
			 
			            WhileSSTNode result = new WhileSSTNode(_localctx.test.result, _localctx.suite.result, loopState.containsContinue, loopState.containsBreak, getStartIndex(_localctx),getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)));
			            loopState = savedState;
			        
			setState(906);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(901);
				match(ELSE);
				setState(902);
				match(COLON);
				setState(903);
				_localctx.suite = suite();
				 
				                    result.setElse(_localctx.suite.result); 
				                    result.setEndOffset(getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)));
				                
				}
			}

			 
			            push(result);
			        
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

	public static class For_stmtContext extends ParserRuleContext {
		public ExprlistContext exprlist;
		public TestlistContext testlist;
		public SuiteContext suite;
		public TerminalNode FOR() { return getToken(Python3Parser.FOR, 0); }
		public ExprlistContext exprlist() {
			return getRuleContext(ExprlistContext.class,0);
		}
		public TerminalNode IN() { return getToken(Python3Parser.IN, 0); }
		public TestlistContext testlist() {
			return getRuleContext(TestlistContext.class,0);
		}
		public List<TerminalNode> COLON() { return getTokens(Python3Parser.COLON); }
		public TerminalNode COLON(int i) {
			return getToken(Python3Parser.COLON, i);
		}
		public List<SuiteContext> suite() {
			return getRuleContexts(SuiteContext.class);
		}
		public SuiteContext suite(int i) {
			return getRuleContext(SuiteContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(Python3Parser.ELSE, 0); }
		public For_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_for_stmt; }
	}

	public final For_stmtContext for_stmt() throws RecognitionException {
		For_stmtContext _localctx = new For_stmtContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_for_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(910);
			match(FOR);
			setState(911);
			_localctx.exprlist = exprlist();
			setState(912);
			match(IN);
			setState(913);
			_localctx.testlist = testlist();
			setState(914);
			match(COLON);
			 LoopState savedState = startLoop(); 
			setState(916);
			_localctx.suite = suite();
			 
			            ForSSTNode result = factory.createForSSTNode(_localctx.exprlist.result, _localctx.testlist.result, _localctx.suite.result, loopState.containsContinue, getStartIndex(_localctx),getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)));
			            result.setContainsBreak(loopState.containsBreak);
			            loopState = savedState;
			        
			setState(923);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(918);
				match(ELSE);
				setState(919);
				match(COLON);
				setState(920);
				_localctx.suite = suite();
				 
				                    result.setElse(_localctx.suite.result); 
				                    result.setEndOffset(getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)));
				                
				}
			}

			  
			            push(result);
			        
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

	public static class Try_stmtContext extends ParserRuleContext {
		public SuiteContext body;
		public SuiteContext suite;
		public Except_clauseContext except_clause;
		public TerminalNode TRY() { return getToken(Python3Parser.TRY, 0); }
		public List<TerminalNode> COLON() { return getTokens(Python3Parser.COLON); }
		public TerminalNode COLON(int i) {
			return getToken(Python3Parser.COLON, i);
		}
		public List<SuiteContext> suite() {
			return getRuleContexts(SuiteContext.class);
		}
		public SuiteContext suite(int i) {
			return getRuleContext(SuiteContext.class,i);
		}
		public TerminalNode FINALLY() { return getToken(Python3Parser.FINALLY, 0); }
		public List<Except_clauseContext> except_clause() {
			return getRuleContexts(Except_clauseContext.class);
		}
		public Except_clauseContext except_clause(int i) {
			return getRuleContext(Except_clauseContext.class,i);
		}
		public TerminalNode ELSE() { return getToken(Python3Parser.ELSE, 0); }
		public Try_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_try_stmt; }
	}

	public final Try_stmtContext try_stmt() throws RecognitionException {
		Try_stmtContext _localctx = new Try_stmtContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_try_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(927);
			match(TRY);
			setState(928);
			match(COLON);
			setState(929);
			_localctx.body = _localctx.suite = suite();
			 int start = start(); 
			 
			            SSTNode elseStatement = null; 
			            SSTNode finallyStatement = null; 
			        
			setState(958);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EXCEPT:
				{
				setState(935); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(932);
					_localctx.except_clause = except_clause();
					 push(_localctx.except_clause.result); 
					}
					}
					setState(937); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==EXCEPT );
				setState(944);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ELSE) {
					{
					setState(939);
					match(ELSE);
					setState(940);
					match(COLON);
					setState(941);
					_localctx.suite = suite();
					 elseStatement = _localctx.suite.result; 
					}
				}

				setState(951);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FINALLY) {
					{
					setState(946);
					match(FINALLY);
					setState(947);
					match(COLON);
					setState(948);
					_localctx.suite = suite();
					 finallyStatement = _localctx.suite.result; 
					}
				}

				}
				break;
			case FINALLY:
				{
				setState(953);
				match(FINALLY);
				setState(954);
				match(COLON);
				setState(955);
				_localctx.suite = suite();
				 finallyStatement = _localctx.suite.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			 push(new TrySSTNode(_localctx.body.result, getArray(start, ExceptSSTNode[].class), elseStatement, finallyStatement, getStartIndex(_localctx), getLastIndex(_localctx))); 
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

	public static class Except_clauseContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext test;
		public Token NAME;
		public SuiteContext suite;
		public TerminalNode EXCEPT() { return getToken(Python3Parser.EXCEPT, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public SuiteContext suite() {
			return getRuleContext(SuiteContext.class,0);
		}
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public TerminalNode AS() { return getToken(Python3Parser.AS, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public Except_clauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_except_clause; }
	}

	public final Except_clauseContext except_clause() throws RecognitionException {
		Except_clauseContext _localctx = new Except_clauseContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_except_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(962);
			match(EXCEPT);
			 SSTNode testNode = null; String asName = null; 
			setState(971);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(964);
				_localctx.test = test();
				 testNode = _localctx.test.result; 
				setState(969);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(966);
					match(AS);
					setState(967);
					_localctx.NAME = match(NAME);
					 
					                        asName = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
					                        factory.getScopeEnvironment().createLocal(asName);
					                    
					}
				}

				}
			}

			setState(973);
			match(COLON);
			setState(974);
			_localctx.suite = suite();
			 _localctx.result =  new ExceptSSTNode(testNode, asName, _localctx.suite.result, getStartIndex(_localctx), getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null))); 
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

	public static class With_stmtContext extends ParserRuleContext {
		public With_itemContext with_item;
		public TerminalNode WITH() { return getToken(Python3Parser.WITH, 0); }
		public With_itemContext with_item() {
			return getRuleContext(With_itemContext.class,0);
		}
		public With_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_with_stmt; }
	}

	public final With_stmtContext with_stmt() throws RecognitionException {
		With_stmtContext _localctx = new With_stmtContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_with_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(977);
			match(WITH);
			setState(978);
			_localctx.with_item = with_item();
			 
			            _localctx.with_item.result.setStartOffset(getStartIndex(_localctx));
			            push(_localctx.with_item.result); 
			        
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

	public static class With_itemContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext test;
		public ExprContext expr;
		public With_itemContext with_item;
		public SuiteContext suite;
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public TerminalNode COMMA() { return getToken(Python3Parser.COMMA, 0); }
		public With_itemContext with_item() {
			return getRuleContext(With_itemContext.class,0);
		}
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public SuiteContext suite() {
			return getRuleContext(SuiteContext.class,0);
		}
		public TerminalNode AS() { return getToken(Python3Parser.AS, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public With_itemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_with_item; }
	}

	public final With_itemContext with_item() throws RecognitionException {
		With_itemContext _localctx = new With_itemContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_with_item);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(981);
			_localctx.test = test();
			 SSTNode asName = null; 
			setState(987);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(983);
				match(AS);
				setState(984);
				_localctx.expr = expr();
				 asName = _localctx.expr.result; 
				}
			}

			 SSTNode sub; 
			setState(998);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COMMA:
				{
				setState(990);
				match(COMMA);
				setState(991);
				_localctx.with_item = with_item();
				 sub = _localctx.with_item.result; 
				}
				break;
			case COLON:
				{
				setState(994);
				match(COLON);
				setState(995);
				_localctx.suite = suite();
				 sub = _localctx.suite.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			 _localctx.result =  factory.createWith(_localctx.test.result, asName, sub, -1, getLastIndex(_localctx)); 
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

	public static class SuiteContext extends ParserRuleContext {
		public SSTNode result;
		public ArrayList<SSTNode> list;
		public Simple_stmtContext simple_stmt() {
			return getRuleContext(Simple_stmtContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(Python3Parser.NEWLINE, 0); }
		public TerminalNode INDENT() { return getToken(Python3Parser.INDENT, 0); }
		public TerminalNode DEDENT() { return getToken(Python3Parser.DEDENT, 0); }
		public List<StmtContext> stmt() {
			return getRuleContexts(StmtContext.class);
		}
		public StmtContext stmt(int i) {
			return getRuleContext(StmtContext.class,i);
		}
		public SuiteContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_suite; }
	}

	public final SuiteContext suite() throws RecognitionException {
		SuiteContext _localctx = new SuiteContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_suite);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 int start = start(); 
			setState(1013);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case RETURN:
			case RAISE:
			case FROM:
			case IMPORT:
			case GLOBAL:
			case NONLOCAL:
			case ASSERT:
			case LAMBDA:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case YIELD:
			case DEL:
			case PASS:
			case CONTINUE:
			case BREAK:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case STAR:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				{
				setState(1003);
				simple_stmt();
				}
				break;
			case NEWLINE:
				{
				setState(1004);
				match(NEWLINE);
				setState(1005);
				match(INDENT);
				setState(1007); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1006);
					stmt();
					}
					}
					setState(1009); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << DEF) | (1L << RETURN) | (1L << RAISE) | (1L << FROM) | (1L << IMPORT) | (1L << GLOBAL) | (1L << NONLOCAL) | (1L << ASSERT) | (1L << IF) | (1L << WHILE) | (1L << FOR) | (1L << TRY) | (1L << WITH) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << CLASS) | (1L << YIELD) | (1L << DEL) | (1L << PASS) | (1L << CONTINUE) | (1L << BREAK) | (1L << ASYNC) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)) | (1L << (AT - 64)))) != 0) );
				setState(1011);
				match(DEDENT);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			 _localctx.result =  new BlockSSTNode(getArray(start, SSTNode[].class));
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

	public static class TestContext extends ParserRuleContext {
		public SSTNode result;
		public Or_testContext or_test;
		public Or_testContext condition;
		public TestContext elTest;
		public LambdefContext lambdef;
		public List<Or_testContext> or_test() {
			return getRuleContexts(Or_testContext.class);
		}
		public Or_testContext or_test(int i) {
			return getRuleContext(Or_testContext.class,i);
		}
		public TerminalNode IF() { return getToken(Python3Parser.IF, 0); }
		public TerminalNode ELSE() { return getToken(Python3Parser.ELSE, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public LambdefContext lambdef() {
			return getRuleContext(LambdefContext.class,0);
		}
		public TestContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_test; }
	}

	public final TestContext test() throws RecognitionException {
		TestContext _localctx = new TestContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_test);
		int _la;
		try {
			setState(1030);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				enterOuterAlt(_localctx, 1);
				{
				setState(1017);
				_localctx.or_test = or_test();
				 _localctx.result =  _localctx.or_test.result; 
				setState(1025);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==IF) {
					{
					setState(1019);
					match(IF);
					setState(1020);
					_localctx.condition = _localctx.or_test = or_test();
					setState(1021);
					match(ELSE);
					setState(1022);
					_localctx.elTest = test();
					 _localctx.result =  new TernaryIfSSTNode(_localctx.condition.result, _localctx.result, _localctx.elTest.result, getStartIndex(_localctx), getLastIndex(_localctx));
					}
				}

				}
				break;
			case LAMBDA:
				enterOuterAlt(_localctx, 2);
				{
				setState(1027);
				_localctx.lambdef = lambdef();
				 _localctx.result =  _localctx.lambdef.result; 
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

	public static class Test_nocondContext extends ParserRuleContext {
		public SSTNode result;
		public Or_testContext or_test;
		public Lambdef_nocondContext lambdef_nocond;
		public Or_testContext or_test() {
			return getRuleContext(Or_testContext.class,0);
		}
		public Lambdef_nocondContext lambdef_nocond() {
			return getRuleContext(Lambdef_nocondContext.class,0);
		}
		public Test_nocondContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_test_nocond; }
	}

	public final Test_nocondContext test_nocond() throws RecognitionException {
		Test_nocondContext _localctx = new Test_nocondContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_test_nocond);
		try {
			setState(1038);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				enterOuterAlt(_localctx, 1);
				{
				setState(1032);
				_localctx.or_test = or_test();
				 _localctx.result =  _localctx.or_test.result; 
				}
				break;
			case LAMBDA:
				enterOuterAlt(_localctx, 2);
				{
				setState(1035);
				_localctx.lambdef_nocond = lambdef_nocond();
				 _localctx.result =  _localctx.lambdef_nocond.result; 
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

	public static class LambdefContext extends ParserRuleContext {
		public SSTNode result;
		public Token l;
		public VarargslistContext varargslist;
		public TestContext test;
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public TerminalNode LAMBDA() { return getToken(Python3Parser.LAMBDA, 0); }
		public VarargslistContext varargslist() {
			return getRuleContext(VarargslistContext.class,0);
		}
		public LambdefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_lambdef; }
	}

	public final LambdefContext lambdef() throws RecognitionException {
		LambdefContext _localctx = new LambdefContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_lambdef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1040);
			_localctx.l = match(LAMBDA);
			 ArgDefListBuilder args = null; 
			setState(1045);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(1042);
				_localctx.varargslist = varargslist();
				 args = _localctx.varargslist.result; 
				}
			}


			            ScopeInfo functionScope = scopeEnvironment.pushScope(ScopeEnvironment.LAMBDA_NAME, ScopeInfo.ScopeKind.Function); 
			            functionScope.setHasAnnotations(true);
			            if (args != null) {
			                args.defineParamsInScope(functionScope);
			            }
			        
			setState(1048);
			match(COLON);
			setState(1049);
			_localctx.test = test();
			 scopeEnvironment.popScope(); 
			 _localctx.result =  new LambdaSSTNode(functionScope, args, _localctx.test.result, getStartIndex(_localctx), getLastIndex(_localctx)); 
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

	public static class Lambdef_nocondContext extends ParserRuleContext {
		public SSTNode result;
		public Token l;
		public VarargslistContext varargslist;
		public Test_nocondContext test_nocond;
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public Test_nocondContext test_nocond() {
			return getRuleContext(Test_nocondContext.class,0);
		}
		public TerminalNode LAMBDA() { return getToken(Python3Parser.LAMBDA, 0); }
		public VarargslistContext varargslist() {
			return getRuleContext(VarargslistContext.class,0);
		}
		public Lambdef_nocondContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_lambdef_nocond; }
	}

	public final Lambdef_nocondContext lambdef_nocond() throws RecognitionException {
		Lambdef_nocondContext _localctx = new Lambdef_nocondContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_lambdef_nocond);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1053);
			_localctx.l = match(LAMBDA);
			 ArgDefListBuilder args = null; 
			setState(1058);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(1055);
				_localctx.varargslist = varargslist();
				 args = _localctx.varargslist.result;
				}
			}


			            ScopeInfo functionScope = scopeEnvironment.pushScope(ScopeEnvironment.LAMBDA_NAME, ScopeInfo.ScopeKind.Function); 
			            functionScope.setHasAnnotations(true);
			            if (args != null) {
			                args.defineParamsInScope(functionScope);
			            }
			        
			setState(1061);
			match(COLON);
			setState(1062);
			_localctx.test_nocond = test_nocond();
			 scopeEnvironment.popScope(); 
			 _localctx.result =  new LambdaSSTNode(functionScope, args, _localctx.test_nocond.result, getStartIndex(_localctx), getLastIndex(_localctx)); 
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

	public static class Or_testContext extends ParserRuleContext {
		public SSTNode result;
		public And_testContext first;
		public And_testContext and_test;
		public List<And_testContext> and_test() {
			return getRuleContexts(And_testContext.class);
		}
		public And_testContext and_test(int i) {
			return getRuleContext(And_testContext.class,i);
		}
		public List<TerminalNode> OR() { return getTokens(Python3Parser.OR); }
		public TerminalNode OR(int i) {
			return getToken(Python3Parser.OR, i);
		}
		public Or_testContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_or_test; }
	}

	public final Or_testContext or_test() throws RecognitionException {
		Or_testContext _localctx = new Or_testContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_or_test);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1066);
			_localctx.first = _localctx.and_test = and_test();
			setState(1080);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OR:
				{
				 int start = start(); 
				 push(_localctx.first.result); 
				setState(1073); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1069);
					match(OR);
					setState(1070);
					_localctx.and_test = and_test();
					 push(_localctx.and_test.result); 
					}
					}
					setState(1075); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==OR );
				 _localctx.result =  new OrSSTNode(getArray(start, SSTNode[].class), getStartIndex(_localctx), getLastIndex(_localctx)); 
				}
				break;
			case EOF:
			case FROM:
			case AS:
			case IF:
			case ELSE:
			case FOR:
			case ASYNC:
			case NEWLINE:
			case CLOSE_PAREN:
			case COMMA:
			case COLON:
			case SEMI_COLON:
			case ASSIGN:
			case CLOSE_BRACK:
			case CLOSE_BRACE:
			case ADD_ASSIGN:
			case SUB_ASSIGN:
			case MULT_ASSIGN:
			case AT_ASSIGN:
			case DIV_ASSIGN:
			case MOD_ASSIGN:
			case AND_ASSIGN:
			case OR_ASSIGN:
			case XOR_ASSIGN:
			case LEFT_SHIFT_ASSIGN:
			case RIGHT_SHIFT_ASSIGN:
			case POWER_ASSIGN:
			case IDIV_ASSIGN:
				{
				 _localctx.result =  _localctx.first.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
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

	public static class And_testContext extends ParserRuleContext {
		public SSTNode result;
		public Not_testContext first;
		public Not_testContext not_test;
		public List<Not_testContext> not_test() {
			return getRuleContexts(Not_testContext.class);
		}
		public Not_testContext not_test(int i) {
			return getRuleContext(Not_testContext.class,i);
		}
		public List<TerminalNode> AND() { return getTokens(Python3Parser.AND); }
		public TerminalNode AND(int i) {
			return getToken(Python3Parser.AND, i);
		}
		public And_testContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_and_test; }
	}

	public final And_testContext and_test() throws RecognitionException {
		And_testContext _localctx = new And_testContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_and_test);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1082);
			_localctx.first = _localctx.not_test = not_test();
			setState(1096);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AND:
				{
				 int start = start(); 
				 push(_localctx.first.result); 
				setState(1089); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1085);
					match(AND);
					setState(1086);
					_localctx.not_test = not_test();
					 push(_localctx.not_test.result); 
					}
					}
					setState(1091); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==AND );
				 _localctx.result =  new AndSSTNode(getArray(start, SSTNode[].class), getStartIndex(_localctx), getLastIndex(_localctx)); 
				}
				break;
			case EOF:
			case FROM:
			case AS:
			case IF:
			case ELSE:
			case FOR:
			case OR:
			case ASYNC:
			case NEWLINE:
			case CLOSE_PAREN:
			case COMMA:
			case COLON:
			case SEMI_COLON:
			case ASSIGN:
			case CLOSE_BRACK:
			case CLOSE_BRACE:
			case ADD_ASSIGN:
			case SUB_ASSIGN:
			case MULT_ASSIGN:
			case AT_ASSIGN:
			case DIV_ASSIGN:
			case MOD_ASSIGN:
			case AND_ASSIGN:
			case OR_ASSIGN:
			case XOR_ASSIGN:
			case LEFT_SHIFT_ASSIGN:
			case RIGHT_SHIFT_ASSIGN:
			case POWER_ASSIGN:
			case IDIV_ASSIGN:
				{
				 _localctx.result =  _localctx.first.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
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

	public static class Not_testContext extends ParserRuleContext {
		public SSTNode result;
		public Not_testContext not_test;
		public ComparisonContext comparison;
		public TerminalNode NOT() { return getToken(Python3Parser.NOT, 0); }
		public Not_testContext not_test() {
			return getRuleContext(Not_testContext.class,0);
		}
		public ComparisonContext comparison() {
			return getRuleContext(ComparisonContext.class,0);
		}
		public Not_testContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_not_test; }
	}

	public final Not_testContext not_test() throws RecognitionException {
		Not_testContext _localctx = new Not_testContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_not_test);
		try {
			setState(1105);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(1098);
				match(NOT);
				setState(1099);
				_localctx.not_test = not_test();
				 _localctx.result =  new NotSSTNode(_localctx.not_test.result, getStartIndex(_localctx), getLastIndex(_localctx)); 
				}
				break;
			case STRING:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				enterOuterAlt(_localctx, 2);
				{
				setState(1102);
				_localctx.comparison = comparison();
				 _localctx.result =  _localctx.comparison.result; 
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

	public static class ComparisonContext extends ParserRuleContext {
		public SSTNode result;
		public ExprContext first;
		public ExprContext expr;
		public Comp_opContext comp_op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<Comp_opContext> comp_op() {
			return getRuleContexts(Comp_opContext.class);
		}
		public Comp_opContext comp_op(int i) {
			return getRuleContext(Comp_opContext.class,i);
		}
		public ComparisonContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparison; }
	}

	public final ComparisonContext comparison() throws RecognitionException {
		ComparisonContext _localctx = new ComparisonContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_comparison);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1107);
			_localctx.first = _localctx.expr = expr();
			setState(1120);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IN:
			case NOT:
			case IS:
			case LESS_THAN:
			case GREATER_THAN:
			case EQUALS:
			case GT_EQ:
			case LT_EQ:
			case NOT_EQ_1:
			case NOT_EQ_2:
				{
				 int start = start(); int stringStart = stringStart(); 
				setState(1113); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1109);
					_localctx.comp_op = comp_op();
					setState(1110);
					_localctx.expr = expr();
					 pushString(_localctx.comp_op.result); push(_localctx.expr.result); 
					}
					}
					setState(1115); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( ((((_la - 16)) & ~0x3f) == 0 && ((1L << (_la - 16)) & ((1L << (IN - 16)) | (1L << (NOT - 16)) | (1L << (IS - 16)) | (1L << (LESS_THAN - 16)) | (1L << (GREATER_THAN - 16)) | (1L << (EQUALS - 16)) | (1L << (GT_EQ - 16)) | (1L << (LT_EQ - 16)) | (1L << (NOT_EQ_1 - 16)) | (1L << (NOT_EQ_2 - 16)))) != 0) );
				 _localctx.result =  new ComparisonSSTNode(_localctx.first.result, getStringArray(stringStart), getArray(start, SSTNode[].class), getStartIndex(_localctx), getStopIndex((_localctx.expr!=null?(_localctx.expr.stop):null))); 
				}
				break;
			case EOF:
			case FROM:
			case AS:
			case IF:
			case ELSE:
			case FOR:
			case OR:
			case AND:
			case ASYNC:
			case NEWLINE:
			case CLOSE_PAREN:
			case COMMA:
			case COLON:
			case SEMI_COLON:
			case ASSIGN:
			case CLOSE_BRACK:
			case CLOSE_BRACE:
			case ADD_ASSIGN:
			case SUB_ASSIGN:
			case MULT_ASSIGN:
			case AT_ASSIGN:
			case DIV_ASSIGN:
			case MOD_ASSIGN:
			case AND_ASSIGN:
			case OR_ASSIGN:
			case XOR_ASSIGN:
			case LEFT_SHIFT_ASSIGN:
			case RIGHT_SHIFT_ASSIGN:
			case POWER_ASSIGN:
			case IDIV_ASSIGN:
				{
				 _localctx.result =  _localctx.first.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
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

	public static class Comp_opContext extends ParserRuleContext {
		public String result;
		public TerminalNode LESS_THAN() { return getToken(Python3Parser.LESS_THAN, 0); }
		public TerminalNode GREATER_THAN() { return getToken(Python3Parser.GREATER_THAN, 0); }
		public TerminalNode EQUALS() { return getToken(Python3Parser.EQUALS, 0); }
		public TerminalNode GT_EQ() { return getToken(Python3Parser.GT_EQ, 0); }
		public TerminalNode LT_EQ() { return getToken(Python3Parser.LT_EQ, 0); }
		public TerminalNode NOT_EQ_1() { return getToken(Python3Parser.NOT_EQ_1, 0); }
		public TerminalNode NOT_EQ_2() { return getToken(Python3Parser.NOT_EQ_2, 0); }
		public TerminalNode IN() { return getToken(Python3Parser.IN, 0); }
		public TerminalNode NOT() { return getToken(Python3Parser.NOT, 0); }
		public TerminalNode IS() { return getToken(Python3Parser.IS, 0); }
		public Comp_opContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comp_op; }
	}

	public final Comp_opContext comp_op() throws RecognitionException {
		Comp_opContext _localctx = new Comp_opContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_comp_op);
		try {
			setState(1146);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,134,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1122);
				match(LESS_THAN);
				 _localctx.result =  "<"; 
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1124);
				match(GREATER_THAN);
				 _localctx.result =  ">"; 
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1126);
				match(EQUALS);
				 _localctx.result =  "=="; 
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1128);
				match(GT_EQ);
				 _localctx.result =  ">="; 
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1130);
				match(LT_EQ);
				 _localctx.result =  "<="; 
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(1132);
				match(NOT_EQ_1);
				 _localctx.result =  "<>"; 
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(1134);
				match(NOT_EQ_2);
				 _localctx.result =  "!="; 
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(1136);
				match(IN);
				 _localctx.result =  "in"; 
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(1138);
				match(NOT);
				setState(1139);
				match(IN);
				 _localctx.result =  "notin"; 
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(1141);
				match(IS);
				 _localctx.result =  "is"; 
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(1143);
				match(IS);
				setState(1144);
				match(NOT);
				 _localctx.result =  "isnot"; 
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

	public static class Star_exprContext extends ParserRuleContext {
		public SSTNode result;
		public ExprContext expr;
		public TerminalNode STAR() { return getToken(Python3Parser.STAR, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public Star_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_star_expr; }
	}

	public final Star_exprContext star_expr() throws RecognitionException {
		Star_exprContext _localctx = new Star_exprContext(_ctx, getState());
		enterRule(_localctx, 120, RULE_star_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1148);
			match(STAR);
			setState(1149);
			_localctx.expr = expr();
			 _localctx.result =  new StarSSTNode(_localctx.expr.result, getStartIndex(_localctx), getLastIndex(_localctx));
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

	public static class ExprContext extends ParserRuleContext {
		public SSTNode result;
		public Xor_exprContext xor_expr;
		public List<Xor_exprContext> xor_expr() {
			return getRuleContexts(Xor_exprContext.class);
		}
		public Xor_exprContext xor_expr(int i) {
			return getRuleContext(Xor_exprContext.class,i);
		}
		public List<TerminalNode> OR_OP() { return getTokens(Python3Parser.OR_OP); }
		public TerminalNode OR_OP(int i) {
			return getToken(Python3Parser.OR_OP, i);
		}
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
	}

	public final ExprContext expr() throws RecognitionException {
		ExprContext _localctx = new ExprContext(_ctx, getState());
		enterRule(_localctx, 122, RULE_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1152);
			_localctx.xor_expr = xor_expr();
			 _localctx.result =  _localctx.xor_expr.result; 
			setState(1160);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR_OP) {
				{
				{
				setState(1154);
				match(OR_OP);
				setState(1155);
				_localctx.xor_expr = xor_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.Or, _localctx.result, _localctx.xor_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.xor_expr!=null?(_localctx.xor_expr.stop):null)));
				}
				}
				setState(1162);
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

	public static class Xor_exprContext extends ParserRuleContext {
		public SSTNode result;
		public And_exprContext and_expr;
		public List<And_exprContext> and_expr() {
			return getRuleContexts(And_exprContext.class);
		}
		public And_exprContext and_expr(int i) {
			return getRuleContext(And_exprContext.class,i);
		}
		public List<TerminalNode> XOR() { return getTokens(Python3Parser.XOR); }
		public TerminalNode XOR(int i) {
			return getToken(Python3Parser.XOR, i);
		}
		public Xor_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_xor_expr; }
	}

	public final Xor_exprContext xor_expr() throws RecognitionException {
		Xor_exprContext _localctx = new Xor_exprContext(_ctx, getState());
		enterRule(_localctx, 124, RULE_xor_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1163);
			_localctx.and_expr = and_expr();
			 _localctx.result =  _localctx.and_expr.result; 
			setState(1171);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==XOR) {
				{
				{
				setState(1165);
				match(XOR);
				setState(1166);
				_localctx.and_expr = and_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.Xor, _localctx.result, _localctx.and_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.and_expr!=null?(_localctx.and_expr.stop):null))); 
				}
				}
				setState(1173);
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

	public static class And_exprContext extends ParserRuleContext {
		public SSTNode result;
		public Shift_exprContext shift_expr;
		public List<Shift_exprContext> shift_expr() {
			return getRuleContexts(Shift_exprContext.class);
		}
		public Shift_exprContext shift_expr(int i) {
			return getRuleContext(Shift_exprContext.class,i);
		}
		public List<TerminalNode> AND_OP() { return getTokens(Python3Parser.AND_OP); }
		public TerminalNode AND_OP(int i) {
			return getToken(Python3Parser.AND_OP, i);
		}
		public And_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_and_expr; }
	}

	public final And_exprContext and_expr() throws RecognitionException {
		And_exprContext _localctx = new And_exprContext(_ctx, getState());
		enterRule(_localctx, 126, RULE_and_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1174);
			_localctx.shift_expr = shift_expr();
			 _localctx.result =  _localctx.shift_expr.result; 
			setState(1182);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND_OP) {
				{
				{
				setState(1176);
				match(AND_OP);
				setState(1177);
				_localctx.shift_expr = shift_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.And, _localctx.result, _localctx.shift_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.shift_expr!=null?(_localctx.shift_expr.stop):null))); 
				}
				}
				setState(1184);
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

	public static class Shift_exprContext extends ParserRuleContext {
		public SSTNode result;
		public Arith_exprContext arith_expr;
		public List<Arith_exprContext> arith_expr() {
			return getRuleContexts(Arith_exprContext.class);
		}
		public Arith_exprContext arith_expr(int i) {
			return getRuleContext(Arith_exprContext.class,i);
		}
		public List<TerminalNode> LEFT_SHIFT() { return getTokens(Python3Parser.LEFT_SHIFT); }
		public TerminalNode LEFT_SHIFT(int i) {
			return getToken(Python3Parser.LEFT_SHIFT, i);
		}
		public List<TerminalNode> RIGHT_SHIFT() { return getTokens(Python3Parser.RIGHT_SHIFT); }
		public TerminalNode RIGHT_SHIFT(int i) {
			return getToken(Python3Parser.RIGHT_SHIFT, i);
		}
		public Shift_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_shift_expr; }
	}

	public final Shift_exprContext shift_expr() throws RecognitionException {
		Shift_exprContext _localctx = new Shift_exprContext(_ctx, getState());
		enterRule(_localctx, 128, RULE_shift_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1185);
			_localctx.arith_expr = arith_expr();
			 _localctx.result =  _localctx.arith_expr.result; 
			setState(1199);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LEFT_SHIFT || _la==RIGHT_SHIFT) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1192);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case LEFT_SHIFT:
					{
					setState(1188);
					match(LEFT_SHIFT);
					 arithmetic = BinaryArithmetic.LShift; 
					}
					break;
				case RIGHT_SHIFT:
					{
					setState(1190);
					match(RIGHT_SHIFT);
					 arithmetic = BinaryArithmetic.RShift; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1194);
				_localctx.arith_expr = arith_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.arith_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.arith_expr!=null?(_localctx.arith_expr.stop):null)));
				}
				}
				setState(1201);
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

	public static class Arith_exprContext extends ParserRuleContext {
		public SSTNode result;
		public TermContext term;
		public List<TermContext> term() {
			return getRuleContexts(TermContext.class);
		}
		public TermContext term(int i) {
			return getRuleContext(TermContext.class,i);
		}
		public List<TerminalNode> ADD() { return getTokens(Python3Parser.ADD); }
		public TerminalNode ADD(int i) {
			return getToken(Python3Parser.ADD, i);
		}
		public List<TerminalNode> MINUS() { return getTokens(Python3Parser.MINUS); }
		public TerminalNode MINUS(int i) {
			return getToken(Python3Parser.MINUS, i);
		}
		public Arith_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arith_expr; }
	}

	public final Arith_exprContext arith_expr() throws RecognitionException {
		Arith_exprContext _localctx = new Arith_exprContext(_ctx, getState());
		enterRule(_localctx, 130, RULE_arith_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1202);
			_localctx.term = term();
			 _localctx.result =  _localctx.term.result; 
			setState(1216);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ADD || _la==MINUS) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1209);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ADD:
					{
					setState(1205);
					match(ADD);
					 arithmetic = BinaryArithmetic.Add; 
					}
					break;
				case MINUS:
					{
					setState(1207);
					match(MINUS);
					 arithmetic = BinaryArithmetic.Sub; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1211);
				_localctx.term = term();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.term.result, getStartIndex(_localctx), getStopIndex((_localctx.term!=null?(_localctx.term.stop):null))); 
				}
				}
				setState(1218);
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

	public static class TermContext extends ParserRuleContext {
		public SSTNode result;
		public FactorContext factor;
		public List<FactorContext> factor() {
			return getRuleContexts(FactorContext.class);
		}
		public FactorContext factor(int i) {
			return getRuleContext(FactorContext.class,i);
		}
		public List<TerminalNode> STAR() { return getTokens(Python3Parser.STAR); }
		public TerminalNode STAR(int i) {
			return getToken(Python3Parser.STAR, i);
		}
		public List<TerminalNode> AT() { return getTokens(Python3Parser.AT); }
		public TerminalNode AT(int i) {
			return getToken(Python3Parser.AT, i);
		}
		public List<TerminalNode> DIV() { return getTokens(Python3Parser.DIV); }
		public TerminalNode DIV(int i) {
			return getToken(Python3Parser.DIV, i);
		}
		public List<TerminalNode> MOD() { return getTokens(Python3Parser.MOD); }
		public TerminalNode MOD(int i) {
			return getToken(Python3Parser.MOD, i);
		}
		public List<TerminalNode> IDIV() { return getTokens(Python3Parser.IDIV); }
		public TerminalNode IDIV(int i) {
			return getToken(Python3Parser.IDIV, i);
		}
		public TermContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_term; }
	}

	public final TermContext term() throws RecognitionException {
		TermContext _localctx = new TermContext(_ctx, getState());
		enterRule(_localctx, 132, RULE_term);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1219);
			_localctx.factor = factor();
			 _localctx.result =  _localctx.factor.result; 
			setState(1239);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((((_la - 49)) & ~0x3f) == 0 && ((1L << (_la - 49)) & ((1L << (STAR - 49)) | (1L << (DIV - 49)) | (1L << (MOD - 49)) | (1L << (IDIV - 49)) | (1L << (AT - 49)))) != 0)) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1232);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STAR:
					{
					setState(1222);
					match(STAR);
					 arithmetic = BinaryArithmetic.Mul; 
					}
					break;
				case AT:
					{
					setState(1224);
					match(AT);
					 arithmetic = BinaryArithmetic.MatMul; 
					}
					break;
				case DIV:
					{
					setState(1226);
					match(DIV);
					 arithmetic = BinaryArithmetic.TrueDiv; 
					}
					break;
				case MOD:
					{
					setState(1228);
					match(MOD);
					 arithmetic = BinaryArithmetic.Mod; 
					}
					break;
				case IDIV:
					{
					setState(1230);
					match(IDIV);
					 arithmetic = BinaryArithmetic.FloorDiv; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1234);
				_localctx.factor = factor();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.factor.result, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
				}
				}
				setState(1241);
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

	public static class FactorContext extends ParserRuleContext {
		public SSTNode result;
		public Token m;
		public FactorContext factor;
		public PowerContext power;
		public FactorContext factor() {
			return getRuleContext(FactorContext.class,0);
		}
		public TerminalNode ADD() { return getToken(Python3Parser.ADD, 0); }
		public TerminalNode NOT_OP() { return getToken(Python3Parser.NOT_OP, 0); }
		public TerminalNode MINUS() { return getToken(Python3Parser.MINUS, 0); }
		public PowerContext power() {
			return getRuleContext(PowerContext.class,0);
		}
		public FactorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_factor; }
	}

	public final FactorContext factor() throws RecognitionException {
		FactorContext _localctx = new FactorContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_factor);
		try {
			setState(1257);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ADD:
			case MINUS:
			case NOT_OP:
				enterOuterAlt(_localctx, 1);
				{
				 
				            UnaryArithmetic arithmetic; 
				            boolean isNeg = false;
				        
				setState(1249);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ADD:
					{
					setState(1243);
					match(ADD);
					 arithmetic = UnaryArithmetic.Pos; 
					}
					break;
				case MINUS:
					{
					setState(1245);
					_localctx.m = match(MINUS);
					 arithmetic = UnaryArithmetic.Neg; isNeg = true; 
					}
					break;
				case NOT_OP:
					{
					setState(1247);
					match(NOT_OP);
					 arithmetic = UnaryArithmetic.Invert; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1251);
				_localctx.factor = factor();
				 
				                assert _localctx.factor != null;
				                SSTNode fResult = _localctx.factor.result;
				                if (isNeg && fResult instanceof NumberLiteralSSTNode) {
				                    if (((NumberLiteralSSTNode)fResult).isNegative()) {
				                        // solving cases like --2
				                        _localctx.result =   new UnarySSTNode(UnaryArithmetic.Neg, fResult, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
				                    } else {
				                        ((NumberLiteralSSTNode)fResult).negate();
				                        fResult.setStartOffset(_localctx.m.getStartIndex());
				                        _localctx.result =   fResult;
				                    }
				                } else {
				                    _localctx.result =  new UnarySSTNode(arithmetic, _localctx.factor.result, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
				                }
				            
				}
				break;
			case STRING:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case OPEN_BRACE:
				enterOuterAlt(_localctx, 2);
				{
				setState(1254);
				_localctx.power = power();
				 _localctx.result =  _localctx.power.result; 
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

	public static class PowerContext extends ParserRuleContext {
		public SSTNode result;
		public Atom_exprContext atom_expr;
		public FactorContext factor;
		public Atom_exprContext atom_expr() {
			return getRuleContext(Atom_exprContext.class,0);
		}
		public TerminalNode POWER() { return getToken(Python3Parser.POWER, 0); }
		public FactorContext factor() {
			return getRuleContext(FactorContext.class,0);
		}
		public PowerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_power; }
	}

	public final PowerContext power() throws RecognitionException {
		PowerContext _localctx = new PowerContext(_ctx, getState());
		enterRule(_localctx, 136, RULE_power);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1259);
			_localctx.atom_expr = atom_expr();
			 _localctx.result =  _localctx.atom_expr.result; 
			setState(1265);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==POWER) {
				{
				setState(1261);
				match(POWER);
				setState(1262);
				_localctx.factor = factor();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.Pow, _localctx.result, _localctx.factor.result, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
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

	public static class Atom_exprContext extends ParserRuleContext {
		public SSTNode result;
		public AtomContext atom;
		public ArglistContext arglist;
		public Token CloseB;
		public SubscriptlistContext subscriptlist;
		public Token c;
		public Token NAME;
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public TerminalNode AWAIT() { return getToken(Python3Parser.AWAIT, 0); }
		public List<TerminalNode> OPEN_PAREN() { return getTokens(Python3Parser.OPEN_PAREN); }
		public TerminalNode OPEN_PAREN(int i) {
			return getToken(Python3Parser.OPEN_PAREN, i);
		}
		public List<ArglistContext> arglist() {
			return getRuleContexts(ArglistContext.class);
		}
		public ArglistContext arglist(int i) {
			return getRuleContext(ArglistContext.class,i);
		}
		public List<TerminalNode> OPEN_BRACK() { return getTokens(Python3Parser.OPEN_BRACK); }
		public TerminalNode OPEN_BRACK(int i) {
			return getToken(Python3Parser.OPEN_BRACK, i);
		}
		public List<SubscriptlistContext> subscriptlist() {
			return getRuleContexts(SubscriptlistContext.class);
		}
		public SubscriptlistContext subscriptlist(int i) {
			return getRuleContext(SubscriptlistContext.class,i);
		}
		public List<TerminalNode> DOT() { return getTokens(Python3Parser.DOT); }
		public TerminalNode DOT(int i) {
			return getToken(Python3Parser.DOT, i);
		}
		public List<TerminalNode> NAME() { return getTokens(Python3Parser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(Python3Parser.NAME, i);
		}
		public List<TerminalNode> CLOSE_PAREN() { return getTokens(Python3Parser.CLOSE_PAREN); }
		public TerminalNode CLOSE_PAREN(int i) {
			return getToken(Python3Parser.CLOSE_PAREN, i);
		}
		public List<TerminalNode> CLOSE_BRACK() { return getTokens(Python3Parser.CLOSE_BRACK); }
		public TerminalNode CLOSE_BRACK(int i) {
			return getToken(Python3Parser.CLOSE_BRACK, i);
		}
		public Atom_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom_expr; }
	}

	public final Atom_exprContext atom_expr() throws RecognitionException {
		Atom_exprContext _localctx = new Atom_exprContext(_ctx, getState());
		enterRule(_localctx, 138, RULE_atom_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1268);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AWAIT) {
				{
				setState(1267);
				match(AWAIT);
				}
			}

			setState(1270);
			_localctx.atom = atom();
			 _localctx.result =  _localctx.atom.result; 
			setState(1287);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << DOT) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0)) {
				{
				setState(1285);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case OPEN_PAREN:
					{
					setState(1272);
					match(OPEN_PAREN);
					setState(1273);
					_localctx.arglist = arglist();
					setState(1274);
					_localctx.CloseB = match(CLOSE_PAREN);
					 _localctx.result =  new CallSSTNode(_localctx.result, _localctx.arglist.result, getStartIndex(_localctx), _localctx.CloseB.getStopIndex() + 1);
					}
					break;
				case OPEN_BRACK:
					{
					setState(1277);
					match(OPEN_BRACK);
					setState(1278);
					_localctx.subscriptlist = subscriptlist();
					setState(1279);
					_localctx.c = match(CLOSE_BRACK);
					 _localctx.result =  new SubscriptSSTNode(_localctx.result, _localctx.subscriptlist.result, getStartIndex(_localctx), _localctx.c.getStopIndex() + 1);
					}
					break;
				case DOT:
					{
					setState(1282);
					match(DOT);
					setState(1283);
					_localctx.NAME = match(NAME);
					   
					                    assert _localctx.NAME != null;
					                    _localctx.result =  new GetAttributeSSTNode(_localctx.result, (_localctx.NAME!=null?_localctx.NAME.getText():null), getStartIndex(_localctx), getStopIndex(_localctx.NAME));
					                
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(1289);
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

	public static class AtomContext extends ParserRuleContext {
		public SSTNode result;
		public Yield_exprContext yield_expr;
		public SetlisttuplemakerContext setlisttuplemaker;
		public Token cp;
		public Token startIndex;
		public Token endIndex;
		public DictmakerContext dictmaker;
		public Token NAME;
		public Token DECIMAL_INTEGER;
		public Token OCT_INTEGER;
		public Token HEX_INTEGER;
		public Token BIN_INTEGER;
		public Token FLOAT_NUMBER;
		public Token IMAG_NUMBER;
		public Token STRING;
		public Token t;
		public TerminalNode OPEN_PAREN() { return getToken(Python3Parser.OPEN_PAREN, 0); }
		public TerminalNode CLOSE_PAREN() { return getToken(Python3Parser.CLOSE_PAREN, 0); }
		public Yield_exprContext yield_expr() {
			return getRuleContext(Yield_exprContext.class,0);
		}
		public SetlisttuplemakerContext setlisttuplemaker() {
			return getRuleContext(SetlisttuplemakerContext.class,0);
		}
		public TerminalNode OPEN_BRACK() { return getToken(Python3Parser.OPEN_BRACK, 0); }
		public TerminalNode CLOSE_BRACK() { return getToken(Python3Parser.CLOSE_BRACK, 0); }
		public TerminalNode OPEN_BRACE() { return getToken(Python3Parser.OPEN_BRACE, 0); }
		public TerminalNode CLOSE_BRACE() { return getToken(Python3Parser.CLOSE_BRACE, 0); }
		public DictmakerContext dictmaker() {
			return getRuleContext(DictmakerContext.class,0);
		}
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public TerminalNode DECIMAL_INTEGER() { return getToken(Python3Parser.DECIMAL_INTEGER, 0); }
		public TerminalNode OCT_INTEGER() { return getToken(Python3Parser.OCT_INTEGER, 0); }
		public TerminalNode HEX_INTEGER() { return getToken(Python3Parser.HEX_INTEGER, 0); }
		public TerminalNode BIN_INTEGER() { return getToken(Python3Parser.BIN_INTEGER, 0); }
		public TerminalNode FLOAT_NUMBER() { return getToken(Python3Parser.FLOAT_NUMBER, 0); }
		public TerminalNode IMAG_NUMBER() { return getToken(Python3Parser.IMAG_NUMBER, 0); }
		public List<TerminalNode> STRING() { return getTokens(Python3Parser.STRING); }
		public TerminalNode STRING(int i) {
			return getToken(Python3Parser.STRING, i);
		}
		public TerminalNode ELLIPSIS() { return getToken(Python3Parser.ELLIPSIS, 0); }
		public TerminalNode NONE() { return getToken(Python3Parser.NONE, 0); }
		public TerminalNode TRUE() { return getToken(Python3Parser.TRUE, 0); }
		public TerminalNode FALSE() { return getToken(Python3Parser.FALSE, 0); }
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 140, RULE_atom);
		int _la;
		try {
			setState(1353);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OPEN_PAREN:
				enterOuterAlt(_localctx, 1);
				{
				setState(1290);
				match(OPEN_PAREN);
				setState(1298);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case YIELD:
					{
					setState(1291);
					_localctx.yield_expr = yield_expr();
					 _localctx.result =  _localctx.yield_expr.result; 
					}
					break;
				case STRING:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case STAR:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
					{
					setState(1294);
					_localctx.setlisttuplemaker = setlisttuplemaker(PythonBuiltinClassType.PTuple, PythonBuiltinClassType.PGenerator);
					 _localctx.result =  _localctx.setlisttuplemaker.result; 
					}
					break;
				case CLOSE_PAREN:
					{
					 _localctx.result =  new CollectionSSTNode(new SSTNode[0], PythonBuiltinClassType.PTuple, -1, -1); 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1300);
				_localctx.cp = match(CLOSE_PAREN);
				   
				            if (_localctx.result instanceof CollectionSSTNode) {
				                _localctx.result.setStartOffset(getStartIndex(_localctx)); 
				                _localctx.result.setEndOffset(_localctx.cp.getStopIndex() + 1); 
				            }
				        
				}
				break;
			case OPEN_BRACK:
				enterOuterAlt(_localctx, 2);
				{
				setState(1302);
				_localctx.startIndex = match(OPEN_BRACK);
				setState(1307);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STRING:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case STAR:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
					{
					setState(1303);
					_localctx.setlisttuplemaker = setlisttuplemaker(PythonBuiltinClassType.PList, PythonBuiltinClassType.PList);
					 _localctx.result =  _localctx.setlisttuplemaker.result; 
					}
					break;
				case CLOSE_BRACK:
					{
					 _localctx.result =  new CollectionSSTNode(new SSTNode[0], PythonBuiltinClassType.PList, -1, -1);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1309);
				_localctx.endIndex = match(CLOSE_BRACK);

				            if (!(_localctx.result instanceof ForComprehensionSSTNode)) {
				                _localctx.result.setStartOffset(_localctx.startIndex.getStartIndex());
				                _localctx.result.setEndOffset(_localctx.endIndex.getStopIndex() + 1);
				            }
				        
				}
				break;
			case OPEN_BRACE:
				enterOuterAlt(_localctx, 3);
				{
				setState(1311);
				_localctx.startIndex = match(OPEN_BRACE);
				setState(1319);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,152,_ctx) ) {
				case 1:
					{
					setState(1312);
					_localctx.dictmaker = dictmaker();
					 _localctx.result =  _localctx.dictmaker.result; 
					}
					break;
				case 2:
					{
					setState(1315);
					_localctx.setlisttuplemaker = setlisttuplemaker(PythonBuiltinClassType.PSet, PythonBuiltinClassType.PSet);
					 _localctx.result =  _localctx.setlisttuplemaker.result; 
					}
					break;
				case 3:
					{
					 _localctx.result =   new CollectionSSTNode(new SSTNode[0], PythonBuiltinClassType.PDict, -1, -1);
					}
					break;
				}
				setState(1321);
				_localctx.endIndex = match(CLOSE_BRACE);

				            if (!(_localctx.result instanceof ForComprehensionSSTNode)) {
				                _localctx.result.setStartOffset(_localctx.startIndex.getStartIndex());
				                _localctx.result.setEndOffset(_localctx.endIndex.getStopIndex() + 1);
				            }
				        
				}
				break;
			case NAME:
				enterOuterAlt(_localctx, 4);
				{
				setState(1323);
				_localctx.NAME = match(NAME);
				   
				                String text = (_localctx.NAME!=null?_localctx.NAME.getText():null);
				                _localctx.result =  text != null ? factory.createVariableLookup(text,  _localctx.NAME.getStartIndex(), _localctx.NAME.getStopIndex() + 1) : null; 
				            
				}
				break;
			case DECIMAL_INTEGER:
				enterOuterAlt(_localctx, 5);
				{
				setState(1325);
				_localctx.DECIMAL_INTEGER = match(DECIMAL_INTEGER);
				 
				                String text = (_localctx.DECIMAL_INTEGER!=null?_localctx.DECIMAL_INTEGER.getText():null);
				                _localctx.result =  text != null ? NumberLiteralSSTNode.create(text, 0, 10, _localctx.DECIMAL_INTEGER.getStartIndex(), _localctx.DECIMAL_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case OCT_INTEGER:
				enterOuterAlt(_localctx, 6);
				{
				setState(1327);
				_localctx.OCT_INTEGER = match(OCT_INTEGER);
				 
				                String text = (_localctx.OCT_INTEGER!=null?_localctx.OCT_INTEGER.getText():null);
				                _localctx.result =  text != null ? NumberLiteralSSTNode.create(text, 2, 8, _localctx.OCT_INTEGER.getStartIndex(), _localctx.OCT_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case HEX_INTEGER:
				enterOuterAlt(_localctx, 7);
				{
				setState(1329);
				_localctx.HEX_INTEGER = match(HEX_INTEGER);
				 
				                String text = (_localctx.HEX_INTEGER!=null?_localctx.HEX_INTEGER.getText():null);
				                _localctx.result =  text != null ? NumberLiteralSSTNode.create(text, 2, 16, _localctx.HEX_INTEGER.getStartIndex(), _localctx.HEX_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 8);
				{
				setState(1331);
				_localctx.BIN_INTEGER = match(BIN_INTEGER);
				 
				                String text = (_localctx.BIN_INTEGER!=null?_localctx.BIN_INTEGER.getText():null);
				                _localctx.result =  text != null ? NumberLiteralSSTNode.create(text, 2, 2, _localctx.BIN_INTEGER.getStartIndex(), _localctx.BIN_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case FLOAT_NUMBER:
				enterOuterAlt(_localctx, 9);
				{
				setState(1333);
				_localctx.FLOAT_NUMBER = match(FLOAT_NUMBER);
				   
				                String text = (_localctx.FLOAT_NUMBER!=null?_localctx.FLOAT_NUMBER.getText():null);
				                _localctx.result =  text != null ? FloatLiteralSSTNode.create(text, false, _localctx.FLOAT_NUMBER.getStartIndex(), _localctx.FLOAT_NUMBER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case IMAG_NUMBER:
				enterOuterAlt(_localctx, 10);
				{
				setState(1335);
				_localctx.IMAG_NUMBER = match(IMAG_NUMBER);
				 
				                String text = (_localctx.IMAG_NUMBER!=null?_localctx.IMAG_NUMBER.getText():null);
				                _localctx.result =  text != null ? FloatLiteralSSTNode.create(text, true, _localctx.IMAG_NUMBER.getStartIndex(), _localctx.IMAG_NUMBER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 11);
				{
				 int start = stringStart(); 
				setState(1340); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1338);
					_localctx.STRING = match(STRING);
					 pushString((_localctx.STRING!=null?_localctx.STRING.getText():null)); 
					}
					}
					setState(1342); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==STRING );
				 _localctx.result =  factory.createStringLiteral(getStringArray(start), getStartIndex(_localctx), getStopIndex(_localctx.STRING)); 
				}
				break;
			case ELLIPSIS:
				enterOuterAlt(_localctx, 12);
				{
				setState(1345);
				_localctx.t = match(ELLIPSIS);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new SimpleSSTNode(SimpleSSTNode.Type.ELLIPSIS,  start, start + 3);
				}
				break;
			case NONE:
				enterOuterAlt(_localctx, 13);
				{
				setState(1347);
				_localctx.t = match(NONE);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new SimpleSSTNode(SimpleSSTNode.Type.NONE,  start, start + 4);
				}
				break;
			case TRUE:
				enterOuterAlt(_localctx, 14);
				{
				setState(1349);
				_localctx.t = match(TRUE);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new BooleanLiteralSSTNode(true,  start, start + 4); 
				}
				break;
			case FALSE:
				enterOuterAlt(_localctx, 15);
				{
				setState(1351);
				_localctx.t = match(FALSE);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new BooleanLiteralSSTNode(false, start, start + 5); 
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

	public static class SubscriptlistContext extends ParserRuleContext {
		public SSTNode result;
		public SubscriptContext subscript;
		public List<SubscriptContext> subscript() {
			return getRuleContexts(SubscriptContext.class);
		}
		public SubscriptContext subscript(int i) {
			return getRuleContext(SubscriptContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public SubscriptlistContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subscriptlist; }
	}

	public final SubscriptlistContext subscriptlist() throws RecognitionException {
		SubscriptlistContext _localctx = new SubscriptlistContext(_ctx, getState());
		enterRule(_localctx, 142, RULE_subscriptlist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1355);
			_localctx.subscript = subscript();
			 _localctx.result =  _localctx.subscript.result; 
			setState(1376);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 int start = start(); push(_localctx.result); 
				setState(1358);
				match(COMMA);
				setState(1373);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << COLON) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1359);
					_localctx.subscript = subscript();
					 push(_localctx.subscript.result); 
					setState(1367);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,155,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(1361);
							match(COMMA);
							setState(1362);
							_localctx.subscript = subscript();
							 push(_localctx.subscript.result); 
							}
							} 
						}
						setState(1369);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,155,_ctx);
					}
					setState(1371);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(1370);
						match(COMMA);
						}
					}

					}
				}

				 _localctx.result =  new CollectionSSTNode(getArray(start, SSTNode[].class), PythonBuiltinClassType.PTuple, getStartIndex(_localctx), getLastIndex(_localctx));
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

	public static class SubscriptContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext test;
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public List<TerminalNode> COLON() { return getTokens(Python3Parser.COLON); }
		public TerminalNode COLON(int i) {
			return getToken(Python3Parser.COLON, i);
		}
		public SubscriptContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subscript; }
	}

	public final SubscriptContext subscript() throws RecognitionException {
		SubscriptContext _localctx = new SubscriptContext(_ctx, getState());
		enterRule(_localctx, 144, RULE_subscript);
		int _la;
		try {
			setState(1402);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,163,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1378);
				_localctx.test = test();
				 _localctx.result =  _localctx.test.result; 
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				 SSTNode sliceStart = null; SSTNode sliceEnd = null; SSTNode sliceStep = null; 
				setState(1385);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1382);
					_localctx.test = test();
					 sliceStart = _localctx.test.result; 
					}
				}

				setState(1387);
				match(COLON);
				setState(1391);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1388);
					_localctx.test = test();
					 sliceEnd = _localctx.test.result; 
					}
				}

				setState(1399);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COLON) {
					{
					setState(1393);
					match(COLON);
					setState(1397);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
						{
						setState(1394);
						_localctx.test = test();
						 sliceStep = _localctx.test.result; 
						}
					}

					}
				}

				 _localctx.result =  new SliceSSTNode(sliceStart, sliceEnd, sliceStep, getStartIndex(_localctx), getLastIndex(_localctx)); 
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

	public static class ExprlistContext extends ParserRuleContext {
		public SSTNode[] result;
		public ExprContext expr;
		public Star_exprContext star_expr;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<Star_exprContext> star_expr() {
			return getRuleContexts(Star_exprContext.class);
		}
		public Star_exprContext star_expr(int i) {
			return getRuleContext(Star_exprContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public ExprlistContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exprlist; }
	}

	public final ExprlistContext exprlist() throws RecognitionException {
		ExprlistContext _localctx = new ExprlistContext(_ctx, getState());
		enterRule(_localctx, 146, RULE_exprlist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 int start = start(); 
			setState(1411);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				{
				setState(1405);
				_localctx.expr = expr();
				 push(_localctx.expr.result); 
				}
				break;
			case STAR:
				{
				setState(1408);
				_localctx.star_expr = star_expr();
				 push(_localctx.star_expr.result); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(1424);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,166,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1413);
					match(COMMA);
					setState(1420);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STRING:
					case NONE:
					case TRUE:
					case FALSE:
					case AWAIT:
					case NAME:
					case DECIMAL_INTEGER:
					case OCT_INTEGER:
					case HEX_INTEGER:
					case BIN_INTEGER:
					case FLOAT_NUMBER:
					case IMAG_NUMBER:
					case ELLIPSIS:
					case OPEN_PAREN:
					case OPEN_BRACK:
					case ADD:
					case MINUS:
					case NOT_OP:
					case OPEN_BRACE:
						{
						setState(1414);
						_localctx.expr = expr();
						 push(_localctx.expr.result); 
						}
						break;
					case STAR:
						{
						setState(1417);
						_localctx.star_expr = star_expr();
						 push(_localctx.star_expr.result); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					} 
				}
				setState(1426);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,166,_ctx);
			}
			setState(1428);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(1427);
				match(COMMA);
				}
			}

			 _localctx.result =  getArray(start, SSTNode[].class); 
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

	public static class TestlistContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext test;
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public TestlistContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_testlist; }
	}

	public final TestlistContext testlist() throws RecognitionException {
		TestlistContext _localctx = new TestlistContext(_ctx, getState());
		enterRule(_localctx, 148, RULE_testlist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1432);
			_localctx.test = test();
			 _localctx.result =  _localctx.test.result; 
			setState(1453);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 int start = start(); push(_localctx.result); 
				setState(1435);
				match(COMMA);
				setState(1450);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1436);
					_localctx.test = test();
					 push(_localctx.test.result); 
					setState(1444);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,168,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(1438);
							match(COMMA);
							setState(1439);
							_localctx.test = test();
							 push(_localctx.test.result); 
							}
							} 
						}
						setState(1446);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,168,_ctx);
					}
					setState(1448);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(1447);
						match(COMMA);
						}
					}

					}
				}

				 _localctx.result =  new CollectionSSTNode(getArray(start, SSTNode[].class), PythonBuiltinClassType.PTuple, getStartIndex(_localctx), getLastIndex(_localctx));
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

	public static class DictmakerContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext n;
		public TestContext v;
		public ExprContext expr;
		public Comp_forContext comp_for;
		public Comp_forContext comp_for() {
			return getRuleContext(Comp_forContext.class,0);
		}
		public List<TerminalNode> COLON() { return getTokens(Python3Parser.COLON); }
		public TerminalNode COLON(int i) {
			return getToken(Python3Parser.COLON, i);
		}
		public List<TerminalNode> POWER() { return getTokens(Python3Parser.POWER); }
		public TerminalNode POWER(int i) {
			return getToken(Python3Parser.POWER, i);
		}
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public DictmakerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_dictmaker; }
	}

	public final DictmakerContext dictmaker() throws RecognitionException {
		DictmakerContext _localctx = new DictmakerContext(_ctx, getState());
		enterRule(_localctx, 150, RULE_dictmaker);
		int _la;
		try {
			int _alt;
			setState(1505);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,177,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				 
				                SSTNode value; 
				                SSTNode name;
				                ScopeInfo generator = scopeEnvironment.pushScope("generator", ScopeInfo.ScopeKind.DictComp);
				                generator.setHasAnnotations(true);
				                
				            
				setState(1465);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STRING:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
					{
					setState(1456);
					_localctx.n = test();
					setState(1457);
					match(COLON);
					setState(1458);
					_localctx.v = test();
					 name = _localctx.n.result; value = _localctx.v.result; 
					}
					break;
				case POWER:
					{
					setState(1461);
					match(POWER);
					setState(1462);
					_localctx.expr = expr();
					 name = null; value = _localctx.expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1467);
				_localctx.comp_for = comp_for(value, name, PythonBuiltinClassType.PDict, 0);
				 
				                _localctx.result =  _localctx.comp_for.result;
				               scopeEnvironment.popScope();
				            
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				 
				                SSTNode value; 
				                SSTNode name;
				            
				setState(1480);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STRING:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
					{
					setState(1471);
					_localctx.n = test();
					setState(1472);
					match(COLON);
					setState(1473);
					_localctx.v = test();
					 name = _localctx.n.result; value = _localctx.v.result; 
					}
					break;
				case POWER:
					{
					setState(1476);
					match(POWER);
					setState(1477);
					_localctx.expr = expr();
					 name = null; value = _localctx.expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 int start = start(); push(name); push(value); 
				setState(1497);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,175,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1483);
						match(COMMA);
						setState(1493);
						_errHandler.sync(this);
						switch (_input.LA(1)) {
						case STRING:
						case LAMBDA:
						case NOT:
						case NONE:
						case TRUE:
						case FALSE:
						case AWAIT:
						case NAME:
						case DECIMAL_INTEGER:
						case OCT_INTEGER:
						case HEX_INTEGER:
						case BIN_INTEGER:
						case FLOAT_NUMBER:
						case IMAG_NUMBER:
						case ELLIPSIS:
						case OPEN_PAREN:
						case OPEN_BRACK:
						case ADD:
						case MINUS:
						case NOT_OP:
						case OPEN_BRACE:
							{
							setState(1484);
							_localctx.n = test();
							setState(1485);
							match(COLON);
							setState(1486);
							_localctx.v = test();
							 push(_localctx.n.result); push(_localctx.v.result); 
							}
							break;
						case POWER:
							{
							setState(1489);
							match(POWER);
							setState(1490);
							_localctx.expr = expr();
							 push(null); push(_localctx.expr.result); 
							}
							break;
						default:
							throw new NoViableAltException(this);
						}
						}
						} 
					}
					setState(1499);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,175,_ctx);
				}
				setState(1501);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1500);
					match(COMMA);
					}
				}

				 _localctx.result =  new CollectionSSTNode(getArray(start, SSTNode[].class), PythonBuiltinClassType.PDict, -1, -1); 
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

	public static class SetlisttuplemakerContext extends ParserRuleContext {
		public PythonBuiltinClassType type;
		public PythonBuiltinClassType compType;
		public SSTNode result;
		public TestContext test;
		public Star_exprContext star_expr;
		public Comp_forContext comp_for;
		public Comp_forContext comp_for() {
			return getRuleContext(Comp_forContext.class,0);
		}
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public List<Star_exprContext> star_expr() {
			return getRuleContexts(Star_exprContext.class);
		}
		public Star_exprContext star_expr(int i) {
			return getRuleContext(Star_exprContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public SetlisttuplemakerContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public SetlisttuplemakerContext(ParserRuleContext parent, int invokingState, PythonBuiltinClassType type, PythonBuiltinClassType compType) {
			super(parent, invokingState);
			this.type = type;
			this.compType = compType;
		}
		@Override public int getRuleIndex() { return RULE_setlisttuplemaker; }
	}

	public final SetlisttuplemakerContext setlisttuplemaker(PythonBuiltinClassType type,PythonBuiltinClassType compType) throws RecognitionException {
		SetlisttuplemakerContext _localctx = new SetlisttuplemakerContext(_ctx, getState(), type, compType);
		enterRule(_localctx, 152, RULE_setlisttuplemaker);
		int _la;
		try {
			int _alt;
			setState(1550);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,183,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				 
				                SSTNode value; 
				                ScopeInfo.ScopeKind scopeKind;
				                switch (compType) {
				                    case PList: scopeKind = ScopeInfo.ScopeKind.ListComp; break;
				                    case PDict: scopeKind = ScopeInfo.ScopeKind.DictComp; break;
				                    case PSet: scopeKind = ScopeInfo.ScopeKind.SetComp; break;
				                    default: scopeKind = ScopeInfo.ScopeKind.GenExp;
				                }
				                ScopeInfo generator = scopeEnvironment.pushScope("generator", scopeKind); 
				                generator.setHasAnnotations(true);
				            
				setState(1514);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STRING:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
					{
					setState(1508);
					_localctx.test = test();
					 value = _localctx.test.result; 
					}
					break;
				case STAR:
					{
					setState(1511);
					_localctx.star_expr = star_expr();
					 value = _localctx.star_expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1516);
				_localctx.comp_for = comp_for(value, null, _localctx.compType, 0);
				 
				                _localctx.result =  _localctx.comp_for.result; 
				                scopeEnvironment.popScope();
				            
				}
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				{
				 SSTNode value; 
				setState(1526);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STRING:
				case LAMBDA:
				case NOT:
				case NONE:
				case TRUE:
				case FALSE:
				case AWAIT:
				case NAME:
				case DECIMAL_INTEGER:
				case OCT_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case FLOAT_NUMBER:
				case IMAG_NUMBER:
				case ELLIPSIS:
				case OPEN_PAREN:
				case OPEN_BRACK:
				case ADD:
				case MINUS:
				case NOT_OP:
				case OPEN_BRACE:
					{
					setState(1520);
					_localctx.test = test();
					 value = _localctx.test.result; 
					}
					break;
				case STAR:
					{
					setState(1523);
					_localctx.star_expr = star_expr();
					 value = _localctx.star_expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 int start = start(); push(value); 
				setState(1540);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,181,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1529);
						match(COMMA);
						setState(1536);
						_errHandler.sync(this);
						switch (_input.LA(1)) {
						case STRING:
						case LAMBDA:
						case NOT:
						case NONE:
						case TRUE:
						case FALSE:
						case AWAIT:
						case NAME:
						case DECIMAL_INTEGER:
						case OCT_INTEGER:
						case HEX_INTEGER:
						case BIN_INTEGER:
						case FLOAT_NUMBER:
						case IMAG_NUMBER:
						case ELLIPSIS:
						case OPEN_PAREN:
						case OPEN_BRACK:
						case ADD:
						case MINUS:
						case NOT_OP:
						case OPEN_BRACE:
							{
							setState(1530);
							_localctx.test = test();
							 push(_localctx.test.result); 
							}
							break;
						case STAR:
							{
							setState(1533);
							_localctx.star_expr = star_expr();
							 push(_localctx.star_expr.result); 
							}
							break;
						default:
							throw new NoViableAltException(this);
						}
						}
						} 
					}
					setState(1542);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,181,_ctx);
				}
				 boolean comma = false; 
				setState(1546);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1544);
					match(COMMA);
					 comma = true; 
					}
				}

				 
				                    SSTNode[] items = getArray(start, SSTNode[].class);
				                    if (_localctx.type == PythonBuiltinClassType.PTuple && items.length == 1 && !comma) {
				                        _localctx.result =  items[0];
				                    } else {
				                        _localctx.result =  new CollectionSSTNode(items, _localctx.type, -1, -1); 
				                    }
				                
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

	public static class ClassdefContext extends ParserRuleContext {
		public com.oracle.graal.python.parser.ScopeInfo scope;
		public Token NAME;
		public ArglistContext arglist;
		public SuiteContext suite;
		public TerminalNode CLASS() { return getToken(Python3Parser.CLASS, 0); }
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public SuiteContext suite() {
			return getRuleContext(SuiteContext.class,0);
		}
		public TerminalNode OPEN_PAREN() { return getToken(Python3Parser.OPEN_PAREN, 0); }
		public ArglistContext arglist() {
			return getRuleContext(ArglistContext.class,0);
		}
		public TerminalNode CLOSE_PAREN() { return getToken(Python3Parser.CLOSE_PAREN, 0); }
		public ClassdefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_classdef; }
	}

	public final ClassdefContext classdef() throws RecognitionException {
		ClassdefContext _localctx = new ClassdefContext(_ctx, getState());
		enterRule(_localctx, 154, RULE_classdef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1552);
			match(CLASS);
			setState(1553);
			_localctx.NAME = match(NAME);
			 ArgListBuilder baseClasses = null; 
			setState(1560);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN_PAREN) {
				{
				setState(1555);
				match(OPEN_PAREN);
				setState(1556);
				_localctx.arglist = arglist();
				setState(1557);
				match(CLOSE_PAREN);
				 baseClasses = _localctx.arglist.result; 
				}
			}


			            // we need to create the scope here to resolve base classes in the outer scope
			            factory.getScopeEnvironment().createLocal((_localctx.NAME!=null?_localctx.NAME.getText():null));
			            ScopeInfo classScope = scopeEnvironment.pushScope((_localctx.NAME!=null?_localctx.NAME.getText():null), ScopeInfo.ScopeKind.Class); 
			        
			 LoopState savedLoopState = saveLoopState(); 
			setState(1564);
			match(COLON);
			setState(1565);
			_localctx.suite = suite();
			 push(factory.createClassDefinition((_localctx.NAME!=null?_localctx.NAME.getText():null), baseClasses, _localctx.suite.result, getStartIndex(_localctx), getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)))); 
			 scopeEnvironment.popScope(); 
			 loopState = savedLoopState; 
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

	public static class ArglistContext extends ParserRuleContext {
		public ArgListBuilder result;
		public List<ArgumentContext> argument() {
			return getRuleContexts(ArgumentContext.class);
		}
		public ArgumentContext argument(int i) {
			return getRuleContext(ArgumentContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(Python3Parser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(Python3Parser.COMMA, i);
		}
		public ArglistContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arglist; }
	}

	public final ArglistContext arglist() throws RecognitionException {
		ArglistContext _localctx = new ArglistContext(_ctx, getState());
		enterRule(_localctx, 156, RULE_arglist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 ArgListBuilder args = new ArgListBuilder(); 
			setState(1582);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << POWER) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(1571);
				argument(args);
				setState(1576);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,185,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1572);
						match(COMMA);
						setState(1573);
						argument(args);
						}
						} 
					}
					setState(1578);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,185,_ctx);
				}
				setState(1580);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1579);
					match(COMMA);
					}
				}

				}
			}

			 _localctx.result =  args; 
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

	public static class ArgumentContext extends ParserRuleContext {
		public ArgListBuilder args;
		public SSTNode result;
		public TestContext test;
		public Comp_forContext comp_for;
		public TestContext n;
		public List<TestContext> test() {
			return getRuleContexts(TestContext.class);
		}
		public TestContext test(int i) {
			return getRuleContext(TestContext.class,i);
		}
		public Comp_forContext comp_for() {
			return getRuleContext(Comp_forContext.class,0);
		}
		public TerminalNode ASSIGN() { return getToken(Python3Parser.ASSIGN, 0); }
		public TerminalNode POWER() { return getToken(Python3Parser.POWER, 0); }
		public TerminalNode STAR() { return getToken(Python3Parser.STAR, 0); }
		public ArgumentContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public ArgumentContext(ParserRuleContext parent, int invokingState, ArgListBuilder args) {
			super(parent, invokingState);
			this.args = args;
		}
		@Override public int getRuleIndex() { return RULE_argument; }
	}

	public final ArgumentContext argument(ArgListBuilder args) throws RecognitionException {
		ArgumentContext _localctx = new ArgumentContext(_ctx, getState(), args);
		enterRule(_localctx, 158, RULE_argument);
		try {
			setState(1609);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,188,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{

				                    ScopeInfo generator = scopeEnvironment.pushScope("generator", ScopeInfo.ScopeKind.GenExp); 
				                    generator.setHasAnnotations(true);
				                
				setState(1587);
				_localctx.test = test();
				setState(1588);
				_localctx.comp_for = comp_for(_localctx.test.result, null, PythonBuiltinClassType.PGenerator, 0);

				                    args.addNakedForComp(_localctx.comp_for.result);
				                   scopeEnvironment.popScope();
				                
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				 String name = getCurrentToken().getText();
				                  if (getCurrentToken().getType() != NAME) {
				                    throw new PythonRecognitionException("keyword can't be an expression", this, _input, _localctx, getCurrentToken());
				                  }
				                  // TODO this is not nice. There is done two times lookup in collection to remove name from seen variables. !!!
				                  boolean isNameAsVariableInScope = scopeEnvironment.getCurrentScope().getSeenVars() == null ? false : scopeEnvironment.getCurrentScope().getSeenVars().contains(name);
				                
				setState(1592);
				_localctx.n = _localctx.test = test();

				                    if (!((_localctx.n).result instanceof VarLookupSSTNode)) {
				                        throw new PythonRecognitionException("keyword can't be an expression", this, _input, _localctx, getCurrentToken());
				                    }
				                    if (!isNameAsVariableInScope && scopeEnvironment.getCurrentScope().getSeenVars().contains(name)) {
				                        scopeEnvironment.getCurrentScope().getSeenVars().remove(name);
				                    }
				                
				setState(1594);
				match(ASSIGN);
				setState(1595);
				_localctx.test = test();
				 
				                        args.addNamedArg(name, _localctx.test.result); 
				                    
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1598);
				_localctx.test = test();
				  
				                        if (args.hasNameArg()) {
				                            throw new PythonRecognitionException("positional argument follows keyword argument", this, _input, _localctx, getCurrentToken());
				                        }
				                        if (args.hasKwArg()) {
				                            throw new PythonRecognitionException("positional argument follows keyword argument unpacking", this, _input, _localctx, getCurrentToken());
				                        }
				                        args.addArg(_localctx.test.result); 
				                    
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1601);
				match(POWER);
				setState(1602);
				_localctx.test = test();
				 args.addKwArg(_localctx.test.result); 
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1605);
				match(STAR);
				setState(1606);
				_localctx.test = test();
				 
				                        if (args.hasKwArg()) {
				                            throw new PythonRecognitionException("iterable argument unpacking follows keyword argument unpacking", this, _input, _localctx, getCurrentToken());
				                        }
				                        args.addStarArg(_localctx.test.result); 
				                    
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

	public static class Comp_forContext extends ParserRuleContext {
		public SSTNode target;
		public SSTNode name;
		public PythonBuiltinClassType resultType;
		public int level;
		public SSTNode result;
		public Token f;
		public ExprlistContext exprlist;
		public Or_testContext or_test;
		public Test_nocondContext test_nocond;
		public Comp_forContext comp_for;
		public ExprlistContext exprlist() {
			return getRuleContext(ExprlistContext.class,0);
		}
		public TerminalNode IN() { return getToken(Python3Parser.IN, 0); }
		public Or_testContext or_test() {
			return getRuleContext(Or_testContext.class,0);
		}
		public TerminalNode FOR() { return getToken(Python3Parser.FOR, 0); }
		public TerminalNode ASYNC() { return getToken(Python3Parser.ASYNC, 0); }
		public List<TerminalNode> IF() { return getTokens(Python3Parser.IF); }
		public TerminalNode IF(int i) {
			return getToken(Python3Parser.IF, i);
		}
		public List<Test_nocondContext> test_nocond() {
			return getRuleContexts(Test_nocondContext.class);
		}
		public Test_nocondContext test_nocond(int i) {
			return getRuleContext(Test_nocondContext.class,i);
		}
		public Comp_forContext comp_for() {
			return getRuleContext(Comp_forContext.class,0);
		}
		public Comp_forContext(ParserRuleContext parent, int invokingState) { super(parent, invokingState); }
		public Comp_forContext(ParserRuleContext parent, int invokingState, SSTNode target, SSTNode name, PythonBuiltinClassType resultType, int level) {
			super(parent, invokingState);
			this.target = target;
			this.name = name;
			this.resultType = resultType;
			this.level = level;
		}
		@Override public int getRuleIndex() { return RULE_comp_for; }
	}

	public final Comp_forContext comp_for(SSTNode target,SSTNode name,PythonBuiltinClassType resultType,int level) throws RecognitionException {
		Comp_forContext _localctx = new Comp_forContext(_ctx, getState(), target, name, resultType, level);
		enterRule(_localctx, 160, RULE_comp_for);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 
			            if (target instanceof StarSSTNode) {
			                throw new PythonRecognitionException("iterable unpacking cannot be used in comprehension", this, _input, _localctx, _localctx.start);
			            }
			            boolean scopeCreated = true; 
			            boolean async = false; 
			        
			setState(1614);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASYNC) {
				{
				setState(1612);
				match(ASYNC);
				 async = true; 
				}
			}

			 
			            SSTNode iterator; 
			            SSTNode[] variables;
			            int lineNumber;
			        
			setState(1617);
			_localctx.f = match(FOR);
			setState(1618);
			_localctx.exprlist = exprlist();
			setState(1619);
			match(IN);

			                ScopeInfo currentScope = null;
			                if (level == 0) {
			                    currentScope = scopeEnvironment.getCurrentScope();
			                    factory.getScopeEnvironment().setCurrentScope(currentScope.getParent());
			                }
			            
			setState(1621);
			_localctx.or_test = or_test();
			   
			            if (level == 0) {
			                factory.getScopeEnvironment().setCurrentScope(currentScope);
			            }
			            lineNumber = _localctx.f.getLine();
			            iterator = _localctx.or_test.result; 
			            variables = _localctx.exprlist.result;
			        
			 int start = start(); 
			setState(1630);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==IF) {
				{
				{
				setState(1624);
				match(IF);
				setState(1625);
				_localctx.test_nocond = test_nocond();
				 push(_localctx.test_nocond.result); 
				}
				}
				setState(1632);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			 SSTNode[] conditions = getArray(start, SSTNode[].class); 
			setState(1637);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==FOR || _la==ASYNC) {
				{
				setState(1634);
				_localctx.comp_for = comp_for(iterator, null, PythonBuiltinClassType.PGenerator, level + 1);
				 
				                iterator = _localctx.comp_for.result; 
				            
				}
			}

			 _localctx.result =  factory.createForComprehension(async, _localctx.target, _localctx.name, variables, iterator, conditions, _localctx.resultType, lineNumber, level, getStartIndex(_localctx.f), getLastIndex(_localctx)); 
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

	public static class Encoding_declContext extends ParserRuleContext {
		public TerminalNode NAME() { return getToken(Python3Parser.NAME, 0); }
		public Encoding_declContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_encoding_decl; }
	}

	public final Encoding_declContext encoding_decl() throws RecognitionException {
		Encoding_declContext _localctx = new Encoding_declContext(_ctx, getState());
		enterRule(_localctx, 162, RULE_encoding_decl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1641);
			match(NAME);
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

	public static class Yield_exprContext extends ParserRuleContext {
		public SSTNode result;
		public TestContext test;
		public Testlist_star_exprContext testlist_star_expr;
		public TerminalNode YIELD() { return getToken(Python3Parser.YIELD, 0); }
		public TerminalNode FROM() { return getToken(Python3Parser.FROM, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public Testlist_star_exprContext testlist_star_expr() {
			return getRuleContext(Testlist_star_exprContext.class,0);
		}
		public Yield_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_yield_expr; }
	}

	public final Yield_exprContext yield_expr() throws RecognitionException {
		Yield_exprContext _localctx = new Yield_exprContext(_ctx, getState());
		enterRule(_localctx, 164, RULE_yield_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			 
			        SSTNode value = null;
			        boolean isFrom = false; 
			    
			setState(1644);
			match(YIELD);
			setState(1652);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case FROM:
				{
				setState(1645);
				match(FROM);
				setState(1646);
				_localctx.test = test();
				value = _localctx.test.result; isFrom = true;
				}
				break;
			case STRING:
			case LAMBDA:
			case NOT:
			case NONE:
			case TRUE:
			case FALSE:
			case AWAIT:
			case NAME:
			case DECIMAL_INTEGER:
			case OCT_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case FLOAT_NUMBER:
			case IMAG_NUMBER:
			case ELLIPSIS:
			case STAR:
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				{
				setState(1649);
				_localctx.testlist_star_expr = testlist_star_expr();
				 value = _localctx.testlist_star_expr.result; 
				}
				break;
			case NEWLINE:
			case CLOSE_PAREN:
			case SEMI_COLON:
			case ASSIGN:
				break;
			default:
				break;
			}
			 _localctx.result =  factory.createYieldExpressionSSTNode(value, isFrom, getStartIndex(_localctx), getLastIndex(_localctx)); 
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

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3h\u067b\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4I"+
		"\tI\4J\tJ\4K\tK\4L\tL\4M\tM\4N\tN\4O\tO\4P\tP\4Q\tQ\4R\tR\4S\tS\4T\tT"+
		"\3\2\3\2\3\2\3\2\5\2\u00ad\n\2\3\2\3\2\3\2\5\2\u00b2\n\2\3\2\7\2\u00b5"+
		"\n\2\f\2\16\2\u00b8\13\2\3\2\3\2\3\2\3\2\3\3\3\3\3\3\3\3\5\3\u00c2\n\3"+
		"\3\3\3\3\7\3\u00c6\n\3\f\3\16\3\u00c9\13\3\3\3\3\3\3\3\3\3\3\4\3\4\3\4"+
		"\3\4\5\4\u00d3\n\4\3\4\3\4\7\4\u00d7\n\4\f\4\16\4\u00da\13\4\3\4\3\4\3"+
		"\4\3\4\3\5\3\5\5\5\u00e2\n\5\3\5\3\5\7\5\u00e6\n\5\f\5\16\5\u00e9\13\5"+
		"\3\5\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\6\3\6\3\6\5\6\u00f7\n\6\3\6\3\6"+
		"\3\6\3\7\3\7\6\7\u00fe\n\7\r\7\16\7\u00ff\3\7\3\7\3\b\3\b\3\b\3\b\3\b"+
		"\5\b\u0109\n\b\3\b\3\b\3\t\3\t\3\t\3\n\3\n\3\n\3\n\3\n\5\n\u0115\n\n\3"+
		"\n\3\n\3\n\3\n\3\n\3\13\3\13\3\13\5\13\u011f\n\13\3\13\3\13\3\13\3\f\3"+
		"\f\3\f\7\f\u0127\n\f\f\f\16\f\u012a\13\f\3\f\3\f\3\f\3\f\3\f\3\f\3\f\7"+
		"\f\u0133\n\f\f\f\16\f\u0136\13\f\5\f\u0138\n\f\3\f\3\f\3\f\3\f\7\f\u013e"+
		"\n\f\f\f\16\f\u0141\13\f\3\f\3\f\3\f\5\f\u0146\n\f\5\f\u0148\n\f\5\f\u014a"+
		"\n\f\3\f\3\f\5\f\u014e\n\f\5\f\u0150\n\f\5\f\u0152\n\f\3\f\3\f\3\f\7\f"+
		"\u0157\n\f\f\f\16\f\u015a\13\f\3\f\3\f\3\f\3\f\7\f\u0160\n\f\f\f\16\f"+
		"\u0163\13\f\3\f\3\f\3\f\5\f\u0168\n\f\5\f\u016a\n\f\5\f\u016c\n\f\3\f"+
		"\3\f\5\f\u0170\n\f\5\f\u0172\n\f\5\f\u0174\n\f\3\f\3\f\3\f\7\f\u0179\n"+
		"\f\f\f\16\f\u017c\13\f\3\f\3\f\3\f\5\f\u0181\n\f\5\f\u0183\n\f\5\f\u0185"+
		"\n\f\3\f\3\f\5\f\u0189\n\f\5\f\u018b\n\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r\3"+
		"\r\5\r\u0195\n\r\3\r\3\r\3\r\3\r\5\r\u019b\n\r\3\r\3\r\3\16\3\16\3\16"+
		"\3\16\3\16\3\16\3\16\3\16\5\16\u01a7\n\16\5\16\u01a9\n\16\3\16\3\16\3"+
		"\17\3\17\3\17\3\17\3\17\3\17\3\17\5\17\u01b4\n\17\3\17\3\17\3\20\3\20"+
		"\3\20\3\20\7\20\u01bc\n\20\f\20\16\20\u01bf\13\20\3\20\3\20\3\20\3\20"+
		"\3\20\3\20\3\20\7\20\u01c8\n\20\f\20\16\20\u01cb\13\20\5\20\u01cd\n\20"+
		"\3\20\3\20\3\20\3\20\7\20\u01d3\n\20\f\20\16\20\u01d6\13\20\3\20\3\20"+
		"\3\20\5\20\u01db\n\20\5\20\u01dd\n\20\5\20\u01df\n\20\3\20\3\20\5\20\u01e3"+
		"\n\20\5\20\u01e5\n\20\5\20\u01e7\n\20\3\20\3\20\3\20\7\20\u01ec\n\20\f"+
		"\20\16\20\u01ef\13\20\3\20\3\20\3\20\3\20\7\20\u01f5\n\20\f\20\16\20\u01f8"+
		"\13\20\3\20\3\20\3\20\5\20\u01fd\n\20\5\20\u01ff\n\20\5\20\u0201\n\20"+
		"\3\20\3\20\5\20\u0205\n\20\5\20\u0207\n\20\5\20\u0209\n\20\3\20\3\20\3"+
		"\20\7\20\u020e\n\20\f\20\16\20\u0211\13\20\3\20\3\20\3\20\5\20\u0216\n"+
		"\20\5\20\u0218\n\20\5\20\u021a\n\20\3\20\3\20\5\20\u021e\n\20\5\20\u0220"+
		"\n\20\3\20\3\20\3\20\3\21\3\21\3\21\3\21\3\21\3\21\5\21\u022b\n\21\3\21"+
		"\3\21\3\22\3\22\3\22\3\22\5\22\u0233\n\22\3\22\3\22\3\23\3\23\3\23\3\23"+
		"\3\24\3\24\5\24\u023d\n\24\3\25\3\25\3\25\7\25\u0242\n\25\f\25\16\25\u0245"+
		"\13\25\3\25\5\25\u0248\n\25\3\25\3\25\3\26\3\26\3\26\3\26\3\26\3\26\3"+
		"\26\3\26\3\26\5\26\u0255\n\26\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\5\27\u025f\n\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\5\27\u026a"+
		"\n\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\5\27"+
		"\u0278\n\27\7\27\u027a\n\27\f\27\16\27\u027d\13\27\3\27\5\27\u0280\n\27"+
		"\3\30\3\30\3\30\3\30\3\30\3\30\5\30\u0288\n\30\3\30\3\30\3\30\3\30\3\30"+
		"\3\30\3\30\3\30\5\30\u0292\n\30\3\30\3\30\3\30\3\30\3\30\3\30\3\30\5\30"+
		"\u029b\n\30\7\30\u029d\n\30\f\30\16\30\u02a0\13\30\3\30\5\30\u02a3\n\30"+
		"\5\30\u02a5\n\30\3\30\5\30\u02a8\n\30\3\31\3\31\3\32\3\32\3\32\3\32\3"+
		"\33\3\33\3\33\3\33\3\33\3\33\3\33\5\33\u02b7\n\33\3\34\3\34\3\34\3\34"+
		"\3\34\5\34\u02be\n\34\3\34\3\34\3\35\3\35\3\35\3\36\3\36\3\36\3\36\3\36"+
		"\3\36\3\36\3\36\5\36\u02cd\n\36\5\36\u02cf\n\36\3\36\3\36\3\37\3\37\5"+
		"\37\u02d5\n\37\3 \3 \3 \3!\3!\3!\3!\3!\3!\7!\u02e0\n!\f!\16!\u02e3\13"+
		"!\3!\3!\3!\3!\3!\3!\3!\6!\u02ec\n!\r!\16!\u02ed\5!\u02f0\n!\3!\3!\3!\3"+
		"!\3!\3!\3!\3!\3!\3!\3!\5!\u02fd\n!\3!\3!\3\"\3\"\3\"\3\"\3\"\5\"\u0306"+
		"\n\"\3\"\3\"\3#\3#\3#\3#\3#\3#\3#\7#\u0311\n#\f#\16#\u0314\13#\3#\5#\u0317"+
		"\n#\3#\3#\3$\3$\3$\3$\3$\7$\u0320\n$\f$\16$\u0323\13$\3%\3%\3%\3%\3%\5"+
		"%\u032a\n%\3&\3&\3&\7&\u032f\n&\f&\16&\u0332\13&\3\'\3\'\3\'\3\'\3\'\3"+
		"\'\3\'\7\'\u033b\n\'\f\'\16\'\u033e\13\'\3\'\3\'\3(\3(\3(\3(\3(\3(\3("+
		"\7(\u0349\n(\f(\16(\u034c\13(\3(\3(\3)\3)\3)\3)\3)\3)\3)\5)\u0357\n)\3"+
		")\3)\3*\3*\3*\3*\3*\3*\3*\3*\3*\5*\u0364\n*\3+\3+\3+\3+\5+\u036a\n+\3"+
		",\3,\3,\3,\3,\3,\3,\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\5-\u0380\n"+
		"-\3.\3.\3.\3.\3.\3.\3.\3.\3.\3.\3.\5.\u038d\n.\3.\3.\3/\3/\3/\3/\3/\3"+
		"/\3/\3/\3/\3/\3/\3/\3/\5/\u039e\n/\3/\3/\3\60\3\60\3\60\3\60\3\60\3\60"+
		"\3\60\3\60\6\60\u03aa\n\60\r\60\16\60\u03ab\3\60\3\60\3\60\3\60\3\60\5"+
		"\60\u03b3\n\60\3\60\3\60\3\60\3\60\3\60\5\60\u03ba\n\60\3\60\3\60\3\60"+
		"\3\60\3\60\5\60\u03c1\n\60\3\60\3\60\3\61\3\61\3\61\3\61\3\61\3\61\3\61"+
		"\5\61\u03cc\n\61\5\61\u03ce\n\61\3\61\3\61\3\61\3\61\3\62\3\62\3\62\3"+
		"\62\3\63\3\63\3\63\3\63\3\63\3\63\5\63\u03de\n\63\3\63\3\63\3\63\3\63"+
		"\3\63\3\63\3\63\3\63\3\63\5\63\u03e9\n\63\3\63\3\63\3\64\3\64\3\64\3\64"+
		"\3\64\6\64\u03f2\n\64\r\64\16\64\u03f3\3\64\3\64\5\64\u03f8\n\64\3\64"+
		"\3\64\3\65\3\65\3\65\3\65\3\65\3\65\3\65\3\65\5\65\u0404\n\65\3\65\3\65"+
		"\3\65\5\65\u0409\n\65\3\66\3\66\3\66\3\66\3\66\3\66\5\66\u0411\n\66\3"+
		"\67\3\67\3\67\3\67\3\67\5\67\u0418\n\67\3\67\3\67\3\67\3\67\3\67\3\67"+
		"\38\38\38\38\38\58\u0425\n8\38\38\38\38\38\38\39\39\39\39\39\39\39\69"+
		"\u0434\n9\r9\169\u0435\39\39\39\59\u043b\n9\3:\3:\3:\3:\3:\3:\3:\6:\u0444"+
		"\n:\r:\16:\u0445\3:\3:\3:\5:\u044b\n:\3;\3;\3;\3;\3;\3;\3;\5;\u0454\n"+
		";\3<\3<\3<\3<\3<\3<\6<\u045c\n<\r<\16<\u045d\3<\3<\3<\5<\u0463\n<\3=\3"+
		"=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\3=\5"+
		"=\u047d\n=\3>\3>\3>\3>\3?\3?\3?\3?\3?\3?\7?\u0489\n?\f?\16?\u048c\13?"+
		"\3@\3@\3@\3@\3@\3@\7@\u0494\n@\f@\16@\u0497\13@\3A\3A\3A\3A\3A\3A\7A\u049f"+
		"\nA\fA\16A\u04a2\13A\3B\3B\3B\3B\3B\3B\3B\5B\u04ab\nB\3B\3B\3B\7B\u04b0"+
		"\nB\fB\16B\u04b3\13B\3C\3C\3C\3C\3C\3C\3C\5C\u04bc\nC\3C\3C\3C\7C\u04c1"+
		"\nC\fC\16C\u04c4\13C\3D\3D\3D\3D\3D\3D\3D\3D\3D\3D\3D\3D\3D\5D\u04d3\n"+
		"D\3D\3D\3D\7D\u04d8\nD\fD\16D\u04db\13D\3E\3E\3E\3E\3E\3E\3E\5E\u04e4"+
		"\nE\3E\3E\3E\3E\3E\3E\5E\u04ec\nE\3F\3F\3F\3F\3F\3F\5F\u04f4\nF\3G\5G"+
		"\u04f7\nG\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\7G\u0508\nG\fG"+
		"\16G\u050b\13G\3H\3H\3H\3H\3H\3H\3H\3H\5H\u0515\nH\3H\3H\3H\3H\3H\3H\3"+
		"H\5H\u051e\nH\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H\5H\u052a\nH\3H\3H\3H\3H\3"+
		"H\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H\6H\u053f\nH\rH\16H\u0540\3"+
		"H\3H\3H\3H\3H\3H\3H\3H\3H\5H\u054c\nH\3I\3I\3I\3I\3I\3I\3I\3I\3I\3I\7"+
		"I\u0558\nI\fI\16I\u055b\13I\3I\5I\u055e\nI\5I\u0560\nI\3I\5I\u0563\nI"+
		"\3J\3J\3J\3J\3J\3J\3J\5J\u056c\nJ\3J\3J\3J\3J\5J\u0572\nJ\3J\3J\3J\3J"+
		"\5J\u0578\nJ\5J\u057a\nJ\3J\5J\u057d\nJ\3K\3K\3K\3K\3K\3K\3K\5K\u0586"+
		"\nK\3K\3K\3K\3K\3K\3K\3K\5K\u058f\nK\7K\u0591\nK\fK\16K\u0594\13K\3K\5"+
		"K\u0597\nK\3K\3K\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\7L\u05a5\nL\fL\16L\u05a8"+
		"\13L\3L\5L\u05ab\nL\5L\u05ad\nL\3L\5L\u05b0\nL\3M\3M\3M\3M\3M\3M\3M\3"+
		"M\3M\3M\5M\u05bc\nM\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\5M\u05cb\n"+
		"M\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\5M\u05d8\nM\7M\u05da\nM\fM\16M\u05dd"+
		"\13M\3M\5M\u05e0\nM\3M\3M\5M\u05e4\nM\3N\3N\3N\3N\3N\3N\3N\5N\u05ed\n"+
		"N\3N\3N\3N\3N\3N\3N\3N\3N\3N\3N\5N\u05f9\nN\3N\3N\3N\3N\3N\3N\3N\3N\5"+
		"N\u0603\nN\7N\u0605\nN\fN\16N\u0608\13N\3N\3N\3N\5N\u060d\nN\3N\3N\5N"+
		"\u0611\nN\3O\3O\3O\3O\3O\3O\3O\3O\5O\u061b\nO\3O\3O\3O\3O\3O\3O\3O\3O"+
		"\3P\3P\3P\3P\7P\u0629\nP\fP\16P\u062c\13P\3P\5P\u062f\nP\5P\u0631\nP\3"+
		"P\3P\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3"+
		"Q\3Q\5Q\u064c\nQ\3R\3R\3R\5R\u0651\nR\3R\3R\3R\3R\3R\3R\3R\3R\3R\3R\3"+
		"R\3R\7R\u065f\nR\fR\16R\u0662\13R\3R\3R\3R\3R\5R\u0668\nR\3R\3R\3S\3S"+
		"\3T\3T\3T\3T\3T\3T\3T\3T\3T\5T\u0677\nT\3T\3T\3T\2\2U\2\4\6\b\n\f\16\20"+
		"\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJLNPRTVXZ\\^`bdfhj"+
		"lnprtvxz|~\u0080\u0082\u0084\u0086\u0088\u008a\u008c\u008e\u0090\u0092"+
		"\u0094\u0096\u0098\u009a\u009c\u009e\u00a0\u00a2\u00a4\u00a6\2\3\3\2S"+
		"_\2\u0727\2\u00a8\3\2\2\2\4\u00bd\3\2\2\2\6\u00ce\3\2\2\2\b\u00df\3\2"+
		"\2\2\n\u00ee\3\2\2\2\f\u00fb\3\2\2\2\16\u0103\3\2\2\2\20\u010c\3\2\2\2"+
		"\22\u010f\3\2\2\2\24\u011b\3\2\2\2\26\u018a\3\2\2\2\30\u018e\3\2\2\2\32"+
		"\u019e\3\2\2\2\34\u01ac\3\2\2\2\36\u01b7\3\2\2\2 \u0224\3\2\2\2\"\u022e"+
		"\3\2\2\2$\u0236\3\2\2\2&\u023c\3\2\2\2(\u023e\3\2\2\2*\u0254\3\2\2\2,"+
		"\u0256\3\2\2\2.\u0287\3\2\2\2\60\u02a9\3\2\2\2\62\u02ab\3\2\2\2\64\u02b6"+
		"\3\2\2\2\66\u02b8\3\2\2\28\u02c1\3\2\2\2:\u02c4\3\2\2\2<\u02d4\3\2\2\2"+
		">\u02d6\3\2\2\2@\u02d9\3\2\2\2B\u0300\3\2\2\2D\u0309\3\2\2\2F\u031a\3"+
		"\2\2\2H\u0324\3\2\2\2J\u032b\3\2\2\2L\u0333\3\2\2\2N\u0341\3\2\2\2P\u034f"+
		"\3\2\2\2R\u0363\3\2\2\2T\u0365\3\2\2\2V\u036b\3\2\2\2X\u037f\3\2\2\2Z"+
		"\u0381\3\2\2\2\\\u0390\3\2\2\2^\u03a1\3\2\2\2`\u03c4\3\2\2\2b\u03d3\3"+
		"\2\2\2d\u03d7\3\2\2\2f\u03ec\3\2\2\2h\u0408\3\2\2\2j\u0410\3\2\2\2l\u0412"+
		"\3\2\2\2n\u041f\3\2\2\2p\u042c\3\2\2\2r\u043c\3\2\2\2t\u0453\3\2\2\2v"+
		"\u0455\3\2\2\2x\u047c\3\2\2\2z\u047e\3\2\2\2|\u0482\3\2\2\2~\u048d\3\2"+
		"\2\2\u0080\u0498\3\2\2\2\u0082\u04a3\3\2\2\2\u0084\u04b4\3\2\2\2\u0086"+
		"\u04c5\3\2\2\2\u0088\u04eb\3\2\2\2\u008a\u04ed\3\2\2\2\u008c\u04f6\3\2"+
		"\2\2\u008e\u054b\3\2\2\2\u0090\u054d\3\2\2\2\u0092\u057c\3\2\2\2\u0094"+
		"\u057e\3\2\2\2\u0096\u059a\3\2\2\2\u0098\u05e3\3\2\2\2\u009a\u0610\3\2"+
		"\2\2\u009c\u0612\3\2\2\2\u009e\u0624\3\2\2\2\u00a0\u064b\3\2\2\2\u00a2"+
		"\u064d\3\2\2\2\u00a4\u066b\3\2\2\2\u00a6\u066d\3\2\2\2\u00a8\u00a9\b\2"+
		"\1\2\u00a9\u00aa\b\2\1\2\u00aa\u00ac\b\2\1\2\u00ab\u00ad\7c\2\2\u00ac"+
		"\u00ab\3\2\2\2\u00ac\u00ad\3\2\2\2\u00ad\u00b1\3\2\2\2\u00ae\u00b2\7\'"+
		"\2\2\u00af\u00b2\5(\25\2\u00b0\u00b2\5R*\2\u00b1\u00ae\3\2\2\2\u00b1\u00af"+
		"\3\2\2\2\u00b1\u00b0\3\2\2\2\u00b2\u00b6\3\2\2\2\u00b3\u00b5\7\'\2\2\u00b4"+
		"\u00b3\3\2\2\2\u00b5\u00b8\3\2\2\2\u00b6\u00b4\3\2\2\2\u00b6\u00b7\3\2"+
		"\2\2\u00b7\u00b9\3\2\2\2\u00b8\u00b6\3\2\2\2\u00b9\u00ba\7\2\2\3\u00ba"+
		"\u00bb\b\2\1\2\u00bb\u00bc\b\2\1\2\u00bc\3\3\2\2\2\u00bd\u00be\b\3\1\2"+
		"\u00be\u00bf\b\3\1\2\u00bf\u00c1\b\3\1\2\u00c0\u00c2\7c\2\2\u00c1\u00c0"+
		"\3\2\2\2\u00c1\u00c2\3\2\2\2\u00c2\u00c7\3\2\2\2\u00c3\u00c6\7\'\2\2\u00c4"+
		"\u00c6\5&\24\2\u00c5\u00c3\3\2\2\2\u00c5\u00c4\3\2\2\2\u00c6\u00c9\3\2"+
		"\2\2\u00c7\u00c5\3\2\2\2\u00c7\u00c8\3\2\2\2\u00c8\u00ca\3\2\2\2\u00c9"+
		"\u00c7\3\2\2\2\u00ca\u00cb\7\2\2\3\u00cb\u00cc\b\3\1\2\u00cc\u00cd\b\3"+
		"\1\2\u00cd\5\3\2\2\2\u00ce\u00cf\b\4\1\2\u00cf\u00d0\b\4\1\2\u00d0\u00d2"+
		"\b\4\1\2\u00d1\u00d3\7c\2\2\u00d2\u00d1\3\2\2\2\u00d2\u00d3\3\2\2\2\u00d3"+
		"\u00d8\3\2\2\2\u00d4\u00d7\7\'\2\2\u00d5\u00d7\5&\24\2\u00d6\u00d4\3\2"+
		"\2\2\u00d6\u00d5\3\2\2\2\u00d7\u00da\3\2\2\2\u00d8\u00d6\3\2\2\2\u00d8"+
		"\u00d9\3\2\2\2\u00d9\u00db\3\2\2\2\u00da\u00d8\3\2\2\2\u00db\u00dc\7\2"+
		"\2\3\u00dc\u00dd\b\4\1\2\u00dd\u00de\b\4\1\2\u00de\7\3\2\2\2\u00df\u00e1"+
		"\b\5\1\2\u00e0\u00e2\7c\2\2\u00e1\u00e0\3\2\2\2\u00e1\u00e2\3\2\2\2\u00e2"+
		"\u00e3\3\2\2\2\u00e3\u00e7\5\u0096L\2\u00e4\u00e6\7\'\2\2\u00e5\u00e4"+
		"\3\2\2\2\u00e6\u00e9\3\2\2\2\u00e7\u00e5\3\2\2\2\u00e7\u00e8\3\2\2\2\u00e8"+
		"\u00ea\3\2\2\2\u00e9\u00e7\3\2\2\2\u00ea\u00eb\7\2\2\3\u00eb\u00ec\b\5"+
		"\1\2\u00ec\u00ed\b\5\1\2\u00ed\t\3\2\2\2\u00ee\u00ef\b\6\1\2\u00ef\u00f0"+
		"\7Q\2\2\u00f0\u00f6\5F$\2\u00f1\u00f2\7\64\2\2\u00f2\u00f3\5\u009eP\2"+
		"\u00f3\u00f4\7\65\2\2\u00f4\u00f5\b\6\1\2\u00f5\u00f7\3\2\2\2\u00f6\u00f1"+
		"\3\2\2\2\u00f6\u00f7\3\2\2\2\u00f7\u00f8\3\2\2\2\u00f8\u00f9\7\'\2\2\u00f9"+
		"\u00fa\b\6\1\2\u00fa\13\3\2\2\2\u00fb\u00fd\b\7\1\2\u00fc\u00fe\5\n\6"+
		"\2\u00fd\u00fc\3\2\2\2\u00fe\u00ff\3\2\2\2\u00ff\u00fd\3\2\2\2\u00ff\u0100"+
		"\3\2\2\2\u0100\u0101\3\2\2\2\u0101\u0102\b\7\1\2\u0102\r\3\2\2\2\u0103"+
		"\u0104\b\b\1\2\u0104\u0108\5\f\7\2\u0105\u0109\5\u009cO\2\u0106\u0109"+
		"\5\22\n\2\u0107\u0109\5\20\t\2\u0108\u0105\3\2\2\2\u0108\u0106\3\2\2\2"+
		"\u0108\u0107\3\2\2\2\u0109\u010a\3\2\2\2\u010a\u010b\b\b\1\2\u010b\17"+
		"\3\2\2\2\u010c\u010d\7%\2\2\u010d\u010e\5\22\n\2\u010e\21\3\2\2\2\u010f"+
		"\u0110\7\4\2\2\u0110\u0111\7(\2\2\u0111\u0114\5\24\13\2\u0112\u0113\7"+
		"R\2\2\u0113\u0115\5h\65\2\u0114\u0112\3\2\2\2\u0114\u0115\3\2\2\2\u0115"+
		"\u0116\3\2\2\2\u0116\u0117\7\67\2\2\u0117\u0118\b\n\1\2\u0118\u0119\5"+
		"f\64\2\u0119\u011a\b\n\1\2\u011a\23\3\2\2\2\u011b\u011c\b\13\1\2\u011c"+
		"\u011e\7\64\2\2\u011d\u011f\5\26\f\2\u011e\u011d\3\2\2\2\u011e\u011f\3"+
		"\2\2\2\u011f\u0120\3\2\2\2\u0120\u0121\7\65\2\2\u0121\u0122\b\13\1\2\u0122"+
		"\25\3\2\2\2\u0123\u0128\5\30\r\2\u0124\u0125\7\66\2\2\u0125\u0127\5\30"+
		"\r\2\u0126\u0124\3\2\2\2\u0127\u012a\3\2\2\2\u0128\u0126\3\2\2\2\u0128"+
		"\u0129\3\2\2\2\u0129\u012b\3\2\2\2\u012a\u0128\3\2\2\2\u012b\u012c\7\66"+
		"\2\2\u012c\u012d\7D\2\2\u012d\u0137\b\f\1\2\u012e\u012f\7\66\2\2\u012f"+
		"\u0134\5\30\r\2\u0130\u0131\7\66\2\2\u0131\u0133\5\30\r\2\u0132\u0130"+
		"\3\2\2\2\u0133\u0136\3\2\2\2\u0134\u0132\3\2\2\2\u0134\u0135\3\2\2\2\u0135"+
		"\u0138\3\2\2\2\u0136\u0134\3\2\2\2\u0137\u012e\3\2\2\2\u0137\u0138\3\2"+
		"\2\2\u0138\u0151\3\2\2\2\u0139\u014f\7\66\2\2\u013a\u013f\5\32\16\2\u013b"+
		"\u013c\7\66\2\2\u013c\u013e\5\30\r\2\u013d\u013b\3\2\2\2\u013e\u0141\3"+
		"\2\2\2\u013f\u013d\3\2\2\2\u013f\u0140\3\2\2\2\u0140\u0149\3\2\2\2\u0141"+
		"\u013f\3\2\2\2\u0142\u0147\7\66\2\2\u0143\u0145\5\34\17\2\u0144\u0146"+
		"\7\66\2\2\u0145\u0144\3\2\2\2\u0145\u0146\3\2\2\2\u0146\u0148\3\2\2\2"+
		"\u0147\u0143\3\2\2\2\u0147\u0148\3\2\2\2\u0148\u014a\3\2\2\2\u0149\u0142"+
		"\3\2\2\2\u0149\u014a\3\2\2\2\u014a\u0150\3\2\2\2\u014b\u014d\5\34\17\2"+
		"\u014c\u014e\7\66\2\2\u014d\u014c\3\2\2\2\u014d\u014e\3\2\2\2\u014e\u0150"+
		"\3\2\2\2\u014f\u013a\3\2\2\2\u014f\u014b\3\2\2\2\u014f\u0150\3\2\2\2\u0150"+
		"\u0152\3\2\2\2\u0151\u0139\3\2\2\2\u0151\u0152\3\2\2\2\u0152\u018b\3\2"+
		"\2\2\u0153\u0158\5\30\r\2\u0154\u0155\7\66\2\2\u0155\u0157\5\30\r\2\u0156"+
		"\u0154\3\2\2\2\u0157\u015a\3\2\2\2\u0158\u0156\3\2\2\2\u0158\u0159\3\2"+
		"\2\2\u0159\u0173\3\2\2\2\u015a\u0158\3\2\2\2\u015b\u0171\7\66\2\2\u015c"+
		"\u0161\5\32\16\2\u015d\u015e\7\66\2\2\u015e\u0160\5\30\r\2\u015f\u015d"+
		"\3\2\2\2\u0160\u0163\3\2\2\2\u0161\u015f\3\2\2\2\u0161\u0162\3\2\2\2\u0162"+
		"\u016b\3\2\2\2\u0163\u0161\3\2\2\2\u0164\u0169\7\66\2\2\u0165\u0167\5"+
		"\34\17\2\u0166\u0168\7\66\2\2\u0167\u0166\3\2\2\2\u0167\u0168\3\2\2\2"+
		"\u0168\u016a\3\2\2\2\u0169\u0165\3\2\2\2\u0169\u016a\3\2\2\2\u016a\u016c"+
		"\3\2\2\2\u016b\u0164\3\2\2\2\u016b\u016c\3\2\2\2\u016c\u0172\3\2\2\2\u016d"+
		"\u016f\5\34\17\2\u016e\u0170\7\66\2\2\u016f\u016e\3\2\2\2\u016f\u0170"+
		"\3\2\2\2\u0170\u0172\3\2\2\2\u0171\u015c\3\2\2\2\u0171\u016d\3\2\2\2\u0171"+
		"\u0172\3\2\2\2\u0172\u0174\3\2\2\2\u0173\u015b\3\2\2\2\u0173\u0174\3\2"+
		"\2\2\u0174\u018b\3\2\2\2\u0175\u017a\5\32\16\2\u0176\u0177\7\66\2\2\u0177"+
		"\u0179\5\30\r\2\u0178\u0176\3\2\2\2\u0179\u017c\3\2\2\2\u017a\u0178\3"+
		"\2\2\2\u017a\u017b\3\2\2\2\u017b\u0184\3\2\2\2\u017c\u017a\3\2\2\2\u017d"+
		"\u0182\7\66\2\2\u017e\u0180\5\34\17\2\u017f\u0181\7\66\2\2\u0180\u017f"+
		"\3\2\2\2\u0180\u0181\3\2\2\2\u0181\u0183\3\2\2\2\u0182\u017e\3\2\2\2\u0182"+
		"\u0183\3\2\2\2\u0183\u0185\3\2\2\2\u0184\u017d\3\2\2\2\u0184\u0185\3\2"+
		"\2\2\u0185\u018b\3\2\2\2\u0186\u0188\5\34\17\2\u0187\u0189\7\66\2\2\u0188"+
		"\u0187\3\2\2\2\u0188\u0189\3\2\2\2\u0189\u018b\3\2\2\2\u018a\u0123\3\2"+
		"\2\2\u018a\u0153\3\2\2\2\u018a\u0175\3\2\2\2\u018a\u0186\3\2\2\2\u018b"+
		"\u018c\3\2\2\2\u018c\u018d\b\f\1\2\u018d\27\3\2\2\2\u018e\u018f\7(\2\2"+
		"\u018f\u0194\b\r\1\2\u0190\u0191\7\67\2\2\u0191\u0192\5h\65\2\u0192\u0193"+
		"\b\r\1\2\u0193\u0195\3\2\2\2\u0194\u0190\3\2\2\2\u0194\u0195\3\2\2\2\u0195"+
		"\u019a\3\2\2\2\u0196\u0197\7:\2\2\u0197\u0198\5h\65\2\u0198\u0199\b\r"+
		"\1\2\u0199\u019b\3\2\2\2\u019a\u0196\3\2\2\2\u019a\u019b\3\2\2\2\u019b"+
		"\u019c\3\2\2\2\u019c\u019d\b\r\1\2\u019d\31\3\2\2\2\u019e\u019f\7\63\2"+
		"\2\u019f\u01a8\b\16\1\2\u01a0\u01a1\7(\2\2\u01a1\u01a6\b\16\1\2\u01a2"+
		"\u01a3\7\67\2\2\u01a3\u01a4\5h\65\2\u01a4\u01a5\b\16\1\2\u01a5\u01a7\3"+
		"\2\2\2\u01a6\u01a2\3\2\2\2\u01a6\u01a7\3\2\2\2\u01a7\u01a9\3\2\2\2\u01a8"+
		"\u01a0\3\2\2\2\u01a8\u01a9\3\2\2\2\u01a9\u01aa\3\2\2\2\u01aa\u01ab\b\16"+
		"\1\2\u01ab\33\3\2\2\2\u01ac\u01ad\79\2\2\u01ad\u01ae\7(\2\2\u01ae\u01b3"+
		"\b\17\1\2\u01af\u01b0\7\67\2\2\u01b0\u01b1\5h\65\2\u01b1\u01b2\b\17\1"+
		"\2\u01b2\u01b4\3\2\2\2\u01b3\u01af\3\2\2\2\u01b3\u01b4\3\2\2\2\u01b4\u01b5"+
		"\3\2\2\2\u01b5\u01b6\b\17\1\2\u01b6\35\3\2\2\2\u01b7\u021f\b\20\1\2\u01b8"+
		"\u01bd\5 \21\2\u01b9\u01ba\7\66\2\2\u01ba\u01bc\5 \21\2\u01bb\u01b9\3"+
		"\2\2\2\u01bc\u01bf\3\2\2\2\u01bd\u01bb\3\2\2\2\u01bd\u01be\3\2\2\2\u01be"+
		"\u01c0\3\2\2\2\u01bf\u01bd\3\2\2\2\u01c0\u01c1\7\66\2\2\u01c1\u01c2\7"+
		"D\2\2\u01c2\u01cc\b\20\1\2\u01c3\u01c4\7\66\2\2\u01c4\u01c9\5 \21\2\u01c5"+
		"\u01c6\7\66\2\2\u01c6\u01c8\5 \21\2\u01c7\u01c5\3\2\2\2\u01c8\u01cb\3"+
		"\2\2\2\u01c9\u01c7\3\2\2\2\u01c9\u01ca\3\2\2\2\u01ca\u01cd\3\2\2\2\u01cb"+
		"\u01c9\3\2\2\2\u01cc\u01c3\3\2\2\2\u01cc\u01cd\3\2\2\2\u01cd\u01e6\3\2"+
		"\2\2\u01ce\u01e4\7\66\2\2\u01cf\u01d4\5\"\22\2\u01d0\u01d1\7\66\2\2\u01d1"+
		"\u01d3\5 \21\2\u01d2\u01d0\3\2\2\2\u01d3\u01d6\3\2\2\2\u01d4\u01d2\3\2"+
		"\2\2\u01d4\u01d5\3\2\2\2\u01d5\u01de\3\2\2\2\u01d6\u01d4\3\2\2\2\u01d7"+
		"\u01dc\7\66\2\2\u01d8\u01da\5$\23\2\u01d9\u01db\7\66\2\2\u01da\u01d9\3"+
		"\2\2\2\u01da\u01db\3\2\2\2\u01db\u01dd\3\2\2\2\u01dc\u01d8\3\2\2\2\u01dc"+
		"\u01dd\3\2\2\2\u01dd\u01df\3\2\2\2\u01de\u01d7\3\2\2\2\u01de\u01df\3\2"+
		"\2\2\u01df\u01e5\3\2\2\2\u01e0\u01e2\5$\23\2\u01e1\u01e3\7\66\2\2\u01e2"+
		"\u01e1\3\2\2\2\u01e2\u01e3\3\2\2\2\u01e3\u01e5\3\2\2\2\u01e4\u01cf\3\2"+
		"\2\2\u01e4\u01e0\3\2\2\2\u01e4\u01e5\3\2\2\2\u01e5\u01e7\3\2\2\2\u01e6"+
		"\u01ce\3\2\2\2\u01e6\u01e7\3\2\2\2\u01e7\u0220\3\2\2\2\u01e8\u01ed\5 "+
		"\21\2\u01e9\u01ea\7\66\2\2\u01ea\u01ec\5 \21\2\u01eb\u01e9\3\2\2\2\u01ec"+
		"\u01ef\3\2\2\2\u01ed\u01eb\3\2\2\2\u01ed\u01ee\3\2\2\2\u01ee\u0208\3\2"+
		"\2\2\u01ef\u01ed\3\2\2\2\u01f0\u0206\7\66\2\2\u01f1\u01f6\5\"\22\2\u01f2"+
		"\u01f3\7\66\2\2\u01f3\u01f5\5 \21\2\u01f4\u01f2\3\2\2\2\u01f5\u01f8\3"+
		"\2\2\2\u01f6\u01f4\3\2\2\2\u01f6\u01f7\3\2\2\2\u01f7\u0200\3\2\2\2\u01f8"+
		"\u01f6\3\2\2\2\u01f9\u01fe\7\66\2\2\u01fa\u01fc\5$\23\2\u01fb\u01fd\7"+
		"\66\2\2\u01fc\u01fb\3\2\2\2\u01fc\u01fd\3\2\2\2\u01fd\u01ff\3\2\2\2\u01fe"+
		"\u01fa\3\2\2\2\u01fe\u01ff\3\2\2\2\u01ff\u0201\3\2\2\2\u0200\u01f9\3\2"+
		"\2\2\u0200\u0201\3\2\2\2\u0201\u0207\3\2\2\2\u0202\u0204\5$\23\2\u0203"+
		"\u0205\7\66\2\2\u0204\u0203\3\2\2\2\u0204\u0205\3\2\2\2\u0205\u0207\3"+
		"\2\2\2\u0206\u01f1\3\2\2\2\u0206\u0202\3\2\2\2\u0206\u0207\3\2\2\2\u0207"+
		"\u0209\3\2\2\2\u0208\u01f0\3\2\2\2\u0208\u0209\3\2\2\2\u0209\u0220\3\2"+
		"\2\2\u020a\u020f\5\"\22\2\u020b\u020c\7\66\2\2\u020c\u020e\5 \21\2\u020d"+
		"\u020b\3\2\2\2\u020e\u0211\3\2\2\2\u020f\u020d\3\2\2\2\u020f\u0210\3\2"+
		"\2\2\u0210\u0219\3\2\2\2\u0211\u020f\3\2\2\2\u0212\u0217\7\66\2\2\u0213"+
		"\u0215\5$\23\2\u0214\u0216\7\66\2\2\u0215\u0214\3\2\2\2\u0215\u0216\3"+
		"\2\2\2\u0216\u0218\3\2\2\2\u0217\u0213\3\2\2\2\u0217\u0218\3\2\2\2\u0218"+
		"\u021a\3\2\2\2\u0219\u0212\3\2\2\2\u0219\u021a\3\2\2\2\u021a\u0220\3\2"+
		"\2\2\u021b\u021d\5$\23\2\u021c\u021e\7\66\2\2\u021d\u021c\3\2\2\2\u021d"+
		"\u021e\3\2\2\2\u021e\u0220\3\2\2\2\u021f\u01b8\3\2\2\2\u021f\u01e8\3\2"+
		"\2\2\u021f\u020a\3\2\2\2\u021f\u021b\3\2\2\2\u0220\u0221\3\2\2\2\u0221"+
		"\u0222\b\20\1\2\u0222\u0223\b\20\1\2\u0223\37\3\2\2\2\u0224\u0225\7(\2"+
		"\2\u0225\u022a\b\21\1\2\u0226\u0227\7:\2\2\u0227\u0228\5h\65\2\u0228\u0229"+
		"\b\21\1\2\u0229\u022b\3\2\2\2\u022a\u0226\3\2\2\2\u022a\u022b\3\2\2\2"+
		"\u022b\u022c\3\2\2\2\u022c\u022d\b\21\1\2\u022d!\3\2\2\2\u022e\u022f\7"+
		"\63\2\2\u022f\u0232\b\22\1\2\u0230\u0231\7(\2\2\u0231\u0233\b\22\1\2\u0232"+
		"\u0230\3\2\2\2\u0232\u0233\3\2\2\2\u0233\u0234\3\2\2\2\u0234\u0235\b\22"+
		"\1\2\u0235#\3\2\2\2\u0236\u0237\79\2\2\u0237\u0238\7(\2\2\u0238\u0239"+
		"\b\23\1\2\u0239%\3\2\2\2\u023a\u023d\5(\25\2\u023b\u023d\5R*\2\u023c\u023a"+
		"\3\2\2\2\u023c\u023b\3\2\2\2\u023d\'\3\2\2\2\u023e\u0243\5*\26\2\u023f"+
		"\u0240\78\2\2\u0240\u0242\5*\26\2\u0241\u023f\3\2\2\2\u0242\u0245\3\2"+
		"\2\2\u0243\u0241\3\2\2\2\u0243\u0244\3\2\2\2\u0244\u0247\3\2\2\2\u0245"+
		"\u0243\3\2\2\2\u0246\u0248\78\2\2\u0247\u0246\3\2\2\2\u0247\u0248\3\2"+
		"\2\2\u0248\u0249\3\2\2\2\u0249\u024a\7\'\2\2\u024a)\3\2\2\2\u024b\u0255"+
		"\5,\27\2\u024c\u0255\5\62\32\2\u024d\u024e\7\"\2\2\u024e\u0255\b\26\1"+
		"\2\u024f\u0255\5\64\33\2\u0250\u0255\5<\37\2\u0251\u0255\5L\'\2\u0252"+
		"\u0255\5N(\2\u0253\u0255\5P)\2\u0254\u024b\3\2\2\2\u0254\u024c\3\2\2\2"+
		"\u0254\u024d\3\2\2\2\u0254\u024f\3\2\2\2\u0254\u0250\3\2\2\2\u0254\u0251"+
		"\3\2\2\2\u0254\u0252\3\2\2\2\u0254\u0253\3\2\2\2\u0255+\3\2\2\2\u0256"+
		"\u0257\5.\30\2\u0257\u027f\b\27\1\2\u0258\u0259\7\67\2\2\u0259\u025e\5"+
		"h\65\2\u025a\u025b\7:\2\2\u025b\u025c\5h\65\2\u025c\u025d\b\27\1\2\u025d"+
		"\u025f\3\2\2\2\u025e\u025a\3\2\2\2\u025e\u025f\3\2\2\2\u025f\u0260\3\2"+
		"\2\2\u0260\u0261\b\27\1\2\u0261\u0280\3\2\2\2\u0262\u0269\5\60\31\2\u0263"+
		"\u0264\5\u00a6T\2\u0264\u0265\b\27\1\2\u0265\u026a\3\2\2\2\u0266\u0267"+
		"\5\u0096L\2\u0267\u0268\b\27\1\2\u0268\u026a\3\2\2\2\u0269\u0263\3\2\2"+
		"\2\u0269\u0266\3\2\2\2\u026a\u026b\3\2\2\2\u026b\u026c\b\27\1\2\u026c"+
		"\u0280\3\2\2\2\u026d\u026e\b\27\1\2\u026e\u027b\b\27\1\2\u026f\u0270\7"+
		":\2\2\u0270\u0277\b\27\1\2\u0271\u0272\5\u00a6T\2\u0272\u0273\b\27\1\2"+
		"\u0273\u0278\3\2\2\2\u0274\u0275\5.\30\2\u0275\u0276\b\27\1\2\u0276\u0278"+
		"\3\2\2\2\u0277\u0271\3\2\2\2\u0277\u0274\3\2\2\2\u0278\u027a\3\2\2\2\u0279"+
		"\u026f\3\2\2\2\u027a\u027d\3\2\2\2\u027b\u0279\3\2\2\2\u027b\u027c\3\2"+
		"\2\2\u027c\u027e\3\2\2\2\u027d\u027b\3\2\2\2\u027e\u0280\b\27\1\2\u027f"+
		"\u0258\3\2\2\2\u027f\u0262\3\2\2\2\u027f\u026d\3\2\2\2\u0280-\3\2\2\2"+
		"\u0281\u0282\5h\65\2\u0282\u0283\b\30\1\2\u0283\u0288\3\2\2\2\u0284\u0285"+
		"\5z>\2\u0285\u0286\b\30\1\2\u0286\u0288\3\2\2\2\u0287\u0281\3\2\2\2\u0287"+
		"\u0284\3\2\2\2\u0288\u02a7\3\2\2\2\u0289\u028a\b\30\1\2\u028a\u02a4\7"+
		"\66\2\2\u028b\u028c\5h\65\2\u028c\u028d\b\30\1\2\u028d\u0292\3\2\2\2\u028e"+
		"\u028f\5z>\2\u028f\u0290\b\30\1\2\u0290\u0292\3\2\2\2\u0291\u028b\3\2"+
		"\2\2\u0291\u028e\3\2\2\2\u0292\u029e\3\2\2\2\u0293\u029a\7\66\2\2\u0294"+
		"\u0295\5h\65\2\u0295\u0296\b\30\1\2\u0296\u029b\3\2\2\2\u0297\u0298\5"+
		"z>\2\u0298\u0299\b\30\1\2\u0299\u029b\3\2\2\2\u029a\u0294\3\2\2\2\u029a"+
		"\u0297\3\2\2\2\u029b\u029d\3\2\2\2\u029c\u0293\3\2\2\2\u029d\u02a0\3\2"+
		"\2\2\u029e\u029c\3\2\2\2\u029e\u029f\3\2\2\2\u029f\u02a2\3\2\2\2\u02a0"+
		"\u029e\3\2\2\2\u02a1\u02a3\7\66\2\2\u02a2\u02a1\3\2\2\2\u02a2\u02a3\3"+
		"\2\2\2\u02a3\u02a5\3\2\2\2\u02a4\u0291\3\2\2\2\u02a4\u02a5\3\2\2\2\u02a5"+
		"\u02a6\3\2\2\2\u02a6\u02a8\b\30\1\2\u02a7\u0289\3\2\2\2\u02a7\u02a8\3"+
		"\2\2\2\u02a8/\3\2\2\2\u02a9\u02aa\t\2\2\2\u02aa\61\3\2\2\2\u02ab\u02ac"+
		"\7!\2\2\u02ac\u02ad\5\u0094K\2\u02ad\u02ae\b\32\1\2\u02ae\63\3\2\2\2\u02af"+
		"\u02b0\7$\2\2\u02b0\u02b7\b\33\1\2\u02b1\u02b2\7#\2\2\u02b2\u02b7\b\33"+
		"\1\2\u02b3\u02b7\5\66\34\2\u02b4\u02b7\5:\36\2\u02b5\u02b7\58\35\2\u02b6"+
		"\u02af\3\2\2\2\u02b6\u02b1\3\2\2\2\u02b6\u02b3\3\2\2\2\u02b6\u02b4\3\2"+
		"\2\2\u02b6\u02b5\3\2\2\2\u02b7\65\3\2\2\2\u02b8\u02b9\7\5\2\2\u02b9\u02bd"+
		"\b\34\1\2\u02ba\u02bb\5.\30\2\u02bb\u02bc\b\34\1\2\u02bc\u02be\3\2\2\2"+
		"\u02bd\u02ba\3\2\2\2\u02bd\u02be\3\2\2\2\u02be\u02bf\3\2\2\2\u02bf\u02c0"+
		"\b\34\1\2\u02c0\67\3\2\2\2\u02c1\u02c2\5\u00a6T\2\u02c2\u02c3\b\35\1\2"+
		"\u02c39\3\2\2\2\u02c4\u02c5\b\36\1\2\u02c5\u02ce\7\6\2\2\u02c6\u02c7\5"+
		"h\65\2\u02c7\u02cc\b\36\1\2\u02c8\u02c9\7\7\2\2\u02c9\u02ca\5h\65\2\u02ca"+
		"\u02cb\b\36\1\2\u02cb\u02cd\3\2\2\2\u02cc\u02c8\3\2\2\2\u02cc\u02cd\3"+
		"\2\2\2\u02cd\u02cf\3\2\2\2\u02ce\u02c6\3\2\2\2\u02ce\u02cf\3\2\2\2\u02cf"+
		"\u02d0\3\2\2\2\u02d0\u02d1\b\36\1\2\u02d1;\3\2\2\2\u02d2\u02d5\5> \2\u02d3"+
		"\u02d5\5@!\2\u02d4\u02d2\3\2\2\2\u02d4\u02d3\3\2\2\2\u02d5=\3\2\2\2\u02d6"+
		"\u02d7\7\b\2\2\u02d7\u02d8\5J&\2\u02d8?\3\2\2\2\u02d9\u02da\7\7\2\2\u02da"+
		"\u02ef\b!\1\2\u02db\u02dc\7\61\2\2\u02dc\u02e0\b!\1\2\u02dd\u02de\7\62"+
		"\2\2\u02de\u02e0\b!\1\2\u02df\u02db\3\2\2\2\u02df\u02dd\3\2\2\2\u02e0"+
		"\u02e3\3\2\2\2\u02e1\u02df\3\2\2\2\u02e1\u02e2\3\2\2\2\u02e2\u02e4\3\2"+
		"\2\2\u02e3\u02e1\3\2\2\2\u02e4\u02e5\5F$\2\u02e5\u02e6\b!\1\2\u02e6\u02f0"+
		"\3\2\2\2\u02e7\u02e8\7\61\2\2\u02e8\u02ec\b!\1\2\u02e9\u02ea\7\62\2\2"+
		"\u02ea\u02ec\b!\1\2\u02eb\u02e7\3\2\2\2\u02eb\u02e9\3\2\2\2\u02ec\u02ed"+
		"\3\2\2\2\u02ed\u02eb\3\2\2\2\u02ed\u02ee\3\2\2\2\u02ee\u02f0\3\2\2\2\u02ef"+
		"\u02e1\3\2\2\2\u02ef\u02eb\3\2\2\2\u02f0\u02f1\3\2\2\2\u02f1\u02f2\7\b"+
		"\2\2\u02f2\u02fc\b!\1\2\u02f3\u02fd\7\63\2\2\u02f4\u02f5\7\64\2\2\u02f5"+
		"\u02f6\5D#\2\u02f6\u02f7\b!\1\2\u02f7\u02f8\7\65\2\2\u02f8\u02fd\3\2\2"+
		"\2\u02f9\u02fa\5D#\2\u02fa\u02fb\b!\1\2\u02fb\u02fd\3\2\2\2\u02fc\u02f3"+
		"\3\2\2\2\u02fc\u02f4\3\2\2\2\u02fc\u02f9\3\2\2\2\u02fd\u02fe\3\2\2\2\u02fe"+
		"\u02ff\b!\1\2\u02ffA\3\2\2\2\u0300\u0301\7(\2\2\u0301\u0305\b\"\1\2\u0302"+
		"\u0303\7\t\2\2\u0303\u0304\7(\2\2\u0304\u0306\b\"\1\2\u0305\u0302\3\2"+
		"\2\2\u0305\u0306\3\2\2\2\u0306\u0307\3\2\2\2\u0307\u0308\b\"\1\2\u0308"+
		"C\3\2\2\2\u0309\u030a\b#\1\2\u030a\u030b\5B\"\2\u030b\u0312\b#\1\2\u030c"+
		"\u030d\7\66\2\2\u030d\u030e\5B\"\2\u030e\u030f\b#\1\2\u030f\u0311\3\2"+
		"\2\2\u0310\u030c\3\2\2\2\u0311\u0314\3\2\2\2\u0312\u0310\3\2\2\2\u0312"+
		"\u0313\3\2\2\2\u0313\u0316\3\2\2\2\u0314\u0312\3\2\2\2\u0315\u0317\7\66"+
		"\2\2\u0316\u0315\3\2\2\2\u0316\u0317\3\2\2\2\u0317\u0318\3\2\2\2\u0318"+
		"\u0319\b#\1\2\u0319E\3\2\2\2\u031a\u031b\7(\2\2\u031b\u0321\b$\1\2\u031c"+
		"\u031d\7\61\2\2\u031d\u031e\7(\2\2\u031e\u0320\b$\1\2\u031f\u031c\3\2"+
		"\2\2\u0320\u0323\3\2\2\2\u0321\u031f\3\2\2\2\u0321\u0322\3\2\2\2\u0322"+
		"G\3\2\2\2\u0323\u0321\3\2\2\2\u0324\u0329\5F$\2\u0325\u0326\7\t\2\2\u0326"+
		"\u0327\7(\2\2\u0327\u032a\b%\1\2\u0328\u032a\b%\1\2\u0329\u0325\3\2\2"+
		"\2\u0329\u0328\3\2\2\2\u032aI\3\2\2\2\u032b\u0330\5H%\2\u032c\u032d\7"+
		"\66\2\2\u032d\u032f\5H%\2\u032e\u032c\3\2\2\2\u032f\u0332\3\2\2\2\u0330"+
		"\u032e\3\2\2\2\u0330\u0331\3\2\2\2\u0331K\3\2\2\2\u0332\u0330\3\2\2\2"+
		"\u0333\u0334\b\'\1\2\u0334\u0335\7\n\2\2\u0335\u0336\7(\2\2\u0336\u033c"+
		"\b\'\1\2\u0337\u0338\7\66\2\2\u0338\u0339\7(\2\2\u0339\u033b\b\'\1\2\u033a"+
		"\u0337\3\2\2\2\u033b\u033e\3\2\2\2\u033c\u033a\3\2\2\2\u033c\u033d\3\2"+
		"\2\2\u033d\u033f\3\2\2\2\u033e\u033c\3\2\2\2\u033f\u0340\b\'\1\2\u0340"+
		"M\3\2\2\2\u0341\u0342\b(\1\2\u0342\u0343\7\13\2\2\u0343\u0344\7(\2\2\u0344"+
		"\u034a\b(\1\2\u0345\u0346\7\66\2\2\u0346\u0347\7(\2\2\u0347\u0349\b(\1"+
		"\2\u0348\u0345\3\2\2\2\u0349\u034c\3\2\2\2\u034a\u0348\3\2\2\2\u034a\u034b"+
		"\3\2\2\2\u034b\u034d\3\2\2\2\u034c\u034a\3\2\2\2\u034d\u034e\b(\1\2\u034e"+
		"O\3\2\2\2\u034f\u0350\7\f\2\2\u0350\u0351\5h\65\2\u0351\u0356\b)\1\2\u0352"+
		"\u0353\7\66\2\2\u0353\u0354\5h\65\2\u0354\u0355\b)\1\2\u0355\u0357\3\2"+
		"\2\2\u0356\u0352\3\2\2\2\u0356\u0357\3\2\2\2\u0357\u0358\3\2\2\2\u0358"+
		"\u0359\b)\1\2\u0359Q\3\2\2\2\u035a\u0364\5V,\2\u035b\u0364\5Z.\2\u035c"+
		"\u0364\5\\/\2\u035d\u0364\5^\60\2\u035e\u0364\5b\62\2\u035f\u0364\5\22"+
		"\n\2\u0360\u0364\5\u009cO\2\u0361\u0364\5\16\b\2\u0362\u0364\5T+\2\u0363"+
		"\u035a\3\2\2\2\u0363\u035b\3\2\2\2\u0363\u035c\3\2\2\2\u0363\u035d\3\2"+
		"\2\2\u0363\u035e\3\2\2\2\u0363\u035f\3\2\2\2\u0363\u0360\3\2\2\2\u0363"+
		"\u0361\3\2\2\2\u0363\u0362\3\2\2\2\u0364S\3\2\2\2\u0365\u0369\7%\2\2\u0366"+
		"\u036a\5\22\n\2\u0367\u036a\5b\62\2\u0368\u036a\5\\/\2\u0369\u0366\3\2"+
		"\2\2\u0369\u0367\3\2\2\2\u0369\u0368\3\2\2\2\u036aU\3\2\2\2\u036b\u036c"+
		"\7\r\2\2\u036c\u036d\5h\65\2\u036d\u036e\7\67\2\2\u036e\u036f\5f\64\2"+
		"\u036f\u0370\5X-\2\u0370\u0371\b,\1\2\u0371W\3\2\2\2\u0372\u0373\7\16"+
		"\2\2\u0373\u0374\5h\65\2\u0374\u0375\7\67\2\2\u0375\u0376\5f\64\2\u0376"+
		"\u0377\5X-\2\u0377\u0378\b-\1\2\u0378\u0380\3\2\2\2\u0379\u037a\7\17\2"+
		"\2\u037a\u037b\7\67\2\2\u037b\u037c\5f\64\2\u037c\u037d\b-\1\2\u037d\u0380"+
		"\3\2\2\2\u037e\u0380\b-\1\2\u037f\u0372\3\2\2\2\u037f\u0379\3\2\2\2\u037f"+
		"\u037e\3\2\2\2\u0380Y\3\2\2\2\u0381\u0382\7\20\2\2\u0382\u0383\5h\65\2"+
		"\u0383\u0384\7\67\2\2\u0384\u0385\b.\1\2\u0385\u0386\5f\64\2\u0386\u038c"+
		"\b.\1\2\u0387\u0388\7\17\2\2\u0388\u0389\7\67\2\2\u0389\u038a\5f\64\2"+
		"\u038a\u038b\b.\1\2\u038b\u038d\3\2\2\2\u038c\u0387\3\2\2\2\u038c\u038d"+
		"\3\2\2\2\u038d\u038e\3\2\2\2\u038e\u038f\b.\1\2\u038f[\3\2\2\2\u0390\u0391"+
		"\7\21\2\2\u0391\u0392\5\u0094K\2\u0392\u0393\7\22\2\2\u0393\u0394\5\u0096"+
		"L\2\u0394\u0395\7\67\2\2\u0395\u0396\b/\1\2\u0396\u0397\5f\64\2\u0397"+
		"\u039d\b/\1\2\u0398\u0399\7\17\2\2\u0399\u039a\7\67\2\2\u039a\u039b\5"+
		"f\64\2\u039b\u039c\b/\1\2\u039c\u039e\3\2\2\2\u039d\u0398\3\2\2\2\u039d"+
		"\u039e\3\2\2\2\u039e\u039f\3\2\2\2\u039f\u03a0\b/\1\2\u03a0]\3\2\2\2\u03a1"+
		"\u03a2\7\23\2\2\u03a2\u03a3\7\67\2\2\u03a3\u03a4\5f\64\2\u03a4\u03a5\b"+
		"\60\1\2\u03a5\u03c0\b\60\1\2\u03a6\u03a7\5`\61\2\u03a7\u03a8\b\60\1\2"+
		"\u03a8\u03aa\3\2\2\2\u03a9\u03a6\3\2\2\2\u03aa\u03ab\3\2\2\2\u03ab\u03a9"+
		"\3\2\2\2\u03ab\u03ac\3\2\2\2\u03ac\u03b2\3\2\2\2\u03ad\u03ae\7\17\2\2"+
		"\u03ae\u03af\7\67\2\2\u03af\u03b0\5f\64\2\u03b0\u03b1\b\60\1\2\u03b1\u03b3"+
		"\3\2\2\2\u03b2\u03ad\3\2\2\2\u03b2\u03b3\3\2\2\2\u03b3\u03b9\3\2\2\2\u03b4"+
		"\u03b5\7\24\2\2\u03b5\u03b6\7\67\2\2\u03b6\u03b7\5f\64\2\u03b7\u03b8\b"+
		"\60\1\2\u03b8\u03ba\3\2\2\2\u03b9\u03b4\3\2\2\2\u03b9\u03ba\3\2\2\2\u03ba"+
		"\u03c1\3\2\2\2\u03bb\u03bc\7\24\2\2\u03bc\u03bd\7\67\2\2\u03bd\u03be\5"+
		"f\64\2\u03be\u03bf\b\60\1\2\u03bf\u03c1\3\2\2\2\u03c0\u03a9\3\2\2\2\u03c0"+
		"\u03bb\3\2\2\2\u03c1\u03c2\3\2\2\2\u03c2\u03c3\b\60\1\2\u03c3_\3\2\2\2"+
		"\u03c4\u03c5\7\26\2\2\u03c5\u03cd\b\61\1\2\u03c6\u03c7\5h\65\2\u03c7\u03cb"+
		"\b\61\1\2\u03c8\u03c9\7\t\2\2\u03c9\u03ca\7(\2\2\u03ca\u03cc\b\61\1\2"+
		"\u03cb\u03c8\3\2\2\2\u03cb\u03cc\3\2\2\2\u03cc\u03ce\3\2\2\2\u03cd\u03c6"+
		"\3\2\2\2\u03cd\u03ce\3\2\2\2\u03ce\u03cf\3\2\2\2\u03cf\u03d0\7\67\2\2"+
		"\u03d0\u03d1\5f\64\2\u03d1\u03d2\b\61\1\2\u03d2a\3\2\2\2\u03d3\u03d4\7"+
		"\25\2\2\u03d4\u03d5\5d\63\2\u03d5\u03d6\b\62\1\2\u03d6c\3\2\2\2\u03d7"+
		"\u03d8\5h\65\2\u03d8\u03dd\b\63\1\2\u03d9\u03da\7\t\2\2\u03da\u03db\5"+
		"|?\2\u03db\u03dc\b\63\1\2\u03dc\u03de\3\2\2\2\u03dd\u03d9\3\2\2\2\u03dd"+
		"\u03de\3\2\2\2\u03de\u03df\3\2\2\2\u03df\u03e8\b\63\1\2\u03e0\u03e1\7"+
		"\66\2\2\u03e1\u03e2\5d\63\2\u03e2\u03e3\b\63\1\2\u03e3\u03e9\3\2\2\2\u03e4"+
		"\u03e5\7\67\2\2\u03e5\u03e6\5f\64\2\u03e6\u03e7\b\63\1\2\u03e7\u03e9\3"+
		"\2\2\2\u03e8\u03e0\3\2\2\2\u03e8\u03e4\3\2\2\2\u03e9\u03ea\3\2\2\2\u03ea"+
		"\u03eb\b\63\1\2\u03ebe\3\2\2\2\u03ec\u03f7\b\64\1\2\u03ed\u03f8\5(\25"+
		"\2\u03ee\u03ef\7\'\2\2\u03ef\u03f1\7e\2\2\u03f0\u03f2\5&\24\2\u03f1\u03f0"+
		"\3\2\2\2\u03f2\u03f3\3\2\2\2\u03f3\u03f1\3\2\2\2\u03f3\u03f4\3\2\2\2\u03f4"+
		"\u03f5\3\2\2\2\u03f5\u03f6\7f\2\2\u03f6\u03f8\3\2\2\2\u03f7\u03ed\3\2"+
		"\2\2\u03f7\u03ee\3\2\2\2\u03f8\u03f9\3\2\2\2\u03f9\u03fa\b\64\1\2\u03fa"+
		"g\3\2\2\2\u03fb\u03fc\5p9\2\u03fc\u0403\b\65\1\2\u03fd\u03fe\7\r\2\2\u03fe"+
		"\u03ff\5p9\2\u03ff\u0400\7\17\2\2\u0400\u0401\5h\65\2\u0401\u0402\b\65"+
		"\1\2\u0402\u0404\3\2\2\2\u0403\u03fd\3\2\2\2\u0403\u0404\3\2\2\2\u0404"+
		"\u0409\3\2\2\2\u0405\u0406\5l\67\2\u0406\u0407\b\65\1\2\u0407\u0409\3"+
		"\2\2\2\u0408\u03fb\3\2\2\2\u0408\u0405\3\2\2\2\u0409i\3\2\2\2\u040a\u040b"+
		"\5p9\2\u040b\u040c\b\66\1\2\u040c\u0411\3\2\2\2\u040d\u040e\5n8\2\u040e"+
		"\u040f\b\66\1\2\u040f\u0411\3\2\2\2\u0410\u040a\3\2\2\2\u0410\u040d\3"+
		"\2\2\2\u0411k\3\2\2\2\u0412\u0413\7\27\2\2\u0413\u0417\b\67\1\2\u0414"+
		"\u0415\5\36\20\2\u0415\u0416\b\67\1\2\u0416\u0418\3\2\2\2\u0417\u0414"+
		"\3\2\2\2\u0417\u0418\3\2\2\2\u0418\u0419\3\2\2\2\u0419\u041a\b\67\1\2"+
		"\u041a\u041b\7\67\2\2\u041b\u041c\5h\65\2\u041c\u041d\b\67\1\2\u041d\u041e"+
		"\b\67\1\2\u041em\3\2\2\2\u041f\u0420\7\27\2\2\u0420\u0424\b8\1\2\u0421"+
		"\u0422\5\36\20\2\u0422\u0423\b8\1\2\u0423\u0425\3\2\2\2\u0424\u0421\3"+
		"\2\2\2\u0424\u0425\3\2\2\2\u0425\u0426\3\2\2\2\u0426\u0427\b8\1\2\u0427"+
		"\u0428\7\67\2\2\u0428\u0429\5j\66\2\u0429\u042a\b8\1\2\u042a\u042b\b8"+
		"\1\2\u042bo\3\2\2\2\u042c\u043a\5r:\2\u042d\u042e\b9\1\2\u042e\u0433\b"+
		"9\1\2\u042f\u0430\7\30\2\2\u0430\u0431\5r:\2\u0431\u0432\b9\1\2\u0432"+
		"\u0434\3\2\2\2\u0433\u042f\3\2\2\2\u0434\u0435\3\2\2\2\u0435\u0433\3\2"+
		"\2\2\u0435\u0436\3\2\2\2\u0436\u0437\3\2\2\2\u0437\u0438\b9\1\2\u0438"+
		"\u043b\3\2\2\2\u0439\u043b\b9\1\2\u043a\u042d\3\2\2\2\u043a\u0439\3\2"+
		"\2\2\u043bq\3\2\2\2\u043c\u044a\5t;\2\u043d\u043e\b:\1\2\u043e\u0443\b"+
		":\1\2\u043f\u0440\7\31\2\2\u0440\u0441\5t;\2\u0441\u0442\b:\1\2\u0442"+
		"\u0444\3\2\2\2\u0443\u043f\3\2\2\2\u0444\u0445\3\2\2\2\u0445\u0443\3\2"+
		"\2\2\u0445\u0446\3\2\2\2\u0446\u0447\3\2\2\2\u0447\u0448\b:\1\2\u0448"+
		"\u044b\3\2\2\2\u0449\u044b\b:\1\2\u044a\u043d\3\2\2\2\u044a\u0449\3\2"+
		"\2\2\u044bs\3\2\2\2\u044c\u044d\7\32\2\2\u044d\u044e\5t;\2\u044e\u044f"+
		"\b;\1\2\u044f\u0454\3\2\2\2\u0450\u0451\5v<\2\u0451\u0452\b;\1\2\u0452"+
		"\u0454\3\2\2\2\u0453\u044c\3\2\2\2\u0453\u0450\3\2\2\2\u0454u\3\2\2\2"+
		"\u0455\u0462\5|?\2\u0456\u045b\b<\1\2\u0457\u0458\5x=\2\u0458\u0459\5"+
		"|?\2\u0459\u045a\b<\1\2\u045a\u045c\3\2\2\2\u045b\u0457\3\2\2\2\u045c"+
		"\u045d\3\2\2\2\u045d\u045b\3\2\2\2\u045d\u045e\3\2\2\2\u045e\u045f\3\2"+
		"\2\2\u045f\u0460\b<\1\2\u0460\u0463\3\2\2\2\u0461\u0463\b<\1\2\u0462\u0456"+
		"\3\2\2\2\u0462\u0461\3\2\2\2\u0463w\3\2\2\2\u0464\u0465\7J\2\2\u0465\u047d"+
		"\b=\1\2\u0466\u0467\7K\2\2\u0467\u047d\b=\1\2\u0468\u0469\7L\2\2\u0469"+
		"\u047d\b=\1\2\u046a\u046b\7M\2\2\u046b\u047d\b=\1\2\u046c\u046d\7N\2\2"+
		"\u046d\u047d\b=\1\2\u046e\u046f\7O\2\2\u046f\u047d\b=\1\2\u0470\u0471"+
		"\7P\2\2\u0471\u047d\b=\1\2\u0472\u0473\7\22\2\2\u0473\u047d\b=\1\2\u0474"+
		"\u0475\7\32\2\2\u0475\u0476\7\22\2\2\u0476\u047d\b=\1\2\u0477\u0478\7"+
		"\33\2\2\u0478\u047d\b=\1\2\u0479\u047a\7\33\2\2\u047a\u047b\7\32\2\2\u047b"+
		"\u047d\b=\1\2\u047c\u0464\3\2\2\2\u047c\u0466\3\2\2\2\u047c\u0468\3\2"+
		"\2\2\u047c\u046a\3\2\2\2\u047c\u046c\3\2\2\2\u047c\u046e\3\2\2\2\u047c"+
		"\u0470\3\2\2\2\u047c\u0472\3\2\2\2\u047c\u0474\3\2\2\2\u047c\u0477\3\2"+
		"\2\2\u047c\u0479\3\2\2\2\u047dy\3\2\2\2\u047e\u047f\7\63\2\2\u047f\u0480"+
		"\5|?\2\u0480\u0481\b>\1\2\u0481{\3\2\2\2\u0482\u0483\5~@\2\u0483\u048a"+
		"\b?\1\2\u0484\u0485\7=\2\2\u0485\u0486\5~@\2\u0486\u0487\b?\1\2\u0487"+
		"\u0489\3\2\2\2\u0488\u0484\3\2\2\2\u0489\u048c\3\2\2\2\u048a\u0488\3\2"+
		"\2\2\u048a\u048b\3\2\2\2\u048b}\3\2\2\2\u048c\u048a\3\2\2\2\u048d\u048e"+
		"\5\u0080A\2\u048e\u0495\b@\1\2\u048f\u0490\7>\2\2\u0490\u0491\5\u0080"+
		"A\2\u0491\u0492\b@\1\2\u0492\u0494\3\2\2\2\u0493\u048f\3\2\2\2\u0494\u0497"+
		"\3\2\2\2\u0495\u0493\3\2\2\2\u0495\u0496\3\2\2\2\u0496\177\3\2\2\2\u0497"+
		"\u0495\3\2\2\2\u0498\u0499\5\u0082B\2\u0499\u04a0\bA\1\2\u049a\u049b\7"+
		"?\2\2\u049b\u049c\5\u0082B\2\u049c\u049d\bA\1\2\u049d\u049f\3\2\2\2\u049e"+
		"\u049a\3\2\2\2\u049f\u04a2\3\2\2\2\u04a0\u049e\3\2\2\2\u04a0\u04a1\3\2"+
		"\2\2\u04a1\u0081\3\2\2\2\u04a2\u04a0\3\2\2\2\u04a3\u04a4\5\u0084C\2\u04a4"+
		"\u04b1\bB\1\2\u04a5\u04aa\bB\1\2\u04a6\u04a7\7@\2\2\u04a7\u04ab\bB\1\2"+
		"\u04a8\u04a9\7A\2\2\u04a9\u04ab\bB\1\2\u04aa\u04a6\3\2\2\2\u04aa\u04a8"+
		"\3\2\2\2\u04ab\u04ac\3\2\2\2\u04ac\u04ad\5\u0084C\2\u04ad\u04ae\bB\1\2"+
		"\u04ae\u04b0\3\2\2\2\u04af\u04a5\3\2\2\2\u04b0\u04b3\3\2\2\2\u04b1\u04af"+
		"\3\2\2\2\u04b1\u04b2\3\2\2\2\u04b2\u0083\3\2\2\2\u04b3\u04b1\3\2\2\2\u04b4"+
		"\u04b5\5\u0086D\2\u04b5\u04c2\bC\1\2\u04b6\u04bb\bC\1\2\u04b7\u04b8\7"+
		"B\2\2\u04b8\u04bc\bC\1\2\u04b9\u04ba\7C\2\2\u04ba\u04bc\bC\1\2\u04bb\u04b7"+
		"\3\2\2\2\u04bb\u04b9\3\2\2\2\u04bc\u04bd\3\2\2\2\u04bd\u04be\5\u0086D"+
		"\2\u04be\u04bf\bC\1\2\u04bf\u04c1\3\2\2\2\u04c0\u04b6\3\2\2\2\u04c1\u04c4"+
		"\3\2\2\2\u04c2\u04c0\3\2\2\2\u04c2\u04c3\3\2\2\2\u04c3\u0085\3\2\2\2\u04c4"+
		"\u04c2\3\2\2\2\u04c5\u04c6\5\u0088E\2\u04c6\u04d9\bD\1\2\u04c7\u04d2\b"+
		"D\1\2\u04c8\u04c9\7\63\2\2\u04c9\u04d3\bD\1\2\u04ca\u04cb\7Q\2\2\u04cb"+
		"\u04d3\bD\1\2\u04cc\u04cd\7D\2\2\u04cd\u04d3\bD\1\2\u04ce\u04cf\7E\2\2"+
		"\u04cf\u04d3\bD\1\2\u04d0\u04d1\7F\2\2\u04d1\u04d3\bD\1\2\u04d2\u04c8"+
		"\3\2\2\2\u04d2\u04ca\3\2\2\2\u04d2\u04cc\3\2\2\2\u04d2\u04ce\3\2\2\2\u04d2"+
		"\u04d0\3\2\2\2\u04d3\u04d4\3\2\2\2\u04d4\u04d5\5\u0088E\2\u04d5\u04d6"+
		"\bD\1\2\u04d6\u04d8\3\2\2\2\u04d7\u04c7\3\2\2\2\u04d8\u04db\3\2\2\2\u04d9"+
		"\u04d7\3\2\2\2\u04d9\u04da\3\2\2\2\u04da\u0087\3\2\2\2\u04db\u04d9\3\2"+
		"\2\2\u04dc\u04e3\bE\1\2\u04dd\u04de\7B\2\2\u04de\u04e4\bE\1\2\u04df\u04e0"+
		"\7C\2\2\u04e0\u04e4\bE\1\2\u04e1\u04e2\7G\2\2\u04e2\u04e4\bE\1\2\u04e3"+
		"\u04dd\3\2\2\2\u04e3\u04df\3\2\2\2\u04e3\u04e1\3\2\2\2\u04e4\u04e5\3\2"+
		"\2\2\u04e5\u04e6\5\u0088E\2\u04e6\u04e7\bE\1\2\u04e7\u04ec\3\2\2\2\u04e8"+
		"\u04e9\5\u008aF\2\u04e9\u04ea\bE\1\2\u04ea\u04ec\3\2\2\2\u04eb\u04dc\3"+
		"\2\2\2\u04eb\u04e8\3\2\2\2\u04ec\u0089\3\2\2\2\u04ed\u04ee\5\u008cG\2"+
		"\u04ee\u04f3\bF\1\2\u04ef\u04f0\79\2\2\u04f0\u04f1\5\u0088E\2\u04f1\u04f2"+
		"\bF\1\2\u04f2\u04f4\3\2\2\2\u04f3\u04ef\3\2\2\2\u04f3\u04f4\3\2\2\2\u04f4"+
		"\u008b\3\2\2\2\u04f5\u04f7\7&\2\2\u04f6\u04f5\3\2\2\2\u04f6\u04f7\3\2"+
		"\2\2\u04f7\u04f8\3\2\2\2\u04f8\u04f9\5\u008eH\2\u04f9\u0509\bG\1\2\u04fa"+
		"\u04fb\7\64\2\2\u04fb\u04fc\5\u009eP\2\u04fc\u04fd\7\65\2\2\u04fd\u04fe"+
		"\bG\1\2\u04fe\u0508\3\2\2\2\u04ff\u0500\7;\2\2\u0500\u0501\5\u0090I\2"+
		"\u0501\u0502\7<\2\2\u0502\u0503\bG\1\2\u0503\u0508\3\2\2\2\u0504\u0505"+
		"\7\61\2\2\u0505\u0506\7(\2\2\u0506\u0508\bG\1\2\u0507\u04fa\3\2\2\2\u0507"+
		"\u04ff\3\2\2\2\u0507\u0504\3\2\2\2\u0508\u050b\3\2\2\2\u0509\u0507\3\2"+
		"\2\2\u0509\u050a\3\2\2\2\u050a\u008d\3\2\2\2\u050b\u0509\3\2\2\2\u050c"+
		"\u0514\7\64\2\2\u050d\u050e\5\u00a6T\2\u050e\u050f\bH\1\2\u050f\u0515"+
		"\3\2\2\2\u0510\u0511\5\u009aN\2\u0511\u0512\bH\1\2\u0512\u0515\3\2\2\2"+
		"\u0513\u0515\bH\1\2\u0514\u050d\3\2\2\2\u0514\u0510\3\2\2\2\u0514\u0513"+
		"\3\2\2\2\u0515\u0516\3\2\2\2\u0516\u0517\7\65\2\2\u0517\u054c\bH\1\2\u0518"+
		"\u051d\7;\2\2\u0519\u051a\5\u009aN\2\u051a\u051b\bH\1\2\u051b\u051e\3"+
		"\2\2\2\u051c\u051e\bH\1\2\u051d\u0519\3\2\2\2\u051d\u051c\3\2\2\2\u051e"+
		"\u051f\3\2\2\2\u051f\u0520\7<\2\2\u0520\u054c\bH\1\2\u0521\u0529\7H\2"+
		"\2\u0522\u0523\5\u0098M\2\u0523\u0524\bH\1\2\u0524\u052a\3\2\2\2\u0525"+
		"\u0526\5\u009aN\2\u0526\u0527\bH\1\2\u0527\u052a\3\2\2\2\u0528\u052a\b"+
		"H\1\2\u0529\u0522\3\2\2\2\u0529\u0525\3\2\2\2\u0529\u0528\3\2\2\2\u052a"+
		"\u052b\3\2\2\2\u052b\u052c\7I\2\2\u052c\u054c\bH\1\2\u052d\u052e\7(\2"+
		"\2\u052e\u054c\bH\1\2\u052f\u0530\7+\2\2\u0530\u054c\bH\1\2\u0531\u0532"+
		"\7,\2\2\u0532\u054c\bH\1\2\u0533\u0534\7-\2\2\u0534\u054c\bH\1\2\u0535"+
		"\u0536\7.\2\2\u0536\u054c\bH\1\2\u0537\u0538\7/\2\2\u0538\u054c\bH\1\2"+
		"\u0539\u053a\7\60\2\2\u053a\u054c\bH\1\2\u053b\u053e\bH\1\2\u053c\u053d"+
		"\7\3\2\2\u053d\u053f\bH\1\2\u053e\u053c\3\2\2\2\u053f\u0540\3\2\2\2\u0540"+
		"\u053e\3\2\2\2\u0540\u0541\3\2\2\2\u0541\u0542\3\2\2\2\u0542\u054c\bH"+
		"\1\2\u0543\u0544\7\62\2\2\u0544\u054c\bH\1\2\u0545\u0546\7\34\2\2\u0546"+
		"\u054c\bH\1\2\u0547\u0548\7\35\2\2\u0548\u054c\bH\1\2\u0549\u054a\7\36"+
		"\2\2\u054a\u054c\bH\1\2\u054b\u050c\3\2\2\2\u054b\u0518\3\2\2\2\u054b"+
		"\u0521\3\2\2\2\u054b\u052d\3\2\2\2\u054b\u052f\3\2\2\2\u054b\u0531\3\2"+
		"\2\2\u054b\u0533\3\2\2\2\u054b\u0535\3\2\2\2\u054b\u0537\3\2\2\2\u054b"+
		"\u0539\3\2\2\2\u054b\u053b\3\2\2\2\u054b\u0543\3\2\2\2\u054b\u0545\3\2"+
		"\2\2\u054b\u0547\3\2\2\2\u054b\u0549\3\2\2\2\u054c\u008f\3\2\2\2\u054d"+
		"\u054e\5\u0092J\2\u054e\u0562\bI\1\2\u054f\u0550\bI\1\2\u0550\u055f\7"+
		"\66\2\2\u0551\u0552\5\u0092J\2\u0552\u0559\bI\1\2\u0553\u0554\7\66\2\2"+
		"\u0554\u0555\5\u0092J\2\u0555\u0556\bI\1\2\u0556\u0558\3\2\2\2\u0557\u0553"+
		"\3\2\2\2\u0558\u055b\3\2\2\2\u0559\u0557\3\2\2\2\u0559\u055a\3\2\2\2\u055a"+
		"\u055d\3\2\2\2\u055b\u0559\3\2\2\2\u055c\u055e\7\66\2\2\u055d\u055c\3"+
		"\2\2\2\u055d\u055e\3\2\2\2\u055e\u0560\3\2\2\2\u055f\u0551\3\2\2\2\u055f"+
		"\u0560\3\2\2\2\u0560\u0561\3\2\2\2\u0561\u0563\bI\1\2\u0562\u054f\3\2"+
		"\2\2\u0562\u0563\3\2\2\2\u0563\u0091\3\2\2\2\u0564\u0565\5h\65\2\u0565"+
		"\u0566\bJ\1\2\u0566\u057d\3\2\2\2\u0567\u056b\bJ\1\2\u0568\u0569\5h\65"+
		"\2\u0569\u056a\bJ\1\2\u056a\u056c\3\2\2\2\u056b\u0568\3\2\2\2\u056b\u056c"+
		"\3\2\2\2\u056c\u056d\3\2\2\2\u056d\u0571\7\67\2\2\u056e\u056f\5h\65\2"+
		"\u056f\u0570\bJ\1\2\u0570\u0572\3\2\2\2\u0571\u056e\3\2\2\2\u0571\u0572"+
		"\3\2\2\2\u0572\u0579\3\2\2\2\u0573\u0577\7\67\2\2\u0574\u0575\5h\65\2"+
		"\u0575\u0576\bJ\1\2\u0576\u0578\3\2\2\2\u0577\u0574\3\2\2\2\u0577\u0578"+
		"\3\2\2\2\u0578\u057a\3\2\2\2\u0579\u0573\3\2\2\2\u0579\u057a\3\2\2\2\u057a"+
		"\u057b\3\2\2\2\u057b\u057d\bJ\1\2\u057c\u0564\3\2\2\2\u057c\u0567\3\2"+
		"\2\2\u057d\u0093\3\2\2\2\u057e\u0585\bK\1\2\u057f\u0580\5|?\2\u0580\u0581"+
		"\bK\1\2\u0581\u0586\3\2\2\2\u0582\u0583\5z>\2\u0583\u0584\bK\1\2\u0584"+
		"\u0586\3\2\2\2\u0585\u057f\3\2\2\2\u0585\u0582\3\2\2\2\u0586\u0592\3\2"+
		"\2\2\u0587\u058e\7\66\2\2\u0588\u0589\5|?\2\u0589\u058a\bK\1\2\u058a\u058f"+
		"\3\2\2\2\u058b\u058c\5z>\2\u058c\u058d\bK\1\2\u058d\u058f\3\2\2\2\u058e"+
		"\u0588\3\2\2\2\u058e\u058b\3\2\2\2\u058f\u0591\3\2\2\2\u0590\u0587\3\2"+
		"\2\2\u0591\u0594\3\2\2\2\u0592\u0590\3\2\2\2\u0592\u0593\3\2\2\2\u0593"+
		"\u0596\3\2\2\2\u0594\u0592\3\2\2\2\u0595\u0597\7\66\2\2\u0596\u0595\3"+
		"\2\2\2\u0596\u0597\3\2\2\2\u0597\u0598\3\2\2\2\u0598\u0599\bK\1\2\u0599"+
		"\u0095\3\2\2\2\u059a\u059b\5h\65\2\u059b\u05af\bL\1\2\u059c\u059d\bL\1"+
		"\2\u059d\u05ac\7\66\2\2\u059e\u059f\5h\65\2\u059f\u05a6\bL\1\2\u05a0\u05a1"+
		"\7\66\2\2\u05a1\u05a2\5h\65\2\u05a2\u05a3\bL\1\2\u05a3\u05a5\3\2\2\2\u05a4"+
		"\u05a0\3\2\2\2\u05a5\u05a8\3\2\2\2\u05a6\u05a4\3\2\2\2\u05a6\u05a7\3\2"+
		"\2\2\u05a7\u05aa\3\2\2\2\u05a8\u05a6\3\2\2\2\u05a9\u05ab\7\66\2\2\u05aa"+
		"\u05a9\3\2\2\2\u05aa\u05ab\3\2\2\2\u05ab\u05ad\3\2\2\2\u05ac\u059e\3\2"+
		"\2\2\u05ac\u05ad\3\2\2\2\u05ad\u05ae\3\2\2\2\u05ae\u05b0\bL\1\2\u05af"+
		"\u059c\3\2\2\2\u05af\u05b0\3\2\2\2\u05b0\u0097\3\2\2\2\u05b1\u05bb\bM"+
		"\1\2\u05b2\u05b3\5h\65\2\u05b3\u05b4\7\67\2\2\u05b4\u05b5\5h\65\2\u05b5"+
		"\u05b6\bM\1\2\u05b6\u05bc\3\2\2\2\u05b7\u05b8\79\2\2\u05b8\u05b9\5|?\2"+
		"\u05b9\u05ba\bM\1\2\u05ba\u05bc\3\2\2\2\u05bb\u05b2\3\2\2\2\u05bb\u05b7"+
		"\3\2\2\2\u05bc\u05bd\3\2\2\2\u05bd\u05be\5\u00a2R\2\u05be\u05bf\bM\1\2"+
		"\u05bf\u05e4\3\2\2\2\u05c0\u05ca\bM\1\2\u05c1\u05c2\5h\65\2\u05c2\u05c3"+
		"\7\67\2\2\u05c3\u05c4\5h\65\2\u05c4\u05c5\bM\1\2\u05c5\u05cb\3\2\2\2\u05c6"+
		"\u05c7\79\2\2\u05c7\u05c8\5|?\2\u05c8\u05c9\bM\1\2\u05c9\u05cb\3\2\2\2"+
		"\u05ca\u05c1\3\2\2\2\u05ca\u05c6\3\2\2\2\u05cb\u05cc\3\2\2\2\u05cc\u05db"+
		"\bM\1\2\u05cd\u05d7\7\66\2\2\u05ce\u05cf\5h\65\2\u05cf\u05d0\7\67\2\2"+
		"\u05d0\u05d1\5h\65\2\u05d1\u05d2\bM\1\2\u05d2\u05d8\3\2\2\2\u05d3\u05d4"+
		"\79\2\2\u05d4\u05d5\5|?\2\u05d5\u05d6\bM\1\2\u05d6\u05d8\3\2\2\2\u05d7"+
		"\u05ce\3\2\2\2\u05d7\u05d3\3\2\2\2\u05d8\u05da\3\2\2\2\u05d9\u05cd\3\2"+
		"\2\2\u05da\u05dd\3\2\2\2\u05db\u05d9\3\2\2\2\u05db\u05dc\3\2\2\2\u05dc"+
		"\u05df\3\2\2\2\u05dd\u05db\3\2\2\2\u05de\u05e0\7\66\2\2\u05df\u05de\3"+
		"\2\2\2\u05df\u05e0\3\2\2\2\u05e0\u05e1\3\2\2\2\u05e1\u05e2\bM\1\2\u05e2"+
		"\u05e4\3\2\2\2\u05e3\u05b1\3\2\2\2\u05e3\u05c0\3\2\2\2\u05e4\u0099\3\2"+
		"\2\2\u05e5\u05ec\bN\1\2\u05e6\u05e7\5h\65\2\u05e7\u05e8\bN\1\2\u05e8\u05ed"+
		"\3\2\2\2\u05e9\u05ea\5z>\2\u05ea\u05eb\bN\1\2\u05eb\u05ed\3\2\2\2\u05ec"+
		"\u05e6\3\2\2\2\u05ec\u05e9\3\2\2\2\u05ed\u05ee\3\2\2\2\u05ee\u05ef\5\u00a2"+
		"R\2\u05ef\u05f0\bN\1\2\u05f0\u0611\3\2\2\2\u05f1\u05f8\bN\1\2\u05f2\u05f3"+
		"\5h\65\2\u05f3\u05f4\bN\1\2\u05f4\u05f9\3\2\2\2\u05f5\u05f6\5z>\2\u05f6"+
		"\u05f7\bN\1\2\u05f7\u05f9\3\2\2\2\u05f8\u05f2\3\2\2\2\u05f8\u05f5\3\2"+
		"\2\2\u05f9\u05fa\3\2\2\2\u05fa\u0606\bN\1\2\u05fb\u0602\7\66\2\2\u05fc"+
		"\u05fd\5h\65\2\u05fd\u05fe\bN\1\2\u05fe\u0603\3\2\2\2\u05ff\u0600\5z>"+
		"\2\u0600\u0601\bN\1\2\u0601\u0603\3\2\2\2\u0602\u05fc\3\2\2\2\u0602\u05ff"+
		"\3\2\2\2\u0603\u0605\3\2\2\2\u0604\u05fb\3\2\2\2\u0605\u0608\3\2\2\2\u0606"+
		"\u0604\3\2\2\2\u0606\u0607\3\2\2\2\u0607\u0609\3\2\2\2\u0608\u0606\3\2"+
		"\2\2\u0609\u060c\bN\1\2\u060a\u060b\7\66\2\2\u060b\u060d\bN\1\2\u060c"+
		"\u060a\3\2\2\2\u060c\u060d\3\2\2\2\u060d\u060e\3\2\2\2\u060e\u060f\bN"+
		"\1\2\u060f\u0611\3\2\2\2\u0610\u05e5\3\2\2\2\u0610\u05f1\3\2\2\2\u0611"+
		"\u009b\3\2\2\2\u0612\u0613\7\37\2\2\u0613\u0614\7(\2\2\u0614\u061a\bO"+
		"\1\2\u0615\u0616\7\64\2\2\u0616\u0617\5\u009eP\2\u0617\u0618\7\65\2\2"+
		"\u0618\u0619\bO\1\2\u0619\u061b\3\2\2\2\u061a\u0615\3\2\2\2\u061a\u061b"+
		"\3\2\2\2\u061b\u061c\3\2\2\2\u061c\u061d\bO\1\2\u061d\u061e\bO\1\2\u061e"+
		"\u061f\7\67\2\2\u061f\u0620\5f\64\2\u0620\u0621\bO\1\2\u0621\u0622\bO"+
		"\1\2\u0622\u0623\bO\1\2\u0623\u009d\3\2\2\2\u0624\u0630\bP\1\2\u0625\u062a"+
		"\5\u00a0Q\2\u0626\u0627\7\66\2\2\u0627\u0629\5\u00a0Q\2\u0628\u0626\3"+
		"\2\2\2\u0629\u062c\3\2\2\2\u062a\u0628\3\2\2\2\u062a\u062b\3\2\2\2\u062b"+
		"\u062e\3\2\2\2\u062c\u062a\3\2\2\2\u062d\u062f\7\66\2\2\u062e\u062d\3"+
		"\2\2\2\u062e\u062f\3\2\2\2\u062f\u0631\3\2\2\2\u0630\u0625\3\2\2\2\u0630"+
		"\u0631\3\2\2\2\u0631\u0632\3\2\2\2\u0632\u0633\bP\1\2\u0633\u009f\3\2"+
		"\2\2\u0634\u0635\bQ\1\2\u0635\u0636\5h\65\2\u0636\u0637\5\u00a2R\2\u0637"+
		"\u0638\bQ\1\2\u0638\u064c\3\2\2\2\u0639\u063a\bQ\1\2\u063a\u063b\5h\65"+
		"\2\u063b\u063c\bQ\1\2\u063c\u063d\7:\2\2\u063d\u063e\5h\65\2\u063e\u063f"+
		"\bQ\1\2\u063f\u064c\3\2\2\2\u0640\u0641\5h\65\2\u0641\u0642\bQ\1\2\u0642"+
		"\u064c\3\2\2\2\u0643\u0644\79\2\2\u0644\u0645\5h\65\2\u0645\u0646\bQ\1"+
		"\2\u0646\u064c\3\2\2\2\u0647\u0648\7\63\2\2\u0648\u0649\5h\65\2\u0649"+
		"\u064a\bQ\1\2\u064a\u064c\3\2\2\2\u064b\u0634\3\2\2\2\u064b\u0639\3\2"+
		"\2\2\u064b\u0640\3\2\2\2\u064b\u0643\3\2\2\2\u064b\u0647\3\2\2\2\u064c"+
		"\u00a1\3\2\2\2\u064d\u0650\bR\1\2\u064e\u064f\7%\2\2\u064f\u0651\bR\1"+
		"\2\u0650\u064e\3\2\2\2\u0650\u0651\3\2\2\2\u0651\u0652\3\2\2\2\u0652\u0653"+
		"\bR\1\2\u0653\u0654\7\21\2\2\u0654\u0655\5\u0094K\2\u0655\u0656\7\22\2"+
		"\2\u0656\u0657\bR\1\2\u0657\u0658\5p9\2\u0658\u0659\bR\1\2\u0659\u0660"+
		"\bR\1\2\u065a\u065b\7\r\2\2\u065b\u065c\5j\66\2\u065c\u065d\bR\1\2\u065d"+
		"\u065f\3\2\2\2\u065e\u065a\3\2\2\2\u065f\u0662\3\2\2\2\u0660\u065e\3\2"+
		"\2\2\u0660\u0661\3\2\2\2\u0661\u0663\3\2\2\2\u0662\u0660\3\2\2\2\u0663"+
		"\u0667\bR\1\2\u0664\u0665\5\u00a2R\2\u0665\u0666\bR\1\2\u0666\u0668\3"+
		"\2\2\2\u0667\u0664\3\2\2\2\u0667\u0668\3\2\2\2\u0668\u0669\3\2\2\2\u0669"+
		"\u066a\bR\1\2\u066a\u00a3\3\2\2\2\u066b\u066c\7(\2\2\u066c\u00a5\3\2\2"+
		"\2\u066d\u066e\bT\1\2\u066e\u0676\7 \2\2\u066f\u0670\7\7\2\2\u0670\u0671"+
		"\5h\65\2\u0671\u0672\bT\1\2\u0672\u0677\3\2\2\2\u0673\u0674\5.\30\2\u0674"+
		"\u0675\bT\1\2\u0675\u0677\3\2\2\2\u0676\u066f\3\2\2\2\u0676\u0673\3\2"+
		"\2\2\u0676\u0677\3\2\2\2\u0677\u0678\3\2\2\2\u0678\u0679\bT\1\2\u0679"+
		"\u00a7\3\2\2\2\u00c3\u00ac\u00b1\u00b6\u00c1\u00c5\u00c7\u00d2\u00d6\u00d8"+
		"\u00e1\u00e7\u00f6\u00ff\u0108\u0114\u011e\u0128\u0134\u0137\u013f\u0145"+
		"\u0147\u0149\u014d\u014f\u0151\u0158\u0161\u0167\u0169\u016b\u016f\u0171"+
		"\u0173\u017a\u0180\u0182\u0184\u0188\u018a\u0194\u019a\u01a6\u01a8\u01b3"+
		"\u01bd\u01c9\u01cc\u01d4\u01da\u01dc\u01de\u01e2\u01e4\u01e6\u01ed\u01f6"+
		"\u01fc\u01fe\u0200\u0204\u0206\u0208\u020f\u0215\u0217\u0219\u021d\u021f"+
		"\u022a\u0232\u023c\u0243\u0247\u0254\u025e\u0269\u0277\u027b\u027f\u0287"+
		"\u0291\u029a\u029e\u02a2\u02a4\u02a7\u02b6\u02bd\u02cc\u02ce\u02d4\u02df"+
		"\u02e1\u02eb\u02ed\u02ef\u02fc\u0305\u0312\u0316\u0321\u0329\u0330\u033c"+
		"\u034a\u0356\u0363\u0369\u037f\u038c\u039d\u03ab\u03b2\u03b9\u03c0\u03cb"+
		"\u03cd\u03dd\u03e8\u03f3\u03f7\u0403\u0408\u0410\u0417\u0424\u0435\u043a"+
		"\u0445\u044a\u0453\u045d\u0462\u047c\u048a\u0495\u04a0\u04aa\u04b1\u04bb"+
		"\u04c2\u04d2\u04d9\u04e3\u04eb\u04f3\u04f6\u0507\u0509\u0514\u051d\u0529"+
		"\u0540\u054b\u0559\u055d\u055f\u0562\u056b\u0571\u0577\u0579\u057c\u0585"+
		"\u058e\u0592\u0596\u05a6\u05aa\u05ac\u05af\u05bb\u05ca\u05d7\u05db\u05df"+
		"\u05e3\u05ec\u05f8\u0602\u0606\u060c\u0610\u061a\u062a\u062e\u0630\u064b"+
		"\u0650\u0660\u0667\u0676";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
