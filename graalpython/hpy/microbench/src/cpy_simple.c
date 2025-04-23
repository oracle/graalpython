#include <Python.h>

/* module-level functions */

static PyObject* noargs(PyObject* self, PyObject* args)
{
    Py_RETURN_NONE;
}

static PyObject* onearg(PyObject* self, PyObject* arg)
{
    Py_RETURN_NONE;
}

static PyObject* varargs(PyObject* self, PyObject* args)
{
    Py_RETURN_NONE;
}

static PyObject* call_with_tuple(PyObject* self, PyObject* args)
{
    PyObject* f;
    PyObject* f_args;
    f = PyTuple_GetItem(args, 0);
    if (f == NULL)
        Py_RETURN_NONE;
    f_args = PyTuple_GetItem(args, 1);
    if (f_args == NULL)
        Py_RETURN_NONE;
    return PyObject_CallObject(f, f_args);
}

static PyObject* call_with_tuple_and_dict(PyObject* self, PyObject* args)
{
    PyObject* f;
    PyObject* f_args;
    PyObject* f_kw;
    f = PyTuple_GetItem(args, 0);
    if (f == NULL)
        Py_RETURN_NONE;
    f_args = PyTuple_GetItem(args, 1);
    if (f_args == NULL)
        Py_RETURN_NONE;
    f_kw = PyTuple_GetItem(args, 2);
    if (f_kw == NULL)
        Py_RETURN_NONE;
    return PyObject_Call(f, f_args, f_kw);
}

static PyObject* allocate_int(PyObject* self, PyObject* args)
{
    return PyLong_FromLong(2048);
}

static PyObject* allocate_tuple(PyObject* self, PyObject* args)
{
    return Py_BuildValue("ii", 2048, 2049);
}

static PyObject * Foo_getitem(PyObject *self, Py_ssize_t i)
{
    Py_RETURN_NONE;
}

static Py_ssize_t Foo_len(PyObject *self)
{
    return 42;
}

static PyMethodDef SimpleMethods[] = {
    {"noargs", (PyCFunction)noargs, METH_NOARGS, ""},
    {"onearg", (PyCFunction)onearg, METH_O, ""},
    {"varargs", (PyCFunction)varargs, METH_VARARGS, ""},
    {"call_with_tuple", (PyCFunction)call_with_tuple, METH_VARARGS, ""},
    {"call_with_tuple_and_dict", (PyCFunction)call_with_tuple_and_dict, METH_VARARGS, ""},
    {"allocate_int", (PyCFunction)allocate_int, METH_NOARGS, ""},
    {"allocate_tuple", (PyCFunction)allocate_tuple, METH_NOARGS, ""},
    {NULL, NULL, 0, NULL}
};


static PySequenceMethods FooSequence = {
	.sq_length = (lenfunc)Foo_len,
	.sq_item = (ssizeargfunc)Foo_getitem,
	NULL,
};


/* types */

typedef struct {
    PyObject_HEAD
} FooObject;

static PyTypeObject Foo_Type = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "cpy_simple.Foo",          /* tp_name */
    sizeof(FooObject),         /* tp_basicsize */
    0,                         /* tp_itemsize */
    0,                         /* tp_dealloc */
    0,                         /* tp_print */
    0,                         /* tp_getattr */
    0,                         /* tp_setattr */
    0,                         /* tp_compare */
    0,                         /* tp_repr */
    0,                         /* tp_as_number */
    &FooSequence,              /* tp_as_sequence */
    0,                         /* tp_as_mapping */
    0,                         /* tp_hash */
    0,                         /* tp_call */
    0,                         /* tp_str */
    0,                         /* tp_getattro */
    0,                         /* tp_setattro */
    0,                         /* tp_as_buffer */
    Py_TPFLAGS_DEFAULT,        /* tp_flags */
    "Foo objects",             /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    SimpleMethods,             /* tp_methods, reuse the same functions */
};


static PyType_Slot HTFoo_slots[] = {
    {Py_tp_doc, "HTFoo objects"},
    {Py_tp_methods, SimpleMethods},
    {Py_sq_item, Foo_getitem},
    {Py_sq_length, Foo_len},
    {0, 0}
};

static PyType_Spec HTFoo_Type_spec = {
    .name = "cpy_simple.Foo",
    .basicsize = sizeof(FooObject),
    .itemsize = 0,
    .flags = Py_TPFLAGS_DEFAULT,
    .slots = HTFoo_slots
};

static struct PyModuleDef moduledef = {
    PyModuleDef_HEAD_INIT,
    "cpy_simple",
    "Module Doc",
    -1,
    SimpleMethods,
    NULL,
    NULL,
    NULL,
    NULL,
};

PyMODINIT_FUNC
PyInit_cpy_simple(void)
{
    PyObject* m;
    Foo_Type.tp_new = PyType_GenericNew;
    if (PyType_Ready(&Foo_Type) < 0)
        return NULL;
    m = PyModule_Create(&moduledef);
    if (m == NULL)
        return NULL;
    Py_INCREF(&Foo_Type);
    PyModule_AddObject(m, "Foo", (PyObject *)&Foo_Type);

    PyObject *HTFoo_Type = PyType_FromSpec(&HTFoo_Type_spec);
    if (HTFoo_Type == NULL)
        return NULL;
    PyModule_AddObject(m, "HTFoo", HTFoo_Type);

    return m;
}
