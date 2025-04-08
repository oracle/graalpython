from copy import deepcopy
import sys
import attr
import re
import py
import pycparser
import shutil
from pycparser import c_ast
from pycparser.c_generator import CGenerator
from sysconfig import get_config_var
from .conf import SPECIAL_CASES, RETURN_CONSTANT

PUBLIC_API_H = py.path.local(__file__).dirpath('public_api.h')
CURRENT_DIR = py.path.local(__file__).dirpath()
AUTOGEN_H = py.path.local(__file__).dirpath('autogen.h')


def toC(node):
    return toC.gen.visit(node)
toC.gen = CGenerator()

def find_typedecl(node):
    while not isinstance(node, c_ast.TypeDecl):
        node = node.type
    return node

def get_context_return_type(func_node, const_return):
    return 'void' if const_return else toC(func_node.type.type)

def make_void(func_node):
    voidid = c_ast.IdentifierType(names=['void'])
    func_node.type.type.type = c_ast.TypeDecl(declname='void', quals=[], align=[], type=voidid)

def get_return_constant(func):
    return RETURN_CONSTANT.get(func.node.name)

def maybe_make_void(func, node):
    if RETURN_CONSTANT.get(func.node.name):
        make_void(node)

@attr.s
class Function:
    _BASE_NAME = re.compile(r'^_?HPy_?')

    name = attr.ib()
    cpython_name = attr.ib()
    ctx_index = attr.ib()
    node = attr.ib(repr=False)

    def base_name(self):
        return self._BASE_NAME.sub('', self.name)

    def ctx_name(self):
        # e.g. "ctx_Module_Create"
        return self._BASE_NAME.sub(r'ctx_', self.name)

    def is_varargs(self):
        return (len(self.node.type.args.params) > 0 and
                isinstance(self.node.type.args.params[-1], c_ast.EllipsisParam))


@attr.s
class GlobalVar:
    name = attr.ib()
    ctx_index = attr.ib()
    node = attr.ib(repr=False)

    def ctx_name(self):
        return self.name


@attr.s
class HPyFunc:
    _BASE_NAME = re.compile(r'^HPyFunc_?')

    name = attr.ib()
    node = attr.ib(repr=False)

    def base_name(self):
        return self._BASE_NAME.sub('', self.name)

    def params(self):
        return self.node.type.type.args.params

    def return_type(self):
        return self.node.type.type.type

@attr.s
class HPySlot:
    # represent a declaration contained inside enum HPySlot_Slot, such as:
    #        HPy_nb_add = SLOT(7, HPyFunc_BINARYFUNC)

    name = attr.ib()      # "HPy_nb_add"
    value = attr.ib()     # "7"
    hpyfunc = attr.ib()   # "HPyFunc_BINARYFUNC"
    node = attr.ib(repr=False)


class HPyAPIVisitor(pycparser.c_ast.NodeVisitor):
    def __init__(self, api, convert_name):
        self.api = api
        self.convert_name = convert_name
        self.cur_index = -1
        self.all_indices = []

    def _consume_ctx_index(self):
        idx = self.cur_index
        self.all_indices.append(idx)
        self.cur_index = -1
        return idx

    def verify_context_indices(self):
        """
        Verifies if context indices are without gaps. This function raises an
        assertion error if not.
        For example:
        [0, 1, 2, 3] is valid
        [0, 1, 3] is invalid
        """
        self.all_indices.sort()
        for i in range(1, len(self.all_indices)):
            prev = self.all_indices[i-1]
            cur = self.all_indices[i]
            assert prev + 1 == cur, \
                "context indices have gaps: %s -> %s" % (prev, cur)

    def _is_function_ptr(self, node):
        return (isinstance(node, c_ast.PtrDecl) and
                isinstance(node.type, c_ast.FuncDecl))

    def visit_Decl(self, node):
        if isinstance(node.type, c_ast.FuncDecl):
            self._visit_function(node)
        elif isinstance(node.type, c_ast.TypeDecl):
            self._visit_global_var(node)

    def visit_Typedef(self, node):
        # find only typedefs to function pointers whose name starts by HPyFunc_
        if node.name.startswith('HPyFunc_') and self._is_function_ptr(node.type):
            self._visit_hpyfunc_typedef(node)
        elif node.name == 'HPySlot_Slot':
            self._visit_hpyslot_slot(node)

    def visit_Pragma(self, node):
        parts = node.string.split('=')
        if len(parts) != 2:
            raise ValueError('invalid pragma: %s' % node)
        self.cur_index = int(parts[1])

    def _visit_function(self, node):
        name = node.name
        if not name.startswith('HPy') and not name.startswith('_HPy'):
            print('WARNING: Ignoring non-hpy declaration: %s' % name)
            return
        for p in node.type.args.params:
            if hasattr(p, 'name') and p.name is None:
                raise ValueError("non-named argument in declaration of %s" %
                                 name)
        cpy_name = self.convert_name(name)
        idx = self._consume_ctx_index()
        if idx == -1:
            raise ValueError('missing context index for %s' % name)
        func = Function(name, cpy_name, idx, node)
        self.api.functions.append(func)

    def _visit_global_var(self, node):
        name = node.name
        if not name.startswith('h_'):
            print('WARNING: Ignoring non-hpy variable declaration: %s' % name)
            return
        assert toC(node.type.type) == "HPy"
        idx = self._consume_ctx_index()
        if idx == -1:
            raise ValueError('missing context index for %s' % name)
        var = GlobalVar(name, idx, node)
        self.api.variables.append(var)

    def _visit_hpyfunc_typedef(self, node):
        hpyfunc = HPyFunc(node.name, node)
        self.api.hpyfunc_typedefs.append(hpyfunc)

    def _visit_hpyslot_slot(self, node):
        for e in node.type.type.values.enumerators:
            call = e.value
            assert isinstance(call, c_ast.FuncCall) and call.name.name == 'SLOT'
            assert len(call.args.exprs) == 2
            const_value, id_hpyfunc = call.args.exprs
            assert isinstance(const_value, c_ast.Constant) and const_value.type == 'int'
            assert isinstance(id_hpyfunc, c_ast.ID)
            value = const_value.value
            hpyfunc = id_hpyfunc.name
            self.api.hpyslots.append(HPySlot(e.name, value, hpyfunc, e))


def convert_name(hpy_name):
    if hpy_name in SPECIAL_CASES:
        return SPECIAL_CASES[hpy_name]
    return re.sub(r'^_?HPy_?', 'Py', hpy_name)


class HPyAPI:
    _r_comment = re.compile(r"/\*.*?\*/|//([^\n\\]|\\.)*?$",
                            re.DOTALL | re.MULTILINE)

    def __init__(self, filename):
        cpp_cmd = get_config_var('CC')
        if cpp_cmd:
            cpp_cmd = cpp_cmd.split(' ')
        elif sys.platform == 'win32':
            cpp_cmd = [shutil.which("cl.exe")]
        if sys.platform == 'win32':
            cpp_cmd += ['/E', '/I%s' % CURRENT_DIR]
        else:
            cpp_cmd += ['-E', '-I%s' % CURRENT_DIR]

        msvc = "cl.exe" in cpp_cmd[0].casefold()

        csource = pycparser.preprocess_file(filename,
                                  cpp_path=str(cpp_cmd[0]),
                                  cpp_args=cpp_cmd[1:])

        # MSVC preprocesses _Pragma(foo) to __pragma(foo),
        # but cparser needs to see a #pragma, not __pragma.
        if msvc:
            csource = re.sub(r'__pragma\(([^)]+)\)', r'#pragma \1\n', csource)

        # Remove comments.  NOTE: this assumes that comments are never inside
        # string literals, but there shouldn't be any here.
        def replace_keeping_newlines(m):
            return ' ' + m.group().count('\n') * '\n'
        csource = self._r_comment.sub(replace_keeping_newlines, csource)
        self.ast = pycparser.CParser().parse(csource)
        ## print(); self.ast.show()
        self.collect_declarations()

    @classmethod
    def parse(cls, filename):
        return cls(filename)

    def get_func(self, name):
        return self._lookup(name, self.functions)

    def get_var(self, name):
        return self._lookup(name, self.variables)

    def get_hpyfunc_typedef(self, name):
        return self._lookup(name, self.hpyfunc_typedefs)

    def get_slot(self, name):
        return self._lookup(name, self.hpyslots)

    def _lookup(self, name, collection):
        for x in collection:
            if x.name == name:
                return x
        raise KeyError(name)

    def collect_declarations(self):
        self.functions = []
        self.variables = []
        self.hpyfunc_typedefs = []
        self.hpyslots = []
        v = HPyAPIVisitor(self, convert_name)
        v.visit(self.ast)

        v.verify_context_indices()

        # Sort lists such that the generated files are deterministic.
        # List elements are either 'Function', 'GlobalVar', or 'HPyFunc'. All
        # of them have a 'node' attribute and the nodes have a 'coord' attr
        # that provides the line and column number. We use that to sort.
        def node_key(e):
            coord = e.node.coord
            return coord.line, coord.column
        self.functions.sort(key=node_key)
        self.variables.sort(key=node_key)
        self.hpyfunc_typedefs.sort(key=node_key)
        self.hpyslots.sort(key=node_key)
