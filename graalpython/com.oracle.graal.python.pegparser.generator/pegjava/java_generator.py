# Copyright (c) 2021, 2022, Oracle and/or its affiliates.
# Copyright (C) 1996-2021 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

"""This is heavily based on the C generator, and we try to keep it as similar as
possible to make updates and maintenance easy. Where we deviate in non obvious
ways, we add comments."""

import os
import ast
import re

from collections import Counter
from dataclasses import dataclass

from typing import Any, Dict, IO, Optional, List, Text, Tuple, Set
from enum import Enum

from pegen import grammar
from pegen.grammar import (
    Alt,
    Cut,
    Forced,
    Gather,
    GrammarVisitor,
    Group,
    Lookahead,
    NamedItem,
    NameLeaf,
    NegativeLookahead,
    Opt,
    PositiveLookahead,
    Repeat0,
    Repeat1,
    Rhs,
    Rule,
    StringLeaf,
)
from pegen.parser_generator import ParserGenerator


class NodeTypes(Enum):
    NAME_TOKEN = 0
    NUMBER_TOKEN = 1
    STRING_TOKEN = 2
    GENERIC_TOKEN = 3
    KEYWORD = 4
    SOFT_KEYWORD = 5
    CUT_OPERATOR = 6


BASE_NODETYPES = {
    "NAME": (NodeTypes.NAME_TOKEN, "expr_ty"),
    "NUMBER": (NodeTypes.NUMBER_TOKEN, "expr_ty"),
    "STRING": (NodeTypes.STRING_TOKEN, "Token*"),
    "SOFT_KEYWORD": (NodeTypes.SOFT_KEYWORD, "expr_ty"),
}


@dataclass
class FunctionCall:
    function: str
    arguments: Optional[List[Any]] = None
    assigned_variable: Optional[str] = None
    assigned_variable_type: Optional[str] = None
    return_type: Optional[str] = None
    nodetype: Optional[NodeTypes] = None
    force_true: bool = False
    comment: Optional[str] = None

    def __str__(self) -> str:
        parts = []
        parts.append(self.function)
        if self.arguments is not None:
            parts.append(f"({', '.join(map(str, self.arguments))})")
        ft_part_start = ""
        try:
            int(self.function)
            ft_part_end = ") != 0"
        except:
            ft_part_end = ") != null"
        if self.force_true:
            ft_part_start= "("
            ft_part_end = f"{ft_part_end} || true)"
        if self.assigned_variable:
            if self.assigned_variable_type:
                var_type = _check_type(self, self.assigned_variable_type)
                parts = [ft_part_start, "(", self.assigned_variable, " = ", '(', var_type, ')', *parts, ft_part_end]
            else:
                parts = [ft_part_start, "(", self.assigned_variable, " = ", *parts, ft_part_end]
        if self.comment:
            parts.append(f"  // {self.comment}")
        return "".join(parts)


TYPE_MAPPINGS = {
    "AugOperator*": "ExprTy.BinOp.Operator",
    "CmpopExprPair*": "CmpopExprPair",
    "CmpopExprPair**": "CmpopExprPair[]",
    "KeyValuePair*": "KeyValuePair",
    "KeyValuePair**": "KeyValuePair[]",
    "KeywordOrStarred*": "KeywordOrStarred",
    "KeywordOrStarred**": "KeywordOrStarred[]",
    "NameDefaultPair*": "NameDefaultPair",
    "NameDefaultPair**": "NameDefaultPair[]",
    "SlashWithDefault*": "SlashWithDefault",
    "StarEtc*": "StarEtc",
    "Token*": "Token",
    "Token**": "Token[]",
    "alias_ty": "AliasTy",
    "alias_ty*": "AliasTy[]",
    "alias_ty**": "AliasTy[]",
    "arg_ty": "ArgTy",
    "arg_ty*": "ArgTy[]",
    "arguments_ty": "ArgumentsTy",
    "asdl_alias_seq*": "AliasTy[]",
    "asdl_arg_seq*": "ArgTy[]",
    "asdl_comprehension_seq*": "ComprehensionTy[]",
    "asdl_excepthandler_seq*": "StmtTy.Try.ExceptHandler[]",
    "asdl_expr_seq*": "ExprTy[]",
    "asdl_expr_seq**": "ExprTy[]",
    "asdl_keyword_seq*": "KeywordTy[]",
    "asdl_match_case_seq*": "StmtTy.Match.Case[]",
    "asdl_stmt_seq*": "StmtTy[]",
    "asdl_stmt_seq**": "StmtTy[]",
    "asdl_withitem_seq*": "StmtTy.With.Item[]",
    "comprehension_ty": "ComprehensionTy",
    "comprehension_ty*": "ComprehensionTy[]",
    "excepthandler_ty": "StmtTy.Try.ExceptHandler",
    "excepthandler_ty*": "StmtTy.Try.ExceptHandler[]",
    "expr_ty": "ExprTy",
    "expr_ty*": "ExprTy[]",
    "expr_ty**": "ExprTy[]",
    "keyword_ty": "KeywordTy",
    "keyword_ty*": "KeywordTy[]",
    "keyword_ty**": "KeywordTy[]",
    "match_case_ty": "StmtTy.Match.Case",
    "match_case_ty*": "StmtTy.Match.Case[]",
    "mod_ty": "ModTy",
    "stmt_ty": "StmtTy",
    "stmt_ty*": "StmtTy[]",
    "stmt_ty**": "StmtTy[]",
    "withitem_ty": "StmtTy.With.Item",
    "withitem_ty*": "StmtTy.With.Item[]",
    "withitem_ty**": "StmtTy.With.Item[]",

    # Java return types here
    "boolean": "boolean",
    "int": "int",
    "Object": "Object",
    "Object*": "Object[]",
}

# Maps C actions to (n, java action), where n is the expected number of occurrences in the grammar
ACTION_MAPPINGS = {
    '( asdl_alias_seq * ) _PyPegen_singleton_seq ( p , CHECK ( alias_ty , _PyPegen_alias_for_star ( p , EXTRA ) ) )': (1, 'new AliasTy [ ] {factory.createAlias("*",null,startToken.startOffset,endToken.endOffset)}'),
    '( asdl_expr_seq * ) _PyPegen_seq_append_to_end ( p , CHECK ( asdl_seq * , _PyPegen_seq_append_to_end ( p , a , b ) ) , c )': (1, 'this.appendToEnd(this.appendToEnd(a,b),c)'),
    '( asdl_expr_seq * ) _PyPegen_seq_append_to_end ( p , CHECK ( asdl_seq * , _PyPegen_singleton_seq ( p , a ) ) , b )': (1, 'this.appendToEnd(new ExprTy [ ] {a},b)'),
    '( asdl_expr_seq * ) _PyPegen_seq_append_to_end ( p , a , b )': (2, 'this.appendToEnd(a,b)'),
    '( asdl_expr_seq * ) _PyPegen_seq_insert_in_front ( p , a , b )': (1, 'this.insertInFront(a,b)'),
    '( asdl_expr_seq * ) _PyPegen_singleton_seq ( p , a )': (3, 'new ExprTy [ ] {a}'),
    '( asdl_stmt_seq * ) _PyPegen_seq_flatten ( p , a )': (1, 'a'),
    '( asdl_stmt_seq * ) _PyPegen_singleton_seq ( p , CHECK ( stmt_ty , _PyAST_Pass ( EXTRA ) ) )': (1, 'new StmtTy [ ] {factory.createPass(startToken.startOffset,endToken.endOffset)}'),
    '( asdl_stmt_seq * ) _PyPegen_singleton_seq ( p , a )': (3, 'new StmtTy [ ] {a}'),
    'CHECK_VERSION ( AugOperator * , 5 , "The \'@\' operator is" , _PyPegen_augoperator ( p , MatMult ) )': (1, 'ExprTy.BinOp.Operator.MATMULT'),
    'CHECK_VERSION ( expr_ty , 5 , "Await expressions are" , _PyAST_Await ( a , EXTRA ) )': (1, 'checkVersion(5,"Await expressions are",factory.createAwait(a,startToken.startOffset,endToken.endOffset))'),
    'CHECK_VERSION ( expr_ty , 5 , "The \'@\' operator is" , _PyAST_BinOp ( a , MatMult , b , EXTRA ) )': (1, 'checkVersion(5,"The \'@\' operator is",factory.createBinaryOp(ExprTy.BinOp.Operator.MATMULT,a,b,startToken.startOffset,endToken.endOffset))'),
    'CHECK_VERSION ( stmt_ty , 5 , "Async functions are" , _PyAST_AsyncFunctionDef ( n -> v . Name . id , ( params ) ? params : CHECK ( arguments_ty , _PyPegen_empty_arguments ( p ) ) , b , NULL , a , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) )': (1, 'checkVersion(5,"Async functions are",factory.createAsyncFunctionDef(((ExprTy.Name)n).id,params,b,a,newTypeComment((Token)tc),startToken.startOffset,endToken.endOffset))'),
    'CHECK_VERSION ( stmt_ty , 6 , "Variable annotation syntax is" , _PyAST_AnnAssign ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , b , c , 1 , EXTRA ) )': (1, 'factory.createAnnAssignment(setExprContext(a,ExprContext.Store),b,(ExprTy)c,true,startToken.startOffset,endToken.endOffset);'),
    'RAISE_ERROR_KNOWN_LOCATION ( p , PyExc_SyntaxError , a -> lineno , a -> end_col_offset - 1 , "\':\' expected after dictionary key" )': (1, 'this.raiseErrorKnownLocation(ParserErrorCallback.ErrorType.Syntax,a,"\':\' expected after dictionary key")'),
    'RAISE_ERROR_KNOWN_LOCATION ( p , PyExc_SyntaxError , a -> lineno , a -> end_col_offset - 1 , "invalid syntax. Perhaps you forgot a comma?" )': (1, 'this.raiseErrorKnownLocation(ParserErrorCallback.ErrorType.Syntax,a,"invalid syntax.Perhaps you forgot a comma?")'),
    'RAISE_SYNTAX_ERROR ( "Cannot have two type comments on def" )': (1, 'this.raiseSyntaxError("Cannot have two type comments on def")'),
    'RAISE_SYNTAX_ERROR ( "bare * has associated type comment" )': (1, 'this.raiseSyntaxError("bare * has associated type comment")'),
    'RAISE_SYNTAX_ERROR ( "expected \':\'" )': (4, 'this.raiseSyntaxError("expected \':\'")'),
    'RAISE_SYNTAX_ERROR ( "iterable argument unpacking follows keyword argument unpacking" )': (1, 'this.raiseSyntaxError("iterable argument unpacking follows keyword argument unpacking")'),
    'RAISE_SYNTAX_ERROR ( "named arguments must follow bare *" )': (2, 'this.raiseSyntaxError("named arguments must follow bare *")'),
    'RAISE_SYNTAX_ERROR ( "non-default argument follows default argument" )': (2, 'this.raiseSyntaxError("non-default argument follows default argument")'),
    'RAISE_SYNTAX_ERROR ( "trailing comma not allowed without surrounding parentheses" )': (1, 'this.raiseSyntaxError("trailing comma not allowed without surrounding parentheses")'),
    'RAISE_SYNTAX_ERROR_INVALID_TARGET ( DEL_TARGETS , a )': (1, 'this.raiseSyntaxErrorInvalidTarget(TargetsType.DEL_TARGETS,a)'),
    'RAISE_SYNTAX_ERROR_INVALID_TARGET ( FOR_TARGETS , a )': (1, 'this.raiseSyntaxErrorInvalidTarget(TargetsType.FOR_TARGETS,a)'),
    'RAISE_SYNTAX_ERROR_INVALID_TARGET ( STAR_TARGETS , a )': (2, 'this.raiseSyntaxErrorInvalidTarget(TargetsType.STAR_TARGETS,a)'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "\'%s\' is an illegal expression for augmented assignment" , _PyPegen_get_expr_name ( a ) )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"\'%s\' is an illegal expression for augmented assignment",getExprName(a))'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "Generator expression must be parenthesized" )': (2, 'this.raiseSyntaxErrorKnownLocation(a,"Generator expression must be parenthesized")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "assignment to yield expression not possible" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"assignment to yield expression not possible")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use a starred expression in a dictionary value" )': (2, 'this.raiseSyntaxErrorKnownLocation(a,"cannot use a starred expression in a dictionary value")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use assignment expressions with %s" , _PyPegen_get_expr_name ( a ) )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"cannot use assignment expressions with %s",getExprName(a))'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use double starred expression here" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"cannot use double starred expression here")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "cannot use starred expression here" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"cannot use starred expression here")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "dict unpacking cannot be used in dict comprehension" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"dict unpacking cannot be used in dict comprehension")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "did you forget parentheses around the comprehension target?" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"did you forget parentheses around the comprehension target?")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "exception group must be parenthesized" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"exception group must be parenthesized")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "expression cannot contain assignment, perhaps you meant \\"==\\"?" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"expression cannot contain assignment,perhaps you meant \\"==\\"?")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "expression expected after dictionary key and \':\'" )': (2, 'this.raiseSyntaxErrorKnownLocation(a,"expression expected after dictionary key and \':\'")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "illegal target for annotation" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"illegal target for annotation")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "invalid syntax" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"invalid syntax")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "iterable unpacking cannot be used in comprehension" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"iterable unpacking cannot be used in comprehension")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "only single target (not %s) can be annotated" , _PyPegen_get_expr_name ( a ) )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"only single target(not %s)can be annotated",getExprName(a))'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( a , "only single target (not tuple) can be annotated" )': (1, 'this.raiseSyntaxErrorKnownLocation(a,"only single target(not tuple)can be annotated")'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( b , "cannot assign to %s here. Maybe you meant \'==\' instead of \'=\'?" , _PyPegen_get_expr_name ( a ) )': (1, 'this.raiseSyntaxErrorKnownLocation(b,"cannot assign to %s here.Maybe you meant \'==\' instead of \'=\'?",getExprName(a))'),
    'RAISE_SYNTAX_ERROR_KNOWN_LOCATION ( b , "invalid syntax. Maybe you meant \'==\' or \':=\' instead of \'=\'?" )': (1, 'this.raiseSyntaxErrorKnownLocation(b,"invalid syntax.Maybe you meant \'==\' or \':=\' instead of \'=\'?")'),
    '_PyAST_Assert ( a , b , EXTRA )': (1, 'factory.createAssert(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Assign ( a , b , NEW_TYPE_COMMENT ( p , tc ) , EXTRA )': (1, 'factory.createAssignment(a,(ExprTy)b,newTypeComment((Token)tc),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Attribute ( a , b -> v . Name . id , Del , EXTRA )': (1, 'factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Delete,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Attribute ( a , b -> v . Name . id , Load , EXTRA )': (2, 'factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Load,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Attribute ( a , b -> v . Name . id , Store , EXTRA )': (3, 'factory.createGetAttribute(a,((ExprTy.Name)b).id,ExprContext.Store,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Attribute ( value , attr -> v . Name . id , Load , EXTRA )': (1, 'factory.createGetAttribute(value,((ExprTy.Name)attr).id,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_AugAssign ( a , b -> kind , c , EXTRA )': (1, 'factory.createAugAssignment(a,b,(ExprTy)c,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , Add , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.ADD,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , BitAnd , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.BITAND,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , BitOr , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.BITOR,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , BitXor , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.BITXOR,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , Div , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.DIV,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , FloorDiv , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.FLOORDIV,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , LShift , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.LSHIFT,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , Mod , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.MOD,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , Mult , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.MULT,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , Pow , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.POW,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , RShift , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.RSHIFT,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( a , Sub , b , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.SUB,a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( real , Add , imag , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.ADD,real,imag,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BinOp ( real , Sub , imag , EXTRA )': (1, 'factory.createBinaryOp(ExprTy.BinOp.Operator.SUB,real,imag,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BoolOp ( And , CHECK ( asdl_expr_seq * , _PyPegen_seq_insert_in_front ( p , a , b ) ) , EXTRA )': (1, 'factory.createAnd(this.insertInFront(a,b),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_BoolOp ( Or , CHECK ( asdl_expr_seq * , _PyPegen_seq_insert_in_front ( p , a , b ) ) , EXTRA )': (1, 'factory.createOr(this.insertInFront(a,b),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Break ( EXTRA )': (1, 'factory.createBreak(startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Call ( _PyPegen_dummy_name ( p ) , CHECK_NULL_ALLOWED ( asdl_expr_seq * , _PyPegen_seq_extract_starred_exprs ( p , a ) ) , CHECK_NULL_ALLOWED ( asdl_keyword_seq * , _PyPegen_seq_delete_starred_exprs ( p , a ) ) , EXTRA )': (1, 'factory.createCall(dummyName(),extractStarredExpressions(a),deleteStarredExpressions(a),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Call ( a , ( b ) ? ( ( expr_ty ) b ) -> v . Call . args : NULL , ( b ) ? ( ( expr_ty ) b ) -> v . Call . keywords : NULL , EXTRA )': (2, 'factory.createCall(a,b != null ?((ExprTy.Call)b).args : EMPTY_EXPR,b != null ?((ExprTy.Call)b).keywords : EMPTY_KWDS,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Call ( a , CHECK ( asdl_expr_seq * , ( asdl_expr_seq * ) _PyPegen_singleton_seq ( p , b ) ) , NULL , EXTRA )': (2, 'factory.createCall(a,new ExprTy [ ] {b},EMPTY_KWDS,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_ClassDef ( a -> v . Name . id , ( b ) ? ( ( expr_ty ) b ) -> v . Call . args : NULL , ( b ) ? ( ( expr_ty ) b ) -> v . Call . keywords : NULL , c , NULL , EXTRA )': (1, 'factory.createClassDef(a,b,c,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Compare ( a , CHECK ( asdl_int_seq * , _PyPegen_get_cmpops ( p , b ) ) , CHECK ( asdl_expr_seq * , _PyPegen_get_exprs ( p , b ) ) , EXTRA )': (1, 'factory.createComparison(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Constant ( Py_Ellipsis , NULL , EXTRA )': (1, 'factory.createEllipsis(startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Constant ( Py_False , NULL , EXTRA )': (2, 'factory.createBooleanLiteral(false,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Constant ( Py_None , NULL , EXTRA )': (2, 'factory.createNone(startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Constant ( Py_True , NULL , EXTRA )': (2, 'factory.createBooleanLiteral(true,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Continue ( EXTRA )': (1, 'factory.createContinue(startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Delete ( a , EXTRA )': (1, 'factory.createDelete(a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Dict ( CHECK ( asdl_expr_seq * , _PyPegen_get_keys ( p , a ) ) , CHECK ( asdl_expr_seq * , _PyPegen_get_values ( p , a ) ) , EXTRA )': (1, 'factory.createDict(extractKeys(a),extractValues(a),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_DictComp ( a -> key , a -> value , b , EXTRA )': (1, 'factory.createDictComprehension(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_ExceptHandler ( NULL , NULL , b , EXTRA )': (1, 'factory.createExceptHandler(null,null,b,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_ExceptHandler ( e , ( t ) ? ( ( expr_ty ) t ) -> v . Name . id : NULL , b , EXTRA )': (1, 'factory.createExceptHandler(e,t != null ?((ExprTy.Name)t).id : null,b,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Expr ( e , EXTRA )': (1, 'factory.createExpression(e)'),
    '_PyAST_Expr ( y , EXTRA )': (1, 'factory.createExpression(y)'),
    '_PyAST_Expression ( a , p -> arena )': (1, 'factory.createExpressionModule(a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_For ( t , ex , b , el , NEW_TYPE_COMMENT ( p , tc ) , EXTRA )': (1, 'factory.createFor(t,ex,b,el,newTypeComment(tc),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_FunctionDef ( n -> v . Name . id , ( params ) ? params : CHECK ( arguments_ty , _PyPegen_empty_arguments ( p ) ) , b , NULL , a , NEW_TYPE_COMMENT ( p , tc ) , EXTRA )': (1, 'factory.createFunctionDef(((ExprTy.Name)n).id,params,b,(ExprTy)a,newTypeComment((Token)tc),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_GeneratorExp ( a , b , EXTRA )': (1, 'factory.createGenerator(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Global ( CHECK ( asdl_identifier_seq * , _PyPegen_map_names_to_ids ( p , a ) ) , EXTRA )': (1, 'factory.createGlobal(extractNames(a),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_If ( a , b , CHECK ( asdl_stmt_seq * , _PyPegen_singleton_seq ( p , c ) ) , EXTRA )': (2, 'factory.createIf(a,b,new StmtTy [ ] {c},startToken.startOffset,endToken.endOffset)'),
    '_PyAST_If ( a , b , c , EXTRA )': (2, 'factory.createIf(a,b,c,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_IfExp ( b , a , c , EXTRA )': (1, 'factory.createIfExpression(b,a,c,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Import ( a , EXTRA )': (1, 'factory.createImport(a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_ImportFrom ( NULL , b , _PyPegen_seq_count_dots ( a ) , EXTRA )': (1, 'factory.createImportFrom(null,b,countDots(a),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_ImportFrom ( b -> v . Name . id , c , _PyPegen_seq_count_dots ( a ) , EXTRA )': (1, 'factory.createImportFrom(((ExprTy.Name)b).id,c,countDots(a),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Interactive ( a , p -> arena )': (1, 'factory.createInteractiveModule(a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Lambda ( ( a ) ? a : CHECK ( arguments_ty , _PyPegen_empty_arguments ( p ) ) , b , EXTRA )': (1, 'factory.createLambda(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_List ( a , Del , EXTRA )': (1, 'factory.createList(a,ExprContext.Delete,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_List ( a , Load , EXTRA )': (1, 'factory.createList(a,ExprContext.Load,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_List ( a , Store , EXTRA )': (1, 'factory.createList(a,ExprContext.Store,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_ListComp ( a , b , EXTRA )': (1, 'factory.createListComprehension(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_NamedExpr ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , b , EXTRA )': (2, 'factory.createNamedExp(this.check(this.setExprContext(a,ExprContext.Store)),b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Nonlocal ( CHECK ( asdl_identifier_seq * , _PyPegen_map_names_to_ids ( p , a ) ) , EXTRA )': (1, 'factory.createNonLocal(extractNames(a),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Pass ( EXTRA )': (1, 'factory.createPass(startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Raise ( NULL , NULL , EXTRA )': (1, 'factory.createRaise(null,null,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Raise ( a , b , EXTRA )': (1, 'factory.createRaise(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Return ( a , EXTRA )': (1, 'factory.createReturn(a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Set ( a , EXTRA )': (1, 'factory.createSet(a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_SetComp ( a , b , EXTRA )': (1, 'factory.createSetComprehension(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Slice ( a , b , c , EXTRA )': (1, 'factory.createSlice(a,b,c,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Starred ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , Store , EXTRA )': (1, 'factory.createStarred(this.setExprContext(a,ExprContext.Store),ExprContext.Store,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Starred ( a , Load , EXTRA )': (3, 'factory.createStarred(a,ExprContext.Load,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Starred ( value , Store , EXTRA )': (1, 'factory.createStarred(value,ExprContext.Store,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Subscript ( a , b , Del , EXTRA )': (1, 'factory.createSubscript(a,b,ExprContext.Delete,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Subscript ( a , b , Load , EXTRA )': (2, 'factory.createSubscript(a,b,ExprContext.Load,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Subscript ( a , b , Store , EXTRA )': (3, 'factory.createSubscript(a,b,ExprContext.Store,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Try ( b , NULL , NULL , f , EXTRA )': (1, 'factory.createTry(b,null,null,f,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Try ( b , ex , el , f , EXTRA )': (1, 'factory.createTry(b,ex,el,f,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Tuple ( CHECK ( asdl_expr_seq * , _PyPegen_seq_insert_in_front ( p , a , b ) ) , Load , EXTRA )': (2, 'factory.createTuple(this.insertInFront(a,b),ExprContext.Load,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Tuple ( CHECK ( asdl_expr_seq * , _PyPegen_seq_insert_in_front ( p , a , b ) ) , Store , EXTRA )': (1, 'factory.createTuple(this.insertInFront(a,b),ExprContext.Store,startToken.startOffset,endToken.endOffset);'),
    '_PyAST_Tuple ( CHECK ( asdl_expr_seq * , _PyPegen_singleton_seq ( p , a ) ) , Load , EXTRA )': (2, 'factory.createTuple(new ExprTy [ ] {a},ExprContext.Load,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Tuple ( a , Del , EXTRA )': (1, 'factory.createTuple(a,ExprContext.Delete,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_Tuple ( a , Load , EXTRA )': (2, 'factory.createTuple(a,ExprContext.Load,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Tuple ( a , Store , EXTRA )': (1, 'factory.createTuple(a,ExprContext.Store,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_UnaryOp ( Invert , a , EXTRA )': (1, 'factory.createUnaryOp(ExprTy.UnaryOp.Operator.INVERT,a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_UnaryOp ( Not , a , EXTRA )': (1, 'factory.createUnaryOp(ExprTy.UnaryOp.Operator.NOT,a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_UnaryOp ( UAdd , a , EXTRA )': (1, 'factory.createUnaryOp(ExprTy.UnaryOp.Operator.ADD,a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_UnaryOp ( USub , a , EXTRA )': (1, 'factory.createUnaryOp(ExprTy.UnaryOp.Operator.SUB,a,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_UnaryOp ( USub , number , EXTRA )': (1, 'factory.createUnaryOp(ExprTy.UnaryOp.Operator.SUB,number,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_While ( a , b , c , EXTRA )': (1, 'factory.createWhile(a,b,c,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_With ( a , b , NEW_TYPE_COMMENT ( p , tc ) , EXTRA )': (1, 'factory.createWith(a,b,newTypeComment(tc),startToken.startOffset,endToken.endOffset)'),
    '_PyAST_With ( a , b , NULL , EXTRA )': (1, 'factory.createWith(a,b,null,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_Yield ( a , EXTRA )': (1, 'factory.createYield(a,false,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_YieldFrom ( a , EXTRA )': (1, 'factory.createYield(a,true,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_alias ( a -> v . Name . id , ( b ) ? ( ( expr_ty ) b ) -> v . Name . id : NULL , EXTRA )': (2, 'factory.createAlias(((ExprTy.Name)a).id,b == null ? null :((ExprTy.Name)b).id,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_arg ( a -> v . Name . id , NULL , NULL , EXTRA )': (1, 'factory.createArgument(((ExprTy.Name)a).id,null,null,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_arg ( a -> v . Name . id , b , NULL , EXTRA )': (1, 'factory.createArgument(((ExprTy.Name)a).id,b,null,startToken.startOffset,startToken.endOffset)'),
    '_PyAST_comprehension ( a , b , c , 0 , p -> arena )': (1, 'factory.createComprehension(a,b,c,false,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_withitem ( e , NULL , p -> arena )': (1, 'factory.createWithItem(e,null,startToken.startOffset,endToken.endOffset)'),
    '_PyAST_withitem ( e , t , p -> arena )': (1, 'factory.createWithItem(e,t,startToken.startOffset,endToken.endOffset)'),
    '_PyPegen_add_type_comment_to_arg ( p , a , tc )': (2, 'factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset())'),
    '_PyPegen_augoperator ( p , Add )': (1, 'ExprTy.BinOp.Operator.ADD'),
    '_PyPegen_augoperator ( p , BitAnd )': (1, 'ExprTy.BinOp.Operator.BITAND'),
    '_PyPegen_augoperator ( p , BitOr )': (1, 'ExprTy.BinOp.Operator.BITOR'),
    '_PyPegen_augoperator ( p , BitXor )': (1, 'ExprTy.BinOp.Operator.BITXOR'),
    '_PyPegen_augoperator ( p , Div )': (1, 'ExprTy.BinOp.Operator.DIV'),
    '_PyPegen_augoperator ( p , FloorDiv )': (1, 'ExprTy.BinOp.Operator.FLOORDIV'),
    '_PyPegen_augoperator ( p , LShift )': (1, 'ExprTy.BinOp.Operator.LSHIFT'),
    '_PyPegen_augoperator ( p , Mod )': (1, 'ExprTy.BinOp.Operator.MOD'),
    '_PyPegen_augoperator ( p , Mult )': (1, 'ExprTy.BinOp.Operator.MULT'),
    '_PyPegen_augoperator ( p , Pow )': (1, 'ExprTy.BinOp.Operator.POW'),
    '_PyPegen_augoperator ( p , RShift )': (1, 'ExprTy.BinOp.Operator.RSHIFT'),
    '_PyPegen_augoperator ( p , Sub )': (1, 'ExprTy.BinOp.Operator.SUB'),
    '_PyPegen_check_barry_as_flufl ( p , tok ) ? NULL : tok': (1, 'this.checkBarryAsFlufl(tok)? null : tok'),
    '_PyPegen_class_def_decorators ( p , a , b )': (1, 'factory.createClassDef(b,a,startToken.startOffset,endToken.endOffset)'),
    '_PyPegen_cmpop_expr_pair ( p , Eq , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.EQ,a);'),
    '_PyPegen_cmpop_expr_pair ( p , Gt , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.GT,a)'),
    '_PyPegen_cmpop_expr_pair ( p , GtE , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.GTE,a)'),
    '_PyPegen_cmpop_expr_pair ( p , In , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.IN,a)'),
    '_PyPegen_cmpop_expr_pair ( p , Is , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.IS,a)'),
    '_PyPegen_cmpop_expr_pair ( p , IsNot , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.ISNOT,a)'),
    '_PyPegen_cmpop_expr_pair ( p , Lt , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.LT,a)'),
    '_PyPegen_cmpop_expr_pair ( p , LtE , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.LTE,a)'),
    '_PyPegen_cmpop_expr_pair ( p , NotEq , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.NOTEQ,a)'),
    '_PyPegen_cmpop_expr_pair ( p , NotIn , a )': (1, 'new CmpopExprPair(ExprTy.Compare.Operator.NOTIN,a)'),
    '_PyPegen_collect_call_seqs ( p , a , b , EXTRA )': (1, 'this.collectCallSequences(a,b,startToken.startOffset,endToken.endOffset)'),
    '_PyPegen_concatenate_strings ( p , a )': (1, 'this.concatenateStrings(a)'),
    '_PyPegen_function_def_decorators ( p , d , f )': (1, 'factory.createFunctionDefWithDecorators(f,d)'),
    '_PyPegen_join_names_with_dot ( p , a , b )': (1, 'this.joinNamesWithDot(a,b)'),
    '_PyPegen_join_sequences ( p , a , b )': (1, 'this.join(a,b)'),
    '_PyPegen_key_value_pair ( p , NULL , a )': (1, 'new KeyValuePair(null,a)'),
    '_PyPegen_key_value_pair ( p , NULL , value )': (1, 'new KeyValuePair(null,value)'),
    '_PyPegen_key_value_pair ( p , a , b )': (1, 'new KeyValuePair(a,b)'),
    '_PyPegen_key_value_pair ( p , key , value )': (1, 'new KeyValuePair(key,value)'),
    '_PyPegen_keyword_or_starred ( p , CHECK ( keyword_ty , _PyAST_keyword ( NULL , a , EXTRA ) ) , 1 )': (1, 'new KeywordOrStarred(factory.createKeyword(null,a,startToken.startOffset,endToken.endOffset),true)'),
    '_PyPegen_keyword_or_starred ( p , CHECK ( keyword_ty , _PyAST_keyword ( a -> v . Name . id , b , EXTRA ) ) , 1 )': (2, 'new KeywordOrStarred(factory.createKeyword(((ExprTy.Name)a).id,b,startToken.startOffset,endToken.endOffset),true)'),
    '_PyPegen_keyword_or_starred ( p , a , 0 )': (1, 'new KeywordOrStarred(a,false)'),
    '_PyPegen_make_arguments ( p , NULL , NULL , NULL , NULL , a )': (2, 'factory.createArguments(null,null,null,null,a)'),
    '_PyPegen_make_arguments ( p , NULL , NULL , NULL , a , b )': (2, 'factory.createArguments(null,null,null,a,b)'),
    '_PyPegen_make_arguments ( p , NULL , NULL , a , b , c )': (2, 'factory.createArguments(null,null,a,b,c)'),
    '_PyPegen_make_arguments ( p , NULL , a , NULL , b , c )': (2, 'factory.createArguments(null,a,null,b,c)'),
    '_PyPegen_make_arguments ( p , a , NULL , b , c , d )': (2, 'factory.createArguments(a,null,b,c,d)'),
    '_PyPegen_make_module ( p , a )': (1, 'factory.createModule(a,startToken.startOffset,endToken.endOffset)'),
    '_PyPegen_name_default_pair ( p , a , c , NULL )': (4, 'new NameDefaultPair(factory.createArgument(a.arg,a.annotation,null,a.getStartOffset(),a.getEndOffset()),c)'),
    '_PyPegen_name_default_pair ( p , a , c , tc )': (4, 'new NameDefaultPair(factory.createArgument(a.arg,a.annotation,newTypeComment(tc),a.getStartOffset(),a.getEndOffset()),c)'),
    '_PyPegen_seq_insert_in_front ( p , value , values )': (1, 'this.insertInFront(value,values)'),
    '_PyPegen_seq_insert_in_front ( p , y , z )': (1, 'this.insertInFront(y,z)'),
    '_PyPegen_set_expr_context ( p , a , Del )': (2, 'this.setExprContext(a,ExprContext.Delete)'),
    '_PyPegen_set_expr_context ( p , a , Store )': (5, 'this.setExprContext(a,ExprContext.Store)'),
    '_PyPegen_singleton_seq ( p , a )': (2, 'new SlashWithDefault [ ] {a}'),
    '_PyPegen_slash_with_default ( p , ( asdl_arg_seq * ) a , b )': (4, 'new SlashWithDefault(a,b)'),
    '_PyPegen_star_etc ( p , NULL , NULL , a )': (2, 'new StarEtc(null,null,a)'),
    '_PyPegen_star_etc ( p , NULL , b , c )': (2, 'new StarEtc(null,b,c)'),
    '_PyPegen_star_etc ( p , a , b , c )': (2, 'new StarEtc(a,b,c)'),
    'a': (33, 'a'),
    'args': (1, 'args'),
    'attr': (1, 'attr'),
    'b': (2, 'b'),
    'c': (6, 'c'),
    'd': (3, 'd'),
    'f': (1, 'f'),
    'guard': (1, 'guard'),
    'items': (1, 'items'),
    'k': (1, 'k'),
    'keywords': (1, 'keywords'),
    'pattern': (1, 'pattern'),
    't': (1, 't'),
    'values': (1, 'values'),
    'z': (11, 'z'),

    # TODO
    # 'CHECK_VERSION ( comprehension_ty , 6 , "Async comprehensions are" , _PyAST_comprehension ( a , b , c , 1 , p -> arena ) )': (1, 'CHECK_VERSION(comprehension_ty,6,"Async comprehensions are",_PyAST_comprehension(a,b,c,1,p -> arena))'),
    # 'CHECK_VERSION ( stmt_ty , 10 , "Pattern matching is" , _PyAST_Match ( subject , cases , EXTRA ) )': (1, 'CHECK_VERSION(stmt_ty,10,"Pattern matching is",_PyAST_Match(subject,cases,EXTRA))'),
    # 'CHECK_VERSION ( stmt_ty , 5 , "Async for loops are" , _PyAST_AsyncFor ( t , ex , b , el , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) )': (1, 'CHECK_VERSION(stmt_ty,5,"Async for loops are",_PyAST_AsyncFor(t,ex,b,el,NEW_TYPE_COMMENT(p,tc),EXTRA))'),
    # 'CHECK_VERSION ( stmt_ty , 5 , "Async with statements are" , _PyAST_AsyncWith ( a , b , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) )': (1, 'CHECK_VERSION(stmt_ty,5,"Async with statements are",_PyAST_AsyncWith(a,b,NEW_TYPE_COMMENT(p,tc),EXTRA))'),
    # 'CHECK_VERSION ( stmt_ty , 5 , "Async with statements are" , _PyAST_AsyncWith ( a , b , NULL , EXTRA ) )': (1, 'CHECK_VERSION(stmt_ty,5,"Async with statements are",_PyAST_AsyncWith(a,b,NULL,EXTRA))'),
    # 'CHECK_VERSION ( stmt_ty , 6 , "Variable annotations syntax is" , _PyAST_AnnAssign ( a , b , c , 0 , EXTRA ) )': (1, 'CHECK_VERSION(stmt_ty,6,"Variable annotations syntax is",_PyAST_AnnAssign(a,b,c,0,EXTRA))'),
    # 'CHECK_VERSION ( void * , 10 , "Pattern matching is" , RAISE_SYNTAX_ERROR ( "expected \':\'" ) )': (1, 'CHECK_VERSION(void *,10,"Pattern matching is",this.raiseSyntaxError("expected \':\'"))'),
    # 'RAISE_INDENTATION_ERROR ( "expected an indented block" )': (1, 'RAISE_INDENTATION_ERROR("expected an indented block")'),
    # '_PyAST_Call ( func , NULL , NULL , EXTRA )': (1, '_PyAST_Call(func,NULL,NULL,EXTRA)'),
    # '_PyAST_Call ( func , NULL , keywords , EXTRA )': (1, '_PyAST_Call(func,NULL,keywords,EXTRA)'),
    # '_PyAST_Call ( func , args , NULL , EXTRA )': (1, '_PyAST_Call(func,args,NULL,EXTRA)'),
    # '_PyAST_Call ( func , args , keywords , EXTRA )': (1, '_PyAST_Call(func,args,keywords,EXTRA)'),
    # '_PyAST_Dict ( CHECK ( asdl_expr_seq * , _PyPegen_get_keys ( p , items ) ) , CHECK ( asdl_expr_seq * , _PyPegen_get_values ( p , items ) ) , EXTRA )': (1, '_PyAST_Dict(CHECK(asdl_expr_seq *,_PyPegen_get_keys(p,items)),CHECK(asdl_expr_seq *,_PyPegen_get_values(p,items)),EXTRA)'),
    # '_PyAST_FunctionType ( a , b , p -> arena )': (1, '_PyAST_FunctionType(a,b,p -> arena)'),
    # '_PyAST_List ( b , Store , EXTRA )': (1, '_PyAST_List(b,Store,EXTRA)'),
    # '_PyAST_List ( values , Load , EXTRA )': (1, '_PyAST_List(values,Load,EXTRA)'),
    # '_PyAST_MatchAs ( pattern , target -> v . Name . id , EXTRA )': (1, '_PyAST_MatchAs(pattern,target -> v.Name.id,EXTRA)'),
    # '_PyAST_Name ( CHECK ( PyObject * , _PyPegen_new_identifier ( p , "_" ) ) , Store , EXTRA )': (1, '_PyAST_Name(CHECK(PyObject *,_PyPegen_new_identifier(p,"_")),Store,EXTRA)'),
    # '_PyAST_Tuple ( CHECK ( asdl_expr_seq * , _PyPegen_seq_insert_in_front ( p , value , values ) ) , Load , EXTRA )': (1, '_PyAST_Tuple(CHECK(asdl_expr_seq *,_PyPegen_seq_insert_in_front(p,value,values)),Load,EXTRA)'),
    # '_PyAST_Tuple ( b , Store , EXTRA )': (1, '_PyAST_Tuple(b,Store,EXTRA)'),
    # '_PyAST_Tuple ( values , Load , EXTRA )': (2, '_PyAST_Tuple(values,Load,EXTRA)'),
    # '_PyAST_keyword ( arg -> v . Name . id , value , EXTRA )': (1, '_PyAST_keyword(arg -> v.Name.id,value,EXTRA)'),
    # '_PyAST_match_case ( pattern , guard , body , p -> arena )': (1, '_PyAST_match_case(pattern,guard,body,p -> arena)'),
    # '_PyPegen_arguments_parsing_error ( p , a )': (1, '_PyPegen_arguments_parsing_error(p,a)'),
    # '_PyPegen_interactive_exit ( p )': (1, '_PyPegen_interactive_exit(p)'),
    # '_PyPegen_nonparen_genexp_in_call ( p , a )': (1, '_PyPegen_nonparen_genexp_in_call(p,a)'),
    # '_PyPegen_set_expr_context ( p , name , Store )': (1, '_PyPegen_set_expr_context(p,name,Store)'),
    # 'asdl_seq_LEN ( patterns ) == 1 ? asdl_seq_GET ( patterns , 0 ) : _PyAST_MatchOr ( patterns , EXTRA )': (1, 'asdl_seq_LEN(patterns)== 1 ? asdl_seq_GET(patterns,0): _PyAST_MatchOr(patterns,EXTRA)'),
}

LICENSE = '''/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
'''

IMPORTS = '''
import com.oracle.graal.python.pegparser.sst.*;
import com.oracle.graal.python.pegparser.tokenizer.Token;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
'''

TRAILER = '''
    @Override
    protected SSTNode runParser(InputType inputType) {
        SSTNode result = null;
        switch (inputType) {
            case FILE:
                return file_rule();
            case SINGLE:
                return interactive_rule();
            case EVAL:
                return eval_rule();
            case FUNCTION_TYPE:
                return func_type_rule();
            case FSTRING:
                return fstring_rule();
        }
        return result;
    }
'''


def _check_type(self, ttype: str) -> str:
    self._type_conversions = getattr(self, "_type_conversions", {})
    mappedType = TYPE_MAPPINGS.get(ttype, None)
    if ttype and not mappedType:
        self._type_conversions.setdefault(f"// TODO replacing {ttype} --> Object")
        mappedType = "Object"
    return mappedType


# This class is a Java specific hack to get more specific types
class TypingVisitor(GrammarVisitor):
    def __init__(self, gen):
        self.gen = gen

    def visit_Rule(self, node):
        type = getattr(node, "type", None)
        if not type or type == "asdl_seq*": # heuristic: try to be more precise
            node.type = self.visit(node.rhs)
            if node.is_loop() or node.is_gather():
                node.type += "*"
        return node.type

    def visit_NameLeaf(self, node):
        return self.gen.callmakervisitor.visit_NameLeaf(node).return_type

    def visit_StringLeaf(self, node):
        return self.gen.callmakervisitor.visit_StringLeaf(node).return_type

    def visit_Rhs(self, node):
        types = getattr(node, "types", set())
        if not types:
            for alt in node.alts:
                typ = self.visit(alt)
                if typ:
                    types.add(typ)
            node.types = types
        if len(types) == 1:
            return next(iter(types))
        else:
            return "Object"

    def visit_Alt(self, node):
        types = getattr(node, "types", set())
        if not types:
            for item in node.items:
                typ = self.visit(item)
                if typ:
                    types.add(typ)
            if len(types) > 1:
                types.discard("Token*") # heuristic. when tokens are in there, they are usually dropped
            if len(types) == 2:
                # might be a pair for gathering
                typ1, typ2 = sorted(types, key=lambda x: x.count('*'))
                if (f"{typ1}*" == typ2 or
                    typ2.startswith(f"asdl_{typ1.replace('_ty', '')}_seq")):
                    types = {typ2.replace("**", "*")}
            node.types = types
        if len(types) == 1:
            return next(iter(types))
        else:
            return "Object"

    def visit_NamedItem(self, node):
        if not getattr(node, "type", None):
            node.type = self.visit(node.item)
        return node.type

    def visit_Forced(self, node):
        if not getattr(node, "type", None):
            node.type = self.visit(node.node)
        return node.type

    def visit_PositiveLookahead(self, node):
        return self.visit(node.node)

    def visit_NegativeLookahead(self, node):
        return self.visit(node.node)

    def visit_Opt(self, node):
        return self.visit(node.node)

    def visit_Repeat0(self, node):
        if not getattr(node, "type", None):
            node_type = self.visit(node.node)
            node.type = f"{node_type}*"
        return node.type

    def visit_Repeat1(self, node):
        if not getattr(node, "type", None):
            node_type = self.visit(node.node)
            node.type = f"{node_type}*"
        return node.type

    def visit_Gather(self, node):
        if not getattr(node, "type", None):
            node_type = self.visit(node.node)
            node.type = f"{node_type}*"
        return node.type

    def visit_Group(self, node):
        return self.visit(node.rhs)

    def visit_Cut(self, node):
        return None


class JavaCallMakerVisitor(GrammarVisitor):
    def __init__(self, parser_generator: ParserGenerator,
            exact_tokens: Dict[str, int],
            non_exact_tokens: Set[str],
            printFnc):
        self.gen = parser_generator
        self.exact_tokens = exact_tokens
        self.non_exact_tokens = non_exact_tokens
        self.cache: Dict[Any, FunctionCall] = {}
        self.keyword_cache: Dict[str, int] = {}
        self.soft_keywords: Set[str] = set()
        self.print = printFnc

    def keyword_helper(self, keyword: str) -> FunctionCall:
        if keyword not in self.keyword_cache:
            self.keyword_cache[keyword] = self.gen.keyword_type()
        return FunctionCall(
            assigned_variable="_keyword",
            function="expect",
            arguments=[self.keyword_cache[keyword]],
            return_type="Token*",
            nodetype=NodeTypes.KEYWORD,
            comment=f"token='{keyword}'",
        )

    def soft_keyword_helper(self, value: str) -> FunctionCall:
        self.soft_keywords.add(value.replace('"', ""))
        return FunctionCall(
            assigned_variable="_keyword",
            function="expect_SOFT_KEYWORD",
            arguments=[value],
            return_type="expr_ty",
            nodetype=NodeTypes.SOFT_KEYWORD,
            comment=f"soft_keyword='{value}'",
        )

    def visit_NameLeaf(self, node: NameLeaf) -> FunctionCall:
        name = node.value
        if name in self.non_exact_tokens:
            if name in BASE_NODETYPES:
                return FunctionCall(
                    assigned_variable=f"{name.lower()}_var",
                    function = f"{name.lower()}_token",
                    arguments=[],
                    nodetype=BASE_NODETYPES[name][0],
                    return_type=BASE_NODETYPES[name][1],
                    comment=name,
                )
            return FunctionCall(
                assigned_variable=f"{name.lower()}_var",
                function=f"expect",
                arguments=["Token.Kind." + name],
                nodetype=NodeTypes.GENERIC_TOKEN,
                return_type="Token*",
                comment=f"token='{name}'",
            )
        type = None
        rule = self.gen.all_rules.get(name.lower())
        if rule is not None:
            type = self.gen.typingvisitor.visit(rule) # Java type change

        return FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type=type,
            comment=f"{node}",
        )

    def visit_StringLeaf(self, node: StringLeaf) -> FunctionCall:
        val = ast.literal_eval(node.value)
        if re.match(r"[a-zA-Z_]\w*\Z", val):  # This is a keyword
            if node.value.endswith("'"):
                return self.keyword_helper(val)
            else:
                return self.soft_keyword_helper(node.value)
        else:
            assert val in self.exact_tokens, f"{node.value} is not a known literal"
            type = self.exact_tokens[val]
            return FunctionCall(
                assigned_variable="_literal",
                function=f"expect",
                arguments=[type],
                nodetype=NodeTypes.GENERIC_TOKEN,
                return_type="Token*",
                comment=f"token='{val}'",
            )

    def visit_Rhs(self, node: Rhs) -> FunctionCall:
        if node in self.cache:
            return self.cache[node]
        # TODO can we inline generated calls?
        name = self.gen.name_node(node)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            # Begin Java type hack
            return_type=self.gen.typingvisitor.visit(node),
            # End Java type hack
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_NamedItem(self, node: NamedItem) -> FunctionCall:
        call = self.generate_call(node.item)
        if node.name:
            call.assigned_variable = node.name
        if node.type:
            call.assigned_variable_type = node.type
        return call

    def lookahead_call_helper(self, node: Lookahead, positive: str) -> FunctionCall:
        # The c_generator passes function pointers, something we cannot do in
        # Java. Instead, we generate helper functions for the function names
        # passed in. Instead of the C-style overload suffixes
        # (lookahead_with_name, _with_string, _with_int), we set the
        # call.argtypes, which are consumed later when we generate the
        # gen.lookahead_functions
        call = self.generate_call(node.node)
        fname = f"genLookahead_{call.function}"
        if fname not in self.gen.lookahead_functions:
            self.gen.lookahead_functions[fname] = call
            call.argtypes = ()
        if call.nodetype == NodeTypes.NAME_TOKEN:
            return FunctionCall(
                function=fname,
                arguments=[positive, *call.arguments],
                return_type="boolean",
            )
        elif call.nodetype == NodeTypes.SOFT_KEYWORD:
            call.argtypes = ("String",)
            return FunctionCall(
                function=fname,
                arguments=[positive, *call.arguments],
                return_type="boolean",
            )
        elif call.nodetype in {NodeTypes.GENERIC_TOKEN, NodeTypes.KEYWORD}:
            call.argtypes = ("int",)
            return FunctionCall(
                function=fname,
                arguments=[positive, *call.arguments],
                return_type="boolean",
                comment=f"token={node.node}",
            )
        else:
            assert len(call.arguments) == 0
            return FunctionCall(
                function=fname,
                arguments=[positive, *call.arguments],
                return_type="boolean",
            )

    def visit_PositiveLookahead(self, node: PositiveLookahead) -> FunctionCall:
        return self.lookahead_call_helper(node, "true")

    def visit_NegativeLookahead(self, node: NegativeLookahead) -> FunctionCall:
        return self.lookahead_call_helper(node, "false")

    def visit_Forced(self, node: Forced) -> FunctionCall:
        call = self.generate_call(node.node)
        if call.nodetype == NodeTypes.GENERIC_TOKEN:
            # val = ast.literal_eval(node.node.value)
            val = eval(node.node.value) # FIXME: this is unsafe
            assert val in self.exact_tokens, f"{node.value} is not a known literal"
            type = self.exact_tokens[val]
            return FunctionCall(
                assigned_variable="_literal",
                function=f"expect_forced_token",
                arguments=[type, f'"{val}"'],
                nodetype=NodeTypes.GENERIC_TOKEN,
                return_type="Token*",
                comment=f"forced_token='{val}'",
            )
        else:
            raise NotImplementedError(
                f"Forced tokens don't work with {call.nodetype} tokens")

    def visit_Opt(self, node: Opt) -> FunctionCall:
        call = self.generate_call(node.node)
        return FunctionCall(
            assigned_variable="_opt_var",
            function=call.function,
            arguments=call.arguments,
            # Begin Java type hack
            return_type=call.return_type,
            # End Java type hack
            force_true=True,
            comment=f"{node}",
        )

    def visit_Repeat0(self, node: Repeat0) -> FunctionCall:
        if node in self.cache:
            return self.cache[node]
        name = self.gen.name_loop(node.node, False)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type=self.gen.typingvisitor.visit(node), # Java type hack
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_Repeat1(self, node: Repeat1) -> FunctionCall:
        if node in self.cache:
            return self.cache[node]
        name = self.gen.name_loop(node.node, True)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type=self.gen.typingvisitor.visit(node), # Java type hack
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_Gather(self, node: Gather) -> FunctionCall:
        if node in self.cache:
            return self.cache[node]
        name = self.gen.name_gather(node)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type=self.gen.typingvisitor.visit(node), # Java type hack
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_Group(self, node: Group) -> FunctionCall:
        return self.generate_call(node.rhs)

    def visit_Cut(self, node: Cut) -> FunctionCall:
        return FunctionCall(
            assigned_variable="_cut_var",
            return_type="int",
            function="1",
            nodetype=NodeTypes.CUT_OPERATOR,
        )

    def generate_call(self, node: Any) -> FunctionCall:
        return super().visit(node)


class ActionMapperVisitor(GrammarVisitor):

    def __init__(self):
        self.action_counter = Counter()
        self.current_rule = None

    def visit_Rule(self, node: Rule):
        self.current_rule = node
        self.generic_visit(node)

    def visit_Alt(self, node: Alt):
        if node.action:
            c_action = str(node.action)
            if c_action not in ACTION_MAPPINGS:
                #  TODO raise an exception
                # raise ValueError(f"Missing mapping for action '{c_action}' in rule \"{self.current_rule.name}: with rhs {node}\"")
                java_action = f"@{c_action}"
            else:
                java_action = ACTION_MAPPINGS[c_action][1]
                self.action_counter.update([c_action])
            node.action = java_action
        self.generic_visit(node)

    def map_actions(self, grammar):
        self.visit(grammar)
        for c_action, (expected_count, _) in ACTION_MAPPINGS.items():
            if expected_count != self.action_counter[c_action]:
                raise ValueError(f"Expected to find {expected_count} occurrences of action '{c_action}', but {self.action_counter[c_action]} were found")


class JavaParserGenerator(ParserGenerator, GrammarVisitor):
    def __init__(
        self,
        grammar: grammar.Grammar,
        tokens: Dict[int, str],
        exact_tokens: Dict[str, int],
        non_exact_tokens: Set[str],
        file: Optional[IO[Text]],
        debug: bool = True,
        skip_actions: bool = False,
    ):
        super().__init__(grammar, tokens, file)
        self.typingvisitor = TypingVisitor(self) # Java type hack
        self.callmakervisitor = JavaCallMakerVisitor(self, exact_tokens, non_exact_tokens, self.print)
        self.lookahead_functions: Dict[str, FunctionCall] = {}
        self.debug = debug
        self.skip_actions = skip_actions
        self.goto_targets = []
        self._collected_type = []
        ActionMapperVisitor().map_actions(grammar)

    def add_level(self) -> None:
        if self.debug:
            self.print("level++;")

    def remove_level(self) -> None:
        if self.debug:
            self.print("level--;")

    def add_return(self, ret_val: str) -> None:
        self.remove_level()
        self.print(f"return {ret_val};")

    def unique_varname(self, name: str = "tmpvar") -> str:
        new_var = name + "_" + str(self._varname_counter)
        self._varname_counter += 1
        return new_var

    def call_with_errorcheck_return(self, call_text: str, returnval: str) -> None:
        # error's in Java come via exceptions, which we just let propagate
        self.print(f"{call_text};")

    def call_with_errorcheck_goto(self, call_text: str, goto_target: str) -> None:
        "not used in Java"
        pass

    def out_of_memory_return(self, expr: str, cleanup_code: Optional[str] = None,) -> None:
        "not used in Java"
        pass

    def out_of_memory_goto(self, expr: str, goto_target: str) -> None:
        "not used in Java"
        pass

    def generate(self, filename: str) -> None:
        self.collect_todo()
        # Java specific stuff
        self.print(LICENSE)
        self.print("// Checkstyle: stop")
        self.print("// JaCoCo Exclude")
        self.print("//@formatter:off")
        self.print(f"// Generated from {filename} by pegen")
        self.print("package com.oracle.graal.python.pegparser;")
        self.print(IMPORTS)
        className = os.path.splitext(os.path.basename(self.file.name))[0]
        self.print('@SuppressWarnings({"all", "cast"})')
        self.print("public final class %s extends AbstractParser {" % className)
        # Java needs a few fields declarations. Also, we're now in a class
        self.level += 1
        self.print()
        # Now back to c generator analogue
        self._setup_keywords()
        self._setup_soft_keywords()
        for i, (rulename, rule) in enumerate(self.todo.items(), 1000):
            comment = "  // Left-recursive" if rule.left_recursive else ""
            self.print(f"private static final int {rulename.upper()}_ID = {i};{comment}")
        self.print()
        # Java needs a constructor
        self.print("public %s(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser) {" % className)
        with self.indent():
            self.print("super(tokenizer, factory, fexprParser);")
        self.print("}" )
        self.print("public %s(ParserTokenizer tokenizer, NodeFactory factory, FExprParser fexprParser, ParserErrorCallback errorCb) {" % className)
        with self.indent():
            self.print("super(tokenizer, factory, fexprParser, errorCb);")
        self.print("}" )
        # we don't need the C declarations, so straight to the rule functions as in c_generator
        while self.todo:
            for rulename, rule in list(self.todo.items()):
                del self.todo[rulename]
                self.print()
                if rule.left_recursive:
                    self.print("// Left-recursive")
                self.visit(rule)

        self._generate_lookahead_methods()
        for todo in getattr(self, "_type_conversions", {}).keys():
            self.print(todo)
        self.print(TRAILER)
        self.level -= 1
        self.print("}")

    def _group_keywords_by_length(self) -> Dict[int, List[Tuple[str, int]]]:
        groups: Dict[int, List[Tuple[str, int]]] = {}
        for keyword_str, keyword_type in self.callmakervisitor.keyword_cache.items():
            length = len(keyword_str)
            if length in groups:
                groups[length].append((keyword_str, keyword_type))
            else:
                groups[length] = [(keyword_str, keyword_type)]
        return groups

    def _setup_keywords(self) -> None:
        keyword_cache = self.callmakervisitor.keyword_cache
        n_keyword_lists = (
            len(max(keyword_cache.keys(), key=len)) + 1 if len(keyword_cache) > 0 else 0
        )
        groups = self._group_keywords_by_length()
        self.print("private static final Object[][][] reservedKeywords = new Object[][][]{")
        with self.indent():
            num_groups = max(groups) + 1 if groups else 1
            for keywords_length in range(num_groups):
                if keywords_length not in groups.keys():
                    self.print("null,")
                else:
                    self.print("{")
                    with self.indent():
                        for keyword_str, keyword_type in groups[keywords_length]:
                            self.print(f'{{"{keyword_str}", {keyword_type}}},')
                    self.print("},")
        self.print("};")
        self.print("@Override")
        self.print("protected Object[][][] getReservedKeywords() { return reservedKeywords; }")

    def _setup_soft_keywords(self) -> None:
        soft_keywords = sorted(self.callmakervisitor.soft_keywords)
        self.print("private static final String[] softKeywords = new String[]{")
        with self.indent():
            for keyword in soft_keywords:
                self.print(f'"{keyword}",')
        self.print("};")
        self.print("@Override")
        self.print("protected String[] getSoftKeywords() { return softKeywords; }")

    def _set_up_token_start_metadata_extraction(self) -> None:
        self.print("Token startToken = getAndInitializeToken();");

    def _set_up_token_end_metadata_extraction(self) -> None:
        self.print("Token endToken = getLastNonWhitespaceToken();")
        self.print("if (endToken == null) {")
        with self.indent():
            self.add_return("null")
        self.print("}")

    def _check_for_errors(self) -> None:
        self.print("if (errorIndicator) {")
        with self.indent():
            self.remove_level();
            self.print("return null;")
        self.print("}")

    def _set_up_rule_memoization(self, node: Rule, result_type: str) -> None:
        self.print("{")
        with self.indent():
            self.add_level()
            self.print("int _mark = mark();")
            self.print(f"Object _res = null;")
            self.print(f"if (cache.hasResult(_mark, {node.name.upper()}_ID)) {{")
            with self.indent():
                self.print(f"_res = cache.getResult(_mark, {node.name.upper()}_ID);")
                self.add_return(f"({result_type})_res")
            self.print("}")
            self.print("int _resmark = mark();")
            self.print("while (true) {")
            with self.indent():
                self.call_with_errorcheck_return(
                    f"cache.putResult(_mark, {node.name.upper()}_ID, _res)", "_res"
                )
                self.print("reset(_mark);")
                self.print(f"Object _raw = {node.name}_raw();")
                self.print("if (_raw == null || mark() <= _resmark)")
                with self.indent():
                    self.print("break;")
                self.print(f"_resmark = mark();")
                self.print("_res = _raw;")
            self.print("}")
            self.print(f"reset(_resmark);")
            self.add_return(f"({result_type})_res")
        self.print("}")
        self.print(f"private {result_type} {node.name}_raw()")

    def _should_memoize(self, node: Rule) -> bool:
        return not node.left_recursive

    def _handle_default_rule_body(self, node: Rule, rhs: Rhs, result_type: str) -> None:
        memoize = self._should_memoize(node)

        with self.indent():
            self.add_level()
            self._check_for_errors()
            self.print("int _mark = mark();")
            self.print(f"Object _res = null;")
            if memoize:
                self.print(f"if (cache.hasResult(_mark, {node.name.upper()}_ID)) {{")
                with self.indent():
                    self.print(f"_res = ({result_type})cache.getResult(_mark, {node.name.upper()}_ID);")
                    self.add_return(f"({result_type})_res")
                self.print("}")
            # Java change: set up the goto target via a lambda
            def _goto_target():
                if memoize:
                    self.print(f"cache.putResult(_mark, {node.name.upper()}_ID, _res);")
                self.add_return(f"({result_type})_res")
            self.goto_targets.append(_goto_target)
            # End goto target setup
            if any(alt.action and "startToken" in alt.action for alt in rhs.alts):
                self._set_up_token_start_metadata_extraction()
            self.visit(
                rhs, is_loop=False, is_gather=node.is_gather(), rulename=node.name,
            )
            self.printDebug(f'debugMessageln("Fail at %d: {node.name}", _mark);')
            self.print("_res = null;")
        with self.indent():
            # insert and pop the goto target
            self.goto_targets.pop()()

    def _handle_loop_rule_body(self, node: Rule, rhs: Rhs) -> None:
        memoize = self._should_memoize(node)
        is_repeat1 = node.name.startswith("_loop1")

        with self.indent():
            self.add_level()
            self._check_for_errors()
            self.print("Object _res = null;")
            self.print("int _mark = mark();")
            if memoize:
                self.print(f"if (cache.hasResult(_mark, {node.name.upper()}_ID)) {{")
                with self.indent():
                    self.print(f"_res = cache.getResult(_mark, {node.name.upper()}_ID);")
                    self.add_return(f"({self._collected_type[-1]}[])_res")
                self.print("}")
            self.print("int _start_mark = mark();")
            self.print(f"List<{self._collected_type[-1]}> _children = new ArrayList<>();")
            self.out_of_memory_return(f"!_children")
            self.print("int _children_capacity = 1;")
            self.print("int _n = 0;")
            if any(alt.action and "startToken" in alt.action for alt in rhs.alts):
                self._set_up_token_start_metadata_extraction()
            self.visit(
                rhs, is_loop=True, is_gather=node.is_gather(), rulename=node.name,
            )
            if is_repeat1:
                self.print("if (_children.size() == 0) {")
                with self.indent():
                    self.add_return("null")
                self.print("}")
            self.print(f"{self._collected_type[-1]}[] _seq = _children.toArray(new {self._collected_type[-1]}[_children.size()]);")
            self.out_of_memory_return(f"!_seq", cleanup_code="PyMem_Free(_children);")
            if node.name:
                self.print(f"cache.putResult(_start_mark, {node.name.upper()}_ID, _seq);")
            self.add_return("_seq")

    def visit_Rule(self, node: Rule) -> None:
        is_loop = node.is_loop()
        is_gather = node.is_gather()
        rhs = node.flatten()
        if is_loop or is_gather:
            # Hacky way to get more specific Java type
            self._collected_type.append(_check_type(self, self.typingvisitor.visit(node)).replace("[]", ""))
            # end of hacky way to get better Java type
            result_type = f"{self._collected_type[-1]}[]"
        elif node.type:
            result_type = _check_type(self, node.type)
        else:
            result_type = _check_type(self, self.typingvisitor.visit(node)) # Java type hack

        for line in str(node).splitlines():
            self.print(f"// {line}")
        if node.left_recursive and node.leader:
            # No need to declare in Java
            pass

        self.print(f"public {result_type} {node.name}_rule()")

        if node.left_recursive and node.leader:
            self._set_up_rule_memoization(node, result_type)

        self.print("{")
        if is_loop:
            self._handle_loop_rule_body(node, rhs)
        else:
            self._handle_default_rule_body(node, rhs, result_type)
        # Java type stack pop
        if is_loop or is_gather:
            self._collected_type.pop()
        self.print("}")

    def visit_NamedItem(self, node: NamedItem) -> None:
        call = self.callmakervisitor.generate_call(node)
        if call.assigned_variable:
            call.assigned_variable = self.dedupe(call.assigned_variable)
        self.print(call)

    def visit_Rhs(
        self, node: Rhs, is_loop: bool, is_gather: bool, rulename: Optional[str]
    ) -> None:
        if is_loop:
            assert len(node.alts) == 1
        for alt in node.alts:
            self.visit(alt, is_loop=is_loop, is_gather=is_gather, rulename=rulename)

    def join_conditions(self, keyword: str, node: Any) -> None:
        self.print(f"{keyword} (")
        with self.indent():
            first = True
            for item in node.items:
                if first:
                    first = False
                else:
                    self.print("&&")
                self.visit(item)
        self.print(")")

    def emit_action(self, node: Alt) -> None:
        action = str(node.action)
        if action[0] == '@':
            # TODO remove once all actions are translated to Java
            self.print(f"// TODO: node.action: {action[1:]}")
            self.print(
                f'''debugMessageln("\033[33;5;7m!!! TODO: Convert {action[1:].replace('"', "'")} to Java !!!\033[0m");'''
            )
            self.print(f"_res = null;")
        else:
            self.print(f"_res = {action};")

        self.printDebug(
            f'''debugMessageln("Hit with action [%d-%d]: %s", _mark, mark(), "{str(node).replace('"', "'")}");'''
        )

    def emit_default_action(self, is_gather: bool, node: Alt) -> None:
        if len(self.local_variable_names) > 1:
            if is_gather:
                assert len(self.local_variable_names) == 2
                element_type = _check_type(self, node.items[0].type)
                self.print(
                    f"_res = insertInFront("
                    f"{self.local_variable_names[0]}, {self.local_variable_names[1]}, {element_type}.class);"
                )
            else:
                self.printDebug(
                    f'debugMessageln("Hit without action [%d:%d]: %s", _mark, mark(), "{node}");'
                )
                self.print(
                    f"_res = dummyName({', '.join(self.local_variable_names)});"
                )
        else:
            self.printDebug(
                f'debugMessageln("Hit with default action [%d:%d]: %s", _mark, mark(), "{node}");'
            )
            self.print(f"_res = {self.local_variable_names[0]};")

    def emit_dummy_action(self) -> None:
        self.print("_res = dummyName();")

    def handle_alt_normal(self, node: Alt, is_gather: bool, rulename: Optional[str]) -> None:
        self.join_conditions(keyword="if", node=node)
        self.print("{")
        # We have parsed successfully all the conditions for the option.
        with self.indent():
            node_str = str(node).replace('"', '\\"')
            self.printDebug(
                f'debugMessageln("%d {rulename}[%d-%d]: %s succeeded!", level, _mark, mark(), "{node_str}");'
            )
            # Prepare to emmit the rule action and do so
            if node.action and "endToken" in node.action:
                self._set_up_token_end_metadata_extraction()
            if self.skip_actions:
                self.emit_dummy_action()
            elif node.action:
                self.emit_action(node)
            else:
                self.emit_default_action(is_gather, node)

            # As the current option has parsed correctly, do not continue with the rest.
            # Java change: C uses a goto here, we have the goto target code in a lambda
            self.goto_targets[-1]()
        self.print("}")

    def handle_alt_loop(self, node: Alt, is_gather: bool, rulename: Optional[str]) -> None:
        # Condition of the main body of the alternative
        self.join_conditions(keyword="while", node=node)
        self.print("{")
        # We have parsed successfully one item!
        with self.indent():
            # Prepare to emit the rule action and do so
            if node.action and "endToken" in node.action:
                self._set_up_token_end_metadata_extraction()
            if self.skip_actions:
                self.emit_dummy_action()
            elif node.action:
                self.emit_action(node)
            else:
                self.emit_default_action(is_gather, node)

            # Add the result of rule to the temporary buffer of children. This buffer
            # will populate later an asdl_seq with all elements to return.
            self.print(f"if (_res instanceof {self._collected_type[-1]}) {{")
            self.print(f"    _children.add(({self._collected_type[-1]})_res);")
            self.print("} else {")
            self.print(f"    _children.addAll(Arrays.asList(({self._collected_type[-1]}[])_res));")
            self.print("}")
            self.print("_mark = mark();")
        self.print("}")

    def visit_Alt(
        self, node: Alt, is_loop: bool, is_gather: bool, rulename: Optional[str]
    ) -> None:
        if len(node.items) == 1 and str(node.items[0]).startswith('invalid_'):
            self.print(f"if (callInvalidRules) {{ // {node}")
        else:
            self.print(f"{{ // {node}")
        with self.indent():
            self._check_for_errors()
            node_str = str(node).replace('"', '\\"')
            self.printDebug(
                f'debugMessageln("%d> {rulename}[%d-%d]: %s", level, _mark, mark(), "{node_str}");'
            )
            # Prepare variable declarations for the alternative
            vars = self.collect_vars(node)
            for v, var_type in sorted(item for item in vars.items() if item[0] is not None):
                if not var_type:
                    var_type = "Object "
                else:
                    var_type += " "
                if v == "_cut_var":
                    v += " = 0"  # cut_var must be initialized
                self.print(f"{var_type}{v};")

            with self.local_variable_context():
                if is_loop:
                    self.handle_alt_loop(node, is_gather, rulename)
                else:
                    self.handle_alt_normal(node, is_gather, rulename)

            self.print("reset(_mark);")
            node_str = str(node).replace('"', '\\"')
            self.printDebug(
                f"debugMessageln(\"%d%s {rulename}[%d-%d]: %s failed!\", level,\n"
                f'                  errorIndicator ? " ERROR!": "-", _mark, mark(), "{node_str}");'
            )
            if "_cut_var" in vars:
                self.print("if (_cut_var != 0) {")
                with self.indent():
                    self.add_return("null")
                self.print("}")
        self.print("}")

    def collect_vars(self, node: Alt) -> Dict[Optional[str], Optional[str]]:
        types = {}
        with self.local_variable_context():
            for item in node.items:
                name, type = self.add_var(item)
                types[name] = _check_type(self, type)
        return types

    def add_var(self, node: NamedItem) -> Tuple[Optional[str], Optional[str]]:
        call = self.callmakervisitor.generate_call(node.item)
        name = node.name if node.name else call.assigned_variable
        if name is not None:
            name = self.dedupe(name)
        return_type = call.return_type if node.type is None else node.type
        return name, return_type

    # Java generator additions
    def _generate_lookahead_methods(self):
        self.print()
        self.print("// lookahead methods generated")
        for name in self.lookahead_functions:
            call = self.lookahead_functions[name];
            if call.argtypes:
                args = ", ".join(map(lambda e: f"{e[1]} arg{e[0]}", enumerate(call.argtypes)))
                assert len(call.arguments) == len(call.argtypes)
                self.print(f"private boolean {name}(boolean match, {args}) {{")
            else:
                assert not call.arguments
                self.print(f"private boolean {name}(boolean match) {{")

            with self.indent():
                self.print("int tmpPos = mark();")
                if call.return_type:
                    return_type = _check_type(self, call.return_type)
                else:
                    return_type = "Object"
                if call.arguments:
                    args = ", ".join(map(lambda e: f"arg{e[0]}", enumerate(call.argtypes)))
                    self.print(f"{return_type} result = {call.function}({args});")
                else:
                    self.print(f"{return_type} result = {call.function}();")
                self.print("reset(tmpPos);")
                self.print(f"return (result != null) == match;")
            self.print("}")
            self.print()

    def printDebug(self, *args):
        if self.debug:
            self.print(*args)
