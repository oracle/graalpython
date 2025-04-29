#include <Python.h>
#include "ctx_meth.h"
#include "hpy/runtime/ctx_type.h"
#include "hpy/runtime/ctx_module.h"
#include "handles.h"

static void _buffer_h2py(HPyContext *ctx, const HPy_buffer *src, Py_buffer *dest)
{
    dest->buf = src->buf;
    dest->obj = HPy_AsPyObject(ctx, src->obj);
    dest->len = src->len;
    dest->itemsize = src->itemsize;
    dest->readonly = src->readonly;
    dest->ndim = src->ndim;
    dest->format = src->format;
    dest->shape = src->shape;
    dest->strides = src->strides;
    dest->suboffsets = src->suboffsets;
    dest->internal = src->internal;
}

static void _buffer_py2h(HPyContext *ctx, const Py_buffer *src, HPy_buffer *dest)
{
    dest->buf = src->buf;
    dest->obj = HPy_FromPyObject(ctx, src->obj);
    dest->len = src->len;
    dest->itemsize = src->itemsize;
    dest->readonly = src->readonly;
    dest->ndim = src->ndim;
    dest->format = src->format;
    dest->shape = src->shape;
    dest->strides = src->strides;
    dest->suboffsets = src->suboffsets;
    dest->internal = src->internal;
}

HPyAPI_IMPL void
ctx_CallRealFunctionFromTrampoline(HPyContext *ctx, HPyFunc_Signature sig,
                                   HPyCFunction func, void *args)
{
    switch (sig) {
    case HPyFunc_VARARGS: {
        HPyFunc_varargs f = (HPyFunc_varargs)func;
        _HPyFunc_args_VARARGS *a = (_HPyFunc_args_VARARGS*)args;
        HPy *h_args = (HPy *)alloca(a->nargs * sizeof(HPy));
        for (HPy_ssize_t i = 0; i < a->nargs; i++) {
            h_args[i] = _py2h(a->args[i]);
        }
        a->result = _h2py(f(ctx, _py2h(a->self), h_args, a->nargs));
        return;
    }
    case HPyFunc_KEYWORDS: {
        HPyFunc_keywords f = (HPyFunc_keywords)func;
        _HPyFunc_args_KEYWORDS *a = (_HPyFunc_args_KEYWORDS*)args;
        size_t n_kwnames = a->kwnames != NULL ? PyTuple_GET_SIZE(a->kwnames) : 0;
        size_t nargs = PyVectorcall_NARGS(a->nargsf);
        size_t nargs_with_kw = nargs + n_kwnames;
        HPy *h_args = (HPy *)alloca(nargs_with_kw * sizeof(HPy));
        for (size_t i = 0; i < nargs_with_kw; i++) {
            h_args[i] = _py2h(a->args[i]);
        }
        a->result = _h2py(f(ctx, _py2h(a->self), h_args, nargs, _py2h(a->kwnames)));
        return;
    }
    case HPyFunc_INITPROC: {
        HPyFunc_initproc f = (HPyFunc_initproc)func;
        _HPyFunc_args_INITPROC *a = (_HPyFunc_args_INITPROC*)args;
        Py_ssize_t nargs = PyTuple_GET_SIZE(a->args);
        HPy *h_args = (HPy *)alloca(nargs * sizeof(HPy));
        for (Py_ssize_t i = 0; i < nargs; i++) {
            h_args[i] = _py2h(PyTuple_GET_ITEM(a->args, i));
        }
        a->result = f(ctx, _py2h(a->self), h_args, nargs, _py2h(a->kw));
        return;
    }
    case HPyFunc_NEWFUNC: {
        HPyFunc_newfunc f = (HPyFunc_newfunc)func;
        _HPyFunc_args_NEWFUNC *a = (_HPyFunc_args_NEWFUNC*)args;
        Py_ssize_t nargs = PyTuple_GET_SIZE(a->args);
        HPy *h_args = (HPy *)alloca(nargs * sizeof(HPy));
        for (Py_ssize_t i = 0; i < nargs; i++) {
            h_args[i] = _py2h(PyTuple_GET_ITEM(a->args, i));
        }
        a->result = _h2py(f(ctx, _py2h(a->self), h_args, nargs, _py2h(a->kw)));
        return;
    }
    case HPyFunc_GETBUFFERPROC: {
        HPyFunc_getbufferproc f = (HPyFunc_getbufferproc)func;
        _HPyFunc_args_GETBUFFERPROC *a = (_HPyFunc_args_GETBUFFERPROC*)args;
        HPy_buffer hbuf;
        a->result = f(ctx, _py2h(a->self), &hbuf, a->flags);
        if (a->result < 0) {
            a->view->obj = NULL;
            return;
        }
        _buffer_h2py(ctx, &hbuf, a->view);
        HPy_Close(ctx, hbuf.obj);
        return;
    }
    case HPyFunc_RELEASEBUFFERPROC: {
        HPyFunc_releasebufferproc f = (HPyFunc_releasebufferproc)func;
        _HPyFunc_args_RELEASEBUFFERPROC *a = (_HPyFunc_args_RELEASEBUFFERPROC*)args;
        HPy_buffer hbuf;
        _buffer_py2h(ctx, a->view, &hbuf);
        f(ctx, _py2h(a->self), &hbuf);
        // XXX: copy back from hbuf?
        HPy_Close(ctx, hbuf.obj);
        return;
    }
    case HPyFunc_TRAVERSEPROC: {
        HPyFunc_traverseproc f = (HPyFunc_traverseproc)func;
        _HPyFunc_args_TRAVERSEPROC *a = (_HPyFunc_args_TRAVERSEPROC*)args;
        a->result = call_traverseproc_from_trampoline(f, a->self,
                                                      a->visit, a->arg);
        return;
    }
    case HPyFunc_CAPSULE_DESTRUCTOR: {
        HPyFunc_Capsule_Destructor f = (HPyFunc_Capsule_Destructor)func;
        PyObject *capsule = (PyObject *)args;
        const char *name = PyCapsule_GetName(capsule);
        f(name, PyCapsule_GetPointer(capsule, name),
                PyCapsule_GetContext(capsule));
        return;
    }
    case HPyFunc_MOD_CREATE: {
            HPyFunc_unaryfunc f = (HPyFunc_unaryfunc)func;
            _HPyFunc_args_UNARYFUNC *a = (_HPyFunc_args_UNARYFUNC*)args;
            a->result = _h2py(f(ctx, _py2h(a->arg0)));
            _HPyModule_CheckCreateSlotResult(&a->result);
            return;
    }
#include "autogen_ctx_call.i"
    default:
        Py_FatalError("Unsupported HPyFunc_Signature in ctx_meth.c");
    }
}
