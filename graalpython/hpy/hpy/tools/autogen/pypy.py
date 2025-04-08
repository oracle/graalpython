from .autogenfile import AutoGenFile
from .parse import toC

# this class should probably be moved somewhere in the PyPy repo
class autogen_pypy_txt(AutoGenFile):
    PATH = 'hpy/tools/autogen/autogen_pypy.txt'
    LANGUAGE = 'txt' # to avoid inserting the disclaimer

    def generate(self):
        lines = []
        w = lines.append
        w("typedef struct _HPyContext_s {")
        w("    int abi_version;")
        for var in self.api.variables:
            w("    struct _HPy_s %s;" % var.ctx_name())
        for func in self.api.functions:
            w("    void * %s;" % func.ctx_name())
        w("} _struct_HPyContext_s;")
        w("")
        w("")
        # generate stubs for all the API functions
        for func in self.api.functions:
            w(self.stub(func))
        return '\n'.join(lines)

    def stub(self, func):
        signature = toC(func.node)
        if func.is_varargs():
            return '# %s' % signature
        #
        argnames = [p.name for p in func.node.type.args.params]
        lines = []
        w = lines.append
        w('@API.func("%s")' % signature)
        w('def %s(space, %s):' % (func.name, ', '.join(argnames)))
        w('    from rpython.rlib.nonconst import NonConstant # for the annotator')
        w('    if NonConstant(False): return 0')
        w('    raise NotImplementedError')
        w('')
        return '\n'.join(lines)
