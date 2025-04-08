from copy import deepcopy
from pycparser import c_ast
from .autogenfile import AutoGenFile
from .parse import toC, find_typedecl, get_context_return_type, \
    maybe_make_void, make_void, get_return_constant


# We will call the delegate context's function but we still need to unwrap
# the context. This is in contrast to, e.g., the debug mode, where you would
# manually write a wrapper function. Here we can generate that as well.
NO_WRAPPER = {
    '_HPy_CallRealFunctionFromTrampoline',
    'HPy_FatalError',
}

class Ctx2TctxVisitor(c_ast.NodeVisitor):
    """Visitor which renames all ctx to tctx"""

    def visit_TypeDecl(self, node):
        if node.declname == 'ctx':
            node.declname = 'tctx'
        self.generic_visit(node)

def funcnode_with_new_name(node, name):
    newnode = deepcopy(node)
    typedecl = find_typedecl(newnode)
    typedecl.declname = name
    return newnode

def get_trace_wrapper_node(func):
    newnode = funcnode_with_new_name(func.node, 'trace_%s' % func.ctx_name())
    maybe_make_void(func, newnode)
    # rename ctx to tctx
    visitor = Ctx2TctxVisitor()
    visitor.visit(newnode)
    return newnode

class autogen_tracer_ctx_init_h(AutoGenFile):
    PATH = 'hpy/trace/src/autogen_trace_ctx_init.h'

    def generate(self):
        lines = []
        w = lines.append
        # emit the declarations for all the trace_ctx_* functions
        for func in self.api.functions:
            if func.name not in NO_WRAPPER:
                w(toC(get_trace_wrapper_node(func)) + ';')
        n_decls = len(self.api.functions) + len(self.api.variables)
        w('')
        w(f'static inline void trace_ctx_init_info(HPyTraceInfo *info, HPyContext *uctx)')
        w('{')
        w(f'    info->magic_number = HPY_TRACE_MAGIC;')
        w(f'    info->uctx = uctx;')
        w(f'    info->call_counts = (uint64_t *)calloc({n_decls}, sizeof(uint64_t));')
        w(f'    info->durations = (_HPyTime_t *)calloc({n_decls}, sizeof(_HPyTime_t));')
        w(f'    info->on_enter_func = HPy_NULL;')
        w(f'    info->on_exit_func = HPy_NULL;')
        w('}')
        w('')
        w(f'static inline void trace_ctx_free_info(HPyTraceInfo *info)')
        w('{')
        w(f'    assert(info->magic_number == HPY_TRACE_MAGIC);')
        w(f'    free(info->call_counts);')
        w(f'    free(info->durations);')
        w(f'    HPy_Close(info->uctx, info->on_enter_func);')
        w(f'    HPy_Close(info->uctx, info->on_exit_func);')
        w('}')
        w('')
        w(f'static inline void trace_ctx_init_fields(HPyContext *tctx, HPyContext *uctx)')
        w('{')
        for var in self.api.variables:
            name = var.name
            w(f'    tctx->{name} = uctx->{name};')
        for func in self.api.functions:
            if func.name in NO_WRAPPER:
                name = func.ctx_name()
                w(f'    tctx->{name} = uctx->{name};')
            else:
                name = func.ctx_name()
                w(f'    tctx->{name} = &trace_{name};')
        w('}')
        return '\n'.join(lines)


class autogen_tracer_wrappers(AutoGenFile):
    PATH = 'hpy/trace/src/autogen_trace_wrappers.c'

    def generate(self):
        lines = []
        w = lines.append
        w('#include "trace_internal.h"')
        w('')
        for func in self.api.functions:
            debug_wrapper = self.gen_trace_wrapper(func)
            if debug_wrapper:
                w(debug_wrapper)
                w('')
        return '\n'.join(lines)

    def gen_trace_wrapper(self, func):
        if func.name in NO_WRAPPER:
            return

        assert not func.is_varargs()
        node = get_trace_wrapper_node(func)
        const_return = get_return_constant(func)
        if const_return:
            make_void(node)
        signature = toC(node)
        rettype = get_context_return_type(node, const_return)

        def get_params():
            lst = []
            for p in node.type.args.params:
                if p.name == 'ctx':
                    lst.append('uctx')
                else:
                    lst.append(p.name)
            return ', '.join(lst)
        params = get_params()

        lines = []
        w = lines.append
        w(signature)
        w('{')
        w(f'    HPyTraceInfo *info = hpy_trace_on_enter(tctx, {func.ctx_index});')
        w(f'    HPyContext *uctx = info->uctx;')
        w(f'    _HPyTime_t _ts_start, _ts_end;')
        w(f'    _HPyClockStatus_t r0, r1;')
        w(f'    r0 = get_monotonic_clock(&_ts_start);')
        if rettype == 'void':
            w(f'    {func.name}({params});')
        else:
            w(f'    {rettype} res = {func.name}({params});')
        w(f'    r1 = get_monotonic_clock(&_ts_end);')
        w(f'    hpy_trace_on_exit(info, {func.ctx_index}, r0, r1, &_ts_start, &_ts_end);')
        if rettype != 'void':
            w(f'    return res;')
        w('}')
        return '\n'.join(lines)


class autogen_trace_func_table_c(AutoGenFile):
    PATH = 'hpy/trace/src/autogen_trace_func_table.c'

    def generate(self):
        lines = []
        w = lines.append

        n_funcs = len(self.api.functions)
        n_decls = n_funcs + len(self.api.variables)
        func_table = ['NO_FUNC'] * n_decls
        for func in self.api.functions:
            name = func.ctx_name()
            func_table[func.ctx_index] = f'"{name}"'

        w('#include "trace_internal.h"')
        w('')
        w(f'#define TRACE_NFUNC {n_funcs}')
        w('')
        w('#define NO_FUNC ""')
        w('static const char *trace_func_table[] = {')
        for func in func_table:
            w(f'    {func},')
        w(f'    NULL /* sentinel */')
        w('};')
        w('')
        w('int hpy_trace_get_nfunc(void)')
        w('{')
        w('    return TRACE_NFUNC;')
        w('}')
        w('')
        w('const char * hpy_trace_get_func_name(int idx)')
        w('{')
        w(f'    if (idx >= 0 && idx < {n_decls})')
        w('        return trace_func_table[idx];')
        w('    return NULL;')
        w('}')
        w('')
        return '\n'.join(lines)


