#ifndef HPY_HANDLES_H
#define HPY_HANDLES_H

#include <Python.h>
#include "hpy.h"

// The main reason for +1/-1 is to make sure that if people casts HPy to
// PyObject* directly, things explode. Moreover, with this we can easily
// distinguish normal and debug handles in gdb, by only looking at the last
// bit.

static inline HPy _py2h(PyObject *obj) {
    if (obj == NULL)
        return HPy_NULL;
    return (HPy){(HPy_ssize_t)obj + 1};
}

static inline PyObject *_h2py(HPy h) {
    if HPy_IsNull(h)
        return NULL;
    return (PyObject *)(h._i - 1);
}

static inline HPyField _py2hf(PyObject *obj)
{
    HPy h = _py2h(obj);
    return (HPyField){ ._i = h._i };
}

static inline PyObject * _hf2py(HPyField hf)
{
    HPy h = { ._i = hf._i };
    return _h2py(h);
}

static inline HPyThreadState _threads2h(PyThreadState* s)
{
    return (HPyThreadState) { (intptr_t) s };
}

static inline PyThreadState* _h2threads(HPyThreadState h)
{
    return (PyThreadState*) h._i;
}

static inline HPyGlobal _py2hg(PyObject *obj)
{
    HPy h = _py2h(obj);
    return (HPyGlobal){ ._i = h._i };
}

static inline PyObject * _hg2py(HPyGlobal hf)
{
    HPy h = { ._i = hf._i };
    return _h2py(h);
}

#endif /* HPY_HANDLES_H */
