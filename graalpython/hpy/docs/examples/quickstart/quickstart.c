// quickstart.c

// This header file is the entrypoint to the HPy API:
#include "hpy.h"

// HPy method: the HPyDef_METH macro generates some boilerplate code,
// the same code can be also written manually if desired
HPyDef_METH(say_hello, "say_hello", HPyFunc_NOARGS)
static HPy say_hello_impl(HPyContext *ctx, HPy self)
{
    // Methods take HPyContext, which must be passed as the first argument to
    // all HPy API functions. Other than that HPyUnicode_FromString does the
    // same thing as PyUnicode_FromString.
    //
    // HPy type represents a "handle" to a Python object, but may not be
    // a pointer to the object itself. It should be fully "opaque" to the
    // users. Try uncommenting the following two lines to see the difference
    // from PyObject*:
    //
    // if (self == self)
    //      HPyUnicode_FromString(ctx, "Surprise? Try HPy_Is(ctx, self, self)");

    return HPyUnicode_FromString(ctx, "Hello world");
}

static HPyDef *QuickstartMethods[] = {
    &say_hello, // 'say_hello' generated for us by the HPyDef_METH macro
    NULL,
};

static HPyModuleDef quickstart_def = {
    .doc = "HPy Quickstart Example",
    .defines = QuickstartMethods,
};

// The Python interpreter will create the module for us from the
// HPyModuleDef specification. Additional initialization can be
// done in the HPy_mod_exec slot
HPy_MODINIT(quickstart, quickstart_def)
