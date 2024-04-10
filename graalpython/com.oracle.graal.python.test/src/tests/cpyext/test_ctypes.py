import ctypes
import struct

from tests.cpyext import CPyExtTestCase, CPyExtType

BufferTester = CPyExtType(
    'BufferTester',
    cmembers='Py_buffer buffer;',
    code='''
    static PyObject* buffer_tester_new(PyTypeObject* type, PyObject* args, PyObject* kwargs) {
        PyObject* obj;
        if (PyArg_ParseTuple(args, "O", &obj) < 0)
            return NULL;
        PyObject* self = type->tp_alloc(type, 0);
        if (!self)
            return NULL;
        if (PyObject_GetBuffer(obj, &((BufferTesterObject*)self)->buffer, PyBUF_CONTIG_RO) < 0) {
            Py_DECREF(self);
            return NULL;
        }
        return self;
    }
    
    static PyObject* buffer_tester_enter(PyObject* self) {
        return Py_NewRef(self);
    }
    
    static PyObject* buffer_tester_exit(BufferTesterObject* self, PyObject* args) {
        PyBuffer_Release(&self->buffer);
        Py_RETURN_NONE;
    }
    
    static PyObject* buffer_tester_obj(BufferTesterObject* self) {
        return Py_NewRef(self->buffer.obj);
    }
    
    static PyObject* buffer_tester_bytes(BufferTesterObject* self) {
        return PyBytes_FromStringAndSize(self->buffer.buf, self->buffer.len);
    }
    
    static PyObject* buffer_tester_itemsize(BufferTesterObject* self) {
        return PyLong_FromLong(self->buffer.itemsize);
    }
    
    static PyObject* buffer_tester_format(BufferTesterObject* self) {
        return PyBytes_FromString(self->buffer.format);
    }
    ''',
    tp_new='buffer_tester_new',
    tp_methods='''
    {"__enter__", (PyCFunction)buffer_tester_enter, METH_NOARGS, ""},
    {"__exit__", (PyCFunction)buffer_tester_exit, METH_VARARGS, ""}
    ''',
    tp_getset='''
    {"obj", (getter)buffer_tester_obj, NULL, NULL, NULL},
    {"bytes", (getter)buffer_tester_bytes, NULL, NULL, NULL},
    {"itemsize", (getter)buffer_tester_itemsize, NULL, NULL, NULL},
    {"format", (getter)buffer_tester_format, NULL, NULL, NULL}
    ''',
)


class TestCDataBuffer(CPyExtTestCase):
    def test_buffer(self):
        numbers = [1, 2, 3, 4]
        array = (ctypes.c_int * 4)(*numbers)
        with BufferTester(array) as buffer:
            assert buffer.obj is array
            assert buffer.bytes == b''.join(struct.pack("i", n) for n in numbers)
            assert buffer.itemsize == 4
            assert buffer.format == b'<i'
