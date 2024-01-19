# Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import os
from importlib import invalidate_caches
from io import StringIO
from pathlib import Path
from string import Formatter

import gc
import sys

DIR = Path(__file__).parent.absolute()

GRAALPYTHON = sys.implementation.name == "graalpy"

IS_MANAGED_LAUNCHER = not GRAALPYTHON or __graalpython__.is_managed_launcher()

def assert_raises(err, fn, *args, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err:
        raised = True
    assert raised


if sys.implementation.name == 'graalpy':
    is_native_object = getattr(__graalpython__, 'is_native_object', None)
    if not is_native_object:
        raise RuntimeError("Needs to be run with --python.EnableDebuggingBuiltins")
else:
    def is_native_object(obj):
        return True


def unhandled_error_compare(x, y):
    if (isinstance(x, BaseException) and isinstance(y, BaseException)):
        return type(x) == type(y)
    else:
        return x == y

def unhandled_error_compare_with_message(x, y):
    if (isinstance(x, BaseException) and isinstance(y, BaseException)):
        return type(x) == type(y) and str(x) == str(y)
    else:
        return x == y

class CPyExtTestCase():

    def setUpClass(self):
        for typ in type(self).mro():
            for k, v in typ.__dict__.items():
                if k.startswith("test_"):
                    modname = k.replace("test_", "")
                    if k.startswith("test_graalpython_"):
                        if not GRAALPYTHON:
                            continue
                        else:
                            modname = k.replace("test_graalpython_", "")
                    self.compile_module(modname)


def ccompile(self, name):
    from distutils.core import setup, Extension
    from distutils.sysconfig import get_config_var
    from hashlib import sha256
    EXT_SUFFIX = get_config_var("EXT_SUFFIX")

    source_file = DIR / f'{name}.c'
    file_not_empty(source_file)

    # compute checksum of source file
    m = sha256()
    with open(source_file,"rb") as f:
        # read 4K blocks
        for block in iter(lambda: f.read(4096),b""):
            m.update(block)
    cur_checksum = m.hexdigest()

    # see if there is already a checksum file
    checksum_file = DIR / f'{name}{EXT_SUFFIX}.sha256'
    available_checksum = ""
    if checksum_file.exists():
        # read checksum file
        with open(checksum_file, "r") as f:
            available_checksum = f.readline()

    # note, the suffix is already a string like '.so'
    lib_file = DIR / f'{name}{EXT_SUFFIX}'

    # Compare checksums and only re-compile if different.
    # Note: It could be that the C source file's checksum didn't change but someone
    # manually deleted the shared library file.
    if available_checksum != cur_checksum or not lib_file.exists():
        module = Extension(name, sources=[str(source_file)])
        verbosity = '--verbose' if sys.flags.verbose else '--quiet'
        args = [verbosity, 'build', 'install_lib', '-f', f'--install-dir={DIR}', 'clean']
        setup(
            script_name='setup',
            script_args=args,
            name=name,
            version='1.0',
            description='',
            ext_modules=[module]
        )

        # write new checksum
        with open(checksum_file, "w") as f:
            f.write(cur_checksum)

        # IMPORTANT:
        # Invalidate caches after creating the native module.
        # FileFinder caches directory contents, and the check for directory
        # changes has whole-second precision, so it can miss quick updates.
        invalidate_caches()

    # ensure file was really written
    if GRAALPYTHON:
        file_not_empty(lib_file)


def file_not_empty(path):
    for i in range(3):
        try:
            stat_result = os.stat(path)
            if stat_result[6] != 0:
                return
        except FileNotFoundError:
            pass
    raise SystemError("file %s not available" % path)

resulttype_mapping = {'s': 'const char *', 'y': 'const char *', 'z': 'const char *', 'u': 'const wchar_t *', 'U': 'const char *', 
                      'i': 'int', 'b': 'char', 'h': 'short int', 'l': 'long int', 'B': 'unsigned char', 'H': 'unsigned short int',
                      'I': 'unsigned int', 'k': 'unsigned long', 'L': 'long long', 'K': 'unsigned long long', 'n': 'Py_ssize_t', 
                      'c': 'char', 'C': 'int', 'd': 'double', 'f': 'float', 'D': 'Py_complex *', 'O': 'PyObject *', 
                      'S': 'PyObject *', 'N': 'PyObject *'}

c_template = """
#define PY_SSIZE_T_CLEAN
#include <Python.h>
{defines}

{customcode}

static PyObject* test_{capifunction}(PyObject* module, PyObject* args) {{
    PyObject* ___arg;
    {argumentdeclarations};
    if (!PyArg_ParseTuple(args, "O", &___arg)) {{
        return NULL;
    }}

#ifdef SINGLEARG
    {singleargumentname} = ___arg;
#else
#ifndef NOARGS
    if (!PyArg_ParseTuple(___arg, "{argspec}", {derefargumentnames})) {{
        return NULL;
    }}
#endif
#endif

    {resulttype} res = {callfunction}({argumentnames});
    if (PyErr_Occurred()) {{
        return NULL;
    }}
    return Py_BuildValue("{resultspec}", res);
}}

static PyMethodDef TestMethods[] = {{
    {{"test_{capifunction}", test_{capifunction}, METH_VARARGS, ""}},
    {{NULL, NULL, 0, NULL}}        /* Sentinel */
}};

static PyModuleDef testmodule = {{
    PyModuleDef_HEAD_INIT,
    "{capifunction}",
    "test module",
    -1,
    TestMethods,
    NULL, NULL, NULL, NULL
}};

PyMODINIT_FUNC
PyInit_{capifunction}(void)
{{
    return PyModule_Create(&testmodule);
}}
"""

c_template_void = """
#include <Python.h>
{defines}

{customcode}

static PyObject* test_{capifunction}(PyObject* module, PyObject* args) {{
    PyObject* ___arg;
    {argumentdeclarations};
#ifndef NOARGS
    if (!PyArg_ParseTuple(args, "O", &___arg)) {{
        return NULL;
    }}

    if (strlen("{argspec}") > 0) {{
        if (!PyArg_ParseTuple(___arg, "{argspec}", {derefargumentnames})) {{
            return NULL;
        }}
    }}
#ifdef SINGLEARG
    else {{
        {singleargumentname} = ___arg;
    }}
#endif
#endif
    {callfunction}({argumentnames});
    return Py_BuildValue("{resultspec}", {resultval});
}}

static PyMethodDef TestMethods[] = {{
    {{"test_{capifunction}", test_{capifunction}, METH_VARARGS, ""}},
    {{NULL, NULL, 0, NULL}}        /* Sentinel */
}};

static PyModuleDef testmodule = {{
    PyModuleDef_HEAD_INIT,
    "{capifunction}",
    "test module",
    -1,
    TestMethods,
    NULL, NULL, NULL, NULL
}};

PyMODINIT_FUNC
PyInit_{capifunction}(void)
{{
    return PyModule_Create(&testmodule);
}}
"""

c_template_multi_res = """
#include <Python.h>
{defines}

{customcode}

static PyObject* test_{capifunction}(PyObject* module, PyObject* args) {{
    PyObject* ___arg;
    {argumentdeclarations};
    {resultvardeclarations}
    {resulttype} res;

    if (!PyArg_ParseTuple(args, "O", &___arg)) {{
        return NULL;
    }}

    if (strlen("{argspec}") > 0) {{
        if (!PyArg_ParseTuple(___arg, "{argspec}", {derefargumentnames})) {{
            return NULL;
        }}
    }}
#ifdef SINGLEARG
    else {{
        {singleargumentname} = ___arg;
    }}
#endif

    res = {callfunction}({argumentnames}{resultvarlocations});

    return Py_BuildValue("{resultspec}", res {resultvarnames});
}}

static PyMethodDef TestMethods[] = {{
    {{"test_{capifunction}", test_{capifunction}, METH_VARARGS, ""}},
    {{NULL, NULL, 0, NULL}}        /* Sentinel */
}};

static PyModuleDef testmodule = {{
    PyModuleDef_HEAD_INIT,
    "{capifunction}",
    "test module",
    -1,
    TestMethods,
    NULL, NULL, NULL, NULL
}};

PyMODINIT_FUNC
PyInit_{capifunction}(void)
{{
    return PyModule_Create(&testmodule);
}}
"""


class CPyExtFunction():

    def __init__(self, pfunc, parameters, template=c_template, cmpfunc=None, stderr_validator=None, **kwargs):
        self.template = template
        self.pfunc = pfunc
        self.parameters = parameters
        kwargs["name"] = kwargs["name"] if "name" in kwargs else None
        self.name = kwargs["name"]
        if "code" in kwargs:
            kwargs["customcode"] = kwargs["code"]
            del kwargs["code"]
        else:
            kwargs["customcode"] = ""
        kwargs["argspec"] = kwargs["argspec"] if "argspec" in kwargs else ""
        kwargs["arguments"] = kwargs["arguments"] if "arguments" in kwargs else ["PyObject* argument"]
        kwargs["parseargs"] = kwargs["parseargs"] if "parseargs" in kwargs else kwargs["arguments"]
        kwargs["resultspec"] = kwargs["resultspec"] if "resultspec" in kwargs else "O"
        kwargs["resulttype"] = kwargs["resulttype"] if "resulttype" in kwargs else resulttype_mapping[kwargs["resultspec"]]
        self.formatargs = kwargs
        self.cmpfunc = cmpfunc or self.do_compare
        self.stderr_validator = stderr_validator

    def do_compare(self, x, y):
        if isinstance(x, BaseException):
            x = repr(x)
        if isinstance(y, BaseException):
            y = repr(y)
        return x == y

    def create_module(self, name=None):
        fargs = self.formatargs
        if name:
            fargs["capifunction"] = name
        elif "name" in fargs:
            fargs["capifunction"] = fargs["name"]
            del fargs["name"]
        self.name = fargs["capifunction"]

        self._insert(fargs, "argumentdeclarations", ";".join(fargs["parseargs"]))
        self._insert(fargs, "argumentnames", ", ".join(arg.rpartition(" ")[2] for arg in fargs["arguments"]))
        self._insert(fargs, "singleargumentname", fargs["arguments"][0].rpartition(" ")[2] if fargs["arguments"] else "")
        self._insert(fargs, "derefargumentnames", ", ".join("&" + arg.rpartition(" ")[2].partition("=")[0] for arg in fargs["arguments"]))
        self._insert(fargs, "callfunction", fargs["capifunction"])
        if len(fargs["argspec"]) == 0 and len(fargs["arguments"]) == 0:
            fargs["defines"] = "#define NOARGS"
        elif len(fargs["argspec"]) == 0:
            fargs["defines"] = "#define SINGLEARG"
        else:
            fargs["defines"] = ""

        code = self.template.format(**fargs)

        with open(DIR / f'{self.name}.c', "wb", buffering=0) as f:
            f.write(bytes(code, 'utf-8'))

    def _insert(self, d, name, default_value):
        d[name] = d.get(name, default_value)

    def __repr__(self):
        return "<CPyExtFunction %s>" % self.name

    def test(self):
        sys.path.insert(0, str(DIR))
        try:
            cmodule = __import__(self.name)
        finally:
            sys.path.pop(0)
        ctest = getattr(cmodule, "test_%s" % self.name)
        cargs = self.parameters()
        pargs = self.parameters()
        for i in range(len(cargs)):
            if self.stderr_validator:
                real_stderr = sys.stderr
                sys.stderr = StringIO()
            try:
                cresult = ctest(cargs[i])
            except BaseException as e:
                cresult = e
            else:
                if self.stderr_validator:
                    s = sys.stderr.getvalue()
                    assert self.stderr_validator(cargs[i], s), f"captured stderr didn't match expectations. Stderr: {s}"
            finally:
                if self.stderr_validator:
                    sys.stderr = real_stderr
            try:
                presult = self.pfunc(pargs[i])
            except BaseException as e:
                presult = e

            if not self.cmpfunc:
                assert cresult == presult, ("%r == %r in %s(%s)" % (cresult, presult, self.name, pargs[i]))
            else:
                assert self.cmpfunc(cresult, presult), ("%r == %r in %s(%s)" % (cresult, presult, self.name, pargs[i]))
        gc.collect()

    def __get__(self, instance, typ=None):
        if typ is None:
            return self
        else:
            CPyExtFunction.test.__name__ = self.name
            CPyExtFunction.test.__qualname__ = f'{CPyExtFunction.__name__}.test_{self.name}'
            return self.test


class CPyExtFunctionOutVars(CPyExtFunction):
    '''
    Some native function have output vars, i.e., take pointers to variables where to store results.
    This class supports this.
    Set 'resultvars' to declare the output vars.
    '''

    def __init__(self, pfunc, parameters, template=c_template_multi_res, **kwargs):
        super(CPyExtFunctionOutVars, self).__init__(pfunc, parameters, **kwargs)
        self.template = template

    def create_module(self, name=None):
        fargs = self.formatargs
        if "resultvars" not in fargs:
            fargs["resultvars"] = ""

        if "resultvardeclarations" not in fargs:
            if len(fargs["resultvars"]):
                fargs["resultvardeclarations"] = ";".join(fargs["resultvars"]) + ";"
            else:
                fargs["resultvardeclarations"] = ""
        if "resultvarnames" not in fargs:
            if len(fargs["resultvars"]):
                fargs["resultvarnames"] = ", ".join(arg.rpartition(" ")[2] for arg in fargs["resultvars"])
            else:
                fargs["resultvarnames"] = ""
        if len(fargs["resultvarnames"]) and not fargs["resultvarnames"].startswith(","):
            fargs["resultvarnames"] = ", " + fargs["resultvarnames"]

        if "resultvarlocations" not in fargs:
                    fargs["resultvarlocations"] = ", ".join("&" + arg.rpartition(" ")[2] for arg in fargs["resultvars"])
        if "resulttype" not in fargs:
                    fargs["resulttype"] = "void*"
        if len(fargs["resultvarlocations"]) and not fargs["resultvarlocations"].startswith(","):
            fargs["resultvarlocations"] = ", " + fargs["resultvarlocations"]
        self._insert(fargs, "customcode", "")
        super(CPyExtFunctionOutVars, self).create_module(name)


class CPyExtFunctionVoid(CPyExtFunction):

    def __init__(self, pfunc, parameters, template=c_template_void, **kwargs):
        super(CPyExtFunctionVoid, self).__init__(pfunc, parameters, **kwargs)
        self.template = template

    def create_module(self, name=None):
        fargs = self.formatargs
        if "resultval" not in fargs:
            fargs["resultval"] = "Py_None"
        super(CPyExtFunctionVoid, self).create_module(name)


class UnseenFormatter(Formatter):

    def get_value(self, key, args, kwds):
        if isinstance(key, str):
            try:
                return kwds[key]
            except KeyError:
                return 0
        else:
            return Formatter.get_value(key, args, kwds)


def CPyExtType(name, code='', **kwargs):
    template = """
    #include "Python.h"
    /* structmember.h is not included by default in Python.h */
    #include "structmember.h"

    #if !GRAALVM_PYTHON && (PY_VERSION_HEX < 0x03090000)
    #define Py_SET_REFCNT(ob, v) ((_PyObject_CAST(ob)->ob_refcnt = (v)))
    #define Py_SET_TYPE(ob, v)   ((_PyObject_CAST(ob)->ob_type) = (v))
    #define Py_SET_SIZE(ob, v)   ((_PyVarObject_CAST(ob)->ob_size = (Py_ssize_t) (v)))
    #endif

    {includes}

    typedef struct {{
        {struct_base}
        {cmembers}
    }} {name}Object;

    {code}

    static PyNumberMethods {name}_number_methods = {{
        {nb_add},
        {nb_subtract},
        {nb_multiply},
        {nb_remainder},
        {nb_divmod},
        {nb_power},
        {nb_negative},
        {nb_positive},
        {nb_absolute},
        {nb_bool},
        {nb_invert},
        {nb_lshift},
        {nb_rshift},
        {nb_and},
        {nb_xor},
        {nb_or},
        {nb_int},
        {nb_reserved},
        {nb_float},
        {nb_inplace_add},
        {nb_inplace_subtract},
        {nb_inplace_multiply},
        {nb_inplace_remainder},
        {nb_inplace_power},
        {nb_inplace_lshift},
        {nb_inplace_rshift},
        {nb_inplace_and},
        {nb_inplace_xor},
        {nb_inplace_or},
        {nb_floor_divide},
        {nb_true_divide},
        {nb_inplace_floor_divide},
        {nb_inplace_true_divide},
        {nb_index},
        {nb_matrix_multiply},
        {nb_inplace_matrix_multiply},
    }};

    static PySequenceMethods {name}_sequence_methods = {{
        {sq_length},        /* sq_length */
        0,                  /* sq_concat */
        0,                  /* sq_repeat */
        {sq_item},          /* sq_item */
        0,                  /* sq_slice */
        {sq_ass_item},      /* sq_ass_item */
        0,                  /* sq_ass_slice */
        {sq_contains},      /* sq_contains */
    }};

    static PyMappingMethods {name}_mapping_methods = {{
        {mp_length},        /* mp_length */
        {mp_subscript},     /* mp_subscript */
    }};

    static struct PyMethodDef {name}_methods[] = {{
        """ + ("""{tp_methods},""" if "tp_methods" in kwargs else "") + """
        {{NULL, NULL, 0, NULL}}
    }};

    static struct PyGetSetDef {name}_getset[] = {{
        """ + ("""{tp_getset},""" if "tp_getset" in kwargs else "") + """
        {{NULL, NULL, NULL, NULL, NULL}}
    }};

    static struct PyMemberDef {name}_members[] = {{
        """ + ("""{tp_members},""" if "tp_members" in kwargs else "") + """
        {{NULL, 0, 0, 0, NULL}}
    }};

    static PyTypeObject {name}Type = {{
        PyVarObject_HEAD_INIT(NULL, 0)
        "{tp_name}",
        sizeof({name}Object),       /* tp_basicsize */
        {tp_itemsize},              /* tp_itemsize */
        {tp_dealloc},               /* tp_dealloc */
        {tp_vectorcall_offset},
        {tp_getattr},
        {tp_setattr},
        0,                          /* tp_reserved */
        {tp_repr},
        &{name}_number_methods,
        &{name}_sequence_methods,
        &{name}_mapping_methods,
        {tp_hash},
        {tp_call},
        {tp_str},
        {tp_getattro},
        {tp_setattro},
        {tp_as_buffer},
        {tp_flags},
        "",
        {tp_traverse},              /* tp_traverse */
        {tp_clear},                 /* tp_clear */
        {tp_richcompare},           /* tp_richcompare */
        0,                          /* tp_weaklistoffset */
        {tp_iter},                  /* tp_iter */
        {tp_iternext},              /* tp_iternext */
        {name}_methods,             /* tp_methods */
        {name}_members,             /* tp_members */
        {name}_getset,              /* tp_getset */
        {tp_base},                  /* tp_base */
        {tp_dict},                  /* tp_dict */
        {tp_descr_get},             /* tp_descr_get */
        {tp_descr_set},             /* tp_descr_set */
        {tp_dictoffset},            /* tp_dictoffset */
        {tp_init},                  /* tp_init */
        {tp_alloc},                 /* tp_alloc */
        {tp_new},                   /* tp_new */
        {tp_free},                  /* tp_free */
    }};

    static PyModuleDef {name}module = {{
        PyModuleDef_HEAD_INIT,
        "{name}",
        "",
        -1,
        NULL, NULL, NULL, NULL, NULL
    }};

    PyMODINIT_FUNC
    PyInit_{name}(void)
    {{
        PyObject* m = PyModule_Create(&{name}module);
        if (m == NULL)
            return NULL;

        {ready_code}
        if (PyType_Ready(&{name}Type) < 0)
            return NULL;
        {post_ready_code}


        Py_INCREF(&{name}Type);
        PyModule_AddObject(m, "{name}", (PyObject *)&{name}Type);
        return m;
    }}
    """

    kwargs["name"] = name
    kwargs["code"] = code
    kwargs.setdefault("ready_code", "")
    kwargs.setdefault("post_ready_code", "")
    kwargs.setdefault("tp_name", f"{name}.{name}")
    kwargs.setdefault("tp_itemsize", "0")
    kwargs.setdefault("tp_new", "PyType_GenericNew")
    kwargs.setdefault("tp_alloc", "PyType_GenericAlloc")
    kwargs.setdefault("tp_free", "PyObject_Del")
    kwargs.setdefault("tp_flags", "Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE")
    kwargs.setdefault("cmembers", "")
    kwargs.setdefault("includes", "")
    kwargs.setdefault("struct_base", "PyObject_HEAD")
    c_source = UnseenFormatter().format(template, **kwargs)

    source_file = DIR / f'{name}.c'
    with open(source_file, "wb", buffering=0) as f:
        f.write(bytes(c_source, 'utf-8'))

    # ensure file was really written
    try:
        stat_result = os.stat(source_file)
        if stat_result[6] == 0:
            raise SystemError("empty source file %s" % (source_file,))
    except FileNotFoundError:
        raise SystemError("source file %s not available" % (source_file,))

    ccompile(None, name)
    sys.path.insert(0, str(DIR))
    try:
        cmodule = __import__(name)
    finally:
        sys.path.pop(0)
    return getattr(cmodule, name)


CPyExtTestCase.compile_module = ccompile
