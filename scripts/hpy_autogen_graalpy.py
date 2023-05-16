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
    #'HPyListBuilder_New', 'HPyListBuilder_Set','HPyListBuilder_Build','HPyListBuilder_Cancel',
    'HPyTupleBuilder_New', 'HPyTupleBuilder_Set','HPyTupleBuilder_Build','HPyTupleBuilder_Cancel',
})

HPY_CONTEXT_PKG = 'com.oracle.graal.python.builtins.objects.cext.hpy.'
HPY_CONTEXT_CLASS = 'GraalHPyNativeContext'

###############################################################################
#                                 JNI BACKEND                                 #
###############################################################################

JNI_HPY_CONTEXT_PKG = HPY_CONTEXT_PKG + 'jni.'

# The qualified name of the Java class that represents the HPy context. This
# class will contain the appropriate up- and downcall methods.
JNI_HPY_CONTEXT_CLASS = JNI_HPY_CONTEXT_PKG + 'GraalHPyJNIContext'

# This class will contain the appropriate downcall methods (the HPy function
# trampolines)
JNI_HPY_TRAMPOLINES_CLASS = 'GraalHPyJNITrampolines'
JNI_HPY_BACKEND_CLASS = 'GraalHPyJNIContext'

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
    '_HPyCapsule_key': 'INT_UP'
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
    '_HPyCapsule_key': 'INT_UP'
}

def get_cast_fun(type_name):
    if type_name == 'HPy':
        return 'HPY_UP'
    elif type_name == 'HPyGlobal':
        return 'HPY_GLOBAL_UP'
    elif type_name == 'HPyField':
        return 'HPY_FIELD_UP'
    elif type_is_pointer(type_name):
        return 'PTR_UP'
    elif type_name in ('int', '_HPyCapsule_key'):
        return 'INT_UP'
    elif type_name == 'long':
        return 'LONG_UP'
    elif type_name == 'double':
        return 'DOUBLE_UP'
    elif type_name == 'HPy_ssize_t':
        return 'SIZE_T_UP'
    elif type_name == 'HPyTracker':
        return 'HPY_TRACKER_UP'
    elif type_name == 'HPyListBuilder':
        return 'HPY_LIST_BUILDER_UP'
    elif type_name == 'HPyThreadState':
        return 'HPY_THREAD_STATE_UP'
    return 'LONG_UP'

def get_jni_signature_type(type_name):
    if type_name in ('int', '_HPyCapsule_key'):
        return 'I'
    elif type_name == 'long':
        return 'L'
    elif type_name == 'double':
        return 'D'
    elif type_name == 'void':
        return 'V'
    return 'J'

def get_jni_c_type(type_name):
    if type_name in ('int', '_HPyCapsule_key'):
        return 'jint'
    elif type_name == 'double':
        return 'jdouble'
    elif type_name == 'void':
        return 'void'
    # also covers type_name == 'long'
    return 'jlong'

def get_java_signature_type(type):
    type_name = toC(type)
    if type_name in ('int', '_HPyCapsule_key'):
        return 'int'
    if type_name == 'double' or type_name == 'void':
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

def get_jni_function_prefix(java_class_qname):
    return 'Java_' + java_class_qname.replace('.', '_') + '_'

AUTOGEN_CTX_INIT_JNI_H_FILE = 'autogen_ctx_init_jni.h'

class autogen_ctx_init_jni_h(GraalPyAutoGenFile):
    PATH = 'graalpython/com.oracle.graal.python.jni/src/' + AUTOGEN_CTX_INIT_JNI_H_FILE

    def generate(self):
        lines = []
        w = lines.append
        w(f'_HPy_HIDDEN int init_autogen_jni_ctx(JNIEnv *env, jclass clazz, HPyContext *{UCTX_ARG});')
        # emit the declarations for all the ctx_*_jni functions
        for func in self.api.functions:
            if func.name not in NO_WRAPPER:
                w(toC(get_trace_wrapper_node(func)) + ';')
        w('')
        return '\n'.join(lines)


class autogen_wrappers_jni(GraalPyAutoGenFile):
    PATH = 'graalpython/com.oracle.graal.python.jni/src/autogen_wrappers_jni.c'

    def generate(self):
        lines = []
        w = lines.append
        w('#include "hpy_jni.h"')
        w('#include "hpynative.h"')
        w('#include "hpy_log.h"')
        w(f'#include "{AUTOGEN_CTX_INIT_JNI_H_FILE}"')
        w('')
        w(f'#define TRAMPOLINE(FUN_NAME) {get_jni_function_prefix(JNI_HPY_BACKEND_CLASS)} ## FUN_NAME')
        w('')
        for func in self.api.functions:
            w(f'static jmethodID jniMethod_{func.ctx_name()};')
        w('')
        w(f'_HPy_HIDDEN int init_autogen_jni_ctx(JNIEnv *env, jclass clazz, HPyContext *{UCTX_ARG})')
        w('{')
        # TODO: initialize context handles
        # for var in self.api.variables:
        #     name = var.name
        #     w(f'    {UCTX_ARG}->{name} = ...;')
        for func in self.api.functions:
            self.gen_jni_method_init(w, func)
        w(f'    return 0;')
        w('}')
        w('')
        for func in self.api.functions:
            debug_wrapper = self.gen_trace_wrapper(func)
            if debug_wrapper:
                w(debug_wrapper)
                w('')
        return '\n'.join(lines)

    def gen_jni_method_init(self, w, func):
        if func.name in NO_WRAPPER:
            return
        node = get_trace_wrapper_node(func)
        name = func.ctx_name()

        # compute JNI signature
        jni_params = []
        assert node.type.args.params[0].name == "ctx"
        # skip the context parameter
        for p in node.type.args.params[1:]:
            param_type = toC(p.type)
            jni_params.append(get_jni_signature_type(param_type))
        jni_sig = "".join(jni_params)
        rettype = get_context_return_type(node, False)
        jni_ret_type = get_jni_signature_type(rettype)

        jname = name.replace('_', '')
        w(f'    {JNI_METHOD_PREFIX}{name} = (*env)->GetMethodID(env, clazz, "{jname}", "({jni_sig}){jni_ret_type}");')
        w(f'    if ({JNI_METHOD_PREFIX}{name} == NULL) {{')
        w(f'        LOGS("ERROR: Java method {jname} not found found !\\n");')
        w('        return 1;')
        w('    }')
        w(f'    {UCTX_ARG}->{name} = &{name}_jni;')

    def gen_trace_wrapper(self, func):
        if func.name in NO_DEFAULT_UPCALL_STUB:
            return

        assert not func.is_varargs()
        node = get_trace_wrapper_node(func)
        #typedecl = find_typedecl(func)
        #typedecl.declname = name
        const_return = get_return_constant(func)
        if const_return:
            make_void(node)
        signature = toC(node)
        rettype = get_context_return_type(node, const_return)

        param_names = []
        assert node.type.args.params[0].name == "ctx"
        # skip the context parameter
        for p in node.type.args.params[1:]:
            param_type = toC(p.type)
            cast_fun = get_cast_fun(param_type)
            param_names.append(f'{cast_fun}({p.name})')

        lines = []
        w = lines.append
        w(signature)
        w('{')

        #return_stmt = "return " if rettype != 'void' else ""
        #w(f'    {return_stmt}DO_UPCALL_{rettype.upper()}(CONTEXT_INSTANCE({UCTX_ARG}, {func.ctx_name()}, {params});')

        #print(f"######## rettype = {rettype}")

        suffix = '' if param_names else '0'
        all_params = [f'CONTEXT_INSTANCE({UCTX_ARG})', func.ctx_name()] + param_names
        params = ", ".join(all_params)
        if rettype == 'void':
            w(f'    DO_UPCALL_VOID{suffix}({params});')
        elif rettype == 'HPy':
            w(f'    return DO_UPCALL_HPY{suffix}({params});')
        elif type_is_pointer(rettype):
            w(f'    return ({rettype})DO_UPCALL_PTR{suffix}({params});')
        else:
            w(f'    return DO_UPCALL_{rettype.replace(" ", "_").upper()}{suffix}({params});')
        w('}')
        return '\n'.join(lines)

JNI_NO_UPCALL = tuple()

class autogen_ctx_jni_upcall_enum(AutoGenFilePart):
    """
    """
    INDENT = '        '
    PATH = 'graalpython/com.oracle.graal.python/src/' + java_qname_to_path(JNI_HPY_CONTEXT_PKG + JNI_HPY_BACKEND_CLASS)
    BEGIN_MARKER = INDENT + '// {{start jni upcalls}}\n'
    END_MARKER = INDENT + '// {{end jni upcalls}}\n'

    def generate(self, old):
        lines = []
        w = lines.append
        for func in self.api.functions:
            fname = func.name.replace('_', '')
            jname = fname[0].upper() + fname[1:]
            w(f'{self.INDENT}{jname}')
        return ',\n'.join(lines) + ';\n'


class autogen_svm_jni_upcall_config(AutoGenFilePart):
    """
    """
    INDENT = '        '
    PATH = 'graalpython/com.oracle.graal.python/src/com/oracle/graal/python/resources/jni-config.json'
    BEGIN_MARKER = '  "name":"com.oracle.graal.python.builtins.objects.cext.hpy.jni.GraalHPyJNIContext",\n  "methods":[\n'
    END_MARKER = ',\n    {"name":"getHPyDebugContext","parameterTypes":[] }\n  ],'

    def generate(self, old):
        lines = []
        w = lines.append
        for func in self.api.functions:
            node = func.node
            jname = func.ctx_name().replace('_', '')
            jni_params = []
            for p in node.type.args.params[1:]:
                jtype = get_java_signature_type(p.type)
                jni_params.append(f'"{jtype}"')
            w(f'    {{"name":"{jname}","parameterTypes":[{",".join(jni_params)}]}}')
        return ',\n'.join(lines)


class autogen_jni_upcall_method_stub(AutoGenFilePart):
    """
    Generates empty JNI upcall methods like
    'public long ctxLongFromLongLong(long v) { /* ... */ }'. This generator will
    not generate methods if they already exist.
    """
    INDENT = '    '
    PATH = 'graalpython/com.oracle.graal.python/src/' + java_qname_to_path(JNI_HPY_CONTEXT_PKG + JNI_HPY_BACKEND_CLASS)
    BEGIN_MARKER = INDENT + '// {{start ctx funcs}}\n'
    END_MARKER = INDENT + '// {{end ctx funcs}}\n'

    def generate(self, old):
        lines = [old]
        w = lines.append
        for func in self.api.functions:
            func_type = func.node.type

            # context function name w/o underscores, e.g., 'ctxGetItemi'
            jname = func.ctx_name().replace('_', '')

            # HPy API function name w/o underscores, e.g., 'HPyGetItemi'
            fname = func.name.replace('_', '')

            rettype = get_java_signature_type(func_type.type)
            if f' {jname}(' not in old:
                java_params = []
                for i, p in enumerate(func_type.args.params[1:]):
                    jtype = get_java_signature_type(p.type)
                    p_name = p.name if p.name else f'arg{i}'
                    java_params.append(f'{jtype} {p_name}')

                w(f'{self.INDENT}public {rettype} {jname}({", ".join(java_params)}) {{')
                w(f'{self.INDENT * 2}increment(HPyJNIUpcall.{fname});')
                w(f'{self.INDENT * 2}// TODO implement')
                w(f'{self.INDENT * 2}throw CompilerDirectives.shouldNotReachHere();')
                w(self.INDENT + '}')
                w('')
        return '\n'.join(lines)


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
            rettype = get_java_signature_type(hpyfunc.return_type())
            args = ['long target', 'long ctx']
            for i, param in enumerate(hpyfunc.params()[1:]):
                pname = param.name
                if pname is None:
                    pname = 'arg%d' % i
                jtype = get_java_signature_type(param.type)
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

generators = (autogen_ctx_init_jni_h,
              autogen_wrappers_jni,
              autogen_ctx_jni,
              autogen_ctx_call_jni,
              autogen_ctx_jni_upcall_enum,
              autogen_svm_jni_upcall_config,
              autogen_jni_upcall_method_stub)