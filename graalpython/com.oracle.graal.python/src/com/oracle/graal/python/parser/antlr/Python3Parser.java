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
// Generated from /home/tim/Dev/graalpython/graalpython/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/parser/antlr/Python3.g4 by ANTLR 4.7.2
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
		RIGHT_SHIFT_ASSIGN=91, POWER_ASSIGN=92, IDIV_ASSIGN=93, SKIP_=94, UNKNOWN_CHAR=95, 
		INDENT=96, DEDENT=97;
	public static final int
		RULE_single_input = 0, RULE_file_input = 1, RULE_eval_input = 2, RULE_decorator = 3, 
		RULE_decorators = 4, RULE_decorated = 5, RULE_async_funcdef = 6, RULE_funcdef = 7, 
		RULE_parameters = 8, RULE_typedargslist = 9, RULE_defparameter = 10, RULE_splatparameter = 11, 
		RULE_kwargsparameter = 12, RULE_varargslist = 13, RULE_vdefparameter = 14, 
		RULE_vsplatparameter = 15, RULE_vkwargsparameter = 16, RULE_stmt = 17, 
		RULE_simple_stmt = 18, RULE_small_stmt = 19, RULE_expr_stmt = 20, RULE_testlist_star_expr = 21, 
		RULE_augassign = 22, RULE_del_stmt = 23, RULE_flow_stmt = 24, RULE_return_stmt = 25, 
		RULE_yield_stmt = 26, RULE_raise_stmt = 27, RULE_import_stmt = 28, RULE_import_name = 29, 
		RULE_import_from = 30, RULE_import_as_name = 31, RULE_import_as_names = 32, 
		RULE_dotted_name = 33, RULE_dotted_as_name = 34, RULE_dotted_as_names = 35, 
		RULE_global_stmt = 36, RULE_nonlocal_stmt = 37, RULE_assert_stmt = 38, 
		RULE_compound_stmt = 39, RULE_async_stmt = 40, RULE_if_stmt = 41, RULE_elif_stmt = 42, 
		RULE_while_stmt = 43, RULE_for_stmt = 44, RULE_try_stmt = 45, RULE_except_clause = 46, 
		RULE_with_stmt = 47, RULE_with_item = 48, RULE_suite = 49, RULE_test = 50, 
		RULE_test_nocond = 51, RULE_lambdef = 52, RULE_lambdef_nocond = 53, RULE_or_test = 54, 
		RULE_and_test = 55, RULE_not_test = 56, RULE_comparison = 57, RULE_comp_op = 58, 
		RULE_star_expr = 59, RULE_expr = 60, RULE_xor_expr = 61, RULE_and_expr = 62, 
		RULE_shift_expr = 63, RULE_arith_expr = 64, RULE_term = 65, RULE_factor = 66, 
		RULE_power = 67, RULE_atom_expr = 68, RULE_atom = 69, RULE_subscriptlist = 70, 
		RULE_subscript = 71, RULE_exprlist = 72, RULE_testlist = 73, RULE_dictmaker = 74, 
		RULE_setlisttuplemaker = 75, RULE_classdef = 76, RULE_arglist = 77, RULE_argument = 78, 
		RULE_comp_for = 79, RULE_encoding_decl = 80, RULE_yield_expr = 81;
	private static String[] makeRuleNames() {
		return new String[] {
			"single_input", "file_input", "eval_input", "decorator", "decorators", 
			"decorated", "async_funcdef", "funcdef", "parameters", "typedargslist", 
			"defparameter", "splatparameter", "kwargsparameter", "varargslist", "vdefparameter", 
			"vsplatparameter", "vkwargsparameter", "stmt", "simple_stmt", "small_stmt", 
			"expr_stmt", "testlist_star_expr", "augassign", "del_stmt", "flow_stmt", 
			"return_stmt", "yield_stmt", "raise_stmt", "import_stmt", "import_name", 
			"import_from", "import_as_name", "import_as_names", "dotted_name", "dotted_as_name", 
			"dotted_as_names", "global_stmt", "nonlocal_stmt", "assert_stmt", "compound_stmt", 
			"async_stmt", "if_stmt", "elif_stmt", "while_stmt", "for_stmt", "try_stmt", 
			"except_clause", "with_stmt", "with_item", "suite", "test", "test_nocond", 
			"lambdef", "lambdef_nocond", "or_test", "and_test", "not_test", "comparison", 
			"comp_op", "star_expr", "expr", "xor_expr", "and_expr", "shift_expr", 
			"arith_expr", "term", "factor", "power", "atom_expr", "atom", "subscriptlist", 
			"subscript", "exprlist", "testlist", "dictmaker", "setlisttuplemaker", 
			"classdef", "arglist", "argument", "comp_for", "encoding_decl", "yield_expr"
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
			"'|='", "'^='", "'<<='", "'>>='", "'**='", "'//='"
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
			"RIGHT_SHIFT_ASSIGN", "POWER_ASSIGN", "IDIV_ASSIGN", "SKIP_", "UNKNOWN_CHAR", 
			"INDENT", "DEDENT"
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


		private PythonSSTNodeFactory factory;
		private ScopeEnvironment scopeEnvironment;
		boolean containsBreak;
		boolean containsContinue;
		
		public final boolean startLoopBreak() {
			try {
				return containsBreak;
			} finally {
				containsBreak = false;
			}
		}
		
		public final boolean startLoopContinue() {
			try {
				return containsContinue;
			} finally {
				containsContinue = false;
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
		public TerminalNode NEWLINE() { return getToken(Python3Parser.NEWLINE, 0); }
		public Simple_stmtContext simple_stmt() {
			return getRuleContext(Simple_stmtContext.class,0);
		}
		public Compound_stmtContext compound_stmt() {
			return getRuleContext(Compound_stmtContext.class,0);
		}
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
		try {
			enterOuterAlt(_localctx, 1);
			{

				    if (!_localctx.interactive && _localctx.curInlineLocals != null) {
			                ScopeInfo functionScope = scopeEnvironment.pushScope("<single_input>", ScopeInfo.ScopeKind.Function, _localctx.curInlineLocals);
			                functionScope.setHasAnnotations(true);
			            } else {
			                scopeEnvironment.pushScope(_localctx.toString(), ScopeInfo.ScopeKind.Module);
			            }
				
			 int start = start(); 
			setState(171);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NEWLINE:
				{
				setState(166);
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
				setState(167);
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
				setState(168);
				compound_stmt();
				setState(169);
				match(NEWLINE);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
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
			 int start = start(); 
			setState(182);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << DEF) | (1L << RETURN) | (1L << RAISE) | (1L << FROM) | (1L << IMPORT) | (1L << GLOBAL) | (1L << NONLOCAL) | (1L << ASSERT) | (1L << IF) | (1L << WHILE) | (1L << FOR) | (1L << TRY) | (1L << WITH) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << CLASS) | (1L << YIELD) | (1L << DEL) | (1L << PASS) | (1L << CONTINUE) | (1L << BREAK) | (1L << ASYNC) | (1L << AWAIT) | (1L << NEWLINE) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)) | (1L << (AT - 64)))) != 0)) {
				{
				setState(180);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case NEWLINE:
					{
					setState(178);
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
					setState(179);
					_localctx.stmt = stmt();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(184);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(185);
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

	public static class Eval_inputContext extends ParserRuleContext {
		public SSTNode result;
		public com.oracle.graal.python.parser.ScopeInfo scope;
		public TestlistContext testlist;
		public TestlistContext testlist() {
			return getRuleContext(TestlistContext.class,0);
		}
		public TerminalNode EOF() { return getToken(Python3Parser.EOF, 0); }
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
		enterRule(_localctx, 4, RULE_eval_input);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 scopeEnvironment.pushScope(_localctx.toString(), ScopeInfo.ScopeKind.Module); 
			setState(190);
			_localctx.testlist = testlist();
			setState(194);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NEWLINE) {
				{
				{
				setState(191);
				match(NEWLINE);
				}
				}
				setState(196);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(197);
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
		enterRule(_localctx, 6, RULE_decorator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 ArgListBuilder args = null ;
			setState(202);
			match(AT);
			setState(203);
			_localctx.dotted_name = dotted_name();
			setState(209);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN_PAREN) {
				{
				setState(204);
				match(OPEN_PAREN);
				setState(205);
				_localctx.arglist = arglist();
				setState(206);
				match(CLOSE_PAREN);
				args = _localctx.arglist.result; 
				}
			}

			setState(211);
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
		enterRule(_localctx, 8, RULE_decorators);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			int start = start();
			setState(216); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(215);
				decorator();
				}
				}
				setState(218); 
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
		enterRule(_localctx, 10, RULE_decorated);
		try {
			enterOuterAlt(_localctx, 1);
			{
			 SSTNode decor; 
			setState(223);
			_localctx.decorators = decorators();
			setState(227);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CLASS:
				{
				setState(224);
				classdef();
				}
				break;
			case DEF:
				{
				setState(225);
				funcdef();
				}
				break;
			case ASYNC:
				{
				setState(226);
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
		enterRule(_localctx, 12, RULE_async_funcdef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(231);
			match(ASYNC);
			setState(232);
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
		enterRule(_localctx, 14, RULE_funcdef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(234);
			match(DEF);
			setState(235);
			_localctx.n = match(NAME);
			setState(236);
			_localctx.parameters = parameters();
			setState(239);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ARROW) {
				{
				setState(237);
				match(ARROW);
				setState(238);
				test();
				}
			}

			setState(241);
			match(COLON);
			 
			            String name = _localctx.n.getText(); 
			            ScopeInfo enclosingScope = scopeEnvironment.getCurrentScope();
			            String enclosingClassName = enclosingScope.isInClassScope() ? enclosingScope.getScopeId() : null;
			            ScopeInfo functionScope = scopeEnvironment.pushScope(name, ScopeInfo.ScopeKind.Function);
			            functionScope.setHasAnnotations(true);
			            _localctx.parameters.result.defineParamsInScope(functionScope); 
			        
			setState(243);
			_localctx.s = suite();
			 
			            SSTNode funcDef = new FunctionDefSSTNode(scopeEnvironment.getCurrentScope(), name, enclosingClassName, _localctx.parameters.result, _localctx.s.result, getStartIndex(_localctx), getStopIndex(_localctx.s));
			           scopeEnvironment.popScope();
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
		enterRule(_localctx, 16, RULE_parameters);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 ArgDefListBuilder args = new ArgDefListBuilder(factory.getScopeEnvironment()); 
			setState(247);
			match(OPEN_PAREN);
			setState(249);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(248);
				typedargslist(args);
				}
			}

			setState(251);
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
		enterRule(_localctx, 18, RULE_typedargslist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(359);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,33,_ctx) ) {
			case 1:
				{
				setState(254);
				defparameter(args);
				setState(259);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(255);
						match(COMMA);
						setState(256);
						defparameter(args);
						}
						} 
					}
					setState(261);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
				}
				setState(262);
				match(COMMA);
				setState(263);
				match(DIV);
				args.markPositionalOnlyIndex();
				setState(302);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
				case 1:
					{
					setState(274);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
					case 1:
						{
						setState(265);
						match(COMMA);
						setState(266);
						defparameter(args);
						setState(271);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(267);
								match(COMMA);
								setState(268);
								defparameter(args);
								}
								} 
							}
							setState(273);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
						}
						}
						break;
					}
					setState(300);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(276);
						match(COMMA);
						setState(298);
						_errHandler.sync(this);
						switch (_input.LA(1)) {
						case STAR:
							{
							setState(277);
							splatparameter(args);
							setState(282);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
							while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
								if ( _alt==1 ) {
									{
									{
									setState(278);
									match(COMMA);
									setState(279);
									defparameter(args);
									}
									} 
								}
								setState(284);
								_errHandler.sync(this);
								_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
							}
							setState(292);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(285);
								match(COMMA);
								setState(290);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==POWER) {
									{
									setState(286);
									kwargsparameter(args);
									setState(288);
									_errHandler.sync(this);
									_la = _input.LA(1);
									if (_la==COMMA) {
										{
										setState(287);
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
							setState(294);
							kwargsparameter(args);
							setState(296);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(295);
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
				}
				}
				break;
			case 2:
				{
				setState(304);
				defparameter(args);
				setState(309);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,20,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(305);
						match(COMMA);
						setState(306);
						defparameter(args);
						}
						} 
					}
					setState(311);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,20,_ctx);
				}
				setState(336);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(312);
					match(COMMA);
					setState(334);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(313);
						splatparameter(args);
						setState(318);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,21,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(314);
								match(COMMA);
								setState(315);
								defparameter(args);
								}
								} 
							}
							setState(320);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,21,_ctx);
						}
						setState(328);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(321);
							match(COMMA);
							setState(326);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(322);
								kwargsparameter(args);
								setState(324);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(323);
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
						setState(330);
						kwargsparameter(args);
						setState(332);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(331);
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
				setState(338);
				splatparameter(args);
				setState(343);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,28,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(339);
						match(COMMA);
						setState(340);
						defparameter(args);
						}
						} 
					}
					setState(345);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,28,_ctx);
				}
				setState(353);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(346);
					match(COMMA);
					setState(351);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==POWER) {
						{
						setState(347);
						kwargsparameter(args);
						setState(349);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(348);
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
		enterRule(_localctx, 20, RULE_defparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(361);
			_localctx.NAME = match(NAME);
			 SSTNode type = null; SSTNode defValue = null; 
			setState(367);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(363);
				match(COLON);
				setState(364);
				_localctx.test = test();
				 type = _localctx.test.result; 
				}
			}

			setState(373);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(369);
				match(ASSIGN);
				setState(370);
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
		enterRule(_localctx, 22, RULE_splatparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(377);
			match(STAR);
			 String name = null; SSTNode type = null; 
			setState(387);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(379);
				_localctx.NAME = match(NAME);
				 name = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				setState(385);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COLON) {
					{
					setState(381);
					match(COLON);
					setState(382);
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
		enterRule(_localctx, 24, RULE_kwargsparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(391);
			match(POWER);
			setState(392);
			_localctx.NAME = match(NAME);
			 SSTNode type = null; 
			setState(398);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(394);
				match(COLON);
				setState(395);
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
		enterRule(_localctx, 26, RULE_varargslist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 ArgDefListBuilder args = new ArgDefListBuilder(factory.getScopeEnvironment()); 
			setState(508);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,63,_ctx) ) {
			case 1:
				{
				setState(403);
				vdefparameter(args);
				setState(408);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,39,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(404);
						match(COMMA);
						setState(405);
						vdefparameter(args);
						}
						} 
					}
					setState(410);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,39,_ctx);
				}
				setState(411);
				match(COMMA);
				setState(412);
				match(DIV);
				args.markPositionalOnlyIndex();
				setState(451);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,49,_ctx) ) {
				case 1:
					{
					setState(423);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,41,_ctx) ) {
					case 1:
						{
						setState(414);
						match(COMMA);
						setState(415);
						vdefparameter(args);
						setState(420);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(416);
								match(COMMA);
								setState(417);
								vdefparameter(args);
								}
								} 
							}
							setState(422);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
						}
						}
						break;
					}
					setState(449);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(425);
						match(COMMA);
						setState(447);
						_errHandler.sync(this);
						switch (_input.LA(1)) {
						case STAR:
							{
							setState(426);
							vsplatparameter(args);
							setState(431);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,42,_ctx);
							while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
								if ( _alt==1 ) {
									{
									{
									setState(427);
									match(COMMA);
									setState(428);
									vdefparameter(args);
									}
									} 
								}
								setState(433);
								_errHandler.sync(this);
								_alt = getInterpreter().adaptivePredict(_input,42,_ctx);
							}
							setState(441);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(434);
								match(COMMA);
								setState(439);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==POWER) {
									{
									setState(435);
									vkwargsparameter(args);
									setState(437);
									_errHandler.sync(this);
									_la = _input.LA(1);
									if (_la==COMMA) {
										{
										setState(436);
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
							setState(443);
							vkwargsparameter(args);
							setState(445);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(444);
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
				}
				}
				break;
			case 2:
				{
				setState(453);
				vdefparameter(args);
				setState(458);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,50,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(454);
						match(COMMA);
						setState(455);
						vdefparameter(args);
						}
						} 
					}
					setState(460);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,50,_ctx);
				}
				setState(485);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(461);
					match(COMMA);
					setState(483);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(462);
						vsplatparameter(args);
						setState(467);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,51,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(463);
								match(COMMA);
								setState(464);
								vdefparameter(args);
								}
								} 
							}
							setState(469);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,51,_ctx);
						}
						setState(477);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(470);
							match(COMMA);
							setState(475);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(471);
								vkwargsparameter(args);
								setState(473);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(472);
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
						setState(479);
						vkwargsparameter(args);
						setState(481);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(480);
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
				setState(487);
				vsplatparameter(args);
				setState(492);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,58,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(488);
						match(COMMA);
						setState(489);
						vdefparameter(args);
						}
						} 
					}
					setState(494);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,58,_ctx);
				}
				setState(502);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(495);
					match(COMMA);
					setState(500);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==POWER) {
						{
						setState(496);
						vkwargsparameter(args);
						setState(498);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(497);
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
				break;
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
		enterRule(_localctx, 28, RULE_vdefparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(512);
			_localctx.NAME = match(NAME);
			 SSTNode defValue = null; 
			setState(518);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(514);
				match(ASSIGN);
				setState(515);
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
		enterRule(_localctx, 30, RULE_vsplatparameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(522);
			match(STAR);
			 String name = null; 
			setState(526);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(524);
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
		enterRule(_localctx, 32, RULE_vkwargsparameter);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(530);
			match(POWER);
			setState(531);
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
		enterRule(_localctx, 34, RULE_stmt);
		try {
			setState(536);
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
				setState(534);
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
				setState(535);
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
		enterRule(_localctx, 36, RULE_simple_stmt);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(538);
			small_stmt();
			setState(543);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,67,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(539);
					match(SEMI_COLON);
					setState(540);
					small_stmt();
					}
					} 
				}
				setState(545);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,67,_ctx);
			}
			setState(547);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEMI_COLON) {
				{
				setState(546);
				match(SEMI_COLON);
				}
			}

			setState(549);
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
		enterRule(_localctx, 38, RULE_small_stmt);
		try {
			setState(560);
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
				setState(551);
				expr_stmt();
				}
				break;
			case DEL:
				enterOuterAlt(_localctx, 2);
				{
				setState(552);
				del_stmt();
				}
				break;
			case PASS:
				enterOuterAlt(_localctx, 3);
				{
				setState(553);
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
				setState(555);
				flow_stmt();
				}
				break;
			case FROM:
			case IMPORT:
				enterOuterAlt(_localctx, 5);
				{
				setState(556);
				import_stmt();
				}
				break;
			case GLOBAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(557);
				global_stmt();
				}
				break;
			case NONLOCAL:
				enterOuterAlt(_localctx, 7);
				{
				setState(558);
				nonlocal_stmt();
				}
				break;
			case ASSERT:
				enterOuterAlt(_localctx, 8);
				{
				setState(559);
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
		enterRule(_localctx, 40, RULE_expr_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(562);
			_localctx.lhs = _localctx.testlist_star_expr = testlist_star_expr();
			 SSTNode rhs = null; 
			          int rhsStopIndex = 0;
			        
			setState(603);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(564);
				match(COLON);
				setState(565);
				_localctx.t = _localctx.test = test();
				setState(570);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ASSIGN) {
					{
					setState(566);
					match(ASSIGN);
					setState(567);
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
				setState(574);
				_localctx.augassign = augassign();
				setState(581);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case YIELD:
					{
					setState(575);
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
					setState(578);
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
				setState(599);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==ASSIGN) {
					{
					{
					setState(587);
					match(ASSIGN);
					 push(value); 
					setState(595);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case YIELD:
						{
						setState(589);
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
						setState(592);
						_localctx.testlist_star_expr = testlist_star_expr();
						 value = _localctx.testlist_star_expr.result; rhsStopIndex = getStopIndex((_localctx.testlist_star_expr!=null?(_localctx.testlist_star_expr.stop):null));
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					}
					setState(601);
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
		enterRule(_localctx, 42, RULE_testlist_star_expr);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(611);
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
				setState(605);
				_localctx.test = test();
				 _localctx.result =  _localctx.test.result; 
				}
				break;
			case STAR:
				{
				setState(608);
				_localctx.star_expr = star_expr();
				  _localctx.result =  _localctx.star_expr.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(643);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 
				                    int start = start(); 
				                    push(_localctx.result);
				                
				setState(614);
				match(COMMA);
				setState(640);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(621);
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
						setState(615);
						_localctx.test = test();
						 push(_localctx.test.result); 
						}
						break;
					case STAR:
						{
						setState(618);
						_localctx.star_expr = star_expr();
						 push(_localctx.star_expr.result); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(634);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,78,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(623);
							match(COMMA);
							setState(630);
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
								setState(624);
								_localctx.test = test();
								 push(_localctx.test.result); 
								}
								break;
							case STAR:
								{
								setState(627);
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
						setState(636);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,78,_ctx);
					}
					setState(638);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(637);
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
		enterRule(_localctx, 44, RULE_augassign);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(645);
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
		enterRule(_localctx, 46, RULE_del_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(647);
			match(DEL);
			setState(648);
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
		enterRule(_localctx, 48, RULE_flow_stmt);
		try {
			setState(658);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BREAK:
				enterOuterAlt(_localctx, 1);
				{
				setState(651);
				_localctx.b = match(BREAK);
				 
				            push(new SimpleSSTNode(SimpleSSTNode.Type.BREAK, getStartIndex(_localctx.b), getStopIndex(_localctx.b)));
				            containsBreak = true; 
				        
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(653);
				_localctx.c = match(CONTINUE);
				 
				            push(new SimpleSSTNode(SimpleSSTNode.Type.CONTINUE, getStartIndex(_localctx.c), getStopIndex(_localctx.c)));
				            containsContinue = true; 
				        
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 3);
				{
				setState(655);
				return_stmt();
				}
				break;
			case RAISE:
				enterOuterAlt(_localctx, 4);
				{
				setState(656);
				raise_stmt();
				}
				break;
			case YIELD:
				enterOuterAlt(_localctx, 5);
				{
				setState(657);
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
		public TestlistContext testlist;
		public TerminalNode RETURN() { return getToken(Python3Parser.RETURN, 0); }
		public TestlistContext testlist() {
			return getRuleContext(TestlistContext.class,0);
		}
		public Return_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_return_stmt; }
	}

	public final Return_stmtContext return_stmt() throws RecognitionException {
		Return_stmtContext _localctx = new Return_stmtContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_return_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(660);
			match(RETURN);
			 SSTNode value = null; 
			setState(665);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(662);
				_localctx.testlist = testlist();
				 value = _localctx.testlist.result; 
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
		enterRule(_localctx, 52, RULE_yield_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(669);
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
		enterRule(_localctx, 54, RULE_raise_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 SSTNode value = null; SSTNode from = null; 
			setState(673);
			match(RAISE);
			setState(682);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(674);
				_localctx.test = test();
				 value = _localctx.test.result; 
				setState(680);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FROM) {
					{
					setState(676);
					match(FROM);
					setState(677);
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
		enterRule(_localctx, 56, RULE_import_stmt);
		try {
			setState(688);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IMPORT:
				enterOuterAlt(_localctx, 1);
				{
				setState(686);
				import_name();
				}
				break;
			case FROM:
				enterOuterAlt(_localctx, 2);
				{
				setState(687);
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
		enterRule(_localctx, 58, RULE_import_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(690);
			match(IMPORT);
			setState(691);
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
		enterRule(_localctx, 60, RULE_import_from);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(693);
			match(FROM);
			 String name = ""; 
			setState(715);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,91,_ctx) ) {
			case 1:
				{
				setState(701);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==DOT || _la==ELLIPSIS) {
					{
					setState(699);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case DOT:
						{
						setState(695);
						match(DOT);
						 name += '.'; 
						}
						break;
					case ELLIPSIS:
						{
						setState(697);
						match(ELLIPSIS);
						 name += "..."; 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					setState(703);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(704);
				_localctx.dotted_name = dotted_name();
				 name += _localctx.dotted_name.result; 
				}
				break;
			case 2:
				{
				setState(711); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					setState(711);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case DOT:
						{
						setState(707);
						match(DOT);
						 name += '.'; 
						}
						break;
					case ELLIPSIS:
						{
						setState(709);
						match(ELLIPSIS);
						 name += "..."; 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					setState(713); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==DOT || _la==ELLIPSIS );
				}
				break;
			}
			setState(717);
			match(IMPORT);
			 String[][] asNames = null; 
			setState(728);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STAR:
				{
				setState(719);
				match(STAR);
				}
				break;
			case OPEN_PAREN:
				{
				setState(720);
				match(OPEN_PAREN);
				setState(721);
				_localctx.import_as_names = import_as_names();
				 asNames = _localctx.import_as_names.result; 
				setState(723);
				match(CLOSE_PAREN);
				}
				break;
			case NAME:
				{
				setState(725);
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
		enterRule(_localctx, 62, RULE_import_as_name);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(732);
			_localctx.n = match(NAME);
			 String asName = null; 
			setState(737);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(734);
				match(AS);
				setState(735);
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
		enterRule(_localctx, 64, RULE_import_as_names);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 int start = start(); 
			setState(742);
			_localctx.import_as_name = import_as_name();
			 push(_localctx.import_as_name.result); 
			setState(750);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,94,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(744);
					match(COMMA);
					setState(745);
					_localctx.import_as_name = import_as_name();
					 push(_localctx.import_as_name.result); 
					}
					} 
				}
				setState(752);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,94,_ctx);
			}
			setState(754);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(753);
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
		enterRule(_localctx, 66, RULE_dotted_name);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(758);
			_localctx.NAME = match(NAME);
			 _localctx.result =  (_localctx.NAME!=null?_localctx.NAME.getText():null); 
			setState(765);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(760);
				match(DOT);
				setState(761);
				_localctx.NAME = match(NAME);
				 _localctx.result =  _localctx.result + "." + (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				}
				}
				setState(767);
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
		enterRule(_localctx, 68, RULE_dotted_as_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(768);
			_localctx.dotted_name = dotted_name();
			setState(773);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AS:
				{
				setState(769);
				match(AS);
				setState(770);
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
		enterRule(_localctx, 70, RULE_dotted_as_names);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(775);
			dotted_as_name();
			setState(780);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(776);
				match(COMMA);
				setState(777);
				dotted_as_name();
				}
				}
				setState(782);
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
		enterRule(_localctx, 72, RULE_global_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 int start = stringStart(); 
			setState(784);
			match(GLOBAL);
			setState(785);
			_localctx.NAME = match(NAME);
			 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
			setState(792);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(787);
				match(COMMA);
				setState(788);
				_localctx.NAME = match(NAME);
				 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
				}
				}
				setState(794);
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
		enterRule(_localctx, 74, RULE_nonlocal_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 int start = stringStart(); 
			setState(798);
			match(NONLOCAL);
			setState(799);
			_localctx.NAME = match(NAME);
			 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
			setState(806);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(801);
				match(COMMA);
				setState(802);
				_localctx.NAME = match(NAME);
				 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
				}
				}
				setState(808);
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
		enterRule(_localctx, 76, RULE_assert_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(811);
			match(ASSERT);
			setState(812);
			_localctx.e = _localctx.test = test();
			 SSTNode message = null; 
			setState(818);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(814);
				match(COMMA);
				setState(815);
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
		enterRule(_localctx, 78, RULE_compound_stmt);
		try {
			setState(831);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IF:
				enterOuterAlt(_localctx, 1);
				{
				setState(822);
				if_stmt();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 2);
				{
				setState(823);
				while_stmt();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 3);
				{
				setState(824);
				for_stmt();
				}
				break;
			case TRY:
				enterOuterAlt(_localctx, 4);
				{
				setState(825);
				try_stmt();
				}
				break;
			case WITH:
				enterOuterAlt(_localctx, 5);
				{
				setState(826);
				with_stmt();
				}
				break;
			case DEF:
				enterOuterAlt(_localctx, 6);
				{
				setState(827);
				funcdef();
				}
				break;
			case CLASS:
				enterOuterAlt(_localctx, 7);
				{
				setState(828);
				classdef();
				}
				break;
			case AT:
				enterOuterAlt(_localctx, 8);
				{
				setState(829);
				decorated();
				}
				break;
			case ASYNC:
				enterOuterAlt(_localctx, 9);
				{
				setState(830);
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
		enterRule(_localctx, 80, RULE_async_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(833);
			match(ASYNC);
			setState(837);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEF:
				{
				setState(834);
				funcdef();
				}
				break;
			case WITH:
				{
				setState(835);
				with_stmt();
				}
				break;
			case FOR:
				{
				setState(836);
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
		enterRule(_localctx, 82, RULE_if_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(839);
			match(IF);
			setState(840);
			_localctx.if_test = test();
			setState(841);
			match(COLON);
			setState(842);
			_localctx.if_suite = suite();
			setState(843);
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
		enterRule(_localctx, 84, RULE_elif_stmt);
		try {
			setState(859);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ELIF:
				enterOuterAlt(_localctx, 1);
				{
				setState(846);
				match(ELIF);
				setState(847);
				_localctx.test = test();
				setState(848);
				match(COLON);
				setState(849);
				_localctx.suite = suite();
				setState(850);
				_localctx.elif_stmt = elif_stmt();
				 _localctx.result =  new IfSSTNode(_localctx.test.result, _localctx.suite.result, _localctx.elif_stmt.result, -1, -1); 
				}
				break;
			case ELSE:
				enterOuterAlt(_localctx, 2);
				{
				setState(853);
				match(ELSE);
				setState(854);
				match(COLON);
				setState(855);
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
		enterRule(_localctx, 86, RULE_while_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(861);
			match(WHILE);
			setState(862);
			_localctx.test = test();
			setState(863);
			match(COLON);
			 boolean bFlag = startLoopBreak(); boolean cFlag = startLoopContinue(); 
			setState(865);
			_localctx.suite = suite();
			 
			            WhileSSTNode result = new WhileSSTNode(_localctx.test.result, _localctx.suite.result, containsContinue, containsBreak, getStartIndex(_localctx),getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null))); 
			            containsContinue = cFlag;
			            containsBreak = bFlag;
			        
			setState(872);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(867);
				match(ELSE);
				setState(868);
				match(COLON);
				setState(869);
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
		enterRule(_localctx, 88, RULE_for_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(876);
			match(FOR);
			setState(877);
			_localctx.exprlist = exprlist();
			setState(878);
			match(IN);
			setState(879);
			_localctx.testlist = testlist();
			setState(880);
			match(COLON);
			 boolean bFlag = startLoopBreak(); boolean cFlag = startLoopContinue(); 
			setState(882);
			_localctx.suite = suite();
			 
			            ForSSTNode result = factory.createForSSTNode(_localctx.exprlist.result, _localctx.testlist.result, _localctx.suite.result, containsContinue, getStartIndex(_localctx),getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)));
			            result.setContainsBreak(containsBreak);
			            containsContinue = cFlag;
			            containsBreak = bFlag;
			        
			setState(889);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(884);
				match(ELSE);
				setState(885);
				match(COLON);
				setState(886);
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
		enterRule(_localctx, 90, RULE_try_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(893);
			match(TRY);
			setState(894);
			match(COLON);
			setState(895);
			_localctx.body = _localctx.suite = suite();
			 int start = start(); 
			 
			            SSTNode elseStatement = null; 
			            SSTNode finallyStatement = null; 
			        
			setState(912);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EXCEPT) {
				{
				setState(901); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(898);
					_localctx.except_clause = except_clause();
					 push(_localctx.except_clause.result); 
					}
					}
					setState(903); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==EXCEPT );
				setState(910);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ELSE) {
					{
					setState(905);
					match(ELSE);
					setState(906);
					match(COLON);
					setState(907);
					_localctx.suite = suite();
					 elseStatement = _localctx.suite.result; 
					}
				}

				}
			}

			setState(919);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==FINALLY) {
				{
				setState(914);
				match(FINALLY);
				setState(915);
				match(COLON);
				setState(916);
				_localctx.suite = suite();
				 finallyStatement = _localctx.suite.result; 
				}
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
		enterRule(_localctx, 92, RULE_except_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(923);
			match(EXCEPT);
			 SSTNode testNode = null; String asName = null; 
			setState(932);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(925);
				_localctx.test = test();
				 testNode = _localctx.test.result; 
				setState(930);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(927);
					match(AS);
					setState(928);
					_localctx.NAME = match(NAME);
					 
					                        asName = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
					                        factory.getScopeEnvironment().createLocal(asName);
					                    
					}
				}

				}
			}

			setState(934);
			match(COLON);
			setState(935);
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
		enterRule(_localctx, 94, RULE_with_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(938);
			match(WITH);
			setState(939);
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
		enterRule(_localctx, 96, RULE_with_item);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(942);
			_localctx.test = test();
			 SSTNode asName = null; 
			setState(948);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(944);
				match(AS);
				setState(945);
				_localctx.expr = expr();
				 asName = _localctx.expr.result; 
				}
			}

			 SSTNode sub; 
			setState(959);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COMMA:
				{
				setState(951);
				match(COMMA);
				setState(952);
				_localctx.with_item = with_item();
				 sub = _localctx.with_item.result; 
				}
				break;
			case COLON:
				{
				setState(955);
				match(COLON);
				setState(956);
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
		enterRule(_localctx, 98, RULE_suite);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 int start = start(); 
			setState(974);
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
				setState(964);
				simple_stmt();
				}
				break;
			case NEWLINE:
				{
				setState(965);
				match(NEWLINE);
				setState(966);
				match(INDENT);
				setState(968); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(967);
					stmt();
					}
					}
					setState(970); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << DEF) | (1L << RETURN) | (1L << RAISE) | (1L << FROM) | (1L << IMPORT) | (1L << GLOBAL) | (1L << NONLOCAL) | (1L << ASSERT) | (1L << IF) | (1L << WHILE) | (1L << FOR) | (1L << TRY) | (1L << WITH) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << CLASS) | (1L << YIELD) | (1L << DEL) | (1L << PASS) | (1L << CONTINUE) | (1L << BREAK) | (1L << ASYNC) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)) | (1L << (AT - 64)))) != 0) );
				setState(972);
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
		enterRule(_localctx, 100, RULE_test);
		int _la;
		try {
			setState(991);
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
				setState(978);
				_localctx.or_test = or_test();
				 _localctx.result =  _localctx.or_test.result; 
				setState(986);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==IF) {
					{
					setState(980);
					match(IF);
					setState(981);
					_localctx.condition = _localctx.or_test = or_test();
					setState(982);
					match(ELSE);
					setState(983);
					_localctx.elTest = test();
					 _localctx.result =  new TernaryIfSSTNode(_localctx.condition.result, _localctx.result, _localctx.elTest.result, getStartIndex(_localctx), getLastIndex(_localctx));
					}
				}

				}
				break;
			case LAMBDA:
				enterOuterAlt(_localctx, 2);
				{
				setState(988);
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
		enterRule(_localctx, 102, RULE_test_nocond);
		try {
			setState(999);
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
				setState(993);
				_localctx.or_test = or_test();
				 _localctx.result =  _localctx.or_test.result; 
				}
				break;
			case LAMBDA:
				enterOuterAlt(_localctx, 2);
				{
				setState(996);
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
		public VarargslistContext varargslist;
		public TestContext test;
		public TerminalNode LAMBDA() { return getToken(Python3Parser.LAMBDA, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
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
		enterRule(_localctx, 104, RULE_lambdef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1001);
			match(LAMBDA);
			 ArgDefListBuilder args = null; 
			setState(1006);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(1003);
				_localctx.varargslist = varargslist();
				 args = _localctx.varargslist.result; 
				}
			}


			            ScopeInfo functionScope = scopeEnvironment.pushScope(ScopeEnvironment.LAMBDA_NAME, ScopeInfo.ScopeKind.Function); 
			            functionScope.setHasAnnotations(true);
			            if (args != null) {
			                args.defineParamsInScope(functionScope);
			            }
			        
			setState(1009);
			match(COLON);
			setState(1010);
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
		public VarargslistContext varargslist;
		public Test_nocondContext test_nocond;
		public TerminalNode LAMBDA() { return getToken(Python3Parser.LAMBDA, 0); }
		public TerminalNode COLON() { return getToken(Python3Parser.COLON, 0); }
		public Test_nocondContext test_nocond() {
			return getRuleContext(Test_nocondContext.class,0);
		}
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
		enterRule(_localctx, 106, RULE_lambdef_nocond);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1014);
			match(LAMBDA);
			 ArgDefListBuilder args = null; 
			setState(1019);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(1016);
				_localctx.varargslist = varargslist();
				 args = _localctx.varargslist.result;
				}
			}


			            ScopeInfo functionScope = scopeEnvironment.pushScope(ScopeEnvironment.LAMBDA_NAME, ScopeInfo.ScopeKind.Function); 
			            functionScope.setHasAnnotations(true);
			            if (args != null) {
			                args.defineParamsInScope(functionScope);
			            }
			        
			setState(1022);
			match(COLON);
			setState(1023);
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
		enterRule(_localctx, 108, RULE_or_test);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1027);
			_localctx.first = _localctx.and_test = and_test();
			setState(1041);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OR:
				{
				 int start = start(); 
				 push(_localctx.first.result); 
				setState(1034); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1030);
					match(OR);
					setState(1031);
					_localctx.and_test = and_test();
					 push(_localctx.and_test.result); 
					}
					}
					setState(1036); 
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
		enterRule(_localctx, 110, RULE_and_test);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1043);
			_localctx.first = _localctx.not_test = not_test();
			setState(1057);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AND:
				{
				 int start = start(); 
				 push(_localctx.first.result); 
				setState(1050); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1046);
					match(AND);
					setState(1047);
					_localctx.not_test = not_test();
					 push(_localctx.not_test.result); 
					}
					}
					setState(1052); 
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
		enterRule(_localctx, 112, RULE_not_test);
		try {
			setState(1066);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(1059);
				match(NOT);
				setState(1060);
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
				setState(1063);
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
		enterRule(_localctx, 114, RULE_comparison);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1068);
			_localctx.first = _localctx.expr = expr();
			setState(1081);
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
				setState(1074); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1070);
					_localctx.comp_op = comp_op();
					setState(1071);
					_localctx.expr = expr();
					 pushString(_localctx.comp_op.result); push(_localctx.expr.result); 
					}
					}
					setState(1076); 
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
		enterRule(_localctx, 116, RULE_comp_op);
		try {
			setState(1107);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,129,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1083);
				match(LESS_THAN);
				 _localctx.result =  "<"; 
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1085);
				match(GREATER_THAN);
				 _localctx.result =  ">"; 
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1087);
				match(EQUALS);
				 _localctx.result =  "=="; 
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1089);
				match(GT_EQ);
				 _localctx.result =  ">="; 
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1091);
				match(LT_EQ);
				 _localctx.result =  "<="; 
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(1093);
				match(NOT_EQ_1);
				 _localctx.result =  "<>"; 
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(1095);
				match(NOT_EQ_2);
				 _localctx.result =  "!="; 
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(1097);
				match(IN);
				 _localctx.result =  "in"; 
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(1099);
				match(NOT);
				setState(1100);
				match(IN);
				 _localctx.result =  "notin"; 
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(1102);
				match(IS);
				 _localctx.result =  "is"; 
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(1104);
				match(IS);
				setState(1105);
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
		enterRule(_localctx, 118, RULE_star_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1109);
			match(STAR);
			setState(1110);
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
		enterRule(_localctx, 120, RULE_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1113);
			_localctx.xor_expr = xor_expr();
			 _localctx.result =  _localctx.xor_expr.result; 
			setState(1121);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR_OP) {
				{
				{
				setState(1115);
				match(OR_OP);
				setState(1116);
				_localctx.xor_expr = xor_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.Or, _localctx.result, _localctx.xor_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.xor_expr!=null?(_localctx.xor_expr.stop):null)));
				}
				}
				setState(1123);
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
		enterRule(_localctx, 122, RULE_xor_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1124);
			_localctx.and_expr = and_expr();
			 _localctx.result =  _localctx.and_expr.result; 
			setState(1132);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==XOR) {
				{
				{
				setState(1126);
				match(XOR);
				setState(1127);
				_localctx.and_expr = and_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.Xor, _localctx.result, _localctx.and_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.and_expr!=null?(_localctx.and_expr.stop):null))); 
				}
				}
				setState(1134);
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
		enterRule(_localctx, 124, RULE_and_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1135);
			_localctx.shift_expr = shift_expr();
			 _localctx.result =  _localctx.shift_expr.result; 
			setState(1143);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND_OP) {
				{
				{
				setState(1137);
				match(AND_OP);
				setState(1138);
				_localctx.shift_expr = shift_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.And, _localctx.result, _localctx.shift_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.shift_expr!=null?(_localctx.shift_expr.stop):null))); 
				}
				}
				setState(1145);
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
		enterRule(_localctx, 126, RULE_shift_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1146);
			_localctx.arith_expr = arith_expr();
			 _localctx.result =  _localctx.arith_expr.result; 
			setState(1160);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LEFT_SHIFT || _la==RIGHT_SHIFT) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1153);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case LEFT_SHIFT:
					{
					setState(1149);
					match(LEFT_SHIFT);
					 arithmetic = BinaryArithmetic.LShift; 
					}
					break;
				case RIGHT_SHIFT:
					{
					setState(1151);
					match(RIGHT_SHIFT);
					 arithmetic = BinaryArithmetic.RShift; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1155);
				_localctx.arith_expr = arith_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.arith_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.arith_expr!=null?(_localctx.arith_expr.stop):null)));
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
		enterRule(_localctx, 128, RULE_arith_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1163);
			_localctx.term = term();
			 _localctx.result =  _localctx.term.result; 
			setState(1177);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ADD || _la==MINUS) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1170);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ADD:
					{
					setState(1166);
					match(ADD);
					 arithmetic = BinaryArithmetic.Add; 
					}
					break;
				case MINUS:
					{
					setState(1168);
					match(MINUS);
					 arithmetic = BinaryArithmetic.Sub; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1172);
				_localctx.term = term();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.term.result, getStartIndex(_localctx), getStopIndex((_localctx.term!=null?(_localctx.term.stop):null))); 
				}
				}
				setState(1179);
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
		enterRule(_localctx, 130, RULE_term);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1180);
			_localctx.factor = factor();
			 _localctx.result =  _localctx.factor.result; 
			setState(1200);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((((_la - 49)) & ~0x3f) == 0 && ((1L << (_la - 49)) & ((1L << (STAR - 49)) | (1L << (DIV - 49)) | (1L << (MOD - 49)) | (1L << (IDIV - 49)) | (1L << (AT - 49)))) != 0)) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1193);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STAR:
					{
					setState(1183);
					match(STAR);
					 arithmetic = BinaryArithmetic.Mul; 
					}
					break;
				case AT:
					{
					setState(1185);
					match(AT);
					 arithmetic = BinaryArithmetic.MatMul; 
					}
					break;
				case DIV:
					{
					setState(1187);
					match(DIV);
					 arithmetic = BinaryArithmetic.TrueDiv; 
					}
					break;
				case MOD:
					{
					setState(1189);
					match(MOD);
					 arithmetic = BinaryArithmetic.Mod; 
					}
					break;
				case IDIV:
					{
					setState(1191);
					match(IDIV);
					 arithmetic = BinaryArithmetic.FloorDiv; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1195);
				_localctx.factor = factor();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.factor.result, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
				}
				}
				setState(1202);
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
		enterRule(_localctx, 132, RULE_factor);
		try {
			setState(1218);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ADD:
			case MINUS:
			case NOT_OP:
				enterOuterAlt(_localctx, 1);
				{
				 
				            UnaryArithmetic arithmetic; 
				            boolean isNeg = false;
				        
				setState(1210);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ADD:
					{
					setState(1204);
					match(ADD);
					 arithmetic = UnaryArithmetic.Pos; 
					}
					break;
				case MINUS:
					{
					setState(1206);
					_localctx.m = match(MINUS);
					 arithmetic = UnaryArithmetic.Neg; isNeg = true; 
					}
					break;
				case NOT_OP:
					{
					setState(1208);
					match(NOT_OP);
					 arithmetic = UnaryArithmetic.Invert; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1212);
				_localctx.factor = factor();
				 
				                assert _localctx.factor != null;
				                SSTNode fResult = _localctx.factor.result;
				                if (isNeg && fResult instanceof NumberLiteralSSTNode) {
				                    if (((NumberLiteralSSTNode)fResult).isNegative()) {
				                        // solving cases like --2
				                        _localctx.result =   new UnarySSTNode(UnaryArithmetic.Neg, fResult, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
				                    } else {
				                        ((NumberLiteralSSTNode)fResult).setIsNegative(true);
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
				setState(1215);
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
		enterRule(_localctx, 134, RULE_power);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1220);
			_localctx.atom_expr = atom_expr();
			 _localctx.result =  _localctx.atom_expr.result; 
			setState(1226);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==POWER) {
				{
				setState(1222);
				match(POWER);
				setState(1223);
				_localctx.factor = factor();
				 _localctx.result =  new TernaryArithmeticSSTNode(_localctx.result, _localctx.factor.result, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
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
		enterRule(_localctx, 136, RULE_atom_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1229);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AWAIT) {
				{
				setState(1228);
				match(AWAIT);
				}
			}

			setState(1231);
			_localctx.atom = atom();
			 _localctx.result =  _localctx.atom.result; 
			setState(1248);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << DOT) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0)) {
				{
				setState(1246);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case OPEN_PAREN:
					{
					setState(1233);
					match(OPEN_PAREN);
					setState(1234);
					_localctx.arglist = arglist();
					setState(1235);
					_localctx.CloseB = match(CLOSE_PAREN);
					 _localctx.result =  new CallSSTNode(_localctx.result, _localctx.arglist.result, getStartIndex(_localctx), _localctx.CloseB.getStopIndex() + 1);
					}
					break;
				case OPEN_BRACK:
					{
					setState(1238);
					match(OPEN_BRACK);
					setState(1239);
					_localctx.subscriptlist = subscriptlist();
					setState(1240);
					_localctx.c = match(CLOSE_BRACK);
					 _localctx.result =  new SubscriptSSTNode(_localctx.result, _localctx.subscriptlist.result, getStartIndex(_localctx), _localctx.c.getStopIndex() + 1);
					}
					break;
				case DOT:
					{
					setState(1243);
					match(DOT);
					setState(1244);
					_localctx.NAME = match(NAME);
					   
					                    assert _localctx.NAME != null;
					                    _localctx.result =  new GetAttributeSSTNode(_localctx.result, (_localctx.NAME!=null?_localctx.NAME.getText():null), getStartIndex(_localctx), getStopIndex(_localctx.NAME));
					                
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(1250);
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
		enterRule(_localctx, 138, RULE_atom);
		int _la;
		try {
			setState(1314);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OPEN_PAREN:
				enterOuterAlt(_localctx, 1);
				{
				setState(1251);
				match(OPEN_PAREN);
				setState(1259);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case YIELD:
					{
					setState(1252);
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
					setState(1255);
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
				setState(1261);
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
				setState(1263);
				_localctx.startIndex = match(OPEN_BRACK);
				setState(1268);
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
					setState(1264);
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
				setState(1270);
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
				setState(1272);
				_localctx.startIndex = match(OPEN_BRACE);
				setState(1280);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,147,_ctx) ) {
				case 1:
					{
					setState(1273);
					_localctx.dictmaker = dictmaker();
					 _localctx.result =  _localctx.dictmaker.result; 
					}
					break;
				case 2:
					{
					setState(1276);
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
				setState(1282);
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
				setState(1284);
				_localctx.NAME = match(NAME);
				   
				                String text = (_localctx.NAME!=null?_localctx.NAME.getText():null);
				                _localctx.result =  text != null ? factory.createVariableLookup(text,  _localctx.NAME.getStartIndex(), _localctx.NAME.getStopIndex() + 1) : null; 
				            
				}
				break;
			case DECIMAL_INTEGER:
				enterOuterAlt(_localctx, 5);
				{
				setState(1286);
				_localctx.DECIMAL_INTEGER = match(DECIMAL_INTEGER);
				 
				                String text = (_localctx.DECIMAL_INTEGER!=null?_localctx.DECIMAL_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 0, 10, _localctx.DECIMAL_INTEGER.getStartIndex(), _localctx.DECIMAL_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case OCT_INTEGER:
				enterOuterAlt(_localctx, 6);
				{
				setState(1288);
				_localctx.OCT_INTEGER = match(OCT_INTEGER);
				 
				                String text = (_localctx.OCT_INTEGER!=null?_localctx.OCT_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 2, 8, _localctx.OCT_INTEGER.getStartIndex(), _localctx.OCT_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case HEX_INTEGER:
				enterOuterAlt(_localctx, 7);
				{
				setState(1290);
				_localctx.HEX_INTEGER = match(HEX_INTEGER);
				 
				                String text = (_localctx.HEX_INTEGER!=null?_localctx.HEX_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 2, 16, _localctx.HEX_INTEGER.getStartIndex(), _localctx.HEX_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 8);
				{
				setState(1292);
				_localctx.BIN_INTEGER = match(BIN_INTEGER);
				 
				                String text = (_localctx.BIN_INTEGER!=null?_localctx.BIN_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 2, 2, _localctx.BIN_INTEGER.getStartIndex(), _localctx.BIN_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case FLOAT_NUMBER:
				enterOuterAlt(_localctx, 9);
				{
				setState(1294);
				_localctx.FLOAT_NUMBER = match(FLOAT_NUMBER);
				   
				                String text = (_localctx.FLOAT_NUMBER!=null?_localctx.FLOAT_NUMBER.getText():null);
				                _localctx.result =  text != null ? new FloatLiteralSSTNode(text, false, _localctx.FLOAT_NUMBER.getStartIndex(), _localctx.FLOAT_NUMBER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case IMAG_NUMBER:
				enterOuterAlt(_localctx, 10);
				{
				setState(1296);
				_localctx.IMAG_NUMBER = match(IMAG_NUMBER);
				 
				                String text = (_localctx.IMAG_NUMBER!=null?_localctx.IMAG_NUMBER.getText():null);
				                _localctx.result =  text != null ? new FloatLiteralSSTNode(text, true, _localctx.IMAG_NUMBER.getStartIndex(), _localctx.IMAG_NUMBER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 11);
				{
				 int start = stringStart(); 
				setState(1301); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1299);
					_localctx.STRING = match(STRING);
					 pushString((_localctx.STRING!=null?_localctx.STRING.getText():null)); 
					}
					}
					setState(1303); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==STRING );
				 _localctx.result =  new StringLiteralSSTNode(getStringArray(start), getStartIndex(_localctx), getStopIndex(_localctx.STRING)); 
				}
				break;
			case ELLIPSIS:
				enterOuterAlt(_localctx, 12);
				{
				setState(1306);
				_localctx.t = match(ELLIPSIS);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new SimpleSSTNode(SimpleSSTNode.Type.ELLIPSIS,  start, start + 3);
				}
				break;
			case NONE:
				enterOuterAlt(_localctx, 13);
				{
				setState(1308);
				_localctx.t = match(NONE);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new SimpleSSTNode(SimpleSSTNode.Type.NONE,  start, start + 4);
				}
				break;
			case TRUE:
				enterOuterAlt(_localctx, 14);
				{
				setState(1310);
				_localctx.t = match(TRUE);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new BooleanLiteralSSTNode(true,  start, start + 4); 
				}
				break;
			case FALSE:
				enterOuterAlt(_localctx, 15);
				{
				setState(1312);
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
		enterRule(_localctx, 140, RULE_subscriptlist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1316);
			_localctx.subscript = subscript();
			 _localctx.result =  _localctx.subscript.result; 
			setState(1337);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 int start = start(); push(_localctx.result); 
				setState(1319);
				match(COMMA);
				setState(1334);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << COLON) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1320);
					_localctx.subscript = subscript();
					 push(_localctx.subscript.result); 
					setState(1328);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,150,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(1322);
							match(COMMA);
							setState(1323);
							_localctx.subscript = subscript();
							 push(_localctx.subscript.result); 
							}
							} 
						}
						setState(1330);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,150,_ctx);
					}
					setState(1332);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(1331);
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
		enterRule(_localctx, 142, RULE_subscript);
		int _la;
		try {
			setState(1363);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,158,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1339);
				_localctx.test = test();
				 _localctx.result =  _localctx.test.result; 
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				 SSTNode sliceStart = null; SSTNode sliceEnd = null; SSTNode sliceStep = null; 
				setState(1346);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1343);
					_localctx.test = test();
					 sliceStart = _localctx.test.result; 
					}
				}

				setState(1348);
				match(COLON);
				setState(1352);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1349);
					_localctx.test = test();
					 sliceEnd = _localctx.test.result; 
					}
				}

				setState(1360);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COLON) {
					{
					setState(1354);
					match(COLON);
					setState(1358);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
						{
						setState(1355);
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
		enterRule(_localctx, 144, RULE_exprlist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 int start = start(); 
			setState(1372);
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
				setState(1366);
				_localctx.expr = expr();
				 push(_localctx.expr.result); 
				}
				break;
			case STAR:
				{
				setState(1369);
				_localctx.star_expr = star_expr();
				 push(_localctx.star_expr.result); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(1385);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,161,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1374);
					match(COMMA);
					setState(1381);
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
						setState(1375);
						_localctx.expr = expr();
						 push(_localctx.expr.result); 
						}
						break;
					case STAR:
						{
						setState(1378);
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
				setState(1387);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,161,_ctx);
			}
			setState(1389);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(1388);
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
		enterRule(_localctx, 146, RULE_testlist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(1393);
			_localctx.test = test();
			 _localctx.result =  _localctx.test.result; 
			setState(1414);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 int start = start(); push(_localctx.result); 
				setState(1396);
				match(COMMA);
				setState(1411);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1397);
					_localctx.test = test();
					 push(_localctx.test.result); 
					setState(1405);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,163,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(1399);
							match(COMMA);
							setState(1400);
							_localctx.test = test();
							 push(_localctx.test.result); 
							}
							} 
						}
						setState(1407);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,163,_ctx);
					}
					setState(1409);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(1408);
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
		enterRule(_localctx, 148, RULE_dictmaker);
		int _la;
		try {
			int _alt;
			setState(1466);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,172,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				{
				 
				                SSTNode value; 
				                SSTNode name;
				                ScopeInfo generator = scopeEnvironment.pushScope("generator"+_localctx.getStart().getStartIndex(), ScopeInfo.ScopeKind.DictComp);
				                generator.setHasAnnotations(true);
				                
				            
				setState(1426);
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
					setState(1417);
					_localctx.n = test();
					setState(1418);
					match(COLON);
					setState(1419);
					_localctx.v = test();
					 name = _localctx.n.result; value = _localctx.v.result; 
					}
					break;
				case POWER:
					{
					setState(1422);
					match(POWER);
					setState(1423);
					_localctx.expr = expr();
					 name = null; value = _localctx.expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1428);
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
				            
				setState(1441);
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
					setState(1432);
					_localctx.n = test();
					setState(1433);
					match(COLON);
					setState(1434);
					_localctx.v = test();
					 name = _localctx.n.result; value = _localctx.v.result; 
					}
					break;
				case POWER:
					{
					setState(1437);
					match(POWER);
					setState(1438);
					_localctx.expr = expr();
					 name = null; value = _localctx.expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 int start = start(); push(name); push(value); 
				setState(1458);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,170,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1444);
						match(COMMA);
						setState(1454);
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
							setState(1445);
							_localctx.n = test();
							setState(1446);
							match(COLON);
							setState(1447);
							_localctx.v = test();
							 push(_localctx.n.result); push(_localctx.v.result); 
							}
							break;
						case POWER:
							{
							setState(1450);
							match(POWER);
							setState(1451);
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
					setState(1460);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,170,_ctx);
				}
				setState(1462);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1461);
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
		enterRule(_localctx, 150, RULE_setlisttuplemaker);
		int _la;
		try {
			int _alt;
			setState(1511);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,178,_ctx) ) {
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
				                ScopeInfo generator = scopeEnvironment.pushScope("generator"+_localctx.getStart().getStartIndex(), scopeKind); 
				                generator.setHasAnnotations(true);
				            
				setState(1475);
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
					setState(1469);
					_localctx.test = test();
					 value = _localctx.test.result; 
					}
					break;
				case STAR:
					{
					setState(1472);
					_localctx.star_expr = star_expr();
					 value = _localctx.star_expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1477);
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
				setState(1487);
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
					setState(1481);
					_localctx.test = test();
					 value = _localctx.test.result; 
					}
					break;
				case STAR:
					{
					setState(1484);
					_localctx.star_expr = star_expr();
					 value = _localctx.star_expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 int start = start(); push(value); 
				setState(1501);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,176,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1490);
						match(COMMA);
						setState(1497);
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
							setState(1491);
							_localctx.test = test();
							 push(_localctx.test.result); 
							}
							break;
						case STAR:
							{
							setState(1494);
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
					setState(1503);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,176,_ctx);
				}
				 boolean comma = false; 
				setState(1507);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1505);
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
		enterRule(_localctx, 152, RULE_classdef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1513);
			match(CLASS);
			setState(1514);
			_localctx.NAME = match(NAME);
			 ArgListBuilder baseClasses = null; 
			setState(1521);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN_PAREN) {
				{
				setState(1516);
				match(OPEN_PAREN);
				setState(1517);
				_localctx.arglist = arglist();
				setState(1518);
				match(CLOSE_PAREN);
				 baseClasses = _localctx.arglist.result; 
				}
			}


			            // we need to create the scope here to resolve base classes in the outer scope
			            factory.getScopeEnvironment().createLocal((_localctx.NAME!=null?_localctx.NAME.getText():null));
			            ScopeInfo classScope = scopeEnvironment.pushScope((_localctx.NAME!=null?_localctx.NAME.getText():null), ScopeInfo.ScopeKind.Class); 
			        
			setState(1524);
			match(COLON);
			setState(1525);
			_localctx.suite = suite();
			 push(factory.createClassDefinition((_localctx.NAME!=null?_localctx.NAME.getText():null), baseClasses, _localctx.suite.result, getStartIndex(_localctx), getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)))); 
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
		enterRule(_localctx, 154, RULE_arglist);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			 ArgListBuilder args = new ArgListBuilder(); 
			setState(1541);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << POWER) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(1530);
				argument(args);
				setState(1535);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,180,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1531);
						match(COMMA);
						setState(1532);
						argument(args);
						}
						} 
					}
					setState(1537);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,180,_ctx);
				}
				setState(1539);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1538);
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
		enterRule(_localctx, 156, RULE_argument);
		try {
			setState(1568);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,183,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{

				                    ScopeInfo generator = scopeEnvironment.pushScope("generator"+_localctx.getStart().getStartIndex(), ScopeInfo.ScopeKind.GenExp); 
				                    generator.setHasAnnotations(true);
				                
				setState(1546);
				_localctx.test = test();
				setState(1547);
				_localctx.comp_for = comp_for(_localctx.test.result, null, PythonBuiltinClassType.PGenerator, 0);

				                    args.addArg(_localctx.comp_for.result);
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
				                
				setState(1551);
				_localctx.n = _localctx.test = test();

				                    if (!((_localctx.n).result instanceof VarLookupSSTNode)) {
				                        throw new PythonRecognitionException("keyword can't be an expression", this, _input, _localctx, getCurrentToken());
				                    }
				                    if (!isNameAsVariableInScope && scopeEnvironment.getCurrentScope().getSeenVars().contains(name)) {
				                        scopeEnvironment.getCurrentScope().getSeenVars().remove(name);
				                    }
				                
				setState(1553);
				match(ASSIGN);
				setState(1554);
				_localctx.test = test();
				 
				                        args.addNamedArg(name, _localctx.test.result); 
				                    
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1557);
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
				setState(1560);
				match(POWER);
				setState(1561);
				_localctx.test = test();
				 args.addKwArg(_localctx.test.result); 
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1564);
				match(STAR);
				setState(1565);
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
		enterRule(_localctx, 158, RULE_comp_for);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			 
			            if (target instanceof StarSSTNode) {
			                throw new PythonRecognitionException("iterable unpacking cannot be used in comprehension", this, _input, _localctx, _localctx.start);
			            }
			            boolean scopeCreated = true; 
			            boolean async = false; 
			        
			setState(1573);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASYNC) {
				{
				setState(1571);
				match(ASYNC);
				 async = true; 
				}
			}

			 
			            SSTNode iterator; 
			            SSTNode[] variables;
			            int lineNumber;
			        
			setState(1576);
			_localctx.f = match(FOR);
			setState(1577);
			_localctx.exprlist = exprlist();
			setState(1578);
			match(IN);

			                ScopeInfo currentScope = null;
			                if (level == 0) {
			                    currentScope = scopeEnvironment.getCurrentScope();
			                    factory.getScopeEnvironment().setCurrentScope(currentScope.getParent());
			                }
			            
			setState(1580);
			_localctx.or_test = or_test();
			   
			            if (level == 0) {
			                factory.getScopeEnvironment().setCurrentScope(currentScope);
			            }
			            lineNumber = _localctx.f.getLine();
			            iterator = _localctx.or_test.result; 
			            variables = _localctx.exprlist.result;
			        
			 int start = start(); 
			setState(1589);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==IF) {
				{
				{
				setState(1583);
				match(IF);
				setState(1584);
				_localctx.test_nocond = test_nocond();
				 push(_localctx.test_nocond.result); 
				}
				}
				setState(1591);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			 SSTNode[] conditions = getArray(start, SSTNode[].class); 
			setState(1596);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==FOR || _la==ASYNC) {
				{
				setState(1593);
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
		enterRule(_localctx, 160, RULE_encoding_decl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1600);
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
		public TestlistContext testlist;
		public TerminalNode YIELD() { return getToken(Python3Parser.YIELD, 0); }
		public TerminalNode FROM() { return getToken(Python3Parser.FROM, 0); }
		public TestContext test() {
			return getRuleContext(TestContext.class,0);
		}
		public TestlistContext testlist() {
			return getRuleContext(TestlistContext.class,0);
		}
		public Yield_exprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_yield_expr; }
	}

	public final Yield_exprContext yield_expr() throws RecognitionException {
		Yield_exprContext _localctx = new Yield_exprContext(_ctx, getState());
		enterRule(_localctx, 162, RULE_yield_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			 
			        SSTNode value = null;
			        boolean isFrom = false; 
			    
			setState(1603);
			match(YIELD);
			setState(1611);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case FROM:
				{
				setState(1604);
				match(FROM);
				setState(1605);
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
			case OPEN_PAREN:
			case OPEN_BRACK:
			case ADD:
			case MINUS:
			case NOT_OP:
			case OPEN_BRACE:
				{
				setState(1608);
				_localctx.testlist = testlist();
				 value = _localctx.testlist.result; 
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3c\u0652\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4I"+
		"\tI\4J\tJ\4K\tK\4L\tL\4M\tM\4N\tN\4O\tO\4P\tP\4Q\tQ\4R\tR\4S\tS\3\2\3"+
		"\2\3\2\3\2\3\2\3\2\3\2\5\2\u00ae\n\2\3\2\3\2\3\2\3\3\3\3\3\3\3\3\7\3\u00b7"+
		"\n\3\f\3\16\3\u00ba\13\3\3\3\3\3\3\3\3\3\3\4\3\4\3\4\7\4\u00c3\n\4\f\4"+
		"\16\4\u00c6\13\4\3\4\3\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\u00d4"+
		"\n\5\3\5\3\5\3\5\3\6\3\6\6\6\u00db\n\6\r\6\16\6\u00dc\3\6\3\6\3\7\3\7"+
		"\3\7\3\7\3\7\5\7\u00e6\n\7\3\7\3\7\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\5\t"+
		"\u00f2\n\t\3\t\3\t\3\t\3\t\3\t\3\n\3\n\3\n\5\n\u00fc\n\n\3\n\3\n\3\n\3"+
		"\13\3\13\3\13\7\13\u0104\n\13\f\13\16\13\u0107\13\13\3\13\3\13\3\13\3"+
		"\13\3\13\3\13\3\13\7\13\u0110\n\13\f\13\16\13\u0113\13\13\5\13\u0115\n"+
		"\13\3\13\3\13\3\13\3\13\7\13\u011b\n\13\f\13\16\13\u011e\13\13\3\13\3"+
		"\13\3\13\5\13\u0123\n\13\5\13\u0125\n\13\5\13\u0127\n\13\3\13\3\13\5\13"+
		"\u012b\n\13\5\13\u012d\n\13\5\13\u012f\n\13\5\13\u0131\n\13\3\13\3\13"+
		"\3\13\7\13\u0136\n\13\f\13\16\13\u0139\13\13\3\13\3\13\3\13\3\13\7\13"+
		"\u013f\n\13\f\13\16\13\u0142\13\13\3\13\3\13\3\13\5\13\u0147\n\13\5\13"+
		"\u0149\n\13\5\13\u014b\n\13\3\13\3\13\5\13\u014f\n\13\5\13\u0151\n\13"+
		"\5\13\u0153\n\13\3\13\3\13\3\13\7\13\u0158\n\13\f\13\16\13\u015b\13\13"+
		"\3\13\3\13\3\13\5\13\u0160\n\13\5\13\u0162\n\13\5\13\u0164\n\13\3\13\3"+
		"\13\5\13\u0168\n\13\5\13\u016a\n\13\3\f\3\f\3\f\3\f\3\f\3\f\5\f\u0172"+
		"\n\f\3\f\3\f\3\f\3\f\5\f\u0178\n\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r"+
		"\3\r\5\r\u0184\n\r\5\r\u0186\n\r\3\r\3\r\3\16\3\16\3\16\3\16\3\16\3\16"+
		"\3\16\5\16\u0191\n\16\3\16\3\16\3\17\3\17\3\17\3\17\7\17\u0199\n\17\f"+
		"\17\16\17\u019c\13\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\7\17\u01a5\n"+
		"\17\f\17\16\17\u01a8\13\17\5\17\u01aa\n\17\3\17\3\17\3\17\3\17\7\17\u01b0"+
		"\n\17\f\17\16\17\u01b3\13\17\3\17\3\17\3\17\5\17\u01b8\n\17\5\17\u01ba"+
		"\n\17\5\17\u01bc\n\17\3\17\3\17\5\17\u01c0\n\17\5\17\u01c2\n\17\5\17\u01c4"+
		"\n\17\5\17\u01c6\n\17\3\17\3\17\3\17\7\17\u01cb\n\17\f\17\16\17\u01ce"+
		"\13\17\3\17\3\17\3\17\3\17\7\17\u01d4\n\17\f\17\16\17\u01d7\13\17\3\17"+
		"\3\17\3\17\5\17\u01dc\n\17\5\17\u01de\n\17\5\17\u01e0\n\17\3\17\3\17\5"+
		"\17\u01e4\n\17\5\17\u01e6\n\17\5\17\u01e8\n\17\3\17\3\17\3\17\7\17\u01ed"+
		"\n\17\f\17\16\17\u01f0\13\17\3\17\3\17\3\17\5\17\u01f5\n\17\5\17\u01f7"+
		"\n\17\5\17\u01f9\n\17\3\17\3\17\5\17\u01fd\n\17\5\17\u01ff\n\17\3\17\3"+
		"\17\3\20\3\20\3\20\3\20\3\20\3\20\5\20\u0209\n\20\3\20\3\20\3\21\3\21"+
		"\3\21\3\21\5\21\u0211\n\21\3\21\3\21\3\22\3\22\3\22\3\22\3\23\3\23\5\23"+
		"\u021b\n\23\3\24\3\24\3\24\7\24\u0220\n\24\f\24\16\24\u0223\13\24\3\24"+
		"\5\24\u0226\n\24\3\24\3\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\5\25\u0233\n\25\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u023d\n"+
		"\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u0248\n\26\3\26"+
		"\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u0256\n\26"+
		"\7\26\u0258\n\26\f\26\16\26\u025b\13\26\3\26\5\26\u025e\n\26\3\27\3\27"+
		"\3\27\3\27\3\27\3\27\5\27\u0266\n\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\3\27\5\27\u0270\n\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\5\27\u0279\n"+
		"\27\7\27\u027b\n\27\f\27\16\27\u027e\13\27\3\27\5\27\u0281\n\27\5\27\u0283"+
		"\n\27\3\27\5\27\u0286\n\27\3\30\3\30\3\31\3\31\3\31\3\31\3\32\3\32\3\32"+
		"\3\32\3\32\3\32\3\32\5\32\u0295\n\32\3\33\3\33\3\33\3\33\3\33\5\33\u029c"+
		"\n\33\3\33\3\33\3\34\3\34\3\34\3\35\3\35\3\35\3\35\3\35\3\35\3\35\3\35"+
		"\5\35\u02ab\n\35\5\35\u02ad\n\35\3\35\3\35\3\36\3\36\5\36\u02b3\n\36\3"+
		"\37\3\37\3\37\3 \3 \3 \3 \3 \3 \7 \u02be\n \f \16 \u02c1\13 \3 \3 \3 "+
		"\3 \3 \3 \3 \6 \u02ca\n \r \16 \u02cb\5 \u02ce\n \3 \3 \3 \3 \3 \3 \3"+
		" \3 \3 \3 \3 \5 \u02db\n \3 \3 \3!\3!\3!\3!\3!\5!\u02e4\n!\3!\3!\3\"\3"+
		"\"\3\"\3\"\3\"\3\"\3\"\7\"\u02ef\n\"\f\"\16\"\u02f2\13\"\3\"\5\"\u02f5"+
		"\n\"\3\"\3\"\3#\3#\3#\3#\3#\7#\u02fe\n#\f#\16#\u0301\13#\3$\3$\3$\3$\3"+
		"$\5$\u0308\n$\3%\3%\3%\7%\u030d\n%\f%\16%\u0310\13%\3&\3&\3&\3&\3&\3&"+
		"\3&\7&\u0319\n&\f&\16&\u031c\13&\3&\3&\3\'\3\'\3\'\3\'\3\'\3\'\3\'\7\'"+
		"\u0327\n\'\f\'\16\'\u032a\13\'\3\'\3\'\3(\3(\3(\3(\3(\3(\3(\5(\u0335\n"+
		"(\3(\3(\3)\3)\3)\3)\3)\3)\3)\3)\3)\5)\u0342\n)\3*\3*\3*\3*\5*\u0348\n"+
		"*\3+\3+\3+\3+\3+\3+\3+\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\5,\u035e"+
		"\n,\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\5-\u036b\n-\3-\3-\3.\3.\3.\3.\3."+
		"\3.\3.\3.\3.\3.\3.\3.\3.\5.\u037c\n.\3.\3.\3/\3/\3/\3/\3/\3/\3/\3/\6/"+
		"\u0388\n/\r/\16/\u0389\3/\3/\3/\3/\3/\5/\u0391\n/\5/\u0393\n/\3/\3/\3"+
		"/\3/\3/\5/\u039a\n/\3/\3/\3\60\3\60\3\60\3\60\3\60\3\60\3\60\5\60\u03a5"+
		"\n\60\5\60\u03a7\n\60\3\60\3\60\3\60\3\60\3\61\3\61\3\61\3\61\3\62\3\62"+
		"\3\62\3\62\3\62\3\62\5\62\u03b7\n\62\3\62\3\62\3\62\3\62\3\62\3\62\3\62"+
		"\3\62\3\62\5\62\u03c2\n\62\3\62\3\62\3\63\3\63\3\63\3\63\3\63\6\63\u03cb"+
		"\n\63\r\63\16\63\u03cc\3\63\3\63\5\63\u03d1\n\63\3\63\3\63\3\64\3\64\3"+
		"\64\3\64\3\64\3\64\3\64\3\64\5\64\u03dd\n\64\3\64\3\64\3\64\5\64\u03e2"+
		"\n\64\3\65\3\65\3\65\3\65\3\65\3\65\5\65\u03ea\n\65\3\66\3\66\3\66\3\66"+
		"\3\66\5\66\u03f1\n\66\3\66\3\66\3\66\3\66\3\66\3\66\3\67\3\67\3\67\3\67"+
		"\3\67\5\67\u03fe\n\67\3\67\3\67\3\67\3\67\3\67\3\67\38\38\38\38\38\38"+
		"\38\68\u040d\n8\r8\168\u040e\38\38\38\58\u0414\n8\39\39\39\39\39\39\3"+
		"9\69\u041d\n9\r9\169\u041e\39\39\39\59\u0424\n9\3:\3:\3:\3:\3:\3:\3:\5"+
		":\u042d\n:\3;\3;\3;\3;\3;\3;\6;\u0435\n;\r;\16;\u0436\3;\3;\3;\5;\u043c"+
		"\n;\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<"+
		"\3<\3<\5<\u0456\n<\3=\3=\3=\3=\3>\3>\3>\3>\3>\3>\7>\u0462\n>\f>\16>\u0465"+
		"\13>\3?\3?\3?\3?\3?\3?\7?\u046d\n?\f?\16?\u0470\13?\3@\3@\3@\3@\3@\3@"+
		"\7@\u0478\n@\f@\16@\u047b\13@\3A\3A\3A\3A\3A\3A\3A\5A\u0484\nA\3A\3A\3"+
		"A\7A\u0489\nA\fA\16A\u048c\13A\3B\3B\3B\3B\3B\3B\3B\5B\u0495\nB\3B\3B"+
		"\3B\7B\u049a\nB\fB\16B\u049d\13B\3C\3C\3C\3C\3C\3C\3C\3C\3C\3C\3C\3C\3"+
		"C\5C\u04ac\nC\3C\3C\3C\7C\u04b1\nC\fC\16C\u04b4\13C\3D\3D\3D\3D\3D\3D"+
		"\3D\5D\u04bd\nD\3D\3D\3D\3D\3D\3D\5D\u04c5\nD\3E\3E\3E\3E\3E\3E\5E\u04cd"+
		"\nE\3F\5F\u04d0\nF\3F\3F\3F\3F\3F\3F\3F\3F\3F\3F\3F\3F\3F\3F\3F\7F\u04e1"+
		"\nF\fF\16F\u04e4\13F\3G\3G\3G\3G\3G\3G\3G\3G\5G\u04ee\nG\3G\3G\3G\3G\3"+
		"G\3G\3G\5G\u04f7\nG\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\5G\u0503\nG\3G\3G\3"+
		"G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\6G\u0518\nG\rG\16G\u0519"+
		"\3G\3G\3G\3G\3G\3G\3G\3G\3G\5G\u0525\nG\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H"+
		"\7H\u0531\nH\fH\16H\u0534\13H\3H\5H\u0537\nH\5H\u0539\nH\3H\5H\u053c\n"+
		"H\3I\3I\3I\3I\3I\3I\3I\5I\u0545\nI\3I\3I\3I\3I\5I\u054b\nI\3I\3I\3I\3"+
		"I\5I\u0551\nI\5I\u0553\nI\3I\5I\u0556\nI\3J\3J\3J\3J\3J\3J\3J\5J\u055f"+
		"\nJ\3J\3J\3J\3J\3J\3J\3J\5J\u0568\nJ\7J\u056a\nJ\fJ\16J\u056d\13J\3J\5"+
		"J\u0570\nJ\3J\3J\3K\3K\3K\3K\3K\3K\3K\3K\3K\3K\7K\u057e\nK\fK\16K\u0581"+
		"\13K\3K\5K\u0584\nK\5K\u0586\nK\3K\5K\u0589\nK\3L\3L\3L\3L\3L\3L\3L\3"+
		"L\3L\3L\5L\u0595\nL\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\5L\u05a4\n"+
		"L\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\5L\u05b1\nL\7L\u05b3\nL\fL\16L\u05b6"+
		"\13L\3L\5L\u05b9\nL\3L\3L\5L\u05bd\nL\3M\3M\3M\3M\3M\3M\3M\5M\u05c6\n"+
		"M\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\5M\u05d2\nM\3M\3M\3M\3M\3M\3M\3M\3M\5"+
		"M\u05dc\nM\7M\u05de\nM\fM\16M\u05e1\13M\3M\3M\3M\5M\u05e6\nM\3M\3M\5M"+
		"\u05ea\nM\3N\3N\3N\3N\3N\3N\3N\3N\5N\u05f4\nN\3N\3N\3N\3N\3N\3N\3O\3O"+
		"\3O\3O\7O\u0600\nO\fO\16O\u0603\13O\3O\5O\u0606\nO\5O\u0608\nO\3O\3O\3"+
		"P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\5"+
		"P\u0623\nP\3Q\3Q\3Q\5Q\u0628\nQ\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\3Q\7"+
		"Q\u0636\nQ\fQ\16Q\u0639\13Q\3Q\3Q\3Q\3Q\5Q\u063f\nQ\3Q\3Q\3R\3R\3S\3S"+
		"\3S\3S\3S\3S\3S\3S\3S\5S\u064e\nS\3S\3S\3S\2\2T\2\4\6\b\n\f\16\20\22\24"+
		"\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtv"+
		"xz|~\u0080\u0082\u0084\u0086\u0088\u008a\u008c\u008e\u0090\u0092\u0094"+
		"\u0096\u0098\u009a\u009c\u009e\u00a0\u00a2\u00a4\2\3\3\2S_\2\u06fa\2\u00a6"+
		"\3\2\2\2\4\u00b2\3\2\2\2\6\u00bf\3\2\2\2\b\u00cb\3\2\2\2\n\u00d8\3\2\2"+
		"\2\f\u00e0\3\2\2\2\16\u00e9\3\2\2\2\20\u00ec\3\2\2\2\22\u00f8\3\2\2\2"+
		"\24\u0169\3\2\2\2\26\u016b\3\2\2\2\30\u017b\3\2\2\2\32\u0189\3\2\2\2\34"+
		"\u0194\3\2\2\2\36\u0202\3\2\2\2 \u020c\3\2\2\2\"\u0214\3\2\2\2$\u021a"+
		"\3\2\2\2&\u021c\3\2\2\2(\u0232\3\2\2\2*\u0234\3\2\2\2,\u0265\3\2\2\2."+
		"\u0287\3\2\2\2\60\u0289\3\2\2\2\62\u0294\3\2\2\2\64\u0296\3\2\2\2\66\u029f"+
		"\3\2\2\28\u02a2\3\2\2\2:\u02b2\3\2\2\2<\u02b4\3\2\2\2>\u02b7\3\2\2\2@"+
		"\u02de\3\2\2\2B\u02e7\3\2\2\2D\u02f8\3\2\2\2F\u0302\3\2\2\2H\u0309\3\2"+
		"\2\2J\u0311\3\2\2\2L\u031f\3\2\2\2N\u032d\3\2\2\2P\u0341\3\2\2\2R\u0343"+
		"\3\2\2\2T\u0349\3\2\2\2V\u035d\3\2\2\2X\u035f\3\2\2\2Z\u036e\3\2\2\2\\"+
		"\u037f\3\2\2\2^\u039d\3\2\2\2`\u03ac\3\2\2\2b\u03b0\3\2\2\2d\u03c5\3\2"+
		"\2\2f\u03e1\3\2\2\2h\u03e9\3\2\2\2j\u03eb\3\2\2\2l\u03f8\3\2\2\2n\u0405"+
		"\3\2\2\2p\u0415\3\2\2\2r\u042c\3\2\2\2t\u042e\3\2\2\2v\u0455\3\2\2\2x"+
		"\u0457\3\2\2\2z\u045b\3\2\2\2|\u0466\3\2\2\2~\u0471\3\2\2\2\u0080\u047c"+
		"\3\2\2\2\u0082\u048d\3\2\2\2\u0084\u049e\3\2\2\2\u0086\u04c4\3\2\2\2\u0088"+
		"\u04c6\3\2\2\2\u008a\u04cf\3\2\2\2\u008c\u0524\3\2\2\2\u008e\u0526\3\2"+
		"\2\2\u0090\u0555\3\2\2\2\u0092\u0557\3\2\2\2\u0094\u0573\3\2\2\2\u0096"+
		"\u05bc\3\2\2\2\u0098\u05e9\3\2\2\2\u009a\u05eb\3\2\2\2\u009c\u05fb\3\2"+
		"\2\2\u009e\u0622\3\2\2\2\u00a0\u0624\3\2\2\2\u00a2\u0642\3\2\2\2\u00a4"+
		"\u0644\3\2\2\2\u00a6\u00a7\b\2\1\2\u00a7\u00ad\b\2\1\2\u00a8\u00ae\7\'"+
		"\2\2\u00a9\u00ae\5&\24\2\u00aa\u00ab\5P)\2\u00ab\u00ac\7\'\2\2\u00ac\u00ae"+
		"\3\2\2\2\u00ad\u00a8\3\2\2\2\u00ad\u00a9\3\2\2\2\u00ad\u00aa\3\2\2\2\u00ae"+
		"\u00af\3\2\2\2\u00af\u00b0\b\2\1\2\u00b0\u00b1\b\2\1\2\u00b1\3\3\2\2\2"+
		"\u00b2\u00b3\b\3\1\2\u00b3\u00b8\b\3\1\2\u00b4\u00b7\7\'\2\2\u00b5\u00b7"+
		"\5$\23\2\u00b6\u00b4\3\2\2\2\u00b6\u00b5\3\2\2\2\u00b7\u00ba\3\2\2\2\u00b8"+
		"\u00b6\3\2\2\2\u00b8\u00b9\3\2\2\2\u00b9\u00bb\3\2\2\2\u00ba\u00b8\3\2"+
		"\2\2\u00bb\u00bc\7\2\2\3\u00bc\u00bd\b\3\1\2\u00bd\u00be\b\3\1\2\u00be"+
		"\5\3\2\2\2\u00bf\u00c0\b\4\1\2\u00c0\u00c4\5\u0094K\2\u00c1\u00c3\7\'"+
		"\2\2\u00c2\u00c1\3\2\2\2\u00c3\u00c6\3\2\2\2\u00c4\u00c2\3\2\2\2\u00c4"+
		"\u00c5\3\2\2\2\u00c5\u00c7\3\2\2\2\u00c6\u00c4\3\2\2\2\u00c7\u00c8\7\2"+
		"\2\3\u00c8\u00c9\b\4\1\2\u00c9\u00ca\b\4\1\2\u00ca\7\3\2\2\2\u00cb\u00cc"+
		"\b\5\1\2\u00cc\u00cd\7Q\2\2\u00cd\u00d3\5D#\2\u00ce\u00cf\7\64\2\2\u00cf"+
		"\u00d0\5\u009cO\2\u00d0\u00d1\7\65\2\2\u00d1\u00d2\b\5\1\2\u00d2\u00d4"+
		"\3\2\2\2\u00d3\u00ce\3\2\2\2\u00d3\u00d4\3\2\2\2\u00d4\u00d5\3\2\2\2\u00d5"+
		"\u00d6\7\'\2\2\u00d6\u00d7\b\5\1\2\u00d7\t\3\2\2\2\u00d8\u00da\b\6\1\2"+
		"\u00d9\u00db\5\b\5\2\u00da\u00d9\3\2\2\2\u00db\u00dc\3\2\2\2\u00dc\u00da"+
		"\3\2\2\2\u00dc\u00dd\3\2\2\2\u00dd\u00de\3\2\2\2\u00de\u00df\b\6\1\2\u00df"+
		"\13\3\2\2\2\u00e0\u00e1\b\7\1\2\u00e1\u00e5\5\n\6\2\u00e2\u00e6\5\u009a"+
		"N\2\u00e3\u00e6\5\20\t\2\u00e4\u00e6\5\16\b\2\u00e5\u00e2\3\2\2\2\u00e5"+
		"\u00e3\3\2\2\2\u00e5\u00e4\3\2\2\2\u00e6\u00e7\3\2\2\2\u00e7\u00e8\b\7"+
		"\1\2\u00e8\r\3\2\2\2\u00e9\u00ea\7%\2\2\u00ea\u00eb\5\20\t\2\u00eb\17"+
		"\3\2\2\2\u00ec\u00ed\7\4\2\2\u00ed\u00ee\7(\2\2\u00ee\u00f1\5\22\n\2\u00ef"+
		"\u00f0\7R\2\2\u00f0\u00f2\5f\64\2\u00f1\u00ef\3\2\2\2\u00f1\u00f2\3\2"+
		"\2\2\u00f2\u00f3\3\2\2\2\u00f3\u00f4\7\67\2\2\u00f4\u00f5\b\t\1\2\u00f5"+
		"\u00f6\5d\63\2\u00f6\u00f7\b\t\1\2\u00f7\21\3\2\2\2\u00f8\u00f9\b\n\1"+
		"\2\u00f9\u00fb\7\64\2\2\u00fa\u00fc\5\24\13\2\u00fb\u00fa\3\2\2\2\u00fb"+
		"\u00fc\3\2\2\2\u00fc\u00fd\3\2\2\2\u00fd\u00fe\7\65\2\2\u00fe\u00ff\b"+
		"\n\1\2\u00ff\23\3\2\2\2\u0100\u0105\5\26\f\2\u0101\u0102\7\66\2\2\u0102"+
		"\u0104\5\26\f\2\u0103\u0101\3\2\2\2\u0104\u0107\3\2\2\2\u0105\u0103\3"+
		"\2\2\2\u0105\u0106\3\2\2\2\u0106\u0108\3\2\2\2\u0107\u0105\3\2\2\2\u0108"+
		"\u0109\7\66\2\2\u0109\u010a\7D\2\2\u010a\u0130\b\13\1\2\u010b\u010c\7"+
		"\66\2\2\u010c\u0111\5\26\f\2\u010d\u010e\7\66\2\2\u010e\u0110\5\26\f\2"+
		"\u010f\u010d\3\2\2\2\u0110\u0113\3\2\2\2\u0111\u010f\3\2\2\2\u0111\u0112"+
		"\3\2\2\2\u0112\u0115\3\2\2\2\u0113\u0111\3\2\2\2\u0114\u010b\3\2\2\2\u0114"+
		"\u0115\3\2\2\2\u0115\u012e\3\2\2\2\u0116\u012c\7\66\2\2\u0117\u011c\5"+
		"\30\r\2\u0118\u0119\7\66\2\2\u0119\u011b\5\26\f\2\u011a\u0118\3\2\2\2"+
		"\u011b\u011e\3\2\2\2\u011c\u011a\3\2\2\2\u011c\u011d\3\2\2\2\u011d\u0126"+
		"\3\2\2\2\u011e\u011c\3\2\2\2\u011f\u0124\7\66\2\2\u0120\u0122\5\32\16"+
		"\2\u0121\u0123\7\66\2\2\u0122\u0121\3\2\2\2\u0122\u0123\3\2\2\2\u0123"+
		"\u0125\3\2\2\2\u0124\u0120\3\2\2\2\u0124\u0125\3\2\2\2\u0125\u0127\3\2"+
		"\2\2\u0126\u011f\3\2\2\2\u0126\u0127\3\2\2\2\u0127\u012d\3\2\2\2\u0128"+
		"\u012a\5\32\16\2\u0129\u012b\7\66\2\2\u012a\u0129\3\2\2\2\u012a\u012b"+
		"\3\2\2\2\u012b\u012d\3\2\2\2\u012c\u0117\3\2\2\2\u012c\u0128\3\2\2\2\u012c"+
		"\u012d\3\2\2\2\u012d\u012f\3\2\2\2\u012e\u0116\3\2\2\2\u012e\u012f\3\2"+
		"\2\2\u012f\u0131\3\2\2\2\u0130\u0114\3\2\2\2\u0130\u0131\3\2\2\2\u0131"+
		"\u016a\3\2\2\2\u0132\u0137\5\26\f\2\u0133\u0134\7\66\2\2\u0134\u0136\5"+
		"\26\f\2\u0135\u0133\3\2\2\2\u0136\u0139\3\2\2\2\u0137\u0135\3\2\2\2\u0137"+
		"\u0138\3\2\2\2\u0138\u0152\3\2\2\2\u0139\u0137\3\2\2\2\u013a\u0150\7\66"+
		"\2\2\u013b\u0140\5\30\r\2\u013c\u013d\7\66\2\2\u013d\u013f\5\26\f\2\u013e"+
		"\u013c\3\2\2\2\u013f\u0142\3\2\2\2\u0140\u013e\3\2\2\2\u0140\u0141\3\2"+
		"\2\2\u0141\u014a\3\2\2\2\u0142\u0140\3\2\2\2\u0143\u0148\7\66\2\2\u0144"+
		"\u0146\5\32\16\2\u0145\u0147\7\66\2\2\u0146\u0145\3\2\2\2\u0146\u0147"+
		"\3\2\2\2\u0147\u0149\3\2\2\2\u0148\u0144\3\2\2\2\u0148\u0149\3\2\2\2\u0149"+
		"\u014b\3\2\2\2\u014a\u0143\3\2\2\2\u014a\u014b\3\2\2\2\u014b\u0151\3\2"+
		"\2\2\u014c\u014e\5\32\16\2\u014d\u014f\7\66\2\2\u014e\u014d\3\2\2\2\u014e"+
		"\u014f\3\2\2\2\u014f\u0151\3\2\2\2\u0150\u013b\3\2\2\2\u0150\u014c\3\2"+
		"\2\2\u0150\u0151\3\2\2\2\u0151\u0153\3\2\2\2\u0152\u013a\3\2\2\2\u0152"+
		"\u0153\3\2\2\2\u0153\u016a\3\2\2\2\u0154\u0159\5\30\r\2\u0155\u0156\7"+
		"\66\2\2\u0156\u0158\5\26\f\2\u0157\u0155\3\2\2\2\u0158\u015b\3\2\2\2\u0159"+
		"\u0157\3\2\2\2\u0159\u015a\3\2\2\2\u015a\u0163\3\2\2\2\u015b\u0159\3\2"+
		"\2\2\u015c\u0161\7\66\2\2\u015d\u015f\5\32\16\2\u015e\u0160\7\66\2\2\u015f"+
		"\u015e\3\2\2\2\u015f\u0160\3\2\2\2\u0160\u0162\3\2\2\2\u0161\u015d\3\2"+
		"\2\2\u0161\u0162\3\2\2\2\u0162\u0164\3\2\2\2\u0163\u015c\3\2\2\2\u0163"+
		"\u0164\3\2\2\2\u0164\u016a\3\2\2\2\u0165\u0167\5\32\16\2\u0166\u0168\7"+
		"\66\2\2\u0167\u0166\3\2\2\2\u0167\u0168\3\2\2\2\u0168\u016a\3\2\2\2\u0169"+
		"\u0100\3\2\2\2\u0169\u0132\3\2\2\2\u0169\u0154\3\2\2\2\u0169\u0165\3\2"+
		"\2\2\u016a\25\3\2\2\2\u016b\u016c\7(\2\2\u016c\u0171\b\f\1\2\u016d\u016e"+
		"\7\67\2\2\u016e\u016f\5f\64\2\u016f\u0170\b\f\1\2\u0170\u0172\3\2\2\2"+
		"\u0171\u016d\3\2\2\2\u0171\u0172\3\2\2\2\u0172\u0177\3\2\2\2\u0173\u0174"+
		"\7:\2\2\u0174\u0175\5f\64\2\u0175\u0176\b\f\1\2\u0176\u0178\3\2\2\2\u0177"+
		"\u0173\3\2\2\2\u0177\u0178\3\2\2\2\u0178\u0179\3\2\2\2\u0179\u017a\b\f"+
		"\1\2\u017a\27\3\2\2\2\u017b\u017c\7\63\2\2\u017c\u0185\b\r\1\2\u017d\u017e"+
		"\7(\2\2\u017e\u0183\b\r\1\2\u017f\u0180\7\67\2\2\u0180\u0181\5f\64\2\u0181"+
		"\u0182\b\r\1\2\u0182\u0184\3\2\2\2\u0183\u017f\3\2\2\2\u0183\u0184\3\2"+
		"\2\2\u0184\u0186\3\2\2\2\u0185\u017d\3\2\2\2\u0185\u0186\3\2\2\2\u0186"+
		"\u0187\3\2\2\2\u0187\u0188\b\r\1\2\u0188\31\3\2\2\2\u0189\u018a\79\2\2"+
		"\u018a\u018b\7(\2\2\u018b\u0190\b\16\1\2\u018c\u018d\7\67\2\2\u018d\u018e"+
		"\5f\64\2\u018e\u018f\b\16\1\2\u018f\u0191\3\2\2\2\u0190\u018c\3\2\2\2"+
		"\u0190\u0191\3\2\2\2\u0191\u0192\3\2\2\2\u0192\u0193\b\16\1\2\u0193\33"+
		"\3\2\2\2\u0194\u01fe\b\17\1\2\u0195\u019a\5\36\20\2\u0196\u0197\7\66\2"+
		"\2\u0197\u0199\5\36\20\2\u0198\u0196\3\2\2\2\u0199\u019c\3\2\2\2\u019a"+
		"\u0198\3\2\2\2\u019a\u019b\3\2\2\2\u019b\u019d\3\2\2\2\u019c\u019a\3\2"+
		"\2\2\u019d\u019e\7\66\2\2\u019e\u019f\7D\2\2\u019f\u01c5\b\17\1\2\u01a0"+
		"\u01a1\7\66\2\2\u01a1\u01a6\5\36\20\2\u01a2\u01a3\7\66\2\2\u01a3\u01a5"+
		"\5\36\20\2\u01a4\u01a2\3\2\2\2\u01a5\u01a8\3\2\2\2\u01a6\u01a4\3\2\2\2"+
		"\u01a6\u01a7\3\2\2\2\u01a7\u01aa\3\2\2\2\u01a8\u01a6\3\2\2\2\u01a9\u01a0"+
		"\3\2\2\2\u01a9\u01aa\3\2\2\2\u01aa\u01c3\3\2\2\2\u01ab\u01c1\7\66\2\2"+
		"\u01ac\u01b1\5 \21\2\u01ad\u01ae\7\66\2\2\u01ae\u01b0\5\36\20\2\u01af"+
		"\u01ad\3\2\2\2\u01b0\u01b3\3\2\2\2\u01b1\u01af\3\2\2\2\u01b1\u01b2\3\2"+
		"\2\2\u01b2\u01bb\3\2\2\2\u01b3\u01b1\3\2\2\2\u01b4\u01b9\7\66\2\2\u01b5"+
		"\u01b7\5\"\22\2\u01b6\u01b8\7\66\2\2\u01b7\u01b6\3\2\2\2\u01b7\u01b8\3"+
		"\2\2\2\u01b8\u01ba\3\2\2\2\u01b9\u01b5\3\2\2\2\u01b9\u01ba\3\2\2\2\u01ba"+
		"\u01bc\3\2\2\2\u01bb\u01b4\3\2\2\2\u01bb\u01bc\3\2\2\2\u01bc\u01c2\3\2"+
		"\2\2\u01bd\u01bf\5\"\22\2\u01be\u01c0\7\66\2\2\u01bf\u01be\3\2\2\2\u01bf"+
		"\u01c0\3\2\2\2\u01c0\u01c2\3\2\2\2\u01c1\u01ac\3\2\2\2\u01c1\u01bd\3\2"+
		"\2\2\u01c1\u01c2\3\2\2\2\u01c2\u01c4\3\2\2\2\u01c3\u01ab\3\2\2\2\u01c3"+
		"\u01c4\3\2\2\2\u01c4\u01c6\3\2\2\2\u01c5\u01a9\3\2\2\2\u01c5\u01c6\3\2"+
		"\2\2\u01c6\u01ff\3\2\2\2\u01c7\u01cc\5\36\20\2\u01c8\u01c9\7\66\2\2\u01c9"+
		"\u01cb\5\36\20\2\u01ca\u01c8\3\2\2\2\u01cb\u01ce\3\2\2\2\u01cc\u01ca\3"+
		"\2\2\2\u01cc\u01cd\3\2\2\2\u01cd\u01e7\3\2\2\2\u01ce\u01cc\3\2\2\2\u01cf"+
		"\u01e5\7\66\2\2\u01d0\u01d5\5 \21\2\u01d1\u01d2\7\66\2\2\u01d2\u01d4\5"+
		"\36\20\2\u01d3\u01d1\3\2\2\2\u01d4\u01d7\3\2\2\2\u01d5\u01d3\3\2\2\2\u01d5"+
		"\u01d6\3\2\2\2\u01d6\u01df\3\2\2\2\u01d7\u01d5\3\2\2\2\u01d8\u01dd\7\66"+
		"\2\2\u01d9\u01db\5\"\22\2\u01da\u01dc\7\66\2\2\u01db\u01da\3\2\2\2\u01db"+
		"\u01dc\3\2\2\2\u01dc\u01de\3\2\2\2\u01dd\u01d9\3\2\2\2\u01dd\u01de\3\2"+
		"\2\2\u01de\u01e0\3\2\2\2\u01df\u01d8\3\2\2\2\u01df\u01e0\3\2\2\2\u01e0"+
		"\u01e6\3\2\2\2\u01e1\u01e3\5\"\22\2\u01e2\u01e4\7\66\2\2\u01e3\u01e2\3"+
		"\2\2\2\u01e3\u01e4\3\2\2\2\u01e4\u01e6\3\2\2\2\u01e5\u01d0\3\2\2\2\u01e5"+
		"\u01e1\3\2\2\2\u01e5\u01e6\3\2\2\2\u01e6\u01e8\3\2\2\2\u01e7\u01cf\3\2"+
		"\2\2\u01e7\u01e8\3\2\2\2\u01e8\u01ff\3\2\2\2\u01e9\u01ee\5 \21\2\u01ea"+
		"\u01eb\7\66\2\2\u01eb\u01ed\5\36\20\2\u01ec\u01ea\3\2\2\2\u01ed\u01f0"+
		"\3\2\2\2\u01ee\u01ec\3\2\2\2\u01ee\u01ef\3\2\2\2\u01ef\u01f8\3\2\2\2\u01f0"+
		"\u01ee\3\2\2\2\u01f1\u01f6\7\66\2\2\u01f2\u01f4\5\"\22\2\u01f3\u01f5\7"+
		"\66\2\2\u01f4\u01f3\3\2\2\2\u01f4\u01f5\3\2\2\2\u01f5\u01f7\3\2\2\2\u01f6"+
		"\u01f2\3\2\2\2\u01f6\u01f7\3\2\2\2\u01f7\u01f9\3\2\2\2\u01f8\u01f1\3\2"+
		"\2\2\u01f8\u01f9\3\2\2\2\u01f9\u01ff\3\2\2\2\u01fa\u01fc\5\"\22\2\u01fb"+
		"\u01fd\7\66\2\2\u01fc\u01fb\3\2\2\2\u01fc\u01fd\3\2\2\2\u01fd\u01ff\3"+
		"\2\2\2\u01fe\u0195\3\2\2\2\u01fe\u01c7\3\2\2\2\u01fe\u01e9\3\2\2\2\u01fe"+
		"\u01fa\3\2\2\2\u01ff\u0200\3\2\2\2\u0200\u0201\b\17\1\2\u0201\35\3\2\2"+
		"\2\u0202\u0203\7(\2\2\u0203\u0208\b\20\1\2\u0204\u0205\7:\2\2\u0205\u0206"+
		"\5f\64\2\u0206\u0207\b\20\1\2\u0207\u0209\3\2\2\2\u0208\u0204\3\2\2\2"+
		"\u0208\u0209\3\2\2\2\u0209\u020a\3\2\2\2\u020a\u020b\b\20\1\2\u020b\37"+
		"\3\2\2\2\u020c\u020d\7\63\2\2\u020d\u0210\b\21\1\2\u020e\u020f\7(\2\2"+
		"\u020f\u0211\b\21\1\2\u0210\u020e\3\2\2\2\u0210\u0211\3\2\2\2\u0211\u0212"+
		"\3\2\2\2\u0212\u0213\b\21\1\2\u0213!\3\2\2\2\u0214\u0215\79\2\2\u0215"+
		"\u0216\7(\2\2\u0216\u0217\b\22\1\2\u0217#\3\2\2\2\u0218\u021b\5&\24\2"+
		"\u0219\u021b\5P)\2\u021a\u0218\3\2\2\2\u021a\u0219\3\2\2\2\u021b%\3\2"+
		"\2\2\u021c\u0221\5(\25\2\u021d\u021e\78\2\2\u021e\u0220\5(\25\2\u021f"+
		"\u021d\3\2\2\2\u0220\u0223\3\2\2\2\u0221\u021f\3\2\2\2\u0221\u0222\3\2"+
		"\2\2\u0222\u0225\3\2\2\2\u0223\u0221\3\2\2\2\u0224\u0226\78\2\2\u0225"+
		"\u0224\3\2\2\2\u0225\u0226\3\2\2\2\u0226\u0227\3\2\2\2\u0227\u0228\7\'"+
		"\2\2\u0228\'\3\2\2\2\u0229\u0233\5*\26\2\u022a\u0233\5\60\31\2\u022b\u022c"+
		"\7\"\2\2\u022c\u0233\b\25\1\2\u022d\u0233\5\62\32\2\u022e\u0233\5:\36"+
		"\2\u022f\u0233\5J&\2\u0230\u0233\5L\'\2\u0231\u0233\5N(\2\u0232\u0229"+
		"\3\2\2\2\u0232\u022a\3\2\2\2\u0232\u022b\3\2\2\2\u0232\u022d\3\2\2\2\u0232"+
		"\u022e\3\2\2\2\u0232\u022f\3\2\2\2\u0232\u0230\3\2\2\2\u0232\u0231\3\2"+
		"\2\2\u0233)\3\2\2\2\u0234\u0235\5,\27\2\u0235\u025d\b\26\1\2\u0236\u0237"+
		"\7\67\2\2\u0237\u023c\5f\64\2\u0238\u0239\7:\2\2\u0239\u023a\5f\64\2\u023a"+
		"\u023b\b\26\1\2\u023b\u023d\3\2\2\2\u023c\u0238\3\2\2\2\u023c\u023d\3"+
		"\2\2\2\u023d\u023e\3\2\2\2\u023e\u023f\b\26\1\2\u023f\u025e\3\2\2\2\u0240"+
		"\u0247\5.\30\2\u0241\u0242\5\u00a4S\2\u0242\u0243\b\26\1\2\u0243\u0248"+
		"\3\2\2\2\u0244\u0245\5\u0094K\2\u0245\u0246\b\26\1\2\u0246\u0248\3\2\2"+
		"\2\u0247\u0241\3\2\2\2\u0247\u0244\3\2\2\2\u0248\u0249\3\2\2\2\u0249\u024a"+
		"\b\26\1\2\u024a\u025e\3\2\2\2\u024b\u024c\b\26\1\2\u024c\u0259\b\26\1"+
		"\2\u024d\u024e\7:\2\2\u024e\u0255\b\26\1\2\u024f\u0250\5\u00a4S\2\u0250"+
		"\u0251\b\26\1\2\u0251\u0256\3\2\2\2\u0252\u0253\5,\27\2\u0253\u0254\b"+
		"\26\1\2\u0254\u0256\3\2\2\2\u0255\u024f\3\2\2\2\u0255\u0252\3\2\2\2\u0256"+
		"\u0258\3\2\2\2\u0257\u024d\3\2\2\2\u0258\u025b\3\2\2\2\u0259\u0257\3\2"+
		"\2\2\u0259\u025a\3\2\2\2\u025a\u025c\3\2\2\2\u025b\u0259\3\2\2\2\u025c"+
		"\u025e\b\26\1\2\u025d\u0236\3\2\2\2\u025d\u0240\3\2\2\2\u025d\u024b\3"+
		"\2\2\2\u025e+\3\2\2\2\u025f\u0260\5f\64\2\u0260\u0261\b\27\1\2\u0261\u0266"+
		"\3\2\2\2\u0262\u0263\5x=\2\u0263\u0264\b\27\1\2\u0264\u0266\3\2\2\2\u0265"+
		"\u025f\3\2\2\2\u0265\u0262\3\2\2\2\u0266\u0285\3\2\2\2\u0267\u0268\b\27"+
		"\1\2\u0268\u0282\7\66\2\2\u0269\u026a\5f\64\2\u026a\u026b\b\27\1\2\u026b"+
		"\u0270\3\2\2\2\u026c\u026d\5x=\2\u026d\u026e\b\27\1\2\u026e\u0270\3\2"+
		"\2\2\u026f\u0269\3\2\2\2\u026f\u026c\3\2\2\2\u0270\u027c\3\2\2\2\u0271"+
		"\u0278\7\66\2\2\u0272\u0273\5f\64\2\u0273\u0274\b\27\1\2\u0274\u0279\3"+
		"\2\2\2\u0275\u0276\5x=\2\u0276\u0277\b\27\1\2\u0277\u0279\3\2\2\2\u0278"+
		"\u0272\3\2\2\2\u0278\u0275\3\2\2\2\u0279\u027b\3\2\2\2\u027a\u0271\3\2"+
		"\2\2\u027b\u027e\3\2\2\2\u027c\u027a\3\2\2\2\u027c\u027d\3\2\2\2\u027d"+
		"\u0280\3\2\2\2\u027e\u027c\3\2\2\2\u027f\u0281\7\66\2\2\u0280\u027f\3"+
		"\2\2\2\u0280\u0281\3\2\2\2\u0281\u0283\3\2\2\2\u0282\u026f\3\2\2\2\u0282"+
		"\u0283\3\2\2\2\u0283\u0284\3\2\2\2\u0284\u0286\b\27\1\2\u0285\u0267\3"+
		"\2\2\2\u0285\u0286\3\2\2\2\u0286-\3\2\2\2\u0287\u0288\t\2\2\2\u0288/\3"+
		"\2\2\2\u0289\u028a\7!\2\2\u028a\u028b\5\u0092J\2\u028b\u028c\b\31\1\2"+
		"\u028c\61\3\2\2\2\u028d\u028e\7$\2\2\u028e\u0295\b\32\1\2\u028f\u0290"+
		"\7#\2\2\u0290\u0295\b\32\1\2\u0291\u0295\5\64\33\2\u0292\u0295\58\35\2"+
		"\u0293\u0295\5\66\34\2\u0294\u028d\3\2\2\2\u0294\u028f\3\2\2\2\u0294\u0291"+
		"\3\2\2\2\u0294\u0292\3\2\2\2\u0294\u0293\3\2\2\2\u0295\63\3\2\2\2\u0296"+
		"\u0297\7\5\2\2\u0297\u029b\b\33\1\2\u0298\u0299\5\u0094K\2\u0299\u029a"+
		"\b\33\1\2\u029a\u029c\3\2\2\2\u029b\u0298\3\2\2\2\u029b\u029c\3\2\2\2"+
		"\u029c\u029d\3\2\2\2\u029d\u029e\b\33\1\2\u029e\65\3\2\2\2\u029f\u02a0"+
		"\5\u00a4S\2\u02a0\u02a1\b\34\1\2\u02a1\67\3\2\2\2\u02a2\u02a3\b\35\1\2"+
		"\u02a3\u02ac\7\6\2\2\u02a4\u02a5\5f\64\2\u02a5\u02aa\b\35\1\2\u02a6\u02a7"+
		"\7\7\2\2\u02a7\u02a8\5f\64\2\u02a8\u02a9\b\35\1\2\u02a9\u02ab\3\2\2\2"+
		"\u02aa\u02a6\3\2\2\2\u02aa\u02ab\3\2\2\2\u02ab\u02ad\3\2\2\2\u02ac\u02a4"+
		"\3\2\2\2\u02ac\u02ad\3\2\2\2\u02ad\u02ae\3\2\2\2\u02ae\u02af\b\35\1\2"+
		"\u02af9\3\2\2\2\u02b0\u02b3\5<\37\2\u02b1\u02b3\5> \2\u02b2\u02b0\3\2"+
		"\2\2\u02b2\u02b1\3\2\2\2\u02b3;\3\2\2\2\u02b4\u02b5\7\b\2\2\u02b5\u02b6"+
		"\5H%\2\u02b6=\3\2\2\2\u02b7\u02b8\7\7\2\2\u02b8\u02cd\b \1\2\u02b9\u02ba"+
		"\7\61\2\2\u02ba\u02be\b \1\2\u02bb\u02bc\7\62\2\2\u02bc\u02be\b \1\2\u02bd"+
		"\u02b9\3\2\2\2\u02bd\u02bb\3\2\2\2\u02be\u02c1\3\2\2\2\u02bf\u02bd\3\2"+
		"\2\2\u02bf\u02c0\3\2\2\2\u02c0\u02c2\3\2\2\2\u02c1\u02bf\3\2\2\2\u02c2"+
		"\u02c3\5D#\2\u02c3\u02c4\b \1\2\u02c4\u02ce\3\2\2\2\u02c5\u02c6\7\61\2"+
		"\2\u02c6\u02ca\b \1\2\u02c7\u02c8\7\62\2\2\u02c8\u02ca\b \1\2\u02c9\u02c5"+
		"\3\2\2\2\u02c9\u02c7\3\2\2\2\u02ca\u02cb\3\2\2\2\u02cb\u02c9\3\2\2\2\u02cb"+
		"\u02cc\3\2\2\2\u02cc\u02ce\3\2\2\2\u02cd\u02bf\3\2\2\2\u02cd\u02c9\3\2"+
		"\2\2\u02ce\u02cf\3\2\2\2\u02cf\u02d0\7\b\2\2\u02d0\u02da\b \1\2\u02d1"+
		"\u02db\7\63\2\2\u02d2\u02d3\7\64\2\2\u02d3\u02d4\5B\"\2\u02d4\u02d5\b"+
		" \1\2\u02d5\u02d6\7\65\2\2\u02d6\u02db\3\2\2\2\u02d7\u02d8\5B\"\2\u02d8"+
		"\u02d9\b \1\2\u02d9\u02db\3\2\2\2\u02da\u02d1\3\2\2\2\u02da\u02d2\3\2"+
		"\2\2\u02da\u02d7\3\2\2\2\u02db\u02dc\3\2\2\2\u02dc\u02dd\b \1\2\u02dd"+
		"?\3\2\2\2\u02de\u02df\7(\2\2\u02df\u02e3\b!\1\2\u02e0\u02e1\7\t\2\2\u02e1"+
		"\u02e2\7(\2\2\u02e2\u02e4\b!\1\2\u02e3\u02e0\3\2\2\2\u02e3\u02e4\3\2\2"+
		"\2\u02e4\u02e5\3\2\2\2\u02e5\u02e6\b!\1\2\u02e6A\3\2\2\2\u02e7\u02e8\b"+
		"\"\1\2\u02e8\u02e9\5@!\2\u02e9\u02f0\b\"\1\2\u02ea\u02eb\7\66\2\2\u02eb"+
		"\u02ec\5@!\2\u02ec\u02ed\b\"\1\2\u02ed\u02ef\3\2\2\2\u02ee\u02ea\3\2\2"+
		"\2\u02ef\u02f2\3\2\2\2\u02f0\u02ee\3\2\2\2\u02f0\u02f1\3\2\2\2\u02f1\u02f4"+
		"\3\2\2\2\u02f2\u02f0\3\2\2\2\u02f3\u02f5\7\66\2\2\u02f4\u02f3\3\2\2\2"+
		"\u02f4\u02f5\3\2\2\2\u02f5\u02f6\3\2\2\2\u02f6\u02f7\b\"\1\2\u02f7C\3"+
		"\2\2\2\u02f8\u02f9\7(\2\2\u02f9\u02ff\b#\1\2\u02fa\u02fb\7\61\2\2\u02fb"+
		"\u02fc\7(\2\2\u02fc\u02fe\b#\1\2\u02fd\u02fa\3\2\2\2\u02fe\u0301\3\2\2"+
		"\2\u02ff\u02fd\3\2\2\2\u02ff\u0300\3\2\2\2\u0300E\3\2\2\2\u0301\u02ff"+
		"\3\2\2\2\u0302\u0307\5D#\2\u0303\u0304\7\t\2\2\u0304\u0305\7(\2\2\u0305"+
		"\u0308\b$\1\2\u0306\u0308\b$\1\2\u0307\u0303\3\2\2\2\u0307\u0306\3\2\2"+
		"\2\u0308G\3\2\2\2\u0309\u030e\5F$\2\u030a\u030b\7\66\2\2\u030b\u030d\5"+
		"F$\2\u030c\u030a\3\2\2\2\u030d\u0310\3\2\2\2\u030e\u030c\3\2\2\2\u030e"+
		"\u030f\3\2\2\2\u030fI\3\2\2\2\u0310\u030e\3\2\2\2\u0311\u0312\b&\1\2\u0312"+
		"\u0313\7\n\2\2\u0313\u0314\7(\2\2\u0314\u031a\b&\1\2\u0315\u0316\7\66"+
		"\2\2\u0316\u0317\7(\2\2\u0317\u0319\b&\1\2\u0318\u0315\3\2\2\2\u0319\u031c"+
		"\3\2\2\2\u031a\u0318\3\2\2\2\u031a\u031b\3\2\2\2\u031b\u031d\3\2\2\2\u031c"+
		"\u031a\3\2\2\2\u031d\u031e\b&\1\2\u031eK\3\2\2\2\u031f\u0320\b\'\1\2\u0320"+
		"\u0321\7\13\2\2\u0321\u0322\7(\2\2\u0322\u0328\b\'\1\2\u0323\u0324\7\66"+
		"\2\2\u0324\u0325\7(\2\2\u0325\u0327\b\'\1\2\u0326\u0323\3\2\2\2\u0327"+
		"\u032a\3\2\2\2\u0328\u0326\3\2\2\2\u0328\u0329\3\2\2\2\u0329\u032b\3\2"+
		"\2\2\u032a\u0328\3\2\2\2\u032b\u032c\b\'\1\2\u032cM\3\2\2\2\u032d\u032e"+
		"\7\f\2\2\u032e\u032f\5f\64\2\u032f\u0334\b(\1\2\u0330\u0331\7\66\2\2\u0331"+
		"\u0332\5f\64\2\u0332\u0333\b(\1\2\u0333\u0335\3\2\2\2\u0334\u0330\3\2"+
		"\2\2\u0334\u0335\3\2\2\2\u0335\u0336\3\2\2\2\u0336\u0337\b(\1\2\u0337"+
		"O\3\2\2\2\u0338\u0342\5T+\2\u0339\u0342\5X-\2\u033a\u0342\5Z.\2\u033b"+
		"\u0342\5\\/\2\u033c\u0342\5`\61\2\u033d\u0342\5\20\t\2\u033e\u0342\5\u009a"+
		"N\2\u033f\u0342\5\f\7\2\u0340\u0342\5R*\2\u0341\u0338\3\2\2\2\u0341\u0339"+
		"\3\2\2\2\u0341\u033a\3\2\2\2\u0341\u033b\3\2\2\2\u0341\u033c\3\2\2\2\u0341"+
		"\u033d\3\2\2\2\u0341\u033e\3\2\2\2\u0341\u033f\3\2\2\2\u0341\u0340\3\2"+
		"\2\2\u0342Q\3\2\2\2\u0343\u0347\7%\2\2\u0344\u0348\5\20\t\2\u0345\u0348"+
		"\5`\61\2\u0346\u0348\5Z.\2\u0347\u0344\3\2\2\2\u0347\u0345\3\2\2\2\u0347"+
		"\u0346\3\2\2\2\u0348S\3\2\2\2\u0349\u034a\7\r\2\2\u034a\u034b\5f\64\2"+
		"\u034b\u034c\7\67\2\2\u034c\u034d\5d\63\2\u034d\u034e\5V,\2\u034e\u034f"+
		"\b+\1\2\u034fU\3\2\2\2\u0350\u0351\7\16\2\2\u0351\u0352\5f\64\2\u0352"+
		"\u0353\7\67\2\2\u0353\u0354\5d\63\2\u0354\u0355\5V,\2\u0355\u0356\b,\1"+
		"\2\u0356\u035e\3\2\2\2\u0357\u0358\7\17\2\2\u0358\u0359\7\67\2\2\u0359"+
		"\u035a\5d\63\2\u035a\u035b\b,\1\2\u035b\u035e\3\2\2\2\u035c\u035e\b,\1"+
		"\2\u035d\u0350\3\2\2\2\u035d\u0357\3\2\2\2\u035d\u035c\3\2\2\2\u035eW"+
		"\3\2\2\2\u035f\u0360\7\20\2\2\u0360\u0361\5f\64\2\u0361\u0362\7\67\2\2"+
		"\u0362\u0363\b-\1\2\u0363\u0364\5d\63\2\u0364\u036a\b-\1\2\u0365\u0366"+
		"\7\17\2\2\u0366\u0367\7\67\2\2\u0367\u0368\5d\63\2\u0368\u0369\b-\1\2"+
		"\u0369\u036b\3\2\2\2\u036a\u0365\3\2\2\2\u036a\u036b\3\2\2\2\u036b\u036c"+
		"\3\2\2\2\u036c\u036d\b-\1\2\u036dY\3\2\2\2\u036e\u036f\7\21\2\2\u036f"+
		"\u0370\5\u0092J\2\u0370\u0371\7\22\2\2\u0371\u0372\5\u0094K\2\u0372\u0373"+
		"\7\67\2\2\u0373\u0374\b.\1\2\u0374\u0375\5d\63\2\u0375\u037b\b.\1\2\u0376"+
		"\u0377\7\17\2\2\u0377\u0378\7\67\2\2\u0378\u0379\5d\63\2\u0379\u037a\b"+
		".\1\2\u037a\u037c\3\2\2\2\u037b\u0376\3\2\2\2\u037b\u037c\3\2\2\2\u037c"+
		"\u037d\3\2\2\2\u037d\u037e\b.\1\2\u037e[\3\2\2\2\u037f\u0380\7\23\2\2"+
		"\u0380\u0381\7\67\2\2\u0381\u0382\5d\63\2\u0382\u0383\b/\1\2\u0383\u0392"+
		"\b/\1\2\u0384\u0385\5^\60\2\u0385\u0386\b/\1\2\u0386\u0388\3\2\2\2\u0387"+
		"\u0384\3\2\2\2\u0388\u0389\3\2\2\2\u0389\u0387\3\2\2\2\u0389\u038a\3\2"+
		"\2\2\u038a\u0390\3\2\2\2\u038b\u038c\7\17\2\2\u038c\u038d\7\67\2\2\u038d"+
		"\u038e\5d\63\2\u038e\u038f\b/\1\2\u038f\u0391\3\2\2\2\u0390\u038b\3\2"+
		"\2\2\u0390\u0391\3\2\2\2\u0391\u0393\3\2\2\2\u0392\u0387\3\2\2\2\u0392"+
		"\u0393\3\2\2\2\u0393\u0399\3\2\2\2\u0394\u0395\7\24\2\2\u0395\u0396\7"+
		"\67\2\2\u0396\u0397\5d\63\2\u0397\u0398\b/\1\2\u0398\u039a\3\2\2\2\u0399"+
		"\u0394\3\2\2\2\u0399\u039a\3\2\2\2\u039a\u039b\3\2\2\2\u039b\u039c\b/"+
		"\1\2\u039c]\3\2\2\2\u039d\u039e\7\26\2\2\u039e\u03a6\b\60\1\2\u039f\u03a0"+
		"\5f\64\2\u03a0\u03a4\b\60\1\2\u03a1\u03a2\7\t\2\2\u03a2\u03a3\7(\2\2\u03a3"+
		"\u03a5\b\60\1\2\u03a4\u03a1\3\2\2\2\u03a4\u03a5\3\2\2\2\u03a5\u03a7\3"+
		"\2\2\2\u03a6\u039f\3\2\2\2\u03a6\u03a7\3\2\2\2\u03a7\u03a8\3\2\2\2\u03a8"+
		"\u03a9\7\67\2\2\u03a9\u03aa\5d\63\2\u03aa\u03ab\b\60\1\2\u03ab_\3\2\2"+
		"\2\u03ac\u03ad\7\25\2\2\u03ad\u03ae\5b\62\2\u03ae\u03af\b\61\1\2\u03af"+
		"a\3\2\2\2\u03b0\u03b1\5f\64\2\u03b1\u03b6\b\62\1\2\u03b2\u03b3\7\t\2\2"+
		"\u03b3\u03b4\5z>\2\u03b4\u03b5\b\62\1\2\u03b5\u03b7\3\2\2\2\u03b6\u03b2"+
		"\3\2\2\2\u03b6\u03b7\3\2\2\2\u03b7\u03b8\3\2\2\2\u03b8\u03c1\b\62\1\2"+
		"\u03b9\u03ba\7\66\2\2\u03ba\u03bb\5b\62\2\u03bb\u03bc\b\62\1\2\u03bc\u03c2"+
		"\3\2\2\2\u03bd\u03be\7\67\2\2\u03be\u03bf\5d\63\2\u03bf\u03c0\b\62\1\2"+
		"\u03c0\u03c2\3\2\2\2\u03c1\u03b9\3\2\2\2\u03c1\u03bd\3\2\2\2\u03c2\u03c3"+
		"\3\2\2\2\u03c3\u03c4\b\62\1\2\u03c4c\3\2\2\2\u03c5\u03d0\b\63\1\2\u03c6"+
		"\u03d1\5&\24\2\u03c7\u03c8\7\'\2\2\u03c8\u03ca\7b\2\2\u03c9\u03cb\5$\23"+
		"\2\u03ca\u03c9\3\2\2\2\u03cb\u03cc\3\2\2\2\u03cc\u03ca\3\2\2\2\u03cc\u03cd"+
		"\3\2\2\2\u03cd\u03ce\3\2\2\2\u03ce\u03cf\7c\2\2\u03cf\u03d1\3\2\2\2\u03d0"+
		"\u03c6\3\2\2\2\u03d0\u03c7\3\2\2\2\u03d1\u03d2\3\2\2\2\u03d2\u03d3\b\63"+
		"\1\2\u03d3e\3\2\2\2\u03d4\u03d5\5n8\2\u03d5\u03dc\b\64\1\2\u03d6\u03d7"+
		"\7\r\2\2\u03d7\u03d8\5n8\2\u03d8\u03d9\7\17\2\2\u03d9\u03da\5f\64\2\u03da"+
		"\u03db\b\64\1\2\u03db\u03dd\3\2\2\2\u03dc\u03d6\3\2\2\2\u03dc\u03dd\3"+
		"\2\2\2\u03dd\u03e2\3\2\2\2\u03de\u03df\5j\66\2\u03df\u03e0\b\64\1\2\u03e0"+
		"\u03e2\3\2\2\2\u03e1\u03d4\3\2\2\2\u03e1\u03de\3\2\2\2\u03e2g\3\2\2\2"+
		"\u03e3\u03e4\5n8\2\u03e4\u03e5\b\65\1\2\u03e5\u03ea\3\2\2\2\u03e6\u03e7"+
		"\5l\67\2\u03e7\u03e8\b\65\1\2\u03e8\u03ea\3\2\2\2\u03e9\u03e3\3\2\2\2"+
		"\u03e9\u03e6\3\2\2\2\u03eai\3\2\2\2\u03eb\u03ec\7\27\2\2\u03ec\u03f0\b"+
		"\66\1\2\u03ed\u03ee\5\34\17\2\u03ee\u03ef\b\66\1\2\u03ef\u03f1\3\2\2\2"+
		"\u03f0\u03ed\3\2\2\2\u03f0\u03f1\3\2\2\2\u03f1\u03f2\3\2\2\2\u03f2\u03f3"+
		"\b\66\1\2\u03f3\u03f4\7\67\2\2\u03f4\u03f5\5f\64\2\u03f5\u03f6\b\66\1"+
		"\2\u03f6\u03f7\b\66\1\2\u03f7k\3\2\2\2\u03f8\u03f9\7\27\2\2\u03f9\u03fd"+
		"\b\67\1\2\u03fa\u03fb\5\34\17\2\u03fb\u03fc\b\67\1\2\u03fc\u03fe\3\2\2"+
		"\2\u03fd\u03fa\3\2\2\2\u03fd\u03fe\3\2\2\2\u03fe\u03ff\3\2\2\2\u03ff\u0400"+
		"\b\67\1\2\u0400\u0401\7\67\2\2\u0401\u0402\5h\65\2\u0402\u0403\b\67\1"+
		"\2\u0403\u0404\b\67\1\2\u0404m\3\2\2\2\u0405\u0413\5p9\2\u0406\u0407\b"+
		"8\1\2\u0407\u040c\b8\1\2\u0408\u0409\7\30\2\2\u0409\u040a\5p9\2\u040a"+
		"\u040b\b8\1\2\u040b\u040d\3\2\2\2\u040c\u0408\3\2\2\2\u040d\u040e\3\2"+
		"\2\2\u040e\u040c\3\2\2\2\u040e\u040f\3\2\2\2\u040f\u0410\3\2\2\2\u0410"+
		"\u0411\b8\1\2\u0411\u0414\3\2\2\2\u0412\u0414\b8\1\2\u0413\u0406\3\2\2"+
		"\2\u0413\u0412\3\2\2\2\u0414o\3\2\2\2\u0415\u0423\5r:\2\u0416\u0417\b"+
		"9\1\2\u0417\u041c\b9\1\2\u0418\u0419\7\31\2\2\u0419\u041a\5r:\2\u041a"+
		"\u041b\b9\1\2\u041b\u041d\3\2\2\2\u041c\u0418\3\2\2\2\u041d\u041e\3\2"+
		"\2\2\u041e\u041c\3\2\2\2\u041e\u041f\3\2\2\2\u041f\u0420\3\2\2\2\u0420"+
		"\u0421\b9\1\2\u0421\u0424\3\2\2\2\u0422\u0424\b9\1\2\u0423\u0416\3\2\2"+
		"\2\u0423\u0422\3\2\2\2\u0424q\3\2\2\2\u0425\u0426\7\32\2\2\u0426\u0427"+
		"\5r:\2\u0427\u0428\b:\1\2\u0428\u042d\3\2\2\2\u0429\u042a\5t;\2\u042a"+
		"\u042b\b:\1\2\u042b\u042d\3\2\2\2\u042c\u0425\3\2\2\2\u042c\u0429\3\2"+
		"\2\2\u042ds\3\2\2\2\u042e\u043b\5z>\2\u042f\u0434\b;\1\2\u0430\u0431\5"+
		"v<\2\u0431\u0432\5z>\2\u0432\u0433\b;\1\2\u0433\u0435\3\2\2\2\u0434\u0430"+
		"\3\2\2\2\u0435\u0436\3\2\2\2\u0436\u0434\3\2\2\2\u0436\u0437\3\2\2\2\u0437"+
		"\u0438\3\2\2\2\u0438\u0439\b;\1\2\u0439\u043c\3\2\2\2\u043a\u043c\b;\1"+
		"\2\u043b\u042f\3\2\2\2\u043b\u043a\3\2\2\2\u043cu\3\2\2\2\u043d\u043e"+
		"\7J\2\2\u043e\u0456\b<\1\2\u043f\u0440\7K\2\2\u0440\u0456\b<\1\2\u0441"+
		"\u0442\7L\2\2\u0442\u0456\b<\1\2\u0443\u0444\7M\2\2\u0444\u0456\b<\1\2"+
		"\u0445\u0446\7N\2\2\u0446\u0456\b<\1\2\u0447\u0448\7O\2\2\u0448\u0456"+
		"\b<\1\2\u0449\u044a\7P\2\2\u044a\u0456\b<\1\2\u044b\u044c\7\22\2\2\u044c"+
		"\u0456\b<\1\2\u044d\u044e\7\32\2\2\u044e\u044f\7\22\2\2\u044f\u0456\b"+
		"<\1\2\u0450\u0451\7\33\2\2\u0451\u0456\b<\1\2\u0452\u0453\7\33\2\2\u0453"+
		"\u0454\7\32\2\2\u0454\u0456\b<\1\2\u0455\u043d\3\2\2\2\u0455\u043f\3\2"+
		"\2\2\u0455\u0441\3\2\2\2\u0455\u0443\3\2\2\2\u0455\u0445\3\2\2\2\u0455"+
		"\u0447\3\2\2\2\u0455\u0449\3\2\2\2\u0455\u044b\3\2\2\2\u0455\u044d\3\2"+
		"\2\2\u0455\u0450\3\2\2\2\u0455\u0452\3\2\2\2\u0456w\3\2\2\2\u0457\u0458"+
		"\7\63\2\2\u0458\u0459\5z>\2\u0459\u045a\b=\1\2\u045ay\3\2\2\2\u045b\u045c"+
		"\5|?\2\u045c\u0463\b>\1\2\u045d\u045e\7=\2\2\u045e\u045f\5|?\2\u045f\u0460"+
		"\b>\1\2\u0460\u0462\3\2\2\2\u0461\u045d\3\2\2\2\u0462\u0465\3\2\2\2\u0463"+
		"\u0461\3\2\2\2\u0463\u0464\3\2\2\2\u0464{\3\2\2\2\u0465\u0463\3\2\2\2"+
		"\u0466\u0467\5~@\2\u0467\u046e\b?\1\2\u0468\u0469\7>\2\2\u0469\u046a\5"+
		"~@\2\u046a\u046b\b?\1\2\u046b\u046d\3\2\2\2\u046c\u0468\3\2\2\2\u046d"+
		"\u0470\3\2\2\2\u046e\u046c\3\2\2\2\u046e\u046f\3\2\2\2\u046f}\3\2\2\2"+
		"\u0470\u046e\3\2\2\2\u0471\u0472\5\u0080A\2\u0472\u0479\b@\1\2\u0473\u0474"+
		"\7?\2\2\u0474\u0475\5\u0080A\2\u0475\u0476\b@\1\2\u0476\u0478\3\2\2\2"+
		"\u0477\u0473\3\2\2\2\u0478\u047b\3\2\2\2\u0479\u0477\3\2\2\2\u0479\u047a"+
		"\3\2\2\2\u047a\177\3\2\2\2\u047b\u0479\3\2\2\2\u047c\u047d\5\u0082B\2"+
		"\u047d\u048a\bA\1\2\u047e\u0483\bA\1\2\u047f\u0480\7@\2\2\u0480\u0484"+
		"\bA\1\2\u0481\u0482\7A\2\2\u0482\u0484\bA\1\2\u0483\u047f\3\2\2\2\u0483"+
		"\u0481\3\2\2\2\u0484\u0485\3\2\2\2\u0485\u0486\5\u0082B\2\u0486\u0487"+
		"\bA\1\2\u0487\u0489\3\2\2\2\u0488\u047e\3\2\2\2\u0489\u048c\3\2\2\2\u048a"+
		"\u0488\3\2\2\2\u048a\u048b\3\2\2\2\u048b\u0081\3\2\2\2\u048c\u048a\3\2"+
		"\2\2\u048d\u048e\5\u0084C\2\u048e\u049b\bB\1\2\u048f\u0494\bB\1\2\u0490"+
		"\u0491\7B\2\2\u0491\u0495\bB\1\2\u0492\u0493\7C\2\2\u0493\u0495\bB\1\2"+
		"\u0494\u0490\3\2\2\2\u0494\u0492\3\2\2\2\u0495\u0496\3\2\2\2\u0496\u0497"+
		"\5\u0084C\2\u0497\u0498\bB\1\2\u0498\u049a\3\2\2\2\u0499\u048f\3\2\2\2"+
		"\u049a\u049d\3\2\2\2\u049b\u0499\3\2\2\2\u049b\u049c\3\2\2\2\u049c\u0083"+
		"\3\2\2\2\u049d\u049b\3\2\2\2\u049e\u049f\5\u0086D\2\u049f\u04b2\bC\1\2"+
		"\u04a0\u04ab\bC\1\2\u04a1\u04a2\7\63\2\2\u04a2\u04ac\bC\1\2\u04a3\u04a4"+
		"\7Q\2\2\u04a4\u04ac\bC\1\2\u04a5\u04a6\7D\2\2\u04a6\u04ac\bC\1\2\u04a7"+
		"\u04a8\7E\2\2\u04a8\u04ac\bC\1\2\u04a9\u04aa\7F\2\2\u04aa\u04ac\bC\1\2"+
		"\u04ab\u04a1\3\2\2\2\u04ab\u04a3\3\2\2\2\u04ab\u04a5\3\2\2\2\u04ab\u04a7"+
		"\3\2\2\2\u04ab\u04a9\3\2\2\2\u04ac\u04ad\3\2\2\2\u04ad\u04ae\5\u0086D"+
		"\2\u04ae\u04af\bC\1\2\u04af\u04b1\3\2\2\2\u04b0\u04a0\3\2\2\2\u04b1\u04b4"+
		"\3\2\2\2\u04b2\u04b0\3\2\2\2\u04b2\u04b3\3\2\2\2\u04b3\u0085\3\2\2\2\u04b4"+
		"\u04b2\3\2\2\2\u04b5\u04bc\bD\1\2\u04b6\u04b7\7B\2\2\u04b7\u04bd\bD\1"+
		"\2\u04b8\u04b9\7C\2\2\u04b9\u04bd\bD\1\2\u04ba\u04bb\7G\2\2\u04bb\u04bd"+
		"\bD\1\2\u04bc\u04b6\3\2\2\2\u04bc\u04b8\3\2\2\2\u04bc\u04ba\3\2\2\2\u04bd"+
		"\u04be\3\2\2\2\u04be\u04bf\5\u0086D\2\u04bf\u04c0\bD\1\2\u04c0\u04c5\3"+
		"\2\2\2\u04c1\u04c2\5\u0088E\2\u04c2\u04c3\bD\1\2\u04c3\u04c5\3\2\2\2\u04c4"+
		"\u04b5\3\2\2\2\u04c4\u04c1\3\2\2\2\u04c5\u0087\3\2\2\2\u04c6\u04c7\5\u008a"+
		"F\2\u04c7\u04cc\bE\1\2\u04c8\u04c9\79\2\2\u04c9\u04ca\5\u0086D\2\u04ca"+
		"\u04cb\bE\1\2\u04cb\u04cd\3\2\2\2\u04cc\u04c8\3\2\2\2\u04cc\u04cd\3\2"+
		"\2\2\u04cd\u0089\3\2\2\2\u04ce\u04d0\7&\2\2\u04cf\u04ce\3\2\2\2\u04cf"+
		"\u04d0\3\2\2\2\u04d0\u04d1\3\2\2\2\u04d1\u04d2\5\u008cG\2\u04d2\u04e2"+
		"\bF\1\2\u04d3\u04d4\7\64\2\2\u04d4\u04d5\5\u009cO\2\u04d5\u04d6\7\65\2"+
		"\2\u04d6\u04d7\bF\1\2\u04d7\u04e1\3\2\2\2\u04d8\u04d9\7;\2\2\u04d9\u04da"+
		"\5\u008eH\2\u04da\u04db\7<\2\2\u04db\u04dc\bF\1\2\u04dc\u04e1\3\2\2\2"+
		"\u04dd\u04de\7\61\2\2\u04de\u04df\7(\2\2\u04df\u04e1\bF\1\2\u04e0\u04d3"+
		"\3\2\2\2\u04e0\u04d8\3\2\2\2\u04e0\u04dd\3\2\2\2\u04e1\u04e4\3\2\2\2\u04e2"+
		"\u04e0\3\2\2\2\u04e2\u04e3\3\2\2\2\u04e3\u008b\3\2\2\2\u04e4\u04e2\3\2"+
		"\2\2\u04e5\u04ed\7\64\2\2\u04e6\u04e7\5\u00a4S\2\u04e7\u04e8\bG\1\2\u04e8"+
		"\u04ee\3\2\2\2\u04e9\u04ea\5\u0098M\2\u04ea\u04eb\bG\1\2\u04eb\u04ee\3"+
		"\2\2\2\u04ec\u04ee\bG\1\2\u04ed\u04e6\3\2\2\2\u04ed\u04e9\3\2\2\2\u04ed"+
		"\u04ec\3\2\2\2\u04ee\u04ef\3\2\2\2\u04ef\u04f0\7\65\2\2\u04f0\u0525\b"+
		"G\1\2\u04f1\u04f6\7;\2\2\u04f2\u04f3\5\u0098M\2\u04f3\u04f4\bG\1\2\u04f4"+
		"\u04f7\3\2\2\2\u04f5\u04f7\bG\1\2\u04f6\u04f2\3\2\2\2\u04f6\u04f5\3\2"+
		"\2\2\u04f7\u04f8\3\2\2\2\u04f8\u04f9\7<\2\2\u04f9\u0525\bG\1\2\u04fa\u0502"+
		"\7H\2\2\u04fb\u04fc\5\u0096L\2\u04fc\u04fd\bG\1\2\u04fd\u0503\3\2\2\2"+
		"\u04fe\u04ff\5\u0098M\2\u04ff\u0500\bG\1\2\u0500\u0503\3\2\2\2\u0501\u0503"+
		"\bG\1\2\u0502\u04fb\3\2\2\2\u0502\u04fe\3\2\2\2\u0502\u0501\3\2\2\2\u0503"+
		"\u0504\3\2\2\2\u0504\u0505\7I\2\2\u0505\u0525\bG\1\2\u0506\u0507\7(\2"+
		"\2\u0507\u0525\bG\1\2\u0508\u0509\7+\2\2\u0509\u0525\bG\1\2\u050a\u050b"+
		"\7,\2\2\u050b\u0525\bG\1\2\u050c\u050d\7-\2\2\u050d\u0525\bG\1\2\u050e"+
		"\u050f\7.\2\2\u050f\u0525\bG\1\2\u0510\u0511\7/\2\2\u0511\u0525\bG\1\2"+
		"\u0512\u0513\7\60\2\2\u0513\u0525\bG\1\2\u0514\u0517\bG\1\2\u0515\u0516"+
		"\7\3\2\2\u0516\u0518\bG\1\2\u0517\u0515\3\2\2\2\u0518\u0519\3\2\2\2\u0519"+
		"\u0517\3\2\2\2\u0519\u051a\3\2\2\2\u051a\u051b\3\2\2\2\u051b\u0525\bG"+
		"\1\2\u051c\u051d\7\62\2\2\u051d\u0525\bG\1\2\u051e\u051f\7\34\2\2\u051f"+
		"\u0525\bG\1\2\u0520\u0521\7\35\2\2\u0521\u0525\bG\1\2\u0522\u0523\7\36"+
		"\2\2\u0523\u0525\bG\1\2\u0524\u04e5\3\2\2\2\u0524\u04f1\3\2\2\2\u0524"+
		"\u04fa\3\2\2\2\u0524\u0506\3\2\2\2\u0524\u0508\3\2\2\2\u0524\u050a\3\2"+
		"\2\2\u0524\u050c\3\2\2\2\u0524\u050e\3\2\2\2\u0524\u0510\3\2\2\2\u0524"+
		"\u0512\3\2\2\2\u0524\u0514\3\2\2\2\u0524\u051c\3\2\2\2\u0524\u051e\3\2"+
		"\2\2\u0524\u0520\3\2\2\2\u0524\u0522\3\2\2\2\u0525\u008d\3\2\2\2\u0526"+
		"\u0527\5\u0090I\2\u0527\u053b\bH\1\2\u0528\u0529\bH\1\2\u0529\u0538\7"+
		"\66\2\2\u052a\u052b\5\u0090I\2\u052b\u0532\bH\1\2\u052c\u052d\7\66\2\2"+
		"\u052d\u052e\5\u0090I\2\u052e\u052f\bH\1\2\u052f\u0531\3\2\2\2\u0530\u052c"+
		"\3\2\2\2\u0531\u0534\3\2\2\2\u0532\u0530\3\2\2\2\u0532\u0533\3\2\2\2\u0533"+
		"\u0536\3\2\2\2\u0534\u0532\3\2\2\2\u0535\u0537\7\66\2\2\u0536\u0535\3"+
		"\2\2\2\u0536\u0537\3\2\2\2\u0537\u0539\3\2\2\2\u0538\u052a\3\2\2\2\u0538"+
		"\u0539\3\2\2\2\u0539\u053a\3\2\2\2\u053a\u053c\bH\1\2\u053b\u0528\3\2"+
		"\2\2\u053b\u053c\3\2\2\2\u053c\u008f\3\2\2\2\u053d\u053e\5f\64\2\u053e"+
		"\u053f\bI\1\2\u053f\u0556\3\2\2\2\u0540\u0544\bI\1\2\u0541\u0542\5f\64"+
		"\2\u0542\u0543\bI\1\2\u0543\u0545\3\2\2\2\u0544\u0541\3\2\2\2\u0544\u0545"+
		"\3\2\2\2\u0545\u0546\3\2\2\2\u0546\u054a\7\67\2\2\u0547\u0548\5f\64\2"+
		"\u0548\u0549\bI\1\2\u0549\u054b\3\2\2\2\u054a\u0547\3\2\2\2\u054a\u054b"+
		"\3\2\2\2\u054b\u0552\3\2\2\2\u054c\u0550\7\67\2\2\u054d\u054e\5f\64\2"+
		"\u054e\u054f\bI\1\2\u054f\u0551\3\2\2\2\u0550\u054d\3\2\2\2\u0550\u0551"+
		"\3\2\2\2\u0551\u0553\3\2\2\2\u0552\u054c\3\2\2\2\u0552\u0553\3\2\2\2\u0553"+
		"\u0554\3\2\2\2\u0554\u0556\bI\1\2\u0555\u053d\3\2\2\2\u0555\u0540\3\2"+
		"\2\2\u0556\u0091\3\2\2\2\u0557\u055e\bJ\1\2\u0558\u0559\5z>\2\u0559\u055a"+
		"\bJ\1\2\u055a\u055f\3\2\2\2\u055b\u055c\5x=\2\u055c\u055d\bJ\1\2\u055d"+
		"\u055f\3\2\2\2\u055e\u0558\3\2\2\2\u055e\u055b\3\2\2\2\u055f\u056b\3\2"+
		"\2\2\u0560\u0567\7\66\2\2\u0561\u0562\5z>\2\u0562\u0563\bJ\1\2\u0563\u0568"+
		"\3\2\2\2\u0564\u0565\5x=\2\u0565\u0566\bJ\1\2\u0566\u0568\3\2\2\2\u0567"+
		"\u0561\3\2\2\2\u0567\u0564\3\2\2\2\u0568\u056a\3\2\2\2\u0569\u0560\3\2"+
		"\2\2\u056a\u056d\3\2\2\2\u056b\u0569\3\2\2\2\u056b\u056c\3\2\2\2\u056c"+
		"\u056f\3\2\2\2\u056d\u056b\3\2\2\2\u056e\u0570\7\66\2\2\u056f\u056e\3"+
		"\2\2\2\u056f\u0570\3\2\2\2\u0570\u0571\3\2\2\2\u0571\u0572\bJ\1\2\u0572"+
		"\u0093\3\2\2\2\u0573\u0574\5f\64\2\u0574\u0588\bK\1\2\u0575\u0576\bK\1"+
		"\2\u0576\u0585\7\66\2\2\u0577\u0578\5f\64\2\u0578\u057f\bK\1\2\u0579\u057a"+
		"\7\66\2\2\u057a\u057b\5f\64\2\u057b\u057c\bK\1\2\u057c\u057e\3\2\2\2\u057d"+
		"\u0579\3\2\2\2\u057e\u0581\3\2\2\2\u057f\u057d\3\2\2\2\u057f\u0580\3\2"+
		"\2\2\u0580\u0583\3\2\2\2\u0581\u057f\3\2\2\2\u0582\u0584\7\66\2\2\u0583"+
		"\u0582\3\2\2\2\u0583\u0584\3\2\2\2\u0584\u0586\3\2\2\2\u0585\u0577\3\2"+
		"\2\2\u0585\u0586\3\2\2\2\u0586\u0587\3\2\2\2\u0587\u0589\bK\1\2\u0588"+
		"\u0575\3\2\2\2\u0588\u0589\3\2\2\2\u0589\u0095\3\2\2\2\u058a\u0594\bL"+
		"\1\2\u058b\u058c\5f\64\2\u058c\u058d\7\67\2\2\u058d\u058e\5f\64\2\u058e"+
		"\u058f\bL\1\2\u058f\u0595\3\2\2\2\u0590\u0591\79\2\2\u0591\u0592\5z>\2"+
		"\u0592\u0593\bL\1\2\u0593\u0595\3\2\2\2\u0594\u058b\3\2\2\2\u0594\u0590"+
		"\3\2\2\2\u0595\u0596\3\2\2\2\u0596\u0597\5\u00a0Q\2\u0597\u0598\bL\1\2"+
		"\u0598\u05bd\3\2\2\2\u0599\u05a3\bL\1\2\u059a\u059b\5f\64\2\u059b\u059c"+
		"\7\67\2\2\u059c\u059d\5f\64\2\u059d\u059e\bL\1\2\u059e\u05a4\3\2\2\2\u059f"+
		"\u05a0\79\2\2\u05a0\u05a1\5z>\2\u05a1\u05a2\bL\1\2\u05a2\u05a4\3\2\2\2"+
		"\u05a3\u059a\3\2\2\2\u05a3\u059f\3\2\2\2\u05a4\u05a5\3\2\2\2\u05a5\u05b4"+
		"\bL\1\2\u05a6\u05b0\7\66\2\2\u05a7\u05a8\5f\64\2\u05a8\u05a9\7\67\2\2"+
		"\u05a9\u05aa\5f\64\2\u05aa\u05ab\bL\1\2\u05ab\u05b1\3\2\2\2\u05ac\u05ad"+
		"\79\2\2\u05ad\u05ae\5z>\2\u05ae\u05af\bL\1\2\u05af\u05b1\3\2\2\2\u05b0"+
		"\u05a7\3\2\2\2\u05b0\u05ac\3\2\2\2\u05b1\u05b3\3\2\2\2\u05b2\u05a6\3\2"+
		"\2\2\u05b3\u05b6\3\2\2\2\u05b4\u05b2\3\2\2\2\u05b4\u05b5\3\2\2\2\u05b5"+
		"\u05b8\3\2\2\2\u05b6\u05b4\3\2\2\2\u05b7\u05b9\7\66\2\2\u05b8\u05b7\3"+
		"\2\2\2\u05b8\u05b9\3\2\2\2\u05b9\u05ba\3\2\2\2\u05ba\u05bb\bL\1\2\u05bb"+
		"\u05bd\3\2\2\2\u05bc\u058a\3\2\2\2\u05bc\u0599\3\2\2\2\u05bd\u0097\3\2"+
		"\2\2\u05be\u05c5\bM\1\2\u05bf\u05c0\5f\64\2\u05c0\u05c1\bM\1\2\u05c1\u05c6"+
		"\3\2\2\2\u05c2\u05c3\5x=\2\u05c3\u05c4\bM\1\2\u05c4\u05c6\3\2\2\2\u05c5"+
		"\u05bf\3\2\2\2\u05c5\u05c2\3\2\2\2\u05c6\u05c7\3\2\2\2\u05c7\u05c8\5\u00a0"+
		"Q\2\u05c8\u05c9\bM\1\2\u05c9\u05ea\3\2\2\2\u05ca\u05d1\bM\1\2\u05cb\u05cc"+
		"\5f\64\2\u05cc\u05cd\bM\1\2\u05cd\u05d2\3\2\2\2\u05ce\u05cf\5x=\2\u05cf"+
		"\u05d0\bM\1\2\u05d0\u05d2\3\2\2\2\u05d1\u05cb\3\2\2\2\u05d1\u05ce\3\2"+
		"\2\2\u05d2\u05d3\3\2\2\2\u05d3\u05df\bM\1\2\u05d4\u05db\7\66\2\2\u05d5"+
		"\u05d6\5f\64\2\u05d6\u05d7\bM\1\2\u05d7\u05dc\3\2\2\2\u05d8\u05d9\5x="+
		"\2\u05d9\u05da\bM\1\2\u05da\u05dc\3\2\2\2\u05db\u05d5\3\2\2\2\u05db\u05d8"+
		"\3\2\2\2\u05dc\u05de\3\2\2\2\u05dd\u05d4\3\2\2\2\u05de\u05e1\3\2\2\2\u05df"+
		"\u05dd\3\2\2\2\u05df\u05e0\3\2\2\2\u05e0\u05e2\3\2\2\2\u05e1\u05df\3\2"+
		"\2\2\u05e2\u05e5\bM\1\2\u05e3\u05e4\7\66\2\2\u05e4\u05e6\bM\1\2\u05e5"+
		"\u05e3\3\2\2\2\u05e5\u05e6\3\2\2\2\u05e6\u05e7\3\2\2\2\u05e7\u05e8\bM"+
		"\1\2\u05e8\u05ea\3\2\2\2\u05e9\u05be\3\2\2\2\u05e9\u05ca\3\2\2\2\u05ea"+
		"\u0099\3\2\2\2\u05eb\u05ec\7\37\2\2\u05ec\u05ed\7(\2\2\u05ed\u05f3\bN"+
		"\1\2\u05ee\u05ef\7\64\2\2\u05ef\u05f0\5\u009cO\2\u05f0\u05f1\7\65\2\2"+
		"\u05f1\u05f2\bN\1\2\u05f2\u05f4\3\2\2\2\u05f3\u05ee\3\2\2\2\u05f3\u05f4"+
		"\3\2\2\2\u05f4\u05f5\3\2\2\2\u05f5\u05f6\bN\1\2\u05f6\u05f7\7\67\2\2\u05f7"+
		"\u05f8\5d\63\2\u05f8\u05f9\bN\1\2\u05f9\u05fa\bN\1\2\u05fa\u009b\3\2\2"+
		"\2\u05fb\u0607\bO\1\2\u05fc\u0601\5\u009eP\2\u05fd\u05fe\7\66\2\2\u05fe"+
		"\u0600\5\u009eP\2\u05ff\u05fd\3\2\2\2\u0600\u0603\3\2\2\2\u0601\u05ff"+
		"\3\2\2\2\u0601\u0602\3\2\2\2\u0602\u0605\3\2\2\2\u0603\u0601\3\2\2\2\u0604"+
		"\u0606\7\66\2\2\u0605\u0604\3\2\2\2\u0605\u0606\3\2\2\2\u0606\u0608\3"+
		"\2\2\2\u0607\u05fc\3\2\2\2\u0607\u0608\3\2\2\2\u0608\u0609\3\2\2\2\u0609"+
		"\u060a\bO\1\2\u060a\u009d\3\2\2\2\u060b\u060c\bP\1\2\u060c\u060d\5f\64"+
		"\2\u060d\u060e\5\u00a0Q\2\u060e\u060f\bP\1\2\u060f\u0623\3\2\2\2\u0610"+
		"\u0611\bP\1\2\u0611\u0612\5f\64\2\u0612\u0613\bP\1\2\u0613\u0614\7:\2"+
		"\2\u0614\u0615\5f\64\2\u0615\u0616\bP\1\2\u0616\u0623\3\2\2\2\u0617\u0618"+
		"\5f\64\2\u0618\u0619\bP\1\2\u0619\u0623\3\2\2\2\u061a\u061b\79\2\2\u061b"+
		"\u061c\5f\64\2\u061c\u061d\bP\1\2\u061d\u0623\3\2\2\2\u061e\u061f\7\63"+
		"\2\2\u061f\u0620\5f\64\2\u0620\u0621\bP\1\2\u0621\u0623\3\2\2\2\u0622"+
		"\u060b\3\2\2\2\u0622\u0610\3\2\2\2\u0622\u0617\3\2\2\2\u0622\u061a\3\2"+
		"\2\2\u0622\u061e\3\2\2\2\u0623\u009f\3\2\2\2\u0624\u0627\bQ\1\2\u0625"+
		"\u0626\7%\2\2\u0626\u0628\bQ\1\2\u0627\u0625\3\2\2\2\u0627\u0628\3\2\2"+
		"\2\u0628\u0629\3\2\2\2\u0629\u062a\bQ\1\2\u062a\u062b\7\21\2\2\u062b\u062c"+
		"\5\u0092J\2\u062c\u062d\7\22\2\2\u062d\u062e\bQ\1\2\u062e\u062f\5n8\2"+
		"\u062f\u0630\bQ\1\2\u0630\u0637\bQ\1\2\u0631\u0632\7\r\2\2\u0632\u0633"+
		"\5h\65\2\u0633\u0634\bQ\1\2\u0634\u0636\3\2\2\2\u0635\u0631\3\2\2\2\u0636"+
		"\u0639\3\2\2\2\u0637\u0635\3\2\2\2\u0637\u0638\3\2\2\2\u0638\u063a\3\2"+
		"\2\2\u0639\u0637\3\2\2\2\u063a\u063e\bQ\1\2\u063b\u063c\5\u00a0Q\2\u063c"+
		"\u063d\bQ\1\2\u063d\u063f\3\2\2\2\u063e\u063b\3\2\2\2\u063e\u063f\3\2"+
		"\2\2\u063f\u0640\3\2\2\2\u0640\u0641\bQ\1\2\u0641\u00a1\3\2\2\2\u0642"+
		"\u0643\7(\2\2\u0643\u00a3\3\2\2\2\u0644\u0645\bS\1\2\u0645\u064d\7 \2"+
		"\2\u0646\u0647\7\7\2\2\u0647\u0648\5f\64\2\u0648\u0649\bS\1\2\u0649\u064e"+
		"\3\2\2\2\u064a\u064b\5\u0094K\2\u064b\u064c\bS\1\2\u064c\u064e\3\2\2\2"+
		"\u064d\u0646\3\2\2\2\u064d\u064a\3\2\2\2\u064d\u064e\3\2\2\2\u064e\u064f"+
		"\3\2\2\2\u064f\u0650\bS\1\2\u0650\u00a5\3\2\2\2\u00be\u00ad\u00b6\u00b8"+
		"\u00c4\u00d3\u00dc\u00e5\u00f1\u00fb\u0105\u0111\u0114\u011c\u0122\u0124"+
		"\u0126\u012a\u012c\u012e\u0130\u0137\u0140\u0146\u0148\u014a\u014e\u0150"+
		"\u0152\u0159\u015f\u0161\u0163\u0167\u0169\u0171\u0177\u0183\u0185\u0190"+
		"\u019a\u01a6\u01a9\u01b1\u01b7\u01b9\u01bb\u01bf\u01c1\u01c3\u01c5\u01cc"+
		"\u01d5\u01db\u01dd\u01df\u01e3\u01e5\u01e7\u01ee\u01f4\u01f6\u01f8\u01fc"+
		"\u01fe\u0208\u0210\u021a\u0221\u0225\u0232\u023c\u0247\u0255\u0259\u025d"+
		"\u0265\u026f\u0278\u027c\u0280\u0282\u0285\u0294\u029b\u02aa\u02ac\u02b2"+
		"\u02bd\u02bf\u02c9\u02cb\u02cd\u02da\u02e3\u02f0\u02f4\u02ff\u0307\u030e"+
		"\u031a\u0328\u0334\u0341\u0347\u035d\u036a\u037b\u0389\u0390\u0392\u0399"+
		"\u03a4\u03a6\u03b6\u03c1\u03cc\u03d0\u03dc\u03e1\u03e9\u03f0\u03fd\u040e"+
		"\u0413\u041e\u0423\u042c\u0436\u043b\u0455\u0463\u046e\u0479\u0483\u048a"+
		"\u0494\u049b\u04ab\u04b2\u04bc\u04c4\u04cc\u04cf\u04e0\u04e2\u04ed\u04f6"+
		"\u0502\u0519\u0524\u0532\u0536\u0538\u053b\u0544\u054a\u0550\u0552\u0555"+
		"\u055e\u0567\u056b\u056f\u057f\u0583\u0585\u0588\u0594\u05a3\u05b0\u05b4"+
		"\u05b8\u05bc\u05c5\u05d1\u05db\u05df\u05e5\u05e9\u05f3\u0601\u0605\u0607"+
		"\u0622\u0627\u0637\u063e\u064d";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
