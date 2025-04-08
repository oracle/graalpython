#define PY_SSIZE_T_CLEAN
#include <Python.h>
#ifdef MS_WIN32
# include <windows.h>
# include "misc_win32.h"
#else
# include <dlfcn.h>
// required for strncasecmp
#include <strings.h>
#endif
#include <stdio.h>
#include <inttypes.h>


#include "api.h"
#include "handles.h"
#include "hpy/version.h"
#include "hpy_debug.h"
#include "hpy_trace.h"
#include "hpy/runtime/ctx_module.h"

#ifdef PYPY_VERSION
#  error "Cannot build hpy.universal on top of PyPy. PyPy comes with its own version of it"
#endif
#ifdef GRAALVM_PYTHON
#  error "Cannot build hpy.universal on top of GraalPy. GraalPy comes with its own version of it"
#endif

static const char *hpy_mode_names[] = {
        "MODE_UNIVERSAL",
        "MODE_DEBUG",
        "MODE_TRACE",
        // "MODE_DEBUG_TRACE",
        // "MODE_TRACE_DEBUG",
        NULL
};

typedef enum {
    MODE_INVALID = -1,
    MODE_UNIVERSAL = 0,
    MODE_DEBUG = 1,
    MODE_TRACE = 2,
    /* We do currently not test the combinations of debug and trace mode, so we
       do not offer them right now. This may change in future. */
    // MODE_DEBUG_TRACE = 3,
    // MODE_TRACE_DEBUG = 4
} HPyMode;

typedef uint32_t (*VersionGetterFuncPtr)(void);
typedef HPyModuleDef* (*InitFuncPtr)(void);
typedef void (*InitContextFuncPtr)(HPyContext*);

static const char *init_prefix = "HPyInit";
static const char *init_ctx_prefix = "HPyInitGlobalContext_";

static inline int
_hpy_strncmp_ignore_case(const char *s0, const char *s1, size_t n)
{
#ifdef MS_WIN32
    return _strnicmp(s0, s1, n);
#else
    return strncasecmp(s0, s1, n);
#endif
}

static HPyContext * get_context(HPyMode mode)
{
    switch (mode)
    {
    case MODE_INVALID:
        return NULL;
    case MODE_DEBUG:
        return hpy_debug_get_ctx(&g_universal_ctx);
    case MODE_TRACE:
        return hpy_trace_get_ctx(&g_universal_ctx);
    // case MODE_DEBUG_TRACE:
    //     return hpy_debug_get_ctx(hpy_trace_get_ctx(&g_universal_ctx));
    // case MODE_TRACE_DEBUG:
    //     return hpy_trace_get_ctx(hpy_debug_get_ctx(&g_universal_ctx));
    default:
        return &g_universal_ctx;
    }
}

static PyObject *
get_encoded_name(PyObject *name) {
    PyObject *tmp;
    PyObject *encoded = NULL;
    PyObject *modname = NULL;
    Py_ssize_t name_len, lastdot;

    /* Get the short name (substring after last dot) */
    name_len = PyUnicode_GetLength(name);
    lastdot = PyUnicode_FindChar(name, '.', 0, name_len, -1);
    if (lastdot < -1) {
        return NULL;
    } else if (lastdot >= 0) {
        tmp = PyUnicode_Substring(name, lastdot + 1, name_len);
        if (tmp == NULL)
            return NULL;
        name = tmp;
        /* "name" now holds a new reference to the substring */
    } else {
        Py_INCREF(name);
    }

    /* Encode to ASCII */
    encoded = PyUnicode_AsEncodedString(name, "ascii", NULL);
    if (encoded != NULL) {
    } else {
        goto error;
    }

    /* Replace '-' by '_' */
    modname = PyObject_CallMethod(encoded, "replace", "cc", '-', '_');
    if (modname == NULL)
        goto error;

    Py_DECREF(name);
    Py_DECREF(encoded);
    return modname;
error:
    Py_DECREF(name);
    Py_XDECREF(encoded);
    return NULL;
}

static bool validate_abi_tag(const char *shortname, const char *soname,
                             uint32_t required_major_version, uint32_t required_minor_version) {
    char *substr = strstr(soname, ".hpy");
    if (substr != NULL) {
        substr += strlen(".hpy");
        if (*substr >= '0' && *substr <= '9') {
            // It is a number w/o sign and whitespace, we can now parse it with atoi
            uint32_t abi_tag = (uint32_t) atoi(substr);
            if (abi_tag == required_major_version) {
                return true;
            }
            PyErr_Format(PyExc_RuntimeError,
                         "HPy extension module '%s' at path '%s': mismatch between the "
                         "HPy ABI tag encoded in the filename and the major version requested "
                         "by the HPy extension itself. Major version tag parsed from "
                         "filename: %" PRIu32 ". Requested version: %" PRIu32 ".%" PRIu32 ".",
                         shortname, soname, abi_tag, required_major_version, required_minor_version);
            return false;
        }
    }
    PyErr_Format(PyExc_RuntimeError,
                 "HPy extension module '%s' at path '%s': could not find "
                 "HPy ABI tag encoded in the filename. The extension claims to be compiled with "
                 "HPy ABI version: %" PRIu32 ".%" PRIu32 ".",
                 shortname, soname, required_major_version, required_minor_version);
    return false;
}

static void dlsym_error(const char *soname, const char *symbol_name) {
    const char *error = dlerror();
    if (error == NULL)
        error = "no error message provided by the system";
    PyErr_Format(PyExc_RuntimeError,
                 "Error during loading of the HPy extension module at "
                 "path '%s' while trying to find symbol '%s'. Did you use"
                 "the HPy_MODINIT macro to register your module? Error "
                 "message from dlsym/WinAPI: %s", soname, symbol_name, error);
}

static PyObject *do_load(PyObject *name_unicode, PyObject *path, HPyMode mode, PyObject *spec)
{
    PyObject *name = NULL;
    PyObject *pathbytes = NULL;
    PyModuleDef *pydef = NULL;
    PyObject *py_mod = NULL;

    name = get_encoded_name(name_unicode);
    if (name == NULL) {
        goto error;
    }

    pathbytes = PyUnicode_EncodeFSDefault(path);
    if (pathbytes == NULL)
        goto error;
    const char *soname = PyBytes_AS_STRING(pathbytes);

    void *mylib = dlopen(soname, RTLD_NOW); // who closes this?
    if (mylib == NULL) {
        const char *error = dlerror();
        if (error == NULL)
            error = "no error message provided by the system";
        PyErr_Format(PyExc_RuntimeError,
                     "Error during loading of the HPy extension module at path "
                     "'%s'. Error message from dlopen/WinAPI: %s", soname, error);
        goto error;
    }

    const char *shortname = PyBytes_AS_STRING(name);
    char minor_version_symbol_name[258];
    char major_version_symbol_name[258];
    PyOS_snprintf(minor_version_symbol_name, sizeof(minor_version_symbol_name),
                  "get_required_hpy_minor_version_%.200s", shortname);
    PyOS_snprintf(major_version_symbol_name, sizeof(major_version_symbol_name),
                  "get_required_hpy_major_version_%.200s", shortname);
    void *minor_version_ptr = dlsym(mylib, minor_version_symbol_name);
    void *major_version_ptr = dlsym(mylib, major_version_symbol_name);
    if (minor_version_ptr == NULL || major_version_ptr == NULL) {
        const char *error = dlerror();
        if (error == NULL)
            error = "no error message provided by the system";
        PyErr_Format(PyExc_RuntimeError,
                     "Error during loading of the HPy extension module at path "
                     "'%s'. Cannot locate the required minimal HPy versions as symbols '%s' and `%s`. "
                     "Error message from dlopen/WinAPI: %s",
                     soname, minor_version_symbol_name, major_version_symbol_name, error);
        goto error;
    }
    uint32_t required_minor_version = ((VersionGetterFuncPtr) minor_version_ptr)();
    uint32_t required_major_version = ((VersionGetterFuncPtr) major_version_ptr)();
    if (required_major_version != HPY_ABI_VERSION || required_minor_version > HPY_ABI_VERSION_MINOR) {
        // For now, we have only one major version, but in the future at this
        // point we would decide which HPyContext to create
        PyErr_Format(PyExc_RuntimeError,
                     "HPy extension module '%s' requires unsupported version of the HPy runtime. "
                     "Requested version: %" PRIu32 ".%" PRIu32 ". Current HPy version: %" PRIu32 ".%" PRIu32 ".",
                     shortname, required_major_version, required_minor_version,
                     HPY_ABI_VERSION, HPY_ABI_VERSION_MINOR);
        goto error;
    }

    // Sanity check of the tag in the shared object filename
    if (!validate_abi_tag(shortname, soname, required_major_version, required_minor_version)) {
        goto error;
    }

    HPyContext *ctx = get_context(mode);
    if (ctx == NULL)
        goto error;

    char init_ctx_name[258];
    PyOS_snprintf(init_ctx_name, sizeof(init_ctx_name), "%.20s_%.200s",
                  init_ctx_prefix, shortname);
    void *initctxfn = dlsym(mylib, init_ctx_name);
    if (initctxfn == NULL) {
        dlsym_error(soname, init_ctx_name);
        goto error;
    }
    ((InitContextFuncPtr)initctxfn)(ctx);

    char init_name[258];
    PyOS_snprintf(init_name, sizeof(init_name), "%.20s_%.200s",
                  init_prefix, shortname);
    void *initfn = dlsym(mylib, init_name);
    if (initfn == NULL) {
        dlsym_error(soname, init_name);
        goto error;
    }

    HPyModuleDef* hpydef = ((InitFuncPtr)initfn)();
    if (hpydef == NULL) {
        PyErr_Format(PyExc_RuntimeError,
                     "Error during loading of the HPy extension module at "
                     "path '%s'. Function '%s' returned NULL.", soname, init_name);
        goto error;
    }

    pydef = _HPyModuleDef_CreatePyModuleDef(hpydef);
    if (pydef == NULL) {
        goto error;
    }

    py_mod = PyModule_FromDefAndSpec(pydef, spec);
    if (py_mod == NULL)
        goto error;

    if (PyModule_Check(py_mod)) {
        if (PyModule_ExecDef(py_mod, pydef) != 0)
            goto error;
    }

    Py_XDECREF(name);
    Py_XDECREF(pathbytes);
    return py_mod;
error:
    Py_XDECREF(py_mod);
    if (pydef != NULL)
        PyMem_Free(pydef);
    Py_XDECREF(name);
    Py_XDECREF(pathbytes);
    return NULL;
}

static PyObject *load(PyObject *self, PyObject *args, PyObject *kwargs)
{
    static char *kwlist[] = {"name", "path", "spec", "debug", "mode", NULL};
    PyObject *name_unicode;
    PyObject *path;
    PyObject *spec;
    int debug = 0;
    int mode = -1;
    if (!PyArg_ParseTupleAndKeywords(args, kwargs, "OOO|pi", kwlist,
                                     &name_unicode, &path, &spec, &debug, &mode)) {
        return NULL;
    }
    HPyMode hmode = debug ? MODE_DEBUG : MODE_UNIVERSAL;
    // 'mode' just overwrites 'debug'
    if (mode > 0)
    {
        hmode = (HPyMode) mode;
    }
    return do_load(name_unicode, path, hmode, spec);
}

static HPyMode get_mode_from_string(const char *s, Py_ssize_t n)
{
    if (_hpy_strncmp_ignore_case("debug", s, n) == 0)
        return MODE_DEBUG;
    if (_hpy_strncmp_ignore_case("trace", s, n) == 0)
        return MODE_TRACE;
    // if (_hpy_strncmp_ignore_case("debug+trace", s, n) == 0)
    //     return MODE_DEBUG_TRACE;
    // if (_hpy_strncmp_ignore_case("trace+debug", s, n) == 0)
    //     return MODE_TRACE_DEBUG;
    if (_hpy_strncmp_ignore_case("universal", s, n) == 0)
        return MODE_UNIVERSAL;
    return MODE_INVALID;
}

/*
 * A little helper that does a fast-path if 'mapping' is a dict.
 */
static int mapping_get_item(PyObject *mapping, const char *skey, PyObject **value)
{
    PyObject *key = PyUnicode_FromString(skey);
    if (key == NULL)
        return 1;

    // fast-path if 'mapping' is a dict
    if (PyDict_Check(mapping)) {
        *value = PyDict_GetItem(mapping, key);
        Py_DECREF(key);
        /* 'NULL' means, the key is not present in the dict, so just return
           'NULL'. Since PyDict_GetItem does not set an error, we don't need to
           clear any error. */
    } else {
        *value = PyObject_GetItem(mapping, key);
        Py_DECREF(key);
        if (*value == NULL) {
            if (PyErr_ExceptionMatches(PyExc_KeyError)) {
                PyErr_Clear();
            } else {
                return 1;
            }
        }
    }
    return 0;
}

/*
 * HPY_MODE := MODE | (MODULE_NAME ':' MODE { ',' MODULE_NAME ':' MODE })
 * MODULE_NAME := IDENTIFIER
 * MODE := 'debug' | 'trace' | 'universal'
 */
static HPyMode get_hpy_mode_from_environ(const char *s_name, PyObject *env)
{
    PyObject *value;
    Py_ssize_t size;
    HPyMode res;
    const char *s_value;

    if (mapping_get_item(env, "HPY", &value)) {
        return MODE_INVALID;
    }

    /* 'value == NULL' is not an error; this just means that the key was not
       present in 'env'. */
    if (value == NULL) {
        return MODE_UNIVERSAL;
    }

    s_value = PyUnicode_AsUTF8AndSize(value, &size);
    if (s_value == NULL) {
        Py_DECREF(value);
        return MODE_INVALID;
    }
    res = MODE_INVALID;
    char *colon = strchr(s_value, ':');
    if (colon) {
        // case 2: modes are specified per module
        char *name_start = (char *)s_value;
        char *comma;
        size_t mode_len;
        while (colon) {
            comma = strchr(colon + 1, ',');
            if (comma) {
                mode_len = comma - colon - 1;
            } else {
                mode_len = (s_value + size) - colon - 1;
            }
            if (strncmp(s_name, name_start, colon - name_start) == 0) {
                res = get_mode_from_string(colon + 1, mode_len);
                break;
            }
            if (comma) {
                name_start = comma + 1;
                colon = strchr(name_start, ':');
            } else {
                colon = NULL;
            }
        }
    } else {
        // case 1: mode was globally specified
        res = get_mode_from_string(s_value, size);
    }

    if (res == MODE_INVALID)
        PyErr_Format(PyExc_ValueError, "invalid HPy mode: %.50s", s_value);
    Py_DECREF(value);
    return res;
}

static PyObject *
load_bootstrap(PyObject *self, PyObject *args, PyObject *kwargs)
{
    static char *kwlist[] = {"name", "ext_name", "package", "file", "loader", "spec",
                                 "env", NULL};
    PyObject *module_name, *name, *package, *file, *loader, *spec, *env;
    PyObject *log_obj, *m;
    HPyMode hmode;
    const char *s_module_name, *log_msg;
    if (!PyArg_ParseTupleAndKeywords(args, kwargs, "OOOOOOO", kwlist,
                                     &module_name, &name, &package, &file,
                                     &loader, &spec, &env)) {
        return NULL;
    }

    s_module_name = PyUnicode_AsUTF8AndSize(module_name, NULL);
    if (s_module_name == NULL) {
        return NULL;
    }

    hmode = get_hpy_mode_from_environ(s_module_name, env);
    if (hmode == MODE_INVALID)
        return NULL;

    if (mapping_get_item(env, "HPY_LOG", &log_obj))
        return NULL;

    if (log_obj != NULL) {
        Py_DECREF(log_obj);
        switch (hmode) {
        case MODE_DEBUG:
            log_msg = " with a debug context";
            break;
        case MODE_TRACE:
            log_msg = " with a trace context";
            break;
        case MODE_UNIVERSAL:
            log_msg = "";
            break;
        default:
            // that's not possible but required for the compiler
            return NULL;
        }
        PySys_FormatStdout("Loading '%.200s' in HPy universal mode%.200s\n",
                s_module_name, log_msg);
    }

    m = do_load(module_name, file, hmode, spec);
    if (m == NULL)
        return NULL;

    PyObject_SetAttrString(m, "__file__", file);
    PyObject_SetAttrString(m, "__loader__", loader);
    PyObject_SetAttrString(m, "__name__", name);
    PyObject_SetAttrString(m, "__package__", package);
    PyObject_SetAttrString(spec, "origin", file);
    PyObject_SetAttrString(m, "__spec__", spec);

    return m;
}

static PyObject *get_version(PyObject *self, PyObject *ignored)
{
    return Py_BuildValue("ss", HPY_VERSION, HPY_GIT_REVISION);
}

PyDoc_STRVAR(load_bootstrap_doc, "Internal function intended to be used by "
        "the stub loader. This function will honor env var 'HPY' and correctly"
        " set the attributes of the module.");

static PyMethodDef HPyMethods[] = {
    {"load", (PyCFunction)load, METH_VARARGS | METH_KEYWORDS,
     ("Load a ." HPY_ABI_TAG ".so file")},
    {"_load_bootstrap", (PyCFunction)load_bootstrap,
     METH_VARARGS | METH_KEYWORDS, load_bootstrap_doc},
    {"get_version", (PyCFunction)get_version, METH_NOARGS,
     "Return a tuple ('version', 'git revision')"},
    {NULL, NULL, 0, NULL}
};


static int exec_module(PyObject *mod);
static PyModuleDef_Slot hpymodule_slots[] = {
    {Py_mod_exec, exec_module},
    {0, NULL},
};

static struct PyModuleDef hpy_pydef = {
    PyModuleDef_HEAD_INIT,
    .m_name = "hpy.universal",
    .m_doc = "HPy universal runtime for CPython",
    .m_size = 0,
    .m_methods = HPyMethods,
    .m_slots = hpymodule_slots,
};

static int initialize_module(HPyContext *ctx, PyObject *hpy_universal, const char* name,
         const char *full_name, HPyModuleDef *hpydef,
         PyObject* spec_from_file_and_location, PyObject *location)
{
    PyObject *spec = NULL, *new_mod = NULL;
    int result = -1;

    spec = PyObject_CallFunction(spec_from_file_and_location, "sO", full_name, location);
    PyModuleDef *pydef = _HPyModuleDef_CreatePyModuleDef(hpydef);
    new_mod = PyModule_FromDefAndSpec(pydef, spec);
    if (new_mod == NULL)
        goto cleanup;

    if (PyModule_ExecDef(new_mod, pydef) != 0)
        goto cleanup;

    Py_INCREF(new_mod); // PyModule_AddObject steals the reference
    if (PyModule_AddObject(hpy_universal, name, new_mod) < 0)
        goto cleanup;

    result = 0;

cleanup:
    Py_XDECREF(new_mod);
    Py_XDECREF(spec);
    return result;
}

// module initialization function
int exec_module(PyObject* mod) {
    HPyContext *ctx  = &g_universal_ctx;

    PyObject *importlib_util = PyImport_ImportModule("importlib.util");
    if (importlib_util == NULL)
        return -1;

    PyObject *spec_from_file_and_location = PyObject_GetAttrString(importlib_util, "spec_from_file_location");
    Py_DecRef(importlib_util);
    if (spec_from_file_and_location == NULL)
        return -1;

    PyObject *current_mod_spec = PyObject_GetAttrString(mod, "__spec__");
    if (current_mod_spec == NULL)
        return -1;

    PyObject *location = PyObject_GetAttrString(current_mod_spec, "origin");
    if (location == NULL)
        return -1;

    HPyInitGlobalContext__debug(ctx);
    int result = initialize_module(ctx, mod, "_debug", "hpy.debug._debug",
                      HPyInit__debug(), spec_from_file_and_location, location);
    if (result != 0)
        return result;

    HPyInitGlobalContext__trace(ctx);
    result = initialize_module(ctx, mod, "_trace", "hpy.trace._trace",
                      HPyInit__trace(), spec_from_file_and_location, location);
    if (result != 0)
        return result;

    for (int i=0; hpy_mode_names[i]; i++)
    {
        if (PyModule_AddIntConstant(mod, hpy_mode_names[i], i) < 0)
            return -1;
    }

    return 0;
}

static void init_universal_ctx(HPyContext *ctx)
{
    if (!HPy_IsNull(ctx->h_None))
        // already initialized
        return;

    // XXX this code is basically the same as found in cpython/hpy.h. We
    // should probably share and/or autogenerate both versions
    /* Constants */
    ctx->h_None = _py2h(Py_None);
    ctx->h_True = _py2h(Py_True);
    ctx->h_False = _py2h(Py_False);
    ctx->h_NotImplemented = _py2h(Py_NotImplemented);
    ctx->h_Ellipsis = _py2h(Py_Ellipsis);
    /* Exceptions */
    ctx->h_BaseException = _py2h(PyExc_BaseException);
    ctx->h_Exception = _py2h(PyExc_Exception);
    ctx->h_StopAsyncIteration = _py2h(PyExc_StopAsyncIteration);
    ctx->h_StopIteration = _py2h(PyExc_StopIteration);
    ctx->h_GeneratorExit = _py2h(PyExc_GeneratorExit);
    ctx->h_ArithmeticError = _py2h(PyExc_ArithmeticError);
    ctx->h_LookupError = _py2h(PyExc_LookupError);
    ctx->h_AssertionError = _py2h(PyExc_AssertionError);
    ctx->h_AttributeError = _py2h(PyExc_AttributeError);
    ctx->h_BufferError = _py2h(PyExc_BufferError);
    ctx->h_EOFError = _py2h(PyExc_EOFError);
    ctx->h_FloatingPointError = _py2h(PyExc_FloatingPointError);
    ctx->h_OSError = _py2h(PyExc_OSError);
    ctx->h_ImportError = _py2h(PyExc_ImportError);
    ctx->h_ModuleNotFoundError = _py2h(PyExc_ModuleNotFoundError);
    ctx->h_IndexError = _py2h(PyExc_IndexError);
    ctx->h_KeyError = _py2h(PyExc_KeyError);
    ctx->h_KeyboardInterrupt = _py2h(PyExc_KeyboardInterrupt);
    ctx->h_MemoryError = _py2h(PyExc_MemoryError);
    ctx->h_NameError = _py2h(PyExc_NameError);
    ctx->h_OverflowError = _py2h(PyExc_OverflowError);
    ctx->h_RuntimeError = _py2h(PyExc_RuntimeError);
    ctx->h_RecursionError = _py2h(PyExc_RecursionError);
    ctx->h_NotImplementedError = _py2h(PyExc_NotImplementedError);
    ctx->h_SyntaxError = _py2h(PyExc_SyntaxError);
    ctx->h_IndentationError = _py2h(PyExc_IndentationError);
    ctx->h_TabError = _py2h(PyExc_TabError);
    ctx->h_ReferenceError = _py2h(PyExc_ReferenceError);
    ctx->h_SystemError = _py2h(PyExc_SystemError);
    ctx->h_SystemExit = _py2h(PyExc_SystemExit);
    ctx->h_TypeError = _py2h(PyExc_TypeError);
    ctx->h_UnboundLocalError = _py2h(PyExc_UnboundLocalError);
    ctx->h_UnicodeError = _py2h(PyExc_UnicodeError);
    ctx->h_UnicodeEncodeError = _py2h(PyExc_UnicodeEncodeError);
    ctx->h_UnicodeDecodeError = _py2h(PyExc_UnicodeDecodeError);
    ctx->h_UnicodeTranslateError = _py2h(PyExc_UnicodeTranslateError);
    ctx->h_ValueError = _py2h(PyExc_ValueError);
    ctx->h_ZeroDivisionError = _py2h(PyExc_ZeroDivisionError);
    ctx->h_BlockingIOError = _py2h(PyExc_BlockingIOError);
    ctx->h_BrokenPipeError = _py2h(PyExc_BrokenPipeError);
    ctx->h_ChildProcessError = _py2h(PyExc_ChildProcessError);
    ctx->h_ConnectionError = _py2h(PyExc_ConnectionError);
    ctx->h_ConnectionAbortedError = _py2h(PyExc_ConnectionAbortedError);
    ctx->h_ConnectionRefusedError = _py2h(PyExc_ConnectionRefusedError);
    ctx->h_ConnectionResetError = _py2h(PyExc_ConnectionResetError);
    ctx->h_FileExistsError = _py2h(PyExc_FileExistsError);
    ctx->h_FileNotFoundError = _py2h(PyExc_FileNotFoundError);
    ctx->h_InterruptedError = _py2h(PyExc_InterruptedError);
    ctx->h_IsADirectoryError = _py2h(PyExc_IsADirectoryError);
    ctx->h_NotADirectoryError = _py2h(PyExc_NotADirectoryError);
    ctx->h_PermissionError = _py2h(PyExc_PermissionError);
    ctx->h_ProcessLookupError = _py2h(PyExc_ProcessLookupError);
    ctx->h_TimeoutError = _py2h(PyExc_TimeoutError);
    /* Warnings */
    ctx->h_Warning = _py2h(PyExc_Warning);
    ctx->h_UserWarning = _py2h(PyExc_UserWarning);
    ctx->h_DeprecationWarning = _py2h(PyExc_DeprecationWarning);
    ctx->h_PendingDeprecationWarning = _py2h(PyExc_PendingDeprecationWarning);
    ctx->h_SyntaxWarning = _py2h(PyExc_SyntaxWarning);
    ctx->h_RuntimeWarning = _py2h(PyExc_RuntimeWarning);
    ctx->h_FutureWarning = _py2h(PyExc_FutureWarning);
    ctx->h_ImportWarning = _py2h(PyExc_ImportWarning);
    ctx->h_UnicodeWarning = _py2h(PyExc_UnicodeWarning);
    ctx->h_BytesWarning = _py2h(PyExc_BytesWarning);
    ctx->h_ResourceWarning = _py2h(PyExc_ResourceWarning);
    /* Types */
    ctx->h_BaseObjectType = _py2h((PyObject *)&PyBaseObject_Type);
    ctx->h_TypeType = _py2h((PyObject *)&PyType_Type);
    ctx->h_BoolType = _py2h((PyObject *)&PyBool_Type);
    ctx->h_LongType = _py2h((PyObject *)&PyLong_Type);
    ctx->h_FloatType  = _py2h((PyObject *)&PyFloat_Type);
    ctx->h_UnicodeType = _py2h((PyObject *)&PyUnicode_Type);
    ctx->h_TupleType = _py2h((PyObject *)&PyTuple_Type);
    ctx->h_ListType = _py2h((PyObject *)&PyList_Type);
    ctx->h_ComplexType = _py2h((PyObject *)&PyComplex_Type);
    ctx->h_BytesType = _py2h((PyObject *)&PyBytes_Type);
    ctx->h_MemoryViewType = _py2h((PyObject *)&PyMemoryView_Type);
    ctx->h_CapsuleType = _py2h((PyObject *)&PyCapsule_Type);
    ctx->h_SliceType = _py2h((PyObject *)&PySlice_Type);
    ctx->h_DictType = _py2h((PyObject *)&PyDict_Type);
    /* Reflection */
    ctx->h_Builtins = _py2h(PyEval_GetBuiltins());
}


PyMODINIT_FUNC
PyInit_universal(void)
{
    init_universal_ctx(&g_universal_ctx);
    PyObject *mod = PyModuleDef_Init(&hpy_pydef);
    return mod;
}
