#include <hpy.h>

// BEGIN EuclideanVectorObject
typedef struct {
    long x;
    long y;
} EuclideanVectorObject;
HPyType_HELPERS(EuclideanVectorObject)
// END EuclideanVectorObject

// BEGIN HPy_tp_call
HPyDef_SLOT(call, HPy_tp_call)
static HPy
call_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs,
          HPy kwnames)
{
    static const char *keywords[] = { "x1", "y1", NULL };
    long x1, y1;
    HPyTracker ht;
    if (!HPyArg_ParseKeywords(ctx, &ht, args, nargs, kwnames, "ll", keywords,
                &x1, &y1)) {
        return HPy_NULL;
    }
    EuclideanVectorObject *data = EuclideanVectorObject_AsStruct(ctx, self);
    return HPyLong_FromLong(ctx, data->x * x1 + data->y * y1);
}
// END HPy_tp_call

// BEGIN HPy_SetCallFunction
HPyDef_CALL_FUNCTION(special_call)
static HPy
special_call_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs,
                  HPy kwnames)
{
    HPy tmp = call_impl(ctx, self, args, nargs, kwnames);
    HPy res = HPy_Negative(ctx, tmp);
    HPy_Close(ctx, tmp);
    return res;
}

HPyDef_SLOT(new, HPy_tp_new)
static HPy
new_impl(HPyContext *ctx, HPy cls, const HPy *args, HPy_ssize_t nargs, HPy kw)
{
    static const char *keywords[] = { "x", "y", "use_special_call", NULL };
    HPyTracker ht;
    long x, y;
    HPy use_special_call = ctx->h_False;
    if (!HPyArg_ParseKeywordsDict(ctx, &ht, args, nargs, kw, "ll|O", keywords,
                &x, &y, &use_special_call)) {
        return HPy_NULL;
    }
    EuclideanVectorObject *vector;
    HPy h_point = HPy_New(ctx, cls, &vector);
    if (HPy_IsNull(h_point)) {
        HPyTracker_Close(ctx, ht);
        return HPy_NULL;
    }
    if (HPy_IsTrue(ctx, use_special_call) &&
            HPy_SetCallFunction(ctx, h_point, &special_call) < 0) {
        HPyTracker_Close(ctx, ht);
        HPy_Close(ctx, h_point);
        return HPy_NULL;
    }
    HPyTracker_Close(ctx, ht);
    vector->x = x;
    vector->y = y;
    return h_point;
}
// END HPy_SetCallFunction

// BEGIN FooObject
typedef struct {
    void *a;
    HPyCallFunction call_func;
    void *b;
} FooObject;
HPyType_HELPERS(FooObject)
// END FooObject

// BEGIN vectorcalloffset
HPyDef_MEMBER(Foo_call_func_offset, "__vectorcalloffset__", HPyMember_HPYSSIZET,
                  offsetof(FooObject, call_func), .readonly=1)

HPyDef_CALL_FUNCTION(Foo_call_func)
static HPy
Foo_call_func_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs,
                   HPy kwnames)
{
    return HPyUnicode_FromString(ctx,
            "hello manually initialized call function");
}

HPyDef_SLOT(Foo_new, HPy_tp_new)
static HPy Foo_new_impl(HPyContext *ctx, HPy cls, const HPy *args,
        HPy_ssize_t nargs, HPy kw)
{
    FooObject *data;
    HPy h_obj = HPy_New(ctx, cls, &data);
    if (HPy_IsNull(h_obj))
        return HPy_NULL;
    data->call_func = Foo_call_func;
    return h_obj;
}
// END vectorcalloffset

// BEGIN pack_args
// function using legacy 'tp_call' calling convention
static HPy
Pack_call_legacy(HPyContext *ctx, HPy self, HPy args, HPy kwd)
{
    // use 'args' and 'kwd'
    return HPy_Dup(ctx, ctx->h_None);
}

// function using HPy calling convention
HPyDef_SLOT(Pack_call, HPy_tp_call)
static HPy
Pack_call_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs,
          HPy kwnames)
{
    HPy args_tuple, kwd;
    HPy result;
    if (!HPyHelpers_PackArgsAndKeywords(ctx, args, nargs, kwnames,
             &args_tuple, &kwd)) {
        return HPy_NULL;
    }
    result = Pack_call_legacy(ctx, self, args_tuple, kwd);
    HPy_Close(ctx, args_tuple);
    HPy_Close(ctx, kwd);
    return result;
}
// END pack_args

static HPyDef *Point_defines[] = {
    &call,
    &new,
    NULL
};
static HPyType_Spec EuclideanVector_spec = {
    .name = "hpycall.EuclideanVector",
    .basicsize = sizeof(EuclideanVectorObject),
    .builtin_shape = SHAPE(EuclideanVectorObject),
    .defines = Point_defines
};

static HPyDef *Foo_defines[] = {
    &Foo_call_func_offset,
    &Foo_new,
    NULL
};
static HPyType_Spec Foo_spec = {
    .name = "hpycall.Foo",
    .basicsize = sizeof(FooObject),
    .builtin_shape = SHAPE(FooObject),
    .defines = Foo_defines
};

static HPyDef *Pack_defines[] = {
    &Pack_call,
    NULL
};
static HPyType_Spec Pack_spec = {
    .name = "hpycall.Pack",
    .defines = Pack_defines
};

HPyDef_SLOT(init, HPy_mod_exec)
static int init_impl(HPyContext *ctx, HPy m)
{
    if (!HPyHelpers_AddType(ctx, m, "EuclideanVector", &EuclideanVector_spec, NULL)) {
        return -1;
    }
    if (!HPyHelpers_AddType(ctx, m, "Foo", &Foo_spec, NULL)) {
        return -1;
    }
    if (!HPyHelpers_AddType(ctx, m, "Pack", &Pack_spec, NULL)) {
        return -1;
    }
    return 0;
}

static HPyDef *moduledefs[] = {
    &init,
    NULL
};

static HPyModuleDef moduledef = {
    .doc = "HPy call protocol usage example",
    .size = 0,
    .legacy_methods = NULL,
    .defines = moduledefs,
    
};

HPy_MODINIT(hpycall, moduledef)
