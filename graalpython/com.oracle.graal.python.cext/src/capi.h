#ifndef CAPI_H
#define CAPI_H

#include "Python.h"


#define to_sulong(o) truffle_invoke(PY_TRUFFLE_CEXT, "to_sulong", o)

extern void* to_java(PyObject* obj);
extern void* to_java_type(PyTypeObject* cls);
#define as_char_pointer(obj) truffle_invoke(PY_TRUFFLE_CEXT, "to_char_pointer", to_java(obj))
#define as_long(obj) truffle_invoke(PY_TRUFFLE_CEXT, "to_long", obj)
#define as_int(obj) ((int)as_long(obj))
#define as_short(obj) ((short)as_long(obj))
#define as_uchar(obj) ((unsigned char)as_long(obj))
#define as_char(obj) ((char)as_long(obj))
#define as_double(obj) truffle_invoke_d(PY_TRUFFLE_CEXT, "to_double", to_java(obj))
#define as_float(obj) ((float)as_double(obj))
#define marry_objects(obj, jobj) {                                      \
        ((PyObject*)obj)->ob_refcnt = truffle_handle_for_managed(jobj); \
        truffle_invoke(PY_TRUFFLE_CEXT, "marry_objects", jobj, obj);    \
        ((PyObject*)obj)->ob_type = to_sulong(truffle_invoke(PY_BUILTIN, "type", jobj)); \
    }

// defined in 'exceptions.c'
void initialize_exceptions();

void* PyTruffle_Unicode_FromUTF8(const char* o, void *error_marker);

#define write_struct_field(object, struct, fieldname, value)            \
    truffle_write(to_java(object),                                      \
                  #fieldname ,                                          \
                  to_java(value));                                      \
    truffle_write(to_java(object),                                      \
                  offsetof(struct, fieldname),                          \
                  to_java(value));


#define TDEBUG __asm__("int $3")
#define get_method_flags_wrapper(flags)                                 \
    (((flags) < 0) ?                                                    \
     truffle_read(PY_TRUFFLE_CEXT, "METH_DIRECT") :                     \
     (((flags) & METH_KEYWORDS) ?                                       \
      truffle_read(PY_TRUFFLE_CEXT, "METH_KEYWORDS") :                  \
      (((flags) & METH_VARARGS) ?                                       \
       truffle_read(PY_TRUFFLE_CEXT, "METH_VARARGS") :                  \
       (((flags) & METH_NOARGS) ?                                       \
        truffle_read(PY_TRUFFLE_CEXT, "METH_NOARGS") :                  \
        (((flags) & METH_O) ?                                           \
         truffle_read(PY_TRUFFLE_CEXT, "METH_O") :                      \
         (((flags) & METH_FASTCALL) ?                                   \
          truffle_read(PY_TRUFFLE_CEXT, "METH_FASTCALL") :              \
          truffle_read(PY_TRUFFLE_CEXT, "METH_UNSUPPORTED")))))))


extern short ReadShortMember(PyObject* object, int offset);
extern int ReadIntMember(PyObject* object, int offset);
extern long ReadLongMember(PyObject* object, int offset);
extern float ReadFloatMember(PyObject* object, int offset);
extern double ReadDoubleMember(PyObject* object, int offset);
extern void* ReadStringMember(PyObject* object, int offset);
extern PyObject* ReadObjectMember(PyObject* object, int offset);
extern char ReadCharMember(PyObject* object, int offset);
extern char ReadByteMember(PyObject* object, int offset);
extern unsigned char ReadUByteMember(PyObject* object, int offset);
extern unsigned short ReadUShortMember(PyObject* object, int offset);
extern unsigned int ReadUIntMember(PyObject* object, int offset);
extern unsigned long ReadULongMember(PyObject* object, int offset);
extern char ReadBoolMember(PyObject* object, int offset);
extern PyObject* ReadObjectExMember(PyObject* object, int offset);
extern long long ReadLongLongMember(PyObject* object, int offset);
extern unsigned long long ReadULongLongMember(PyObject* object, int offset);
extern Py_ssize_t ReadPySSizeT(PyObject* object, int offset);

extern void WriteShortMember(PyObject* object, int offset, short value);
extern void WriteIntMember(PyObject* object, int offset, int value);
extern void WriteLongMember(PyObject* object, int offset, long value);
extern void WriteFloatMember(PyObject* object, int offset, float value);
extern void WriteDoubleMember(PyObject* object, int offset, double value);
extern void WriteStringMember(PyObject* object, int offset, PyObject* value);
extern void WriteObjectMember(PyObject* object, int offset, PyObject* value);
extern void WriteCharMember(PyObject* object, int offset, PyObject* value);
extern void WriteByteMember(PyObject* object, int offset, PyObject* value);
extern void WriteUByteMember(PyObject* object, int offset, PyObject* value);
extern void WriteUShortMember(PyObject* object, int offset, unsigned short value);
extern void WriteUIntMember(PyObject* object, int offset, unsigned int value);
extern void WriteULongMember(PyObject* object, int offset, unsigned long value);
extern void WriteBoolMember(PyObject* object, int offset, PyObject* value);
extern void WriteObjectExMember(PyObject* object, int offset, PyObject* value);
extern void WriteLongLongMember(PyObject* object, int offset, long long value);
extern void WriteULongLongMember(PyObject* object, int offset, unsigned long long value);
extern void WritePySSizeT(PyObject* object, int offset, Py_ssize_t value);

extern PyObject marker_struct;
#define ERROR_MARKER &marker_struct

#endif
