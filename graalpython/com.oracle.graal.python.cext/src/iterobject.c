#include "capi.h"

PyObject* PySeqIter_New(PyObject *seq) {
    if (!PySequence_Check(seq)) {
        PyErr_BadInternalCall();
        return NULL;
    }
    return UPCALL_O(PY_BUILTIN, "iter", native_to_java(seq));
}
