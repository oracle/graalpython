from copy import deepcopy
from pycparser import c_ast
from .autogenfile import AutoGenFile
from .parse import toC, find_typedecl

NO_CALL = ('VARARGS', 'KEYWORDS', 'INITPROC', 'DESTROYFUNC',
           'GETBUFFERPROC', 'RELEASEBUFFERPROC', 'TRAVERSEPROC', 'MOD_CREATE',
           'VECTORCALLFUNC', 'NEWFUNC')
NO_TRAMPOLINE = NO_CALL + ('RICHCMPFUNC',)

# This is a list of type that can automatically be converted from Python to HPy
# and vice versa.
AUTO_CONVERSION_TYPES = ('HPy', 'HPy_ssize_t', 'void *', 'int', 'char *',
                         'HPy_hash_t', 'HPy_RichCmpOp', 'void')

class autogen_hpyfunc_declare_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/autogen_hpyfunc_declare.h'

    ## #define _HPyFunc_DECLARE_HPyFunc_NOARGS(SYM)  \
    ##     static HPy SYM(HPyContext *ctx, HPy self)

    def generate(self):
        lines = []
        w = lines.append
        for hpyfunc in self.api.hpyfunc_typedefs:
            # declare a function named 'SYM' of the appropriate type
            funcdecl = hpyfunc.node.type.type
            symdecl = deepcopy(funcdecl)
            if isinstance(symdecl.type, c_ast.PtrDecl):
                symdecl.type = symdecl.type.type
            symdecl.type.declname = 'SYM'
            symdecl = toC(symdecl)
            #
            # generate a macro emitting 'symdecl'
            name = hpyfunc.base_name().upper()
            w(f'#define _HPyFunc_DECLARE_HPyFunc_{name}(SYM) static {symdecl}')
        w('')

        for hpyfunc in self.api.hpyfunc_typedefs:
            # generate the typedef for HPyFunc_{base_name}
            w(f'{toC(hpyfunc.node)};')

        return '\n'.join(lines)


def hpy_to_cpy(declnode):
    if toC(declnode.type) == 'HPy':
        declnode = deepcopy(declnode)
        declnode.type.type.names = ['cpy_PyObject']
        declnode.type = c_ast.PtrDecl(type=declnode.type, quals=[])
    return declnode


class autogen_hpyfunc_trampoline_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/universal/autogen_hpyfunc_trampolines.h'

    def generate(self):
        lines = []
        w = lines.append
        for hpyfunc in self.api.hpyfunc_typedefs:
            NAME = hpyfunc.base_name().upper()
            if NAME in NO_TRAMPOLINE:
                continue
            #
            tramp_node = deepcopy(hpyfunc.node.type.type)
            if isinstance(tramp_node.type, c_ast.PtrDecl):
                tramp_node.type = tramp_node.type.type
            tramp_node.type.declname = 'SYM'
            tramp_node = hpy_to_cpy(tramp_node)
            assert toC(tramp_node.args.params[0].type) in ['void', 'HPyContext *']
            tramp_node.args.params = [hpy_to_cpy(p)
                                      for p in tramp_node.args.params[1:]]
            for i, param in enumerate(tramp_node.args.params):
                typedecl = find_typedecl(param.type)
                if typedecl.declname is None:
                    param.name = 'arg%d' % i
                    typedecl.declname = 'arg%d' % i
            arg_names = [param.name for param in tramp_node.args.params]
            arg_names = ', '.join(arg_names)
            #
            # generate the struct that will contain all parameters
            w(f'typedef struct {{')
            for param in tramp_node.args.params:
                w(f'    {toC(param)};')
            if toC(tramp_node.type) != 'void':
                w(f'    {toC(tramp_node.type)} result;')
            w(f'}} _HPyFunc_args_{NAME};')
            w('')
            #
            # generate the trampoline itself
            w(f'#define _HPyFunc_TRAMPOLINE_HPyFunc_{NAME}(SYM, IMPL) \\')
            w(f'    static {toC(tramp_node)} \\')
            w(f'    {{ \\')
            w(f'        _HPyFunc_args_{NAME} a = {{ {arg_names} }}; \\')
            w(f'        _HPy_CallRealFunctionFromTrampoline( \\')
            w(f'           _ctx_for_trampolines, HPyFunc_{NAME}, (HPyCFunction)IMPL, &a); \\')
            if toC(tramp_node.type) == 'void':
                w(f'        return; \\')
            else:
                w(f'        return a.result; \\')
            w(f'    }}')
            w('')
        return '\n'.join(lines)


def _py2h(type):
    if type == 'HPy':
        return '_py2h'
    elif type in AUTO_CONVERSION_TYPES:
        return ''
    raise TypeError(f'cannot generate automatic conversion for type \'{type}\'')


def _h2py(type):
    if type == 'HPy':
        return '_h2py'
    elif type in AUTO_CONVERSION_TYPES:
        return ''
    raise TypeError(f'cannot generate automatic conversion for type \'{type}\'')


class autogen_ctx_call_i(AutoGenFile):
    PATH = 'hpy/universal/src/autogen_ctx_call.i'

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
            args = ['ctx']
            for i, param in enumerate(hpyfunc.params()[1:]):
                pname = param.name
                if pname is None:
                    pname = 'arg%d' % i
                args.append(f'{_py2h(toC(param.type))}(a->{pname})')
            args = ', '.join(args)
            #
            w(f'    case HPyFunc_{NAME}: {{')
            w(f'        HPyFunc_{name} f = (HPyFunc_{name})func;')
            w(f'        _HPyFunc_args_{NAME} *a = (_HPyFunc_args_{NAME}*)args;')
            if c_ret_type == 'void':
                w(f'        f({args});')
            else:
                w(f'        a->result = {_h2py(c_ret_type)}(f({args}));')
            w(f'        return;')
            w(f'    }}')
        return '\n'.join(lines)


class autogen_cpython_hpyfunc_trampoline_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/cpython/autogen_hpyfunc_trampolines.h'

    def generate(self):
        lines = []
        w = lines.append
        for hpyfunc in self.api.hpyfunc_typedefs:
            name = hpyfunc.base_name()
            NAME = name.upper()
            if NAME in NO_TRAMPOLINE:
                continue
            #
            tramp_node = deepcopy(hpyfunc.node.type.type)
            if isinstance(tramp_node.type, c_ast.PtrDecl):
                tramp_node.type = tramp_node.type.type
            tramp_node.type.declname = 'SYM'
            tramp_node = hpy_to_cpy(tramp_node)
            tramp_node.args.params = [hpy_to_cpy(p)
                                      for p in tramp_node.args.params[1:]]
            for i, param in enumerate(tramp_node.args.params):
                typedecl = find_typedecl(param.type)
                if typedecl.declname is None:
                    param.name = 'arg%d' % i
                    typedecl.declname = 'arg%d' % i

            result = _h2py(toC(hpyfunc.return_type()))
            args = ['_HPyGetContext()']
            func_ptr_ret_type = toC(hpyfunc.return_type())
            func_ptr_sig = ['HPyContext *']
            for i, param in enumerate(hpyfunc.params()[1:]):
                pname = param.name
                if pname is None:
                    pname = 'arg%d' % i
                func_ptr_sig.append(toC(param.type))
                args.append(f'{_py2h(toC(param.type))}({pname})')
            args = ', '.join(args)
            func_ptr_sig = ', '.join(func_ptr_sig)
            #
            w(f'typedef {func_ptr_ret_type} (*_HPyCFunction_{NAME})({func_ptr_sig});')
            w(f'#define _HPyFunc_TRAMPOLINE_HPyFunc_{NAME}(SYM, IMPL) \\')
            w(f'    static {toC(tramp_node)} \\')
            w(f'    {{ \\')
            w(f'        _HPyCFunction_{NAME} func = (_HPyCFunction_{NAME})IMPL; \\')
            if toC(tramp_node.type) == 'void':
                w(f'        func({args}); \\')
                w(f'        return; \\')
            else:
                w(f'        return {result}(func({args})); \\')
            w(f'    }}')
        return '\n'.join(lines)
