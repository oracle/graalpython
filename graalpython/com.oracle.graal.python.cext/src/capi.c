/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
#include "capi.h"

static void initialize_type_structure(PyTypeObject* structure, const char* typname) {
	void *jtype = truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Type", truffle_read_string(typname));
    PyTypeObject* ptype = (PyTypeObject *)to_sulong(jtype);
    PyTypeObject *nativePointer = PyObjectHandle_ForJavaType(jtype);

    // We eagerly create a native pointer for all builtin types. This is necessary for pointer comparisons to work correctly.
    // TODO Remove this as soon as this is properly supported.
    truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Set_Ptr", ptype, nativePointer);

    unsigned long original_flags = structure->tp_flags;
    Py_ssize_t basicsize = structure->tp_basicsize;
    PyTypeObject* type_handle = truffle_assign_managed(structure, polyglot_as__typeobject(ptype));
    // write flags as specified in the dummy to the PythonClass object
    type_handle->tp_flags = original_flags | Py_TPFLAGS_READY;
    type_handle->tp_basicsize = basicsize;
}

static void initialize_globals() {
    // None
    void *jnone = polyglot_as__object(to_sulong(polyglot_invoke(PY_TRUFFLE_CEXT, "Py_None")));
    truffle_assign_managed(&_Py_NoneStruct, jnone);

    // NotImplemented
    void *jnotimpl = polyglot_as__object(to_sulong(polyglot_get_member(PY_BUILTIN, "NotImplemented")));
    truffle_assign_managed(&_Py_NotImplementedStruct, jnotimpl);

    // Ellipsis
    void *jellipsis = polyglot_as__object(to_sulong(polyglot_invoke(PY_TRUFFLE_CEXT, "Py_Ellipsis")));
    truffle_assign_managed(&_Py_EllipsisObject, jellipsis);

    // True, False
    void *jtrue = polyglot_invoke(PY_TRUFFLE_CEXT, "Py_True");
    truffle_assign_managed(&_Py_TrueStruct, polyglot_as__longobject(to_sulong(jtrue)));
    void *jfalse = polyglot_invoke(PY_TRUFFLE_CEXT, "Py_False");
    truffle_assign_managed(&_Py_FalseStruct, polyglot_as__longobject(to_sulong(jfalse)));

    // error marker
    void *jerrormarker = polyglot_as__object(polyglot_get_member(PY_TRUFFLE_CEXT, "error_handler"));
    truffle_assign_managed(&marker_struct, jerrormarker);
}

static void initialize_bufferprocs() {
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_SetBufferProcs", to_java((PyObject*)&PyBytes_Type), (getbufferproc)bytes_buffer_getbuffer, (releasebufferproc)NULL);
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_SetBufferProcs", to_java((PyObject*)&PyByteArray_Type), (getbufferproc)NULL, (releasebufferproc)NULL);
    polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_SetBufferProcs", to_java((PyObject*)&PyBuffer_Type), (getbufferproc)bufferdecorator_getbuffer, (releasebufferproc)NULL);
}

__attribute__((constructor))
static void initialize_capi() {
    // initialize base types
    initialize_type_structure(&PyType_Type, "type");
    initialize_type_structure(&PyBaseObject_Type, "object");
    initialize_type_structure(&PySuper_Type, "super");
    initialize_type_structure(&_PyNone_Type, "NoneType");
    initialize_type_structure(&PyModule_Type, "module");
    initialize_type_structure(&PyUnicode_Type, "str");
    initialize_type_structure(&PyBool_Type, "bool");
    initialize_type_structure(&PyFloat_Type, "float");
    initialize_type_structure(&PyLong_Type, "int");
    initialize_type_structure(&PyBytes_Type, "bytes");
    initialize_type_structure(&PyDict_Type, "dict");
    initialize_type_structure(&PyTuple_Type, "tuple");
    initialize_type_structure(&PyList_Type, "list");
    initialize_type_structure(&PyDictProxy_Type, "mappingproxy");
    initialize_type_structure(&PyComplex_Type, "complex");
    initialize_type_structure(&PySlice_Type, "slice");
    initialize_type_structure(&PyByteArray_Type, "bytearray");
    initialize_type_structure(&_PyNotImplemented_Type, "NotImplementedType");
    initialize_type_structure(&PyCapsule_Type, "PyCapsule");
    initialize_type_structure(&PyMemoryView_Type, "memoryview");
    initialize_type_structure(&PyCFunction_Type, "function");
    initialize_type_structure(&PyFrozenSet_Type, "frozenset");
    initialize_type_structure(&PySet_Type, "set");
    initialize_type_structure(&PyEllipsis_Type, "ellipsis");
    initialize_type_structure(&PyBuffer_Type, "buffer");

    // initialize global variables like '_Py_NoneStruct', etc.
    initialize_globals();

    initialize_exceptions();
    initialize_hashes();
    initialize_bufferprocs();
}

void* native_to_java(PyObject* obj) {
    if (obj == Py_None) {
        return Py_None;
    } else if (obj == NULL) {
    	return Py_NoValue;
    } else if (polyglot_is_string(obj)) {
        return obj;
    } else if (truffle_is_handle_to_managed(obj)) {
    	return truffle_managed_from_handle(obj);
    } else if (truffle_is_handle_to_managed(obj->ob_refcnt)) {
        return truffle_managed_from_handle(obj->ob_refcnt);
    }
    return obj;
}

void* to_java(PyObject* obj) {
	PyObject* managed_obj = native_to_java(obj);

	// Since Python object respond to 'IS_POINTER' with true if there has already
	// been a 'TO_NATIVE' before, we need to first check if it is directly a Python
	// object to avoid conversion to a pointer.
 	if (truffle_invoke(PY_TRUFFLE_CEXT, "is_python_object", managed_obj)) {
   		return managed_obj;
   	}
    return truffle_invoke(PY_TRUFFLE_CEXT, "to_java", managed_obj);
}

void* to_java_type(PyTypeObject* cls) {
	return to_java((PyObject*)cls);
}


#define PyTruffle_FastSubclass(__flags, __reference_flags) ((__flags) & (__reference_flags))

__attribute__((always_inline))
static inline PyObject* PyTruffle_Explicit_Cast(PyObject* cobj, unsigned long flags) {
    if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_TYPE_SUBCLASS)) {
    	return (PyObject*)polyglot_as__typeobject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_TUPLE_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyTupleObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_LIST_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyListObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_DICT_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyDictObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_UNICODE_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyUnicodeObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_BYTES_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyBytesObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_LONG_SUBCLASS)) {
    	return (PyObject*)polyglot_as__longobject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_BASE_EXC_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyBaseExceptionObject(cobj);
    }
    return (PyObject*)polyglot_as_PyVarObject(cobj);
}


__attribute__((always_inline))
inline PyObject* explicit_cast(PyObject* cobj) {
    if(polyglot_is_value(cobj)) {
        unsigned long flags = polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_GetTpFlags", cobj));
        return PyTruffle_Explicit_Cast(cobj, flags);
    }
    return cobj;
}

PyObject* to_sulong(void *o) {
    return explicit_cast(truffle_invoke(PY_TRUFFLE_CEXT, "to_sulong", o));
}

void* get_ob_type(PyObject* obj) {
    return to_java_type(obj->ob_type);
}

typedef struct PyObjectHandle {
    PyObject_HEAD
} PyObjectHandle;

uint64_t PyTruffle_Wchar_Size() {
    return SIZEOF_WCHAR_T;
}

PyObject* PyObjectHandle_ForJavaObject(PyObject* jobj, unsigned long flags) {
	if (!truffle_is_handle_to_managed(jobj)) {
		PyObject* cobj = truffle_invoke(PY_TRUFFLE_CEXT, "to_sulong", jobj);
		if(polyglot_is_value(cobj)) {
			cobj = PyTruffle_Explicit_Cast(cobj, flags);
		}
		return truffle_deref_handle_for_managed(cobj);
	}
	return jobj;
}

/** to be used from Java code only; only creates the deref handle */
PyTypeObject* PyObjectHandle_ForJavaType(void* jobj) {
	if (!truffle_is_handle_to_managed(jobj)) {
		PyTypeObject* jtypeobj = polyglot_as__typeobject(to_sulong(jobj));
		PyTypeObject* deref_handle = truffle_deref_handle_for_managed(jtypeobj);
		return deref_handle;
	}
	return jobj;
}

/** to be used from Java code only; creates the deref handle for a sequence wrapper */
void* NativeHandle_ForArray(void* jobj, ssize_t element_size) {
    // TODO do polyglot typecast depending on element_size
    return truffle_deref_handle_for_managed(jobj);
}

const char* PyTruffle_StringToCstr(void* jlString) {
    return truffle_string_to_cstr(jlString);
}

#define ReadMember(object, offset, T) ((T*)(((char*)object) + PyLong_AsSsize_t(offset)))[0]

PyObject* ReadShortMember(PyObject* object, PyObject* offset) {
	return PyLong_FromLong(ReadMember(object, offset, short));
}

PyObject* ReadIntMember(PyObject* object, PyObject* offset) {
	return PyLong_FromLong(ReadMember(object, offset, int));
}

PyObject* ReadLongMember(PyObject* object, PyObject* offset) {
	return PyLong_FromLong(ReadMember(object, offset, long));
}

PyObject* ReadFloatMember(PyObject* object, PyObject* offset) {
	return PyFloat_FromDouble(ReadMember(object, offset, float));
}

PyObject* ReadDoubleMember(PyObject* object, PyObject* offset) {
	return PyFloat_FromDouble(ReadMember(object, offset, double));
}

PyObject* ReadStringMember(PyObject* object, PyObject* offset) {
    return (PyObject*)polyglot_from_string(ReadMember(object, offset, char*), "utf-8");
}

PyObject* ReadObjectMember(PyObject* object, PyObject* offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        return Py_None;
    } else {
        return to_java(member);
    }
}

PyObject* ReadCharMember(PyObject* object, PyObject* offset) {
    return polyglot_from_string_n(&ReadMember(object, offset, char), 1, "utf-8");
}

PyObject* ReadByteMember(PyObject* object, PyObject* offset) {
	return PyLong_FromLong(ReadMember(object, offset, char));
}

PyObject* ReadUByteMember(PyObject* object, PyObject* offset) {
	return PyLong_FromUnsignedLong(ReadMember(object, offset, unsigned char));
}

PyObject* ReadUShortMember(PyObject* object, PyObject* offset) {
	return PyLong_FromUnsignedLong(ReadMember(object, offset, unsigned short));
}

PyObject* ReadUIntMember(PyObject* object, PyObject* offset) {
	return PyLong_FromUnsignedLong(ReadMember(object, offset, unsigned int));
}

PyObject* ReadULongMember(PyObject* object, PyObject* offset) {
	return PyLong_FromUnsignedLong(ReadMember(object, offset, unsigned long));
}

PyObject* ReadBoolMember(PyObject* object, PyObject* offset) {
    char flag = ReadMember(object, offset, char);
    return flag ? Py_True : Py_False;
}

PyObject* ReadObjectExMember(PyObject* object, PyObject* offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        PyErr_SetString(PyExc_ValueError, "member must not be NULL");
        return NULL;
    } else {
        return to_java(member);
    }
}

PyObject* ReadLongLongMember(PyObject* object, PyObject* offset) {
	return PyLong_FromLongLong(ReadMember(object, offset, long long));
}

PyObject* ReadULongLongMember(PyObject* object, PyObject* offset) {
	return PyLong_FromUnsignedLongLong(ReadMember(object, offset, unsigned long long));
}

PyObject* ReadPySSizeT(PyObject* object, PyObject* offset) {
	return PyLong_FromSsize_t(ReadMember(object, offset, Py_ssize_t));
}

#undef ReadMember

#define WriteMember(object, offset, value, T) *(T*)(((char*)object) + PyLong_AsSsize_t(offset)) = (T)(value)

PyObject* WriteShortMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), short);
    return value;
}

PyObject* WriteIntMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), int);
    return value;
}

PyObject* WriteLongMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), long);
    return value;
}

PyObject* WriteFloatMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyFloat_AsDouble(value), float);
    return value;
}

PyObject* WriteDoubleMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyFloat_AsDouble(value), double);
    return value;
}

PyObject* WriteStringMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, as_char_pointer(value), char*);
    return value;
}

PyObject* WriteObjectMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, value, PyObject*);
    return value;
}

PyObject* WriteCharMember(PyObject* object, PyObject* offset, PyObject* value) {
    const char* ptr = as_char_pointer(value);
    const char c = ptr[0];
    truffle_free_cstr(ptr);
    WriteMember(object, offset, c, char);
    return value;
}

PyObject* WriteByteMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), char);
    return value;
}

PyObject* WriteUByteMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsLong(value), uint8_t);
    return value;
}

PyObject* WriteUShortMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsUnsignedLong(value), unsigned short);
    return value;
}

PyObject* WriteUIntMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsUnsignedLong(value), unsigned int);
    return value;
}

PyObject* WriteULongMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, PyLong_AsUnsignedLong(value), unsigned long);
    return value;
}

PyObject* WriteBoolMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, truffle_invoke(to_java(value), "__bool__") == Py_True ? (char)1 : (char)0, char);
    return value;
}

PyObject* WriteObjectExMember(PyObject* object, PyObject* offset, PyObject* value) {
    if (value == NULL) {
        PyErr_SetString(PyExc_ValueError, "member must not be NULL");
        return NULL;
    } else {
        WriteMember(object, offset, value, PyObject*);
        return value;
    }
}

PyObject* WriteLongLongMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, value, long long);
    return value;
}

PyObject* WriteULongLongMember(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, value, unsigned long long);
    return value;
}

PyObject* WritePySSizeT(PyObject* object, PyObject* offset, PyObject* value) {
    WriteMember(object, offset, value, Py_ssize_t);
    return value;
}

PyObject marker_struct = {
  _PyObject_EXTRA_INIT
  1, &PyBaseObject_Type
};

#undef WriteMember

int PyTruffle_Debug(void *arg) {
	truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Debug", arg);
	return 0;
}

typedef PyObject* (*f0)();
typedef PyObject* (*f1)(PyObject*);
typedef PyObject* (*f2)(PyObject*, PyObject*);
typedef PyObject* (*f3)(PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f4)(PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f5)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f6)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f7)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f8)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f9)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f10)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f11)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f12)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f13)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f14)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f15)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f16)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f17)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f18)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f19)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);
typedef PyObject* (*f20)(PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*, PyObject*);

#define _PICK_FUN_CAST(DUMMY, _0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, _19, NAME, ...) NAME
#define _CALL_ARITY(FUN, ...) ( (_PICK_FUN_CAST(NULL, ##__VA_ARGS__, f20, f19, f18, f17, f16, f15, f14, f13, f12, f11, f10, f9, f8, f7, f6, f5, f4, f3, f2, f1, f0))(FUN))(__VA_ARGS__)
#define ARG(__n) explicit_cast((PyObject*)polyglot_get_arg((__n)))

void* wrap_direct(PyCFunction fun, ...) {
	PyObject *res = NULL;
	switch(polyglot_get_arg_count()-1) {
	case 0:
		res = _CALL_ARITY(fun);
		break;
	case 1:
		res = _CALL_ARITY(fun, ARG(1));
		break;
	case 2:
		res = _CALL_ARITY(fun, ARG(1), ARG(2));
		break;
	case 3:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3));
		break;
	case 4:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4));
		break;
	case 5:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5));
		break;
	case 6:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6));
		break;
	case 7:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7));
		break;
	case 8:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8));
		break;
	case 9:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9));
		break;
	case 10:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10));
		break;
	case 11:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11));
		break;
	case 12:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12));
		break;
	case 13:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13));
		break;
	case 14:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13), ARG(14));
		break;
	case 15:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13), ARG(14), ARG(15));
		break;
	case 16:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13), ARG(14), ARG(15), ARG(16));
		break;
	case 17:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13), ARG(14), ARG(15), ARG(16), ARG(17));
		break;
	case 18:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13), ARG(14), ARG(15), ARG(16), ARG(17), ARG(18));
		break;
	case 19:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13), ARG(14), ARG(15), ARG(16), ARG(17), ARG(18), ARG(19));
		break;
	case 20:
		res = _CALL_ARITY(fun, ARG(1), ARG(2), ARG(3), ARG(4), ARG(5), ARG(6), ARG(7), ARG(8), ARG(9), ARG(10), ARG(11), ARG(12), ARG(13), ARG(14), ARG(15), ARG(16), ARG(17), ARG(18), ARG(19), ARG(20));
		break;
	default:
	    _PyErr_BadInternalCall(__FILE__, __LINE__);
	    res = NULL;
	}
	return native_to_java(res);
}

void* wrap_varargs(PyCFunction fun, PyObject *module, PyObject *varargs) {
	return native_to_java(fun(explicit_cast(module), explicit_cast(varargs)));
}

void* wrap_keywords(PyCFunctionWithKeywords fun, PyObject *module, PyObject *varargs, PyObject *kwargs) {
	return native_to_java(fun(explicit_cast(module), explicit_cast(varargs), explicit_cast(kwargs)));
}

void* wrap_noargs(PyCFunction fun, PyObject *module, PyObject *pnone) {
	return native_to_java(fun(explicit_cast(module), explicit_cast(pnone)));
}

void* wrap_fastcall(_PyCFunctionFast fun, PyObject *self, PyObject **args, Py_ssize_t nargs, PyObject *kwnames) {
	Py_ssize_t i;
    for (i=0; i < nargs; i++) {
    	args[i] = explicit_cast(args[i]);
    }
	return native_to_java(fun(explicit_cast(self), args, nargs, explicit_cast(kwnames)));
}

void* wrap_unsupported(void *fun, ...) {
	return NULL;
}
