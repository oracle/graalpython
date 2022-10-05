#include "capi.h"

UPCALL_ID(Py_GenericAlias)
PyObject* Py_GenericAlias(PyObject *origin, PyObject *args) {
    return UPCALL_CEXT_O(_jls_Py_GenericAlias, native_to_java(origin), native_to_java(args));
}
