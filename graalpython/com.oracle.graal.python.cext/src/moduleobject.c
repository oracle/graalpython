/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include "capi.h"

PyTypeObject PyModule_Type = PY_TRUFFLE_TYPE("module", &PyType_Type, Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC | Py_TPFLAGS_BASETYPE);

/* Modules */
int PyModule_AddFunctions(PyObject* mod, PyMethodDef* methods) {
    if (!methods) {
        return -1;
    }
    int idx = 0;
    void* mod_w = to_java(mod);
    PyMethodDef def = methods[idx];
    while (def.ml_name != NULL) {
        truffle_invoke(PY_TRUFFLE_CEXT,
                       "AddFunction",
                       mod_w,
                       truffle_read_string((const char*)(def.ml_name)),
                       truffle_address_to_function(def.ml_meth),
                       truffle_address_to_function(get_method_flags_cwrapper(def.ml_flags)),
                       get_method_flags_wrapper(def.ml_flags),
                       truffle_read_string((const char*)(def.ml_doc ? def.ml_doc : "")));
        def = methods[++idx];
    }
    return 0;
}

int PyModule_SetDocString(PyObject* m, const char* doc) {
    truffle_invoke(PY_TRUFFLE_CEXT, "PyModule_SetDocString", to_java(m), truffle_read_string(doc));
    return 0;
}

PyObject* _PyModule_CreateInitialized(PyModuleDef* moduledef, int apiversion) {
    if (moduledef->m_slots) {
        PyErr_Format(
            PyExc_SystemError,
            "module %s: PyModule_Create is incompatible with m_slots", moduledef->m_name);
        return NULL;
    }

    PyObject* mod = to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "_PyModule_CreateInitialized_PyModule_New", truffle_read_string(moduledef->m_name)));

    if (moduledef->m_size > 0) {
        void* md_state = PyMem_MALLOC(moduledef->m_size);
        if (!md_state) {
            PyErr_NoMemory();
            return NULL;
        }
        memset(md_state, 0, moduledef->m_size);
        truffle_write(to_java(mod), "md_state", md_state);
    }

    if (moduledef->m_methods != NULL) {
        if (PyModule_AddFunctions(mod, moduledef->m_methods) != 0) {
            return NULL;
        }
    }

    if (moduledef->m_doc != NULL) {
        if (PyModule_SetDocString(mod, moduledef->m_doc) != 0) {
            return NULL;
        }
    }

    truffle_write(to_java(mod), "md_def", moduledef);
    return mod;
}

int PyModule_AddObject(PyObject* m, const char* k, PyObject* v) {
    truffle_invoke(PY_TRUFFLE_CEXT, "PyModule_AddObject", to_java(m), truffle_read_string(k), to_java(v));
    return 0;
}

int PyModule_AddIntConstant(PyObject* m, const char* k, long constant) {
    truffle_invoke(PY_TRUFFLE_CEXT, "PyModule_AddObject", to_java(m), truffle_read_string(k), constant);
    return 0;
}

PyObject* PyModule_Create2(PyModuleDef* moduledef, int apiversion) {
    return _PyModule_CreateInitialized(moduledef, apiversion);
}

PyObject* PyModule_GetDict(PyObject* o) {
    if (!PyModule_Check(o)) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return ((PyModuleObject*)polyglot_as_PyModuleObject(o))->md_dict;
}
