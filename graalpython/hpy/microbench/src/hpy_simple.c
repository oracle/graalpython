#include "hpy.h"

/* module-level functions */

HPyDef_METH(noargs, "noargs", HPyFunc_NOARGS)
static HPy noargs_impl(HPyContext *ctx, HPy self)
{
    return HPy_Dup(ctx, ctx->h_None);
}

HPyDef_METH(onearg, "onearg", HPyFunc_O)
static HPy onearg_impl(HPyContext *ctx, HPy self, HPy arg)
{
    return HPy_Dup(ctx, ctx->h_None);
}

HPyDef_METH(varargs, "varargs", HPyFunc_VARARGS)
static HPy varargs_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
{
    return HPy_Dup(ctx, ctx->h_None);
}

HPyDef_METH(call_with_tuple, "call_with_tuple", HPyFunc_VARARGS)
static HPy call_with_tuple_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
{
    HPy f, f_args;
    if (nargs != 2) {
        HPyErr_SetString(ctx, ctx->h_TypeError, "call_with_tuple requires two arguments");
        return HPy_NULL;
    }
    f = args[0];
    f_args = args[1];
    return HPy_CallTupleDict(ctx, f, f_args, HPy_NULL);
}

HPyDef_METH(call_with_tuple_and_dict, "call_with_tuple_and_dict", HPyFunc_VARARGS)
static HPy call_with_tuple_and_dict_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
{
    HPy f, f_args, f_kw;
    if (nargs != 3) {
        HPyErr_SetString(ctx, ctx->h_TypeError, "call_with_tuple_and_dict requires three arguments");
        return HPy_NULL;
    }
    f = args[0];
    f_args = args[1];
    f_kw = args[2];
    return HPy_CallTupleDict(ctx, f, f_args, f_kw);
}

HPyDef_METH(allocate_int, "allocate_int", HPyFunc_NOARGS)
static HPy allocate_int_impl(HPyContext *ctx, HPy self)
{
    return HPyLong_FromLong(ctx, 2048);
}

HPyDef_METH(allocate_tuple, "allocate_tuple", HPyFunc_NOARGS)
static HPy allocate_tuple_impl(HPyContext *ctx, HPy self)
{
    return HPy_BuildValue(ctx, "ii", 2048, 2049);
}


/* Foo type */

typedef struct {
    long x;
    long y;
} FooObject;

HPyDef_SLOT(Foo_getitem, HPy_sq_item)
static HPy Foo_getitem_impl(HPyContext *ctx, HPy self, HPy_ssize_t i)
{
    return HPy_Dup(ctx, ctx->h_None);
}

HPyDef_SLOT(Foo_len, HPy_sq_length)
static HPy_ssize_t Foo_len_impl(HPyContext *ctx, HPy self)
{
    return 42;
}


// note that we can reuse the same HPyDef for both module-level and type-level
// methods
static HPyDef *foo_defines[] = {
    &noargs,
    &onearg,
    &varargs,
    &allocate_int,
    &allocate_tuple,
    &Foo_getitem,
    &Foo_len,
};


static HPyType_Spec Foo_spec = {
    .name = "hpy_simple.Foo",
    .basicsize = sizeof(FooObject),
    .flags = HPy_TPFLAGS_DEFAULT,
    .defines = foo_defines
};


/* Module defines */

HPyDef_SLOT(init_hpy_simple, HPy_mod_exec)
static int init_hpy_simple_impl(HPyContext *ctx, HPy m)
{
    HPy h_Foo = HPyType_FromSpec(ctx, &Foo_spec, NULL);
    if (HPy_IsNull(h_Foo))
        return -1;
    HPy_SetAttr_s(ctx, m, "Foo", h_Foo);
    HPy_SetAttr_s(ctx, m, "HTFoo", h_Foo);
    return 0;
}

static HPyDef *module_defines[] = {
    &noargs,
    &onearg,
    &varargs,
    &call_with_tuple,
    &call_with_tuple_and_dict,
    &allocate_int,
    &allocate_tuple,
    &init_hpy_simple,
    NULL
};

static HPyModuleDef moduledef = {
    .doc = "HPy microbenchmarks",
    .size = 0,
    .defines = module_defines,
};

HPy_MODINIT(hpy_simple, moduledef)
