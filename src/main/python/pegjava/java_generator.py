#!/usr/bin/env python3
#encoding: UTF-8

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
    "NAME": NodeTypes.NAME_TOKEN,
    "NUMBER": NodeTypes.NUMBER_TOKEN,
    "STRING": NodeTypes.STRING_TOKEN,
    "SOFT_KEYWORD": NodeTypes.SOFT_KEYWORD,
}


@dataclass
class FunctionCall:
    function: str
    arguments: List[Any] = field(default_factory=list)
    assigned_variable: Optional[str] = None
    assigned_variable_type: Optional[str] = None
    return_type: Optional[str] = None
    nodetype: Optional[NodeTypes] = None
    force_true: bool = False
    comment: Optional[str] = None

    def __str__(self) -> str:
        parts = []
        parts.append(self.function)
        if self.arguments:
            parts.append(f"({', '.join(map(str, self.arguments))})")
        else:
            parts.append("()")

        ft_part_start = ""
        ft_part_end = ""
        if self.force_true:
            ft_part_start= "("
            ft_part_end = f" || {self.assigned_variable} == null)"

        if self.assigned_variable:
            if self.assigned_variable_type:
                var_type = _check_type(self, self.assigned_variable_type);
                parts = [ft_part_start, "(", self.assigned_variable, " = ", '(', var_type, ')', *parts, ") != null", ft_part_end]
            else:
                parts = [ft_part_start, "(", self.assigned_variable, " = ", *parts, ") != null", ft_part_end]
        if self.comment:
            parts.append(f"  // {self.comment}")
        return "".join(parts)

# TODO this is temporary solution until all types in the grammar will not be java types
def _check_type(self, ttype: str) -> str:
    if ttype and type(ttype) == str and "Token" != ttype and not "SSTNode" in ttype:
        if "[]" in ttype or "*" in ttype:
            if hasattr(self, "print"):
                self.print(f"// TODO replacing {ttype} --> SSTNode[]")
            return "SSTNode[]"
        if hasattr(self, "print"):
            self.print(f"// TODO replacing {ttype} --> SSTNode")
        return "SSTNode"
    elif "SSTNode*" == ttype:
        if hasattr(self, "print"):
            self.print(f"// TODO replacing {ttype} --> SSTNode[]")
        return "SSTNode[]"
    return ttype

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
            arguments=[f"KEYWORD_{keyword.upper()}"],
            return_type="Token",
            nodetype=NodeTypes.KEYWORD,
            comment=f"token='{keyword}'",
        )

    def soft_keyword_helper(self, value: str) -> FunctionCall:
        self.soft_keywords.add(value.replace('"', ""))
        return FunctionCall(
            assigned_variable="_keyword",
            function="expect",
            arguments=[value],
            return_type="Token",
            nodetype=NodeTypes.SOFT_KEYWORD,
            comment=f"soft_keyword='{value}'",
        )

    def visit_NameLeaf(self, node: NameLeaf) -> FunctionCall:
        self.print(f"// REMOVE visiting JavaCallMakerVisitor.visit_NameLeaf({node}) - should work")
        name = node.value
        self.print(f"// name = {name}")
        if name in self.non_exact_tokens:
            if name in BASE_NODETYPES:
                if name == "SOFT_KEYWORD":
                    return FunctionCall(
                        assigned_variable=f"{name.lower()}_var",
                        function = "softKeywordToken",
                        arguments=[],
                        nodetype=BASE_NODETYPES[name],
                        return_type="Token",  # TODO check the type
                        comment=name,
                    )
                return FunctionCall(
                    assigned_variable=f"{name.lower()}_var",
                    #function=f"_PyPegen_{name.lower()}_token",
                    function = "expect",
                    #arguments=None,
                    arguments=["Token.Kind." + name],
                    nodetype=BASE_NODETYPES[name],
                    return_type="Token",  # TODO check the type
                    comment=name,
                )
            return FunctionCall(
                assigned_variable=f"{name.lower()}_var",
                function=f"expect",
                arguments=["Token.Kind." + name],
                nodetype=NodeTypes.GENERIC_TOKEN,
                return_type="Token",
                comment=f"token='{name}'",
            )
        type = None
        rule = self.gen.all_rules.get(name.lower())
        if rule  is not None:
            type = "SSTNode[]" if rule.is_loop() or rule.is_gather() else rule.type  # TODO check the type

        if type and type.endswith('*'):
            type = type.replace("*", "[]")


        return FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type=_check_type(self, type),
            comment=f"{node}",
        )

    def visit_StringLeaf(self, node: StringLeaf) -> FunctionCall:
        self.print(f"// REMOVE visiting JavaCallMakerVisitor.visit_StringLeaf({node})")
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
                arguments=["Token.Kind." + self.gen.tokens[type]],
                nodetype=NodeTypes.GENERIC_TOKEN,
                return_type="Token",
                comment=f"token='{val}'",
            )

    def visit_Rhs(self, node: Rhs) -> FunctionCall:
        self.print(f"// REMOVE visiting JavaCallMakerVisitor.visit_Rhs({node})")

        if node in self.cache:
            return self.cache[node]
        # TODO can we inline generated calls?
        name = self.gen.name_node(node)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_NamedItem(self, node: NamedItem) -> FunctionCall:
        self.print(f"// REMOVE visiting JavaCallMakerVisitor.visit_NamedItem({node})")
        call = self.generate_call(node.item)
        if not call :
            self.print(f"// TODO call is not created {node} -> creates artificial")
            call = "true"
            return call
        self.print(f"// REMOVE result call {call}")
        if node.name:
            call.assigned_variable = node.name
        if node.type:
            call.assigned_variable_type = node.type
        return call

    def lookahead_call_helper(self, node: Lookahead, positive: bool) -> FunctionCall:
        call = self.generate_call(node.node)
        self.print(f"// lookahead_call_helper call: {call}")
        if not call :
            self.print(f"// TODO call is not created {node} -> creates artificial")
            call = "true"
            return call
        self.print(f"//    call.nodetype: {call.nodetype}")
        if call.nodetype == NodeTypes.NAME_TOKEN:
            return FunctionCall(
                function=f"_PyPegen_lookahead_with_name",
                arguments=[positive, call.function, *call.arguments],
                return_type="int",
            )
        elif call.nodetype == NodeTypes.SOFT_KEYWORD:
            return FunctionCall(
                function="lookahead" if call.arguments and len(call.arguments) > 0  else "lookaheadSoftKeyword",
                arguments=[str(positive).lower(), *call.arguments],
                return_type="boolean",
            )
        elif call.nodetype in {NodeTypes.GENERIC_TOKEN, NodeTypes.KEYWORD}:
            return FunctionCall(
                function=f"lookahead",
                arguments=[str(positive).lower(), *call.arguments],
                return_type="boolean",
                comment=f"token={node.node}",
            )
        else:
            fname = f"lookahed{call.function}"
            if fname not in self.gen.lookahead_functions:
                self.gen.lookahead_functions[fname] = call
            return FunctionCall(
                function=fname,
                arguments=[str(positive).lower(), *call.arguments],
                return_type="boolean",
            )

    def visit_PositiveLookahead(self, node: PositiveLookahead) -> FunctionCall:
        self.print(f"// TODO visiting JavaCallMakerVisitor.visit_PositiveLookahead({node})")

    def visit_NegativeLookahead(self, node: NegativeLookahead) -> FunctionCall:
        self.print(f"// TODO visiting JavaCallMakerVisitor.visit_NegativeLookahead({node})")
        return self.lookahead_call_helper(node, False)

    def visit_Forced(self, node: Forced) -> FunctionCall:
        self.print(f"// TODO visiting JavaCallMakerVisitor.visit_Forced({node})")

    def visit_Opt(self, node: Opt) -> FunctionCall:
        self.print(f"// REMOVE visiting JavaCallMakerVisitor.visit_Opt({node})")
        call = self.generate_call(node.node)
        self.print(f"    // JavaCallMakerVisitor.visit_Opt.generated call: {call}")
        if not call:
            return None
        return FunctionCall(
            assigned_variable="_opt_var",
            function=call.function,
            arguments=call.arguments,
            force_true=True,
            comment=f"{node}",
        )

    def visit_Repeat0(self, node: Repeat0) -> FunctionCall:
        self.print(f"// TODO visiting JavaCallMakerVisitor.visit_Repeat0({node})")
        if node in self.cache:
            return self.cache[node]
        name = self.gen.name_loop(node.node, False)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type="SSTNode[]",   # Check -> asdl_seq *",
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_Repeat1(self, node: Repeat1) -> FunctionCall:
        self.print(f"// TODO visiting JavaCallMakerVisitor.visit_Repeat1({node})")
        if node in self.cache:
            return self.cache[node]
        name = self.gen.name_loop(node.node, True)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type="SSTNode[]",   # Check -> asdl_seq *",
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_Gather(self, node: Gather) -> FunctionCall:
        self.print(f"// REMOVE visiting JavaCallMakerVisitor.visit_Gather({node})")
        if node in self.cache:
            return self.cache[node]
        name = self.gen.name_gather(node)
        self.cache[node] = FunctionCall(
            assigned_variable=f"{name}_var",
            function=f"{name}_rule",
            arguments=[],
            return_type= "SSTNode[]",    # TODO "asdl_seq *",
            comment=f"{node}",
        )
        return self.cache[node]

    def visit_Group(self, node: Group) -> FunctionCall:
        self.print(f"// TODO visiting JavaCallMakerVisitor.visit_Group({node})")
        return self.generate_call(node.rhs)

    def visit_Cut(self, node: Cut) -> FunctionCall:
        self.print(f"// TODO visiting JavaCallMakerVisitor.visit_Cut({node})")

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
    ):
        super().__init__(grammar, tokens, file)
        self.callmakervisitor = JavaCallMakerVisitor(self, exact_tokens, non_exact_tokens, self.print)
        self.lookahead_functions: Dict[str, FunctionCall] = {}
        self.debug = debug

    def _generate_rule_ids(self):
        with self.indent():
            self.print("// rule ids ")
            for id, (rulename, rule) in enumerate(self.todo.items()):
                if rule.left_recursive:
                    self.print(f'private static final int {rule.name.upper()}_ID = {id}; // Left-recursive')
                else:
                    self.print(f'private static final int {rule.name.upper()}_ID = {id};')
        self.print()

    def _generate_keywords(self):
        with self.indent():
            self.print("// keywords constants")
            for key in self.callmakervisitor.keyword_cache.keys():
                self.print(f'private static final String KEYWORD_{key.upper()} = "{key}";')

    # generating helper methods
    def _generate_methods(self):
        with self.indent():
            self.print('''
    // lookahead methods written in generator
    private boolean lookahead(boolean match, Token.Kind kind) {
        int pos = mark();
        Token token = expect(kind);
        reset(pos);
        return (token != null) == match;
    }

    private boolean lookahead(boolean match, String text) {
        int pos = mark();
        Token token = expect(text);
        reset(pos);
        return (token != null) == match;
    }

    private boolean lookaheadSoftKeyword(boolean match) {
        int pos = mark();
        Token token = softKeywordToken();
        reset(pos);
        return (token != null) == match;
    }

    private Token softKeywordToken() {
        Token t = expect(Token.Kind.NAME);
        if (t != null) {
            String text = getText(t);
            if (softKeywords.contains(text)) {
                return t;
            }
        }
        return null;
    }
    ''')

    #debug message
    def _generate_debug_methods(self):
        with self.indent():
            self.print('''
    private void indent(StringBuffer sb) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    void debugMessage(String text) {
        debugMessage(text, true);
    }

    void debugMessage(String text, boolean indent) {
        StringBuffer sb = new StringBuffer();
        if(indent) {
            indent(sb);
        }
        sb.append(text);
        System.out.print(sb.toString());
    }

    void debugMessageln(String text) {
        debugMessageln(text, true);
    }

    void debugMessageln(String text, boolean indent) {
        StringBuffer sb = new StringBuffer();
        if (indent) {
            indent(sb);
        }
        sb.append(text);
        System.out.println(sb.toString());
    }''')

    def _generate_lookahead_methods(self):
        with self.indent():
            self.print()
            self.print("// lookahead methods generated")
            for name in self.lookahead_functions:
                call = self.lookahead_functions[name];
                if call.arguments and len(call.arguments) > 0:
                    self.print(f"private boolean {name}(boolean match, {', '.join(map(str, call.arguments))}) {{")
                else:
                    self.print(f"private boolean {name}(boolean match){{")
                with self.indent():
                    self.print("int tmpPos = mark();")
                    return_type = "Object"
                    if call.return_type:
                        return_type = call.return_type;
                    else:
                        return_type = "Object"
                        self.print("// TODO the return type of this call in not set -> Object is used")
                    if call.arguments and len(call.arguments) > 0:
                        self.print(f"{return_type} result = {call.function}({', '.join(map(str, call.arguments))});")
                    else:
                        self.print(f"{return_type} result = {call.function}();")
                    self.print("reset(tmpPos);")
                    self.print(f"return (result != null) == match;")
                self.print("}")
                self.print()

#    def put(self, *args, **kwargs):
#        print(end = self.indentation, file = self.file)
#        print(*args, **kwargs,  file = self.file)

    def generate(self, filename: str) -> None:
        self.print(f"// @generated by java_generator.py from {filename}")
        license = self.grammar.metas.get("license")
        if license:
            self.print(license)
        package = self.grammar.metas.get("package")
        if package:
            self.print("package %s;" % package.strip("\n"))
        imports = self.grammar.metas.get("imports")
        if imports:
            self.print(imports)
        className = os.path.splitext(os.path.basename(self.file.name))[0]
        self.print('@SuppressWarnings("all")')
        self.print("public class %s extends Parser {" % className)

        # parser method generation
        self.collect_todo();

        self._generate_rule_ids()
        self._generate_keywords()

        with self.indent():
            self.print()
            self.print("// parser fields")
            self.print("private int level = 0;")
            self.print("private final RuleResultCache<Object> cache;")
            self.print("private final Set<String> softKeywords;")


        fields = self.grammar.metas.get("parser_fields")
        if fields:
            self.print(fields)

        # TODO do we need the constructor set in grammar?
        #constructor = self.grammar.metas.get("parser_constructor")
        #if constructor:
        #    self.print(constructor % className)
        #else:
        with self.indent():
            self.print("public %s(ParserTokenizer tokenizer, NodeFactory factory) {" % className)
            with self.indent():
                self.print("super(tokenizer, factory);")
                self.print("cache = new RuleResultCache(this);")
                self.print('softKeywords = new HashSet<>(Arrays.asList("_", "case", "match"));')
            self.print("}" )


        with self.indent():
            while self.todo:
                for rulename, rule in list(self.todo.items()):
                    del self.todo[rulename]
                    self.print()
                    self.visit(rule)

        self._generate_lookahead_methods()
        self._generate_methods()
        if self.debug:
            self._generate_debug_methods()
        self.print("}")

    def _insert_debug_rule_enter (self, message):
        if self.debug:
            self.print(f'debugMessageln({message});')
            self.print("this.level++;")

    def _insert_debug_rule_leave (self, message):
        if self.debug:
            self.print("this.level--;")
            self.print(f'debugMessageln({message});')

    def _set_up_token_start_metadata_extraction(self) -> None:
        #self.print("if (p->mark == p->fill && _PyPegen_fill_token(p) < 0) {")
        #with self.indent():
        #    self.print("p->error_indicator = 1;")
        #    self.add_return("NULL")
        #self.print("}")
        self.print("Token startToken = getToken(pos);")
        #self.print("UNUSED(_start_lineno); // Only used by EXTRA macro")
        #self.print("int _start_col_offset = p->tokens[_mark]->col_offset;")
        #self.print("UNUSED(_start_col_offset); // Only used by EXTRA macro")

    def _set_up_token_end_metadata_extraction(self) -> None:
        #self.print("Token *_token = _PyPegen_get_last_nonnwhitespace_token(p);")
        #self.print("if (_token == NULL) {")
        #with self.indent():
        #    self.add_return("NULL")
        #self.print("}")
        self.print("Token endToken = getToken(mark());")
        #self.print("UNUSED(_end_lineno); // Only used by EXTRA macro")
        #self.print("int _end_col_offset = _token->end_col_offset;")
        #self.print("UNUSED(_end_col_offset); // Only used by EXTRA macro")

    def emit_action(self, is_loop:bool, node: Alt) -> None:
        # TODO this is ugly hack. We need to find out why the node.action contains so space that are not written in grammar file
        self.print(f"// node.action: {node.action}")
        node_action = (str(node.action).replace(' ', '').replace ('newSST', 'new SST'))
        # TODO this condition filter c action now. Should be removed after the grammar contains only java actions
        if node_action.startswith('factory') or node_action.startswith('new') or len(node_action) == 1:
            if is_loop:
                self.print(f"result.add({node_action});")
            else:
                self.print(f"result = {node_action};")

        #self.print("if (_res == NULL && PyErr_Occurred()) {")
        #with self.indent():
        #    self.print("p->error_indicator = 1;")
        #    if cleanup_code:
        #        self.print(cleanup_code)
        #    self.add_return("NULL")
        #self.print("}")

        #if self.debug:
        #    self.print(
        #        f'D(fprintf(stderr, "Hit with action [%d-%d]: %s\\n", _mark, p->mark, "{node}"));'
        #    )

    def emit_default_action(self, is_loop: bool, is_gather: bool, node: Alt) -> None:
        self.print(f"// self.local_variable_names: {self.local_variable_names}")
        if len(self.local_variable_names) > 1:
            if is_gather:
                assert len(self.local_variable_names) == 2
                self.print(f"SSTNode[] _res = Arrays.copyOf({self.local_variable_names[1]}, {self.local_variable_names[1]}.length + 1);")
                self.print(f"System.arraycopy(_res, 0, _res, 1, _res.length - 1);")
                self.print(f"_res[0] = {self.local_variable_names[0]};")
            else:
                self.print(f"_res = factory.createDummyName({', '.join(self.local_variable_names)});")
        else:
            if is_loop:
                self.print(f"if ({self.local_variable_names[0]} instanceof SSTNode) {{")
                with self.indent():
                    self.print(f"children.add((SSTNode){self.local_variable_names[0]});")
                self.print(f"}} else if ({self.local_variable_names[0]} instanceof SSTNode[]) {{")
                with self.indent():
                    self.print(f"for (SSTNode node: (SSTNode[]){self.local_variable_names[0]}) {{")
                    with self.indent():
                        self.print("children.add(node);")
                    self.print("}")
                self.print("}")
            else:
                self.print(f"result = {self.local_variable_names[0]};")
        pass

    def _insert_debug_message(self, message: str , indent: bool = True):
        if self.debug:
            self.print(f'debugMessageln("{message}", {str(indent).lower()});')

    def _handle_cache_result(self, rule):
        self.print(f'if (cache.hasResult(pos, {rule.name.upper()}_ID)) {{')
        with self.indent():
            self._insert_debug_rule_leave('"Taken from cache, level: " + level')
            self.print(f'return ({self.rule_return_type})cache.getResult(pos, {rule.name.upper()}_ID);')
        self.print("}")

    def _handle_left_recursion(self, rule, result_type):
        with self.indent():
            self.print("// left recursion--")
            self.print("int pos = mark();")

            self._insert_debug_rule_enter(f'"Left Recursion Rule: {rule.name}, pos: " + pos + ", level: " + level')

            self._handle_cache_result(rule)
            self.print("int lastPos = pos;")
            self.print("int endPos;")
            self.print(f"{result_type} lastResult = null;")
            self.print(f"cache.putResult(pos, {rule.name.upper()}_ID, null);")
            self.print("while(true) {")
            with self.indent():
                self.print("reset(pos);")
                self.print(f"SSTNode result = {rule.name}_rule_body();")
                self.print("endPos = mark();")
                self.print("if (endPos <= lastPos) {")
                with self.indent():
                    self.print("break;")
                self.print("}")
                self.print("lastResult = result;")
                self.print("lastPos = endPos;")
                self.print(f"cache.putResult(pos, {rule.name.upper()}_ID, result);")
            self.print("}")
            self.print("reset(lastPos);");

            self._insert_debug_rule_leave('"Result: " + lastResult + ", level: " + level')

            self.print("return lastResult;");
        self.print("}")

        self.print()
        self.print("// left-rersive rule body")
        self.print(f"public SSTNode {rule.name}_rule_body() {{")

    def _handle_default_rule_body(self, node: Rule, rhs: Rhs, result_type: str) -> None:
        isRecursive = node.left_recursive and node.leader
        if isRecursive:
            self._handle_left_recursion(node, result_type)

        with self.indent():
            self.print("// default rule body")
            self.print("int pos = mark();")
            #TODO here we should use the result_type, currently jsut Object
            #self.print(f"{result_type} result = null;")
            self.print("Object result = null;")
            self._insert_debug_rule_enter(f'"Rule: {node.name}, pos: " + pos+ ", level: " + level')

            if not isRecursive:
                self._handle_cache_result(node)

            #if any(alt.action and "EXTRA" in alt.action for alt in rhs.alts):
            if any(alt.action for alt in rhs.alts):
                self._set_up_token_start_metadata_extraction()

            self.visit(rhs, is_loop=False, is_gather=node.is_gather(), rulename = node.name)
        #    for alts in node.rhs:
                #self.print(f"// here should be generated code for {alt}")
        #        self._generate_alts(alts, node, rule_vars)

            self._insert_debug_rule_leave('"Result: null, level: " + level')
            self.print(f'return ({self.rule_return_type})cache.putResult(pos, {node.name.upper()}_ID, null);')

    def _handle_loop_rule_body(self, node: Rule, rhs: Rhs) -> None:
        with self.indent():
            self.print("// loop rule body")
            self.print("int pos = mark();")
            self.print("List<SSTNode> children = new ArrayList();")
            self._insert_debug_rule_enter(f'"Rule: {node.name}, pos: " + pos + ", level: " + level')

            self.visit(rhs, is_loop=True, is_gather=node.is_gather(), rulename=node.name)

            self._insert_debug_rule_leave('"Result: null, level: " + level')
            self.print(f'return ({self.rule_return_type})cache.putResult(pos, {node.name.upper()}_ID, null);')

    def visit_Rule(self, node: Rule) -> None:
        is_loop = node.is_loop()
        is_gather = node.is_gather()
        rhs = node.flatten()

        if node.left_recursive:
            self.print("// Left-recursive")

        for line in str(node).splitlines():
            self.print(f"// {line}")

        if is_loop or is_gather:
            result_type = "SSTNode[]"
        elif node.type:
            result_type = _check_type(self, node.type)
        else:
            result_type = "Object"

        self.print(f"public {result_type} {node.name}_rule() {{")
        self.print(f"// isLoop: {is_loop}, isGather: {is_gather}, type: {node.type})")
#        with self.local_variable_context():

        self.rule_return_type = result_type

        if is_loop:
            self._handle_loop_rule_body(node, rhs)
        else:
            self._handle_default_rule_body(node, rhs, result_type)
        self.print("}")

    def visit_Rhs(self, node: Rhs, is_loop: bool, is_gather: bool, rulename: Optional[str]) -> None:
        if is_loop:
            assert len(node.alts) == 1
        for alt in node.alts:
            self.print(f"// the result should be constructed through action: {alt.action}")
            self.visit(alt, is_loop=is_loop, is_gather=is_gather, rulename=rulename)

    def visit_NamedItem(self, node: NamedItem) -> None:
        self.print(f"// TODO visiting JavaParserGeneratorNamedItem: {node}")
        call = self.callmakervisitor.generate_call(node)
        if not call:
            self.print(f"// TODO call is not created {node}")
            return
        if hasattr(call, "assigned_variable") and call.assigned_variable:    # TODO remove check for the attr. Should not be needed.
            call.assigned_variable = self.dedupe(call.assigned_variable)
        self.print(call)

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
        self.print(") {")

    def handle_alt_normal(self, node: Alt, is_gather: bool, rulename: Optional[str]) -> None:
        self.join_conditions(keyword="if", node=node)

        with self.indent():
            # Prepare to emmit the rule action and do so
            node_str = str(node).replace('"', '\\"')
            self._insert_debug_rule_leave(f'"{rulename}[" + pos + ", " + mark() +" ](level: " + level + ") {node_str} succeeded!"');
            #if node.action and "EXTRA" in node.action:
            self.print(f"// alt action: {node.action}")
            if node.action:
                self._set_up_token_end_metadata_extraction()
            #if self.skip_actions:
            #    self.emit_dummy_action()
            #elif node.action:
            if node.action:
                self.emit_action(False, node)
            else:
                self.emit_default_action(False, is_gather, node)

            self.print(f'return ({self.rule_return_type})cache.putResult(pos, {rulename.upper()}_ID, result);')
        self.print("}")
        self.print("reset(pos);")

    def handle_alt_loop(self, node: Alt, is_gather: bool, rulename: Optional[str]) -> None:
        # Condition of the main body of the alternative
        self.join_conditions(keyword="while", node=node)
        with self.indent():
            if self.debug:
                self.print('debugMessageln("Succeeded - adding one result to collection!");');

            self.print(f"// alt action: {node.action}")
            if node.action:
                self._set_up_token_end_metadata_extraction()
            if node.action:
                self.emit_action(True, node)
            else:
                self.emit_default_action(True, is_gather, node)

            self.print ("pos = mark();")
        self.print("}")
        self.print("reset(pos);")
        self.print("if (children.size() > 0) {")
        with self.indent():
            self.print(f'return ({self.rule_return_type})cache.putResult(pos, {rulename.upper()}_ID, children.toArray(new SSTNode[children.size()]));')
        self.print("}")

        # We have parsed successfully one item!
#        with self.indent():
#            # Prepare to emit the rule action and do so
#            if node.action and "EXTRA" in node.action:
#                self._set_up_token_end_metadata_extraction()
#            if self.skip_actions:
#                self.emit_dummy_action()
#            elif node.action:
#                self.emit_action(node, cleanup_code="PyMem_Free(_children);")
#            else:
#                self.emit_default_action(is_gather, node)
#
#            # Add the result of rule to the temporary buffer of children. This buffer
#            # will populate later an asdl_seq with all elements to return.
#            self.print("if (_n == _children_capacity) {")
#            with self.indent():
#                self.print("_children_capacity *= 2;")
#                self.print(
#                    "void **_new_children = PyMem_Realloc(_children, _children_capacity*sizeof(void *));"
#                )
#                self.out_of_memory_return(f"!_new_children")
#                self.print("_children = _new_children;")
#            self.print("}")
#            self.print("_children[_n++] = _res;")
#            self.print("_mark = p->mark;")
#        self.print("}")

    def visit_Alt( self, node: Alt, is_loop: bool, is_gather: bool, rulename: Optional[str]) -> None:
        self.print("{")
        with self.indent():
            self.print(f"// visiting Alt: {node}")
            # TODO check if we can call invalid rules

            # collection variables for the alt
            vars = self.collect_vars(node)
            self.print(f"//    vars: {vars}")
            for v, var_type in sorted(item for item in vars.items() if item[0] is not None):


                if not var_type:
                    var_type = "Object"
                else:
                    var_type = _check_type(self, var_type);
                if v == "_cut_var":
                    v += " = 0"  # cut_var must be initialized
                if not is_loop:
                    self.print(f"{var_type} {v};")
                else:
                    self.print("// TODO tmp solution, we need to know how to handle tokens here")
                    self.print(f"Object {v};")

            with self.local_variable_context():
                if is_loop:
                    self.handle_alt_loop(node, is_gather, rulename)
                else:
                    self.handle_alt_normal(node, is_gather, rulename)


        self.print("}")

    def collect_vars(self, node: Alt) -> Dict[Optional[str], Optional[str]]:
        types = {}
        with self.local_variable_context():
            for item in node.items:
                name, type = self.add_var(item)
                self.print(f"// collecting vars: {type} {name}")
                types[name] = type
        return types

    def add_var(self, node: NamedItem) -> Tuple[Optional[str], Optional[str]]:
        call = self.callmakervisitor.generate_call(node.item)
        self.print(f"    // generated call: {call}")
        if not call:
            return None, None

        name = node.name if node.name else call.assigned_variable
        if name is not None:
            name = self.dedupe(name)
        return_type = call.return_type if node.type is None else node.type

        node.name = name
        return name, return_type
