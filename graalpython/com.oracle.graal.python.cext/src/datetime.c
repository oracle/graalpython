#include "capi.h"

POLYGLOT_DECLARE_TYPE(PyDateTime_CAPI);

/** to be used from Java code only; returns the type ID for a PyDateTime_CAPI */
extern PyObject* set_PyDateTime_CAPI_typeid(PyTypeObject* type) {
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Set_SulongType", type, polyglot_PyDateTime_CAPI_typeid());
    return Py_True;
}

