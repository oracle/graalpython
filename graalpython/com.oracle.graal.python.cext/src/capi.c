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
    PyTypeObject* ptype = (PyTypeObject *)to_sulong(truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Type", truffle_read_string(typname)));
    unsigned long original_flags = structure->tp_flags;
    PyTypeObject* type_handle = truffle_assign_managed(structure, polyglot_as__typeobject(ptype));
    // write flags as specified in the dummy to the PythonClass object
    type_handle->tp_flags = original_flags | Py_TPFLAGS_READY;
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

    // initialize global variables like '_Py_NoneStruct', etc.
    initialize_globals();

    initialize_exceptions();
    initialize_hashes();
}

void* native_to_java(PyObject* obj) {
	if (obj == Py_None) {
        return Py_None;
    } else if (obj == NULL) {
    	return Py_NoValue;
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
    if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_TUPLE_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyTupleObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_LIST_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyListObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_DICT_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyDictObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_UNICODE_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyUnicodeObject(cobj);
    } else if (PyTruffle_FastSubclass(flags, Py_TPFLAGS_BYTES_SUBCLASS)) {
    	return (PyObject*)polyglot_as_PyBytesObject(cobj);
    }
    return (PyObject*)polyglot_as_PyVarObject(cobj);
}


PyObject* to_sulong(void *o) {
    PyObject* cobj = truffle_invoke(PY_TRUFFLE_CEXT, "to_sulong", o);
    if(polyglot_is_value(cobj)) {
        unsigned long flags = polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyTruffle_GetTpFlags", cobj));
        return PyTruffle_Explicit_Cast(cobj, flags);
    }
    return cobj;
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

const char* PyTruffle_StringToCstr(void* jlString) {
    return truffle_string_to_cstr(jlString);
}

#define ReadMember(object, offset, T) ((T*)(((char*)object) + offset))[0]

short ReadShortMember(PyObject* object, int offset) {
    return ReadMember(object, offset, short);
}

int ReadIntMember(PyObject* object, int offset) {
    return ReadMember(object, offset, int);
}

long ReadLongMember(PyObject* object, int offset) {
    return ReadMember(object, offset, long);
}

float ReadFloatMember(PyObject* object, int offset) {
    return ReadMember(object, offset, float);
}

double ReadDoubleMember(PyObject* object, int offset) {
    return ReadMember(object, offset, double);
}

void* ReadStringMember(PyObject* object, int offset) {
    return truffle_read_string(ReadMember(object, offset, char*));
}

PyObject* ReadObjectMember(PyObject* object, int offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        return Py_None;
    } else {
        return to_java(member);
    }
}

char ReadCharMember(PyObject* object, int offset) {
    return ReadMember(object, offset, char);
}

char ReadByteMember(PyObject* object, int offset) {
    return ReadMember(object, offset, char);
}

unsigned char ReadUByteMember(PyObject* object, int offset) {
    return ReadMember(object, offset, unsigned char);
}

unsigned short ReadUShortMember(PyObject* object, int offset) {
    return ReadMember(object, offset, unsigned short);
}

unsigned int ReadUIntMember(PyObject* object, int offset) {
    return ReadMember(object, offset, unsigned int);
}

unsigned long ReadULongMember(PyObject* object, int offset) {
    return ReadMember(object, offset, unsigned long);
}

char ReadBoolMember(PyObject* object, int offset) {
    char flag = ReadMember(object, offset, char);
    return flag ? Py_True : Py_False;
}

PyObject* ReadObjectExMember(PyObject* object, int offset) {
    PyObject* member = ReadMember(object, offset, PyObject*);
    if (member == NULL) {
        PyErr_SetString(PyExc_ValueError, "member must not be NULL");
        return NULL;
    } else {
        return to_java(member);
    }
}

long long ReadLongLongMember(PyObject* object, int offset) {
    return ReadMember(object, offset, long long);
}

unsigned long long ReadULongLongMember(PyObject* object, int offset) {
    return ReadMember(object, offset, unsigned long long);
}

Py_ssize_t ReadPySSizeT(PyObject* object, int offset) {
    return ReadMember(object, offset, Py_ssize_t);
}

#undef ReadMember

#define WriteMember(object, offset, value, T) *(T*)(((char*)object) + offset) = value;

void WriteShortMember(PyObject* object, int offset, short value) {
    WriteMember(object, offset, value, short);
}

void WriteIntMember(PyObject* object, int offset, int value) {
    WriteMember(object, offset, value, int);
}

void WriteLongMember(PyObject* object, int offset, long value) {
    WriteMember(object, offset, value, long);
}

void WriteFloatMember(PyObject* object, int offset, float value) {
    WriteMember(object, offset, value, float);
}

void WriteDoubleMember(PyObject* object, int offset, double value) {
    WriteMember(object, offset, value, double);
}

void WriteStringMember(PyObject* object, int offset, PyObject* value) {
    WriteMember(object, offset, as_char_pointer(value), char*);
}

void WriteObjectMember(PyObject* object, int offset, PyObject* value) {
    WriteMember(object, offset, value, PyObject*);
}

void WriteCharMember(PyObject* object, int offset, PyObject* value) {
    const char* ptr = as_char_pointer(value);
    const char c = ptr[0];
    truffle_free_cstr(ptr);
    WriteMember(object, offset, c, char);
}

void WriteByteMember(PyObject* object, int offset, PyObject* value) {
    WriteCharMember(object, offset, value);
}

void WriteUByteMember(PyObject* object, int offset, PyObject* value) {
    WriteCharMember(object, offset, value);
}

void WriteUShortMember(PyObject* object, int offset, unsigned short value) {
    WriteMember(object, offset, value, unsigned short);
}

void WriteUIntMember(PyObject* object, int offset, unsigned int value) {
    WriteMember(object, offset, value, unsigned int);
}

void WriteULongMember(PyObject* object, int offset, unsigned long value) {
    WriteMember(object, offset, value, unsigned long);
}

void WriteBoolMember(PyObject* object, int offset, PyObject* value) {
    WriteMember(object, offset, truffle_invoke(to_java(value), "__bool__") == Py_True ? (char)1 : (char)0, char);
}

void WriteObjectExMember(PyObject* object, int offset, PyObject* value) {
    if (value == NULL) {
        PyErr_SetString(PyExc_ValueError, "member must not be NULL");
    } else {
        WriteMember(object, offset, value, PyObject*);
    }
}

void WriteLongLongMember(PyObject* object, int offset, long long value) {
    WriteMember(object, offset, value, long long);
}

void WriteULongLongMember(PyObject* object, int offset, unsigned long long value) {
    WriteMember(object, offset, value, unsigned long long);
}

void WritePySSizeT(PyObject* object, int offset, Py_ssize_t value) {
    WriteMember(object, offset, value, Py_ssize_t);
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
