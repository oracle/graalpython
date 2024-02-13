/* Copyright (c) 2022, 2023, Oracle and/or its affiliates.
 * Copyright (C) 1996-2022 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
#include "capi.h"



// partially taken from CPython "Objects/moduleobject.c"
PyObject*
PyModuleDef_Init(struct PyModuleDef* def)
{
    if (PyType_Ready(&PyModuleDef_Type) < 0)
         return NULL;
    if (def->m_base.m_index == 0) {
        Py_SET_REFCNT(def, 1);
        Py_SET_TYPE(def, &PyModuleDef_Type);
        def->m_base.m_index = Graal_PyTruffleModule_GetAndIncMaxModuleNumber();
    }
    return (PyObject*)def;
}

int PyModule_AddFunctions(PyObject* mod, PyMethodDef* methods) {
    if (!methods) {
        return -1;
    }
    for (PyMethodDef* def = methods; def->ml_name != NULL; def++) {
        GraalPyTruffleModule_AddFunctionToModule(def,
                       mod,
                       def->ml_name,
                       def->ml_meth,
                       def->ml_flags,
                       get_method_flags_wrapper(def->ml_flags),
					   def->ml_doc);
    }
    return 0;
}

PyObject* _PyModule_CreateInitialized(PyModuleDef* moduledef, int apiversion) {
    if (!PyModuleDef_Init(moduledef))
        return NULL;
    if (moduledef->m_slots) {
        PyErr_Format(
            PyExc_SystemError,
            "module %s: PyModule_Create is incompatible with m_slots", PyModuleDef_m_name(moduledef));
        return NULL;
    }

    PyModuleObject* mod = Graal_PyTruffleModule_CreateInitialized_PyModule_New(PyModuleDef_m_name(moduledef));

    if (moduledef->m_size > 0) {
        void* md_state = PyMem_MALLOC(PyModuleDef_m_size(moduledef));
        if (!md_state) {
            PyErr_NoMemory();
            return NULL;
        }
        memset(md_state, 0, PyModuleDef_m_size(moduledef));
        GraalPy_set_PyModuleObject_md_state(mod, md_state);
    }

    if (PyModuleDef_m_methods(moduledef) != NULL) {
        if (PyModule_AddFunctions((PyObject*) mod, PyModuleDef_m_methods(moduledef)) != 0) {
            return NULL;
        }
    }

    if (PyModuleDef_m_doc(moduledef) != NULL) {
        if (PyModule_SetDocString((PyObject*) mod, PyModuleDef_m_doc(moduledef)) != 0) {
            return NULL;
        }
    }

    GraalPy_set_PyModuleObject_md_def(mod, moduledef);
    return (PyObject*) mod;
}

PyObject* PyModule_Create2(PyModuleDef* moduledef, int apiversion) {
    return _PyModule_CreateInitialized(moduledef, apiversion);
}

PyObject* PyModule_GetDict(PyObject* o) {
    if (!PyModule_Check(o)) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return GraalPy_get_PyModuleObject_md_dict((PyModuleObject*) o);
}

PyModuleDef* PyModule_GetDef(PyObject* m) {
    if (!PyModule_Check(m)) {
        PyErr_BadArgument();
        return NULL;
    }
    return GraalPy_get_PyModuleObject_md_def((PyModuleObject*) m);
}

void* PyModule_GetState(PyObject *m) {
    if (!PyModule_Check(m)) {
        PyErr_BadArgument();
        return NULL;
    }
    return GraalPy_get_PyModuleObject_md_state((PyModuleObject*) m);
}

// partially taken from CPython "Objects/moduleobject.h"
const char * PyModule_GetName(PyObject *m) {
    PyObject *name = PyModule_GetNameObject(m);
    if (name == NULL) {
        return NULL;
    }
    return PyUnicode_AsUTF8(name);
}
