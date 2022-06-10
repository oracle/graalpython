/* MIT License
 *
 * Copyright (c) 2022, Oracle and/or its affiliates.
 * Copyright (c) 2019 pyhandle
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

/**
 * Helper functions to create struct sequences.
 *
 * These are not part of the HPy context or ABI, but instead are just helpers
 * that delegate to the relevant HPy/CPython APIs.
 *
 */

#include "hpy.h"

const char * const HPyStructSequence_UnnamedField = "_";

HPyAPI_HELPER HPy
HPyStructSequence_NewType(HPyContext *ctx, HPyStructSequence_Desc *desc)
{
    HPy_ssize_t i;
    for (i = 0; desc->fields[i].name != NULL; i++) {
    }

#ifdef HPY_UNIVERSAL_ABI
    HPy collections = HPyImport_ImportModule(ctx, "collections");
    if (HPy_IsNull(collections)) {
        return HPy_NULL;
    }
    HPy namedtuple = HPy_GetAttr_s(ctx, collections, "namedtuple");
    HPy_Close(ctx, collections);
    if (HPy_IsNull(namedtuple)) {
        return HPy_NULL;
    }

    HPy kwds = HPy_NULL;
    HPy args = HPy_NULL;
    HPy fields = HPy_NULL;
    HPy defs = HPy_NULL;
    HPy result = HPy_NULL;
    HPy docstring = HPy_NULL;

    HPyTupleBuilder argsBuilder = HPyTupleBuilder_New(ctx, 2);
    HPyTupleBuilder fieldsBuilder = HPyTupleBuilder_New(ctx, i);
    HPyTupleBuilder defsBuilder = HPyTupleBuilder_New(ctx, i);

    HPy h_name = HPyUnicode_FromString(ctx, "tmpname");
    if (HPy_IsNull(h_name)) {
        goto error;
    }
    HPyTupleBuilder_Set(ctx, argsBuilder, 0, h_name);
    HPy_Close(ctx, h_name);

    i = 0;
    for (const char* name = desc->fields[i].name; name != NULL; name = desc->fields[++i].name) {
        HPy h_field = HPyUnicode_FromString(ctx, name);
        if (HPy_IsNull(h_name)) {
            HPyTupleBuilder_Cancel(ctx, argsBuilder);
            HPyTupleBuilder_Cancel(ctx, fieldsBuilder);
            HPyTupleBuilder_Cancel(ctx, defsBuilder);
            goto error;
        }
        HPyTupleBuilder_Set(ctx, fieldsBuilder, i, h_field);
        HPyTupleBuilder_Set(ctx, defsBuilder, i, ctx->h_None);
        HPy_Close(ctx, h_field);
    }
    fields = HPyTupleBuilder_Build(ctx, fieldsBuilder);
    if (HPy_IsNull(fields)) {
        HPyTupleBuilder_Cancel(ctx, argsBuilder);
        HPyTupleBuilder_Cancel(ctx, defsBuilder);
        goto error;
    }
    defs = HPyTupleBuilder_Build(ctx, defsBuilder);
    if (HPy_IsNull(defs)) {
        HPyTupleBuilder_Cancel(ctx, argsBuilder);
        goto error;
    }
    HPyTupleBuilder_Set(ctx, argsBuilder, 1, fields);
    args = HPyTupleBuilder_Build(ctx, argsBuilder);
    if (HPy_IsNull(args)) {
        goto error;
    }

    kwds = HPyDict_New(ctx);
    if (HPy_IsNull(kwds)) {
        goto error;
    }
    if (HPy_SetItem_s(ctx, kwds, "rename", ctx->h_True) < 0) {
        goto error;
    }
    if (HPy_SetItem_s(ctx, kwds, "defaults", defs) < 0) {
        goto error;
    }

    result = HPy_CallTupleDict(ctx, namedtuple, args, kwds);

    if (HPy_IsNull(result)) {
        goto error;
    }

    docstring = HPyUnicode_FromString(ctx, desc->doc);
    if (HPy_IsNull(docstring) || HPy_SetAttr_s(ctx, result, "__doc__", docstring) < 0) {
        HPy_Close(ctx, result);
        result = HPy_NULL;
        goto error;
    }

    h_name = HPyUnicode_FromString(ctx, desc->name);
    if (HPy_IsNull(h_name)) {
        goto error;
    }
    if (HPy_IsNull(h_name) ||
        HPy_SetAttr_s(ctx, result, "__name__", h_name) < 0 ||
        HPy_SetAttr_s(ctx, result, "__qualname__", h_name) < 0) {
        HPy_Close(ctx, result);
        result = HPy_NULL;
        goto error;
    }

 error:
    HPy_Close(ctx, h_name);
    HPy_Close(ctx, namedtuple);
    HPy_Close(ctx, kwds);
    HPy_Close(ctx, args);
    HPy_Close(ctx, fields);
    HPy_Close(ctx, defs);
    HPy_Close(ctx, docstring);
    return result;
#else    
    PyStructSequence_Desc d = {
        .name = desc->name,
        .doc = desc->doc,
        .fields = (PyStructSequence_Field *)desc->fields,
        .n_in_sequence = i
    };
    return _py2h((PyObject*) PyStructSequence_NewType(&d));
#endif
}

HPyAPI_HELPER HPyStructSequenceBuilder
HPyStructSequenceBuilder_New(HPyContext *ctx, HPy type)
{
#ifdef HPY_UNIVERSAL_ABI
    HPy fields = HPy_GetAttr_s(ctx, type, "_fields");
    if (HPy_IsNull(fields)) {
#ifdef GRAALVM_PYTHON_LLVM
        return (HPyTupleBuilder){NULL};
#else
        return (HPyTupleBuilder){(HPy_ssize_t)0};
#endif
    }
    HPy_ssize_t n = HPy_Length(ctx, fields);
    if (n < 0) {
        HPy_Close(ctx, fields);
#ifdef GRAALVM_PYTHON_LLVM
        return (HPyTupleBuilder){NULL};
#else
        return (HPyTupleBuilder){(HPy_ssize_t)0};
#endif
    }
    return HPyTupleBuilder_New(ctx, n);
#else
    PyObject *seq = PyStructSequence_New((PyTypeObject *)_h2py(type));
#ifdef GRAALVM_PYTHON_LLVM
    return (HPyStructSequenceBuilder){(void *)seq};
#else
    return (HPyStructSequenceBuilder){(HPy_ssize_t)seq};
#endif
#endif
}

HPyAPI_HELPER void
HPyStructSequenceBuilder_Set(HPyContext *ctx, HPyStructSequenceBuilder self, HPy_ssize_t i, HPy value)
{
#ifdef HPY_UNIVERSAL_ABI
    HPyTupleBuilder_Set(ctx, self, i, value);
#else
    PyObject *v = _h2py(value);
    Py_INCREF(v);
    PyStructSequence_SetItem((PyObject *)self._tup, i, v);
#endif
}

HPyAPI_HELPER void
HPyStructSequenceBuilder_Set_i(HPyContext *ctx, HPyStructSequenceBuilder self, HPy_ssize_t i, long value)
{
#ifdef HPY_UNIVERSAL_ABI
    HPy v = HPyLong_FromLong(ctx, value);
    HPyTupleBuilder_Set(ctx, self, i, v);
    HPy_Close(ctx, v);
#else
    PyStructSequence_SetItem((PyObject *)self._tup, i, PyLong_FromLong(value));
#endif
}

HPyAPI_HELPER HPy
HPyStructSequenceBuilder_Build(HPyContext *ctx, HPyStructSequenceBuilder self, HPy type)
{
#ifdef HPY_UNIVERSAL_ABI
    HPy tuple = HPyTupleBuilder_Build(ctx, self);
    if (HPy_IsNull(tuple)) {
        return HPy_NULL;
    }
    HPy kw = HPyDict_New(ctx);
    if (HPy_IsNull(kw)) {
        HPy_Close(ctx, tuple);
        return HPy_NULL;
    }
    HPy result = HPy_CallTupleDict(ctx, type, tuple, kw);
    HPy_Close(ctx, kw);
    HPy_Close(ctx, tuple);
    return result;
#else
    return _py2h((PyObject *)self._tup);
#endif
}

HPyAPI_HELPER void
HPyStructSequenceBuilder_Cancel(HPyContext *ctx, HPyStructSequenceBuilder self)
{
#ifdef HPY_UNIVERSAL_ABI
    HPyTupleBuilder_Cancel(ctx, self);
#else
    Py_DECREF((PyObject *)self._tup);
    self._tup = 0;
#endif
}

HPyAPI_HELPER HPy
HPyStructSequence_GetItem(HPyContext *ctx, HPy self, HPy_ssize_t i)
{
#ifdef HPY_UNIVERSAL_ABI
    return HPy_GetItem_i(ctx, self, i);
#else
    PyObject *v = PyStructSequence_GetItem(_h2py(self), i);
    Py_INCREF(v);
    return _py2h(v);
#endif
}
