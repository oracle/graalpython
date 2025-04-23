#include <math.h>
#include <Python.h>
#include <hpy.h>

// Porting to HPy, Step 1: All legacy methods
//
// An example of porting a C extension that implements a Point type
// with a couple of simple methods (a norm and a dot product). It
// illustrates the steps needed to port types that contain additional
// C attributes (in this case, x and y).
//
// This file contains an example first step of the port in which all methods
// still receive PyObject arguments and may still cast them to
// PyPointObject if they are instances of Point.

typedef struct {
    // PyObject_HEAD is required while legacy_slots are still used
    // but can (and should) be removed once the port to HPy is completed.
    PyObject_HEAD
    double x;
    double y;
    PyObject *obj;
} PointObject;

// This defines PyPointObject as an alias of PointObject so that existing
// code that still uses PyPointObject and expects PyObject_HEAD continues to
// compile and run. Once PyObject_HEAD has been removed, this alias should be
// removed so that code that still expects PyObject_HEAD will fail to compile.
typedef PointObject PyPointObject;

// The legacy type helper macro defines an PointObject_AsStruct function allows
// non-legacy methods to convert HPy handles to PointObject structs. It is not
// used in this file, but is provided so that methods can start to be ported
// (see point_hpy_legacy_2.c). The legacy type helper macro is used because
// PyObject_HEAD is still present in PointObject. Once PyObject_HEAD has been
// removed (see point_hpy_final.c) we will use HPy_TYPE_HELPERS instead.
HPyType_LEGACY_HELPERS(PointObject)

int Point_traverse(PyObject *self, visitproc visit, void *arg)
{
    Py_VISIT(((PyPointObject*)self)->obj);
    Py_VISIT(Py_TYPE(self));
    return 0;
}

void Point_dealloc(PyObject *self)
{
    Py_CLEAR(((PyPointObject*)self)->obj);
    PyTypeObject *tp = Py_TYPE(self);
    tp->tp_free(self);
    Py_DECREF(tp);
}

// this is a method for creating a Point
int Point_init(PyObject *self, PyObject *args, PyObject *kw)
{
    static char *kwlist[] = {"x", "y", "obj", NULL};
    PyPointObject *p = (PyPointObject *)self;
    p->x = 0.0;
    p->y = 0.0;
    p->obj = NULL;
    if (!PyArg_ParseTupleAndKeywords(args, kw, "|ddO", kwlist,
                                     &p->x, &p->y, &p->obj))
        return -1;
    if (p->obj == NULL)
        p->obj = Py_None;
    Py_INCREF(p->obj);
    return 0;
}

// this is a LEGACY method of Point
PyObject* Point_norm(PyObject *self)
{
    PyPointObject *p = (PyPointObject *)self;
    double norm;
    norm = sqrt(p->x * p->x + p->y * p->y);
    return PyFloat_FromDouble(norm);
}

// this is the getter for the associated object
PyObject* Point_obj_get(PyObject *self, void *context)
{
    PyPointObject *p = (PyPointObject *)self;
    Py_INCREF(p->obj);
    return p->obj;
}

// this is an LEGACY function which casts a PyObject* into a PyPointObject*
PyObject* dot(PyObject *self, PyObject *args)
{
    PyObject *point1, *point2;
    if (!PyArg_ParseTuple(args, "OO",  &point1, &point2))
        return NULL;

    PyPointObject *p1 = (PyPointObject *)point1;
    PyPointObject *p2 = (PyPointObject *)point2;

    double dp;
    dp = p1->x * p2->x + p1->y * p2->y;
    return PyFloat_FromDouble(dp);
}


// Method, type and module definitions. In this porting step, the module and
// type definitions have been ported to HPy, but the methods themselves
// remaining legacy methods.

// Legacy methods (all methods are still legacy methods)
static PyMethodDef PointMethods[] = {
    {"norm", (PyCFunction)Point_norm, METH_NOARGS, "Distance from origin."},
    {NULL, NULL, 0, NULL}
};

// Legacy getsets
static PyGetSetDef PointGetSets[] = {
    {"obj", (getter)Point_obj_get, NULL, "Associated object.", NULL},
    {NULL, NULL, 0, NULL}
};

// Legacy slots (all slots are still legacy slots)
static PyType_Slot Point_legacy_slots[] = {
    {Py_tp_doc, "Point (Step 1; All legacy methods)"},
    {Py_tp_init, Point_init},
    {Py_tp_methods, PointMethods},
    {Py_tp_getset, PointGetSets},
    {Py_tp_traverse, Point_traverse},
    {Py_tp_dealloc, Point_dealloc},
    {0, 0}
};

// HPy type methods and slots (no methods or slots have been ported yet)
static HPyDef *point_defines[] = {
    NULL
};

static HPyType_Spec Point_Type_spec = {
    .name = "point_hpy_legacy_1.Point",
    .basicsize = sizeof(PointObject),
    .itemsize = 0,
    .flags = HPy_TPFLAGS_DEFAULT,
    .builtin_shape = SHAPE(PointObject),
    .legacy_slots = Point_legacy_slots,
    .defines = point_defines,
};

// HPy supports only multiphase module initialization, so we must migrate the
// single phase initialization by extracting the code that populates the module
// object with attributes into a separate 'exec' slot. The module is not
// created manually by calling API like PyModule_Create, but the runtime creates
// the module for us from the specification in HPyModuleDef, and we can provide
// additional slots to populate the module before its initialization is finalized
HPyDef_SLOT(module_exec, HPy_mod_exec)
static int module_exec_impl(HPyContext *ctx, HPy mod)
{
    HPy point_type = HPyType_FromSpec(ctx, &Point_Type_spec, NULL);
    if (HPy_IsNull(point_type))
        return -1;
    HPy_SetAttr_s(ctx, mod, "Point", point_type);
    return 0;
}

// Legacy module methods (the "dot" method is still a PyCFunction)
static PyMethodDef PointModuleLegacyMethods[] = {
    {"dot", (PyCFunction)dot, METH_VARARGS, "Dot product."},
    {NULL, NULL, 0, NULL}
};

// HPy module methods: no regular methods have been ported yet,
// but we add the module execute slot
static HPyDef *module_defines[] = {
    &module_exec,
    NULL
};

static HPyModuleDef moduledef = {
    // .name = "step_01_hpy_legacy",
    // ^-- .name is not needed for multiphase module initialization,
    // it is always taken from the ModuleSpec
    .doc = "Point module (Step 1; All legacy methods)",
    .size = 0,
    .legacy_methods = PointModuleLegacyMethods,
    .defines = module_defines,
};
// END-OF: HPyModuleDef

// HPy_MODINIT takes the extension name, i.e., what would be XXX in PyInit_XXX,
// and the module definition. The module will be created by the runtime and
// passed to the HPy_mod_exec slots if any are defined
HPy_MODINIT(step_01_hpy_legacy, moduledef)
