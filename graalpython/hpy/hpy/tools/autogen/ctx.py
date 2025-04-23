from copy import deepcopy
from pycparser import c_ast
from .autogenfile import AutoGenFile
from .parse import toC, find_typedecl, maybe_make_void


class autogen_ctx_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/universal/autogen_ctx.h'

    ## struct _HPyContext_s {
    ##     const char *name;
    ##     void *_private;
    ##     int abi_version;
    ##     HPy h_None;
    ##     ...
    ##     HPy (*ctx_Module_Create)(HPyContext *ctx, HPyModuleDef *def);
    ##     ...
    ## }

    def generate(self):
        # Put all declarations (variables and functions) into one list in order
        # to be able to sort them by their given context index.
        # We need to remember the output function per item (either
        # 'declare_var' or 'declare_func'). So, we create tuples with structure
        # '(decl, declare_*)'.
        all_decls = [(x, self.declare_var) for x in self.api.variables]
        all_decls += [(x, self.declare_func) for x in self.api.functions]

        # sort the list of all declaration by 'decl.ctx_index'
        all_decls.sort(key=lambda x: x[0].ctx_index)

        lines = []
        w = lines.append
        w('struct _HPyContext_s {')
        w('    const char *name; // used just to make debugging and testing easier')
        w('    void *_private;   // used by implementations to store custom data')
        w('    int abi_version;')
        for var, cons in all_decls:
            w('    %s;' % cons(var))
        w('};')
        return '\n'.join(lines)

    def declare_var(self, var):
        return toC(var.node)

    def declare_func(self, func):
        # e.g. "HPy (*ctx_Module_Create)(HPyContext *ctx, HPyModuleDef *def)"
        #
        # turn the function declaration into a function POINTER declaration
        newnode = deepcopy(func.node)
        newnode.type = c_ast.PtrDecl(type=newnode.type, quals=[])
        maybe_make_void(func, newnode)

        # fix the name of the function pointer
        typedecl = find_typedecl(newnode)
        typedecl.declname = func.ctx_name()
        return toC(newnode)


class autogen_ctx_def_h(AutoGenFile):
    PATH = 'hpy/universal/src/autogen_ctx_def.h'

    ## struct _HPyContext_s g_universal_ctx = {
    ##     .name = "...",
    ##     ._private = NULL,
    ##     .abi_version = HPY_ABI_VERSION,
    ##     .h_None = {CONSTANT_H_NONE},
    ##     ...
    ##     .ctx_Module_Create = &ctx_Module_Create,
    ##     ...
    ## }

    def generate(self):
        lines = []
        w = lines.append
        w('struct _HPyContext_s g_universal_ctx = {')
        w('    .name = "HPy Universal ABI (CPython backend)",')
        w('    ._private = NULL,')
        w('    .abi_version = HPY_ABI_VERSION,')
        w('    /* h_None & co. are initialized by init_universal_ctx() */')
        for func in self.api.functions:
            w('    .%s = &%s,' % (func.ctx_name(), func.ctx_name()))
        w('};')
        return '\n'.join(lines)


class cpython_autogen_ctx_h(AutoGenFile):
    PATH = 'hpy/devel/include/hpy/cpython/autogen_ctx.h'

    def generate(self):
        # Put all variable declarations into a list in order
        # to be able to sort them by their given context index.
        var_decls = list(self.api.variables)

        # sort the list of var declaration by 'decl.ctx_index'
        var_decls.sort(key=lambda x: x.ctx_index)

        lines = []
        w = lines.append
        w('struct _HPyContext_s {')
        w('    const char *name;')
        w('    int abi_version;')
        for var in var_decls:
            w('    %s;' % toC(var.node))
        w('};')
        return '\n'.join(lines)
