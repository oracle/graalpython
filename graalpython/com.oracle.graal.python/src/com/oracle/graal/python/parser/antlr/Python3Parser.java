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
		LONG_QUOTES2=95, SKIP_=96, UNKNOWN_CHAR=97, INDENT=98, DEDENT=99;
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
			"'|='", "'^='", "'<<='", "'>>='", "'**='", "'//='", "'\"\"\"'", "'''''"
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
			"LONG_QUOTES2", "SKIP_", "UNKNOWN_CHAR", "INDENT", "DEDENT"
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
			setState(169);
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
			setState(180);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << DEF) | (1L << RETURN) | (1L << RAISE) | (1L << FROM) | (1L << IMPORT) | (1L << GLOBAL) | (1L << NONLOCAL) | (1L << ASSERT) | (1L << IF) | (1L << WHILE) | (1L << FOR) | (1L << TRY) | (1L << WITH) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << CLASS) | (1L << YIELD) | (1L << DEL) | (1L << PASS) | (1L << CONTINUE) | (1L << BREAK) | (1L << ASYNC) | (1L << AWAIT) | (1L << NEWLINE) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)) | (1L << (AT - 64)))) != 0)) {
				{
				setState(178);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case NEWLINE:
					{
					setState(176);
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
					setState(177);
					_localctx.stmt = stmt();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(182);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(183);
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
			setState(188);
			_localctx.testlist = testlist();
			setState(192);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==NEWLINE) {
				{
				{
				setState(189);
				match(NEWLINE);
				}
				}
				setState(194);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(195);
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
			setState(200);
			match(AT);
			setState(201);
			_localctx.dotted_name = dotted_name();
			setState(207);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN_PAREN) {
				{
				setState(202);
				match(OPEN_PAREN);
				setState(203);
				_localctx.arglist = arglist();
				setState(204);
				match(CLOSE_PAREN);
				args = _localctx.arglist.result; 
				}
			}

			setState(209);
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
			setState(214); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(213);
				decorator();
				}
				}
				setState(216); 
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
			setState(221);
			_localctx.decorators = decorators();
			setState(225);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case CLASS:
				{
				setState(222);
				classdef();
				}
				break;
			case DEF:
				{
				setState(223);
				funcdef();
				}
				break;
			case ASYNC:
				{
				setState(224);
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
			setState(229);
			match(ASYNC);
			setState(230);
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
			setState(232);
			match(DEF);
			setState(233);
			_localctx.n = match(NAME);
			setState(234);
			_localctx.parameters = parameters();
			setState(237);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ARROW) {
				{
				setState(235);
				match(ARROW);
				setState(236);
				test();
				}
			}

			setState(239);
			match(COLON);
			 
			            String name = _localctx.n.getText(); 
			            ScopeInfo enclosingScope = scopeEnvironment.getCurrentScope();
			            String enclosingClassName = enclosingScope.isInClassScope() ? enclosingScope.getScopeId() : null;
			            ScopeInfo functionScope = scopeEnvironment.pushScope(name, ScopeInfo.ScopeKind.Function);
			            functionScope.setHasAnnotations(true);
			            _localctx.parameters.result.defineParamsInScope(functionScope); 
			        
			setState(241);
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
			setState(245);
			match(OPEN_PAREN);
			setState(247);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(246);
				typedargslist(args);
				}
			}

			setState(249);
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
			setState(357);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,33,_ctx) ) {
			case 1:
				{
				setState(252);
				defparameter(args);
				setState(257);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(253);
						match(COMMA);
						setState(254);
						defparameter(args);
						}
						} 
					}
					setState(259);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,9,_ctx);
				}
				setState(260);
				match(COMMA);
				setState(261);
				match(DIV);
				args.markPositionalOnlyIndex();
				setState(300);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
				case 1:
					{
					setState(272);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
					case 1:
						{
						setState(263);
						match(COMMA);
						setState(264);
						defparameter(args);
						setState(269);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(265);
								match(COMMA);
								setState(266);
								defparameter(args);
								}
								} 
							}
							setState(271);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
						}
						}
						break;
					}
					setState(298);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(274);
						match(COMMA);
						setState(296);
						_errHandler.sync(this);
						switch (_input.LA(1)) {
						case STAR:
							{
							setState(275);
							splatparameter(args);
							setState(280);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
							while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
								if ( _alt==1 ) {
									{
									{
									setState(276);
									match(COMMA);
									setState(277);
									defparameter(args);
									}
									} 
								}
								setState(282);
								_errHandler.sync(this);
								_alt = getInterpreter().adaptivePredict(_input,12,_ctx);
							}
							setState(290);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(283);
								match(COMMA);
								setState(288);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==POWER) {
									{
									setState(284);
									kwargsparameter(args);
									setState(286);
									_errHandler.sync(this);
									_la = _input.LA(1);
									if (_la==COMMA) {
										{
										setState(285);
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
							setState(292);
							kwargsparameter(args);
							setState(294);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(293);
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
				setState(302);
				defparameter(args);
				setState(307);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,20,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(303);
						match(COMMA);
						setState(304);
						defparameter(args);
						}
						} 
					}
					setState(309);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,20,_ctx);
				}
				setState(334);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(310);
					match(COMMA);
					setState(332);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(311);
						splatparameter(args);
						setState(316);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,21,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(312);
								match(COMMA);
								setState(313);
								defparameter(args);
								}
								} 
							}
							setState(318);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,21,_ctx);
						}
						setState(326);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(319);
							match(COMMA);
							setState(324);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(320);
								kwargsparameter(args);
								setState(322);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(321);
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
						setState(328);
						kwargsparameter(args);
						setState(330);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(329);
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
				setState(336);
				splatparameter(args);
				setState(341);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,28,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(337);
						match(COMMA);
						setState(338);
						defparameter(args);
						}
						} 
					}
					setState(343);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,28,_ctx);
				}
				setState(351);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(344);
					match(COMMA);
					setState(349);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==POWER) {
						{
						setState(345);
						kwargsparameter(args);
						setState(347);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(346);
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
				setState(353);
				kwargsparameter(args);
				setState(355);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(354);
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
			setState(359);
			_localctx.NAME = match(NAME);
			 SSTNode type = null; SSTNode defValue = null; 
			setState(365);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(361);
				match(COLON);
				setState(362);
				_localctx.test = test();
				 type = _localctx.test.result; 
				}
			}

			setState(371);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(367);
				match(ASSIGN);
				setState(368);
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
			setState(375);
			match(STAR);
			 String name = null; SSTNode type = null; 
			setState(385);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(377);
				_localctx.NAME = match(NAME);
				 name = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				setState(383);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COLON) {
					{
					setState(379);
					match(COLON);
					setState(380);
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
			setState(389);
			match(POWER);
			setState(390);
			_localctx.NAME = match(NAME);
			 SSTNode type = null; 
			setState(396);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COLON) {
				{
				setState(392);
				match(COLON);
				setState(393);
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
			setState(506);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,63,_ctx) ) {
			case 1:
				{
				setState(401);
				vdefparameter(args);
				setState(406);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,39,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(402);
						match(COMMA);
						setState(403);
						vdefparameter(args);
						}
						} 
					}
					setState(408);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,39,_ctx);
				}
				setState(409);
				match(COMMA);
				setState(410);
				match(DIV);
				args.markPositionalOnlyIndex();
				setState(449);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,49,_ctx) ) {
				case 1:
					{
					setState(421);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,41,_ctx) ) {
					case 1:
						{
						setState(412);
						match(COMMA);
						setState(413);
						vdefparameter(args);
						setState(418);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(414);
								match(COMMA);
								setState(415);
								vdefparameter(args);
								}
								} 
							}
							setState(420);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,40,_ctx);
						}
						}
						break;
					}
					setState(447);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(423);
						match(COMMA);
						setState(445);
						_errHandler.sync(this);
						switch (_input.LA(1)) {
						case STAR:
							{
							setState(424);
							vsplatparameter(args);
							setState(429);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,42,_ctx);
							while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
								if ( _alt==1 ) {
									{
									{
									setState(425);
									match(COMMA);
									setState(426);
									vdefparameter(args);
									}
									} 
								}
								setState(431);
								_errHandler.sync(this);
								_alt = getInterpreter().adaptivePredict(_input,42,_ctx);
							}
							setState(439);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(432);
								match(COMMA);
								setState(437);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==POWER) {
									{
									setState(433);
									vkwargsparameter(args);
									setState(435);
									_errHandler.sync(this);
									_la = _input.LA(1);
									if (_la==COMMA) {
										{
										setState(434);
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
							setState(441);
							vkwargsparameter(args);
							setState(443);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==COMMA) {
								{
								setState(442);
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
				setState(451);
				vdefparameter(args);
				setState(456);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,50,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(452);
						match(COMMA);
						setState(453);
						vdefparameter(args);
						}
						} 
					}
					setState(458);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,50,_ctx);
				}
				setState(483);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(459);
					match(COMMA);
					setState(481);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case STAR:
						{
						setState(460);
						vsplatparameter(args);
						setState(465);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,51,_ctx);
						while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
							if ( _alt==1 ) {
								{
								{
								setState(461);
								match(COMMA);
								setState(462);
								vdefparameter(args);
								}
								} 
							}
							setState(467);
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,51,_ctx);
						}
						setState(475);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(468);
							match(COMMA);
							setState(473);
							_errHandler.sync(this);
							_la = _input.LA(1);
							if (_la==POWER) {
								{
								setState(469);
								vkwargsparameter(args);
								setState(471);
								_errHandler.sync(this);
								_la = _input.LA(1);
								if (_la==COMMA) {
									{
									setState(470);
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
						setState(477);
						vkwargsparameter(args);
						setState(479);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(478);
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
				setState(485);
				vsplatparameter(args);
				setState(490);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,58,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(486);
						match(COMMA);
						setState(487);
						vdefparameter(args);
						}
						} 
					}
					setState(492);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,58,_ctx);
				}
				setState(500);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(493);
					match(COMMA);
					setState(498);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==POWER) {
						{
						setState(494);
						vkwargsparameter(args);
						setState(496);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==COMMA) {
							{
							setState(495);
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
				setState(502);
				vkwargsparameter(args);
				setState(504);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(503);
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
			setState(510);
			_localctx.NAME = match(NAME);
			 SSTNode defValue = null; 
			setState(516);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASSIGN) {
				{
				setState(512);
				match(ASSIGN);
				setState(513);
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
			setState(520);
			match(STAR);
			 String name = null; 
			setState(524);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(522);
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
			setState(528);
			match(POWER);
			setState(529);
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
			setState(534);
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
				setState(532);
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
				setState(533);
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
			setState(536);
			small_stmt();
			setState(541);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,67,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(537);
					match(SEMI_COLON);
					setState(538);
					small_stmt();
					}
					} 
				}
				setState(543);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,67,_ctx);
			}
			setState(545);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEMI_COLON) {
				{
				setState(544);
				match(SEMI_COLON);
				}
			}

			setState(547);
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
			setState(558);
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
				setState(549);
				expr_stmt();
				}
				break;
			case DEL:
				enterOuterAlt(_localctx, 2);
				{
				setState(550);
				del_stmt();
				}
				break;
			case PASS:
				enterOuterAlt(_localctx, 3);
				{
				setState(551);
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
				setState(553);
				flow_stmt();
				}
				break;
			case FROM:
			case IMPORT:
				enterOuterAlt(_localctx, 5);
				{
				setState(554);
				import_stmt();
				}
				break;
			case GLOBAL:
				enterOuterAlt(_localctx, 6);
				{
				setState(555);
				global_stmt();
				}
				break;
			case NONLOCAL:
				enterOuterAlt(_localctx, 7);
				{
				setState(556);
				nonlocal_stmt();
				}
				break;
			case ASSERT:
				enterOuterAlt(_localctx, 8);
				{
				setState(557);
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
			setState(560);
			_localctx.lhs = _localctx.testlist_star_expr = testlist_star_expr();
			 SSTNode rhs = null; 
			          int rhsStopIndex = 0;
			        
			setState(601);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COLON:
				{
				setState(562);
				match(COLON);
				setState(563);
				_localctx.t = _localctx.test = test();
				setState(568);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ASSIGN) {
					{
					setState(564);
					match(ASSIGN);
					setState(565);
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
				setState(572);
				_localctx.augassign = augassign();
				setState(579);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case YIELD:
					{
					setState(573);
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
					setState(576);
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
				setState(597);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==ASSIGN) {
					{
					{
					setState(585);
					match(ASSIGN);
					 push(value); 
					setState(593);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case YIELD:
						{
						setState(587);
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
						setState(590);
						_localctx.testlist_star_expr = testlist_star_expr();
						 value = _localctx.testlist_star_expr.result; rhsStopIndex = getStopIndex((_localctx.testlist_star_expr!=null?(_localctx.testlist_star_expr.stop):null));
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					}
					setState(599);
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
			setState(609);
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
				setState(603);
				_localctx.test = test();
				 _localctx.result =  _localctx.test.result; 
				}
				break;
			case STAR:
				{
				setState(606);
				_localctx.star_expr = star_expr();
				  _localctx.result =  _localctx.star_expr.result; 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(641);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 
				                    int start = start(); 
				                    push(_localctx.result);
				                
				setState(612);
				match(COMMA);
				setState(638);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(619);
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
						setState(613);
						_localctx.test = test();
						 push(_localctx.test.result); 
						}
						break;
					case STAR:
						{
						setState(616);
						_localctx.star_expr = star_expr();
						 push(_localctx.star_expr.result); 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(632);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,78,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(621);
							match(COMMA);
							setState(628);
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
								setState(622);
								_localctx.test = test();
								 push(_localctx.test.result); 
								}
								break;
							case STAR:
								{
								setState(625);
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
						setState(634);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,78,_ctx);
					}
					setState(636);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(635);
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
			setState(643);
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
			setState(645);
			match(DEL);
			setState(646);
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
			setState(656);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case BREAK:
				enterOuterAlt(_localctx, 1);
				{
				setState(649);
				_localctx.b = match(BREAK);
				 
				            push(new SimpleSSTNode(SimpleSSTNode.Type.BREAK, getStartIndex(_localctx.b), getStopIndex(_localctx.b)));
				            containsBreak = true; 
				        
				}
				break;
			case CONTINUE:
				enterOuterAlt(_localctx, 2);
				{
				setState(651);
				_localctx.c = match(CONTINUE);
				 
				            push(new SimpleSSTNode(SimpleSSTNode.Type.CONTINUE, getStartIndex(_localctx.c), getStopIndex(_localctx.c)));
				            containsContinue = true; 
				        
				}
				break;
			case RETURN:
				enterOuterAlt(_localctx, 3);
				{
				setState(653);
				return_stmt();
				}
				break;
			case RAISE:
				enterOuterAlt(_localctx, 4);
				{
				setState(654);
				raise_stmt();
				}
				break;
			case YIELD:
				enterOuterAlt(_localctx, 5);
				{
				setState(655);
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
		enterRule(_localctx, 50, RULE_return_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(658);
			match(RETURN);
			 SSTNode value = null; 
			setState(663);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(660);
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
		enterRule(_localctx, 52, RULE_yield_stmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(667);
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
			setState(671);
			match(RAISE);
			setState(680);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(672);
				_localctx.test = test();
				 value = _localctx.test.result; 
				setState(678);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FROM) {
					{
					setState(674);
					match(FROM);
					setState(675);
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
			setState(686);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IMPORT:
				enterOuterAlt(_localctx, 1);
				{
				setState(684);
				import_name();
				}
				break;
			case FROM:
				enterOuterAlt(_localctx, 2);
				{
				setState(685);
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
			setState(688);
			match(IMPORT);
			setState(689);
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
			setState(691);
			match(FROM);
			 String name = ""; 
			setState(713);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,91,_ctx) ) {
			case 1:
				{
				setState(699);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==DOT || _la==ELLIPSIS) {
					{
					setState(697);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case DOT:
						{
						setState(693);
						match(DOT);
						 name += '.'; 
						}
						break;
					case ELLIPSIS:
						{
						setState(695);
						match(ELLIPSIS);
						 name += "..."; 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					setState(701);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(702);
				_localctx.dotted_name = dotted_name();
				 name += _localctx.dotted_name.result; 
				}
				break;
			case 2:
				{
				setState(709); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					setState(709);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case DOT:
						{
						setState(705);
						match(DOT);
						 name += '.'; 
						}
						break;
					case ELLIPSIS:
						{
						setState(707);
						match(ELLIPSIS);
						 name += "..."; 
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					}
					setState(711); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==DOT || _la==ELLIPSIS );
				}
				break;
			}
			setState(715);
			match(IMPORT);
			 String[][] asNames = null; 
			setState(726);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STAR:
				{
				setState(717);
				match(STAR);
				}
				break;
			case OPEN_PAREN:
				{
				setState(718);
				match(OPEN_PAREN);
				setState(719);
				_localctx.import_as_names = import_as_names();
				 asNames = _localctx.import_as_names.result; 
				setState(721);
				match(CLOSE_PAREN);
				}
				break;
			case NAME:
				{
				setState(723);
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
			setState(730);
			_localctx.n = match(NAME);
			 String asName = null; 
			setState(735);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(732);
				match(AS);
				setState(733);
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
			setState(740);
			_localctx.import_as_name = import_as_name();
			 push(_localctx.import_as_name.result); 
			setState(748);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,94,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(742);
					match(COMMA);
					setState(743);
					_localctx.import_as_name = import_as_name();
					 push(_localctx.import_as_name.result); 
					}
					} 
				}
				setState(750);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,94,_ctx);
			}
			setState(752);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(751);
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
			setState(756);
			_localctx.NAME = match(NAME);
			 _localctx.result =  (_localctx.NAME!=null?_localctx.NAME.getText():null); 
			setState(763);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==DOT) {
				{
				{
				setState(758);
				match(DOT);
				setState(759);
				_localctx.NAME = match(NAME);
				 _localctx.result =  _localctx.result + "." + (_localctx.NAME!=null?_localctx.NAME.getText():null); 
				}
				}
				setState(765);
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
			setState(766);
			_localctx.dotted_name = dotted_name();
			setState(771);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AS:
				{
				setState(767);
				match(AS);
				setState(768);
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
			setState(773);
			dotted_as_name();
			setState(778);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(774);
				match(COMMA);
				setState(775);
				dotted_as_name();
				}
				}
				setState(780);
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
			setState(782);
			match(GLOBAL);
			setState(783);
			_localctx.NAME = match(NAME);
			 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
			setState(790);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(785);
				match(COMMA);
				setState(786);
				_localctx.NAME = match(NAME);
				 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
				}
				}
				setState(792);
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
			setState(796);
			match(NONLOCAL);
			setState(797);
			_localctx.NAME = match(NAME);
			 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
			setState(804);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(799);
				match(COMMA);
				setState(800);
				_localctx.NAME = match(NAME);
				 pushString((_localctx.NAME!=null?_localctx.NAME.getText():null)); 
				}
				}
				setState(806);
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
			setState(809);
			match(ASSERT);
			setState(810);
			_localctx.e = _localctx.test = test();
			 SSTNode message = null; 
			setState(816);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(812);
				match(COMMA);
				setState(813);
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
			setState(829);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case IF:
				enterOuterAlt(_localctx, 1);
				{
				setState(820);
				if_stmt();
				}
				break;
			case WHILE:
				enterOuterAlt(_localctx, 2);
				{
				setState(821);
				while_stmt();
				}
				break;
			case FOR:
				enterOuterAlt(_localctx, 3);
				{
				setState(822);
				for_stmt();
				}
				break;
			case TRY:
				enterOuterAlt(_localctx, 4);
				{
				setState(823);
				try_stmt();
				}
				break;
			case WITH:
				enterOuterAlt(_localctx, 5);
				{
				setState(824);
				with_stmt();
				}
				break;
			case DEF:
				enterOuterAlt(_localctx, 6);
				{
				setState(825);
				funcdef();
				}
				break;
			case CLASS:
				enterOuterAlt(_localctx, 7);
				{
				setState(826);
				classdef();
				}
				break;
			case AT:
				enterOuterAlt(_localctx, 8);
				{
				setState(827);
				decorated();
				}
				break;
			case ASYNC:
				enterOuterAlt(_localctx, 9);
				{
				setState(828);
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
			setState(831);
			match(ASYNC);
			setState(835);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEF:
				{
				setState(832);
				funcdef();
				}
				break;
			case WITH:
				{
				setState(833);
				with_stmt();
				}
				break;
			case FOR:
				{
				setState(834);
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
			setState(837);
			match(IF);
			setState(838);
			_localctx.if_test = test();
			setState(839);
			match(COLON);
			setState(840);
			_localctx.if_suite = suite();
			setState(841);
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
			setState(857);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ELIF:
				enterOuterAlt(_localctx, 1);
				{
				setState(844);
				match(ELIF);
				setState(845);
				_localctx.test = test();
				setState(846);
				match(COLON);
				setState(847);
				_localctx.suite = suite();
				setState(848);
				_localctx.elif_stmt = elif_stmt();
				 _localctx.result =  new IfSSTNode(_localctx.test.result, _localctx.suite.result, _localctx.elif_stmt.result, -1, -1); 
				}
				break;
			case ELSE:
				enterOuterAlt(_localctx, 2);
				{
				setState(851);
				match(ELSE);
				setState(852);
				match(COLON);
				setState(853);
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
			setState(859);
			match(WHILE);
			setState(860);
			_localctx.test = test();
			setState(861);
			match(COLON);
			 boolean bFlag = startLoopBreak(); boolean cFlag = startLoopContinue(); 
			setState(863);
			_localctx.suite = suite();
			 
			            WhileSSTNode result = new WhileSSTNode(_localctx.test.result, _localctx.suite.result, containsContinue, containsBreak, getStartIndex(_localctx),getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null))); 
			            containsContinue = cFlag;
			            containsBreak = bFlag;
			        
			setState(870);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(865);
				match(ELSE);
				setState(866);
				match(COLON);
				setState(867);
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
			setState(874);
			match(FOR);
			setState(875);
			_localctx.exprlist = exprlist();
			setState(876);
			match(IN);
			setState(877);
			_localctx.testlist = testlist();
			setState(878);
			match(COLON);
			 boolean bFlag = startLoopBreak(); boolean cFlag = startLoopContinue(); 
			setState(880);
			_localctx.suite = suite();
			 
			            ForSSTNode result = factory.createForSSTNode(_localctx.exprlist.result, _localctx.testlist.result, _localctx.suite.result, containsContinue, getStartIndex(_localctx),getStopIndex((_localctx.suite!=null?(_localctx.suite.stop):null)));
			            result.setContainsBreak(containsBreak);
			            containsContinue = cFlag;
			            containsBreak = bFlag;
			        
			setState(887);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ELSE) {
				{
				setState(882);
				match(ELSE);
				setState(883);
				match(COLON);
				setState(884);
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
			setState(891);
			match(TRY);
			setState(892);
			match(COLON);
			setState(893);
			_localctx.body = _localctx.suite = suite();
			 int start = start(); 
			 
			            SSTNode elseStatement = null; 
			            SSTNode finallyStatement = null; 
			        
			setState(922);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case EXCEPT:
				{
				setState(899); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(896);
					_localctx.except_clause = except_clause();
					 push(_localctx.except_clause.result); 
					}
					}
					setState(901); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==EXCEPT );
				setState(908);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==ELSE) {
					{
					setState(903);
					match(ELSE);
					setState(904);
					match(COLON);
					setState(905);
					_localctx.suite = suite();
					 elseStatement = _localctx.suite.result; 
					}
				}

				setState(915);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==FINALLY) {
					{
					setState(910);
					match(FINALLY);
					setState(911);
					match(COLON);
					setState(912);
					_localctx.suite = suite();
					 finallyStatement = _localctx.suite.result; 
					}
				}

				}
				break;
			case FINALLY:
				{
				setState(917);
				match(FINALLY);
				setState(918);
				match(COLON);
				setState(919);
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
		enterRule(_localctx, 92, RULE_except_clause);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(926);
			match(EXCEPT);
			 SSTNode testNode = null; String asName = null; 
			setState(935);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(928);
				_localctx.test = test();
				 testNode = _localctx.test.result; 
				setState(933);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==AS) {
					{
					setState(930);
					match(AS);
					setState(931);
					_localctx.NAME = match(NAME);
					 
					                        asName = (_localctx.NAME!=null?_localctx.NAME.getText():null); 
					                        factory.getScopeEnvironment().createLocal(asName);
					                    
					}
				}

				}
			}

			setState(937);
			match(COLON);
			setState(938);
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
			setState(941);
			match(WITH);
			setState(942);
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
			setState(945);
			_localctx.test = test();
			 SSTNode asName = null; 
			setState(951);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AS) {
				{
				setState(947);
				match(AS);
				setState(948);
				_localctx.expr = expr();
				 asName = _localctx.expr.result; 
				}
			}

			 SSTNode sub; 
			setState(962);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case COMMA:
				{
				setState(954);
				match(COMMA);
				setState(955);
				_localctx.with_item = with_item();
				 sub = _localctx.with_item.result; 
				}
				break;
			case COLON:
				{
				setState(958);
				match(COLON);
				setState(959);
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
			setState(977);
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
				setState(967);
				simple_stmt();
				}
				break;
			case NEWLINE:
				{
				setState(968);
				match(NEWLINE);
				setState(969);
				match(INDENT);
				setState(971); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(970);
					stmt();
					}
					}
					setState(973); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << DEF) | (1L << RETURN) | (1L << RAISE) | (1L << FROM) | (1L << IMPORT) | (1L << GLOBAL) | (1L << NONLOCAL) | (1L << ASSERT) | (1L << IF) | (1L << WHILE) | (1L << FOR) | (1L << TRY) | (1L << WITH) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << CLASS) | (1L << YIELD) | (1L << DEL) | (1L << PASS) | (1L << CONTINUE) | (1L << BREAK) | (1L << ASYNC) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)) | (1L << (AT - 64)))) != 0) );
				setState(975);
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
			setState(994);
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
				setState(981);
				_localctx.or_test = or_test();
				 _localctx.result =  _localctx.or_test.result; 
				setState(989);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==IF) {
					{
					setState(983);
					match(IF);
					setState(984);
					_localctx.condition = _localctx.or_test = or_test();
					setState(985);
					match(ELSE);
					setState(986);
					_localctx.elTest = test();
					 _localctx.result =  new TernaryIfSSTNode(_localctx.condition.result, _localctx.result, _localctx.elTest.result, getStartIndex(_localctx), getLastIndex(_localctx));
					}
				}

				}
				break;
			case LAMBDA:
				enterOuterAlt(_localctx, 2);
				{
				setState(991);
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
			setState(1002);
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
				setState(996);
				_localctx.or_test = or_test();
				 _localctx.result =  _localctx.or_test.result; 
				}
				break;
			case LAMBDA:
				enterOuterAlt(_localctx, 2);
				{
				setState(999);
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
			setState(1004);
			match(LAMBDA);
			 ArgDefListBuilder args = null; 
			setState(1009);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(1006);
				_localctx.varargslist = varargslist();
				 args = _localctx.varargslist.result; 
				}
			}


			            ScopeInfo functionScope = scopeEnvironment.pushScope(ScopeEnvironment.LAMBDA_NAME, ScopeInfo.ScopeKind.Function); 
			            functionScope.setHasAnnotations(true);
			            if (args != null) {
			                args.defineParamsInScope(functionScope);
			            }
			        
			setState(1012);
			match(COLON);
			setState(1013);
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
			setState(1017);
			match(LAMBDA);
			 ArgDefListBuilder args = null; 
			setState(1022);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << NAME) | (1L << STAR) | (1L << POWER))) != 0)) {
				{
				setState(1019);
				_localctx.varargslist = varargslist();
				 args = _localctx.varargslist.result;
				}
			}


			            ScopeInfo functionScope = scopeEnvironment.pushScope(ScopeEnvironment.LAMBDA_NAME, ScopeInfo.ScopeKind.Function); 
			            functionScope.setHasAnnotations(true);
			            if (args != null) {
			                args.defineParamsInScope(functionScope);
			            }
			        
			setState(1025);
			match(COLON);
			setState(1026);
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
			setState(1030);
			_localctx.first = _localctx.and_test = and_test();
			setState(1044);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OR:
				{
				 int start = start(); 
				 push(_localctx.first.result); 
				setState(1037); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1033);
					match(OR);
					setState(1034);
					_localctx.and_test = and_test();
					 push(_localctx.and_test.result); 
					}
					}
					setState(1039); 
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
			setState(1046);
			_localctx.first = _localctx.not_test = not_test();
			setState(1060);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case AND:
				{
				 int start = start(); 
				 push(_localctx.first.result); 
				setState(1053); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1049);
					match(AND);
					setState(1050);
					_localctx.not_test = not_test();
					 push(_localctx.not_test.result); 
					}
					}
					setState(1055); 
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
			setState(1069);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case NOT:
				enterOuterAlt(_localctx, 1);
				{
				setState(1062);
				match(NOT);
				setState(1063);
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
				setState(1066);
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
			setState(1071);
			_localctx.first = _localctx.expr = expr();
			setState(1084);
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
				setState(1077); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1073);
					_localctx.comp_op = comp_op();
					setState(1074);
					_localctx.expr = expr();
					 pushString(_localctx.comp_op.result); push(_localctx.expr.result); 
					}
					}
					setState(1079); 
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
			setState(1110);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,129,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1086);
				match(LESS_THAN);
				 _localctx.result =  "<"; 
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(1088);
				match(GREATER_THAN);
				 _localctx.result =  ">"; 
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1090);
				match(EQUALS);
				 _localctx.result =  "=="; 
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(1092);
				match(GT_EQ);
				 _localctx.result =  ">="; 
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1094);
				match(LT_EQ);
				 _localctx.result =  "<="; 
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(1096);
				match(NOT_EQ_1);
				 _localctx.result =  "<>"; 
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(1098);
				match(NOT_EQ_2);
				 _localctx.result =  "!="; 
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(1100);
				match(IN);
				 _localctx.result =  "in"; 
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(1102);
				match(NOT);
				setState(1103);
				match(IN);
				 _localctx.result =  "notin"; 
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(1105);
				match(IS);
				 _localctx.result =  "is"; 
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(1107);
				match(IS);
				setState(1108);
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
			setState(1112);
			match(STAR);
			setState(1113);
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
			setState(1116);
			_localctx.xor_expr = xor_expr();
			 _localctx.result =  _localctx.xor_expr.result; 
			setState(1124);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==OR_OP) {
				{
				{
				setState(1118);
				match(OR_OP);
				setState(1119);
				_localctx.xor_expr = xor_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.Or, _localctx.result, _localctx.xor_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.xor_expr!=null?(_localctx.xor_expr.stop):null)));
				}
				}
				setState(1126);
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
			setState(1127);
			_localctx.and_expr = and_expr();
			 _localctx.result =  _localctx.and_expr.result; 
			setState(1135);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==XOR) {
				{
				{
				setState(1129);
				match(XOR);
				setState(1130);
				_localctx.and_expr = and_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.Xor, _localctx.result, _localctx.and_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.and_expr!=null?(_localctx.and_expr.stop):null))); 
				}
				}
				setState(1137);
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
			setState(1138);
			_localctx.shift_expr = shift_expr();
			 _localctx.result =  _localctx.shift_expr.result; 
			setState(1146);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==AND_OP) {
				{
				{
				setState(1140);
				match(AND_OP);
				setState(1141);
				_localctx.shift_expr = shift_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(BinaryArithmetic.And, _localctx.result, _localctx.shift_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.shift_expr!=null?(_localctx.shift_expr.stop):null))); 
				}
				}
				setState(1148);
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
			setState(1149);
			_localctx.arith_expr = arith_expr();
			 _localctx.result =  _localctx.arith_expr.result; 
			setState(1163);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==LEFT_SHIFT || _la==RIGHT_SHIFT) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1156);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case LEFT_SHIFT:
					{
					setState(1152);
					match(LEFT_SHIFT);
					 arithmetic = BinaryArithmetic.LShift; 
					}
					break;
				case RIGHT_SHIFT:
					{
					setState(1154);
					match(RIGHT_SHIFT);
					 arithmetic = BinaryArithmetic.RShift; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1158);
				_localctx.arith_expr = arith_expr();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.arith_expr.result, getStartIndex(_localctx), getStopIndex((_localctx.arith_expr!=null?(_localctx.arith_expr.stop):null)));
				}
				}
				setState(1165);
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
			setState(1166);
			_localctx.term = term();
			 _localctx.result =  _localctx.term.result; 
			setState(1180);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==ADD || _la==MINUS) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1173);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ADD:
					{
					setState(1169);
					match(ADD);
					 arithmetic = BinaryArithmetic.Add; 
					}
					break;
				case MINUS:
					{
					setState(1171);
					match(MINUS);
					 arithmetic = BinaryArithmetic.Sub; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1175);
				_localctx.term = term();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.term.result, getStartIndex(_localctx), getStopIndex((_localctx.term!=null?(_localctx.term.stop):null))); 
				}
				}
				setState(1182);
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
			setState(1183);
			_localctx.factor = factor();
			 _localctx.result =  _localctx.factor.result; 
			setState(1203);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (((((_la - 49)) & ~0x3f) == 0 && ((1L << (_la - 49)) & ((1L << (STAR - 49)) | (1L << (DIV - 49)) | (1L << (MOD - 49)) | (1L << (IDIV - 49)) | (1L << (AT - 49)))) != 0)) {
				{
				{
				 BinaryArithmetic arithmetic; 
				setState(1196);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case STAR:
					{
					setState(1186);
					match(STAR);
					 arithmetic = BinaryArithmetic.Mul; 
					}
					break;
				case AT:
					{
					setState(1188);
					match(AT);
					 arithmetic = BinaryArithmetic.MatMul; 
					}
					break;
				case DIV:
					{
					setState(1190);
					match(DIV);
					 arithmetic = BinaryArithmetic.TrueDiv; 
					}
					break;
				case MOD:
					{
					setState(1192);
					match(MOD);
					 arithmetic = BinaryArithmetic.Mod; 
					}
					break;
				case IDIV:
					{
					setState(1194);
					match(IDIV);
					 arithmetic = BinaryArithmetic.FloorDiv; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1198);
				_localctx.factor = factor();
				 _localctx.result =  new BinaryArithmeticSSTNode(arithmetic, _localctx.result, _localctx.factor.result, getStartIndex(_localctx), getStopIndex((_localctx.factor!=null?(_localctx.factor.stop):null))); 
				}
				}
				setState(1205);
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
			setState(1221);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ADD:
			case MINUS:
			case NOT_OP:
				enterOuterAlt(_localctx, 1);
				{
				 
				            UnaryArithmetic arithmetic; 
				            boolean isNeg = false;
				        
				setState(1213);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case ADD:
					{
					setState(1207);
					match(ADD);
					 arithmetic = UnaryArithmetic.Pos; 
					}
					break;
				case MINUS:
					{
					setState(1209);
					_localctx.m = match(MINUS);
					 arithmetic = UnaryArithmetic.Neg; isNeg = true; 
					}
					break;
				case NOT_OP:
					{
					setState(1211);
					match(NOT_OP);
					 arithmetic = UnaryArithmetic.Invert; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1215);
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
				setState(1218);
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
			setState(1223);
			_localctx.atom_expr = atom_expr();
			 _localctx.result =  _localctx.atom_expr.result; 
			setState(1229);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==POWER) {
				{
				setState(1225);
				match(POWER);
				setState(1226);
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
		enterRule(_localctx, 136, RULE_atom_expr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(1232);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==AWAIT) {
				{
				setState(1231);
				match(AWAIT);
				}
			}

			setState(1234);
			_localctx.atom = atom();
			 _localctx.result =  _localctx.atom.result; 
			setState(1251);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << DOT) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0)) {
				{
				setState(1249);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case OPEN_PAREN:
					{
					setState(1236);
					match(OPEN_PAREN);
					setState(1237);
					_localctx.arglist = arglist();
					setState(1238);
					_localctx.CloseB = match(CLOSE_PAREN);
					 _localctx.result =  new CallSSTNode(_localctx.result, _localctx.arglist.result, getStartIndex(_localctx), _localctx.CloseB.getStopIndex() + 1);
					}
					break;
				case OPEN_BRACK:
					{
					setState(1241);
					match(OPEN_BRACK);
					setState(1242);
					_localctx.subscriptlist = subscriptlist();
					setState(1243);
					_localctx.c = match(CLOSE_BRACK);
					 _localctx.result =  new SubscriptSSTNode(_localctx.result, _localctx.subscriptlist.result, getStartIndex(_localctx), _localctx.c.getStopIndex() + 1);
					}
					break;
				case DOT:
					{
					setState(1246);
					match(DOT);
					setState(1247);
					_localctx.NAME = match(NAME);
					   
					                    assert _localctx.NAME != null;
					                    _localctx.result =  new GetAttributeSSTNode(_localctx.result, (_localctx.NAME!=null?_localctx.NAME.getText():null), getStartIndex(_localctx), getStopIndex(_localctx.NAME));
					                
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(1253);
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
			setState(1317);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case OPEN_PAREN:
				enterOuterAlt(_localctx, 1);
				{
				setState(1254);
				match(OPEN_PAREN);
				setState(1262);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case YIELD:
					{
					setState(1255);
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
					setState(1258);
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
				setState(1264);
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
				setState(1266);
				_localctx.startIndex = match(OPEN_BRACK);
				setState(1271);
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
					setState(1267);
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
				setState(1273);
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
				setState(1275);
				_localctx.startIndex = match(OPEN_BRACE);
				setState(1283);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,147,_ctx) ) {
				case 1:
					{
					setState(1276);
					_localctx.dictmaker = dictmaker();
					 _localctx.result =  _localctx.dictmaker.result; 
					}
					break;
				case 2:
					{
					setState(1279);
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
				setState(1285);
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
				setState(1287);
				_localctx.NAME = match(NAME);
				   
				                String text = (_localctx.NAME!=null?_localctx.NAME.getText():null);
				                _localctx.result =  text != null ? factory.createVariableLookup(text,  _localctx.NAME.getStartIndex(), _localctx.NAME.getStopIndex() + 1) : null; 
				            
				}
				break;
			case DECIMAL_INTEGER:
				enterOuterAlt(_localctx, 5);
				{
				setState(1289);
				_localctx.DECIMAL_INTEGER = match(DECIMAL_INTEGER);
				 
				                String text = (_localctx.DECIMAL_INTEGER!=null?_localctx.DECIMAL_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 0, 10, _localctx.DECIMAL_INTEGER.getStartIndex(), _localctx.DECIMAL_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case OCT_INTEGER:
				enterOuterAlt(_localctx, 6);
				{
				setState(1291);
				_localctx.OCT_INTEGER = match(OCT_INTEGER);
				 
				                String text = (_localctx.OCT_INTEGER!=null?_localctx.OCT_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 2, 8, _localctx.OCT_INTEGER.getStartIndex(), _localctx.OCT_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case HEX_INTEGER:
				enterOuterAlt(_localctx, 7);
				{
				setState(1293);
				_localctx.HEX_INTEGER = match(HEX_INTEGER);
				 
				                String text = (_localctx.HEX_INTEGER!=null?_localctx.HEX_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 2, 16, _localctx.HEX_INTEGER.getStartIndex(), _localctx.HEX_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 8);
				{
				setState(1295);
				_localctx.BIN_INTEGER = match(BIN_INTEGER);
				 
				                String text = (_localctx.BIN_INTEGER!=null?_localctx.BIN_INTEGER.getText():null);
				                _localctx.result =  text != null ? new NumberLiteralSSTNode(text, 2, 2, _localctx.BIN_INTEGER.getStartIndex(), _localctx.BIN_INTEGER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case FLOAT_NUMBER:
				enterOuterAlt(_localctx, 9);
				{
				setState(1297);
				_localctx.FLOAT_NUMBER = match(FLOAT_NUMBER);
				   
				                String text = (_localctx.FLOAT_NUMBER!=null?_localctx.FLOAT_NUMBER.getText():null);
				                _localctx.result =  text != null ? new FloatLiteralSSTNode(text, false, _localctx.FLOAT_NUMBER.getStartIndex(), _localctx.FLOAT_NUMBER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case IMAG_NUMBER:
				enterOuterAlt(_localctx, 10);
				{
				setState(1299);
				_localctx.IMAG_NUMBER = match(IMAG_NUMBER);
				 
				                String text = (_localctx.IMAG_NUMBER!=null?_localctx.IMAG_NUMBER.getText():null);
				                _localctx.result =  text != null ? new FloatLiteralSSTNode(text, true, _localctx.IMAG_NUMBER.getStartIndex(), _localctx.IMAG_NUMBER.getStopIndex() + 1) : null; 
				            
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 11);
				{
				 int start = stringStart(); 
				setState(1304); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(1302);
					_localctx.STRING = match(STRING);
					 pushString((_localctx.STRING!=null?_localctx.STRING.getText():null)); 
					}
					}
					setState(1306); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==STRING );
				 _localctx.result =  new StringLiteralSSTNode(getStringArray(start), getStartIndex(_localctx), getStopIndex(_localctx.STRING)); 
				}
				break;
			case ELLIPSIS:
				enterOuterAlt(_localctx, 12);
				{
				setState(1309);
				_localctx.t = match(ELLIPSIS);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new SimpleSSTNode(SimpleSSTNode.Type.ELLIPSIS,  start, start + 3);
				}
				break;
			case NONE:
				enterOuterAlt(_localctx, 13);
				{
				setState(1311);
				_localctx.t = match(NONE);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new SimpleSSTNode(SimpleSSTNode.Type.NONE,  start, start + 4);
				}
				break;
			case TRUE:
				enterOuterAlt(_localctx, 14);
				{
				setState(1313);
				_localctx.t = match(TRUE);
				 int start = _localctx.t.getStartIndex(); _localctx.result =  new BooleanLiteralSSTNode(true,  start, start + 4); 
				}
				break;
			case FALSE:
				enterOuterAlt(_localctx, 15);
				{
				setState(1315);
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
			setState(1319);
			_localctx.subscript = subscript();
			 _localctx.result =  _localctx.subscript.result; 
			setState(1340);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 int start = start(); push(_localctx.result); 
				setState(1322);
				match(COMMA);
				setState(1337);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << COLON) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1323);
					_localctx.subscript = subscript();
					 push(_localctx.subscript.result); 
					setState(1331);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,150,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(1325);
							match(COMMA);
							setState(1326);
							_localctx.subscript = subscript();
							 push(_localctx.subscript.result); 
							}
							} 
						}
						setState(1333);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,150,_ctx);
					}
					setState(1335);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(1334);
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
			setState(1366);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,158,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(1342);
				_localctx.test = test();
				 _localctx.result =  _localctx.test.result; 
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				 SSTNode sliceStart = null; SSTNode sliceEnd = null; SSTNode sliceStep = null; 
				setState(1349);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1346);
					_localctx.test = test();
					 sliceStart = _localctx.test.result; 
					}
				}

				setState(1351);
				match(COLON);
				setState(1355);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1352);
					_localctx.test = test();
					 sliceEnd = _localctx.test.result; 
					}
				}

				setState(1363);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COLON) {
					{
					setState(1357);
					match(COLON);
					setState(1361);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
						{
						setState(1358);
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
			setState(1375);
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
				setState(1369);
				_localctx.expr = expr();
				 push(_localctx.expr.result); 
				}
				break;
			case STAR:
				{
				setState(1372);
				_localctx.star_expr = star_expr();
				 push(_localctx.star_expr.result); 
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(1388);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,161,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(1377);
					match(COMMA);
					setState(1384);
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
						setState(1378);
						_localctx.expr = expr();
						 push(_localctx.expr.result); 
						}
						break;
					case STAR:
						{
						setState(1381);
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
				setState(1390);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,161,_ctx);
			}
			setState(1392);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				setState(1391);
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
			setState(1396);
			_localctx.test = test();
			 _localctx.result =  _localctx.test.result; 
			setState(1417);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==COMMA) {
				{
				 int start = start(); push(_localctx.result); 
				setState(1399);
				match(COMMA);
				setState(1414);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << OPEN_PAREN) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
					{
					setState(1400);
					_localctx.test = test();
					 push(_localctx.test.result); 
					setState(1408);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,163,_ctx);
					while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
						if ( _alt==1 ) {
							{
							{
							setState(1402);
							match(COMMA);
							setState(1403);
							_localctx.test = test();
							 push(_localctx.test.result); 
							}
							} 
						}
						setState(1410);
						_errHandler.sync(this);
						_alt = getInterpreter().adaptivePredict(_input,163,_ctx);
					}
					setState(1412);
					_errHandler.sync(this);
					_la = _input.LA(1);
					if (_la==COMMA) {
						{
						setState(1411);
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
			setState(1469);
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
				                
				            
				setState(1429);
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
					setState(1420);
					_localctx.n = test();
					setState(1421);
					match(COLON);
					setState(1422);
					_localctx.v = test();
					 name = _localctx.n.result; value = _localctx.v.result; 
					}
					break;
				case POWER:
					{
					setState(1425);
					match(POWER);
					setState(1426);
					_localctx.expr = expr();
					 name = null; value = _localctx.expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1431);
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
				            
				setState(1444);
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
					setState(1435);
					_localctx.n = test();
					setState(1436);
					match(COLON);
					setState(1437);
					_localctx.v = test();
					 name = _localctx.n.result; value = _localctx.v.result; 
					}
					break;
				case POWER:
					{
					setState(1440);
					match(POWER);
					setState(1441);
					_localctx.expr = expr();
					 name = null; value = _localctx.expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 int start = start(); push(name); push(value); 
				setState(1461);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,170,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1447);
						match(COMMA);
						setState(1457);
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
							setState(1448);
							_localctx.n = test();
							setState(1449);
							match(COLON);
							setState(1450);
							_localctx.v = test();
							 push(_localctx.n.result); push(_localctx.v.result); 
							}
							break;
						case POWER:
							{
							setState(1453);
							match(POWER);
							setState(1454);
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
					setState(1463);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,170,_ctx);
				}
				setState(1465);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1464);
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
			setState(1514);
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
				            
				setState(1478);
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
					setState(1472);
					_localctx.test = test();
					 value = _localctx.test.result; 
					}
					break;
				case STAR:
					{
					setState(1475);
					_localctx.star_expr = star_expr();
					 value = _localctx.star_expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(1480);
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
				setState(1490);
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
					_localctx.test = test();
					 value = _localctx.test.result; 
					}
					break;
				case STAR:
					{
					setState(1487);
					_localctx.star_expr = star_expr();
					 value = _localctx.star_expr.result; 
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				 int start = start(); push(value); 
				setState(1504);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,176,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1493);
						match(COMMA);
						setState(1500);
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
							setState(1494);
							_localctx.test = test();
							 push(_localctx.test.result); 
							}
							break;
						case STAR:
							{
							setState(1497);
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
					setState(1506);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,176,_ctx);
				}
				 boolean comma = false; 
				setState(1510);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1508);
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
			setState(1516);
			match(CLASS);
			setState(1517);
			_localctx.NAME = match(NAME);
			 ArgListBuilder baseClasses = null; 
			setState(1524);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==OPEN_PAREN) {
				{
				setState(1519);
				match(OPEN_PAREN);
				setState(1520);
				_localctx.arglist = arglist();
				setState(1521);
				match(CLOSE_PAREN);
				 baseClasses = _localctx.arglist.result; 
				}
			}


			            // we need to create the scope here to resolve base classes in the outer scope
			            factory.getScopeEnvironment().createLocal((_localctx.NAME!=null?_localctx.NAME.getText():null));
			            ScopeInfo classScope = scopeEnvironment.pushScope((_localctx.NAME!=null?_localctx.NAME.getText():null), ScopeInfo.ScopeKind.Class); 
			        
			setState(1527);
			match(COLON);
			setState(1528);
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
			setState(1544);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << STRING) | (1L << LAMBDA) | (1L << NOT) | (1L << NONE) | (1L << TRUE) | (1L << FALSE) | (1L << AWAIT) | (1L << NAME) | (1L << DECIMAL_INTEGER) | (1L << OCT_INTEGER) | (1L << HEX_INTEGER) | (1L << BIN_INTEGER) | (1L << FLOAT_NUMBER) | (1L << IMAG_NUMBER) | (1L << ELLIPSIS) | (1L << STAR) | (1L << OPEN_PAREN) | (1L << POWER) | (1L << OPEN_BRACK))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (ADD - 64)) | (1L << (MINUS - 64)) | (1L << (NOT_OP - 64)) | (1L << (OPEN_BRACE - 64)))) != 0)) {
				{
				setState(1533);
				argument(args);
				setState(1538);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,180,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(1534);
						match(COMMA);
						setState(1535);
						argument(args);
						}
						} 
					}
					setState(1540);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,180,_ctx);
				}
				setState(1542);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==COMMA) {
					{
					setState(1541);
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
			setState(1571);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,183,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{

				                    ScopeInfo generator = scopeEnvironment.pushScope("generator"+_localctx.getStart().getStartIndex(), ScopeInfo.ScopeKind.GenExp); 
				                    generator.setHasAnnotations(true);
				                
				setState(1549);
				_localctx.test = test();
				setState(1550);
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
				                
				setState(1554);
				_localctx.n = _localctx.test = test();

				                    if (!((_localctx.n).result instanceof VarLookupSSTNode)) {
				                        throw new PythonRecognitionException("keyword can't be an expression", this, _input, _localctx, getCurrentToken());
				                    }
				                    if (!isNameAsVariableInScope && scopeEnvironment.getCurrentScope().getSeenVars().contains(name)) {
				                        scopeEnvironment.getCurrentScope().getSeenVars().remove(name);
				                    }
				                
				setState(1556);
				match(ASSIGN);
				setState(1557);
				_localctx.test = test();
				 
				                        args.addNamedArg(name, _localctx.test.result); 
				                    
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(1560);
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
				setState(1563);
				match(POWER);
				setState(1564);
				_localctx.test = test();
				 args.addKwArg(_localctx.test.result); 
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(1567);
				match(STAR);
				setState(1568);
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
			        
			setState(1576);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ASYNC) {
				{
				setState(1574);
				match(ASYNC);
				 async = true; 
				}
			}

			 
			            SSTNode iterator; 
			            SSTNode[] variables;
			            int lineNumber;
			        
			setState(1579);
			_localctx.f = match(FOR);
			setState(1580);
			_localctx.exprlist = exprlist();
			setState(1581);
			match(IN);

			                ScopeInfo currentScope = null;
			                if (level == 0) {
			                    currentScope = scopeEnvironment.getCurrentScope();
			                    factory.getScopeEnvironment().setCurrentScope(currentScope.getParent());
			                }
			            
			setState(1583);
			_localctx.or_test = or_test();
			   
			            if (level == 0) {
			                factory.getScopeEnvironment().setCurrentScope(currentScope);
			            }
			            lineNumber = _localctx.f.getLine();
			            iterator = _localctx.or_test.result; 
			            variables = _localctx.exprlist.result;
			        
			 int start = start(); 
			setState(1592);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==IF) {
				{
				{
				setState(1586);
				match(IF);
				setState(1587);
				_localctx.test_nocond = test_nocond();
				 push(_localctx.test_nocond.result); 
				}
				}
				setState(1594);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			 SSTNode[] conditions = getArray(start, SSTNode[].class); 
			setState(1599);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==FOR || _la==ASYNC) {
				{
				setState(1596);
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
			setState(1603);
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
		enterRule(_localctx, 162, RULE_yield_expr);
		try {
			enterOuterAlt(_localctx, 1);
			{
			 
			        SSTNode value = null;
			        boolean isFrom = false; 
			    
			setState(1606);
			match(YIELD);
			setState(1614);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case FROM:
				{
				setState(1607);
				match(FROM);
				setState(1608);
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
				setState(1611);
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3e\u0655\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4I"+
		"\tI\4J\tJ\4K\tK\4L\tL\4M\tM\4N\tN\4O\tO\4P\tP\4Q\tQ\4R\tR\4S\tS\3\2\3"+
		"\2\3\2\3\2\3\2\5\2\u00ac\n\2\3\2\3\2\3\2\3\3\3\3\3\3\3\3\7\3\u00b5\n\3"+
		"\f\3\16\3\u00b8\13\3\3\3\3\3\3\3\3\3\3\4\3\4\3\4\7\4\u00c1\n\4\f\4\16"+
		"\4\u00c4\13\4\3\4\3\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\u00d2"+
		"\n\5\3\5\3\5\3\5\3\6\3\6\6\6\u00d9\n\6\r\6\16\6\u00da\3\6\3\6\3\7\3\7"+
		"\3\7\3\7\3\7\5\7\u00e4\n\7\3\7\3\7\3\b\3\b\3\b\3\t\3\t\3\t\3\t\3\t\5\t"+
		"\u00f0\n\t\3\t\3\t\3\t\3\t\3\t\3\n\3\n\3\n\5\n\u00fa\n\n\3\n\3\n\3\n\3"+
		"\13\3\13\3\13\7\13\u0102\n\13\f\13\16\13\u0105\13\13\3\13\3\13\3\13\3"+
		"\13\3\13\3\13\3\13\7\13\u010e\n\13\f\13\16\13\u0111\13\13\5\13\u0113\n"+
		"\13\3\13\3\13\3\13\3\13\7\13\u0119\n\13\f\13\16\13\u011c\13\13\3\13\3"+
		"\13\3\13\5\13\u0121\n\13\5\13\u0123\n\13\5\13\u0125\n\13\3\13\3\13\5\13"+
		"\u0129\n\13\5\13\u012b\n\13\5\13\u012d\n\13\5\13\u012f\n\13\3\13\3\13"+
		"\3\13\7\13\u0134\n\13\f\13\16\13\u0137\13\13\3\13\3\13\3\13\3\13\7\13"+
		"\u013d\n\13\f\13\16\13\u0140\13\13\3\13\3\13\3\13\5\13\u0145\n\13\5\13"+
		"\u0147\n\13\5\13\u0149\n\13\3\13\3\13\5\13\u014d\n\13\5\13\u014f\n\13"+
		"\5\13\u0151\n\13\3\13\3\13\3\13\7\13\u0156\n\13\f\13\16\13\u0159\13\13"+
		"\3\13\3\13\3\13\5\13\u015e\n\13\5\13\u0160\n\13\5\13\u0162\n\13\3\13\3"+
		"\13\5\13\u0166\n\13\5\13\u0168\n\13\3\f\3\f\3\f\3\f\3\f\3\f\5\f\u0170"+
		"\n\f\3\f\3\f\3\f\3\f\5\f\u0176\n\f\3\f\3\f\3\r\3\r\3\r\3\r\3\r\3\r\3\r"+
		"\3\r\5\r\u0182\n\r\5\r\u0184\n\r\3\r\3\r\3\16\3\16\3\16\3\16\3\16\3\16"+
		"\3\16\5\16\u018f\n\16\3\16\3\16\3\17\3\17\3\17\3\17\7\17\u0197\n\17\f"+
		"\17\16\17\u019a\13\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\7\17\u01a3\n"+
		"\17\f\17\16\17\u01a6\13\17\5\17\u01a8\n\17\3\17\3\17\3\17\3\17\7\17\u01ae"+
		"\n\17\f\17\16\17\u01b1\13\17\3\17\3\17\3\17\5\17\u01b6\n\17\5\17\u01b8"+
		"\n\17\5\17\u01ba\n\17\3\17\3\17\5\17\u01be\n\17\5\17\u01c0\n\17\5\17\u01c2"+
		"\n\17\5\17\u01c4\n\17\3\17\3\17\3\17\7\17\u01c9\n\17\f\17\16\17\u01cc"+
		"\13\17\3\17\3\17\3\17\3\17\7\17\u01d2\n\17\f\17\16\17\u01d5\13\17\3\17"+
		"\3\17\3\17\5\17\u01da\n\17\5\17\u01dc\n\17\5\17\u01de\n\17\3\17\3\17\5"+
		"\17\u01e2\n\17\5\17\u01e4\n\17\5\17\u01e6\n\17\3\17\3\17\3\17\7\17\u01eb"+
		"\n\17\f\17\16\17\u01ee\13\17\3\17\3\17\3\17\5\17\u01f3\n\17\5\17\u01f5"+
		"\n\17\5\17\u01f7\n\17\3\17\3\17\5\17\u01fb\n\17\5\17\u01fd\n\17\3\17\3"+
		"\17\3\20\3\20\3\20\3\20\3\20\3\20\5\20\u0207\n\20\3\20\3\20\3\21\3\21"+
		"\3\21\3\21\5\21\u020f\n\21\3\21\3\21\3\22\3\22\3\22\3\22\3\23\3\23\5\23"+
		"\u0219\n\23\3\24\3\24\3\24\7\24\u021e\n\24\f\24\16\24\u0221\13\24\3\24"+
		"\5\24\u0224\n\24\3\24\3\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\5\25\u0231\n\25\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u023b\n"+
		"\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u0246\n\26\3\26"+
		"\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\3\26\5\26\u0254\n\26"+
		"\7\26\u0256\n\26\f\26\16\26\u0259\13\26\3\26\5\26\u025c\n\26\3\27\3\27"+
		"\3\27\3\27\3\27\3\27\5\27\u0264\n\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27"+
		"\3\27\5\27\u026e\n\27\3\27\3\27\3\27\3\27\3\27\3\27\3\27\5\27\u0277\n"+
		"\27\7\27\u0279\n\27\f\27\16\27\u027c\13\27\3\27\5\27\u027f\n\27\5\27\u0281"+
		"\n\27\3\27\5\27\u0284\n\27\3\30\3\30\3\31\3\31\3\31\3\31\3\32\3\32\3\32"+
		"\3\32\3\32\3\32\3\32\5\32\u0293\n\32\3\33\3\33\3\33\3\33\3\33\5\33\u029a"+
		"\n\33\3\33\3\33\3\34\3\34\3\34\3\35\3\35\3\35\3\35\3\35\3\35\3\35\3\35"+
		"\5\35\u02a9\n\35\5\35\u02ab\n\35\3\35\3\35\3\36\3\36\5\36\u02b1\n\36\3"+
		"\37\3\37\3\37\3 \3 \3 \3 \3 \3 \7 \u02bc\n \f \16 \u02bf\13 \3 \3 \3 "+
		"\3 \3 \3 \3 \6 \u02c8\n \r \16 \u02c9\5 \u02cc\n \3 \3 \3 \3 \3 \3 \3"+
		" \3 \3 \3 \3 \5 \u02d9\n \3 \3 \3!\3!\3!\3!\3!\5!\u02e2\n!\3!\3!\3\"\3"+
		"\"\3\"\3\"\3\"\3\"\3\"\7\"\u02ed\n\"\f\"\16\"\u02f0\13\"\3\"\5\"\u02f3"+
		"\n\"\3\"\3\"\3#\3#\3#\3#\3#\7#\u02fc\n#\f#\16#\u02ff\13#\3$\3$\3$\3$\3"+
		"$\5$\u0306\n$\3%\3%\3%\7%\u030b\n%\f%\16%\u030e\13%\3&\3&\3&\3&\3&\3&"+
		"\3&\7&\u0317\n&\f&\16&\u031a\13&\3&\3&\3\'\3\'\3\'\3\'\3\'\3\'\3\'\7\'"+
		"\u0325\n\'\f\'\16\'\u0328\13\'\3\'\3\'\3(\3(\3(\3(\3(\3(\3(\5(\u0333\n"+
		"(\3(\3(\3)\3)\3)\3)\3)\3)\3)\3)\3)\5)\u0340\n)\3*\3*\3*\3*\5*\u0346\n"+
		"*\3+\3+\3+\3+\3+\3+\3+\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\3,\5,\u035c"+
		"\n,\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\3-\5-\u0369\n-\3-\3-\3.\3.\3.\3.\3."+
		"\3.\3.\3.\3.\3.\3.\3.\3.\5.\u037a\n.\3.\3.\3/\3/\3/\3/\3/\3/\3/\3/\6/"+
		"\u0386\n/\r/\16/\u0387\3/\3/\3/\3/\3/\5/\u038f\n/\3/\3/\3/\3/\3/\5/\u0396"+
		"\n/\3/\3/\3/\3/\3/\5/\u039d\n/\3/\3/\3\60\3\60\3\60\3\60\3\60\3\60\3\60"+
		"\5\60\u03a8\n\60\5\60\u03aa\n\60\3\60\3\60\3\60\3\60\3\61\3\61\3\61\3"+
		"\61\3\62\3\62\3\62\3\62\3\62\3\62\5\62\u03ba\n\62\3\62\3\62\3\62\3\62"+
		"\3\62\3\62\3\62\3\62\3\62\5\62\u03c5\n\62\3\62\3\62\3\63\3\63\3\63\3\63"+
		"\3\63\6\63\u03ce\n\63\r\63\16\63\u03cf\3\63\3\63\5\63\u03d4\n\63\3\63"+
		"\3\63\3\64\3\64\3\64\3\64\3\64\3\64\3\64\3\64\5\64\u03e0\n\64\3\64\3\64"+
		"\3\64\5\64\u03e5\n\64\3\65\3\65\3\65\3\65\3\65\3\65\5\65\u03ed\n\65\3"+
		"\66\3\66\3\66\3\66\3\66\5\66\u03f4\n\66\3\66\3\66\3\66\3\66\3\66\3\66"+
		"\3\67\3\67\3\67\3\67\3\67\5\67\u0401\n\67\3\67\3\67\3\67\3\67\3\67\3\67"+
		"\38\38\38\38\38\38\38\68\u0410\n8\r8\168\u0411\38\38\38\58\u0417\n8\3"+
		"9\39\39\39\39\39\39\69\u0420\n9\r9\169\u0421\39\39\39\59\u0427\n9\3:\3"+
		":\3:\3:\3:\3:\3:\5:\u0430\n:\3;\3;\3;\3;\3;\3;\6;\u0438\n;\r;\16;\u0439"+
		"\3;\3;\3;\5;\u043f\n;\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<\3<"+
		"\3<\3<\3<\3<\3<\3<\3<\3<\5<\u0459\n<\3=\3=\3=\3=\3>\3>\3>\3>\3>\3>\7>"+
		"\u0465\n>\f>\16>\u0468\13>\3?\3?\3?\3?\3?\3?\7?\u0470\n?\f?\16?\u0473"+
		"\13?\3@\3@\3@\3@\3@\3@\7@\u047b\n@\f@\16@\u047e\13@\3A\3A\3A\3A\3A\3A"+
		"\3A\5A\u0487\nA\3A\3A\3A\7A\u048c\nA\fA\16A\u048f\13A\3B\3B\3B\3B\3B\3"+
		"B\3B\5B\u0498\nB\3B\3B\3B\7B\u049d\nB\fB\16B\u04a0\13B\3C\3C\3C\3C\3C"+
		"\3C\3C\3C\3C\3C\3C\3C\3C\5C\u04af\nC\3C\3C\3C\7C\u04b4\nC\fC\16C\u04b7"+
		"\13C\3D\3D\3D\3D\3D\3D\3D\5D\u04c0\nD\3D\3D\3D\3D\3D\3D\5D\u04c8\nD\3"+
		"E\3E\3E\3E\3E\3E\5E\u04d0\nE\3F\5F\u04d3\nF\3F\3F\3F\3F\3F\3F\3F\3F\3"+
		"F\3F\3F\3F\3F\3F\3F\7F\u04e4\nF\fF\16F\u04e7\13F\3G\3G\3G\3G\3G\3G\3G"+
		"\3G\5G\u04f1\nG\3G\3G\3G\3G\3G\3G\3G\5G\u04fa\nG\3G\3G\3G\3G\3G\3G\3G"+
		"\3G\3G\3G\5G\u0506\nG\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G\3G"+
		"\3G\3G\3G\6G\u051b\nG\rG\16G\u051c\3G\3G\3G\3G\3G\3G\3G\3G\3G\5G\u0528"+
		"\nG\3H\3H\3H\3H\3H\3H\3H\3H\3H\3H\7H\u0534\nH\fH\16H\u0537\13H\3H\5H\u053a"+
		"\nH\5H\u053c\nH\3H\5H\u053f\nH\3I\3I\3I\3I\3I\3I\3I\5I\u0548\nI\3I\3I"+
		"\3I\3I\5I\u054e\nI\3I\3I\3I\3I\5I\u0554\nI\5I\u0556\nI\3I\5I\u0559\nI"+
		"\3J\3J\3J\3J\3J\3J\3J\5J\u0562\nJ\3J\3J\3J\3J\3J\3J\3J\5J\u056b\nJ\7J"+
		"\u056d\nJ\fJ\16J\u0570\13J\3J\5J\u0573\nJ\3J\3J\3K\3K\3K\3K\3K\3K\3K\3"+
		"K\3K\3K\7K\u0581\nK\fK\16K\u0584\13K\3K\5K\u0587\nK\5K\u0589\nK\3K\5K"+
		"\u058c\nK\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\5L\u0598\nL\3L\3L\3L\3L\3L\3L"+
		"\3L\3L\3L\3L\3L\3L\3L\5L\u05a7\nL\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\3L\5L"+
		"\u05b4\nL\7L\u05b6\nL\fL\16L\u05b9\13L\3L\5L\u05bc\nL\3L\3L\5L\u05c0\n"+
		"L\3M\3M\3M\3M\3M\3M\3M\5M\u05c9\nM\3M\3M\3M\3M\3M\3M\3M\3M\3M\3M\5M\u05d5"+
		"\nM\3M\3M\3M\3M\3M\3M\3M\3M\5M\u05df\nM\7M\u05e1\nM\fM\16M\u05e4\13M\3"+
		"M\3M\3M\5M\u05e9\nM\3M\3M\5M\u05ed\nM\3N\3N\3N\3N\3N\3N\3N\3N\5N\u05f7"+
		"\nN\3N\3N\3N\3N\3N\3N\3O\3O\3O\3O\7O\u0603\nO\fO\16O\u0606\13O\3O\5O\u0609"+
		"\nO\5O\u060b\nO\3O\3O\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P\3P"+
		"\3P\3P\3P\3P\3P\3P\3P\5P\u0626\nP\3Q\3Q\3Q\5Q\u062b\nQ\3Q\3Q\3Q\3Q\3Q"+
		"\3Q\3Q\3Q\3Q\3Q\3Q\3Q\7Q\u0639\nQ\fQ\16Q\u063c\13Q\3Q\3Q\3Q\3Q\5Q\u0642"+
		"\nQ\3Q\3Q\3R\3R\3S\3S\3S\3S\3S\3S\3S\3S\3S\5S\u0651\nS\3S\3S\3S\2\2T\2"+
		"\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJL"+
		"NPRTVXZ\\^`bdfhjlnprtvxz|~\u0080\u0082\u0084\u0086\u0088\u008a\u008c\u008e"+
		"\u0090\u0092\u0094\u0096\u0098\u009a\u009c\u009e\u00a0\u00a2\u00a4\2\3"+
		"\3\2S_\2\u06fd\2\u00a6\3\2\2\2\4\u00b0\3\2\2\2\6\u00bd\3\2\2\2\b\u00c9"+
		"\3\2\2\2\n\u00d6\3\2\2\2\f\u00de\3\2\2\2\16\u00e7\3\2\2\2\20\u00ea\3\2"+
		"\2\2\22\u00f6\3\2\2\2\24\u0167\3\2\2\2\26\u0169\3\2\2\2\30\u0179\3\2\2"+
		"\2\32\u0187\3\2\2\2\34\u0192\3\2\2\2\36\u0200\3\2\2\2 \u020a\3\2\2\2\""+
		"\u0212\3\2\2\2$\u0218\3\2\2\2&\u021a\3\2\2\2(\u0230\3\2\2\2*\u0232\3\2"+
		"\2\2,\u0263\3\2\2\2.\u0285\3\2\2\2\60\u0287\3\2\2\2\62\u0292\3\2\2\2\64"+
		"\u0294\3\2\2\2\66\u029d\3\2\2\28\u02a0\3\2\2\2:\u02b0\3\2\2\2<\u02b2\3"+
		"\2\2\2>\u02b5\3\2\2\2@\u02dc\3\2\2\2B\u02e5\3\2\2\2D\u02f6\3\2\2\2F\u0300"+
		"\3\2\2\2H\u0307\3\2\2\2J\u030f\3\2\2\2L\u031d\3\2\2\2N\u032b\3\2\2\2P"+
		"\u033f\3\2\2\2R\u0341\3\2\2\2T\u0347\3\2\2\2V\u035b\3\2\2\2X\u035d\3\2"+
		"\2\2Z\u036c\3\2\2\2\\\u037d\3\2\2\2^\u03a0\3\2\2\2`\u03af\3\2\2\2b\u03b3"+
		"\3\2\2\2d\u03c8\3\2\2\2f\u03e4\3\2\2\2h\u03ec\3\2\2\2j\u03ee\3\2\2\2l"+
		"\u03fb\3\2\2\2n\u0408\3\2\2\2p\u0418\3\2\2\2r\u042f\3\2\2\2t\u0431\3\2"+
		"\2\2v\u0458\3\2\2\2x\u045a\3\2\2\2z\u045e\3\2\2\2|\u0469\3\2\2\2~\u0474"+
		"\3\2\2\2\u0080\u047f\3\2\2\2\u0082\u0490\3\2\2\2\u0084\u04a1\3\2\2\2\u0086"+
		"\u04c7\3\2\2\2\u0088\u04c9\3\2\2\2\u008a\u04d2\3\2\2\2\u008c\u0527\3\2"+
		"\2\2\u008e\u0529\3\2\2\2\u0090\u0558\3\2\2\2\u0092\u055a\3\2\2\2\u0094"+
		"\u0576\3\2\2\2\u0096\u05bf\3\2\2\2\u0098\u05ec\3\2\2\2\u009a\u05ee\3\2"+
		"\2\2\u009c\u05fe\3\2\2\2\u009e\u0625\3\2\2\2\u00a0\u0627\3\2\2\2\u00a2"+
		"\u0645\3\2\2\2\u00a4\u0647\3\2\2\2\u00a6\u00a7\b\2\1\2\u00a7\u00ab\b\2"+
		"\1\2\u00a8\u00ac\7\'\2\2\u00a9\u00ac\5&\24\2\u00aa\u00ac\5P)\2\u00ab\u00a8"+
		"\3\2\2\2\u00ab\u00a9\3\2\2\2\u00ab\u00aa\3\2\2\2\u00ac\u00ad\3\2\2\2\u00ad"+
		"\u00ae\b\2\1\2\u00ae\u00af\b\2\1\2\u00af\3\3\2\2\2\u00b0\u00b1\b\3\1\2"+
		"\u00b1\u00b6\b\3\1\2\u00b2\u00b5\7\'\2\2\u00b3\u00b5\5$\23\2\u00b4\u00b2"+
		"\3\2\2\2\u00b4\u00b3\3\2\2\2\u00b5\u00b8\3\2\2\2\u00b6\u00b4\3\2\2\2\u00b6"+
		"\u00b7\3\2\2\2\u00b7\u00b9\3\2\2\2\u00b8\u00b6\3\2\2\2\u00b9\u00ba\7\2"+
		"\2\3\u00ba\u00bb\b\3\1\2\u00bb\u00bc\b\3\1\2\u00bc\5\3\2\2\2\u00bd\u00be"+
		"\b\4\1\2\u00be\u00c2\5\u0094K\2\u00bf\u00c1\7\'\2\2\u00c0\u00bf\3\2\2"+
		"\2\u00c1\u00c4\3\2\2\2\u00c2\u00c0\3\2\2\2\u00c2\u00c3\3\2\2\2\u00c3\u00c5"+
		"\3\2\2\2\u00c4\u00c2\3\2\2\2\u00c5\u00c6\7\2\2\3\u00c6\u00c7\b\4\1\2\u00c7"+
		"\u00c8\b\4\1\2\u00c8\7\3\2\2\2\u00c9\u00ca\b\5\1\2\u00ca\u00cb\7Q\2\2"+
		"\u00cb\u00d1\5D#\2\u00cc\u00cd\7\64\2\2\u00cd\u00ce\5\u009cO\2\u00ce\u00cf"+
		"\7\65\2\2\u00cf\u00d0\b\5\1\2\u00d0\u00d2\3\2\2\2\u00d1\u00cc\3\2\2\2"+
		"\u00d1\u00d2\3\2\2\2\u00d2\u00d3\3\2\2\2\u00d3\u00d4\7\'\2\2\u00d4\u00d5"+
		"\b\5\1\2\u00d5\t\3\2\2\2\u00d6\u00d8\b\6\1\2\u00d7\u00d9\5\b\5\2\u00d8"+
		"\u00d7\3\2\2\2\u00d9\u00da\3\2\2\2\u00da\u00d8\3\2\2\2\u00da\u00db\3\2"+
		"\2\2\u00db\u00dc\3\2\2\2\u00dc\u00dd\b\6\1\2\u00dd\13\3\2\2\2\u00de\u00df"+
		"\b\7\1\2\u00df\u00e3\5\n\6\2\u00e0\u00e4\5\u009aN\2\u00e1\u00e4\5\20\t"+
		"\2\u00e2\u00e4\5\16\b\2\u00e3\u00e0\3\2\2\2\u00e3\u00e1\3\2\2\2\u00e3"+
		"\u00e2\3\2\2\2\u00e4\u00e5\3\2\2\2\u00e5\u00e6\b\7\1\2\u00e6\r\3\2\2\2"+
		"\u00e7\u00e8\7%\2\2\u00e8\u00e9\5\20\t\2\u00e9\17\3\2\2\2\u00ea\u00eb"+
		"\7\4\2\2\u00eb\u00ec\7(\2\2\u00ec\u00ef\5\22\n\2\u00ed\u00ee\7R\2\2\u00ee"+
		"\u00f0\5f\64\2\u00ef\u00ed\3\2\2\2\u00ef\u00f0\3\2\2\2\u00f0\u00f1\3\2"+
		"\2\2\u00f1\u00f2\7\67\2\2\u00f2\u00f3\b\t\1\2\u00f3\u00f4\5d\63\2\u00f4"+
		"\u00f5\b\t\1\2\u00f5\21\3\2\2\2\u00f6\u00f7\b\n\1\2\u00f7\u00f9\7\64\2"+
		"\2\u00f8\u00fa\5\24\13\2\u00f9\u00f8\3\2\2\2\u00f9\u00fa\3\2\2\2\u00fa"+
		"\u00fb\3\2\2\2\u00fb\u00fc\7\65\2\2\u00fc\u00fd\b\n\1\2\u00fd\23\3\2\2"+
		"\2\u00fe\u0103\5\26\f\2\u00ff\u0100\7\66\2\2\u0100\u0102\5\26\f\2\u0101"+
		"\u00ff\3\2\2\2\u0102\u0105\3\2\2\2\u0103\u0101\3\2\2\2\u0103\u0104\3\2"+
		"\2\2\u0104\u0106\3\2\2\2\u0105\u0103\3\2\2\2\u0106\u0107\7\66\2\2\u0107"+
		"\u0108\7D\2\2\u0108\u012e\b\13\1\2\u0109\u010a\7\66\2\2\u010a\u010f\5"+
		"\26\f\2\u010b\u010c\7\66\2\2\u010c\u010e\5\26\f\2\u010d\u010b\3\2\2\2"+
		"\u010e\u0111\3\2\2\2\u010f\u010d\3\2\2\2\u010f\u0110\3\2\2\2\u0110\u0113"+
		"\3\2\2\2\u0111\u010f\3\2\2\2\u0112\u0109\3\2\2\2\u0112\u0113\3\2\2\2\u0113"+
		"\u012c\3\2\2\2\u0114\u012a\7\66\2\2\u0115\u011a\5\30\r\2\u0116\u0117\7"+
		"\66\2\2\u0117\u0119\5\26\f\2\u0118\u0116\3\2\2\2\u0119\u011c\3\2\2\2\u011a"+
		"\u0118\3\2\2\2\u011a\u011b\3\2\2\2\u011b\u0124\3\2\2\2\u011c\u011a\3\2"+
		"\2\2\u011d\u0122\7\66\2\2\u011e\u0120\5\32\16\2\u011f\u0121\7\66\2\2\u0120"+
		"\u011f\3\2\2\2\u0120\u0121\3\2\2\2\u0121\u0123\3\2\2\2\u0122\u011e\3\2"+
		"\2\2\u0122\u0123\3\2\2\2\u0123\u0125\3\2\2\2\u0124\u011d\3\2\2\2\u0124"+
		"\u0125\3\2\2\2\u0125\u012b\3\2\2\2\u0126\u0128\5\32\16\2\u0127\u0129\7"+
		"\66\2\2\u0128\u0127\3\2\2\2\u0128\u0129\3\2\2\2\u0129\u012b\3\2\2\2\u012a"+
		"\u0115\3\2\2\2\u012a\u0126\3\2\2\2\u012a\u012b\3\2\2\2\u012b\u012d\3\2"+
		"\2\2\u012c\u0114\3\2\2\2\u012c\u012d\3\2\2\2\u012d\u012f\3\2\2\2\u012e"+
		"\u0112\3\2\2\2\u012e\u012f\3\2\2\2\u012f\u0168\3\2\2\2\u0130\u0135\5\26"+
		"\f\2\u0131\u0132\7\66\2\2\u0132\u0134\5\26\f\2\u0133\u0131\3\2\2\2\u0134"+
		"\u0137\3\2\2\2\u0135\u0133\3\2\2\2\u0135\u0136\3\2\2\2\u0136\u0150\3\2"+
		"\2\2\u0137\u0135\3\2\2\2\u0138\u014e\7\66\2\2\u0139\u013e\5\30\r\2\u013a"+
		"\u013b\7\66\2\2\u013b\u013d\5\26\f\2\u013c\u013a\3\2\2\2\u013d\u0140\3"+
		"\2\2\2\u013e\u013c\3\2\2\2\u013e\u013f\3\2\2\2\u013f\u0148\3\2\2\2\u0140"+
		"\u013e\3\2\2\2\u0141\u0146\7\66\2\2\u0142\u0144\5\32\16\2\u0143\u0145"+
		"\7\66\2\2\u0144\u0143\3\2\2\2\u0144\u0145\3\2\2\2\u0145\u0147\3\2\2\2"+
		"\u0146\u0142\3\2\2\2\u0146\u0147\3\2\2\2\u0147\u0149\3\2\2\2\u0148\u0141"+
		"\3\2\2\2\u0148\u0149\3\2\2\2\u0149\u014f\3\2\2\2\u014a\u014c\5\32\16\2"+
		"\u014b\u014d\7\66\2\2\u014c\u014b\3\2\2\2\u014c\u014d\3\2\2\2\u014d\u014f"+
		"\3\2\2\2\u014e\u0139\3\2\2\2\u014e\u014a\3\2\2\2\u014e\u014f\3\2\2\2\u014f"+
		"\u0151\3\2\2\2\u0150\u0138\3\2\2\2\u0150\u0151\3\2\2\2\u0151\u0168\3\2"+
		"\2\2\u0152\u0157\5\30\r\2\u0153\u0154\7\66\2\2\u0154\u0156\5\26\f\2\u0155"+
		"\u0153\3\2\2\2\u0156\u0159\3\2\2\2\u0157\u0155\3\2\2\2\u0157\u0158\3\2"+
		"\2\2\u0158\u0161\3\2\2\2\u0159\u0157\3\2\2\2\u015a\u015f\7\66\2\2\u015b"+
		"\u015d\5\32\16\2\u015c\u015e\7\66\2\2\u015d\u015c\3\2\2\2\u015d\u015e"+
		"\3\2\2\2\u015e\u0160\3\2\2\2\u015f\u015b\3\2\2\2\u015f\u0160\3\2\2\2\u0160"+
		"\u0162\3\2\2\2\u0161\u015a\3\2\2\2\u0161\u0162\3\2\2\2\u0162\u0168\3\2"+
		"\2\2\u0163\u0165\5\32\16\2\u0164\u0166\7\66\2\2\u0165\u0164\3\2\2\2\u0165"+
		"\u0166\3\2\2\2\u0166\u0168\3\2\2\2\u0167\u00fe\3\2\2\2\u0167\u0130\3\2"+
		"\2\2\u0167\u0152\3\2\2\2\u0167\u0163\3\2\2\2\u0168\25\3\2\2\2\u0169\u016a"+
		"\7(\2\2\u016a\u016f\b\f\1\2\u016b\u016c\7\67\2\2\u016c\u016d\5f\64\2\u016d"+
		"\u016e\b\f\1\2\u016e\u0170\3\2\2\2\u016f\u016b\3\2\2\2\u016f\u0170\3\2"+
		"\2\2\u0170\u0175\3\2\2\2\u0171\u0172\7:\2\2\u0172\u0173\5f\64\2\u0173"+
		"\u0174\b\f\1\2\u0174\u0176\3\2\2\2\u0175\u0171\3\2\2\2\u0175\u0176\3\2"+
		"\2\2\u0176\u0177\3\2\2\2\u0177\u0178\b\f\1\2\u0178\27\3\2\2\2\u0179\u017a"+
		"\7\63\2\2\u017a\u0183\b\r\1\2\u017b\u017c\7(\2\2\u017c\u0181\b\r\1\2\u017d"+
		"\u017e\7\67\2\2\u017e\u017f\5f\64\2\u017f\u0180\b\r\1\2\u0180\u0182\3"+
		"\2\2\2\u0181\u017d\3\2\2\2\u0181\u0182\3\2\2\2\u0182\u0184\3\2\2\2\u0183"+
		"\u017b\3\2\2\2\u0183\u0184\3\2\2\2\u0184\u0185\3\2\2\2\u0185\u0186\b\r"+
		"\1\2\u0186\31\3\2\2\2\u0187\u0188\79\2\2\u0188\u0189\7(\2\2\u0189\u018e"+
		"\b\16\1\2\u018a\u018b\7\67\2\2\u018b\u018c\5f\64\2\u018c\u018d\b\16\1"+
		"\2\u018d\u018f\3\2\2\2\u018e\u018a\3\2\2\2\u018e\u018f\3\2\2\2\u018f\u0190"+
		"\3\2\2\2\u0190\u0191\b\16\1\2\u0191\33\3\2\2\2\u0192\u01fc\b\17\1\2\u0193"+
		"\u0198\5\36\20\2\u0194\u0195\7\66\2\2\u0195\u0197\5\36\20\2\u0196\u0194"+
		"\3\2\2\2\u0197\u019a\3\2\2\2\u0198\u0196\3\2\2\2\u0198\u0199\3\2\2\2\u0199"+
		"\u019b\3\2\2\2\u019a\u0198\3\2\2\2\u019b\u019c\7\66\2\2\u019c\u019d\7"+
		"D\2\2\u019d\u01c3\b\17\1\2\u019e\u019f\7\66\2\2\u019f\u01a4\5\36\20\2"+
		"\u01a0\u01a1\7\66\2\2\u01a1\u01a3\5\36\20\2\u01a2\u01a0\3\2\2\2\u01a3"+
		"\u01a6\3\2\2\2\u01a4\u01a2\3\2\2\2\u01a4\u01a5\3\2\2\2\u01a5\u01a8\3\2"+
		"\2\2\u01a6\u01a4\3\2\2\2\u01a7\u019e\3\2\2\2\u01a7\u01a8\3\2\2\2\u01a8"+
		"\u01c1\3\2\2\2\u01a9\u01bf\7\66\2\2\u01aa\u01af\5 \21\2\u01ab\u01ac\7"+
		"\66\2\2\u01ac\u01ae\5\36\20\2\u01ad\u01ab\3\2\2\2\u01ae\u01b1\3\2\2\2"+
		"\u01af\u01ad\3\2\2\2\u01af\u01b0\3\2\2\2\u01b0\u01b9\3\2\2\2\u01b1\u01af"+
		"\3\2\2\2\u01b2\u01b7\7\66\2\2\u01b3\u01b5\5\"\22\2\u01b4\u01b6\7\66\2"+
		"\2\u01b5\u01b4\3\2\2\2\u01b5\u01b6\3\2\2\2\u01b6\u01b8\3\2\2\2\u01b7\u01b3"+
		"\3\2\2\2\u01b7\u01b8\3\2\2\2\u01b8\u01ba\3\2\2\2\u01b9\u01b2\3\2\2\2\u01b9"+
		"\u01ba\3\2\2\2\u01ba\u01c0\3\2\2\2\u01bb\u01bd\5\"\22\2\u01bc\u01be\7"+
		"\66\2\2\u01bd\u01bc\3\2\2\2\u01bd\u01be\3\2\2\2\u01be\u01c0\3\2\2\2\u01bf"+
		"\u01aa\3\2\2\2\u01bf\u01bb\3\2\2\2\u01bf\u01c0\3\2\2\2\u01c0\u01c2\3\2"+
		"\2\2\u01c1\u01a9\3\2\2\2\u01c1\u01c2\3\2\2\2\u01c2\u01c4\3\2\2\2\u01c3"+
		"\u01a7\3\2\2\2\u01c3\u01c4\3\2\2\2\u01c4\u01fd\3\2\2\2\u01c5\u01ca\5\36"+
		"\20\2\u01c6\u01c7\7\66\2\2\u01c7\u01c9\5\36\20\2\u01c8\u01c6\3\2\2\2\u01c9"+
		"\u01cc\3\2\2\2\u01ca\u01c8\3\2\2\2\u01ca\u01cb\3\2\2\2\u01cb\u01e5\3\2"+
		"\2\2\u01cc\u01ca\3\2\2\2\u01cd\u01e3\7\66\2\2\u01ce\u01d3\5 \21\2\u01cf"+
		"\u01d0\7\66\2\2\u01d0\u01d2\5\36\20\2\u01d1\u01cf\3\2\2\2\u01d2\u01d5"+
		"\3\2\2\2\u01d3\u01d1\3\2\2\2\u01d3\u01d4\3\2\2\2\u01d4\u01dd\3\2\2\2\u01d5"+
		"\u01d3\3\2\2\2\u01d6\u01db\7\66\2\2\u01d7\u01d9\5\"\22\2\u01d8\u01da\7"+
		"\66\2\2\u01d9\u01d8\3\2\2\2\u01d9\u01da\3\2\2\2\u01da\u01dc\3\2\2\2\u01db"+
		"\u01d7\3\2\2\2\u01db\u01dc\3\2\2\2\u01dc\u01de\3\2\2\2\u01dd\u01d6\3\2"+
		"\2\2\u01dd\u01de\3\2\2\2\u01de\u01e4\3\2\2\2\u01df\u01e1\5\"\22\2\u01e0"+
		"\u01e2\7\66\2\2\u01e1\u01e0\3\2\2\2\u01e1\u01e2\3\2\2\2\u01e2\u01e4\3"+
		"\2\2\2\u01e3\u01ce\3\2\2\2\u01e3\u01df\3\2\2\2\u01e3\u01e4\3\2\2\2\u01e4"+
		"\u01e6\3\2\2\2\u01e5\u01cd\3\2\2\2\u01e5\u01e6\3\2\2\2\u01e6\u01fd\3\2"+
		"\2\2\u01e7\u01ec\5 \21\2\u01e8\u01e9\7\66\2\2\u01e9\u01eb\5\36\20\2\u01ea"+
		"\u01e8\3\2\2\2\u01eb\u01ee\3\2\2\2\u01ec\u01ea\3\2\2\2\u01ec\u01ed\3\2"+
		"\2\2\u01ed\u01f6\3\2\2\2\u01ee\u01ec\3\2\2\2\u01ef\u01f4\7\66\2\2\u01f0"+
		"\u01f2\5\"\22\2\u01f1\u01f3\7\66\2\2\u01f2\u01f1\3\2\2\2\u01f2\u01f3\3"+
		"\2\2\2\u01f3\u01f5\3\2\2\2\u01f4\u01f0\3\2\2\2\u01f4\u01f5\3\2\2\2\u01f5"+
		"\u01f7\3\2\2\2\u01f6\u01ef\3\2\2\2\u01f6\u01f7\3\2\2\2\u01f7\u01fd\3\2"+
		"\2\2\u01f8\u01fa\5\"\22\2\u01f9\u01fb\7\66\2\2\u01fa\u01f9\3\2\2\2\u01fa"+
		"\u01fb\3\2\2\2\u01fb\u01fd\3\2\2\2\u01fc\u0193\3\2\2\2\u01fc\u01c5\3\2"+
		"\2\2\u01fc\u01e7\3\2\2\2\u01fc\u01f8\3\2\2\2\u01fd\u01fe\3\2\2\2\u01fe"+
		"\u01ff\b\17\1\2\u01ff\35\3\2\2\2\u0200\u0201\7(\2\2\u0201\u0206\b\20\1"+
		"\2\u0202\u0203\7:\2\2\u0203\u0204\5f\64\2\u0204\u0205\b\20\1\2\u0205\u0207"+
		"\3\2\2\2\u0206\u0202\3\2\2\2\u0206\u0207\3\2\2\2\u0207\u0208\3\2\2\2\u0208"+
		"\u0209\b\20\1\2\u0209\37\3\2\2\2\u020a\u020b\7\63\2\2\u020b\u020e\b\21"+
		"\1\2\u020c\u020d\7(\2\2\u020d\u020f\b\21\1\2\u020e\u020c\3\2\2\2\u020e"+
		"\u020f\3\2\2\2\u020f\u0210\3\2\2\2\u0210\u0211\b\21\1\2\u0211!\3\2\2\2"+
		"\u0212\u0213\79\2\2\u0213\u0214\7(\2\2\u0214\u0215\b\22\1\2\u0215#\3\2"+
		"\2\2\u0216\u0219\5&\24\2\u0217\u0219\5P)\2\u0218\u0216\3\2\2\2\u0218\u0217"+
		"\3\2\2\2\u0219%\3\2\2\2\u021a\u021f\5(\25\2\u021b\u021c\78\2\2\u021c\u021e"+
		"\5(\25\2\u021d\u021b\3\2\2\2\u021e\u0221\3\2\2\2\u021f\u021d\3\2\2\2\u021f"+
		"\u0220\3\2\2\2\u0220\u0223\3\2\2\2\u0221\u021f\3\2\2\2\u0222\u0224\78"+
		"\2\2\u0223\u0222\3\2\2\2\u0223\u0224\3\2\2\2\u0224\u0225\3\2\2\2\u0225"+
		"\u0226\7\'\2\2\u0226\'\3\2\2\2\u0227\u0231\5*\26\2\u0228\u0231\5\60\31"+
		"\2\u0229\u022a\7\"\2\2\u022a\u0231\b\25\1\2\u022b\u0231\5\62\32\2\u022c"+
		"\u0231\5:\36\2\u022d\u0231\5J&\2\u022e\u0231\5L\'\2\u022f\u0231\5N(\2"+
		"\u0230\u0227\3\2\2\2\u0230\u0228\3\2\2\2\u0230\u0229\3\2\2\2\u0230\u022b"+
		"\3\2\2\2\u0230\u022c\3\2\2\2\u0230\u022d\3\2\2\2\u0230\u022e\3\2\2\2\u0230"+
		"\u022f\3\2\2\2\u0231)\3\2\2\2\u0232\u0233\5,\27\2\u0233\u025b\b\26\1\2"+
		"\u0234\u0235\7\67\2\2\u0235\u023a\5f\64\2\u0236\u0237\7:\2\2\u0237\u0238"+
		"\5f\64\2\u0238\u0239\b\26\1\2\u0239\u023b\3\2\2\2\u023a\u0236\3\2\2\2"+
		"\u023a\u023b\3\2\2\2\u023b\u023c\3\2\2\2\u023c\u023d\b\26\1\2\u023d\u025c"+
		"\3\2\2\2\u023e\u0245\5.\30\2\u023f\u0240\5\u00a4S\2\u0240\u0241\b\26\1"+
		"\2\u0241\u0246\3\2\2\2\u0242\u0243\5\u0094K\2\u0243\u0244\b\26\1\2\u0244"+
		"\u0246\3\2\2\2\u0245\u023f\3\2\2\2\u0245\u0242\3\2\2\2\u0246\u0247\3\2"+
		"\2\2\u0247\u0248\b\26\1\2\u0248\u025c\3\2\2\2\u0249\u024a\b\26\1\2\u024a"+
		"\u0257\b\26\1\2\u024b\u024c\7:\2\2\u024c\u0253\b\26\1\2\u024d\u024e\5"+
		"\u00a4S\2\u024e\u024f\b\26\1\2\u024f\u0254\3\2\2\2\u0250\u0251\5,\27\2"+
		"\u0251\u0252\b\26\1\2\u0252\u0254\3\2\2\2\u0253\u024d\3\2\2\2\u0253\u0250"+
		"\3\2\2\2\u0254\u0256\3\2\2\2\u0255\u024b\3\2\2\2\u0256\u0259\3\2\2\2\u0257"+
		"\u0255\3\2\2\2\u0257\u0258\3\2\2\2\u0258\u025a\3\2\2\2\u0259\u0257\3\2"+
		"\2\2\u025a\u025c\b\26\1\2\u025b\u0234\3\2\2\2\u025b\u023e\3\2\2\2\u025b"+
		"\u0249\3\2\2\2\u025c+\3\2\2\2\u025d\u025e\5f\64\2\u025e\u025f\b\27\1\2"+
		"\u025f\u0264\3\2\2\2\u0260\u0261\5x=\2\u0261\u0262\b\27\1\2\u0262\u0264"+
		"\3\2\2\2\u0263\u025d\3\2\2\2\u0263\u0260\3\2\2\2\u0264\u0283\3\2\2\2\u0265"+
		"\u0266\b\27\1\2\u0266\u0280\7\66\2\2\u0267\u0268\5f\64\2\u0268\u0269\b"+
		"\27\1\2\u0269\u026e\3\2\2\2\u026a\u026b\5x=\2\u026b\u026c\b\27\1\2\u026c"+
		"\u026e\3\2\2\2\u026d\u0267\3\2\2\2\u026d\u026a\3\2\2\2\u026e\u027a\3\2"+
		"\2\2\u026f\u0276\7\66\2\2\u0270\u0271\5f\64\2\u0271\u0272\b\27\1\2\u0272"+
		"\u0277\3\2\2\2\u0273\u0274\5x=\2\u0274\u0275\b\27\1\2\u0275\u0277\3\2"+
		"\2\2\u0276\u0270\3\2\2\2\u0276\u0273\3\2\2\2\u0277\u0279\3\2\2\2\u0278"+
		"\u026f\3\2\2\2\u0279\u027c\3\2\2\2\u027a\u0278\3\2\2\2\u027a\u027b\3\2"+
		"\2\2\u027b\u027e\3\2\2\2\u027c\u027a\3\2\2\2\u027d\u027f\7\66\2\2\u027e"+
		"\u027d\3\2\2\2\u027e\u027f\3\2\2\2\u027f\u0281\3\2\2\2\u0280\u026d\3\2"+
		"\2\2\u0280\u0281\3\2\2\2\u0281\u0282\3\2\2\2\u0282\u0284\b\27\1\2\u0283"+
		"\u0265\3\2\2\2\u0283\u0284\3\2\2\2\u0284-\3\2\2\2\u0285\u0286\t\2\2\2"+
		"\u0286/\3\2\2\2\u0287\u0288\7!\2\2\u0288\u0289\5\u0092J\2\u0289\u028a"+
		"\b\31\1\2\u028a\61\3\2\2\2\u028b\u028c\7$\2\2\u028c\u0293\b\32\1\2\u028d"+
		"\u028e\7#\2\2\u028e\u0293\b\32\1\2\u028f\u0293\5\64\33\2\u0290\u0293\5"+
		"8\35\2\u0291\u0293\5\66\34\2\u0292\u028b\3\2\2\2\u0292\u028d\3\2\2\2\u0292"+
		"\u028f\3\2\2\2\u0292\u0290\3\2\2\2\u0292\u0291\3\2\2\2\u0293\63\3\2\2"+
		"\2\u0294\u0295\7\5\2\2\u0295\u0299\b\33\1\2\u0296\u0297\5,\27\2\u0297"+
		"\u0298\b\33\1\2\u0298\u029a\3\2\2\2\u0299\u0296\3\2\2\2\u0299\u029a\3"+
		"\2\2\2\u029a\u029b\3\2\2\2\u029b\u029c\b\33\1\2\u029c\65\3\2\2\2\u029d"+
		"\u029e\5\u00a4S\2\u029e\u029f\b\34\1\2\u029f\67\3\2\2\2\u02a0\u02a1\b"+
		"\35\1\2\u02a1\u02aa\7\6\2\2\u02a2\u02a3\5f\64\2\u02a3\u02a8\b\35\1\2\u02a4"+
		"\u02a5\7\7\2\2\u02a5\u02a6\5f\64\2\u02a6\u02a7\b\35\1\2\u02a7\u02a9\3"+
		"\2\2\2\u02a8\u02a4\3\2\2\2\u02a8\u02a9\3\2\2\2\u02a9\u02ab\3\2\2\2\u02aa"+
		"\u02a2\3\2\2\2\u02aa\u02ab\3\2\2\2\u02ab\u02ac\3\2\2\2\u02ac\u02ad\b\35"+
		"\1\2\u02ad9\3\2\2\2\u02ae\u02b1\5<\37\2\u02af\u02b1\5> \2\u02b0\u02ae"+
		"\3\2\2\2\u02b0\u02af\3\2\2\2\u02b1;\3\2\2\2\u02b2\u02b3\7\b\2\2\u02b3"+
		"\u02b4\5H%\2\u02b4=\3\2\2\2\u02b5\u02b6\7\7\2\2\u02b6\u02cb\b \1\2\u02b7"+
		"\u02b8\7\61\2\2\u02b8\u02bc\b \1\2\u02b9\u02ba\7\62\2\2\u02ba\u02bc\b"+
		" \1\2\u02bb\u02b7\3\2\2\2\u02bb\u02b9\3\2\2\2\u02bc\u02bf\3\2\2\2\u02bd"+
		"\u02bb\3\2\2\2\u02bd\u02be\3\2\2\2\u02be\u02c0\3\2\2\2\u02bf\u02bd\3\2"+
		"\2\2\u02c0\u02c1\5D#\2\u02c1\u02c2\b \1\2\u02c2\u02cc\3\2\2\2\u02c3\u02c4"+
		"\7\61\2\2\u02c4\u02c8\b \1\2\u02c5\u02c6\7\62\2\2\u02c6\u02c8\b \1\2\u02c7"+
		"\u02c3\3\2\2\2\u02c7\u02c5\3\2\2\2\u02c8\u02c9\3\2\2\2\u02c9\u02c7\3\2"+
		"\2\2\u02c9\u02ca\3\2\2\2\u02ca\u02cc\3\2\2\2\u02cb\u02bd\3\2\2\2\u02cb"+
		"\u02c7\3\2\2\2\u02cc\u02cd\3\2\2\2\u02cd\u02ce\7\b\2\2\u02ce\u02d8\b "+
		"\1\2\u02cf\u02d9\7\63\2\2\u02d0\u02d1\7\64\2\2\u02d1\u02d2\5B\"\2\u02d2"+
		"\u02d3\b \1\2\u02d3\u02d4\7\65\2\2\u02d4\u02d9\3\2\2\2\u02d5\u02d6\5B"+
		"\"\2\u02d6\u02d7\b \1\2\u02d7\u02d9\3\2\2\2\u02d8\u02cf\3\2\2\2\u02d8"+
		"\u02d0\3\2\2\2\u02d8\u02d5\3\2\2\2\u02d9\u02da\3\2\2\2\u02da\u02db\b "+
		"\1\2\u02db?\3\2\2\2\u02dc\u02dd\7(\2\2\u02dd\u02e1\b!\1\2\u02de\u02df"+
		"\7\t\2\2\u02df\u02e0\7(\2\2\u02e0\u02e2\b!\1\2\u02e1\u02de\3\2\2\2\u02e1"+
		"\u02e2\3\2\2\2\u02e2\u02e3\3\2\2\2\u02e3\u02e4\b!\1\2\u02e4A\3\2\2\2\u02e5"+
		"\u02e6\b\"\1\2\u02e6\u02e7\5@!\2\u02e7\u02ee\b\"\1\2\u02e8\u02e9\7\66"+
		"\2\2\u02e9\u02ea\5@!\2\u02ea\u02eb\b\"\1\2\u02eb\u02ed\3\2\2\2\u02ec\u02e8"+
		"\3\2\2\2\u02ed\u02f0\3\2\2\2\u02ee\u02ec\3\2\2\2\u02ee\u02ef\3\2\2\2\u02ef"+
		"\u02f2\3\2\2\2\u02f0\u02ee\3\2\2\2\u02f1\u02f3\7\66\2\2\u02f2\u02f1\3"+
		"\2\2\2\u02f2\u02f3\3\2\2\2\u02f3\u02f4\3\2\2\2\u02f4\u02f5\b\"\1\2\u02f5"+
		"C\3\2\2\2\u02f6\u02f7\7(\2\2\u02f7\u02fd\b#\1\2\u02f8\u02f9\7\61\2\2\u02f9"+
		"\u02fa\7(\2\2\u02fa\u02fc\b#\1\2\u02fb\u02f8\3\2\2\2\u02fc\u02ff\3\2\2"+
		"\2\u02fd\u02fb\3\2\2\2\u02fd\u02fe\3\2\2\2\u02feE\3\2\2\2\u02ff\u02fd"+
		"\3\2\2\2\u0300\u0305\5D#\2\u0301\u0302\7\t\2\2\u0302\u0303\7(\2\2\u0303"+
		"\u0306\b$\1\2\u0304\u0306\b$\1\2\u0305\u0301\3\2\2\2\u0305\u0304\3\2\2"+
		"\2\u0306G\3\2\2\2\u0307\u030c\5F$\2\u0308\u0309\7\66\2\2\u0309\u030b\5"+
		"F$\2\u030a\u0308\3\2\2\2\u030b\u030e\3\2\2\2\u030c\u030a\3\2\2\2\u030c"+
		"\u030d\3\2\2\2\u030dI\3\2\2\2\u030e\u030c\3\2\2\2\u030f\u0310\b&\1\2\u0310"+
		"\u0311\7\n\2\2\u0311\u0312\7(\2\2\u0312\u0318\b&\1\2\u0313\u0314\7\66"+
		"\2\2\u0314\u0315\7(\2\2\u0315\u0317\b&\1\2\u0316\u0313\3\2\2\2\u0317\u031a"+
		"\3\2\2\2\u0318\u0316\3\2\2\2\u0318\u0319\3\2\2\2\u0319\u031b\3\2\2\2\u031a"+
		"\u0318\3\2\2\2\u031b\u031c\b&\1\2\u031cK\3\2\2\2\u031d\u031e\b\'\1\2\u031e"+
		"\u031f\7\13\2\2\u031f\u0320\7(\2\2\u0320\u0326\b\'\1\2\u0321\u0322\7\66"+
		"\2\2\u0322\u0323\7(\2\2\u0323\u0325\b\'\1\2\u0324\u0321\3\2\2\2\u0325"+
		"\u0328\3\2\2\2\u0326\u0324\3\2\2\2\u0326\u0327\3\2\2\2\u0327\u0329\3\2"+
		"\2\2\u0328\u0326\3\2\2\2\u0329\u032a\b\'\1\2\u032aM\3\2\2\2\u032b\u032c"+
		"\7\f\2\2\u032c\u032d\5f\64\2\u032d\u0332\b(\1\2\u032e\u032f\7\66\2\2\u032f"+
		"\u0330\5f\64\2\u0330\u0331\b(\1\2\u0331\u0333\3\2\2\2\u0332\u032e\3\2"+
		"\2\2\u0332\u0333\3\2\2\2\u0333\u0334\3\2\2\2\u0334\u0335\b(\1\2\u0335"+
		"O\3\2\2\2\u0336\u0340\5T+\2\u0337\u0340\5X-\2\u0338\u0340\5Z.\2\u0339"+
		"\u0340\5\\/\2\u033a\u0340\5`\61\2\u033b\u0340\5\20\t\2\u033c\u0340\5\u009a"+
		"N\2\u033d\u0340\5\f\7\2\u033e\u0340\5R*\2\u033f\u0336\3\2\2\2\u033f\u0337"+
		"\3\2\2\2\u033f\u0338\3\2\2\2\u033f\u0339\3\2\2\2\u033f\u033a\3\2\2\2\u033f"+
		"\u033b\3\2\2\2\u033f\u033c\3\2\2\2\u033f\u033d\3\2\2\2\u033f\u033e\3\2"+
		"\2\2\u0340Q\3\2\2\2\u0341\u0345\7%\2\2\u0342\u0346\5\20\t\2\u0343\u0346"+
		"\5`\61\2\u0344\u0346\5Z.\2\u0345\u0342\3\2\2\2\u0345\u0343\3\2\2\2\u0345"+
		"\u0344\3\2\2\2\u0346S\3\2\2\2\u0347\u0348\7\r\2\2\u0348\u0349\5f\64\2"+
		"\u0349\u034a\7\67\2\2\u034a\u034b\5d\63\2\u034b\u034c\5V,\2\u034c\u034d"+
		"\b+\1\2\u034dU\3\2\2\2\u034e\u034f\7\16\2\2\u034f\u0350\5f\64\2\u0350"+
		"\u0351\7\67\2\2\u0351\u0352\5d\63\2\u0352\u0353\5V,\2\u0353\u0354\b,\1"+
		"\2\u0354\u035c\3\2\2\2\u0355\u0356\7\17\2\2\u0356\u0357\7\67\2\2\u0357"+
		"\u0358\5d\63\2\u0358\u0359\b,\1\2\u0359\u035c\3\2\2\2\u035a\u035c\b,\1"+
		"\2\u035b\u034e\3\2\2\2\u035b\u0355\3\2\2\2\u035b\u035a\3\2\2\2\u035cW"+
		"\3\2\2\2\u035d\u035e\7\20\2\2\u035e\u035f\5f\64\2\u035f\u0360\7\67\2\2"+
		"\u0360\u0361\b-\1\2\u0361\u0362\5d\63\2\u0362\u0368\b-\1\2\u0363\u0364"+
		"\7\17\2\2\u0364\u0365\7\67\2\2\u0365\u0366\5d\63\2\u0366\u0367\b-\1\2"+
		"\u0367\u0369\3\2\2\2\u0368\u0363\3\2\2\2\u0368\u0369\3\2\2\2\u0369\u036a"+
		"\3\2\2\2\u036a\u036b\b-\1\2\u036bY\3\2\2\2\u036c\u036d\7\21\2\2\u036d"+
		"\u036e\5\u0092J\2\u036e\u036f\7\22\2\2\u036f\u0370\5\u0094K\2\u0370\u0371"+
		"\7\67\2\2\u0371\u0372\b.\1\2\u0372\u0373\5d\63\2\u0373\u0379\b.\1\2\u0374"+
		"\u0375\7\17\2\2\u0375\u0376\7\67\2\2\u0376\u0377\5d\63\2\u0377\u0378\b"+
		".\1\2\u0378\u037a\3\2\2\2\u0379\u0374\3\2\2\2\u0379\u037a\3\2\2\2\u037a"+
		"\u037b\3\2\2\2\u037b\u037c\b.\1\2\u037c[\3\2\2\2\u037d\u037e\7\23\2\2"+
		"\u037e\u037f\7\67\2\2\u037f\u0380\5d\63\2\u0380\u0381\b/\1\2\u0381\u039c"+
		"\b/\1\2\u0382\u0383\5^\60\2\u0383\u0384\b/\1\2\u0384\u0386\3\2\2\2\u0385"+
		"\u0382\3\2\2\2\u0386\u0387\3\2\2\2\u0387\u0385\3\2\2\2\u0387\u0388\3\2"+
		"\2\2\u0388\u038e\3\2\2\2\u0389\u038a\7\17\2\2\u038a\u038b\7\67\2\2\u038b"+
		"\u038c\5d\63\2\u038c\u038d\b/\1\2\u038d\u038f\3\2\2\2\u038e\u0389\3\2"+
		"\2\2\u038e\u038f\3\2\2\2\u038f\u0395\3\2\2\2\u0390\u0391\7\24\2\2\u0391"+
		"\u0392\7\67\2\2\u0392\u0393\5d\63\2\u0393\u0394\b/\1\2\u0394\u0396\3\2"+
		"\2\2\u0395\u0390\3\2\2\2\u0395\u0396\3\2\2\2\u0396\u039d\3\2\2\2\u0397"+
		"\u0398\7\24\2\2\u0398\u0399\7\67\2\2\u0399\u039a\5d\63\2\u039a\u039b\b"+
		"/\1\2\u039b\u039d\3\2\2\2\u039c\u0385\3\2\2\2\u039c\u0397\3\2\2\2\u039d"+
		"\u039e\3\2\2\2\u039e\u039f\b/\1\2\u039f]\3\2\2\2\u03a0\u03a1\7\26\2\2"+
		"\u03a1\u03a9\b\60\1\2\u03a2\u03a3\5f\64\2\u03a3\u03a7\b\60\1\2\u03a4\u03a5"+
		"\7\t\2\2\u03a5\u03a6\7(\2\2\u03a6\u03a8\b\60\1\2\u03a7\u03a4\3\2\2\2\u03a7"+
		"\u03a8\3\2\2\2\u03a8\u03aa\3\2\2\2\u03a9\u03a2\3\2\2\2\u03a9\u03aa\3\2"+
		"\2\2\u03aa\u03ab\3\2\2\2\u03ab\u03ac\7\67\2\2\u03ac\u03ad\5d\63\2\u03ad"+
		"\u03ae\b\60\1\2\u03ae_\3\2\2\2\u03af\u03b0\7\25\2\2\u03b0\u03b1\5b\62"+
		"\2\u03b1\u03b2\b\61\1\2\u03b2a\3\2\2\2\u03b3\u03b4\5f\64\2\u03b4\u03b9"+
		"\b\62\1\2\u03b5\u03b6\7\t\2\2\u03b6\u03b7\5z>\2\u03b7\u03b8\b\62\1\2\u03b8"+
		"\u03ba\3\2\2\2\u03b9\u03b5\3\2\2\2\u03b9\u03ba\3\2\2\2\u03ba\u03bb\3\2"+
		"\2\2\u03bb\u03c4\b\62\1\2\u03bc\u03bd\7\66\2\2\u03bd\u03be\5b\62\2\u03be"+
		"\u03bf\b\62\1\2\u03bf\u03c5\3\2\2\2\u03c0\u03c1\7\67\2\2\u03c1\u03c2\5"+
		"d\63\2\u03c2\u03c3\b\62\1\2\u03c3\u03c5\3\2\2\2\u03c4\u03bc\3\2\2\2\u03c4"+
		"\u03c0\3\2\2\2\u03c5\u03c6\3\2\2\2\u03c6\u03c7\b\62\1\2\u03c7c\3\2\2\2"+
		"\u03c8\u03d3\b\63\1\2\u03c9\u03d4\5&\24\2\u03ca\u03cb\7\'\2\2\u03cb\u03cd"+
		"\7d\2\2\u03cc\u03ce\5$\23\2\u03cd\u03cc\3\2\2\2\u03ce\u03cf\3\2\2\2\u03cf"+
		"\u03cd\3\2\2\2\u03cf\u03d0\3\2\2\2\u03d0\u03d1\3\2\2\2\u03d1\u03d2\7e"+
		"\2\2\u03d2\u03d4\3\2\2\2\u03d3\u03c9\3\2\2\2\u03d3\u03ca\3\2\2\2\u03d4"+
		"\u03d5\3\2\2\2\u03d5\u03d6\b\63\1\2\u03d6e\3\2\2\2\u03d7\u03d8\5n8\2\u03d8"+
		"\u03df\b\64\1\2\u03d9\u03da\7\r\2\2\u03da\u03db\5n8\2\u03db\u03dc\7\17"+
		"\2\2\u03dc\u03dd\5f\64\2\u03dd\u03de\b\64\1\2\u03de\u03e0\3\2\2\2\u03df"+
		"\u03d9\3\2\2\2\u03df\u03e0\3\2\2\2\u03e0\u03e5\3\2\2\2\u03e1\u03e2\5j"+
		"\66\2\u03e2\u03e3\b\64\1\2\u03e3\u03e5\3\2\2\2\u03e4\u03d7\3\2\2\2\u03e4"+
		"\u03e1\3\2\2\2\u03e5g\3\2\2\2\u03e6\u03e7\5n8\2\u03e7\u03e8\b\65\1\2\u03e8"+
		"\u03ed\3\2\2\2\u03e9\u03ea\5l\67\2\u03ea\u03eb\b\65\1\2\u03eb\u03ed\3"+
		"\2\2\2\u03ec\u03e6\3\2\2\2\u03ec\u03e9\3\2\2\2\u03edi\3\2\2\2\u03ee\u03ef"+
		"\7\27\2\2\u03ef\u03f3\b\66\1\2\u03f0\u03f1\5\34\17\2\u03f1\u03f2\b\66"+
		"\1\2\u03f2\u03f4\3\2\2\2\u03f3\u03f0\3\2\2\2\u03f3\u03f4\3\2\2\2\u03f4"+
		"\u03f5\3\2\2\2\u03f5\u03f6\b\66\1\2\u03f6\u03f7\7\67\2\2\u03f7\u03f8\5"+
		"f\64\2\u03f8\u03f9\b\66\1\2\u03f9\u03fa\b\66\1\2\u03fak\3\2\2\2\u03fb"+
		"\u03fc\7\27\2\2\u03fc\u0400\b\67\1\2\u03fd\u03fe\5\34\17\2\u03fe\u03ff"+
		"\b\67\1\2\u03ff\u0401\3\2\2\2\u0400\u03fd\3\2\2\2\u0400\u0401\3\2\2\2"+
		"\u0401\u0402\3\2\2\2\u0402\u0403\b\67\1\2\u0403\u0404\7\67\2\2\u0404\u0405"+
		"\5h\65\2\u0405\u0406\b\67\1\2\u0406\u0407\b\67\1\2\u0407m\3\2\2\2\u0408"+
		"\u0416\5p9\2\u0409\u040a\b8\1\2\u040a\u040f\b8\1\2\u040b\u040c\7\30\2"+
		"\2\u040c\u040d\5p9\2\u040d\u040e\b8\1\2\u040e\u0410\3\2\2\2\u040f\u040b"+
		"\3\2\2\2\u0410\u0411\3\2\2\2\u0411\u040f\3\2\2\2\u0411\u0412\3\2\2\2\u0412"+
		"\u0413\3\2\2\2\u0413\u0414\b8\1\2\u0414\u0417\3\2\2\2\u0415\u0417\b8\1"+
		"\2\u0416\u0409\3\2\2\2\u0416\u0415\3\2\2\2\u0417o\3\2\2\2\u0418\u0426"+
		"\5r:\2\u0419\u041a\b9\1\2\u041a\u041f\b9\1\2\u041b\u041c\7\31\2\2\u041c"+
		"\u041d\5r:\2\u041d\u041e\b9\1\2\u041e\u0420\3\2\2\2\u041f\u041b\3\2\2"+
		"\2\u0420\u0421\3\2\2\2\u0421\u041f\3\2\2\2\u0421\u0422\3\2\2\2\u0422\u0423"+
		"\3\2\2\2\u0423\u0424\b9\1\2\u0424\u0427\3\2\2\2\u0425\u0427\b9\1\2\u0426"+
		"\u0419\3\2\2\2\u0426\u0425\3\2\2\2\u0427q\3\2\2\2\u0428\u0429\7\32\2\2"+
		"\u0429\u042a\5r:\2\u042a\u042b\b:\1\2\u042b\u0430\3\2\2\2\u042c\u042d"+
		"\5t;\2\u042d\u042e\b:\1\2\u042e\u0430\3\2\2\2\u042f\u0428\3\2\2\2\u042f"+
		"\u042c\3\2\2\2\u0430s\3\2\2\2\u0431\u043e\5z>\2\u0432\u0437\b;\1\2\u0433"+
		"\u0434\5v<\2\u0434\u0435\5z>\2\u0435\u0436\b;\1\2\u0436\u0438\3\2\2\2"+
		"\u0437\u0433\3\2\2\2\u0438\u0439\3\2\2\2\u0439\u0437\3\2\2\2\u0439\u043a"+
		"\3\2\2\2\u043a\u043b\3\2\2\2\u043b\u043c\b;\1\2\u043c\u043f\3\2\2\2\u043d"+
		"\u043f\b;\1\2\u043e\u0432\3\2\2\2\u043e\u043d\3\2\2\2\u043fu\3\2\2\2\u0440"+
		"\u0441\7J\2\2\u0441\u0459\b<\1\2\u0442\u0443\7K\2\2\u0443\u0459\b<\1\2"+
		"\u0444\u0445\7L\2\2\u0445\u0459\b<\1\2\u0446\u0447\7M\2\2\u0447\u0459"+
		"\b<\1\2\u0448\u0449\7N\2\2\u0449\u0459\b<\1\2\u044a\u044b\7O\2\2\u044b"+
		"\u0459\b<\1\2\u044c\u044d\7P\2\2\u044d\u0459\b<\1\2\u044e\u044f\7\22\2"+
		"\2\u044f\u0459\b<\1\2\u0450\u0451\7\32\2\2\u0451\u0452\7\22\2\2\u0452"+
		"\u0459\b<\1\2\u0453\u0454\7\33\2\2\u0454\u0459\b<\1\2\u0455\u0456\7\33"+
		"\2\2\u0456\u0457\7\32\2\2\u0457\u0459\b<\1\2\u0458\u0440\3\2\2\2\u0458"+
		"\u0442\3\2\2\2\u0458\u0444\3\2\2\2\u0458\u0446\3\2\2\2\u0458\u0448\3\2"+
		"\2\2\u0458\u044a\3\2\2\2\u0458\u044c\3\2\2\2\u0458\u044e\3\2\2\2\u0458"+
		"\u0450\3\2\2\2\u0458\u0453\3\2\2\2\u0458\u0455\3\2\2\2\u0459w\3\2\2\2"+
		"\u045a\u045b\7\63\2\2\u045b\u045c\5z>\2\u045c\u045d\b=\1\2\u045dy\3\2"+
		"\2\2\u045e\u045f\5|?\2\u045f\u0466\b>\1\2\u0460\u0461\7=\2\2\u0461\u0462"+
		"\5|?\2\u0462\u0463\b>\1\2\u0463\u0465\3\2\2\2\u0464\u0460\3\2\2\2\u0465"+
		"\u0468\3\2\2\2\u0466\u0464\3\2\2\2\u0466\u0467\3\2\2\2\u0467{\3\2\2\2"+
		"\u0468\u0466\3\2\2\2\u0469\u046a\5~@\2\u046a\u0471\b?\1\2\u046b\u046c"+
		"\7>\2\2\u046c\u046d\5~@\2\u046d\u046e\b?\1\2\u046e\u0470\3\2\2\2\u046f"+
		"\u046b\3\2\2\2\u0470\u0473\3\2\2\2\u0471\u046f\3\2\2\2\u0471\u0472\3\2"+
		"\2\2\u0472}\3\2\2\2\u0473\u0471\3\2\2\2\u0474\u0475\5\u0080A\2\u0475\u047c"+
		"\b@\1\2\u0476\u0477\7?\2\2\u0477\u0478\5\u0080A\2\u0478\u0479\b@\1\2\u0479"+
		"\u047b\3\2\2\2\u047a\u0476\3\2\2\2\u047b\u047e\3\2\2\2\u047c\u047a\3\2"+
		"\2\2\u047c\u047d\3\2\2\2\u047d\177\3\2\2\2\u047e\u047c\3\2\2\2\u047f\u0480"+
		"\5\u0082B\2\u0480\u048d\bA\1\2\u0481\u0486\bA\1\2\u0482\u0483\7@\2\2\u0483"+
		"\u0487\bA\1\2\u0484\u0485\7A\2\2\u0485\u0487\bA\1\2\u0486\u0482\3\2\2"+
		"\2\u0486\u0484\3\2\2\2\u0487\u0488\3\2\2\2\u0488\u0489\5\u0082B\2\u0489"+
		"\u048a\bA\1\2\u048a\u048c\3\2\2\2\u048b\u0481\3\2\2\2\u048c\u048f\3\2"+
		"\2\2\u048d\u048b\3\2\2\2\u048d\u048e\3\2\2\2\u048e\u0081\3\2\2\2\u048f"+
		"\u048d\3\2\2\2\u0490\u0491\5\u0084C\2\u0491\u049e\bB\1\2\u0492\u0497\b"+
		"B\1\2\u0493\u0494\7B\2\2\u0494\u0498\bB\1\2\u0495\u0496\7C\2\2\u0496\u0498"+
		"\bB\1\2\u0497\u0493\3\2\2\2\u0497\u0495\3\2\2\2\u0498\u0499\3\2\2\2\u0499"+
		"\u049a\5\u0084C\2\u049a\u049b\bB\1\2\u049b\u049d\3\2\2\2\u049c\u0492\3"+
		"\2\2\2\u049d\u04a0\3\2\2\2\u049e\u049c\3\2\2\2\u049e\u049f\3\2\2\2\u049f"+
		"\u0083\3\2\2\2\u04a0\u049e\3\2\2\2\u04a1\u04a2\5\u0086D\2\u04a2\u04b5"+
		"\bC\1\2\u04a3\u04ae\bC\1\2\u04a4\u04a5\7\63\2\2\u04a5\u04af\bC\1\2\u04a6"+
		"\u04a7\7Q\2\2\u04a7\u04af\bC\1\2\u04a8\u04a9\7D\2\2\u04a9\u04af\bC\1\2"+
		"\u04aa\u04ab\7E\2\2\u04ab\u04af\bC\1\2\u04ac\u04ad\7F\2\2\u04ad\u04af"+
		"\bC\1\2\u04ae\u04a4\3\2\2\2\u04ae\u04a6\3\2\2\2\u04ae\u04a8\3\2\2\2\u04ae"+
		"\u04aa\3\2\2\2\u04ae\u04ac\3\2\2\2\u04af\u04b0\3\2\2\2\u04b0\u04b1\5\u0086"+
		"D\2\u04b1\u04b2\bC\1\2\u04b2\u04b4\3\2\2\2\u04b3\u04a3\3\2\2\2\u04b4\u04b7"+
		"\3\2\2\2\u04b5\u04b3\3\2\2\2\u04b5\u04b6\3\2\2\2\u04b6\u0085\3\2\2\2\u04b7"+
		"\u04b5\3\2\2\2\u04b8\u04bf\bD\1\2\u04b9\u04ba\7B\2\2\u04ba\u04c0\bD\1"+
		"\2\u04bb\u04bc\7C\2\2\u04bc\u04c0\bD\1\2\u04bd\u04be\7G\2\2\u04be\u04c0"+
		"\bD\1\2\u04bf\u04b9\3\2\2\2\u04bf\u04bb\3\2\2\2\u04bf\u04bd\3\2\2\2\u04c0"+
		"\u04c1\3\2\2\2\u04c1\u04c2\5\u0086D\2\u04c2\u04c3\bD\1\2\u04c3\u04c8\3"+
		"\2\2\2\u04c4\u04c5\5\u0088E\2\u04c5\u04c6\bD\1\2\u04c6\u04c8\3\2\2\2\u04c7"+
		"\u04b8\3\2\2\2\u04c7\u04c4\3\2\2\2\u04c8\u0087\3\2\2\2\u04c9\u04ca\5\u008a"+
		"F\2\u04ca\u04cf\bE\1\2\u04cb\u04cc\79\2\2\u04cc\u04cd\5\u0086D\2\u04cd"+
		"\u04ce\bE\1\2\u04ce\u04d0\3\2\2\2\u04cf\u04cb\3\2\2\2\u04cf\u04d0\3\2"+
		"\2\2\u04d0\u0089\3\2\2\2\u04d1\u04d3\7&\2\2\u04d2\u04d1\3\2\2\2\u04d2"+
		"\u04d3\3\2\2\2\u04d3\u04d4\3\2\2\2\u04d4\u04d5\5\u008cG\2\u04d5\u04e5"+
		"\bF\1\2\u04d6\u04d7\7\64\2\2\u04d7\u04d8\5\u009cO\2\u04d8\u04d9\7\65\2"+
		"\2\u04d9\u04da\bF\1\2\u04da\u04e4\3\2\2\2\u04db\u04dc\7;\2\2\u04dc\u04dd"+
		"\5\u008eH\2\u04dd\u04de\7<\2\2\u04de\u04df\bF\1\2\u04df\u04e4\3\2\2\2"+
		"\u04e0\u04e1\7\61\2\2\u04e1\u04e2\7(\2\2\u04e2\u04e4\bF\1\2\u04e3\u04d6"+
		"\3\2\2\2\u04e3\u04db\3\2\2\2\u04e3\u04e0\3\2\2\2\u04e4\u04e7\3\2\2\2\u04e5"+
		"\u04e3\3\2\2\2\u04e5\u04e6\3\2\2\2\u04e6\u008b\3\2\2\2\u04e7\u04e5\3\2"+
		"\2\2\u04e8\u04f0\7\64\2\2\u04e9\u04ea\5\u00a4S\2\u04ea\u04eb\bG\1\2\u04eb"+
		"\u04f1\3\2\2\2\u04ec\u04ed\5\u0098M\2\u04ed\u04ee\bG\1\2\u04ee\u04f1\3"+
		"\2\2\2\u04ef\u04f1\bG\1\2\u04f0\u04e9\3\2\2\2\u04f0\u04ec\3\2\2\2\u04f0"+
		"\u04ef\3\2\2\2\u04f1\u04f2\3\2\2\2\u04f2\u04f3\7\65\2\2\u04f3\u0528\b"+
		"G\1\2\u04f4\u04f9\7;\2\2\u04f5\u04f6\5\u0098M\2\u04f6\u04f7\bG\1\2\u04f7"+
		"\u04fa\3\2\2\2\u04f8\u04fa\bG\1\2\u04f9\u04f5\3\2\2\2\u04f9\u04f8\3\2"+
		"\2\2\u04fa\u04fb\3\2\2\2\u04fb\u04fc\7<\2\2\u04fc\u0528\bG\1\2\u04fd\u0505"+
		"\7H\2\2\u04fe\u04ff\5\u0096L\2\u04ff\u0500\bG\1\2\u0500\u0506\3\2\2\2"+
		"\u0501\u0502\5\u0098M\2\u0502\u0503\bG\1\2\u0503\u0506\3\2\2\2\u0504\u0506"+
		"\bG\1\2\u0505\u04fe\3\2\2\2\u0505\u0501\3\2\2\2\u0505\u0504\3\2\2\2\u0506"+
		"\u0507\3\2\2\2\u0507\u0508\7I\2\2\u0508\u0528\bG\1\2\u0509\u050a\7(\2"+
		"\2\u050a\u0528\bG\1\2\u050b\u050c\7+\2\2\u050c\u0528\bG\1\2\u050d\u050e"+
		"\7,\2\2\u050e\u0528\bG\1\2\u050f\u0510\7-\2\2\u0510\u0528\bG\1\2\u0511"+
		"\u0512\7.\2\2\u0512\u0528\bG\1\2\u0513\u0514\7/\2\2\u0514\u0528\bG\1\2"+
		"\u0515\u0516\7\60\2\2\u0516\u0528\bG\1\2\u0517\u051a\bG\1\2\u0518\u0519"+
		"\7\3\2\2\u0519\u051b\bG\1\2\u051a\u0518\3\2\2\2\u051b\u051c\3\2\2\2\u051c"+
		"\u051a\3\2\2\2\u051c\u051d\3\2\2\2\u051d\u051e\3\2\2\2\u051e\u0528\bG"+
		"\1\2\u051f\u0520\7\62\2\2\u0520\u0528\bG\1\2\u0521\u0522\7\34\2\2\u0522"+
		"\u0528\bG\1\2\u0523\u0524\7\35\2\2\u0524\u0528\bG\1\2\u0525\u0526\7\36"+
		"\2\2\u0526\u0528\bG\1\2\u0527\u04e8\3\2\2\2\u0527\u04f4\3\2\2\2\u0527"+
		"\u04fd\3\2\2\2\u0527\u0509\3\2\2\2\u0527\u050b\3\2\2\2\u0527\u050d\3\2"+
		"\2\2\u0527\u050f\3\2\2\2\u0527\u0511\3\2\2\2\u0527\u0513\3\2\2\2\u0527"+
		"\u0515\3\2\2\2\u0527\u0517\3\2\2\2\u0527\u051f\3\2\2\2\u0527\u0521\3\2"+
		"\2\2\u0527\u0523\3\2\2\2\u0527\u0525\3\2\2\2\u0528\u008d\3\2\2\2\u0529"+
		"\u052a\5\u0090I\2\u052a\u053e\bH\1\2\u052b\u052c\bH\1\2\u052c\u053b\7"+
		"\66\2\2\u052d\u052e\5\u0090I\2\u052e\u0535\bH\1\2\u052f\u0530\7\66\2\2"+
		"\u0530\u0531\5\u0090I\2\u0531\u0532\bH\1\2\u0532\u0534\3\2\2\2\u0533\u052f"+
		"\3\2\2\2\u0534\u0537\3\2\2\2\u0535\u0533\3\2\2\2\u0535\u0536\3\2\2\2\u0536"+
		"\u0539\3\2\2\2\u0537\u0535\3\2\2\2\u0538\u053a\7\66\2\2\u0539\u0538\3"+
		"\2\2\2\u0539\u053a\3\2\2\2\u053a\u053c\3\2\2\2\u053b\u052d\3\2\2\2\u053b"+
		"\u053c\3\2\2\2\u053c\u053d\3\2\2\2\u053d\u053f\bH\1\2\u053e\u052b\3\2"+
		"\2\2\u053e\u053f\3\2\2\2\u053f\u008f\3\2\2\2\u0540\u0541\5f\64\2\u0541"+
		"\u0542\bI\1\2\u0542\u0559\3\2\2\2\u0543\u0547\bI\1\2\u0544\u0545\5f\64"+
		"\2\u0545\u0546\bI\1\2\u0546\u0548\3\2\2\2\u0547\u0544\3\2\2\2\u0547\u0548"+
		"\3\2\2\2\u0548\u0549\3\2\2\2\u0549\u054d\7\67\2\2\u054a\u054b\5f\64\2"+
		"\u054b\u054c\bI\1\2\u054c\u054e\3\2\2\2\u054d\u054a\3\2\2\2\u054d\u054e"+
		"\3\2\2\2\u054e\u0555\3\2\2\2\u054f\u0553\7\67\2\2\u0550\u0551\5f\64\2"+
		"\u0551\u0552\bI\1\2\u0552\u0554\3\2\2\2\u0553\u0550\3\2\2\2\u0553\u0554"+
		"\3\2\2\2\u0554\u0556\3\2\2\2\u0555\u054f\3\2\2\2\u0555\u0556\3\2\2\2\u0556"+
		"\u0557\3\2\2\2\u0557\u0559\bI\1\2\u0558\u0540\3\2\2\2\u0558\u0543\3\2"+
		"\2\2\u0559\u0091\3\2\2\2\u055a\u0561\bJ\1\2\u055b\u055c\5z>\2\u055c\u055d"+
		"\bJ\1\2\u055d\u0562\3\2\2\2\u055e\u055f\5x=\2\u055f\u0560\bJ\1\2\u0560"+
		"\u0562\3\2\2\2\u0561\u055b\3\2\2\2\u0561\u055e\3\2\2\2\u0562\u056e\3\2"+
		"\2\2\u0563\u056a\7\66\2\2\u0564\u0565\5z>\2\u0565\u0566\bJ\1\2\u0566\u056b"+
		"\3\2\2\2\u0567\u0568\5x=\2\u0568\u0569\bJ\1\2\u0569\u056b\3\2\2\2\u056a"+
		"\u0564\3\2\2\2\u056a\u0567\3\2\2\2\u056b\u056d\3\2\2\2\u056c\u0563\3\2"+
		"\2\2\u056d\u0570\3\2\2\2\u056e\u056c\3\2\2\2\u056e\u056f\3\2\2\2\u056f"+
		"\u0572\3\2\2\2\u0570\u056e\3\2\2\2\u0571\u0573\7\66\2\2\u0572\u0571\3"+
		"\2\2\2\u0572\u0573\3\2\2\2\u0573\u0574\3\2\2\2\u0574\u0575\bJ\1\2\u0575"+
		"\u0093\3\2\2\2\u0576\u0577\5f\64\2\u0577\u058b\bK\1\2\u0578\u0579\bK\1"+
		"\2\u0579\u0588\7\66\2\2\u057a\u057b\5f\64\2\u057b\u0582\bK\1\2\u057c\u057d"+
		"\7\66\2\2\u057d\u057e\5f\64\2\u057e\u057f\bK\1\2\u057f\u0581\3\2\2\2\u0580"+
		"\u057c\3\2\2\2\u0581\u0584\3\2\2\2\u0582\u0580\3\2\2\2\u0582\u0583\3\2"+
		"\2\2\u0583\u0586\3\2\2\2\u0584\u0582\3\2\2\2\u0585\u0587\7\66\2\2\u0586"+
		"\u0585\3\2\2\2\u0586\u0587\3\2\2\2\u0587\u0589\3\2\2\2\u0588\u057a\3\2"+
		"\2\2\u0588\u0589\3\2\2\2\u0589\u058a\3\2\2\2\u058a\u058c\bK\1\2\u058b"+
		"\u0578\3\2\2\2\u058b\u058c\3\2\2\2\u058c\u0095\3\2\2\2\u058d\u0597\bL"+
		"\1\2\u058e\u058f\5f\64\2\u058f\u0590\7\67\2\2\u0590\u0591\5f\64\2\u0591"+
		"\u0592\bL\1\2\u0592\u0598\3\2\2\2\u0593\u0594\79\2\2\u0594\u0595\5z>\2"+
		"\u0595\u0596\bL\1\2\u0596\u0598\3\2\2\2\u0597\u058e\3\2\2\2\u0597\u0593"+
		"\3\2\2\2\u0598\u0599\3\2\2\2\u0599\u059a\5\u00a0Q\2\u059a\u059b\bL\1\2"+
		"\u059b\u05c0\3\2\2\2\u059c\u05a6\bL\1\2\u059d\u059e\5f\64\2\u059e\u059f"+
		"\7\67\2\2\u059f\u05a0\5f\64\2\u05a0\u05a1\bL\1\2\u05a1\u05a7\3\2\2\2\u05a2"+
		"\u05a3\79\2\2\u05a3\u05a4\5z>\2\u05a4\u05a5\bL\1\2\u05a5\u05a7\3\2\2\2"+
		"\u05a6\u059d\3\2\2\2\u05a6\u05a2\3\2\2\2\u05a7\u05a8\3\2\2\2\u05a8\u05b7"+
		"\bL\1\2\u05a9\u05b3\7\66\2\2\u05aa\u05ab\5f\64\2\u05ab\u05ac\7\67\2\2"+
		"\u05ac\u05ad\5f\64\2\u05ad\u05ae\bL\1\2\u05ae\u05b4\3\2\2\2\u05af\u05b0"+
		"\79\2\2\u05b0\u05b1\5z>\2\u05b1\u05b2\bL\1\2\u05b2\u05b4\3\2\2\2\u05b3"+
		"\u05aa\3\2\2\2\u05b3\u05af\3\2\2\2\u05b4\u05b6\3\2\2\2\u05b5\u05a9\3\2"+
		"\2\2\u05b6\u05b9\3\2\2\2\u05b7\u05b5\3\2\2\2\u05b7\u05b8\3\2\2\2\u05b8"+
		"\u05bb\3\2\2\2\u05b9\u05b7\3\2\2\2\u05ba\u05bc\7\66\2\2\u05bb\u05ba\3"+
		"\2\2\2\u05bb\u05bc\3\2\2\2\u05bc\u05bd\3\2\2\2\u05bd\u05be\bL\1\2\u05be"+
		"\u05c0\3\2\2\2\u05bf\u058d\3\2\2\2\u05bf\u059c\3\2\2\2\u05c0\u0097\3\2"+
		"\2\2\u05c1\u05c8\bM\1\2\u05c2\u05c3\5f\64\2\u05c3\u05c4\bM\1\2\u05c4\u05c9"+
		"\3\2\2\2\u05c5\u05c6\5x=\2\u05c6\u05c7\bM\1\2\u05c7\u05c9\3\2\2\2\u05c8"+
		"\u05c2\3\2\2\2\u05c8\u05c5\3\2\2\2\u05c9\u05ca\3\2\2\2\u05ca\u05cb\5\u00a0"+
		"Q\2\u05cb\u05cc\bM\1\2\u05cc\u05ed\3\2\2\2\u05cd\u05d4\bM\1\2\u05ce\u05cf"+
		"\5f\64\2\u05cf\u05d0\bM\1\2\u05d0\u05d5\3\2\2\2\u05d1\u05d2\5x=\2\u05d2"+
		"\u05d3\bM\1\2\u05d3\u05d5\3\2\2\2\u05d4\u05ce\3\2\2\2\u05d4\u05d1\3\2"+
		"\2\2\u05d5\u05d6\3\2\2\2\u05d6\u05e2\bM\1\2\u05d7\u05de\7\66\2\2\u05d8"+
		"\u05d9\5f\64\2\u05d9\u05da\bM\1\2\u05da\u05df\3\2\2\2\u05db\u05dc\5x="+
		"\2\u05dc\u05dd\bM\1\2\u05dd\u05df\3\2\2\2\u05de\u05d8\3\2\2\2\u05de\u05db"+
		"\3\2\2\2\u05df\u05e1\3\2\2\2\u05e0\u05d7\3\2\2\2\u05e1\u05e4\3\2\2\2\u05e2"+
		"\u05e0\3\2\2\2\u05e2\u05e3\3\2\2\2\u05e3\u05e5\3\2\2\2\u05e4\u05e2\3\2"+
		"\2\2\u05e5\u05e8\bM\1\2\u05e6\u05e7\7\66\2\2\u05e7\u05e9\bM\1\2\u05e8"+
		"\u05e6\3\2\2\2\u05e8\u05e9\3\2\2\2\u05e9\u05ea\3\2\2\2\u05ea\u05eb\bM"+
		"\1\2\u05eb\u05ed\3\2\2\2\u05ec\u05c1\3\2\2\2\u05ec\u05cd\3\2\2\2\u05ed"+
		"\u0099\3\2\2\2\u05ee\u05ef\7\37\2\2\u05ef\u05f0\7(\2\2\u05f0\u05f6\bN"+
		"\1\2\u05f1\u05f2\7\64\2\2\u05f2\u05f3\5\u009cO\2\u05f3\u05f4\7\65\2\2"+
		"\u05f4\u05f5\bN\1\2\u05f5\u05f7\3\2\2\2\u05f6\u05f1\3\2\2\2\u05f6\u05f7"+
		"\3\2\2\2\u05f7\u05f8\3\2\2\2\u05f8\u05f9\bN\1\2\u05f9\u05fa\7\67\2\2\u05fa"+
		"\u05fb\5d\63\2\u05fb\u05fc\bN\1\2\u05fc\u05fd\bN\1\2\u05fd\u009b\3\2\2"+
		"\2\u05fe\u060a\bO\1\2\u05ff\u0604\5\u009eP\2\u0600\u0601\7\66\2\2\u0601"+
		"\u0603\5\u009eP\2\u0602\u0600\3\2\2\2\u0603\u0606\3\2\2\2\u0604\u0602"+
		"\3\2\2\2\u0604\u0605\3\2\2\2\u0605\u0608\3\2\2\2\u0606\u0604\3\2\2\2\u0607"+
		"\u0609\7\66\2\2\u0608\u0607\3\2\2\2\u0608\u0609\3\2\2\2\u0609\u060b\3"+
		"\2\2\2\u060a\u05ff\3\2\2\2\u060a\u060b\3\2\2\2\u060b\u060c\3\2\2\2\u060c"+
		"\u060d\bO\1\2\u060d\u009d\3\2\2\2\u060e\u060f\bP\1\2\u060f\u0610\5f\64"+
		"\2\u0610\u0611\5\u00a0Q\2\u0611\u0612\bP\1\2\u0612\u0626\3\2\2\2\u0613"+
		"\u0614\bP\1\2\u0614\u0615\5f\64\2\u0615\u0616\bP\1\2\u0616\u0617\7:\2"+
		"\2\u0617\u0618\5f\64\2\u0618\u0619\bP\1\2\u0619\u0626\3\2\2\2\u061a\u061b"+
		"\5f\64\2\u061b\u061c\bP\1\2\u061c\u0626\3\2\2\2\u061d\u061e\79\2\2\u061e"+
		"\u061f\5f\64\2\u061f\u0620\bP\1\2\u0620\u0626\3\2\2\2\u0621\u0622\7\63"+
		"\2\2\u0622\u0623\5f\64\2\u0623\u0624\bP\1\2\u0624\u0626\3\2\2\2\u0625"+
		"\u060e\3\2\2\2\u0625\u0613\3\2\2\2\u0625\u061a\3\2\2\2\u0625\u061d\3\2"+
		"\2\2\u0625\u0621\3\2\2\2\u0626\u009f\3\2\2\2\u0627\u062a\bQ\1\2\u0628"+
		"\u0629\7%\2\2\u0629\u062b\bQ\1\2\u062a\u0628\3\2\2\2\u062a\u062b\3\2\2"+
		"\2\u062b\u062c\3\2\2\2\u062c\u062d\bQ\1\2\u062d\u062e\7\21\2\2\u062e\u062f"+
		"\5\u0092J\2\u062f\u0630\7\22\2\2\u0630\u0631\bQ\1\2\u0631\u0632\5n8\2"+
		"\u0632\u0633\bQ\1\2\u0633\u063a\bQ\1\2\u0634\u0635\7\r\2\2\u0635\u0636"+
		"\5h\65\2\u0636\u0637\bQ\1\2\u0637\u0639\3\2\2\2\u0638\u0634\3\2\2\2\u0639"+
		"\u063c\3\2\2\2\u063a\u0638\3\2\2\2\u063a\u063b\3\2\2\2\u063b\u063d\3\2"+
		"\2\2\u063c\u063a\3\2\2\2\u063d\u0641\bQ\1\2\u063e\u063f\5\u00a0Q\2\u063f"+
		"\u0640\bQ\1\2\u0640\u0642\3\2\2\2\u0641\u063e\3\2\2\2\u0641\u0642\3\2"+
		"\2\2\u0642\u0643\3\2\2\2\u0643\u0644\bQ\1\2\u0644\u00a1\3\2\2\2\u0645"+
		"\u0646\7(\2\2\u0646\u00a3\3\2\2\2\u0647\u0648\bS\1\2\u0648\u0650\7 \2"+
		"\2\u0649\u064a\7\7\2\2\u064a\u064b\5f\64\2\u064b\u064c\bS\1\2\u064c\u0651"+
		"\3\2\2\2\u064d\u064e\5,\27\2\u064e\u064f\bS\1\2\u064f\u0651\3\2\2\2\u0650"+
		"\u0649\3\2\2\2\u0650\u064d\3\2\2\2\u0650\u0651\3\2\2\2\u0651\u0652\3\2"+
		"\2\2\u0652\u0653\bS\1\2\u0653\u00a5\3\2\2\2\u00be\u00ab\u00b4\u00b6\u00c2"+
		"\u00d1\u00da\u00e3\u00ef\u00f9\u0103\u010f\u0112\u011a\u0120\u0122\u0124"+
		"\u0128\u012a\u012c\u012e\u0135\u013e\u0144\u0146\u0148\u014c\u014e\u0150"+
		"\u0157\u015d\u015f\u0161\u0165\u0167\u016f\u0175\u0181\u0183\u018e\u0198"+
		"\u01a4\u01a7\u01af\u01b5\u01b7\u01b9\u01bd\u01bf\u01c1\u01c3\u01ca\u01d3"+
		"\u01d9\u01db\u01dd\u01e1\u01e3\u01e5\u01ec\u01f2\u01f4\u01f6\u01fa\u01fc"+
		"\u0206\u020e\u0218\u021f\u0223\u0230\u023a\u0245\u0253\u0257\u025b\u0263"+
		"\u026d\u0276\u027a\u027e\u0280\u0283\u0292\u0299\u02a8\u02aa\u02b0\u02bb"+
		"\u02bd\u02c7\u02c9\u02cb\u02d8\u02e1\u02ee\u02f2\u02fd\u0305\u030c\u0318"+
		"\u0326\u0332\u033f\u0345\u035b\u0368\u0379\u0387\u038e\u0395\u039c\u03a7"+
		"\u03a9\u03b9\u03c4\u03cf\u03d3\u03df\u03e4\u03ec\u03f3\u0400\u0411\u0416"+
		"\u0421\u0426\u042f\u0439\u043e\u0458\u0466\u0471\u047c\u0486\u048d\u0497"+
		"\u049e\u04ae\u04b5\u04bf\u04c7\u04cf\u04d2\u04e3\u04e5\u04f0\u04f9\u0505"+
		"\u051c\u0527\u0535\u0539\u053b\u053e\u0547\u054d\u0553\u0555\u0558\u0561"+
		"\u056a\u056e\u0572\u0582\u0586\u0588\u058b\u0597\u05a6\u05b3\u05b7\u05bb"+
		"\u05bf\u05c8\u05d4\u05de\u05e2\u05e8\u05ec\u05f6\u0604\u0608\u060a\u0625"+
		"\u062a\u063a\u0641\u0650";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
