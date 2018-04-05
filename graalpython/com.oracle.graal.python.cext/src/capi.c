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

void marry_objects(PyObject* obj, void* jobj) {
    obj->ob_refcnt = truffle_handle_for_managed(jobj);
    truffle_invoke(PY_TRUFFLE_CEXT, "marry_objects", jobj, obj);
    void *type = (PyTypeObject *)truffle_invoke(PY_BUILTIN, "type", jobj);
    obj->ob_type = PyObjectHandle_ForJavaType(type);
}

static void initialize_type_structure(PyTypeObject* structure, const char* typname) {
    PyTypeObject* ptype = polyglot_as__typeobject(truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Type", truffle_read_string(typname)));
    unsigned long original_flags = structure->tp_flags;
    PyTypeObject* type_handle = truffle_assign_managed(structure, ptype);
    // write flags as specified in the dummy to the PythonClass object
    type_handle->tp_flags = original_flags | Py_TPFLAGS_READY;
}

__attribute__((constructor))
static void initialize_capi() {
    // initialize base types
    initialize_type_structure(&PyType_Type, "type");
    initialize_type_structure(&PyBaseObject_Type, "object");
    initialize_type_structure(&PySuper_Type, "super");
    initialize_type_structure(&PyUnicode_Type, "str");
    initialize_type_structure(&PyBool_Type, "bool");
    initialize_type_structure(&PyFloat_Type, "float");
    initialize_type_structure(&PyLong_Type, "int");
    initialize_type_structure(&PyBytes_Type, "bytes");
    initialize_type_structure(&PyDict_Type, "dict");
    initialize_type_structure(&PyTuple_Type, "tuple");
    initialize_type_structure(&PyList_Type, "list");
    initialize_type_structure(&PyDictProxy_Type, "mappingproxy");

    initialize_exceptions();
}

void* to_java(PyObject* obj) {
	if (obj == &_Py_NoneStruct) {
        return Py_None;
    } else if (obj == NULL) {
    	return Py_NoValue;
    } else if (truffle_is_handle_to_managed(obj)) {
        return truffle_invoke(PY_TRUFFLE_CEXT, "to_java", truffle_managed_from_handle(obj));
    } else if (truffle_is_handle_to_managed(obj->ob_refcnt)) {
        return truffle_managed_from_handle(obj->ob_refcnt);
    } else {
        return truffle_invoke(PY_TRUFFLE_CEXT, "to_java", obj);
    }
}

void* to_java_type(PyTypeObject* cls) {
    PyType_Ready(cls); // make sure we have an associated Java class
    return truffle_managed_from_handle(((PyObject*)cls)->ob_refcnt);
}

void* get_ob_type(PyObject* obj) {
    return to_java_type(obj->ob_type);
}

typedef struct PyObjectHandle {
    PyObject_HEAD
} PyObjectHandle;

PyObject* PyNoneHandle() {
    return &_Py_NoneStruct;
}

PyObject* PyObjectHandle_ForJavaObject(PyObject* jobject) {
    PyObject* obj = (PyObject*)PyObject_Malloc(sizeof(PyObjectHandle));
    marry_objects(obj, jobject);
    return obj;
}

PyTypeObject* PyObjectHandle_ForJavaType(void* jobj) {
	// A handle is created at the first time we
	if (!truffle_is_handle_to_managed(jobj)) {
		PyTypeObject* jtypeobj = polyglot_as__typeobject(jobj);
		PyTypeObject* deref_handle = truffle_deref_handle_for_managed(jtypeobj);
		truffle_invoke(PY_TRUFFLE_CEXT, "marry_objects", jtypeobj, deref_handle);
		return deref_handle;
	}
	return jobj;
}

const char* PyTruffle_StringToCstr(void* jlString) {
    return truffle_string_to_cstr(jlString);
}

/** like 'truffle_read_string' but uses UTF-8 encoding (also returns a String object) */
void* PyTruffle_Unicode_FromUTF8(const char* o, void *error_marker) {
	void* jobj = truffle_read_bytes(o);
	return truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Unicode_FromUTF8", jobj, error_marker);
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
  1, &_PyNone_Type
};

#undef WriteMember

int PyTruffle_Debug(void *arg) {
	truffle_invoke(PY_TRUFFLE_CEXT, "PyTruffle_Debug", arg);
	return 0;
}
