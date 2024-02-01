#include <stdio.h>
#include <assert.h>
#include <stddef.h> /* For offsetof */
#include "Python.h"
#include "exports.h"
#include "pyport.h"
#include "object.h"
#include "datetime.h"
#include "structmember.h"
#include <graal_isolate.h>
#include <libpythonvm.h>


static graal_isolatethread_t* graal_thread = NULL;

PyAPI_FUNC(int) Py_IsInitialized() {
    return graal_thread != NULL;
}

PyAPI_FUNC(void) Py_InitializeEx(int initsigs) {
    assert(graal_thread == NULL);
    if(graal_create_isolate(NULL, NULL, &graal_thread) != 0) {
        fprintf(stderr, "graal_create_isolate failed\n");
        return;
    }
    fprintf(stderr, "graal_create_isolate succeeded\n");

    graalpy_init_embed(graal_thread);

    fprintf(stderr, "graalpy_init_embed succeeded, acquire GIL\n");
    // accuire GIL
    PyGILState_STATE gstate;
    gstate = PyGILState_Ensure();

    fprintf(stderr, "got GIL\n");
}

static void unimplemented(const char* name) {
	printf("Function not implemented in graalpython-embed: %s\n", name);
}

#define FUNC_NOT_IMPLEMENTED unimplemented(__func__); exit(-1);


// {{start CAPI_BUILTINS}}
// GENERATED CODE - see CApiCodeGen
// This can be re-generated using the 'mx python-capi-forwards' command or
// by executing the main class CApiCodeGen

PyAPI_FUNC(int64_t*) PyTruffle_constants() {
    static int64_t constants[] = {
        (int64_t) PYLONG_BITS_IN_DIGIT,
        (int64_t) READONLY,
        (int64_t) CHAR_MIN,
        0xdead1111 // marker value
    };
    return constants;
}
PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_offsets() {
    static Py_ssize_t offsets[] = {
        offsetof(PyObject, ob_refcnt),
        offsetof(PyObject, ob_type),
        offsetof(PyVarObject, ob_size),
        offsetof(PyModuleDef, m_name),
        offsetof(PyModuleDef, m_doc),
        offsetof(PyModuleDef, m_size),
        offsetof(PyModuleDef, m_methods),
        offsetof(PyModuleDef, m_slots),
        offsetof(PyModuleDef, m_traverse),
        offsetof(PyModuleDef, m_clear),
        offsetof(PyModuleDef, m_free),
        offsetof(PyModuleDef_Slot, slot),
        offsetof(PyModuleDef_Slot, value),
        offsetof(PyMethodDef, ml_name),
        offsetof(PyMethodDef, ml_meth),
        offsetof(PyMethodDef, ml_flags),
        offsetof(PyMethodDef, ml_doc),
        offsetof(PyMemoryViewObject, hash),
        offsetof(PyMemoryViewObject, flags),
        offsetof(PyMemoryViewObject, exports),
        offsetof(PyMemoryViewObject, view),
        offsetof(PyMemoryViewObject, ob_array),
        offsetof(Py_buffer, buf),
        offsetof(Py_buffer, obj),
        offsetof(Py_buffer, len),
        offsetof(Py_buffer, itemsize),
        offsetof(Py_buffer, readonly),
        offsetof(Py_buffer, ndim),
        offsetof(Py_buffer, format),
        offsetof(Py_buffer, shape),
        offsetof(Py_buffer, strides),
        offsetof(Py_buffer, suboffsets),
        offsetof(Py_buffer, internal),
        offsetof(PyDateTime_CAPI, DateType),
        offsetof(PyDateTime_CAPI, DateTimeType),
        offsetof(PyDateTime_CAPI, TimeType),
        offsetof(PyDateTime_CAPI, DeltaType),
        offsetof(PyDateTime_CAPI, TZInfoType),
        offsetof(PyDateTime_CAPI, TimeZone_UTC),
        offsetof(PyDateTime_CAPI, Date_FromDate),
        offsetof(PyDateTime_CAPI, DateTime_FromDateAndTime),
        offsetof(PyDateTime_CAPI, Time_FromTime),
        offsetof(PyDateTime_CAPI, Delta_FromDelta),
        offsetof(PyDateTime_CAPI, TimeZone_FromTimeZone),
        offsetof(PyDateTime_CAPI, DateTime_FromTimestamp),
        offsetof(PyDateTime_CAPI, Date_FromTimestamp),
        offsetof(PyDateTime_CAPI, DateTime_FromDateAndTimeAndFold),
        offsetof(PyDateTime_CAPI, Time_FromTimeAndFold),
        offsetof(PyNumberMethods, nb_add),
        offsetof(PyNumberMethods, nb_subtract),
        offsetof(PyNumberMethods, nb_multiply),
        offsetof(PyNumberMethods, nb_remainder),
        offsetof(PyNumberMethods, nb_divmod),
        offsetof(PyNumberMethods, nb_power),
        offsetof(PyNumberMethods, nb_negative),
        offsetof(PyNumberMethods, nb_positive),
        offsetof(PyNumberMethods, nb_absolute),
        offsetof(PyNumberMethods, nb_bool),
        offsetof(PyNumberMethods, nb_invert),
        offsetof(PyNumberMethods, nb_lshift),
        offsetof(PyNumberMethods, nb_rshift),
        offsetof(PyNumberMethods, nb_and),
        offsetof(PyNumberMethods, nb_xor),
        offsetof(PyNumberMethods, nb_or),
        offsetof(PyNumberMethods, nb_int),
        offsetof(PyNumberMethods, nb_reserved),
        offsetof(PyNumberMethods, nb_float),
        offsetof(PyNumberMethods, nb_inplace_add),
        offsetof(PyNumberMethods, nb_inplace_subtract),
        offsetof(PyNumberMethods, nb_inplace_multiply),
        offsetof(PyNumberMethods, nb_inplace_remainder),
        offsetof(PyNumberMethods, nb_inplace_power),
        offsetof(PyNumberMethods, nb_inplace_lshift),
        offsetof(PyNumberMethods, nb_inplace_rshift),
        offsetof(PyNumberMethods, nb_inplace_and),
        offsetof(PyNumberMethods, nb_inplace_xor),
        offsetof(PyNumberMethods, nb_inplace_or),
        offsetof(PyNumberMethods, nb_floor_divide),
        offsetof(PyNumberMethods, nb_true_divide),
        offsetof(PyNumberMethods, nb_inplace_floor_divide),
        offsetof(PyNumberMethods, nb_inplace_true_divide),
        offsetof(PyNumberMethods, nb_index),
        offsetof(PyNumberMethods, nb_matrix_multiply),
        offsetof(PyNumberMethods, nb_inplace_matrix_multiply),
        offsetof(PySequenceMethods, sq_length),
        offsetof(PySequenceMethods, sq_concat),
        offsetof(PySequenceMethods, sq_repeat),
        offsetof(PySequenceMethods, sq_item),
        offsetof(PySequenceMethods, was_sq_slice),
        offsetof(PySequenceMethods, sq_ass_item),
        offsetof(PySequenceMethods, was_sq_ass_slice),
        offsetof(PySequenceMethods, sq_contains),
        offsetof(PySequenceMethods, sq_inplace_concat),
        offsetof(PySequenceMethods, sq_inplace_repeat),
        offsetof(PyMappingMethods, mp_length),
        offsetof(PyMappingMethods, mp_subscript),
        offsetof(PyMappingMethods, mp_ass_subscript),
        offsetof(PyAsyncMethods, am_await),
        offsetof(PyAsyncMethods, am_aiter),
        offsetof(PyAsyncMethods, am_anext),
        offsetof(PyAsyncMethods, am_send),
        offsetof(PyBufferProcs, bf_getbuffer),
        offsetof(PyBufferProcs, bf_releasebuffer),
        offsetof(PyTypeObject, tp_name),
        offsetof(PyTypeObject, tp_basicsize),
        offsetof(PyTypeObject, tp_itemsize),
        offsetof(PyTypeObject, tp_dealloc),
        offsetof(PyTypeObject, tp_vectorcall_offset),
        offsetof(PyTypeObject, tp_getattr),
        offsetof(PyTypeObject, tp_setattr),
        offsetof(PyTypeObject, tp_as_async),
        offsetof(PyTypeObject, tp_repr),
        offsetof(PyTypeObject, tp_as_number),
        offsetof(PyTypeObject, tp_as_sequence),
        offsetof(PyTypeObject, tp_as_mapping),
        offsetof(PyTypeObject, tp_hash),
        offsetof(PyTypeObject, tp_call),
        offsetof(PyTypeObject, tp_str),
        offsetof(PyTypeObject, tp_getattro),
        offsetof(PyTypeObject, tp_setattro),
        offsetof(PyTypeObject, tp_as_buffer),
        offsetof(PyTypeObject, tp_flags),
        offsetof(PyTypeObject, tp_doc),
        offsetof(PyTypeObject, tp_traverse),
        offsetof(PyTypeObject, tp_clear),
        offsetof(PyTypeObject, tp_richcompare),
        offsetof(PyTypeObject, tp_weaklistoffset),
        offsetof(PyTypeObject, tp_iter),
        offsetof(PyTypeObject, tp_iternext),
        offsetof(PyTypeObject, tp_methods),
        offsetof(PyTypeObject, tp_members),
        offsetof(PyTypeObject, tp_getset),
        offsetof(PyTypeObject, tp_base),
        offsetof(PyTypeObject, tp_dict),
        offsetof(PyTypeObject, tp_descr_get),
        offsetof(PyTypeObject, tp_descr_set),
        offsetof(PyTypeObject, tp_dictoffset),
        offsetof(PyTypeObject, tp_init),
        offsetof(PyTypeObject, tp_alloc),
        offsetof(PyTypeObject, tp_new),
        offsetof(PyTypeObject, tp_free),
        offsetof(PyTypeObject, tp_is_gc),
        offsetof(PyTypeObject, tp_bases),
        offsetof(PyTypeObject, tp_mro),
        offsetof(PyTypeObject, tp_cache),
        offsetof(PyTypeObject, tp_subclasses),
        offsetof(PyTypeObject, tp_weaklist),
        offsetof(PyTypeObject, tp_del),
        offsetof(PyTypeObject, tp_version_tag),
        offsetof(PyTypeObject, tp_finalize),
        offsetof(PyTypeObject, tp_vectorcall),
        offsetof(PyBytesObject, ob_shash),
        offsetof(PyBytesObject, ob_sval),
        offsetof(PyListObject, ob_item),
        offsetof(PyListObject, allocated),
        offsetof(PyTupleObject, ob_item),
        offsetof(PyFloatObject, ob_fval),
        offsetof(PyModuleDef_Base, m_index),
        offsetof(PyComplexObject, cval.real),
        offsetof(PyComplexObject, cval.imag),
        offsetof(PyASCIIObject, length),
        offsetof(PyASCIIObject, hash),
        offsetof(PyASCIIObject, state),
        offsetof(PyASCIIObject, wstr),
        offsetof(PyCompactUnicodeObject, utf8_length),
        offsetof(PyCompactUnicodeObject, utf8),
        offsetof(PyCompactUnicodeObject, wstr_length),
        offsetof(PyUnicodeObject, data),
        offsetof(PyGetSetDef, name),
        offsetof(PyGetSetDef, get),
        offsetof(PyGetSetDef, set),
        offsetof(PyGetSetDef, doc),
        offsetof(PyGetSetDef, closure),
        offsetof(PyMemberDef, name),
        offsetof(PyMemberDef, type),
        offsetof(PyMemberDef, offset),
        offsetof(PyMemberDef, flags),
        offsetof(PyMemberDef, doc),
        offsetof(PyThreadState, interp),
        offsetof(PyThreadState, dict),
        offsetof(PyBaseExceptionObject, dict),
        offsetof(PyBaseExceptionObject, args),
        offsetof(PyBaseExceptionObject, traceback),
        offsetof(PyBaseExceptionObject, context),
        offsetof(PyBaseExceptionObject, cause),
        offsetof(PyBaseExceptionObject, suppress_context),
        0xdead2222 // marker value
    };
    return offsets;
}
PyAPI_FUNC(Py_ssize_t*) PyTruffle_struct_sizes() {
    static Py_ssize_t sizes[] = {
        sizeof(PyModuleDef),
        sizeof(PyModuleDef_Slot),
        sizeof(PyMethodDef),
        sizeof(PyObject),
        sizeof(PyBytesObject),
        sizeof(PyListObject),
        sizeof(PyVarObject),
        sizeof(PyMemoryViewObject),
        sizeof(Py_buffer),
        sizeof(PyDateTime_CAPI),
        sizeof(PyNumberMethods),
        sizeof(PySequenceMethods),
        sizeof(PyMappingMethods),
        sizeof(PyAsyncMethods),
        sizeof(PyBufferProcs),
        sizeof(PyTypeObject),
        sizeof(PyTupleObject),
        sizeof(PyFloatObject),
        sizeof(PyLongObject),
        sizeof(PyModuleDef_Base),
        sizeof(PyComplexObject),
        sizeof(PyDateTime_Date),
        sizeof(PyDateTime_Time),
        sizeof(PyDateTime_DateTime),
        sizeof(PyDateTime_Delta),
        sizeof(PyASCIIObject),
        sizeof(PyCompactUnicodeObject),
        sizeof(PyBaseExceptionObject),
        sizeof(PyUnicodeObject),
        sizeof(Py_UNICODE),
        sizeof(PyGetSetDef),
        sizeof(PyMemberDef),
        sizeof(PyThreadState),
        sizeof(wchar_t),
        sizeof(long long),
        sizeof(Py_ssize_t),
        0xdead3333 // marker value
    };
    return sizes;
}
// {{end CAPI_BUILTINS}}

