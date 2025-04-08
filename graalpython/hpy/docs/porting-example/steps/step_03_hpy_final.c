#include <math.h>
// #include <Python.h>  // disallow use of the old C API
#include <hpy.h>

// Porting to HPy, Step 3: All methods ported
//
// An example of porting a C extension that implements a Point type
// with a couple of simple methods (a norm and a dot product). It
// illustrates the steps needed to port types that contain additional
// C attributes (in this case, x and y).
//
// This file contains an example final step of the port in which all methods
// have been converted to HPy methods and PyObject_HEAD has been removed.

typedef struct {
    // PyObject_HEAD is no longer available in PointObject. In CPython,
    // of course, it still exists but is inaccessible from HPy_AsStruct. In
    // other Python implementations (e.g. PyPy) it might no longer exist at
    // all.
    double x;
    double y;
    HPyField obj;
} PointObject;

// Code using PyPointObject relied on PyObject_HEAD and is no longer valid
// (PyObject_HEAD has been removed from the PointObject struct above). The
// typedef below has been deleted to ensure that such code is now generates
// an error during compilation.
// typedef PointObject PyPointObject;

// The type helper macro defines an PointObject_AsStruct function allows
// converting HPy handles to PointObject structs. We no longer need to use
// the legacy type helper macro because PyObject_HEAD has been removed from
// PointObject.
HPyType_HELPERS(PointObject)

HPyDef_SLOT(Point_traverse, HPy_tp_traverse)
int Point_traverse_impl(void *self, HPyFunc_visitproc visit, void *arg)
{
    HPy_VISIT(&((PointObject*)self)->obj);
    return 0;
}

// this is a method for creating a Point
HPyDef_SLOT(Point_init, HPy_tp_init)
int Point_init_impl(HPyContext *ctx, HPy self, const HPy *args,
        HPy_ssize_t nargs, HPy kw)
{
    static const char *kwlist[] = {"x", "y", "obj", NULL};
    PointObject *p = PointObject_AsStruct(ctx, self);
    p->x = 0.0;
    p->y = 0.0;
    HPy obj = HPy_NULL;
    HPyTracker ht;
    if (!HPyArg_ParseKeywordsDict(ctx, &ht, args, nargs, kw, "|ddO", kwlist,
                              &p->x, &p->y, &obj))
        return -1;
    if (HPy_IsNull(obj))
        obj = ctx->h_None;
    /* INCREF not needed because HPyArg_ParseKeywordsDict does not steal a
       reference */
    HPyField_Store(ctx, self, &p->obj, obj);
    HPyTracker_Close(ctx, ht);
    return 0;
}

// this is the getter for the associated object
HPyDef_GET(Point_obj, "obj", .doc="Associated object.")
HPy Point_obj_get(HPyContext *ctx, HPy self, void* closure)
{
    PointObject *p = PointObject_AsStruct(ctx, self);
    return HPyField_Load(ctx, self, p->obj);
}

// an HPy method of Point
HPyDef_METH(Point_norm, "norm", HPyFunc_NOARGS, .doc="Distance from origin.")
HPy Point_norm_impl(HPyContext *ctx, HPy self)
{
    PointObject *p = PointObject_AsStruct(ctx, self);
    double norm;
    norm = sqrt(p->x * p->x + p->y * p->y);
    return HPyFloat_FromDouble(ctx, norm);
}

// this is an HPy function that uses Point
HPyDef_METH(dot, "dot", HPyFunc_VARARGS, .doc="Dot product.")
HPy dot_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
{
    HPy point1, point2;
    if (!HPyArg_Parse(ctx, NULL, args, nargs, "OO", &point1, &point2))
        return HPy_NULL;
    PointObject *p1 = PointObject_AsStruct(ctx, point1);
    PointObject *p2 = PointObject_AsStruct(ctx, point2);
    double dp;
    dp = p1->x * p2->x + p1->y * p2->y;
    return HPyFloat_FromDouble(ctx, dp);
}

// Method, type and module definitions. In this porting step all
// methods and slots have been ported to HPy and all legacy support
// has been removed.

// Support for legacy methods and slots has been removed. It used to be:
///
// static PyMethodDef PointMethods[] = { ... }
// static PyType_Slot Point_legacy_slots[] = { ... }
// static PyMethodDef PointModuleLegacyMethods[] = { ... }
//
// and .legacy_slots and .legacy_defines have been removed from HPyType_Spec
// HPyModuleDef respectively.

// HPy type methods and slots
static HPyDef *point_defines[] = {
    &Point_init,
    &Point_norm,
    &Point_obj,
    &Point_traverse,
    NULL
};

static HPyType_Spec Point_Type_spec = {
    .name = "point_hpy_final.Point",
    .doc = "Point (Step 03)",
    .basicsize = sizeof(PointObject),
    .itemsize = 0,
    .flags = HPy_TPFLAGS_DEFAULT,
    .defines = point_defines
};

HPyDef_SLOT(module_exec, HPy_mod_exec)
static int module_exec_impl(HPyContext *ctx, HPy mod)
{
    HPy point_type = HPyType_FromSpec(ctx, &Point_Type_spec, NULL);
    if (HPy_IsNull(point_type))
        return -1;
    HPy_SetAttr_s(ctx, mod, "Point", point_type);
    return 0;
}

// HPy module methods
static HPyDef *module_defines[] = {
        &module_exec,
    &dot,
    NULL
};

static HPyModuleDef moduledef = {
    .doc = "Point module (Step 3; Porting complete)",
    .size = 0,
    .defines = module_defines,
};

HPy_MODINIT(step_03_hpy_final, moduledef)
