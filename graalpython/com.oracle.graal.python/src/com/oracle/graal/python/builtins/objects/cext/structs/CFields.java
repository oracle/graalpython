/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
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
package com.oracle.graal.python.builtins.objects.cext.structs;

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Double;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_C_FUNCTION;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_SSIZE_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyAsyncMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyBufferProcs;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyGetSetDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMappingMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMemberDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleDefSlot;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyNumberMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySequenceMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_buffer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_hash_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UINTPTR_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.WCHAR_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.allocfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.binaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.descrgetfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.descrsetfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.destructor;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.freefunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getbufferproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getiterfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.hashfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.initproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.inquiry;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.iternextfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.lenfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.newfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.objobjargproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.objobjproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.releasebufferproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.reprfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.richcmpfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.sendfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ssizeargfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ssizeobjargproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ternaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.traverseproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.unaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.vectorcallfunc;

import com.oracle.graal.python.annotations.CApiFields;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

@CApiFields
public enum CFields {
    PyObject__ob_refcnt(Py_ssize_t),
    PyObject__ob_type(PyTypeObject),

    PyVarObject__ob_size(Py_ssize_t),

    GraalPyObject__handle_table_index(Int),
    GraalPyVarObject__ob_size(Py_ssize_t),
    GraalPyVarObject__ob_item(PyObjectPtr),
    GraalPyFloatObject__ob_fval(Double),

    PyModuleDef__m_name(ConstCharPtr),
    PyModuleDef__m_doc(ConstCharPtr),
    PyModuleDef__m_size(Py_ssize_t),
    PyModuleDef__m_methods(PyMethodDef),
    PyModuleDef__m_slots(PyModuleDefSlot),
    PyModuleDef__m_traverse(traverseproc),
    PyModuleDef__m_clear(inquiry),
    PyModuleDef__m_free(freefunc),

    PyModuleDef_Slot__slot(Int),
    PyModuleDef_Slot__value(Pointer),

    PyMethodDef__ml_name(ConstCharPtr),
    PyMethodDef__ml_meth(PY_C_FUNCTION),
    PyMethodDef__ml_flags(Int),
    PyMethodDef__ml_doc(ConstCharPtr),

    PyMemoryViewObject__hash(Py_ssize_t),
    PyMemoryViewObject__flags(Int),
    PyMemoryViewObject__exports(Py_ssize_t),
    PyMemoryViewObject__view(Py_buffer),
    PyMemoryViewObject__ob_array(Py_ssize_t),

    Py_buffer__buf(Pointer),
    Py_buffer__obj(PyObject),
    Py_buffer__len(Py_ssize_t),
    Py_buffer__itemsize(Py_ssize_t),
    Py_buffer__readonly(Int),
    Py_buffer__ndim(Int),
    Py_buffer__format(CharPtrAsTruffleString),
    Py_buffer__shape(PY_SSIZE_T_PTR),
    Py_buffer__strides(PY_SSIZE_T_PTR),
    Py_buffer__suboffsets(PY_SSIZE_T_PTR),
    Py_buffer__internal(Pointer),

    PyDateTime_CAPI__DateType(PyTypeObject),
    PyDateTime_CAPI__DateTimeType(PyTypeObject),
    PyDateTime_CAPI__TimeType(PyTypeObject),
    PyDateTime_CAPI__DeltaType(PyTypeObject),
    PyDateTime_CAPI__TZInfoType(PyTypeObject),
    PyDateTime_CAPI__TimeZone_UTC(PyObject),
    PyDateTime_CAPI__Date_FromDate(Pointer),
    PyDateTime_CAPI__DateTime_FromDateAndTime(Pointer),
    PyDateTime_CAPI__Time_FromTime(Pointer),
    PyDateTime_CAPI__Delta_FromDelta(Pointer),
    PyDateTime_CAPI__TimeZone_FromTimeZone(Pointer),
    PyDateTime_CAPI__DateTime_FromTimestamp(Pointer),
    PyDateTime_CAPI__Date_FromTimestamp(Pointer),
    PyDateTime_CAPI__DateTime_FromDateAndTimeAndFold(Pointer),
    PyDateTime_CAPI__Time_FromTimeAndFold(Pointer),

    PyNumberMethods__nb_add(binaryfunc),
    PyNumberMethods__nb_subtract(binaryfunc),
    PyNumberMethods__nb_multiply(binaryfunc),
    PyNumberMethods__nb_remainder(binaryfunc),
    PyNumberMethods__nb_divmod(binaryfunc),
    PyNumberMethods__nb_power(ternaryfunc),
    PyNumberMethods__nb_negative(unaryfunc),
    PyNumberMethods__nb_positive(unaryfunc),
    PyNumberMethods__nb_absolute(unaryfunc),
    PyNumberMethods__nb_bool(inquiry),
    PyNumberMethods__nb_invert(unaryfunc),
    PyNumberMethods__nb_lshift(binaryfunc),
    PyNumberMethods__nb_rshift(binaryfunc),
    PyNumberMethods__nb_and(binaryfunc),
    PyNumberMethods__nb_xor(binaryfunc),
    PyNumberMethods__nb_or(binaryfunc),
    PyNumberMethods__nb_int(unaryfunc),
    PyNumberMethods__nb_reserved(Pointer),
    PyNumberMethods__nb_float(unaryfunc),
    PyNumberMethods__nb_inplace_add(binaryfunc),
    PyNumberMethods__nb_inplace_subtract(binaryfunc),
    PyNumberMethods__nb_inplace_multiply(binaryfunc),
    PyNumberMethods__nb_inplace_remainder(binaryfunc),
    PyNumberMethods__nb_inplace_power(ternaryfunc),
    PyNumberMethods__nb_inplace_lshift(binaryfunc),
    PyNumberMethods__nb_inplace_rshift(binaryfunc),
    PyNumberMethods__nb_inplace_and(binaryfunc),
    PyNumberMethods__nb_inplace_xor(binaryfunc),
    PyNumberMethods__nb_inplace_or(binaryfunc),
    PyNumberMethods__nb_floor_divide(binaryfunc),
    PyNumberMethods__nb_true_divide(binaryfunc),
    PyNumberMethods__nb_inplace_floor_divide(binaryfunc),
    PyNumberMethods__nb_inplace_true_divide(binaryfunc),
    PyNumberMethods__nb_index(unaryfunc),
    PyNumberMethods__nb_matrix_multiply(binaryfunc),
    PyNumberMethods__nb_inplace_matrix_multiply(binaryfunc),

    PySequenceMethods__sq_length(lenfunc),
    PySequenceMethods__sq_concat(binaryfunc),
    PySequenceMethods__sq_repeat(ssizeargfunc),
    PySequenceMethods__sq_item(ssizeargfunc),
    PySequenceMethods__was_sq_slice(Pointer),
    PySequenceMethods__sq_ass_item(ssizeobjargproc),
    PySequenceMethods__was_sq_ass_slice(Pointer),
    PySequenceMethods__sq_contains(objobjproc),
    PySequenceMethods__sq_inplace_concat(binaryfunc),
    PySequenceMethods__sq_inplace_repeat(ssizeargfunc),

    PyMappingMethods__mp_length(lenfunc),
    PyMappingMethods__mp_subscript(binaryfunc),
    PyMappingMethods__mp_ass_subscript(objobjargproc),

    PyAsyncMethods__am_await(unaryfunc),
    PyAsyncMethods__am_aiter(unaryfunc),
    PyAsyncMethods__am_anext(unaryfunc),
    PyAsyncMethods__am_send(sendfunc),

    PyBufferProcs__bf_getbuffer(getbufferproc),
    PyBufferProcs__bf_releasebuffer(releasebufferproc),

    PyTypeObject__tp_name(ConstCharPtr),
    PyTypeObject__tp_basicsize(Py_ssize_t),
    PyTypeObject__tp_itemsize(Py_ssize_t),
    PyTypeObject__tp_dealloc(destructor),
    PyTypeObject__tp_vectorcall_offset(Py_ssize_t),
    PyTypeObject__tp_getattr(getattrfunc),
    PyTypeObject__tp_setattr(setattrfunc),
    PyTypeObject__tp_as_async(PyAsyncMethods),
    PyTypeObject__tp_repr(reprfunc),
    PyTypeObject__tp_as_number(PyNumberMethods),
    PyTypeObject__tp_as_sequence(PySequenceMethods),
    PyTypeObject__tp_as_mapping(PyMappingMethods),
    PyTypeObject__tp_hash(hashfunc),
    PyTypeObject__tp_call(ternaryfunc),
    PyTypeObject__tp_str(reprfunc),
    PyTypeObject__tp_getattro(getattrofunc),
    PyTypeObject__tp_setattro(setattrofunc),
    PyTypeObject__tp_as_buffer(PyBufferProcs),
    PyTypeObject__tp_flags(UNSIGNED_LONG),
    PyTypeObject__tp_doc(ConstCharPtr),
    PyTypeObject__tp_traverse(traverseproc),
    PyTypeObject__tp_clear(inquiry),
    PyTypeObject__tp_richcompare(richcmpfunc),
    PyTypeObject__tp_weaklistoffset(Py_ssize_t),
    PyTypeObject__tp_iter(getiterfunc),
    PyTypeObject__tp_iternext(iternextfunc),
    PyTypeObject__tp_methods(PyMethodDef),
    PyTypeObject__tp_members(PyMemberDef),
    PyTypeObject__tp_getset(PyGetSetDef),
    PyTypeObject__tp_base(PyTypeObject),
    PyTypeObject__tp_dict(PyObject),
    PyTypeObject__tp_descr_get(descrgetfunc),
    PyTypeObject__tp_descr_set(descrsetfunc),
    PyTypeObject__tp_dictoffset(Py_ssize_t),
    PyTypeObject__tp_init(initproc),
    PyTypeObject__tp_alloc(allocfunc),
    PyTypeObject__tp_new(newfunc),
    PyTypeObject__tp_free(freefunc),
    PyTypeObject__tp_is_gc(inquiry),
    PyTypeObject__tp_bases(PyObject),
    PyTypeObject__tp_mro(PyObject),
    PyTypeObject__tp_cache(PyObject),
    PyTypeObject__tp_subclasses(PyObject),
    PyTypeObject__tp_weaklist(PyObject),
    PyTypeObject__tp_del(destructor),
    PyTypeObject__tp_version_tag(UNSIGNED_INT),
    PyTypeObject__tp_finalize(destructor),
    PyTypeObject__tp_vectorcall(vectorcallfunc),

    PyHeapTypeObject__as_async(PyAsyncMethods),
    PyHeapTypeObject__as_number(PyNumberMethods),
    PyHeapTypeObject__as_mapping(PyMappingMethods),
    PyHeapTypeObject__as_sequence(PySequenceMethods),
    PyHeapTypeObject__as_buffer(PyBufferProcs),
    PyHeapTypeObject__ht_name(PyObject),
    PyHeapTypeObject__ht_slots(PyObject),
    PyHeapTypeObject__ht_qualname(PyObject),
    PyHeapTypeObject__ht_module(PyObject),

    PyBytesObject__ob_shash(Py_hash_t),
    PyBytesObject__ob_sval(CHAR_PTR),

    PyByteArrayObject__ob_start(CHAR_PTR),

    PyListObject__ob_item(PyObjectPtr),
    PyListObject__allocated(Py_ssize_t),

    PyTupleObject__ob_item(PyObjectPtr),

    PyFloatObject__ob_fval(Double),

    PyModuleDef_Base__m_index(Py_ssize_t),

    Py_complex__real(Double),
    Py_complex__imag(Double),

    PyComplexObject__cval__real(Double),
    PyComplexObject__cval__imag(Double),

    PyASCIIObject__length(Py_ssize_t),
    PyASCIIObject__hash(Py_hash_t),
    PyASCIIObject__state(Int),
    PyASCIIObject__wstr(WCHAR_T_PTR),

    PyCompactUnicodeObject__utf8_length(Py_ssize_t),
    PyCompactUnicodeObject__utf8(CharPtrAsTruffleString),
    PyCompactUnicodeObject__wstr_length(Py_ssize_t),

    PyUnicodeObject__data(Pointer),

    PyGetSetDef__name(ConstCharPtr),
    PyGetSetDef__get(getter),
    PyGetSetDef__set(setter),
    PyGetSetDef__doc(ConstCharPtr),
    PyGetSetDef__closure(Pointer),

    PyMemberDef__name(ConstCharPtr),
    PyMemberDef__type(Int),
    PyMemberDef__offset(Py_ssize_t),
    PyMemberDef__flags(Int),
    PyMemberDef__doc(ConstCharPtr),

    PyThreadState__curexc_type(PyObject),
    PyThreadState__curexc_value(PyObject),
    PyThreadState__curexc_traceback(PyObject),
    PyThreadState__dict(PyObject),
    PyThreadState__small_ints(PyObjectPtr),
    PyThreadState__gc(Pointer),

    GCState__enabled(Int),
    GCState__debug(Int),
    GCState__generation0(Pointer),

    PyGC_Head___gc_prev(UINTPTR_T),
    PyGC_Head___gc_next(UINTPTR_T),
    GraalPyGC_CycleNode__item(PyObject),
    GraalPyGC_CycleNode__next(Pointer),

    PyBaseExceptionObject__dict(PyObject),
    PyBaseExceptionObject__args(PyObject),
    PyBaseExceptionObject__traceback(PyObject),
    PyBaseExceptionObject__context(PyObject),
    PyBaseExceptionObject__cause(PyObject),
    PyBaseExceptionObject__suppress_context(CHAR);

    public static final int PyASCIIObject__state_interned_shift = 0;
    public static final int PyASCIIObject__state_kind_shift = 2;
    public static final int PyASCIIObject__state_compact_shift = 5;
    public static final int PyASCIIObject__state_ascii_shift = 6;
    public static final int PyASCIIObject__state_ready_shift = 7;

    @CompilationFinal(dimensions = 1) public static final CFields[] VALUES = values();

    final ArgDescriptor type;
    final CStructs struct;

    CFields(ArgDescriptor type) {
        this.type = type;
        int delim = name().indexOf("__");
        this.struct = CStructs.valueOf(name().substring(0, delim));
        assert this.struct != null;
    }

    @CompilationFinal private long offset = -1;

    long offset() {
        long o = offset;
        if (o == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            resolve();
            return offset;
        }
        return o;
    }

    CStructs struct() {
        return struct;
    }

    private static void resolve() {
        CompilerAsserts.neverPartOfCompilation();
        Object offsetsPointer = PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_PYTRUFFLE_STRUCT_OFFSETS);
        long[] offsets = CStructAccessFactory.ReadI64NodeGen.getUncached().readLongArray(offsetsPointer, VALUES.length);
        for (CFields field : VALUES) {
            field.offset = offsets[field.ordinal()];
            assert field.offset >= 0 && field.offset < 1024;
        }
    }
}
