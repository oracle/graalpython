#include <Python.h>
#include "hpy.h"

#ifdef HPY_UNIVERSAL_ABI
   // for _h2py and _py2h
#  include "handles.h"
#endif

_HPy_HIDDEN int
ctx_ContextVar_Get(HPyContext *ctx, HPy context_var, HPy defaul_value, HPy *result) {
    PyObject *py_result;
    int ret = PyContextVar_Get(_h2py(context_var), _h2py(defaul_value), &py_result);
    *result = _py2h(py_result);
    return ret;
}