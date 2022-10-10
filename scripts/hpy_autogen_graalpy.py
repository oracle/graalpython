from copy import deepcopy
from .autogenfile import AutoGenFile
from .parse import toC, find_typedecl, get_context_return_type, \
    maybe_make_void, make_void, get_return_constant


class GraalPyAutoGenFile(AutoGenFile):
    COPYRIGHT_FILE = 'mx.graalpython/copyrights/oracle.copyright.star'

    def write(self, root):
        cls = self.__class__
        clsname = '%s.%s' % (cls.__module__, cls.__name__)
        with root.join(self.COPYRIGHT_FILE).open('r') as f:
            copyright_header = f.read()
        with root.join(self.PATH).open('w') as f:
            f.write(copyright_header + '\n')
            if self.DISCLAIMER is not None:
                f.write(self.DISCLAIMER.format(clsname=clsname) + '\n')
            f.write(self.generate())
            f.write('\n')

class AutoGenFilePart:
    PATH = None
    BEGIN_MARKER = None
    END_MARKER = None

    def __init__(self, api):
        self.api = api

    def generate(self, old):
        raise NotImplementedError

    def write(self, root):
        if not self.BEGIN_MARKER or not self.END_MARKER:
            raise RuntimeError("missing BEGIN_MARKER or END_MARKER")
        n_begin = len(self.BEGIN_MARKER)
        with root.join(self.PATH).open('r') as f:
            content = f.read()
        start = content.find(self.BEGIN_MARKER)
        if start < 0:
            raise RuntimeError(f'begin marker "{self.BEGIN_MARKER}" not found'
                               f'in file {self.PATH}')
        end = content.find(self.END_MARKER, start + n_begin)
        if end < 0:
            raise RuntimeError(f'end marker "{self.END_MARKER}" not found in'
                               f'file {self.PATH}')
        old_content = content[(start+n_begin):end]
        new_content = self.generate(old_content)

        # only write file if content changed (to avoid updating the 'mtime')
        if old_content != new_content:
            with root.join(self.PATH).open('w') as f:
                f.write(content[:start + n_begin] + new_content + content[end:])


# If contained in this set, we won't generate anything for this HPy API func.
NO_WRAPPER = {
    '_HPy_CallRealFunctionFromTrampoline',
}

# If contained in this set, we won't generate a default upcall stub. But we
# will still generate the function declaration and such. The common use case
# for that is if you provide a custom upcall stub implementation.
NO_DEFAULT_UPCALL_STUB = NO_WRAPPER.union({
})

HPY_CONTEXT_PKG = 'com.oracle.graal.python.builtins.objects.cext.hpy.'
HPY_CONTEXT_CLASS = 'GraalHPyNativeContext'

###############################################################################
#                                 JNI BACKEND                                 #
###############################################################################

JNI_HPY_CONTEXT_PKG = HPY_CONTEXT_PKG + 'jni.'

# The qualified name of the Java class that represents the HPy context. This
# class will contain the appropriate up- and downcall methods.
JNI_HPY_CONTEXT_CLASS = JNI_HPY_CONTEXT_PKG + 'GraalHPyNativeContextJNI'

# This class will contain the appropriate downcall methods (the HPy function
# trampolines)
JNI_HPY_TRAMPOLINES_CLASS = 'GraalHPyJNITrampolines'

# The name of the native HPy context (will be used for HPyContext.name)
JNI_HPY_CONTEXT_NAME = 'HPy Universal ABI (GraalVM backend, JNI)'

JNI_FUN_PREFIX = 'Java_' + (JNI_HPY_CONTEXT_PKG + JNI_HPY_TRAMPOLINES_CLASS).replace('.', '_') + '_'
JNI_METHOD_PREFIX = 'jniMethod_'

UCTX_ARG = 'ctx'

JNI_UPCALL_TYPE_CASTS = {
    'HPy': 'HPY_UP',
    'void *': 'PTR_UP',
    'int': 'INT_UP',
    'long': 'LONG_UP',
    'double': 'DOUBLE_UP',
    'size_t': 'SIZE_T_UP',
    'HPyTracker': 'TRACKER_UP',
}

JNI_UPCALLS = {
    'void', 'DO_UPCALL_VOID',
    'HPy', 'DO_UPCALL_HPY',
    # "DO_UPCALL_HPY_NOARGS",
    'DO_UPCALL_TRACKER',
    'void *', 'DO_UPCALL_PTR',
    'const char *', 'DO_UPCALL_PTR',
    'char *', 'DO_UPCALL_PTR',
    'cpy_PyObject *', 'DO_UPCALL_PTR',
    # "DO_UPCALL_PTR_NOARGS",
    'DO_UPCALL_SIZE_T',
    'int', 'DO_UPCALL_INT',
    'double', 'DO_UPCALL_DOUBLE',
    'long', 'DO_UPCALL_LONG',
    'HPy_UCS4', 'DO_UPCALL_UCS4',
}

JNI_UPCALL_ARG_CASTS = {
    'HPy': 'HPY_UP',
    'int': 'INT_UP',
    'long': 'LONG_UP',
    'double': 'DOUBLE_UP',
    'HPy_ssize_t': 'SIZE_T_UP',
    'HPyTracker': 'TRACKER_UP',
}

def get_jni_signature_type(type_name):
    if type_name == 'int':
        return 'I'
    elif type_name == 'long':
        return 'L'
    elif type_name == 'double':
        return 'D'
    elif type_name == 'void':
        return 'V'
    return 'J'

def get_jni_c_type(type_name):
    if type_name == 'int':
        return 'jint'
    elif type_name == 'double':
        return 'jdouble'
    elif type_name == 'void':
        return 'void'
    # also covers type_name == 'long'
    return 'jlong'

def get_java_signature_type(type_name):
    if type_name == 'int' or type_name == 'double' or type_name == 'void':
        return type_name
    # also covers type_name == 'long'
    return 'long'

def type_is_pointer(type):
    return '*' in type

def funcnode_with_new_name(node, name):
    newnode = deepcopy(node)
    typedecl = find_typedecl(newnode)
    typedecl.declname = name
    return newnode

def get_trace_wrapper_node(func):
    newnode = funcnode_with_new_name(func.node, '%s_jni' % func.ctx_name())
    maybe_make_void(func, newnode)
    return newnode

def java_qname_to_path(java_class_qname):
    return java_class_qname.replace('.', '/') + '.java'


NO_CALL = ('DESTROYFUNC', 'TRAVERSEPROC')
NO_DEBUG_TRAMPOLINE = NO_CALL + ('GETBUFFERPROC', 'RELEASEBUFFERPROC')
NO_UNIVERSAL_TRAMPOLINE = NO_CALL


class autogen_ctx_jni(AutoGenFilePart):
    """
    Generates the Java JNI trampoline class for calling native functions of a
    certain signature like 'HPy_tp_init'.
    """
    PATH = 'graalpython/com.oracle.graal.python/src/' + java_qname_to_path(JNI_HPY_CONTEXT_PKG + JNI_HPY_TRAMPOLINES_CLASS)
    BEGIN_MARKER = '    // {{start autogen}}\n'
    END_MARKER = '    // {{end autogen}}\n'

    def generate(self, old):
        lines_universal = []
        u = lines_universal.append
        lines_debug = []
        d = lines_debug.append
        for hpyfunc in self.api.hpyfunc_typedefs:
            name = hpyfunc.base_name().capitalize()
            if name.upper() in NO_CALL:
                continue
            #
            rettype = get_java_signature_type(toC(hpyfunc.return_type()))
            args = ['long target', 'long ctx']
            for i, param in enumerate(hpyfunc.params()[1:]):
                pname = param.name
                if pname is None:
                    pname = 'arg%d' % i
                jtype = get_java_signature_type(toC(param.type))
                args.append(f'{jtype} {pname}')
            args = ', '.join(args)
            u(f'    // {toC(hpyfunc.node)}')
            u('    @TruffleBoundary')
            u(f'    public static native {rettype} execute{name}({args});')
            u('')
            d(f'    // {toC(hpyfunc.node)}')
            d('    @TruffleBoundary')
            d(f'    public static native {rettype} executeDebug{name}({args});')
            d('')
        return '\n'.join(lines_universal + lines_debug)


class autogen_ctx_call_jni(GraalPyAutoGenFile):
    """
    Generates the JNI call trampolines that will be used to call HPy functions
    from Java (for both, universal or debug mode).
    """
    PATH = 'graalpython/com.oracle.graal.python.jni/src/autogen_ctx_call_jni.c'

    def generate(self):
        lines = []
        w = lines.append
        jni_include = JNI_HPY_CONTEXT_PKG.replace('.', '_') + JNI_HPY_TRAMPOLINES_CLASS
        w(f'#include "hpy_jni.h"')
        w(f'#include "{jni_include}.h"')
        w('')
        w(f'#define TRAMPOLINE(name) {JNI_FUN_PREFIX} ## name')
        w('')
        self.generateUniversal(w)
        w('')
        self.generateDebug(w)
        w('#undef TRAMPOLINE')
        return '\n'.join(lines)

    def generateUniversal(self, w):
        w('/*******************************************************************')
        w(' *                    UNIVERSAL MODE TRAMPOLINES                   *')
        w(' ******************************************************************/')
        w('')
        w(f'JNIEXPORT jlong JNICALL TRAMPOLINE(executeModuleInit)(JNIEnv *env, jclass clazz, jlong target, jlong ctx)')
        w('{')
        w('    return _h2jlong(((DHPy (*)(HPyContext *)) target)((HPyContext *) ctx));')
        w('}')
        w('')
        for hpyfunc in self.api.hpyfunc_typedefs:
            name = hpyfunc.base_name()
            if name.upper() in NO_CALL:
                continue
            #
            c_rettype = toC(hpyfunc.return_type())
            jni_c_rettype = get_jni_c_type(c_rettype)
            args = ['(HPyContext *)ctx']
            trampoline_args = ['jlong target', 'jlong ctx']
            for i, param in enumerate(hpyfunc.params()[1:]):
                pname = param.name
                if pname is None:
                    pname = 'arg%d' % i
                c_param_type = toC(param.type)
                jni_type = get_jni_c_type(c_param_type)
                trampoline_args.append(f'{jni_type} {pname}')
                if c_param_type == 'HPy':
                    args.append(f'_jlong2h({pname})')
                else:
                    args.append(f'({c_param_type}) {pname}')
            trampoline_args = ', '.join(trampoline_args)
            args = ', '.join(args)
            #
            w(f'JNIEXPORT {jni_c_rettype} JNICALL TRAMPOLINE(execute{name.capitalize()})(JNIEnv *env, jclass clazz, {trampoline_args})')
            w('{')
            w(f'    HPyFunc_{name} f = (HPyFunc_{name})target;')
            if c_rettype == 'void':
                w(f'    f({args});')
            elif c_rettype == 'HPy':
                w(f'    return _h2jlong(f({args}));')
            else:
                w(f'    return ({jni_c_rettype}) f({args});')
            w('}')
            w('')

    def generateDebug(self, w):
        w('/*******************************************************************')
        w(' *                      DEBUG MODE TRAMPOLINES                     *')
        w(' ******************************************************************/')
        w('')
        w(f'JNIEXPORT jlong JNICALL TRAMPOLINE(executeDebugModuleInit)(JNIEnv *env, jclass clazz, jlong target, jlong ctx)')
        w('{')
        w('    HPyContext *dctx = (HPyContext *) ctx;')
        w('    return from_dh(dctx, ((DHPy (*)(HPyContext *)) target)(dctx));')
        w('}')
        w('')
        for hpyfunc in self.api.hpyfunc_typedefs:
            name = hpyfunc.base_name()
            if name.upper() in NO_DEBUG_TRAMPOLINE:
                continue
            #
            c_rettype = toC(hpyfunc.return_type())
            jni_c_rettype = get_jni_c_type(c_rettype)
            args = ['dctx']
            dh_init = [''] * len(args)
            dh_arr = []
            has_args_param = False
            trampoline_args = ['jlong target', 'jlong ctx']
            for i, param in enumerate(hpyfunc.params()[1:]):
                pname = param.name
                if pname is None:
                    pname = f'arg{i}'
                c_param_type = toC(param.type)
                jni_type = get_jni_c_type(c_param_type)
                trampoline_args.append(f'{jni_type} {pname}')
                if c_param_type == 'HPy':
                    dh_arg = f'dh_{pname}'
                    dh_init.append(pname)
                    args.append(dh_arg)
                elif c_param_type == 'HPy *' and pname == 'args':
                    dh_init.append('')
                    args.append('dh_args')
                    has_args_param = True
                else:
                    dh_init.append('')
                    args.append(f'({c_param_type}){pname}')
            trampoline_args = ', '.join(trampoline_args)
            s_args = ', '.join(args)
            #
            w(f'JNIEXPORT {jni_c_rettype} JNICALL TRAMPOLINE(executeDebug{name.capitalize()})(JNIEnv *env, jclass clazz, {trampoline_args})')
            w('{')
            w('    HPyContext *dctx = (HPyContext *) ctx;')
            w(f'    HPyFunc_{name} f = (HPyFunc_{name})target;')
            for dh_arg, h_arg in zip(args, dh_init):
                if h_arg:
                    w(f'    DHPy {dh_arg} = _jlong2dh(dctx, {h_arg});')
            if has_args_param:
                w(f'    _ARR_JLONG2DH(dctx, dh_args, args, nargs)')
            retvar = ''
            if c_rettype == 'void':
                w(f'    f({s_args});')
            elif c_rettype == 'HPy':
                retvar = 'dh_result'
                w(f'    DHPy {retvar} = f({s_args});')
            else:
                retvar = 'result'
                w(f'    {jni_c_rettype} {retvar} = ({jni_c_rettype}) f({s_args});')
            if has_args_param:
                w(f'    _ARR_DH_CLOSE(dctx, dh_args, nargs)')
            for dh_arg, h_arg in zip(args, dh_init):
                if h_arg:
                    w(f'    DHPy_close_and_check(dctx, {dh_arg});')
            if c_rettype == 'HPy':
                w(f'    return from_dh(dctx, {retvar});')
            elif retvar:
                w(f'    return {retvar};')
            w('}')
            w('')
