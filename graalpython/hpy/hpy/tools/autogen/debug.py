from copy import deepcopy
from pycparser import c_ast
from .autogenfile import AutoGenFile
from .parse import toC, find_typedecl, get_context_return_type, \
    maybe_make_void, make_void, get_return_constant
from .hpyfunc import NO_CALL


class HPy_2_DHPy_Visitor(c_ast.NodeVisitor):
    "Visitor which renames all HPy types to DHPy"

    def visit_IdentifierType(self, node):
        if node.names == ['HPy']:
            node.names = ['DHPy']

    def visit_TypeDecl(self, node):
        if node.declname == 'ctx':
            node.declname = 'dctx'
        self.generic_visit(node)

def funcnode_with_new_name(node, name):
    newnode = deepcopy(node)
    typedecl = find_typedecl(newnode)
    typedecl.declname = name
    return newnode

def get_debug_wrapper_node(func):
    newnode = funcnode_with_new_name(func.node, 'debug_%s' % func.ctx_name())
    maybe_make_void(func, newnode)
    # fix all the types
    visitor = HPy_2_DHPy_Visitor()
    visitor.visit(newnode)
    return newnode


class autogen_debug_ctx_init_h(AutoGenFile):
    PATH = 'hpy/debug/src/autogen_debug_ctx_init.h'

    def generate(self):
        lines = []
        w = lines.append
        # emit the declarations for all the debug_ctx_* functions
        for func in self.api.functions:
            w(toC(get_debug_wrapper_node(func)) + ';')
        w('')
        w('static inline void debug_ctx_init_fields(HPyContext *dctx, HPyContext *uctx)')
        w('{')
        for var in self.api.variables:
            name = var.name
            w(f'    dctx->{name} = DHPy_open_immortal(dctx, uctx->{name});')
        for func in self.api.functions:
            name = func.ctx_name()
            w(f'    dctx->{name} = &debug_{name};')

        w('}')
        return '\n'.join(lines)


class autogen_debug_wrappers(AutoGenFile):
    PATH = 'hpy/debug/src/autogen_debug_wrappers.c'

    NO_WRAPPER = {
        '_HPy_CallRealFunctionFromTrampoline',
        'HPy_Close',
        'HPyUnicode_AsUTF8AndSize',
        'HPyTuple_FromArray',
        'HPyType_GenericNew',
        'HPyType_FromSpec',
        '_HPy_AsStruct_Legacy',
        '_HPy_AsStruct_Object',
        '_HPy_AsStruct_Type',
        '_HPy_AsStruct_Long',
        '_HPy_AsStruct_Float',
        '_HPy_AsStruct_Unicode',
        '_HPy_AsStruct_Tuple',
        '_HPy_AsStruct_List',
        '_HPy_AsStruct_Dict',
        'HPyTracker_New',
        'HPyTracker_Add',
        'HPyTracker_ForgetAll',
        'HPyTracker_Close',
        'HPyBytes_AsString',
        'HPyBytes_AS_STRING',
        'HPyTupleBuilder_New',
        'HPyTupleBuilder_Set',
        'HPyTupleBuilder_Build',
        'HPyTupleBuilder_Cancel',
        'HPyListBuilder_New',
        'HPyListBuilder_Set',
        'HPyListBuilder_Build',
        'HPyListBuilder_Cancel',
        'HPy_TypeCheck',
        'HPyContextVar_Get',
        'HPyType_GetName',
        'HPyType_IsSubtype',
        'HPyUnicode_Substring',
        'HPy_Call',
        'HPy_CallMethod',
    }

    def generate(self):
        lines = []
        w = lines.append
        w('#include "debug_internal.h"')
        w('')
        for func in self.api.functions:
            debug_wrapper = self.gen_debug_wrapper(func)
            if debug_wrapper:
                w(debug_wrapper)
                w('')
        return '\n'.join(lines)

    def gen_debug_wrapper(self, func):
        if func.name in self.NO_WRAPPER:
            return
        #
        assert not func.is_varargs()
        node = get_debug_wrapper_node(func)
        const_return = get_return_constant(func)
        if const_return:
            make_void(node)
        signature = toC(node)
        rettype = get_context_return_type(node, const_return)
        #
        def get_params_and_decls():
            lst = []
            decls = []
            for p in node.type.args.params:
                if p.name == 'ctx':
                    lst.append('get_info(dctx)->uctx')
                elif toC(p.type) == 'DHPy':
                    decls.append('    HPy dh_%s = DHPy_unwrap(dctx, %s);' % (p.name, p.name))
                    lst.append('dh_%s' % p.name)
                elif toC(p.type) in ('DHPy *', 'DHPy []'):
                    assert False, ('C type %s not supported, please write the wrapper '
                                   'for %s by hand' % (toC(p.type), func.name))
                else:
                    lst.append(p.name)
            return (', '.join(lst), '\n'.join(decls))
        (params, param_decls) = get_params_and_decls()
        #
        lines = []
        w = lines.append
        w(signature)
        w('{')
        w('    if (!get_ctx_info(dctx)->is_valid) {')
        w('        report_invalid_debug_context();')
        w('    }')
        if param_decls:
            w(param_decls)
        w('    get_ctx_info(dctx)->is_valid = false;')
        if rettype == 'void':
            w(f'    {func.name}({params});')
            w('    get_ctx_info(dctx)->is_valid = true;')
        elif rettype == 'DHPy':
            w(f'    HPy universal_result = {func.name}({params});')
            w('    get_ctx_info(dctx)->is_valid = true;')
            w('    return DHPy_open(dctx, universal_result);')
        else:
            w(f'    {rettype} universal_result = {func.name}({params});')
            w('    get_ctx_info(dctx)->is_valid = true;')
            w('    return universal_result;')
        w('}')
        return '\n'.join(lines)


class autogen_debug_ctx_call_i(AutoGenFile):
    PATH = 'hpy/debug/src/autogen_debug_ctx_call.i'

    def generate(self):
        lines = []
        w = lines.append
        for hpyfunc in self.api.hpyfunc_typedefs:
            name = hpyfunc.base_name()
            NAME = name.upper()
            if NAME in NO_CALL:
                continue
            #
            c_ret_type = toC(hpyfunc.return_type())
            args = ['next_dctx']
            dhpys = []
            for i, param in enumerate(hpyfunc.params()[1:]):
                pname = param.name
                if pname is None:
                    pname = 'arg%d' % i
                if toC(param.type) == 'HPy':
                    dhpys.append(pname)
                    args.append(f'dh_{pname}')
                else:
                    args.append(f'a->{pname}')
            args = ', '.join(args)
            #
            w(f'    case HPyFunc_{NAME}: {{')
            w(f'        HPyFunc_{name} f = (HPyFunc_{name})func;')
            w(f'        _HPyFunc_args_{NAME} *a = (_HPyFunc_args_{NAME}*)args;')
            for pname in dhpys:
                w(f'        DHPy dh_{pname} = _py2dh(dctx, a->{pname});')
            #
            w('        HPyContext *next_dctx = _switch_to_next_dctx_from_cache(dctx);')
            w('        if (next_dctx == NULL) {')
            if c_ret_type == 'HPy':
                w('            a->result = NULL;')
            elif c_ret_type == 'int' or c_ret_type == 'HPy_ssize_t' or c_ret_type == 'HPy_hash_t':
                w('            a->result = -1;')
            else:
                assert c_ret_type == 'void', c_ret_type + " not implemented"
            w('            return;')
            w('        }')
            #
            if c_ret_type == 'void':
                w(f'        f({args});')
            elif c_ret_type == 'HPy':
                w(f'        DHPy dh_result = f({args});')
            else:
                w(f'        a->result = f({args});')
            #
            w('        _switch_back_to_original_dctx(dctx, next_dctx);')
            #
            for pname in dhpys:
                w(f'        DHPy_close_and_check(dctx, dh_{pname});')
            #
            if c_ret_type == 'HPy':
                w(f'        a->result = _dh2py(dctx, dh_result);')
                w(f'        DHPy_close(dctx, dh_result);')
            #
            w(f'        return;')
            w(f'    }}')
        return '\n'.join(lines)
