#!/usr/bin/env python3
# encoding: UTF-8
# Copyright (c) 2021, Oracle and/or its affiliates.
# Copyright (C) 1996-2021 Python Software Foundation
#
# Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2

"""This is heavily based on the C generator, and we try to keep it as similar as
possible to make updates and maintenance easy. Where we deviate in non obvious
ways, we add comments."""

import os
import ast
import re

from dataclasses import field, dataclass

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
                var_type = _check_type(self, self.assigned_variable_type);
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
                typ1, typ2 = sorted(types)
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
        license = self.grammar.metas.get("license")
        if license:
            self.print(license)
        self.print("// Checkstyle: stop")
        self.print("// JaCoCo Exclude")
        self.print("//@formatter:off")
        self.print(f"// Generated from {filename} by pegen")
        package = self.grammar.metas.get("package")
        if package:
            self.print("package %s;" % package.strip("\n"))
        imports = self.grammar.metas.get("imports")
        if imports:
            self.print(imports)
        className = os.path.splitext(os.path.basename(self.file.name))[0]
        self.print('@SuppressWarnings({"all", "cast"})')
        self.print("public final class %s extends AbstractParser {" % className)
        # Java needs a few fields declarations. Also, we're now in a class
        self.level += 1
        self.print()
        self.print("// parser fields")
        fields = self.grammar.metas.get("parser_fields")
        if fields:
            self.print(fields)
        # Now back to c generator analogue
        self._setup_keywords()
        self._setup_soft_keywords()
        for i, (rulename, rule) in enumerate(self.todo.items(), 1000):
            comment = "  // Left-recursive" if rule.left_recursive else ""
            self.print(f"private static final int {rulename.upper()}_ID = {i};{comment}")
        self.print()
        # Java needs a constructor
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
        # we don't need the C trailer, but we have our own final things to generate and close the class
        self._generate_lookahead_methods()
        for todo in getattr(self, "_type_conversions", {}).keys():
            self.print(todo)
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
        "not used in Java"
        pass

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

    def emit_action(self, node: Alt, cleanup_code: Optional[str] = None) -> None:
        node_action = re.sub(r" ?([\.\(\),]) ?", r"\1", str(node.action))

        # TODO this condition filter c action now. Should be removed after the grammar contains only java actions
        if (node_action.startswith('factory')
            or node_action.startswith('new')
            or "ExprTy." in node_action
            or 'SSTNode' in node_action
            or len(node_action) == 1
            or node_action.startswith('finish')
            or node_action == "elem"
            or re.match("(\\([^()*]+\\))?this.", node_action)):
            self.print(f"_res = {node_action};")
        else:
            self.print(f"// TODO: node.action: {node.action}")
            self.print(
                f'''debugMessageln("\033[33;5;7m!!! TODO: Convert {node.action.replace('"', "'")} to Java !!!\033[0m");'''
            )
            self.print(f"_res = null;")

        self.printDebug(
            f'''debugMessageln("Hit with action [%d-%d]: %s", _mark, mark(), "{str(node).replace('"', "'")}");'''
        )

    def emit_default_action(self, is_gather: bool, node: Alt) -> None:
        if len(self.local_variable_names) > 1:
            if is_gather:
                assert len(self.local_variable_names) == 2
                self.print(
                    f"_res = insertInFront("
                    f"{self.local_variable_names[0]}, {self.local_variable_names[1]});"
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
                f'                  "-", _mark, mark(), "{node_str}");'
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
