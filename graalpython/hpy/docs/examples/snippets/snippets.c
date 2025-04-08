/* Module with various code snippets used in the docs.
 * All code snippets should be put into this file if possible. Notable
 * exception are code snippets showing definition of the module or the
 * HPyDef array initialization. Remember to also add tests to ../tests.py
 */
#include "hpy.h"

// ------------------------------------
// Snippets used in api.rst

// BEGIN: foo
void foo(HPyContext *ctx)
{
    HPy x = HPyLong_FromLong(ctx, 42);
    HPy y = HPy_Dup(ctx, x);
    /* ... */
    // we need to close x and y independently
    HPy_Close(ctx, x);
    HPy_Close(ctx, y);
}
// END: foo

// BEGIN: is_same_object
int is_same_object(HPyContext *ctx, HPy x, HPy y)
{
    // return x == y; // compilation error!
    return HPy_Is(ctx, x, y);
}
// END: is_same_object

// dummy entry point so that we can test the snippets:
HPyDef_METH(test_foo_and_is_same_object, "test_foo_and_is_same_object", HPyFunc_VARARGS)
static HPy test_foo_and_is_same_object_impl(HPyContext *ctx, HPy self,
                                            const HPy *args, size_t nargs)
{
    foo(ctx);   // not much we can test here
    return HPyLong_FromLong(ctx, is_same_object(ctx, args[0], args[1]));
}

// BEGIN: test_leak_stacktrace
HPyDef_METH(test_leak_stacktrace, "test_leak_stacktrace", HPyFunc_NOARGS)
static HPy test_leak_stacktrace_impl(HPyContext *ctx, HPy self)
{
    HPy num = HPyLong_FromLong(ctx, 42);
    if (HPy_IsNull(num)) {
        return HPy_NULL;
    }
    // No HPy_Close(ctx, num);
    return HPy_Dup(ctx, ctx->h_None);
}
// END: test_leak_stacktrace

// BEGIN: add
HPyDef_METH(add, "add", HPyFunc_VARARGS)
static HPy add_impl(HPyContext *ctx, HPy self, const HPy *args, size_t nargs)
{
    if (nargs != 2) {
        HPyErr_SetString(ctx, ctx->h_TypeError, "expected exactly two args");
        return HPy_NULL;
    }
    return HPy_Add(ctx, args[0], args[1]);
}
// END: add

// ------------------------------------
// Dummy module definition, so that we can test the snippets

static HPyDef *Methods[] = {
        &test_foo_and_is_same_object,
        &test_leak_stacktrace,
        &add,
        NULL,
};

static HPyModuleDef snippets = {
        .doc = "Various HPy code snippets for the docs",
        .size = 0,
        .defines = Methods
};

HPy_MODINIT(snippets, snippets)
