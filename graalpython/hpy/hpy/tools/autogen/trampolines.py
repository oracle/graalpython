from copy import deepcopy
from .autogenfile import AutoGenFile
from .parse import toC,find_typedecl, get_context_return_type, \
    make_void, get_return_constant
from . import conf


class autogen_trampolines_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/universal/autogen_trampolines.h'

    def generate(self):
        lines = []
        for func in self.api.functions:
            trampoline = self.gen_trampoline(func)
            if trampoline:
                lines.append(trampoline)
                lines.append('')
        return '\n'.join(lines)

    def gen_trampoline(self, func):
        # HPyAPI_FUNC HPy HPyModule_Create(HPyContext *ctx, HPyModuleDef *def) {
        #      return ctx->ctx_Module_Create ( ctx, def );
        # }
        if func.name in conf.NO_TRAMPOLINES:
            return None
        const_return = get_return_constant(func)
        rettype = get_context_return_type(func.node, const_return)
        parts = []
        w = parts.append
        w('HPyAPI_FUNC')
        w(toC(func.node))
        w('{\n    ')

        # trampolines cannot deal with varargs easily
        assert not func.is_varargs()

        if rettype == 'void':
            w('ctx->%s' % func.ctx_name())
        else:
            w('return ctx->%s' % func.ctx_name())
        w('(')
        params = [p.name for p in func.node.type.args.params]
        w(', '.join(params))
        w(');')

        if const_return:
            w('return %s;' % const_return)

        w('\n}')
        return ' '.join(parts)


class cpython_autogen_api_impl_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/cpython/autogen_api_impl.h'
    GENERATE_CONST_RETURN = True

    def signature(self, func, const_return):
        """
        Return the C signature of the impl function.

        In CPython mode, the name it's the same as in public_api:
           HPy_Add          ==> HPyAPI_FUNC HPy_Add
           HPyLong_FromLong ==> HPyAPI_FUNC HPyLong_FromLong

        See also universal_autogen_ctx_impl_h.
        """
        sig = toC(func.node)
        return 'HPyAPI_FUNC %s' % sig

    def generate(self):
        lines = []
        for func in self.api.functions:
            if not func.cpython_name:
                continue
            lines.append(self.gen_implementation(func))
            lines.append('')
        return '\n'.join(lines)

    def gen_implementation(self, func):
        def call(pyfunc, return_type):
            # return _py2h(PyNumber_Add(_h2py(x), _h2py(y)))
            args = []
            for p in func.node.type.args.params:
                if toC(p.type) == 'HPyContext *':
                    continue
                elif toC(p.type) == 'HPy':
                    arg = '_h2py(%s)' % p.name
                elif toC(p.type) == 'HPyThreadState':
                    arg = '_h2threads(%s)' % p.name
                else:
                    arg = p.name
                args.append(arg)
            result = '%s(%s)' % (pyfunc, ', '.join(args))
            if return_type == 'HPy':
                result = '_py2h(%s)' % result
            elif return_type == 'HPyThreadState':
                result = '_threads2h(%s)' % result
            return result
        #
        lines = []
        w = lines.append
        pyfunc = func.cpython_name
        if not pyfunc:
            raise ValueError(f"Cannot generate implementation for {self}")
        const_return = get_return_constant(func)
        return_type = get_context_return_type(func.node, const_return)
        return_stmt = '' if return_type == 'void' else 'return '
        w(self.signature(func, const_return))
        w('{')
        w('    %s%s;' % (return_stmt, call(pyfunc, return_type)))

        if self.GENERATE_CONST_RETURN and const_return:
            w('    return %s;' % const_return)

        w('}')
        return '\n'.join(lines)


class universal_autogen_ctx_impl_h(cpython_autogen_api_impl_h):
    PATH = 'hpy/universal/src/autogen_ctx_impl.h'
    GENERATE_CONST_RETURN = False

    def signature(self, func, const_return):
        """
        Return the C signature of the impl function.

        In Universal mode, the name is prefixed by ctx_:
           HPy_Add          ==> HPyAPI_IMPL ctx_Add
           HPyLong_FromLong ==> HPyAPI_IMPL ctx_Long_FromLong

        See also cpython_autogen_api_impl_h.
        """
        newnode = deepcopy(func.node)
        if const_return:
            make_void(newnode)
        typedecl = find_typedecl(newnode)
        # rename the function
        typedecl.declname = func.ctx_name()
        sig = toC(newnode)
        return 'HPyAPI_IMPL %s' % sig
