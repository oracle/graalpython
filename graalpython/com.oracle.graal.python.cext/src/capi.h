/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#ifndef CAPI_H
#define CAPI_H

#define MUST_INLINE __attribute__((always_inline)) inline

#include "Python.h"

#define SRC_CS "utf-8"

/* Private types are defined here because we need to declare the type cast. */
typedef struct {
    PyObject_HEAD
    PyObject *md_dict;
    struct PyModuleDef *md_def;
    void *md_state;
    PyObject *md_weaklist;
    PyObject *md_name;  /* for logging purposes after md_dict is cleared */
} PyModuleObject;

// taken from CPython "Objects/capsule.c"
typedef struct {
    PyObject_HEAD
    void *pointer;
    const char *name;
    void *context;
    PyCapsule_Destructor destructor;
} PyCapsule;

typedef struct {
    PyObject_VAR_HEAD
    int readonly;
    void *buf_delegate;
} PyBufferDecorator;

PyAPI_DATA(PyTypeObject) PyBuffer_Type;
PyAPI_DATA(PyTypeObject) _PyExc_BaseException;

// TODO cache landing function ?
#define PY_TRUFFLE_LANDING ((PyObject*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall", SRC_CS)))
#define PY_TRUFFLE_LANDING_L ((PyObject*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_l", SRC_CS)))
#define PY_TRUFFLE_LANDING_D ((PyObject*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_d", SRC_CS)))
#define PY_TRUFFLE_LANDING_PTR ((void*(*)(void *rcv, void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Upcall_ptr", SRC_CS)))
#define PY_TRUFFLE_CEXT_LANDING ((PyObject*(*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall", SRC_CS)))
#define PY_TRUFFLE_CEXT_LANDING_L ((uint64_t (*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_l", SRC_CS)))
#define PY_TRUFFLE_CEXT_LANDING_D ((double (*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_d", SRC_CS)))
#define PY_TRUFFLE_CEXT_LANDING_PTR ((void* (*)(void* name, ...))polyglot_get_member(PY_TRUFFLE_CEXT, polyglot_from_string("PyTruffle_Cext_Upcall_ptr", SRC_CS)))

/* upcall macros for calling into Python */

/* Call function with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_O(__recv__, __name__, ...) handle_exception(PY_TRUFFLE_LANDING((__recv__), polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function with a primitive return; no polyglot cast but error handling */
#define UPCALL_P(__recv__, __name__, ...) (PY_TRUFFLE_LANDING_L((__recv__), polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function with return type 'int'; no polyglot cast but error handling */
#define UPCALL_I(__recv__, __name__, ...) UPCALL_P(__recv__, __name__, ##__VA_ARGS__)

/* Call function with return type 'long'; no polyglot cast but error handling */
#define UPCALL_L(__recv__, __name__, ...) UPCALL_P(__recv__, __name__, ##__VA_ARGS__)

/* Call function with return type 'double'; no polyglot cast but error handling */
#define UPCALL_D(__recv__, __name__, ...) handle_exception(PY_TRUFFLE_LANDING_D((__recv__), polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function with return type 'void*'; no polyglot cast and no error handling */
#define UPCALL_PTR(__name__, ...) (PY_TRUFFLE_LANDING_PTR(polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'PyObject *'; does polyglot cast and error handling */
#define UPCALL_CEXT_O(__name__, ...) handle_exception(PY_TRUFFLE_CEXT_LANDING(polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call void function of 'python_cext' module; no polyglot cast and no error handling */
#define UPCALL_CEXT_VOID(__name__, ...) (PY_TRUFFLE_CEXT_LANDING(polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'PyObject*'; no polyglot cast but error handling */
#define UPCALL_CEXT_NOCAST(__name__, ...) handle_exception(PY_TRUFFLE_CEXT_LANDING(polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'void*'; no polyglot cast and no error handling */
#define UPCALL_CEXT_PTR(__name__, ...) (PY_TRUFFLE_CEXT_LANDING_PTR(polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function of 'python_cext' module with a primitive return; no polyglot cast but error handling */
#define UPCALL_CEXT_P(__name__, ...) (PY_TRUFFLE_CEXT_LANDING_L(polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

/* Call function of 'python_cext' module with return type 'int'; no polyglot cast but error handling */
#define UPCALL_CEXT_I(__name__, ...) UPCALL_CEXT_P(__name__, ##__VA_ARGS__)

/* Call function of 'python_cext' module with return type 'long'; no polyglot cast but error handling */
#define UPCALL_CEXT_L(__name__, ...) UPCALL_CEXT_P(__name__, ##__VA_ARGS__)

/* Call function of 'python_cext' module with return type 'double'; no polyglot cast but error handling */
#define UPCALL_CEXT_D(__name__, ...) (PY_TRUFFLE_CEXT_LANDING_D(polyglot_from_string((__name__), SRC_CS), ##__VA_ARGS__))

#define as_char_pointer(obj) ((const char*)UPCALL_CEXT_PTR("to_char_pointer", native_to_java(obj)))
#define as_long(obj) ((long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "to_long", to_java(obj))))
#define as_long_long(obj) ((long long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), 1, sizeof(long long), polyglot_from_string("long long", "utf-8"))))
#define as_unsigned_long_long(obj) ((unsigned long long)polyglot_as_i64(polyglot_invoke(PY_TRUFFLE_CEXT, "PyLong_AsPrimitive", to_java(obj), 0, sizeof(unsigned long long), polyglot_from_string("unsigned long long", "utf-8"))))
#define as_int(obj) ((int)as_long(obj))
#define as_short(obj) ((short)as_long(obj))
#define as_uchar(obj) ((unsigned char)as_long(obj))
#define as_char(obj) ((char)as_long(obj))
#define as_double(obj) polyglot_as_double(polyglot_invoke(PY_TRUFFLE_CEXT, "to_double", to_java(obj)))
#define as_float(obj) ((float)as_double(obj))


void* handle_exception(void* val);
void* native_to_java(PyObject* obj);
extern void* to_java(PyObject* obj);
extern void* to_java_type(PyTypeObject* cls);
extern PyObject* to_sulong(void *o);

// defined in 'exceptions.c'
void initialize_exceptions();
// defined in 'pyhash.c'
void initialize_hashes();

// prototype of C landing function
void* wrap_direct(PyCFunction fun, ...);
int wrap_setter(PyCFunction fun, PyObject *self, PyObject *value, void *closure);
void* wrap_varargs(PyCFunction fun, PyObject *module, PyObject *varargs);
void* wrap_noargs(PyCFunction fun, PyObject *module, PyObject *pnone);
void* wrap_keywords(PyCFunctionWithKeywords fun, PyObject *module, PyObject *varargs, PyObject *kwargs);
void* wrap_fastcall(_PyCFunctionFast        fun, PyObject *  self, PyObject   **args, PyObject  *nargs, PyObject *kwnames);
void* wrap_unsupported(void *fun, ...);

#define TDEBUG __asm__("int $3")
#define get_method_flags_wrapper(flags)                                 \
    (((flags) < 0) ?                                                    \
     truffle_read(PY_TRUFFLE_CEXT, "METH_DIRECT") :                     \
     (((flags) & METH_FASTCALL) ?                                       \
      truffle_read(PY_TRUFFLE_CEXT, "METH_FASTCALL") :                  \
      (((flags) & METH_KEYWORDS) ?                                       \
       truffle_read(PY_TRUFFLE_CEXT, "METH_KEYWORDS") :                  \
       (((flags) & METH_VARARGS) ?                                       \
        truffle_read(PY_TRUFFLE_CEXT, "METH_VARARGS") :                  \
        (((flags) & METH_NOARGS) ?                                           \
         truffle_read(PY_TRUFFLE_CEXT, "METH_NOARGS") :                      \
         (((flags) & METH_O) ?                                   \
          truffle_read(PY_TRUFFLE_CEXT, "METH_O") :              \
          truffle_read(PY_TRUFFLE_CEXT, "METH_UNSUPPORTED")))))))

#define get_method_flags_cwrapper(flags)                                \
    (void*)((((flags) < 0) ?                                            \
     wrap_direct :                                                      \
     (((flags) & METH_FASTCALL) ?                                       \
      wrap_fastcall :                                                   \
      (((flags) & METH_KEYWORDS) ?                                      \
       wrap_keywords :                                                   \
       (((flags) & METH_VARARGS) ?                                       \
        wrap_varargs :                                                   \
        (((flags) & METH_NOARGS) ?                                           \
         wrap_noargs :                                                  \
         (((flags) & METH_O) ?                                   \
          wrap_direct :                                               \
          wrap_unsupported)))))))

#define PY_TRUFFLE_TYPE(__TYPE_NAME__, __SUPER_TYPE__, __FLAGS__, __SIZE__) {\
    PyVarObject_HEAD_INIT((__SUPER_TYPE__), 0)\
    __TYPE_NAME__,                              /* tp_name */\
    (__SIZE__),                                 /* tp_basicsize */\
    0,                                          /* tp_itemsize */\
    0,                                          /* tp_dealloc */\
    0,                                          /* tp_print */\
    0,                                          /* tp_getattr */\
    0,                                          /* tp_setattr */\
    0,                                          /* tp_reserved */\
    0,                                          /* tp_repr */\
    0,                                          /* tp_as_number */\
    0,                                          /* tp_as_sequence */\
    0,                                          /* tp_as_mapping */\
    0,                                          /* tp_hash */\
    0,                                          /* tp_call */\
    0,                                          /* tp_str */\
    0,                                          /* tp_getattro */\
    0,                                          /* tp_setattro */\
    0,                                          /* tp_as_buffer */\
    (__FLAGS__),                                /* tp_flags */\
    0,                                          /* tp_doc */\
    0,                                          /* tp_traverse */\
    0,                                          /* tp_clear */\
    0,                                          /* tp_richcompare */\
    0,                                          /* tp_weaklistoffset */\
    0,                                          /* tp_iter */\
    0,                                          /* tp_iternext */\
    0,                                          /* tp_methods */\
    0,                                          /* tp_members */\
    0,                                          /* tp_getset */\
    0,                                          /* tp_base */\
    0,                                          /* tp_dict */\
    0,                                          /* tp_descr_get */\
    0,                                          /* tp_descr_set */\
    0,                                          /* tp_dictoffset */\
    0,                                          /* tp_init */\
    0,                                          /* tp_alloc */\
    0,                                          /* tp_new */\
    0,                                          /* tp_free */\
    0,                                          /* tp_is_gc */\
}


int PyTruffle_Debug(void *arg);
void* PyObjectHandle_ForJavaType(void* jobj);

extern PyObject* ReadShortMember(PyObject* object, PyObject* offset);
extern PyObject* ReadIntMember(PyObject* object, PyObject* offset);
extern PyObject* ReadLongMember(PyObject* object, PyObject* offset);
extern PyObject* ReadFloatMember(PyObject* object, PyObject* offset);
extern PyObject* ReadDoubleMember(PyObject* object, PyObject* offset);
extern PyObject* ReadStringMember(PyObject* object, PyObject* offset);
extern PyObject* ReadObjectMember(PyObject* object, PyObject* offset);
extern PyObject* ReadCharMember(PyObject* object, PyObject* offset);
extern PyObject* ReadByteMember(PyObject* object, PyObject* offset);
extern PyObject* ReadUByteMember(PyObject* object, PyObject* offset);
extern PyObject* ReadUShortMember(PyObject* object, PyObject* offset);
extern PyObject* ReadUIntMember(PyObject* object, PyObject* offset);
extern PyObject* ReadULongMember(PyObject* object, PyObject* offset);
extern PyObject* ReadBoolMember(PyObject* object, PyObject* offset);
extern PyObject* ReadObjectExMember(PyObject* object, PyObject* offset);
extern PyObject* ReadLongLongMember(PyObject* object, PyObject* offset);
extern PyObject* ReadULongLongMember(PyObject* object, PyObject* offset);
extern PyObject* ReadPySSizeT(PyObject* object, PyObject* offset);

extern PyObject* WriteShortMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteIntMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteLongMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteFloatMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteDoubleMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteStringMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteObjectMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteCharMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteByteMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteUByteMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteUShortMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteUIntMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteULongMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteBoolMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteObjectExMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteLongLongMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WriteULongLongMember(PyObject* object, PyObject* offset, PyObject* value);
extern PyObject* WritePySSizeT(PyObject* object, PyObject* offset, PyObject* value);

extern PyObject marker_struct;
#define ERROR_MARKER &marker_struct

/* internal functions to avoid unnecessary managed <-> native conversions */

/* DICT */
void* PyTruffle_Tuple_GetItem(void* jtuple, Py_ssize_t position);

/* BYTES, BYTEARRAY */
int bytes_buffer_getbuffer(PyBytesObject *self, Py_buffer *view, int flags);
int bytearray_getbuffer(PyByteArrayObject *obj, Py_buffer *view, int flags);

/* Like 'memcpy' but can read/write from/to managed objects. */
int bytes_copy2mem(char* target, char* source, size_t nbytes);

/* MEMORYVIEW, BUFFERDECORATOR */
int bufferdecorator_getbuffer(PyBufferDecorator *self, Py_buffer *view, int flags);

#endif
